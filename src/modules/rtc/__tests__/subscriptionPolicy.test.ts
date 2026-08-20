import { describe, expect, it } from 'vitest'
import {
  resolveSubscriptionPolicy,
  type SubscriptionPolicyInput
} from '../quality/subscriptionPolicy'

function decision(input: Partial<SubscriptionPolicyInput> & { participantId: string }) {
  return resolveSubscriptionPolicy({
    visibleParticipantIds: null,
    activeSpeakerId: null,
    screenShare: false,
    ...input
  })
}

describe('resolveSubscriptionPolicy (RTC-012)', () => {
  it('keeps legacy full-subscription when visibility set is null', () => {
    const out = decision({ participantId: 'p1' })
    expect(out.video).toBe(true)
    expect(out.audio).toBe(true)
    expect(out.quality).toBe('medium')
  })

  it('visible tile defaults to MEDIUM video', () => {
    const out = decision({
      participantId: 'p1',
      visibleParticipantIds: new Set(['p1', 'p2']),
      activeSpeakerId: 'p2'
    })
    expect(out.video).toBe(true)
    expect(out.quality).toBe('medium')
  })

  it('active speaker gets HIGH quality', () => {
    const out = decision({
      participantId: 'p2',
      visibleParticipantIds: new Set(['p1', 'p2', 'p3']),
      activeSpeakerId: 'p2'
    })
    expect(out.video).toBe(true)
    expect(out.quality).toBe('high')
  })

  it('screen share is HIGH even for a hidden participant', () => {
    const out = decision({
      participantId: 'p3',
      visibleParticipantIds: new Set(['p1', 'p2']),
      activeSpeakerId: 'p2',
      screenShare: true
    })
    expect(out.video).toBe(true)
    expect(out.quality).toBe('high')
  })

  it('hidden non-active participant drops video but keeps audio', () => {
    const out = decision({
      participantId: 'p4',
      visibleParticipantIds: new Set(['p1', 'p2', 'p3']),
      activeSpeakerId: 'p2'
    })
    expect(out.video).toBe(false)
    expect(out.audio).toBe(true)
  })

  it('explicit requestedVideo overrides visibility', () => {
    const out = decision({
      participantId: 'p4',
      visibleParticipantIds: new Set(['p1', 'p2', 'p3']),
      activeSpeakerId: 'p2',
      requestedVideo: true
    })
    expect(out.video).toBe(true)
  })

  it('weak-network demotion only via explicit LOW request', () => {
    const out = decision({
      participantId: 'p1',
      visibleParticipantIds: new Set(['p1']),
      requestedVideoQuality: 'low'
    })
    expect(out.quality).toBe('low')
  })

  it('requestedAudio false keeps video policy unchanged', () => {
    const out = decision({
      participantId: 'p1',
      visibleParticipantIds: new Set(['p1']),
      requestedAudio: false
    })
    expect(out.audio).toBe(false)
    expect(out.video).toBe(true)
  })
})
