export const versionEmoji = (t: string) =>
    ({ release: "📦", snapshot: "🔬", old_beta: "⚗️", old_alpha: "⚔️" }[t] ?? "🎮");

export const versionGradient = (t: string) =>
    ({
        release: "linear-gradient(135deg,#0d3a18,#184d22)",
        snapshot: "linear-gradient(135deg,#0a2040,#001535)",
        old_beta: "linear-gradient(135deg,#3a1a08,#5a2a10)",
        old_alpha: "linear-gradient(135deg,#4d0f0f,#7a1a1a)",
    }[t] ?? "linear-gradient(135deg,#0a1535,#122050)");

export const formatRelativeDate = (ts: number) => {
    if (!ts) return "Nie gespielt";
    const d = Date.now() - ts;
    const m = Math.floor(d / 6e4);
    const h = Math.floor(d / 36e5);
    const dy = Math.floor(d / 864e5);
    const w = Math.floor(dy / 7);
    const mo = Math.floor(dy / 30);
    if (m < 1) return "gerade eben";
    if (m < 60) return `vor ${m}min`;
    if (h < 24) return `vor ${h}h`;
    if (dy < 7) return `vor ${dy}T`;
    if (w < 5) return `vor ${w}W`;
    return `vor ${mo}M`;
};

export const formatVersionType = (t: string) =>
    ({ release: "Release", snapshot: "Snapshot", old_beta: "Beta", old_alpha: "Alpha" }[t] ?? t);

export const formatReleaseTime = (rt: string) => {
    if (!rt) return "Unbekannt";
    const d = new Date(rt);
    return Number.isNaN(d.getTime()) ? rt : d.toLocaleDateString("de-DE");
};

// Parses compound loader version IDs like "fabric:26.1.2:0.19.2" → "Fabric 0.19.2 - 26.1.2"
export const formatLoaderId = (id: string) => {
    const parts = id.split(":");
    if (parts.length !== 3) return id;
    const [platform, mcVersion, loaderVersion] = parts;
    const name = platform.charAt(0).toUpperCase() + platform.slice(1);
    return `${name} ${loaderVersion} - ${mcVersion}`;
};

export const handleImgError = (event: Event) => {
    const img = event.target as HTMLImageElement;
    const fb = img.dataset.fallbackSrc;
    if (fb && img.src !== fb) img.src = fb;
};
