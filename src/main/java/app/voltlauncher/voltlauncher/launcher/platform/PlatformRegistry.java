package app.voltlauncher.voltlauncher.launcher.platform;

import app.voltlauncher.voltlauncher.launcher.platform.impl.*;
import app.voltlauncher.voltlauncher.launcher.platform.version.IVersionResolver;
import app.voltlauncher.voltlauncher.launcher.platform.version.resolver.FabricVersionResolver;
import app.voltlauncher.voltlauncher.launcher.platform.version.resolver.ForgeVersionResolver;
import app.voltlauncher.voltlauncher.launcher.platform.version.resolver.NeoForgeVersionResolver;
import app.voltlauncher.voltlauncher.launcher.platform.version.resolver.QuiltVersionResolver;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PlatformRegistry {

    public static final String VANILLA_ID = "vanilla";
    public static final String FABRIC_ID = "fabric";
    public static final String FORGE_ID = "forge";
    public static final String NEOFORGE_ID = "neoforge";
    public static final String QUILT_ID = "quilt";

    private final Map < String, IPlatform> byId = new ConcurrentHashMap <> ();

    public static PlatformRegistry withDefaults(IVersionResolver vanillaResolver) {
        PlatformRegistry registry = new PlatformRegistry();
        registry.register(new VanillaPlatform(vanillaResolver));
        registry.register(new FabricPlatform(new FabricVersionResolver(vanillaResolver)));
        registry.register(new ForgePlatform(new ForgeVersionResolver(vanillaResolver)));
        registry.register(new NeoForgePlatform(new NeoForgeVersionResolver(vanillaResolver)));
        registry.register(new QuiltPlatform(new QuiltVersionResolver(vanillaResolver)));
        return registry;
    }

    public PlatformRegistry register(IPlatform platform) {
        byId.put(normalize(platform.id()), platform);
        return this;
    }

    public Optional < IPlatform> get(String id) {
        return Optional.ofNullable(byId.get(normalize(id)));
    }

    public IPlatform require(String id) {
        return get(id).orElseThrow(() -> new IllegalArgumentException("Unsupported platform: " + id));
    }

    public IPlatform requireDefault() {
        return require(VANILLA_ID);
    }

    public List < IPlatform> list() {
        List < IPlatform> values = new ArrayList <> (byId.values());
        values.sort(Comparator.comparing(IPlatform::id));
        return Collections.unmodifiableList(values);
    }

    private String normalize(String id) {
        return Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT);
    }
}

