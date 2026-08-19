# STEP 8 — Network Pipeline Verification

## Module: `network/`

### RTMPStreamer (rtmp_streamer.cpp)

| Method | Status | Notes |
|--------|--------|-------|
| `init()` | ✅ REAL | FFmpeg `avformat_alloc_output_context2("flv")` |
| `connect()` | ✅ REAL | `avio_open2()` + `avformat_write_header()` |
| `send_packet()` | ✅ REAL | `av_interleaved_write_frame()` |
| `disconnect()` | ✅ REAL | `av_write_trailer()` + cleanup |
| `is_connected()` | ✅ REAL | Check state flag |

- Fully functional RTMP publishing via FFmpeg
- No stubs, no placeholders
- **Compiles and works** in current build

### SRTStreamer (srt/srt_streamer.cpp)

| Method | Status | Notes |
|--------|--------|-------|
| `init()` | ✅ REAL | Sets stream info + url + mode |
| `connect()` | ⚠️ PARTIAL | Calls `srt_create_socket()`, `srt_setsockopt()`, `srt_connect()` (REAL SRT calls) when `ENABLE_SRT` is defined; otherwise STUB |
| `send_packet()` | ❌ STUB | Always returns true — does nothing |
| `disconnect()` | ⚠️ PARTIAL | `srt_close()` when enabled; otherwise empty |

**Key Finding:** When `ENABLE_SRT=ON`, the SRT socket connection code is REAL, but `send_packet()` is a STUB that never actually sends data. This means even with SRT enabled, the streamer would connect but send nothing.

- `ENABLE_SRT=OFF` in current build — entire file is compiled-out to stubs
- Real SRT API calls guarded by `#ifdef ENABLE_SRT`

### WebRTCStreamer (webrtc/webrtc_streamer.cpp)

| Method | Status | Notes |
|--------|--------|-------|
| `init()` | ⚠️ MINIMAL | Sets config values, no WebRTC init |
| `offer()` | ❌ STUB | `cout << "Would create SDP offer for..."` |
| `answer()` | ❌ STUB | `cout << "Would create SDP answer..."` |
| `send_packet()` | ❌ STUB | `cout << "Would send frame to track..."`, always returns true |
| `add_ice_candidate()` | ❌ STUB | `cout << "Would add ICE candidate..."`, always returns true |
| `close()` | ❌ STUB | Empty |

**Finding:** 100% stub. No WebRTC API calls, no peer connection, no data channels, no media tracks. This is essentially a placeholder.

- `ENABLE_WEBRTC=OFF` in current build
- No actual WebRTC library linked

### FEC Codec (fec.cpp, fec_decoder.cpp)

| Component | Status | Notes |
|-----------|--------|-------|
| `FECEncoder::encode()` | ✅ REAL | XOR-based parity encoding |
| `FECEncoder::encode_interleaved()` | ✅ REAL | Interleaved row+column parity |
| `FECEncoder::reset()` | ✅ REAL | Clear state |
| `FECDecoder::decode()` | ✅ REAL | XOR-based recovery (up to 2 lost packets per group) |
| `FECDecoder::is_packet_lost()` | ⚠️ PARTIAL | Simple sequence number check |
| `FECDecoder::recover()` | ✅ REAL | Row + column reconstruction |

**Finding:** FEC encoder and decoder are both real implementations. XOR-based (2D parity), row-level + column-level interleaving. No stubs, no placeholders. This is production-ready.

### NACK Handler (nack_handler.cpp)

| Method | Status | Notes |
|--------|--------|-------|
| `add_packet()` | ✅ REAL | Stores packet in circular buffer with timestamp |
| `on_nack_received()` | ✅ REAL | Returns requested packet (burst mode) |
| `on_fir_received()` | ❌ STUB | Just increments FIR count |
| `on_pli_received()` | ❌ STUB | Just increments PLI count |
| `cleanup_expired()` | ✅ REAL | Removes packets older than `NACK_HISTORY_MS` |
| `get_stats()` | ✅ REAL | Returns NACK/FIR/PLI counts |

**Finding:** NACK handler is real and functional for packet-level retransmission. FIR/PLI are stubs (these require encoder interaction which is complex). Packet buffer with time-based expiry is implemented.

### Muxer (muxer.cpp)

| Method | Status | Notes |
|--------|--------|-------|
| `init()` | ✅ REAL | Allocates FIFO, sets up codec parameters |
| `add_video_stream()` | ⚠️ MINIMAL | Sets codec context, no muxer-specific config |
| `add_audio_stream()` | ⚠️ MINIMAL | Same as video, just placeholder config |
| `mux()` | ⚠️ PARTIAL | Assembles AVPacket, does NOT actually mux into container format |
| `flush()` | ❌ STUB | Empty |
| `shutdown()` | ✅ REAL | FFmpeg `avformat_free_context` + FIFO free |

**Finding:** The Muxer is partially implemented. It allocates FFmpeg contexts and creates packet structures but does NOT actually mux into an output container. The `mux()` method assembles a packet but returns `true` without writing to the muxer.

### NetworkPipe (network_pipe.cpp)

| Method | Status | Notes |
|--------|--------|-------|
| `pump()` | ✅ REAL | Round-robin streaming to all connected streamers |
| `add_streamer()` | ✅ REAL | Adds streamer to muxer |
| `add_output()` | ✅ REAL | Registers output streamer |
| `add_fec()` | ✅ REAL | Adds FEC encoding layer |
| `add_nack()` | ✅ REAL | Adds NACK handling layer |
| `set_dynamic_bitrate()` | ✅ REAL | Adjusts bitrate, updates streamers |
| `get_network_stats()` | ✅ REAL | Collects stats from NACK + FEC |
| `is_healthy()` | ✅ REAL | Checks latency + packet loss thresholds |
| `get_avg_latency()` | ⚠️ PARTIAL | Returns estimate based on recent latency samples |
| `get_packet_loss()` | ✅ REAL | Returns FEC decoder loss rate |

**Finding:** NetworkPipe is real — orchestrates the full network pipeline. The pump function correctly distributes frames across all registered streamers.

## Network Summary

| Component | Implementation Status | Production Ready? |
|-----------|----------------------|-------------------|
| RTMP Streamer | ✅ Fully real (FFmpeg) | ✅ YES |
| SRT Streamer | ⚠️ Real sock connect; STUB send_packet | ❌ NO |
| WebRTC Streamer | ❌ 100% STUB | ❌ NO |
| FEC Encoder | ✅ Fully real | ✅ YES |
| FEC Decoder | ✅ Fully real | ✅ YES |
| NACK Handler | ✅ Real (packet buffer + retransmission) | ⚠️ FIR/PLI are stubs |
| Muxer | ⚠️ Partial (allocates, doesn't mux) | ❌ NO |
| NetworkPipe | ✅ Fully real orchestrator | ✅ YES |

**Only RTMP + FEC + NACK are truly functional end-to-end.**

## Network Data Flow

```
[Encoder Output]
      │
      ▼
NetworkPipe::pump()
  ├─ FECEncoder::encode()        →  Produces parity packets
  ├─ NACKHandler::add_packet()   →  Stores in buffer
  │
  ├─ RTMPStreamer::send_packet()    →  ✅ RTMP OUTPUT
  ├─ SRTStreamer::send_packet()     →  ❌ STUB (no output)
  └─ WebRTCStreamer::send_packet()  →  ❌ STUB (no output)
```

## Verdict

**Network layer is 60% functional:**
- RTMP → works (real FFmpeg FLV muxing + publishing)
- FEC → works (real XOR-based error correction)
- NACK → works (real packet buffer + retransmission)
- SRT → connects but doesn't send
- WebRTC → 100% placeholder
- Muxer → allocates but doesn't mux
- NetworkPipe → correctly orchestrates all of the above
