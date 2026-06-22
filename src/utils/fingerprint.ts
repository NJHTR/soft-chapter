/**
 * 浏览器指纹 — 未登录用户标识。
 *
 * 技术栈 (业界标准方案, 参考 FingerprintJS):
 *   - Canvas 指纹: 不同 GPU/驱动渲染结果有微小差异
 *   - WebGL 指纹: GPU 型号 + 厂商字符串
 *   - AudioContext 指纹: 音频处理栈的硬件差异
 *   - 字体检测: 已安装字体集合
 *   - 浏览器基础属性: UA / 语言 / 分辨率 / 时区 / CPU 核数
 *
 * 所有信号合并后 SHA-256 哈希, 写入 localStorage 缓存。
 * 稳定性: 同一设备同一浏览器不清除站点数据则不变。
 */

const HEX_DIGITS = '0123456789abcdef'

/** Canvas 指纹: 用不同字体和颜色绘制文字+形状, 提取像素数据 */
function canvasFingerprint(): string {
  try {
    const canvas = document.createElement('canvas')
    canvas.width = 280
    canvas.height = 60
    const ctx = canvas.getContext('2d')
    if (!ctx) return ''
    // 背景
    ctx.fillStyle = '#f3f3f3'
    ctx.fillRect(0, 0, 280, 60)
    // 多层文字渲染 (不同字体 → 不同光栅化结果)
    ctx.fillStyle = '#069'
    ctx.font = '14px Arial'
    ctx.fillText('BrowserFingerprint 浏览器指纹', 10, 20)
    ctx.fillStyle = '#c00'
    ctx.font = 'bold 12px "Times New Roman"'
    ctx.fillText('SeekFlow 视频平台', 15, 38)
    ctx.fillStyle = '#0a0'
    ctx.font = 'italic 11px "Courier New"'
    ctx.fillText('Canvas GPU Render', 20, 55)
    // 小圆弧 (不同 GPU 反锯齿差异)
    ctx.beginPath()
    ctx.arc(260, 30, 8, 0, Math.PI * 2)
    ctx.fillStyle = 'rgba(100,149,237,0.5)'
    ctx.fill()
    const dataUrl = canvas.toDataURL()
    // 只取像素数据部分, 去掉 data URL 头
    const base64 = dataUrl.split(',')[1] || ''
    return base64.slice(0, 256) // 前256字符足够区分
  } catch {
    return ''
  }
}

/** WebGL 指纹: GPU 渲染器字符串 */
function webglFingerprint(): string {
  try {
    const canvas = document.createElement('canvas')
    const gl =
      canvas.getContext('webgl') ||
      (canvas.getContext('experimental-webgl') as WebGLRenderingContext | null)
    if (!gl) return ''
    const dbgRenderInfo = gl.getExtension('WEBGL_debug_renderer_info')
    if (!dbgRenderInfo) return ''
    const vendor = gl.getParameter(dbgRenderInfo.UNMASKED_VENDOR_WEBGL) || ''
    const renderer = gl.getParameter(dbgRenderInfo.UNMASKED_RENDERER_WEBGL) || ''
    return vendor + '|' + renderer
  } catch {
    return ''
  }
}

/** AudioContext 指纹: 振荡器 → 压缩器 → 缓冲区 的硬件差异 */
async function audioFingerprint(): Promise<string> {
  try {
    const ctx = new (window.OfflineAudioContext || (window as any).webkitOfflineAudioContext)(
      1,
      44100,
      44100
    )
    const osc = ctx.createOscillator()
    osc.type = 'triangle'
    osc.frequency.value = 10000
    const compressor = ctx.createDynamicsCompressor()
    compressor.threshold.value = -50
    compressor.knee.value = 40
    compressor.ratio.value = 12
    compressor.attack.value = 0
    compressor.release.value = 0.25
    osc.connect(compressor)
    compressor.connect(ctx.destination)
    osc.start(0)
    const buf = await ctx.startRendering()
    const data = buf.getChannelData(0)
    let sum = 0
    for (let i = 0; i < data.length; i += 1000) {
      sum += Math.abs(data[i])
    }
    return sum.toFixed(10)
  } catch {
    return ''
  }
}

/** 字体检测: 用已知字体列表测哪些可用 */
function fontFingerprint(): string {
  try {
    const testFonts = [
      'Arial',
      'Verdana',
      'Times New Roman',
      'Courier New',
      'Georgia',
      'Comic Sans MS',
      'Trebuchet MS',
      'Impact',
      'Microsoft YaHei',
      'SimSun',
      'SimHei',
      'KaiTi',
      'FangSong'
    ]
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')
    if (!ctx) return ''
    const available: string[] = []
    const testText = 'mmmmmmmmmwwwwwww 测试字体'
    const baseWidth = ctx.measureText(testText).width
    for (const font of testFonts) {
      ctx.font = `16px "${font}", sans-serif`
      const w = ctx.measureText(testText).width
      // 不同字体渲染宽度不同
      if (Math.abs(w - baseWidth) > 0.5 || available.length === 0) {
        available.push(font)
      }
    }
    return available.join(',')
  } catch {
    return ''
  }
}

/** 浏览器基础属性 */
function basicSignals(): string {
  return [
    navigator.userAgent,
    navigator.language,
    screen.width + 'x' + screen.height + 'x' + (screen.colorDepth || 0),
    new Date().getTimezoneOffset(),
    navigator.hardwareConcurrency || 'unknown',
    navigator.platform || 'unknown',
    navigator.maxTouchPoints || 0,
    // 某些浏览器独有特征
    'doNotTrack' in navigator ? '1' : '0'
  ].join('|')
}

// ─── 主入口 ──────────────────────────────────────────────

export async function getBrowserFingerprint(): Promise<string> {
  const cacheKey = '_dyn_fp_v2'
  const cached = localStorage.getItem(cacheKey)
  if (cached) return cached

  // Canvas / WebGL / 字体 是同步的
  const parts = [basicSignals(), canvasFingerprint(), webglFingerprint(), fontFingerprint()]

  // AudioContext 是异步的
  try {
    parts.push(await audioFingerprint())
  } catch {
    parts.push('')
  }

  const raw = parts.join('|')

  // SHA-256 via Web Crypto (异步)
  let hash: string
  try {
    const encoder = new TextEncoder()
    const data = encoder.encode(raw)
    const digest = await crypto.subtle.digest('SHA-256', data)
    const hex = Array.from(new Uint8Array(digest))
      .map((b) => HEX_DIGITS[b >> 4] + HEX_DIGITS[b & 15])
      .join('')
    hash = 'fp_' + hex.slice(0, 16)
  } catch {
    // fallback: 简易 djb2 hash (crypto.subtle 在非 HTTPS 下不可用)
    let h = 0
    for (let i = 0; i < raw.length; i++) {
      h = (h << 5) - h + raw.charCodeAt(i)
      h |= 0
    }
    hash = 'fp_' + Math.abs(h).toString(36)
  }

  localStorage.setItem(cacheKey, hash)
  return hash
}
