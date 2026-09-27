package com.billiard.club.service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billiard.club.common.BizException;
import com.billiard.club.dict.TournamentDict;
import com.billiard.club.domain.Member;
import com.billiard.club.domain.MemberRepository;
import com.billiard.club.domain.Tournament;
import com.billiard.club.domain.TournamentEntry;
import com.billiard.club.domain.TournamentEntryRepository;
import com.billiard.club.domain.TournamentMatch;
import com.billiard.club.domain.TournamentMatchRepository;
import com.billiard.club.domain.TournamentRepository;
import com.billiard.club.domain.TournamentRevision;
import com.billiard.club.domain.TournamentRevisionMatch;
import com.billiard.club.domain.TournamentRevisionMatchRepository;
import com.billiard.club.domain.TournamentRevisionRepository;
import com.billiard.club.dto.MatchResultRequest;
import com.billiard.club.dto.RollbackRequest;
import com.billiard.club.dto.TournamentCreateRequest;
import com.billiard.club.dto.WithdrawRequest;
import com.billiard.club.dto.view.EntryView;
import com.billiard.club.dto.view.MatchView;
import com.billiard.club.dto.view.RevisionMatchView;
import com.billiard.club.dto.view.RevisionView;
import com.billiard.club.dto.view.TournamentDetailView;
import com.billiard.club.dto.view.TournamentListItemView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 双败淘汰赛事服务。
 *
 * 括号推进的核心是 {@link #recompute}：从受影响场次出发，沿 winner/loser 两条边
 * 自上游向下游重算每个未结束场次的入位与状态，自动消化轮空（BYE）、
 * 退赛直接晋级（WALKOVER）、双方退赛作废（VOID）。创建括号、赛果确认、退赛、
 * 回退重建都走这同一个确定性过程，保证任意时刻刷新页面，括号、比赛列表与冠军一致。
 */
@Service
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TournamentEntryRepository entryRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentRevisionRepository revisionRepository;
    private final TournamentRevisionMatchRepository revisionMatchRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    public TournamentService(TournamentRepository tournamentRepository,
                             TournamentEntryRepository entryRepository,
                             TournamentMatchRepository matchRepository,
                             TournamentRevisionRepository revisionRepository,
                             TournamentRevisionMatchRepository revisionMatchRepository,
                             MemberRepository memberRepository,
                             ObjectMapper objectMapper) {
        this.tournamentRepository = tournamentRepository;
        this.entryRepository = entryRepository;
        this.matchRepository = matchRepository;
        this.revisionRepository = revisionRepository;
        this.revisionMatchRepository = revisionMatchRepository;
        this.memberRepository = memberRepository;
        this.objectMapper = objectMapper;
    }

    // =====================================================================
    // 查询
    // =====================================================================

    @Transactional(readOnly = true)
    public List<TournamentListItemView> list() {
        return tournamentRepository.findAllByOrderByIdDesc().stream().map(t -> toListItem(t, null)).toList();
    }

    @Transactional(readOnly = true)
    public TournamentDetailView detail(Long id) {
        Tournament tournament = requireTournament(id);
        List<TournamentEntry> entries = entryRepository.findByTournamentIdOrderBySeedNoAsc(id);
        List<TournamentMatch> matches = matches(id);
        return toDetail(tournament, entries, matches);
    }

    @Transactional(readOnly = true)
    public List<RevisionView> revisions(Long tournamentId) {
        requireTournament(tournamentId);
        List<RevisionView> result = new ArrayList<>();
        Map<Long, TournamentEntry> entryMap = entryMap(tournamentId);
        for (TournamentRevision revision : revisionRepository.findByTournamentIdOrderByIdDesc(tournamentId)) {
            List<TournamentRevisionMatch> rows =
                    revisionMatchRepository.findByRevisionIdOrderByIdAsc(revision.getId());
            result.add(new RevisionView(
                    revision.getId(),
                    revision.getRootMatchId(),
                    matchNoById(rows, revision.getRootMatchId()),
                    revision.getOperator(),
                    revision.getReason(),
                    revision.getCorrectedWinnerEntryId(),
                    revision.getCorrectedWinnerEntryId() == null
                            ? null
                            : entryName(entryMap, revision.getCorrectedWinnerEntryId()),
                    revision.getCreatedAt(),
                    rows.stream().map(this::toRevisionMatchView).toList()));
        }
        return result;
    }

    // =====================================================================
    // 创建赛事 + 生成完整括号
    // =====================================================================

    @Transactional
    public TournamentDetailView create(TournamentCreateRequest request) {
        if (request == null || request.entries() == null) {
            throw new BizException("请求体与参赛者名单不能为空");
        }
        List<TournamentCreateRequest.EntrySeed> seeds = request.entries();
        if (seeds.size() < 4 || seeds.size() > 64) {
            throw new BizException("双败赛事开赛人数需在 4 ~ 64 人之间");
        }

        Set<Long> usedMemberIds = new HashSet<>();
        Set<String> usedNames = new HashSet<>();
        List<TournamentEntry> entries = new ArrayList<>();
        for (int i = 0; i < seeds.size(); i++) {
            int seedIndex = i + 1;
            TournamentCreateRequest.EntrySeed seed = seeds.get(i);
            TournamentEntry entry = new TournamentEntry();
            entry.setSeedNo(seedIndex);
            entry.setLossCount(0);
            entry.setStatus(TournamentDict.ENTRY_ACTIVE);

            if (seed.memberId() != null) {
                Member member = memberRepository.findById(seed.memberId())
                        .orElseThrow(() -> new BizException("第 " + seedIndex + " 号种子选择的会员不存在"));
                if (!usedMemberIds.add(member.getId())) {
                    throw new BizException("同一会员不能在一场赛事里重复参赛：" + member.getName());
                }
                entry.setMemberId(member.getId());
                entry.setEntryType(TournamentDict.ENTRY_MEMBER);
                entry.setDisplayName(member.getName());
            } else {
                String name = seed.guestName() == null ? "" : seed.guestName().trim();
                if (name.isEmpty()) {
                    throw new BizException("第 " + seedIndex + " 号种子是临时参赛者，必须填写姓名");
                }
                if (name.length() > 64) {
                    throw new BizException("临时参赛者姓名最长 64 字：" + name);
                }
                entry.setMemberId(null);
                entry.setEntryType(TournamentDict.ENTRY_GUEST);
                entry.setDisplayName(name);
            }
            if (!usedNames.add(entry.getDisplayName())) {
                throw new BizException("参赛者姓名不能重复（会员与临时参赛者之间也会去重）："
                        + entry.getDisplayName());
            }
            entries.add(entry);
        }

        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new BizException("赛事名称不能为空");
        }

        Tournament tournament = new Tournament();
        tournament.setName(name);
        tournament.setStatus(TournamentDict.TOURNAMENT_RUNNING);
        tournament.setPlayerCount(entries.size());
        tournament.setBracketSize(bracketSize(entries.size()));
        tournament.setTournamentNo(nextTournamentNo());
        tournament = tournamentRepository.save(tournament);

        for (TournamentEntry entry : entries) {
            entry.setTournamentId(tournament.getId());
        }
        entries = entryRepository.saveAll(entries);

        buildBracket(tournament, entries);
        return detail(tournament.getId());
    }

    /** 依据 BracketGenerator 规划落库全部场次，并跑一次全量推进消化首轮轮空。 */
    private void buildBracket(Tournament tournament, List<TournamentEntry> entries) {
        List<Long> idsBySeed = entries.stream().map(TournamentEntry::getId).toList();
        List<BracketGenerator.Node> plan = BracketGenerator.generate(idsBySeed);

        Map<String, TournamentMatch> byKey = new LinkedHashMap<>();
        List<TournamentMatch> saved = new ArrayList<>();
        for (BracketGenerator.Node node : plan) {
            TournamentMatch match = new TournamentMatch();
            match.setTournamentId(tournament.getId());
            match.setMatchNo(node.matchNo);
            match.setStage(node.stage);
            match.setRoundNo(node.round);
            match.setSeq(node.seq);
            match.setSideAEntryId(node.sideAEntryId);
            match.setSideBEntryId(node.sideBEntryId);
            match.setSideASource(node.sideASource);
            match.setSideBSource(node.sideBSource);
            match.setWinnerNextSlot(node.winnerNextSlot);
            match.setLoserNextSlot(node.loserNextSlot);
            match.setStatus(TournamentDict.MATCH_PENDING);
            byKey.put(node.key, match);
            saved.add(match);
        }
        saved = matchRepository.saveAll(saved);

        for (int i = 0; i < plan.size(); i++) {
            BracketGenerator.Node node = plan.get(i);
            TournamentMatch match = saved.get(i);
            if (node.winnerNextKey != null) {
                match.setWinnerNextMatchId(byKey.get(node.winnerNextKey).getId());
            }
            if (node.loserNextKey != null) {
                match.setLoserNextMatchId(byKey.get(node.loserNextKey).getId());
            }
        }
        matchRepository.saveAll(saved);

        // 全量推进：首轮轮空直入、败者组结构性轮空在此一次性自动消化
        recompute(tournament, saved,
                new ArrayDeque<>(saved), "系统", "按种子顺序自动安排轮空");
    }

    // =====================================================================
    // 确认赛果
    // =====================================================================

    @Transactional
    public TournamentDetailView confirmResult(Long matchId, MatchResultRequest request) {
        if (request == null || request.winnerEntryId() == null) {
            throw new BizException("必须指定获胜选手");
        }
        TournamentMatch match = requireMatch(matchId);
        Tournament tournament = requireTournament(match.getTournamentId());
        if (TournamentDict.TOURNAMENT_FINISHED.equals(tournament.getStatus())
                && !isResetMatch(match)) {
            throw new BizException("赛事已结束，不能再确认赛果");
        }
        if (TournamentDict.MATCH_FINISHED.equals(match.getStatus())
                || TournamentDict.MATCH_VOID.equals(match.getStatus())) {
            throw new BizException("「" + match.getMatchNo()
                    + "」已经产生下游对阵，赛果不能被普通操作直接修订，请管理员走「回退纠错」");
        }
        if (match.getSideAEntryId() == null || match.getSideBEntryId() == null) {
            throw new BizException("「" + match.getMatchNo() + "」双方选手尚未到齐，不能确认赛果");
        }
        Long winnerId = request.winnerEntryId();
        if (!winnerId.equals(match.getSideAEntryId()) && !winnerId.equals(match.getSideBEntryId())) {
            throw new BizException("胜者必须是本场已到齐的两名选手之一");
        }
        Map<Long, TournamentEntry> entries = entryMap(tournament.getId());
        if (!TournamentDict.ENTRY_ACTIVE.equals(entries.get(winnerId).getStatus())) {
            throw new BizException("退赛选手不能作为胜者");
        }

        // 正常确认（双败规则推进；必要时动态生成重置赛）
        finishNormal(tournament, match, winnerId, request.operator(), request.note(), entries);

        // 重新加载：重置赛可能在 finishNormal 中刚被创建，必须让重算引擎看到新边与新场次
        List<TournamentMatch> all = matches(tournament.getId());
        match = byId(all).get(matchId);
        Deque<TournamentMatch> queue = downstreamQueue(match, all);
        recompute(tournament, all, queue, request.operator(), null);

        tournamentRepository.save(tournament);
        return detail(tournament.getId());
    }

    // =====================================================================
    // 中途退赛
    // =====================================================================

    @Transactional
    public TournamentDetailView withdraw(Long tournamentId, Long entryId, WithdrawRequest request) {
        if (request == null || request.operator() == null || request.operator().isBlank()) {
            throw new BizException("退赛登记必须填写操作人");
        }
        Tournament tournament = requireTournament(tournamentId);
        if (TournamentDict.TOURNAMENT_FINISHED.equals(tournament.getStatus())) {
            throw new BizException("赛事已结束，不能再登记退赛");
        }
        TournamentEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new BizException("参赛者不存在"));
        if (!entry.getTournamentId().equals(tournamentId)) {
            throw new BizException("该参赛者不属于本场赛事");
        }
        if (TournamentDict.ENTRY_WITHDRAWN.equals(entry.getStatus())) {
            throw new BizException("「" + entry.getDisplayName() + "」已登记退赛，不能重复操作");
        }
        if (TournamentDict.ENTRY_ELIMINATED.equals(entry.getStatus())) {
            throw new BizException("「" + entry.getDisplayName()
                    + "」已经两败出局，出局与退赛分开记录，不能再登记退赛");
        }

        // 未出场（lossCount=0）/ 已一败（lossCount=1）只置退赛状态，不折算失利次数
        String stage = entry.getLossCount() == 0 ? "尚未出场" : "已有一败";
        String note = (request.note() == null || request.note().isBlank()
                ? "选手中途退赛" : request.note().trim());
        entry.setStatus(TournamentDict.ENTRY_WITHDRAWN);
        entry.setWithdrawNote(note + "（操作人：" + request.operator().trim() + "，" + stage + "）");
        entryRepository.save(entry);

        // 从该选手仍占用的所有未结束场次开始重算：WALKOVER/VOID 自动消化、晋级链保持可解释
        List<TournamentMatch> all = matches(tournamentId);
        Set<Long> startIds = new HashSet<>();
        for (TournamentMatch m : all) {
            if (!TournamentDict.MATCH_FINISHED.equals(m.getStatus())
                    && !TournamentDict.MATCH_VOID.equals(m.getStatus())
                    && (entryId.equals(m.getSideAEntryId()) || entryId.equals(m.getSideBEntryId()))) {
                startIds.add(m.getId());
            }
        }
        Deque<TournamentMatch> queue = new ArrayDeque<>();
        Map<Long, TournamentMatch> byId = byId(all);
        for (Long id : startIds) {
            queue.add(byId.get(id));
        }
        recompute(tournament, all, queue, request.operator(), note);
        tournamentRepository.save(tournament);
        return detail(tournamentId);
    }

    // =====================================================================
    // 受控回退 + 确定性重建
    // =====================================================================

    @Transactional
    public TournamentDetailView rollback(Long matchId, RollbackRequest request) {
        if (request == null || request.operator() == null || request.operator().isBlank()) {
            throw new BizException("回退纠错必须填写操作人");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BizException("回退纠错必须说明原因");
        }
        TournamentMatch root = requireMatch(matchId);
        Tournament tournament = requireTournament(root.getTournamentId());
        if (!TournamentDict.MATCH_FINISHED.equals(root.getStatus())) {
            throw new BizException("只能从一场已结束的场次回退：「" + root.getMatchNo() + "」当前为"
                    + TournamentDict.matchStatusName(root.getStatus()));
        }
        if (TournamentDict.RESULT_BYE.equals(root.getResultType())) {
            throw new BizException("轮空不是真实比赛，不能回退");
        }

        List<TournamentMatch> all = matches(tournament.getId());
        Map<Long, TournamentEntry> entries = entryMap(tournament.getId());

        Long correctedWinner = request.correctedWinnerEntryId();
        if (correctedWinner != null) {
            boolean valid = correctedWinner.equals(root.getSideAEntryId())
                    || correctedWinner.equals(root.getSideBEntryId());
            if (!valid) {
                throw new BizException("更正后的胜者必须是「" + root.getMatchNo() + "」的两名选手之一");
            }
            if (TournamentDict.ENTRY_WITHDRAWN.equals(entries.get(correctedWinner).getStatus())) {
                throw new BizException("更正后的胜者已退赛，不能设为胜者");
            }
        }

        // 1) 沿两条边求派生对阵集合（含动态生成的重置赛），另一分支不在集合内、原样保留
        List<TournamentMatch> descendants = descendants(root, all);

        // 2) 纠错前快照 + 版本记录
        TournamentRevision revision = new TournamentRevision();
        revision.setTournamentId(tournament.getId());
        revision.setRootMatchId(root.getId());
        revision.setOperator(request.operator().trim());
        revision.setReason(request.reason().trim());
        revision.setCorrectedWinnerEntryId(correctedWinner);
        revision = revisionRepository.save(revision);

        List<TournamentRevisionMatch> auditRows = new ArrayList<>();
        auditRows.add(auditRow(revision, root, "ROOT", snapshot(root)));
        for (TournamentMatch m : descendants) {
            String changeType = isResetMatch(m) ? "REMOVED"
                    : TournamentDict.MATCH_FINISHED.equals(m.getStatus()) ? "RESET" : "CLEARED";
            auditRows.add(auditRow(revision, m, changeType, snapshot(m)));
        }

        // 3) 回退派生对阵：清空入位与结果，恢复因正常失利累加的败绩
        Set<Long> grIdsToRemove = new HashSet<>();
        for (TournamentMatch m : descendants) {
            if (isResetMatch(m)) {
                // 重置赛删除：其败者的第二败是在 GR 里吃的，删除 GR 要一并撤销
                restoreLoss(m, entries);
                grIdsToRemove.add(m.getId());
                continue;
            }
            restoreLoss(m, entries);
            clearMatchResult(m, false);
        }
        entryRepository.saveAll(entries.values());

        // 删除动态重置赛，摘掉 GF 指向它的两条边
        if (!grIdsToRemove.isEmpty()) {
            for (TournamentMatch m : all) {
                if (grIdsToRemove.contains(m.getWinnerNextMatchId())
                        || grIdsToRemove.contains(m.getLoserNextMatchId())) {
                    m.setWinnerNextMatchId(null);
                    m.setWinnerNextSlot(null);
                    m.setLoserNextMatchId(null);
                    m.setLoserNextSlot(null);
                }
            }
            matchRepository.deleteAllById(grIdsToRemove);
            all.removeIf(m -> grIdsToRemove.contains(m.getId()));
        }

        // 4) 回退起点（保留两名选手的入位）
        restoreLoss(root, entries);
        entryRepository.saveAll(entries.values());
        clearMatchResult(root, true);
        tournament.setChampionEntryId(null);
        tournament.setStatus(TournamentDict.TOURNAMENT_RUNNING);

        // 5) 确定性重建：起点 + 所有未结束场次重算
        Set<TournamentMatch> startSet = new HashSet<>();
        startSet.add(root);
        for (TournamentMatch m : all) {
            if (!TournamentDict.MATCH_FINISHED.equals(m.getStatus())
                    && !TournamentDict.MATCH_VOID.equals(m.getStatus())) {
                startSet.add(m);
            }
        }
        matchRepository.saveAll(all);
        recompute(tournament, all, new ArrayDeque<>(startSet), request.operator(), null);

        // 6) 同时给出更正胜者：起点重算后双方应已到齐，立即按更正结果推进
        if (correctedWinner != null) {
            List<TournamentMatch> reloaded = matches(tournament.getId());
            TournamentMatch root2 = byId(reloaded).get(root.getId());
            if (!TournamentDict.MATCH_READY.equals(root2.getStatus())) {
                throw new BizException("回退后起点尚未到齐双方选手，不能立即确认更正结果，请先补齐对阵");
            }
            Map<Long, TournamentEntry> entries2 = entryMap(tournament.getId());
            finishNormal(tournament, root2, correctedWinner, request.operator(),
                    "纠错更正后重新确认（原因：" + request.reason().trim() + "）", entries2);
            recompute(tournament, matches(tournament.getId()),
                    downstreamQueue(root2, matches(tournament.getId())),
                    request.operator(), null);
        }

        // 7) 重建后快照
        List<TournamentMatch> finalAll = matches(tournament.getId());
        Map<Long, TournamentMatch> finalById = byId(finalAll);
        for (TournamentRevisionMatch row : auditRows) {
            TournamentMatch after = row.getMatchId() == null ? null : finalById.get(row.getMatchId());
            row.setAfterJson(after == null ? null : snapshot(after));
        }
        revisionMatchRepository.saveAll(auditRows);

        tournamentRepository.save(tournament);
        return detail(tournament.getId());
    }

    // =====================================================================
    // 核心推进引擎
    // =====================================================================

    /** 一个入位的解析结果 */
    private static final class Slot {
        Long entryId;      // 占用选手（含退赛选手，用于括号上显示其名字）
        boolean active;    // 占用者仍可上场（ACTIVE）
        boolean dead;      // 无人可再入位（结构性轮空 / 上游作废 / 占用者退赛）
        String deadReason; // BYE / VOID / WALKOVER
        boolean unknown;   // 上游尚未结束，未来可能有人
    }

    private Slot unknownSlot() {
        Slot slot = new Slot();
        slot.unknown = true;
        return slot;
    }

    /**
     * 从队列场次开始，按括号顺序向下游重算。队列场次的上游结果须已落库。
     * 自动消化轮空（BYE 直入）、退赛 WALKOVER、双方退赛 VOID；冠军在 GF/GR 终结时产生。
     */
    private void recompute(Tournament tournament,
                           List<TournamentMatch> all,
                           Deque<TournamentMatch> queue,
                           String operator,
                           String note) {
        Map<Long, TournamentEntry> entries = entryMap(tournament.getId());
        Map<Long, TournamentMatch> byId = byId(all);
        Set<Long> enqueued = new HashSet<>();
        queue.forEach(m -> enqueued.add(m.getId()));

        List<TournamentMatch> dirty = new ArrayList<>();
        boolean championDirty = false;

        while (!queue.isEmpty()) {
            TournamentMatch m = queue.poll();
            // 出队即从去重集合移除：同一次推进里，若先处理时上游未齐（PENDING），
            // 等另一路上游落库后允许再次入队（括号图是有向无环图，不会死循环）
            enqueued.remove(m.getId());
            if (TournamentDict.MATCH_FINISHED.equals(m.getStatus())
                    || TournamentDict.MATCH_VOID.equals(m.getStatus())) {
                continue;
            }

            Slot slotA = resolveSlot(m, "A", byId, entries);
            Slot slotB = resolveSlot(m, "B", byId, entries);
            m.setSideAEntryId(slotA.entryId);
            m.setSideBEntryId(slotB.entryId);

            // 两侧都还没到齐：保持等待，绝不能把「等待」当成轮空直接判胜
            if (slotA.unknown || slotB.unknown) {
                m.setStatus(TournamentDict.MATCH_PENDING);
                dirty.add(m);
                continue;
            }
            if (slotA.active && slotB.active) {
                m.setStatus(TournamentDict.MATCH_READY);
                dirty.add(m);
                continue;
            }
            // 已确定一侧能打、另一侧永久无人：自动消化（轮空 BYE / 退赛 WALKOVER）
            if (slotA.active) {
                autoFinish(tournament, m, slotA.entryId, slotB, entries, operator, note);
                championDirty = true;
                dirty.add(m);
                enqueueDownstream(m, byId, queue, enqueued);
                continue;
            }
            if (slotB.active) {
                autoFinish(tournament, m, slotB.entryId, slotA, entries, operator, note);
                championDirty = true;
                dirty.add(m);
                enqueueDownstream(m, byId, queue, enqueued);
                continue;
            }
            // 两侧都永久无人：结构性轮空占位 / 双方退赛作废
            boolean bothBye = TournamentDict.RESULT_BYE.equals(slotA.deadReason)
                    && TournamentDict.RESULT_BYE.equals(slotB.deadReason);
            markByeVoid(m, bothBye, operator);
            dirty.add(m);
            enqueueDownstream(m, byId, queue, enqueued);
        }

        if (!dirty.isEmpty()) {
            matchRepository.saveAll(dirty);
        }
        if (championDirty || TournamentDict.TOURNAMENT_FINISHED.equals(tournament.getStatus())
                || tournament.getChampionEntryId() != null) {
            tournamentRepository.save(tournament);
        }
    }

    /** 解析一场比赛某一侧的入位。 */
    private Slot resolveSlot(TournamentMatch m, String side,
                             Map<Long, TournamentMatch> byId,
                             Map<Long, TournamentEntry> entries) {
        // W1 的种子/轮空位是静态的
        if (TournamentDict.STAGE_WINNERS.equals(m.getStage()) && m.getRoundNo() == 1) {
            Long entryId = "A".equals(side) ? m.getSideAEntryId() : m.getSideBEntryId();
            if (entryId != null) {
                TournamentEntry entry = entries.get(entryId);
                Slot slot = new Slot();
                slot.entryId = entryId;
                if (entry == null || TournamentDict.ENTRY_ACTIVE.equals(entry.getStatus())) {
                    slot.active = true;
                } else {
                    slot.dead = true;
                    slot.deadReason = TournamentDict.RESULT_WALKOVER;
                }
                return slot;
            }
            Slot dead = new Slot();
            dead.dead = true;
            dead.deadReason = TournamentDict.RESULT_BYE;
            return dead;
        }

        // 收集所有把胜者/败者送入本侧的上游场次，逐个判定；任一上游未结束即「等待」
        boolean hasFeeder = false;
        boolean anyUnknown = false;
        Slot deadFallback = null;
        for (TournamentMatch up : byId.values()) {
            if (!feedsInto(up, m, side)) {
                continue;
            }
            hasFeeder = true;
            if (TournamentDict.MATCH_VOID.equals(up.getStatus())) {
                if (deadFallback == null) {
                    Slot dead = new Slot();
                    dead.dead = true;
                    dead.deadReason = TournamentDict.DEAD_VOID;
                    deadFallback = dead;
                }
                continue;
            }
            if (!TournamentDict.MATCH_FINISHED.equals(up.getStatus())) {
                anyUnknown = true;
                continue;
            }
            boolean winnerEdge = up.getWinnerNextMatchId() != null
                    && up.getWinnerNextMatchId().equals(m.getId())
                    && side.equals(up.getWinnerNextSlot());
            Long entryId = winnerEdge ? up.getWinnerEntryId() : up.getLoserEntryId();
            if (entryId == null) {
                // 轮空占位场无人输出：这一路结构性轮空
                if (deadFallback == null) {
                    Slot dead = new Slot();
                    dead.dead = true;
                    dead.deadReason = TournamentDict.RESULT_BYE;
                    deadFallback = dead;
                }
                continue;
            }
            TournamentEntry entry = entries.get(entryId);
            if (entry == null) {
                anyUnknown = true;
                continue;
            }
            if (TournamentDict.ENTRY_WITHDRAWN.equals(entry.getStatus())) {
                Slot dead = new Slot();
                dead.entryId = entryId;
                dead.dead = true;
                dead.deadReason = TournamentDict.RESULT_WALKOVER;
                return dead;
            }
            Slot slot = new Slot();
            slot.entryId = entryId;
            slot.active = true;
            return slot;
        }
        if (anyUnknown) {
            return unknownSlot();
        }
        if (deadFallback != null) {
            return deadFallback;
        }
        if (!hasFeeder) {
            return unknownSlot();
        }
        // 所有 feeder 都结束但没有任何人来：结构性轮空
        Slot dead = new Slot();
        dead.dead = true;
        dead.deadReason = TournamentDict.RESULT_BYE;
        return dead;
    }

    private boolean feedsInto(TournamentMatch up, TournamentMatch target, String side) {
        boolean winner = up.getWinnerNextMatchId() != null
                && up.getWinnerNextMatchId().equals(target.getId())
                && side.equals(up.getWinnerNextSlot());
        boolean loser = up.getLoserNextMatchId() != null
                && up.getLoserNextMatchId().equals(target.getId())
                && side.equals(up.getLoserNextSlot());
        return winner || loser;
    }

    /** 自动消化「只有一人能上场」的场次：轮空直入 BYE / 退赛 WALKOVER。 */
    private void autoFinish(Tournament tournament,
                            TournamentMatch m, Long winnerId, Slot deadSlot,
                            Map<Long, TournamentEntry> entries,
                            String operator, String note) {
        boolean byePath = TournamentDict.RESULT_BYE.equals(deadSlot.deadReason);
        String resultType = byePath ? TournamentDict.RESULT_BYE : TournamentDict.RESULT_WALKOVER;

        m.setWinnerEntryId(winnerId);
        m.setLoserEntryId(byePath ? null : deadSlot.entryId);
        m.setStatus(TournamentDict.MATCH_FINISHED);
        m.setResultType(resultType);
        m.setConfirmedBy(operator);
        m.setConfirmedAt(LocalDateTime.now());
        m.setResultNote(byePath
                ? "轮空直入（非真实比赛）"
                : "对手退赛/上游作废，直接晋级" + (note == null ? "" : "：" + note));
        // 自动晋级不增加任何人的败绩；退赛者不进入败者组

        if (TournamentDict.STAGE_GRAND_FINAL.equals(m.getStage())
                || TournamentDict.STAGE_GRAND_RESET.equals(m.getStage())) {
            // 总决赛/重置赛只剩一人能打：直接冠军（退赛放弃重置资格，不生成新的重置赛）
            finishChampion(tournament, winnerId);
        }
    }

    /** 店员正常确认赛果：双败规则推进，必要时动态生成重置赛。 */
    private void finishNormal(Tournament tournament,
                              TournamentMatch m, Long winnerId,
                              String operator, String note,
                              Map<Long, TournamentEntry> entries) {
        Long loserId = winnerId.equals(m.getSideAEntryId())
                ? m.getSideBEntryId() : m.getSideAEntryId();

        m.setWinnerEntryId(winnerId);
        m.setLoserEntryId(loserId);
        m.setStatus(TournamentDict.MATCH_FINISHED);
        m.setResultType(TournamentDict.RESULT_NORMAL);
        m.setResultNote(note);
        m.setConfirmedBy(operator == null || operator.isBlank() ? "店员" : operator.trim());
        m.setConfirmedAt(LocalDateTime.now());
        matchRepository.save(m);

        TournamentEntry loser = entries.get(loserId);

        if (TournamentDict.STAGE_GRAND_RESET.equals(m.getStage())) {
            // 重置赛结束：败者第二败出局，胜者冠军
            loser.setLossCount(2);
            loser.setStatus(TournamentDict.ENTRY_ELIMINATED);
            entryRepository.save(loser);
            finishChampion(tournament, winnerId);
            return;
        }

        if (TournamentDict.STAGE_GRAND_FINAL.equals(m.getStage())) {
            boolean loserFromWinners = loserId.equals(m.getSideAEntryId());
            boolean winnerFromLosers = winnerId.equals(m.getSideBEntryId());
            if (loserFromWinners && winnerFromLosers && loser.getLossCount() == 0) {
                // 败者组冠军首次击败不败的胜者组冠军：生成重置赛，冠军待定
                loser.setLossCount(1);
                entryRepository.save(loser);
                createResetMatch(m, winnerId, loserId);
                return;
            }
            loser.setLossCount(loser.getLossCount() + 1);
            if (loser.getLossCount() >= 2) {
                loser.setStatus(TournamentDict.ENTRY_ELIMINATED);
            }
            entryRepository.save(loser);
            finishChampion(tournament, winnerId);
            return;
        }

        // 胜者组/败者组普通场次：第一次失利落败者组，第二次失利出局
        loser.setLossCount(loser.getLossCount() + 1);
        if (loser.getLossCount() >= 2) {
            loser.setStatus(TournamentDict.ENTRY_ELIMINATED);
        }
        entryRepository.save(loser);
    }

    /** 创建重置赛并把 GF 的两条边指向它（A=GF 胜者即败者组冠军，B=GF 败者即胜者组冠军）。 */
    private void createResetMatch(TournamentMatch gf, Long gfWinnerId, Long gfLoserId) {
        TournamentMatch gr = new TournamentMatch();
        gr.setTournamentId(gf.getTournamentId());
        gr.setMatchNo("GR");
        gr.setStage(TournamentDict.STAGE_GRAND_RESET);
        gr.setRoundNo(gf.getRoundNo());
        gr.setSeq(2);
        gr.setSideAEntryId(gfWinnerId);
        gr.setSideBEntryId(gfLoserId);
        gr.setSideASource("GF 胜者（败者组冠军）");
        gr.setSideBSource("GF 败者（胜者组冠军，首败）");
        gr.setStatus(TournamentDict.MATCH_READY);
        gr = matchRepository.save(gr);

        gf.setWinnerNextMatchId(gr.getId());
        gf.setWinnerNextSlot("A");
        gf.setLoserNextMatchId(gr.getId());
        gf.setLoserNextSlot("B");
        matchRepository.save(gf);
    }

    private void finishChampion(Tournament tournament, Long championId) {
        tournament.setChampionEntryId(championId);
        tournament.setStatus(TournamentDict.TOURNAMENT_FINISHED);
    }

    private void markByeVoid(TournamentMatch m, boolean bye, String operator) {
        m.setWinnerEntryId(null);
        m.setLoserEntryId(null);
        m.setStatus(bye ? TournamentDict.MATCH_FINISHED : TournamentDict.MATCH_VOID);
        m.setResultType(bye ? TournamentDict.RESULT_BYE : null);
        m.setResultNote(bye ? "结构性轮空（非真实比赛）" : "双方选手均退赛/上游作废，本场不进行");
        m.setConfirmedBy(operator);
        m.setConfirmedAt(LocalDateTime.now());
    }

    private void enqueueDownstream(TournamentMatch m, Map<Long, TournamentMatch> byId,
                                   Deque<TournamentMatch> queue, Set<Long> enqueued) {
        enqueue(m.getWinnerNextMatchId(), byId, queue, enqueued);
        enqueue(m.getLoserNextMatchId(), byId, queue, enqueued);
    }

    private void enqueue(Long id, Map<Long, TournamentMatch> byId,
                         Deque<TournamentMatch> queue, Set<Long> enqueued) {
        if (id == null) {
            return;
        }
        TournamentMatch next = byId.get(id);
        if ( next != null && enqueued.add(id)) {
            queue.add(next);
        }
    }

    /** 收集一场比赛的全部派生场次（两条边的 BFS，不含本场）。 */
    private Deque<TournamentMatch> downstreamQueue(TournamentMatch root, List<TournamentMatch> all) {
        Map<Long, TournamentMatch> byId = byId(all);
        Deque<TournamentMatch> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        Deque<Long> ids = new ArrayDeque<>();
        if (root.getWinnerNextMatchId() != null) {
            ids.add(root.getWinnerNextMatchId());
        }
        if (root.getLoserNextMatchId() != null) {
            ids.add(root.getLoserNextMatchId());
        }
        while (!ids.isEmpty()) {
            Long id = ids.poll();
            if (!seen.add(id)) {
                continue;
            }
            TournamentMatch m = byId.get(id);
            if (m == null) {
                continue;
            }
            queue.add(m);
            if (m.getWinnerNextMatchId() != null) {
                ids.add(m.getWinnerNextMatchId());
            }
            if (m.getLoserNextMatchId() != null) {
                ids.add(m.getLoserNextMatchId());
            }
        }
        return queue;
    }

    /** 沿两条边收集全部派生场次（不含起点），按 id 排序保证重建确定性。 */
    private List<TournamentMatch> descendants(TournamentMatch root, List<TournamentMatch> all) {
        Deque<TournamentMatch> queue = downstreamQueue(root, all);
        List<TournamentMatch> result = new ArrayList<>(queue);
        result.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        return result;
    }

    /** 回退一场已正常结束的比赛：恢复其败者的一条败绩。 */
    private void restoreLoss(TournamentMatch m, Map<Long, TournamentEntry> entries) {
        if (!TournamentDict.RESULT_NORMAL.equals(m.getResultType()) || m.getLoserEntryId() == null) {
            return;
        }
        TournamentEntry loser = entries.get(m.getLoserEntryId());
        if (loser == null || TournamentDict.ENTRY_WITHDRAWN.equals(loser.getStatus())) {
            return;
        }
        loser.setLossCount(Math.max(0, loser.getLossCount() - 1));
        if (loser.getLossCount() < 2 && TournamentDict.ENTRY_ELIMINATED.equals(loser.getStatus())) {
            loser.setStatus(TournamentDict.ENTRY_ACTIVE);
        }
    }

    private void clearMatchResult(TournamentMatch m, boolean keepSides) {
        m.setWinnerEntryId(null);
        m.setLoserEntryId(null);
        m.setResultType(null);
        m.setResultNote(null);
        m.setConfirmedBy(null);
        m.setConfirmedAt(null);
        m.setStatus(TournamentDict.MATCH_PENDING);
        if (!keepSides) {
            m.setSideAEntryId(null);
            m.setSideBEntryId(null);
        }
    }

    // =====================================================================
    // 视图
    // =====================================================================

    private TournamentDetailView toDetail(Tournament tournament,
                                          List<TournamentEntry> entries,
                                          List<TournamentMatch> matches) {
        Map<Long, Member> members = memberMap(entries);
        Map<Long, TournamentEntry> entryMap = new LinkedHashMap<>();
        entries.forEach(e -> entryMap.put(e.getId(), e));
        Map<Long, TournamentMatch> matchById = byId(matches);

        List<EntryView> entryViews = entries.stream()
                .map(e -> new EntryView(
                        e.getId(),
                        e.getMemberId(),
                        e.getMemberId() == null ? null
                                : Optional.ofNullable(members.get(e.getMemberId()))
                                        .map(Member::getCardNo).orElse(null),
                        e.getEntryType(),
                        TournamentDict.entryTypeName(e.getEntryType()),
                        e.getDisplayName(),
                        e.getSeedNo(),
                        e.getLossCount(),
                        e.getStatus(),
                        TournamentDict.entryStatusName(e.getStatus()),
                        e.getWithdrawNote()))
                .toList();

        List<MatchView> matchViews = matches.stream()
                .map(m -> toMatchView(m, entryMap, matchById))
                .toList();

        int bracketSize = tournament.getBracketSize() == null
                ? bracketSize(tournament.getPlayerCount())
                : tournament.getBracketSize();
        return new TournamentDetailView(
                tournament.getId(),
                tournament.getTournamentNo(),
                tournament.getName(),
                tournament.getStatus(),
                TournamentDict.tournamentStatusName(tournament.getStatus()),
                tournament.getPlayerCount(),
                bracketSize,
                bracketSize - tournament.getPlayerCount(),
                tournament.getChampionEntryId(),
                entryName(entryMap, tournament.getChampionEntryId()),
                tournament.getCreatedAt(),
                entryViews,
                matchViews);
    }

    private MatchView toMatchView(TournamentMatch m,
                                  Map<Long, TournamentEntry> entries,
                                  Map<Long, TournamentMatch> matchById) {
        return new MatchView(
                m.getId(),
                m.getMatchNo(),
                m.getStage(),
                TournamentDict.stageName(m.getStage()),
                m.getRoundNo(),
                m.getSeq(),
                m.getSideAEntryId(),
                entryName(entries, m.getSideAEntryId()),
                m.getSideASource(),
                m.getSideBEntryId(),
                entryName(entries, m.getSideBEntryId()),
                m.getSideBSource(),
                m.getWinnerNextMatchId(),
                m.getWinnerNextMatchId() == null
                        ? null : matchById.get(m.getWinnerNextMatchId()).getMatchNo(),
                m.getWinnerNextSlot(),
                m.getLoserNextMatchId(),
                m.getLoserNextMatchId() == null
                        ? null : matchById.get(m.getLoserNextMatchId()).getMatchNo(),
                m.getLoserNextSlot(),
                m.getStatus(),
                TournamentDict.matchStatusName(m.getStatus()),
                m.getWinnerEntryId(),
                entryName(entries, m.getWinnerEntryId()),
                m.getLoserEntryId(),
                entryName(entries, m.getLoserEntryId()),
                m.getResultType(),
                m.getResultType() == null ? null : TournamentDict.resultTypeName(m.getResultType()),
                !TournamentDict.RESULT_BYE.equals(m.getResultType()),
                m.getResultNote(),
                m.getConfirmedBy(),
                m.getConfirmedAt());
    }

    private TournamentListItemView toListItem(Tournament t, Map<Long, TournamentEntry> entries) {
        String championName = null;
        if (t.getChampionEntryId() != null) {
            if (entries == null) {
                entries = entryMap(t.getId());
            }
            championName = entryName(entries, t.getChampionEntryId());
        }
        return new TournamentListItemView(
                t.getId(),
                t.getTournamentNo(),
                t.getName(),
                t.getStatus(),
                TournamentDict.tournamentStatusName(t.getStatus()),
                t.getPlayerCount(),
                t.getBracketSize(),
                t.getChampionEntryId(),
                championName,
                t.getCreatedAt());
    }

    private RevisionMatchView toRevisionMatchView(TournamentRevisionMatch row) {
        return new RevisionMatchView(
                row.getMatchId(),
                row.getMatchNo(),
                row.getStage(),
                row.getStage() == null ? null : TournamentDict.stageName(row.getStage()),
                row.getRoundNo(),
                row.getChangeType(),
                switch (row.getChangeType()) {
                    case "ROOT" -> "回退起点";
                    case "RESET" -> "已结束被重置";
                    case "CLEARED" -> "派生场入位被清空";
                    case "REMOVED" -> "重置赛被删除";
                    default -> row.getChangeType();
                },
                row.getBeforeJson(),
                row.getAfterJson());
    }

    private TournamentRevisionMatch auditRow(TournamentRevision revision, TournamentMatch m,
                                             String changeType, String beforeJson) {
        TournamentRevisionMatch row = new TournamentRevisionMatch();
        row.setRevisionId(revision.getId());
        row.setMatchId(m.getId());
        row.setMatchNo(m.getMatchNo());
        row.setStage(m.getStage());
        row.setRoundNo(m.getRoundNo());
        row.setChangeType(changeType);
        row.setBeforeJson(beforeJson);
        return row;
    }

    private String snapshot(TournamentMatch m) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matchNo", m.getMatchNo());
        data.put("stage", m.getStage());
        data.put("round", m.getRoundNo());
        data.put("status", m.getStatus());
        data.put("sideAEntryId", m.getSideAEntryId());
        data.put("sideBEntryId", m.getSideBEntryId());
        data.put("winnerEntryId", m.getWinnerEntryId());
        data.put("loserEntryId", m.getLoserEntryId());
        data.put("resultType", m.getResultType());
        data.put("resultNote", m.getResultNote());
        data.put("confirmedBy", m.getConfirmedBy());
        data.put("confirmedAt", m.getConfirmedAt() == null ? null : m.getConfirmedAt().toString());
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return data.toString();
        }
    }

    // =====================================================================
    // 辅助
    // =====================================================================

    private Tournament requireTournament(Long id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new BizException("赛事不存在"));
    }

    private TournamentMatch requireMatch(Long id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new BizException("场次不存在"));
    }

    private List<TournamentMatch> matches(Long tournamentId) {
        return matchRepository.findByTournamentIdOrderByIdAsc(tournamentId);
    }

    private Map<Long, TournamentMatch> byId(List<TournamentMatch> matches) {
        Map<Long, TournamentMatch> map = new LinkedHashMap<>();
        for (TournamentMatch m : matches) {
            map.put(m.getId(), m);
        }
        return map;
    }

    private Map<Long, TournamentEntry> entryMap(Long tournamentId) {
        Map<Long, TournamentEntry> map = new LinkedHashMap<>();
        for (TournamentEntry e : entryRepository.findByTournamentIdOrderBySeedNoAsc(tournamentId)) {
            map.put(e.getId(), e);
        }
        return map;
    }

    private String entryName(Map<Long, TournamentEntry> entries, Long entryId) {
        if (entryId == null) {
            return null;
        }
        TournamentEntry e = entries.get(entryId);
        return e == null ? null : e.getDisplayName();
    }

    private Map<Long, Member> memberMap(List<TournamentEntry> entries) {
        List<Long> memberIds = entries.stream()
                .map(TournamentEntry::getMemberId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Member> map = new LinkedHashMap<>();
        memberRepository.findAllById(memberIds).forEach(m -> map.put(m.getId(), m));
        return map;
    }

    private String matchNoById(List<TournamentRevisionMatch> rows, Long matchId) {
        if (matchId == null) {
            return null;
        }
        return rows.stream()
                .filter(r -> matchId.equals(r.getMatchId()))
                .findFirst()
                .map(TournamentRevisionMatch::getMatchNo)
                .orElse(null);
    }

    private boolean isResetMatch(TournamentMatch m) {
        return m != null && TournamentDict.STAGE_GRAND_RESET.equals(m.getStage());
    }

    private int bracketSize(int n) {
        int b = Integer.highestOneBit(n);
        return b < n ? b << 1 : b;
    }

    private String nextTournamentNo() {
        long count = tournamentRepository.count();
        String candidate;
        do {
            count++;
            candidate = String.format("T-%tY%<tm-%03d", java.time.LocalDate.now(), count);
        } while (tournamentRepository.existsByTournamentNo(candidate));
        return candidate;
    }
}
