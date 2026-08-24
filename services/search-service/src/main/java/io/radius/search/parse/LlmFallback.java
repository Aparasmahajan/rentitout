package io.radius.search.parse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The escape hatch for sentences the rules cannot read. It is deliberately
 * behind an interface and off by default: every call costs money and latency,
 * and the rules handle the overwhelming majority of real queries.
 *
 * When you wire a provider, cache by normalised query string — see
 * {@code SearchService#parse}, which already does the caching around this call.
 */
public interface LlmFallback {

    Optional<QueryParser.Parsed> parse(String sentence);

    /** The default. Logs what the rules could not read, which is the list to grow synonyms from. */
    @Component
    @ConditionalOnProperty(prefix = "radius.search.llm", name = "enabled", havingValue = "false",
            matchIfMissing = true)
    class Disabled implements LlmFallback {

        private static final Logger log = LoggerFactory.getLogger(Disabled.class);

        @Override
        public Optional<QueryParser.Parsed> parse(String sentence) {
            log.info("unparsed query (candidate for a new synonym): {}", sentence);
            return Optional.empty();
        }
    }
}
