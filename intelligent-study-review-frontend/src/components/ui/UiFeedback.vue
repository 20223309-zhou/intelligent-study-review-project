<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { uiState, resolveConfirm } from '@/utils/ui.js'
import UiButton from './UiButton.vue'

const confirmBtn = ref(null)

const visible = computed(() => !!uiState.confirm)

function onConfirm() { resolveConfirm(true) }
function onCancel() { resolveConfirm(false) }

// 用文档级监听而不是元素级，避免焦点不在弹窗上时 Esc / Enter 失效
function onKeydown(e) {
  if (!visible.value) return
  if (e.key === 'Escape') { e.preventDefault(); onCancel() }
  else if (e.key === 'Enter') { e.preventDefault(); onConfirm() }
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})

watch(visible, v => {
  if (v) {
    document.body.style.overflow = 'hidden'
    // UiButton 的根元素是原生按钮，组件实例需取 $el
    nextTick(() => {
      const el = confirmBtn.value?.$el || confirmBtn.value
      el?.focus?.()
    })
  } else {
    document.body.style.overflow = ''
  }
})
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="ui-mask" @click.self="onCancel">
      <div class="ui-dialog" role="dialog" aria-modal="true" :aria-label="uiState.confirm.title">
        <div class="ui-title">{{ uiState.confirm.title }}</div>
        <div class="ui-message">{{ uiState.confirm.message }}</div>
        <div class="ui-actions">
          <UiButton size="sm" @click="onCancel">{{ uiState.confirm.cancelText }}</UiButton>
          <UiButton
            ref="confirmBtn"
            size="sm"
            :variant="uiState.confirm.danger ? 'danger' : 'primary'"
            @click="onConfirm"
          >
            {{ uiState.confirm.confirmText }}
          </UiButton>
        </div>
      </div>
    </div>
  </Teleport>

  <Teleport to="body">
    <div class="ui-toasts" aria-live="polite">
      <TransitionGroup name="ui-toast">
        <div v-for="t in uiState.toasts" :key="t.id" class="ui-toast" :class="'is-' + t.type">
          <span class="ui-toast-dot"></span>
          <span class="ui-toast-text">{{ t.message }}</span>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.ui-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-5);
  background: var(--color-overlay);
  backdrop-filter: blur(2px);
  font-family: var(--font-sans);
}

.ui-dialog {
  width: 100%;
  max-width: 400px;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xl);
  padding: 22px var(--space-6) 18px;
  animation: ui-pop 0.18s ease-out;
}

@keyframes ui-pop {
  from { opacity: 0; transform: translateY(8px) scale(0.98); }
  to { opacity: 1; transform: none; }
}

.ui-title {
  font-size: var(--fs-md);
  font-weight: var(--fw-bold);
  color: var(--color-text-primary);
}

.ui-message {
  margin-top: var(--space-2);
  font-size: var(--fs-sm);
  line-height: var(--lh-normal);
  color: var(--color-text-secondary);
  white-space: pre-wrap;
}

.ui-actions {
  margin-top: var(--space-5);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* ===== Toast ===== */
.ui-toasts {
  position: fixed;
  top: var(--space-4);
  left: 50%;
  transform: translateX(-50%);
  z-index: 2100;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  pointer-events: none;
  font-family: var(--font-sans);
}

.ui-toast {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  max-width: min(90vw, 420px);
  padding: 10px var(--space-4);
  border-radius: var(--radius-full);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-lg);
  font-size: var(--fs-sm);
  color: var(--color-text-primary);
}

.ui-toast-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--color-info);
}
.ui-toast.is-success .ui-toast-dot { background: var(--color-success); }
.ui-toast.is-warn .ui-toast-dot { background: var(--color-warning); }
.ui-toast.is-error .ui-toast-dot { background: var(--color-error); }

.ui-toast-text { line-height: var(--lh-snug); }

.ui-toast-enter-active, .ui-toast-leave-active { transition: all 0.22s ease; }
.ui-toast-enter-from, .ui-toast-leave-to { opacity: 0; transform: translateY(-10px); }
</style>
