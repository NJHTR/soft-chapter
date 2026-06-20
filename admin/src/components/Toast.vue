<template>
  <teleport to="body">
    <div class="toast-container">
      <transition-group name="toast">
        <div v-for="t in toasts" :key="t.id" :class="['toast-item', 'toast-' + t.type]">
          <span class="toast-icon">{{ iconMap[t.type] }}</span>
          <span class="toast-msg">{{ t.message }}</span>
        </div>
      </transition-group>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import { toasts } from '@/utils/toast'

const iconMap = { success: '✓', error: '✕', warning: '⚠', info: 'ℹ' }
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 10000;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}
.toast-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border-radius: 8px;
  font-size: 14px;
  color: #fff;
  min-width: 200px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  pointer-events: auto;
  cursor: default;
}
.toast-icon { font-weight: 700; font-size: 16px; flex-shrink: 0; }
.toast-msg { flex: 1; }
.toast-success { background: #10b981; }
.toast-error { background: #ef4444; }
.toast-warning { background: #f59e0b; }
.toast-info { background: #3b82f6; }

.toast-enter-active { transition: all 0.3s ease-out; }
.toast-leave-active { transition: all 0.2s ease-in; }
.toast-enter-from { opacity: 0; transform: translateX(40px); }
.toast-leave-to { opacity: 0; transform: translateX(40px); }
</style>
