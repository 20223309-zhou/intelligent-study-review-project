<script setup>
import BrandLogo from './BrandLogo.vue'

const points = [
  '多 Agent 协作：先规划、再命题、最后审查回炉',
  '交卷即批改，并给出按题型与知识点的掌握度分析',
  '一键导出 Word / PDF，含独立的标准答案卷'
]
</script>

<template>
  <div class="auth-shell">
    <!-- 左侧品牌区 -->
    <aside class="auth-brand">
      <span class="ab-blob ab-blob-a" aria-hidden="true"></span>
      <span class="ab-blob ab-blob-b" aria-hidden="true"></span>

      <div class="ab-inner">
        <BrandLogo :height="42" />
        <h2 class="ab-title">把出卷这件苦活<br />交给 AI</h2>
        <p class="ab-desc">填好学科与题型配置，几分钟拿到一份带答案、解析和知识点标签的完整试卷。</p>
        <ul class="ab-points">
          <li v-for="p in points" :key="p">{{ p }}</li>
        </ul>
      </div>
    </aside>

    <!-- 右侧表单区 -->
    <section class="auth-panel">
      <div class="auth-panel-inner">
        <slot />
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(340px, 440px);
  max-width: 1000px;
  min-height: 560px;
  margin: 0 auto;
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

/* ===== 左侧品牌区 ===== */
.auth-brand {
  position: relative;
  display: flex;
  align-items: center;
  padding: var(--space-10);
  /* 用深色品牌面而非 --color-primary-gradient：后者配白色小字只有约 3:1 对比度 */
  background: var(--color-primary-gradient-deep);
  color: var(--color-text-inverse);
  overflow: hidden;
}

/* 两块柔光斑：纯装饰，只为让色块有层次、不单调。
   这里用的是「白色半透明覆盖层」，不是语义色，故不纳入令牌。 */
.ab-blob {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}
.ab-blob-a {
  top: -120px;
  right: -100px;
  width: 340px;
  height: 340px;
  background: rgba(255, 255, 255, 0.14);
}
.ab-blob-b {
  bottom: -110px;
  left: -80px;
  width: 260px;
  height: 260px;
  background: rgba(255, 255, 255, 0.09);
}

.ab-inner { position: relative; z-index: 1; }

.ab-title {
  margin-top: var(--space-8);
  font-size: var(--fs-2xl);
  font-weight: var(--fw-heavy);
  line-height: var(--lh-tight);
  letter-spacing: 0.5px;
}

.ab-desc {
  margin-top: var(--space-3);
  font-size: var(--fs-sm);
  line-height: var(--lh-relaxed);
  /* 透明度低于 0.95 时，14px 小字对比度会掉到 4.5:1 以下 */
  color: rgba(255, 255, 255, 0.95);
}

.ab-points {
  margin-top: var(--space-6);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  list-style: none;
}
.ab-points li {
  position: relative;
  padding-left: var(--space-6);
  font-size: var(--fs-sm);
  line-height: var(--lh-normal);
  color: var(--color-text-inverse);
}
.ab-points li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.62em;
  width: 12px;
  height: 3px;
  border-radius: var(--radius-full);
  background: rgba(255, 255, 255, 0.55);
}

/* ===== 右侧表单区 ===== */
.auth-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-10) var(--space-8);
}
.auth-panel-inner {
  width: 100%;
  max-width: 360px;
}

/* 窄屏：改成上下结构，品牌区收成一条横幅 */
@media (max-width: 900px) {
  .auth-shell {
    grid-template-columns: 1fr;
    min-height: 0;
  }
  .auth-brand { padding: var(--space-6); }
  .ab-title {
    margin-top: var(--space-5);
    font-size: var(--fs-xl);
  }
  .ab-desc,
  .ab-points { display: none; }
  .auth-panel { padding: var(--space-6) var(--space-5) var(--space-8); }
}

@media (max-width: 480px) {
  .auth-brand { padding: var(--space-5); }
  .auth-panel { padding: var(--space-5) var(--space-4) var(--space-6); }
}
</style>
