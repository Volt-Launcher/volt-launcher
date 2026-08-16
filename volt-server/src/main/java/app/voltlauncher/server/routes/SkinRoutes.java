package app.voltlauncher.server.routes;

import app.voltlauncher.auth.session.MicrosoftAuth;
import app.voltlauncher.auth.skin.SavedSkin;
import app.voltlauncher.auth.skin.SkinStore;
import app.voltlauncher.server.route.RequestBody;
import app.voltlauncher.server.route.RouteModule;
import app.voltlauncher.server.route.RouteRegistry;
import io.javalin.http.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Base64;
import java.util.NoSuchElementException;

/** The local skin library, and applying one of its skins to the active Minecraft account. */
public final class SkinRoutes implements RouteModule {

    private final SkinStore skins;
    private final MicrosoftAuth auth;

    public SkinRoutes(SkinStore skins, MicrosoftAuth auth) {
        this.skins = skins;
        this.auth = auth;
    }

    @Override
    public void register(RouteRegistry routes) {
        routes.get("/api/skins", this::list);
        routes.post("/api/skins", this::save);
        routes.patch("/api/skins/{id}", this::rename);
        routes.delete("/api/skins/{id}", this::delete);
        routes.post("/api/skins/{id}/select", this::select);

        // Serves raw PNG bytes rather than the usual JSON envelope, so it bypasses the registry's
        // response handling and is registered directly on Javalin.
        routes.raw("GET", "/api/skins/{id}/image", this::image);
    }

    private JSONObject list(Context ctx) throws Exception {
        JSONArray array = new JSONArray();
        for (SavedSkin skin : skins.listSkins()) {
            array.put(skin.toJson());
        }
        String selected = skins.getSelectedSkinId();
        return new JSONObject()
                .put("skins", array)
                .put("selectedSkinId", selected == null ? JSONObject.NULL : selected);
    }

    /**
     * Body: {@code { name, slim, imageBase64 }}. The base64 may carry a
     * {@code data:image/png;base64,} prefix, which the browser's FileReader adds.
     */
    private JSONObject save(Context ctx) throws Exception {
        JSONObject body = RequestBody.json(ctx);
        String imageBase64 = body.optString("imageBase64", "");
        int comma = imageBase64.indexOf(',');
        if (imageBase64.startsWith("data:") && comma != -1) {
            imageBase64 = imageBase64.substring(comma + 1);
        }
        if (imageBase64.isBlank()) {
            throw new IllegalArgumentException("Skin image is required");
        }

        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(imageBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Skin image is not valid base64 data");
        }

        SavedSkin skin = skins.saveSkin(body.optString("name", ""), body.optBoolean("slim", false), imageBytes);
        return new JSONObject().put("skin", skin.toJson());
    }

    private JSONObject rename(Context ctx) throws Exception {
        JSONObject body = RequestBody.json(ctx);
        SavedSkin renamed = skins.renameSkin(ctx.pathParam("id"), RequestBody.requiredString(body, "name"));
        return new JSONObject().put("skin", renamed.toJson());
    }

    private JSONObject delete(Context ctx) throws Exception {
        skins.deleteSkin(ctx.pathParam("id"));
        return new JSONObject();
    }

    /** Uploads the skin to Mojang, and only records it as selected once that succeeded. */
    private JSONObject select(Context ctx) throws Exception {
        String id = ctx.pathParam("id");
        SavedSkin skin = skins.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Skin not found: " + id));

        auth.changeSkin(skins.readImage(id), skin.slim());
        skins.setSelectedSkinId(id);
        return new JSONObject().put("skin", skin.toJson());
    }

    private void image(Context ctx) {
        String id = ctx.pathParam("id");
        try {
            byte[] png = skins.readImage(id);
            // Stored images are immutable — a new upload gets a new id — so they can be cached
            // hard. Without this the grid refetches every thumbnail on each render.
            ctx.header("Cache-Control", "private, max-age=31536000, immutable");
            ctx.contentType("image/png").result(png);
        } catch (IllegalArgumentException e) {
            ctx.status(400).result(e.getMessage() == null ? "Invalid skin id" : e.getMessage());
        } catch (NoSuchElementException e) {
            ctx.status(404).result(e.getMessage() == null ? "Skin not found" : e.getMessage());
        } catch (Exception e) {
            System.err.println("[API] Reading skin image failed: " + e);
            ctx.status(500).result("Could not read the skin image");
        }
    }
}
