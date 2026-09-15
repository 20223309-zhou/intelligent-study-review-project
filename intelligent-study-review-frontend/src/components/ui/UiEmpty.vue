<script setup>
defineProps({
  /** 主文案 */
  text: { type: String, default: '暂无数据' },
  /** 补充说明 */
  description: { type: String, default: '' },
  /** 是否作为卡片展示（自带底色与圆角） */
  card: { type: Boolean, default: true }
})
</script>

<template>
  <div class="ui-empty" :class="{ 'is-card': card }">
    <div class="ui-empty-icon">
      <slot name="icon">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"
          stroke-linecap="round" stroke-linejoin="round">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <polyline points="14 2 14 8 20 8" />
        </svg>
      </slot>
    </div>
    <p class="ui-empty-text">{{ text }}</p>
    <p v-if="description" class="ui-empty-desc">{{ description }}</p>
    <div v-if="$slots.action" class="ui-empty-action"><slot name="action" /></div>
  </div>
</template>

<style scoped>
.ui-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: var(--space-12) var(--space-5);
  color: var(--color-text-muted);
}

.ui-empty.is-card {
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
}

.ui-empty-icon {
  color: var(--color-border-strong);
  margin-bottom: var(--space-4);
  display: flex;
}

.ui-empty-text {
  font-size: var(--fs-md);
  color: var(--color-text-secondary);
}

.ui-empty-desc {
  margin-top: var(--space-1);
  font-size: var(--fs-sm);
  color: var(--color-text-muted);
}

.ui-empty-action { margin-top: var(--space-5); }
</style>
