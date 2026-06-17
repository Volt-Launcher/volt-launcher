package app.voltlauncher.voltlauncher.launcher.install;

import app.voltlauncher.voltlauncher.util.HttpFetcher;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

final class AssetDownloader {

    private static final int ASSET_CONCURRENCY = 16;
    private final HttpFetcher http;

    AssetDownloader(HttpFetcher http) {
        this.http = http;
    }

    void downloadParallel(JSONObject indexJson, Path objectsDir) throws Exception {
        JSONObject objects = indexJson.getJSONObject("objects");
        Semaphore sem = new Semaphore(ASSET_CONCURRENCY);
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> tasks = new ArrayList<>(objects.length());
            for (String name : objects.keySet()) {
                String hash = objects.getJSONObject(name).getString("hash");
                String prefix = hash.substring(0, 2);
                Path target = objectsDir.resolve(prefix).resolve(hash);
                String url = "https://resources.download.minecraft.net/" + prefix + "/" + hash;
                tasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        sem.acquire();
                        try {
                            http.download(url, target, hash);
                        } finally {
                            sem.release();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, exec));
            }
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        }
    }

    void createVirtualAssets(JSONObject indexJson, Path objectsDir, Path virtualDir) throws Exception {
        JSONObject objects = indexJson.getJSONObject("objects");
        Files.createDirectories(virtualDir);
        for (String assetName : objects.keySet()) {
            String hash = objects.getJSONObject(assetName).getString("hash");
            String prefix = hash.substring(0, 2);
            Path src = objectsDir.resolve(prefix).resolve(hash);
            Path dest = virtualDir.resolve(assetName.replace("/", File.separator));
            if (Files.exists(dest)) continue;
            Files.createDirectories(dest.getParent());
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
