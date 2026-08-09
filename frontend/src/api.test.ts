import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { api, ApiError } from './api';

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });

describe('api client', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('surfaces the server error message rather than a generic one', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({ error: 'File is too large. The maximum upload size is 10MB.' }, 413),
    ));

    await expect(api.listRules()).rejects.toThrowError(
      'File is too large. The maximum upload size is 10MB.',
    );
  });

  it('classifies 401 and 503 as auth problems so callers can prompt for a key', async () => {
    for (const status of [401, 503]) {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ error: 'nope' }, status)));
      const err = await api.history().catch((e) => e);
      expect(err).toBeInstanceOf(ApiError);
      expect(err.status).toBe(status);
      expect(err.isAuthProblem).toBe(true);
    }
  });

  it('does not treat other failures as auth problems', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ error: 'boom' }, 500)));
    const err = await api.history().catch((e) => e);
    expect(err.isAuthProblem).toBe(false);
  });

  it('falls back to a readable message when the body is not JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response('<html>502 Bad Gateway</html>', {
        status: 502,
        headers: { 'content-type': 'text/html' },
      }),
    ));
    const err = await api.listRules().catch((e) => e);
    expect(err.message).toContain('502 Bad Gateway');
  });

  it('reports an unreachable backend distinctly from an HTTP error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    const err = await api.listRules().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(0);
    expect(err.message).toContain('Could not reach');
  });

  it('attaches the admin key when one is stored, and omits it when not', async () => {
    // A Response body can only be read once, so each call needs a fresh one
    const spy = vi.fn().mockImplementation(async () => jsonResponse([]));
    vi.stubGlobal('fetch', spy);

    await api.history();
    expect((spy.mock.calls[0][1] as RequestInit).headers).not.toHaveProperty('X-Admin-Api-Key');

    localStorage.setItem('pdf_promptscanner_admin_key', 'secret-value');
    await api.history();
    expect((spy.mock.calls[1][1] as RequestInit).headers)
      .toHaveProperty('X-Admin-Api-Key', 'secret-value');
  });

  it('sends scan uploads as multipart without forcing a Content-Type', async () => {
    const spy = vi.fn().mockResolvedValue(jsonResponse({}));
    vi.stubGlobal('fetch', spy);

    const file = new File(['%PDF-1.4'], 'doc.pdf', { type: 'application/pdf' });
    await api.scan(file, true, false);

    const init = spy.mock.calls[0][1] as RequestInit;
    expect(init.body).toBeInstanceOf(FormData);
    // The browser must set the multipart boundary itself
    expect(init.headers).toBeUndefined();
    expect((init.body as FormData).get('useLLM')).toBe('true');
    expect((init.body as FormData).get('useHeuristics')).toBe('false');
  });
});
