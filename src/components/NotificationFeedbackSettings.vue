<template>
  <div v-if="open" class="feedback-mask" @click.self="close">
    <section class="feedback-sheet" role="dialog" aria-modal="true" aria-labelledby="feedback-title">
      <header class="feedback-header">
        <h2 id="feedback-title">通知声音与震动</h2>
        <button type="button" class="feedback-close" aria-label="关闭" @click="close">×</button>
      </header>

      <div class="feedback-content">
        <label class="feedback-switch-row">
          <span>开启通知反馈</span>
          <input v-model="draft.enabled" type="checkbox" @change="save" />
        </label>
        <label class="feedback-switch-row">
          <span>声音</span>
          <input v-model="draft.soundEnabled" type="checkbox" @change="save" />
        </label>
        <label class="feedback-switch-row">
          <span>震动</span>
          <input v-model="draft.vibrationEnabled" type="checkbox" @change="save" />
        </label>

        <label class="feedback-field">
          <span>声音音量 {{ Math.round(draft.volume * 100) }}%</span>
          <input v-model.number="draft.volume" type="range" min="0" max="1" step="0.05" @change="save" />
        </label>

        <label class="feedback-field">
          <span>消息类型</span>
          <select v-model="selectedKind">
            <option v-for="item in kindOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>

        <label class="feedback-field">
          <span>震动节奏</span>
          <select v-model="selectedPattern" @change="applyPattern">
            <option value="default">默认</option>
            <option value="short">短促</option>
            <option value="double">双击</option>
            <option value="long">长震</option>
            <option value="none">关闭此类型震动</option>
          </select>
        </label>

        <div class="feedback-file-row">
          <div>
            <strong>自定义声音</strong>
            <small>{{ hasCustomSound ? '已设置本地声音' : '未设置，使用默认提示音' }}</small>
          </div>
          <label class="feedback-file-button">
            选择音频
            <input type="file" accept="audio/*" @change="onSoundFile" />
          </label>
        </div>

        <div class="feedback-actions">
          <button type="button" class="secondary" @click="testFeedback">试听</button>
          <button type="button" class="secondary" @click="reset">恢复默认</button>
          <button type="button" class="primary" @click="close">完成</button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  getNotificationFeedbackSettings,
  playNotificationFeedback,
  resetNotificationFeedbackSettings,
  setCustomNotificationSound,
  setVibrationPattern,
  type FeedbackKind,
  type NotificationFeedbackSettings,
  updateNotificationFeedbackSettings
} from '@/utils/notificationFeedback'

defineOptions({ name: 'NotificationFeedbackSettings' })

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (event: 'close'): void }>()

const kindOptions: Array<{ value: FeedbackKind; label: string }> = [
  { value: 'chat', label: '私聊消息' },
  { value: 'group', label: '群聊消息' },
  { value: 'follow', label: '新增关注' },
  { value: 'like', label: '点赞' },
  { value: 'comment', label: '评论' },
  { value: 'collect', label: '收藏' },
  { value: 'mention', label: '提及我' },
  { value: 'friend', label: '好友动态' },
  { value: 'call', label: '来电' }
]

const draft = reactive<NotificationFeedbackSettings>(getNotificationFeedbackSettings())
const selectedKind = ref<FeedbackKind>('chat')
const selectedPattern = ref('default')

const hasCustomSound = computed(() => Boolean(draft.soundUrls[selectedKind.value]))

function syncDraft() {
  Object.assign(draft, getNotificationFeedbackSettings())
  updateSelectedPattern()
}

function save() {
  updateNotificationFeedbackSettings({
    enabled: draft.enabled,
    soundEnabled: draft.soundEnabled,
    vibrationEnabled: draft.vibrationEnabled,
    volume: draft.volume
  })
}

function updateSelectedPattern() {
  const pattern = draft.vibrationPatterns[selectedKind.value]
  if (!pattern) {
    selectedPattern.value = 'default'
  } else if (pattern.length === 0) {
    selectedPattern.value = 'none'
  } else if (pattern.length === 1 && pattern[0] <= 70) {
    selectedPattern.value = 'short'
  } else if (pattern.length === 3) {
    selectedPattern.value = 'double'
  } else if (pattern.length === 1) {
    selectedPattern.value = 'long'
  } else {
    selectedPattern.value = 'default'
  }
}

function applyPattern() {
  const patterns: Record<string, number[]> = {
    default: [],
    short: [50],
    double: [60, 50, 60],
    long: [220],
    none: []
  }
  if (selectedPattern.value === 'default') {
    const next = { ...draft.vibrationPatterns }
    delete next[selectedKind.value]
    draft.vibrationPatterns = next
    updateNotificationFeedbackSettings({ vibrationPatterns: next })
    return
  }
  setVibrationPattern(selectedKind.value, patterns[selectedPattern.value] || [])
  draft.vibrationPatterns[selectedKind.value] = patterns[selectedPattern.value] || []
}

async function onSoundFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  try {
    await setCustomNotificationSound(selectedKind.value, file)
    draft.soundUrls[selectedKind.value] =
      getNotificationFeedbackSettings().soundUrls[selectedKind.value] || ''
  } catch (error) {
    console.warn('[notification-feedback] custom sound rejected', error)
  }
  ;(event.target as HTMLInputElement).value = ''
}

function testFeedback() {
  void playNotificationFeedback(selectedKind.value)
}

function reset() {
  Object.assign(draft, resetNotificationFeedbackSettings())
  updateSelectedPattern()
}

function close() {
  emit('close')
}

watch(() => props.open, (open) => {
  if (open) syncDraft()
})
watch(selectedKind, updateSelectedPattern)
</script>

<style scoped lang="less">
.feedback-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.55);
}

.feedback-sheet {
  width: 100%;
  max-height: 86vh;
  overflow: auto;
  padding: 18rem 18rem calc(20rem + env(safe-area-inset-bottom));
  color: #fff;
  background: #1c1c1f;
  border-radius: 16rem 16rem 0 0;
  box-sizing: border-box;
}

.feedback-header,
.feedback-switch-row,
.feedback-file-row,
.feedback-actions {
  display: flex;
  align-items: center;
}

.feedback-header {
  justify-content: space-between;
  margin-bottom: 16rem;
}

.feedback-header h2 {
  margin: 0;
  font-size: 17rem;
}

.feedback-close {
  width: 32rem;
  height: 32rem;
  padding: 0;
  color: #aaa;
  font-size: 26rem;
  line-height: 1;
  background: transparent;
  border: 0;
}

.feedback-switch-row,
.feedback-field,
.feedback-file-row {
  min-height: 46rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.feedback-switch-row {
  justify-content: space-between;
}

.feedback-switch-row input {
  width: 18rem;
  height: 18rem;
  accent-color: #fe2c55;
}

.feedback-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rem;
  padding: 10rem 0;
}

.feedback-field input[type='range'] {
  flex: 1;
  accent-color: #fe2c55;
}

.feedback-field select {
  min-width: 130rem;
  padding: 7rem 8rem;
  color: #fff;
  background: #2b2b30;
  border: 1px solid #44444b;
  border-radius: 6rem;
}

.feedback-file-row {
  justify-content: space-between;
  gap: 12rem;
  padding: 10rem 0;
}

.feedback-file-row strong,
.feedback-file-row small {
  display: block;
}

.feedback-file-row small {
  margin-top: 3rem;
  color: #999;
  font-size: 12rem;
}

.feedback-file-button,
.feedback-actions button {
  padding: 8rem 12rem;
  border-radius: 6rem;
  font-size: 13rem;
}

.feedback-file-button {
  color: #fff;
  background: #303036;
}

.feedback-file-button input {
  display: none;
}

.feedback-actions {
  justify-content: flex-end;
  gap: 8rem;
  padding-top: 18rem;
}

.feedback-actions button {
  border: 0;
}

.feedback-actions .secondary {
  color: #ddd;
  background: #303036;
}

.feedback-actions .primary {
  color: #fff;
  background: #fe2c55;
}
</style>
