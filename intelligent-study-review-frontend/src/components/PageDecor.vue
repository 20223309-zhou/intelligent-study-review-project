<script setup>
/**
 * 内容页的背景点缀（纯装饰）。
 *
 * 与登录页的 AuthStickers 是同一套语言（内联 SVG 线稿、currentColor、极低透明度），
 * 但这两页信息密度高，所以：
 *   · 透明度压到 0.05–0.08（登录页是 0.12–0.20）；
 *   · 位置贴着 1120px 内容栏的**外侧**，只在宽屏的留白里出现，不钻到卡片底下；
 *   · 另加一层 28px 点阵，用径向遮罩只在四周显影、中间淡出，是「纸的纹理」而不是噪点。
 * 全部 aria-hidden + pointer-events: none。z-index: -1 → 压在所有内容之下、页面柔光之上。
 */
</script>

<template>
  <div class="page-decor" aria-hidden="true">
    <div class="decor-grid"></div>

    <!-- 答题卡填涂格 -->
    <span class="decor d-bubble">
      <svg viewBox="0 0 64 64">
        <rect x="6" y="8" width="52" height="48" rx="4" />
        <circle cx="16" cy="20" r="3.2" /><circle cx="27" cy="20" r="3.2" /><circle cx="38" cy="20" r="3.2" /><circle cx="49" cy="20" r="3.2" />
        <circle cx="16" cy="32" r="3.2" /><circle cx="27" cy="32" r="3.2" /><circle cx="38" cy="32" r="3.2" /><circle cx="49" cy="32" r="3.2" />
        <circle cx="16" cy="44" r="3.2" /><circle cx="27" cy="44" r="3.2" /><circle cx="38" cy="44" r="3.2" /><circle cx="49" cy="44" r="3.2" />
        <path d="M27 20 l2 2 4-4" />
      </svg>
    </span>

    <!-- 铅笔 -->
    <span class="decor d-pencil">
      <svg viewBox="0 0 64 64">
        <path d="M12 52 L18 34 L42 10 L54 22 L30 46 Z" />
        <path d="M18 34 L30 46" />
        <path d="M42 10 L48 16" />
        <path d="M12 52 L10 58 L16 54" />
      </svg>
    </span>

    <!-- 坐标系与曲线 -->
    <span class="decor d-axis">
      <svg viewBox="0 0 64 64">
        <path d="M10 14 V54 H58" />
        <path d="M14 50 Q30 12 54 38" />
        <path d="M20 54 V51 M30 54 V51 M40 54 V51" />
      </svg>
    </span>

    <!-- 圆规 -->
    <span class="decor d-compass">
      <svg viewBox="0 0 64 64">
        <circle cx="32" cy="9" r="3.5" />
        <path d="M32 12.5 L21 48" />
        <path d="M32 12.5 L43 48" />
        <path d="M21 48 L19 55" />
        <path d="M43 48 L45 55" />
        <path d="M12 55 A20 20 0 0 1 52 55" />
      </svg>
    </span>

    <!-- 三角尺 -->
    <span class="decor d-ruler">
      <svg viewBox="0 0 64 64">
        <path d="M8 56 L56 56 L8 8 Z" />
        <path d="M16 56 L16 48 M24 56 L24 48 M32 56 L32 48" />
      </svg>
    </span>

    <!-- 阅卷章 -->
    <span class="decor d-stamp">
      <svg viewBox="0 0 64 64">
        <rect x="10" y="10" width="44" height="44" rx="7" />
        <path d="M22 34 L29 41 L43 24" />
      </svg>
    </span>
  </div>
</template>

<style scoped>
/* 固定铺满视口；z-index: -1 让它压在全局柔光（body::before，同为 -1，DOM 更靠前）之上、
   所有内容之下。不能用 0：这两页的卡片没有定位，0 会盖在卡面上。 */
.page-decor {
  position: fixed;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  overflow: hidden;
}

/* 点阵：只在四周显影，中间（内容区）完全淡出。
   用深色点而不是品牌绿 —— 绿底上绿点几乎没有对比度，看起来像脏。 */
.decor-grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(var(--color-text-primary) 1px, transparent 1px);
  background-size: 26px 26px;
  opacity: 0.09;
  -webkit-mask-image: radial-gradient(ellipse 58% 52% at 50% 40%, transparent 32%, #000 78%);
  mask-image: radial-gradient(ellipse 58% 52% at 50% 40%, transparent 32%, #000 78%);
}

.decor {
  --rot: 0deg;
  --o: 0.14;
  --dur: 18s;
  position: absolute;
  color: var(--color-primary-dark);
  opacity: var(--o);
  transform: rotate(var(--rot));
  will-change: transform;
}

.decor svg {
  display: block;
  width: 100%;
  height: auto;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

/* ===== 位置：贴着内容栏（1120）外侧的留白，不钻到卡片下面 =====
   用 calc(50% - 560px - …) 而不是百分比，宽屏上才会一直挨着内容栏，
   而不是被推到离内容几百像素远的屏幕边缘。 */
.d-bubble  { top: 15%;  right: calc(50% - 560px - 108px); width: 92px; --rot: 6deg;   --o: 0.15; --dur: 20s; }
.d-pencil  { top: 62%;  right: calc(50% - 560px - 74px);  width: 78px; --rot: -8deg;  --o: 0.14; --dur: 22s; }
.d-axis    { top: 58%;  left: calc(50% - 560px - 96px);   width: 86px; --rot: 3deg;   --o: 0.15; --dur: 19s; }
.d-compass { top: 12%;  left: calc(50% - 560px - 88px);   width: 80px; --rot: -6deg;  --o: 0.14; --dur: 24s; }
.d-ruler   { top: 84%;  left: calc(50% - 560px - 70px);   width: 62px; --rot: 10deg;  --o: 0.13; --dur: 21s; }
.d-stamp   { top: 88%;  right: calc(50% - 560px - 84px);  width: 70px; --rot: -12deg; --o: 0.13; --dur: 23s; }

/* 极慢的上下浮动：背景「有呼吸」，但幅度只有 6px，不会抢注意力 */
@media (prefers-reduced-motion: no-preference) {
  .decor {
    animation: decor-float var(--dur) ease-in-out infinite;
  }
  @keyframes decor-float {
    0%, 100% { transform: translateY(0) rotate(var(--rot)); }
    50%      { transform: translateY(-6px) rotate(var(--rot)); }
  }
}

/* 内容栏两侧没有留白时不显示 —— 否则图案会从卡片缝里露半截，比没有更脏。
   门槛 = 容器 1120 + 两侧内边距 48 + 两侧图案与间距约 220。 */
@media (max-width: 1319px) {
  .page-decor { display: none; }
}
</style>
