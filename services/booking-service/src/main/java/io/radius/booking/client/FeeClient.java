package io.radius.booking.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The fee line exists from Phase 03 and reads zero until Phase 05 turns it on.
 * No code path assumes a non-zero fee, and none assumes a zero one either — it
 * is always asked for and always displayed.
 */
@Component
public class FeeClient {

    private static final Logger log = LoggerFactory.getLogger(FeeClient.class);

    public record FeeQuote(long feeMinor, String label) {}

    private final RestClient http;

    public FeeClient(RestClient.Builder builder, @Value("${radius.clients.payment-url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    @CircuitBreaker(name = "payment", fallbackMethod = "noFee")
    public FeeQuote quote(String kind, long amountMinor) {
        FeeQuote quote = http.get()
                .uri(uri -> uri.path("/internal/fees/quote")
                        .queryParam("kind", kind)
                        .queryParam("amountMinor", amountMinor).build())
                .retrieve()
                .body(FeeQuote.class);
        return quote == null ? new FeeQuote(0, "Radius fee: none for now") : quote;
    }

    @SuppressWarnings("unused")   // resilience4j resolves this by name
    private FeeQuote noFee(String kind, long amountMinor, Throwable t) {
        log.warn("payment-service unavailable; quoting a zero fee", t);
        return new FeeQuote(0, "Radius fee: none for now");
    }
}
