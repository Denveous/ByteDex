import { getInspect } from "@/lib/api";
import {
  Chip,
  Col,
  Dir,
  Hex,
  Row,
  Screen,
  Sidebar,
} from "@/components/primitives";

type SP = Record<string, string | undefined>;

export default async function InspectPage({
  searchParams,
}: {
  searchParams: Promise<SP>;
}) {
  const sp = await searchParams;
  const uuid = sp.packet?.trim() ?? "";
  const data = await getInspect(uuid);

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
              <Row gap={10}>
                <span className="bd-h1" style={{ fontSize: 20 }}>
                  Inspect
                </span>
                {data.found && (
                  <>
                    <Dir dir={data.direction} />
                    <Hex style={{ fontWeight: 600 }}>
                      {data.protocol}/{data.packetIdHex}
                    </Hex>
                    <Chip sm>v{data.versionLabel}</Chip>
                    <Chip sm>{data.payloadSizeLabel}</Chip>
                  </>
                )}
              </Row>
              {data.found ? (
                <Row gap={8}>
                  <span className="bd-xs bd-mono">{data.capturedLabel}</span>
                  <span className="bd-xs" style={{ color: "var(--fg-muted)" }}>
                    ·
                  </span>
                  <a
                    href={`/sessions?session=${encodeURIComponent(data.sessionId)}`}
                    className="bd-xs bd-mono"
                    style={{ color: "var(--accent)" }}
                  >
                    session 0x{data.sessionShortId}
                  </a>
                </Row>
              ) : (
                <span className="bd-sm">
                  Open a packet from Packets -{">"} Inspect.
                </span>
              )}
            </Col>
          </Row>

          {!data.found ? (
            <Col style={{ flex: 1, padding: 24 }}>
              <span
                className="bd-sm"
                style={{ color: "var(--fg-tertiary)" }}
              >
                {uuid ? "Packet not found." : "No packet selected."}
              </span>
            </Col>
          ) : (
            <Row style={{ flex: 1, minHeight: 0, alignItems: "stretch" }}>
              <Col
                gap={0}
                style={{ flex: 1, minWidth: 0, padding: "16px 18px", overflow: "auto" }}
              >
                {data.lines.length === 0 ? (
                  <span className="bd-xs">Empty payload.</span>
                ) : (
                  <div
                    className="bd-mono"
                    style={{ fontSize: 12.5, lineHeight: 1.7 }}
                  >
                    {data.lines.map((ln, li) => (
                      <Row key={li} gap={14} style={{ alignItems: "baseline" }}>
                        <span style={{ color: "var(--fg-muted)" }}>
                          {ln.offset}
                        </span>
                        <span style={{ display: "flex", gap: 5 }}>
                          {ln.bytes.map((b, bi) => (
                            <span
                              key={bi}
                              style={{ color: "var(--fg-tertiary)" }}
                            >
                              {b.hex}
                            </span>
                          ))}
                        </span>
                        <span
                          style={{
                            color: "var(--fg-tertiary)",
                            whiteSpace: "pre",
                          }}
                        >
                          {ln.ascii}
                        </span>
                      </Row>
                    ))}
                  </div>
                )}
              </Col>

              <Col
                gap={6}
                style={{
                  width: 260,
                  flexShrink: 0,
                  borderLeft: "1px solid var(--border)",
                  padding: "16px 14px",
                  overflow: "auto",
                }}
              >
                <span className="bd-h3">Session neighbours</span>
                {data.neighbors.length === 0 && (
                  <span className="bd-xs">No neighbours.</span>
                )}
                {data.neighbors.map((n) => (
                  <a
                    key={n.id}
                    href={`/packets/inspect?packet=${encodeURIComponent(n.id)}`}
                    style={{ textDecoration: "none", color: "inherit" }}
                  >
                    <Row
                      gap={8}
                      style={{
                        padding: "6px 8px",
                        borderRadius: "var(--radius-sm)",
                        background: n.current
                          ? "var(--accent-soft)"
                          : "transparent",
                      }}
                    >
                      <span
                        className="bd-xs bd-mono"
                        style={{ width: 52, color: "var(--fg-tertiary)" }}
                      >
                        {n.relLabel}
                      </span>
                      <Dir dir={n.direction} />
                      <span className="bd-xs bd-mono">
                        {n.protocol}/{n.packetIdHex}
                      </span>
                      <div style={{ flex: 1 }} />
                      <span className="bd-xs">{n.payloadSize}B</span>
                    </Row>
                  </a>
                ))}
              </Col>
            </Row>
          )}
        </Col>
      </Row>
    </Screen>
  );
}
