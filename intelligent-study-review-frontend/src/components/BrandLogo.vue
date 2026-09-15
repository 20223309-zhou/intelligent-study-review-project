<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 渲染高度，宽度按 viewBox 200:60 等比缩放 */
  height: { type: Number, default: 48 }
})

const width = computed(() => Math.round((props.height * 200) / 60))
</script>

<template>
  <!-- 全部使用 currentColor：换主色 / 换深色背景时只需改父级的 color -->
  <svg
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 200 60"
    :width="width"
    :height="height"
    role="img"
    aria-label="智卷派"
  >
    <g transform="translate(8, 6) scale(2)">
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="24" height="24" fill="currentColor">
        <path fill="currentColor" d="M216 40H40a16 16 0 0 0-16 16v160a8 8 0 0 0 11.58 7.16L64 208.94l28.42 14.22a8 8 0 0 0 7.16 0L128 208.94l28.42 14.22a8 8 0 0 0 7.16 0L192 208.94l28.42 14.22A8 8 0 0 0 232 216V56a16 16 0 0 0-16-16m0 163.06l-20.42-10.22a8 8 0 0 0-7.16 0L160 207.06l-28.42-14.22a8 8 0 0 0-7.16 0L96 207.06l-28.42-14.22a8 8 0 0 0-7.16 0L40 203.06V56h176Zm-155.58-35.9a8 8 0 0 0 10.74-3.58L76.94 152h38.12l5.78 11.58a8 8 0 1 0 14.32-7.16l-32-64a8 8 0 0 0-14.32 0l-32 64a8 8 0 0 0 3.58 10.74M96 113.89L107.06 136H84.94ZM136 128a8 8 0 0 1 8-8h16v-16a8 8 0 0 1 16 0v16h16a8 8 0 0 1 0 16h-16v16a8 8 0 0 1-16 0v-16h-16a8 8 0 0 1-8-8"/>
      </svg>
    </g>
    <text x="66" y="40" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif" font-size="22" font-weight="700" fill="currentColor" dominant-baseline="middle">智卷派</text>
  </svg>
</template>
