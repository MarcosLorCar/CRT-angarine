export interface CameraData {
  id: string;
  name: string;
  x: number;
  y: number;
  z: number;
  isOnline: boolean;
}

export interface CameraInfo {
  pos: string;
  name: string;
  x: number;
  y: number;
  z: number;
  isOnline: boolean;
}

export interface StationInfo {
  ownerUuid: string;
  customName: string;
  pos: string;
  cameras: CameraInfo[];
}

export interface CameraRegistryUpdate {
  stations: StationInfo[];
}

export interface AuthTokenPacket {
  playerUuid: string;
  encryptedToken: string;
  assignedStations: string[];
}

export interface BlockData {
  x: number;
  y: number;
  z: number;
  stateId: number;
}

export interface TerrainFrustumPayload {
  cameraId: string;
  pitch: number;
  yaw: number;
  blocks: BlockData[];
}

export interface EntityData {
  id: string;
  type: string; // "PLAYER", "MONSTER", "PASSIVE", "ITEM"
  x: number;
  y: number;
  z: number;
  yaw: number;
  pitch: number;
}

export interface EntityDeltaStream {
  cameraId: string;
  entities: EntityData[];
}

export interface CameraStreamCommand {
  cameraId: string;
  isActive: boolean;
}

export type ModMessage =
  | { type: "me.orange.crtangarine.shared.RegistryUpdateMessage"; data: CameraRegistryUpdate }
  | { type: "me.orange.crtangarine.shared.FrustumPayloadMessage"; data: TerrainFrustumPayload }
  | { type: "me.orange.crtangarine.shared.EntityStreamMessage"; data: EntityDeltaStream };
