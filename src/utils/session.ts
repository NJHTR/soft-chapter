/** 浏览会话 ID — 页面级生命周期，刷新后重新生成 */
let browsingSessionId: string | null = null

export function getBrowsingSessionId(): string {
  if (!browsingSessionId) {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
      browsingSessionId = crypto.randomUUID()
    } else {
      browsingSessionId = Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
    }
  }
  return browsingSessionId
}
