import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { apiClient } from "@/lib/api/client";
import { parseSession, SESSION_COOKIE } from "@/lib/session";

export const dynamic = "force-dynamic";

export async function POST(req: NextRequest) {
  const session = parseSession(req.cookies.get(SESSION_COOKIE)?.value);
  let deleted = false;
  if (session) {
    try {
      await apiClient(session.accessToken).users.deleteCurrentUser();
      deleted = true;
    } catch {
      // empty
    }
  }

  const dest = deleted ? "/login?deleted=1" : "/settings?error=delete";
  const res = NextResponse.redirect(new URL(dest, req.url), { status: 303 });
  if (deleted) {
    res.cookies.set(SESSION_COOKIE, "", { path: "/", maxAge: 0, httpOnly: true });
  }
  return res;
}
