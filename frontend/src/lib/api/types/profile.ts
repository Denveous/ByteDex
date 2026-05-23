import type { PublicUser, SessionStatus } from "@/openapi";

export interface ProfileStat {
  label: string;
  value: string;
  sub: string;
  trend?: "up" | "flame";
}

export interface ProfileSubmissionView {
  id?: string;
  shortId: string;
  whenLabel: string;
  packetCount: number;
  versionLabel: string;
  sizeLabel?: string;
  status: SessionStatus;
}

export interface ProfileTab {
  label: string;
  count: number | null;
  active: boolean;
}

export interface ProfileView {
  user: PublicUser;
  stats: ProfileStat[];
  tabs: ProfileTab[];
  submissions: ProfileSubmissionView[];
}
