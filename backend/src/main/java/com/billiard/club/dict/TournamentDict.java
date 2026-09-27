package com.billiard.club.dict;

import java.util.Map;

/**
 * 双败淘汰赛事词典：状态、所在组、结果类型、名称映射集中在此。
 */
public final class TournamentDict {

    private TournamentDict() {
    }

    // 赛事状态
    public static final String TOURNAMENT_RUNNING = "RUNNING";
    public static final String TOURNAMENT_FINISHED = "FINISHED";

    private static final Map<String, String> TOURNAMENT_STATUS = Map.of(
            "DRAFT", "未开赛",
            TOURNAMENT_RUNNING, "进行中",
            TOURNAMENT_FINISHED, "已结束");

    // 参赛者类型
    public static final String ENTRY_MEMBER = "MEMBER";
    public static final String ENTRY_GUEST = "GUEST";

    private static final Map<String, String> ENTRY_TYPE = Map.of(
            ENTRY_MEMBER, "会员",
            ENTRY_GUEST, "临时参赛者");

    // 参赛者状态
    public static final String ENTRY_ACTIVE = "ACTIVE";
    public static final String ENTRY_ELIMINATED = "ELIMINATED";
    public static final String ENTRY_WITHDRAWN = "WITHDRAWN";

    private static final Map<String, String> ENTRY_STATUS = Map.of(
            ENTRY_ACTIVE, "在赛",
            ENTRY_ELIMINATED, "已出局（两败）",
            ENTRY_WITHDRAWN, "中途退赛");

    // 所在组
    public static final String STAGE_WINNERS = "WINNERS";
    public static final String STAGE_LOSERS = "LOSERS";
    public static final String STAGE_GRAND_FINAL = "GRAND_FINAL";
    public static final String STAGE_GRAND_RESET = "GRAND_RESET";

    private static final Map<String, String> STAGE = Map.of(
            STAGE_WINNERS, "胜者组",
            STAGE_LOSERS, "败者组",
            STAGE_GRAND_FINAL, "总决赛",
            STAGE_GRAND_RESET, "重置赛");

    // 场次状态
    public static final String MATCH_PENDING = "PENDING";
    public static final String MATCH_READY = "READY";
    public static final String MATCH_FINISHED = "FINISHED";
    public static final String MATCH_VOID = "VOID";

    private static final Map<String, String> MATCH_STATUS = Map.of(
            MATCH_PENDING, "等待入位",
            MATCH_READY, "待开赛",
            MATCH_FINISHED, "已结束",
            MATCH_VOID, "已作废（双方退赛）");

    // 结果类型
    public static final String RESULT_NORMAL = "NORMAL";
    public static final String RESULT_WALKOVER = "WALKOVER";
    public static final String RESULT_BYE = "BYE";
    /** 仅用于入位解析：上游场次作废（双方退赛），这一路无人可来 */
    public static final String DEAD_VOID = "VOID";

    private static final Map<String, String> RESULT_TYPE = Map.of(
            RESULT_NORMAL, "正常确认",
            RESULT_WALKOVER, "对手退赛直接晋级",
            RESULT_BYE, "轮空（非真实比赛）");

    public static String tournamentStatusName(String status) {
        return status == null ? "" : TOURNAMENT_STATUS.getOrDefault(status, status);
    }

    public static String entryTypeName(String type) {
        return type == null ? "" : ENTRY_TYPE.getOrDefault(type, type);
    }

    public static String entryStatusName(String status) {
        return status == null ? "" : ENTRY_STATUS.getOrDefault(status, status);
    }

    public static String stageName(String stage) {
        return stage == null ? "" : STAGE.getOrDefault(stage, stage);
    }

    public static String matchStatusName(String status) {
        return status == null ? "" : MATCH_STATUS.getOrDefault(status, status);
    }

    public static String resultTypeName(String type) {
        return type == null ? "" : RESULT_TYPE.getOrDefault(type, type);
    }
}
