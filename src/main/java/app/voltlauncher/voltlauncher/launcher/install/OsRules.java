package app.voltlauncher.voltlauncher.launcher.install;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Objects;

final class OsRules {

    private OsRules() {}

    static boolean isAllowedByRules(JSONArray rules) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allowed = false;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            if (!ruleMatches(rule)) continue;
            allowed = Objects.equals(rule.optString("action", "allow"), "allow");
        }
        return allowed;
    }

    private static boolean ruleMatches(JSONObject rule) {
        if (rule.has("features") && !rule.getJSONObject("features").isEmpty()) return false;
        if (!rule.has("os")) return true;
        OsDetails os = current();
        JSONObject osJson = rule.getJSONObject("os");
        String expectedName = osJson.optString("name", "");
        if (!expectedName.isBlank() && !expectedName.equals(os.name())) return false;
        String expectedArch = osJson.optString("arch", "");
        return expectedArch.isBlank() || System.getProperty("os.arch", "").toLowerCase(Locale.ROOT)
                .contains(expectedArch.toLowerCase(Locale.ROOT));
    }

    static String resolveNativeClassifier(JSONObject natives) {
        OsDetails os = current();
        String classifier = natives.optString(os.name(), "");
        if (classifier.isBlank()) return null;
        return classifier.replace("${arch}", os.archBits());
    }

    static OsDetails current() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String bits = System.getProperty("os.arch", "").contains("64") ? "64" : "32";
        if (name.contains("win")) return new OsDetails("windows", bits);
        if (name.contains("mac") || name.contains("darwin")) return new OsDetails("osx", bits);
        return new OsDetails("linux", bits);
    }

    record OsDetails(String name, String archBits) {}
}
