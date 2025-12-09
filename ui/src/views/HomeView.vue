<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from "vue";

interface AuthData {
  uuid: string;
  username: string;
}

const authData = ref<AuthData | null>(null);
const isAuthenticating = ref(false);
const error = ref<string | null>(null);
const authUrl = ref<string>("");
const state = ref<string>("");

const messageHandler = (event: MessageEvent) => {
  if (event.data?.type === "auth_success") {
    authData.value = { uuid: event.data.uuid, username: event.data.username };
    localStorage.setItem("minecraft_auth", JSON.stringify(authData.value));
    isAuthenticating.value = false;
    error.value = null;
  } else if (event.data?.type === "auth_error") {
    authData.value = null;
    isAuthenticating.value = false;
    error.value =
      event.data.description || event.data.error || "Authentication failed";
  }
};

const handleLogin = async () => {
  try {
    isAuthenticating.value = true;
    error.value = null;
    const response = await fetch("/api/auth/login");
    const data = await response.json();

    if (!data.success) {
      error.value = data.error || "Authentication failed";
      isAuthenticating.value = false;
      return;
    }

    authUrl.value = data.url;
    state.value = data.state;

    window.open(authUrl.value, "Microsoft Login", "width=500,height=700");
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Unknown error";
    isAuthenticating.value = false;
  }
};

const handleLogout = () => {
  authData.value = null;
  localStorage.removeItem("minecraft_auth");
  error.value = null;
};

// Check for existing auth on mount
onMounted(() => {
  const stored = localStorage.getItem("minecraft_auth");
  if (stored) {
    try {
      authData.value = JSON.parse(stored);
    } catch {
      localStorage.removeItem("minecraft_auth");
    }
  }

  window.addEventListener("message", messageHandler);
});

onBeforeUnmount(() => window.removeEventListener("message", messageHandler));
</script>

<template>
  <div class="w-screen h-screen flex">
    <div class="w-5/7 flex flex-col">
      <div class="relative h-4/9 bg-amber-900">
        <img
          class="brightness-75 max-h-full w-full object-cover overflow-clip"
          src="https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/key-art/SoulSteel_.NetHomepage_1920x1080.jpg"
          alt=""
        />
        <button
          v-if="authData"
          class="absolute z-20 left-1/2 top-1/2 w-60 h-20 bg-blue-600 shadow-blue-600 shadow-2xl rounded-lg -translate-x-1/2 -translate-y-1/2 cursor-pointer transition-all duration-300 ease-in-out hover:bg-blue-500 hover:shadow-bg-blue-500 hover:scale-105"
        >
          <p class="text-2xl font-semibold">Play</p>
          <p id="selected-version" class="text-sm">1.21.1</p>
        </button>
        <button
          v-else
          @click="handleLogin"
          :disabled="isAuthenticating"
          class="absolute z-20 left-1/2 top-1/2 w-60 h-20 bg-green-600 shadow-green-600 shadow-2xl rounded-lg -translate-x-1/2 -translate-y-1/2 cursor-pointer transition-all duration-300 ease-in-out hover:bg-green-500 hover:shadow-bg-green-500 hover:scale-105 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <p class="text-2xl font-semibold">
            {{
              isAuthenticating ? "Authenticating..." : "Login with Microsoft"
            }}
          </p>
        </button>
      </div>
      <div class="h-5/9 bg-amber-500 p-4">
        <div v-if="error" class="p-4 bg-red-500 text-white rounded mb-4">
          Error: {{ error }}
        </div>

        <div v-if="isAuthenticating" class="bg-white p-6 rounded-lg shadow-lg">
          <h3 class="text-xl font-bold mb-4">Authenticating...</h3>
          <p class="text-sm mb-2">We opened the Microsoft login in a popup.</p>
          <p class="text-sm mb-2">
            Complete the login there; this window updates automatically.
          </p>
          <p class="text-xs text-gray-600 break-all">
            Falls nötig, Link manuell: {{ authUrl }}
          </p>
        </div>
      </div>
    </div>
    <div class="w-2/7 bg-amber-700 p-4">
      <div v-if="authData" class="text-white">
        <h2 class="text-xl font-bold mb-4">Player Info</h2>
        <div class="mb-2">
          <p class="text-sm opacity-70">Username</p>
          <p class="font-semibold">{{ authData.username }}</p>
        </div>
        <div class="mb-4">
          <p class="text-sm opacity-70">UUID</p>
          <p class="font-mono text-xs break-all">{{ authData.uuid }}</p>
        </div>
        <button
          @click="handleLogout"
          class="w-full px-4 py-2 bg-red-600 rounded hover:bg-red-500 transition-colors"
        >
          Logout
        </button>
      </div>
      <div v-else class="text-white">
        <h2 class="text-xl font-bold mb-4">Not Logged In</h2>
        <p class="text-sm opacity-70">Please login with Microsoft to play</p>
      </div>
    </div>
  </div>
</template>
