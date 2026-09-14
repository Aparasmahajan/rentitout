package io.radius.listing.repo;

/**
 * The two numbers denormalised onto a listing. A constructor expression rather
 * than an {@code Object[]}, because the array form silently changes shape
 * between a single-row and multi-row query and fails at cast time, not compile
 * time.
 */
public record RatingAggregate(Double average, Long count) {

    public double averageOrZero() { return average == null ? 0d : average; }

    public int countOrZero() { return count == null ? 0 : count.intValue(); }
}
