import type { InferredField } from "@/openapi";

export interface SchemaView {
  found: boolean;
  gameVersion: number;
  protocol: string;
  direction: string;
  packetId: number;
  sampleCount: number;
  sessionCount?: number;
  generatedLabel: string;
  fields: InferredField[];
}
