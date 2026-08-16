package app.voltlauncher.providers.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/** Full project page data. */
public record ProjectDetail(
        ProjectSummary summary,
        String body,
        List<String> gallery,
        String license,
        Map<String, String> links) {

    public ProjectDetail {
        gallery = gallery == null ? List.of() : List.copyOf(gallery);
        links = links == null ? Map.of() : Map.copyOf(links);
    }

    public JSONObject toJson() {
        JSONObject json = summary.toJson();
        json.put("body", body == null ? "" : body);
        json.put("gallery", new JSONArray(gallery));
        json.put("license", license == null ? "" : license);
        json.put("links", new JSONObject(links));
        return json;
    }
}
