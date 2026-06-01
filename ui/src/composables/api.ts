export const APP_API_BASE = "http://localhost:7070";

export const apiFetch = async <T>(url: string, init?: RequestInit): Promise<T> => {
    const fullUrl = url.startsWith("/") ? `${APP_API_BASE}${url}` : url;
    const r = await fetch(fullUrl, init);
    return r.json() as Promise<T>;
};
