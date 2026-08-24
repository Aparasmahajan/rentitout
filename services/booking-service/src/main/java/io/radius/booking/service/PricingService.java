package io.radius.booking.service;

import io.radius.booking.api.Dtos;
import io.radius.booking.client.FeeClient;
import io.radius.booking.client.ListingClient;
import io.radius.common.web.ApiException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * All the arithmetic about money, in one class, in integer minor units. No
 * double, no BigDecimal rounding surprises, no client-side totals.
 */
@Service
public class PricingService {

    private final FeeClient fees;

    public PricingService(FeeClient fees) {
        this.fees = fees;
    }

    public Dtos.Breakdown quote(ListingClient.ListingSnapshot listing, int units) {
        if (listing.priceMinor() == null) {
            throw ApiException.badRequest("not_bookable", "That listing has no rate to book against");
        }
        long amount = Math.multiplyExact(listing.priceMinor(), (long) units);
        FeeClient.FeeQuote fee = fees.quote(listing.kind(), amount);
        long total = amount + listing.depositMinor() + fee.feeMinor();

        String note = fee.feeMinor() == 0
                ? "Settled between you at handover"
                : "Paid in the app when the owner accepts";

        return new Dtos.Breakdown(listing.priceMinor(), listing.unit(), units, amount,
                listing.depositMinor(), fee.feeMinor(), fee.label(), total, listing.currency(), note);
    }

    /** A DAY listing booked for three units runs three days from the start date. */
    public LocalDate endDate(LocalDate start, String unit, int units) {
        return switch (unit == null ? "DAY" : unit) {
            case "DAY" -> start.plusDays(units - 1L);
            case "WEEK" -> start.plusWeeks(units).minusDays(1);
            // Hours, sessions and single items all happen on the one day.
            default -> start;
        };
    }
}
