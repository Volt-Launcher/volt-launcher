package app.voltlauncher.providers.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public record SearchResult(List<ProjectSummary> hits, long total, int offset, int limit) {

    public SearchResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }

    public JSONObject toJson() {
        JSONArray array = new JSONArray();
        hits.forEach(hit -> array.put(hit.toJson()));
        return new JSONObject()
                .put("hits", array)
                .put("total", total)
                .put("offset", offset)
                .put("limit", limit);
    }
}
