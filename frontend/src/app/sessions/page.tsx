import {
  getSessionCounts,
  getSessionDetail,
  listSessions,
  SessionStatus,
} from "@/lib/api";
import { getAuthedUser } from "@/lib/auth";
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

function href(params: SP): string {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v) q.set(k, v);
  const s = q.toString();
  return s ? `/sessions?${s}` : "/sessions";
}

export default async function SessionsPage({
  searchParams,
}: {
  searchParams: Promise<SP>;
}) {
  const sp = await searchParams;

  const filter =
    sp.filter === "mine" || sp.filter === "open" ? sp.filter : "all";
  const selectedId = sp.session && sp.session.trim() ? sp.session : null;
  const dir = sp.dir === "c2s" || sp.dir === "s2c" ? sp.dir : null;
  const proto =
    sp.proto && PROTOCOLS.includes(sp.proto.toUpperCase() as never)
      ? sp.proto.toUpperCase()
      : null;
  const pageNum = Math.max(1, Number.parseInt(sp.page ?? "1", 10) || 1);
  const keep = filter === "all" ? undefined : filter;

  const me = filter === "mine" ? await getAuthedUser() : null;
  const [counts, list, detail] = await Promise.all([
    getSessionCounts(),
    listSessions({
      submittedBy: filter === "mine" ? me?.id : undefined,
      status: filter === "open" ? "open" : undefined,
    }),
    selectedId
      ? getSessionDetail(selectedId, {
          direction: dir ? dir.toUpperCase() : undefined,
          protocol: proto ?? undefined,
          page: pageNum,
        })
      : Promise.resolve(null),
  ]);

  const chip = (label: string, active: boolean, to: string) => (
    <a
      href={to}
      className={`bd-chip sq sm ${active ? "active" : ""}`}
      style={{ textDecoration: "none" }}
    >
      {label}
    </a>
  );

  return (
    <Screen>
      <Row style={{ flex: 1, minHeight: 0, alignItems: "stretch" }}>
        <Sidebar active="Sessions" />
        <Col style={{ flex: 1, minWidth: 0 }}>
          <Row
            style={{
              padding: "14px 24px",
              borderBottom: "1px solid var(--border)",
              background: "var(--bg-surface)",
              flexShrink: 0,
            }}
          >
            <Col gap={2}>
              <Row gap={8}>
                <span className="bd-h1">Sessions</span>
                <Chip sm>{counts.total}</Chip>
                <Chip sm kind="c2s" dot>
                  {counts.open}
                </Chip>
              </Row>
              <span className="bd-sm">
                Recordings of one continuous packet stream. Closed sessions are
                immutable.
              </span>
            </Col>
            <div style={{ flex: 1 }} />
            <form action="/sessions" method="get" style={{ display: "flex" }}>
              {keep && <input type="hidden" name="filter" value={keep} />}
              <input
                name="session"
                defaultValue={selectedId ?? ""}
                placeholder="Jump to session id..."
                className="bd-input mono"
                style={{ width: 280 }}
              />
            </form>
          </Row>

          <Row style={{ flex: 1, minHeight: 0, alignItems: "stretch" }}>
            <Col
              style={{
                width: detail ? 340 : undefined,
                flex: detail ? undefined : 1,
                borderRight: detail ? "1px solid var(--border)" : undefined,
                background: "var(--bg-surface)",
                minHeight: 0,
              }}
            >
              <Row
                gap={6}
                style={{
                  padding: "10px 14px",
                  borderBottom: "1px solid var(--border)",
                }}
              >
                {chip("All", filter === "all", href({ session: selectedId ?? undefined }))}
                {chip("Mine", filter === "mine", href({ filter: "mine", session: selectedId ?? undefined }))}
                {chip("Open", filter === "open", href({ filter: "open", session: selectedId ?? undefined }))}
                <div style={{ flex: 1 }} />
                <span className="bd-xs">{list.length} shown</span>
              </Row>
              <Col gap={0} style={{ flex: 1, overflow: "auto" }}>
                {list.length === 0 && (
                  <span
                    className="bd-sm"
                    style={{ padding: "16px 14px", color: "var(--fg-tertiary)" }}
                  >
                    No sessions match.
                  </span>
                )}
                {list.map((s) => {
                  const sel = s.id === selectedId;
                  return (
                    <a
                      key={s.id}
                      href={href({ session: s.id, filter: keep })}
                      style={{ textDecoration: "none", color: "inherit" }}
                    >
                      <Col
                        gap={6}
                        style={{
                          padding: "12px 14px",
                          paddingLeft: 12,
                          borderBottom: "1px solid var(--border)",
                          background: sel ? "var(--accent-soft)" : "transparent",
                          borderLeft: sel
                            ? "2px solid var(--accent)"
                            : "2px solid transparent",
                        }}
                      >
                        <Row gap={8}>
                          <Hex style={{ fontWeight: 600 }}>0x{s.shortId}</Hex>
                          {s.status === SessionStatus.OPEN && (
                            <Chip sm kind="c2s" dot>
                              open
                            </Chip>
                          )}
                          <div style={{ flex: 1 }} />
                          <span className="bd-xs">{s.whenLabel}</span>
                        </Row>
                        <Row gap={10}>
                          <span className="bd-xs">v{s.versionLabel}</span>
                          {s.durationLabel !== "-" && (
                            <span className="bd-xs">· {s.durationLabel}</span>
                          )}
                          <div style={{ flex: 1 }} />
                          <span className="bd-xs bd-mono">
                            {s.packetCount.toLocaleString()} pkts
                          </span>
                        </Row>
                        {s.tags && s.tags.length > 0 && (
                          <Row gap={4} style={{ flexWrap: "wrap" }}>
                            {s.tags.map((t, j) => (
                              <Chip key={j} sm>
                                {t}
                              </Chip>
                            ))}
                          </Row>
                        )}
                      </Col>
                    </a>
                  );
                })}
              </Col>
            </Col>

            {detail && (
              <Col
                gap={20}
                style={{
                  flex: 1,
                  padding: 24,
                  minWidth: 0,
                  overflow: "hidden",
                }}
              >
                <Row gap={14}>
                  <Col gap={4}>
                    <Row gap={10}>
                      <Hex style={{ fontSize: 18, fontWeight: 600 }}>
                        session 0x{detail.session.shortId}
                      </Hex>
                      <Chip sm>{detail.session.status}</Chip>
                      <Chip sm>v {detail.session.versionLabel}</Chip>
                    </Row>
                    <Row gap={8}>
                      <span className="bd-sm">
                        created {detail.session.whenLabel}
                      </span>
                      {detail.session.durationLabel !== "-" && (
                        <>
                          <span className="bd-sm">·</span>
                          <span className="bd-sm">
                            {detail.session.durationLabel}
                          </span>
                        </>
                      )}
                    </Row>
                  </Col>
                  <div style={{ flex: 1 }} />
                  <Btn ghost leading={<Icon name="download" size={13} />}>
                    Export
                  </Btn>
                  <a
                    href={href({ filter: keep })}
                    className="bd-btn ghost"
                    style={{ textDecoration: "none" }}
                    title="Close"
                  >
                    Close
                  </a>
                </Row>

                <Row gap={10}>
                  {detail.stats.map((s, i) => (
                    <Card key={i} padding={14} style={{ flex: 1 }}>
                      <span className="bd-label">{s.label}</span>
                      <div
                        className="bd-mono"
                        style={{
                          fontSize: 22,
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

                <Col gap={10} style={{ flex: 1, minHeight: 0 }}>
                  <Row>
                    <span className="bd-h3">Packets in this session</span>
                    {(() => {
                      const { page, pageSize, total } = detail.packetPagination;
                      const from = total === 0 ? 0 : (page - 1) * pageSize + 1;
                      const to = Math.min(total, page * pageSize);
                      return (
                        <Chip sm>
                          {total === 0
                            ? "0 packets"
                            : `${from.toLocaleString()}–${to.toLocaleString()} of ${total.toLocaleString()}`}
                        </Chip>
                      );
                    })()}
                    <div style={{ flex: 1 }} />
                    <Row gap={4}>
                      {chip(
                        "All",
                        !dir,
                        href({ session: selectedId ?? undefined, filter: keep, proto: proto ?? undefined }),
                      )}
                      {chip(
                        "C2S",
                        dir === "c2s",
                        href({ session: selectedId ?? undefined, filter: keep, dir: "c2s", proto: proto ?? undefined }),
                      )}
                      {chip(
                        "S2C",
                        dir === "s2c",
                        href({ session: selectedId ?? undefined, filter: keep, dir: "s2c", proto: proto ?? undefined }),
                      )}
                    </Row>
                    <div
                      style={{
                        width: 1,
                        height: 18,
                        background: "var(--border)",
                        margin: "0 4px",
                      }}
                    />
                    <Row gap={4}>
                      {PROTOCOLS.map((p) =>
                        chip(
                          p,
                          proto === p,
                          href({
                            session: selectedId ?? undefined,
                            filter: keep,
                            dir: dir ?? undefined,
                            proto: proto === p ? undefined : p,
                          }),
                        ),
                      )}
                    </Row>
                  </Row>
                  <div
                    style={{
                      overflow: "auto",
                      borderRadius: "var(--radius-md)",
                      border: "1px solid var(--border)",
                      flex: 1,
                      minHeight: 0,
                    }}
                  >
                    <table
                      className="bd-table dense sticky"
                      style={{ border: "none", borderRadius: 0 }}
                    >
                      <thead>
                        <tr>
                          <th style={{ width: 80 }}>t</th>
                          <th style={{ width: 54 }}>dir</th>
                          <th style={{ width: 70 }}>proto</th>
                          <th style={{ width: 60 }}>id</th>
                          <th>size</th>
                          <th style={{ width: 40 }}></th>
                        </tr>
                      </thead>
                      <tbody>
                        {detail.packets.length === 0 && (
                          <tr>
                            <td
                              colSpan={6}
                              className="bd-sm"
                              style={{
                                color: "var(--fg-tertiary)",
                                padding: "16px 12px",
                              }}
                            >
                              No packets match this filter.
                            </td>
                          </tr>
                        )}
                        {detail.packets.map((p, i) => {
                          const href = `/packets/inspect?packet=${encodeURIComponent(p.id)}`;
                          // link fills the un-padded cell so a click anywhere in the row navigates
                          const cell = {
                            display: "block",
                            padding: "6px 10px",
                            color: "inherit",
                            textDecoration: "none",
                          } as const;
                          return (
                            <tr key={i}>
                              <td
                                className="bd-mono"
                                style={{ padding: 0, color: "var(--fg-tertiary)" }}
                              >
                                <a href={href} style={cell}>
                                  {p.offsetLabel}
                                </a>
                              </td>
                              <td style={{ padding: 0 }}>
                                <a href={href} style={cell}>
                                  <Dir dir={p.direction} />
                                </a>
                              </td>
                              <td style={{ padding: 0 }}>
                                <a href={href} style={cell} className="bd-mono">
                                  {p.protocol}
                                </a>
                              </td>
                              <td style={{ padding: 0 }}>
                                <a href={href} style={cell} className="bd-mono">
                                  {p.packetIdHex}
                                </a>
                              </td>
                              <td
                                className="bd-mono"
                                style={{ padding: 0, color: "var(--fg-tertiary)" }}
                              >
                                <a href={href} style={cell}>
                                  {p.payloadSize} B
                                </a>
                              </td>
                              <td style={{ padding: 0 }}>
                                <a
                                  href={href}
                                  style={cell}
                                  title="Inspect packet"
                                >
                                  <Icon
                                    name="chevron"
                                    size={12}
                                    style={{ color: "var(--fg-muted)" }}
                                  />
                                </a>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                  {(() => {
                    const { page, pageSize, total } = detail.packetPagination;
                    if (total <= pageSize) return null;
                    const pages = Math.max(1, Math.ceil(total / pageSize));
                    const prevHref =
                      page > 1
                        ? href({
                            session: selectedId ?? undefined,
                            filter: keep,
                            dir: dir ?? undefined,
                            proto: proto ?? undefined,
                            page: page > 2 ? String(page - 1) : undefined,
                          })
                        : null;
                    const nextHref =
                      page < pages
                        ? href({
                            session: selectedId ?? undefined,
                            filter: keep,
                            dir: dir ?? undefined,
                            proto: proto ?? undefined,
                            page: String(page + 1),
                          })
                        : null;
                    const disabledStyle = {
                      pointerEvents: "none" as const,
                      opacity: 0.4,
                    };
                    return (
                      <Row gap={6} style={{ justifyContent: "flex-end" }}>
                        <a
                          href={prevHref ?? "#"}
                          className="bd-btn ghost sm"
                          style={{
                            textDecoration: "none",
                            ...(prevHref ? {} : disabledStyle),
                          }}
                        >
                          ← Prev
                        </a>
                        <span className="bd-xs" style={{ alignSelf: "center" }}>
                          page {page} / {pages}
                        </span>
                        <a
                          href={nextHref ?? "#"}
                          className="bd-btn ghost sm"
                          style={{
                            textDecoration: "none",
                            ...(nextHref ? {} : disabledStyle),
                          }}
                        >
                          Next -{">"}
                        </a>
                      </Row>
                    );
                  })()}
                </Col>
              </Col>
            )}
          </Row>
        </Col>
      </Row>
    </Screen>
  );
}
