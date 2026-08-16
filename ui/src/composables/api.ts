export const APP_API_BASE = "http://localhost:45938";

/** Every launcher endpoint answers with this envelope. */
export interface ApiEnvelope {
  success: boolean;
  error?: string;
}

export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

const toUrl = (path: string) => (path.startsWith("/") ? `${APP_API_BASE}${path}` : path);

/**
 * Calls the launcher API and returns the payload.
 *
 * Unlike a bare `fetch`, this rejects on transport failures, non-JSON responses and
 * `success: false` bodies alike, so callers only need one `catch` instead of checking a flag
 * on every result.
 */
export async function apiFetch<T extends ApiEnvelope>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(toUrl(path), init);
  } catch (cause) {
    throw new ApiError(
      cause instanceof Error ? cause.message : "The launcher backend is not reachable",
      0,
    );
  }

  let body: T;
  try {
    body = (await response.json()) as T;
  } catch {
    throw new ApiError(`The launcher returned an unreadable response (HTTP ${response.status})`, response.status);
  }

  if (!body.success) {
    throw new ApiError(body.error ?? `Request failed with HTTP ${response.status}`, response.status);
  }
  return body;
}

export const apiGet = <T extends ApiEnvelope>(path: string) => apiFetch<T>(path);

export const apiSend = <T extends ApiEnvelope>(
  method: "POST" | "PATCH" | "PUT" | "DELETE",
  path: string,
  body?: unknown,
) =>
  apiFetch<T>(path, {
    method,
    ...(body === undefined
      ? {}
      : { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }),
  });

/** Builds a query string, dropping empty values so the backend sees only real filters. */
export const buildQuery = (params: Record<string, string | number | boolean | null | undefined>): string => {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === "") continue;
    search.set(key, String(value));
  }
  const encoded = search.toString();
  return encoded ? `?${encoded}` : "";
};

export const errorMessage = (error: unknown, fallback: string): string =>
  error instanceof Error && error.message ? error.message : fallback;
