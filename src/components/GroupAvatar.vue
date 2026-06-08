<template>
  <div class="group-avatar" :style="containerStyle">
    <template v-if="displayAvatars.length">
      <img
        v-for="(url, i) of displayAvatars"
        :key="i"
        :src="_checkImgUrl(url)"
        class="cell"
        :style="getCellStyle(i)"
        @error="onImgError"
      />
    </template>
    <img v-else :src="fallback" class="single" :style="singleStyle" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { _checkImgUrl } from '@/utils'
import defaultGroupPng from '@/assets/img/icon/people-gray.png'

const props = withDefaults(
  defineProps<{
    avatars: string[]
    size?: number
    fallback?: string
  }>(),
  {
    size: 44,
    fallback: () => defaultGroupPng
  }
)

const displayAvatars = computed(() => props.avatars.filter(Boolean).slice(0, 9))
const n = computed(() => displayAvatars.value.length)

const cols = computed(() => (n.value <= 4 ? 2 : 3))
const rows = computed(() => Math.ceil(n.value / cols.value))

const containerStyle = computed(() => {
  const s = props.size + 'rem'
  const base: Record<string, string> = { width: s, height: s }

  if (n.value >= 2) {
    base.display = 'flex'
    base.flexWrap = 'wrap'
  }

  return base
})

function getCellStyle(i: number) {
  const c = cols.value
  const r = rows.value
  const w = 100 / c + '%'
  const h = 100 / r + '%'
  const leftover = n.value % c

  // 不完整末行居中
  if (leftover > 0) {
    const lastRowStart = Math.floor(n.value / c) * c
    if (i >= lastRowStart && i === lastRowStart) {
      const offset = ((c - leftover) * (100 / c)) / 2
      return { width: w, height: h, marginLeft: offset + '%' }
    }
  }

  return { width: w, height: h }
}

const singleStyle = computed(() => ({
  width: props.size + 'rem',
  height: props.size + 'rem'
}))

function onImgError(e: Event) {
  ;(e.target as HTMLImageElement).style.display = 'none'
}
</script>

<style scoped lang="less">
.group-avatar {
  border-radius: 6rem;
  overflow: hidden;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.06);

  .cell {
    object-fit: cover;
    background: rgba(255, 255, 255, 0.08);
  }

  .single {
    object-fit: cover;
    border-radius: 6rem;
  }
}
</style>
