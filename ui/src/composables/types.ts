export interface AuthData {
    uuid: string;
    username: string;
}

export interface AccountEntry {
    uuid: string;
    username: string;
    selected: boolean;
}

export interface InstanceSettings {
    maxMemoryMb: number | null;
    minMemoryMb: number | null;
    jvmArgs: string | null;
    resolutionWidth: number | null;
    resolutionHeight: number | null;
    javaPath: string | null;
}

export interface LauncherInstance {
    name: string;
    slug: string;
    versionId: string;
    versionType: string;
    createdAt: number;
    lastPlayedAt: number;
    javaMajorVersion: number;
    javaComponent: string;
    settings: InstanceSettings;
    running: boolean;
    launchPhase?: string;
    pid?: number;
    startedAt?: number;
    javaExecutable?: string;
    runningJavaMajorVersion?: number;
}

export type ContentType = "mods" | "resourcepacks" | "shaderpacks" | "datapacks";

export interface ContentEntry {
    fileName: string;
    size: number;
    enabled: boolean;
}

export interface AvailableVersion {
    id: string;
    type: string;
    releaseTime: string;
}

export type PlatformId = "vanilla" | "fabric" | "forge" | "neoforge" | "quilt";

export type MainTab = "home" | "profiles" | "skins" | "discover" | "settings";

export interface JavaRuntime {
    version: number;
    path: string;
}

export type LaunchPhase = "idle" | "installing" | "launching" | "running" | "failed";

export interface LauncherNotification {
    id: number;
    type: "error" | "info";
    message: string;
    timestamp: number;
}
