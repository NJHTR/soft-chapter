<template>
  <div class="noise-card" :class="variant">
    <slot />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  variant?: 'default' | 'subtle' | 'elevated'
}>()
</script>

<style scoped>
.noise-card {
  position: relative;
  background: #fff;
  border-radius: 6px;
  padding: 24px;
}
.noise-card.elevated {
  background: #fafbfc;
  border: 1px solid #f1f5f9;
}
</style>
