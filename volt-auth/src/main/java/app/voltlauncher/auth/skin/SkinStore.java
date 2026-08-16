package app.voltlauncher.auth.skin;

import app.voltlauncher.core.AppPaths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The user's local skin library: PNG files on disk plus a metadata document recording their names
 * and which one was last applied.
 */
public final class SkinStore {

    private static final int MAX_NAME = 64;
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** Skins are 64x64, or 64x32 for the pre-1.8 layout Minecraft still accepts. */
    private static final int SKIN_WIDTH = 64;
    private static final int MODERN_SKIN_HEIGHT = 64;
    private static final int LEGACY_SKIN_HEIGHT = 32;

    /** Ids are generated as UUIDs; anything else must never reach a file path. */
    private static final Pattern SKIN_ID = Pattern.compile("[0-9a-fA-F-]{36}");

    private final Path storagePath;

    /** The whole metadata document, so a mutation reads and writes the file exactly once. */
    private record Library(List<SavedSkin> skins, String selectedSkinId) {}

    public SkinStore() {
        this(AppPaths.skinsMetadataPath());
    }

    public SkinStore(Path storagePath) {
        this.storagePath = storagePath;
    }

    // ── queries ───────────────────────────────────────────────────────────────

    public synchronized List<SavedSkin> listSkins() throws Exception {
        List<SavedSkin> skins = new ArrayList<>(read().skins());
        skins.sort(Comparator.comparing(SavedSkin::name, String.CASE_INSENSITIVE_ORDER));
        return skins;
    }

    public synchronized String getSelectedSkinId() throws Exception {
        return read().selectedSkinId();
    }

    public synchronized Optional<SavedSkin> findById(String id) throws Exception {
        String skinId = requireValidId(id);
        return read().skins().stream().filter(skin -> skin.id().equals(skinId)).findFirst();
    }

    public synchronized byte[] readImage(String id) throws Exception {
        Path imagePath = AppPaths.skinImagePath(requireValidId(id));
        if (!Files.exists(imagePath)) {
            throw new NoSuchElementException("Skin not found: " + id);
        }
        return Files.readAllBytes(imagePath);
    }

    // ── mutations ─────────────────────────────────────────────────────────────

    public synchronized SavedSkin saveSkin(String name, boolean slim, byte[] imageBytes) throws Exception {
        String normalizedName = normalizeName(name);
        validateName(normalizedName);
        validateImage(imageBytes);

        Library library = read();
        String id = UUID.randomUUID().toString();
        SavedSkin skin = new SavedSkin(id, normalizedName, slim, System.currentTimeMillis());

        Files.createDirectories(AppPaths.skinsDirectory());
        Files.write(AppPaths.skinImagePath(id), imageBytes);

        List<SavedSkin> skins = new ArrayList<>(library.skins());
        skins.add(skin);
        try {
            write(new Library(skins, library.selectedSkinId()));
        } catch (Exception e) {
            // Keep the library consistent: a PNG with no metadata entry would be invisible
            // but still occupy disk forever.
            Files.deleteIfExists(AppPaths.skinImagePath(id));
            throw e;
        }
        return skin;
    }

    public synchronized SavedSkin renameSkin(String id, String newName) throws Exception {
        String skinId = requireValidId(id);
        String normalizedName = normalizeName(newName);
        validateName(normalizedName);

        Library library = read();
        List<SavedSkin> skins = new ArrayList<>(library.skins());
        int index = indexOf(skins, skinId);
        if (index == -1) throw new NoSuchElementException("Skin not found: " + id);

        SavedSkin renamed = skins.get(index).withName(normalizedName);
        skins.set(index, renamed);
        write(new Library(skins, library.selectedSkinId()));
        return renamed;
    }

    public synchronized void deleteSkin(String id) throws Exception {
        String skinId = requireValidId(id);

        Library library = read();
        List<SavedSkin> skins = new ArrayList<>(library.skins());
        int index = indexOf(skins, skinId);
        if (index == -1) throw new NoSuchElementException("Skin not found: " + id);

        skins.remove(index);
        String selected = skinId.equals(library.selectedSkinId()) ? null : library.selectedSkinId();
        write(new Library(skins, selected));
        Files.deleteIfExists(AppPaths.skinImagePath(skinId));
    }

    public synchronized void setSelectedSkinId(String id) throws Exception {
        String skinId = requireValidId(id);
        Library library = read();
        if (indexOf(library.skins(), skinId) == -1) {
            throw new NoSuchElementException("Skin not found: " + id);
        }
        write(new Library(library.skins(), skinId));
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private static int indexOf(List<SavedSkin> skins, String id) {
        for (int i = 0; i < skins.size(); i++) {
            if (skins.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    /**
     * Ids arrive from URL path parameters and are joined into file paths, so anything that is not
     * a generated UUID is rejected outright rather than sanitised.
     */
    private static String requireValidId(String id) {
        if (id == null || !SKIN_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid skin id");
        }
        return id;
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    private static void validateName(String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Skin name is required");
        }
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Skin name must be " + MAX_NAME + " characters or shorter");
        }
    }

    /**
     * Rejects anything Minecraft would refuse. Checking the dimensions locally matters because
     * Mojang answers a wrong-sized upload with an opaque error long after the user picked the file.
     */
    private static void validateImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Skin image is required");
        }
        if (imageBytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Skin image must be 2 MB or smaller");
        }
        // Signature (8) + chunk length (4) + "IHDR" (4) + width (4) + height (4)
        if (imageBytes.length < 24) {
            throw new IllegalArgumentException("Skin image must be a PNG file");
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (imageBytes[i] != PNG_MAGIC[i]) {
                throw new IllegalArgumentException("Skin image must be a PNG file");
            }
        }

        int width = readBigEndianInt(imageBytes, 16);
        int height = readBigEndianInt(imageBytes, 20);
        boolean validSize = width == SKIN_WIDTH
                && (height == MODERN_SKIN_HEIGHT || height == LEGACY_SKIN_HEIGHT);
        if (!validSize) {
            throw new IllegalArgumentException(
                    "A Minecraft skin must be 64x64 pixels (or 64x32 for the old layout), but this image is "
                            + width + "x" + height + ".");
        }
    }

    /** PNG stores the IHDR width and height as big-endian 32-bit integers. */
    private static int readBigEndianInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private Library read() throws Exception {
        if (!Files.exists(storagePath)) {
            return new Library(List.of(), null);
        }

        JSONObject root = new JSONObject(Files.readString(storagePath, StandardCharsets.UTF_8));
        JSONArray array = root.optJSONArray("skins");

        List<SavedSkin> skins = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                skins.add(SavedSkin.fromJson(array.getJSONObject(i)));
            }
        }

        String selected = root.optString("selectedSkinId", "");
        return new Library(skins, selected.isBlank() ? null : selected);
    }

    /** Writes through a temporary file so a crash mid-save cannot truncate the library. */
    private void write(Library library) throws IOException {
        Files.createDirectories(storagePath.getParent());

        JSONArray array = new JSONArray();
        library.skins().forEach(skin -> array.put(skin.toJson()));

        JSONObject root = new JSONObject().put("skins", array);
        if (library.selectedSkinId() != null) {
            root.put("selectedSkinId", library.selectedSkinId());
        }

        Path temp = Files.createTempFile(storagePath.getParent(), "skins-", ".tmp");
        try {
            Files.writeString(temp, root.toString(2), StandardCharsets.UTF_8);
            Files.move(temp, storagePath, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
