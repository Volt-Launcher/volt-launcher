package app.voltlauncher.voltlauncher.rest.util;

import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.launcher.instance.Instance;
import app.voltlauncher.voltlauncher.launcher.instance.RunningInstanceStatus;
import org.json.JSONObject;

public class InstanceHelper {
    public static JSONObject toInstanceJson(Instance instance, MinecraftLauncherService minecraftLauncher) {
        JSONObject json = new JSONObject();
        json.put("name", instance.name());
        json.put("slug", instance.slug());
        json.put("versionId", instance.versionId());
        json.put("versionType", instance.versionType());
        json.put("createdAt", instance.createdAt());
        json.put("lastPlayedAt", instance.lastPlayedAt());
        json.put("javaMajorVersion", instance.javaMajorVersion());
        json.put("javaComponent", instance.javaComponent());

        RunningInstanceStatus runningStatus = minecraftLauncher.getRunningInstanceStatus(instance.name());
        json.put("running", runningStatus != null && runningStatus.alive());
        if (runningStatus != null) {
            json.put("pid", runningStatus.pid());
            json.put("startedAt", runningStatus.startedAt());
            json.put("javaExecutable", runningStatus.javaExecutable());
            json.put("runningJavaMajorVersion", runningStatus.javaMajorVersion());
        }
        return json;
    }
}

