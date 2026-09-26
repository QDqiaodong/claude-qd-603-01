package com.billiard.club.dict;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 台球俱乐部业务字典与规则参数表。
 *
 * 球台状态机、局单状态机的许可边、计费口径（不足一小时按一小时）、会员折扣、
 * 充值区间、器材状态等规则全部集中在这里，便于按业务口径统一调整或扩展。
 */
public final class BilliardDict {

    private BilliardDict() {
    }

    // ------------------------------------------------------------------
    // 球台类型
    // ------------------------------------------------------------------
    public static final String TABLE_TYPE_CHINESE_EIGHT = "CHINESE_EIGHT";
    public static final String TABLE_TYPE_SNOOKER = "SNOOKER";
    public static final String TABLE_TYPE_AMERICAN_NINE = "AMERICAN_NINE";

    private static final Map<String, String> TABLE_TYPE = Map.of(
            TABLE_TYPE_CHINESE_EIGHT, "中式黑八",
            TABLE_TYPE_SNOOKER, "斯诺克",
            TABLE_TYPE_AMERICAN_NINE, "美式九球");

    // ------------------------------------------------------------------
    // 球台状态机：空闲 -> 使用中 -> 空闲，任意非使用中可转维护
    // ------------------------------------------------------------------
    public static final String TABLE_IDLE = "IDLE";
    public static final String TABLE_OCCUPIED = "OCCUPIED";
    public static final String TABLE_MAINTENANCE = "MAINTENANCE";

    private static final Map<String, String> TABLE_STATUS = Map.of(
            TABLE_IDLE, "空闲",
            TABLE_OCCUPIED, "使用中",
            TABLE_MAINTENANCE, "维护中");

    /** 球台状态机许可边：维护中必须走「恢复」回空闲，不能直接跳到使用中 */
    private static final Map<String, List<String>> TABLE_STATUS_EDGE = Map.of(
            TABLE_IDLE, List.of(TABLE_OCCUPIED, TABLE_MAINTENANCE),
            TABLE_OCCUPIED, List.of(TABLE_IDLE, TABLE_MAINTENANCE),
            TABLE_MAINTENANCE, List.of(TABLE_IDLE));

    // ------------------------------------------------------------------
    // 局单（开局 -> 结束 -> 结算）单向状态机
    // ------------------------------------------------------------------
    public static final String SESSION_OPEN = "OPEN";
    public static final String SESSION_CLOSED = "CLOSED";
    public static final String SESSION_SETTLED = "SETTLED";

    private static final Map<String, String> SESSION_STATUS = Map.of(
            SESSION_OPEN, "进行中",
            SESSION_CLOSED, "已结束待结算",
            SESSION_SETTLED, "已结算");

    /** 局单状态机许可边：只许往前走，已结算即终态 */
    private static final Map<String, List<String>> SESSION_STATUS_EDGE = Map.of(
            SESSION_OPEN, List.of(SESSION_CLOSED),
            SESSION_CLOSED, List.of(SESSION_SETTLED),
            SESSION_SETTLED, List.of());

    /** 计费口径：不足一小时按一小时算（只适用于从未换过台的局；换过台的局按各段实际分钟计费） */
    public static final int MIN_BILLABLE_MINUTES = 60;

    /** 每局的最短计费分钟数 */
    public static final int MIN_SESSION_MINUTES = 1;

    // ------------------------------------------------------------------
    // 计费分段关闭原因
    // ------------------------------------------------------------------
    public static final String SEGMENT_CLOSE_TRANSFER = "TRANSFER";
    public static final String SEGMENT_CLOSE_CLOSE = "CLOSE";

    private static final Map<String, String> SEGMENT_CLOSE_CAUSE = Map.of(
            SEGMENT_CLOSE_TRANSFER, "换台关闭",
            SEGMENT_CLOSE_CLOSE, "结账关闭");

    // ------------------------------------------------------------------
    // 器材与租借
    // ------------------------------------------------------------------
    private static final Map<String, String> GEAR_TYPE = Map.of(
            "CUE", "球杆",
            "GLOVE", "手套",
            "BRIDGE", "架杆",
            "BALL_SET", "球组");

    public static final String GEAR_AVAILABLE = "AVAILABLE";
    public static final String GEAR_RENTED = "RENTED";
    public static final String GEAR_DAMAGED = "DAMAGED";
    public static final String GEAR_SCRAPPED = "SCRAPPED";

    private static final Map<String, String> GEAR_STATUS = Map.of(
            GEAR_AVAILABLE, "在库",
            GEAR_RENTED, "已借出",
            GEAR_DAMAGED, "已损坏",
            GEAR_SCRAPPED, "已报废");

    public static final String RENTAL_RENTED = "RENTED";
    public static final String RENTAL_RETURNED = "RETURNED";
    public static final String RENTAL_DAMAGED = "DAMAGED";

    private static final Map<String, String> RENTAL_STATUS = Map.of(
            RENTAL_RENTED, "租借中",
            RENTAL_RETURNED, "已归还",
            RENTAL_DAMAGED, "损坏待赔");

    // ------------------------------------------------------------------
    // 会员
    // ------------------------------------------------------------------
    private static final List<String> MEMBER_LEVEL_ORDER = List.of("NORMAL", "SILVER", "GOLD", "DIAMOND");

    private static final Map<String, String> MEMBER_LEVEL = Map.of(
            "NORMAL", "普通会员",
            "SILVER", "银卡会员",
            "GOLD", "金卡会员",
            "DIAMOND", "钻石卡会员");

    /** 会员等级折扣率：实收 = 应收 × 折扣率 */
    private static final Map<String, BigDecimal> MEMBER_DISCOUNT = Map.of(
            "NORMAL", BigDecimal.valueOf(1.00),
            "SILVER", BigDecimal.valueOf(0.95),
            "GOLD", BigDecimal.valueOf(0.90),
            "DIAMOND", BigDecimal.valueOf(0.85));

    /** 累计充值达到此金额可升级到对应等级（只升不降） */
    private static final Map<String, BigDecimal> MEMBER_UPGRADE_THRESHOLD = Map.of(
            "SILVER", BigDecimal.valueOf(3000),
            "GOLD", BigDecimal.valueOf(10000),
            "DIAMOND", BigDecimal.valueOf(20000));

    public static final String MEMBER_NORMAL = "NORMAL";
    public static final String MEMBER_FROZEN = "FROZEN";

    private static final Map<String, String> MEMBER_STATUS = Map.of(
            MEMBER_NORMAL, "正常",
            MEMBER_FROZEN, "冻结");

    public static final BigDecimal RECHARGE_MIN = BigDecimal.valueOf(100);
    public static final BigDecimal RECHARGE_MAX = BigDecimal.valueOf(5000);

    // ------------------------------------------------------------------
    // 支付方式
    // ------------------------------------------------------------------
    public static final String PAY_BALANCE = "BALANCE";
    public static final String PAY_CASH = "CASH";

    private static final Map<String, String> PAY_MODE = Map.of(
            PAY_BALANCE, "储值余额",
            PAY_CASH, "现金",
            "WECHAT", "微信支付",
            "CARD", "银行卡");

    // ------------------------------------------------------------------
    // 取值方法
    // ------------------------------------------------------------------

    public static boolean isValidTableType(String type) {
        return type != null && TABLE_TYPE.containsKey(type);
    }

    public static String tableTypeName(String type) {
        return type == null ? "" : TABLE_TYPE.getOrDefault(type, type);
    }

    public static boolean isValidTableStatus(String status) {
        return status != null && TABLE_STATUS.containsKey(status);
    }

    public static String tableStatusName(String status) {
        return status == null ? "" : TABLE_STATUS.getOrDefault(status, status);
    }

    /** 球台状态机：from 能否走到 to */
    public static boolean canTransferTable(String from, String to) {
        return from != null && to != null && TABLE_STATUS_EDGE.getOrDefault(from, List.of()).contains(to);
    }

    public static boolean isValidSessionStatus(String status) {
        return status != null && SESSION_STATUS.containsKey(status);
    }

    public static String sessionStatusName(String status) {
        return status == null ? "" : SESSION_STATUS.getOrDefault(status, status);
    }

    /** 局单状态机：from 能否走到 to */
    public static boolean canTransferSession(String from, String to) {
        return from != null && to != null && SESSION_STATUS_EDGE.getOrDefault(from, List.of()).contains(to);
    }

    public static String segmentCloseCauseName(String cause) {
        return cause == null ? "" : SEGMENT_CLOSE_CAUSE.getOrDefault(cause, cause);
    }

    public static boolean isValidGearType(String type) {
        return type != null && GEAR_TYPE.containsKey(type);
    }

    public static String gearTypeName(String type) {
        return type == null ? "" : GEAR_TYPE.getOrDefault(type, type);
    }

    public static boolean isValidGearStatus(String status) {
        return status != null && GEAR_STATUS.containsKey(status);
    }

    public static String gearStatusName(String status) {
        return status == null ? "" : GEAR_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidRentalStatus(String status) {
        return status != null && RENTAL_STATUS.containsKey(status);
    }

    public static String rentalStatusName(String status) {
        return status == null ? "" : RENTAL_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidMemberLevel(String level) {
        return level != null && MEMBER_LEVEL.containsKey(level);
    }

    public static String memberLevelName(String level) {
        return level == null ? "" : MEMBER_LEVEL.getOrDefault(level, level);
    }

    public static BigDecimal memberDiscount(String level) {
        return MEMBER_DISCOUNT.getOrDefault(level, BigDecimal.valueOf(1.00));
    }

    public static int memberLevelRank(String level) {
        int index = MEMBER_LEVEL_ORDER.indexOf(level);
        return index < 0 ? 0 : index + 1;
    }

    /** 依据累计充值金额算出应得的最高等级 */
    public static String levelByTotalRecharge(BigDecimal totalRecharge) {
        BigDecimal total = totalRecharge == null ? BigDecimal.ZERO : totalRecharge;
        if (total.compareTo(MEMBER_UPGRADE_THRESHOLD.get("DIAMOND")) >= 0) {
            return "DIAMOND";
        }
        if (total.compareTo(MEMBER_UPGRADE_THRESHOLD.get("GOLD")) >= 0) {
            return "GOLD";
        }
        if (total.compareTo(MEMBER_UPGRADE_THRESHOLD.get("SILVER")) >= 0) {
            return "SILVER";
        }
        return "NORMAL";
    }

    public static boolean isValidMemberStatus(String status) {
        return status != null && MEMBER_STATUS.containsKey(status);
    }

    public static String memberStatusName(String status) {
        return status == null ? "" : MEMBER_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidPayMode(String mode) {
        return mode != null && PAY_MODE.containsKey(mode);
    }

    public static String payModeName(String mode) {
        return mode == null ? "" : PAY_MODE.getOrDefault(mode, mode);
    }
}
