# TODO

## Done

- [x] Recode/Fix Microsoft Login
- [x] Fix using the correct Java Version for profiles
- [x] Add support for the Vanilla platform
- [x] Add support for the Fabric platform
- [x] Add support for the Quilt platform
- [x] Add support for the Forge platform
- [x] Add support for the NeoForge platform
- [x] Make the mod loaders actually launch (Forge/NeoForge install processors now run —
      Mojang client mappings are downloaded, processor-produced artifacts are no longer
      treated as failed downloads, and the vanilla client jar stays on the classpath)
- [x] Download Java Version (Temurin downloader, exposed in Settings)
- [x] Add support for custom Java installations (per-profile override and per-version paths)
- [x] Add support for Modrinth discovery
- [x] Add support for CurseForge discovery (through the bridge service in `bridge/`)
- [x] Add Install from discovery (modpacks create a profile)
- [x] Add Install into profile from discovery (mods, shaders, resource packs, data packs,
      with required-dependency resolution)
- [x] Create universal setup (`volt-setup`, Windows Start Menu / Linux .desktop / macOS bundle)
- [x] Split into a multi-module project
- [x] Make settings persistent and functional
- [x] Localise the interface (English + German)
- [x] Share assets and libraries between profiles instead of duplicating ~1 GB per profile
- [x] Skin management (ported from the pre-overhaul branch: local library, upload,
      rename, delete, apply to the signed-in account)
- [x] Track where every installed file came from (`<instance>/content.json`), which is what
      makes version management, pack updates and export possible
- [x] Modpack updating (checks the profile's pack for a newer release, drops the files the
      new release no longer ships and keeps worlds, configs and self-installed mods)
- [x] Version management for mods / shaders / resource packs / data packs (per-file update
      check, update all, and switching to any specific release)
- [x] Import CurseForge `.zip` and Modrinth `.mrpack` archives (drag onto the profile grid
      or use IMPORT; the format is detected from the manifest inside)
- [x] Export a profile as `.mrpack` or CurseForge `.zip` (files the target platform cannot
      reference by id are bundled into `overrides/`)

## Open

- [ ] Add support for Technic discovery
- [ ] Add support for FTB discovery
- [ ] Fix scrolling
- [ ] Add GraalVM support for executables (the `native` profile builds, but the reflection
      config still needs regenerating after the routing rewrite)
- [ ] Discord rich presence (the setting exists but nothing consumes it yet)
- [ ] Launcher self-update (the Updates screen is not wired to a release feed)
- [ ] Automated tests — there is currently no test suite
