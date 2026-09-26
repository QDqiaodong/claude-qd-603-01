package com.billiard.club.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.billiard.club.common.ApiResponse;
import com.billiard.club.dto.RentRequest;
import com.billiard.club.dto.ReturnRequest;
import com.billiard.club.dto.view.RentalView;
import com.billiard.club.service.GearService;

/** 模块三之二（器材租借）：列表 / 借出 / 归还（可登记损坏赔偿） */
@RestController
@RequestMapping("/api/rentals")
public class RentalController {

    private final GearService gearService;

    public RentalController(GearService gearService) {
        this.gearService = gearService;
    }

    @GetMapping
    public ApiResponse<List<RentalView>> list() {
        return ApiResponse.success(gearService.listRentals());
    }

    @GetMapping("/by-gear/{gearId}")
    public ApiResponse<List<RentalView>> byGear(@PathVariable Long gearId) {
        return ApiResponse.success(gearService.rentalsOfGear(gearId));
    }

    @PostMapping
    public ApiResponse<RentalView> rent(@RequestBody RentRequest request) {
        return ApiResponse.success("借出成功", gearService.rent(request));
    }

    @PostMapping("/{id}/return")
    public ApiResponse<RentalView> returnGear(@PathVariable Long id, @RequestBody(required = false) ReturnRequest request) {
        return ApiResponse.success("归还成功", gearService.returnGear(id, request));
    }
}
