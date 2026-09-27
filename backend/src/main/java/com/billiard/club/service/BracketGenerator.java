package com.billiard.club.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 双败括号结构生成器（纯函数式规划，不碰数据库）。
 *
 * 给定开赛人数 n 与按种子顺序排列的参赛者，产出全部场次节点（含两条去向边、
 * 首轮种子入位与轮空标记）。规则口径：
 *
 *  1. 括号容量 B = 不小于 n 的最小 2 的幂；保证 n &gt; B/2（n 本身是 2 的幂时 B=n）。
 *  2. 种子位采用标准蛇形展位：B=8 时为 1,8,4,5,2,7,3,6，高种子对位高号位，
 *     缺失的高位号即轮空；【轮空也占节点】但标 resultType=BYE，不算真实比赛。
 *  3. 胜者组 W：W1 每对一个节点；W(r) 节点 i 由 W(r-1) 的 2i / 2i+1 两场胜者组成。
 *  4. 败者组 L 共 2k-2 轮（B=2^k）：
 *       L1 接收 W1 败者（可能结构性轮空/无人）；L2 起偶数轮在 B 侧接收
 *       对应胜者组轮次的败者，奇数轮为败者组内部合并。
 *  5. 总决赛 GF：A 侧胜者组冠军，B 侧败者组冠军；重置赛 GR 在赛果推进时动态生成。
 */
public final class BracketGenerator {

    private BracketGenerator() {
    }

    /** 场次规划节点 */
    public static class Node {
        String key;
        String matchNo;
        String stage;
        int round;
        int seq;

        Long sideAEntryId;
        Long sideBEntryId;
        String sideASource;
        String sideBSource;

        String winnerNextKey;
        String winnerNextSlot;
        String loserNextKey;
        String loserNextSlot;
    }

    /**
     * 生成括号规划。
     *
     * @param entryIdsBySeed 按种子顺序（种子 1 在 index 0）的参赛者 id
     */
    public static List<Node> generate(List<Long> entryIdsBySeed) {
        int n = entryIdsBySeed.size();
        if (n < 4 || n > 64) {
            throw new IllegalArgumentException("双败赛事开赛人数需在 4 ~ 64 之间");
        }
        int b = Integer.highestOneBit(n);
        if (b < n) {
            b <<= 1;
        }
        int k = Integer.numberOfTrailingZeros(b);

        int[] seedSlots = seedSlotOrder(b);
        Map<String, Node> nodes = new LinkedHashMap<>();

        // ---------------- 胜者组 ----------------
        for (int round = 1; round <= k; round++) {
            int count = b >> round;
            for (int i = 0; i < count; i++) {
                Node node = new Node();
                node.stage = "WINNERS";
                node.round = round;
                node.seq = i + 1;
                node.matchNo = "W" + round + "-" + (i + 1);
                node.key = key(node.stage, round, i);
                nodes.put(node.key, node);

                if (round >= 2) {
                    // 两侧分别来自上一轮相邻两场的胜者
                    node.sideASource = "W" + (round - 1) + "-" + (2 * i + 1) + " 胜者";
                    node.sideBSource = "W" + (round - 1) + "-" + (2 * i + 2) + " 胜者";
                }
                if (round < k) {
                    node.winnerNextKey = key("WINNERS", round + 1, i >> 1);
                    node.winnerNextSlot = (i & 1) == 0 ? "A" : "B";
                }
                // W1 -> L1；W2..Wk -> L(2(round-1)) 的 B 侧
                if (round == 1) {
                    node.loserNextKey = key("LOSERS", 1, i >> 1);
                    node.loserNextSlot = (i & 1) == 0 ? "A" : "B";
                } else {
                    node.loserNextKey = key("LOSERS", 2 * (round - 1), i);
                    node.loserNextSlot = "B";
                }
            }
        }

        // W1 种子入位 + 轮空标记
        for (int j = 0; j < b / 2; j++) {
            Node w1 = nodes.get(key("WINNERS", 1, j));
            int seedA = seedSlots[2 * j];
            int seedB = seedSlots[2 * j + 1];
            boolean realA = seedA <= n;
            boolean realB = seedB <= n;

            if (realA) {
                w1.sideAEntryId = entryIdsBySeed.get(seedA - 1);
                w1.sideASource = "种子 " + seedA + " 直入";
            } else {
                w1.sideASource = "轮空位（种子号位 " + seedA + " 无人）";
            }
            if (realB) {
                w1.sideBEntryId = entryIdsBySeed.get(seedB - 1);
                w1.sideBSource = "种子 " + seedB + " 直入";
            } else {
                w1.sideBSource = "轮空位（种子号位 " + seedB + " 无人）";
            }
            // 单真实种子 / 双空节点都交给 recompute 统一判为 BYE（非真实比赛）/ VOID
        }

        // ---------------- 败者组（B>=4 时 k>=2，共 2k-2 轮） ----------------
        if (k >= 2) {
            int loserRounds = 2 * k - 2;
            for (int round = 1; round <= loserRounds; round++) {
                int count = loserRoundCount(b, round);
                for (int i = 0; i < count; i++) {
                    Node node = new Node();
                    node.stage = "LOSERS";
                    node.round = round;
                    node.seq = i + 1;
                    node.matchNo = "L" + round + "-" + (i + 1);
                    node.key = key(node.stage, round, i);
                    nodes.put(node.key, node);

                    boolean lastLoserRound = round == loserRounds;
                    if (round == 1) {
                        // L1 胜者 -> L2 同侧 A
                        node.winnerNextKey = key("LOSERS", 2, i);
                        node.winnerNextSlot = "A";
                        node.sideASource = "W1-" + (2 * i + 1) + " 败者";
                        node.sideBSource = "W1-" + (2 * i + 2) + " 败者";
                    } else if (round % 2 == 1) {
                        // 奇数轮（>=3）：上一败者轮 2i / 2i+1 两场胜者合并
                        node.sideASource = "L" + (round - 1) + "-" + (2 * i + 1) + " 胜者";
                        node.sideBSource = "L" + (round - 1) + "-" + (2 * i + 2) + " 胜者";
                        if (!lastLoserRound) {
                            node.winnerNextKey = key("LOSERS", round + 1, i);
                            node.winnerNextSlot = "A";
                        }
                    } else {
                        // 偶数轮：A 侧上一败者轮胜者，B 侧胜者组对应轮败者
                        node.sideASource = "L" + (round - 1) + "-" + (i + 1) + " 胜者";
                        node.sideBSource = "W" + (round / 2 + 1) + "-" + (i + 1) + " 败者";
                        if (!lastLoserRound) {
                            // 下一奇数轮要合并：2i -> A、2i+1 -> B
                            node.winnerNextKey = key("LOSERS", round + 1, i >> 1);
                            node.winnerNextSlot = (i & 1) == 0 ? "A" : "B";
                        }
                    }
                    // 最后一轮胜者进总决赛 B 侧
                    if (lastLoserRound) {
                        node.winnerNextKey = "GF";
                        node.winnerNextSlot = "B";
                    }
                    // 败者组任何一场的败者都是第二败，直接出局（无边）
                }
            }
        }

        // ---------------- 总决赛 ----------------
        Node gf = new Node();
        gf.stage = "GRAND_FINAL";
        gf.round = k;
        gf.seq = 1;
        gf.matchNo = "GF";
        gf.key = "GF";
        gf.sideASource = "W" + k + "-1 胜者（胜者组冠军）";
        gf.sideBSource = "L" + (2 * k - 2) + "-1 胜者（败者组冠军）";
        nodes.put("GF", gf);
        // GF 的两条边在「败者组冠军首次击败胜者组冠军」时指向动态生成的 GR

        // 胜者组冠军轮（Wk）胜者 -> GF A
        Node wFinal = nodes.get(key("WINNERS", k, 0));
        wFinal.winnerNextKey = "GF";
        wFinal.winnerNextSlot = "A";

        return new ArrayList<>(nodes.values());
    }

    /**
     * 败者组第 round 轮的场次数。
     * L1 合并 W1 败者：b/4；偶数轮在 B 侧接对应胜者组轮的败者，
     * 奇数轮为上一偶数轮胜者两两合并。败者组共 b-2 场，加胜者组 b-1 场与总决赛 1 场，
     * 静态括号共 2b-2 场；若总决赛败者组冠军首胜，再动态追加 1 场重置赛（至多 2b-1 场）。
     */
    static int loserRoundCount(int b, int round) {
        if (round == 1) {
            return b / 4;
        }
        if (round % 2 == 0) {
            return b / (1 << (round / 2 + 1));
        }
        return b / (1 << ((round + 3) / 2));
    }

    /**
     * 标准种子展位：B=4 -> 1,4,2,3；B=8 -> 1,8,4,5,2,7,3,6。
     * 每次扩容把旧位号保留在偶数位，新位号按和为 2*size+1 放在奇数位。
     */
    static int[] seedSlotOrder(int b) {
        int[] order = {1};
        int size = 1;
        while (size < b) {
            int[] next = new int[size * 2];
            for (int i = 0; i < size; i++) {
                next[2 * i] = order[i];
                next[2 * i + 1] = 2 * size + 1 - order[i];
            }
            order = next;
            size <<= 1;
        }
        return order;
    }

    static String key(String stage, int round, int zeroBasedSeq) {
        return stage + ":" + round + ":" + zeroBasedSeq;
    }
}
