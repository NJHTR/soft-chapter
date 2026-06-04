<template>
  <from-bottom-dialog
    :page-id="pageId"
    :modelValue="modelValue"
    @update:modelValue="(e) => $emit('update:modelValue', e)"
    @cancel="cancel"
    :show-heng-gang="false"
    maskMode="light"
    :height="height"
    tag="comment"
    mode="white"
  >
    <template v-slot:header>
      <div class="title">
        <dy-back mode="dark" img="close" direction="right" style="opacity: 0" />
        <div class="num">{{ _formatNumber(comments.length) }}条评论</div>
        <div class="right">
          <Icon icon="prime:arrow-up-right-and-arrow-down-left-from-center" @click.stop="_no" />
          <Icon icon="ic:round-close" v-click="cancel" />
        </div>
      </div>
    </template>
    <div class="comment">
      <div class="wrapper" v-if="comments.length">
        <div class="items">
          <div class="item" :key="i" :data-comment-id="item.comment_id" v-for="(item, i) in comments">
            <div class="main">
              <div class="content">
                <img :src="_checkImgUrl(item.avatar)" alt="" class="head-image" />
                <div class="comment-container">
                  <div class="name">{{ item.nickname }}</div>
                  <!-- 文字内容 -->
                  <div class="detail" :class="item.user_buried && 'gray'" v-if="item.content && !item.user_buried">
                    {{ renderContent(item.content) }}
                  </div>
                  <div class="detail gray" v-else-if="item.user_buried">该评论已折叠</div>
                  <!-- 语音消息：宽度按时长动态变化 -->
                  <div class="comment-voice-bubble" v-for="(m, mi) in (item.mediaList || []).filter((x: any) => x.type === 'voice')" :key="'v'+mi" @click.stop="playVoice(m, item)" :style="{ width: voiceBubbleWidth(m.duration) }">
                    <img src="../assets/img/icon/message/chat/rss.png" alt="" class="voice-rss-icon" :class="{ playing: m._playing }" />
                    <div class="voice-wave-bars" :class="{ playing: m._playing }">
                      <span class="bar" v-for="j in 4" :key="j" :style="{ animationDelay: (j * 0.15) + 's' }"></span>
                    </div>
                    <span class="voice-dur">{{ m.duration || 0 }}″</span>
                  </div>
                  <!-- 图片消息：在文字下面展示，点击预览 -->
                  <div class="comment-images" v-if="(item.mediaList || []).some((x: any) => x.type === 'image')">
                    <img
                      v-for="(m, mi) in (item.mediaList || []).filter((x: any) => x.type === 'image')"
                      :key="'img'+mi"
                      :src="m.url"
                      class="comment-image-item"
                      @click.stop="previewImage(m.url)"
                    />
                  </div>
                  <div class="time-wrapper">
                    <div class="left">
                      <div class="time">
                        {{ _time(item.create_time)
                        }}{{ item.ip_location && ` · ${item.ip_location}` }}
                      </div>
                      <div class="reply-text" @click.stop="startReply(item)">回复</div>
                    </div>
                    <div class="right d-flex" style="gap: 10rem">
                      <div class="love" :class="item.user_digged && 'loved'" @click="loved(item)">
                        <Icon
                          icon="icon-park-solid:like"
                          v-show="item.user_digged"
                          class="love-image"
                        />
                        <Icon
                          icon="icon-park-outline:like"
                          v-show="!item.user_digged"
                          class="love-image"
                        />
                        <span v-if="item.digg_count">{{ _formatNumber(item.digg_count) }}</span>
                      </div>
                      <div class="love" @click="item.user_buried = !item.user_buried">
                        <Icon
                          v-if="item.user_buried"
                          icon="icon-park-solid:dislike-two"
                          class="love-image"
                        />
                        <Icon v-else icon="icon-park-outline:dislike" class="love-image" />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="replies" v-if="Number(item.sub_comment_count)">
              <template v-if="item.showChildren">
                <div class="reply" :key="i" :data-comment-id="child.comment_id" v-for="(child, i) in item.children">
                  <div class="content">
                    <img :src="_checkImgUrl(child.avatar)" alt="" class="head-image" />
                    <div class="comment-container">
                      <div class="name">
                        {{ child.nickname }}
                        <template v-if="child.reply_to_nickname">
                          <div class="reply-user"></div>
                          <span class="reply-to-name">@{{ child.reply_to_nickname }}</span>
                        </template>
                      </div>
                      <div class="detail">{{ renderContent(child.content) }}</div>
                      <div class="time-wrapper">
                        <div class="left">
                          <div class="time">
                            {{ _time(child.create_time)
                            }}{{ child.ip_location && ` · ${item.ip_location}` }}
                          </div>
                          <div class="reply-text" @click.stop="startReply(child, item.comment_id)">回复</div>
                        </div>
                        <div
                          class="love"
                          :class="child.user_digged && 'loved'"
                          @click="loved(child)"
                        >
                          <Icon
                            icon="icon-park-solid:like"
                            v-show="child.user_digged"
                            class="love-image"
                          />
                          <Icon
                            icon="icon-park-outline:like"
                            v-show="!child.user_digged"
                            class="love-image"
                          />
                          <span>{{ _formatNumber(child.digg_count) }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </template>
              <Loading
                v-if="item._loadingChildren"
                :type="'small'"
                :is-full-screen="false"
              />
              <div class="more" v-else @click="handShowChildren(item)">
                <div class="gang"></div>
                <span>{{ item.showChildren ? '收起' : `展开${item.sub_comment_count}条` }}回复</span>
                <Icon :icon="item.showChildren ? 'ep:arrow-up-bold' : 'ep:arrow-down-bold'" />
              </div>
            </div>
          </div>
        </div>
        <no-more />
      </div>
      <Loading v-else style="position: absolute" />
      <transition name="fade">
        <BaseMask v-if="isCall" mode="lightgray" @click="isCall = false" />
      </transition>
      <div class="input-toolbar">
        <transition name="fade">
          <div class="call-friend" v-if="isCall">
            <div class="friend" :key="i" v-for="(item, i) in friends.all" @click="toggleCall(item)">
              <img
                :style="item.select ? 'opacity: .5;' : ''"
                class="avatar"
                :src="_checkImgUrl(item.avatar)"
                alt=""
              />
              <span>{{ item.name }}</span>
              <img
                v-if="item.select"
                class="checked"
                src="../assets/img/icon/components/check/check-red-share.png"
              />
            </div>
          </div>
        </transition>

        <div class="toolbar">
          <div class="reply-to" v-if="replyingTo">
            <span>回复 @{{ replyingTo.nickname }}</span>
            <Icon icon="ic:round-close" @click="cancelReply" style="font-size: 14rem; cursor: pointer" />
          </div>
          <!-- 媒体预览 -->
          <div class="media-preview" v-if="mediaList.length">
            <div class="preview-item" v-for="(m, i) in mediaList" :key="i">
              <img v-if="m.type === 'image'" :src="m.url" class="preview-img" />
              <div v-else-if="m.type === 'voice'" class="preview-voice">
                <Icon icon="mdi:microphone" /> {{ m.duration }}″
              </div>
              <Icon icon="ic:round-close" class="remove-media" @click="mediaList.splice(i, 1)" />
            </div>
          </div>
          <!-- 隐藏的文件选择器 -->
          <input ref="imageInput" type="file" accept="image/*" style="display:none" @change="handleImagePicked" />
          <div class="input-wrapper">
            <AutoInput ref="autoInputRef" v-model="comment" placeholder="善语结善缘，恶言伤人心"></AutoInput>
            <div class="right">
              <img src="../assets/img/icon/message/call.png" @click="isCall = !isCall" />
              <img src="../assets/img/icon/message/emoji-black.png" @click="showEmoji = !showEmoji" />
              <img src="../assets/img/icon/message/camera.png" class="camera-btn" @click="pickImage" />
              <img v-if="!recording" src="../assets/img/icon/message/voice-black.png" @click="startRecording" class="voice-btn" />
              <img v-if="comment || mediaList.length" class="send-btn" src="../assets/img/icon/message/up.png" @click="send" />
            </div>
          </div>
          <!-- 表情包面板 -->
          <div class="emoji-panel" v-if="showEmoji">
            <span class="emoji-item" v-for="e in emojiList" :key="e" @click="comment += e">{{ e }}</span>
          </div>
          <!-- 录音状态 -->
          <div class="recording-bar" v-if="recording">
            <span class="recording-hint">{{ recordingActive ? '松开 发送' : '按住 说话' }}</span>
            <div class="record-timer" v-if="recordingActive">{{ voiceDuration }}″</div>
            <button class="cancel-record" @click="cancelRecording">取消</button>
            <div
              class="record-zone"
              @touchstart.prevent="voiceStart"
              @touchmove.prevent="voiceMove"
              @touchend.prevent="voiceEnd"
              @mousedown.prevent="voiceStart"
              @mousemove.prevent="voiceMove"
              @mouseup.prevent="voiceEnd"
              @mouseleave.prevent="voiceEnd"
            >
              <div class="record-wave" :class="{ active: recordingActive }">
                <span class="bar" v-for="i in 5" :key="i"></span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <ConfirmDialog title="私信给" ok-text="发送" v-model:visible="showPrivateChat">
        <Search mode="light" v-model="test" :isShowSearchIcon="false" />
      </ConfirmDialog>
      <!-- 图片全屏预览 -->
      <transition name="fade">
        <div class="image-preview-mask" v-if="previewUrl" @click="previewUrl = ''">
          <img :src="previewUrl" alt="" />
        </div>
      </transition>
    </div>
  </from-bottom-dialog>
</template>

<script lang="ts">
import AutoInput from './AutoInput.vue'
import ConfirmDialog from './dialog/ConfirmDialog.vue'
import { mapState } from 'pinia'
import FromBottomDialog from './dialog/FromBottomDialog.vue'
import Loading from './Loading.vue'
import Search from './Search.vue'
import {
  _checkImgUrl,
  _formatNumber,
  _no,
  _notice,
  _showSelectDialog,
  _time
} from '@/utils'
import { useBaseStore } from '@/store/pinia'
import bus, { EVENT_KEY } from '@/utils/bus'
import { videoComments, postComment, toggleCommentLike, getCommentReplies } from '@/api/videos'
import { uploadImage, uploadVoice } from '@/api/user'

const emojiList = ['😀','😂','🤣','😍','🥰','😘','😜','😎','🤩','😊','😏','🥺','😭','😡','🤬','👍','👎','👏','🙌','💪','🤝','❤️','💔','🔥','⭐','🎉','🎊','🙏','🤔','🤗','😱','😴','🤤','🫡','🫠','😶','🤐','😮‍💨','😵','🥴','🤧','💩','👻','💀','👀','👋','🫶','🌹','🌈','🍉','🎂','🍺','💰','🔞']

export default {
  name: 'Comment',
  components: {
    AutoInput,
    ConfirmDialog,
    FromBottomDialog,
    Loading,
    Search
  },
  props: {
    modelValue: {
      type: Boolean,
      default() { return false }
    },
    videoId: {
      type: String,
      default: null
    },
    pageId: {
      type: String,
      default: 'home-index'
    },
    height: {
      type: String,
      default: 'calc(var(--vh, 1vh) * 70)'
    },
    scrollToCommentId: {
      type: [String, Number],
      default: ''
    }
  },
  computed: {
    ...mapState(useBaseStore, ['friends'])
  },
  watch: {
    modelValue(newVale) {
      if (newVale) {
        this.pageNo = 1
        this.hasMore = true
        this.comments = []
        this.loadComments().then(() => this.$nextTick(() => this.scrollToTargetComment()))
      } else {
        this.comments = []
        this.comment = ''
        this.mediaList = []
        this.replyingTo = null
        this.showEmoji = false
        this.recording = false
      }
    }
  },
  data() {
    return {
      comment: '',
      test: '',
      comments: [],
      mediaList: [] as { type: string; url: string; duration?: number }[],
      showEmoji: false,
      recording: false,
      recordingActive: false,
      voiceDuration: 0,
      voiceTimer: null as any,
      voiceCancelled: false,
      emojiList,
      pageNo: 1,
      pageSize: 15,
      hasMore: true,
      loadingMore: false,
      options: [
        { id: 1, name: '私信回复' },
        { id: 2, name: '复制' },
        { id: 3, name: '搜一搜' },
        { id: 4, name: '举报' }
      ],
      selectRow: {},
      showPrivateChat: false,
      isInput: false,
      isCall: false,
      replyingTo: null as any,
      replyingToParentId: null as any,
      sending: false,
      previewUrl: '',
      mediaRecorder: null as any,
      audioChunks: [] as Blob[]
    }
  },
  mounted() {
    this.$nextTick(() => {
      // FromBottomDialog 的 .wrapper 才是真正的滚动容器
      const el = this.$el?.parentElement
      if (el) el.addEventListener('scroll', this.onScroll)
    })
  },
  beforeUnmount() {
    const el = this.$el?.parentElement
    if (el) el.removeEventListener('scroll', this.onScroll)
  },
  methods: {
    _no,
    _time,
    _formatNumber,
    _checkImgUrl,
    renderContent(content) {
      if (!content) return ''
      return content.replace(/@\[([^\]:]+):([^\]]+)\]/g, '@$2')
    },
    resetSelectStatus() {
      this.friends.all.forEach((item) => {
        item.select = false
      })
    },
    async handShowChildren(item) {
      if (item.showChildren) {
        item.showChildren = false
        return
      }
      if (!item.children || item.children.length === 0) {
        item._loadingChildren = true
        const res = await getCommentReplies(item.comment_id)
        item._loadingChildren = false
        if (res.success) {
          item.children = res.data
        }
      }
      item.showChildren = true
    },
    // 图片上传
    pickImage() {
      (this.$refs.imageInput as HTMLInputElement)?.click()
    },
    async handleImagePicked(e: Event) {
      const file = (e.target as HTMLInputElement).files?.[0]
      if (!file) return
      try {
        const res = await uploadImage(file)
        if (res.success && (res.data as any)?.url) {
          this.mediaList.push({ type: 'image', url: (res.data as any).url })
        } else {
          _notice('图片上传失败')
        }
      } catch { _notice('图片上传失败') }
      // 重置 input 以便重复选同一文件
      ;(e.target as HTMLInputElement).value = ''
    },
    // 语音录制
    startRecording() {
      this.recording = true
      this.recordingActive = false
      this.voiceCancelled = false
    },
    cancelRecording() {
      if (this.mediaRecorder && this.mediaRecorder.state === 'recording') {
        this.mediaRecorder.stop()
      }
      this.recording = false
      this.recordingActive = false
      clearInterval(this.voiceTimer)
    },
    async voiceStart(e: any) {
      if (!this.recording) return
      this.recordingActive = true
      this.voiceCancelled = false
      this.voiceDuration = 0
      this.audioChunks = []
      // 立即开始录音
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
        this.mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm;codecs=opus' })
        this.mediaRecorder.ondataavailable = (ev: any) => { if (ev.data.size > 0) this.audioChunks.push(ev.data) }
        this.mediaRecorder.onstop = () => {
          stream.getTracks().forEach((t: any) => t.stop())
          if (this.voiceCancelled || this.audioChunks.length === 0) return
          const blob = new Blob(this.audioChunks, { type: 'audio/webm' })
          uploadVoice(blob).then((res: any) => {
            if (res.success && res.data?.url) {
              this.mediaList.push({ type: 'voice', url: res.data.url, duration: this.voiceDuration })
            } else {
              _notice('语音上传失败')
            }
          }).catch(() => _notice('语音上传失败'))
        }
        this.mediaRecorder.start()
        this.voiceTimer = setInterval(() => {
          this.voiceDuration++
          if (this.voiceDuration >= 60) this.voiceEnd(e)
        }, 1000)
      } catch {
        _notice('无法访问麦克风')
        this.recording = false
      }
    },
    voiceMove(e: any) {
      if (!this.recordingActive) return
      const touch = e.touches?.[0] || e
      const el = this.$el.querySelector('.record-zone')
      if (el) {
        const rect = el.getBoundingClientRect()
        this.voiceCancelled = touch.clientY < rect.top - 80
      }
    },
    voiceEnd(e: any) {
      if (!this.recordingActive) return
      this.recordingActive = false
      clearInterval(this.voiceTimer)
      if (this.mediaRecorder && this.mediaRecorder.state === 'recording') {
        this.mediaRecorder.stop()
      }
      this.recording = false
    },
    // 语音气泡宽度按时长计算：1s→100rem, 60s→280rem
    voiceBubbleWidth(duration: number) {
      const dur = Math.max(1, Math.min(60, duration || 1))
      return Math.round(100 + (dur / 60) * 180) + 'rem'
    },
    // 播放语音
    playVoice(m: any, item: any) {
      if (m._playing) {
        m._playing = false
        if (m._audio) { m._audio.pause(); m._audio = null }
        return
      }
      // 停止其他播放中的
      this.comments.forEach(c => {
        if (c.mediaList) c.mediaList.forEach((cm: any) => {
          if (cm !== m && cm._playing) { cm._playing = false; if (cm._audio) { cm._audio.pause(); cm._audio = null } }
        })
      })
      m._playing = true
      m._audio = new Audio(m.url)
      m._audio.onended = () => { m._playing = false; m._audio = null }
      m._audio.onerror = () => { m._playing = false; m._audio = null }
      m._audio.play()
    },
    previewImage(url: string) {
      this.previewUrl = url
    },
    // 分页加载评论
    async loadComments() {
      if (this.loadingMore || !this.hasMore) return
      this.loadingMore = true
      try {
        const res: any = await videoComments({ id: this.videoId, pageNo: this.pageNo, pageSize: this.pageSize })
        if (res.success) {
          const list = (res.data || []).map((v: any) => {
            v.showChildren = false
            v.digg_count = Number(v.digg_count)
            if (v.extra) {
              try { v.mediaList = JSON.parse(v.extra) } catch { v.mediaList = [] }
            }
            return v
          })
          this.comments = [...this.comments, ...list]
          this.hasMore = list.length >= this.pageSize
          this.pageNo++
          // 滑动窗口：超过 200 条时从尾部裁剪
          this.trimCache()
        }
      } catch { /* ignore */ }
      this.loadingMore = false
    },
    // 滑动窗口裁剪：保留最新 200 条
    trimCache() {
      const MAX = 200
      if (this.comments.length > MAX) {
        this.comments = this.comments.slice(0, MAX)
      }
    },
    // 滚动加载更多
    onScroll(e: Event) {
      const el = e.target as HTMLElement
      if (!el) return
      const { scrollTop, scrollHeight, clientHeight } = el
      if (scrollHeight - scrollTop - clientHeight < 100) {
        this.loadComments()
      }
    },
    async send() {
      if (this.sending) return
      const content = this.comment.trim()
      if (!content && !this.mediaList.length) return
      this.sending = true
      const payload: any = {
        video_id: this.videoId,
        content: content,
      }
      if (this.mediaList.length) {
        payload.extra = JSON.stringify(this.mediaList)
      }
      if (this.replyingTo) {
        payload.parent_id = this.replyingToParentId || this.replyingTo.comment_id
        payload.reply_to_user_id = this.replyingTo.user_id || this.replyingTo.id
      }
      // 乐观清空输入
      this.comment = ''
      const sentMedia = [...this.mediaList]
      this.mediaList = []
      this.replyingTo = null
      this.isCall = false
      this.showEmoji = false
      this.resetSelectStatus()
      try {
        const res: any = await postComment(payload)
        if (res.success) {
          bus.emit(EVENT_KEY.COMMENT_ADDED, this.videoId)
          // 发送成功后重新加载第一页评论
          this.loadingMore = false
          this.pageNo = 1
          this.hasMore = true
          this.comments = []
          // 强制渲染 Loading，等待 DOM 更新后再请求
          await this.$nextTick()
          await this.loadComments()
        } else {
          this.comment = content
          this.mediaList = sentMedia
          _notice((res as any).msg || '评论失败')
        }
      } catch (e) {
        this.comment = content
        this.mediaList = sentMedia
        _notice('评论发送失败，请重试')
      } finally {
        this.sending = false
      }
    },
    startReply(comment, parentCommentId) {
      this.replyingTo = comment
      this.replyingToParentId = parentCommentId || null
      this.comment = ''
      this.mediaList = []
      this.$nextTick(() => {
        const input = this.$el.querySelector('.auto-input')
        if (input) input.focus()
      })
    },
    cancelReply() {
      this.replyingTo = null
      this.replyingToParentId = null
      this.comment = ''
      this.mediaList = []
    },
    async getData() {
      let res: any = await videoComments({ id: this.videoId })
      if (res.success) {
        res.data.map((v) => {
          v.showChildren = false
          v.digg_count = Number(v.digg_count)
          // 解析媒体附件
          if (v.extra) {
            try { v.mediaList = JSON.parse(v.extra) } catch { v.mediaList = [] }
          }
        })
        this.comments = res.data
      }
    },
    cancel() {
      this.$emit('update:modelValue', false)
      this.$emit('close')
    },
    toggleCall(item) {
      item.select = !item.select
      const mentionStr = `@[${item.id}:${item.name}]`
      if (this.comment.includes(mentionStr)) {
        this.comment = this.comment.replace(mentionStr + ' ', '').replace(mentionStr, '').trim()
      } else {
        this.comment += mentionStr + ' '
      }
    },
    async loved(row) {
      const res = await toggleCommentLike(row.comment_id)
      if (res.success) {
        row.user_digged = res.data.isLoved
        row.digg_count = res.data.likeCount
      }
    },
    scrollToTargetComment() {
      const targetId = this.scrollToCommentId
      if (!targetId) return
      const el = this.$el.querySelector(`.item[data-comment-id="${targetId}"]`)
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' })
        el.classList.add('highlight-comment')
        setTimeout(() => el.classList.remove('highlight-comment'), 2000)
      }
    },
    showOptions(row) {
      _showSelectDialog(this.options, (e) => {
        if (e.id === 1) {
          this.selectRow = row
          this.showPrivateChat = true
        }
      })
    }
  }
}
</script>

<style lang="less" scoped>
@import '../assets/less/index';

.title {
  box-sizing: border-box;
  width: 100%;
  height: 40rem;
  padding: 0 15rem;
  background: white;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-radius: 10rem 10rem 0 0;

  .num {
    width: 100%;
    position: absolute;
    font-size: 12rem;
    font-weight: bold;
    text-align: center;
  }

  .right {
    display: flex;
    gap: 12rem;
    position: relative;
    z-index: 9;

    svg {
      color: #000;
      background: rgb(242, 242, 242);
      padding: 4rem;
      font-size: 16rem;
      border-radius: 50%;
    }
  }
}

.gray {
  color: var(--second-text-color);
}

.comment {
  color: #000;
  width: 100%;
  background: #fff;
  z-index: 5;

  .wrapper {
    width: 100%;
    position: relative;
    padding-bottom: 60rem;
  }

  .items {
    width: 100%;

    .item {
      width: 100%;
      margin-bottom: 15rem;

      .main {
        width: 100%;
        padding: 5rem 0;
        display: flex;

        &:active {
          background: #53535321;
        }

        .head-image {
          margin-left: 15rem;
          margin-right: 10rem;
          width: 37rem;
          height: 37rem;
          border-radius: 50%;
        }
      }

      .replies {
        padding-left: 55rem;

        .reply {
          padding: 5rem 0 5rem 5rem;
          display: flex;

          &:active {
            background: #53535321;
          }

          .head-image {
            margin-right: 10rem;
            width: 20rem;
            height: 20rem;
            border-radius: 50%;
          }
        }

        .more {
          font-size: 13rem;
          margin: 5rem;
          display: flex;
          align-items: center;
          color: gray;

          .gang {
            background: #d5d5d5;
            width: 20rem;
            margin-right: 10rem;
            height: 1px;
          }

          span {
            margin-right: 5rem;
          }

          svg {
            font-size: 10rem;
          }
        }
      }

      .content {
        width: 100%;
        display: flex;
        font-size: 14rem;

        .comment-container {
          flex: 1;
          margin-right: 20rem;

          .name {
            color: var(--second-text-color);
            margin-bottom: 5rem;
            display: flex;
            align-items: center;

            .reply-user {
              margin-left: 5rem;
              width: 0;
              height: 0;
              border: 5rem solid transparent;
              border-left: 6rem solid gray;
            }

            .reply-to-name {
              color: #507daf;
              margin-left: 2rem;
              font-size: 12rem;
            }
          }

          .detail {
            margin-bottom: 5rem;
          }

          // 语音气泡：聊天气泡风格，独占一行
          .comment-voice-bubble {
            display: flex;
            align-items: center;
            gap: 8rem;
            padding: 8rem 14rem;
            margin-bottom: 8rem;
            margin-top: 6rem;
            background: #f2f2f2;
            border-radius: 16rem;
            cursor: pointer;
            min-width: 100rem;
            max-width: 280rem;

            .voice-rss-icon {
              width: 22rem;
              height: 22rem;
              flex-shrink: 0;
            }

            .voice-dur {
              font-size: 13rem;
              color: #666;
              min-width: 24rem;
              text-align: right;
            }

            .voice-wave-bars {
              display: flex;
              align-items: flex-end;
              gap: 2.5rem;
              height: 22rem;
              flex: 1;
              justify-content: center;

              .bar {
                width: 3rem;
                border-radius: 2rem;
                background: #ccc;
                height: 6rem;
                transition: height 0.2s;
              }

              &.playing .bar {
                background: #fe2c55;
                animation: voiceWave 0.7s ease-in-out infinite alternate;

                &:nth-child(1) { height: 10rem; }
                &:nth-child(2) { height: 18rem; }
                &:nth-child(3) { height: 14rem; }
                &:nth-child(4) { height: 20rem; }
              }
            }
          }

          @keyframes voiceWave {
            0% { transform: scaleY(0.4); }
            100% { transform: scaleY(1); }
          }

          // 图片：在文字下方，网格展示
          .comment-images {
            display: flex;
            flex-wrap: wrap;
            gap: 6rem;
            margin-bottom: 8rem;

            .comment-image-item {
              width: 75rem;
              height: 75rem;
              object-fit: cover;
              border-radius: 6rem;
              cursor: pointer;
              border: 1px solid #eee;
            }
          }

          .time-wrapper {
            display: flex;
            align-items: center;
            justify-content: space-between;
            font-size: 13rem;

            .left {
              display: flex;

              .time {
                color: #c4c3c3;
                margin-right: 10rem;
              }

              .reply-text {
                color: var(--second-text-color);
              }
            }

            .love {
              color: gray;
              display: flex;
              align-items: center;

              &.loved {
                color: rgb(231, 58, 87);
              }

              .love-image {
                font-size: 17rem;
                margin-right: 4rem;
              }

              span {
                word-break: keep-all;
              }
            }
          }
        }
      }
    }
  }

  @normal-bg-color: rgb(35, 38, 47);
  @chat-bg-color: rgb(105, 143, 244);

  .input-toolbar {
    border-radius: 10rem 10rem 0 0;
    background: white;
    position: fixed;
    width: 100%;
    bottom: 0;
    z-index: 3;

    @space-width: 18rem;
    @icon-width: 48rem;

    .call-friend {
      padding-top: 30rem;
      overflow-x: scroll;
      display: flex;
      padding-right: @space-width;

      .friend {
        width: @icon-width;
        position: relative;
        margin-left: @space-width;
        margin-bottom: @space-width;
        font-size: 10rem;
        display: flex;
        flex-direction: column;
        align-items: center;

        .avatar {
          width: @icon-width;
          height: @icon-width;
          border-radius: 50%;
        }

        span {
          margin-top: 5rem;
          text-align: center;
          width: @icon-width;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .checked {
          position: absolute;
          top: @icon-width - 1.5;
          right: -2px;
          width: 20rem;
          height: 20rem;
          border-radius: 50%;
        }
      }
    }

    .media-preview {
      display: flex;
      gap: 8rem;
      padding: 8rem 15rem 0 15rem;
      flex-wrap: wrap;

      .preview-item {
        position: relative;
        display: inline-flex;
        align-items: center;

        .preview-img {
          width: 50rem;
          height: 50rem;
          object-fit: cover;
          border-radius: 6rem;
        }

        .preview-voice {
          display: flex;
          align-items: center;
          gap: 4rem;
          padding: 6rem 12rem;
          background: #f0f0f0;
          border-radius: 16rem;
          font-size: 13rem;
          color: #fe2c55;
        }

        .remove-media {
          position: absolute;
          top: -6rem;
          right: -6rem;
          font-size: 16rem;
          background: rgba(0,0,0,0.5);
          color: white;
          border-radius: 50%;
          cursor: pointer;
        }
      }
    }

    .emoji-panel {
      display: flex;
      flex-wrap: wrap;
      gap: 6rem;
      padding: 10rem 15rem;
      border-top: 1px solid #e2e1e1;
      max-height: 120rem;
      overflow-y: auto;
      background: #fafafa;

      .emoji-item {
        font-size: 22rem;
        cursor: pointer;
        padding: 4rem;
        &:active { transform: scale(1.3); }
      }
    }

    .recording-bar {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10rem;
      padding: 10rem 15rem;
      border-top: 1px solid #e2e1e1;
      background: #fafafa;

      .recording-hint {
        font-size: 14rem;
        color: #666;
      }

      .record-timer {
        font-size: 18rem;
        font-weight: bold;
        color: #fe2c55;
      }

      .cancel-record {
        padding: 4rem 16rem;
        border-radius: 16rem;
        border: 1px solid #ddd;
        background: white;
        font-size: 13rem;
        cursor: pointer;
      }

      .record-zone {
        width: 80%;
        height: 50rem;
        display: flex;
        align-items: center;
        justify-content: center;
        background: #f0f0f0;
        border-radius: 25rem;
        cursor: pointer;
        user-select: none;

        .record-wave {
          display: flex;
          align-items: center;
          gap: 4rem;
          opacity: 0.5;

          &.active {
            opacity: 1;
            .bar { animation: waveAnim 0.8s infinite alternate; }
          }

          .bar {
            width: 4rem;
            height: 16rem;
            background: #fe2c55;
            border-radius: 2rem;
          }
        }
      }
    }

    @keyframes waveAnim {
      from { height: 8rem; }
      to { height: 24rem; }
    }

    .reply-to {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 6rem 15rem;
      background: #f5f5f5;
      font-size: 12rem;
      color: #666;
    }

    .toolbar {
      @icon-width: 25rem;
      display: flex;
      flex-direction: column;
      padding: 10rem 15rem;
      border-top: 1px solid #e2e1e1;

      .input-wrapper {
        display: flex;
        align-items: center;
        box-sizing: border-box;
        padding: 5rem 10rem;
        background: #eee;
        border-radius: 20rem;
        gap: 6rem;

        .auto-input {
          flex: 1;
          min-width: 0;
        }

        .right {
          display: flex;
          align-items: center;
          flex-shrink: 0;
          gap: 6rem;

          img {
            width: @icon-width;
            height: @icon-width;
            cursor: pointer;
          }

          .voice-btn {
            width: 22rem;
            height: 22rem;
            opacity: 0.7;
          }

          .camera-btn {
            width: 22rem;
            height: 22rem;
            filter: brightness(0);
            opacity: 0.7;
          }

          .send-btn {
            width: 22rem;
            height: 22rem;
            border-radius: 50%;
            background: #fe2c55;
            padding: 3rem;
          }
        }
      }
    }
  }
}

.highlight-comment {
  animation: highlightFade 2s ease;
}
@keyframes highlightFade {
  0% { background: rgba(254, 44, 85, 0.3); }
  100% { background: transparent; }
}

.comment-enter-active,
.comment-leave-active {
  transition: all 0.15s ease;
}

.comment-enter-from,
.comment-leave-to {
  transform: translateY(60vh);
}

.image-preview-mask {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: calc(var(--vh, 1vh) * 100);
  background: rgba(0, 0, 0, 0.95);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;

  img {
    max-width: 100vw;
    max-height: 100vh;
    object-fit: contain;
  }
}
</style>
