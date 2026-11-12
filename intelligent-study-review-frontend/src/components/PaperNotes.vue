<script setup>
/**
 * 试卷「注意事项」——页面卷面与导出 PDF 共用同一份，避免两处文案走偏。
 * 只负责内容与排版；外边距、分隔线由使用方（.paper-notes / .pe-notes）给。
 */
defineProps({
  count: { type: [Number, String], default: 0 },
  totalScore: { type: [Number, String], default: 0 },
  duration: { type: [Number, String], default: 0 },
  // [{ label, count, score }]
  types: { type: Array, default: () => [] },
  hasMultiple: { type: Boolean, default: false }
})
</script>

<template>
  <div class="pn-title">注意事项</div>
  <ol class="pn-list">
    <li>本试卷共 {{ count }} 题，满分 {{ totalScore }} 分，考试时间 {{ duration }} 分钟。</li>
    <li>
      试卷分 {{ types.length }} 部分：<span
        v-for="(t, i) in types" :key="t.label"
        class="pn-item">{{ t.label }} {{ t.count }} 题，共 {{ t.score }} 分{{ i < types.length - 1 ? '；' : '。' }}</span>
    </li>
    <li>选择题在选项前勾选作答<template v-if="hasMultiple">，多选题全部选对才得分</template>；其余题目在答题区内作答。</li>
    <li>考试结束后，将试卷与答题卡一并交回。</li>
  </ol>
</template>

<style scoped>
.pn-title {
  font-weight: var(--fw-bold);
  color: var(--color-text-primary);
  margin-bottom: 2px;
  letter-spacing: 1px;
}
.pn-list {
  margin: 0;
  padding-left: 2.2em;   /* 与「1．」的宽度对齐，正文形成统一缩进 */
  list-style: none;
  counter-reset: pn;
}
.pn-list li {
  counter-increment: pn;
  position: relative;
  /* 不要 text-align: justify —— 与 inline-block 的题型片段配合时，
     中文行尾的空格会被拉伸成一大段空隙，反而像排版出错 */
  text-align: left;
}
.pn-list li::before {
  content: counter(pn) '．';
  position: absolute;
  left: -1.9em;
  color: var(--color-text-secondary);
}
/* 每个题型作为整体换行：「填空题 2 题，」和「共 10 分」不该被拆到两行 */
.pn-item { display: inline-block; white-space: nowrap; }
</style>
