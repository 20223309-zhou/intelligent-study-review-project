<script setup>
defineProps({
  /** neutral · primary · success · warning · error · info */
  variant: { type: String, default: 'neutral' },
  /** sm · md */
  size: { type: String, default: 'sm' }
})
</script>

<template>
  <span class="ui-badge" :class="[`v-${variant}`, `s-${size}`]">
    <slot />
  </span>
</template>

<style scoped>
.ui-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  border: 1px solid transparent;
  border-radius: var(--radius-full);
  font-family: var(--font-sans);
  font-weight: var(--fw-semibold);
  line-height: 1.4;
}

.ui-badge.s-sm { padding: 2px 9px; font-size: var(--fs-2xs); }
.ui-badge.s-md { padding: 4px 12px; font-size: var(--fs-xs); }

.ui-badge.v-neutral {
  background: var(--color-neutral-bg);
  border-color: var(--color-neutral-border);
  color: var(--color-neutral-text);
}
.ui-badge.v-primary {
  background: var(--color-primary-bg);
  border-color: var(--color-primary-border);
  color: var(--color-primary-dark);
}
.ui-badge.v-success {
  background: var(--color-success-bg);
  border-color: var(--color-success-border);
  color: var(--color-success-text);
}
.ui-badge.v-warning {
  background: var(--color-warning-bg);
  border-color: var(--color-warning-border);
  color: var(--color-warning-text);
}
.ui-badge.v-error {
  background: var(--color-error-bg);
  border-color: var(--color-error-border);
  color: var(--color-error-text);
}
.ui-badge.v-info {
  background: var(--color-info-bg);
  border-color: var(--color-info-border);
  color: var(--color-info-text);
}
</style>
