<script setup>
const props = defineProps({
  typeKey: { type: String, required: true },
  label: { type: String, required: true },
  badge: { type: String, required: true },
  selected: { type: Boolean, default: false },
  count: { type: Number, default: 1 }
})

const emit = defineEmits(['toggle', 'update:count'])

function handleToggle() {
  emit('toggle', props.typeKey)
}

function handleDecrease() {
  if (props.count > 1) {
    emit('update:count', props.typeKey, props.count - 1)
  }
}

function handleIncrease() {
  if (props.count < 99) {
    emit('update:count', props.typeKey, props.count + 1)
  }
}
</script>

<template>
  <div
    class="qtype-card"
    :class="{ 'is-selected': selected }"
    @click="handleToggle"
    role="checkbox"
    :aria-checked="selected"
    :tabindex="0"
    @keydown.space.prevent="handleToggle"
    @keydown.enter.prevent="handleToggle"
  >
    <div class="qtype-header">
      <div class="qtype-check">
        <svg v-if="selected" class="qtype-check-on" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="4" fill="currentColor" stroke="currentColor"/>
          <polyline points="8 12 11 15 16 9" stroke="#fff"/>
        </svg>
        <svg v-else class="qtype-check-off" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="4" fill="none"/>
        </svg>
      </div>
      <span class="qtype-label">{{ label }}</span>
      <span class="qtype-badge">{{ badge }}</span>
    </div>
    <div v-if="selected" class="qtype-counter" @click.stop>
      <button
        type="button"
        class="counter-btn"
        :class="{ 'is-disabled': count <= 1 }"
        :disabled="count <= 1"
        @click="handleDecrease"
        aria-label="减少数量"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
      </button>
      <span class="counter-value">{{ count }}</span>
      <button
        type="button"
        class="counter-btn"
        :class="{ 'is-disabled': count >= 99 }"
        :disabled="count >= 99"
        @click="handleIncrease"
        aria-label="增加数量"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.qtype-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border: 2px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  cursor: pointer;
  transition: all var(--transition-fast);
  user-select: none;
  min-height: 80px;
}

.qtype-card:hover {
  border-color: var(--color-primary-light);
  box-shadow: var(--shadow-sm);
}

.qtype-card.is-selected {
  border-color: var(--color-primary);
  background: var(--color-primary-bg);
  box-shadow: 0 0 0 1px var(--color-primary-light);
}

.qtype-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.qtype-check {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.qtype-check-on { color: var(--color-primary); }
.qtype-check-off { color: var(--color-text-muted); }

.qtype-label {
  font-size: var(--fs-md);
  font-weight: var(--fw-semibold);
  color: var(--color-text-primary);
  flex: 1;
}

.qtype-badge {
  font-size: var(--fs-2xs);
  font-weight: var(--fw-medium);
  color: var(--color-primary);
  background: var(--color-primary-bg);
  padding: 2px 8px;
  border-radius: var(--radius-full);
  white-space: nowrap;
  flex-shrink: 0;
}

.qtype-card.is-selected .qtype-badge {
  background: var(--color-surface);
}

.qtype-counter {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding-top: var(--space-2);
  border-top: 1px solid var(--color-primary-border);
}

.counter-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 1.5px solid var(--color-border);
  color: var(--color-text-secondary);
  transition: all var(--transition-fast);
}

.counter-btn:hover:not(.is-disabled) {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}

.counter-btn.is-disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.counter-value {
  font-size: var(--fs-lg);
  font-weight: var(--fw-bold);
  color: var(--color-primary);
  min-width: 28px;
  text-align: center;
}

@media (max-width: 640px) {
  .qtype-card {
    padding: var(--space-3) var(--space-4);
    min-height: 72px;
  }

  .counter-btn {
    width: 44px;
    height: 44px;
  }
}
</style>
