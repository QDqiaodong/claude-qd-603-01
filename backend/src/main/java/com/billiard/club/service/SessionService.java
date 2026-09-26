package com.billiard.club.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billiard.club.common.BizException;
import com.billiard.club.dict.BilliardDict;
import com.billiard.club.domain.BilliardTable;
import com.billiard.club.domain.BilliardTableRepository;
import com.billiard.club.domain.Member;
import com.billiard.club.domain.MemberRepository;
import com.billiard.club.domain.SessionSegment;
import com.billiard.club.domain.SessionSegmentRepository;
import com.billiard.club.domain.SessionTransfer;
import com.billiard.club.domain.SessionTransferRepository;
import com.billiard.club.domain.TableSession;
import com.billiard.club.domain.TableSessionRepository;
import com.billiard.club.dto.SessionOpenRequest;
import com.billiard.club.dto.SessionSettleRequest;
import com.billiard.club.dto.SessionTransferRequest;
import com.billiard.club.dto.view.SessionSegmentView;
import com.billiard.club.dto.view.SessionTransferResultView;
import com.billiard.club.dto.view.SessionTransferView;
import com.billiard.club.dto.view.SessionView;

/**
 * 模块二：开局与计费。
 * 规则：开局 -> 结束 -> 结算单向流程 / 同一球台同一时间只能有一局 /
 * 时长按分钟计价、不足一小时按一小时 / 会员折扣 / 冻结卡禁开局 / 余额不足拦。
 *
 * 换台：进行中的局可整体挪到另一张同类型空闲台，单号与会员归属不变；
 * 每次换台只关闭当前计费段并开启下一段（单价取当时台价快照），
 * 结束时按各段各自的单价 × 实际分钟合并，再统一套会员折扣。
 *
 * 并发互斥：换台 / 结束 / 结算先锁开局单行，开局 / 换台再按 id 升序锁球台行，
 * 同一行上只有一个状态转换能生效；任一校验失败整个事务回滚，不留半成功。
 */
@Service
public class SessionService {

    private final TableSessionRepository sessionRepository;
    private final BilliardTableRepository tableRepository;
    private final MemberRepository memberRepository;
    private final SessionSegmentRepository segmentRepository;
    private final SessionTransferRepository transferRepository;
    private final TableService tableService;
    private final MemberService memberService;

    public SessionService(TableSessionRepository sessionRepository,
                          BilliardTableRepository tableRepository,
                          MemberRepository memberRepository,
                          SessionSegmentRepository segmentRepository,
                          SessionTransferRepository transferRepository,
                          TableService tableService,
                          MemberService memberService) {
        this.sessionRepository = sessionRepository;
        this.tableRepository = tableRepository;
        this.memberRepository = memberRepository;
        this.segmentRepository = segmentRepository;
        this.transferRepository = transferRepository;
        this.tableService = tableService;
        this.memberService = memberService;
    }

    @Transactional(readOnly = true)
    public List<SessionView> list() {
        return sessionRepository.findAllByOrderByIdDesc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<SessionView> openList() {
        return sessionRepository.findAllByOrderByIdDesc().stream()
                .filter(session -> BilliardDict.SESSION_OPEN.equals(session.getStatus()))
                .map(this::toView)
                .toList();
    }

    /** 某台的开局单：按计费分段反查，换台路过的局也算这台的历史 */
    @Transactional(readOnly = true)
    public List<SessionView> byTable(Long tableId) {
        BilliardTable table = tableService.require(tableId);
        List<Long> sessionIds = segmentRepository.findByTableId(table.getId()).stream()
                .map(SessionSegment::getSessionId)
                .distinct()
                .toList();
        if (sessionIds.isEmpty()) {
            return List.of();
        }
        return sessionRepository.findAllById(sessionIds).stream()
                .sorted(Comparator.comparing(TableSession::getStartTime).reversed())
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionView detail(Long id) {
        return toView(require(id));
    }

    /**
     * 开局：维护中的台不能开、台上已有进行中的局不能开、冻结卡不能开、余额为 0 不能开。
     * 开局即落地第一段计费段（单价取开局时刻的台价快照）。
     */
    @Transactional
    public SessionView open(SessionOpenRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        // 必填外键先判 null，再查库，避免甩给用户一个非人话的数据库报错
        if (request.tableId() == null) {
            throw new BizException("开局失败：请先选择要开的球台");
        }
        // 锁球台行：与换台、另一笔开局互斥，两个前台同时抢同一张台只有一笔能成功
        BilliardTable table = lockTable(request.tableId());

        if (BilliardDict.TABLE_MAINTENANCE.equals(table.getStatus())) {
            throw new BizException("球台「" + table.getTableNo() + "」正在维护中，维护中的球台禁止开局");
        }

        Optional<TableSession> running = sessionRepository
                .findFirstByTableIdAndStatus(table.getId(), BilliardDict.SESSION_OPEN);
        if (running.isPresent() || BilliardDict.TABLE_OCCUPIED.equals(table.getStatus())) {
            String sessionNo = running.map(TableSession::getSessionNo).orElse("未知");
            throw new BizException("球台「" + table.getTableNo() + "」已有一局进行中（单号 " + sessionNo
                    + "），同一球台同一时间只能有一局");
        }

        Member member = null;
        if (request.memberId() != null) {
            member = memberService.require(request.memberId());
            if (BilliardDict.MEMBER_FROZEN.equals(member.getStatus())) {
                throw new BizException("会员卡「" + member.getCardNo() + "」已冻结，冻结卡不能开局");
            }
            BigDecimal balance = member.getBalance() == null ? BigDecimal.ZERO : member.getBalance();
            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("会员卡「" + member.getCardNo() + "」余额不足（当前余额 " + balance
                        + " 元），请先充值再开局");
            }
        }

        TableSession session = new TableSession();
        session.setSessionNo(nextSessionNo());
        session.setTableId(table.getId());
        session.setMemberId(member == null ? null : member.getId());
        session.setStartTime(LocalDateTime.now());

        // 默认值只放在 service 的 create 里，实体上不写默认值
        session.setStatus(BilliardDict.SESSION_OPEN);
        session.setDiscount(BilliardDict.memberDiscount(member == null ? null : member.getLevel()));

        table.setStatus(BilliardDict.TABLE_OCCUPIED);
        tableRepository.save(table);

        TableSession saved = sessionRepository.save(session);

        // 第一段计费段：从开局时刻起，单价取当前台价快照
        SessionSegment first = new SessionSegment();
        first.setSessionId(saved.getId());
        first.setTableId(table.getId());
        first.setSeq(1);
        first.setStartTime(saved.getStartTime());
        first.setHourlyRate(table.getHourlyRate());
        segmentRepository.save(first);

        return toView(saved);
    }

    /**
     * 结束：只有进行中的局能结束，且只能走一步。
     * 关闭当前计费段后按「各段各自单价 × 实际分钟」合并计费，再统一套会员折扣；
     * 从未换过台的局保持「不足一小时按一小时」，球台释放回空闲。
     */
    @Transactional
    public SessionView close(Long id) {
        // 锁开局单行：结束与换台互斥，只有一个状态转换能生效
        TableSession session = lockSession(id);
        if (BilliardDict.SESSION_CLOSED.equals(session.getStatus())) {
            throw new BizException("开局单「" + session.getSessionNo() + "」已经结束，不能重复结束");
        }
        if (BilliardDict.SESSION_SETTLED.equals(session.getStatus())) {
            throw new BizException("开局单「" + session.getSessionNo() + "」已结算，不能重复结束");
        }

        LocalDateTime endTime = LocalDateTime.now();
        BilliardTable table = lockTable(session.getTableId());

        List<SessionSegment> segments = ensureSegments(session, table);
        SessionSegment current = currentOpenSegment(session, segments);
        // 只有「从未换过台」的局（全程只有一段）保留不足一小时按一小时；
        // 换过台的局按各段实际分钟计费，故障换台不让客人重复吃最低消费
        closeSegment(current, endTime, BilliardDict.SEGMENT_CLOSE_CLOSE, segments.size() == 1);
        segmentRepository.save(current);

        int minutes = 0;
        int billableMinutes = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (SessionSegment segment : segments) {
            minutes += segment.getMinutes();
            billableMinutes += segment.getBillableMinutes();
            amount = amount.add(segment.getAmount());
        }

        Member member = session.getMemberId() == null ? null : memberService.require(session.getMemberId());
        BigDecimal discount = BilliardDict.memberDiscount(member == null ? null : member.getLevel());

        session.setEndTime(endTime);
        session.setMinutes(minutes);
        session.setBillableMinutes(billableMinutes);
        session.setAmount(amount);
        session.setDiscount(discount);
        session.setPayAmount(amount.multiply(discount).setScale(2, RoundingMode.HALF_UP));
        session.setStatus(BilliardDict.SESSION_CLOSED);
        sessionRepository.save(session);

        // 局结束了，球台就空出来了（换过台的局释放的是当前所在台，故障原台留在维护中）
        if (BilliardDict.TABLE_OCCUPIED.equals(table.getStatus())) {
            table.setStatus(BilliardDict.TABLE_IDLE);
            tableRepository.save(table);
        }
        return toView(session);
    }

    /**
     * 换台：台呢破损 / 灯光故障等突发情况，把进行中的局挪到另一张同类型空闲台。
     *
     * 成功时同一个事务里完成：关闭当前计费段（记下原台结束时刻与分钟数）、
     * 开启下一段（目标台接续时刻与当时单价快照）、写换台轨迹、
     * 故障台转维护中、目标台转使用中；单号与会员归属不变。
     * 任一校验失败整体回滚，故障台、目标台、计费分段与开局状态不会出现半成功。
     *
     * 幂等：requestId 由前端在本次发起时生成，连点 / 网络重试都带同一个；
     * 命中已受理的 requestId 时直接返回当前状态，不产生第二条轨迹与分段。
     */
    @Transactional
    public SessionTransferResultView transfer(Long id, SessionTransferRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        if (request.targetTableId() == null) {
            throw new BizException("换台失败：请先选择要换入的目标球台");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BizException("换台失败：请填写换台原因（如台呢破损、灯光故障）");
        }
        if (request.requestId() == null || request.requestId().isBlank()) {
            throw new BizException("换台失败：缺少请求标识 requestId，请刷新页面后重试");
        }
        String requestId = request.requestId().trim();
        String reason = request.reason().trim();

        // 1) 先锁开局单行：与结束计费 / 结算互斥，另一位前台已结束的话这里拿到的是最新状态
        TableSession session = lockSession(id);

        // 2) 幂等：同一请求重复到达，返回已受理的结果，不再产生第二条轨迹
        Optional<SessionTransfer> duplicated = transferRepository.findByRequestId(requestId);
        if (duplicated.isPresent()) {
            if (!duplicated.get().getSessionId().equals(session.getId())) {
                throw new BizException("换台失败：请求标识 requestId 与其他开局单冲突，请刷新页面后重试");
            }
            return new SessionTransferResultView(toView(session), toTransferView(duplicated.get()), true);
        }

        // 3) 只有进行中的局能换台：已结束 / 已结算的局不能换
        if (BilliardDict.SESSION_CLOSED.equals(session.getStatus())) {
            throw new BizException("开局单「" + session.getSessionNo() + "」已经结束，已结束的局不能换台");
        }
        if (BilliardDict.SESSION_SETTLED.equals(session.getStatus())) {
            throw new BizException("开局单「" + session.getSessionNo() + "」已结算，已结算的局不能换台");
        }

        Long fromTableId = session.getTableId();
        Long toTableId = request.targetTableId();
        if (fromTableId.equals(toTableId)) {
            throw new BizException("目标球台就是当前球台，无需换台");
        }

        // 4) 按 id 升序锁两张球台（固定顺序上锁，避免两笔换台互相等待死锁）
        BilliardTable first = lockTable(Math.min(fromTableId, toTableId));
        BilliardTable second = lockTable(Math.max(fromTableId, toTableId));
        BilliardTable from = first.getId().equals(fromTableId) ? first : second;
        BilliardTable target = first.getId().equals(toTableId) ? first : second;

        // 5) 锁内校验目标台：必须同类型、空闲、非维护、没有刚被别人开局
        if (!from.getTableType().equals(target.getTableType())) {
            throw new BizException("只能换到同类型球台：当前台是「" + BilliardDict.tableTypeName(from.getTableType())
                    + "」，目标台「" + target.getTableNo() + "」是「" + BilliardDict.tableTypeName(target.getTableType()) + "」");
        }
        if (BilliardDict.TABLE_MAINTENANCE.equals(target.getStatus())) {
            throw new BizException("目标球台「" + target.getTableNo() + "」正在维护中，不能换入，请另选空闲台");
        }
        Optional<TableSession> runningOnTarget = sessionRepository
                .findFirstByTableIdAndStatus(target.getId(), BilliardDict.SESSION_OPEN);
        if (BilliardDict.TABLE_OCCUPIED.equals(target.getStatus()) || runningOnTarget.isPresent()) {
            String sessionNo = runningOnTarget.map(TableSession::getSessionNo).orElse("未知");
            throw new BizException("目标球台「" + target.getTableNo() + "」刚被另一局占用（单号 " + sessionNo
                    + "），换台失败，请另选空闲台");
        }

        LocalDateTime now = LocalDateTime.now();

        // 6) 关闭当前计费段：记下原台结束时刻与分钟数；历史段只关不改，单价快照早已落库
        List<SessionSegment> segments = ensureSegments(session, from);
        SessionSegment current = currentOpenSegment(session, segments);
        closeSegment(current, now, BilliardDict.SEGMENT_CLOSE_TRANSFER, false);
        segmentRepository.save(current);

        // 7) 开启下一段：接续时刻 = 上一段结束时刻，单价取目标台当下台价快照
        SessionSegment next = new SessionSegment();
        next.setSessionId(session.getId());
        next.setTableId(target.getId());
        next.setSeq(current.getSeq() + 1);
        next.setStartTime(now);
        next.setHourlyRate(target.getHourlyRate());
        segmentRepository.save(next);

        // 8) 写换台轨迹（requestId 唯一索引兜底，重复提交到不了这里）
        SessionTransfer transfer = new SessionTransfer();
        transfer.setSessionId(session.getId());
        transfer.setFromTableId(from.getId());
        transfer.setToTableId(target.getId());
        transfer.setReason(reason);
        transfer.setRequestId(requestId);
        transferRepository.save(transfer);

        // 9) 开局单：单号、会员、开局时间都不动，只换当前所在台
        session.setTableId(target.getId());
        sessionRepository.save(session);

        // 10) 球台状态：故障台直接转维护中，目标台转使用中，开局继续进行
        from.setStatus(BilliardDict.TABLE_MAINTENANCE);
        target.setStatus(BilliardDict.TABLE_OCCUPIED);
        tableRepository.save(from);
        tableRepository.save(target);

        return new SessionTransferResultView(toView(session), toTransferView(transfer), false);
    }

    /**
     * 结算：只有已结束的局能结算；余额结算要核余额，不足直接拦住。
     */
    @Transactional
    public SessionView settle(Long id, SessionSettleRequest request) {
        // 锁开局单行：与结束 / 换台互斥
        TableSession session = lockSession(id);
        if (BilliardDict.SESSION_OPEN.equals(session.getStatus())) {
            throw new BizException("开局单「" + session.getSessionNo() + "」尚未结束，请先结束计费再结算");
        }
        if (BilliardDict.SESSION_SETTLED.equals(session.getStatus())) {
            throw new BizException("开局单「" + session.getSessionNo() + "」已结算，不能重复结算");
        }
        if (request == null || request.payMode() == null) {
            throw new BizException("请选择结算支付方式：BALANCE / CASH / WECHAT / CARD");
        }
        String payMode = request.payMode();
        if (!BilliardDict.isValidPayMode(payMode)) {
            throw new BizException("支付方式不合法：" + payMode + "，只支持 BALANCE / CASH / WECHAT / CARD");
        }

        BigDecimal payAmount = session.getPayAmount() == null ? BigDecimal.ZERO : session.getPayAmount();

        if (BilliardDict.PAY_BALANCE.equals(payMode)) {
            if (session.getMemberId() == null) {
                throw new BizException("开局单「" + session.getSessionNo() + "」是散客开局，未挂会员卡，不能用储值余额结算");
            }
            Member member = memberService.require(session.getMemberId());
            if (BilliardDict.MEMBER_FROZEN.equals(member.getStatus())) {
                throw new BizException("会员卡「" + member.getCardNo() + "」已冻结，不能用储值余额结算");
            }
            BigDecimal balance = member.getBalance() == null ? BigDecimal.ZERO : member.getBalance();
            if (balance.compareTo(payAmount) < 0) {
                BigDecimal gap = payAmount.subtract(balance).setScale(2, RoundingMode.HALF_UP);
                throw new BizException("会员卡「" + member.getCardNo() + "」余额不足，本局应收 " + payAmount
                        + " 元，当前余额 " + balance + " 元，还差 " + gap + " 元，请先充值");
            }
            member.setBalance(balance.subtract(payAmount).setScale(2, RoundingMode.HALF_UP));
            memberRepository.save(member);
        }

        session.setPayMode(payMode);
        session.setStatus(BilliardDict.SESSION_SETTLED);
        return toView(sessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public TableSession require(Long id) {
        if (id == null) {
            throw new BizException("请先选择开局单");
        }
        return sessionRepository.findById(id)
                .orElseThrow(() -> new BizException("开局单不存在，ID：" + id));
    }

    /** 加行级写锁取开局单：换台 / 结束 / 结算的第一道互斥 */
    private TableSession lockSession(Long id) {
        if (id == null) {
            throw new BizException("请先选择开局单");
        }
        return sessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("开局单不存在，ID：" + id));
    }

    /** 加行级写锁取球台：开局 / 换台抢台的第一道互斥 */
    private BilliardTable lockTable(Long id) {
        if (id == null) {
            throw new BizException("请先选择球台");
        }
        return tableRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("球台不存在，ID：" + id));
    }

    /**
     * 取一局的全部计费分段；老数据没有分段时按开局时间与当前台价补录第一段，
     * 保证后续关段、合并计费的口径一致。
     */
    private List<SessionSegment> ensureSegments(TableSession session, BilliardTable currentTable) {
        List<SessionSegment> segments = new ArrayList<>(
                segmentRepository.findBySessionIdOrderBySeqAsc(session.getId()));
        if (!segments.isEmpty()) {
            return segments;
        }
        SessionSegment first = new SessionSegment();
        first.setSessionId(session.getId());
        first.setTableId(session.getTableId());
        first.setSeq(1);
        first.setStartTime(session.getStartTime());
        first.setHourlyRate(currentTable.getHourlyRate());
        segmentRepository.save(first);
        segments.add(first);
        return segments;
    }

    /** 当前进行中的段（只许最后一段是打开的，否则视为数据异常，拒绝继续） */
    private SessionSegment currentOpenSegment(TableSession session, List<SessionSegment> segments) {
        SessionSegment last = segments.get(segments.size() - 1);
        if (last.getEndTime() != null) {
            throw new BizException("开局单「" + session.getSessionNo()
                    + "」的计费分段状态异常（最后一段已关闭但局未结束），请检查数据");
        }
        return last;
    }

    /**
     * 关闭一段：记下结束时刻、实际分钟与小计。
     * minimumHour 只对「从未换过台的局」成立（不足一小时按一小时）；
     * 换台关闭的段一律按实际分钟计费。
     */
    private void closeSegment(SessionSegment segment, LocalDateTime endTime, String cause, boolean minimumHour) {
        long elapsed = Duration.between(segment.getStartTime(), endTime).toMinutes();
        int minutes = (int) Math.max(BilliardDict.MIN_SESSION_MINUTES, elapsed);
        int billableMinutes = minimumHour ? Math.max(minutes, BilliardDict.MIN_BILLABLE_MINUTES) : minutes;
        segment.setEndTime(endTime);
        segment.setMinutes(minutes);
        segment.setBillableMinutes(billableMinutes);
        segment.setAmount(segment.getHourlyRate()
                .multiply(BigDecimal.valueOf(billableMinutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
        segment.setCloseCause(cause);
    }

    private String nextSessionNo() {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i <= 9999; i++) {
            String no = "S" + day + "-" + String.format("%04d", i);
            if (!sessionRepository.existsBySessionNo(no)) {
                return no;
            }
        }
        throw new BizException("今日开局单号已用尽");
    }

    private SessionView toView(TableSession session) {
        BilliardTable table = tableRepository.findById(session.getTableId()).orElse(null);
        Member member = session.getMemberId() == null ? null
                : memberRepository.findById(session.getMemberId()).orElse(null);
        List<SessionSegmentView> segments = segmentRepository.findBySessionIdOrderBySeqAsc(session.getId())
                .stream().map(this::toSegmentView).toList();
        List<SessionTransferView> transfers = transferRepository.findBySessionIdOrderByIdAsc(session.getId())
                .stream().map(this::toTransferView).toList();
        return new SessionView(
                session.getId(),
                session.getSessionNo(),
                session.getTableId(),
                table == null ? null : table.getTableNo(),
                table == null ? null : table.getAreaName(),
                session.getMemberId(),
                member == null ? null : member.getName(),
                member == null ? null : member.getLevel(),
                member == null ? null : BilliardDict.memberLevelName(member.getLevel()),
                session.getStartTime(),
                session.getEndTime(),
                session.getMinutes(),
                session.getBillableMinutes(),
                table == null ? null : table.getHourlyRate(),
                session.getAmount(),
                session.getDiscount(),
                session.getPayAmount(),
                session.getStatus(),
                BilliardDict.sessionStatusName(session.getStatus()),
                session.getPayMode(),
                BilliardDict.payModeName(session.getPayMode()),
                segments,
                transfers);
    }

    private SessionSegmentView toSegmentView(SessionSegment segment) {
        BilliardTable table = tableRepository.findById(segment.getTableId()).orElse(null);
        return new SessionSegmentView(
                segment.getId(),
                segment.getSeq(),
                segment.getTableId(),
                table == null ? null : table.getTableNo(),
                table == null ? null : table.getAreaName(),
                segment.getStartTime(),
                segment.getEndTime(),
                segment.getMinutes(),
                segment.getBillableMinutes(),
                segment.getHourlyRate(),
                segment.getAmount(),
                segment.getCloseCause(),
                BilliardDict.segmentCloseCauseName(segment.getCloseCause()),
                segment.getEndTime() == null);
    }

    private SessionTransferView toTransferView(SessionTransfer transfer) {
        String fromTableNo = tableRepository.findById(transfer.getFromTableId())
                .map(BilliardTable::getTableNo).orElse(null);
        String toTableNo = tableRepository.findById(transfer.getToTableId())
                .map(BilliardTable::getTableNo).orElse(null);
        return new SessionTransferView(
                transfer.getId(),
                transfer.getFromTableId(),
                fromTableNo,
                transfer.getToTableId(),
                toTableNo,
                transfer.getReason(),
                transfer.getCreatedAt());
    }
}
