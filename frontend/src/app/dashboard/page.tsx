import { getDashboard, SessionStatus } from "@/lib/api";
import {
  Card,
  Chip,
  Col,
  Hex,
  Row,
  Screen,
  Sidebar,
} from "@/components/primitives";

type SP = Record<string, string | undefined>;

const RANGES: Record<string, number> = { "24h": 1, "7d": 7, "30d": 30, all: 365 };

export default async function DashboardPage({
  searchParams,
}: {
  searchParams: Promise<SP>;
}) {
  const sp = await searchParams;
  const range = sp.range && sp.range in RANGES ? sp.range : "7d";
  const { stats, ingestSummary, ingest, topProtocols, recentSessions, leaderboard } =
    await getDashboard(RANGES[range]);

  const maxBar = Math.max(1, ...ingest.map((p) => p.c2s + p.s2c));
  const CHART_H = 72;
  const barPx = (v: number) =>
    v <= 0 ? 0 : Math.max(2, Math.round((v / maxBar) * CHART_H));

  return (
    <Screen>
      <Row style={{ flex: 1, minHeight: 0, alignItems: "stretch" }}>
        <Sidebar active="Dashboard" />
        <Col style={{ flex: 1, minWidth: 0 }}>
          <Row
            style={{
              padding: "14px 24px",
              borderBottom: "1px solid var(--border)",
              background: "var(--bg-surface)",
            }}
          >
            <Col gap={2}>
              <span className="bd-h1">Dashboard</span>
              <span className="bd-sm">Archive activity at a glance.</span>
            </Col>
            <div style={{ flex: 1 }} />
            <Row gap={4}>
              {Object.keys(RANGES).map((r) => (
                <a
                  key={r}
                  href={`/dashboard?range=${r}`}
                  className={`bd-chip sq sm ${r === range ? "active" : ""}`}
                  style={{ textDecoration: "none" }}
                >
                  {r}
                </a>
              ))}
            </Row>
          </Row>

          <Col
            gap={18}
            style={{
              flex: 1,
              padding: "20px 24px",
              overflow: "auto",
              minHeight: 0,
            }}
          >
            <Row gap={10}>
              {stats.map((s, i) => (
                <Card key={i} padding={14} style={{ flex: 1 }}>
                  <span className="bd-label">{s.label}</span>
                  <div
                    className="bd-mono"
                    style={{
                      fontSize: 24,
                      fontWeight: 600,
                      marginTop: 6,
                      letterSpacing: "-0.01em",
                    }}
                  >
                    {s.value}
                  </div>
                  <span className="bd-xs">{s.sub}</span>
                </Card>
              ))}
            </Row>

            <Card padding={14}>
              <Row>
                <Col gap={2}>
                  <span className="bd-h3">Ingest</span>
                  <span className="bd-xs">{ingestSummary}</span>
                </Col>
                <div style={{ flex: 1 }} />
                <Row gap={4}>
                  <Chip sm dot kind="c2s">
                    C2S
                  </Chip>
                  <Chip sm dot kind="s2c">
                    S2C
                  </Chip>
                </Row>
              </Row>
              {ingest.length === 0 ? (
                <span
                  className="bd-sm"
                  style={{
                    display: "block",
                    marginTop: 16,
                    color: "var(--fg-tertiary)",
                  }}
                >
                  No ingest in this window.
                </span>
              ) : (
                <div
                  style={{
                    marginTop: 12,
                    height: 80,
                    display: "flex",
                    alignItems: "flex-end",
                    gap: 3,
                  }}
                >
                  {ingest.map((p, i) => (
                    <Col
                      key={i}
                      gap={1}
                      style={{ flex: 1, alignItems: "stretch" }}
                      title={`C2S ${p.c2s} · S2C ${p.s2c}`}
                    >
                      <div
                        style={{
                          height: `${barPx(p.s2c)}px`,
                          background: "var(--s2c)",
                          opacity: 0.85,
                          borderRadius: "2px 2px 0 0",
                        }}
                      />
                      <div
                        style={{
                          height: `${barPx(p.c2s)}px`,
                          background: "var(--c2s)",
                          opacity: 0.85,
                        }}
                      />
                    </Col>
                  ))}
                </div>
              )}
            </Card>

            <Row gap={18} style={{ alignItems: "stretch" }}>
              <Col gap={10} style={{ flex: 2, minWidth: 0 }}>
                <Row>
                  <span className="bd-h3">Recent sessions</span>
                  <div style={{ flex: 1 }} />
                  <a
                    href="/sessions"
                    className="bd-btn ghost sm"
                    style={{ textDecoration: "none" }}
                  >
                    All sessions -{">"}
                  </a>
                </Row>
                <table className="bd-table">
                  <thead>
                    <tr>
                      <th style={{ width: 90 }}>session</th>
                      <th style={{ width: 130 }}>game ver</th>
                      <th style={{ width: 110 }}>packets</th>
                      <th style={{ width: 130 }}>when</th>
                      <th style={{ width: 90 }}>status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {recentSessions.length === 0 && (
                      <tr>
                        <td
                          colSpan={5}
                          className="bd-sm"
                          style={{
                            color: "var(--fg-tertiary)",
                            padding: "16px 12px",
                          }}
                        >
                          No sessions yet.
                        </td>
                      </tr>
                    )}
                    {recentSessions.map((s) => {
                      const href = `/sessions?session=${encodeURIComponent(s.id)}`;
                      const cell = {
                        display: "block",
                        padding: "9px 12px",
                        color: "inherit",
                        textDecoration: "none",
                      } as const;
                      return (
                        <tr key={s.id}>
                          <td style={{ padding: 0 }}>
                            <a href={href} style={cell}>
                              <Hex style={{ fontWeight: 600 }}>
                                0x{s.shortId}
                              </Hex>
                            </a>
                          </td>
                          <td
                            style={{ padding: 0, color: "var(--fg-secondary)" }}
                          >
                            <a href={href} style={cell} className="bd-mono">
                              v{s.versionLabel}
                            </a>
                          </td>
                          <td style={{ padding: 0 }}>
                            <a href={href} style={cell} className="bd-mono">
                              {s.packetCount.toLocaleString()}
                            </a>
                          </td>
                          <td
                            style={{ padding: 0, color: "var(--fg-tertiary)" }}
                          >
                            <a href={href} style={cell} className="bd-sm">
                              {s.whenLabel}
                            </a>
                          </td>
                          <td style={{ padding: 0 }}>
                            <a href={href} style={cell}>
                              {s.status === SessionStatus.OPEN ? (
                                <Chip sm dot kind="c2s">
                                  open
                                </Chip>
                              ) : (
                                <Chip sm>closed</Chip>
                              )}
                            </a>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </Col>

              <Col gap={18} style={{ flex: 1, minWidth: 0 }}>
                <Card padding={14}>
                  <span className="bd-h3">Top protocols</span>
                  <Col gap={12} style={{ marginTop: 12 }}>
                    {topProtocols.length === 0 && (
                      <span className="bd-xs">No packets yet.</span>
                    )}
                    {topProtocols.map((p, i) => (
                      <Col key={i} gap={5}>
                        <Row>
                          <span
                            className="bd-mono"
                            style={{ fontSize: 12.5, fontWeight: 600 }}
                          >
                            {p.protocol}
                          </span>
                          <div style={{ flex: 1 }} />
                          <span className="bd-xs bd-mono">
                            {p.packetsLabel}
                          </span>
                        </Row>
                        <div
                          style={{
                            height: 6,
                            background: "var(--bg-subtle)",
                            borderRadius: 999,
                          }}
                        >
                          <div
                            style={{
                              width: `${p.share}%`,
                              height: "100%",
                              background: "var(--accent)",
                              borderRadius: 999,
                            }}
                          />
                        </div>
                      </Col>
                    ))}
                  </Col>
                </Card>

                <Card padding={14}>
                  <Row>
                    <span className="bd-h3">Leaderboard</span>
                    <div style={{ flex: 1 }} />
                    <span className="bd-xs">by packets</span>
                  </Row>
                  <Col gap={0} style={{ marginTop: 10 }}>
                    {leaderboard.length === 0 && (
                      <span className="bd-xs">No contributors yet.</span>
                    )}
                    {leaderboard.map((r, i) => (
                      <Row
                        key={r.rank}
                        gap={10}
                        style={{
                          padding: "8px 0",
                          borderBottom:
                            i < leaderboard.length - 1
                              ? "1px solid var(--border)"
                              : "none",
                        }}
                      >
                        <span
                          className="bd-mono"
                          style={{
                            width: 16,
                            color: "var(--fg-tertiary)",
                            fontSize: 12,
                          }}
                        >
                          {r.rank}
                        </span>
                        <span
                          className="bd-avatar"
                          style={{
                            width: 20,
                            height: 20,
                            overflow: "hidden",
                            padding: 0,
                          }}
                        >
                          {/* eslint-disable-next-line @next/next/no-img-element */}
                          <img
                            src={`https://github.com/${r.userLogin}.png`}
                            alt={r.userLogin}
                            width={20}
                            height={20}
                            style={{
                              width: "100%",
                              height: "100%",
                              objectFit: "cover",
                            }}
                          />
                        </span>
                        <span className="bd-sm" style={{ fontWeight: 500 }}>
                          @{r.userLogin}
                        </span>
                        <div style={{ flex: 1 }} />
                        <Col gap={0} style={{ alignItems: "flex-end" }}>
                          <span className="bd-sm bd-mono">
                            {r.packetsLabel}
                          </span>
                          <span className="bd-xs">
                            {r.sessionCount} sessions
                          </span>
                        </Col>
                      </Row>
                    ))}
                  </Col>
                </Card>
              </Col>
            </Row>
          </Col>
        </Col>
      </Row>
    </Screen>
  );
}
