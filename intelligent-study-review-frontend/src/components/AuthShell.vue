<script setup>
import AuthStickers from './AuthStickers.vue'

defineProps({
  /** 卷头标题，如「登录」「创建账号」 */
  title: { type: String, required: true },
  /** 标题下的一行说明 */
  subtitle: { type: String, default: '' }
})
</script>

<template>
  <div class="auth-page">
    <!-- 背景贴纸：三角尺 / 地球仪 / 圆规 / 原子 / 书本 / 量角器 / 三角形 -->
    <AuthStickers />

    <div class="paper">
      <!-- 卷头：短横线 + 宋体标题 + 说明 -->
      <header class="paper-head">
        <span class="paper-mark" aria-hidden="true"></span>
        <h1 class="paper-title">{{ title }}</h1>
        <p v-if="subtitle" class="paper-sub">{{ subtitle }}</p>
      </header>

      <div class="paper-body">
        <slot />
      </div>

      <footer class="paper-foot">
        <slot name="footer" />
      </footer>
    </div>
  </div>
</template>

<style scoped>
/* ===== 版面 =====
   140px 是「顶栏 73px + app-main 上下内边距 64px」的近似值，留几像素余量。
   用它让卡片在视口里垂直居中，同时不产生多余的滚动条。 */
.auth-page {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 140px);
  padding: var(--space-4) 0 var(--space-8);
}

/* 本页专属背景层：中心柔光 + 极淡点阵。
   用 fixed 伪元素铺满视口，而不是只铺 .app-main 那一条 —— 后者在宽屏上会出现横向接缝。
   z-index: 0 让它在全局柔光层（body::before, -1）之上、纸卡（.paper, 1）之下。
   只在本页生效，生成 / 列表 / 详情这些工作页仍然保持安静。 */
.auth-page::before {
  content: '';
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background-image:
    /* ① 卡片正后方的柔光：让纸卡像被一盏灯照住，而不是浮在平涂底色上 */
    radial-gradient(640px 440px at 50% 52%, rgba(18, 146, 74, 0.10), transparent 72%),
    /* ② 点阵：答题卡方格的隐喻。加了贴纸之后退成「纸的纹理」，
       透明度从 0.10 降到 0.07 —— 否则两套纹理互相打架 */
    radial-gradient(circle, rgba(31, 42, 36, 0.07) 1px, transparent 1px);
  background-size: 100% 100%, 24px 24px;
  /* 点阵自上而下淡出，避免整屏网格带来的压迫感 */
  mask-image: linear-gradient(to bottom, #000 0%, #000 58%, transparent 100%);
  -webkit-mask-image: linear-gradient(to bottom, #000 0%, #000 58%, transparent 100%);
}

/* 一张纸：白底、细边、轻微投影，从柔光背景上浮起来。
   z-index: 1 让它压在上面的点阵层之上。 */
.paper {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 460px;
  padding: var(--space-10) var(--space-10) var(--space-8);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
}

/* ===== 卷头 ===== */
.paper-head {
  margin-bottom: var(--space-6);
  padding-bottom: var(--space-5);
  text-align: center;
  /* 卷面分栏用的虚线，与试卷详情页的行分隔是同一套语言 */
  border-bottom: 1px dashed var(--color-border);
}

.paper-mark {
  display: block;
  width: 36px;
  height: 3px;
  margin: 0 auto var(--space-5);
  background: var(--color-primary);
  border-radius: var(--radius-full);
}

.paper-title {
  /* 宋体 + 字距，是这个页面唯一的「试卷」记忆点 */
  font-family: var(--font-serif);
  font-size: var(--fs-2xl);
  font-weight: var(--fw-bold);
  line-height: var(--lh-tight);
  letter-spacing: 0.2em;
  /* 字距会让末字右侧多出一格，居中时整体偏左；用等量缩进补回来 */
  text-indent: 0.2em;
  color: var(--color-text-primary);
}

.paper-sub {
  margin-top: var(--space-2);
  font-size: var(--fs-xs);
  line-height: var(--lh-normal);
  color: var(--color-text-muted);
}

/* ===== 页脚 ===== */
.paper-foot {
  margin-top: var(--space-6);
  padding-top: var(--space-5);
  border-top: 1px dashed var(--color-border);
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--color-text-secondary);
}

/* 用 :deep() 是因为表单是插槽内容（属于 Login / Register），
   但在 DOM 上位于 .paper-body 之内 —— 这样两个页面不必各写一份表单样式。 */
.paper-body :deep(.auth-form) {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.paper-body :deep(.form-group) {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.paper-body :deep(.form-label) {
  font-size: var(--fs-xs);
  font-weight: var(--fw-medium);
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
}

/* 48px 高：既是舒适的点击区，也让「验证码输入框 + 图片」严丝合缝对齐。
   16px 起字号，避免 iOS 聚焦时页面自动缩放。 */
.paper-body :deep(.form-input) {
  width: 100%;
  height: 48px;
  padding: 0 var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text-primary);
  font-size: 1rem;
  outline: none;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}
.paper-body :deep(.form-input::placeholder) {
  color: var(--color-text-muted);
}
.paper-body :deep(.form-input:hover:not(:focus)) {
  border-color: var(--color-border-strong);
}
.paper-body :deep(.form-input:focus) {
  border-color: var(--color-primary);
  box-shadow: var(--ring-primary);
}
.paper-body :deep(.form-input.has-error) {
  border-color: var(--color-error);
}
.paper-body :deep(.form-input.has-error:focus) {
  box-shadow: var(--ring-error);
}

.paper-body :deep(.field-error) {
  font-size: var(--fs-xs);
  color: var(--color-error);
}

.paper-body :deep(.form-error) {
  padding: var(--space-2) var(--space-4);
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--color-error-text);
  background: var(--color-error-bg);
  border: 1px solid var(--color-error-border);
  border-radius: var(--radius-md);
}

.paper-body :deep(.form-success) {
  padding: var(--space-2) var(--space-4);
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--color-success-text);
  background: var(--color-success-bg);
  border: 1px solid var(--color-success-border);
  border-radius: var(--radius-md);
}

.paper-foot :deep(.auth-link) {
  color: var(--color-primary);
  font-weight: var(--fw-semibold);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-color var(--transition-fast);
}
.paper-foot :deep(.auth-link:hover) {
  border-bottom-color: var(--color-primary);
}

/* ===== 窄屏 ===== */
@media (max-width: 520px) {
  .auth-page {
    min-height: calc(100vh - 110px);
    align-items: flex-start;
    padding: var(--space-4) 0 var(--space-6);
  }
  .paper {
    padding: var(--space-8) var(--space-5) var(--space-6);
    border-radius: var(--radius-lg);
  }
  .paper-title { font-size: var(--fs-xl); }
}
</style>
