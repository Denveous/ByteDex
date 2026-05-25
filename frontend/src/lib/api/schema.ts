import type { Direction, Protocol } from "@/openapi";
import { authedApi } from "@/lib/auth";
import type { SchemaView } from "./types";

export interface SchemaQuery {
  gameVersion?: number;
  protocol?: string;
  direction?: string;
  packetId?: number;
}

export async function getSchema(q: SchemaQuery): Promise<SchemaView> {
  const empty: SchemaView = {
    found: false,
    gameVersion: q.gameVersion ?? 0,
    protocol: q.protocol ?? "",
    direction: q.direction ?? "",
    packetId: q.packetId ?? 0,
    sampleCount: 0,
    generatedLabel: "",
    fields: [],
  };

  const { gameVersion, protocol, direction, packetId } = q;
  if (gameVersion == null || !protocol || !direction || packetId == null) {
    return empty;
  }
  const api = await authedApi();
  if (!api) return empty;

  try {
    const { data: s } = await api.schemas.getSchema({
      gameVersion,
      protocol: protocol as Protocol,
      direction: direction as Direction,
      packetId,
    });
    return {
      found: true,
      gameVersion: s.gameVersion,
      protocol: String(s.protocol),
      direction: String(s.direction),
      packetId: s.packetId,
      sampleCount: s.sampleCount,
      sessionCount: s.sessionCount,
      generatedLabel: s.generatedAt
        ? new Date(s.generatedAt).toLocaleString()
        : "",
      fields: s.fields ?? [],
    };
  } catch {
    return empty;
  }
}
