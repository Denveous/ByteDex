import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { apiClient } from "@/lib/api/client";
import { parseSession, SESSION_COOKIE } from "@/lib/session";

const isPublic = (path: string) =>
  path === "/login" || path.startsWith("/auth/");

async function verifyAccessToken(accessToken: string): Promise<boolean> {
  try {
    await apiClient(accessToken).users.getCurrentUser();
    return true;
  } catch {
    return false;
  }
}

export async function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl;
  const session = parseSession(req.cookies.get(SESSION_COOKIE)?.value);

  const structurallyValid =
    session !== null && Date.now() < session.expiresAt;
  const valid = structurallyValid
    ? await verifyAccessToken(session.accessToken)
    : false;

  if (valid) {
    if (pathname === "/login") {
      return NextResponse.redirect(new URL("/dashboard", req.url));
    }
    return NextResponse.next();
  }

  if (isPublic(pathname)) return NextResponse.next();

  const res = NextResponse.redirect(new URL("/login", req.url));
  if (session !== null) res.cookies.delete(SESSION_COOKIE);
  return res;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\..*).*)"],
};
