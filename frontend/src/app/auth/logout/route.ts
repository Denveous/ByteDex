import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { apiClient } from "@/lib/api/client";
import { parseSession, SESSION_COOKIE } from "@/lib/session";

export const dynamic = "force-dynamic";

export async function POST(req: NextRequest) {
  const session = parseSession(req.cookies.get(SESSION_COOKIE)?.value);
  if (session) {
    try {
      await apiClient(session.accessToken).auth.logout();
    } catch {
      // empty
    }
  }

  const res = NextResponse.redirect(new URL("/login", req.url), { status: 303 });
  res.cookies.set(SESSION_COOKIE, "", { path: "/", maxAge: 0, httpOnly: true });
  return res;
}
