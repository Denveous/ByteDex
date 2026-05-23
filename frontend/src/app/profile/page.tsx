import { getProfile } from "@/lib/api";
import {
  Card,
  Chip,
  Col,
  Hex,
  Icon,
  Row,
  Screen,
  Sidebar,
} from "@/components/primitives";

function joinedLabel(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? ""
    : `Joined ${d.toLocaleDateString([], { month: "short", year: "numeric" })}`;
}

export default async function ProfilePage() {
  const { user, stats, tabs, submissions } = await getProfile();
  const joined = joinedLabel(user.createdAt);
  const hasLogin = user.githubLogin !== "-" && user.githubLogin !== "";
  const avatar =
    user.avatarUrl ||
    (hasLogin ? `https://github.com/${user.githubLogin}.png` : null);

  return (
    <Screen>
      <Row style={{ flex: 1, minHeight: 0, alignItems: "stretch" }}>
        <Sidebar active="Profile" />
        <Col style={{ flex: 1, minWidth: 0 }}>
          <div
            style={{
              padding: "16px 24px",
              borderBottom: "1px solid var(--border)",
              background: "var(--bg-surface)",
            }}
          >
            <Row gap={16}>
              <span
                className="bd-avatar lg"
                style={{ overflow: "hidden", padding: 0 }}
              >
                {avatar ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={avatar}
                    alt={user.githubLogin}
                    width={64}
                    height={64}
                    style={{ width: "100%", height: "100%", objectFit: "cover" }}
                  />
                ) : (
                  (user.githubLogin[0] ?? "?").toUpperCase()
                )}
              </span>
              <Col gap={6} style={{ flex: 1, minWidth: 0 }}>
                <Row gap={10}>
                  <span className="bd-h1" style={{ fontSize: 24 }}>
                    @{user.githubLogin}
                  </span>
                  <Chip sm leading={<Icon name="git" size={11} />}>
                    github ✓
                  </Chip>
                </Row>
                {joined && <span className="bd-xs">{joined}</span>}
              </Col>
              {hasLogin && (
                <a
                  href={`https://github.com/${user.githubLogin}`}
                  target="_blank"
                  rel="noreferrer"
                  className="bd-btn ghost sm"
                  style={{ textDecoration: "none" }}
                >
                  <Icon name="git" size={12} />
                  github.com/{user.githubLogin}
                </a>
              )}
            </Row>
          </div>

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

            <Row
              gap={2}
              style={{
                borderBottom: "1px solid var(--border)",
                marginBottom: -10,
              }}
            >
              {tabs.map((t, i) => (
                <Row
                  key={i}
                  gap={6}
                  style={{
                    padding: "8px 14px",
                    borderBottom: "2px solid var(--accent)",
                    color: "var(--fg-primary)",
                    fontSize: 13.5,
                    fontWeight: 500,
                    marginBottom: -1,
                  }}
                >
                  {t.label}
                  {t.count !== null && (
                    <Chip
                      sm
                      style={{
                        background: "var(--accent-soft)",
                        color: "var(--accent-ink)",
                      }}
                    >
                      {t.count}
                    </Chip>
                  )}
                </Row>
              ))}
            </Row>

            <table className="bd-table">
              <thead>
                <tr>
                  <th style={{ width: 100 }}>session</th>
                  <th style={{ width: 160 }}>submitted</th>
                  <th style={{ width: 120 }}>packets</th>
                  <th style={{ width: 140 }}>game ver</th>
                  <th style={{ width: 110 }}>status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {submissions.length === 0 && (
                  <tr>
                    <td
                      colSpan={6}
                      className="bd-sm"
                      style={{
                        color: "var(--fg-tertiary)",
                        padding: "16px 12px",
                      }}
                    >
                      No submissions yet.
                    </td>
                  </tr>
                )}
                {submissions.map((r, i) => (
                  <tr key={i}>
                    <td>
                      <Hex style={{ fontWeight: 600 }}>0x{r.shortId}</Hex>
                    </td>
                    <td
                      className="bd-sm"
                      style={{ color: "var(--fg-tertiary)" }}
                    >
                      {r.whenLabel}
                    </td>
                    <td className="bd-mono">
                      {r.packetCount.toLocaleString()}
                    </td>
                    <td
                      className="bd-mono"
                      style={{ color: "var(--fg-secondary)" }}
                    >
                      v{r.versionLabel}
                    </td>
                    <td>
                      <Chip sm dot>
                        {r.status}
                      </Chip>
                    </td>
                    <td style={{ textAlign: "right" }}>
                      {r.id && (
                        <a
                          href={`/sessions?session=${encodeURIComponent(r.id)}`}
                          className="bd-btn sm ghost"
                          style={{ textDecoration: "none" }}
                        >
                          Open
                        </a>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Col>
        </Col>
      </Row>
    </Screen>
  );
}
