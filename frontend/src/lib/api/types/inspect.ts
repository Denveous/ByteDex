export interface InspectFieldView {
  offset: number;
  length: number;
  type: string;
  name: string;
  confidence: number;
  value: string;
  fieldNote?: string;
  paletteIdx: number;
}

export interface InspectByteView {
  hex: string;
  paletteIdx: number; // -1 = not covered by an inferred field
}

export interface InspectLineView {
  offset: string;
  bytes: InspectByteView[];
  ascii: string;
}

export interface NeighborView {
  id: string;
  packetIdHex: string;
  protocol: string;
  direction: string;
  payloadSize: number;
  relLabel: string;
  current: boolean;
}

export interface InspectView {
  found: boolean;
  packetUuid: string;
  sessionId: string;
  sessionShortId: string;
  protocol: string;
  direction: string;
  packetIdHex: string;
  versionLabel: string;
  payloadSizeLabel: string;
  capturedLabel: string;
  lines: InspectLineView[];
  neighbors: NeighborView[];
}
