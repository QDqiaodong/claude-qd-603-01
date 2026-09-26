package com.billiard.club.dto.view;

import java.util.List;

/**
 * 球台平面图视图：按区域分区，区内按台号排布。
 * 前端平面图直接把每个 TableView 画成一个方块。
 */
public record FloorPlanView(
        List<AreaGroup> areas) {

    /** 一个区域（例如「A 区大厅」）及其台位统计 */
    public record AreaGroup(
            String areaName,
            int total,
            int idleCount,
            int occupiedCount,
            int maintenanceCount,
            List<TableView> tables) {
    }
}
