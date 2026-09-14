package io.radius.payment.api;

import io.radius.common.security.AuthUser;
import io.radius.payment.domain.Payment;
import io.radius.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Payments", description = "Fees, deposits and payouts. The fee reads zero until it does not.")
public class PaymentController {

    public record PaymentDto(UUID id, UUID requestId, long amountMinor, long feeMinor, long depositMinor,
                             String currency, String state, Instant createdAt) {}

    public record PayoutDto(UUID id, long amountMinor, long feeMinor, String currency, String period,
                            String state, String statementRef, Instant createdAt) {}

    public record WebhookPayload(@NotBlank String gatewayRef, @NotBlank String type) {}

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping("/api/fees/quote")
    @Operation(summary = "What Radius would charge on this amount today")
    public PaymentService.FeeQuote quote(@RequestParam(required = false) String kind,
                                         @RequestParam long amountMinor) {
        return payments.quote(kind, amountMinor);
    }

    @GetMapping("/api/payments/mine")
    @Operation(summary = "What the caller has earned")
    public List<PaymentDto> mine(AuthUser me) {
        return payments.earningsFor(me.id()).stream().map(PaymentController::toDto).toList();
    }

    @GetMapping("/api/payments/payouts")
    @Operation(summary = "Payout statements for the caller")
    public List<PayoutDto> payouts(AuthUser me) {
        return payments.payoutsFor(me.id()).stream()
                .map(p -> new PayoutDto(p.getId(), p.getAmountMinor(), p.getFeeMinor(), p.getCurrency(),
                        p.getPeriod(), p.getState(), p.getStatementRef(), p.getCreatedAt()))
                .toList();
    }

    /**
     * The gateway calls this, not a member — so it is not behind the member
     * token. A real deployment verifies the provider's signature header here
     * before doing anything else.
     */
    @PostMapping("/api/webhooks/gateway")
    @Operation(summary = "Gateway callback — idempotent on gatewayRef")
    public ResponseEntity<Void> webhook(@RequestBody WebhookPayload body) {
        payments.handleWebhook(body.gatewayRef(), body.type(), body.toString());
        return ResponseEntity.accepted().build();
    }

    private static PaymentDto toDto(Payment p) {
        return new PaymentDto(p.getId(), p.getRequestId(), p.getAmountMinor(), p.getFeeMinor(),
                p.getDepositMinor(), p.getCurrency(), p.getState(), p.getCreatedAt());
    }
}
