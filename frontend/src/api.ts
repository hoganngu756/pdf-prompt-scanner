import { API_BASE_URL } from './config';
import { HeuristicRule, ScanResponse } from './types';

/**
 * The single place the frontend talks to the backend.
 *
 * The API is now entirely public and read-only apart from submitting a scan, so
 * there are no credentials to attach and no auth failures to branch on.
 */

/** An error carrying the HTTP status so callers can report it precisely. */
export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/**
 * Reads the server's error message when there is one. The backend answers with
 * `{"error": "..."}`, but a proxy or a crash can return HTML or nothing at all,
 * so the body is treated as untrusted.
 */
async function errorFrom(response: Response, fallback: string): Promise<ApiError> {
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) {
    try {
      const body = await response.json();
      if (body && typeof body.error === 'string' && body.error) {
        return new ApiError(body.error, response.status);
      }
    } catch {
      // fall through to the generic message
    }
  } else {
    try {
      const text = await response.text();
      if (text.trim()) return new ApiError(text.trim().slice(0, 300), response.status);
    } catch {
      // fall through
    }
  }
  return new ApiError(`${fallback} (HTTP ${response.status})`, response.status);
}

async function request<T>(path: string, init: RequestInit, fallback: string): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, init);
  } catch {
    // Network-level failure: no response at all, so status 0.
    throw new ApiError('Could not reach the scanner backend. Is it running?', 0);
  }
  if (!response.ok) {
    throw await errorFrom(response, fallback);
  }
  return (await response.json()) as T;
}

export const api = {
  scan(file: File, useLLM: boolean, useHeuristics: boolean): Promise<ScanResponse> {
    const form = new FormData();
    form.append('file', file);
    form.append('useLLM', String(useLLM));
    form.append('useHeuristics', String(useHeuristics));
    // No Content-Type header: the browser must set the multipart boundary itself.
    return request<ScanResponse>('/scan', { method: 'POST', body: form }, 'Failed to scan document');
  },

  listRules(): Promise<HeuristicRule[]> {
    return request<HeuristicRule[]>('/rules', {}, 'Failed to load rules');
  },
};
