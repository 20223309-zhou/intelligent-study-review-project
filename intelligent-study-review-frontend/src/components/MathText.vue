<script setup>
import { onMounted, ref, watch, nextTick } from 'vue'
import { whenKatexReady } from '@/utils/mathRender.js'

/**
 * 带公式渲染的文本节点。
 *
 * 为什么不用 v-html：题目内容全部由大模型生成，v-html 等于把模型输出当 HTML 执行。
 * 这里始终走 textContent 写入纯文本，再由 KaTeX 的 auto-render 就地替换公式片段，
 * 既拿得到排版效果，也不给注入留口子。
 */
const props = defineProps({
  text: { type: [String, Number], default: '' }
})

const el = ref(null)

// 顺序有意义：$$ 必须排在 $ 前面，否则 "$$x$$" 会被单 $ 规则先咬掉一半
const DELIMITERS = [
  { left: '$$', right: '$$', display: true },
  { left: '\\[', right: '\\]', display: true },
  { left: '$', right: '$', display: false },
  { left: '\\(', right: '\\)', display: false }
]

/**
 * 幂等：每次都从纯文本重建，再交给 auto-render。
 * KaTeX 没就绪时这一步也会留下可读的 LaTeX 原文，不会白屏。
 */
function paint() {
  const node = el.value
  if (!node) return
  node.textContent = props.text == null ? '' : String(props.text)
  const renderMath = window.renderMathInElement
  if (!renderMath) return
  try {
    renderMath(node, {
      delimiters: DELIMITERS,
      throwOnError: false,
      // 行内公式高一点也不会顶破卷面行距
      strict: false,
      ignoredTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code']
    })
  } catch (e) {
    // 渲染失败就保留 LaTeX 原文：宁可难看，也不能让题目消失
    console.warn('[MathText] 公式渲染失败', e)
  }
}

onMounted(() => {
  paint()
  // KaTeX 是 defer 加载，可能比组件晚到；就绪后再补一次（paint 幂等）
  whenKatexReady(paint)
})
watch(() => props.text, () => nextTick(paint))
</script>

<template>
  <span ref="el" class="math-text"></span>
</template>

<style scoped>
.math-text { white-space: inherit; }

/* 行内公式不要撑高行距，否则卷面会一段紧一段松 */
.math-text :deep(.katex) { font-size: 1.02em; }
.math-text :deep(.katex-display) { margin: 0.4em 0; }
</style>
