/**
 * 分片上传工具
 *
 * 用法:
 *   const uploader = createChunkUploader(file, { chunkSize: 5 * 1024 * 1024 })
 *   uploader.on('progress', (pct) => console.log(`${pct}%`))
 *   const { url } = await uploader.start()
 */

import { request } from '@/utils/request'

const DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024 // 5MB

export interface ChunkUploadOptions {
  chunkSize?: number // 每片大小, 默认 5MB
  concurrency?: number // 并发上传数, 默认 2
  maxRetries?: number // 每片最大重试次数, 默认 3
}

export interface ChunkUploadResult {
  url: string // MinIO 路径, 如 "douyin-video/uuid.mp4"
}

type EventHandler = (payload: any) => void

export class ChunkUploader {
  private file: File
  private uploadId: string
  private chunkSize: number
  private concurrency: number
  private maxRetries: number
  private totalChunks: number
  private aborted = false
  private handlers: Record<string, EventHandler[]> = {}

  constructor(file: File, options: ChunkUploadOptions = {}) {
    this.file = file
    this.chunkSize = options.chunkSize || DEFAULT_CHUNK_SIZE
    this.concurrency = Math.min(options.concurrency || 2, 4)
    this.maxRetries = options.maxRetries ?? 3
    this.totalChunks = Math.ceil(file.size / this.chunkSize)
    this.uploadId = generateUploadId()
  }

  on(event: 'progress' | 'done' | 'error', handler: EventHandler) {
    if (!this.handlers[event]) this.handlers[event] = []
    this.handlers[event].push(handler)
    return this
  }

  private emit(event: string, payload: any) {
    this.handlers[event]?.forEach((h) => h(payload))
  }

  async start(): Promise<ChunkUploadResult> {
    const completed = new Array(this.totalChunks).fill(false)

    // 创建待上传队列
    const pending: number[] = []
    for (let i = 0; i < this.totalChunks; i++) pending.push(i)

    let doneCount = 0

    const uploadOne = async (index: number): Promise<void> => {
      if (this.aborted) return

      const start = index * this.chunkSize
      const end = Math.min(start + this.chunkSize, this.file.size)
      const blob = this.file.slice(start, end)

      let lastErr: Error | null = null
      for (let attempt = 0; attempt <= this.maxRetries; attempt++) {
        if (this.aborted) return
        try {
          const formData = new FormData()
          formData.append('file', blob, `chunk_${index}`)
          formData.append('uploadId', this.uploadId)
          formData.append('chunkIndex', String(index))
          formData.append('totalChunks', String(this.totalChunks))
          formData.append('fileName', this.file.name)

          await request({
            url: '/upload/chunk',
            method: 'post',
            data: formData,
          })

          completed[index] = true
          doneCount++
          this.emit('progress', Math.round((doneCount / this.totalChunks) * 100))
          return
        } catch (e: any) {
          lastErr = e
          if (attempt < this.maxRetries) {
            // 指数退避: 1s, 2s, 4s
            await sleep(Math.pow(2, attempt) * 1000)
          }
        }
      }
      throw lastErr || new Error(`Chunk ${index} upload failed`)
    }

    // 并发控制
    let cursor = 0
    const workers: Promise<void>[] = []

    const worker = async () => {
      while (cursor < this.totalChunks && !this.aborted) {
        const idx = cursor++
        await uploadOne(idx)
      }
    }

    for (let i = 0; i < this.concurrency; i++) {
      workers.push(worker())
    }

    try {
      await Promise.all(workers)
    } catch (e: any) {
      this.emit('error', e)
      throw e
    }

    if (this.aborted) throw new Error('Upload aborted')

    // 合并分片
    const res = await request({
      url: '/upload/chunk/merge',
      method: 'post',
      data: {
        uploadId: this.uploadId,
        fileName: this.file.name,
        totalChunks: this.totalChunks,
      },
    })

    if (!res.success || !res.data?.url) {
      throw new Error(res.msg || 'Merge failed')
    }

    this.emit('progress', 100)
    this.emit('done', { url: res.data.url })
    return { url: res.data.url }
  }

  abort() {
    this.aborted = true
  }
}

export function createChunkUploader(file: File, options?: ChunkUploadOptions) {
  return new ChunkUploader(file, options)
}

function generateUploadId(): string {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789'
  let id = Date.now().toString(36)
  for (let i = 0; i < 16; i++) {
    id += chars[Math.floor(Math.random() * chars.length)]
  }
  return id
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
