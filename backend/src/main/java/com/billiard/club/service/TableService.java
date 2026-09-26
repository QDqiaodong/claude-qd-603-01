package com.billiard.club.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billiard.club.common.BizException;
import com.billiard.club.dict.BilliardDict;
import com.billiard.club.domain.BilliardTable;
import com.billiard.club.domain.BilliardTableRepository;
import com.billiard.club.domain.Member;
import com.billiard.club.domain.MemberRepository;
import com.billiard.club.domain.TableSession;
import com.billiard.club.domain.TableSessionRepository;
import com.billiard.club.dto.TableRequest;
import com.billiard.club.dto.view.FloorPlanView;
import com.billiard.club.dto.view.TableView;

/**
 * 模块一：球台管理。
 * 规则：台号唯一 / 状态机（空闲·使用中·维护中）/ 维护中禁止开局 / 使用中不能转维护。
 */
@Service
public class TableService {

    private final BilliardTableRepository tableRepository;
    private final TableSessionRepository sessionRepository;
    private final MemberRepository memberRepository;

    public TableService(BilliardTableRepository tableRepository,
                        TableSessionRepository sessionRepository,
                        MemberRepository memberRepository) {
        this.tableRepository = tableRepository;
        this.sessionRepository = sessionRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public List<TableView> list() {
        return tableRepository.findAllByOrderByAreaNameAscTableNoAsc().stream().map(this::toView).toList();
    }

    /** 球台平面图：按区域分组，每组带台位统计 */
    @Transactional(readOnly = true)
    public FloorPlanView floorPlan() {
        Map<String, List<TableView>> grouped = new LinkedHashMap<>();
        for (BilliardTable table : tableRepository.findAllByOrderByAreaNameAscTableNoAsc()) {
            grouped.computeIfAbsent(table.getAreaName(), key -> new ArrayList<>()).add(toView(table));
        }
        List<FloorPlanView.AreaGroup> areas = new ArrayList<>();
        for (Map.Entry<String, List<TableView>> entry : grouped.entrySet()) {
            List<TableView> tables = entry.getValue();
            int idle = 0;
            int occupied = 0;
            int maintenance = 0;
            for (TableView table : tables) {
                if (BilliardDict.TABLE_OCCUPIED.equals(table.status())) {
                    occupied++;
                } else if (BilliardDict.TABLE_MAINTENANCE.equals(table.status())) {
                    maintenance++;
                } else {
                    idle++;
                }
            }
            areas.add(new FloorPlanView.AreaGroup(
                    entry.getKey(), tables.size(), idle, occupied, maintenance, tables));
        }
        return new FloorPlanView(areas);
    }

    @Transactional(readOnly = true)
    public TableView detail(Long id) {
        return toView(require(id));
    }

    @Transactional
    public TableView create(TableRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        if (request.tableNo() == null || request.tableNo().isBlank()) {
            throw new BizException("台号不能为空");
        }
        String tableNo = request.tableNo().trim();
        if (tableRepository.existsByTableNo(tableNo)) {
            throw new BizException("台号「" + tableNo + "」已存在，台号必须唯一");
        }
        if (request.areaName() == null || request.areaName().isBlank()) {
            throw new BizException("所属区域不能为空");
        }
        if (!BilliardDict.isValidTableType(request.tableType())) {
            throw new BizException("球台类型不合法：" + request.tableType()
                    + "，只支持 CHINESE_EIGHT / SNOOKER / AMERICAN_NINE");
        }
        if (request.hourlyRate() == null) {
            throw new BizException("每小时单价不能为空");
        }
        if (request.hourlyRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("每小时单价必须大于 0 元，当前填写：" + request.hourlyRate());
        }

        BilliardTable table = new BilliardTable();
        table.setTableNo(tableNo);
        table.setAreaName(request.areaName().trim());
        table.setTableType(request.tableType());
        table.setHourlyRate(request.hourlyRate().setScale(2, java.math.RoundingMode.HALF_UP));
        table.setRemark(request.remark());

        // 默认值只放在 service 的 create 里，实体上不写默认值
        String status = request.status() == null ? BilliardDict.TABLE_IDLE : request.status();
        if (!BilliardDict.isValidTableStatus(status)) {
            throw new BizException("球台状态不合法：" + status + "，只支持 IDLE / OCCUPIED / MAINTENANCE");
        }
        if (BilliardDict.TABLE_OCCUPIED.equals(status)) {
            throw new BizException("新建球台不能直接置为「使用中」，请创建后用开局操作置为使用中");
        }
        table.setStatus(status);

        return toView(tableRepository.save(table));
    }

    @Transactional
    public TableView update(Long id, TableRequest request) {
        BilliardTable table = require(id);
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        if (request.tableNo() != null && !request.tableNo().isBlank()) {
            String tableNo = request.tableNo().trim();
            if (!tableNo.equals(table.getTableNo())) {
                if (tableRepository.existsByTableNo(tableNo)) {
                    throw new BizException("台号「" + tableNo + "」已存在，台号必须唯一");
                }
                table.setTableNo(tableNo);
            }
        }
        if (request.areaName() != null && !request.areaName().isBlank()) {
            table.setAreaName(request.areaName().trim());
        }
        if (request.tableType() != null) {
            if (!BilliardDict.isValidTableType(request.tableType())) {
                throw new BizException("球台类型不合法：" + request.tableType()
                        + "，只支持 CHINESE_EIGHT / SNOOKER / AMERICAN_NINE");
            }
            table.setTableType(request.tableType());
        }
        if (request.hourlyRate() != null) {
            if (request.hourlyRate().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("每小时单价必须大于 0 元，当前填写：" + request.hourlyRate());
            }
            table.setHourlyRate(request.hourlyRate().setScale(2, java.math.RoundingMode.HALF_UP));
        }
        if (request.remark() != null) {
            table.setRemark(request.remark());
        }
        if (request.status() != null) {
            throw new BizException("球台状态由开局 / 结束 / 维护操作驱动，不能通过编辑直接修改");
        }
        return toView(tableRepository.save(table));
    }

    /** 转入维护：台上有进行中的局就必须先结束结算（换台成功的故障台除外——它的局已经挪走） */
    @Transactional
    public TableView maintenance(Long id) {
        // 加行级写锁：与开局 / 换台互斥，避免把刚换入的局覆盖成维护中
        BilliardTable table = lockForUpdate(id);
        if (BilliardDict.TABLE_MAINTENANCE.equals(table.getStatus())) {
            throw new BizException("球台「" + table.getTableNo() + "」已经是维护中状态");
        }
        sessionRepository.findFirstByTableIdAndStatus(table.getId(), BilliardDict.SESSION_OPEN)
                .ifPresent(open -> {
                    throw new BizException("球台「" + table.getTableNo() + "」上还有一局进行中（单号 "
                            + open.getSessionNo() + "），请先结束并结算再转入维护");
                });
        table.setStatus(BilliardDict.TABLE_MAINTENANCE);
        return toView(tableRepository.save(table));
    }

    /** 维护完成，恢复空闲 */
    @Transactional
    public TableView restore(Long id) {
        BilliardTable table = lockForUpdate(id);
        if (!BilliardDict.TABLE_MAINTENANCE.equals(table.getStatus())) {
            throw new BizException("球台「" + table.getTableNo() + "」不在维护中，无需恢复");
        }
        table.setStatus(BilliardDict.TABLE_IDLE);
        return toView(tableRepository.save(table));
    }

    /** 其他 service 复用的取台方法，顺带把 null 挡在库前面 */
    @Transactional(readOnly = true)
    public BilliardTable require(Long id) {
        if (id == null) {
            throw new BizException("请先选择球台");
        }
        return tableRepository.findById(id)
                .orElseThrow(() -> new BizException("球台不存在，ID：" + id));
    }

    /** 加行级写锁取球台：状态变更（维护 / 恢复）与开局、换台互斥 */
    private BilliardTable lockForUpdate(Long id) {
        if (id == null) {
            throw new BizException("请先选择球台");
        }
        return tableRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("球台不存在，ID：" + id));
    }

    public TableView toView(BilliardTable table) {
        Long currentSessionId = null;
        String currentSessionNo = null;
        String currentMemberName = null;
        TableSession open = sessionRepository
                .findFirstByTableIdAndStatus(table.getId(), BilliardDict.SESSION_OPEN)
                .orElse(null);
        if (open != null) {
            currentSessionId = open.getId();
            currentSessionNo = open.getSessionNo();
            if (open.getMemberId() != null) {
                currentMemberName = memberRepository.findById(open.getMemberId())
                        .map(Member::getName)
                        .orElse(null);
            }
        }
        return new TableView(
                table.getId(),
                table.getTableNo(),
                table.getAreaName(),
                table.getTableType(),
                BilliardDict.tableTypeName(table.getTableType()),
                table.getHourlyRate(),
                table.getStatus(),
                BilliardDict.tableStatusName(table.getStatus()),
                table.getRemark(),
                table.getOpenCount(),
                table.getTotalMinutes(),
                currentSessionId,
                currentSessionNo,
                currentMemberName);
    }
}
