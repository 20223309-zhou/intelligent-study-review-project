<script setup>
// 登录 / 注册页的背景贴纸（纯装饰）。
// 用内联 SVG 而不是图片资源：跟随 currentColor 换色、任意 DPI 不糊、不增加请求。
// 全部 aria-hidden，且 pointer-events: none，不干扰任何交互。
</script>

<template>
  <div class="auth-stickers" aria-hidden="true">
    <!-- 三角尺 -->
    <span class="sticker st-1">
      <svg viewBox="0 0 64 64">
        <path d="M8 56 L56 56 L8 8 Z" />
        <path d="M16 56 L16 48 M24 56 L24 48 M32 56 L32 48" />
      </svg>
    </span>

    <!-- 地球仪 -->
    <span class="sticker st-2">
      <svg viewBox="0 0 64 64">
        <circle cx="32" cy="27" r="18" />
        <ellipse cx="32" cy="27" rx="7" ry="18" />
        <path d="M14 27 H50" />
        <path d="M18 16 Q32 22 46 16" />
        <path d="M18 38 Q32 32 46 38" />
        <path d="M32 45 V50" />
        <path d="M32 50 L24 57 M32 50 L40 57" />
        <path d="M23 57 H41" />
      </svg>
    </span>

    <!-- 圆规 -->
    <span class="sticker st-3">
      <svg viewBox="0 0 64 64">
        <circle cx="32" cy="9" r="3.5" />
        <path d="M32 12.5 L21 48" />
        <path d="M32 12.5 L43 48" />
        <path d="M21 48 L19 55" />
        <path d="M43 48 L45 55" />
        <path d="M12 55 A20 20 0 0 1 52 55" />
      </svg>
    </span>

    <!-- 原子 -->
    <span class="sticker st-4">
      <svg viewBox="0 0 64 64">
        <circle cx="32" cy="32" r="3" />
        <ellipse cx="32" cy="32" rx="25" ry="9" />
        <ellipse cx="32" cy="32" rx="25" ry="9" transform="rotate(60 32 32)" />
        <ellipse cx="32" cy="32" rx="25" ry="9" transform="rotate(120 32 32)" />
      </svg>
    </span>

    <!-- 书本 -->
    <span class="sticker st-5">
      <svg viewBox="0 0 64 64">
        <rect x="14" y="13" width="36" height="8" rx="2.5" />
        <rect x="11" y="25" width="42" height="8" rx="2.5" />
        <rect x="16" y="37" width="32" height="8" rx="2.5" />
      </svg>
    </span>

    <!-- 量角器 -->
    <span class="sticker st-6">
      <svg viewBox="0 0 64 64">
        <path d="M8 48 A24 24 0 0 1 56 48 Z" />
        <path d="M15 48 A17 17 0 0 1 49 48" />
        <path d="M32 24 V30" />
      </svg>
    </span>

    <!-- 三角形 -->
    <span class="sticker st-7">
      <svg viewBox="0 0 64 64">
        <path d="M32 10 L56 52 L8 52 Z" />
      </svg>
    </span>
  </div>
</template>

<style scoped>
/* 铺满视口（fixed），而不是只铺 .app-main 那一条 —— 否则宽屏上会出现横向接缝。
   z-index 0：在全局柔光层（body::before, -1）之上、纸卡（.paper, 1）之下。 */
.auth-stickers {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}

.sticker {
  --rot: 0deg;
  --o: 0.16;
  --dur: 11s;
  position: absolute;
  color: var(--color-primary);
  opacity: var(--o);
  transform: translateY(0) rotate(var(--rot));
  will-change: transform;
}

.sticker svg {
  display: block;
  width: 100%;
  height: auto;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}

/* ===== 位置 =====
   全部贴着卡片两侧的安全区，靠内的两枚（-4 / -6）在窄屏下先收起。 */
.st-1 { top: 13%; left: 9%;  width: clamp(56px, 5.5vw, 84px);  --rot: -14deg; --o: 0.20; --dur: 13s; }
.st-2 { top: 58%; right: 12%; width: clamp(64px, 6.2vw, 96px);  --rot: 8deg;   --o: 0.18; --dur: 15s; }
.st-3 { top: 60%; left: 14%; width: clamp(44px, 4vw, 60px);    --rot: 9deg;   --o: 0.15; --dur: 10s; }
.st-4 { top: 36%; left: 18%; width: clamp(38px, 3.4vw, 52px);  --rot: 22deg;  --o: 0.12; --dur: 12s; }
.st-5 { top: 79%; left: 8%;  width: clamp(50px, 4.6vw, 70px);  --rot: -6deg;  --o: 0.14; --dur: 14s; }
.st-6 { top: 54%; right: 17%; width: clamp(46px, 4.2vw, 64px); --rot: -11deg; --o: 0.13; --dur: 16s; }
.st-7 { top: 76%; right: 9%; width: clamp(54px, 5vw, 78px);    --rot: 12deg;  --o: 0.12; --dur: 11s; }

/* 极慢的上下浮动，让背景「有呼吸」但不抢注意力 */
@media (prefers-reduced-motion: no-preference) {
  .sticker {
    animation: sticker-float var(--dur) ease-in-out infinite;
  }
  @keyframes sticker-float {
    0%, 100% { transform: translateY(0) rotate(var(--rot)); }
    50%      { transform: translateY(-10px) rotate(var(--rot)); }
  }
}

/* 窄屏：先收起靠内侧的两枚，避免贴到卡片上 */
@media (max-width: 1180px) {
  .st-4, .st-6 { display: none; }
}

/* 再窄就整层关掉 —— 卡片几乎占满宽度，贴纸只会从卡片边缘露出半截 */
@media (max-width: 900px) {
  .auth-stickers { display: none; }
}
</style>
