package com.billiard.club.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billiard.club.common.BizException;
import com.billiard.club.domain.Member;
import com.billiard.club.domain.MemberRepository;
import com.billiard.club.domain.Tournament;
import com.billiard.club.domain.TournamentEntrant;
import com.billiard.club.domain.TournamentEntrantRepository;
import com.billiard.club.domain.TournamentMatch;
import com.billiard.club.domain.TournamentMatchRepository;
import com.billiard.club.domain.TournamentRevision;
import com.billiard.club.domain.TournamentRevisionRepository;
import com.billiard.club.domain.TournamentRepository;
import com.billiard.club.dto.CorrectResultRequest;
import com.billiard.club.dto.EntrantWithdrawRequest;
import com.billiard.club.dto.MatchResultRequest;
import com.billiard.club.dto.RollbackRequest;
import com.billiard.club.dto.TournamentCreateRequest;
import com.billiard.club.dto.view.EntrantView;
import com.billiard.club.dto.view.MatchView;
import com.billiard.club.dto.view.RevisionView;
import com.billiard.club.dto.view.RoundView;
import com.billiard.club.dto.view.SlotView;
import com.billiard.club.dto.view.TournamentListItemView;
import com.billiard.club.dto.view.TournamentView;
import com.billiard.club.service.tournament.BracketGenerator;
import com.billiard.club.service.tournament.BracketResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 双败淘汰赛事服务（模块五，独立于会员/散客/球台计费）。
 *
 * 结构在创建时一次性生成；赛果确认按双败规则只读式推进；退赛按
 * 「尚未出场 / 已经一败 / 已经出局」三态处理并把弃权链物化；总决赛
 * 败者组冠军首胜则补生成重置赛。已出结果的场次普通操作不能改，只能
 * 管理员带原因整体回退派生链，再按更正结果确定性重建；整个过程的
 * 版本、操作人、原因、受影响场次都写入纠错流水。
 */
@Service
public class TournamentService {

    private static final String STATUS_LIVE = "LIVE";
    private static final String STATUS_FINISHED = "FINISHED";

    private static final String MATCH_WAITING = "WAITING";
    private static final String MATCH_READY = "READY";
    private static final String MATCH_FINISHED = "FINISHED";

    private static final String KIND_MEMBER = "MEMBER";
    private static final String KIND_WALK_IN = "WALK_IN";

    private static final String WIN_NORMAL = "NORMAL";
    private static final String WIN_WALKOVER = "WALKOVER";
    public static final String WIN_DOUBLE_WALKOVER = "DOUBLE_WALKOVER";

    private static final String REV_RESULT = "RESULT";
    private static final String REV_WALKOVER = "WALKOVER";
    private static final String REV_ROLLBACK = "ROLLBACK";
    private static final String REV_CORRECT = "CORRECT";

    /** 操作人名包含该标记即视为管理员（回退/纠错入口） */
    private static final String ADMIN_MARK = "管理员";

    private final TournamentRepository tournamentRepository;
    private final TournamentEntrantRepository entrantRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentRevisionRepository revisionRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    public TournamentService(TournamentRepository tournamentRepository,
                             TournamentEntrantRepository entrantRepository,
                             TournamentMatchRepository matchRepository,
                             TournamentRevisionRepository revisionRepository,
                             MemberRepository memberRepository,
                             ObjectMapper objectMapper) {
        this.tournamentRepository = tournamentRepository;
        this.entrantRepository = entrantRepository;
        this.matchRepository = matchRepository;
        this.revisionRepository = revisionRepository;
        this.memberRepository = memberRepository;
        this.objectMapper = objectMapper;
    }

    // ==================================================================
    // 查询
    // ==================================================================

    @Transactional(readOnly = true)
    public List<TournamentListItemView> list() {
        return tournamentRepository.findAllByOrderByIdDesc().stream().map(t -> {
            String championName = null;
            if (t.getChampionId() != null) {
                championName = entrantRepository.findById(t.getChampionId())
                        .map(TournamentEntrant::getDisplayName).orElse(null);
            }
            return new TournamentListItemView(t.getId(), t.getTournamentName(), t.getEntrantCount(),
                    t.getBracketSize(), t.getByeCount(), t.getStatus(), statusName(t.getStatus()),
                    t.getCurrentVersion(), championName, t.getCreatedBy());
        }).toList();
    }

    @Transactional(readOnly = true)
    public TournamentView detail(Long id) {
        Tournament t = requireTournament(id);
        List<TournamentEntrant> entrants = entrantRepository.findByTournamentIdOrderBySeedNoAsc(id);
        List<TournamentMatch> matches = matchRepository.findByTournamentId(id);
        List<TournamentRevision> revisions = revisionRepository.findByTournamentIdOrderByIdAsc(id);
        return buildView(t, entrants, matches, revisions);
    }

    // ==================================================================
    // 创建赛事（生成完整括号）
    // ==================================================================

    @Transactional
    public TournamentView create(TournamentCreateRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        String name = request.tournamentName() == null ? "" : request.tournamentName().trim();
        if (name.isEmpty()) {
            throw new BizException("赛事名称不能为空");
        }
        String operator = trimOperator(request.operatorName());
        if (request.entrants() == null || request.entrants().size() < 4) {
            throw new BizException("双败淘汰赛事至少需要 4 名参赛者");
        }
        if (request.entrants().size() > 32) {
            throw new BizException("赛事参赛人数不能超过 32 人");
        }
        if (tournamentRepository.findByTournamentName(name).isPresent()) {
            throw new BizException("赛事名称「" + name + "」已存在");
        }

        List<TournamentEntrant> entrants = new ArrayList<>();
        Map<Long, Boolean> memberSeen = new HashMap<>();
        int seed = 0;
        for (TournamentCreateRequest.EntrantInput input : request.entrants()) {
            seed++;
            final int seedOrder = seed;
            TournamentEntrant entrant = new TournamentEntrant();
            if (input.memberId() != null) {
                Member member = memberRepository.findById(input.memberId())
                        .orElseThrow(() -> new BizException("第 " + seedOrder + " 号种子选择的会员不存在，ID：" + input.memberId()));
                if (memberSeen.put(member.getId(), Boolean.TRUE) != null) {
                    throw new BizException("会员「" + member.getName() + "」在同一场赛事中不能重复报名");
                }
                entrant.setMemberId(member.getId());
                entrant.setDisplayName(member.getName());
                entrant.setEntrantKind(KIND_MEMBER);
            } else {
                String walkIn = input.walkInName() == null ? "" : input.walkInName().trim();
                if (walkIn.isEmpty()) {
                    throw new BizException("第 " + seed + " 号种子是临时参赛者，必须登记姓名");
                }
                entrant.setMemberId(null);
                entrant.setDisplayName(walkIn);
                entrant.setEntrantKind(KIND_WALK_IN);
            }
            entrant.setSeedNo(seed);
            entrant.setWithdrawn(false);
            entrants.add(entrant);
        }

        int n = entrants.size();
        int bracketSize = BracketGenerator.bracketSizeOf(n);

        Tournament tournament = new Tournament();
        tournament.setTournamentName(name);
        tournament.setEntrantCount(n);
        tournament.setBracketSize(bracketSize);
        tournament.setByeCount(bracketSize - n);
        tournament.setStatus(STATUS_LIVE);
        tournament.setCurrentVersion(0);
        tournament.setCreatedBy(operator);
        tournament = tournamentRepository.save(tournament);

        for (TournamentEntrant entrant : entrants) {
            entrant.setTournamentId(tournament.getId());
        }
        entrants = entrantRepository.saveAll(entrants);

        BracketGenerator.Bracket bracket = BracketGenerator.generate(n);
        List<TournamentMatch> matchEntities = new ArrayList<>();
        for (BracketGenerator.MatchSpec spec : bracket.matches()) {
            matchEntities.add(toEntity(tournament.getId(), spec));
        }
        matchEntities = matchRepository.saveAll(matchEntities);
        refreshStatuses(tournament, matchEntities, entrants);

        return detail(tournament.getId());
    }

    private TournamentMatch toEntity(Long tournamentId, BracketGenerator.MatchSpec spec) {
        TournamentMatch m = new TournamentMatch();
        m.setTournamentId(tournamentId);
        m.setMatchCode(spec.matchCode);
        m.setBracketPart(spec.bracketPart);
        m.setRoundNo(spec.roundNo);
        m.setRoundStage(spec.roundStage);
        m.setSeqInRound(spec.seqInRound);
        m.setSlotASource(spec.slotA.source());
        m.setSlotBSource(spec.slotB.source());
        m.setSlotASeedNo(spec.slotA.seedNo());
        m.setSlotBSeedNo(spec.slotB.seedNo());
        m.setNextWin(spec.nextWin);
        m.setNextLose(spec.nextLose);
        m.setStructuralBye(spec.structuralBye);
        m.setStatus(MATCH_WAITING);
        return m;
    }

    // ==================================================================
    // 赛果确认
    // ==================================================================

    @Transactional
    public TournamentView confirmResult(Long tournamentId, Long matchId, MatchResultRequest request) {
        TournamentBundle bundle = loadBundle(tournamentId);
        TournamentMatch match = requireMatch(bundle, matchId);
        String operator = trimOperator(request == null ? null : request.operatorName());
        Long winnerId = request == null ? null : request.winnerEntrantId();
        if (winnerId == null) {
            throw new BizException("请选择胜方");
        }
        if (STATUS_FINISHED.equals(bundle.tournament.getStatus())) {
            throw new BizException("赛事已完赛产生冠军，不能再确认赛果");
        }
        if (match.getStructuralBye()) {
            throw new BizException("「" + match.getMatchCode() + "」是轮空占位，轮空不算真实比赛，无需确认结果");
        }
        if (match.getStatus().equals(MATCH_FINISHED)) {
            throw new BizException("「" + match.getMatchCode()
                    + "」赛果已确认并已推进下游对阵，普通操作不能直接修订，请由管理员说明原因后回退纠错");
        }
        BracketResolver resolver = bundle.resolver();
        BracketResolver.Resolved ra = resolver.resolve(match.getSlotASource());
        BracketResolver.Resolved rb = resolver.resolve(match.getSlotBSource());
        if (!ra.present() || !rb.present()) {
            throw new BizException("「" + match.getMatchCode() + "」双方尚未到齐，待上场人员到齐后才能确认赛果");
        }
        if (!winnerId.equals(ra.entrantId()) && !winnerId.equals(rb.entrantId())) {
            throw new BizException("胜方必须是本场两名参赛者之一");
        }
        Long loserId = winnerId.equals(ra.entrantId()) ? rb.entrantId() : ra.entrantId();
        applyOutcome(bundle, match, winnerId, loserId, WIN_NORMAL, operator);

        List<TournamentMatch> autoFinished = propagateWalkovers(bundle, operator, REV_RESULT);
        List<TournamentMatch> affected = new ArrayList<>(List.of(match));
        autoFinished.stream().filter(m -> !m.getId().equals(match.getId())).forEach(affected::add);
        bumpVersion(bundle.tournament);
        writeRevision(bundle, REV_RESULT, operator, null, match, affected,
                Map.of("action", "CONFIRM", "winnerId", winnerId, "loserId", loserId));
        return saveAndView(bundle);
    }

    /** 把一场比赛写定结果；总决赛败者组冠军首胜时补生成重置赛 */
    private void applyOutcome(TournamentBundle bundle, TournamentMatch match,
                              Long winnerId, Long loserId, String winKind, String operator) {
        match.setWinnerEntrantId(winnerId);
        match.setLoserEntrantId(loserId);
        match.setWinKind(winKind);
        match.setConfirmedBy(operator);
        match.setConfirmedAt(LocalDateTime.now());
        match.setStatus(MATCH_FINISHED);

        if ("GF".equals(match.getMatchCode())) {
            BracketResolver resolver = bundle.resolver();
            Long wbChampion = resolver.resolve(match.getSlotASource()).entrantId();
            Long lbChampion = resolver.resolve(match.getSlotBSource()).entrantId();
            boolean loserBracketChampionWon = winnerId.equals(lbChampion);
            boolean winnerBracketChampionLost = wbChampion.equals(loserId);
            if (loserBracketChampionWon && winnerBracketChampionLost && resetNotExists(bundle)) {
                // 败者组冠军（一败）首次击败胜者组冠军（零败）：胜者组冠军拿到首败，
                // 双方都只剩「再败即淘汰」，必须再打一场重置赛才能产生冠军
                createResetMatch(bundle);
            }
        }
    }

    private boolean resetNotExists(TournamentBundle bundle) {
        return bundle.byCode.get("GF-RESET") == null;
    }

    private void createResetMatch(TournamentBundle bundle) {
        TournamentMatch reset = new TournamentMatch();
        reset.setTournamentId(bundle.tournament.getId());
        reset.setMatchCode("GF-RESET");
        reset.setBracketPart(BracketGenerator.PART_GF);
        reset.setRoundNo(2);
        reset.setRoundStage(BracketGenerator.STAGE_FINAL);
        reset.setSeqInRound(1);
        // 重置赛双方就是总决赛双方；用 GF 的胜/败槽固定引用，顺序与总决赛一致
        reset.setSlotASource("LOSE:GF");
        reset.setSlotBSource("WIN:GF");
        reset.setSlotASeedNo(null);
        reset.setSlotBSeedNo(null);
        reset.setNextWin(null);
        reset.setNextLose(null);
        reset.setStructuralBye(false);
        reset.setStatus(MATCH_WAITING);
        reset = matchRepository.save(reset);
        bundle.matches.add(reset);
        bundle.byCode.put(reset.getMatchCode(), reset);
        bundle.idIndex.put(reset.getId(), reset);
    }

    // ==================================================================
    // 中途退赛（三态）与弃权链物化
    // ==================================================================

    @Transactional
    public TournamentView withdraw(Long tournamentId, Long entrantId, EntrantWithdrawRequest request) {
        TournamentBundle bundle = loadBundle(tournamentId);
        String operator = trimOperator(request == null ? null : request.operatorName());
        String reason = request == null || request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty()) {
            throw new BizException("退赛必须填写原因");
        }
        TournamentEntrant entrant = bundle.entrants.stream()
                .filter(e -> e.getId().equals(entrantId)).findFirst()
                .orElseThrow(() -> new BizException("参赛者不存在，ID：" + entrantId));
        if (Boolean.TRUE.equals(entrant.getWithdrawn())) {
            throw new BizException("「" + entrant.getDisplayName() + "」已登记退赛");
        }
        if (STATUS_FINISHED.equals(bundle.tournament.getStatus())) {
            throw new BizException("赛事已完赛，不能再登记退赛");
        }

        BracketResolver resolver = bundle.resolver();
        int losses = resolver.lossesOf(entrantId);
        boolean played = resolver.hasPlayed(entrantId);
        String phaseBefore = played ? (losses >= 2 ? "ELIMINATED" : losses == 1 ? "ONE_LOSS" : "UNBEATEN")
                : "NOT_PLAYED";
        if ("ELIMINATED".equals(phaseBefore)) {
            throw new BizException("「" + entrant.getDisplayName() + "」已经二败出局，无需再退赛；如系误判请走管理员回退");
        }

        entrant.setWithdrawn(true);
        entrant.setWithdrawReason(reason);
        entrant.setWithdrewAt(LocalDateTime.now());

        // 物化受影响的弃权链：该参赛者在场的所有待出结果场次，对手不战而胜
        List<TournamentMatch> autoFinished = propagateWalkovers(bundle, operator, REV_WALKOVER);
        List<Long> affected = new ArrayList<>();
        autoFinished.forEach(m -> affected.add(m.getId()));
        // 若该参赛者本来就将在后续场次登场，对应场次也会随上游结果一并物化；
        // 尚未与任何对阵绑定（轮空直进位但还没到轮次）的情况由槽位解析的退赛判定兜底。
        bumpVersion(bundle.tournament);
        writeRevision(bundle, REV_WALKOVER, operator, reason, null, autoFinished,
                Map.of("entrantId", entrantId, "entrantName", entrant.getDisplayName(),
                        "phaseBefore", phaseBefore, "autoWalkoverCount", autoFinished.size()));
        return saveAndView(bundle);
    }

    /**
     * 扫描所有未出结果的真实场次：一方在场、另一方不在场（退赛/双弃权上游）时，
     * 把在场一方记为不战而胜；双方均不在场时记双弃权（DOUBLE_WALKOVER）。
     * 反复扫描直到不再产生新结果（弃权会沿下游继续传导）。
     */
    private List<TournamentMatch> propagateWalkovers(TournamentBundle bundle, String operator, String trigger) {
        List<TournamentMatch> changed = new ArrayList<>();
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            // 每轮用最新结果重建解析器，保证新物化的结果立刻传导到下游
            BracketResolver resolver = bundle.resolver();
            List<TournamentMatch> pending = bundle.matches.stream()
                    .filter(m -> !m.getStructuralBye() && !MATCH_FINISHED.equals(m.getStatus()))
                    .sorted(Comparator.comparingInt((TournamentMatch m) -> BracketResolver.partOrder(m.getBracketPart()))
                            .thenComparingInt(TournamentMatch::getRoundNo)
                            .thenComparingInt(TournamentMatch::getSeqInRound))
                    .toList();
            for (TournamentMatch m : pending) {
                BracketResolver.Resolved ra = resolver.resolve(m.getSlotASource());
                BracketResolver.Resolved rb = resolver.resolve(m.getSlotBSource());
                boolean aLive = ra.present();
                boolean bLive = rb.present();
                if (aLive && bLive) {
                    continue;
                }
                // 任一方仍在等待上游结果，就绝不能提前判弃权——否则会沿链误判一整串
                if (BracketResolver.STATE_WAITING.equals(ra.state())
                        || BracketResolver.STATE_WAITING.equals(rb.state())) {
                    continue;
                }
                if (!aLive && !bLive) {
                    // 双方都确定不能出场（都已退赛，或来自双弃权）：双弃权
                    applyDoubleWalkover(bundle, m, operator);
                } else {
                    Long winnerId = aLive ? ra.entrantId() : rb.entrantId();
                    // 败者是不在场一方：若能解析到具体退赛参赛者就记到他名下，便于解释；
                    // 追溯不到（双弃权上游）时允许为空
                    Long loserId = loserOfAbsentSlot(bundle, m, aLive ? m.getSlotBSource() : m.getSlotASource());
                    applyOutcome(bundle, m, winnerId, loserId, WIN_WALKOVER, operator);
                }
                changed.add(m);
                progressed = true;
                break; // 重建解析器后继续下一轮
            }
        }
        return changed;
    }

    /** 双弃权：没有胜者也没有败者，winKind 标记后下游解析为 OUT */
    private void applyDoubleWalkover(TournamentBundle bundle, TournamentMatch m, String operator) {
        m.setWinnerEntrantId(null);
        m.setLoserEntrantId(null);
        m.setWinKind(WIN_DOUBLE_WALKOVER);
        m.setConfirmedBy(operator);
        m.setConfirmedAt(LocalDateTime.now());
        m.setStatus(MATCH_FINISHED);
    }

    /** 找出缺赛场槽对应的退赛参赛者 ID（直接是退赛种子，或上游某场的退赛败者）；找不到返回 null */
    private Long loserOfAbsentSlot(TournamentBundle bundle, TournamentMatch m, String source) {
        BracketResolver resolver = bundle.resolver();
        BracketResolver.Resolved r = resolver.resolve(source);
        if (r.entrantId() != null) {
            return r.entrantId();
        }
        return traceAbsentEntrant(bundle, source);
    }

    private Long traceAbsentEntrant(TournamentBundle bundle, String source) {
        if (source == null || "BYE".equals(source) || source.startsWith("SEED:")) {
            if (source != null && source.startsWith("SEED:")) {
                int seed = Integer.parseInt(source.substring(5));
                return bundle.entrants.stream().filter(e -> e.getSeedNo() == seed)
                        .map(TournamentEntrant::getId).findFirst().orElse(null);
            }
            return null;
        }
        boolean winnerEdge = source.startsWith("WIN:");
        String code = source.substring(winnerEdge ? 4 : 5);
        TournamentMatch up = bundle.byCode.get(code);
        if (up == null) {
            return null;
        }
        if (WIN_DOUBLE_WALKOVER.equals(up.getWinKind())) {
            // 双弃权没有具体败者，继续往上游两个槽找退赛者
            Long fromA = traceAbsentEntrant(bundle, up.getSlotASource());
            return fromA != null ? fromA : traceAbsentEntrant(bundle, up.getSlotBSource());
        }
        if (!winnerEdge && up.getLoserEntrantId() != null) {
            return up.getLoserEntrantId();
        }
        return null;
    }

    // ==================================================================
    // 受控回退与纠错重建
    // ==================================================================

    @Transactional
    public TournamentView rollback(Long tournamentId, RollbackRequest request) {
        TournamentBundle bundle = loadBundle(tournamentId);
        String operator = requireAdmin(request == null ? null : request.operatorName());
        String reason = request == null || request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty()) {
            throw new BizException("回退必须填写原因");
        }
        Long anchorId = request == null ? null : request.anchorMatchId();
        TournamentMatch anchor = requireMatch(bundle, anchorId);
        if (anchor.getStructuralBye()) {
            throw new BizException("轮空占位不是真实比赛，不能回退");
        }
        if (!MATCH_FINISHED.equals(anchor.getStatus())) {
            throw new BizException("「" + anchor.getMatchCode() + "」还没有确认结果，无需回退");
        }

        // 派生集合：锚点沿胜/败边可达的所有场次（含锚点，含可能存在的重置赛）
        BracketResolver resolver = bundle.resolver();
        List<String> downstreamCodes = resolver.downstreamCodes(anchor.getMatchCode());
        List<TournamentMatch> downstream = downstreamCodes.stream()
                .map(bundle.byCode::get).filter(java.util.Objects::nonNull)
                .filter(m -> !m.getStructuralBye()).toList();

        // 快照看板（落库前先抓）。受影响场次的 code 也必须在删除（重置赛）前抓住，
        // 否则流水里删除掉的重置赛只能显示成 ID 占位，破坏可追溯性。
        String snapshot = writeJson(snapshot(downstream));

        // 清空派生集合的结果与状态；重置赛（动态场次）直接删除
        List<TournamentMatch> toDelete = new ArrayList<>();
        List<TournamentMatch> toClear = new ArrayList<>();
        for (TournamentMatch m : downstream) {
            if ("GF-RESET".equals(m.getMatchCode())) {
                toDelete.add(m);
            } else {
                clearResult(m);
                toClear.add(m);
            }
        }
        if (!toDelete.isEmpty()) {
            matchRepository.deleteAll(toDelete);
            bundle.matches.removeAll(toDelete);
            for (TournamentMatch m : toDelete) {
                bundle.byCode.remove(m.getMatchCode());
                bundle.idIndex.remove(m.getId());
            }
        }

        // 回退后重算状态：退赛弃权链按当前结果重新物化（另一分支未清结果，原样保留）
        List<TournamentMatch> reAuto = propagateWalkovers(bundle, operator, REV_ROLLBACK);
        // 通常锚点本身在等人工更正，不应被自动重判；若它被弃权链重新物化，说明双方
        // 有已退赛者，这也是确定性结果，允许（但要记入受影响场次）
        bumpVersion(bundle.tournament);
        // 受影响场次以删除前抓到的 downstream 为准（含即将删除的重置赛），
        // 再并上回退后弃权链重新物化的场次
        List<TournamentMatch> affectedForAudit = new ArrayList<>(downstream);
        for (TournamentMatch m : reAuto) {
            if (affectedForAudit.stream().noneMatch(x -> x.getId().equals(m.getId()))) {
                affectedForAudit.add(m);
            }
        }
        writeRevision(bundle, REV_ROLLBACK, operator, reason, anchor,
                affectedForAudit, Map.of("action", "ROLLBACK", "clearedSnapshot", snapshot));
        return saveAndView(bundle);
    }

    @Transactional
    public TournamentView correctResult(Long tournamentId, Long matchId, CorrectResultRequest request) {
        TournamentBundle bundle = loadBundle(tournamentId);
        String operator = requireAdmin(request == null ? null : request.operatorName());
        String reason = request == null || request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty()) {
            throw new BizException("纠错必须填写原因");
        }
        Long winnerId = request == null ? null : request.winnerEntrantId();
        if (winnerId == null) {
            throw new BizException("请选择更正后的胜方");
        }
        TournamentMatch match = requireMatch(bundle, matchId);
        if (STATUS_FINISHED.equals(bundle.tournament.getStatus())) {
            throw new BizException("赛事已完赛产生冠军，不能再更正结果");
        }
        if (match.getStructuralBye()) {
            throw new BizException("轮空占位不能更正结果");
        }
        if (MATCH_FINISHED.equals(match.getStatus())) {
            // 已出结果的场次不能直接走更正：必须先管理员回退清空该场及派生结果
            throw new BizException("「" + match.getMatchCode()
                    + "」赛果已确认并已推进下游，请先由管理员回退该场，再在回退点提交更正结果");
        }
        // 强约束：更正只能作用于「已被管理员回退、正停在待更正」的场次
        if (!computePendingCorrectionIds(bundle.revisions).contains(match.getId())) {
            throw new BizException("「" + match.getMatchCode()
                    + "」尚未被回退，不是回退点；普通场次请直接确认赛果，需要改判时先由管理员回退");
        }
        BracketResolver resolver = bundle.resolver();
        BracketResolver.Resolved ra = resolver.resolve(match.getSlotASource());
        BracketResolver.Resolved rb = resolver.resolve(match.getSlotBSource());
        if (!ra.present() || !rb.present()) {
            throw new BizException("「" + match.getMatchCode() + "」双方尚未到齐，无法更正结果");
        }
        if (!winnerId.equals(ra.entrantId()) && !winnerId.equals(rb.entrantId())) {
            throw new BizException("更正后的胜方必须是本场两名参赛者之一");
        }
        Long loserId = winnerId.equals(ra.entrantId()) ? rb.entrantId() : ra.entrantId();

        String before = writeJson(Map.of("matchCode", match.getMatchCode(), "status", match.getStatus()));
        applyOutcome(bundle, match, winnerId, loserId, WIN_NORMAL, operator);
        List<TournamentMatch> autoFinished = propagateWalkovers(bundle, operator, REV_CORRECT);
        bumpVersion(bundle.tournament);
        List<TournamentMatch> affected = new ArrayList<>(List.of(match));
        autoFinished.stream().filter(m -> !m.getId().equals(match.getId())).forEach(affected::add);
        writeRevision(bundle, REV_CORRECT, operator, reason, match, affected,
                Map.of("action", "CORRECT", "winnerId", winnerId, "loserId", loserId, "before", before));
        return saveAndView(bundle);
    }

    private void clearResult(TournamentMatch m) {
        m.setWinnerEntrantId(null);
        m.setLoserEntrantId(null);
        m.setWinKind(null);
        m.setConfirmedBy(null);
        m.setConfirmedAt(null);
        m.setStatus(MATCH_WAITING);
    }

    // ==================================================================
    // 内部：装载、状态刷新、版本流水
    // ==================================================================

    private Tournament requireTournament(Long id) {
        if (id == null) {
            throw new BizException("缺少赛事 ID");
        }
        return tournamentRepository.findById(id).orElseThrow(() -> new BizException("赛事不存在，ID：" + id));
    }

    private TournamentMatch requireMatch(TournamentBundle bundle, Long matchId) {
        if (matchId == null) {
            throw new BizException("缺少场次 ID");
        }
        TournamentMatch m = bundle.idIndex.get(matchId);
        if (m == null) {
            throw new BizException("场次不存在，ID：" + matchId);
        }
        return m;
    }

    private String trimOperator(String operator) {
        if (operator == null || operator.trim().isEmpty()) {
            throw new BizException("操作人不能为空");
        }
        return operator.trim();
    }

    private String requireAdmin(String operator) {
        String name = trimOperator(operator);
        if (!name.contains(ADMIN_MARK)) {
            throw new BizException("回退 / 纠错属于受控操作，操作人必须为管理员（操作名包含「管理员」）");
        }
        return name;
    }

    private TournamentBundle loadBundle(Long tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        List<TournamentEntrant> entrants = entrantRepository.findByTournamentIdOrderBySeedNoAsc(tournamentId);
        List<TournamentMatch> matches = matchRepository.findByTournamentId(tournamentId);
        List<TournamentRevision> revisions = revisionRepository.findByTournamentIdOrderByIdAsc(tournamentId);
        return new TournamentBundle(tournament, entrants, matches, revisions);
    }

    private TournamentView saveAndView(TournamentBundle bundle) {
        // 保存前依据最新结果图重算全部场次状态与冠军（结果字段本身不动）
        refreshStatuses(bundle.tournament, bundle.matches, bundle.entrants);
        entrantRepository.saveAll(bundle.entrants);
        matchRepository.saveAll(bundle.matches);
        tournamentRepository.save(bundle.tournament);
        return buildView(bundle.tournament, bundle.entrants, bundle.matches,
                revisionRepository.findByTournamentIdOrderByIdAsc(bundle.tournament.getId()));
    }

    /** 依据解析结果重算所有非轮空场次的 WAITING/READY（结果字段不动） */
    private void refreshStatuses(Tournament tournament, List<TournamentMatch> matches,
                                 List<TournamentEntrant> entrants) {
        BracketResolver resolver = new BracketResolver(toRows(matches), toEntrantRows(entrants));
        for (TournamentMatch m : matches) {
            if (m.getStructuralBye()) {
                // 轮空不是比赛：没有状态机位置，保持 WAITING；视图与解析器单独识别 structuralBye
                continue;
            }
            if (MATCH_FINISHED.equals(m.getStatus())) {
                continue;
            }
            BracketResolver.Resolved a = resolver.resolve(m.getSlotASource());
            BracketResolver.Resolved b = resolver.resolve(m.getSlotBSource());
            boolean ready = a.present() && b.present();
            m.setStatus(ready ? MATCH_READY : MATCH_WAITING);
        }
        recomputeChampion(tournament, matches, resolver);
    }

    private void recomputeChampion(Tournament tournament, List<TournamentMatch> matches, BracketResolver resolver) {
        TournamentMatch reset = matches.stream().filter(m -> "GF-RESET".equals(m.getMatchCode())).findFirst().orElse(null);
        if (reset != null && MATCH_FINISHED.equals(reset.getStatus())
                && !WIN_DOUBLE_WALKOVER.equals(reset.getWinKind()) && reset.getWinnerEntrantId() != null) {
            tournament.setChampionId(reset.getWinnerEntrantId());
            tournament.setStatus(STATUS_FINISHED);
            return;
        }
        TournamentMatch gf = matches.stream().filter(m -> "GF".equals(m.getMatchCode())).findFirst().orElse(null);
        // 没有重置赛且总决赛已出结果：只有胜者组冠军（A 槽零败者）获胜才直接夺冠；
        // 败者组冠军获胜时会先生成重置赛，GF 已 FINISHED 但 reset 存在且未完 => 未完赛
        if (reset == null && gf != null && MATCH_FINISHED.equals(gf.getStatus())
                && !WIN_DOUBLE_WALKOVER.equals(gf.getWinKind()) && gf.getWinnerEntrantId() != null) {
            BracketResolver.Resolved wbChamp = resolver.resolve(gf.getSlotASource());
            if (gf.getWinnerEntrantId().equals(wbChamp.entrantId())) {
                tournament.setChampionId(gf.getWinnerEntrantId());
                tournament.setStatus(STATUS_FINISHED);
                return;
            }
        }
        // 双弃权极端场景：总决赛/重置赛双方都不能出场，无冠军可产生，保持 LIVE 但所有真实场次已 FINISHED
        tournament.setChampionId(null);
        tournament.setStatus(STATUS_LIVE);
    }

    private void bumpVersion(Tournament tournament) {
        tournament.setCurrentVersion((tournament.getCurrentVersion() == null ? 0 : tournament.getCurrentVersion()) + 1);
    }

    private void writeRevision(TournamentBundle bundle, String type, String operator, String reason,
                               TournamentMatch anchor, List<TournamentMatch> affected, Map<String, Object> detail) {
        TournamentRevision revision = new TournamentRevision();
        revision.setTournamentId(bundle.tournament.getId());
        revision.setVersionNo(bundle.tournament.getCurrentVersion());
        revision.setChangeType(type);
        revision.setOperatorName(operator);
        revision.setReason(reason);
        if (anchor != null) {
            revision.setAnchorMatchId(anchor.getId());
        }
        revision.setAffectedMatchIds(affected.stream().map(m -> String.valueOf(m.getId()))
                .reduce((a, b) -> a + "," + b).orElse(""));
        Map<String, Object> full = new LinkedHashMap<>(detail);
        full.put("affectedCodes", affected.stream().map(TournamentMatch::getMatchCode).toList());
        revision.setDetailJson(writeJson(full));
        revision = revisionRepository.save(revision);
        bundle.revisions.add(revision);
    }

    private List<Map<String, Object>> snapshot(List<TournamentMatch> matches) {
        return matches.stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("matchCode", m.getMatchCode());
            row.put("status", m.getStatus());
            row.put("winnerId", m.getWinnerEntrantId());
            row.put("loserId", m.getLoserEntrantId());
            row.put("winKind", m.getWinKind());
            row.put("confirmedBy", m.getConfirmedBy());
            return row;
        }).toList();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    // ==================================================================
    // 视图装配
    // ==================================================================

    private TournamentView buildView(Tournament tournament, List<TournamentEntrant> entrants,
                                     List<TournamentMatch> matches, List<TournamentRevision> revisions) {
        BracketResolver resolver = new BracketResolver(toRows(matches), toEntrantRows(entrants));

        List<TournamentMatch> ordered = new ArrayList<>(matches);
        ordered.sort(Comparator.comparingInt((TournamentMatch m) -> BracketResolver.partOrder(m.getBracketPart()))
                .thenComparingInt(TournamentMatch::getRoundNo)
                .thenComparingInt(m -> "MINOR".equals(m.getRoundStage()) ? 0 : "MAJOR".equals(m.getRoundStage()) ? 1 : 2)
                .thenComparingInt(TournamentMatch::getSeqInRound));

        Map<Long, EntrantView> entrantViews = new LinkedHashMap<>();
        for (TournamentEntrant e : entrants) {
            entrantViews.put(e.getId(), toEntrantView(e, resolver, tournament));
        }

        // 「待更正」锚点：按流水顺序回放——回退把锚点置入待更正；
        // 之后任何触及该场的操作（普通确认 / 更正 / 弃权）都把它移出。
        java.util.Set<Long> pendingCorrectionIds = computePendingCorrectionIds(revisions);

        List<MatchView> matchViews = ordered.stream()
                .map(m -> toMatchView(m, resolver, entrantViews, pendingCorrectionIds)).toList();
        List<RoundView> rounds = buildRounds(ordered);
        List<MatchView> ready = matchViews.stream().filter(mv -> Boolean.TRUE.equals(mv.canConfirm())).toList();

        EntrantView champion = tournament.getChampionId() == null ? null
                : entrantViews.get(tournament.getChampionId());
        String resetCode = matches.stream().anyMatch(m -> "GF-RESET".equals(m.getMatchCode())) ? "GF-RESET" : null;

        List<RevisionView> revisionViews = revisions.stream()
                .map(r -> toRevisionView(r, matches)).toList();

        return new TournamentView(tournament.getId(), tournament.getTournamentName(),
                tournament.getEntrantCount(), tournament.getBracketSize(), tournament.getByeCount(),
                tournament.getStatus(), statusName(tournament.getStatus()),
                tournament.getChampionId(), champion, tournament.getCurrentVersion(),
                tournament.getCreatedBy(), tournament.getCreatedAt(),
                List.copyOf(entrantViews.values()), rounds, matchViews, revisionViews, ready, resetCode);
    }

    /**
     * 按纠错流水回放「待更正锚点」：ROLLBACK 把锚点置入；之后该锚点若出现
     * 更正（CORRECT）、普通确认（RESULT）或弃权（WALKOVER）结果，则移出。
     */
    private java.util.Set<Long> computePendingCorrectionIds(List<TournamentRevision> revisions) {
        java.util.Set<Long> pending = new java.util.HashSet<>();
        for (TournamentRevision r : revisions) {
            if (REV_ROLLBACK.equals(r.getChangeType()) && r.getAnchorMatchId() != null) {
                pending.add(r.getAnchorMatchId());
                continue;
            }
            if (r.getAnchorMatchId() != null
                    && (REV_CORRECT.equals(r.getChangeType()) || REV_RESULT.equals(r.getChangeType()))) {
                pending.remove(r.getAnchorMatchId());
            }
            if (REV_WALKOVER.equals(r.getChangeType()) && r.getAffectedMatchIds() != null) {
                for (String token : r.getAffectedMatchIds().split(",")) {
                    if (!token.isBlank()) {
                        pending.remove(Long.valueOf(token.trim()));
                    }
                }
            }
        }
        return pending;
    }

    private List<RoundView> buildRounds(List<TournamentMatch> ordered) {        // 按 part+round+stage 归并成列，保持 ordered 的出现顺序
        Map<String, List<String>> columns = new LinkedHashMap<>();
        for (TournamentMatch m : ordered) {
            String stage = m.getRoundStage();
            String key = m.getBracketPart() + "|" + m.getRoundNo() + "|" + stage;
            columns.computeIfAbsent(key, k -> new ArrayList<>()).add(m.getMatchCode());
        }
        List<RoundView> rounds = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : columns.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            String part = parts[0];
            int roundNo = Integer.parseInt(parts[1]);
            String stage = parts[2];
            rounds.add(new RoundView(entry.getKey(), part, bracketPartName(part), roundNo, stage,
                    roundTitle(part, roundNo, stage), entry.getValue()));
        }
        return rounds;
    }

    private EntrantView toEntrantView(TournamentEntrant e, BracketResolver resolver, Tournament tournament) {
        int losses = resolver.lossesOf(e.getId());
        boolean played = resolver.hasPlayed(e.getId());
        boolean withdrawn = Boolean.TRUE.equals(e.getWithdrawn());
        String progress;
        if (e.getId().equals(tournament.getChampionId())) {
            progress = "CHAMPION";
        } else if (withdrawn) {
            progress = played ? (losses >= 2 ? "OUT" : losses == 1 ? "LOSERS" : "WINNERS") : "WITHDRAWN_IDLE";
        } else {
            progress = losses >= 2 ? "OUT" : losses == 1 ? "LOSERS" : "WINNERS";
        }
        return new EntrantView(e.getId(), e.getMemberId(), e.getDisplayName(), e.getEntrantKind(),
                KIND_MEMBER.equals(e.getEntrantKind()) ? "会员" : "临时参赛者",
                e.getSeedNo(), withdrawn, e.getWithdrawReason(), e.getWithdrewAt(),
                losses, progress, progressName(progress, played), played);
    }

    private MatchView toMatchView(TournamentMatch m, BracketResolver resolver,
                                  Map<Long, EntrantView> entrantViews,
                                  java.util.Set<Long> pendingCorrectionIds) {
        SlotView a = toSlotView(m.getSlotASource(), resolver, entrantViews);
        SlotView b = toSlotView(m.getSlotBSource(), resolver, entrantViews);
        boolean derivedFinished = false;
        // 是否已有下游长出结果（用于页面提示为什么不能普通改）
        if (MATCH_FINISHED.equals(m.getStatus())) {
            derivedFinished = hasFinishedDescendant(m, resolver);
        }
        boolean canConfirm = !m.getStructuralBye() && MATCH_READY.equals(m.getStatus());
        // 可确认状态以实时解析为准（READY 落库可能滞后）
        if (!m.getStructuralBye() && !MATCH_FINISHED.equals(m.getStatus())) {
            canConfirm = a.resolvedState().equals(BracketResolver.STATE_PRESENT)
                    && b.resolvedState().equals(BracketResolver.STATE_PRESENT);
        }
        // 管理员回退过、且当前双方已到齐等待重新出结果 => 待更正入口
        boolean pendingCorrection = !m.getStructuralBye()
                && pendingCorrectionIds.contains(m.getId()) && canConfirm;
        return new MatchView(m.getId(), m.getMatchCode(), m.getBracketPart(),
                bracketPartName(m.getBracketPart()), m.getRoundNo(), m.getRoundStage(),
                roundTitle(m.getBracketPart(), m.getRoundNo(), m.getRoundStage()),
                m.getSeqInRound(), a, b, targetText(m.getNextWin()), targetText(m.getNextLose()),
                m.getStructuralBye(), m.getStatus(), matchStatusName(m),
                m.getWinnerEntrantId(), m.getLoserEntrantId(), m.getWinKind(), winKindName(m.getWinKind()),
                m.getConfirmedBy(), m.getConfirmedAt(), canConfirm, pendingCorrection, derivedFinished);
    }

    private boolean hasFinishedDescendant(TournamentMatch m, BracketResolver resolver) {
        for (String code : resolver.downstreamCodes(m.getMatchCode())) {
            if (code.equals(m.getMatchCode())) {
                continue;
            }
            BracketResolver.MatchRow row = resolver.match(code);
            if (row != null && row.finished() && !"GF".equals(code)) {
                return true;
            }
        }
        return false;
    }

    private SlotView toSlotView(String source, BracketResolver resolver,
                                Map<Long, EntrantView> entrantViews) {
        String kind;
        Integer seedNo = null;
        String sourceMatchCode = null;
        String sourceSide = null;
        String sourceText;
        if (source == null || "BYE".equals(source)) {
            kind = "BYE";
            sourceText = "轮空";
        } else if (source.startsWith("SEED:")) {
            kind = "SEED";
            seedNo = Integer.parseInt(source.substring(5));
            sourceText = seedNo + " 号种子";
        } else {
            boolean win = source.startsWith("WIN:");
            kind = win ? "WIN" : "LOSE";
            sourceMatchCode = source.substring(win ? 4 : 5);
            sourceSide = win ? "胜者" : "败者";
            sourceText = sourceMatchCode + " 的" + (win ? "胜者" : "败者");
        }
        BracketResolver.Resolved r = resolver.resolve(source);
        Long entrantId = r.entrantId();
        String entrantName = entrantId == null ? null
                : entrantViews.containsKey(entrantId) ? entrantViews.get(entrantId).displayName() : null;
        String state;
        if (source == null || "BYE".equals(source)) {
            state = BracketResolver.STATE_BYE;
        } else if (r.state().equals(BracketResolver.STATE_WAITING)) {
            state = BracketResolver.STATE_WAITING;
        } else if (r.state().equals(BracketResolver.STATE_OUT)) {
            state = BracketResolver.STATE_OUT;
        } else {
            state = BracketResolver.STATE_PRESENT;
        }
        return new SlotView(kind, sourceText, sourceMatchCode, sourceSide, seedNo,
                entrantId, entrantName, state, slotStateName(state));
    }

    private RevisionView toRevisionView(TournamentRevision r, List<TournamentMatch> matches) {
        Map<Long, String> codeById = new HashMap<>();
        for (TournamentMatch m : matches) {
            codeById.put(m.getId(), m.getMatchCode());
        }
        List<Long> ids = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        if (r.getAffectedMatchIds() != null && !r.getAffectedMatchIds().isBlank()) {
            for (String token : r.getAffectedMatchIds().split(",")) {
                if (token.isBlank()) {
                    continue;
                }
                Long id = Long.valueOf(token);
                ids.add(id);
            }
        }
        // 受影响场次 code 以落库快照 detailJson.affectedCodes 为准——
        // 回退删除的重置赛已不在现存场次里，但快照仍能给出 GF-RESET，保证可追溯
        List<String> snapshotCodes = readStringList(r.getDetailJson(), "affectedCodes");
        if (!snapshotCodes.isEmpty()) {
            codes.addAll(snapshotCodes);
        } else {
            ids.forEach(id -> codes.add(codeById.getOrDefault(id, "场次 #" + id)));
        }
        String anchorCode = r.getAnchorMatchId() == null ? null
                : codeById.getOrDefault(r.getAnchorMatchId(),
                        snapshotCodes.isEmpty() ? "场次 #" + r.getAnchorMatchId()
                                : readAnchorFromSnapshot(r.getDetailJson(), r.getAnchorMatchId(), codeById));
        return new RevisionView(r.getId(), r.getVersionNo(), r.getChangeType(),
                changeTypeName(r.getChangeType()), r.getOperatorName(), r.getReason(),
                r.getAnchorMatchId(), anchorCode, ids, codes, r.getCreatedAt());
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringList(String json, String field) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            Object value = map.get(field);
            if (value instanceof List<?> list) {
                List<String> result = new ArrayList<>();
                for (Object o : list) {
                    result.add(String.valueOf(o));
                }
                return result;
            }
        } catch (Exception ignored) {
            // 快照解析失败不影响主流程
        }
        return List.of();
    }

    private String readAnchorFromSnapshot(String json, Long anchorId, Map<Long, String> codeById) {
        return codeById.getOrDefault(anchorId, "场次 #" + anchorId);
    }

    private String targetText(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        int hash = target.indexOf('#');
        String code = target.substring(0, hash);
        String side = target.substring(hash + 1);
        return code + " " + ("A".equals(side) ? "上槽" : "下槽");
    }

    // ==================================================================
    // 行转换 + 文案
    // ==================================================================

    private List<BracketResolver.MatchRow> toRows(List<TournamentMatch> matches) {
        return matches.stream().map(m -> new BracketResolver.MatchRow(
                m.getId(), m.getMatchCode(), m.getBracketPart(), m.getRoundNo(), m.getRoundStage(),
                m.getSeqInRound(), m.getSlotASource(), m.getSlotBSource(), m.getNextWin(), m.getNextLose(),
                Boolean.TRUE.equals(m.getStructuralBye()), m.getStatus(),
                m.getWinnerEntrantId(), m.getLoserEntrantId(), m.getWinKind())).toList();
    }

    private List<BracketResolver.EntrantRow> toEntrantRows(List<TournamentEntrant> entrants) {
        return entrants.stream().map(e -> new BracketResolver.EntrantRow(
                e.getId(), e.getSeedNo(), e.getDisplayName(), Boolean.TRUE.equals(e.getWithdrawn()))).toList();
    }

    private String statusName(String status) {
        return STATUS_FINISHED.equals(status) ? "已完赛" : "进行中";
    }

    private String bracketPartName(String part) {
        return "WB".equals(part) ? "胜者组" : "LB".equals(part) ? "败者组" : "总决赛";
    }

    private String roundTitle(String part, int roundNo, String stage) {
        if ("GF".equals(part)) {
            return roundNo >= 2 ? "重置赛" : "总决赛";
        }
        if ("FINAL".equals(stage)) {
            return "WB".equals(part) ? "胜者组决赛" : "败者组决赛";
        }
        String prefix = "WB".equals(part) ? "胜者组" : "败者组";
        String kind = "MINOR".equals(stage) ? "小轮" : "轮";
        return prefix + "第 " + roundNo + " " + kind;
    }

    private String matchStatusName(TournamentMatch m) {
        if (m.getStructuralBye()) {
            return "轮空（自动晋级）";
        }
        return switch (m.getStatus()) {
            case MATCH_FINISHED -> WIN_DOUBLE_WALKOVER.equals(m.getWinKind()) ? "双方弃权" : "已出结果";
            case MATCH_READY -> "可开打";
            default -> "待上场";
        };
    }

    private String winKindName(String winKind) {
        if (WIN_WALKOVER.equals(winKind)) {
            return "对手退赛，不战而胜";
        }
        if (WIN_DOUBLE_WALKOVER.equals(winKind)) {
            return "双方均退赛";
        }
        if (WIN_NORMAL.equals(winKind)) {
            return "正常完赛";
        }
        return null;
    }

    private String slotStateName(String state) {
        return switch (state) {
            case BracketResolver.STATE_PRESENT -> "已到场";
            case BracketResolver.STATE_WAITING -> "等待上游";
            case BracketResolver.STATE_OUT -> "已退赛/出局";
            default -> "轮空";
        };
    }

    private String changeTypeName(String type) {
        return switch (type) {
            case REV_RESULT -> "赛果确认";
            case REV_WALKOVER -> "退赛弃权";
            case REV_ROLLBACK -> "管理员回退";
            case REV_CORRECT -> "管理员更正";
            default -> type;
        };
    }

    private String progressName(String progress, boolean played) {
        return switch (progress) {
            case "CHAMPION" -> "冠军";
            case "OUT" -> "已淘汰（二败）";
            case "LOSERS" -> "败者组（一败）";
            case "WINNERS" -> "胜者组（零败）";
            case "WITHDRAWN_IDLE" -> "已退赛（尚未出场）";
            default -> progress;
        };
    }

    // ==================================================================
    // 内存工作包
    // ==================================================================

    private final class TournamentBundle {
        final Tournament tournament;
        final List<TournamentEntrant> entrants;
        final List<TournamentMatch> matches;
        final List<TournamentRevision> revisions;
        final Map<String, TournamentMatch> byCode = new HashMap<>();
        final Map<Long, TournamentMatch> idIndex = new HashMap<>();

        TournamentBundle(Tournament tournament, List<TournamentEntrant> entrants,
                         List<TournamentMatch> matches, List<TournamentRevision> revisions) {
            this.tournament = tournament;
            this.entrants = entrants;
            this.matches = new ArrayList<>(matches);
            this.revisions = new ArrayList<>(revisions);
            for (TournamentMatch m : this.matches) {
                byCode.put(m.getMatchCode(), m);
                idIndex.put(m.getId(), m);
            }
        }

        BracketResolver resolver() {
            return new BracketResolver(toRows(matches), toEntrantRows(entrants));
        }
    }
}
