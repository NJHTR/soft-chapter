/**
 * 聊天消息本地持久化 —— 打开会话秒出，后台静默刷新
 */

const CACHE_PREFIX = 'douyin_chat_';
const MAX_MESSAGES_PER_CHAT = 100;
const MAX_CHATS = 30; // 最多缓存30个会话

interface StoredChat {
  messages: any[];
  updatedAt: number;
}

/** 清理旧会话缓存 (保留最近 30 个) */
function pruneChats() {
  const keys: { key: string; time: number }[] = [];
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    if (key && key.startsWith(CACHE_PREFIX)) {
      try {
        const data = JSON.parse(localStorage.getItem(key) || '{}');
        keys.push({ key, time: data.updatedAt || 0 });
      } catch {}
    }
  }
  keys.sort((a, b) => b.time - a.time);
  for (const k of keys.slice(MAX_CHATS)) {
    localStorage.removeItem(k.key);
  }
}

export function getCachedMessages(partnerUserId: number | string): any[] | null {
  try {
    const raw = localStorage.getItem(CACHE_PREFIX + partnerUserId);
    if (!raw) return null;
    const data: StoredChat = JSON.parse(raw);
    return data.messages || null;
  } catch {
    return null;
  }
}

export function setCachedMessages(partnerUserId: number | string, messages: any[]) {
  try {
    const stored: StoredChat = {
      messages: messages.slice(-MAX_MESSAGES_PER_CHAT),
      updatedAt: Date.now()
    };
    localStorage.setItem(CACHE_PREFIX + partnerUserId, JSON.stringify(stored));
    pruneChats();
  } catch {
    // localStorage 满了或不可用
  }
}

/** 追加新消息到缓存 (不覆盖全部) */
export function appendCachedMessage(partnerUserId: number | string, message: any) {
  try {
    const existing = getCachedMessages(partnerUserId) || [];
    existing.push(message);
    setCachedMessages(partnerUserId, existing);
  } catch {}
}

/** 删除指定会话的缓存 */
export function clearCachedChat(partnerUserId: number | string) {
  localStorage.removeItem(CACHE_PREFIX + partnerUserId);
}
