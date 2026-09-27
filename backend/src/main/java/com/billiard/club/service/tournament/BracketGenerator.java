package com.billiard.club.service.tournament;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 双败淘汰括号结构生成器（纯逻辑、无副作用）。
 *
 * 口径：
 *  1. 括号规模 bracketSize = 不小于开赛人数的最小 2 的幂（4/8/16/32），
 *     轮空数 = bracketSize - 开赛人数。标准种子布位（seedChart 递归：
 *     1,2 → 1,3,2,4 → 1,5,3,7,2,6,4,8 …）天然把轮空分给最高种子，
 *     且高种子不会提前相遇。
 *  2. 轮空只是结构占位：单方轮空的场次标 structuralBye，不算比赛、不计场数、
 *     不产生败者落子；双方都缺的对阵在生成期直接塌缩，绝不留下「幽灵比赛」。
 *  3. 同一参赛者在同一轮不会出现两场：胜者组每轮只走一条边；败者组
 *     小轮只接住「上一轮胜者组刚输掉」的人（他们该轮胜者组赛程已结束），
 *     大轮才与败者组存活者交叉合并，由结构本身保证。
 *  4. 败者组小轮（MINOR）只接胜者组首轮/后续轮的落子，大轮（MAJOR）
 *     每轮把「上一小轮存活者」与「更晚一轮胜者组掉下来的人」交叉合并；
 *     总决赛（GF）= 胜者组冠军 vs 败者组冠军。重置赛（GF-RESET）
 *     不在结构里预造，由赛果服务在败者组冠军首胜时补生成——它可能发生，
 *     也可能不发生，不能预先算成一场真实比赛。
 *
 * 槽位编码（也直接持久化）：
 *   SEED:n        种子 n 直接占位（首轮真实种子 / 首轮轮空直进位）
 *   BYE           结构性轮空空槽
 *   WIN:<code>    某场胜者
 *   LOSE:<code>   某场败者
 * 去向编码在其基础上加 "#A"/"#B" 表示进入下游场次的哪个槽。
 */
public final class BracketGenerator {

    public static final String PART_WB = "WB";
    public static final String PART_LB = "LB";
    public static final String PART_GF = "GF";

    public static final String STAGE_MINOR = "MINOR";
    public static final String STAGE_MAJOR = "MAJOR";
    public static final String STAGE_FINAL = "FINAL";

    private BracketGenerator() {
    }

    /** 一个来源/去向槽位 */
    public record Slot(String source, int refTempId) {
        public static Slot seed(int seedNo) {
            return new Slot("SEED:" + seedNo, -1);
        }

        public static Slot bye() {
            return new Slot("BYE", -1);
        }

        public static Slot win(int tempId) {
            return new Slot("WIN:" + tempId, tempId);
        }

        public static Slot lose(int tempId) {
            return new Slot("LOSE:" + tempId, tempId);
        }

        public boolean isBye() {
            return "BYE".equals(source);
        }

        public boolean isSeed() {
            return source.startsWith("SEED:");
        }

        public Integer seedNo() {
            return isSeed() ? Integer.parseInt(source.substring(5)) : null;
        }
    }

    /** 一场结构完整的对阵（可能是单方轮空占位；双方皆空不生成场次） */
    public static class MatchSpec {
        public int tempId;
        public String matchCode;
        public String bracketPart;
        public int roundNo;
        public String roundStage;
        public int seqInRound;
        public Slot slotA;
        public Slot slotB;
        public String nextWin;
        public String nextLose;
        public boolean structuralBye;

        MatchSpec(int tempId, String bracketPart, int roundNo, String roundStage, int seqInRound,
                  Slot slotA, Slot slotB) {
            this.tempId = tempId;
            this.bracketPart = bracketPart;
            this.roundNo = roundNo;
            this.roundStage = roundStage;
            this.seqInRound = seqInRound;
            this.slotA = slotA;
            this.slotB = slotB;
            boolean aLive = !slotA.isBye();
            boolean bLive = !slotB.isBye();
            this.structuralBye = aLive ^ bLive;
        }
    }

    /** 生成结果：场次（按展示顺序）+ 总决赛编号 */
    public record Bracket(List<MatchSpec> matches, String grandFinalCode) {
    }

    /** 一条边：生产者（某场的胜者或败者）→ 消费者（下游某场的 A/B 槽） */
    private record Edge(int producerTempId, boolean winnerEdge, int consumerTempId, String consumerSide) {
    }

    // ------------------------------------------------------------------
    // 规模与种子布位
    // ------------------------------------------------------------------

    /** 不小于人数的最小 2 的幂（人数限定 4~32） */
    public static int bracketSizeOf(int entrantCount) {
        if (entrantCount < 4) {
            throw new IllegalArgumentException("赛事至少需要 4 名参赛者");
        }
        int size = 2;
        while (size < entrantCount) {
            size <<= 1;
        }
        if (size > 32) {
            throw new IllegalArgumentException("赛事参赛人数不能超过 32 人");
        }
        return size;
    }

    /**
     * 标准种子布位：返回长度 bracketSize 的数组（1 基索引），值 = 该括号位上的种子号；
     * 值 &gt; entrantCount 的位置就是轮空位。递归展平：1,2 → 1,3,2,4 → 1,5,3,7,2,6,4,8。
     */
    public static int[] seedChart(int bracketSize) {
        List<Integer> chart = new ArrayList<>(List.of(1, 2));
        while (chart.size() < bracketSize) {
            List<Integer> next = new ArrayList<>(chart.size() * 2);
            // 括号翻倍：每个旧位 x 展开成 x 与对侧种子 x + 旧表大小
            //   [1,2] → [1,3,2,4] → [1,5,3,7,2,6,4,8]
            int stride = chart.size();
            for (int seed : chart) {
                next.add(seed);
                next.add(seed + stride);
            }
            chart = next;
        }
        int[] result = new int[bracketSize + 1];
        for (int i = 0; i < bracketSize; i++) {
            result[i + 1] = chart.get(i);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 生成
    // ------------------------------------------------------------------

    public static Bracket generate(int entrantCount) {
        int bracketSize = bracketSizeOf(entrantCount);
        int[] chart = seedChart(bracketSize);
        int totalRounds = Integer.numberOfTrailingZeros(bracketSize);

        List<MatchSpec> all = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        int nextTempId = 1;

        // ================= 胜者组 =================
        // 逐轮收集「败者落子槽」（按括号位顺序，含 BYE 占位），败者组大轮按列取用。
        // 必须在这里按括号顺序收，不能事后扫全场次列表：败者组场次会插在中间，且
        // 单方轮空场次会缺位，事后扫出来的顺序和数量都对不上败者组的列。
        List<List<Slot>> wbDropsByRound = new ArrayList<>();
        wbDropsByRound.add(List.of()); // 占位让轮次 1 基
        // 第 1 轮：按括号位两两配对
        List<Slot> wbBoundary = new ArrayList<>();
        List<Slot> dropsRound1 = new ArrayList<>();
        for (int p = 0; p < bracketSize / 2; p++) {
            Slot a = chartSlot(chart[p * 2 + 1], entrantCount);
            Slot b = chartSlot(chart[p * 2 + 2], entrantCount);
            Slot winSlot;
            Slot loseSlot;
            if (a.isBye() && b.isBye()) {
                // 人数极少时出现的整格塌缩：不生成场次，上、下游都给空槽
                winSlot = Slot.bye();
                loseSlot = Slot.bye();
            } else {
                MatchSpec m = new MatchSpec(nextTempId++, PART_WB, 1, stageOfWb(1, totalRounds), p + 1, a, b);
                all.add(m);
                winSlot = Slot.win(m.tempId);
                loseSlot = m.structuralBye ? Slot.bye() : Slot.lose(m.tempId);
            }
            wbBoundary.add(winSlot);
            dropsRound1.add(loseSlot);
        }
        wbDropsByRound.add(dropsRound1);

        // 第 2..totalRounds 轮
        for (int r = 2; r <= totalRounds; r++) {
            List<Slot> nextBoundary = new ArrayList<>();
            List<Slot> drops = new ArrayList<>();
            for (int p = 0; p < wbBoundary.size() / 2; p++) {
                Slot a = wbBoundary.get(p * 2);
                Slot b = wbBoundary.get(p * 2 + 1);
                Slot winSlot;
                Slot loseSlot;
                if (a.isBye() && b.isBye()) {
                    winSlot = Slot.bye();
                    loseSlot = Slot.bye();
                } else {
                    MatchSpec m = new MatchSpec(nextTempId++, PART_WB, r, stageOfWb(r, totalRounds), p + 1, a, b);
                    all.add(m);
                    winSlot = Slot.win(m.tempId);
                    // 标准布位下第 2 轮起不会出现单方轮空（同子树不会全空），
                    // 但兜底保留：轮空占位不产生真实败者
                    loseSlot = m.structuralBye ? Slot.bye() : Slot.lose(m.tempId);
                }
                nextBoundary.add(winSlot);
                drops.add(loseSlot);
            }
            wbBoundary = nextBoundary;
            wbDropsByRound.add(drops);
        }
        Slot wbChampion = wbBoundary.get(0);

        // ================= 败者组 =================
        // 败者组里没有「轮空比赛」：它按真实败者人数动态配对，与胜者组的结构性轮空完全不同。
        //
        // 算法（pool = 当前只剩一败、仍存活的参赛者槽列表）：
        //   1) 初始 pool = 胜者组首轮真实败者（去掉 BYE 占位），先配对一波（小轮），
        //      奇数人时多出来的 1 人直接进位，不造「对轮空」的比赛；
        //   2) 胜者组第 r 轮（2..R-1）打完后，把该轮真实败者并入 pool 再配对一波（大轮）；
        //   3) 每波只两两配对、每人最多打一场，奇数人进位 1 个；
        //   4) 胜者组决赛开打前，把 pool 继续配对到只剩 1 人（小轮）；
        //   5) 败者组决赛 = pool 唯一存活者 vs 胜者组决赛败者。
        // 时间口径保证：每一波都在对应胜者组轮次结束之后才配对，新掉下来的人
        // 该轮胜者组赛程已结束，同一参赛者绝不会在同一轮出现两场。
        List<List<Slot>> realDrops = new ArrayList<>();
        for (List<Slot> roundDrops : wbDropsByRound) {
            List<Slot> real = new ArrayList<>();
            for (Slot s : roundDrops) {
                if (!s.isBye()) {
                    real.add(s);
                }
            }
            realDrops.add(real);
        }

        List<Slot> pool = new ArrayList<>(realDrops.get(1));
        int lbRound = 0;
        MatchSpec lbFinal;
        int[] nextTempIdHolder = new int[]{nextTempId};

        // 小轮：只消化首轮败者（可能 0 场，例如首轮只有 1 名真实败者）
        if (pool.size() >= 2) {
            pool = lbPairWave(all, PART_LB, ++lbRound, STAGE_MINOR, pool, nextTempIdHolder);
        }
        nextTempId = nextTempIdHolder[0];

        // 大轮：逐波并入胜者组第 2..R-1 轮败者（交错合并：老兵 i 对新败者 i，
        // 保证同半区对位、首败者进入正确的败者组位置；人数不等时同类余下者内部配对）
        for (int r = 2; r <= totalRounds - 1; r++) {
            List<Slot> drops = realDrops.get(r);
            if (drops.isEmpty()) {
                continue;
            }
            List<Slot> merged = new ArrayList<>();
            int max = Math.max(pool.size(), drops.size());
            for (int i = 0; i < max; i++) {
                if (i < pool.size()) {
                    merged.add(pool.get(i));
                }
                if (i < drops.size()) {
                    merged.add(drops.get(i));
                }
            }
            pool = lbPairWave(all, PART_LB, ++lbRound, STAGE_MAJOR, merged, nextTempIdHolder);
            nextTempId = nextTempIdHolder[0];
        }
        // 对齐小轮：把 pool 收到恰好 1 人（可能连续多波，每波 1 人进位）
        while (pool.size() > 1) {
            pool = lbPairWave(all, PART_LB, ++lbRound, STAGE_MINOR, pool, nextTempIdHolder);
            nextTempId = nextTempIdHolder[0];
        }
        if (pool.isEmpty()) {
            throw new IllegalStateException("败者组存活池为空，括号生成异常");
        }

        // 败者组决赛：唯一存活者 vs 胜者组决赛败者
        List<Slot> finalDrops = realDrops.get(totalRounds);
        lbFinal = new MatchSpec(nextTempId++, PART_LB, lbRound + 1, STAGE_FINAL, 1,
                pool.get(0), finalDrops.get(0));
        all.add(lbFinal);

        // ================= 总决赛 =================
        MatchSpec grandFinal = new MatchSpec(nextTempId++, PART_GF, 1, STAGE_FINAL, 1,
                wbChampion, Slot.win(lbFinal.tempId));
        all.add(grandFinal);

        // ================= 收集边 =================
        for (MatchSpec m : all) {
            collectEdge(m.slotA, m, "A", edges);
            collectEdge(m.slotB, m, "B", edges);
        }

        assignCodesAndWire(all, edges);
        return new Bracket(all, grandFinal.matchCode);
    }

    private static Slot chartSlot(int seedNo, int entrantCount) {
        return seedNo <= entrantCount ? Slot.seed(seedNo) : Slot.bye();
    }

    private static String stageOfWb(int round, int totalRounds) {
        return round == totalRounds ? STAGE_FINAL : STAGE_MAJOR;
    }

    /**
     * 败者组一波配对：把 pool 两两配成一场场真实比赛，奇数人多出来的 1 人直接进位
     * （不造「对轮空」的比赛，败者组的轮空只是赛程进位，不是比赛）。
     * 返回下一波存活者槽列表；tempIdHolder 回写已用编号。
     */
    private static List<Slot> lbPairWave(List<MatchSpec> all, String part, int roundNo, String stage,
                                         List<Slot> pool, int[] tempIdHolder) {
        List<Slot> survivors = new ArrayList<>();
        int seq = 0;
        int pairs = pool.size() / 2;
        for (int p = 0; p < pairs; p++) {
            Slot a = pool.get(p * 2);
            Slot b = pool.get(p * 2 + 1);
            MatchSpec m = new MatchSpec(tempIdHolder[0]++, part, roundNo, stage, ++seq, a, b);
            all.add(m);
            survivors.add(Slot.win(m.tempId));
            // 该场败者 = 第二次失利 → 直接淘汰，nextLose 留空（终态）
        }
        if (pool.size() % 2 == 1) {
            // 奇数人：最后一人直接进位，不打比赛
            survivors.add(pool.get(pool.size() - 1));
        }
        return survivors;
    }

    private static void collectEdge(Slot slot, MatchSpec consumer, String side, List<Edge> edges) {
        if (slot == null || slot.isBye() || slot.isSeed() || slot.refTempId < 0) {
            return;
        }
        if (slot.source.startsWith("WIN:")) {
            edges.add(new Edge(slot.refTempId, true, consumer.tempId, side));
        } else if (slot.source.startsWith("LOSE:")) {
            edges.add(new Edge(slot.refTempId, false, consumer.tempId, side));
        }
    }

    /** 分配正式场次编号，把 WIN/LOSE:临时号 重写为正式 code，并回填每场的去向 */
    private static void assignCodesAndWire(List<MatchSpec> all, List<Edge> edges) {
        for (MatchSpec m : all) {
            m.matchCode = switch (m.bracketPart) {
                case PART_WB -> "WB-R" + m.roundNo + "-" + m.seqInRound;
                case PART_LB -> m.roundStage.equals(STAGE_FINAL)
                        ? "LB-FINAL"
                        : "LB-" + (m.roundStage.equals(STAGE_MINOR) ? "M" : "R") + m.roundNo + "-" + m.seqInRound;
                default -> "GF";
            };
        }
        Map<Integer, String> codeByTemp = new HashMap<>();
        Map<Integer, MatchSpec> byTemp = new HashMap<>();
        for (MatchSpec m : all) {
            codeByTemp.put(m.tempId, m.matchCode);
            byTemp.put(m.tempId, m);
        }

        for (Edge edge : edges) {
            MatchSpec producer = byTemp.get(edge.producerTempId());
            MatchSpec consumer = byTemp.get(edge.consumerTempId());
            String target = consumer.matchCode + "#" + edge.consumerSide();
            if (edge.winnerEdge()) {
                producer.nextWin = target;
            } else {
                producer.nextLose = target;
            }
        }

        for (MatchSpec m : all) {
            m.slotA = rewriteRef(m.slotA, codeByTemp);
            m.slotB = rewriteRef(m.slotB, codeByTemp);
        }
    }

    private static Slot rewriteRef(Slot slot, Map<Integer, String> codeByTemp) {
        if (slot.isSeed() || slot.isBye() || slot.refTempId < 0) {
            return slot;
        }
        if (slot.source.startsWith("WIN:")) {
            return new Slot("WIN:" + codeByTemp.get(slot.refTempId), slot.refTempId);
        }
        if (slot.source.startsWith("LOSE:")) {
            return new Slot("LOSE:" + codeByTemp.get(slot.refTempId), slot.refTempId);
        }
        return slot;
    }
}
