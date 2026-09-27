package com.billiard.club.service.tournament;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 括号读时解析（纯内存）：根据场次结构与已确认结果，解析每个槽位当前站的是谁、
 * 每场是待上场/可开打/已出结果、每位参赛者几败、冠军是谁。
 *
 * 关键口径：
 *  - 结构性轮空（structuralBye）不是比赛：它的「胜者」就是在场的那一方，没有败者；
 *  - DOUBLE_WALKOVER（对阵双方均已退赛）的胜者槽解析为 OUT，下游遇到在场者时直接判不战而胜；
 *  - 已退赛的参赛者即使被上游结果送进场，槽位状态也是 WITHDRAWN（视为不可出战）。
 */
public class BracketResolver {

    public static final String STATE_PRESENT = "PRESENT";
    public static final String STATE_WAITING = "WAITING";
    public static final String STATE_BYE = "BYE";
    public static final String STATE_OUT = "OUT";

    private final Map<String, MatchRow> byCode = new HashMap<>();
    private final Map<Integer, EntrantRow> bySeed = new HashMap<>();
    private final Map<Long, EntrantRow> entrantById = new HashMap<>();

    public record EntrantRow(Long id, int seedNo, String name, boolean withdrawn) {
    }

    public record MatchRow(Long id, String code, String part, int roundNo, String stage, int seq,
                           String slotA, String slotB, String nextWin, String nextLose,
                           boolean structuralBye, String status,
                           Long winnerId, Long loserId, String winKind) {
        public boolean finished() {
            return "FINISHED".equals(status);
        }
    }

    public record Resolved(Long entrantId, String state) {
        public boolean present() {
            return STATE_PRESENT.equals(state);
        }
    }

    public static final Resolved WAITING = new Resolved(null, STATE_WAITING);
    public static final Resolved BYE = new Resolved(null, STATE_BYE);
    public static final Resolved OUT = new Resolved(null, STATE_OUT);

    public BracketResolver(List<MatchRow> matches, List<EntrantRow> entrants) {
        for (MatchRow m : matches) {
            byCode.put(m.code(), m);
        }
        for (EntrantRow e : entrants) {
            bySeed.put(e.seedNo(), e);
            entrantById.put(e.id(), e);
        }
    }

    public MatchRow match(String code) {
        return byCode.get(code);
    }

    public EntrantRow entrant(Long id) {
        return id == null ? null : entrantById.get(id);
    }

    /** 解析槽位当前归属 */
    public Resolved resolve(String slotSource) {
        if (slotSource == null || "BYE".equals(slotSource)) {
            return BYE;
        }
        if (slotSource.startsWith("SEED:")) {
            int seed = Integer.parseInt(slotSource.substring(5));
            EntrantRow e = bySeed.get(seed);
            if (e == null) {
                return BYE;
            }
            return new Resolved(e.id(), e.withdrawn() ? STATE_OUT : STATE_PRESENT);
        }
        boolean winnerEdge = slotSource.startsWith("WIN:");
        String code = slotSource.substring(winnerEdge ? 4 : 5);
        MatchRow m = byCode.get(code);
        if (m == null) {
            return WAITING;
        }
        if (m.structuralBye()) {
            if (!winnerEdge) {
                return BYE; // 轮空占位没有败者
            }
            // 轮空占位的胜者 = 在场那一方
            Resolved live = resolve(m.slotA());
            if (!live.present()) {
                live = resolve(m.slotB());
            }
            return live;
        }
        if (!m.finished()) {
            return WAITING;
        }
        if ("DOUBLE_WALKOVER".equals(m.winKind())) {
            return OUT;
        }
        Long id = winnerEdge ? m.winnerId() : m.loserId();
        if (id == null) {
            return OUT;
        }
        EntrantRow e = entrantById.get(id);
        if (e != null && e.withdrawn()) {
            return new Resolved(id, STATE_OUT);
        }
        return new Resolved(id, STATE_PRESENT);
    }

    /** 参赛者当前失利场数（轮空不算；弃权计入） */
    public int lossesOf(Long entrantId) {
        int count = 0;
        for (MatchRow m : byCode.values()) {
            if (!m.structuralBye() && m.finished() && entrantId.equals(m.loserId())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 是否真正上过场（在一场真实对抗里出战）：只计正常完赛 NORMAL；
     * 对手退赛的不战而胜（WALKOVER）/双弃权不算真正打过——人没来、没出杆，
     * 仍属于「尚未出场」，用于退赛三态的区分。
     */
    public boolean hasPlayed(Long entrantId) {
        for (MatchRow m : byCode.values()) {
            if (!m.structuralBye() && m.finished()
                    && "NORMAL".equals(m.winKind())
                    && (entrantId.equals(m.winnerId()) || entrantId.equals(m.loserId()))) {
                return true;
            }
        }
        return false;
    }

    /** 该参赛者下一场等待他的比赛（尚未出结果且沿结构引用链能追到他/他的种子位） */
    public MatchRow nextMatchOf(Long entrantId, Integer seedNo) {
        List<MatchRow> pending = new ArrayList<>();
        for (MatchRow m : byCode.values()) {
            if (m.structuralBye() || m.finished()) {
                continue;
            }
            if (references(m.slotA(), entrantId, seedNo) || references(m.slotB(), entrantId, seedNo)) {
                pending.add(m);
            }
        }
        pending.sort(Comparator.comparingInt((MatchRow m) -> partOrder(m.part()))
                .thenComparingInt(MatchRow::roundNo).thenComparingInt(MatchRow::seq));
        return pending.isEmpty() ? null : pending.get(0);
    }

    /** 参赛者（及其种子位）是否会沿引用链进入该槽：已解析到他本人，或上游链路追到他的种子 */
    private boolean references(String source, Long entrantId, Integer seedNo) {
        if (source == null || "BYE".equals(source)) {
            return false;
        }
        if (source.startsWith("SEED:")) {
            int seed = Integer.parseInt(source.substring(5));
            if (seedNo != null && seed == seedNo) {
                return true;
            }
            EntrantRow e = bySeed.get(seed);
            return e != null && entrantId.equals(e.id());
        }
        Resolved r = resolve(source);
        if (entrantId.equals(r.entrantId())) {
            return true;
        }
        if (!STATE_WAITING.equals(r.state())) {
            return false;
        }
        // 上游尚未出结果：沿「他是这场上游的可能参赛者之一」继续往上追溯
        boolean winnerEdge = source.startsWith("WIN:");
        String code = source.substring(winnerEdge ? 4 : 5);
        MatchRow up = byCode.get(code);
        if (up == null) {
            return false;
        }
        return references(up.slotA(), entrantId, seedNo) || references(up.slotB(), entrantId, seedNo);
    }

    public static int partOrder(String part) {
        return "WB".equals(part) ? 0 : "LB".equals(part) ? 1 : 2;
    }

    /** 从某场沿胜/败边做下游可达集合（包含自身） */
    public List<String> downstreamCodes(String startCode) {
        List<String> result = new ArrayList<>();
        List<String> queue = new ArrayList<>(List.of(startCode));
        while (!queue.isEmpty()) {
            String code = queue.remove(0);
            if (result.contains(code)) {
                continue;
            }
            result.add(code);
            MatchRow m = byCode.get(code);
            if (m == null) {
                continue;
            }
            addTarget(m.nextWin(), queue);
            addTarget(m.nextLose(), queue);
            // 重置赛的槽位直接引用总决赛，边表里没有 next 指向它，这里补一条
            MatchRow reset = byCode.get("GF-RESET");
            if ("GF".equals(code) && reset != null && !result.contains("GF-RESET")) {
                queue.add("GF-RESET");
            }
        }
        return result;
    }

    private void addTarget(String target, List<String> queue) {
        if (target == null || target.isBlank()) {
            return;
        }
        String code = target.substring(0, target.indexOf('#'));
        if (!queue.contains(code)) {
            queue.add(code);
        }
    }

    /** 保持结构展示顺序：胜者组按轮 → 败者组按轮 → 总决赛 → 重置赛 */
    public List<MatchRow> orderedMatches() {
        List<MatchRow> rows = new ArrayList<>(byCode.values());
        rows.sort(Comparator.comparingInt((MatchRow m) -> partOrder(m.part()))
                .thenComparingInt(MatchRow::roundNo)
                .thenComparing(Comparator.comparingInt((MatchRow m) -> stageOrder(m.stage())))
                .thenComparingInt(MatchRow::seq));
        return rows;
    }

    private int stageOrder(String stage) {
        return "MINOR".equals(stage) ? 0 : "MAJOR".equals(stage) ? 1 : 2;
    }

    public Map<String, MatchRow> getByCode() {
        return byCode;
    }
}
