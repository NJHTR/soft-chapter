/**
 * 通用 LRU 缓存 + TTL 过期
 * 适合: API 响应缓存、视频列表缓存、用户资料缓存
 */
export class LRUCache<K, V> {
  private map = new Map<K, { value: V; expireAt: number }>();
  private maxSize: number;
  private defaultTTL: number; // ms

  constructor(maxSize: number, defaultTTLMs: number = 5 * 60 * 1000) {
    this.maxSize = maxSize;
    this.defaultTTL = defaultTTLMs;
  }

  get(key: K): V | undefined {
    const entry = this.map.get(key);
    if (!entry) return undefined;
    if (Date.now() > entry.expireAt) {
      this.map.delete(key);
      return undefined;
    }
    // LRU: 访问后移到末尾
    this.map.delete(key);
    this.map.set(key, entry);
    return entry.value;
  }

  set(key: K, value: V, ttlMs?: number) {
    if (this.map.has(key)) this.map.delete(key);
    else if (this.map.size >= this.maxSize) {
      // 淘汰最旧 (Map 的第一个 key)
      const oldest = this.map.keys().next().value;
      if (oldest !== undefined) this.map.delete(oldest);
    }
    this.map.set(key, {
      value,
      expireAt: Date.now() + (ttlMs ?? this.defaultTTL)
    });
  }

  /** 批量设置 (不触发 LRU 淘汰, 用于预加载) */
  setAll(entries: [K, V][], ttlMs?: number) {
    for (const [k, v] of entries) this.set(k, v, ttlMs);
  }

  has(key: K): boolean {
    return this.get(key) !== undefined;
  }

  delete(key: K) {
    this.map.delete(key);
  }

  deleteByPrefix(prefix: string) {
    for (const key of this.map.keys()) {
      if (typeof key === 'string' && key.startsWith(prefix)) this.map.delete(key);
    }
  }

  clear() {
    this.map.clear();
  }

  get size() {
    return this.map.size;
  }
}

/** 单例: 全局视频列表缓存 */
export const videoFeedCache = new LRUCache<string, any>(20, 3 * 60 * 1000); // 20页, 3min TTL

/** 单例: 用户资料缓存 */
export const userProfileCache = new LRUCache<number, any>(200, 5 * 60 * 1000); // 200条, 5min TTL

/** 单例: 搜索结果缓存 */
export const searchCache = new LRUCache<string, any>(50, 2 * 60 * 1000); // 50条, 2min TTL
