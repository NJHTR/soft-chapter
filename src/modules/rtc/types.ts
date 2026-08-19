export type CallPhase =
  | 'idle'
  | 'dialing'
  | 'ringingIn'
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'ended'

export type CallMode = 'audio' | 'video'

export interface CallSession {
  call_id: string
  room_id: string
  scope: 'direct' | 'group'
  mode: CallMode
  initiator_id: string | number
  provider: string
  state: string
  connected_at: string | null
  ended_at: string | null
  end_reason: string | null
  [key: string]: any
}

export interface RtcParticipant {
  userId: string
  role: string
  state: string
}

/** 后端 detail.participants 的原始 snake_case 结构 */
export interface RtcParticipantRaw {
  user_id: string | number
  role?: string
  state?: string
  joined_at?: string | null
  left_at?: string | null
  [key: string]: any
}

export interface RtcCallEvent {
  kind: string
  seq: number
  occurred_at: string
  payload: any
}

export interface CallDetail {
  call: CallSession
  participants: RtcParticipantRaw[]
  events: RtcCallEvent[]
}

export interface DeviceStates {
  audioMuted: boolean
  videoOff: boolean
  backgroundRemoved: boolean
  speakerOn: boolean
  activeInputId: string | null
  activeOutputId: string | null
}

export interface IncomingCall {
  callId: string
  fromUserId: string
  name: string
  avatar: string
  mode: CallMode
  isGroup?: boolean
  groupMembers?: string[]
}

export interface OutgoingMeta {
  toUserId?: string
  name: string
  avatar: string
  isVideo: boolean
}

export interface GroupCallMeta {
  groupId: string
  members: Array<{ userId: string; name: string; avatar: string }>
  isVideo: boolean
}

export const TERMINAL_CALL_STATES = ['REJECTED', 'CANCELLED', 'EXPIRED', 'FAILED', 'ENDED']

export const ACCEPTING_CALL_STATES = ['ACCEPTED', 'NEGOTIATING']
