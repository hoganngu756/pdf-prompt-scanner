const STORAGE_KEY = 'pdf_promptscanner_admin_key';

/**
 * The admin key gates rule management and reading scan history. It lives in
 * localStorage so it is shared across tabs of the app within a browser.
 */
export const getAdminKey = (): string => localStorage.getItem(STORAGE_KEY) || '';

export const setAdminKey = (value: string): void => {
  localStorage.setItem(STORAGE_KEY, value);
};

/** Returns headers including the admin key, when one has been entered. */
export const withAdminKey = (headers: Record<string, string> = {}): Record<string, string> => {
  const key = getAdminKey().trim();
  return key ? { ...headers, 'X-Admin-Api-Key': key } : headers;
};
