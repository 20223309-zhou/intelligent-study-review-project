<script setup>
import { computed, useAttrs } from 'vue'
import UiSpinner from './UiSpinner.vue'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  /** primary · secondary · ghost · danger · success · warning */
  variant: { type: String, default: 'secondary' },
  /** sm · md · lg */
  size: { type: String, default: 'md' },
  block: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  /** 传入则渲染为 router-link */
  to: { type: [String, Object], default: null },
  /** 传入则渲染为 <a> */
  href: { type: String, default: null },
  /** 原生 button 的 type */
  nativeType: { type: String, default: 'button' }
})

const attrs = useAttrs()

const tag = computed(() => (props.to ? 'router-link' : props.href ? 'a' : 'button'))

const bindings = computed(() => {
  // 非原生 button 时把 type / disabled 摘掉，避免挂到 <a> 上
  const { type, disabled, ...rest } = attrs
  if (props.to) return { ...rest, to: props.to }
  if (props.href) return { ...rest, href: props.href }
  return { ...rest, type: props.nativeType, disabled: props.disabled || props.loading }
})

const spinnerSize = computed(() => (props.size === 'lg' ? 18 : props.size === 'sm' ? 13 : 15))
</script>

<template>
  <component
    :is="tag"
    class="ui-btn"
    :class="[`v-${variant}`, `s-${size}`, { 'is-block': block, 'is-loading': loading, 'is-disabled': disabled }]"
    v-bind="bindings"
  >
    <UiSpinner v-if="loading" :size="spinnerSize" :tone="['primary', 'secondary', 'warning'].includes(variant) ? 'primary' : 'light'" />
    <slot v-else name="icon" />
    <span v-if="$slots.default" class="ui-btn-text"><slot /></span>
  </component>
</template>

<style scoped>
.ui-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font-family: var(--font-sans);
  font-weight: var(--fw-semibold);
  line-height: 1.2;
  white-space: nowrap;
  text-decoration: none;
  cursor: pointer;
  transition: background-color var(--transition-fast), border-color var(--transition-fast),
    color var(--transition-fast), box-shadow var(--transition-fast), transform var(--transition-fast);
}

.ui-btn.is-block { width: 100%; }

.ui-btn.is-disabled,
.ui-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none !important;
  transform: none !important;
}

.ui-btn.is-loading { cursor: wait; opacity: 0.85; }

/* ===== 尺寸 ===== */
.ui-btn.s-sm { padding: 6px 12px; font-size: var(--fs-xs); }
.ui-btn.s-md { padding: 9px 16px; font-size: var(--fs-sm); }
.ui-btn.s-lg { padding: 12px 30px; font-size: var(--fs-md); font-weight: var(--fw-bold); }

/* ===== 变体 ===== */
.ui-btn.v-primary {
  background: var(--color-primary);
  color: var(--color-text-inverse);
}
.ui-btn.v-primary:hover:not(:disabled):not(.is-disabled) { background: var(--color-primary-dark); }

.ui-btn.v-secondary {
  background: var(--color-surface);
  border-color: var(--color-border);
  color: var(--color-text-secondary);
}
.ui-btn.v-secondary:hover:not(:disabled):not(.is-disabled) {
  border-color: var(--color-primary-light);
  color: var(--color-primary);
}

.ui-btn.v-ghost {
  background: transparent;
  color: var(--color-text-secondary);
}
.ui-btn.v-ghost:hover:not(:disabled):not(.is-disabled) {
  background: var(--color-bg);
  color: var(--color-text-primary);
}

.ui-btn.v-success {
  background: var(--color-success-dark);
  color: var(--color-text-inverse);
}
.ui-btn.v-success:hover:not(:disabled):not(.is-disabled) { background: var(--color-success-text); }

.ui-btn.v-danger {
  background: var(--color-error);
  color: var(--color-text-inverse);
}
.ui-btn.v-danger:hover:not(:disabled):not(.is-disabled) { background: var(--color-error-dark); }

/* 软色（低干扰的次级动作，如「错题重做」） */
.ui-btn.v-warning {
  background: var(--color-warning-bg);
  border-color: var(--color-warning-border);
  color: var(--color-warning-text);
}
.ui-btn.v-warning:hover:not(:disabled):not(.is-disabled) { background: var(--color-warning-bg-strong); }

.ui-btn.v-soft {
  background: var(--color-primary-bg);
  color: var(--color-primary-dark);
}
.ui-btn.v-soft:hover:not(:disabled):not(.is-disabled) { background: var(--color-primary-border); }

.ui-btn-text { display: inline-block; }
</style>
