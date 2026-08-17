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
  /** True while a long-running operation owns this profile; its controls must stay disabled. */
  busy: boolean;
  busyStage?: string;
  busyCompleted?: number;
  busyTotal?: number;
  /** Completion percentage, or -1 when the work cannot be counted. */
  busyPercent?: number;
  pid?: number;
  startedAt?: number;
  javaExecutable?: string;
  runningJavaMajorVersion?: number;
  /** Name of the modpack this profile came from; blank for hand-made profiles. */
  packName: string;
  /** Modpack artwork, used instead of the version emoji when present. */
  packIconUrl: string;
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

/** Where an installed file came from, recorded so its versions can be managed. */
export interface ContentSource {
  contentType: ContentType;
  fileName: string;
  provider: ProviderId | "";
  projectId: string;
  versionId: string;
  projectName: string;
  versionNumber: string;
  downloadUrl: string;
  sha1: string;
  fileSize: number;
  origin: "provider" | "modpack" | "manual";
  /** Project artwork, blank until the project behind the file is known. */
  iconUrl: string;
}

export interface ContentEntry {
  fileName: string;
  size: number;
  enabled: boolean;
  /** Null for files the launcher has no provenance for. */
  source: ContentSource | null;
  /** True when the file has a known project and can be updated or rolled back. */
  tracked: boolean;
}

/** An installed file with a newer compatible release available. */
export interface ContentUpdate {
  contentType: ContentType;
  fileName: string;
  projectId: string;
  projectName: string;
  currentVersionId: string;
  currentVersionNumber: string;
  latestVersionId: string;
  latestVersionNumber: string;
  latestFileName: string;
  releaseDate: string;
}

// ── Modpacks ──────────────────────────────────────────────────────────────────

export interface ModpackOrigin {
  provider: ProviderId | "";
  projectId: string;
  versionId: string;
  versionNumber: string;
  name: string;
  /** Set for locally imported archives, which have no upstream to check. */
  sourceFile: string;
  iconUrl: string;
  updatable: boolean;
}

export interface ModpackStatus {
  modpack: ModpackOrigin | null;
  updateAvailable: boolean;
  latestVersionId: string;
  latestVersionNumber: string;
  latestReleaseDate: string;
  reason: string;
}

export type ExportFormat = "mrpack" | "curseforge";

export interface ExportResult {
  path: string;
  fileName: string;
  /** Files the target platform can reference by id. */
  referenced: number;
  /** Files carried inside overrides/ because they could not be referenced. */
  bundled: number;
  notes: string[];
}

// ── Skins ─────────────────────────────────────────────────────────────────────

export interface SavedSkin {
  id: string;
  name: string;
  /** True for the 3px-arm ("Alex") model. */
  slim: boolean;
  createdAt: number;
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
