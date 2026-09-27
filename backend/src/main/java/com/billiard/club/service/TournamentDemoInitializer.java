package com.billiard.club.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.billiard.club.domain.Member;
import com.billiard.club.domain.MemberRepository;
import com.billiard.club.domain.Tournament;
import com.billiard.club.domain.TournamentRepository;
import com.billiard.club.dto.MatchResultRequest;
import com.billiard.club.dto.TournamentCreateRequest;
import com.billiard.club.dto.view.MatchView;
import com.billiard.club.dto.view.TournamentDetailView;

/**
 * 演示数据准备：系统启动后若演示赛事尚不存在，则用业务服务确定性地创建两场：
 *
 *   T-DEMO-01「夏季双败公开赛」6 人（含 2 名临时参赛者、2 个轮空），推进到
 *     「胜者即将落入败者组、败者再被淘汰」的现场：W2-2 与 L1-2 两场待开赛；
 *   T-DEMO-02「五一擂台赛」4 名会员，完整打完含重置赛 GR，陈曦夺冠。
 *
 * 全部走 TournamentService 正规入口，演示括号与真实业务括号结构完全一致；
 * 只新增赛事模块数据，不触碰会员、散客与球台计费数据。
 */
@Component
public class TournamentDemoInitializer {

    private static final Logger log = LoggerFactory.getLogger(TournamentDemoInitializer.class);

    private static final String DEMO_NO_1 = "T-DEMO-01";
    private static final String DEMO_NO_2 = "T-DEMO-02";

    private final TournamentRepository tournamentRepository;
    private final TournamentService tournamentService;
    private final MemberRepository memberRepository;

    public TournamentDemoInitializer(TournamentRepository tournamentRepository,
                                     TournamentService tournamentService,
                                     MemberRepository memberRepository) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentService = tournamentService;
        this.memberRepository = memberRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDemoTournaments() {
        try {
            boolean hasLive = tournamentRepository.existsByTournamentNo(DEMO_NO_1);
            boolean hasFinished = tournamentRepository.existsByTournamentNo(DEMO_NO_2);
            if (!hasLive) {
                seedLiveDemo();
            }
            if (!hasFinished) {
                seedFinishedDemo();
            }
            if (!hasLive || !hasFinished) {
                log.info("[赛事模块] 演示双败赛事已就绪：{}（进行中）、{}（含重置赛已完赛）",
                        DEMO_NO_1, DEMO_NO_2);
            }
        } catch (Exception e) {
            // 演示数据失败不能影响系统启动与既有模块
            log.warn("[赛事模块] 演示赛事初始化失败（不影响其他模块）：{}", e.getMessage());
        }
    }

    /** 演示一：6 人现场 —— 王海涛、赵一鸣各一败；陈曦 vs 刘思远待开赛。 */
    private void seedLiveDemo() {
        Member zhao = requireMemberCard("M-004");
        Member chen = requireMemberCard("M-003");
        Member liu = requireMemberCard("M-002");
        Member wang = requireMemberCard("M-001");

        List<TournamentCreateRequest.EntrySeed> seeds = List.of(
                new TournamentCreateRequest.EntrySeed(zhao.getId(), null),
                new TournamentCreateRequest.EntrySeed(chen.getId(), null),
                new TournamentCreateRequest.EntrySeed(liu.getId(), null),
                new TournamentCreateRequest.EntrySeed(wang.getId(), null),
                new TournamentCreateRequest.EntrySeed(null, "阿杰"),
                new TournamentCreateRequest.EntrySeed(null, "老周"));

        Long tournamentId = createWithFixedNo(DEMO_NO_1, "夏季双败公开赛（演示）", seeds);

        // 种子展位 B=8：W1-1 = 1轮空 vs 8无人（赵一鸣轮空）；W1-2 = 4王海涛 vs 5阿杰；
        //               W1-3 = 2陈曦轮空 vs 7无人；W1-4 = 3刘思远 vs 6老周
        // 确认 W1-2 王海涛胜阿杰（阿杰一败）、W1-4 刘思远胜老周（老周一败）
        confirm(tournamentId, "W1-2", "王海涛", "系统演示");
        confirm(tournamentId, "W1-4", "刘思远", "系统演示");
        // W2-1 = 赵一鸣 vs 王海涛：王海涛胜（赵一鸣一败进败者组）
        confirm(tournamentId, "W2-1", "王海涛", "系统演示");
        // 此刻 W2-2（陈曦 vs 刘思远）READY 待开赛：一确认败者即落入 L1-1；
        // L1-2（老周 vs 赵一鸣）READY 待开赛：败者第二败直接淘汰。
    }

    /** 演示二：4 人完整双败，GF 败者组冠军赵一鸣首胜陈曦 -> 生成 GR -> 陈曦 GR 夺冠。 */
    private void seedFinishedDemo() {
        Member chen = requireMemberCard("M-003");
        Member zhao = requireMemberCard("M-004");
        Member wang = requireMemberCard("M-001");
        Member liu = requireMemberCard("M-002");

        List<TournamentCreateRequest.EntrySeed> seeds = List.of(
                new TournamentCreateRequest.EntrySeed(chen.getId(), null),
                new TournamentCreateRequest.EntrySeed(zhao.getId(), null),
                new TournamentCreateRequest.EntrySeed(wang.getId(), null),
                new TournamentCreateRequest.EntrySeed(liu.getId(), null));

        Long tournamentId = createWithFixedNo(DEMO_NO_2, "五一擂台赛（演示·含重置赛）", seeds);

        // B=4 展位：W1-1 = 陈曦 vs 刘思远；W1-2 = 赵一鸣 vs 王海涛
        confirm(tournamentId, "W1-1", "陈曦", "系统演示");   // 刘思远一败
        confirm(tournamentId, "W1-2", "王海涛", "系统演示"); // 赵一鸣一败
        // L1-1 = 刘思远 vs 赵一鸣：赵一鸣胜，刘思远两败出局
        confirm(tournamentId, "L1-1", "赵一鸣", "系统演示");
        // W2-1 = 陈曦 vs 王海涛：陈曦胜，王海涛一败
        confirm(tournamentId, "W2-1", "陈曦", "系统演示");
        // L2-1 = 赵一鸣 vs 王海涛：赵一鸣胜，王海涛两败出局
        confirm(tournamentId, "L2-1", "赵一鸣", "系统演示");
        // GF：败者组冠军赵一鸣首次击败胜者组冠军陈曦 -> 陈曦一败，自动生成 GR
        confirm(tournamentId, "GF", "赵一鸣", "系统演示");
        // GR：陈曦胜赵一鸣 -> 赵一鸣第二败出局，陈曦夺冠
        confirm(tournamentId, "GR", "陈曦", "系统演示");
    }

    // ------------------------------------------------------------------

    private Member requireMemberCard(String cardNo) {
        return memberRepository.findByCardNo(cardNo)
                .orElseThrow(() -> new IllegalStateException("缺少演示用会员 " + cardNo));
    }

    /** 走正式创建入口，再把自动编号替换为固定的演示编号。 */
    private Long createWithFixedNo(String fixedNo, String name,
                                   List<TournamentCreateRequest.EntrySeed> seeds) {
        TournamentDetailView created = tournamentService.create(
                new TournamentCreateRequest(name, seeds));
        Tournament tournament = tournamentRepository.findById(created.id()).orElseThrow();
        tournament.setTournamentNo(fixedNo);
        tournamentRepository.save(tournament);
        return tournament.getId();
    }

    private void confirm(Long tournamentId, String matchNo, String winnerName, String operator) {
        TournamentDetailView detail = tournamentService.detail(tournamentId);
        MatchView match = detail.matches().stream()
                .filter(m -> m.matchNo().equals(matchNo))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("演示场次不存在：" + matchNo));
        Map<String, Long> idByName = new LinkedHashMap<>();
        detail.entries().forEach(e -> idByName.put(e.displayName(), e.id()));
        Long winnerId = idByName.get(winnerName);
        if (winnerId == null) {
            throw new IllegalStateException("演示胜者不存在：" + winnerName);
        }
        tournamentService.confirmResult(match.id(),
                new MatchResultRequest(winnerId, operator, null));
    }
}
