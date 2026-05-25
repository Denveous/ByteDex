import { getSchema, listGameVersions } from "@/lib/api";
import {
  Chip,
  Col,
  Hex,
  Row,
  Screen,
  Sidebar,
} from "@/components/primitives";

type SP = Record<string, string | undefined>;

const PROTOCOLS = ["LOGIN", "GAME", "CHAT"];
const hex2 = (n: number) => "0x" + n.toString(16).padStart(2, "0").toUpperCase();

export default async function SchemasPage({
  searchParams,
}: {
  searchParams: Promise<SP>;
}) {
  const sp = await searchParams;

  const gv = sp.gv ? Number(sp.gv) : undefined;
  const proto =
    sp.proto && PROTOCOLS.includes(sp.proto.toUpperCase())
      ? sp.proto.toUpperCase()
      : undefined;
  const dir =
    sp.dir?.toUpperCase() === "C2S" || sp.dir?.toUpperCase() === "S2C"
      ? sp.dir.toUpperCase()
      : undefined;
  const idNum = sp.id ? Number.parseInt(sp.id.replace(/^0x/i, ""), 16) : NaN;
  const packetId =
    Number.isInteger(idNum) && idNum >= 0 && idNum <= 255 ? idNum : undefined;

  const queried =
    gv != null && proto != null && dir != null && packetId != null;
  const [schema, gameVersions] = await Promise.all([
    getSchema({
      gameVersion: gv,
      protocol: proto,
      direction: dir,
      packetId,
    }),
    listGameVersions(),
  ]);

  return (
    <Screen>
      <Row style={{ flex: 1, minHeight: 0, alignItems: "stretch" }}>
        <Sidebar active="Schemas" />
        <Col style={{ flex: 1, minWidth: 0 }}>
          <Row
            style={{
              padding: "14px 24px",
              borderBottom: "1px solid var(--border)",
              background: "var(--bg-surface)",
            }}
          >
            <Col gap={2}>
              <span className="bd-h1">Schemas</span>
              <span className="bd-sm">
                Inferred structure for a packet type.
              </span>
            </Col>
            <div style={{ flex: 1 }} />
            <form
              action="/schemas"
              method="get"
              style={{ display: "flex", gap: 8, alignItems: "center" }}
            >
              <select
                name="gv"
                defaultValue={sp.gv ?? ""}
                className="bd-input mono"
                style={{ width: 130, height: 32 }}
              >
                <option value="">version</option>
                {gameVersions.map((v) => (
                  <option key={v} value={String(v)}>
                    {v}
                  </option>
                ))}
              </select>
              <select
                name="proto"
                defaultValue={proto ?? "GAME"}
                className="bd-input"
                style={{ height: 32 }}
              >
                {PROTOCOLS.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
              <select
                name="dir"
                defaultValue={dir ?? "S2C"}
                className="bd-input"
                style={{ height: 32 }}
              >
                <option value="C2S">C2S</option>
                <option value="S2C">S2C</option>
              </select>
              <input
                name="id"
                defaultValue={sp.id ?? ""}
                placeholder="packet id"
                className="bd-input mono"
                style={{ width: 110, height: 32 }}
              />
              <button
                type="submit"
                className="bd-btn primary"
                style={{ cursor: "pointer" }}
              >
                Load
              </button>
            </form>
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
            {!schema.found ? (
              <span
                className="bd-sm"
                style={{ color: "var(--fg-tertiary)", padding: "8px 2px" }}
              >
                {queried
                  ? "No inferred schema for that packet type yet."
                  : "Pick a game version, protocol, direction and packet id, then Load."}
              </span>
            ) : (
              <>
                <Row gap={10}>
                  <Hex style={{ fontSize: 18, fontWeight: 600 }}>
                    {schema.protocol}/{schema.direction}/
                    {hex2(schema.packetId)}
                  </Hex>
                  <Chip sm>v{schema.gameVersion}</Chip>
                  <div style={{ flex: 1 }} />
                  <span className="bd-xs">
                    {schema.sampleCount.toLocaleString()} samples
                    {schema.sessionCount != null
                      ? ` · ${schema.sessionCount.toLocaleString()} sessions`
                      : ""}
                    {schema.generatedLabel
                      ? ` · inferred ${schema.generatedLabel}`
                      : ""}
                  </span>
                </Row>

                <table className="bd-table">
                  <thead>
                    <tr>
                      <th style={{ width: 70 }}>offset</th>
                      <th style={{ width: 60 }}>len</th>
                      <th style={{ width: 90 }}>type</th>
                      <th>name</th>
                      <th style={{ width: 90 }}>conf</th>
                      <th style={{ width: 160 }}>constancy</th>
                      <th>notes</th>
                    </tr>
                  </thead>
                  <tbody>
                    {schema.fields.length === 0 && (
                      <tr>
                        <td
                          colSpan={7}
                          className="bd-sm"
                          style={{
                            color: "var(--fg-tertiary)",
                            padding: "16px 12px",
                          }}
                        >
                          No fields inferred yet.
                        </td>
                      </tr>
                    )}
                    {schema.fields.map((f, i) => (
                      <tr key={i}>
                        <td className="bd-mono">{f.offset}</td>
                        <td className="bd-mono">{f.length}</td>
                        <td className="bd-mono">{String(f.type)}</td>
                        <td style={{ fontWeight: 500 }}>{f.name ?? "-"}</td>
                        <td
                          className="bd-mono"
                          style={{ color: "var(--fg-tertiary)" }}
                        >
                          {Math.round((f.confidence ?? 0) * 100)}%
                        </td>
                        <td>
                          <Row gap={4} style={{ flexWrap: "wrap" }}>
                            {f.isGlobalConstant && (
                              <Chip sm>global-const</Chip>
                            )}
                            {f.isSessionConstant && !f.isGlobalConstant && (
                              <Chip sm>session-const</Chip>
                            )}
                          </Row>
                        </td>
                        <td
                          className="bd-sm"
                          style={{ color: "var(--fg-tertiary)" }}
                        >
                          {f.notes ?? ""}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </>
            )}
          </Col>
        </Col>
      </Row>
    </Screen>
  );
}
