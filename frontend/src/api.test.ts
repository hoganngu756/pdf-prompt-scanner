import { describe, it, expect, vi, afterEach } from 'vitest';
import { api, ApiError } from './api';

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });

describe('api client', () => {
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

  it('preserves the HTTP status on the error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ error: 'boom' }, 500)));
    const err = await api.listRules().catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(500);
  });

  it('falls back to a readable message when the body is not JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response('<html>502 Bad Gateway</html>', {
        status: 502,
        headers: { 'content-type': 'text/html' },
      }),
    ));
    const err = await api.listRules().catch((e: unknown) => e);
    expect((err as ApiError).message).toContain('502 Bad Gateway');
  });

  it('reports an unreachable backend distinctly from an HTTP error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    const err = await api.listRules().catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(0);
    expect((err as ApiError).message).toContain('Could not reach');
  });

  it('never attaches credentials — the API is public and read-only', async () => {
    const spy = vi.fn().mockImplementation(async () => jsonResponse([]));
    vi.stubGlobal('fetch', spy);

    await api.listRules();

    const init = spy.mock.calls[0][1] as RequestInit;
    const headers = (init?.headers ?? {}) as Record<string, string>;
    expect(Object.keys(headers)).not.toContain('X-Admin-Api-Key');
  });

  it('sends scan uploads as multipart without forcing a Content-Type', async () => {
    const spy = vi.fn().mockImplementation(async () => jsonResponse({}));
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
