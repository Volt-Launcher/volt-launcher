package de.eztxm.thelauncherproject.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.concurrent.Executors;

public final class HttpFetcher {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int DOWNLOAD_MAX_ATTEMPTS = 3;

    private final HttpClient client;

    public HttpFetcher() {
        this.client = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String getString(String url) throws Exception {
        HttpResponse<String> resp = client.send(request(url), HttpResponse.BodyHandlers.ofString());
        assertSuccess(url, resp.statusCode());
        return resp.body();
    }

    public JSONObject getJson(String url) throws Exception {
        return JsonUtil.parse(getString(url));
    }

    public JSONArray getJsonArray(String url) throws Exception {
        return JsonUtil.parseArray(getString(url));
    }

    /**
     * Download mit automatischem Retry (bis zu DOWNLOAD_MAX_ATTEMPTS Versuche).
     * Wartet zwischen Versuchen exponentiell: 200ms, 400ms, 800ms, ...
     * Überspringt den Download wenn die Datei existiert und der Hash passt.
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
                if (attempt < DOWNLOAD_MAX_ATTEMPTS) {
                    Thread.sleep(Duration.ofMillis(200L * attempt));
                }
            }
        }
        throw last;
    }

    private void doDownload(String url, Path target, String expectedHash) throws Exception {
        Files.createDirectories(target.getParent());
        HttpResponse<InputStream> resp = client.send(
                request(url), HttpResponse.BodyHandlers.ofInputStream());
        assertSuccess(url, resp.statusCode());

        Path tmp = Files.createTempFile(target.getParent(), "dl-", ".tmp");
        try (InputStream body = resp.body()) {
            Files.copy(body, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!hashMatches(tmp, expectedHash)) {
            Files.deleteIfExists(tmp);
            throw new IOException("Hash mismatch für " + target.getFileName());
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
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
        String algo = expected.length() == 64 ? "SHA-256" : "SHA-1";
        MessageDigest digest = MessageDigest.getInstance(algo);
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buf = new byte[16_384];
            int read;
            while ((read = in.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
        }
        byte[] raw = digest.digest();
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString().equalsIgnoreCase(expected);
    }
}