package io.radius.search.parse;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The parser is the part of search most likely to quietly get worse. Pin it down. */
class QueryParserTest {

    private final QueryParser parser = new QueryParser();

    @Test
    void reads_the_sentence_from_the_spec() {
        var parsed = parser.parse("I need someone who knows video editing and lives within 5 km");

        assertThat(parsed.tags()).contains("video-editing");
        assertThat(parsed.radiusKm()).isEqualTo(5);
        assertThat(parsed.terms()).doesNotContain("i", "need", "who", "within");
    }

    @Test
    void understands_metres_and_minutes() {
        assertThat(parser.parse("drill within 800 m").radiusKm()).isEqualTo(1);
        assertThat(parser.parse("ladder within 30 minutes").radiusKm()).isEqualTo(5);
    }

    @Test
    void picks_up_day_words() {
        assertThat(parser.parse("pressure washer tomorrow").day()).isEqualTo(LocalDate.now().plusDays(1));
        assertThat(parser.parse("projector today").day()).isEqualTo(LocalDate.now());
        assertThat(parser.parse("a ladder").day()).isNull();
    }

    @Test
    void infers_the_kind_from_the_verb() {
        assertThat(parser.parse("who can teach guitar").kinds()).contains("TEACHING");
        assertThat(parser.parse("want to buy a printer").kinds()).contains("SELL_ITEM");
    }

    @Test
    void does_not_match_a_word_inside_another_word() {
        // "van" must not fire on "advanced"
        assertThat(parser.parse("advanced photography course").kinds()).doesNotContain("SPACE_OR_VEHICLE");
    }

    @Test
    void an_unreadable_sentence_parses_to_empty() {
        // Nothing long enough to be a term, no tag, no kind, no distance, no day.
        var parsed = parser.parse("zz qq");
        assertThat(parsed.isEmpty()).isTrue();
    }
}
