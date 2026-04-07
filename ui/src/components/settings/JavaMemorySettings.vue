<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";
import type { JavaRuntime } from "@/composables/useLauncher";

const { javaRuntimes, jvmArgs, minMemory, maxMemory } = useLauncher();

const addRuntime = () => {
  const existing = javaRuntimes.value.map((r) => r.version);
  const next = [8, 11, 16, 17, 19, 20, 21, 22].find((v) => !existing.includes(v)) ?? (Math.max(...existing, 0) + 1);
  javaRuntimes.value.push({ version: next, path: "" });
};

const removeRuntime = (index: number) => {
  if (javaRuntimes.value.length > 1) javaRuntimes.value.splice(index, 1);
};

const updateVersion = (rt: JavaRuntime, raw: string) => {
  const n = parseInt(raw, 10);
  if (!Number.isNaN(n) && n > 0) rt.version = n;
};
</script>

<template>
  <div class="flex flex-col gap-3">
    <!-- Memory -->
    <div class="rounded-xl border border-white/10 bg-[var(--surface-panel)] p-[18px]">
      <div class="mb-1 flex items-center gap-[7px] text-[length:var(--text-xs)] font-bold tracking-[0.12em] text-white/60">
        <Icon icon="lucide:memory-stick" class="size-[13px]" />MEMORY ALLOCATION
      </div>
      <div class="mb-[14px] text-[length:var(--text-base)] leading-[1.55] text-white/60">
        Configure minimum and maximum RAM for Minecraft
      </div>
      <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <VoltSlider v-model="minMemory" :min="1" :max="16" :step="1" label="Minimum (GB)" />
        <VoltSlider v-model="maxMemory" :min="1" :max="32" :step="1" label="Maximum (GB)" />
      </div>
    </div>

    <!-- Java Runtimes -->
    <div class="rounded-xl border border-white/10 bg-[var(--surface-panel)] p-[18px]">
      <div class="mb-1 flex items-center justify-between">
        <div class="flex items-center gap-[7px] text-[length:var(--text-xs)] font-bold tracking-[0.12em] text-white/60">
          <Icon icon="lucide:coffee" class="size-[13px]" />JAVA RUNTIMES
        </div>
        <button
          type="button"
          class="inline-flex items-center gap-1 rounded-md border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-2 py-1 text-[length:var(--text-2xs)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)]"
          @click="addRuntime"
        >
          <Icon icon="lucide:plus" class="size-[10px]" />Add
        </button>
      </div>
      <div class="mb-[14px] text-[length:var(--text-base)] leading-[1.55] text-white/60">
        Assign a custom Java executable per major version, or leave blank for auto-detection
      </div>

      <div class="flex flex-col gap-2">
        <div
          v-for="(rt, i) in javaRuntimes"
          :key="i"
          class="flex items-center gap-2 rounded-[10px] border border-white/10 bg-[var(--surface-input-muted)] px-3 py-2.5"
        >
          <div class="flex shrink-0 flex-col items-center gap-0.5">
            <span class="text-[length:var(--text-2xs)] font-bold tracking-wider text-white/35">JAVA</span>
            <input
              type="number"
              min="1"
              :value="rt.version"
              class="w-[42px] rounded-md border border-white/10 bg-[var(--surface-input)] px-1.5 py-0.5 text-center font-mono text-[length:var(--text-sm)] text-white outline-none transition-colors duration-200 focus:border-[var(--accent-border-focus)] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none [-moz-appearance:textfield]"
              @change="updateVersion(rt, ($event.target as HTMLInputElement).value)"
            />
          </div>
          <input
            v-model="rt.path"
            type="text"
            placeholder="Auto-detect"
            class="min-w-0 flex-1 rounded-lg border border-white/10 bg-[var(--surface-input)] px-3 py-2 text-[length:var(--text-sm)] text-white placeholder-white/30 outline-none transition-colors duration-200 focus:border-[var(--accent-border-focus)]"
          />
          <button
            type="button"
            class="shrink-0 rounded-md p-1.5 text-white/25 transition-colors duration-200 hover:bg-white/5 hover:text-white/60 disabled:pointer-events-none disabled:opacity-30"
            :disabled="javaRuntimes.length <= 1"
            @click="removeRuntime(i)"
          >
            <Icon icon="lucide:x" class="size-[12px]" />
          </button>
        </div>
      </div>
    </div>

    <!-- JVM Arguments -->
    <div class="rounded-xl border border-white/10 bg-[var(--surface-panel)] p-[18px]">
      <div class="mb-1 flex items-center gap-[7px] text-[length:var(--text-xs)] font-bold tracking-[0.12em] text-white/60">
        <Icon icon="lucide:terminal" class="size-[13px]" />JVM ARGUMENTS
      </div>
      <div class="mb-[14px] text-[length:var(--text-base)] leading-[1.55] text-white/60">
        Custom flags passed to every Java launch
      </div>
      <input
        v-model="jvmArgs"
        type="text"
        class="w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-3 py-2 font-mono text-[length:var(--text-2xs)] text-white/70 outline-none transition-colors duration-200 focus:border-[var(--accent-border-focus)]"
      />
    </div>
  </div>
</template>
