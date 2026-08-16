import { computed, ref } from "vue";
import { APP_API_BASE, apiGet, apiSend, errorMessage } from "./api";
import { error, launcherMessage } from "./state";
import { t } from "@/i18n";
import type { SavedSkin } from "./types";

/** The local skin library and applying one of its entries to the signed-in account. */

const skins = ref<SavedSkin[]>([]);
const selectedSkinId = ref<string | null>(null);
const isLoadingSkins = ref(false);
const isSavingSkin = ref(false);
/** Id of the skin currently being uploaded to Mojang, or null. */
const applyingSkinId = ref<string | null>(null);

const showSaveSkinModal = ref(false);
const showEditSkinModal = ref(false);
const showDeleteSkinModal = ref(false);
const editTargetSkin = ref<SavedSkin | null>(null);
const editSkinNewName = ref("");
const deleteTargetSkin = ref<SavedSkin | null>(null);

const selectedSkin = computed(
  () => skins.value.find((skin) => skin.id === selectedSkinId.value) ?? null,
);

/**
 * The backend serves stored images as immutable, so no cache-busting is needed: a replaced skin
 * always gets a new id.
 */
const skinImageUrl = (id: string) => `${APP_API_BASE}/api/skins/${encodeURIComponent(id)}/image`;

const loadSkins = async () => {
  try {
    isLoadingSkins.value = true;
    const response = await apiGet<{
      success: boolean;
      skins: SavedSkin[];
      selectedSkinId: string | null;
    }>("/api/skins");
    skins.value = response.skins;
    selectedSkinId.value = response.selectedSkinId;
  } catch (e) {
    error.value = errorMessage(e, t("skins.loadFailed"));
  } finally {
    isLoadingSkins.value = false;
  }
};

const handleSaveSkin = async (name: string, slim: boolean, imageBase64: string): Promise<boolean> => {
  if (!name.trim()) {
    error.value = t("skins.nameRequired");
    return false;
  }
  try {
    isSavingSkin.value = true;
    const response = await apiSend<{ success: boolean; skin: SavedSkin }>("POST", "/api/skins", {
      name: name.trim(),
      slim,
      imageBase64,
    });
    launcherMessage.value = t("skins.saved", { name: response.skin.name });
    showSaveSkinModal.value = false;
    await loadSkins();
    return true;
  } catch (e) {
    // Carries the backend's specific reason, e.g. the required 64x64 dimensions.
    error.value = errorMessage(e, t("skins.saveFailed"));
    return false;
  } finally {
    isSavingSkin.value = false;
  }
};

const handleRenameSkin = async (id: string, newName: string) => {
  if (!newName.trim()) {
    error.value = t("skins.nameRequired");
    return;
  }
  try {
    const response = await apiSend<{ success: boolean; skin: SavedSkin }>(
      "PATCH",
      `/api/skins/${encodeURIComponent(id)}`,
      { name: newName.trim() },
    );
    launcherMessage.value = t("skins.renamed", { name: response.skin.name });
    showEditSkinModal.value = false;
    editTargetSkin.value = null;
    editSkinNewName.value = "";
    await loadSkins();
  } catch (e) {
    error.value = errorMessage(e, t("skins.renameFailed"));
  }
};

/** Uploads the skin to Mojang; the account it applies to is whichever one is active. */
const handleSelectSkin = async (id: string) => {
  try {
    applyingSkinId.value = id;
    const response = await apiSend<{ success: boolean; skin: SavedSkin }>(
      "POST",
      `/api/skins/${encodeURIComponent(id)}/select`,
    );
    selectedSkinId.value = response.skin.id;
    launcherMessage.value = t("skins.applied", { name: response.skin.name });
  } catch (e) {
    error.value = errorMessage(e, t("skins.applyFailed"));
  } finally {
    applyingSkinId.value = null;
  }
};

const handleDeleteSkin = async (id: string) => {
  try {
    await apiSend("DELETE", `/api/skins/${encodeURIComponent(id)}`);
    launcherMessage.value = t("skins.deleted");
    if (selectedSkinId.value === id) selectedSkinId.value = null;
    showDeleteSkinModal.value = false;
    deleteTargetSkin.value = null;
    await loadSkins();
  } catch (e) {
    error.value = errorMessage(e, t("skins.deleteFailed"));
  }
};

export function useSkins() {
  return {
    skins,
    selectedSkinId,
    selectedSkin,
    isLoadingSkins,
    isSavingSkin,
    applyingSkinId,
    showSaveSkinModal,
    showEditSkinModal,
    showDeleteSkinModal,
    editTargetSkin,
    editSkinNewName,
    deleteTargetSkin,
    skinImageUrl,
    loadSkins,
    handleSaveSkin,
    handleRenameSkin,
    handleDeleteSkin,
    handleSelectSkin,
  };
}
