package io.radius.payment.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.radius.common.events.RadiusEvents;
import io.radius.common.events.Topics;
import io.radius.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Acceptance opens the payment and authorises the deposit; completion releases
 * it. Both handlers are idempotent through unique constraints in the database
 * rather than a cache — this is money.
 */
@Component
public class RequestEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RequestEventConsumer.class);

    private final PaymentService payments;
    private final ObjectMapper mapper;

    public RequestEventConsumer(PaymentService payments, ObjectMapper mapper) {
        this.payments = payments;
        this.mapper = mapper;
    }

    @KafkaListener(topics = Topics.REQUEST, groupId = "payment-service.request")
    public void onRequestEvent(@Payload String payload,
                               @Header(name = Topics.HEADER_EVENT_TYPE, required = false) String type)
            throws Exception {
        switch (type == null ? "" : type) {
            case "RequestAccepted" -> {
                var e = mapper.readValue(payload, RadiusEvents.RequestAccepted.class);
                payments.openPayment(e.requestId(), e.requesterId(), e.ownerId(), e.amountMinor(),
                        e.depositMinor(), "INR", null);
                log.info("payment opened for accepted request {}", e.requestId());
            }
            case "RequestCompleted" -> {
                var e = mapper.readValue(payload, RadiusEvents.RequestCompleted.class);
                payments.releaseDeposit(e.requestId());
            }
            case "RequestCancelled" -> {
                var e = mapper.readValue(payload, RadiusEvents.RequestCancelled.class);
                payments.releaseDeposit(e.requestId());
            }
            default -> log.debug("ignoring request event of type {}", type);
        }
    }
}
