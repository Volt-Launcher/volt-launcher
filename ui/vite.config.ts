import { fileURLToPath, URL } from "node:url";
import { version } from './package.json';

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
  define: {
    'import.meta.env.VITE_APP_VERSION': JSON.stringify(version),
  },
  server: {
    port: 3020,
  },
  build: {
    outDir: "../src/main/resources/dist",
    emptyOutDir: true,
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            { name: "three", test: /node_modules[\\/]three/, priority: 30 },
            { name: "skinview3d", test: /node_modules[\\/]skinview3d/, priority: 25 },
            { name: "markdown", test: /node_modules[\\/](marked|dompurify)/, priority: 20 },
            { name: "vendor", test: /node_modules/, priority: 10 },
          ],
        },
      },
    },
  },
});
