package com.billiard.club.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billiard.club.common.BizException;
import com.billiard.club.dict.BilliardDict;
import com.billiard.club.domain.Gear;
import com.billiard.club.domain.GearRental;
import com.billiard.club.domain.GearRentalRepository;
import com.billiard.club.domain.GearRepository;
import com.billiard.club.domain.Member;
import com.billiard.club.dto.GearRequest;
import com.billiard.club.dto.RentRequest;
import com.billiard.club.dto.ReturnRequest;
import com.billiard.club.dto.view.GearView;
import com.billiard.club.dto.view.RentalView;

/**
 * 模块三：球杆与器材租借。
 * 规则：器材编号唯一 / 借出未还禁止再借 / 损坏赔偿（有上限，且损坏器材维修完成前禁借）/
 * 归还与赔偿结清都是单向动作。
 */
@Service
public class GearService {

    private final GearRepository gearRepository;
    private final GearRentalRepository rentalRepository;
    private final MemberService memberService;

    public GearService(GearRepository gearRepository,
                       GearRentalRepository rentalRepository,
                       MemberService memberService) {
        this.gearRepository = gearRepository;
        this.rentalRepository = rentalRepository;
        this.memberService = memberService;
    }

    // ------------------------------------------------------------------
    // 器材
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<GearView> listGears() {
        return gearRepository.findAllByOrderByGearNoAsc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public GearView detailGear(Long id) {
        return toView(requireGear(id));
    }

    @Transactional
    public GearView createGear(GearRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        if (request.gearNo() == null || request.gearNo().isBlank()) {
            throw new BizException("器材编号不能为空");
        }
        String gearNo = request.gearNo().trim();
        if (gearRepository.existsByGearNo(gearNo)) {
            throw new BizException("器材编号「" + gearNo + "」已存在，器材编号必须唯一");
        }
        if (request.gearName() == null || request.gearName().isBlank()) {
            throw new BizException("器材名称不能为空");
        }
        if (!BilliardDict.isValidGearType(request.gearType())) {
            throw new BizException("器材类型不合法：" + request.gearType()
                    + "，只支持 CUE / GLOVE / BRIDGE / BALL_SET");
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("器材原价必须大于 0 元，当前填写：" + request.price());
        }

        Gear gear = new Gear();
        gear.setGearNo(gearNo);
        gear.setGearName(request.gearName().trim());
        gear.setGearType(request.gearType());
        gear.setPrice(request.price().setScale(2, RoundingMode.HALF_UP));

        // 默认值只放在 service 的 create 里，实体上不写默认值
        String status = request.status() == null ? BilliardDict.GEAR_AVAILABLE : request.status();
        if (!BilliardDict.isValidGearStatus(status)) {
            throw new BizException("器材状态不合法：" + status + "，只支持 AVAILABLE / RENTED / DAMAGED / SCRAPPED");
        }
        if (BilliardDict.GEAR_RENTED.equals(status)) {
            throw new BizException("新建器材不能直接置为「已借出」，请创建后用借出操作处理");
        }
        gear.setStatus(status);

        return toView(gearRepository.save(gear));
    }

    @Transactional
    public GearView updateGear(Long id, GearRequest request) {
        Gear gear = requireGear(id);
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        if (request.gearNo() != null && !request.gearNo().isBlank()) {
            String gearNo = request.gearNo().trim();
            if (!gearNo.equals(gear.getGearNo())) {
                if (gearRepository.existsByGearNo(gearNo)) {
                    throw new BizException("器材编号「" + gearNo + "」已存在，器材编号必须唯一");
                }
                gear.setGearNo(gearNo);
            }
        }
        if (request.gearName() != null && !request.gearName().isBlank()) {
            gear.setGearName(request.gearName().trim());
        }
        if (request.gearType() != null) {
            if (!BilliardDict.isValidGearType(request.gearType())) {
                throw new BizException("器材类型不合法：" + request.gearType()
                        + "，只支持 CUE / GLOVE / BRIDGE / BALL_SET");
            }
            gear.setGearType(request.gearType());
        }
        if (request.price() != null) {
            if (request.price().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("器材原价必须大于 0 元，当前填写：" + request.price());
            }
            gear.setPrice(request.price().setScale(2, RoundingMode.HALF_UP));
        }
        if (request.status() != null) {
            throw new BizException("器材状态由借出 / 归还 / 赔偿 / 维修操作驱动，不能通过编辑直接修改");
        }
        return toView(gearRepository.save(gear));
    }

    /** 损坏器材维修完成，回到在库 */
    @Transactional
    public GearView repairGear(Long id) {
        Gear gear = requireGear(id);
        if (!BilliardDict.GEAR_DAMAGED.equals(gear.getStatus())) {
            throw new BizException("器材「" + gear.getGearNo() + "」不在损坏状态，无需维修");
        }
        gear.setStatus(BilliardDict.GEAR_AVAILABLE);
        return toView(gearRepository.save(gear));
    }

    // ------------------------------------------------------------------
    // 租借
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RentalView> listRentals() {
        return rentalRepository.findAllByOrderByIdDesc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<RentalView> rentalsOfGear(Long gearId) {
        Gear gear = requireGear(gearId);
        return rentalRepository.findAllByOrderByIdDesc().stream()
                .filter(rental -> rental.getGearId().equals(gear.getId()))
                .map(this::toView)
                .toList();
    }

    /** 借出：已借出未还 / 已损坏 / 已报废 的器材都不能借 */
    @Transactional
    public RentalView rent(RentRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        if (request.gearId() == null) {
            throw new BizException("借出失败：请先选择要借出的器材");
        }
        Gear gear = requireGear(request.gearId());

        if (BilliardDict.GEAR_RENTED.equals(gear.getStatus())) {
            String rentalNo = rentalRepository
                    .findFirstByGearIdAndStatus(gear.getId(), BilliardDict.RENTAL_RENTED)
                    .map(GearRental::getRentalNo)
                    .orElse("未知");
            throw new BizException("器材「" + gear.getGearNo() + "」已借出未归还（单号 " + rentalNo
                    + "），借出未还禁止再借");
        }
        if (BilliardDict.GEAR_DAMAGED.equals(gear.getStatus())) {
            throw new BizException("器材「" + gear.getGearNo() + "」已损坏，维修完成前不能借出");
        }
        if (BilliardDict.GEAR_SCRAPPED.equals(gear.getStatus())) {
            throw new BizException("器材「" + gear.getGearNo() + "」已报废，不能借出");
        }

        Member member = null;
        if (request.memberId() != null) {
            member = memberService.require(request.memberId());
            if (BilliardDict.MEMBER_FROZEN.equals(member.getStatus())) {
                throw new BizException("会员卡「" + member.getCardNo() + "」已冻结，冻结卡不能借器材");
            }
        }

        GearRental rental = new GearRental();
        rental.setRentalNo(nextRentalNo());
        rental.setGearId(gear.getId());
        rental.setMemberId(member == null ? null : member.getId());
        rental.setRentTime(LocalDateTime.now());

        // 默认值只放在 service 的 create 里：押金默认取器材原价
        rental.setStatus(BilliardDict.RENTAL_RENTED);
        rental.setDeposit(request.deposit() == null
                ? gear.getPrice()
                : request.deposit().setScale(2, RoundingMode.HALF_UP));

        gear.setStatus(BilliardDict.GEAR_RENTED);
        gearRepository.save(gear);

        return toView(rentalRepository.save(rental));
    }

    /**
     * 归还：正常归还回在库；登记损坏则按赔偿金额结清，器材转损坏、维修完成前不能再借。
     */
    @Transactional
    public RentalView returnGear(Long id, ReturnRequest request) {
        GearRental rental = requireRental(id);
        if (BilliardDict.RENTAL_RETURNED.equals(rental.getStatus())) {
            throw new BizException("租借单「" + rental.getRentalNo() + "」已归还，不能重复归还");
        }
        if (BilliardDict.RENTAL_DAMAGED.equals(rental.getStatus())) {
            throw new BizException("租借单「" + rental.getRentalNo() + "」已按损坏赔偿结清，不能重复归还");
        }

        Gear gear = requireGear(rental.getGearId());
        boolean damaged = request != null && Boolean.TRUE.equals(request.damaged());
        LocalDateTime now = LocalDateTime.now();

        if (damaged) {
            if (request.damageFee() == null) {
                throw new BizException("请填写损坏赔偿金额");
            }
            BigDecimal fee = request.damageFee();
            if (fee.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("损坏赔偿金额必须大于 0 元，当前填写：" + fee + " 元");
            }
            if (fee.compareTo(gear.getPrice()) > 0) {
                throw new BizException("损坏赔偿金额不能超过器材原价 " + gear.getPrice()
                        + " 元，当前填写：" + fee + " 元");
            }
            rental.setDamageFee(fee.setScale(2, RoundingMode.HALF_UP));
            rental.setDamageNote(request.damageNote());
            rental.setStatus(BilliardDict.RENTAL_DAMAGED);
            gear.setStatus(BilliardDict.GEAR_DAMAGED);
        } else {
            rental.setStatus(BilliardDict.RENTAL_RETURNED);
            if (BilliardDict.GEAR_RENTED.equals(gear.getStatus())) {
                gear.setStatus(BilliardDict.GEAR_AVAILABLE);
            }
        }

        rental.setReturnTime(now);
        gearRepository.save(gear);
        return toView(rentalRepository.save(rental));
    }

    @Transactional(readOnly = true)
    public Gear requireGear(Long id) {
        if (id == null) {
            throw new BizException("请先选择器材");
        }
        return gearRepository.findById(id)
                .orElseThrow(() -> new BizException("器材不存在，ID：" + id));
    }

    @Transactional(readOnly = true)
    public GearRental requireRental(Long id) {
        if (id == null) {
            throw new BizException("请先选择租借单");
        }
        return rentalRepository.findById(id)
                .orElseThrow(() -> new BizException("租借单不存在，ID：" + id));
    }

    private String nextRentalNo() {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i <= 9999; i++) {
            String no = "R" + day + "-" + String.format("%04d", i);
            if (!rentalRepository.existsByRentalNo(no)) {
                return no;
            }
        }
        throw new BizException("今日租借单号已用尽");
    }

    private GearView toView(Gear gear) {
        Long currentRentalId = null;
        String currentRentalNo = null;
        String holderName = null;
        GearRental running = rentalRepository
                .findFirstByGearIdAndStatus(gear.getId(), BilliardDict.RENTAL_RENTED)
                .orElse(null);
        if (running != null) {
            currentRentalId = running.getId();
            currentRentalNo = running.getRentalNo();
            if (running.getMemberId() != null) {
                holderName = memberService.require(running.getMemberId()).getName();
            }
        }
        return new GearView(
                gear.getId(),
                gear.getGearNo(),
                gear.getGearName(),
                gear.getGearType(),
                BilliardDict.gearTypeName(gear.getGearType()),
                gear.getPrice(),
                gear.getStatus(),
                BilliardDict.gearStatusName(gear.getStatus()),
                gear.getRentCount(),
                currentRentalId,
                currentRentalNo,
                holderName);
    }

    private RentalView toView(GearRental rental) {
        Gear gear = gearRepository.findById(rental.getGearId()).orElse(null);
        Member member = rental.getMemberId() == null ? null
                : memberService.require(rental.getMemberId());
        return new RentalView(
                rental.getId(),
                rental.getRentalNo(),
                rental.getGearId(),
                gear == null ? null : gear.getGearNo(),
                gear == null ? null : gear.getGearName(),
                gear == null ? null : gear.getPrice(),
                rental.getMemberId(),
                member == null ? null : member.getName(),
                rental.getRentTime(),
                rental.getReturnTime(),
                rental.getStatus(),
                BilliardDict.rentalStatusName(rental.getStatus()),
                rental.getDeposit(),
                rental.getDamageFee(),
                rental.getDamageNote());
    }
}
