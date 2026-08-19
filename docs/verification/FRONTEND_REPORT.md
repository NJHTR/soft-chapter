# STEP 10 — Frontend Verification

## Module: `src/utils/streaming/`

### webcodecs_player.ts

| Function | Status | Notes |
|----------|--------|-------|
| `getSupportedCodecs()` | ✅ REAL | Enumerates VideoDecoder.isConfigSupported for 3 codecs |
| `requestVideoFrameCallback()` | ✅ REAL | Modern rVFC with fallback to rAF |
| `playVideoFrame()` | ⚠️ PARTIAL | Frame scheduling with jitter buffer |

**Critical Bug — Line 154:**
```typescript
const supported = await VideoDecoder.isConfigSupported(config);
// BUG: No assignment of the awaited result below
```

**Actual code (lines 148–158):**
```typescript
const codecsToTry = ['av01.0.05M.08', 'vp09.00.10.08', 'avc1.64001e'];
let selectedCodec = codecsToTry[0];  // Always av01.0.05M.08

for (const codec of codecsToTry) {
    const config = { codec, codedWidth: 1920, codedHeight: 1080 };
    const supported = await VideoDecoder.isConfigSupported(config);
    // Bug: 'supported' is assigned but never used for selection
}
```

**The loop checks all 3 codecs but always uses `codecsToTry[0]` (av01).** Even if AV1 is not supported, it's still selected. The `supported` variable is never used.

**Intended behavior:** Should fall back through AV1 → VP9 → H.264 based on `supported.supported`.

### webrtc_client.ts

| Method | Status | Notes |
|--------|--------|-------|
| `connect()` | ⚠️ PARTIAL | Creates RTCPeerConnection, sets up transceivers |
| `disconnect()` | ✅ REAL | Close peer connection |
| `createOffer()` | ⚠️ PARTIAL | Basic offer creation, sends to server |
| `handleAnswer()` | ✅ REAL | Sets remote description |
| `addIceCandidate()` | ✅ REAL | ICE candidate handling |
| `onIceCandidate()` | ⚠️ PARTIAL | Sends ICE candidate to server |

- Functional WebRTC client with basic signaling
- No stats reporting (`getStats()` not called)
- No reconnection logic
- No simulcast support

## Module: `src/views/live/`

### LiveCreate.vue

| Feature | Status | Notes |
|---------|--------|-------|
| Device selection | ✅ REAL | Lists camera/mic devices |
| Resolution/bitrate config | ✅ REAL | UI controls |
| Preview | ✅ REAL | Local preview via `<video>` |
| WebCodecs recording | ✅ REAL | MediaStreamRecorder + WebCodecs encoder |
| RTMP push | ⚠️ PARTIAL | Experimental — tries WebSocket proxy |
| WebRTC push | ⚠️ PARTIAL | via webrtc_client.ts |
| Beauty controls | ✅ REAL | UI sliders + toggles |
| AI Denoise toggle | ✅ REAL | Checkbox |
| Start/Stop streaming | ✅ REAL | Backend API calls |

### LiveWatch.vue

| Feature | Status | Notes |
|---------|--------|-------|
| WebCodecs player | ⚠️ PARTIAL | Uses webcodecs_player.ts (codec bug) |
| MSE/HLS fallback | ✅ REAL | `<video>` src fallback |
| WebSocket streaming | ✅ REAL | Binary frame WebSocket |
| Stats display | ✅ REAL | FPS, bitrate, latency |
| Transport selector | ✅ REAL | WebRTC / WebSocket / MSE toggle |

## Frontend Summary

| Component | Status | Notes |
|-----------|--------|-------|
| WebCodecs Player | ⚠️ Bug in codec selection | Always selects AV1 |
| WebRTC Client | ⚠️ Partial | Needs reconnection, stats |
| LiveCreate UI | ✅ Good | Full controls |
| LiveWatch UI | ✅ Good | Multi-transport viewer |
| Beauty Settings | ✅ Real | Working sliders |
| Device Selection | ✅ Real | enumerateDevices |
| WebSocket Streaming | ✅ Real | Binary frame transport |

**Critical Bug:** `webcodecs_player.ts` line 154 — `supported` result from `isConfigSupported()` is never used. Codec always defaults to AV1, which may fail on older browsers.
