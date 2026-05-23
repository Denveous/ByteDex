import type { Direction, PacketPage, Protocol, SearchQuery } from "@/openapi";
import { authedApi } from "@/lib/auth";
import type { SearchPageView, SearchResultView } from "./types";

export async function listGameVersions(): Promise<number[]> {
  const api = await authedApi();
  if (!api) return [];
  try {
    const { data } = await api.search.listGameVersions();
    return data.versions ?? [];
  } catch {
    return [];
  }
}

const PAGE = 50;

export interface SearchInput {
  payloadHex?: string;
  protocols?: string[];
  directions?: string[];
  packetIds?: number[];
  gameVersion?: number;
  page?: number;
}

const shortId = (id: string) => id.replace(/-/g, "").slice(0, 4) || "-";
const hex2 = (n: number) => "0x" + n.toString(16).padStart(2, "0").toUpperCase();

function capturedLabel(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? "-"
    : d.toISOString().replace("T", " ").slice(0, 19);
}

const EMPTY: SearchPageView = {
  results: [],
  pagination: { page: 1, pageSize: PAGE, total: 0 },
  tookMs: 0,
};

export async function searchPackets(
  input: SearchInput = {},
): Promise<SearchPageView> {
  const api = await authedApi();
  if (!api) return EMPTY;

  const searchQuery: SearchQuery = { pageSize: PAGE, page: input.page ?? 1 };
  if (input.payloadHex?.trim()) searchQuery.payloadHex = input.payloadHex.trim();
  if (input.protocols?.length) searchQuery.protocols = input.protocols as Protocol[];
  if (input.directions?.length) searchQuery.directions = input.directions as Direction[];
  if (input.packetIds?.length) searchQuery.packetIds = input.packetIds;
  if (input.gameVersion != null) searchQuery.gameVersions = [input.gameVersion];

  const started = Date.now();
  let page: PacketPage;
  try {
    page = (await api.search.searchPackets({ searchQuery })).data;
  } catch {
    return EMPTY;
  }

  const results: SearchResultView[] = page.items.map((p) => ({
    id: p.id,
    protocol: p.protocol,
    direction: p.direction,
    payloadSize: p.payloadSize,
    sessionId: p.sessionId,
    packetIdHex: hex2(p.packetId),
    shortSessionId: shortId(p.sessionId),
    versionLabel: String(p.gameVersion),
    capturedLabel: capturedLabel(p.capturedAt),
  }));
  return { results, pagination: page.pagination, tookMs: Date.now() - started };
}
