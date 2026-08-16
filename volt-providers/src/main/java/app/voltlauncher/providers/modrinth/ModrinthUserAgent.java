package app.voltlauncher.providers.modrinth;

/**
 * Modrinth asks API consumers to identify themselves; requests without a descriptive
 * User-Agent are rate limited more aggressively.
 */
final class ModrinthUserAgent {

    static final String VALUE = "VoltLauncher/0.2.0 (https://volt-launcher.app)";

    private ModrinthUserAgent() {}
}
