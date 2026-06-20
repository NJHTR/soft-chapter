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
  border-radius: 12px;
  padding: 24px;
  box-shadow:
    0 0 0 1px rgba(0,0,0,0.03),
    0 2px 4px rgba(0,0,0,0.04),
    0 8px 24px rgba(0,0,0,0.06);
  border-top: 1px solid rgba(255,255,255,0.8);
  transition: box-shadow 0.25s ease, transform 0.25s ease;
}
.noise-card::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  z-index: 1;
}
.noise-card.default::after {
  filter: url(#noise-filter);
}
.noise-card.subtle::after {
  filter: url(#noise-filter-subtle);
}
.noise-card.elevated::after {
  filter: url(#noise-filter);
}
.noise-card:hover {
  box-shadow:
    0 0 0 1px rgba(0,0,0,0.04),
    0 4px 8px rgba(0,0,0,0.06),
    0 12px 32px rgba(0,0,0,0.1);
  transform: translateY(-1px);
}
.noise-card.elevated {
  box-shadow:
    0 0 0 1px rgba(0,0,0,0.04),
    0 4px 8px rgba(0,0,0,0.06),
    0 16px 40px rgba(0,0,0,0.1);
}
.noise-card.elevated:hover {
  box-shadow:
    0 0 0 1px rgba(0,0,0,0.05),
    0 8px 16px rgba(0,0,0,0.08),
    0 24px 48px rgba(0,0,0,0.14);
  transform: translateY(-2px);
}
</style>
