import { getAuthedUser } from "@/lib/auth";
import {
  Card,
  Chip,
  Col,
  Icon,
  Row,
  Screen,
  Sidebar,
} from "@/components/primitives";
import { DeleteAccountButton } from "@/components/DeleteAccountButton";

function joinedLabel(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? ""
    : `joined ${d.toLocaleDateString([], { month: "short", year: "numeric" })}`;
}

export default async function SettingsPage() {
  const me = await getAuthedUser();
  const login = me?.githubLogin && me.githubLogin !== "" ? me.githubLogin : "-";
  const hasLogin = login !== "-";
  const avatar =
    me?.avatarUrl || (hasLogin ? `https://github.com/${login}.png` : null);
  const joined = me ? joinedLabel(me.createdAt) : "";

  return (
    <Screen>
      <Row style={{ flex: 1, minHeight: 0, alignItems: "stretch" }}>
        <Sidebar active="Settings" />
        <Col style={{ flex: 1, minWidth: 0 }}>
          <Row
            style={{
              padding: "14px 24px",
              borderBottom: "1px solid var(--border)",
              background: "var(--bg-surface)",
            }}
          >
            <Col gap={2}>
              <span className="bd-h1">Settings</span>
              <span className="bd-sm">Manage your account.</span>
            </Col>
          </Row>

          <Col
            gap={18}
            style={{
              flex: 1,
              padding: "20px 24px",
              overflow: "auto",
              minHeight: 0,
              maxWidth: 760,
              width: "100%",
            }}
          >
            <Card padding={16}>
              <span className="bd-h3">Account</span>
              <Row gap={16} style={{ marginTop: 14 }}>
                <span
                  className="bd-avatar lg"
                  style={{ overflow: "hidden", padding: 0 }}
                >
                  {avatar ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={avatar}
                      alt={login}
                      width={64}
                      height={64}
                      style={{
                        width: "100%",
                        height: "100%",
                        objectFit: "cover",
                      }}
                    />
                  ) : (
                    (login[0] ?? "?").toUpperCase()
                  )}
                </span>
                <Col gap={4} style={{ flex: 1, minWidth: 0 }}>
                  <Row gap={8}>
                    <span style={{ fontSize: 15, fontWeight: 600 }}>
                      @{login}
                    </span>
                    <Chip sm leading={<Icon name="git" size={11} />}>
                      github ✓
                    </Chip>
                  </Row>
                  <span className="bd-sm">
                    {hasLogin ? (
                      <>
                        Linked to{" "}
                        <a
                          href={`https://github.com/${login}`}
                          target="_blank"
                          rel="noreferrer"
                          style={{ color: "var(--accent)" }}
                        >
                          github.com/{login}
                        </a>
                        {joined ? ` · ${joined}` : ""}
                      </>
                    ) : (
                      "Not signed in."
                    )}
                  </span>
                </Col>
                <form action="/auth/logout" method="post" style={{ margin: 0 }}>
                  <button
                    type="submit"
                    className="bd-btn ghost"
                    style={{ cursor: "pointer" }}
                  >
                    Sign out
                  </button>
                </form>
              </Row>
            </Card>

            <div
              className="bd-card"
              style={{
                padding: 16,
                borderColor: "#fecaca",
                background: "#fef2f2",
              }}
            >
              <span className="bd-h3" style={{ color: "var(--bad)" }}>
                Danger zone
              </span>
              <Row style={{ marginTop: 12 }}>
                <Col gap={2} style={{ flex: 1 }}>
                  <span style={{ fontSize: 13.5, fontWeight: 500 }}>
                    Delete account
                  </span>
                  <span className="bd-sm">
                    Your submitted sessions stay in the archive but are
                    anonymised. This cannot be undone.
                  </span>
                </Col>
                <DeleteAccountButton />
              </Row>
            </div>
          </Col>
        </Col>
      </Row>
    </Screen>
  );
}
