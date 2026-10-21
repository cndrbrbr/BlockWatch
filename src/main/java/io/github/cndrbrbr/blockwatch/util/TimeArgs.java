package io.github.cndrbrbr.blockwatch.util;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Zeitangaben wie {@code 30m}, {@code 2h}, {@code 7d}, {@code 2w} fuer "seit wann". */
public final class TimeArgs {

    private static final Pattern DURATION = Pattern.compile("(\\d+)([mhdw])", Pattern.CASE_INSENSITIVE);

    private TimeArgs() {
    }

    /** Dauer in Millisekunden, leer bei ungueltiger Angabe. */
    public static OptionalLong parseDuration(String text) {
        Matcher matcher = DURATION.matcher(text);
        if (!matcher.matches()) {
            return OptionalLong.empty();
        }
        long amount = Long.parseLong(matcher.group(1));
        long unit = switch (Character.toLowerCase(matcher.group(2).charAt(0))) {
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            default -> 7 * 86_400_000L;
        };
        return OptionalLong.of(amount * unit);
    }

    /** Deutsche Beschreibung wie "letzte 2 Stunden" fuer Kopfzeilen. */
    public static String describe(String text) {
        Matcher matcher = DURATION.matcher(text);
        if (!matcher.matches()) {
            return text;
        }
        long amount = Long.parseLong(matcher.group(1));
        String unit = switch (Character.toLowerCase(matcher.group(2).charAt(0))) {
            case 'm' -> amount == 1 ? "Minute" : "Minuten";
            case 'h' -> amount == 1 ? "Stunde" : "Stunden";
            case 'd' -> amount == 1 ? "Tag" : "Tage";
            default -> amount == 1 ? "Woche" : "Wochen";
        };
        return (amount == 1 ? "letzte " : "letzte " + amount + " ") + unit;
    }
}
