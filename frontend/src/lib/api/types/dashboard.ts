import type { ProfileStat } from "./profile";
import type { SessionListItemView } from "./sessions";

export type DashboardStat = ProfileStat;

export interface ProtocolShare {
  protocol: string;
  packetsLabel: string;
  share: number; // 0..100
}

export interface LeaderboardRow {
  rank: number;
  userLogin: string;
  packetsLabel: string;
  sessionCount: number;
}

export interface IngestPoint {
  c2s: number;
  s2c: number;
}

export interface DashboardView {
  stats: DashboardStat[];
  ingestSummary: string;
  ingest: IngestPoint[];
  topProtocols: ProtocolShare[];
  recentSessions: SessionListItemView[];
  leaderboard: LeaderboardRow[];
}
