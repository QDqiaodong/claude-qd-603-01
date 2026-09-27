-- H2（MySQL 兼容模式）专用测试建表脚本，仅覆盖赛事模块 + 会员弱引用所需表。
SET MODE MySQL;

CREATE TABLE club_member (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_no        VARCHAR(32)  NOT NULL,
    name           VARCHAR(64)  NOT NULL,
    phone          VARCHAR(32),
    level          VARCHAR(16)  NOT NULL,
    balance        DECIMAL(10,2) NOT NULL,
    total_recharge DECIMAL(10,2) NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    joined_at      DATE,
    created_at     DATETIME,
    updated_at     DATETIME,
    CONSTRAINT uk_member_card UNIQUE (card_no)
);

-- 仅为 club_member.activeOrderCount 的 @Formula 子查询提供存在性
CREATE TABLE table_session (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT,
    status    VARCHAR(16)
);

CREATE TABLE tournament (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_name  VARCHAR(128) NOT NULL,
    entrant_count    INT NOT NULL,
    bracket_size     INT NOT NULL,
    bye_count        INT NOT NULL,
    status           VARCHAR(16) NOT NULL,
    champion_id      BIGINT,
    current_version  INT NOT NULL,
    created_by       VARCHAR(64) NOT NULL,
    created_at       DATETIME,
    updated_at       DATETIME
);

CREATE TABLE tournament_entrant (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id   BIGINT NOT NULL,
    member_id       BIGINT,
    display_name    VARCHAR(64) NOT NULL,
    entrant_kind    VARCHAR(16) NOT NULL,
    seed_no         INT NOT NULL,
    withdrawn       TINYINT NOT NULL DEFAULT 0,
    withdraw_reason VARCHAR(255),
    withdrew_at     DATETIME,
    created_at      DATETIME,
    updated_at      DATETIME,
    CONSTRAINT uk_entrant_seed UNIQUE (tournament_id, seed_no)
);

CREATE TABLE tournament_match (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id     BIGINT NOT NULL,
    match_code        VARCHAR(32) NOT NULL,
    bracket_part      VARCHAR(16) NOT NULL,
    round_no          INT NOT NULL,
    round_stage       VARCHAR(16) NOT NULL,
    seq_in_round      INT NOT NULL,
    slot_a_source     VARCHAR(64) NOT NULL,
    slot_b_source     VARCHAR(64) NOT NULL,
    slot_a_seed_no    INT,
    slot_b_seed_no    INT,
    next_win          VARCHAR(64),
    next_lose         VARCHAR(64),
    structural_bye    TINYINT NOT NULL DEFAULT 0,
    status            VARCHAR(16) NOT NULL,
    winner_entrant_id BIGINT,
    loser_entrant_id  BIGINT,
    win_kind          VARCHAR(16),
    confirmed_by      VARCHAR(64),
    confirmed_at      DATETIME,
    created_at        DATETIME,
    updated_at        DATETIME,
    CONSTRAINT uk_match_code UNIQUE (tournament_id, match_code)
);

CREATE TABLE tournament_revision (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id      BIGINT NOT NULL,
    version_no         INT NOT NULL,
    change_type        VARCHAR(16) NOT NULL,
    operator_name      VARCHAR(64) NOT NULL,
    reason             VARCHAR(255),
    anchor_match_id    BIGINT,
    affected_match_ids VARCHAR(512),
    detail_json        CLOB,
    created_at         DATETIME
);
