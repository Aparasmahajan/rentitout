package io.radius.payment.api;

import io.radius.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** booking-service asks for the fee line here. Not routed by the gateway. */
@RestController
@RequestMapping("/internal/fees")
@Tag(name = "Internal", description = "Not exposed through the gateway")
public class InternalFeeController {

    private final PaymentService payments;

    public InternalFeeController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping("/quote")
    public PaymentService.FeeQuote quote(@RequestParam(required = false) String kind,
                                         @RequestParam long amountMinor) {
        return payments.quote(kind, amountMinor);
    }
}
