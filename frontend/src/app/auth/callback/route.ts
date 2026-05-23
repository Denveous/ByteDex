import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { apiClient } from "@/lib/api/client";
import { encodeSession, SESSION_COOKIE } from "@/lib/session";

export const dynamic = "force-dynamic";

function appBase(req: NextRequest): string {
  return (process.env.APP_URL ?? new URL(req.url).origin).replace(/\/$/, "");
}

export async function GET(req: NextRequest) {
  const base = appBase(req);
  const code = req.nextUrl.searchParams.get("code");
  if (!code) {
    return NextResponse.redirect(`${base}/login?error=oauth`);
  }

  let accessToken: string;
  let refreshToken: string;
  let expiresIn: number;
  try {
    const { data } = await apiClient().auth.exchangeAuthCode({
      exchangeAuthCodeRequest: { code },
    });
    ({ accessToken, refreshToken, expiresIn } = data);
  } catch {
    return NextResponse.redirect(`${base}/login?error=oauth`);
  }

  const res = NextResponse.redirect(`${base}/dashboard`);
  res.cookies.set(SESSION_COOKIE, encodeSession({
    accessToken,
    refreshToken,
    expiresAt: Date.now() + expiresIn * 1000,
  }), {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: 60 * 60 * 24 * 30,
  });
  return res;
}
