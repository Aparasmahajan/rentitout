package io.radius.search.parse;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rules first: a synonym dictionary over the tag vocabulary, a distance regex
 * and a date-word list. Cheap, instant, debuggable, and it handles the sentences
 * people actually type. A language model is the fallback for the rest — see
 * {@link io.radius.search.parse.LlmFallback}.
 *
 * The parse is returned to the client so it can show removable chips: the
 * member can see what we understood and correct it.
 */
@Component
public class QueryParser {

    /** What we extracted. Everything is optional — an empty parse is a valid outcome. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Parsed(List<String> terms, List<String> tags, List<String> kinds,
                         Integer radiusKm, LocalDate day, boolean freeText) {

        public boolean isEmpty() {
            return terms.isEmpty() && tags.isEmpty() && kinds.isEmpty() && radiusKm == null && day == null;
        }
    }

    /**
     * Alternation in Java is ordered, so the longer units have to come first:
     * with "m" earlier in the list, "within 30 minutes" matches "m" and becomes
     * 30 metres. The trailing \b stops "5 km" from matching inside a word.
     */
    private static final Pattern DISTANCE = Pattern.compile(
            "(?:within|inside|under|less than|max)\\s+(\\d{1,3})\\s*"
                    + "(kilometres|kilometers|kilometre|kilometer|minutes|mins|metres|meters|min|km|m)?\\b",
            Pattern.CASE_INSENSITIVE);

    /** Words that are noise in a two-word query and hide the signal in a long one. */
    private static final Set<String> STOP = Set.of(
            "i", "need", "want", "looking", "for", "someone", "who", "knows", "can", "with", "a", "an",
            "the", "to", "of", "in", "on", "at", "and", "or", "my", "me", "is", "are", "please", "help",
            "near", "nearby", "close", "around", "here", "lives", "live", "within", "km", "any", "have");

    /** Tag slug -> the words that should find it. Grown from real queries, not guessed once. */
    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("video-editing", List.of("video", "editing", "editor", "premiere", "davinci", "footage")),
            Map.entry("photography", List.of("photo", "photos", "photographer", "camera", "headshot", "shoot")),
            Map.entry("electrical", List.of("electrician", "electrical", "wiring", "socket", "fuse", "light")),
            Map.entry("plumbing", List.of("plumber", "plumbing", "tap", "leak", "sink", "pipe", "boiler")),
            Map.entry("carpentry", List.of("carpenter", "carpentry", "wood", "shelf", "shelves", "furniture")),
            Map.entry("gardening", List.of("garden", "gardening", "gardener", "plants", "hedge", "lawn", "mower")),
            Map.entry("tutoring-maths", List.of("maths", "math", "tutor", "tutoring", "algebra", "calculus")),
            Map.entry("guitar", List.of("guitar", "guitarist", "strings", "acoustic")),
            Map.entry("yoga", List.of("yoga", "stretching", "pilates")),
            Map.entry("cooking", List.of("cook", "cooking", "baking", "chef", "kitchen")),
            Map.entry("coding", List.of("coding", "programming", "developer", "python", "javascript")),
            Map.entry("dog-walking", List.of("dog", "dogs", "walking", "walker", "puppy")),
            Map.entry("childcare", List.of("childcare", "babysitter", "babysitting", "nanny", "kids")),
            Map.entry("moving-help", List.of("moving", "move", "boxes", "removal", "van", "lifting")),
            Map.entry("ladder", List.of("ladder", "ladders", "stepladder")),
            Map.entry("drill", List.of("drill", "drilling", "hammer drill", "sds")),
            Map.entry("pressure-washer", List.of("pressure", "washer", "jetwash", "karcher")),
            Map.entry("camping-gear", List.of("camping", "tent", "sleeping bag", "roll mat")),
            Map.entry("projector", List.of("projector", "beamer", "screen", "film night")),
            Map.entry("printer", List.of("printer", "printing", "scanner")),
            Map.entry("car-roof-box", List.of("roof box", "roofbox", "roof", "luggage box")),
            Map.entry("party-tent", List.of("party tent", "gazebo", "marquee", "pavilion")),
            Map.entry("sewing-machine", List.of("sewing", "machine", "overlocker", "hemming")));

    /** Words that point at one of the seven kinds. */
    private static final Map<String, List<String>> KIND_WORDS = Map.of(
            "RENT_ITEM", List.of("rent", "renting", "borrow", "hire an item", "lend"),
            "SELL_ITEM", List.of("buy", "sell", "selling", "second hand", "used"),
            "SKILL_FOR_HIRE", List.of("hire", "someone who", "professional", "pro", "freelance"),
            "TEACHING", List.of("teach", "teaches", "lesson", "lessons", "learn", "class", "course"),
            "SPACE_OR_VEHICLE", List.of("space", "parking", "garage", "van", "car", "storage"),
            "OPEN_NEED", List.of("wanted", "does anyone", "anyone have"));

    public Parsed parse(String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return new Parsed(List.of(), List.of(), List.of(), null, null, false);
        }
        String lower = sentence.toLowerCase(Locale.ROOT);

        Integer radiusKm = radius(lower);
        LocalDate day = day(lower);
        List<String> tags = tags(lower);
        List<String> kinds = kinds(lower);
        List<String> terms = terms(lower);

        return new Parsed(terms, tags, kinds, radiusKm, day, true);
    }

    private static Integer radius(String lower) {
        Matcher m = DISTANCE.matcher(lower);
        if (!m.find()) return null;
        int value = Integer.parseInt(m.group(1));
        String unit = m.group(2) == null ? "km" : m.group(2).toLowerCase(Locale.ROOT);
        return switch (unit) {
            case "m", "metres", "meters" -> Math.max(1, value / 1000);
            // "within 20 minutes" — a rough walking-and-cycling guess, better than ignoring it.
            case "min", "mins", "minutes" -> Math.clamp(value / 6, 1, 50);
            default -> Math.clamp(value, 1, 50);
        };
    }

    private static LocalDate day(String lower) {
        LocalDate today = LocalDate.now();
        if (lower.contains("today") || lower.contains("tonight")) return today;
        if (lower.contains("tomorrow")) return today.plusDays(1);
        if (lower.contains("this weekend") || lower.contains("weekend")) {
            return today.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        }
        for (DayOfWeek dow : DayOfWeek.values()) {
            String name = dow.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
                    .toLowerCase(Locale.ROOT);
            if (lower.contains(name)) {
                return today.with(java.time.temporal.TemporalAdjusters.nextOrSame(dow));
            }
        }
        return null;
    }

    private static List<String> tags(String lower) {
        Set<String> hits = new LinkedHashSet<>();
        SYNONYMS.forEach((slug, words) -> {
            for (String word : words) {
                if (containsWord(lower, word)) {
                    hits.add(slug);
                    return;
                }
            }
        });
        return List.copyOf(hits);
    }

    private static List<String> kinds(String lower) {
        Set<String> hits = new LinkedHashSet<>();
        KIND_WORDS.forEach((kind, words) -> {
            for (String word : words) {
                if (containsWord(lower, word)) {
                    hits.add(kind);
                    return;
                }
            }
        });
        return List.copyOf(hits);
    }

    private static List<String> terms(String lower) {
        List<String> out = new ArrayList<>();
        for (String raw : lower.split("[^\\p{L}\\p{N}]+")) {
            if (raw.length() < 3 || STOP.contains(raw) || raw.matches("\\d+")) continue;
            if (!out.contains(raw)) out.add(raw);
            if (out.size() == 8) break;
        }
        return out;
    }

    /** Whole-word match, so "van" does not fire on "advanced". */
    private static boolean containsWord(String haystack, String needle) {
        if (needle.contains(" ")) return haystack.contains(needle);
        return Pattern.compile("\\b" + Pattern.quote(needle) + "\\b").matcher(haystack).find();
    }
}
