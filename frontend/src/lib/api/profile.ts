import type { PublicUser, SessionPage, SessionStatus, User } from "@/openapi";
import { authedApi } from "@/lib/auth";
import type {
  ProfileStat,
  ProfileSubmissionView,
  ProfileTab,
  ProfileView,
} from "./types";

const PAGE = 20;

const shortId = (id: string) => id.replace(/-/g, "").slice(0, 4) || "-";

function whenLabel(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "-";
  if (d.toDateString() === new Date().toDateString()) {
    return `today · ${d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
  }
  return d.toLocaleDateString([], { month: "short", day: "numeric" });
}

const ANON: PublicUser = {
  id: "",
  githubLogin: "-",
  createdAt: "",
  submissionCount: 0,
};

export async function getProfile(): Promise<ProfileView> {
  const empty: ProfileView = { user: ANON, stats: [], tabs: [], submissions: [] };
  const api = await authedApi();
  if (!api) return empty;

  let me: User;
  try {
    me = (await api.users.getCurrentUser()).data;
  } catch {
    return empty;
  }

  const sessions = async (
    pageSize: number,
    status?: SessionStatus,
  ): Promise<SessionPage | null> => {
    try {
      return (
        await api.sessions.listSessions({ submittedBy: me.id, status, pageSize })
      ).data;
    } catch {
      return null;
    }
  };

  const [pub, allS, openS, subs] = await Promise.all([
    api.users
      .getPublicUser({ userId: me.id })
      .then((r) => r.data)
      .catch(() => null),
    sessions(1),
    sessions(1, "open"),
    sessions(PAGE),
  ]);

  const user: PublicUser = pub ?? {
    id: me.id,
    githubLogin: me.githubLogin,
    displayName: me.displayName,
    avatarUrl: me.avatarUrl,
    createdAt: me.createdAt,
    submissionCount: 0,
  };

  const sessionsTotal = allS?.pagination.total ?? user.sessionCount ?? 0;
  const openTotal = openS?.pagination.total ?? 0;
  const closedTotal = Math.max(0, sessionsTotal - openTotal);

  const stats: ProfileStat[] = [
    {
      label: "packets submitted",
      value: user.submissionCount.toLocaleString(),
      sub: "total",
    },
    {
      label: "sessions",
      value: sessionsTotal.toLocaleString(),
      sub: `${openTotal} open · ${closedTotal} closed`,
    },
  ];

  const submissions: ProfileSubmissionView[] = (subs?.items ?? []).map((s) => ({
    id: s.id,
    shortId: shortId(s.id),
    whenLabel: whenLabel(s.createdAt),
    packetCount: s.packetCount,
    versionLabel: String(s.gameVersion),
    status: s.status,
  }));

  const tabs: ProfileTab[] = [
    { label: "Submissions", count: subs?.pagination.total ?? submissions.length, active: true },
  ];

  return { user, stats, tabs, submissions };
}
