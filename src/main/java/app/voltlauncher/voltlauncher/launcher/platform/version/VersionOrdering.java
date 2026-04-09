package app.voltlauncher.voltlauncher.launcher.platform.version;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class VersionOrdering {

    private VersionOrdering() {}

    public static void sortNewestFirst(List<AvailableVersion> versions) {
        versions.sort(newestFirst());
    }

    public static Comparator<AvailableVersion> newestFirst() {
        return (a, b) -> {
            int byTime = compareReleaseTimeDesc(a.releaseTime(), b.releaseTime());
            if (byTime != 0) return byTime;

            int byId = compareVersionIdDesc(a.id(), b.id());
            if (byId != 0) return byId;

            return String.CASE_INSENSITIVE_ORDER.compare(a.type(), b.type());
        };
    }

    public static int compareVersionIdDesc(String left, String right) {
        return -compareVersionIdAsc(left, right);
    }

    public static int compareVersionIdAsc(String left, String right) {
        String[] a = tokenize(left);
        String[] b = tokenize(right);
        int len = Math.min(a.length, b.length);

        for (int i = 0; i < len; i++) {
            String ta = a[i];
            String tb = b[i];
            boolean na = isDigits(ta);
            boolean nb = isDigits(tb);

            int cmp;
            if (na && nb) {
                cmp = compareNumericTokens(ta, tb);
            } else if (na != nb) {
                // Numeric segments are considered greater than text segments.
                cmp = na ? 1 : -1;
            } else {
                cmp = ta.compareTo(tb);
            }

            if (cmp != 0) return cmp;
        }

        return Integer.compare(a.length, b.length);
    }

    private static int compareReleaseTimeDesc(String left, String right) {
        Instant a = parseInstant(left);
        Instant b = parseInstant(right);

        if (a != null && b != null) return b.compareTo(a);
        if (a != null) return -1;
        if (b != null) return 1;
        return 0;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String[] tokenize(String input) {
        if (input == null || input.isBlank()) return new String[0];
        return input.toLowerCase(Locale.ROOT)
                .replace(':', '.')
                .split("[^a-z0-9]+") ;
    }

    private static boolean isDigits(String token) {
        if (token.isEmpty()) return false;
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) return false;
        }
        return true;
    }

    private static int compareNumericTokens(String left, String right) {
        String a = stripLeadingZeros(left);
        String b = stripLeadingZeros(right);

        int lenCmp = Integer.compare(a.length(), b.length());
        if (lenCmp != 0) return lenCmp;
        return a.compareTo(b);
    }

    private static String stripLeadingZeros(String token) {
        int i = 0;
        while (i < token.length() - 1 && token.charAt(i) == '0') i++;
        return token.substring(i);
    }
}

