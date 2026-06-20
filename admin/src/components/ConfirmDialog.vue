<template>
  <teleport to="body">
    <div v-if="show" class="confirm-mask" @click.self="onCancel">
      <div class="confirm-dialog">
        <component :is="iconMap[type]" :size="40" class="confirm-icon" />
        <h3 class="confirm-title">{{ title }}</h3>
        <p class="confirm-message">{{ message }}</p>
        <div class="confirm-btns">
          <button class="btn-cancel" @click="onCancel" :disabled="loading">{{ cancelText }}</button>
          <button :class="['btn-confirm', 'confirm-' + type]" @click="onConfirm" :disabled="loading">
            {{ loading ? '处理中...' : confirmText }}
          </button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import { TriangleAlert, OctagonX, Info } from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  show: boolean
  title?: string
  message?: string
  type?: 'warning' | 'danger' | 'info'
  confirmText?: string
  cancelText?: string
  loading?: boolean
}>(), {
  title: '确认操作',
  message: '',
  type: 'warning',
  confirmText: '确认',
  cancelText: '取消',
  loading: false
})

const emit = defineEmits(['confirm', 'cancel'])

const iconMap: Record<string, any> = { warning: TriangleAlert, danger: OctagonX, info: Info }

function onConfirm() { emit('confirm') }
function onCancel() { emit('cancel') }
</script>

<style scoped>
.confirm-mask {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}
.confirm-dialog {
  background: #fff;
  border-radius: 12px;
  padding: 32px;
  width: 380px;
  text-align: center;
  box-shadow: 0 8px 32px rgba(0,0,0,0.2);
}
.confirm-icon { margin-bottom: 12px; }
.confirm-icon.confirm-warning { color: #f59e0b; }
.confirm-icon.confirm-danger { color: #ef4444; }
.confirm-icon.confirm-info { color: #3b82f6; }
.confirm-title { font-size: 16px; font-weight: 600; color: #333; margin-bottom: 8px; }
.confirm-message { font-size: 14px; color: #888; margin-bottom: 24px; line-height: 1.5; }
.confirm-btns { display: flex; gap: 12px; justify-content: center; }
.confirm-btns button {
  padding: 8px 24px;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  border: none;
  transition: opacity 0.2s;
}
.confirm-btns button:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-cancel { background: #f3f4f6; color: #666; border: 1px solid #ddd; }
.btn-confirm { color: #fff; }
.confirm-warning { background: #f59e0b; }
.confirm-danger { background: #ef4444; }
.confirm-info { background: #3b82f6; }
</style>
