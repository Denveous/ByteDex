import type { PacketSummary, Pagination, Session } from "@/openapi";

export interface SessionListItemView extends Session {
  shortId: string;
  userLogin: string;
  versionLabel: string;
  durationLabel: string;
  whenLabel: string;
  selected?: boolean;
}

export interface SessionStat {
  label: string;
  value: string;
  sub: string;
}

export interface SessionPacketRowView
  extends Pick<PacketSummary, "protocol" | "direction" | "payloadSize"> {
  id: string;
  packetIdHex: string;
  offsetLabel: string;
}

export interface SessionDetailView {
  session: SessionListItemView;
  stats: SessionStat[];
  packets: SessionPacketRowView[];
  packetPagination: Pagination;
}
