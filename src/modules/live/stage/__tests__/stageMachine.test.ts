import { describe, expect, it } from 'vitest'
import { applyStageEvent, isStagePublishGranted, nextStatus } from '../stageMachine'

function event(
  over: Partial<{ command: string; userId: number; generation: number; eventId: string }> = {}
) {
  return {
    command: 'REQUEST',
    liveId: 7,
    userId: 1,
    eventId: 'e1',
    generation: 0,
    arrivedAtMs: 1000,
    ...over
  }
}

describe('stageMachine.nextStatus', () => {
  it('applies the contract lifecycle from REQUEST to ON_STAGE and back', () => {
    const path = ['REQUEST', 'APPROVE', 'JOINED', 'DEMOTE', 'LEFT'].reduce(
      (acc, c) => {
        acc.push(nextStatus(acc[acc.length - 1], c))
        return acc
      },
      ['AUDIENCE']
    )
    expect(path).toEqual(['AUDIENCE', 'REQUESTED', 'PROMOTING', 'ON_STAGE', 'DEMOTING', 'AUDIENCE'])
  })

  it('revoke diverges from any controlled state into REVOKED (terminal)', () => {
    expect(nextStatus('REQUESTED', 'REVOKE')).toBe('REVOKING')
    expect(nextStatus('ON_STAGE', 'REVOKE')).toBe('REVOKING')
    expect(nextStatus('REVOKING', 'REVOKE')).toBe('REVOKING')
    expect(nextStatus('REVOKED', 'APPROVE')).toBe('REVOKED')
  })

  it('unknown transition keeps current status', () => {
    expect(nextStatus('AUDIENCE', 'JOINED')).toBe('AUDIENCE')
    expect(nextStatus('ON_STAGE', 'REQUEST')).toBe('ON_STAGE')
  })

  it('publish grant is exclusivly ON_STAGE', () => {
    expect(isStagePublishGranted('ON_STAGE')).toBe(true)
    expect(isStagePublishGranted('PROMOTING')).toBe(false)
    expect(isStagePublishGranted('REVOKED')).toBe(false)
  })
})

describe('stageMachine.applyStageEvent', () => {
  it('replays the same event_id instead of re-requesting', () => {
    const first = applyStageEvent(undefined, event())
    expect(first.applied).toBe(true)
    if (first.applied) {
      expect(first.member.status).toBe('REQUESTED')
      const replay = applyStageEvent(first.member, event())
      expect(replay).toEqual({ applied: false, reason: 'replay', member: first.member })
    }
  })

  it('rejects stale generations (out-of-order events must not resurrect a member)', () => {
    const first = applyStageEvent(undefined, event({ generation: 0 }))
    if (!first.applied) throw new Error('expected applied')
    const stale = applyStageEvent(
      first.member,
      event({ command: 'JOINED', generation: 0, eventId: 'e9' })
    )
    expect(stale).toEqual({ applied: false, reason: 'stale', member: first.member })
  })

  it('applies the approve->joined sequence and toggles mayPublish', () => {
    const req = applyStageEvent(undefined, event())
    if (!req.applied) throw new Error('expected applied')
    const app = applyStageEvent(
      req.member,
      event({ command: 'APPROVE', eventId: 'e2', generation: 1 })
    )
    if (!app.applied) throw new Error('expected applied')
    expect(app.member.status).toBe('PROMOTING')
    expect(app.member.mayPublish).toBe(false)
    const join = applyStageEvent(
      app.member,
      event({ command: 'JOINED', eventId: 'e3', generation: 2 })
    )
    if (!join.applied) throw new Error('expected applied')
    expect(join.member.status).toBe('ON_STAGE')
    expect(join.member.mayPublish).toBe(true)
  })

  it('mirrors revoke to terminal REVOKED with publish revoked', () => {
    const seq = applyStageEvent(undefined, event())
    if (!seq.applied) throw new Error('expected applied')
    const on = applyStageEvent(
      seq.member,
      event({ command: 'JOINED', eventId: 'e2', generation: 1 })
    )
    if (!on.applied) throw new Error('expected applied')
    const rev = applyStageEvent(
      on.member,
      event({ command: 'REVOKE', eventId: 'e4', generation: 2 })
    )
    if (!rev.applied) throw new Error('expected applied')
    expect(rev.member.status).toBe('REVOKING')
    expect(rev.member.mayPublish).toBe(false)
  })
})
