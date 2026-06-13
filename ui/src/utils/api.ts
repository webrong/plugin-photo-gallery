function getXsrfToken(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

export class ApiError extends Error {
  status: number;
  detail?: unknown;
  constructor(message: string, status: number, detail?: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.detail = detail;
  }
}

interface ApiRequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
}

function buildUrl(path: string, query?: ApiRequestOptions["query"]): string {
  if (!query) return path;
  const params = new URLSearchParams();
  Object.entries(query).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== "") {
      params.append(k, String(v));
    }
  });
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}

export async function apiRequest<T = unknown>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const { body, query, headers, ...rest } = options;
  const xsrf = getXsrfToken();

  const finalHeaders: Record<string, string> = {
    "X-Requested-With": "XMLHttpRequest",
    ...(headers as Record<string, string> | undefined),
  };
  if (xsrf) {
    finalHeaders["X-XSRF-TOKEN"] = xsrf;
  }
  let payload: BodyInit | undefined;
  if (body !== undefined && body !== null) {
    if (typeof body === "string" || body instanceof FormData) {
      payload = body;
    } else {
      finalHeaders["Content-Type"] = finalHeaders["Content-Type"] || "application/json";
      payload = JSON.stringify(body);
    }
  }

  const res = await fetch(buildUrl(path, query), {
    ...rest,
    headers: finalHeaders,
    body: payload,
    credentials: "same-origin",
  });

  if (!res.ok) {
    let detail: unknown;
    let message = `请求失败 (${res.status})`;
    try {
      const ct = res.headers.get("content-type") || "";
      if (ct.includes("application/json")) {
        detail = await res.json();
        const d = detail as { message?: string; title?: string };
        message = d.message || d.title || message;
      } else {
        const text = await res.text();
        if (text) {
          detail = text;
          message = text.length > 200 ? text.slice(0, 200) + "..." : text;
        }
      }
    } catch {
      // ignore parsing errors
    }
    throw new ApiError(message, res.status, detail);
  }

  if (res.status === 204) {
    return undefined as T;
  }
  const ct = res.headers.get("content-type") || "";
  if (ct.includes("application/json")) {
    return (await res.json()) as T;
  }
  return (await res.text()) as unknown as T;
}
