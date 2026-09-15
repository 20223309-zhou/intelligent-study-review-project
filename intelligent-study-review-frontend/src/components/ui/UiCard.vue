<script setup>
defineProps({
  /** 是否带内边距（表格 / 分栏卡片可关掉自行控制） */
  padded: { type: Boolean, default: true },
  /** 悬浮抬升（可点击卡片） */
  hoverable: { type: Boolean, default: false },
  /** 去掉边框（纯色块场景） */
  borderless: { type: Boolean, default: false },
  /** 圆角档位：md · lg · xl */
  radius: { type: String, default: 'lg' },
  /** 阴影档位：none · sm · md */
  elevation: { type: String, default: 'sm' }
})
</script>

<template>
  <div
    class="ui-card"
    :class="[
      `r-${radius}`,
      `e-${elevation}`,
      { 'is-padded': padded, 'is-hoverable': hoverable, 'is-borderless': borderless }
    ]"
  >
    <div v-if="$slots.header" class="ui-card-header"><slot name="header" /></div>
    <slot />
    <div v-if="$slots.footer" class="ui-card-footer"><slot name="footer" /></div>
  </div>
</template>

<style scoped>
.ui-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.ui-card.r-md { border-radius: var(--radius-md); }
.ui-card.r-lg { border-radius: var(--radius-lg); }
.ui-card.r-xl { border-radius: var(--radius-xl); }

.ui-card.e-none { box-shadow: none; }
.ui-card.e-sm { box-shadow: var(--shadow-sm); }
.ui-card.e-md { box-shadow: var(--shadow-md); }

.ui-card.is-padded { padding: var(--space-5); }
.ui-card.is-borderless { border-color: transparent; }

.ui-card.is-hoverable:hover {
  border-color: var(--color-primary-light);
  box-shadow: var(--shadow-md);
}

.ui-card-header {
  margin-bottom: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--color-border-light);
}

.ui-card-footer {
  margin-top: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-border-light);
}
</style>
