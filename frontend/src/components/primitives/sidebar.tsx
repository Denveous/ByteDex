import Link from "next/link";
import { getAuthedUser } from "@/lib/auth";
import { Icon } from "./icons";
import { Col, Row } from "./layout";

type NavItem = { id: string; icon: string; href?: string };

export const Sidebar = async ({
  active = "Sessions",
}: {
  active?: string;
}) => {
  const me = await getAuthedUser();
  const login = me?.githubLogin && me.githubLogin !== "" ? me.githubLogin : "-";
  const avatarUrl =
    me?.avatarUrl ||
    (login !== "-" ? `https://github.com/${login}.png` : null);
  const subtitle =
    me?.displayName && me.displayName !== login ? me.displayName : null;
  const items: NavItem[] = [
    { id: "Dashboard", icon: "chart", href: "/dashboard" },
    { id: "Sessions", icon: "layers", href: "/sessions" },
    { id: "Packets", icon: "inbox", href: "/packets" },
  ];
  const accountItems: NavItem[] = [
    { id: "Profile", icon: "user", href: "/profile" },
    { id: "Settings", icon: "gear", href: "/settings" },
  ];
  const renderItem = (i: NavItem) => {
    const cls = `bd-navitem ${active === i.id ? "active" : ""}`;
    const inner = (
      <>
        <span className="bd-navicon">
          <Icon name={i.icon} />
        </span>
        <span>{i.id}</span>
      </>
    );
    return i.href ? (
      <Link key={i.id} className={cls} href={i.href}>
        {inner}
      </Link>
    ) : (
      <div key={i.id} className={cls}>
        {inner}
      </div>
    );
  };
  return (
    <div className="bd-sidebar">
      <Row style={{ padding: "4px 8px 14px" }}>
        <span className="bd-logo">
          <span className="bd-logo-mark">B</span>
          ByteDex
        </span>
      </Row>
      <div className="bd-label" style={{ padding: "4px 10px" }}>
        Browse
      </div>
      {items.map(renderItem)}
      <div className="bd-label" style={{ padding: "14px 10px 4px" }}>
        Account
      </div>
      {accountItems.map(renderItem)}
      <div style={{ flex: 1 }} />
      <hr className="bd-divider" />
      <Row gap={8} style={{ padding: "10px 6px 4px" }}>
        <span
          className="bd-avatar"
          style={{ width: 28, height: 28, overflow: "hidden", padding: 0 }}
        >
          {avatarUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={avatarUrl}
              alt={login}
              width={28}
              height={28}
              style={{ width: "100%", height: "100%", objectFit: "cover" }}
            />
          ) : (
            (login[0] ?? "?").toUpperCase()
          )}
        </span>
        <Col gap={0} style={{ minWidth: 0 }}>
          <span style={{ fontSize: 13, fontWeight: 500 }}>@{login}</span>
          {subtitle && <span className="bd-xs">{subtitle}</span>}
        </Col>
        <div style={{ flex: 1 }} />
        <form
          action="/auth/logout"
          method="post"
          style={{ margin: 0, display: "flex" }}
        >
          <button
            type="submit"
            title="Sign out"
            aria-label="Sign out"
            className="bd-navitem"
            style={{
              padding: 6,
              cursor: "pointer",
              background: "none",
              border: "none",
            }}
          >
            <span className="bd-navicon">
              <Icon name="arrowRight" />
            </span>
          </button>
        </form>
      </Row>
    </div>
  );
};
