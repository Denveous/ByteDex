export const SESSION_COOKIE = "bdx_session";

export interface Session {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}

export function encodeSession(s: Session): string {
  return JSON.stringify(s);
}

export function parseSession(raw: string | undefined): Session | null {
  if (!raw) return null;
  try {
    const s = JSON.parse(raw) as Partial<Session>;
    if (typeof s.accessToken !== "string" || typeof s.expiresAt !== "number") {
      return null;
    }
    return s as Session;
  } catch {
    return null;
  }
}
