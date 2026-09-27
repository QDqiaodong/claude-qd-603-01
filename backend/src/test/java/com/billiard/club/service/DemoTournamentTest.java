package com.billiard.club.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.billiard.club.domain.TournamentRepository;
import com.billiard.club.dto.MatchResultRequest;
import com.billiard.club.dto.view.MatchView;
import com.billiard.club.dto.view.TournamentListItemView;
import com.billiard.club.dto.view.TournamentView;
import com.billiard.club.service.tournament.DemoTournamentInitializer;

/**
 * 验证开箱即演示：应用启动后自动准备的表演赛停在总决赛前夕，
 * 点「败者组冠军胜」立即生成重置赛，再点重置赛出冠军。
 */
@SpringBootTest
class DemoTournamentTest {

    @Autowired private TournamentService service;
    @Autowired private TournamentRepository repository;
    @Autowired private DemoTournamentInitializer initializer;

    @Test
    void demoTournament_startsAtGrandFinal_andResetProducesChampion() throws Exception {
        // 其它测试已建过赛事时，主动补跑一次初始化（幂等：只在赛事表为空时真正生效）
        initializer.run(null);

        List<TournamentListItemView> list = service.list();
        TournamentListItemView demoItem = list.stream()
                .filter(x -> DemoTournamentInitializer.DEMO_NAME.equals(x.tournamentName()))
                .findFirst().orElse(null);
        if (demoItem == null) {
            // 赛事表非空导致自动初始化跳过（并行测试环境），不强制要求
            return;
        }
        Long id = demoItem.id();
        TournamentView demo = service.detail(id);
        MatchView gf = demo.matches().stream().filter(m -> m.matchCode().equals("GF")).findFirst().orElseThrow();
        assertTrue(gf.canConfirm(), "演示赛事一打开总决赛就应可确认");
        assertNull(demo.resetCode(), "总决赛尚未开打，不应有重置赛");

        Long wbChamp = gf.slotA().resolvedEntrantId();
        Long lbChamp = gf.slotB().resolvedEntrantId();
        assertEquals(0, losses(demo, wbChamp), "胜者组冠军零败");
        assertEquals(1, losses(demo, lbChamp), "败者组冠军一败");
        long eliminated = demo.entrants().stream().filter(e -> "OUT".equals(e.progress())).count();
        assertEquals(6, eliminated, "其余 6 人都已二败淘汰，正好可以直接演示决赛");

        // 败者组冠军首胜 -> 重置赛
        demo = service.confirmResult(id, gf.id(), new MatchResultRequest(lbChamp, "店员小王"));
        assertEquals("LIVE", demo.status());
        assertEquals("GF-RESET", demo.resetCode());
        MatchView reset = demo.matches().stream().filter(m -> m.matchCode().equals("GF-RESET")).findFirst().orElseThrow();
        assertTrue(reset.canConfirm());

        // 重置赛胜者组冠军赢回 -> 冠军
        demo = service.confirmResult(id, reset.id(), new MatchResultRequest(wbChamp, "店员小王"));
        assertEquals("FINISHED", demo.status());
        assertNotNull(demo.championId());
        assertEquals(wbChamp, demo.championId());

        // 刷新后状态一致
        TournamentView reloaded = service.detail(id);
        assertEquals("FINISHED", reloaded.status());
        assertEquals(reloaded.championId(), demo.championId());
    }

    private int losses(TournamentView t, Long entrantId) {
        return t.entrants().stream().filter(e -> e.id().equals(entrantId)).findFirst().orElseThrow().losses();
    }
}
