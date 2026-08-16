package app.voltlauncher.core.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;

public final class HttpFetcher {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration API_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);
    private static final int DOWNLOAD_MAX_ATTEMPTS = 3;

    private final HttpClient client;

    public HttpFetcher() {
        this.client = HttpClient.newBuilder()
        .executor(Executors.newVirtualThreadPerTaskExecutor())
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    }

    private static HttpRequest apiGet(String url) {
        return HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(API_TIMEOUT)
        .GET()
        .build();
    }

    private static void assertSuccess(String url, int status) throws IOException {
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " → " + url);
        }
    }

    private static boolean hashMatches(Path path, String expected) throws Exception {
        if (expected == null || expected.isBlank()) {
            return Files.exists(path);
        }
        String algo = expected.length() == 128 ? "SHA-512" : expected.length() == 64 ? "SHA-256" : "SHA-1";
        MessageDigest digest = MessageDigest.getInstance(algo);
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buf = new byte[65_536];
            int read;
            while ((read = in.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
        }
        byte[] raw = digest.digest();
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) { sb.append(String.format("%02x", b)); }
        return sb.toString().equalsIgnoreCase(expected);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public String getString(String url) throws Exception {
        HttpResponse < String> resp = client.send(apiGet(url), HttpResponse.BodyHandlers.ofString());
        assertSuccess(url, resp.statusCode());
        return resp.body();
    }

    public String getString(String url, Map < String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(API_TIMEOUT)
        .GET();
        headers.forEach(builder::header);
        HttpResponse < String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertSuccess(url, resp.statusCode());
        return resp.body();
    }

    public JSONObject getJson(String url) throws Exception {
        return JsonUtil.parse(getString(url));
    }

    public JSONObject getJson(String url, Map < String, String> headers) throws Exception {
        return JsonUtil.parse(getString(url, headers));
    }

    public JSONArray getJsonArray(String url) throws Exception {
        return JsonUtil.parseArray(getString(url));
    }

    public JSONObject postForm(String url, Map < String, String> fields) throws Exception {
        String body = fields.entrySet().stream()
        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
        .reduce((a, b) -> a + "&" + b)
        .orElse("");
        HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(API_TIMEOUT)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
        HttpResponse < String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertSuccess(url, resp.statusCode());
        return JsonUtil.parse(resp.body());
    }

    public JSONObject postJson(String url, JSONObject body) throws Exception {
        return postJson(url, body, Map.of());
    }

    public JSONObject postJson(String url, JSONObject body, Map < String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(API_TIMEOUT)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        headers.forEach(builder::header);
        HttpResponse < String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertSuccess(url, resp.statusCode());
        return JsonUtil.parse(resp.body());
    }

    /**
    * Download mit Hash-Prüfung und automatischem Retry.
    * Nutzt einen separaten langen Timeout (10 Minuten) damit auch
    * große Dateien (client.jar, Temurin JDK) zuverlässig geladen werden.
    */
    public void download(String url, Path target, String expectedHash) throws Exception {
        if (Files.exists(target) && hashMatches(target, expectedHash)) {
            return;
        }
        Exception last = null;
        for (int attempt = 1; attempt <= DOWNLOAD_MAX_ATTEMPTS; attempt++) {
            try {
                doDownload(url, target, expectedHash);
                return;
            } catch (IOException e) {
                last = e;
                System.err.printf("[HttpFetcher] Download fehlgeschlagen (Versuch %d/%d): %s — %s%n", attempt, DOWNLOAD_MAX_ATTEMPTS, target.getFileName(), e.getMessage());
                if (attempt < DOWNLOAD_MAX_ATTEMPTS) {
                    Thread.sleep(Duration.ofMillis(500L * attempt));
                }
            }
        }
        throw new IOException("Download nach " + DOWNLOAD_MAX_ATTEMPTS + " Versuchen fehlgeschlagen: " + target.getFileName(), last);
    }

    private void doDownload(String url, Path target, String expectedHash) throws Exception {
        Files.createDirectories(target.getParent());

        HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(DOWNLOAD_TIMEOUT)
        .GET()
        .build();

        HttpResponse < InputStream> resp = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertSuccess(url, resp.statusCode());

        Path tmp = Files.createTempFile(target.getParent(), "dl-", ".tmp");
        try (InputStream body = resp.body()) {
            Files.copy(body, tmp, StandardCopyOption.REPLACE_EXISTING);
        }

        if (!hashMatches(tmp, expectedHash)) {
            Files.deleteIfExists(tmp);
            throw new IOException("Hash-Prüfung fehlgeschlagen für: " + target.getFileName());
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
