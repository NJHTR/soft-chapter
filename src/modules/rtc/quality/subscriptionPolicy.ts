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
 * Resolve a participant's subscription without depending on LiveKit types.
 * A null visibility set deliberately means legacy/full subscription mode.
 */
export function resolveSubscriptionPolicy(
  input: SubscriptionPolicyInput
): SubscriptionPolicyDecision {
  const visible =
    input.visibleParticipantIds === null || input.visibleParticipantIds.has(input.participantId)
  const video = input.requestedVideo ?? (visible || input.activeSpeakerId === input.participantId)
  const quality =
    input.requestedVideoQuality ??
    (input.screenShare
      ? 'high'
      : input.activeSpeakerId === input.participantId
        ? 'medium'
        : 'low')

  return {
    audio: input.requestedAudio ?? true,
    video,
    quality
  }
}
