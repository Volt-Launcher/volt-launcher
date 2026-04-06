import { fileURLToPath, URL } from "node:url";

import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import vueJsx from "@vitejs/plugin-vue-jsx";
import VueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [vue(), vueJsx(), VueDevTools(), tailwindcss()],
  base: "./",
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    port: 3020,
  },
  build: {
    outDir: "../src/main/resources/dist",
    emptyOutDir: true,
  },
});
