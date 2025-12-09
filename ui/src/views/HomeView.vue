<script setup lang="ts">

</script>

<template>
  <div class="w-screen h-screen flex">
    <div class="w-5/7 flex flex-col">
      <div class="relative h-4/9 bg-amber-900">
        <img class="brightness-75 max-h-full w-full object-cover overflow-clip"
             src="https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/key-art/SoulSteel_.NetHomepage_1920x1080.jpg"
             alt=""/>
        <button class="absolute z-20 left-1/2 top-1/2 w-60 h-20 bg-blue-600 shadow-blue-600 shadow-2xl rounded-lg -translate-x-1/2 -translate-y-1/2 cursor-pointer transition-all duration-300 ease-in-out hover:bg-blue-500 hover:shadow-bg-blue-500 hover:scale-105">
          <p class="text-2xl font-semibold">Play</p>
          <p id="selected-version" class="text-sm">1.21.1</p>
        </button>
      </div>
      <div class="h-5/9 bg-amber-500"></div>
    </div>
    <div class="w-2/7 bg-amber-700"></div>
  </div>
</template>
