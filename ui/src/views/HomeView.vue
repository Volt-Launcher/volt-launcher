<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";

interface AuthData {
  uuid: string;
  username: string;
}

interface SessionResponse {
  success: boolean;
  authenticated: boolean;
  uuid?: string;
  username?: string;
  error?: string;
}

interface LauncherInstance {
  name: string;
  slug: string;
  versionId: string;
  versionType: string;
  createdAt: number;
  lastPlayedAt: number;
  javaMajorVersion: number;
  javaComponent: string;
  running: boolean;
  pid?: number;
  startedAt?: number;
  javaExecutable?: string;
  runningJavaMajorVersion?: number;
}

interface AvailableVersion {
  id: string;
  type: string;
  releaseTime: string;
}

const authData = ref<AuthData | null>(null);
const instances = ref<LauncherInstance[]>([]);
const availableVersions = ref<AvailableVersion[]>([]);
const selectedInstanceName = ref<string>("");
const newInstanceName = ref<string>("");
const selectedVersionId = ref<string>("");
const includeSnapshots = ref(false);
const includeBetas = ref(false);
const includeAlphas = ref(false);

const isAuthenticating = ref(false);
const isLaunching = ref(false);
const isCreatingInstance = ref(false);
const isLoadingInstances = ref(false);
const isLoadingVersions = ref(false);

const error = ref<string | null>(null);
const launcherMessage = ref<string | null>(null);
const authUrl = ref<string>("");
const authState = ref<string>("");
const authWindowWasClosed = ref(false);

let authPopup: Window | null = null;
let authStatusInterval: ReturnType<typeof window.setInterval> | null = null;
let instanceRefreshInterval: ReturnType<typeof window.setInterval> | null = null;
let authStartedAt = 0;

const selectedInstance = computed<LauncherInstance | null>(() => {
  return (
    instances.value.find(
      (instance) => instance.name === selectedInstanceName.value,
    ) ?? null
  );
});

const runningInstancesCount = computed(() => {
  return instances.value.filter((instance) => instance.running).length;
});

const selectedVersion = computed<AvailableVersion | null>(() => {
  return (
    availableVersions.value.find(
      (version) => version.id === selectedVersionId.value,
    ) ?? null
  );
});

type HomeTab = "play" | "library" | "create" | "account";

const activeTab = ref<HomeTab>("play");

const homeTabs: Array<{
  id: HomeTab;
  label: string;
  description: string;
}> = [
  {
    id: "play",
    label: "Play",
    description: "Launch, stop and session readiness",
  },
  {
    id: "library",
    label: "Library",
    description: "Choose and inspect launcher profiles",
  },
  {
    id: "create",
    label: "Create",
    description: "Build a new launcher profile",
  },
  {
    id: "account",
    label: "Account",
    description: "Microsoft session and authentication flow",
  },
];

const defaultHomeTab = homeTabs[0]!;

const activeTabMeta = computed(() => {
  return homeTabs.find((tab) => tab.id === activeTab.value) ?? defaultHomeTab;
});

const heroAvatarUrl = computed(() => {
  const username = authData.value?.username?.trim() || "MHF_Steve";
  return `https://mc-heads.net/body/${encodeURIComponent(username)}/right`;
});

const stopAuthPolling = () => {
  if (authStatusInterval !== null) {
    window.clearInterval(authStatusInterval);
    authStatusInterval = null;
  }
};

const stopInstanceRefresh = () => {
  if (instanceRefreshInterval !== null) {
    window.clearInterval(instanceRefreshInterval);
    instanceRefreshInterval = null;
  }
};

const resetAuthFlow = (closePopup = false) => {
  stopAuthPolling();

  if (closePopup && authPopup && !authPopup.closed) {
    authPopup.close();
  }

  authPopup = null;
  authStartedAt = 0;
  authState.value = "";
  authWindowWasClosed.value = false;
};

const formatVersionType = (type: string) => {
  switch (type) {
    case "release":
      return "Release";
    case "snapshot":
      return "Snapshot";
    case "old_beta":
      return "Beta";
    case "old_alpha":
      return "Alpha";
    default:
      return type;
  }
};

const formatDate = (timestamp: number) => {
  if (!timestamp) {
    return "Never";
  }

  return new Date(timestamp).toLocaleString();
};

const formatReleaseTime = (releaseTime: string) => {
  if (!releaseTime) {
    return "Unknown";
  }

  const date = new Date(releaseTime);
  return Number.isNaN(date.getTime()) ? releaseTime : date.toLocaleDateString();
};

const formatJavaComponent = (component: string) => {
  return component || "default-runtime";
};

const completeAuthentication = (data: AuthData) => {
  authData.value = data;
  isAuthenticating.value = false;
  error.value = null;
  resetAuthFlow(true);
};

const failAuthentication = (message: string, closePopup = false) => {
  authData.value = null;
  isAuthenticating.value = false;
  error.value = message;
  resetAuthFlow(closePopup);
};

const loadSession = async () => {
  try {
    const response = await fetch("/api/session");
    const data = (await response.json()) as SessionResponse;

    if (!data.success) {
      authData.value = null;
      error.value = data.error || "Failed to restore the saved session";
      return;
    }

    if (data.authenticated && data.uuid && data.username) {
      authData.value = { uuid: data.uuid, username: data.username };
      return;
    }

    authData.value = null;
  } catch (err) {
    authData.value = null;
    error.value =
      err instanceof Error ? err.message : "Failed to restore the saved session";
  }
};

const loadInstances = async () => {
  try {
    isLoadingInstances.value = true;
    const response = await fetch("/api/instances");
    const data = (await response.json()) as {
      success: boolean;
      instances?: LauncherInstance[];
      error?: string;
    };

    if (!data.success) {
      error.value = data.error || "Failed to load instances";
      return;
    }

    instances.value = data.instances ?? [];
    if (
      selectedInstanceName.value &&
      instances.value.some((instance) => instance.name === selectedInstanceName.value)
    ) {
      return;
    }

    selectedInstanceName.value = instances.value[0]?.name ?? "";
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Failed to load instances";
  } finally {
    isLoadingInstances.value = false;
  }
};

const loadVersions = async () => {
  try {
    isLoadingVersions.value = true;
    const query = new URLSearchParams({
      includeSnapshots: String(includeSnapshots.value),
      includeBetas: String(includeBetas.value),
      includeAlphas: String(includeAlphas.value),
    });

    const response = await fetch(`/api/instances/versions?${query.toString()}`);
    const data = (await response.json()) as {
      success: boolean;
      versions?: AvailableVersion[];
      error?: string;
    };

    if (!data.success) {
      error.value = data.error || "Failed to load versions";
      return;
    }

    availableVersions.value = data.versions ?? [];
    if (
      selectedVersionId.value &&
      availableVersions.value.some((version) => version.id === selectedVersionId.value)
    ) {
      return;
    }

    selectedVersionId.value = availableVersions.value[0]?.id ?? "";
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Failed to load versions";
  } finally {
    isLoadingVersions.value = false;
  }
};

const pollAuthStatus = async () => {
  if (!authState.value) {
    return;
  }

  try {
    const response = await fetch(
      `/api/auth/status?state=${encodeURIComponent(authState.value)}`,
    );
    const data = (await response.json()) as {
      success: boolean;
      status: string;
      uuid?: string;
      username?: string;
      error?: string;
    };

    if (data.status === "pending") {
      if (authPopup?.closed) {
        authWindowWasClosed.value = true;

        if (Date.now() - authStartedAt > 10_000) {
          failAuthentication(
            "The login window was closed before the Microsoft sign-in finished.",
          );
        }
      }

      return;
    }

    if (data.status === "success" && data.uuid && data.username) {
      completeAuthentication({ uuid: data.uuid, username: data.username });
      return;
    }

    failAuthentication(data.error || "Authentication failed", true);
  } catch (err) {
    failAuthentication(
      err instanceof Error ? err.message : "Failed to check authentication status",
      true,
    );
  }
};

const startAuthPolling = () => {
  stopAuthPolling();
  authStartedAt = Date.now();
  authStatusInterval = window.setInterval(() => {
    void pollAuthStatus();
  }, 1000);
  void pollAuthStatus();
};

const handleLogin = async () => {
  try {
    isAuthenticating.value = true;
    error.value = null;
    launcherMessage.value = null;
    authWindowWasClosed.value = false;

    const response = await fetch("/api/auth/login");
    const data = (await response.json()) as {
      success: boolean;
      url?: string;
      state?: string;
      error?: string;
    };

    if (!data.success || !data.url || !data.state) {
      error.value = data.error || "Authentication failed";
      isAuthenticating.value = false;
      return;
    }

    authUrl.value = data.url;
    authState.value = data.state;

    authPopup = window.open(
      authUrl.value,
      "Microsoft Login",
      "popup=yes,width=520,height=760",
    );

    if (!authPopup) {
      failAuthentication(
        "The login window could not be opened. Please allow popups and try again.",
      );
      return;
    }

    authPopup.focus();
    startAuthPolling();
  } catch (err) {
    failAuthentication(err instanceof Error ? err.message : "Unknown error", true);
  }
};

const handleCreateInstance = async () => {
  if (!newInstanceName.value.trim()) {
    error.value = "Please enter an instance name";
    return;
  }

  if (!selectedVersionId.value) {
    error.value = "Please choose a Minecraft version";
    return;
  }

  try {
    isCreatingInstance.value = true;
    error.value = null;
    launcherMessage.value = null;

    const response = await fetch("/api/instances", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        name: newInstanceName.value.trim(),
        versionId: selectedVersionId.value,
      }),
    });

    const data = (await response.json()) as {
      success: boolean;
      instance?: LauncherInstance;
      error?: string;
    };

    if (!data.success || !data.instance) {
      error.value = data.error || "Failed to create instance";
      return;
    }

    launcherMessage.value = `Instance ${data.instance.name} created successfully.`;
    newInstanceName.value = "";
    await loadInstances();
    selectedInstanceName.value = data.instance.name;
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Failed to create instance";
  } finally {
    isCreatingInstance.value = false;
  }
};

const handleLaunch = async () => {
  if (!authData.value || !selectedInstance.value) {
    return;
  }

  try {
    isLaunching.value = true;
    error.value = null;
    launcherMessage.value = null;

    const response = await fetch(
      `/api/instances/${encodeURIComponent(selectedInstance.value.name)}/launch`,
      {
        method: "POST",
      },
    );
    const data = (await response.json()) as {
      success: boolean;
      instanceName?: string;
      version?: string;
      pid?: number;
      logFile?: string;
      javaMajorVersion?: number;
      javaExecutable?: string;
      error?: string;
    };

    if (!data.success) {
      error.value = data.error || "Minecraft could not be launched";
      return;
    }

    launcherMessage.value = `Instance ${data.instanceName ?? selectedInstance.value.name} started with ${data.version ?? selectedInstance.value.versionId}${data.javaMajorVersion ? ` on Java ${data.javaMajorVersion}` : ""}${data.pid ? ` (PID ${data.pid})` : ""}.`;
    await loadInstances();
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Minecraft could not be launched";
  } finally {
    isLaunching.value = false;
  }
};

const handleStop = async () => {
  if (!selectedInstance.value) {
    return;
  }

  try {
    isLaunching.value = true;
    error.value = null;
    launcherMessage.value = null;

    const response = await fetch(
      `/api/instances/${encodeURIComponent(selectedInstance.value.name)}/stop`,
      {
        method: "POST",
      },
    );
    const data = (await response.json()) as {
      success: boolean;
      instanceName?: string;
      stopped?: boolean;
      error?: string;
    };

    if (!data.success) {
      error.value = data.error || "The instance could not be stopped";
      return;
    }

    launcherMessage.value = `Instance ${data.instanceName ?? selectedInstance.value.name} stopped successfully.`;
    await loadInstances();
  } catch (err) {
    error.value = err instanceof Error ? err.message : "The instance could not be stopped";
  } finally {
    isLaunching.value = false;
  }
};

const handleLogout = async () => {
  try {
    await fetch("/api/auth/logout", { method: "POST" });
  } catch {
    // ignore logout transport errors and clear the local UI state anyway
  }

  authData.value = null;
  error.value = null;
  launcherMessage.value = null;
};

watch([includeSnapshots, includeBetas, includeAlphas], () => {
  void loadVersions();
});

onMounted(() => {
  void loadSession();
  void loadInstances();
  void loadVersions();
  instanceRefreshInterval = window.setInterval(() => {
    void loadInstances();
  }, 3000);
});

onBeforeUnmount(() => {
  resetAuthFlow(true);
  stopInstanceRefresh();
});

</script>

<template>
  <div class="relative h-dvh min-h-0 w-full overflow-hidden bg-[#050b16] text-slate-100">
    <div aria-hidden="true" class="pointer-events-none absolute inset-0 overflow-hidden">
      <div class="absolute inset-0 bg-[radial-gradient(circle_at_top_left,rgba(96,165,250,0.16),transparent_28%),radial-gradient(circle_at_top_right,rgba(59,130,246,0.12),transparent_22%),linear-gradient(180deg,#07111f_0%,#040914_58%,#02060d_100%)]" />
      <div class="absolute -left-24 top-10 h-72 w-72 rounded-full bg-sky-400/10 blur-3xl" />
      <div class="absolute right-[-5%] top-[-5%] h-96 w-96 rounded-full bg-blue-500/8 blur-3xl" />
    </div>

    <div class="relative z-10 h-full w-full">
      <div class="launcher-shell launcher-shell-full rounded-none">
        <div class="grid h-full min-h-0 lg:grid-cols-[104px_minmax(0,1fr)]">
          <aside class="border-b border-sky-300/14 bg-[#27457c]/92 lg:border-r lg:border-b-0">
            <div class="flex items-center justify-between gap-4 p-4 lg:h-full lg:flex-col lg:items-center lg:justify-between lg:px-3 lg:py-5">
              <div class="flex items-center gap-3 lg:flex-col">
                <div class="flex h-20 w-20 items-center justify-center rounded-[26px] border border-sky-300/24 bg-white/8 shadow-[inset_0_1px_0_rgba(255,255,255,0.08)]">
                  <svg viewBox="0 0 24 24" class="h-9 w-9 fill-white" aria-hidden="true">
                    <path d="M13.2 1 5 13.2h5.1L8.8 23 19 9.8h-5.1L13.2 1Z" />
                  </svg>
                </div>

                <div class="flex flex-wrap gap-3 lg:flex-col" role="tablist" aria-label="Launcher navigation">
                  <button
                    v-for="tab in homeTabs"
                    :key="tab.id"
                    :id="`tab-${tab.id}`"
                    type="button"
                    role="tab"
                    :aria-selected="activeTab === tab.id"
                    :aria-controls="`panel-${tab.id}`"
                    class="launcher-rail-button"
                    :class="activeTab === tab.id ? 'launcher-rail-button-active' : ''"
                    @click="activeTab = tab.id"
                  >
                    <svg v-if="tab.id === 'play'" viewBox="0 0 24 24" class="h-6 w-6 fill-current" aria-hidden="true">
                      <path d="M8 5.14v14l11-7-11-7Z" />
                    </svg>
                    <svg v-else-if="tab.id === 'library'" viewBox="0 0 24 24" class="h-6 w-6 fill-current" aria-hidden="true">
                      <path d="M4 4h7v7H4V4Zm9 0h7v7h-7V4ZM4 13h7v7H4v-7Zm9 0h7v7h-7v-7Z" />
                    </svg>
                    <svg v-else-if="tab.id === 'create'" viewBox="0 0 24 24" class="h-6 w-6 fill-current" aria-hidden="true">
                      <path d="M11 4h2v7h7v2h-7v7h-2v-7H4v-2h7V4Z" />
                    </svg>
                    <svg v-else viewBox="0 0 24 24" class="h-6 w-6 fill-current" aria-hidden="true">
                      <path d="M12 12a4 4 0 1 0-4-4 4 4 0 0 0 4 4Zm0 2c-4.42 0-8 2.24-8 5v1h16v-1c0-2.76-3.58-5-8-5Z" />
                    </svg>
                  </button>
                </div>
              </div>

              <div class="hidden lg:block">
                <div class="rounded-2xl border border-white/10 bg-white/6 px-3 py-4 text-center">
                  <p class="text-[10px] font-semibold uppercase tracking-[0.22em] text-sky-100/70">Live</p>
                  <p class="mt-2 text-xl font-semibold text-white">{{ runningInstancesCount }}</p>
                  <p class="mt-1 text-[11px] uppercase tracking-[0.18em] text-slate-300">running</p>
                </div>
              </div>
            </div>
          </aside>

          <div class="grid min-h-0 grid-rows-[auto_minmax(0,1fr)]">
            <header class="border-b border-sky-300/14 bg-slate-950/70 backdrop-blur-sm">
              <div class="flex flex-col gap-4 px-4 py-4 lg:flex-row lg:items-center lg:justify-between lg:px-6">
                <div>
                  <p class="font-mono text-2xl font-bold uppercase tracking-[0.16em] text-white">TheLauncherProject</p>
                  <p class="font-mono text-xs uppercase tracking-[0.18em] text-sky-100/55">
                    {{ activeTabMeta.label }} · focused launcher workflow
                  </p>
                </div>

                <div class="flex flex-wrap items-center gap-3">
                  <button type="button" class="launcher-toolbar-button" @click="activeTab = 'library'">
                    <svg viewBox="0 0 24 24" class="h-5 w-5 fill-current" aria-hidden="true">
                      <path d="M4 5h16v3H4V5Zm2 5h12v9H6v-9Z" />
                    </svg>
                    <span class="font-mono uppercase tracking-[0.12em]">
                      {{ selectedInstance ? selectedInstance.name : 'NO INSTANCE' }}
                    </span>
                  </button>

                  <button type="button" class="launcher-toolbar-button" @click="activeTab = 'account'">
                    <span class="flex h-8 w-8 items-center justify-center overflow-hidden rounded-lg border border-sky-300/20 bg-sky-200/10">
                      <img :src="heroAvatarUrl" alt="Account avatar" class="h-full w-full object-cover" />
                    </span>
                    <span class="font-mono uppercase tracking-[0.12em]">
                      {{ authData ? authData.username : 'SIGN IN' }}
                    </span>
                  </button>
                </div>
              </div>
            </header>

            <div class="min-h-0 overflow-y-auto p-3 lg:p-4">
              <div class="mx-auto flex w-full max-w-6xl flex-col gap-4">
                <section v-if="error || launcherMessage || isAuthenticating" class="launcher-panel p-4 sm:p-5">
                  <div class="space-y-3">
                    <div v-if="error" class="rounded-2xl border border-rose-300/20 bg-rose-400/10 px-4 py-3 text-sm text-rose-50">
                      <p class="font-semibold uppercase tracking-[0.12em]">Error</p>
                      <p class="mt-2 text-rose-100/90">{{ error }}</p>
                    </div>

                    <div v-if="launcherMessage" class="rounded-2xl border border-emerald-300/20 bg-emerald-400/10 px-4 py-3 text-sm text-emerald-50">
                      <p class="font-semibold uppercase tracking-[0.12em]">Launcher Update</p>
                      <p class="mt-2 text-emerald-100/90">{{ launcherMessage }}</p>
                    </div>

                    <div v-if="isAuthenticating" class="rounded-2xl border border-sky-300/15 bg-sky-400/8 px-4 py-3 text-sm text-slate-200">
                      <p class="font-semibold uppercase tracking-[0.12em] text-white">Authentication in progress</p>
                      <p class="mt-2">Finish the Microsoft sign-in in the popup window. This launcher checks the session automatically.</p>
                      <p v-if="authWindowWasClosed" class="mt-2 text-amber-200">The popup was closed. Waiting briefly for a completed callback.</p>
                      <a class="mt-3 block break-all text-sky-200 underline decoration-sky-300/30 underline-offset-4 hover:text-white" :href="authUrl" target="_blank" rel="noopener noreferrer">{{ authUrl }}</a>
                    </div>
                  </div>
                </section>

                <section class="launcher-panel launcher-hero-stage relative isolate overflow-hidden">
                  <div aria-hidden="true" class="pointer-events-none absolute inset-0">
                    <div class="absolute inset-0 bg-[linear-gradient(to_right,rgba(59,130,246,0.16)_1px,transparent_1px),linear-gradient(to_bottom,rgba(59,130,246,0.16)_1px,transparent_1px)] bg-size-[120px_74px] opacity-60" />
                    <div class="absolute inset-x-0 bottom-0 h-56 bg-[linear-gradient(to_top,rgba(37,99,235,0.26),transparent)]" />
                    <div class="absolute inset-x-0 bottom-[-6%] h-72 bg-[linear-gradient(to_right,rgba(59,130,246,0.22)_1px,transparent_1px),linear-gradient(to_bottom,rgba(59,130,246,0.22)_1px,transparent_1px)] bg-size-[84px_52px] opacity-75" />
                    <div class="absolute inset-0 bg-[radial-gradient(circle_at_center,transparent_26%,rgba(4,9,20,0.68)_78%)]" />
                  </div>

                  <div class="launcher-hero-stage relative z-10 flex flex-col px-4 py-5 sm:px-6 sm:py-6">
                    <div class="flex items-start justify-between gap-3">
                      <div class="rounded-2xl border border-sky-300/20 bg-[#1c315b]/90 px-5 py-4 shadow-[0_16px_28px_rgba(30,64,175,0.18)]">
                        <p class="font-mono text-lg font-semibold uppercase tracking-[0.14em] text-white">{{ activeTabMeta.label }}</p>
                      </div>

                      <div class="rounded-2xl border border-sky-300/12 bg-slate-950/40 px-4 py-3 text-right">
                        <p class="font-mono text-[11px] uppercase tracking-[0.2em] text-slate-400">Session state</p>
                        <p class="mt-1 text-sm font-semibold text-white">
                          {{ selectedInstance?.running ? 'RUNNING' : authData ? 'READY' : 'OFFLINE' }}
                        </p>
                      </div>
                    </div>

                    <div class="flex flex-1 flex-col items-center justify-center pb-24 pt-8 text-center xl:pb-28 xl:pt-10">
                      <p class="font-mono text-4xl font-bold uppercase tracking-[0.22em] text-white sm:text-5xl">
                        {{ selectedInstance?.name ?? authData?.username ?? 'PLAY' }}
                      </p>
                      <p class="mt-3 max-w-xl text-sm uppercase tracking-[0.18em] text-sky-100/70 sm:text-base">
                        {{
                          selectedInstance
                            ? `${selectedInstance.versionId} · ${formatVersionType(selectedInstance.versionType)} · Java ${selectedInstance.javaMajorVersion}`
                            : authData
                              ? 'choose an instance in the library or create a new profile'
                              : 'connect Microsoft and prepare your launcher session'
                        }}
                      </p>

                      <div class="relative mt-6">
                        <div class="absolute inset-x-8 bottom-2 h-10 rounded-full bg-blue-500/25 blur-2xl" />
                        <img :src="heroAvatarUrl" alt="Minecraft avatar render" class="launcher-avatar relative z-10 object-contain drop-shadow-[0_24px_45px_rgba(0,0,0,0.55)]" />
                      </div>
                    </div>

                    <div class="absolute inset-x-0 bottom-0 px-4 pb-4 sm:px-6 sm:pb-6">
                      <div class="mx-auto max-w-xl rounded-[26px] border border-sky-300/25 bg-[#1a2f57]/94 shadow-[0_18px_42px_rgba(30,64,175,0.28)]">
                        <div class="grid grid-cols-[minmax(0,1fr)_88px]">
                          <button
                            v-if="selectedInstance?.running"
                            type="button"
                            @click="handleStop"
                            :disabled="isLaunching"
                            class="flex min-h-24 flex-col items-center justify-center border-r border-sky-300/20 px-4 text-center transition hover:bg-red-500/12 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            <span class="font-mono text-4xl font-bold uppercase tracking-[0.12em] text-white">{{ isLaunching ? 'STOPPING' : 'STOP' }}</span>
                            <span class="mt-2 text-sm uppercase tracking-[0.14em] text-slate-300">{{ selectedInstance.name }} {{ selectedInstance.pid ? `· PID ${selectedInstance.pid}` : '' }}</span>
                          </button>

                          <button
                            v-else-if="authData"
                            type="button"
                            @click="handleLaunch"
                            :disabled="isLaunching || !selectedInstance"
                            class="flex min-h-24 flex-col items-center justify-center border-r border-sky-300/20 px-4 text-center transition hover:bg-sky-400/10 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            <span class="font-mono text-4xl font-bold uppercase tracking-[0.12em] text-white">{{ isLaunching ? 'LAUNCHING' : 'LAUNCH' }}</span>
                            <span class="mt-2 text-sm uppercase tracking-[0.14em] text-slate-300">{{ selectedInstance ? selectedInstance.versionId : 'select instance first' }}</span>
                          </button>

                          <button
                            v-else
                            type="button"
                            @click="handleLogin"
                            :disabled="isAuthenticating"
                            class="flex min-h-24 flex-col items-center justify-center border-r border-sky-300/20 px-4 text-center transition hover:bg-sky-400/10 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            <span class="font-mono text-3xl font-bold uppercase tracking-[0.12em] text-white sm:text-4xl">{{ isAuthenticating ? 'WAIT' : 'LOGIN' }}</span>
                            <span class="mt-2 text-sm uppercase tracking-[0.14em] text-slate-300">Microsoft account required</span>
                          </button>

                          <button type="button" class="flex items-center justify-center text-white transition hover:bg-sky-400/10" @click="activeTab = selectedInstance ? 'library' : authData ? 'create' : 'account'">
                            <svg viewBox="0 0 24 24" class="h-7 w-7 fill-current" aria-hidden="true">
                              <path d="M7 9.5 12 15l5-5.5H7Z" />
                            </svg>
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </section>

                <section v-if="activeTab === 'play'" id="panel-play" role="tabpanel" aria-labelledby="tab-play" class="grid gap-4 lg:grid-cols-[minmax(0,1.2fr)_minmax(280px,0.8fr)]">
                  <div class="launcher-panel p-5 sm:p-6">
                    <div class="mb-5 flex items-center justify-between gap-3">
                      <div>
                        <p class="font-mono text-xs uppercase tracking-[0.2em] text-sky-100/70">Play</p>
                        <h2 class="mt-2 font-mono text-xl font-bold uppercase tracking-[0.12em] text-white">Launch readiness</h2>
                      </div>
                      <span class="rounded-full border border-sky-300/15 bg-white/5 px-3 py-1 text-xs font-medium uppercase tracking-[0.14em] text-slate-300">{{ runningInstancesCount }} active</span>
                    </div>

                    <div class="grid gap-3 md:grid-cols-3">
                      <div class="stat-tile">
                        <p class="text-xs font-medium uppercase tracking-[0.16em] text-slate-400">Account</p>
                        <p class="mt-2 text-lg font-semibold text-white">{{ authData ? authData.username : 'Offline' }}</p>
                        <p class="mt-1 text-sm text-slate-300">{{ authData ? 'Launch permissions ready' : 'Login required' }}</p>
                      </div>
                      <div class="stat-tile">
                        <p class="text-xs font-medium uppercase tracking-[0.16em] text-slate-400">Selected</p>
                        <p class="mt-2 text-lg font-semibold text-white">{{ selectedInstance?.name ?? 'No instance' }}</p>
                        <p class="mt-1 text-sm text-slate-300">{{ selectedInstance?.versionId ?? 'Choose one in library' }}</p>
                      </div>
                      <div class="stat-tile">
                        <p class="text-xs font-medium uppercase tracking-[0.16em] text-slate-400">Status</p>
                        <p class="mt-2 text-lg font-semibold text-white">{{ selectedInstance?.running ? 'Running' : authData ? 'Ready' : 'Offline' }}</p>
                        <p class="mt-1 text-sm text-slate-300">{{ selectedInstance?.running ? 'Stop the current instance' : 'Use the primary action above' }}</p>
                      </div>
                    </div>

                    <div class="mt-5 grid gap-3 sm:grid-cols-3">
                      <button type="button" class="soft-button-muted w-full" @click="activeTab = 'library'">Open library</button>
                      <button type="button" class="soft-button-muted w-full" @click="activeTab = 'create'">Create instance</button>
                      <button type="button" class="soft-button-muted w-full" @click="activeTab = 'account'">Account panel</button>
                    </div>
                  </div>

                  <div class="launcher-panel p-5 sm:p-6">
                    <div class="mb-4">
                      <p class="font-mono text-xs uppercase tracking-[0.2em] text-sky-100/70">Current context</p>
                      <h2 class="mt-2 font-mono text-xl font-bold uppercase tracking-[0.12em] text-white">Selected profile</h2>
                    </div>

                    <div v-if="selectedInstance" class="space-y-3">
                      <div class="detail-row"><p class="detail-key">Profile</p><p class="detail-value">{{ selectedInstance.name }}</p></div>
                      <div class="detail-row"><p class="detail-key">Version</p><p class="detail-value">{{ selectedInstance.versionId }} · {{ formatVersionType(selectedInstance.versionType) }}</p></div>
                      <div class="detail-row"><p class="detail-key">Java</p><p class="detail-value">Java {{ selectedInstance.javaMajorVersion }} · {{ formatJavaComponent(selectedInstance.javaComponent) }}</p></div>
                      <div class="detail-row"><p class="detail-key">Last played</p><p class="detail-value">{{ formatDate(selectedInstance.lastPlayedAt) }}</p></div>
                    </div>

                    <div v-else-if="!instances.length && !isLoadingInstances" class="rounded-3xl border border-dashed border-white/12 bg-white/4 p-4 text-sm text-slate-300">
                      No instances yet. Open create to build your first launcher profile.
                    </div>

                    <div v-else class="space-y-3">
                      <p class="text-sm text-slate-300">No instance selected yet. Choose one in the library to launch Minecraft from Play.</p>
                      <button v-for="instance in instances.slice(0, 3)" :key="instance.slug" type="button" @click="selectedInstanceName = instance.name" class="w-full rounded-2xl border px-4 py-3 text-left transition" :class="selectedInstanceName === instance.name ? 'border-sky-300/35 bg-sky-300/10' : 'border-white/10 bg-white/5 hover:border-sky-200/20 hover:bg-white/8'">
                        <div class="flex items-center justify-between gap-3">
                          <div class="min-w-0">
                            <p class="truncate font-semibold text-white">{{ instance.name }}</p>
                            <p class="mt-1 text-sm text-slate-300">{{ instance.versionId }} · {{ formatVersionType(instance.versionType) }}</p>
                          </div>
                          <span class="text-xs uppercase tracking-[0.14em]" :class="instance.running ? 'text-emerald-300' : 'text-slate-400'">{{ instance.running ? 'Running' : 'Stopped' }}</span>
                        </div>
                      </button>
                    </div>
                  </div>
                </section>

                <section v-else-if="activeTab === 'library'" id="panel-library" role="tabpanel" aria-labelledby="tab-library" class="grid gap-4 lg:grid-cols-[minmax(320px,0.9fr)_minmax(0,1.1fr)]">
                  <div class="launcher-panel p-5 sm:p-6">
                    <div class="mb-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                      <div>
                        <p class="font-mono text-xs uppercase tracking-[0.2em] text-sky-100/70">Library</p>
                        <h2 class="mt-2 font-mono text-xl font-bold uppercase tracking-[0.12em] text-white">Instance list</h2>
                        <p class="mt-2 text-sm text-slate-300">Select the profile you want to inspect or launch.</p>
                      </div>
                      <span class="rounded-full border border-sky-300/15 bg-white/5 px-3 py-1 text-xs font-medium uppercase tracking-[0.14em] text-slate-300">{{ isLoadingInstances ? 'Refreshing' : `${instances.length} profiles` }}</span>
                    </div>

                    <div v-if="!instances.length && !isLoadingInstances" class="rounded-3xl border border-dashed border-white/12 bg-white/4 p-5 text-sm text-slate-300">
                      No instances yet. Switch to create and add your first Minecraft profile.
                    </div>

                    <div v-else class="space-y-3">
                      <button v-for="instance in instances" :key="instance.slug" type="button" @click="selectedInstanceName = instance.name" class="group w-full rounded-3xl border px-4 py-4 text-left transition duration-200" :class="selectedInstanceName === instance.name ? 'border-sky-300/35 bg-sky-300/10 shadow-[0_18px_40px_rgba(56,189,248,0.14)]' : 'border-white/10 bg-white/5 hover:border-sky-200/20 hover:bg-white/8'">
                        <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                          <div class="space-y-1">
                            <div class="flex flex-wrap items-center gap-2">
                              <p class="text-lg font-semibold text-white">{{ instance.name }}</p>
                              <span class="rounded-full px-2.5 py-1 text-xs font-medium" :class="instance.running ? 'bg-emerald-400/15 text-emerald-100 ring-1 ring-emerald-300/25' : 'bg-white/8 text-slate-300 ring-1 ring-white/10'">{{ instance.running ? 'Running' : 'Stopped' }}</span>
                            </div>
                            <p class="text-sm text-slate-300">{{ instance.versionId }} • {{ formatVersionType(instance.versionType) }}</p>
                          </div>
                          <p class="text-sm text-slate-400">{{ formatDate(instance.lastPlayedAt) }}</p>
                        </div>
                      </button>
                    </div>
                  </div>

                  <div class="launcher-panel p-5 sm:p-6">
                    <div class="mb-5">
                      <p class="font-mono text-xs uppercase tracking-[0.2em] text-sky-100/70">Details</p>
                      <h2 class="mt-2 font-mono text-xl font-bold uppercase tracking-[0.12em] text-white">Selected instance</h2>
                    </div>

                    <div v-if="selectedInstance" class="space-y-3">
                      <div class="detail-row"><p class="detail-key">Name</p><p class="detail-value">{{ selectedInstance.name }}</p></div>
                      <div class="detail-row"><p class="detail-key">Version</p><p class="detail-value">{{ selectedInstance.versionId }} ({{ formatVersionType(selectedInstance.versionType) }})</p></div>
                      <div class="detail-row"><p class="detail-key">Java</p><p class="detail-value">Java {{ selectedInstance.javaMajorVersion }} · {{ formatJavaComponent(selectedInstance.javaComponent) }}</p></div>
                      <div class="detail-row"><p class="detail-key">Created</p><p class="detail-value">{{ formatDate(selectedInstance.createdAt) }}</p></div>
                      <div class="detail-row"><p class="detail-key">Last played</p><p class="detail-value">{{ formatDate(selectedInstance.lastPlayedAt) }}</p></div>
                      <div v-if="selectedInstance.running" class="detail-row"><p class="detail-key">Process</p><p class="detail-value">PID {{ selectedInstance.pid ?? '?' }}</p></div>
                      <div class="mt-5 grid gap-3 sm:grid-cols-2">
                        <button type="button" class="soft-button-muted w-full" @click="activeTab = 'play'">Open in play</button>
                        <button v-if="selectedInstance.running" type="button" class="soft-button-danger w-full" :disabled="isLaunching" @click="handleStop">{{ isLaunching ? 'Stopping...' : 'Stop instance' }}</button>
                      </div>
                    </div>

                    <div v-else class="rounded-3xl border border-dashed border-white/12 bg-white/4 p-5 text-sm text-slate-300">
                      Choose a profile from the list to inspect its version, Java runtime and status.
                    </div>
                  </div>
                </section>

                <section v-else-if="activeTab === 'create'" id="panel-create" role="tabpanel" aria-labelledby="tab-create" class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_300px]">
                  <div class="launcher-panel p-5 sm:p-6">
                    <div class="mb-5">
                      <p class="font-mono text-xs uppercase tracking-[0.2em] text-sky-100/70">Create</p>
                      <h2 class="mt-2 font-mono text-xl font-bold uppercase tracking-[0.12em] text-white">Build new instance</h2>
                      <p class="mt-2 text-sm text-slate-300">Set a name, choose a version and store a new launcher profile.</p>
                    </div>

                    <div class="grid gap-4">
                      <div class="field-shell">
                        <label class="field-label" for="instance-name">Instance name</label>
                        <input id="instance-name" v-model="newInstanceName" type="text" placeholder="My survival world" class="field-input" />
                      </div>

                      <div class="field-shell">
                        <p class="field-label">Version filters</p>
                        <div class="flex flex-wrap gap-3">
                          <label class="toggle-pill"><input v-model="includeSnapshots" type="checkbox" class="h-4 w-4 rounded border-white/10 bg-slate-950/50 text-sky-300 focus:ring-sky-300/30" /><span>Snapshots</span></label>
                          <label class="toggle-pill"><input v-model="includeBetas" type="checkbox" class="h-4 w-4 rounded border-white/10 bg-slate-950/50 text-sky-300 focus:ring-sky-300/30" /><span>Betas</span></label>
                          <label class="toggle-pill"><input v-model="includeAlphas" type="checkbox" class="h-4 w-4 rounded border-white/10 bg-slate-950/50 text-sky-300 focus:ring-sky-300/30" /><span>Alphas</span></label>
                        </div>
                      </div>

                      <div class="field-shell">
                        <label class="field-label" for="version-select">Minecraft version</label>
                        <select id="version-select" v-model="selectedVersionId" class="field-input" :disabled="isLoadingVersions || !availableVersions.length">
                          <option disabled value="">Select a version</option>
                          <option v-for="version in availableVersions" :key="version.id" :value="version.id">{{ version.id }} — {{ formatVersionType(version.type) }} — {{ formatReleaseTime(version.releaseTime) }}</option>
                        </select>
                        <p class="mt-3 text-xs text-slate-400">{{ isLoadingVersions ? 'Loading versions...' : `${availableVersions.length} versions available` }}</p>
                      </div>

                      <button type="button" @click="handleCreateInstance" :disabled="isCreatingInstance || !selectedVersionId" class="soft-button w-full sm:w-auto">{{ isCreatingInstance ? 'Creating instance...' : 'Create Instance' }}</button>
                    </div>
                  </div>

                  <div class="launcher-panel p-5 sm:p-6">
                    <p class="font-mono text-xs uppercase tracking-[0.2em] text-sky-100/70">Selection</p>
                    <h2 class="mt-2 font-mono text-xl font-bold uppercase tracking-[0.12em] text-white">Version preview</h2>

                    <div v-if="selectedVersion" class="mt-5 space-y-3">
                      <div class="detail-row"><p class="detail-key">Version</p><p class="detail-value">{{ selectedVersion.id }}</p></div>
                      <div class="detail-row"><p class="detail-key">Channel</p><p class="detail-value">{{ formatVersionType(selectedVersion.type) }}</p></div>
                      <div class="detail-row"><p class="detail-key">Released</p><p class="detail-value">{{ formatReleaseTime(selectedVersion.releaseTime) }}</p></div>
                    </div>

                    <div v-else class="mt-5 rounded-3xl border border-dashed border-white/12 bg-white/4 p-4 text-sm text-slate-300">
                      Pick a version from the list to preview the release information here.
                    </div>
                  </div>
                </section>

                <section v-else id="panel-account" role="tabpanel" aria-labelledby="tab-account" class="launcher-panel p-5 sm:p-6">
                  <div class="mb-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                    <div>
                      <p class="font-mono text-xs uppercase tracking-[0.2em] text-sky-100/70">Account</p>
                      <h2 class="mt-2 font-mono text-xl font-bold uppercase tracking-[0.12em] text-white">Microsoft session</h2>
                      <p class="mt-2 text-sm text-slate-300">Connect, inspect and manage the current authenticated launcher session.</p>
                    </div>
                  </div>

                  <div v-if="authData" class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_220px]">
                    <div class="space-y-3">
                      <div class="detail-row"><p class="detail-key">Username</p><p class="detail-value">{{ authData.username }}</p></div>
                      <div class="detail-row"><div class="min-w-0"><p class="detail-key">UUID</p><p class="mt-1 break-all font-mono text-xs text-slate-200">{{ authData.uuid }}</p></div></div>
                    </div>

                    <div class="flex flex-col gap-3">
                      <button type="button" class="soft-button-muted w-full" @click="activeTab = 'play'">Back to play</button>
                      <button type="button" class="soft-button-muted w-full" @click="handleLogout">Logout</button>
                    </div>
                  </div>

                  <div v-else class="space-y-4">
                    <div class="rounded-3xl border border-dashed border-white/12 bg-white/4 p-4 text-sm leading-6 text-slate-300">Sign in with Microsoft to enable launching and keep your player session ready across restarts.</div>
                    <button type="button" @click="handleLogin" :disabled="isAuthenticating" class="soft-button w-full sm:w-auto">{{ isAuthenticating ? 'Authenticating...' : 'Login with Microsoft' }}</button>
                  </div>

                  <div v-if="isAuthenticating" class="mt-5 rounded-3xl border border-white/10 bg-slate-950/35 p-4 text-sm text-slate-300">
                    <p class="font-semibold text-white">Authentication in progress</p>
                    <p class="mt-2">Finish the Microsoft sign-in in the popup window.</p>
                    <p v-if="authWindowWasClosed" class="mt-2 text-amber-200">The popup was closed; the launcher is still checking the status briefly.</p>
                    <a class="mt-3 block break-all text-sky-200 underline decoration-sky-300/30 underline-offset-4 hover:text-white" :href="authUrl" target="_blank" rel="noopener noreferrer">{{ authUrl }}</a>
                  </div>
                </section>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
