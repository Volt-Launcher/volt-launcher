package app.voltlauncher.voltlauncher.launcher.instance;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class InstanceNamePolicy {

    private static final int MAX_NAME = 64;

    private InstanceNamePolicy() {}

    static String normalize(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("\\s+", " ");
    }

    static void validate(String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Instance name is required");
        }
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Instance name must be 64 characters or shorter");
        }
        if (hasUnsupportedChar(name)) {
            throw new IllegalArgumentException(
                    "Instance name contains unsupported characters: / \\ : * ? \" < > | or control characters");
        }
    }

    static String uniqueSlug(String name, List<Instance> existing) {
        String base = slugify(name);
        if (base.isBlank()) base = "instance";

        Set<String> used = new HashSet<>();
        for (Instance i : existing) {
            used.add(i.slug().toLowerCase(Locale.ROOT));
        }

        String slug = base;
        int counter = 2;
        while (used.contains(slug.toLowerCase(Locale.ROOT))) {
            slug = base + "-" + counter;
            counter++;
        }
        return slug;
    }

    private static String slugify(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static boolean hasUnsupportedChar(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"'
                    || c == '<' || c == '>' || c == '|' || Character.isISOControl(c)) {
                return true;
            }
        }
        return false;
    }
}
