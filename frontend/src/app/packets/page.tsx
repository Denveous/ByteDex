import { listGameVersions, searchPackets } from "@/lib/api";
import {
  Btn,
  Card,
  Chip,
  Col,
  Dir,
  Hex,
  Icon,
  Row,
  Screen,
  Sidebar,
} from "@/components/primitives";

type SP = Record<string, string | undefined>;

const PROTOCOLS = ["LOGIN", "GAME", "CHAT"] as const;

export default async function PacketsPage({
  searchParams,
}: {
  searchParams: Promise<SP>;
}) {
  const sp = await searchParams;

  const q = sp.q?.trim() || "";
  const proto =
    sp.proto && PROTOCOLS.includes(sp.proto.toUpperCase() as never)
      ? sp.proto.toUpperCase()
      : undefined;
  const dir =
    sp.dir?.toUpperCase() === "C2S" || sp.dir?.toUpperCase() === "S2C"
      ? sp.dir.toUpperCase()
      : undefined;
  const idNum = sp.id ? Number.parseInt(sp.id.replace(/^0x/i, ""), 16) : NaN;
  const packetId =
    Number.isInteger(idNum) && idNum >= 0 && idNum <= 255 ? idNum : undefined;
  const gvNum = sp.gv ? Number(sp.gv) : NaN;
  const gameVersion = Number.isFinite(gvNum) ? gvNum : undefined;
  const page = Math.max(1, Number(sp.page) || 1);

  const [{ results, pagination, tookMs }, gameVersions] = await Promise.all([
    searchPackets({
      payloadHex: q || undefined,
      protocols: proto ? [proto] : undefined,
      directions: dir ? [dir] : undefined,
      packetIds: packetId != null ? [packetId] : undefined,
      gameVersion,
      page,
    }),
    listGameVersions(),
  ]);

  const pages = Math.max(
    1,
    Math.ceil(pagination.total / pagination.pageSize) || 1,
  );
  const pageHref = (n: number) => {
    const p = new URLSearchParams();
    if (q) p.set("q", q);
    if (sp.proto) p.set("proto", sp.proto);
    if (sp.dir) p.set("dir", sp.dir);
    if (sp.id) p.set("id", sp.id);
    if (sp.gv) p.set("gv", sp.gv);
    if (n > 1) p.set("page", String(n));
    const s = p.toString();
    return s ? `/packets?${s}` : "/packets";
  };

  return (
    <Screen>
      <Row style={{ flex: 1, minHeight: 0, alignItems: "stretch" }}>
        <Sidebar active="Packets" />
        <Col style={{ flex: 1, minWidth: 0 }}>
          <Row
            style={{
              padding: "14px 24px",
              borderBottom: "1px solid var(--border)",
              background: "var(--bg-surface)",
            }}
          >
            <Col gap={2}>
              <span className="bd-h1">Packets</span>
              <span className="bd-sm">
                Search across all sessions in the archive.
              </span>
            </Col>
            <div style={{ flex: 1 }} />
          </Row>

          <Col
            gap={14}
            style={{
              flex: 1,
              padding: "20px 24px",
              overflow: "hidden",
              minHeight: 0,
            }}
          >
            <form action="/packets" method="get">
              <Row gap={10} style={{ flexWrap: "wrap" }}>
                <input
                  name="q"
                  defaultValue={q}
                  placeholder='payload hex - e.g. "14 03 03"'
                  className="bd-input mono"
                  style={{ flex: 1, minWidth: 240, height: 40 }}
                />
                <select
                  name="proto"
                  defaultValue={proto ?? ""}
                  className="bd-input"
                  style={{ height: 40 }}
                >
                  <option value="">any protocol</option>
                  {PROTOCOLS.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
                <select
                  name="dir"
                  defaultValue={dir ?? ""}
                  className="bd-input"
                  style={{ height: 40 }}
                >
                  <option value="">any direction</option>
                  <option value="C2S">C2S</option>
                  <option value="S2C">S2C</option>
                </select>
                <input
                  name="id"
                  defaultValue={sp.id ?? ""}
                  placeholder="packet id e.g. 0x10"
                  className="bd-input mono"
                  style={{ width: 150, height: 40 }}
                />
                <select
                  name="gv"
                  defaultValue={sp.gv ?? ""}
                  className="bd-input mono"
                  style={{ width: 140, height: 40 }}
                >
                  <option value="">any version</option>
                  {gameVersions.map((v) => (
                    <option key={v} value={String(v)}>
                      {v}
                    </option>
                  ))}
                </select>
                <Btn lg primary>
                  Search
                </Btn>
              </Row>
            </form>

            <Row>
              <Row gap={6}>
                <span style={{ fontSize: 13, fontWeight: 500 }}>
                  {pagination.total.toLocaleString()} packets
                </span>
                <span className="bd-xs">
                  · {tookMs} ms · page {pagination.page} of {pages}
                </span>
              </Row>
              <div style={{ flex: 1 }} />
              {q && (
                <span className="bd-xs">
                  payload contains{" "}
                  <span className="bd-mono" style={{ color: "var(--accent-ink)" }}>
                    {q}
                  </span>
                </span>
              )}
            </Row>

            <Col gap={8} style={{ flex: 1, overflow: "auto", minHeight: 0 }}>
              {results.length === 0 && (
                <span
                  className="bd-sm"
                  style={{ padding: "24px 4px", color: "var(--fg-tertiary)" }}
                >
                  No packets match this query.
                </span>
              )}
              {results.map((p, i) => (
                <a
                  key={i}
                  href={`/packets/inspect?packet=${encodeURIComponent(p.id)}`}
                  style={{ textDecoration: "none", color: "inherit" }}
                >
                  <Card padding={14}>
                    <Row gap={12}>
                      <Dir dir={p.direction} />
                      <Col gap={2} style={{ minWidth: 0 }}>
                        <Row gap={8}>
                          <Hex style={{ fontWeight: 600 }}>
                            {p.protocol}/{p.packetIdHex}
                          </Hex>
                          <Chip sm>{p.payloadSize} B</Chip>
                          <span className="bd-xs">v{p.versionLabel}</span>
                        </Row>
                        <Row gap={8} style={{ color: "var(--fg-tertiary)" }}>
                          <Hex style={{ fontSize: 11.5 }}>
                            session 0x{p.shortSessionId}
                          </Hex>
                          <span className="bd-xs">·</span>
                          <span className="bd-xs bd-mono">
                            {p.capturedLabel}
                          </span>
                        </Row>
                      </Col>
                      <div style={{ flex: 1 }} />
                      <Icon name="chevron" size={12} style={{ color: "var(--fg-muted)" }} />
                    </Row>
                  </Card>
                </a>
              ))}
            </Col>

            <Row gap={6} style={{ justifyContent: "flex-end" }}>
              <span className="bd-xs">
                {pagination.total === 0
                  ? "0 results"
                  : `page ${pagination.page} of ${pages}`}
              </span>
              <div
                style={{
                  width: 1,
                  height: 16,
                  background: "var(--border)",
                  margin: "0 6px",
                }}
              />
              {page > 1 ? (
                <a
                  href={pageHref(page - 1)}
                  className="bd-chip sq sm"
                  style={{ textDecoration: "none" }}
                >
                  ‹ Prev
                </a>
              ) : (
                <Chip sq sm>
                  ‹ Prev
                </Chip>
              )}
              {page < pages ? (
                <a
                  href={pageHref(page + 1)}
                  className="bd-chip sq sm"
                  style={{ textDecoration: "none" }}
                >
                  Next ›
                </a>
              ) : (
                <Chip sq sm>
                  Next ›
                </Chip>
              )}
            </Row>
          </Col>
        </Col>
      </Row>
    </Screen>
  );
}
