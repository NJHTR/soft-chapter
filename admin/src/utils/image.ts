/**
 * Normalize image URLs:
 * - http/https URLs pass through
 * - MinIO relative paths (douyin-video/, douyin-image/, etc.) are proxied via /api/file/url
 * - MinIO pre-signed URLs (containing :9000 or X-Amz-Signature) are also proxied
 */
export function normalizeImgUrl(url: string): string {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  if (url.startsWith('douyin-video/') || url.startsWith('douyin-image/')) {
    return '/api/file/url?path=' + encodeURIComponent(url)
  }
  if (url.includes(':9000') || url.includes('X-Amz-Signature')) {
    return '/api/file/url?path=' + encodeURIComponent(url)
  }
  // Assume relative path needs proxying
  if (!url.startsWith('/') && !url.startsWith('blob:') && !url.startsWith('data:')) {
    return '/api/file/url?path=' + encodeURIComponent(url)
  }
  return url
}

/**
 * Fallback placeholder image as inline SVG (dark theme)
 */
export const NO_COVER_SVG = 'data:image/svg+xml,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="160" height="100" fill="%231a2d4a"><rect width="160" height="100"/><text x="80" y="55" text-anchor="middle" fill="%234a5c7a" font-size="12">暂无封面</text></svg>'
)
