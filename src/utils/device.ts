/**
 * 收集浏览器设备信息，用于登录设备记录和安全风控
 */
export interface DeviceInfo {
  screen: string       // 屏幕分辨率 e.g. "1920x1080"
  language: string     // 浏览器语言 e.g. "zh-CN"
  timezone: string     // 时区 e.g. "Asia/Shanghai"
  platform: string     // 平台 e.g. "Win32"
  cores: number        // CPU核心数
  memory?: number      // 设备内存(GB, Chrome only)
  colorDepth: number   // 色深
  pixelRatio: number   // 设备像素比
  gpu: string          // GPU渲染器信息
  touchPoints: number  // 触屏点数(0=非触屏)
  connection?: string  // 网络类型
}

export function collectDeviceInfo(): DeviceInfo {
  const w = window
  const s = w.screen
  const n = w.navigator as any

  return {
    screen: `${s.width}x${s.height}`,
    language: n.language || n.userLanguage || 'unknown',
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'unknown',
    platform: n.platform || 'unknown',
    cores: n.hardwareConcurrency || 0,
    memory: n.deviceMemory || undefined,
    colorDepth: s.colorDepth || 0,
    pixelRatio: w.devicePixelRatio || 1,
    gpu: getGpuRenderer(),
    touchPoints: n.maxTouchPoints || 0,
    connection: (n.connection || n.mozConnection || n.webkitConnection)?.effectiveType || undefined
  }
}

function getGpuRenderer(): string {
  try {
    const canvas = document.createElement('canvas')
    const gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl') as any
    if (gl) {
      const dbg = gl.getExtension('WEBGL_debug_renderer_info')
      if (dbg) return gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL) || ''
    }
  } catch {}
  return ''
}

/** 收集设备信息并编码为 HTTP Header 值 */
export function deviceInfoToHeader(): string {
  return encodeURIComponent(JSON.stringify(collectDeviceInfo()))
}
