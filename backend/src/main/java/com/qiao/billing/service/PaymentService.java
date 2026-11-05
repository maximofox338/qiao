package com.qiao.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiao.billing.domain.Payment;
import com.qiao.billing.domain.PaymentMapper;
import com.qiao.billing.domain.PaymentProviderType;
import com.qiao.billing.domain.PaymentStatus;
import com.qiao.billing.web.CheckoutResponse;
import com.qiao.billing.web.PaymentView;
import com.qiao.client.service.CompanyService;
import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import com.qiao.common.money.Money;
import com.qiao.compliance.service.Actor;
import com.qiao.compliance.service.AuditAction;
import com.qiao.compliance.service.AuditService;
import com.qiao.contract.service.MilestoneRef;
import com.qiao.contract.service.MilestoneService;
import com.qiao.project.service.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Milestone prepayments: checkout → PAID (fake confirmation or Stripe webhook) → milestone FUNDED.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentMapper paymentMapper;
    private final PaymentGateway gateway;
    private final MilestoneService milestoneService;
    private final ProjectService projectService;
    private final CompanyService companyService;
    private final AuditService auditService;
    private final Clock clock;

    public PaymentService(PaymentMapper paymentMapper, PaymentGateway gateway, MilestoneService milestoneService,
                          ProjectService projectService, CompanyService companyService, AuditService auditService,
                          Clock clock) {
        this.paymentMapper = paymentMapper;
        this.gateway = gateway;
        this.milestoneService = milestoneService;
        this.projectService = projectService;
        this.companyService = companyService;
        this.auditService = auditService;
        this.clock = clock;
    }

    public PaymentProviderType providerType() {
        return gateway.type();
    }

    @Transactional
    public CheckoutResponse checkout(long clientUserId, long milestoneId) {
        MilestoneRef milestone = milestoneService.requireFundable(clientUserId, milestoneId);
        Payment payment = new Payment();
        payment.setContractMilestoneId(milestoneId);
        payment.setProvider(gateway.type());
        payment.setAmountUsd(milestone.clientAmountUsd());
        payment.setStatus(PaymentStatus.PENDING);
        paymentMapper.insert(payment);
        PaymentGateway.CheckoutSession session = gateway.createCheckout(new PaymentGateway.CheckoutRequest(payment,
                milestone.contractId(), milestone.seq(), projectService.get(milestone.projectId()).getTitle()));
        payment.setProviderRef(session.providerRef());
        paymentMapper.updateById(payment);
        return new CheckoutResponse(payment.getId(), session.url());
    }

    /**
     * Confirms a fake payment; only available while {@code PAYMENT_PROVIDER=fake}.
     */
    @Transactional
    public PaymentView confirmFake(long clientUserId, long paymentId) {
        if (gateway.type() != PaymentProviderType.FAKE) {
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_NOT_FAKE);
        }
        Payment payment = lockOwned(clientUserId, paymentId);
        if (payment.getProvider() != PaymentProviderType.FAKE) {
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_NOT_FAKE);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ApiException(ErrorCode.PAYMENT_ALREADY_PAID);
        }
        markPaid(payment, Actor.user(clientUserId));
        return view(payment);
    }

    @Transactional
    public void handleStripeWebhook(String payload, String signatureHeader) {
        gateway.paidCheckoutRef(payload, signatureHeader).ifPresent(sessionId -> {
            Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                    .eq(Payment::getProvider, PaymentProviderType.STRIPE)
                    .eq(Payment::getProviderRef, sessionId)
                    .last("FOR UPDATE"));
            if (payment == null) {
                log.warn("Stripe webhook for unknown checkout session {}", sessionId);
                return;
            }
            markPaid(payment, Actor.system());
        });
    }

    public PaymentView get(long clientUserId, long paymentId) {
        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw ApiException.notFound("Payment");
        }
        requireOwner(clientUserId, payment);
        return view(payment);
    }

    /**
     * Refunds the paid payment of a milestone through the provider (dispute resolved as REFUND).
     */
    @Transactional
    public void refundMilestone(long milestoneId, long adminId) {
        paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getContractMilestoneId, milestoneId)
                        .eq(Payment::getStatus, PaymentStatus.PAID)
                        .last("FOR UPDATE"))
                .forEach(payment -> {
                    gateway.refund(payment);
                    payment.setStatus(PaymentStatus.REFUNDED);
                    paymentMapper.updateById(payment);
                    auditService.log(Actor.admin(adminId), AuditAction.PAYMENT_REFUNDED, "payment", payment.getId(),
                            Map.of("amountUsd", payment.getAmountUsd()));
                });
    }

    public Set<Long> paidMilestoneIds() {
        return paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                        .select(Payment::getContractMilestoneId)
                        .eq(Payment::getStatus, PaymentStatus.PAID))
                .stream().map(Payment::getContractMilestoneId).collect(Collectors.toSet());
    }

    /**
     * Gross merchandise value (paid, not refunded) and the platform's share of it.
     */
    public Totals totals() {
        List<Payment> paid = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getStatus, PaymentStatus.PAID));
        BigDecimal gmv = Money.sum(paid.stream().map(Payment::getAmountUsd).toList());
        BigDecimal talent = Money.sum(milestoneService.refs(paid.stream().map(Payment::getContractMilestoneId)
                .collect(Collectors.toSet())).stream().map(MilestoneRef::talentAmountUsd).toList());
        return new Totals(gmv, gmv.subtract(talent));
    }

    public record Totals(BigDecimal gmvUsd, BigDecimal feesUsd) {
    }

    private void markPaid(Payment payment, Actor actor) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(clock.instant());
        paymentMapper.updateById(payment);
        if (!milestoneService.markFunded(payment.getContractMilestoneId())) {
            log.warn("Payment {} paid but milestone {} was not awaiting funds; refund manually",
                    payment.getId(), payment.getContractMilestoneId());
        }
        auditService.log(actor, AuditAction.PAYMENT_CONFIRMED, "payment", payment.getId(),
                Map.of("milestoneId", payment.getContractMilestoneId(), "amountUsd", payment.getAmountUsd()));
    }

    private PaymentView view(Payment payment) {
        return PaymentView.from(payment, milestoneService.ref(payment.getContractMilestoneId()).contractId());
    }

    private Payment lockOwned(long clientUserId, long paymentId) {
        Payment payment = paymentMapper.selectByIdForUpdate(paymentId);
        if (payment == null) {
            throw ApiException.notFound("Payment");
        }
        requireOwner(clientUserId, payment);
        return payment;
    }

    private void requireOwner(long clientUserId, Payment payment) {
        MilestoneRef milestone = milestoneService.ref(payment.getContractMilestoneId());
        if (!companyService.isMember(clientUserId, milestone.companyId())) {
            throw ApiException.notFound("Payment");
        }
    }
}
