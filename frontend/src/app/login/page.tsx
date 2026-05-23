import { Btn, Card, Col, Screen } from "@/components/primitives";
import { githubLoginUrl } from "@/lib/auth";

export const dynamic = "force-dynamic";

export default function LoginPage() {
  return (
    <Screen
      style={{
        background: "var(--bg-page)",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <Col gap={28} style={{ width: 380, alignItems: "stretch" }}>
        <Col gap={6} style={{ alignItems: "center" }}>
          <div
            style={{
              width: 48,
              height: 48,
              borderRadius: 12,
              background: "var(--fg-primary)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: "var(--fg-inverse)",
              fontFamily: "var(--font-mono)",
              fontWeight: 600,
              fontSize: 22,
              boxShadow: "var(--shadow-md)",
            }}
          >
            B
          </div>
          <span
            style={{
              fontSize: 20,
              fontWeight: 600,
              letterSpacing: "-0.01em",
              marginTop: 6,
            }}
          >
            Sign in to ByteDex
          </span>
        </Col>

        <Card padding={28} style={{ boxShadow: "var(--shadow-md)" }}>
          <a
            href={githubLoginUrl()}
            style={{ textDecoration: "none", display: "block" }}
          >
            <Btn
              primary
              lg
              block
              style={{ height: 42, fontSize: 14 }}
              leading={
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 16 16"
                  fill="currentColor"
                  aria-hidden="true"
                >
                  <path
                    fillRule="evenodd"
                    d="M8 .2a8 8 0 0 0-2.5 15.6c.4.1.5-.2.5-.4v-1.4c-2.2.5-2.7-1.1-2.7-1.1-.4-1-.9-1.2-.9-1.2-.7-.5.1-.5.1-.5.8.1 1.2.8 1.2.8.7 1.2 1.9.9 2.4.7.1-.5.3-.9.5-1.1-1.8-.2-3.6-.9-3.6-4 0-.9.3-1.6.8-2.2-.1-.2-.4-1 .1-2.1 0 0 .7-.2 2.2.8a7.5 7.5 0 0 1 4 0c1.5-1 2.2-.8 2.2-.8.4 1.1.2 1.9.1 2.1.5.6.8 1.3.8 2.2 0 3.1-1.9 3.8-3.6 4 .3.2.6.7.6 1.4v2c0 .2.1.5.6.4A8 8 0 0 0 8 .2"
                  />
                </svg>
              }
            >
              Continue with GitHub
            </Btn>
          </a>
        </Card>
      </Col>
    </Screen>
  );
}
