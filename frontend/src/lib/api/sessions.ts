import type {
  Direction,
  PacketSummary,
  Pagination,
  Protocol,
  Session,
  SessionStatus,
} from "@/openapi";
import { authedApi } from "@/lib/auth";
import type {
  SessionDetailView,
  SessionListItemView,
  SessionPacketRowView,
  SessionStat,
} from "./types";

const PAGE = 50;

const shortId = (id: string) => id.replace(/-/g, "").slice(0, 4) || "-";
const hex2 = (n: number) => "0x" + n.toString(16).padStart(2, "0").toUpperCase();

function whenLabel(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "-";
  if (d.toDateString() === new Date().toDateString()) {
    return `today · ${d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
  }
  return d.toLocaleDateString([], { month: "short", day: "numeric" });
}

function durationLabel(from?: string, to?: string): string {
  if (!from || !to) return "-";
  const ms = new Date(to).getTime() - new Date(from).getTime();
  if (!Number.isFinite(ms) || ms < 0) return "-";
  const m = Math.round(ms / 60000);
  return m < 60 ? `${m}m` : `${Math.floor(m / 60)}h ${m % 60}m`;
}

function toListItem(s: Session, selected = false): SessionListItemView {
  return {
    ...s,
    shortId: shortId(s.id),
    userLogin: "",
    versionLabel: String(s.gameVersion),
    durationLabel: durationLabel(s.firstPacketAt, s.lastPacketAt),
    whenLabel: whenLabel(s.createdAt),
    selected,
  };
}

export interface SessionFilter {
  submittedBy?: string;
  status?: string;
}

export async function listSessions(
  filter: SessionFilter = {},
): Promise<SessionListItemView[]> {
  const api = await authedApi();
  if (!api) return [];
  try {
    const { data } = await api.sessions.listSessions({
      pageSize: PAGE,
      submittedBy: filter.submittedBy,
      status: filter.status as SessionStatus | undefined,
    });
    return data.items.map((s) => toListItem(s));
  } catch {
    return [];
  }
}

export async function getSessionCounts(): Promise<{
  total: string;
  open: string;
}> {
  const api = await authedApi();
  const count = async (status?: SessionStatus): Promise<number> => {
    if (!api) return 0;
    try {
      const { data } = await api.sessions.listSessions({ pageSize: 1, status });
      return data.pagination.total;
    } catch {
      return 0;
    }
  };
  const [all, open] = await Promise.all([count(), count("open")]);
  return {
    total: `${all.toLocaleString()} total`,
    open: `${open.toLocaleString()} open`,
  };
}

function packetRows(items: PacketSummary[]): SessionPacketRowView[] {
  const t0 = items.length ? new Date(items[0].capturedAt).getTime() : 0;
  return items.map((p) => {
    const raw = (new Date(p.capturedAt).getTime() - t0) / 1000;
    const off = Number.isFinite(raw) ? raw : 0;
    const sign = off >= 0 ? "+" : "";
    return {
      id: p.id,
      direction: p.direction,
      protocol: p.protocol,
      payloadSize: p.payloadSize,
      packetIdHex: hex2(p.packetId),
      offsetLabel: `${sign}${off.toFixed(3)}`,
    };
  });
}

function stats(session: Session): SessionStat[] {
  const total = session.packetCount.toLocaleString();
  const ageLabel = durationLabel(session.firstPacketAt, session.lastPacketAt);
  const startedLabel = session.firstPacketAt
    ? new Date(session.firstPacketAt).toLocaleString([], {
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      })
    : "-";
  return [
    { label: "packets", value: total, sub: "total" },
    { label: "duration", value: ageLabel, sub: "first -> last packet" },
    { label: "started", value: startedLabel, sub: "first packet" },
    { label: "status", value: session.status, sub: session.closedAt ? "closed" : "live" },
  ];
}

export interface PacketFilter {
  protocol?: string;
  direction?: string;
  page?: number;
  pageSize?: number;
}

export async function getSessionDetail(
  sessionId: string,
  pf: PacketFilter = {},
): Promise<SessionDetailView | null> {
  const api = await authedApi();
  if (!api) return null;

  let session: Session;
  try {
    session = (await api.sessions.getSession({ sessionId })).data;
  } catch {
    return null;
  }

  const pageSize = pf.pageSize && pf.pageSize > 0 ? pf.pageSize : PAGE;
  const page = pf.page && pf.page > 0 ? pf.page : 1;

  let items: PacketSummary[] = [];
  let pagination: Pagination = { page, pageSize, total: 0 };
  try {
    const { data } = await api.packets.listSessionPackets({
      sessionId,
      page,
      pageSize,
      protocol: pf.protocol as Protocol | undefined,
      direction: pf.direction as Direction | undefined,
    });
    items = data.items;
    pagination = data.pagination;
  } catch {
    // empty
  }

  return {
    session: toListItem(session, true),
    stats: stats(session),
    packets: packetRows(items),
    packetPagination: pagination,
  };
}
