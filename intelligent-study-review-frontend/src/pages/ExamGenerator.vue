<script setup>
import { ref, computed, reactive, nextTick, onBeforeUnmount } from 'vue'
import QuestionTypeCard from '@/components/QuestionTypeCard.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiCard from '@/components/ui/UiCard.vue'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiSpinner from '@/components/ui/UiSpinner.vue'
import { createGenerationStream } from '@/utils/generationStream.js'

const API_BASE = '/api'

// 题型定义
const questionTypes = [
  { key: 'SINGLE_CHOICE', label: '单选题', badge: '4选项' },
  { key: 'MULTIPLE_CHOICE', label: '多选题', badge: '多选' },
  { key: 'TRUE_FALSE', label: '判断题', badge: '对/错' },
  { key: 'SHORT_ANSWER', label: '简答题', badge: '简答' },
  { key: 'BLANK_FILLING', label: '填空题', badge: '填空' },
  { key: 'PROOF_QUESTION', label: '证明题', badge: '证明' },
  { key: 'MATERIAL', label: '材料题', badge: '材料' },
  { key: 'ESSAY', label: '论述题', badge: '论述' }
]

// 表单数据
const form = reactive({
  subject: '',
  gradeOrLevel: '',
  textbookVersion: '',
  durationMinutes: '',
  totalScore: '',
  comment: '',
  questionConfig: {}
})

// 初始化题型配置
function initQuestionConfig() {
  questionTypes.forEach(qt => {
    form.questionConfig[qt.key] = 0
  })
}
initQuestionConfig()

// 年级选项
const gradeOptions = ['小学', '初中', '高中', '大学', '研究生']
const durationOptions = [30, 60, 90, 120, 150, 180]
const scoreOptions = [50, 100, 150]

// 校验
const isStep1Valid = computed(() => {
  return form.subject.trim() !== '' &&
    form.gradeOrLevel !== '' &&
    form.durationMinutes !== '' &&
    form.totalScore !== ''
})

const selectedCount = computed(() => {
  return Object.values(form.questionConfig).filter(v => v > 0).length
})

const totalQuestionCount = computed(() => {
  return Object.values(form.questionConfig).reduce((sum, v) => sum + v, 0)
})

const isTotalValid = computed(() => totalQuestionCount.value > 0)

const canSubmit = computed(() => isStep1Valid.value && isTotalValid.value)

// 题型交互
function toggleQuestionType(key) {
  form.questionConfig[key] = form.questionConfig[key] > 0 ? 0 : 1
}

function updateQuestionCount(key, count) {
  form.questionConfig[key] = count
}

// 重置表单
function resetForm() {
  form.subject = ''
  form.gradeOrLevel = ''
  form.textbookVersion = ''
  form.durationMinutes = ''
  form.totalScore = ''
  form.comment = ''
  initQuestionConfig()
}

// 提交状态
const isSubmitting = ref(false)
const submitStep = ref('') // '' | 'saving' | 'generating'
const errorMsg = ref('')
const resultVO = ref(null)

// 生成过程时间线（来自 SSE）：让用户看见多 Agent 的「规划 → 生成 → 审查 → 回炉」。
// 用列表而不是单行文案 —— 单行会被后一条覆盖，用户只能看到"最后一句话"，
// 看不到多 Agent 协作的完整轨迹，这正是流式输出显得不明显的原因。
const logs = ref([])
const logBox = ref(null)
const elapsedSec = ref(0)
const tokenChars = ref(0)
let closeStream = null
let startedAt = 0
let tickTimer = null
let logSeq = 0

function fmtElapsed(sec) {
  return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`
}

function pushLog(entry) {
  logs.value.push({ id: ++logSeq, at: fmtElapsed(elapsedSec.value), ...entry })
  // 一次生成的事件量很小（阶段 1~7 条、审查 1~3 条），上限只是兜底
  if (logs.value.length > 100) logs.value.splice(0, logs.value.length - 100)
  // 跟随最新一条
  nextTick(() => {
    if (logBox.value) logBox.value.scrollTop = logBox.value.scrollHeight
  })
}

function startTimer() {
  startedAt = Date.now()
  stopTimer()
  tickTimer = setInterval(() => {
    elapsedSec.value = Math.floor((Date.now() - startedAt) / 1000)
  }, 1000)
}

function stopTimer() {
  if (tickTimer) {
    clearInterval(tickTimer)
    tickTimer = null
  }
}

function resetProgress() {
  logs.value = []
  elapsedSec.value = 0
  tokenChars.value = 0
  logSeq = 0
  stopTimer()
}

onBeforeUnmount(() => {
  closeStream?.()
  stopTimer()
})

// 题型映射
const typeMap = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  TRUE_FALSE: '判断题',
  SHORT_ANSWER: '简答题',
  BLANK_FILLING: '填空题',
  PROOF_QUESTION: '证明题',
  MATERIAL: '材料题',
  ESSAY: '论述题'
}
function typeLabel(type) {
  return typeMap[type] || type
}

function renderContent(q) {
  // 简答题/填空题/证明题/论述题不需要额外渲染
  // 如果有特殊格式需求可在这里处理
  return q.content
}

async function handleSubmit() {
  if (!canSubmit.value || isSubmitting.value) return
  errorMsg.value = ''
  resultVO.value = null
  resetProgress()

  // 构建 payload
  const payload = {
    subject: form.subject.trim(),
    gradeOrLevel: form.gradeOrLevel,
    durationMinutes: Number(form.durationMinutes),
    totalScore: Number(form.totalScore),
    questionConfig: {}
  }
  if (form.textbookVersion.trim()) payload.textbookVersion = form.textbookVersion.trim()
  if (form.comment.trim()) payload.comment = form.comment.trim()
  Object.entries(form.questionConfig).forEach(([key, val]) => {
    if (val > 0) payload.questionConfig[key] = val
  })

  isSubmitting.value = true

  try {
    // 第一步：保存试卷基本信息
    submitStep.value = 'saving'
    const saveRes = await fetch(`${API_BASE}/exam-paper/add`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    }).then(r => r.json())

    if (saveRes.code !== 0) {
      errorMsg.value = saveRes.message || '保存试卷失败'
      return
    }

    const paperId = saveRes.data

    // 第二步：订阅 SSE 实时接收生成进度（阶段 + 每轮审查分数）
    // 原来的同步 POST /generate 要等几十秒到几分钟，长连接容易被掐断且看不到任何过程
    submitStep.value = 'generating'
    startTimer()
    await new Promise((resolve, reject) => {
      closeStream = createGenerationStream(paperId, {
        stage: (d) => {
          pushLog({ kind: 'stage', text: d.label || d.node || '正在生成', round: d.round || 0 })
        },
        review: (d) => {
          pushLog({
            kind: 'review',
            text: `第 ${d.round} 轮审查 · ${d.qualityScore} / 10`,
            note: d.passed ? '已通过质检' : '未达标，继续优化',
            passed: !!d.passed
          })
        },
        // Writer 产出的是纯 JSON，不展示原文，只用字符数证明"确实在输出"
        token: (d) => {
          tokenChars.value = d.chars || 0
        },
        done: () => resolve(),
        error: (d) => reject(new Error(d.message || '生成试卷失败'))
      })
    })
    closeStream?.()
    closeStream = null

    // done 事件只带摘要，整卷（含题目）从详情接口取，避免单条 SSE payload 过大
    const paperRes = await fetch(`${API_BASE}/exam-paper/get/${paperId}`).then(r => r.json())
    if (paperRes.code !== 0) {
      errorMsg.value = paperRes.message || '获取生成结果失败'
      return
    }
    resultVO.value = paperRes.data
  } catch (e) {
    errorMsg.value = e?.message && e.message !== 'Failed to fetch'
      ? e.message
      : '网络错误，请稍后重试'
  } finally {
    closeStream?.()
    closeStream = null
    // 计时停在完成那一刻；logs 保留下来供结果页回看
    stopTimer()
    isSubmitting.value = false
    submitStep.value = ''
  }
}
</script>

<template>
  <div class="exam-generator">
    <div class="page-intro">
      <h1 class="page-title">智能试卷生成器</h1>
      <p class="page-desc">三步完成试卷配置，快速生成符合需求的试卷</p>
    </div>

    <form class="generator-form" @submit.prevent="handleSubmit" v-if="!resultVO">
      <!-- 步骤1: 基本信息 -->
      <UiCard class="form-section" :padded="false" radius="xl" elevation="md">
        <div class="section-header">
          <span class="step-number">①</span>
          <h2 class="section-title">基本信息</h2>
        </div>
        <div class="section-body">
          <div class="form-grid">
            <div class="form-group">
              <label class="form-label">
                学科
                <span class="required">*</span>
              </label>
              <input
                v-model="form.subject"
                type="text"
                class="form-input"
                placeholder="请输入学科名称，如：高等数学"
              />
            </div>
            <div class="form-group">
              <label class="form-label">
                年级或等级
                <span class="required">*</span>
              </label>
              <select v-model="form.gradeOrLevel" class="form-input form-select">
                <option value="" disabled>请选择年级或等级</option>
                <option v-for="opt in gradeOptions" :key="opt" :value="opt">{{ opt }}</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">教材版本</label>
              <input
                v-model="form.textbookVersion"
                type="text"
                class="form-input"
                placeholder="如：人教版、同济版（选填）"
              />
            </div>
            <div class="form-group">
              <label class="form-label">
                考试时长（分钟）
                <span class="required">*</span>
              </label>
              <select v-model="form.durationMinutes" class="form-input form-select">
                <option value="" disabled>请选择考试时长</option>
                <option v-for="opt in durationOptions" :key="opt" :value="opt">{{ opt }} 分钟</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">
                总分
                <span class="required">*</span>
              </label>
              <select v-model="form.totalScore" class="form-input form-select">
                <option value="" disabled>请选择总分</option>
                <option v-for="opt in scoreOptions" :key="opt" :value="opt">{{ opt }} 分</option>
              </select>
            </div>
          </div>
        </div>
      </UiCard>

      <!-- 步骤2: 题型配置 -->
      <UiCard class="form-section" :padded="false" radius="xl" elevation="md">
        <div class="section-header">
          <span class="step-number">②</span>
          <h2 class="section-title">题型配置</h2>
        </div>
        <div class="section-body">
          <div class="qtype-grid">
            <QuestionTypeCard
              v-for="qt in questionTypes"
              :key="qt.key"
              :typeKey="qt.key"
              :label="qt.label"
              :badge="qt.badge"
              :selected="form.questionConfig[qt.key] > 0"
              :count="form.questionConfig[qt.key] || 1"
              @toggle="toggleQuestionType"
              @update:count="updateQuestionCount"
            />
          </div>
          <div class="qtype-summary">
            <span class="summary-item">
              已选题型 <strong>{{ selectedCount }}</strong> / 8
            </span>
            <span class="summary-divider">|</span>
            <span class="summary-item">
              总题数 <strong>{{ totalQuestionCount }}</strong> 题
            </span>
          </div>
        </div>
      </UiCard>

      <!-- 步骤3: 补充信息 -->
      <UiCard class="form-section" :padded="false" radius="xl" elevation="md">
        <div class="section-header">
          <span class="step-number">③</span>
          <h2 class="section-title">补充信息</h2>
        </div>
        <div class="section-body">
          <div class="form-group">
            <label class="form-label">用户备注</label>
            <textarea
              v-model="form.comment"
              class="form-input form-textarea"
              placeholder="请输入对试卷的备注要求，如：提供试卷的考试范围和考试重点"
              rows="4"
            ></textarea>
          </div>
        </div>
      </UiCard>

      <!-- 错误提示 -->
      <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>

      <!-- 底部操作栏 -->
      <div class="form-actions">
        <UiButton variant="secondary" :disabled="isSubmitting" @click="resetForm">
          <template #icon>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="1 4 1 10 7 10"/>
              <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>
            </svg>
          </template>
          重置
        </UiButton>
        <UiButton
          native-type="submit"
          variant="primary"
          size="lg"
          :loading="isSubmitting"
          :disabled="!canSubmit"
        >
          <template #icon>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 20h9"/>
              <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/>
            </svg>
          </template>
          {{ isSubmitting ? '生成中...' : '开始生成试卷' }}
        </UiButton>
      </div>

      <!-- 生成过程：SSE 实时推进。每一条都保留，不覆盖 —— 便于回看完整的多 Agent 轨迹 -->
      <div v-if="isSubmitting" class="gen-panel">
        <div class="gen-head">
          <UiSpinner :size="22" />
          <span class="gen-title">
            {{ submitStep === 'saving' ? '正在保存试卷信息…' : 'AI 正在生成试卷…' }}
          </span>
          <span v-if="submitStep === 'generating'" class="gen-stat">已用时 {{ fmtElapsed(elapsedSec) }}</span>
          <span v-if="submitStep === 'generating' && tokenChars > 0" class="gen-stat">
            模型已输出 {{ tokenChars }} 字符
          </span>
        </div>
        <div v-if="submitStep === 'generating'" ref="logBox" class="gen-log">
          <div
            v-for="item in logs"
            :key="item.id"
            class="gen-log-item"
            :class="item.kind === 'review' ? (item.passed ? 'k-pass' : 'k-warn') : 'k-stage'"
          >
            <span class="gl-dot" />
            <span class="gl-text">
              {{ item.text }}<template v-if="item.round > 1"> · 第 {{ item.round }} 轮</template>
            </span>
            <span v-if="item.note" class="gl-note">{{ item.note }}</span>
            <span class="gl-at">{{ item.at }}</span>
          </div>
          <div v-if="!logs.length" class="gen-log-item k-stage">
            <span class="gl-dot" />
            <span class="gl-text">正在启动生成工作流…</span>
            <span class="gl-at">0:00</span>
          </div>
        </div>
      </div>
    </form>

    <!-- 生成结果 -->
    <div v-else class="result-wrapper">
      <UiCard class="result-header-bar" radius="xl" elevation="md">
        <div class="result-header-left">
          <svg class="result-icon" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22 4 12 14.01 9 11.01"/>
          </svg>
          <div>
            <h2 class="result-title">试卷生成成功</h2>
            <p class="result-paper-name">{{ resultVO.paperName }}</p>
          </div>
        </div>
        <div class="result-header-meta">
          <UiBadge variant="neutral" size="md">总分 {{ resultVO.totalScore }} 分</UiBadge>
          <UiBadge variant="neutral" size="md">{{ resultVO.durationMinutes }} 分钟</UiBadge>
          <UiBadge variant="neutral" size="md">{{ resultVO.questions?.length || 0 }} 题</UiBadge>
        </div>
      </UiCard>

      <!-- 生成过程：默认收起，但一直保留，随时可以回看这次的完整轨迹 -->
      <details v-if="logs.length" class="gen-history">
        <summary>
          查看生成过程（{{ logs.length }} 步 · 用时 {{ fmtElapsed(elapsedSec) }}）
        </summary>
        <div class="gen-log gen-log--static">
          <div
            v-for="item in logs"
            :key="item.id"
            class="gen-log-item"
            :class="item.kind === 'review' ? (item.passed ? 'k-pass' : 'k-warn') : 'k-stage'"
          >
            <span class="gl-dot" />
            <span class="gl-text">
              {{ item.text }}<template v-if="item.round > 1"> · 第 {{ item.round }} 轮</template>
            </span>
            <span v-if="item.note" class="gl-note">{{ item.note }}</span>
            <span class="gl-at">{{ item.at }}</span>
          </div>
        </div>
      </details>

      <!-- 题目列表 -->
      <div class="question-list">
        <UiCard
          v-for="(q, index) in resultVO.questions"
          :key="index"
          class="question-item"
        >
          <div class="q-header">
            <span class="q-number">{{ index + 1 }}</span>
            <span class="q-type-badge" :class="'q-type--' + q.questionType">{{ typeLabel(q.questionType) }}</span>
            <span class="q-score">{{ q.score }} 分</span>
          </div>
          <div class="q-content" v-html="renderContent(q)"></div>

          <!-- 选项（选择题） -->
          <div v-if="q.options && q.options.length" class="q-options">
            <div v-for="(opt, oi) in q.options" :key="oi" class="q-option">
              {{ opt }}
            </div>
          </div>

          <!-- 材料文本 -->
          <div v-if="q.materialText" class="q-material">
            <div class="q-material-label">【材料】</div>
            <div class="q-material-text">{{ q.materialText }}</div>
          </div>

          <!-- 答案与解析（默认折叠） -->
          <details class="q-details">
            <summary class="q-details-summary">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
              查看答案与解析
            </summary>
            <div class="q-answer">参考答案：{{ q.answer }}</div>
            <div class="q-analysis">解析：{{ q.analysis }}</div>
          </details>

          <!-- 知识点 -->
          <div v-if="q.knowledgePoints && q.knowledgePoints.length" class="q-tags">
            <span v-for="(kp, ki) in q.knowledgePoints" :key="ki" class="q-tag">{{ kp }}</span>
          </div>
        </UiCard>
      </div>

      <div class="result-actions">
        <UiButton variant="secondary" @click="resultVO = null">
          <template #icon>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="1 4 1 10 7 10"/>
              <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>
            </svg>
          </template>
          继续生成
        </UiButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.exam-generator {
  max-width: 100%;
}

.page-intro {
  text-align: center;
  margin-bottom: var(--space-8);
}

.page-title {
  font-size: var(--fs-3xl);
  font-weight: var(--fw-heavy);
  color: var(--color-text-primary);
  letter-spacing: -0.02em;
}

.page-desc {
  color: var(--color-text-muted);
  margin-top: var(--space-2);
  font-size: var(--fs-md);
}

.generator-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.form-section {
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-5) var(--space-6);
  border-bottom: 1px solid var(--color-border-light);
  background: var(--color-primary-soft);
}

.step-number {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
  background: var(--color-primary-gradient);
  color: white;
  font-size: var(--fs-sm);
  font-weight: var(--fw-bold);
  flex-shrink: 0;
}

.section-title {
  font-size: var(--fs-lg);
  font-weight: var(--fw-bold);
  color: var(--color-text-primary);
}

.section-body {
  padding: var(--space-6);
}

/* 表单栅格：按容器宽度自适应列数，避免宽屏下单个输入框被拉得过长 */
.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--space-5);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.form-label {
  font-size: var(--fs-sm);
  font-weight: var(--fw-semibold);
  color: var(--color-text-secondary);
}

.required {
  color: var(--color-error);
  margin-left: 2px;
}

.form-input {
  width: 100%;
  padding: 10px 14px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text-primary);
  /* 16px 起，避免 iOS 聚焦时页面自动缩放（此前靠媒体查询打补丁） */
  font-size: 1rem;
  transition: all var(--transition-fast);
  outline: none;
}

.form-input:focus {
  border-color: var(--color-primary-light);
  box-shadow: var(--ring-primary);
}

.form-input::placeholder {
  color: var(--color-text-muted);
}

.form-select {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%236b7280' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpolyline points='6 9 12 15 18 9'%3E%3C/polyline%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 36px;
  cursor: pointer;
}

.form-textarea {
  resize: vertical;
  min-height: 100px;
  line-height: var(--lh-normal);
}

.qtype-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--space-4);
}

.qtype-summary {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: var(--space-4);
  margin-top: var(--space-6);
  padding: var(--space-3) var(--space-5);
  background: var(--color-bg);
  border-radius: var(--radius-lg);
  font-size: var(--fs-sm);
  color: var(--color-text-secondary);
}

.qtype-summary strong {
  color: var(--color-primary);
  font-size: var(--fs-lg);
}

.summary-divider {
  color: var(--color-border);
}

.form-error {
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--color-error-text);
  padding: var(--space-3) var(--space-4);
  background: var(--color-error-bg);
  border: 1px solid var(--color-error-border);
  border-radius: var(--radius-md);
}

.form-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-5) 0;
}

/* 生成过程面板：头部状态 + 保留式时间线 */
.gen-panel {
  padding: var(--space-4) var(--space-5);
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.gen-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.gen-title {
  font-size: var(--fs-sm);
  font-weight: var(--fw-semibold);
  color: var(--color-text-primary);
}

.gen-stat {
  margin-left: auto;
  font-size: var(--fs-xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-muted);
}
.gen-stat + .gen-stat { margin-left: 0; }

.gen-log {
  margin-top: var(--space-3);
  max-height: 240px;
  overflow-y: auto;
  padding-left: 11px;
  border-left: 2px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
/* 结果页里复用时间线：不滚动、撑开即可 */
.gen-log--static {
  max-height: none;
  overflow: visible;
  margin-top: var(--space-3);
}

.gen-log-item {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  font-size: var(--fs-sm);
  line-height: var(--lh-normal);
}

.gl-dot {
  flex-shrink: 0;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-border-strong);
  align-self: center;
}
.k-stage .gl-dot { background: var(--color-primary); }
.k-pass .gl-dot { background: var(--color-success); }
.k-warn .gl-dot { background: var(--color-warning); }

.gl-text { color: var(--color-text-primary); }

.gl-note {
  font-size: var(--fs-xs);
  font-weight: var(--fw-semibold);
}
.k-pass .gl-note { color: var(--color-success-text); }
.k-warn .gl-note { color: var(--color-warning-text); }

.gl-at {
  margin-left: auto;
  flex-shrink: 0;
  font-size: var(--fs-2xs);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-muted);
}

/* 结果页的折叠面板 */
.gen-history {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-3) var(--space-5);
  background: var(--color-surface);
}
.gen-history summary {
  cursor: pointer;
  font-size: var(--fs-sm);
  font-weight: var(--fw-semibold);
  color: var(--color-text-secondary);
  user-select: none;
}
.gen-history[open] summary { margin-bottom: var(--space-2); }

/* 结果区域 */
.result-wrapper {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.result-header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
  padding: var(--space-6);
}

.result-header-left {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.result-icon { color: var(--color-success); flex-shrink: 0; }

.result-title {
  font-size: var(--fs-xl);
  font-weight: var(--fw-bold);
  color: var(--color-text-primary);
}

.result-paper-name {
  font-size: var(--fs-md);
  color: var(--color-text-muted);
  margin-top: 2px;
}

.result-header-meta {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.result-actions {
  display: flex;
  justify-content: center;
  gap: var(--space-3);
}

/* 题目列表 */
.question-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.q-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.q-number {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--color-primary-gradient);
  color: white;
  font-size: var(--fs-xs);
  font-weight: var(--fw-bold);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.q-type-badge {
  font-size: var(--fs-2xs);
  font-weight: var(--fw-semibold);
  padding: 2px 10px;
  border-radius: var(--radius-full);
}

/* 题型配色为「分类色板」，不属于语义状态色，故在此就地定义 */
.q-type--SINGLE_CHOICE   { background: #eef2ff; color: #4f46e5; }
.q-type--MULTIPLE_CHOICE { background: #faf5ff; color: #9333ea; }
.q-type--TRUE_FALSE      { background: #f0fdfa; color: #0d9488; }
.q-type--SHORT_ANSWER    { background: #fefce8; color: #ca8a04; }
.q-type--BLANK_FILLING   { background: #fff7ed; color: #ea580c; }
.q-type--PROOF_QUESTION  { background: #fef2f2; color: #dc2626; }
.q-type--MATERIAL        { background: #ecfeff; color: #0891b2; }
.q-type--ESSAY           { background: #f8fafc; color: #475569; }

.q-score {
  margin-left: auto;
  font-size: var(--fs-xs);
  font-weight: var(--fw-bold);
  color: var(--color-primary);
}

.q-content {
  font-size: var(--fs-md);
  line-height: var(--lh-relaxed);
  color: var(--color-text-primary);
  white-space: pre-wrap;
}

.q-options {
  margin-top: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.q-option {
  font-size: var(--fs-sm);
  color: var(--color-text-secondary);
  padding: 6px 12px;
  border-radius: var(--radius-md);
  background: var(--color-bg);
}

.q-material {
  margin-top: var(--space-3);
  padding: var(--space-3);
  background: var(--color-success-bg);
  border-radius: var(--radius-md);
  border-left: 3px solid var(--color-success);
}

.q-material-label {
  font-size: var(--fs-2xs);
  font-weight: var(--fw-bold);
  color: var(--color-success-text);
  margin-bottom: var(--space-1);
}

.q-material-text {
  font-size: var(--fs-sm);
  color: var(--color-text-secondary);
  line-height: var(--lh-normal);
}

.q-details {
  margin-top: var(--space-3);
}

.q-details-summary {
  cursor: pointer;
  font-size: var(--fs-sm);
  font-weight: var(--fw-semibold);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  gap: 4px;
  user-select: none;
}

.q-details-summary svg {
  transition: transform 0.2s;
}

.q-details[open] .q-details-summary svg {
  transform: rotate(90deg);
}

.q-answer {
  margin-top: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--color-success-bg);
  border-radius: var(--radius-md);
  font-size: var(--fs-sm);
  color: var(--color-success-text);
  line-height: var(--lh-normal);
}

.q-analysis {
  margin-top: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--color-bg);
  border-radius: var(--radius-md);
  font-size: var(--fs-sm);
  color: var(--color-text-secondary);
  line-height: var(--lh-normal);
}

.q-tags {
  margin-top: var(--space-3);
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.q-tag {
  font-size: var(--fs-2xs);
  padding: 2px 10px;
  border-radius: var(--radius-full);
  background: var(--color-primary-bg);
  color: var(--color-primary);
}

@media (max-width: 768px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .qtype-grid {
    grid-template-columns: 1fr;
  }

  .page-title {
    font-size: var(--fs-2xl);
  }

  .section-header {
    padding: var(--space-4);
  }

  .section-body {
    padding: var(--space-4);
  }

  .form-actions {
    flex-direction: column-reverse;
    gap: var(--space-3);
  }

  .form-actions :deep(.ui-btn) {
    width: 100%;
  }

  .result-header-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .result-actions {
    flex-direction: column;
  }

  .result-actions :deep(.ui-btn) {
    width: 100%;
  }
}

@media (max-width: 480px) {
  .qtype-grid {
    gap: var(--space-3);
  }
}
</style>
