<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import {
  FlyingAnimation,
  FunctionAnimation,
  IdleAnimation,
  RunningAnimation,
  SkinViewer,
  WalkingAnimation,
} from "skinview3d";

export type AnimationMode = "walk" | "idle" | "run" | "fly" | "none" | "chill";

const props = withDefaults(defineProps<{
  skin: string;
  width?: number;
  height?: number;
  slim?: boolean;
  animation?: AnimationMode;
  autoRotate?: boolean;
  interactive?: boolean;
}>(), {
  width: 240,
  height: 400,
  slim: false,
  animation: "walk",
  autoRotate: false,
  interactive: true,
});

const canvasRef = ref<HTMLCanvasElement | null>(null);
let viewer: SkinViewer | null = null;

function applyViewerLayout(mode: AnimationMode) {
  if (!viewer) return;

  viewer.zoom = 0.9;
  viewer.playerObject.position.x = 0;
  viewer.playerObject.position.y = 0;
  viewer.playerObject.position.z = 0;
  viewer.playerObject.rotation.x = 0;
  viewer.playerObject.rotation.y = 0;
  viewer.playerObject.rotation.z = 0;

  if (mode === "chill") {
    viewer.zoom = 0.78;
    viewer.playerObject.position.y = -4;
    viewer.playerObject.rotation.y = 0.46;
  }
}

function makeAnimation(mode: AnimationMode) {
  if (mode === "walk") {
    const a = new WalkingAnimation();
    a.speed = 0.5;
    a.headBobbing = false;
    return a;
  }
  if (mode === "idle") return new IdleAnimation();
  if (mode === "run") {
    const a = new RunningAnimation();
    a.speed = 0.8;
    return a;
  }
  if (mode === "fly") return new FlyingAnimation();
  if (mode === "chill") {
    // Seated pose matching the reference: torso leaned back, head looking
    // down, one leg extended, the other hanging, arms out to the sides.
    const a = new FunctionAnimation((player, progress) => {
      const breath = Math.sin(progress * 0.14);

      // Torso leaned back
      player.skin.body.rotation.x = -0.38 + breath * 0.01;
      player.skin.body.rotation.y = 0;
      player.skin.body.rotation.z = -0.04;

      // Head compensates for lean + looks slightly down toward viewer
      player.skin.head.rotation.x = 0.34 + breath * 0.012;
      player.skin.head.rotation.y = 0.14;
      player.skin.head.rotation.z = 0.06;

      // Right arm: out to the side and slightly back, bracing
      player.skin.rightArm.rotation.x = 0.22 + breath * 0.008;
      player.skin.rightArm.rotation.y = 0;
      player.skin.rightArm.rotation.z = -0.38;

      // Left arm: out to the other side, relaxed
      player.skin.leftArm.rotation.x = 0.18 + breath * 0.008;
      player.skin.leftArm.rotation.y = 0;
      player.skin.leftArm.rotation.z = 0.32;

      // Right leg: extended forward (the main seated-leg silhouette)
      player.skin.rightLeg.rotation.x = -0.3;
      player.skin.rightLeg.rotation.y = 0;
      player.skin.rightLeg.rotation.z = -0.18;

      // Left leg: hanging down more vertically
      player.skin.leftLeg.rotation.x = -0.2;
      player.skin.leftLeg.rotation.y = 0;
      player.skin.leftLeg.rotation.z = 0;
    });
    a.speed = 0.5;
    return a;
  }
  return null;
}

// Applies a natural "hero" static pose — used when animation = "none".
// Must be called AFTER setting viewer.animation = null (which resets joints).
function applyHeroPose() {
  if (!viewer) return;
  const { skin } = viewer.playerObject;
  skin.head.rotation.y = 0.28;
  skin.rightArm.rotation.z = -0.55;
  skin.rightArm.rotation.x = 0.3;
  skin.leftArm.rotation.z = 0.3;
  skin.leftArm.rotation.x = -0.1;
  skin.rightLeg.rotation.x = 0.12;
  skin.leftLeg.rotation.x = -0.18;
}

function applyAnimation(mode: AnimationMode) {
  if (!viewer) return;
  viewer.animation = null;
  applyViewerLayout(mode);
  viewer.animation = makeAnimation(mode);
  if (!viewer.animation) applyHeroPose();
}

onMounted(async () => {
  if (!canvasRef.value) return;

  viewer = new SkinViewer({
    canvas: canvasRef.value,
    width: props.width,
    height: props.height,
    enableControls: props.interactive,
    zoom: 0.9,
    fov: 65,
  });

  viewer.autoRotate = props.autoRotate;
  viewer.autoRotateSpeed = 0.35;

  if (props.interactive) {
    viewer.controls.enablePan = false;
    viewer.controls.minDistance = 10;
    viewer.controls.maxDistance = 80;
  }

  applyAnimation(props.animation);

  await viewer.loadSkin(props.skin, {
    model: props.slim ? "slim" : "auto-detect",
  });

  applyAnimation(props.animation);
});

watch(() => props.skin, async (url) => {
  if (!viewer) return;
  await viewer.loadSkin(url, { model: props.slim ? "slim" : "auto-detect" });
  applyAnimation(props.animation);
});

watch(() => props.slim, async (isSlim) => {
  if (!viewer || !props.skin) return;
  await viewer.loadSkin(props.skin, { model: isSlim ? "slim" : "auto-detect" });
  applyAnimation(props.animation);
});

watch(() => props.animation, (mode) => { applyAnimation(mode); });

watch(() => [props.width, props.height] as const, ([w, h]) => { viewer?.setSize(w, h); });

watch(() => props.autoRotate, (val) => { if (viewer) viewer.autoRotate = val; });

onBeforeUnmount(() => {
  viewer?.dispose();
  viewer = null;
});
</script>

<template>
  <canvas ref="canvasRef" class="block" />
</template>
