export type RtcRemoteVideoQuality = 'high' | 'medium' | 'low'

export interface SubscriptionPolicyInput {
  participantId: string
  visibleParticipantIds: ReadonlySet<string> | null
  activeSpeakerId: string | null
  screenShare: boolean
  requestedVideoQuality?: RtcRemoteVideoQuality
  requestedVideo?: boolean
  requestedAudio?: boolean
}

export interface SubscriptionPolicyDecision {
  audio: boolean
  video: boolean
  quality: RtcRemoteVideoQuality
}

/**
 * RTC-012 选择性订阅决策，不依赖 LiveKit 类型。
 *
 * - null 可见集合 = 旧版全订阅兼容模式（1 对 1 基线不变）
 * - 屏幕共享恒为 HIGH 且优先于摄像头
 * - active speaker / 主画面为 HIGH
 * - 普通可见 tile 默认 MEDIUM
 * - 弱网或人数策略只能通过 requestedVideoQuality 显式降级（LOW）
 * - 播放条件：可见 或 屏幕共享 或 active speaker；否则只有音频
 */
export function resolveSubscriptionPolicy(
  input: SubscriptionPolicyInput
): SubscriptionPolicyDecision {
  const visible =
    input.visibleParticipantIds === null || input.visibleParticipantIds.has(input.participantId)
  const video =
    input.requestedVideo ??
    (input.screenShare || visible || input.activeSpeakerId === input.participantId)
  const quality =
    input.requestedVideoQuality ??
    (input.screenShare || input.activeSpeakerId === input.participantId ? 'high' : 'medium')

  return {
    audio: input.requestedAudio ?? true,
    video,
    quality
  }
}
