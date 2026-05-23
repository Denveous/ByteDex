import type { ArchiveStats, IngestSeries, Leaderboard } from "@/openapi";
import { authedApi } from "@/lib/auth";
import type {
  DashboardStat,
  DashboardView,
  IngestPoint,
  LeaderboardRow,
  ProtocolShare,
} from "./types";
import { getSessionCounts, listSessions } from "./sessions";

function fmt(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(2).replace(/\.?0+$/, "")} M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1).replace(/\.0$/, "")} K`;
  return n.toLocaleString();
}

const EMPTY: DashboardView = {
  stats: [],
  ingestSummary: "",
  ingest: [],
  topProtocols: [],
  recentSessions: [],
  leaderboard: [],
};

export async function getDashboard(days = 7): Promise<DashboardView> {
  const api = await authedApi();
  if (!api) return EMPTY;

  const unwrap = <T>(p: Promise<{ data: T }>): Promise<T | null> =>
    p.then((r) => r.data).catch(() => null);

  const [stats, lb, ingest, recentSessions, counts] = await Promise.all([
    unwrap<ArchiveStats>(api.search.getStats()),
    unwrap<Leaderboard>(api.search.getLeaderboard({ limit: 8 })),
    unwrap<IngestSeries>(api.search.getIngestSeries({ days })),
    listSessions(),
    getSessionCounts(),
  ]);

  const recent = recentSessions.slice(0, 6);
  if (!stats) return { ...EMPTY, recentSessions: recent };

  const byProto = stats.byProtocol ?? {};
  const maxProto = Math.max(1, ...Object.values(byProto));
  const topProtocols: ProtocolShare[] = Object.entries(byProto)
    .sort((a, b) => b[1] - a[1])
    .map(([protocol, count]) => ({
      protocol,
      packetsLabel: fmt(count),
      share: Math.round((count / maxProto) * 100),
    }));

  const dir = stats.byDirection ?? {};
  const statsCards: DashboardStat[] = [
    {
      label: "packets archived",
      value: fmt(stats.totalPackets ?? 0),
      sub: `${fmt(dir["C2S"] ?? 0)} C2S · ${fmt(dir["S2C"] ?? 0)} S2C`,
    },
    {
      label: "sessions",
      value: (stats.totalSessions ?? 0).toLocaleString(),
      sub: counts.open,
    },
    {
      label: "contributors",
      value: (stats.totalContributors ?? 0).toLocaleString(),
      sub: `${stats.activeContributors ?? 0} active · 7d`,
    },
    {
      label: "schemas inferred",
      value: (stats.totalSchemas ?? 0).toLocaleString(),
      sub: `across ${Object.keys(byProto).length} protocols`,
    },
  ];

  const buckets = ingest?.buckets ?? [];
  const ingestPoints: IngestPoint[] = buckets.map((b) => ({
    c2s: b.c2s,
    s2c: b.s2c,
  }));
  const ingestTotal = buckets.reduce((a, b) => a + b.c2s + b.s2c, 0);

  const leaderboard: LeaderboardRow[] = (lb?.entries ?? []).map((e) => ({
    rank: e.rank,
    userLogin: e.githubLogin,
    packetsLabel: fmt(e.packetCount),
    sessionCount: e.sessionCount,
  }));

  return {
    stats: statsCards,
    ingestSummary: `${fmt(ingestTotal)} packets · last ${ingest?.days ?? days}d`,
    ingest: ingestPoints,
    topProtocols,
    recentSessions: recent,
    leaderboard,
  };
}
