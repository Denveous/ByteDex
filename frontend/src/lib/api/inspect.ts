import type { Packet, PacketSummary } from "@/openapi";
import { authedApi } from "@/lib/auth";
import type {
  InspectByteView,
  InspectLineView,
  InspectView,
  NeighborView,
} from "./types";

const hex2 = (n: number) => "0x" + n.toString(16).padStart(2, "0").toUpperCase();
const shortId = (id: string) => id.replace(/-/g, "").slice(0, 4) || "-";

function notFound(uuid: string): InspectView {
  return {
    found: false,
    packetUuid: uuid,
    sessionId: "",
    sessionShortId: "-",
    protocol: "",
    direction: "",
    packetIdHex: "",
    versionLabel: "",
    payloadSizeLabel: "",
    capturedLabel: "",
    lines: [],
    neighbors: [],
  };
}

export async function getInspect(packetUuid: string): Promise<InspectView> {
  if (!packetUuid) return notFound(packetUuid);
  const api = await authedApi();
  if (!api) return notFound(packetUuid);

  let pkt: Packet;
  try {
    pkt = (await api.packets.getPacket({ packetUuid })).data;
  } catch {
    return notFound(packetUuid);
  }

  const buf = Buffer.from(pkt.payload ?? "", "base64");

  const lines: InspectLineView[] = [];
  for (let o = 0; o < buf.length; o += 16) {
    const bytes: InspectByteView[] = [];
    let ascii = "";
    for (let i = 0; i < 16 && o + i < buf.length; i++) {
      const v = buf[o + i];
      bytes.push({
        hex: v.toString(16).padStart(2, "0").toUpperCase(),
        paletteIdx: -1,
      });
      ascii += v >= 32 && v < 127 ? String.fromCharCode(v) : "·";
    }
    lines.push({
      offset: "0x" + o.toString(16).padStart(4, "0").toUpperCase(),
      bytes,
      ascii,
    });
  }

  let neighbors: NeighborView[] = [];
  const ts = pkt.capturedAt;
  if (ts) {
    const window = (params: {
      capturedBefore?: string;
      capturedAfter?: string;
      sort: "asc" | "desc";
    }): Promise<PacketSummary[]> =>
      api.packets
        .listSessionPackets({ sessionId: pkt.sessionId, pageSize: 6, ...params })
        .then((r) => r.data.items)
        .catch(() => []);
    const [beforeDesc, afterAsc] = await Promise.all([
      window({ capturedBefore: ts, sort: "desc" }),
      window({ capturedAfter: ts, sort: "asc" }),
    ]);
    const t0 = new Date(ts).getTime();
    const toView = (p: PacketSummary, current: boolean): NeighborView => {
      const dt = (new Date(p.capturedAt).getTime() - t0) / 1000;
      return {
        id: p.id,
        packetIdHex: hex2(p.packetId),
        protocol: String(p.protocol),
        direction: String(p.direction),
        payloadSize: p.payloadSize,
        relLabel: current
          ? "0.000"
          : `${Number.isFinite(dt) && dt >= 0 ? "+" : ""}${(Number.isFinite(dt) ? dt : 0).toFixed(2)}s`,
        current,
      };
    };
    neighbors = [
      ...beforeDesc.slice().reverse().map((p) => toView(p, false)),
      toView(
        {
          id: pkt.id,
          sessionId: pkt.sessionId,
          gameVersion: pkt.gameVersion,
          protocol: pkt.protocol,
          direction: pkt.direction,
          packetId: pkt.packetId,
          payloadSize: pkt.payloadSize ?? buf.length,
          capturedAt: ts,
          submittedBy: pkt.submittedBy,
        },
        true,
      ),
      ...afterAsc.map((p) => toView(p, false)),
    ];
  }

  return {
    found: true,
    packetUuid,
    sessionId: pkt.sessionId,
    sessionShortId: shortId(pkt.sessionId),
    protocol: String(pkt.protocol),
    direction: String(pkt.direction),
    packetIdHex: hex2(pkt.packetId),
    versionLabel: String(pkt.gameVersion),
    payloadSizeLabel: `${pkt.payloadSize ?? buf.length} B`,
    capturedLabel: pkt.capturedAt
      ? new Date(pkt.capturedAt).toISOString().replace("T", " ").slice(0, 19)
      : "-",
    lines,
    neighbors,
  };
}
