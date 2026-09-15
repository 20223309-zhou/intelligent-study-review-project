<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 直径（px） */
  size: { type: Number, default: 16 },
  /** primary=轨道灰+主色头 · light=白色（深底按钮内） · current=继承文字色 */
  tone: { type: String, default: 'primary' },
  label: { type: String, default: '加载中' }
})

const style = computed(() => {
  const w = Math.max(2, Math.round(props.size / 8))
  return {
    width: props.size + 'px',
    height: props.size + 'px',
    borderWidth: w + 'px'
  }
})
</script>

<template>
  <span class="ui-spinner" :class="'t-' + tone" :style="style" role="status" :aria-label="label"></span>
</template>

<style scoped>
.ui-spinner {
  display: inline-block;
  flex-shrink: 0;
  border-style: solid;
  border-radius: var(--radius-full);
  animation: ui-spin 0.7s linear infinite;
  vertical-align: middle;
}

.ui-spinner.t-primary {
  border-color: var(--color-border);
  border-top-color: var(--color-primary);
}

.ui-spinner.t-light {
  border-color: rgba(255, 255, 255, 0.35);
  border-top-color: #ffffff;
}

.ui-spinner.t-current {
  border-color: currentColor;
  border-top-color: transparent;
  opacity: 0.85;
}

@keyframes ui-spin {
  to { transform: rotate(360deg); }
}
</style>
