import { cookies } from "next/headers";
import type { User } from "@/openapi";
import { apiClient, type ApiClient } from "@/lib/api/client";
import { parseSession, SESSION_COOKIE } from "./session";

export { SESSION_COOKIE, encodeSession } from "./session";
export type { Session } from "./session";

function appUrl(): string {
  return (process.env.APP_URL ?? "http://localhost:3000").replace(/\/$/, "");
}

function publicApiBaseUrl(): string {
  return (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
}

export function githubLoginUrl(): string {
  const redirectUri = `${appUrl()}/auth/callback`;
  return `${publicApiBaseUrl()}/auth/github/login?redirect_uri=${encodeURIComponent(redirectUri)}`;
}

async function fetchMe(accessToken: string): Promise<User | null> {
  try {
    const { data } = await apiClient(accessToken).users.getCurrentUser();
    return data;
  } catch {
    return null;
  }
}

export async function getAuthedUser(): Promise<User | null> {
  const session = parseSession((await cookies()).get(SESSION_COOKIE)?.value);
  if (!session) return null;
  if (Date.now() >= session.expiresAt) return null;
  return fetchMe(session.accessToken);
}

export async function getAccessToken(): Promise<string | null> {
  const session = parseSession((await cookies()).get(SESSION_COOKIE)?.value);
  if (!session || Date.now() >= session.expiresAt) return null;
  return session.accessToken;
}

export async function authedApi(): Promise<ApiClient | null> {
  const token = await getAccessToken();
  return token ? apiClient(token) : null;
}
