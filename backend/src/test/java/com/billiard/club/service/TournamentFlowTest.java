package com.billiard.club.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.billiard.club.common.BizException;
import com.billiard.club.domain.Member;
import com.billiard.club.domain.MemberRepository;
import com.billiard.club.dto.CorrectResultRequest;
import com.billiard.club.dto.EntrantWithdrawRequest;
import com.billiard.club.dto.MatchResultRequest;
import com.billiard.club.dto.RollbackRequest;
import com.billiard.club.dto.TournamentCreateRequest;
import com.billiard.club.dto.view.EntrantView;
import com.billiard.club.dto.view.MatchView;
import com.billiard.club.dto.view.SlotView;
import com.billiard.club.dto.view.TournamentView;

/**
 * 双败淘汰端到端测试（H2）：完整覆盖
 *  - 非二的幂人数自动轮空、轮空不算比赛、同轮不会两场；
 *  - 胜者入对应场次、首败入败者组、二败淘汰；
 *  - 总决赛败者组冠军首胜 → 重置赛 → 冠军；
 *  - 等待中的场次不能确认；已出结果普通操作不能改；
 *  - 退赛三态（未出场 / 一败 / 已出局）与弃权链；
 *  - 管理员回退 + 更正后确定性重建，另一分支原样保留，流水可追溯。
 */
@SpringBootTest
class TournamentFlowTest {

    @Autowired
    private TournamentService tournamentService;
    @Autowired
    private MemberRepository memberRepository;

    private final List<Long> memberIds = new ArrayList<>();
    private final java.util.concurrent.atomic.AtomicInteger nameSeq = new java.util.concurrent.atomic.AtomicInteger();

    private String uniqueName(String prefix) {
        return prefix + "-" + nameSeq.incrementAndGet();
    }

    private List<TournamentCreateRequest.EntrantInput> entrants(int n) {
        if (memberIds.isEmpty()) {
            for (int i = 1; i <= 32; i++) {
                final int idx = i;
                String cardNo = "T-" + (1000 + i);
                Member m = memberRepository.findByCardNo(cardNo).orElseGet(() -> {
                    Member created = new Member();
                    created.setCardNo(cardNo);
                    created.setName("选手" + idx);
                    created.setLevel("NORMAL");
                    created.setBalance(java.math.BigDecimal.ZERO);
                    created.setTotalRecharge(java.math.BigDecimal.ZERO);
                    created.setStatus("NORMAL");
                    return memberRepository.save(created);
                });
                memberIds.add(m.getId());
            }
        }
        List<TournamentCreateRequest.EntrantInput> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new TournamentCreateRequest.EntrantInput(memberIds.get(i), null));
        }
        return list;
    }

    private Long seedOf(TournamentView t, String name) {
        return t.entrants().stream().filter(e -> e.displayName().equals(name)).findFirst().orElseThrow().id();
    }

    private MatchView match(TournamentView t, String code) {
        return t.matches().stream().filter(m -> m.matchCode().equals(code)).findFirst().orElseThrow();
    }

    private Map<String, MatchView> map(TournamentView t) {
        return t.matches().stream().collect(Collectors.toMap(MatchView::matchCode, Function.identity()));
    }

    private Long winnerOf(TournamentView t, String code, String firstName) {
        MatchView m = match(t, code);
        Long a = m.slotA().resolvedEntrantId();
        Long b = m.slotB().resolvedEntrantId();
        Long winner = entrantByName(t, firstName);
        assertTrue(winner.equals(a) || winner.equals(b), code + " 胜方必须是本场参赛者");
        return winner;
    }

    private Long entrantByName(TournamentView t, String name) {
        return t.entrants().stream().filter(e -> e.displayName().equals(name)).findFirst().orElseThrow().id();
    }

    private TournamentView result(Long tid, MatchView m, Long winnerId) {
        return tournamentService.confirmResult(tid, m.id(), new MatchResultRequest(winnerId, "店员小李"));
    }

    // ------------------------------------------------------------------
    // 1. 建赛结构：非二的幂自动轮空，轮空不是比赛
    // ------------------------------------------------------------------

    @Test
    void create_withByes_structureIsValid() {
        TournamentView t = tournamentService.create(
                new TournamentCreateRequest(uniqueName("六强双败赛"), entrants(6), "店员小李"));
        assertEquals(6, t.entrantCount());
        assertEquals(8, t.bracketSize());
        assertEquals(2, t.byeCount());
        long byeMatches = t.matches().stream().filter(MatchView::structuralBye).count();
        assertEquals(2, byeMatches, "轮空数必须等于 2，且都是单方轮空占位");
        // 所有轮空占位恰好有一个空槽和一个在场/待上游槽
        t.matches().stream().filter(MatchView::structuralBye).forEach(m -> {
            long live = (m.slotA().resolvedState().equals("PRESENT") || m.slotA().resolvedState().equals("WAITING") ? 1 : 0)
                    + (m.slotB().resolvedState().equals("PRESENT") || m.slotB().resolvedState().equals("WAITING") ? 1 : 0);
            int byes = (m.slotA().resolvedState().equals("BYE") ? 1 : 0) + (m.slotB().resolvedState().equals("BYE") ? 1 : 0);
            assertEquals(1, byes);
            assertEquals(1, live);
            assertNull(m.nextLoseText(), "轮空不产生败者落子");
        });
        // 标准布位下 6 人（8 格）的两个轮空签位由 3、4 号种子直进位（7、8 号签位为空）
        // 高种子 1、2 首轮都打真实场：验证 1 号种子的首轮是真实可打场次
        MatchView wbR1 = match(t, "WB-R1-1");
        assertFalse(wbR1.structuralBye(), "1 号种子首轮是真实比赛，不轮空");
        // 两场轮空占位里直接占位的正是 3、4 号种子
        List<Integer> byeSeeds = t.matches().stream().filter(MatchView::structuralBye)
                .map(m -> m.slotA().seedNo() != null ? m.slotA().seedNo() : m.slotB().seedNo())
                .sorted().toList();
        assertEquals(List.of(3, 4), byeSeeds, "3、4 号种子占两个轮空直进位");
        // 初始即可开打的场次：非轮空且双方到齐
        assertFalse(t.readyMatches().isEmpty());
        assertTrue(t.readyMatches().stream().noneMatch(MatchView::structuralBye));
    }

    // ------------------------------------------------------------------
    // 2. 等待场次不能确认；赛果推进：胜者进胜者组、首败落败者组、二败淘汰
    // ------------------------------------------------------------------

    @Test
    void confirmResult_doubleEliminationFlow() {
        TournamentView t = tournamentService.create(
                new TournamentCreateRequest(uniqueName("八强标准赛"), entrants(8), "店员小李"));
        Long id = t.id();

        // 总决赛在双方未到齐时不能确认
        final MatchView gfPending = match(t, "GF");
        final Long e1Before = entrantByName(t, "选手1");
        assertThrows(BizException.class, () -> result(id, gfPending, e1Before));

        // WB R1: 1,3,2,4 胜
        t = result(id, match(t, "WB-R1-1"), entrantByName(t, "选手1"));
        t = result(id, match(t, "WB-R1-2"), entrantByName(t, "选手3"));
        t = result(id, match(t, "WB-R1-3"), entrantByName(t, "选手2"));
        t = result(id, match(t, "WB-R1-4"), entrantByName(t, "选手4"));

        // 首败者进入败者组小轮：LB-M1-1 = 5 vs 7；M1-2 = 6 vs 8
        MatchView m11 = match(t, "LB-M1-1");
        assertEquals(entrantByName(t, "选手5"), m11.slotA().resolvedEntrantId());
        assertEquals(entrantByName(t, "选手7"), m11.slotB().resolvedEntrantId());
        // 胜者组第二轮到齐
        assertTrue(match(t, "WB-R2-1").canConfirm());

        t = result(id, match(t, "LB-M1-1"), entrantByName(t, "选手5"));
        t = result(id, match(t, "LB-M1-2"), entrantByName(t, "选手6"));
        // 选手7、8 二败淘汰
        assertEquals("OUT", entrant(t, "选手7").progress());
        assertEquals("已淘汰（二败）", entrant(t, "选手7").progressName());

        // WB R2：1、2 胜 -> 3、4 落入 LB-R2
        t = result(id, match(t, "WB-R2-1"), entrantByName(t, "选手1"));
        t = result(id, match(t, "WB-R2-2"), entrantByName(t, "选手2"));
        assertEquals(entrantByName(t, "选手3"), match(t, "LB-R2-1").slotB().resolvedEntrantId());
        assertEquals(entrantByName(t, "选手4"), match(t, "LB-R2-2").slotB().resolvedEntrantId());

        t = result(id, match(t, "LB-R2-1"), entrantByName(t, "选手5")); // 3 二败
        t = result(id, match(t, "LB-R2-2"), entrantByName(t, "选手6")); // 4 二败
        t = result(id, match(t, "LB-M3-1"), entrantByName(t, "选手6")); // 5 二败
        t = result(id, match(t, "WB-R3-1"), entrantByName(t, "选手1")); // 2 落入 LB-FINAL

        // 败者组决赛：6 vs 2，6 胜 => 2 淘汰
        MatchView lbFinal = match(t, "LB-FINAL");
        assertEquals(entrantByName(t, "选手6"), lbFinal.slotA().resolvedEntrantId());
        assertEquals(entrantByName(t, "选手2"), lbFinal.slotB().resolvedEntrantId());
        t = result(id, lbFinal, entrantByName(t, "选手6"));
        assertEquals("OUT", entrant(t, "选手2").progress());

        // 总决赛：1（零败）vs 6（一败）
        MatchView gf = match(t, "GF");
        assertEquals(entrantByName(t, "选手1"), gf.slotA().resolvedEntrantId());
        assertEquals(entrantByName(t, "选手6"), gf.slotB().resolvedEntrantId());
        t = result(id, gf, entrantByName(t, "选手1"));
        assertEquals("FINISHED", t.status());
        assertEquals(entrantByName(t, "选手1"), t.championId());
        assertEquals("CHAMPION", entrant(t, "选手1").progress());
        assertNull(t.resetCode(), "胜者组冠军夺冠不产生重置赛");
    }

    // ------------------------------------------------------------------
    // 3. 总决赛败者组冠军首胜 → 重置赛；重置赛出冠军
    // ------------------------------------------------------------------

    @Test
    void grandFinal_loserBracketWinnerFirstWin_createsReset() {
        TournamentView t = fullBracketToGf("八强重置赛", 8);
        Long id = t.id();
        // 选手6 是败者组冠军（一败），选手1 是胜者组冠军（零败）
        Long lb = entrantByName(t, "选手6");
        Long wb = entrantByName(t, "选手1");

        t = tournamentService.confirmResult(id, match(t, "GF").id(), new MatchResultRequest(lb, "店员小李"));
        assertEquals("LIVE", t.status(), "败者组冠军首胜，赛事不能结束");
        assertNull(t.championId());
        assertEquals("GF-RESET", t.resetCode());
        MatchView reset = match(t, "GF-RESET");
        assertEquals(wb, reset.slotA().resolvedEntrantId());
        assertEquals(lb, reset.slotB().resolvedEntrantId());
        // 选手1 此刻一败，仍在胜者组? 不：他已在总决赛输掉，progress 应反映一败
        EntrantView wbView = entrant(t, "选手1");
        assertEquals(1, wbView.losses());
        assertEquals("LOSERS", wbView.progress());

        // 重置赛选手1 胜 => 冠军（选手6 第二败淘汰）
        t = tournamentService.confirmResult(id, reset.id(), new MatchResultRequest(wb, "店员小李"));
        assertEquals("FINISHED", t.status());
        assertEquals(wb, t.championId());
        assertEquals(2, entrant(t, "选手6").losses());
    }

    // ------------------------------------------------------------------
    // 4. 已出结果不能普通修改
    // ------------------------------------------------------------------

    @Test
    void finishedMatch_cannotBeChangedNormally() {
        TournamentView t = tournamentService.create(
                new TournamentCreateRequest(uniqueName("四强锁赛果"), entrants(4), "店员小李"));
        MatchView m = match(t, "WB-R1-1");
        Long id = t.id();
        t = result(id, m, entrantByName(t, "选手1"));
        MatchView finished = match(t, "WB-R1-1");
        // 再次提交普通确认 -> 拒绝
        final TournamentView lockedView = t;
        final Long loserPick = entrantByName(lockedView, "选手3");
        BizException ex = assertThrows(BizException.class,
                () -> result(id, finished, loserPick));
        assertTrue(ex.getMessage().contains("回退"));
        // 非管理员尝试回退 -> 拒绝
        assertThrows(BizException.class, () -> tournamentService.rollback(id,
                new RollbackRequest("填错了", "店员小李", finished.id())));
    }

    // ------------------------------------------------------------------
    // 4b. 没有先回退，不能直接对一场普通待确认场次走「更正」
    // ------------------------------------------------------------------

    @Test
    void correctWithoutRollback_isRejected() {
        TournamentView t = tournamentService.create(
                new TournamentCreateRequest(uniqueName("更正须先回退"), entrants(4), "店员小李"));
        Long id = t.id();
        MatchView m = match(t, "WB-R1-1");
        // 尚未出结果、也没回退 -> 管理员直接更正必须拒绝
        assertThrows(BizException.class, () -> tournamentService.correctResult(id, m.id(),
                new CorrectResultRequest(entrantByName(t, "选手1"), "想改", "管理员张姐")));
    }

    // ------------------------------------------------------------------
    // 5. 退赛三态：未出场 / 一败 / 已出局
    // ------------------------------------------------------------------

    @Test
    void withdraw_threePhases() {
        // 未出场：8 强建好，选手8 首轮未打，退赛 -> 其对手 4 不战而胜，8 标记未出场退赛
        TournamentView t = tournamentService.create(
                new TournamentCreateRequest(uniqueName("退赛演示赛"), entrants(8), "店员小李"));
        Long id = t.id();
        Long e8 = entrantByName(t, "选手8");
        t = tournamentService.withdraw(id, e8, new EntrantWithdrawRequest("临时有急事", "店员小李"));
        EntrantView e8v = entrant(t, "选手8");
        assertTrue(e8v.withdrawn());
        assertFalse(e8v.played(), "尚未出场");
        assertEquals("WITHDRAWN_IDLE", e8v.progress());
        MatchView r14 = match(t, "WB-R1-4");
        assertEquals("WALKOVER", r14.winKind());
        assertEquals(entrantByName(t, "选手4"), r14.winnerEntrantId());
        assertEquals(e8, r14.loserEntrantId());

        // 一败：先让选手7 落到败者组再退赛
        t = result(id, match(t, "WB-R1-1"), entrantByName(t, "选手1"));
        t = result(id, match(t, "WB-R1-2"), entrantByName(t, "选手3"));
        t = result(id, match(t, "WB-R1-3"), entrantByName(t, "选手2"));
        t = result(id, match(t, "LB-M1-1"), entrantByName(t, "选手5")); // 7 二败前先... 7 已一败
        // 选手7 此时已二败（LB-M1-1 输给5），直接验证已出局不能退赛
        final Long e7 = entrantByName(t, "选手7");
        assertThrows(BizException.class, () -> tournamentService.withdraw(id,
                e7, new EntrantWithdrawRequest("伤退", "店员小李")));

        // 让选手5 成为「一败退赛」：他在败者组（已一败），退赛
        Long e5 = entrantByName(t, "选手5");
        t = tournamentService.withdraw(id, e5, new EntrantWithdrawRequest("手腕受伤", "店员小李"));
        EntrantView e5v = entrant(t, "选手5");
        assertTrue(e5v.played());
        assertEquals(1, e5v.losses(), "退赛时只输过一场");
        // 他将参加 LB-R2-1，对手是 WB-R2-1 的败者：推进 WB-R2-1（3 胜，1 首败落入败者组）
        t = result(id, match(t, "WB-R2-1"), entrantByName(t, "选手3"));
        MatchView lbR21 = match(t, "LB-R2-1");
        assertEquals("WALKOVER", lbR21.winKind(), "一败退赛者的对手不战而胜");
        assertEquals(entrantByName(t, "选手1"), lbR21.winnerEntrantId(), "WB-R2-1 败者是选手1，他不战而胜");
        assertEquals(e5, lbR21.loserEntrantId());
    }

    // ------------------------------------------------------------------
    // 6. 回退 + 更正：派生链重建，另一分支保留，流水可追溯
    // ------------------------------------------------------------------

    @Test
    void rollbackAndCorrect_rebuildsDerivationKeepsOtherBranch() {
        TournamentView t = fullBracketToGf("回退重建赛", 8);
        Long id = t.id();
        // 此时：GF = 1 vs 6。回退到 WB-R2-1（当时误判 1 胜 3，实际应是 3 胜）
        MatchView anchorBefore = match(t, "WB-R2-1");
        Long anchorId = anchorBefore.id();
        int revisionsBefore = t.revisions().size();

        TournamentView rolled = tournamentService.rollback(id,
                new RollbackRequest("记分员把胜负记反", "管理员张姐", anchorId));
        // 锚点结果被清空、回到可确认；其下游右半区（含 WB-R3-1、LB 大轮、LB-FINAL、GF）结果被回退
        MatchView anchorAfter = match(rolled, "WB-R2-1");
        assertEquals("READY", anchorAfter.status());
        assertNull(anchorAfter.winnerEntrantId());
        assertTrue(anchorAfter.pendingCorrection(), "回退后锚点应标记为待管理员更正");
        // 重置赛若此前不存在则不受影响；另一分支 WB-R2-2（2 胜 4）原样保留
        MatchView otherBranch = match(rolled, "WB-R2-2");
        assertEquals("FINISHED", otherBranch.status());
        assertEquals(entrantByName(rolled, "选手2"), otherBranch.winnerEntrantId());
        // 总决赛结果也被回退
        assertNull(match(rolled, "GF").winnerEntrantId());
        // 流水新增 ROLLBACK，原因/操作人/受影响场次齐全
        var lastRev = rolled.revisions().get(rolled.revisions().size() - 1);
        assertEquals("ROLLBACK", lastRev.changeType());
        assertEquals("管理员张姐", lastRev.operatorName());
        assertEquals("记分员把胜负记反", lastRev.reason());
        assertTrue(lastRev.affectedMatchCodes().contains("WB-R2-1"));
        assertTrue(lastRev.affectedMatchCodes().contains("GF"));
        assertEquals(rolled.currentVersion(), lastRev.versionNo());

        // 更正：3 才是胜者
        rolled = tournamentService.correctResult(id, anchorId,
                new CorrectResultRequest(entrantByName(rolled, "选手3"), "现场录像确认", "管理员张姐"));
        assertEquals(entrantByName(rolled, "选手3"), match(rolled, "WB-R2-1").winnerEntrantId());
        assertFalse(match(rolled, "WB-R2-1").pendingCorrection(), "更正后待更正标记应清除");
        // 1 现在首败，落入 LB-R2-1 的 B 槽
        assertEquals(entrantByName(rolled, "选手1"), match(rolled, "LB-R2-1").slotB().resolvedEntrantId());
        // 更正流水
        var corrRev = rolled.revisions().get(rolled.revisions().size() - 1);
        assertEquals("CORRECT", corrRev.changeType());
        assertEquals("现场录像确认", corrRev.reason());
        // 版本号单调递增
        assertTrue(rolled.currentVersion() >= revisionsBefore + 2);

        // 更正后可以正常打完，冠军状态与括号一致（页面刷新一致性由同一份视图保证）
        rolled = playOnAfterCorrection(id, rolled);
        // 冠军必然产生（具体是谁取决于后续录入，这里只验证状态闭合）
        assertEquals("FINISHED", rolled.status());
        assertNotNull(rolled.championId());
        TournamentView reloaded = tournamentService.detail(id);
        assertEquals(reloaded.status(), rolled.status());
        assertEquals(reloaded.championId(), rolled.championId());
        assertEquals(reloaded.matches().size(), rolled.matches().size());
    }

    // ------------------------------------------------------------------
    // 7. 回退总决赛：连同已生成的重置赛一起删除，流水仍可追溯 GF-RESET
    // ------------------------------------------------------------------

    @Test
    void rollbackGrandFinal_removesResetButKeepsItInAuditTrail() {
        TournamentView t = fullBracketToGf(uniqueName("总决赛回退赛"), 8);
        Long id = t.id();
        Long lb = entrantByName(t, "选手6");
        Long gfId = match(t, "GF").id();

        // 败者组冠军首胜 -> 生成重置赛
        t = tournamentService.confirmResult(id, gfId, new MatchResultRequest(lb, "店员小李"));
        assertEquals("GF-RESET", t.resetCode());

        // 回退总决赛
        TournamentView rolled = tournamentService.rollback(id,
                new RollbackRequest("总决赛记分争议", "管理员张姐", gfId));
        assertEquals("LIVE", rolled.status());
        assertNull(rolled.championId());
        assertNull(rolled.resetCode(), "重置赛作为派生场次应被删除");
        assertTrue(rolled.matches().stream().noneMatch(m -> "GF-RESET".equals(m.matchCode())));
        assertEquals("READY", match(rolled, "GF").status());
        // 上游结果原样保留
        assertEquals("FINISHED", match(rolled, "WB-R3-1").status());
        assertEquals("FINISHED", match(rolled, "LB-FINAL").status());

        // 流水必须记录被删除的重置赛，可追溯
        var rev = rolled.revisions().get(rolled.revisions().size() - 1);
        assertEquals("ROLLBACK", rev.changeType());
        assertTrue(rev.affectedMatchCodes().contains("GF"));
        assertTrue(rev.affectedMatchCodes().contains("GF-RESET"),
                "已删除的重置赛仍须出现在受影响场次流水里");
    }

    /** 更正后把剩余可打场次顺次打完（选每场 A 槽者胜；总决赛若 LB 胜再打重置赛） */
    private TournamentView playOnAfterCorrection(Long id, TournamentView start) {
        TournamentView t = start;
        for (int guard = 0; guard < 100; guard++) {
            List<MatchView> ready = new ArrayList<>(t.readyMatches());
            if (ready.isEmpty()) {
                break;
            }
            ready.sort(java.util.Comparator.comparing(MatchView::matchCode));
            MatchView m = ready.get(0);
            Long winner = m.slotA().resolvedEntrantId();
            t = tournamentService.confirmResult(id, m.id(), new MatchResultRequest(winner, "店员小李"));
        }
        return t;
    }

    /** 打完一场 8 强赛事到总决赛：WB 冠军=选手1（零败），LB 冠军=选手6（一败） */
    private TournamentView fullBracketToGf(String name, int n) {
        TournamentView t = tournamentService.create(
                new TournamentCreateRequest(name, entrants(n), "店员小李"));
        Long id = t.id();
        t = result(id, match(t, "WB-R1-1"), entrantByName(t, "选手1"));
        t = result(id, match(t, "WB-R1-2"), entrantByName(t, "选手3"));
        t = result(id, match(t, "WB-R1-3"), entrantByName(t, "选手2"));
        t = result(id, match(t, "WB-R1-4"), entrantByName(t, "选手4"));
        t = result(id, match(t, "LB-M1-1"), entrantByName(t, "选手5"));
        t = result(id, match(t, "LB-M1-2"), entrantByName(t, "选手6"));
        t = result(id, match(t, "WB-R2-1"), entrantByName(t, "选手1"));
        t = result(id, match(t, "WB-R2-2"), entrantByName(t, "选手2"));
        t = result(id, match(t, "LB-R2-1"), entrantByName(t, "选手5"));
        t = result(id, match(t, "LB-R2-2"), entrantByName(t, "选手6"));
        t = result(id, match(t, "LB-M3-1"), entrantByName(t, "选手6"));
        t = result(id, match(t, "WB-R3-1"), entrantByName(t, "选手1"));
        t = result(id, match(t, "LB-FINAL"), entrantByName(t, "选手6"));
        assertEquals("READY", match(t, "GF").status());
        return t;
    }

    private EntrantView entrant(TournamentView t, String name) {
        return t.entrants().stream().filter(e -> e.displayName().equals(name)).findFirst().orElseThrow();
    }
}
