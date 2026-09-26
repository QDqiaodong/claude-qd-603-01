package com.billiard.club.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.billiard.club.common.ApiResponse;
import com.billiard.club.dto.TableRequest;
import com.billiard.club.dto.view.FloorPlanView;
import com.billiard.club.dto.view.TableView;
import com.billiard.club.service.TableService;

/** 模块一（球台管理）：平面图 / 列表 / 新增 / 局部修改 / 转维护 / 恢复空闲 */
@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping
    public ApiResponse<List<TableView>> list() {
        return ApiResponse.success(tableService.list());
    }

    @GetMapping("/floor-plan")
    public ApiResponse<FloorPlanView> floorPlan() {
        return ApiResponse.success(tableService.floorPlan());
    }

    @GetMapping("/{id}")
    public ApiResponse<TableView> detail(@PathVariable Long id) {
        return ApiResponse.success(tableService.detail(id));
    }

    @PostMapping
    public ApiResponse<TableView> create(@RequestBody TableRequest request) {
        return ApiResponse.success("球台已创建", tableService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TableView> update(@PathVariable Long id, @RequestBody TableRequest request) {
        return ApiResponse.success("球台已更新", tableService.update(id, request));
    }

    @PostMapping("/{id}/maintenance")
    public ApiResponse<TableView> maintenance(@PathVariable Long id) {
        return ApiResponse.success("球台已转入维护", tableService.maintenance(id));
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<TableView> restore(@PathVariable Long id) {
        return ApiResponse.success("球台维护完成", tableService.restore(id));
    }
}
