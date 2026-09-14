package io.radius.payment.service;

import io.radius.common.events.RadiusEvents;
import io.radius.common.events.Topics;
import io.radius.common.outbox.OutboxService;
import io.radius.common.web.ApiException;
import io.radius.payment.domain.DepositHold;
import io.radius.payment.domain.FeeConfig;
import io.radius.payment.domain.GatewayEvent;
import io.radius.payment.domain.Payment;
import io.radius.payment.domain.Payout;
import io.radius.payment.repo.DepositHoldRepository;
import io.radius.payment.repo.FeeConfigRepository;
import io.radius.payment.repo.GatewayEventRepository;
import io.radius.payment.repo.PaymentRepository;
import io.radius.payment.repo.PayoutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Phase 05 in the shape it will ship in, with the fee reading zero and a
 * sandbox gateway standing in for the real one.
 *
 * Two rules the code is built around:
 *   - the webhook is the source of truth, not our optimistic local state;
 *   - every write is idempotent on the gateway reference.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final FeeConfigRepository feeConfigs;
    private final PaymentRepository payments;
    private final DepositHoldRepository holds;
    private final PayoutRepository payouts;
    private final GatewayEventRepository gatewayEvents;
    private final OutboxService outbox;

    public PaymentService(FeeConfigRepository feeConfigs, PaymentRepository payments,
                          DepositHoldRepository holds, PayoutRepository payouts,
                          GatewayEventRepository gatewayEvents, OutboxService outbox) {
        this.feeConfigs = feeConfigs;
        this.payments = payments;
        this.holds = holds;
        this.payouts = payouts;
        this.gatewayEvents = gatewayEvents;
        this.outbox = outbox;
    }

    public record FeeQuote(long feeMinor, String label) {}

    /**
     * Read at request time and stored on the payment row, so changing the fee
     * later never re-prices an old request.
     */
    @Transactional(readOnly = true)
    public FeeQuote quote(String kind, long amountMinor) {
        FeeConfig config = feeConfigs.findByKind(kind)
                .or(feeConfigs::findFirstByKindIsNull)
                .orElse(null);
        long fee = config == null ? 0 : config.feeFor(amountMinor);
        return new FeeQuote(fee, fee == 0 ? "Radius fee: none for now" : "Radius fee");
    }

    /**
     * Called when a request is accepted. The unique index on request_id is what
     * makes a redelivered RequestAccepted event harmless.
     */
    @Transactional
    public Payment openPayment(UUID requestId, UUID payerId, UUID payeeId, long amountMinor,
                               long depositMinor, String currency, String kind) {
        return payments.findByRequestId(requestId).orElseGet(() -> {
            long fee = quote(kind, amountMinor).feeMinor();
            Payment payment = payments.save(new Payment(requestId, payerId, payeeId, amountMinor, fee,
                    depositMinor, currency, sandboxRef(requestId)));

            if (depositMinor > 0) {
                holds.findByRequestId(requestId)
                        .orElseGet(() -> holds.save(new DepositHold(requestId, payerId, depositMinor)));
            }
            log.info("payment {} opened for request {} (fee {})", payment.getId(), requestId, fee);
            return payment;
        });
    }

    /**
     * The webhook. Duplicate deliveries are the norm, not the exception, so the
     * first thing it does is ask whether this reference was already handled.
     */
    @Transactional
    public void handleWebhook(String gatewayRef, String eventType, String rawPayload) {
        if (gatewayEvents.existsByGatewayRef(gatewayRef)) {
            log.info("gateway event {} already processed", gatewayRef);
            return;
        }
        gatewayEvents.save(new GatewayEvent(gatewayRef, eventType, rawPayload));

        Payment payment = payments.findByGatewayRef(gatewayRef)
                .orElseThrow(() -> ApiException.notFound("Payment for " + gatewayRef));

        switch (eventType) {
            case "payment.authorised" -> payment.moveTo(Payment.State.AUTHORISED);
            case "payment.settled" -> {
                payment.moveTo(Payment.State.SETTLED);
                outbox.publish(Topics.PAYMENT, payment.getRequestId(), new RadiusEvents.PaymentSettled(
                        payment.getId(), payment.getRequestId(), payment.getPayerId(), payment.getPayeeId(),
                        payment.getAmountMinor(), payment.getFeeMinor(), "SETTLED", Instant.now()));
            }
            case "payment.failed" -> payment.moveTo(Payment.State.FAILED);
            case "payment.refunded" -> payment.moveTo(Payment.State.REFUNDED);
            default -> log.warn("unknown gateway event type {}", eventType);
        }
    }

    /** On completion the deposit goes back. A claim, if any, is an admin decision. */
    @Transactional
    public void releaseDeposit(UUID requestId) {
        holds.findByRequestId(requestId).ifPresent(hold -> {
            if (hold.getReleasedAt() != null) return;
            if ("OPEN".equals(hold.getClaimState())) {
                log.info("deposit for {} stays held: a claim is open", requestId);
                return;
            }
            hold.release();
            outbox.publish(Topics.PAYMENT, requestId, new RadiusEvents.DepositReleased(
                    requestId, hold.getPayerId(), hold.getAmountMinor(), Instant.now()));
            log.info("deposit released for request {}", requestId);
        });
    }

    @Transactional(readOnly = true)
    public List<Payout> payoutsFor(UUID ownerId) {
        return payouts.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @Transactional(readOnly = true)
    public List<Payment> earningsFor(UUID ownerId) {
        return payments.findByPayeeIdOrderByCreatedAtDesc(ownerId);
    }

    /**
     * Rolls settled payments into one payout per owner per month. Idempotent by
     * the (owner, period) unique index, so running it twice changes nothing.
     */
    @Transactional
    public Payout closePeriod(UUID ownerId, YearMonth period) {
        String key = period.toString();
        return payouts.findByOwnerIdAndPeriod(ownerId, key).orElseGet(() -> {
            List<Payment> settled = payments.findByPayeeIdAndState(ownerId, Payment.State.SETTLED.name());
            long gross = settled.stream().mapToLong(Payment::getAmountMinor).sum();
            long fees = settled.stream().mapToLong(Payment::getFeeMinor).sum();
            String currency = settled.isEmpty() ? "INR" : settled.get(0).getCurrency();
            return payouts.save(new Payout(ownerId, gross - fees, fees, currency, key));
        });
    }

    /**
     * Stands in for the gateway's reference. A real integration replaces this
     * with the id the gateway returns when the intent is created — nothing else
     * in this class changes.
     */
    private static String sandboxRef(UUID requestId) {
        return "sandbox_" + requestId;
    }
}
