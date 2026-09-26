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
import com.billiard.club.dto.GearRequest;
import com.billiard.club.dto.view.GearView;
import com.billiard.club.service.GearService;

/** 模块三之一（器材台账）：列表 / 新增 / 局部修改 / 维修完成 */
@RestController
@RequestMapping("/api/gears")
public class GearController {

    private final GearService gearService;

    public GearController(GearService gearService) {
        this.gearService = gearService;
    }

    @GetMapping
    public ApiResponse<List<GearView>> list() {
        return ApiResponse.success(gearService.listGears());
    }

    @GetMapping("/{id}")
    public ApiResponse<GearView> detail(@PathVariable Long id) {
        return ApiResponse.success(gearService.detailGear(id));
    }

    @PostMapping
    public ApiResponse<GearView> create(@RequestBody GearRequest request) {
        return ApiResponse.success("器材已入库", gearService.createGear(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<GearView> update(@PathVariable Long id, @RequestBody GearRequest request) {
        return ApiResponse.success("器材已更新", gearService.updateGear(id, request));
    }

    @PostMapping("/{id}/repair")
    public ApiResponse<GearView> repair(@PathVariable Long id) {
        return ApiResponse.success("器材维修完成，已回到在库", gearService.repairGear(id));
    }
}
