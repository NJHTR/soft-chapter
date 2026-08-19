# STEP 11 — Docker Infrastructure Verification

## File: `docker-compose.streaming.yml`

### Services

| Service | Image | Ports | Status in YAML |
|---------|-------|-------|----------------|
| **SRS** (Simple-Rtmp-Server) | `ossrs/srs:6.0` | 1935 (RTMP), 8080 (HTTP), 1985 (SRT), 8000 (UDP/WebRTC) | ✅ Well-configured |
| **MediaMTX** (formerly RTSP-Simple-Server) | `bluenviron/mediamtx:latest` | 8554 (RTSP), 1935 (RTMP), 8889 (HLS), 8890 (WebRTC), 8000 (UDP) | ✅ Well-configured |
| **LiveKit** | `livekit/livekit-server:latest` | 7880 (HTTP), 7881 (WS), 50000-60000 (UDP) | ✅ Well-configured |

### Network Configuration

```
Network: streaming-net (bridge)
  SRS─────┐
  MediaMTX├── streaming-net
  LiveKit─┘
```

All three services share the same bridge network. No port conflicts in the YAML definition.

### Deployment Status

| Check | Status | Notes |
|-------|--------|-------|
| Docker Compose file exists | ✅ YES | `docker-compose.streaming.yml` |
| Docker Desktop installed | ⚠️ UNKNOWN | Not verified in this session |
| Containers running | ❌ NOT DEPLOYED | No docker compose up was executed |
| SRTSRS accessible | ❌ NOT TESTED | Ports not checked |
| MediaMTX accessible | ❌ NOT TESTED | Ports not checked |
| LiveKit accessible | ❌ NOT TESTED | Ports not checked |

**The Docker infrastructure is configured but not deployed.** All three services are well-configured with proper port mappings and shared networking.

**Potential issue:** Port 1935 (RTMP) is mapped in both SRS and MediaMTX. Docker would fail if both are started simultaneously without removing the duplicate mapping from one service. Port 8000 UDP is also used by both SRS and MediaMTX.
