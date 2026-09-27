-- ============================================================================
-- 台球俱乐部管理系统 —— 建表 + 种子数据
-- 库名 billiard_club，字符集 utf8mb4。
--
-- 本文件同时被 mysql 镜像在构建期导入（mysql/Dockerfile 会 COPY schema.sql），
-- 因此 backend/src/main/resources/schema.sql 与 mysql/schema.sql 必须保持一致。
--
-- 种子数据的设计口径：下面每一条业务规则都能用现成数据直接触发，不存在永远排不到的规则。
--   球台：台号唯一 / 状态机（空闲·使用中·维护中）/ 维护中禁开局 / 使用中不能转维护
--   计费：开局→结束→结算单向 / 同一球台同一时间只能有一局 / 不足一小时按一小时 / 会员折扣
--   换台：进行中的局可换到同类型空闲台 / 原台转维护 / 分段计费（各段各自单价×实际分钟）/
--         重复提交幂等 / 换台与开局·结束互斥，失败整体回滚
--   器材：编号唯一 / 借出未还禁再借 / 损坏赔偿有上限且维修前禁借
--   会员：卡号唯一 / 余额不足拦（开局与余额结算）/ 冻结卡禁开局·禁充值 / 有进行中的局不能冻结
--
-- 每张表都刻意留了「能踩到规则」的行，见文件末尾的触发清单注释。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS billiard_club
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE billiard_club;

SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS tournament_revision_match;
DROP TABLE IF EXISTS tournament_revision;
DROP TABLE IF EXISTS tournament_match;
DROP TABLE IF EXISTS tournament_entry;
DROP TABLE IF EXISTS tournament;
DROP TABLE IF EXISTS recharge_record;
DROP TABLE IF EXISTS gear_rental;
DROP TABLE IF EXISTS session_transfer;
DROP TABLE IF EXISTS session_segment;
DROP TABLE IF EXISTS table_session;
DROP TABLE IF EXISTS gear;
DROP TABLE IF EXISTS billiard_table;
DROP TABLE IF EXISTS club_member;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------------
-- 球台
-- ---------------------------------------------------------------------------
CREATE TABLE billiard_table (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    table_no    VARCHAR(32)   NOT NULL COMMENT '台号，唯一',
    area_name   VARCHAR(64)   NOT NULL COMMENT '所属区域（平面图分区）',
    table_type  VARCHAR(16)   NOT NULL COMMENT 'CHINESE_EIGHT 中式黑八 / SNOOKER 斯诺克 / AMERICAN_NINE 美式九球',
    hourly_rate DECIMAL(10, 2) NOT NULL COMMENT '每小时单价',
    status      VARCHAR(16)   NOT NULL COMMENT 'IDLE 空闲 / OCCUPIED 使用中 / MAINTENANCE 维护中',
    remark      VARCHAR(255)           COMMENT '备注',
    created_at  DATETIME               COMMENT '创建时间',
    updated_at  DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_table_no (table_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '球台';

-- ---------------------------------------------------------------------------
-- 会员（表名避开 MySQL 8 保留字 member）
-- ---------------------------------------------------------------------------
CREATE TABLE club_member (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    card_no        VARCHAR(32)   NOT NULL COMMENT '会员卡号，唯一',
    name           VARCHAR(64)   NOT NULL COMMENT '会员姓名',
    phone          VARCHAR(32)            COMMENT '手机号',
    level          VARCHAR(16)   NOT NULL COMMENT 'NORMAL 普通 / SILVER 银卡 / GOLD 金卡 / DIAMOND 钻石卡',
    balance        DECIMAL(10, 2) NOT NULL COMMENT '卡内余额，只能被充值或余额结算变动',
    total_recharge DECIMAL(10, 2) NOT NULL COMMENT '累计充值金额，用于只升不降地算等级',
    status         VARCHAR(16)   NOT NULL COMMENT 'NORMAL 正常 / FROZEN 冻结',
    joined_at      DATE                   COMMENT '入会日期',
    created_at     DATETIME               COMMENT '创建时间',
    updated_at     DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_card_no (card_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '会员';

-- ---------------------------------------------------------------------------
-- 开局单（一局球）
-- ---------------------------------------------------------------------------
CREATE TABLE table_session (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    session_no       VARCHAR(32)   NOT NULL COMMENT '开局单号，唯一',
    table_id         BIGINT        NOT NULL COMMENT '球台',
    member_id        BIGINT                 COMMENT '可空外键：散客开局为 NULL',
    start_time       DATETIME      NOT NULL COMMENT '开局时间',
    end_time         DATETIME               COMMENT '结束时间，未结束为 NULL',
    minutes          INT                    COMMENT '实际时长（分钟）',
    billable_minutes INT                    COMMENT '计费分钟数：不足一小时按一小时',
    amount           DECIMAL(10, 2)         COMMENT '应收金额（未打折）',
    pay_amount       DECIMAL(10, 2)         COMMENT '实收金额（按会员等级折扣折算）',
    discount         DECIMAL(4, 2)          COMMENT '套用的会员折扣率',
    status           VARCHAR(16)   NOT NULL COMMENT 'OPEN 进行中 / CLOSED 已结束待结算 / SETTLED 已结算',
    pay_mode         VARCHAR(16)            COMMENT '结算支付方式，未结算为 NULL',
    created_at       DATETIME               COMMENT '创建时间',
    updated_at       DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_no (session_no),
    KEY idx_session_table_status (table_id, status),
    KEY idx_session_member_status (member_id, status),
    CONSTRAINT fk_session_table FOREIGN KEY (table_id) REFERENCES billiard_table (id),
    CONSTRAINT fk_session_member FOREIGN KEY (member_id) REFERENCES club_member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '开局单';

-- ---------------------------------------------------------------------------
-- 计费分段（一局至少一段；每次换台关闭当前段、开启下一段）
--   段的单价是「该段开始时」的台价快照，之后球台改价不影响已关闭的历史段。
--   结束计费时按各段各自的单价 × 实际分钟合并，再统一套会员折扣。
-- ---------------------------------------------------------------------------
CREATE TABLE session_segment (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    session_id       BIGINT        NOT NULL COMMENT '所属开局单',
    table_id         BIGINT        NOT NULL COMMENT '本段使用的球台',
    seq              INT           NOT NULL COMMENT '段序号，从 1 开始，每次换台 +1',
    start_time       DATETIME      NOT NULL COMMENT '本段开始时刻（第一段 = 开局时刻，后续 = 换台接续时刻）',
    end_time         DATETIME               COMMENT '本段结束时刻，进行中的段为 NULL',
    minutes          INT                    COMMENT '本段实际使用分钟数，关闭时算出',
    billable_minutes INT                    COMMENT '本段计费分钟数：未换过台的局不足一小时按一小时',
    hourly_rate      DECIMAL(10, 2) NOT NULL COMMENT '本段开始时的台价快照',
    amount           DECIMAL(10, 2)         COMMENT '本段小计 = 快照单价 × 计费分钟 / 60',
    close_cause      VARCHAR(16)            COMMENT 'TRANSFER 换台关闭 / CLOSE 结账关闭，未关闭为 NULL',
    created_at       DATETIME               COMMENT '创建时间',
    updated_at       DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_segment_session_seq (session_id, seq),
    KEY idx_segment_table (table_id),
    CONSTRAINT fk_segment_session FOREIGN KEY (session_id) REFERENCES table_session (id),
    CONSTRAINT fk_segment_table FOREIGN KEY (table_id) REFERENCES billiard_table (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '开局计费分段';

-- ---------------------------------------------------------------------------
-- 换台轨迹（只增不改）
--   request_id 是幂等键：前端每次发起换台生成一次，连点 / 网络重试都带同一个，
--   服务端凭它保证同一请求只产生一条轨迹、一个计费分段。
-- ---------------------------------------------------------------------------
CREATE TABLE session_transfer (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    session_id    BIGINT       NOT NULL COMMENT '所属开局单（单号不变）',
    from_table_id BIGINT       NOT NULL COMMENT '换出的故障台（换台成功后转维护中）',
    to_table_id   BIGINT       NOT NULL COMMENT '换入的目标台（换台成功后转使用中）',
    reason        VARCHAR(255) NOT NULL COMMENT '换台原因（台呢破损、灯光故障等）',
    request_id    VARCHAR(64)  NOT NULL COMMENT '幂等键：同一次换台请求重复提交只生效一次',
    created_at    DATETIME              COMMENT '换台时刻 = 原台结束时刻 = 目标台接续时刻',
    PRIMARY KEY (id),
    UNIQUE KEY uk_transfer_request (request_id),
    KEY idx_transfer_session (session_id),
    CONSTRAINT fk_transfer_session FOREIGN KEY (session_id) REFERENCES table_session (id),
    CONSTRAINT fk_transfer_from FOREIGN KEY (from_table_id) REFERENCES billiard_table (id),
    CONSTRAINT fk_transfer_to FOREIGN KEY (to_table_id) REFERENCES billiard_table (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '换台轨迹';

-- ---------------------------------------------------------------------------
-- 器材
-- ---------------------------------------------------------------------------
CREATE TABLE gear (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    gear_no    VARCHAR(32)   NOT NULL COMMENT '器材编号，唯一',
    gear_name  VARCHAR(64)   NOT NULL COMMENT '器材名称',
    gear_type  VARCHAR(16)   NOT NULL COMMENT 'CUE 球杆 / GLOVE 手套 / BRIDGE 架杆 / BALL_SET 球组',
    price      DECIMAL(10, 2) NOT NULL COMMENT '器材原价，损坏赔偿以此为上限',
    status     VARCHAR(16)   NOT NULL COMMENT 'AVAILABLE 在库 / RENTED 已借出 / DAMAGED 已损坏 / SCRAPPED 已报废',
    created_at DATETIME               COMMENT '创建时间',
    updated_at DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gear_no (gear_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '器材';

-- ---------------------------------------------------------------------------
-- 器材租借单
-- ---------------------------------------------------------------------------
CREATE TABLE gear_rental (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    rental_no   VARCHAR(32)   NOT NULL COMMENT '租借单号，唯一',
    gear_id     BIGINT        NOT NULL COMMENT '器材',
    member_id   BIGINT                 COMMENT '可空外键：散客借杆为 NULL',
    rent_time   DATETIME      NOT NULL COMMENT '借出时间',
    return_time DATETIME               COMMENT '归还时间，未归还为 NULL',
    status      VARCHAR(16)   NOT NULL COMMENT 'RENTED 租借中 / RETURNED 已归还 / DAMAGED 损坏待赔',
    deposit     DECIMAL(10, 2)         COMMENT '押金，默认取器材原价',
    damage_fee  DECIMAL(10, 2)         COMMENT '损坏赔偿金额，未损坏为 NULL',
    damage_note VARCHAR(255)           COMMENT '损坏说明',
    created_at  DATETIME               COMMENT '创建时间',
    updated_at  DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rental_no (rental_no),
    KEY idx_rental_gear_status (gear_id, status),
    CONSTRAINT fk_rental_gear FOREIGN KEY (gear_id) REFERENCES gear (id),
    CONSTRAINT fk_rental_member FOREIGN KEY (member_id) REFERENCES club_member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '器材租借单';

-- ---------------------------------------------------------------------------
-- 充值流水（只增不改）
-- ---------------------------------------------------------------------------
CREATE TABLE recharge_record (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    member_id      BIGINT        NOT NULL COMMENT '会员',
    amount         DECIMAL(10, 2) NOT NULL COMMENT '本次充值金额',
    before_balance DECIMAL(10, 2) NOT NULL COMMENT '充值前余额',
    after_balance  DECIMAL(10, 2) NOT NULL COMMENT '充值后余额',
    before_level   VARCHAR(16)   NOT NULL COMMENT '充值前等级',
    after_level    VARCHAR(16)   NOT NULL COMMENT '充值后等级（只升不降）',
    pay_mode       VARCHAR(16)   NOT NULL COMMENT 'BALANCE / CASH / WECHAT / CARD',
    created_at     DATETIME               COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_recharge_member (member_id),
    CONSTRAINT fk_recharge_member FOREIGN KEY (member_id) REFERENCES club_member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '充值流水';

-- ---------------------------------------------------------------------------
-- 双败淘汰赛事（模块五，与会员/散客/球台计费完全解耦，只对 club_member 做只读引用）
--
-- 括号图由三张结构组成：胜者组 WINNERS、败者组 LOSERS、总决赛 GRAND_FINAL；
-- 当总决赛里败者组冠军首次击败胜者组冠军时，再生成一场 GRAND_RESET（重置赛）。
--
-- 口径：
--   1. 开赛人数不是 2 的幂时，高种子自动轮空。轮空以专门的 BYE 场次行落库
--      （result_type='BYE'，视图层 realMatch 判否），绝不算真实比赛；拿轮空的
--      选手自动判为该 BYE 行胜者挂到下一场入位，同一选手不会在同一轮出现两场。
--   2. 场次之间用 winner_next_match_id / loser_next_match_id 两条有向边连成括号图，
--      回退纠错时沿边求「派生对阵」集合；另一分支不受影响、结果原样保留。
--   3. 选手第一次失利进入 loser_next 指向的败者组位置，第二次失利出局；
--      退赛走 WALKOVER（对手直接晋级），退赛选手保持 WITHDRAWN，不计两败。
--   4. tournament_revision(_match) 记录每一次受控回退的操作人、原因、
--      纠错前后快照与受影响场次，版本可追溯。
-- ---------------------------------------------------------------------------
CREATE TABLE tournament (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    tournament_no      VARCHAR(32)  NOT NULL COMMENT '赛事编号，唯一',
    name               VARCHAR(128) NOT NULL COMMENT '赛事名称',
    status             VARCHAR(16)  NOT NULL COMMENT 'DRAFT 未开赛 / RUNNING 进行中 / FINISHED 已结束',
    player_count       INT          NOT NULL COMMENT '开赛人数（参赛者数）',
    bracket_size       INT                   COMMENT '轮空后括号容量，2 的幂，开赛后回填',
    champion_entry_id  BIGINT                COMMENT '冠军参赛者，冠军产生后回填',
    created_at         DATETIME              COMMENT '创建时间',
    updated_at         DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tournament_no (tournament_no),
    KEY idx_tournament_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '双败淘汰赛事';

CREATE TABLE tournament_entry (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    tournament_id  BIGINT       NOT NULL COMMENT '所属赛事',
    member_id      BIGINT                COMMENT '会员参赛者外键；临时参赛者（散客）为 NULL',
    entry_type     VARCHAR(16)  NOT NULL COMMENT 'MEMBER 会员 / GUEST 临时参赛者',
    display_name   VARCHAR(64)  NOT NULL COMMENT '参赛用名（会员取会员姓名）',
    seed_no        INT          NOT NULL COMMENT '种子顺序，从 1 开始',
    loss_count     INT          NOT NULL COMMENT '累计失利次数：0 在胜者组 / 1 在败者组 / 2 出局',
    status         VARCHAR(16)  NOT NULL COMMENT 'ACTIVE 在赛 / ELIMINATED 两败出局 / WITHDRAWN 中途退赛',
    withdraw_note  VARCHAR(255)          COMMENT '退赛说明，退赛时回填',
    created_at     DATETIME              COMMENT '创建时间',
    updated_at     DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_entry_tournament_seed (tournament_id, seed_no),
    KEY idx_entry_member (member_id),
    CONSTRAINT fk_entry_tournament FOREIGN KEY (tournament_id) REFERENCES tournament (id),
    CONSTRAINT fk_entry_member FOREIGN KEY (member_id) REFERENCES club_member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '赛事参赛者';

ALTER TABLE tournament
    ADD CONSTRAINT fk_tournament_champion FOREIGN KEY (champion_entry_id) REFERENCES tournament_entry (id);

CREATE TABLE tournament_match (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    tournament_id           BIGINT       NOT NULL COMMENT '所属赛事',
    match_no                VARCHAR(16)  NOT NULL COMMENT '场次编号，如 W1-1 / L2-1 / GF / GR，赛事内唯一',
    stage                   VARCHAR(16)  NOT NULL COMMENT 'WINNERS 胜者组 / LOSERS 败者组 / GRAND_FINAL 总决赛 / GRAND_RESET 重置赛',
    round_no                INT          NOT NULL COMMENT '轮次（组内）：胜者组 W1..Wk，败者组 L1..L2k-2，总决赛单独编号',
    seq                     INT          NOT NULL COMMENT '该轮内从左到右的序号',
    side_a_entry_id         BIGINT                COMMENT '上半区（A 侧）参赛者，未到齐为 NULL；退赛者保留 id 并以参赛者状态标识',
    side_b_entry_id         BIGINT                COMMENT '下半区（B 侧）参赛者，未到齐为 NULL',
    side_a_source           VARCHAR(128)          COMMENT 'A 侧来源说明：种子直入 / 轮空直入 / 某场胜者 / 某场败者',
    side_b_source           VARCHAR(128)          COMMENT 'B 侧来源说明',
    winner_next_match_id    BIGINT                COMMENT '胜者去向场次（括号图边），总决赛无后续时为 NULL',
    winner_next_slot        VARCHAR(1)            COMMENT '胜者进入下场的位置 A / B',
    loser_next_match_id     BIGINT                COMMENT '败者去向场次（第一次失利落入败者组的位置），无后续即第二败出局',
    loser_next_slot         VARCHAR(1)            COMMENT '败者进入下场的位置 A / B',
    status                  VARCHAR(16)  NOT NULL COMMENT 'PENDING 等待选手到齐 / READY 双方到齐待开赛 / FINISHED 已结束 / VOID 双方退赛作废',
    winner_entry_id         BIGINT                COMMENT '胜者',
    loser_entry_id          BIGINT                COMMENT '败者',
    result_type             VARCHAR(16)           COMMENT 'NORMAL 正常确认 / WALKOVER 对手退赛直接晋级 / BYE 轮空（非真实比赛），未结束为 NULL',
    result_note             VARCHAR(255)          COMMENT '结果备注（退赛晋级时写明原因）',
    confirmed_by            VARCHAR(64)           COMMENT '确认/操作人',
    confirmed_at            DATETIME              COMMENT '确认时刻',
    created_at              DATETIME              COMMENT '创建时间',
    updated_at              DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_match_tournament_no (tournament_id, match_no),
    KEY idx_match_tournament_round (tournament_id, stage, round_no),
    CONSTRAINT fk_match_tournament FOREIGN KEY (tournament_id) REFERENCES tournament (id),
    CONSTRAINT fk_match_side_a FOREIGN KEY (side_a_entry_id) REFERENCES tournament_entry (id),
    CONSTRAINT fk_match_side_b FOREIGN KEY (side_b_entry_id) REFERENCES tournament_entry (id),
    CONSTRAINT fk_match_winner FOREIGN KEY (winner_entry_id) REFERENCES tournament_entry (id),
    CONSTRAINT fk_match_loser FOREIGN KEY (loser_entry_id) REFERENCES tournament_entry (id),
    CONSTRAINT fk_match_winner_next FOREIGN KEY (winner_next_match_id) REFERENCES tournament_match (id),
    CONSTRAINT fk_match_loser_next FOREIGN KEY (loser_next_match_id) REFERENCES tournament_match (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '赛事对阵场次';

CREATE TABLE tournament_revision (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    tournament_id            BIGINT       NOT NULL COMMENT '所属赛事',
    root_match_id            BIGINT                COMMENT '回退起点场次',
    operator                 VARCHAR(64)  NOT NULL COMMENT '纠错操作人（管理员必填）',
    reason                   VARCHAR(255) NOT NULL COMMENT '纠错原因（必填）',
    corrected_winner_entry_id BIGINT               COMMENT '同时更正后的胜者；为空表示仅回退到待确认',
    created_at               DATETIME              COMMENT '纠错时刻',
    PRIMARY KEY (id),
    KEY idx_revision_tournament (tournament_id),
    CONSTRAINT fk_revision_tournament FOREIGN KEY (tournament_id) REFERENCES tournament (id),
    -- 历史审计版本不能因为括号重建（删除动态重置赛）而无法落库：源场次删除后保留操作记录，编号仍在 match_no 快照里
    CONSTRAINT fk_revision_root_match FOREIGN KEY (root_match_id) REFERENCES tournament_match (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '赛事纠错版本（回退审计）';

CREATE TABLE tournament_revision_match (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    revision_id   BIGINT       NOT NULL COMMENT '所属纠错版本',
    match_id      BIGINT                COMMENT '场次 id；被删除的重置赛为 NULL',
    match_no      VARCHAR(16)  NOT NULL COMMENT '场次编号快照',
    stage         VARCHAR(16)           COMMENT '所在组快照',
    round_no      INT                   COMMENT '轮次快照',
    change_type   VARCHAR(16)  NOT NULL COMMENT 'ROOT 回退起点 / RESET 已结束被重置 / CLEARED 待赛场次入位被清空 / REMOVED 重置赛被删除',
    before_json   TEXT                  COMMENT '纠错前快照（JSON）',
    after_json    TEXT                  COMMENT '纠错重建后快照（JSON），删除的场次为 NULL',
    created_at    DATETIME              COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_revision_match_revision (revision_id),
    CONSTRAINT fk_revision_match_revision FOREIGN KEY (revision_id) REFERENCES tournament_revision (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '赛事纠错受影响场次快照';

-- ===========================================================================
-- 种子数据
-- ===========================================================================

-- 球台：8 张，覆盖 2 个区域、3 种台型
--   T-02 / T-03 / T-07 使用中（各有 1 局 OPEN）  -> 触发「同一球台同一时间只能有一局」「使用中不能转维护」
--   T-04 / T-08 维护中                          -> 触发「维护中禁开局」
--   T-01 / T-05 / T-06 空闲                     -> 可直接开局 / 转维护
INSERT INTO billiard_table (id, table_no, area_name, table_type, hourly_rate, status, remark, created_at, updated_at) VALUES
(1, 'T-01', 'A 区大厅', 'CHINESE_EIGHT', 40.00, 'IDLE',        '靠窗，最常被散客挑走',       NOW(), NOW()),
(2, 'T-02', 'A 区大厅', 'CHINESE_EIGHT', 40.00, 'OCCUPIED',    '临场教练位',                 NOW(), NOW()),
(3, 'T-03', 'A 区大厅', 'SNOOKER',       68.00, 'OCCUPIED',    '斯诺克标准台',               NOW(), NOW()),
(4, 'T-04', 'A 区大厅', 'SNOOKER',       68.00, 'MAINTENANCE', '台呢起球，等换台呢，暂停开放', NOW(), NOW()),
(5, 'T-05', 'B 区包间', 'SNOOKER',       88.00, 'IDLE',        '可预约包场',                 NOW(), NOW()),
(6, 'T-06', 'B 区包间', 'AMERICAN_NINE', 50.00, 'IDLE',        '九球专用',                   NOW(), NOW()),
(7, 'T-07', 'B 区包间', 'CHINESE_EIGHT', 48.00, 'OCCUPIED',    '包间内第二张台',             NOW(), NOW()),
(8, 'T-08', 'B 区包间', 'AMERICAN_NINE', 50.00, 'MAINTENANCE', '库边松动，已排维修',         NOW(), NOW());

-- 会员：7 位
--   M-002 正在进行中 1 局            -> 触发「有进行中的局不能冻结」
--   M-005 冻结                       -> 触发「冻结卡禁开局」「冻结卡禁充值」
--   M-006 余额 80 元（有一局待结算 88 元） -> 触发「余额结算余额不足」
--   M-007 余额 0 元                  -> 触发「余额不足不能开局」
INSERT INTO club_member (id, card_no, name, phone, level, balance, total_recharge, status, joined_at, created_at, updated_at) VALUES
(1, 'M-001', '王海涛', '13800000301', 'NORMAL',   200.00,   800.00, 'NORMAL', DATE_SUB(CURDATE(), INTERVAL 210 DAY), NOW(), NOW()),
(2, 'M-002', '刘思远', '13800000302', 'SILVER',  1200.00,  3600.00, 'NORMAL', DATE_SUB(CURDATE(), INTERVAL 160 DAY), NOW(), NOW()),
(3, 'M-003', '陈曦',   '13800000303', 'GOLD',    5600.00, 12800.00, 'NORMAL', DATE_SUB(CURDATE(), INTERVAL 120 DAY), NOW(), NOW()),
(4, 'M-004', '赵一鸣', '13800000304', 'DIAMOND',24000.00, 25000.00, 'NORMAL', DATE_SUB(CURDATE(), INTERVAL 90 DAY),  NOW(), NOW()),
(5, 'M-005', '孙倩',   '13800000305', 'NORMAL',   500.00,   500.00, 'FROZEN', DATE_SUB(CURDATE(), INTERVAL 60 DAY),  NOW(), NOW()),
(6, 'M-006', '周凯',   '13800000306', 'NORMAL',    80.00,   300.00, 'NORMAL', DATE_SUB(CURDATE(), INTERVAL 45 DAY),  NOW(), NOW()),
(7, 'M-007', '郑毅',   '13800000307', 'NORMAL',     0.00,   200.00, 'NORMAL', DATE_SUB(CURDATE(), INTERVAL 20 DAY),  NOW(), NOW());

-- 开局单：12 条
--   1 / 2 / 3 为 OPEN（进行中），2 号是散客局（member_id 为 NULL）
--   6 / 7 为 CLOSED（已结束待结算），其中 6 号挂 M-006，余额不足
--   4 / 5 / 8 / 9 / 10 / 11 / 12 为 SETTLED（已结算），折扣按会员等级折算
--   计费口径：不足一小时按一小时（S-0006 实际 50 分钟 -> 计费 60 分钟）
INSERT INTO table_session (id, session_no, table_id, member_id, start_time, end_time, minutes, billable_minutes, amount, pay_amount, discount, status, pay_mode, created_at, updated_at) VALUES
(1,  'S-0001', 2, 2,    DATE_SUB(NOW(), INTERVAL 42 MINUTE),  NULL,                            NULL, NULL, NULL,  NULL,  NULL, 'OPEN',    NULL,     NOW(), NOW()),
(2,  'S-0002', 3, NULL, DATE_SUB(NOW(), INTERVAL 18 MINUTE),  NULL,                            NULL, NULL, NULL,  NULL,  NULL, 'OPEN',    NULL,     NOW(), NOW()),
(3,  'S-0003', 7, 3,    DATE_SUB(NOW(), INTERVAL 76 MINUTE),  NULL,                            NULL, NULL, NULL,  NULL,  NULL, 'OPEN',    NULL,     NOW(), NOW()),
(4,  'S-0004', 1, 3,    DATE_SUB(NOW(), INTERVAL 200 MINUTE), DATE_SUB(NOW(), INTERVAL 140 MINUTE), 60, 60, 40.00, 36.00, 0.90, 'SETTLED', 'BALANCE', NOW(), NOW()),
(5,  'S-0005', 1, NULL, DATE_SUB(NOW(), INTERVAL 320 MINUTE), DATE_SUB(NOW(), INTERVAL 250 MINUTE), 70, 70, 46.67, 46.67, 1.00, 'SETTLED', 'CASH',    NOW(), NOW()),
(6,  'S-0006', 5, 6,    DATE_SUB(NOW(), INTERVAL 180 MINUTE), DATE_SUB(NOW(), INTERVAL 130 MINUTE), 50, 60, 88.00, 88.00, 1.00, 'CLOSED',  NULL,      NOW(), NOW()),
(7,  'S-0007', 6, 4,    DATE_SUB(NOW(), INTERVAL 400 MINUTE), DATE_SUB(NOW(), INTERVAL 370 MINUTE), 30, 60, 50.00, 42.50, 0.85, 'CLOSED',  NULL,      NOW(), NOW()),
(8,  'S-0008', 6, 1,    DATE_SUB(NOW(), INTERVAL 470 MINUTE), DATE_SUB(NOW(), INTERVAL 410 MINUTE), 60, 60, 50.00, 50.00, 1.00, 'SETTLED', 'WECHAT',  NOW(), NOW()),
(9,  'S-0009', 2, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY),      DATE_SUB(NOW(), INTERVAL 1 DAY) + INTERVAL 90 MINUTE, 90, 90, 60.00, 60.00, 1.00, 'SETTLED', 'CASH',    NOW(), NOW()),
(10, 'S-0010', 3, 2,    DATE_SUB(NOW(), INTERVAL 1 DAY),      DATE_SUB(NOW(), INTERVAL 1 DAY) + INTERVAL 45 MINUTE, 45, 60, 68.00, 64.60, 0.95, 'SETTLED', 'BALANCE', NOW(), NOW()),
(11, 'S-0011', 1, 4,    DATE_SUB(NOW(), INTERVAL 2 DAY),      DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 120 MINUTE, 120, 120, 80.00, 68.00, 0.85, 'SETTLED', 'BALANCE', NOW(), NOW()),
(12, 'S-0012', 7, 3,    DATE_SUB(NOW(), INTERVAL 2 DAY),      DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 120 MINUTE, 120, 120, 96.00, 86.40, 0.90, 'SETTLED', 'BALANCE', NOW(), NOW());

-- 计费分段：12 局各种子局都未换过台，每局恰好一段，与所属开局单完全对齐
--   1 / 2 / 3 的段仍进行中（end_time 为 NULL），其余段已随结账关闭
INSERT INTO session_segment (id, session_id, table_id, seq, start_time, end_time, minutes, billable_minutes, hourly_rate, amount, close_cause, created_at, updated_at) VALUES
(1,  1,  2, 1, DATE_SUB(NOW(), INTERVAL 42 MINUTE),  NULL,                                 NULL, NULL, 40.00, NULL,  NULL,    NOW(), NOW()),
(2,  2,  3, 1, DATE_SUB(NOW(), INTERVAL 18 MINUTE),  NULL,                                 NULL, NULL, 68.00, NULL,  NULL,    NOW(), NOW()),
(3,  3,  7, 1, DATE_SUB(NOW(), INTERVAL 76 MINUTE),  NULL,                                 NULL, NULL, 48.00, NULL,  NULL,    NOW(), NOW()),
(4,  4,  1, 1, DATE_SUB(NOW(), INTERVAL 200 MINUTE), DATE_SUB(NOW(), INTERVAL 140 MINUTE), 60,  60,  40.00, 40.00, 'CLOSE', NOW(), NOW()),
(5,  5,  1, 1, DATE_SUB(NOW(), INTERVAL 320 MINUTE), DATE_SUB(NOW(), INTERVAL 250 MINUTE), 70,  70,  40.00, 46.67, 'CLOSE', NOW(), NOW()),
(6,  6,  5, 1, DATE_SUB(NOW(), INTERVAL 180 MINUTE), DATE_SUB(NOW(), INTERVAL 130 MINUTE), 50,  60,  88.00, 88.00, 'CLOSE', NOW(), NOW()),
(7,  7,  6, 1, DATE_SUB(NOW(), INTERVAL 400 MINUTE), DATE_SUB(NOW(), INTERVAL 370 MINUTE), 30,  60,  50.00, 50.00, 'CLOSE', NOW(), NOW()),
(8,  8,  6, 1, DATE_SUB(NOW(), INTERVAL 470 MINUTE), DATE_SUB(NOW(), INTERVAL 410 MINUTE), 60,  60,  50.00, 50.00, 'CLOSE', NOW(), NOW()),
(9,  9,  2, 1, DATE_SUB(NOW(), INTERVAL 1 DAY),      DATE_SUB(NOW(), INTERVAL 1 DAY) + INTERVAL 90 MINUTE, 90, 90, 40.00, 60.00, 'CLOSE', NOW(), NOW()),
(10, 10, 3, 1, DATE_SUB(NOW(), INTERVAL 1 DAY),      DATE_SUB(NOW(), INTERVAL 1 DAY) + INTERVAL 45 MINUTE, 45, 60, 68.00, 68.00, 'CLOSE', NOW(), NOW()),
(11, 11, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY),      DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 120 MINUTE, 120, 120, 40.00, 80.00, 'CLOSE', NOW(), NOW()),
(12, 12, 7, 1, DATE_SUB(NOW(), INTERVAL 2 DAY),      DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 120 MINUTE, 120, 120, 48.00, 96.00, 'CLOSE', NOW(), NOW());

-- 器材：8 件
--   Q-002 / Q-004 已借出（各有一条 RENTED 的租借单） -> 触发「借出未还禁再借」
--   Q-006 已损坏                                     -> 触发「损坏器材维修前禁借」
--   Q-008 已报废                                     -> 触发「报废器材禁借」
INSERT INTO gear (id, gear_no, gear_name, gear_type, price, status, created_at, updated_at) VALUES
(1, 'Q-001', '小头杆「黑檀」',   'CUE',      680.00,  'AVAILABLE', NOW(), NOW()),
(2, 'Q-002', '小头杆「枫木」',   'CUE',      580.00,  'RENTED',    NOW(), NOW()),
(3, 'Q-003', '九球杆「碳纤」',   'CUE',     1280.00,  'AVAILABLE', NOW(), NOW()),
(4, 'Q-004', '架杆「金属长架」', 'BRIDGE',   120.00,  'RENTED',    NOW(), NOW()),
(5, 'Q-005', '三指手套 L',       'GLOVE',     60.00,  'AVAILABLE', NOW(), NOW()),
(6, 'Q-006', '三指手套 M',       'GLOVE',     60.00,  'DAMAGED',   NOW(), NOW()),
(7, 'Q-007', '美式球组「红白」', 'BALL_SET', 880.00,  'AVAILABLE', NOW(), NOW()),
(8, 'Q-008', '斯诺克球组',       'BALL_SET',1580.00,  'SCRAPPED',  NOW(), NOW());

-- 器材租借单：7 条（Q-002 被借过 2 次 -> 用于验证 @Formula rentCount = 2）
--   R-0001（Q-002）/ R-0002（Q-004）仍在租借中
--   R-0004 为损坏赔偿已结清单，赔偿金额取满器材原价 60 元
INSERT INTO gear_rental (id, rental_no, gear_id, member_id, rent_time, return_time, status, deposit, damage_fee, damage_note, created_at, updated_at) VALUES
(1, 'R-0001', 2, 2,    DATE_SUB(NOW(), INTERVAL 30 MINUTE),  NULL,                            'RENTED',   580.00, NULL,   NULL,                      NOW(), NOW()),
(2, 'R-0002', 4, NULL, DATE_SUB(NOW(), INTERVAL 25 MINUTE),  NULL,                            'RENTED',   120.00, NULL,   NULL,                      NOW(), NOW()),
(3, 'R-0003', 1, 3,    DATE_SUB(NOW(), INTERVAL 200 MINUTE), DATE_SUB(NOW(), INTERVAL 150 MINUTE), 'RETURNED', 680.00, NULL,   NULL,                      NOW(), NOW()),
(4, 'R-0004', 6, 1,    DATE_SUB(NOW(), INTERVAL 300 MINUTE), DATE_SUB(NOW(), INTERVAL 240 MINUTE), 'DAMAGED',   60.00, 60.00, '手套缝线开裂，按原价赔付', NOW(), NOW()),
(5, 'R-0005', 5, 4,    DATE_SUB(NOW(), INTERVAL 350 MINUTE), DATE_SUB(NOW(), INTERVAL 330 MINUTE), 'RETURNED',  60.00, NULL,   NULL,                      NOW(), NOW()),
(6, 'R-0006', 3, 2,    DATE_SUB(NOW(), INTERVAL 1 DAY),      DATE_SUB(NOW(), INTERVAL 1 DAY) + INTERVAL 40 MINUTE, 'RETURNED', 1280.00, NULL, NULL,      NOW(), NOW()),
(7, 'R-0007', 2, 1,    DATE_SUB(NOW(), INTERVAL 3 DAY),      DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 50 MINUTE, 'RETURNED',  580.00, NULL, NULL,      NOW(), NOW());

-- 充值流水：11 条，每个会员的累计充值与其 total_recharge / 等级对得上
INSERT INTO recharge_record (id, member_id, amount, before_balance, after_balance, before_level, after_level, pay_mode, created_at) VALUES
(1,  1,   500.00,     0.00,   500.00, 'NORMAL',  'NORMAL',  'WECHAT', DATE_SUB(NOW(), INTERVAL 210 DAY)),
(2,  1,   300.00,   500.00,   800.00, 'NORMAL',  'NORMAL',  'CASH',   DATE_SUB(NOW(), INTERVAL 150 DAY)),
(3,  2,  2000.00,     0.00,  2000.00, 'NORMAL',  'NORMAL',  'WECHAT', DATE_SUB(NOW(), INTERVAL 160 DAY)),
(4,  2,  1600.00,  2000.00,  3600.00, 'NORMAL',  'SILVER',  'BALANCE',DATE_SUB(NOW(), INTERVAL 100 DAY)),
(5,  3,  8000.00,     0.00,  8000.00, 'NORMAL',  'SILVER',  'CARD',   DATE_SUB(NOW(), INTERVAL 120 DAY)),
(6,  3,  4800.00,  8000.00, 12800.00, 'SILVER',  'GOLD',    'WECHAT', DATE_SUB(NOW(), INTERVAL 70 DAY)),
(7,  4, 15000.00,     0.00, 15000.00, 'NORMAL',  'GOLD',    'CARD',   DATE_SUB(NOW(), INTERVAL 90 DAY)),
(8,  4, 10000.00, 15000.00, 25000.00, 'GOLD',    'DIAMOND', 'WECHAT', DATE_SUB(NOW(), INTERVAL 40 DAY)),
(9,  5,   500.00,     0.00,   500.00, 'NORMAL',  'NORMAL',  'CASH',   DATE_SUB(NOW(), INTERVAL 60 DAY)),
(10, 6,   300.00,     0.00,   300.00, 'NORMAL',  'NORMAL',  'CASH',   DATE_SUB(NOW(), INTERVAL 45 DAY)),
(11, 7,   200.00,     0.00,   200.00, 'NORMAL',  'NORMAL',  'WECHAT', DATE_SUB(NOW(), INTERVAL 20 DAY));

-- ===========================================================================
-- 种子数据下的规则触发清单（每条规则都能用现成数据打到）
--   台号唯一                 POST /api/tables   {tableNo:"T-01"}
--   维护中禁开局             POST /api/sessions {tableId:4}
--   同一球台同时只有一局     POST /api/sessions {tableId:2}
--   使用中不能转维护         POST /api/tables/2/maintenance
--   冻结卡禁开局             POST /api/sessions {tableId:5,memberId:5}
--   余额为 0 禁开局          POST /api/sessions {tableId:6,memberId:7}
--   未结束不能结算           POST /api/sessions/1/settle  {payMode:"CASH"}
--   已结算不能重复结束       POST /api/sessions/4/close
--   余额结算余额不足         POST /api/sessions/6/settle  {payMode:"BALANCE"}   (M-006 余额 80 < 88)
--   借出未还禁再借           POST /api/rentals  {gearId:2}
--   损坏器材禁借             POST /api/rentals  {gearId:6}
--   赔偿超过器材原价         POST /api/rentals/2/return {damaged:true,damageFee:9999}  (Q-004 原价 120)
--   卡号唯一                 POST /api/members  {cardNo:"M-001"}
--   冻结卡禁充值             POST /api/members/5/recharge {amount:500}
--   充值区间 100~5000        POST /api/members/1/recharge {amount:50}
--   有进行中的局不能冻结     POST /api/members/2/freeze
--   换台（成功）             POST /api/sessions/1/transfer  {targetTableId:1, reason:"台呢破损", requestId:"任意唯一串"}
--                            （S-0001 在中式黑八 T-02 上，T-01 是同类型空闲台；成功后 T-02 转维护、T-01 转使用中）
--   换到不同类型台（被拒）   POST /api/sessions/1/transfer  {targetTableId:5, reason:"台呢破损", requestId:"..."}
--   换到维护中的台（被拒）   POST /api/sessions/1/transfer  {targetTableId:4, reason:"台呢破损", requestId:"..."}
--   换到已有局的台（被拒）   POST /api/sessions/1/transfer  {targetTableId:7, reason:"台呢破损", requestId:"..."}
--   同一换台重复提交（幂等） 同一个 requestId 再发一次 -> 返回已受理，不产生第二条轨迹与分段
--   已结束的局换台（被拒）   POST /api/sessions/6/transfer  {targetTableId:1, reason:"...", requestId:"..."}
-- ===========================================================================

-- ===========================================================================
-- 赛事模块（双败淘汰）规则触发清单 —— 演示数据由后端启动时通过业务服务创建：
--   T-DEMO-01 夏季双败公开赛（6 人，2 个轮空，进行中）
--     W1-1 / W1-3 是 BYE 轮空行（非真实比赛，不能回退）；
--     W2-2（陈曦 vs 刘思远）确认 -> 败者首次失利落入败者组 L2-2 B 侧；
--     L2-1/L2-2 确认 -> 败者第二败，状态变 ELIMINATED；
--     总决赛让败者组冠军击败胜者组冠军 -> 自动生成重置赛 GR，GR 结束才产生冠军。
--   T-DEMO-02 五一擂台赛（4 人，已完赛）：GF 败者组冠军首胜 -> GR -> 陈曦夺冠。
--   已确认不能普通改单   POST /api/tournaments/matches/{id}/result  对 FINISHED 场次
--   受控回退+确定性重建  POST /api/tournaments/matches/{id}/rollback
--                        {operator, reason, correctedWinnerEntryId?}
--                        版本/操作人/原因/受影响场次在 /api/tournaments/{id}/revisions
--   中途退赛三态         POST /api/tournaments/{tid}/entries/{eid}/withdraw
--                        未出场（0 败）/ 已一败（1 败）保持 WITHDRAWN；已出局退赛被拦
-- ===========================================================================
