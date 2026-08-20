import { describe, expect, it } from 'vitest'
import {
  deriveProbeOutcome,
  isBudgetValid,
  isProbeExpired,
  nextTopology,
  probeDeadline
} from '../p2pMachine'

describe('p2pMachine.nextTopology', () => {
  it('mirrors the contract lifecycle', () => {
    expect(nextTopology('DISABLED', 'EVALUATE')).toBe('ELIGIBLE')
    expect(nextTopology('ELIGIBLE', 'CONSENT')).toBe('CONSENTED')
    expect(nextTopology('CONSENTED', 'PROBE_START')).toBe('PROBING')
    expect(nextTopology('PROBING', 'PROBE_PASS')).toBe('P2P_CONNECTED')
    expect(nextTopology('PROBING', 'PROBE_FAIL')).toBe('FALLING_BACK')
  })

  it('never silently switches topology once connected', () => {
    expect(nextTopology('P2P_CONNECTED', 'FALLBACK')).toBe('P2P_CONNECTED')
    expect(nextTopology('SFU_CONNECTED', 'PROBE_START')).toBe('SFU_CONNECTED')
    expect(nextTopology('FAILED', 'FALLBACK')).toBe('FAILED')
  })

  it('revoke during probing falls back', () => {
    expect(nextTopology('PROBING', 'REVOKE')).toBe('FALLING_BACK')
    expect(nextTopology('CONSENTED', 'REVOKE')).toBe('ELIGIBLE')
  })
})

describe('p2pMachine.candidate pair policy', () => {
  it('host/host is direct', () => {
    expect(deriveProbeOutcome('host', 'host')).toBe('direct')
  })

  it('any srflx/prflx derives srflx', () => {
    expect(deriveProbeOutcome('host', 'srflx')).toBe('srflx')
    expect(deriveProbeOutcome('prflx', 'host')).toBe('srflx')
  })

  it('any relay or unknown fails closed to relay', () => {
    expect(deriveProbeOutcome('host', 'relay')).toBe('relay')
    expect(deriveProbeOutcome(undefined, 'host')).toBe('relay')
    expect(deriveProbeOutcome('webrtc', 'host')).toBe('relay')
  })
})

describe('p2pMachine.probe budget', () => {
  it('budget must stay in 1500..3000 ms', () => {
    expect(isBudgetValid(2000)).toBe(true)
    expect(isBudgetValid(1500)).toBe(true)
    expect(isBudgetValid(3000)).toBe(true)
    expect(isBudgetValid(500)).toBe(false)
    expect(isBudgetValid(5000)).toBe(false)
  })

  it('deadline math and expiry', () => {
    const deadline = probeDeadline(1000, 2000)
    expect(deadline).toBe(3000)
    expect(isProbeExpired(2999, deadline)).toBe(false)
    expect(isProbeExpired(3000, deadline)).toBe(true)
  })
})
