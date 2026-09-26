package com.billiard.club.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billiard.club.common.BizException;
import com.billiard.club.dict.BilliardDict;
import com.billiard.club.domain.Member;
import com.billiard.club.domain.MemberRepository;
import com.billiard.club.domain.RechargeRecord;
import com.billiard.club.domain.RechargeRecordRepository;
import com.billiard.club.domain.TableSessionRepository;
import com.billiard.club.dto.MemberRequest;
import com.billiard.club.dto.RechargeRequest;
import com.billiard.club.dto.view.MemberView;
import com.billiard.club.dto.view.RechargeView;

/**
 * 模块四：会员与充值。
 * 规则：卡号唯一 / 余额不足拦（开局与余额结算）/ 冻结卡禁开局与禁充值 /
 * 单次充值区间 100~5000 / 等级由累计充值只升不降 / 有进行中的局不能冻结。
 */
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final RechargeRecordRepository rechargeRepository;
    private final TableSessionRepository sessionRepository;

    public MemberService(MemberRepository memberRepository,
                         RechargeRecordRepository rechargeRepository,
                         TableSessionRepository sessionRepository) {
        this.memberRepository = memberRepository;
        this.rechargeRepository = rechargeRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public List<MemberView> list() {
        return memberRepository.findAllByOrderByCardNoAsc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public MemberView detail(Long id) {
        return toView(require(id));
    }

    @Transactional(readOnly = true)
    public List<RechargeView> recharges(Long memberId) {
        Member member = require(memberId);
        return rechargeRepository.findByMemberIdOrderByIdDesc(member.getId()).stream()
                .map(record -> toView(record, member))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RechargeView> allRecharges() {
        return rechargeRepository.findAllByOrderByIdDesc().stream()
                .map(record -> toView(record, memberRepository.findById(record.getMemberId()).orElse(null)))
                .toList();
    }

    @Transactional
    public MemberView create(MemberRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        if (request.cardNo() == null || request.cardNo().isBlank()) {
            throw new BizException("会员卡号不能为空");
        }
        String cardNo = request.cardNo().trim();
        if (memberRepository.existsByCardNo(cardNo)) {
            throw new BizException("卡号「" + cardNo + "」已存在，卡号必须唯一");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new BizException("会员姓名不能为空");
        }
        if (request.level() != null && !"NORMAL".equals(request.level())) {
            throw new BizException("会员等级由累计充值自动升级，新建会员只能从普通会员（NORMAL）开始");
        }
        if (request.status() != null && !BilliardDict.MEMBER_NORMAL.equals(request.status())) {
            if (!BilliardDict.isValidMemberStatus(request.status())) {
                throw new BizException("会员卡状态不合法：" + request.status() + "，只支持 NORMAL / FROZEN");
            }
            throw new BizException("新建会员卡不能直接置为冻结状态，请创建后用冻结操作处理");
        }

        Member member = new Member();
        member.setCardNo(cardNo);
        member.setName(request.name().trim());
        member.setPhone(request.phone());

        // 默认值只放在 service 的 create 里，实体上不写默认值
        member.setLevel("NORMAL");
        member.setBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        member.setTotalRecharge(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        member.setStatus(BilliardDict.MEMBER_NORMAL);
        member.setJoinedAt(LocalDate.now());

        return toView(memberRepository.save(member));
    }

    @Transactional
    public MemberView update(Long id, MemberRequest request) {
        Member member = require(id);
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        if (request.cardNo() != null && !request.cardNo().isBlank()) {
            String cardNo = request.cardNo().trim();
            if (!cardNo.equals(member.getCardNo())) {
                if (memberRepository.existsByCardNo(cardNo)) {
                    throw new BizException("卡号「" + cardNo + "」已存在，卡号必须唯一");
                }
                member.setCardNo(cardNo);
            }
        }
        if (request.name() != null && !request.name().isBlank()) {
            member.setName(request.name().trim());
        }
        if (request.phone() != null) {
            member.setPhone(request.phone());
        }
        if (request.level() != null) {
            throw new BizException("会员等级由累计充值自动升级，不能手工修改");
        }
        if (request.status() != null) {
            throw new BizException("会员卡状态由冻结 / 解冻操作驱动，不能通过编辑直接修改");
        }
        return toView(memberRepository.save(member));
    }

    /** 充值：校验区间与冻结状态，按累计充值只升不降地升级，并留一条流水 */
    @Transactional
    public RechargeView recharge(Long id, RechargeRequest request) {
        Member member = require(id);
        if (request == null || request.amount() == null) {
            throw new BizException("请填写充值金额");
        }
        BigDecimal amount = request.amount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("充值金额必须大于 0 元，当前填写：" + amount + " 元");
        }
        if (amount.compareTo(BilliardDict.RECHARGE_MIN) < 0 || amount.compareTo(BilliardDict.RECHARGE_MAX) > 0) {
            throw new BizException("单次充值金额必须在 " + BilliardDict.RECHARGE_MIN + " ~ "
                    + BilliardDict.RECHARGE_MAX + " 元之间，当前填写：" + amount + " 元");
        }
        if (BilliardDict.MEMBER_FROZEN.equals(member.getStatus())) {
            throw new BizException("会员卡「" + member.getCardNo() + "」已冻结，冻结卡不能充值，请先解冻");
        }

        String payMode = request.payMode() == null ? BilliardDict.PAY_CASH : request.payMode();
        if (!BilliardDict.isValidPayMode(payMode)) {
            throw new BizException("支付方式不合法：" + payMode + "，只支持 BALANCE / CASH / WECHAT / CARD");
        }

        String beforeLevel = member.getLevel();
        BigDecimal beforeBalance = member.getBalance() == null ? BigDecimal.ZERO : member.getBalance();

        RechargeRecord record = new RechargeRecord();
        record.setMemberId(member.getId());
        record.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        record.setBeforeBalance(beforeBalance);
        record.setAfterBalance(beforeBalance.add(amount).setScale(2, RoundingMode.HALF_UP));
        record.setBeforeLevel(beforeLevel);
        record.setPayMode(payMode);

        member.setBalance(beforeBalance.add(amount).setScale(2, RoundingMode.HALF_UP));
        BigDecimal totalRecharge = (member.getTotalRecharge() == null ? BigDecimal.ZERO : member.getTotalRecharge())
                .add(amount).setScale(2, RoundingMode.HALF_UP);
        member.setTotalRecharge(totalRecharge);

        // 等级只升不降
        String earned = BilliardDict.levelByTotalRecharge(totalRecharge);
        if (BilliardDict.memberLevelRank(earned) > BilliardDict.memberLevelRank(beforeLevel)) {
            member.setLevel(earned);
        }
        record.setAfterLevel(member.getLevel());

        memberRepository.save(member);
        RechargeRecord saved = rechargeRepository.save(record);
        return toView(saved, member);
    }

    /** 冻结：还有进行中的局就先收尾 */
    @Transactional
    public MemberView freeze(Long id) {
        Member member = require(id);
        if (BilliardDict.MEMBER_FROZEN.equals(member.getStatus())) {
            throw new BizException("会员卡「" + member.getCardNo() + "」已经是冻结状态");
        }
        long active = sessionRepository.countByMemberIdAndStatus(member.getId(), BilliardDict.SESSION_OPEN);
        if (active > 0) {
            throw new BizException("会员「" + member.getName() + "」还有 " + active
                    + " 局进行中，请先结束并结算再冻结会员卡");
        }
        member.setStatus(BilliardDict.MEMBER_FROZEN);
        return toView(memberRepository.save(member));
    }

    @Transactional
    public MemberView unfreeze(Long id) {
        Member member = require(id);
        if (BilliardDict.MEMBER_NORMAL.equals(member.getStatus())) {
            throw new BizException("会员卡「" + member.getCardNo() + "」未被冻结，无需解冻");
        }
        member.setStatus(BilliardDict.MEMBER_NORMAL);
        return toView(memberRepository.save(member));
    }

    /** 其他 service 复用的取会员方法，顺带把 null 挡在库前面 */
    @Transactional(readOnly = true)
    public Member require(Long id) {
        if (id == null) {
            throw new BizException("请先选择会员卡");
        }
        return memberRepository.findById(id)
                .orElseThrow(() -> new BizException("会员不存在，ID：" + id));
    }

    private MemberView toView(Member member) {
        return new MemberView(
                member.getId(),
                member.getCardNo(),
                member.getName(),
                member.getPhone(),
                member.getLevel(),
                BilliardDict.memberLevelName(member.getLevel()),
                BilliardDict.memberDiscount(member.getLevel()),
                member.getBalance(),
                member.getTotalRecharge(),
                member.getStatus(),
                BilliardDict.memberStatusName(member.getStatus()),
                member.getJoinedAt(),
                member.getActiveOrderCount());
    }

    private RechargeView toView(RechargeRecord record, Member member) {
        return new RechargeView(
                record.getId(),
                record.getMemberId(),
                member == null ? null : member.getCardNo(),
                member == null ? null : member.getName(),
                record.getAmount(),
                record.getBeforeBalance(),
                record.getAfterBalance(),
                record.getBeforeLevel(),
                record.getAfterLevel(),
                record.getPayMode(),
                BilliardDict.payModeName(record.getPayMode()),
                record.getCreatedAt());
    }
}
