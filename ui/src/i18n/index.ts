import { computed, ref } from "vue";
import { en, type Messages } from "./en";
import { de } from "./de";

export type LocaleId = "en" | "de";

export const SUPPORTED_LOCALES: Array<{ id: LocaleId; label: string }> = [
  { id: "en", label: "English" },
  { id: "de", label: "Deutsch" },
];

const CATALOGUES: Record<LocaleId, Messages> = { en, de };

const locale = ref<LocaleId>("en");

export const setLocale = (next: string) => {
  locale.value = next === "de" ? "de" : "en";
  document.documentElement.setAttribute("lang", locale.value);
};

export const currentLocale = computed(() => locale.value);

/** Walks a dotted key like `settings.sections.java` through the active catalogue. */
const lookup = (key: string): string => {
  const fromLocale = resolve(CATALOGUES[locale.value], key);
  if (typeof fromLocale === "string") return fromLocale;

  // Fall back to English so a missing translation degrades to readable text
  // rather than showing the raw key.
  const fromFallback = resolve(CATALOGUES.en, key);
  return typeof fromFallback === "string" ? fromFallback : key;
};

const resolve = (catalogue: unknown, key: string): unknown =>
  key.split(".").reduce<unknown>((node, part) => {
    if (node && typeof node === "object" && part in node) {
      return (node as Record<string, unknown>)[part];
    }
    return undefined;
  }, catalogue);

/** Replaces `{placeholder}` tokens with the supplied values. */
const interpolate = (template: string, values?: Record<string, string | number>): string => {
  if (!values) return template;
  return template.replace(/\{(\w+)}/g, (match, name: string) =>
    name in values ? String(values[name]) : match,
  );
};

/**
 * Translates a key. Reactive: components re-render when the language changes because
 * `locale` is a ref read inside this function.
 */
export const t = (key: string, values?: Record<string, string | number>): string =>
  interpolate(lookup(key), values);

export function useI18n() {
  return { t, locale: currentLocale, setLocale, locales: SUPPORTED_LOCALES };
}
