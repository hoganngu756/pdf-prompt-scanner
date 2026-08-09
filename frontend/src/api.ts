import { API_BASE_URL } from './config';
import { withAdminKey } from './adminKey';
import { HeuristicRule, ScanRecord, ScanResponse } from './types';

/**
 * The single place the frontend talks to the backend.
 *
 * Call sites previously each did their own fetch, header assembly, JSON parsing
 * and error handling — two different implementations had drifted apart, and the
 * admin key was attached by hand per request. Centralising it means an endpoint
 * gains auth, error shape and content-type handling by construction.
 */

/** An error carrying the HTTP status so callers can branch on 401/503 without re-parsing. */
export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }

  /** The server has no admin key configured, or the one supplied was wrong. */
  get isAuthProblem(): boolean {
    return this.status === 401 || this.status === 503;
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
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}

const jsonHeaders = () => withAdminKey({ 'Content-Type': 'application/json' });

export interface RulePayload {
  phrase: string;
  isRegex: boolean;
  active: boolean;
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

  history(): Promise<ScanRecord[]> {
    return request<ScanRecord[]>('/history', { headers: withAdminKey() }, 'Failed to fetch scan history');
  },

  listRules(): Promise<HeuristicRule[]> {
    return request<HeuristicRule[]>('/rules', {}, 'Failed to load rules');
  },

  createRule(payload: RulePayload): Promise<HeuristicRule> {
    return request<HeuristicRule>(
      '/rules',
      { method: 'POST', headers: jsonHeaders(), body: JSON.stringify(payload) },
      'Failed to create rule',
    );
  },

  updateRule(id: number, payload: RulePayload): Promise<HeuristicRule> {
    return request<HeuristicRule>(
      `/rules/${id}`,
      { method: 'PUT', headers: jsonHeaders(), body: JSON.stringify(payload) },
      'Failed to update rule',
    );
  },

  async deleteRule(id: number): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/rules/${id}`, {
      method: 'DELETE',
      headers: withAdminKey(),
    });
    if (!response.ok) {
      throw await errorFrom(response, 'Failed to delete rule');
    }
  },
};
