package com.billiard.club.service.tournament;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.billiard.club.domain.MemberRepository;
import com.billiard.club.domain.TournamentRepository;
import com.billiard.club.dto.MatchResultRequest;
import com.billiard.club.dto.TournamentCreateRequest;
import com.billiard.club.dto.view.MatchView;
import com.billiard.club.dto.view.TournamentView;
import com.billiard.club.service.TournamentService;

/**
 * 演示赛事初始化：系统第一次启动（赛事表为空）时，自动准备一场 8 强双败表演赛，
 * 已推进到「总决赛即将开打」：胜者组冠军 王海涛 零败，败者组冠军 周凯 一败，
 * 其余 6 人都已二败淘汰。店员只需在总决赛上点两下就能演示：
 *   - 先选周凯胜 → 王海涛首败落入败者组、系统补生成重置赛；
 *   - 重置赛再选一人胜 → 冠军产生。
 * 会员参赛者只读取会员姓名，不触碰会员余额/状态；散客以临时参赛者身份登记。
 */
@Component
@Order(20)
public class DemoTournamentInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoTournamentInitializer.class);
    public static final String DEMO_NAME = "周末双败表演赛（演示）";
    public static final String DEMO_OPERATOR = "店员小王";

    private final TournamentService tournamentService;
    private final TournamentRepository tournamentRepository;
    private final MemberRepository memberRepository;

    public DemoTournamentInitializer(TournamentService tournamentService,
                                     TournamentRepository tournamentRepository,
                                     MemberRepository memberRepository) {
        this.tournamentService = tournamentService;
        this.tournamentRepository = tournamentRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (tournamentRepository.count() > 0) {
            return;
        }
        try {
            prepare();
            log.info("[演示赛事] 已准备「{}」，推进到总决赛前夕", DEMO_NAME);
        } catch (Exception e) {
            // 初始化失败不能影响旧系统（会员/散客/球台计费）启动
            log.warn("[演示赛事] 初始化失败，已跳过：{}", e.getMessage());
        }
    }

    private void prepare() {
        // 第 1 号种子优先挂一位现有会员（只读取姓名），其余 7 人为临时散客，
        // 让同一场演示同时覆盖会员与散客两类参赛者，且不依赖会员必须存在
        List<TournamentCreateRequest.EntrantInput> entrants = new ArrayList<>();
        memberRepository.findAllByOrderByCardNoAsc().stream().findFirst()
                .ifPresentOrElse(
                        m -> entrants.add(new TournamentCreateRequest.EntrantInput(m.getId(), null)),
                        () -> entrants.add(new TournamentCreateRequest.EntrantInput(null, "王海涛")));
        String[] walkIns = {"刘思远（散客）", "陈曦（散客）", "赵一鸣（散客）",
                "老周（散客）", "周凯（散客）", "大刘（散客）", "小马（散客）"};
        for (String name : walkIns) {
            entrants.add(new TournamentCreateRequest.EntrantInput(null, name));
        }

        TournamentView t = tournamentService.create(
                new TournamentCreateRequest(DEMO_NAME, entrants, DEMO_OPERATOR));
        Long id = t.id();

        // 预置一条固定剧本（与集成测试 fullBracketToGf 相同的路径）：
        // 种子 1,3,2,4 过首轮；种子 5,6 赢败者组小轮；WB 次轮 1,2 胜；
        // 5、6 分别在 LB-R2 赢下 3、4；6 赢 LB-M3 淘汰 5；1 赢 WB 决赛把 2 送入败者组决赛；
        // 6 赢败者组决赛淘汰 2。最终 GF = 1（零败）vs 6（一败）。
        String[] path = {
                "WB-R1-1:A", "WB-R1-2:A", "WB-R1-3:A", "WB-R1-4:A",
                "LB-M1-1:A", "LB-M1-2:A",
                "WB-R2-1:A", "WB-R2-2:A",
                "LB-R2-1:A", "LB-R2-2:A",
                "LB-M3-1:B",
                "WB-R3-1:A",
                "LB-FINAL:B"
        };
        for (String step : path) {
            String[] parts = step.split(":");
            String code = parts[0];
            String side = parts[1];
            t = tournamentService.detail(id);
            MatchView m = t.matches().stream().filter(x -> x.matchCode().equals(code)).findFirst().orElseThrow();
            Long winnerId = "A".equals(side) ? m.slotA().resolvedEntrantId() : m.slotB().resolvedEntrantId();
            t = tournamentService.confirmResult(id, m.id(), new MatchResultRequest(winnerId, DEMO_OPERATOR));
        }

        // 校验演示前置条件：总决赛 READY，尚无重置赛
        TournamentView ready = tournamentService.detail(id);
        MatchView gf = ready.matches().stream().filter(m -> m.matchCode().equals("GF")).findFirst().orElseThrow();
        if (!Boolean.TRUE.equals(gf.canConfirm()) || ready.resetCode() != null) {
            throw new IllegalStateException("演示赛事预置结果不符合预期");
        }
    }
}
