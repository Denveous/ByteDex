import type { PacketSummary, Pagination } from "@/openapi";

export interface SearchResultView
  extends Pick<
    PacketSummary,
    "protocol" | "direction" | "payloadSize" | "sessionId"
  > {
  id: string;
  packetIdHex: string;
  shortSessionId: string;
  versionLabel: string;
  capturedLabel: string;
}

export interface SearchPageView {
  results: SearchResultView[];
  pagination: Pagination;
  tookMs: number;
}
