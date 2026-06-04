<template>
  <div class="avatar-cropper" v-if="visible" @touchmove.prevent>
    <div class="mask"></div>
    <!-- 圆形镂空区域 -->
    <div class="crop-area" :style="{ width: cropSize + 'px', height: cropSize + 'px' }">
      <div class="crop-circle"></div>
      <img
        ref="imgRef"
        :src="imageUrl"
        class="crop-image"
        :style="imgStyle"
        @load="onImgLoad"
        @mousedown="onDragStart"
        @touchstart="onDragStart"
      />
    </div>
    <!-- 底部操作栏 -->
    <div class="controls">
      <span class="btn-cancel" @click="$emit('cancel')">取消</span>
      <div class="zoom-bar">
        <img src="../assets/img/icon/message/chat/narrow.png" alt="" class="zoom-icon" />
        <input type="range" min="1" max="300" :value="scalePercent" @input="onZoom" class="slider" />
        <img src="../assets/img/icon/message/chat/narrow.png" alt="" class="zoom-icon zoom-out" />
      </div>
      <span class="btn-confirm" @click="cropAndConfirm">确定</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, onUnmounted } from 'vue'

const props = defineProps<{ visible: boolean; imageUrl: string }>()
const emit = defineEmits<{ cancel: []; confirm: [blob: Blob] }>()

const imgRef = ref<HTMLImageElement>()
const cropSize = ref(Math.min(document.body.clientWidth - 60, 300))
const scale = ref(100)
const offset = reactive({ x: 0, y: 0 })
const imgNatural = reactive({ w: 0, h: 0 })
let dragging = false
let dragStart = { x: 0, y: 0, imgX: 0, imgY: 0 }

const scalePercent = computed(() => Math.round(scale.value))

const imgStyle = computed(() => {
  const ratio = scale.value / 100
  const w = imgNatural.w * ratio
  const h = imgNatural.h * ratio
  return {
    width: w + 'px',
    height: h + 'px',
    transform: `translate(${offset.x}px, ${offset.y}px)`
  }
})

function onImgLoad() {
  const img = imgRef.value
  if (!img) return
  imgNatural.w = img.naturalWidth
  imgNatural.h = img.naturalHeight
  // 初始缩放：让图片短边撑满裁剪框
  const minDim = Math.min(imgNatural.w, imgNatural.h)
  scale.value = Math.round((cropSize.value / minDim) * 100)
  // 居中
  const ratio = scale.value / 100
  offset.x = (cropSize.value - imgNatural.w * ratio) / 2
  offset.y = (cropSize.value - imgNatural.h * ratio) / 2
}

function onZoom(e: Event) {
  scale.value = Number((e.target as HTMLInputElement).value)
}

function onDragStart(e: MouseEvent | TouchEvent) {
  dragging = true
  const pos = 'touches' in e ? e.touches[0] : e
  dragStart = { x: pos.clientX, y: pos.clientY, imgX: offset.x, imgY: offset.y }

  const onMove = (ev: MouseEvent | TouchEvent) => {
    if (!dragging) return
    const p = 'touches' in ev ? ev.touches[0] : ev
    offset.x = dragStart.imgX + (p.clientX - dragStart.x)
    offset.y = dragStart.imgY + (p.clientY - dragStart.y)
  }

  const onEnd = () => {
    dragging = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onEnd)
    document.removeEventListener('touchmove', onMove)
    document.removeEventListener('touchend', onEnd)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onEnd)
  document.addEventListener('touchmove', onMove, { passive: false })
  document.addEventListener('touchend', onEnd)
}

function cropAndConfirm() {
  const img = imgRef.value
  if (!img) return
  const canvas = document.createElement('canvas')
  const size = cropSize.value
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  // 圆形裁剪路径
  ctx.beginPath()
  ctx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2)
  ctx.clip()
  // 绘制图片
  const ratio = scale.value / 100
  ctx.drawImage(img, offset.x, offset.y, imgNatural.w * ratio, imgNatural.h * ratio)
  canvas.toBlob(blob => {
    if (blob) emit('confirm', blob)
  }, 'image/png')
}
</script>

<style scoped lang="less">
.avatar-cropper {
  position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; z-index: 999;
  display: flex; align-items: center; justify-content: center; flex-direction: column;
  background: #000;

  .mask {
    position: absolute; top: 0; left: 0; width: 100%; height: 100%;
    background: rgba(0,0,0,0.85);
  }

  .crop-area {
    position: relative; z-index: 1; overflow: hidden; border-radius: 50%;
    box-shadow: 0 0 0 9999px rgba(0,0,0,0.85);

    .crop-circle {
      position: absolute; top: 0; left: 0; width: 100%; height: 100%;
      border-radius: 50%; border: 2px solid rgba(255,255,255,0.3);
      box-shadow: inset 0 0 0 2px rgba(255,255,255,0.2);
      pointer-events: none; z-index: 2;
    }

    .crop-image {
      position: absolute; cursor: grab; user-select: none; -webkit-user-drag: none;
    }
  }

  .controls {
    z-index: 3; position: fixed; bottom: 0; left: 0; right: 0;
    display: flex; align-items: center; justify-content: space-between;
    padding: 20rem 30rem 40rem; color: white; font-size: 16rem;

    .btn-cancel { cursor: pointer; opacity: 0.8; }
    .btn-confirm { cursor: pointer; color: #fe2c55; font-weight: bold; }

    .zoom-bar {
      display: flex; align-items: center; gap: 10rem; flex: 1; padding: 0 20rem;

      .zoom-icon { width: 16rem; height: 16rem; opacity: 0.6; }
      .zoom-out { width: 24rem; height: 24rem; }

      .slider {
        flex: 1; height: 4rem; -webkit-appearance: none; appearance: none;
        background: rgba(255,255,255,0.3); border-radius: 2rem; outline: none;
        &::-webkit-slider-thumb {
          -webkit-appearance: none; appearance: none;
          width: 20rem; height: 20rem; border-radius: 50%;
          background: #fff; cursor: pointer;
        }
      }
    }
  }
}
</style>
