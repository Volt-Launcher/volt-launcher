<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";
import { formatFileSize } from "@/composables/useProviders";
import { minecraftVersionOf, loaderOf } from "@/composables/helpers";
import type { ProjectSummary, ProjectVersion } from "@/composables/types";

const props = defineProps<{ project: ProjectSummary | null }>();
const emit = defineEmits<{ close: [] }>();

const {
  t,
  instances,
  loadInstances,
  selectedInstanceName,
  installIntoProfile,
  installModpack,
  loadProjectVersions,
  isInstalling,
  launcherMessage,
} = useLauncher();

const targetProfile = ref("");
const selectedVersionId = ref("");
const withDependencies = ref(true);
const versions = ref<ProjectVersion[]>([]);
const isLoadingVersions = ref(false);

const isModpack = computed(() => props.project?.kind === "modpack");

const profile = computed(() => instances.value.find((instance) => instance.name === targetProfile.value) ?? null);

/**
 * Versions are filtered by the target profile's Minecraft version and loader, so the list only
 * ever offers builds that will actually load in that profile.
 */
const compatibleVersions = computed(() => {
  if (isModpack.value || !profile.value) return versions.value;

  const gameVersion = minecraftVersionOf(profile.value.versionId);
  const loader = loaderOf(profile.value.versionId);

  return versions.value.filter((version) => {
    const matchesGame = version.gameVersions.length === 0 || version.gameVersions.includes(gameVersion);
    // Resource packs, shaders and data packs are loader-independent.
    const matchesLoader =
      !loader || version.loaders.length === 0 || version.loaders.includes(loader);
    return matchesGame && matchesLoader;
  });
});

const canInstall = computed(
  () =>
    Boolean(props.project) &&
    Boolean(selectedVersionId.value) &&
    (isModpack.value || Boolean(targetProfile.value)) &&
    !isInstalling.value,
);

const reload = async () => {
  if (!props.project) return;
  isLoadingVersions.value = true;
  try {
    versions.value = await loadProjectVersions(props.project.provider, props.project.projectId);
  } finally {
    isLoadingVersions.value = false;
  }
};

watch(
  () => props.project,
  async (project) => {
    if (!project) return;
    targetProfile.value = selectedInstanceName.value || instances.value[0]?.name || "";
    selectedVersionId.value = "";
    await reload();
  },
  { immediate: true },
);

// Re-pick a sensible default whenever the compatible set changes.
watch(compatibleVersions, (list) => {
  if (!list.some((version) => version.versionId === selectedVersionId.value)) {
    selectedVersionId.value = list[0]?.versionId ?? "";
  }
});

const submit = async () => {
  const project = props.project;
  if (!project || !selectedVersionId.value) return;

  if (isModpack.value) {
    const created = await installModpack(project.provider, selectedVersionId.value, project.title);
    if (created) {
      await loadInstances();
      selectedInstanceName.value = created;
      emit("close");
    }
    return;
  }

  const outcome = await installIntoProfile(
    targetProfile.value,
    project.provider,
    selectedVersionId.value,
    project.kind,
    withDependencies.value,
  );

  if (outcome) {
    launcherMessage.value =
      outcome.skipped.length > 0
        ? t("install.successWithSkips", {
            name: project.title,
            profile: targetProfile.value,
            n: outcome.skipped.length,
          })
        : t("install.success", { name: project.title, profile: targetProfile.value });
    emit("close");
  }
};

const releaseColour = (releaseType: string) =>
  releaseType === "beta" ? "text-amber-300/80" : releaseType === "alpha" ? "text-rose-300/80" : "text-emerald-300/80";
</script>

<template>
  <div
    v-if="project"
    class="fixed inset-0 z-[80] flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm"
    @click.self="emit('close')"
  >
    <div class="flex max-h-[85vh] w-full max-w-[520px] flex-col rounded-2xl border border-white/10 bg-[var(--surface-modal)] shadow-2xl">
      <header class="flex items-start gap-3 border-b border-white/[0.07] p-5">
        <img
          v-if="project.iconUrl"
          :src="project.iconUrl"
          :alt="project.title"
          class="size-10 shrink-0 rounded-lg bg-white/5 object-cover"
        />
        <div class="min-w-0 flex-1">
          <h2 class="truncate text-[length:var(--text-lg)] font-bold text-white">
            {{ isModpack ? t("install.createProfile") : t("install.toProfile") }}
          </h2>
          <p class="truncate text-[length:var(--text-sm)] text-white/50">{{ project.title }}</p>
        </div>
        <button
          type="button"
          class="shrink-0 rounded-md p-1.5 text-white/40 transition-colors hover:bg-white/5 hover:text-white"
          :aria-label="t('common.close')"
          @click="emit('close')"
        >
          <Icon icon="lucide:x" class="size-[15px]" />
        </button>
      </header>

      <div class="flex flex-col gap-4 overflow-y-auto p-5">
        <!-- Target profile (not applicable to modpacks, which create their own) -->
        <div v-if="!isModpack">
          <label class="mb-1.5 block text-[length:var(--text-xs)] font-bold tracking-[0.1em] text-white/50 uppercase">
            {{ t("install.chooseProfile") }}
          </label>
          <p v-if="instances.length === 0" class="text-[length:var(--text-sm)] text-white/50">
            {{ t("install.noProfiles") }}
          </p>
          <select
            v-else
            v-model="targetProfile"
            class="w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-3 py-2 text-[length:var(--text-base)] text-white outline-none transition-colors focus:border-[var(--accent-border-focus)]"
          >
            <option v-for="instance in instances" :key="instance.name" :value="instance.name">
              {{ instance.name }} — {{ instance.versionId }}
            </option>
          </select>
        </div>

        <!-- Version -->
        <div>
          <label class="mb-1.5 block text-[length:var(--text-xs)] font-bold tracking-[0.1em] text-white/50 uppercase">
            {{ t("install.chooseVersion") }}
          </label>

          <div v-if="isLoadingVersions" class="flex items-center gap-2 text-[length:var(--text-sm)] text-white/50">
            <Icon icon="lucide:loader-2" class="size-[13px] animate-spin" />{{ t("common.loading") }}
          </div>

          <p
            v-else-if="compatibleVersions.length === 0"
            class="rounded-lg border border-white/10 bg-white/[0.03] px-3 py-2 text-[length:var(--text-sm)] text-white/50"
          >
            {{ t("install.noCompatibleVersion") }}
          </p>

          <div v-else class="flex max-h-[220px] flex-col gap-1.5 overflow-y-auto pr-1">
            <button
              v-for="version in compatibleVersions"
              :key="version.versionId"
              type="button"
              class="flex items-center gap-2 rounded-lg border px-3 py-2 text-left transition-all duration-150"
              :class="selectedVersionId === version.versionId
                ? 'border-[var(--accent-border-active)] bg-[var(--accent-bg-strong)]'
                : 'border-white/10 bg-white/[0.03] hover:bg-white/[0.06]'"
              :disabled="!version.downloadable"
              @click="selectedVersionId = version.versionId"
            >
              <div class="min-w-0 flex-1">
                <div class="truncate text-[length:var(--text-base)] font-semibold text-white">
                  {{ version.name || version.versionNumber }}
                </div>
                <div class="truncate text-[length:var(--text-2xs)] text-white/40">
                  {{ version.gameVersions.slice(0, 4).join(", ") }}
                  <template v-if="version.loaders.length"> · {{ version.loaders.join(", ") }}</template>
                  <template v-if="version.fileSize"> · {{ formatFileSize(version.fileSize) }}</template>
                </div>
              </div>
              <span
                class="shrink-0 text-[length:var(--text-2xs)] font-bold uppercase"
                :class="releaseColour(version.releaseType)"
              >
                {{ version.releaseType }}
              </span>
            </button>
          </div>
        </div>

        <!-- Dependencies -->
        <label
          v-if="!isModpack"
          class="flex cursor-pointer items-center gap-2.5 text-[length:var(--text-base)] text-white/70"
        >
          <input
            v-model="withDependencies"
            type="checkbox"
            class="size-4 rounded border-white/20 bg-transparent accent-[var(--primary)]"
          />
          {{ t("install.withDependencies") }}
        </label>
      </div>

      <footer class="flex justify-end gap-2 border-t border-white/[0.07] p-5">
        <button
          type="button"
          class="rounded-[7px] border border-white/10 bg-white/5 px-4 py-2 text-[length:var(--text-sm)] font-semibold text-white/60 transition-all hover:bg-white/10"
          @click="emit('close')"
        >
          {{ t("common.cancel") }}
        </button>
        <button
          type="button"
          class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-4 py-2 text-[length:var(--text-sm)] font-semibold text-[var(--primary)] transition-all hover:bg-[var(--accent-bg-hover)] disabled:cursor-not-allowed disabled:opacity-40"
          :disabled="!canInstall"
          @click="submit"
        >
          <Icon v-if="isInstalling" icon="lucide:loader-2" class="size-[13px] animate-spin" />
          <Icon v-else icon="lucide:download" class="size-[13px]" />
          {{ isInstalling ? t("common.installing") : t("common.install") }}
        </button>
      </footer>
    </div>
  </div>
</template>
