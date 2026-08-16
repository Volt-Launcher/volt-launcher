package app.voltlauncher.game.install;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates Mojang's {@code rules} blocks, which gate libraries and launch arguments on the
 * current OS and on optional launcher "features" such as a custom window size.
 *
 * <p>Rules are evaluated in order and the last matching one wins; an empty list allows.
 */
public final class OsRules {

    private OsRules() {}

    /** Evaluates rules that depend on the OS only. Any feature-gated rule is treated as unmet. */
    public static boolean isAllowedByRules(JSONArray rules) {
        return isAllowedByRules(rules, Map.of());
    }

    /**
     * @param features launcher features that are currently active, e.g.
     *                 {@code has_custom_resolution -> true}
     */
    public static boolean isAllowedByRules(JSONArray rules, Map<String, Boolean> features) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allowed = false;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule == null || !ruleMatches(rule, features)) continue;
            allowed = Objects.equals(rule.optString("action", "allow"), "allow");
        }
        return allowed;
    }

    private static boolean ruleMatches(JSONObject rule, Map<String, Boolean> features) {
        JSONObject requiredFeatures = rule.optJSONObject("features");
        if (requiredFeatures != null) {
            for (String feature : requiredFeatures.keySet()) {
                boolean required = requiredFeatures.optBoolean(feature, true);
                boolean actual = features.getOrDefault(feature, false);
                if (required != actual) return false;
            }
        }

        JSONObject os = rule.optJSONObject("os");
        if (os == null) return true;

        OsDetails current = current();
        String expectedName = os.optString("name", "");
        if (!expectedName.isBlank() && !expectedName.equals(current.name())) return false;

        String expectedArch = os.optString("arch", "");
        if (!expectedArch.isBlank()
                && !System.getProperty("os.arch", "").toLowerCase(Locale.ROOT)
                        .contains(expectedArch.toLowerCase(Locale.ROOT))) {
            return false;
        }

        String expectedVersion = os.optString("version", "");
        if (!expectedVersion.isBlank()) {
            try {
                return System.getProperty("os.version", "").matches(expectedVersion);
            } catch (Exception ignored) {
                return true;
            }
        }
        return true;
    }

    public static String resolveNativeClassifier(JSONObject natives) {
        OsDetails os = current();
        String classifier = natives.optString(os.name(), "");
        if (classifier.isBlank()) return null;
        return classifier.replace("${arch}", os.archBits());
    }

    public static OsDetails current() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String bits = System.getProperty("os.arch", "").contains("64") ? "64" : "32";
        if (name.contains("win")) return new OsDetails("windows", bits);
        if (name.contains("mac") || name.contains("darwin")) return new OsDetails("osx", bits);
        return new OsDetails("linux", bits);
    }

    public record OsDetails(String name, String archBits) {}
}
