package app.voltlauncher.providers.model;

import java.util.List;

/**
 * A provider-neutral discovery query. Each provider translates it into its own filter syntax.
 *
 * @param sort one of {@code relevance}, {@code downloads}, {@code follows}, {@code newest}, {@code updated}
 */
public record SearchQuery(
        String query,
        ContentKind kind,
        String gameVersion,
        String loader,
        List<String> categories,
        String sort,
        int offset,
        int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    public SearchQuery {
        query = query == null ? "" : query.trim();
        kind = kind == null ? ContentKind.MOD : kind;
        gameVersion = blankToNull(gameVersion);
        loader = blankToNull(loader);
        categories = categories == null ? List.of() : List.copyOf(categories);
        sort = sort == null || sort.isBlank() ? "relevance" : sort.trim();
        offset = Math.max(0, offset);
        limit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }

    public boolean hasQuery() {
        return !query.isEmpty();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
