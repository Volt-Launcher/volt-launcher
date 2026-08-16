// ── Accounts ──────────────────────────────────────────────────────────────────

export interface AuthData {
  uuid: string;
  username: string;
}

export interface AccountEntry {
  uuid: string;
  username: string;
  selected: boolean;
}

// ── Profiles ──────────────────────────────────────────────────────────────────

export interface InstanceSettings {
  maxMemoryMb: number | null;
  minMemoryMb: number | null;
  jvmArgs: string | null;
  resolutionWidth: number | null;
  resolutionHeight: number | null;
  javaPath: string | null;
}

export type LaunchPhase = "idle" | "installing" | "launching" | "running" | "failed";

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
  launchPhase: LaunchPhase;
  pid?: number;
  startedAt?: number;
  javaExecutable?: string;
  runningJavaMajorVersion?: number;
}

export interface AvailableVersion {
  id: string;
  type: string;
  releaseTime: string;
}

export interface Platform {
  id: string;
  displayName: string;
}

// ── Instance content ──────────────────────────────────────────────────────────

export type ContentType = "mods" | "resourcepacks" | "shaderpacks" | "datapacks";

export interface ContentEntry {
  fileName: string;
  size: number;
  enabled: boolean;
}

// ── Content providers ─────────────────────────────────────────────────────────

export type ProviderId = "modrinth" | "curseforge";

export type ContentKind = "mod" | "modpack" | "resourcepack" | "shader" | "datapack";

export interface ProviderInfo {
  id: ProviderId;
  displayName: string;
  available: boolean;
  unavailableReason: string;
  kinds: ContentKind[];
}

export interface ProjectSummary {
  provider: ProviderId;
  projectId: string;
  slug: string;
  title: string;
  description: string;
  author: string;
  iconUrl: string;
  downloads: number;
  follows: number;
  categories: string[];
  loaders: string[];
  gameVersions: string[];
  kind: ContentKind;
  updated: string;
}

export interface ProjectDetail extends ProjectSummary {
  body: string;
  gallery: string[];
  license: string;
  links: Record<string, string>;
}

export interface VersionDependency {
  projectId: string;
  versionId: string;
  relation: "required" | "optional" | "incompatible" | "embedded" | string;
}

export interface ProjectVersion {
  provider: ProviderId;
  versionId: string;
  projectId: string;
  name: string;
  versionNumber: string;
  gameVersions: string[];
  loaders: string[];
  releaseType: "release" | "beta" | "alpha" | string;
  fileName: string;
  fileSize: number;
  datePublished: string;
  downloadable: boolean;
  dependencies: VersionDependency[];
}

export interface SearchResult {
  hits: ProjectSummary[];
  total: number;
  offset: number;
  limit: number;
}

export type SortIndex = "relevance" | "downloads" | "follows" | "newest" | "updated";

// ── Java ──────────────────────────────────────────────────────────────────────

export interface JavaRuntime {
  majorVersion: number;
  path: string;
  source: string;
}

export interface JavaRuntimeEntry {
  majorVersion: number;
  path: string;
}

export type JavaInstallPhase = "downloading" | "done" | "failed";

export interface JavaInstallJob {
  majorVersion: number;
  phase: JavaInstallPhase;
  message: string | null;
  javaExecutable: string | null;
}

// ── Launcher settings ─────────────────────────────────────────────────────────

export interface LauncherSettings {
  language: "en" | "de";
  accentColor: string;
  uiScale: "compact" | "default" | "comfortable";
  animationsEnabled: boolean;
  showFps: boolean;
  discordPresence: boolean;
  hideLauncherOnLaunch: boolean;
  openLogsOnLaunch: boolean;
  autoUpdate: boolean;
  betaUpdates: boolean;
  defaultMaxMemoryMb: number;
  defaultMinMemoryMb: number;
  defaultJvmArgs: string;
  maxConcurrentDownloads: number;
  curseForgeBridgeUrl: string;
  javaRuntimes: JavaRuntimeEntry[];
}

export interface LauncherDirectory {
  id: string;
  label: string;
  path: string;
}

export type SettingsSectionId =
  | "general"
  | "appearance"
  | "java"
  | "providers"
  | "updates"
  | "account"
  | "advanced";

export type MainTab = "home" | "profiles" | "skins" | "discover" | "settings";

// ── Notifications ─────────────────────────────────────────────────────────────

export interface LauncherNotification {
  id: number;
  type: "error" | "info";
  message: string;
  timestamp: number;
}
