package io.radius.booking.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class Dtos {

    public record CreateRequest(
            @NotNull UUID listingId,
            @NotNull @Future LocalDate startDate,
            @Min(1) @Max(365) int units,
            @Size(max = 500) String message) {}

    /**
     * Computed on the server, always. The client renders these lines and never
     * does the arithmetic — that is how the Phase 05 fee switch changes nothing
     * on the client but the words under the total.
     */
    public record Breakdown(long rateMinor, String unit, int units, long amountMinor,
                            long depositMinor, long feeMinor, String feeLabel, long totalMinor,
                            String currency, String settlementNote) {}

    public record RequestResponse(UUID id, UUID listingId, String listingTitle, UUID requesterId,
                                  UUID ownerId, LocalDate startDate, LocalDate endDate, int units,
                                  String unit, String message, String status, Breakdown breakdown,
                                  Instant createdAt, Instant expiresAt, long unreadCount,
                                  boolean iAmOwner, List<TransitionDto> history) {}

    public record TransitionDto(String from, String to, UUID actorId, String reason, Instant at) {}

    public record QuoteRequest(@NotNull UUID listingId, @NotNull LocalDate startDate,
                               @Min(1) @Max(365) int units) {}

    public record DeclineRequest(@Size(max = 200) String reason) {}

    public record MessageDto(UUID id, UUID requestId, UUID senderId, String body, Instant sentAt,
                             Instant readAt) {}

    public record PostMessageRequest(@NotBlank @Size(max = 2000) String body) {}

    private Dtos() {}
}
