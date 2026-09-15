<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { exportWord } from '@/utils/exportWord.js'
import { confirmDialog, toast, toastSuccess, toastError } from '@/utils/ui.js'
import UiButton from '@/components/ui/UiButton.vue'

const API_BASE = '/api'
const route = useRoute()
const router = useRouter()
const paper = ref(null)
const loading = ref(true)
const exporting = ref(false)
const showExportMenu = ref(false)
// 预览模式的「查看解析」开关：展示参考答案、解析、知识点，并把正确选项标出来
const showAnswers = ref(false)
const mode = ref('preview') // 'preview' | 'exam' | 'result'
const submitting = ref(false)
const gradeResult = ref(null)
const remainingSeconds = ref(0)
const currentKey = ref(null)
const headerOffset = ref(0)

// 结果页筛选：'all' 全部 | 'wrong' 错题 | 'blank' 未作答
const resultFilter = ref('all')
// 错题重做：非空数组时只渲染这些题号
const practiceKeys = ref(null)

const typeLabel = {
  SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', TRUE_FALSE: '判断题',
  SHORT_ANSWER: '简答题', BLANK_FILLING: '填空题', PROOF_QUESTION: '证明题',
  MATERIAL: '材料题', ESSAY: '论述题'
}

// 用户答案
const userAnswers = reactive({})

// 题目稳定 key：优先用 sortOrder，缺失时按整卷顺序回退
// 保证「初始化 / 展示 / 答题卡 / 批改匹配」四处用的是同一个编号
const questionKeys = computed(() => {
  const map = new Map()
  const qs = paper.value?.questions
  if (!qs) return map
  ;[...qs]
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    .forEach((q, idx) => map.set(q, q.sortOrder > 0 ? q.sortOrder : idx + 1))
  return map
})
function qKey(q, fallback) {
  return questionKeys.value.get(q) ?? q.sortOrder ?? fallback
}
function questionByKey(key) {
  return allQuestions.value.find(q => qKey(q) === key)
}

// 整卷题目（按真实题号排序）
const allQuestions = computed(() => {
  const qs = paper.value?.questions
  if (!qs) return []
  return [...qs].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
})

// 实际渲染的题目（错题重做 + 结果筛选叠加）
const displayQuestions = computed(() => {
  let qs = allQuestions.value
  if (practiceKeys.value) {
    const set = new Set(practiceKeys.value)
    qs = qs.filter(q => set.has(qKey(q)))
  }
  if (mode.value === 'result' && resultFilter.value !== 'all') {
    qs = qs.filter(q => answerState(qKey(q)) === resultFilter.value)
  }
  return qs
})

// 计入「已答 / 未答」进度的题目（答题模式下若在重做错题，只统计重做范围）
const progressQuestions = computed(() => {
  if (practiceKeys.value) {
    const set = new Set(practiceKeys.value)
    return allQuestions.value.filter(q => set.has(qKey(q)))
  }
  return allQuestions.value
})

const sections = computed(() => {
  const groups = {}
  displayQuestions.value.forEach(q => {
    const t = q.questionType
    if (!groups[t]) groups[t] = []
    groups[t].push(q)
  })
  return Object.entries(groups).map(([type, questions]) => ({
    type, label: typeLabel[type] || type, questions
  }))
})

function isAnswered(q) {
  const v = userAnswers[qKey(q)]
  if (Array.isArray(v)) return v.length > 0
  return v !== undefined && v !== null && String(v).trim() !== ''
}
const answeredCount = computed(() => progressQuestions.value.filter(isAnswered).length)
const unansweredCount = computed(() => progressQuestions.value.length - answeredCount.value)
const progressPct = computed(() => {
  const total = progressQuestions.value.length
  return total ? Math.round((answeredCount.value / total) * 100) + '%' : '0%'
})

// ===== 试卷说明（由试卷数据生成，替代原先写死的纸质考试须知）=====
const composition = computed(() => {
  const qs = allQuestions.value
  const byType = new Map()
  const kpSet = new Set()
  qs.forEach(q => {
    const t = q.questionType
    if (!byType.has(t)) byType.set(t, { label: typeLabel[t] || t, count: 0, score: 0 })
    const bucket = byType.get(t)
    bucket.count += 1
    bucket.score += Number(q.score) || 0
    ;(q.knowledgePoints || []).forEach(k => kpSet.add(k))
  })
  return {
    types: [...byType.values()],
    kps: [...kpSet],
    hasMultiple: qs.some(q => q.questionType === 'MULTIPLE_CHOICE'),
    hasSubjective: qs.some(q => isLongAnswer(q))
  }
})

onMounted(async () => {
  measureHeader()
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', measureHeader)
  const id = route.params.id
  try {
    const res = await fetch(`${API_BASE}/exam-paper/get/${id}`).then(r => r.json())
    if (res.code === 0) paper.value = res.data
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
})

// 顶部全局 header 是 sticky 的，窄屏下工具栏吸顶需要让开它的高度
function measureHeader() {
  const el = document.querySelector('.app-header')
  headerOffset.value = el ? Math.round(el.getBoundingClientRect().height) : 0
}

function goBack() { router.push('/papers') }

// ===== 导出 =====
// PDF 走「独立渲染」而不是截取当前界面。答案在 DOM 里是否存在原本取决于 mode，
// 所以预览态点「含答案」导出的其实是空答案。现在按 exportKind 渲染一份专用 DOM，
// 答案直接取自数据层（q.answer / q.analysis），与界面状态、是否提交过完全无关。
const exportKind = ref(null) // null | 'questions' | 'key'

// 导出始终输出整卷（按真实题号），不受结果页筛选 / 错题重做范围影响
const exportSections = computed(() => {
  const groups = {}
  allQuestions.value.forEach(q => {
    const t = q.questionType
    if (!groups[t]) groups[t] = []
    groups[t].push(q)
  })
  return Object.entries(groups).map(([type, questions]) => ({
    type, label: typeLabel[type] || type, questions
  }))
})
function sectionScore(questions) {
  return questions.reduce((s, q) => s + (Number(q.score) || 0), 0)
}
// 该选项是否为标准答案项（按选项字母匹配，兼容 "ACE" / "A,C,E" 等格式）
function isAnswerOption(q, opt) {
  return String(q.answer || '').toUpperCase().includes(optLetter(opt))
}

async function exportPDF(withAnswers) {
  exporting.value = true; showExportMenu.value = false
  exportKind.value = withAnswers ? 'key' : 'questions'
  let ok = false
  try {
    await nextTick()
    const el = document.getElementById('paper-export')
    if (!el) throw new Error('导出容器未渲染')
    // nextTick 只保证 DOM 已更新，不保证已布局绘制；刚插入的元素要等一帧再交给 html2canvas，
    // 否则它可能读到空布局，同样会导出空白。
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    await window.html2pdf().set({
      margin: [18, 20, 18, 20],
      filename: `${paper.value.paperName || '试卷'}${withAnswers ? '（含答案）' : ''}.pdf`,
      image: { type: 'jpeg', quality: 0.95 },
      html2canvas: { scale: 2, useCORS: true, letterRendering: true },
      jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
      // 不再用 avoid-all：它要求所有元素都不分页，长题目（尤其材料题）无处可放时会重叠错页。
      // 改为只对单个题目块声明 break-inside: avoid。
      pagebreak: { mode: ['css', 'legacy'] }
    }).from(el).save()
    ok = true
  } catch (e) {
    console.error(e)
  } finally {
    // 必须先收起遮罩：toast 的 z-index 低于遮罩，在遮罩内弹会被盖住
    exportKind.value = null
    exporting.value = false
  }
  if (ok) toastSuccess(withAnswers ? '答案卷 PDF 导出完成' : '题目卷 PDF 导出完成')
  else toastError('导出失败')
}
async function handleExportWord(withAnswers) {
  exporting.value = true; showExportMenu.value = false
  try {
    await exportWord(paper.value, withAnswers)
    toastSuccess('Word 导出完成')
  } catch (e) {
    console.error(e)
    toastError('导出失败')
  } finally {
    exporting.value = false
  }
}
function handleExportClick(format, withAnswers) {
  format === 'pdf' ? exportPDF(withAnswers) : handleExportWord(withAnswers)
}
function onDocClick(e) {
  const btn = document.getElementById('export-btn-wrap')
  if (btn && !btn.contains(e.target)) showExportMenu.value = false
}

function choiceLabel(i) { return String.fromCharCode(65 + i) }
function optLetter(opt) {
  const m = opt.match(/^([A-Z])[.、]\s*/)
  return m ? m[1] : opt
}
// 判断选项是否被选中（兼容单选 string 与多选 array）
function isSelected(key, letter) {
  const v = userAnswers[key]
  return Array.isArray(v) ? v.includes(letter) : v === letter
}
// 需要多行输入作答的题型（填空/材料/简答/证明/论述）
function isLongAnswer(q) {
  return ['SHORT_ANSWER', 'PROOF_QUESTION', 'ESSAY', 'BLANK_FILLING', 'MATERIAL'].includes(q.questionType)
}
function answerRows(q) {
  return q.questionType === 'BLANK_FILLING' ? 2 : 4
}
function letterOptions(q) {
  return (q.options || []).map(o => optLetter(o))
}

// ===== 倒计时 =====
let timerId = null
function durationSeconds() {
  const m = Number(paper.value?.durationMinutes) || 0
  return m > 0 ? m * 60 : 0
}
function formatRemaining() {
  const s = Math.max(0, remainingSeconds.value)
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}
function startTimer() {
  stopTimer()
  if (remainingSeconds.value <= 0) return
  timerId = setInterval(() => {
    remainingSeconds.value -= 1
    if (remainingSeconds.value <= 0) {
      remainingSeconds.value = 0
      stopTimer()
      toast('考试时间到，系统已自动提交答卷', 'warn', 4000)
      submitPaper(true)
    }
  }, 1000)
}
function stopTimer() {
  if (timerId) { clearInterval(timerId); timerId = null }
}

// ===== 草稿暂存（防止刷新 / 误退出丢失作答）=====
// 错题重做用独立的草稿 key，避免与整卷作答互相覆盖
function draftKey() {
  return `exam-draft-${route.params.id}${practiceKeys.value ? '-retry' : ''}`
}
function allDraftKeys() {
  return [`exam-draft-${route.params.id}`, `exam-draft-${route.params.id}-retry`]
}
function saveDraft() {
  try {
    localStorage.setItem(draftKey(), JSON.stringify({
      answers: { ...userAnswers },
      remainingSeconds: remainingSeconds.value,
      savedAt: Date.now()
    }))
  } catch (e) { /* 隐私模式等场景下忽略存储异常 */ }
}
function loadDraft() {
  try { return JSON.parse(localStorage.getItem(draftKey()) || 'null') } catch (e) { return null }
}
function clearDraft() {
  try { allDraftKeys().forEach(k => localStorage.removeItem(k)) } catch (e) { /* ignore */ }
}
let draftTimer = null
watch(userAnswers, () => {
  if (mode.value !== 'exam') return
  clearTimeout(draftTimer)
  draftTimer = setTimeout(saveDraft, 500)
}, { deep: true })

// ===== 答题 =====
function resetAnswers() {
  Object.keys(userAnswers).forEach(k => { delete userAnswers[k] })
  allQuestions.value.forEach(q => {
    userAnswers[qKey(q)] = q.questionType === 'MULTIPLE_CHOICE' ? [] : ''
  })
}
function enterExamSession(firstKey) {
  mode.value = 'exam'
  gradeResult.value = null
  remainingSeconds.value = durationSeconds()
  currentKey.value = firstKey ?? null
  startTimer()
  nextTick(() => { if (firstKey) jumpTo(firstKey); saveDraft() })
}

async function enterExamMode() {
  const draft = loadDraft()
  const hasDraft = !!(draft && draft.answers && Object.keys(draft.answers).length)
  const restore = hasDraft
    ? await confirmDialog({
        title: '恢复作答进度',
        message: '检测到上次未提交的答卷，是否恢复上次的作答进度？',
        confirmText: '恢复进度',
        cancelText: '重新开始'
      })
    : false

  practiceKeys.value = null
  resultFilter.value = 'all'
  // 进答题态前先收起解析，否则等于把答案摊在卷面上
  showAnswers.value = false
  resetAnswers()
  const first = allQuestions.value[0]
  enterExamSession(first ? qKey(first) : null)

  if (restore) {
    Object.entries(draft.answers).forEach(([k, v]) => { userAnswers[k] = v })
    remainingSeconds.value = draft.remainingSeconds > 0 ? draft.remainingSeconds : durationSeconds()
    saveDraft()
  } else {
    clearDraft()
  }
}

async function exitExamMode() {
  const ok = await confirmDialog({
    title: '退出答题',
    message: '退出后本次作答内容将被清空，确定退出吗？',
    confirmText: '退出并清空',
    danger: true
  })
  if (!ok) return
  stopTimer()
  clearDraft()
  Object.keys(userAnswers).forEach(k => { delete userAnswers[k] })
  practiceKeys.value = null
  resultFilter.value = 'all'
  mode.value = 'preview'
}

// 错题重做：保留上次其它题目的作答，仅清空待重做的题，只渲染错题
function startRetryMode() {
  const list = allQuestions.value.filter(q => answerState(qKey(q)) !== 'correct')
  if (!list.length) { toast('本次没有需要重做的题目'); return }

  const details = gradeResult.value?.details || []
  details.forEach(d => {
    const q = questionByKey(d.sortOrder)
    if (!q) return
    const ua = String(d.userAnswer ?? '')
    userAnswers[d.sortOrder] = q.questionType === 'MULTIPLE_CHOICE'
      ? ua.split('').filter(c => /[A-Z]/.test(c))
      : ua
  })
  list.forEach(q => {
    userAnswers[qKey(q)] = q.questionType === 'MULTIPLE_CHOICE' ? [] : ''
  })

  practiceKeys.value = list.map(q => qKey(q))
  resultFilter.value = 'all'
  const firstKey = practiceKeys.value[0]
  enterExamSession(firstKey)
  toast(`已进入错题重做，共 ${list.length} 题`, 'info')
}

function backToPreview() {
  resultFilter.value = 'all'
  practiceKeys.value = null
  mode.value = 'preview'
}

function toggleMultiChoice(key, val) {
  const cur = userAnswers[key]
  const arr = Array.isArray(cur) ? cur : (cur ? [cur] : [])
  userAnswers[key] = arr.includes(val) ? arr.filter(x => x !== val) : [...arr, val].sort()
}

function jumpTo(key) {
  currentKey.value = key
  const el = document.getElementById('q-' + key)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

// ===== 键盘操作（答题模式）=====
function onKeydown(e) {
  if (mode.value !== 'exam') return
  if (e.ctrlKey || e.metaKey || e.altKey) return
  const tag = (e.target?.tagName || '').toLowerCase()
  // 只有在「文本录入」控件里才让出键盘；radio/checkbox 获得焦点时仍要保留快捷键
  const typing = tag === 'textarea'
    || (tag === 'input' && !['checkbox', 'radio'].includes((e.target.type || '').toLowerCase()))
    || tag === 'select'
    || !!e.target?.isContentEditable
  const list = displayQuestions.value
  if (!list.length) return
  const idx = list.findIndex(q => qKey(q) === currentKey.value)

  if (typing) {
    // 文本作答时只接管 Esc（失焦返回快捷键导航）
    if (e.key === 'Escape') e.target.blur()
    return
  }

  if (['ArrowDown', 'ArrowRight', 'PageDown'].includes(e.key)) {
    e.preventDefault()
    if (idx >= 0 && idx < list.length - 1) jumpTo(qKey(list[idx + 1]))
    return
  }
  if (['ArrowUp', 'ArrowLeft', 'PageUp'].includes(e.key)) {
    e.preventDefault()
    if (idx > 0) jumpTo(qKey(list[idx - 1]))
    return
  }

  // A/B/C/D 或 1/2/3/4 选择当前题的选项
  let letter = null
  if (/^[a-zA-Z]$/.test(e.key)) letter = e.key.toUpperCase()
  else if (/^[1-9]$/.test(e.key)) letter = String.fromCharCode(64 + Number(e.key))
  if (!letter) return

  const q = questionByKey(currentKey.value)
  if (!q || !q.options?.length) return
  if (!letterOptions(q).includes(letter)) return
  e.preventDefault()
  const key = qKey(q)
  if (q.questionType === 'MULTIPLE_CHOICE') toggleMultiChoice(key, letter)
  else userAnswers[key] = letter
}

async function submitPaper(skipConfirm = false) {
  if (!skipConfirm) {
    const tip = unansweredCount.value > 0 ? `还有 ${unansweredCount.value} 题未作答。\n` : ''
    const ok = await confirmDialog({
      title: '提交答卷',
      message: `${tip}提交后不可修改，确定要提交吗？`,
      confirmText: '提交答卷',
      danger: unansweredCount.value > 0
    })
    if (!ok) return
  }
  submitting.value = true
  try {
    const answers = {}
    Object.entries(userAnswers).forEach(([key, val]) => {
      // 只对客观题做「取出选项字母」的规整：主观题（含填空/论述）是整段文字，
      // 若其中恰好以 "A." 开头而被截成 "A"，AI 拿到的作答就废了
      const obj = isObjectiveQuestion(questionByKey(Number(key)))
      let answer = Array.isArray(val) ? [...val].sort().join('') : String(val).trim()
      if (obj) {
        const m = answer.match(/^([A-Z])[.、]/)
        if (m) answer = m[1]
      }
      answers[key] = answer
    })
    const res = await fetch(`${API_BASE}/exam-paper/grade`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ paperId: route.params.id, answers })
    }).then(r => r.json())
    if (res.code === 0) {
      stopTimer()
      clearDraft()
      practiceKeys.value = null
      resultFilter.value = 'all'
      gradeResult.value = res.data
      mode.value = 'result'
      window.scrollTo({ top: 0, behavior: 'smooth' })
      const passed = res.data.totalScore > 0 && res.data.userScore >= res.data.totalScore * 0.6
      // 主观题由 AI 判分，可能有个别题没批出来，要明确告知而不是让它看起来像答错
      const pending = (res.data.details || []).filter(d => d.pending).length
      const suffix = pending > 0 ? `（${pending} 题待评阅）` : ''
      toast(`已提交：${res.data.userScore} / ${res.data.totalScore} 分${suffix}`,
        passed ? (pending > 0 ? 'warn' : 'success') : 'warn', 3200)
    } else {
      toastError(res.message || '提交失败')
    }
  } catch (e) {
    console.error(e)
    toastError('网络错误，提交失败')
  } finally {
    submitting.value = false
  }
}

// ===== 结果判定 =====
function getResult(key) {
  return gradeResult.value?.details?.find(d => d.sortOrder === key)
}
// 判定结果：correct / partial / wrong / blank / pending
// partial = 主观题拿了部分分；pending = AI 没批出来（分数不可信，不能当答错）
function answerState(key) {
  const d = getResult(key)
  if (!d) return 'blank'
  const ua = (d.userAnswer ?? '').toString().trim()
  if (ua === '') return 'blank'
  if (d.pending) return 'pending'
  if (d.correct) return 'correct'
  const full = Number(d.fullScore ?? questionByKey(key)?.score) || 0
  const got = Number(d.score) || 0
  if (full > 0 && got > 0 && got < full) return 'partial'
  return 'wrong'
}
// 结果标记：答对/答错用符号，未作答、部分得分、待评阅用带语义的小标签
// （原来未作答渲染的是一个孤立的破折号，容易被误读成排版残留）
const MARK_TEXT = { correct: '✓', wrong: '✗', partial: '部分', blank: '未答', pending: '待评' }
const MARK_TITLE = {
  correct: '答对',
  wrong: '答错',
  partial: '部分得分',
  blank: '未作答',
  pending: '待评阅'
}
function answerMark(key) {
  const s = answerState(key)
  return MARK_TEXT[s] || MARK_TEXT.blank
}
// 完整语义：供 tooltip 与无障碍读取（✓ / ✗ 单独读出来没有含义）
function answerTitle(key) {
  const s = answerState(key)
  return MARK_TITLE[s] || MARK_TITLE.blank
}
function userAnswerText(key) {
  const ua = getResult(key)?.userAnswer
  return ua === undefined || ua === null || String(ua).trim() === '' ? '' : String(ua)
}
// 客观题判定，与后端 ExamPaperServiceImpl.isObjective 保持同一口径：
// 题型白名单 或 有选项（后者用于兜住 MATERIAL 的选择题子题）
const OBJECTIVE_TYPES = ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE']
function isObjectiveQuestion(q) {
  if (!q) return false
  if (OBJECTIVE_TYPES.includes(q.questionType)) return true
  return Array.isArray(q.options) && q.options.length > 0
}
// 该选项是否属于正确答案（兼容多选 "ACE"、"A,C,E" 等格式）
function isCorrectOption(detail, opt) {
  const ca = detail?.correctAnswer
  if (!ca) return false
  return ca.includes(optLetter(opt))
}
// 该选项是否为用户选错的选项
function isWrongOption(detail, opt) {
  if (!detail || detail.correct) return false
  const ua = detail.userAnswer || ''
  return ua.includes(optLetter(opt)) && !isCorrectOption(detail, opt)
}
// 该选项是否为用户自己选的（用于叠加「你的答案」角标）
function isUserChoice(detail, opt) {
  if (!detail) return false
  return (detail.userAnswer || '').includes(optLetter(opt))
}

// 结果页筛选计数
const resultCounts = computed(() => {
  const all = allQuestions.value.length
  if (mode.value !== 'result' || !gradeResult.value) return { all, wrong: 0, blank: 0, pending: 0 }
  let wrong = 0, blank = 0, pending = 0
  allQuestions.value.forEach(q => {
    const s = answerState(qKey(q))
    // 部分得分也算"错题"：它确实丢了分，值得回看
    if (s === 'wrong' || s === 'partial') wrong++
    else if (s === 'blank') blank++
    else if (s === 'pending') pending++
  })
  return { all, wrong, blank, pending }
})
const retryableCount = computed(() => resultCounts.value.wrong + resultCounts.value.blank)
// 待评阅：已作答但 AI 没批出来。不计入"错题重做"——作答是完整的，重做没有意义
const pendingCount = computed(() => resultCounts.value.pending || 0)

// ===== 成绩分析：按题型 / 知识点聚合得分率 =====
function rateClass(rate) { return rate >= 0.8 ? 'good' : rate >= 0.6 ? 'mid' : 'poor' }
function pct(rate) { return Math.round(Math.max(0, Math.min(1, rate)) * 100) + '%' }

const analysis = computed(() => {
  const details = gradeResult.value?.details
  if (!details?.length) return null
  const byType = new Map()
  const byKp = new Map()
  let correctCount = 0, blankCount = 0

  details.forEach(d => {
    const q = questionByKey(d.sortOrder)
    if (!q) return
    const full = Number(q.score) || 0
    // 直接用后端给的得分：主观题是部分分（如 7/10），不能再按 correct 二值化成满分或 0
    const earned = Number(d.score) || 0
    if (d.correct) correctCount++
    else if (answerState(d.sortOrder) === 'blank') blankCount++

    const t = q.questionType
    if (!byType.has(t)) byType.set(t, { label: typeLabel[t] || t, full: 0, earned: 0, total: 0, correct: 0 })
    const tb = byType.get(t)
    tb.full += full; tb.earned += earned; tb.total++
    if (d.correct) tb.correct++

    const kps = q.knowledgePoints?.length ? q.knowledgePoints : ['未标注知识点']
    kps.forEach(kp => {
      if (!byKp.has(kp)) byKp.set(kp, { label: kp, full: 0, earned: 0, total: 0, correct: 0 })
      const kb = byKp.get(kp)
      kb.full += full; kb.earned += earned; kb.total++
      if (d.correct) kb.correct++
    })
  })

  const withRate = o => ({ ...o, rate: o.full > 0 ? o.earned / o.full : (o.total ? o.correct / o.total : 0) })
  const types = [...byType.values()].map(withRate).sort((a, b) => b.full - a.full)
  const kps = [...byKp.values()].map(withRate).sort((a, b) => a.rate - b.rate || b.total - a.total)

  return {
    types,
    kps: kps.slice(0, 24),
    weak: kps.filter(o => o.rate < 0.6).slice(0, 8),
    correctCount,
    blankCount,
    wrongCount: details.length - correctCount - blankCount
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', measureHeader)
  stopTimer()
  clearTimeout(draftTimer)
})
</script>

<template>
  <div class="paper-detail-page" :class="{ 'is-exporting': !!exportKind }" :style="{ '--paper-sticky-top': headerOffset + 'px' }">
    <div v-if="loading" class="state-tip">加载中...</div>
    <div v-else-if="!paper" class="state-tip">试卷不存在</div>

    <template v-else>
      <!-- ===== 工具栏 ===== -->
      <div class="toolbar">
        <UiButton size="md" @click="goBack">
          <template #icon><span aria-hidden="true">←</span></template>
          返回列表
        </UiButton>
        <div class="tb-right">
          <div v-if="mode === 'exam'" class="timer" :class="{ 'timer-warn': remainingSeconds <= 300 }" title="剩余作答时间">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9"/><polyline points="12 7 12 12 15.5 14"/>
            </svg>
            <span>{{ formatRemaining() }}</span>
          </div>
          <UiButton v-if="mode === 'preview'" variant="primary" size="lg" @click="enterExamMode">开始答题</UiButton>
          <UiButton v-if="mode === 'preview'" variant="secondary" size="lg" @click="showAnswers = !showAnswers">
            {{ showAnswers ? '收起解析' : '查看解析' }}
          </UiButton>
          <span v-if="mode === 'exam'" class="only-wide">
            <UiButton size="md" @click="exitExamMode">退出答题</UiButton>
          </span>
          <div v-if="mode !== 'exam'" id="export-btn-wrap" class="export-wrap">
            <UiButton
              variant="success"
              size="lg"
              :loading="exporting"
              :disabled="exporting"
              @click.stop="showExportMenu = !showExportMenu"
            >
              {{ exporting ? '导出中' : '导出' }} <span class="em-caret" aria-hidden="true">▾</span>
            </UiButton>
            <div v-if="showExportMenu" class="export-menu">
              <div class="em-group"><div class="em-label">PDF</div>
                <button class="em-item" @click="handleExportClick('pdf', true)">含答案</button>
                <button class="em-item" @click="handleExportClick('pdf', false)">仅题目</button>
              </div>
              <div class="em-divider"></div>
              <div class="em-group"><div class="em-label">Word</div>
                <button class="em-item" @click="handleExportClick('word', true)">含答案</button>
                <button class="em-item" @click="handleExportClick('word', false)">仅题目</button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== 结果页操作条：筛选 + 错题重做 ===== -->
      <div v-if="mode === 'result' && gradeResult" class="result-actions">
        <div class="ra-filter">
          <button class="ra-chip" :class="{ active: resultFilter === 'all' }" @click="resultFilter = 'all'">
            全部 <b>{{ resultCounts.all }}</b>
          </button>
          <button class="ra-chip ra-chip-wrong" :class="{ active: resultFilter === 'wrong' }" @click="resultFilter = 'wrong'">
            错题 <b>{{ resultCounts.wrong }}</b>
          </button>
          <button class="ra-chip ra-chip-blank" :class="{ active: resultFilter === 'blank' }" @click="resultFilter = 'blank'">
            未作答 <b>{{ resultCounts.blank }}</b>
          </button>
        </div>
        <UiButton variant="warning" size="sm" :disabled="retryableCount === 0" @click="startRetryMode">
          错题重做{{ retryableCount ? `（${retryableCount}）` : '' }}
        </UiButton>
      </div>

      <!-- ===== 试卷正文 ===== -->
      <div v-show="!exportKind" id="paper-content" class="paper-body" :class="{ 'is-exam': mode === 'exam' }">
        <div class="paper-title-block">
          <h1 class="paper-title">{{ paper.paperName }}</h1>
          <div class="paper-meta">{{ paper.questions?.length || 0 }} 题 · 满分 {{ paper.totalScore }} 分 · {{ paper.durationMinutes }} 分钟</div>
          <div v-if="mode === 'result' && gradeResult" class="score-banner" :class="gradeResult.userScore >= gradeResult.totalScore * 0.6 ? 'pass' : 'fail'">
            得分：{{ gradeResult.userScore }} / {{ gradeResult.totalScore }} 分
            （{{ gradeResult.totalScore ? (gradeResult.userScore / gradeResult.totalScore * 100).toFixed(1) : '0.0' }}%）
            <span class="sb-extra">· 已答 {{ answeredCount }} / {{ progressQuestions.length }} 题<template v-if="unansweredCount > 0">，{{ unansweredCount }} 题未作答</template></span>
            <span v-if="pendingCount > 0" class="sb-extra">· {{ pendingCount }} 题待评阅</span>
          </div>
        </div>

        <!-- 装订说明：由试卷自身数据生成，随卷变化 -->
        <div class="paper-info">
          <div class="pi-row">
            <span class="pi-label">题型构成</span>
            <span class="pi-items">
              <span v-for="t in composition.types" :key="t.label" class="pi-item">
                {{ t.label }} <b>{{ t.count }}</b> 题 · <b>{{ t.score }}</b> 分
              </span>
            </span>
          </div>
          <div v-if="composition.kps.length" class="pi-row">
            <span class="pi-label">知识点覆盖</span>
            <span class="pi-items">
              <span v-for="kp in composition.kps" :key="kp" class="pi-kp">{{ kp }}</span>
            </span>
          </div>
          <div class="pi-row">
            <span class="pi-label">作答说明</span>
            <span class="pi-items">
              <span class="pi-rule">共 {{ paper.questions?.length || 0 }} 题，满分 {{ paper.totalScore }} 分</span>
              <span class="pi-rule">限时 {{ paper.durationMinutes }} 分钟<template v-if="mode === 'exam'">，时间到将自动交卷</template></span>
              <span class="pi-rule">选择题勾选选项作答<template v-if="composition.hasMultiple">，多选题需全部选对才计分</template></span>
              <span v-if="composition.hasSubjective" class="pi-rule">主观题在输入框内作答，提交后可查看参考答案与解析</span>
            </span>
          </div>
        </div>

        <!-- 成绩分析 -->
        <div v-if="mode === 'result' && analysis" class="analysis">
          <div class="an-head">
            <span class="an-title">成绩分析</span>
            <span class="an-chips">
              <span class="an-chip good">正确 {{ analysis.correctCount }}</span>
              <span class="an-chip poor">错误 {{ analysis.wrongCount }}</span>
              <span class="an-chip blank">未作答 {{ analysis.blankCount }}</span>
            </span>
          </div>

          <div class="an-section">
            <div class="an-sub">按题型得分率</div>
            <div v-for="t in analysis.types" :key="t.label" class="an-row">
              <div class="an-row-top">
                <span class="an-name">{{ t.label }}</span>
                <span class="an-val">{{ t.earned }} / {{ t.full }} 分 · 答对 {{ t.correct }}/{{ t.total }} 题</span>
              </div>
              <div class="an-bar"><i class="an-fill" :class="rateClass(t.rate)" :style="{ width: pct(t.rate) }"></i></div>
            </div>
          </div>

          <div class="an-section">
            <div class="an-sub">
              知识点掌握度
              <span class="an-note">同一题目含多个知识点时会重复计入</span>
            </div>
            <div v-if="analysis.weak.length" class="an-weak">
              <span class="an-weak-label">薄弱环节</span>
              <span v-for="w in analysis.weak" :key="w.label" class="an-weak-tag">{{ w.label }} {{ w.correct }}/{{ w.total }}</span>
            </div>
            <div v-else class="an-good">各知识点掌握情况良好</div>
            <div class="an-kp-grid">
              <div v-for="k in analysis.kps" :key="k.label" class="an-kp" :class="rateClass(k.rate)"
                :title="`得分 ${k.earned} / ${k.full} 分`">
                <span class="an-kp-name">{{ k.label }}</span>
                <span class="an-kp-val">{{ k.correct }}/{{ k.total }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="exam-layout" :class="{ 'with-palette': mode === 'exam' }">
          <div class="exam-main">
            <div v-if="!displayQuestions.length" class="filter-empty">当前筛选条件下没有题目</div>

            <div v-for="(sec, si) in sections" :key="si" class="section-block">
              <div class="section-header">
                {{ sec.label }}（共 {{ sec.questions.length }} 题，{{ sec.questions.reduce((s, q) => s + (q.score || 0), 0) }} 分）
              </div>
              <div v-for="(q, qi) in sec.questions" :key="qi" class="question-item" :id="'q-' + qKey(q, qi + 1)"
                @click="currentKey = qKey(q, qi + 1)">
                <!-- 左侧：真实题号 + 结果标记 -->
                <div class="q-number-col">
                  <div class="q-number">{{ qKey(q, qi + 1) }}.</div>
                  <div v-if="mode === 'result'" class="q-mark" :class="answerState(qKey(q, qi + 1))"
                    :title="answerTitle(qKey(q, qi + 1))">
                    {{ answerMark(qKey(q, qi + 1)) }}
                  </div>
                </div>
                <div class="q-body">
                  <div class="q-content">{{ q.content }}</div>
                  <div v-if="q.materialText" class="material-block">
                    <div class="material-label">材料</div>
                    <div class="material-text">{{ q.materialText }}</div>
                  </div>

                  <!-- 预览模式：展示选项（开启「查看解析」时把正确项标出来） -->
                  <div v-if="q.options?.length && mode === 'preview'" class="choices">
                    <div v-for="(opt, oi) in q.options" :key="oi" class="choice"
                      :class="{ 'is-correct': showAnswers && isAnswerOption(q, opt) }">
                      <span class="choice-label">{{ choiceLabel(oi) }}.</span>
                      <span class="choice-text">{{ opt.replace(/^[A-Z][.、]\s*/, '') }}</span>
                    </div>
                  </div>

                  <!-- 预览模式的「查看解析」：参考答案 / 解析 / 知识点（观感与生成结果页一致） -->
                  <div v-if="mode === 'preview' && showAnswers" class="answer-block">
                    <div class="ab-row">
                      <span class="ab-label">参考答案</span>
                      <span class="ab-answer">{{ q.answer || '—' }}</span>
                    </div>
                    <div v-if="q.analysis" class="ab-row">
                      <span class="ab-label">解析</span>
                      <span class="ab-analysis">{{ q.analysis }}</span>
                    </div>
                    <div v-if="q.knowledgePoints?.length" class="ab-tags">
                      <span v-for="kp in q.knowledgePoints" :key="kp" class="ab-tag">{{ kp }}</span>
                    </div>
                  </div>

                  <!-- 答题/结果模式：选择题 -->
                  <div v-if="mode !== 'preview' && q.options?.length" class="exam-choices">
                    <label v-for="(opt, oi) in q.options" :key="oi" class="exam-choice"
                      :class="{
                        selected: isSelected(qKey(q, qi + 1), optLetter(opt)),
                        'correct-ans': mode === 'result' && isCorrectOption(getResult(qKey(q, qi + 1)), opt),
                        'wrong-ans': mode === 'result' && isWrongOption(getResult(qKey(q, qi + 1)), opt)
                      }">
                      <!-- 多选题用 checkbox 允许多选，单选/判断题用 radio -->
                      <input v-if="q.questionType === 'MULTIPLE_CHOICE'"
                        type="checkbox"
                        :value="optLetter(opt)"
                        :checked="isSelected(qKey(q, qi + 1), optLetter(opt))"
                        @change="toggleMultiChoice(qKey(q, qi + 1), optLetter(opt))"
                        :disabled="mode === 'result'">
                      <input v-else type="radio" :name="'q_' + qKey(q, qi + 1)"
                        :value="optLetter(opt)"
                        v-model="userAnswers[qKey(q, qi + 1)]"
                        :disabled="mode === 'result'">
                      <span class="ec-label">{{ choiceLabel(oi) }}.</span>
                      <span class="ec-text">{{ opt.replace(/^[A-Z][.、]\s*/, '') }}</span>
                      <span v-if="mode === 'result' && isUserChoice(getResult(qKey(q, qi + 1)), opt)" class="ec-hint ec-hint-user">你的答案</span>
                      <span v-if="mode === 'result' && isCorrectOption(getResult(qKey(q, qi + 1)), opt)" class="ec-hint">正确答案</span>
                    </label>
                  </div>

                  <!-- 答题/结果模式：文本作答 -->
                  <div v-if="mode !== 'preview' && !q.options?.length" class="exam-text-input">
                    <textarea v-if="isLongAnswer(q)"
                      v-model="userAnswers[qKey(q, qi + 1)]"
                      :disabled="mode === 'result'"
                      :placeholder="mode === 'exam' ? '请在此输入你的答案...' : ''"
                      :aria-label="`第 ${qKey(q, qi + 1)} 题作答区`"
                      :rows="answerRows(q)"></textarea>
                    <input v-else type="text"
                      v-model="userAnswers[qKey(q, qi + 1)]"
                      :disabled="mode === 'result'"
                      :placeholder="mode === 'exam' ? '请输入答案...' : ''"
                      :aria-label="`第 ${qKey(q, qi + 1)} 题作答区`">
                  </div>

                  <!-- 结果反馈：所有题型统一展示答案 / 解析 / 知识点 -->
                  <div v-if="mode === 'result'" class="result-feedback">
                    <div class="rf-user">
                      你的答案：{{ userAnswerText(qKey(q, qi + 1)) || '(未作答)' }}
                      <span v-if="q.questionType === 'MULTIPLE_CHOICE'" class="rf-note">多选题需完全一致才计分</span>
                    </div>
                    <div class="rf-correct">
                      {{ isObjectiveQuestion(q) ? '正确答案' : '参考答案' }}：{{ getResult(qKey(q, qi + 1))?.correctAnswer || '—' }}
                    </div>
                    <!-- 主观题：AI 判分结果（可能只拿到部分分，也可能没批出来） -->
                    <template v-if="!isObjectiveQuestion(q) && getResult(qKey(q, qi + 1))">
                      <div
                        class="rf-score"
                        :class="{ 'is-pending': getResult(qKey(q, qi + 1))?.pending }"
                      >
                        <template v-if="getResult(qKey(q, qi + 1))?.pending">
                          本题待评阅
                        </template>
                        <template v-else>
                          本题得分：{{ getResult(qKey(q, qi + 1))?.score ?? 0 }} / {{ q.score }} 分
                        </template>
                      </div>
                      <div v-if="getResult(qKey(q, qi + 1))?.aiComment" class="rf-ai">
                        AI 评语：{{ getResult(qKey(q, qi + 1))?.aiComment }}
                      </div>
                    </template>
                    <div v-if="q.analysis" class="rf-analysis">解析：{{ q.analysis }}</div>
                    <div v-if="q.knowledgePoints?.length" class="rf-tags">
                      <span v-for="kp in q.knowledgePoints" :key="kp" class="rf-tag">{{ kp }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 答题卡 -->
          <aside v-if="mode === 'exam'" class="answer-sheet">
            <div class="as-head">
              <span class="as-title">答题卡</span>
              <span v-if="practiceKeys" class="as-badge">错题重做</span>
            </div>

            <div class="as-progress">
              <div class="as-progress-track"><i class="as-progress-fill" :style="{ width: progressPct }"></i></div>
              <span class="as-progress-text">{{ answeredCount }} / {{ progressQuestions.length }}</span>
            </div>

            <div class="as-groups">
              <div v-for="(sec, si) in sections" :key="si" class="as-group">
                <div class="as-group-label">{{ sec.label }}<span class="as-group-count">{{ sec.questions.length }}</span></div>
                <div class="as-grid">
                  <button v-for="q in sec.questions" :key="qKey(q)" class="as-cell"
                    :class="{ answered: isAnswered(q), current: currentKey === qKey(q) }"
                    :aria-label="`跳到第 ${qKey(q)} 题`"
                    @click="jumpTo(qKey(q))">{{ qKey(q) }}</button>
                </div>
              </div>
            </div>

            <div class="as-legend">
              <span class="as-legend-item"><i class="as-dot is-answered"></i>已答</span>
              <span class="as-legend-item"><i class="as-dot is-blank"></i>未答</span>
              <span class="as-legend-item"><i class="as-dot is-current"></i>当前</span>
            </div>
            <div class="as-tips">快捷键：<b>↑↓</b> 切题 · <b>A/B/C/D</b> 选答案</div>
          </aside>
        </div>
      </div>

      <!-- ===== PDF 导出专用渲染 =====
           内容只依赖数据层与 exportKind，所以预览态也能导出答案卷，
           且不会夹带用户的作答、勾选与得分。

           定位上刻意保持「静态流式块」，与改造前的 #paper-content 完全一致 ——
           这是本工程里唯一被验证过确实能出 PDF 的条件。
           教训：不要给它加 position: fixed/absolute，也不要在它上面盖遮罩层。
           html2canvas 会把同一区域内 z-index 更高的覆盖层一并算进画面，
           截出来就只剩纯底色 —— 导出「全空白」就是这么来的。
           导出期间由 #paper-content 的 v-show 让位，两者不并存。 -->
      <div v-if="exportKind" id="paper-export" class="paper-export">
        <h1 class="pe-title">{{ paper.paperName }}</h1>
        <div class="pe-meta">{{ paper.questions?.length || 0 }} 题 · 满分 {{ paper.totalScore }} 分 · {{ paper.durationMinutes }} 分钟</div>
        <div class="pe-compose">
          <span v-for="t in composition.types" :key="t.label" class="pe-compose-item">{{ t.label }} {{ t.count }} 题 · {{ t.score }} 分</span>
        </div>

        <div v-for="(sec, si) in exportSections" :key="si" class="pe-section">
          <div class="pe-section-header">
            {{ sec.label }}（共 {{ sec.questions.length }} 题，{{ sectionScore(sec.questions) }} 分）
          </div>
          <div v-for="(q, qi) in sec.questions" :key="qi" class="pe-q">
            <div class="pe-q-num">{{ qKey(q, qi + 1) }}.</div>
            <div class="pe-q-body">
              <div class="pe-q-content">{{ q.content }}</div>
              <div v-if="q.materialText" class="pe-material">
                <div class="pe-material-label">【材料】</div>
                <div class="pe-material-text">{{ q.materialText }}</div>
              </div>
              <div v-if="q.options?.length" class="pe-options">
                <div v-for="(opt, oi) in q.options" :key="oi" class="pe-option"
                  :class="{ 'is-correct': exportKind === 'key' && isAnswerOption(q, opt) }">{{ opt }}</div>
              </div>
              <div v-if="exportKind === 'key'" class="pe-answer">
                <div class="pe-answer-line">参考答案：{{ q.answer || '—' }}</div>
                <div v-if="q.analysis" class="pe-analysis">解析：{{ q.analysis }}</div>
                <div v-if="q.knowledgePoints?.length" class="pe-kps">
                  <span v-for="kp in q.knowledgePoints" :key="kp" class="pe-kp">{{ kp }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 提交栏（答题时吸底） -->
      <div v-if="mode === 'exam'" class="submit-bar">
        <div class="sb-info">
          已答 {{ answeredCount }} / {{ progressQuestions.length }}
          <span v-if="unansweredCount > 0" class="sb-warn">· 还有 {{ unansweredCount }} 题未作答</span>
          <span v-else class="sb-ok">· 全部作答完成</span>
          <span v-if="practiceKeys" class="sb-mode">· 错题重做</span>
        </div>
        <div class="sb-btns">
          <span class="sb-timer only-narrow" :class="{ warn: remainingSeconds <= 300 }">{{ formatRemaining() }}</span>
          <span class="only-narrow"><UiButton size="sm" @click="exitExamMode">退出</UiButton></span>
          <UiButton variant="primary" size="lg" :loading="submitting" @click="submitPaper()">
            {{ submitting ? '提交中...' : '提交答卷' }}
          </UiButton>
        </div>
      </div>
      <div v-else-if="mode === 'result'" class="submit-bar sb-center">
        <UiButton size="sm" @click="backToPreview">返回预览</UiButton>
        <UiButton variant="warning" size="sm" :disabled="retryableCount === 0" @click="startRetryMode">
          错题重做{{ retryableCount ? `（${retryableCount}）` : '' }}
        </UiButton>
        <UiButton variant="primary" size="sm" @click="enterExamMode">重新答题</UiButton>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* 页面容器宽度由 layout 统一控制；卷面本身单独收窄到可读行宽 */
.paper-detail-page { width: 100%; }

.state-tip {
  text-align: center;
  padding: 60px 20px;
  color: var(--color-text-muted);
  font-size: var(--fs-md);
}

/* ===== 工具栏 ===== */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}
.tb-right {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.export-wrap { position: relative; }
.em-caret { font-size: 0.7em; opacity: 0.85; }
.export-menu {
  position: absolute; right: 0; top: calc(100% + 4px);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  padding: 6px;
  min-width: 132px;
  z-index: 50;
}
.em-group { display: flex; flex-direction: column; gap: 2px; }
.em-label {
  font-size: var(--fs-2xs);
  font-weight: var(--fw-bold);
  color: var(--color-text-muted);
  padding: 4px 10px 2px;
  letter-spacing: 1px;
}
.em-item {
  padding: 6px 10px;
  font-size: var(--fs-sm);
  color: var(--color-text-secondary);
  border-radius: var(--radius-sm);
  text-align: left;
  transition: background-color var(--transition-fast);
}
.em-item:hover { background: var(--color-bg); color: var(--color-text-primary); }
.em-divider { height: 1px; background: var(--color-border-light); margin: 4px 8px; }

/* 倒计时 */
.timer {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-bg);
  color: var(--color-primary-dark);
  font-size: var(--fs-sm);
  font-weight: var(--fw-bold);
  font-variant-numeric: tabular-nums;
}
.timer-warn { background: var(--color-error-bg); color: var(--color-error-text); }

/* ===== 结果页操作条 ===== */
.result-actions {
  display: flex; align-items: center; justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-bottom: var(--space-4);
  padding: var(--space-3);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}
.ra-filter { display: flex; gap: 6px; flex-wrap: wrap; }
.ra-chip {
  padding: 6px 12px;
  font-size: var(--fs-xs);
  font-weight: var(--fw-semibold);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  transition: all var(--transition-fast);
}
.ra-chip b { font-weight: var(--fw-bold); margin-left: 2px; }
.ra-chip:hover { border-color: var(--color-primary); color: var(--color-primary); }
.ra-chip.active { background: var(--color-primary-bg); border-color: var(--color-primary); color: var(--color-primary-dark); }
.ra-chip-wrong.active { background: var(--color-error-bg); border-color: var(--color-error); color: var(--color-error-text); }
.ra-chip-blank.active { background: var(--color-neutral-bg); border-color: var(--color-text-muted); color: var(--color-neutral-text); }

/* ===== 试卷正文 =====
   卷面做成一张真正的「纸」：白底 + 圆角 + 投影，从分层页面背景上浮起来，
   这样页面才有前后层次，而不是整屏一个底色。 */
.paper-body {
  max-width: var(--paper-measure);
  margin: 0 auto;
  padding: var(--space-10) var(--space-10) var(--space-8);
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  font-family: var(--font-serif);
  color: var(--color-text-primary);
  font-size: var(--fs-md);
  line-height: var(--lh-loose);
}
/* 答题态需要容纳「卷面 + 答题卡」双栏，纸张相应放宽、内边距收窄 */
.paper-body.is-exam {
  max-width: var(--paper-measure-wide);
  padding: var(--space-8) var(--space-6);
}

/* 卷头不再画横线：纸张与下方说明条已经分层，标题层级由字号/居中/字距表达 */
.paper-title-block {
  text-align: center;
  margin-bottom: var(--space-6);
}
.paper-title {
  font-size: var(--fs-xl);
  font-weight: var(--fw-bold);
  letter-spacing: 2px;
  margin-bottom: 6px;
}
.paper-meta { font-size: var(--fs-xs); color: var(--color-text-secondary); }

/* 得分条 */
.score-banner {
  margin: var(--space-3) auto 0;
  padding: 8px 20px;
  display: inline-block;
  border-radius: var(--radius-sm);
  font-size: var(--fs-lg);
  font-weight: var(--fw-bold);
  letter-spacing: 1px;
}
.score-banner.pass { background: var(--color-success-bg); color: var(--color-success-text); }
.score-banner.fail { background: var(--color-error-bg); color: var(--color-error-text); }
.sb-extra { font-size: var(--fs-2xs); font-weight: var(--fw-normal); letter-spacing: 0; }

/* ===== 试卷说明（替代原先写死的纸质考试须知）=====
   位于白色纸面上，因此用淡绿底条而非描边卡片 —— 避免白底白框两层线 */
.paper-info {
  margin-bottom: var(--space-6);
  padding: var(--space-4) var(--space-5);
  background: var(--color-primary-bg);
  border-radius: var(--radius-lg);
  font-family: var(--font-sans);
  line-height: var(--lh-normal);
}
.pi-row { display: flex; gap: var(--space-3); padding: 5px 0; }
.pi-row + .pi-row { border-top: 1px dashed var(--color-primary-border); }
.pi-label {
  flex-shrink: 0;
  width: 84px;
  font-size: var(--fs-xs);
  font-weight: var(--fw-bold);
  color: var(--color-text-muted);
  padding-top: 2px;
}
.pi-items { display: flex; flex-wrap: wrap; gap: 6px 10px; align-items: center; }
.pi-item { font-size: var(--fs-xs); color: var(--color-text-secondary); }
.pi-item b { color: var(--color-text-primary); font-variant-numeric: tabular-nums; }
.pi-kp {
  font-size: var(--fs-2xs);
  padding: 1px 9px;
  border-radius: var(--radius-full);
  background: var(--color-primary-bg);
  color: var(--color-primary-dark);
}
.pi-rule {
  font-size: var(--fs-xs);
  color: var(--color-text-secondary);
  padding-left: 14px;
  position: relative;
}
.pi-rule::before {
  content: '';
  position: absolute;
  left: 3px;
  top: 0.62em;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--color-primary-light);
}

/* ===== 成绩分析 ===== */
.analysis {
  font-family: var(--font-sans);
  line-height: var(--lh-normal);
  margin-bottom: var(--space-6);
  padding: var(--space-4) var(--space-5);
  background: var(--color-bg);
  border-radius: var(--radius-lg);
}
.an-head { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); flex-wrap: wrap; }
.an-title { font-size: var(--fs-md); font-weight: var(--fw-bold); color: var(--color-text-primary); }
.an-chips { display: flex; gap: 6px; flex-wrap: wrap; }
.an-chip {
  font-size: var(--fs-2xs);
  font-weight: var(--fw-bold);
  padding: 2px 9px;
  border-radius: var(--radius-full);
  background: var(--color-neutral-bg);
  color: var(--color-neutral-text);
}
.an-chip.good { background: var(--color-success-bg); color: var(--color-success-text); }
.an-chip.poor { background: var(--color-error-bg); color: var(--color-error-text); }
.an-chip.blank { background: var(--color-neutral-bg); color: var(--color-text-secondary); }

.an-section { margin-top: var(--space-4); }
.an-sub { font-size: var(--fs-xs); font-weight: var(--fw-bold); color: var(--color-text-secondary); margin-bottom: var(--space-2); }
.an-note { font-size: var(--fs-2xs); font-weight: var(--fw-normal); color: var(--color-text-muted); margin-left: 6px; }

.an-row { margin-bottom: 10px; }
.an-row-top { display: flex; justify-content: space-between; gap: 10px; font-size: var(--fs-xs); }
.an-name { color: var(--color-text-primary); font-weight: var(--fw-semibold); }
.an-val { color: var(--color-text-secondary); font-variant-numeric: tabular-nums; }
.an-bar { margin-top: 4px; height: 6px; border-radius: var(--radius-full); background: var(--color-bg); overflow: hidden; }
.an-fill { display: block; height: 100%; border-radius: var(--radius-full); transition: width 0.4s ease; }
.an-fill.good { background: var(--color-success); }
.an-fill.mid { background: var(--color-warning); }
.an-fill.poor { background: var(--color-error); }

.an-weak { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-bottom: 10px; }
.an-weak-label { font-size: var(--fs-2xs); font-weight: var(--fw-bold); color: var(--color-error-text); }
.an-weak-tag {
  font-size: var(--fs-2xs);
  font-weight: var(--fw-semibold);
  padding: 2px 9px;
  border-radius: var(--radius-full);
  background: var(--color-error-bg);
  color: var(--color-error-text);
  border: 1px solid var(--color-error-border);
}
.an-good { font-size: var(--fs-xs); color: var(--color-success-text); margin-bottom: 10px; }

.an-kp-grid { display: flex; flex-wrap: wrap; gap: 6px; }
.an-kp {
  display: inline-flex; align-items: center; gap: 6px;
  max-width: 100%;
  padding: 4px 10px;
  border-radius: var(--radius-full);
  font-size: var(--fs-xs);
  border: 1px solid var(--color-border);
  background: var(--color-neutral-bg);
  color: var(--color-neutral-text);
}
.an-kp.good { background: var(--color-success-bg); border-color: var(--color-success-border); color: var(--color-success-text); }
.an-kp.mid { background: var(--color-warning-bg); border-color: var(--color-warning-border); color: var(--color-warning-text); }
.an-kp.poor { background: var(--color-error-bg); border-color: var(--color-error-border); color: var(--color-error-text); }
.an-kp-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 200px; }
.an-kp-val { font-weight: var(--fw-bold); font-variant-numeric: tabular-nums; }

/* 答题区布局：答题时右侧挂答题卡 */
.exam-layout.with-palette {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: var(--space-6);
  align-items: start;
}
.exam-main { min-width: 0; }
.filter-empty {
  padding: 40px 0;
  text-align: center;
  color: var(--color-text-muted);
  font-family: var(--font-sans);
  font-size: var(--fs-sm);
}

.section-block { margin-bottom: var(--space-8); }
.section-header {
  font-size: var(--fs-sm);
  font-weight: var(--fw-bold);
  margin-bottom: var(--space-4);
  padding-bottom: 6px;
  border-bottom: 1px solid var(--color-border);
}

/* 题目 */
.question-item { display: flex; gap: var(--space-2); margin-bottom: var(--space-5); scroll-margin-top: 90px; }
.q-number-col { min-width: 40px; text-align: right; }
.q-number { font-weight: var(--fw-bold); }
.q-mark { font-size: var(--fs-lg); font-weight: var(--fw-bold); margin-top: 4px; line-height: 1; }
.q-mark.correct { color: var(--color-success-text); }
.q-mark.wrong { color: var(--color-error-text); }
/* 小标签形态的标记：窄栏里只放符号会丢语义（未作答 / 部分得分 / 待评阅三种都不是"错"）。
   需与 .q-number-col 的 min-width 对齐，否则会顶开卷面正文。 */
.q-mark.blank,
.q-mark.partial,
.q-mark.pending {
  display: inline-block;
  padding: 2px 5px;
  font-family: var(--font-sans);
  font-size: var(--fs-2xs);
  font-weight: var(--fw-semibold);
  line-height: 1.2;
  white-space: nowrap;
  border-radius: var(--radius-sm);
}
.q-mark.blank {
  color: var(--color-neutral-text);
  background: var(--color-neutral-bg);
  border: 1px solid var(--color-neutral-border);
}
.q-mark.partial {
  color: var(--color-warning-text);
  background: var(--color-warning-bg);
  border: 1px solid var(--color-warning-border);
}
.q-mark.pending {
  color: var(--color-info-text);
  background: var(--color-info-bg);
  border: 1px solid var(--color-info-border);
}
.q-body { flex: 1; min-width: 0; }
.q-content { font-size: var(--fs-md); line-height: var(--lh-loose); white-space: pre-wrap; }

/* 预览选项 */
.choices { margin: 6px 0 0; display: flex; flex-direction: column; gap: 2px; }
.choice { display: flex; gap: 6px; padding-left: var(--space-5); font-size: var(--fs-sm); line-height: var(--lh-loose); }
.choice-label { font-weight: var(--fw-semibold); min-width: 20px; }
/* 「查看解析」时标出正确选项 */
.choice.is-correct .choice-label,
.choice.is-correct .choice-text { color: var(--color-success-text); font-weight: var(--fw-semibold); }

/* 预览模式的答案解析块：观感与生成结果页的「答案与解析」一致 */
.answer-block {
  margin-top: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--color-success-bg);
  border-radius: var(--radius-md);
  font-family: var(--font-sans);
  line-height: var(--lh-normal);
}
.ab-row { display: flex; gap: var(--space-3); padding: 3px 0; }
.ab-label {
  flex-shrink: 0;
  min-width: 56px;
  font-size: var(--fs-xs);
  font-weight: var(--fw-semibold);
  color: var(--color-text-secondary);
}
.ab-answer { font-size: var(--fs-sm); font-weight: var(--fw-semibold); color: var(--color-success-text); }
.ab-analysis { font-size: var(--fs-sm); color: var(--color-text-primary); white-space: pre-wrap; }
.ab-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: var(--space-2); }
.ab-tag {
  padding: 1px 8px;
  border-radius: var(--radius-full);
  background: var(--color-surface);
  color: var(--color-primary-dark);
  font-size: var(--fs-2xs);
}

/* 材料 */
.material-block {
  margin-top: var(--space-2);
  padding: 10px 12px;
  background: var(--color-bg);
  border-left: 2px solid var(--color-text-muted);
  font-size: var(--fs-xs);
  line-height: var(--lh-relaxed);
  color: var(--color-text-secondary);
}
.material-label { font-weight: var(--fw-bold); color: var(--color-text-secondary); margin-bottom: 2px; font-size: var(--fs-2xs); }
.material-text { white-space: pre-wrap; }

/* 答题选项 */
.exam-choices { margin-top: var(--space-2); display: flex; flex-direction: column; gap: 6px; }
.exam-choice {
  display: flex; align-items: center; gap: var(--space-2);
  padding: 8px 12px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: border-color var(--transition-fast), background-color var(--transition-fast);
  font-size: var(--fs-sm);
}
.exam-choice:hover:not(:has(input:disabled)) { border-color: var(--color-primary); background: var(--color-primary-soft); }
.exam-choice.selected { border-color: var(--color-primary); background: var(--color-primary-bg); }
.exam-choice.correct-ans { border-color: var(--color-success); background: var(--color-success-bg); }
.exam-choice.wrong-ans { border-color: var(--color-error); background: var(--color-error-bg); }
.exam-choice input[type="radio"],
.exam-choice input[type="checkbox"] { accent-color: var(--color-primary); width: 16px; height: 16px; flex-shrink: 0; }
.ec-label { font-weight: var(--fw-semibold); min-width: 20px; }
.ec-text { flex: 1; }
.ec-hint {
  font-size: var(--fs-2xs);
  font-weight: var(--fw-bold);
  color: var(--color-success-text);
  background: var(--color-success-bg);
  padding: 1px 8px;
  border-radius: var(--radius-sm);
  white-space: nowrap;
}
.ec-hint-user { color: var(--color-primary-dark); background: var(--color-primary-bg); }

/* ===== 答题卡 ===== */
.answer-sheet {
  /* 让开全局 sticky header，否则滚动时会钻到 header 底下 */
  position: sticky;
  top: calc(var(--paper-sticky-top, 0px) + var(--space-3));
  padding: var(--space-4);
  background: var(--color-surface);
  /* 卷面本身已是白纸，这里靠品牌色描边 + 投影把它拉成独立浮层 */
  border: 1px solid var(--color-primary-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  font-family: var(--font-sans);
  line-height: var(--lh-normal);
}
.as-head { display: flex; align-items: center; justify-content: space-between; gap: var(--space-2); }
.as-title { font-size: var(--fs-sm); font-weight: var(--fw-bold); color: var(--color-text-primary); }
.as-badge {
  font-size: var(--fs-2xs);
  font-weight: var(--fw-bold);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
  border: 1px solid var(--color-warning-border);
}

.as-progress { display: flex; align-items: center; gap: var(--space-2); margin-top: 10px; }
.as-progress-track { flex: 1; height: 5px; border-radius: var(--radius-full); background: var(--color-bg); overflow: hidden; }
.as-progress-fill { display: block; height: 100%; border-radius: var(--radius-full); background: var(--color-primary); transition: width 0.3s ease; }
.as-progress-text { font-size: var(--fs-2xs); color: var(--color-text-secondary); font-variant-numeric: tabular-nums; flex-shrink: 0; }

.as-groups { margin-top: var(--space-3); display: flex; flex-direction: column; gap: var(--space-3); }
.as-group-label {
  display: flex; align-items: center; justify-content: space-between;
  font-size: var(--fs-2xs);
  font-weight: var(--fw-bold);
  color: var(--color-text-muted);
  margin-bottom: 6px;
}
.as-group-count { font-weight: var(--fw-normal); }
.as-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 6px; }
.as-cell {
  display: flex; align-items: center; justify-content: center;
  height: 34px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: var(--fs-xs);
  font-variant-numeric: tabular-nums;
  transition: all var(--transition-fast);
  padding: 0;
}
.as-cell:hover { border-color: var(--color-primary); color: var(--color-primary); }
.as-cell.answered { background: var(--color-primary); border-color: var(--color-primary); color: var(--color-text-inverse); }
.as-cell.current { box-shadow: 0 0 0 2px var(--color-primary-border); }

.as-legend {
  display: flex; flex-wrap: wrap; gap: var(--space-3);
  margin-top: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-border-light);
}
.as-legend-item { display: inline-flex; align-items: center; gap: 4px; font-size: var(--fs-2xs); color: var(--color-text-muted); }
.as-dot { width: 9px; height: 9px; border-radius: 3px; display: inline-block; }
.as-dot.is-answered { background: var(--color-primary); }
.as-dot.is-blank { background: var(--color-surface); border: 1px solid var(--color-border); }
.as-dot.is-current { background: var(--color-surface); box-shadow: 0 0 0 2px var(--color-primary-border); }

.as-tips { margin-top: var(--space-2); font-size: var(--fs-2xs); color: var(--color-text-muted); line-height: var(--lh-snug); }
.as-tips b { color: var(--color-text-secondary); }

/* 文本输入 */
.exam-text-input { margin-top: var(--space-2); }
.exam-text-input textarea,
.exam-text-input input[type="text"] {
  width: 100%;
  padding: 10px 12px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-family: var(--font-sans);
  font-size: var(--fs-sm);
  line-height: var(--lh-normal);
  color: var(--color-text-primary);
  outline: none;
  resize: vertical;
}
.exam-text-input textarea:focus,
.exam-text-input input:focus { border-color: var(--color-primary); box-shadow: var(--ring-primary); }
.exam-text-input textarea:disabled,
.exam-text-input input:disabled { background: var(--color-bg); color: var(--color-text-primary); }

/* 结果反馈 */
.result-feedback {
  margin-top: var(--space-2);
  padding: 10px 12px;
  background: var(--color-bg);
  border-radius: var(--radius-sm);
  font-family: var(--font-sans);
  font-size: var(--fs-sm);
  line-height: var(--lh-relaxed);
}
.rf-user { color: var(--color-error-text); }
.rf-note { margin-left: 6px; font-size: var(--fs-2xs); color: var(--color-text-muted); }
.rf-correct { color: var(--color-success-text); font-weight: var(--fw-semibold); }
/* 主观题得分：部分分用警示色，待评阅用信息色（都不是"答对/答错"） */
.rf-score {
  margin-top: 4px;
  font-weight: var(--fw-semibold);
  color: var(--color-warning-text);
}
.rf-score.is-pending { color: var(--color-info-text); font-weight: var(--fw-normal); }
.rf-ai {
  margin-top: 4px;
  padding: var(--space-2) var(--space-3);
  border-left: 3px solid var(--color-primary-border);
  background: var(--color-primary-bg);
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
}
.rf-analysis { color: var(--color-text-secondary); margin-top: 4px; }
.rf-tags { margin-top: 6px; display: flex; gap: 6px; flex-wrap: wrap; }
.rf-tag {
  font-size: var(--fs-2xs);
  padding: 1px 8px;
  background: var(--color-neutral-bg);
  border-radius: var(--radius-sm);
  color: var(--color-neutral-text);
}

/* 提交栏：答题时吸底常驻 */
.submit-bar {
  position: sticky;
  bottom: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-top: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
  box-shadow: var(--shadow-up);
  font-family: var(--font-sans);
}
.submit-bar.sb-center {
  justify-content: center;
  flex-wrap: wrap;
  padding: var(--space-5) 0 var(--space-10);
  border-top: none;
}
.sb-info { font-size: var(--fs-xs); color: var(--color-text-secondary); }
.sb-warn { color: var(--color-error-text); font-weight: var(--fw-semibold); }
.sb-ok { color: var(--color-success-text); font-weight: var(--fw-semibold); }
.sb-mode { color: var(--color-warning-text); font-weight: var(--fw-semibold); }
.sb-btns { display: flex; align-items: center; gap: var(--space-2); flex-shrink: 0; }
.sb-timer { font-size: var(--fs-xs); font-weight: var(--fw-bold); color: var(--color-primary-dark); font-variant-numeric: tabular-nums; }
.sb-timer.warn { color: var(--color-error-text); }

/* 仅窄屏 / 仅宽屏显示 */
.only-narrow { display: none; }

/* 窄屏：答题卡置顶，工具栏吸顶（让开全局 header 高度） */
@media (max-width: 900px) {
  .exam-layout.with-palette { grid-template-columns: 1fr; }
  .answer-sheet { position: static; order: -1; }
  .as-grid { grid-template-columns: repeat(8, 1fr); }
}

@media (max-width: 720px) {
  .toolbar {
    position: sticky;
    top: var(--paper-sticky-top, 0px);
    z-index: 40;
    margin: 0 calc(-1 * var(--space-3)) var(--space-3);
    padding: var(--space-3);
    background: var(--color-bg);
    border-bottom: 1px solid var(--color-border);
  }
  .only-wide { display: none; }
  .only-narrow { display: inline-flex; }

  /* 纸张内边距在窄屏收窄，避免正文可用宽度被吃掉 */
  .paper-body { padding: var(--space-5) var(--space-4); border-radius: var(--radius-lg); }
  .paper-body.is-exam { padding: var(--space-4) var(--space-3); }

  .submit-bar { margin-left: calc(-1 * var(--space-3)); margin-right: calc(-1 * var(--space-3)); padding: var(--space-3); }
  .sb-btns { flex-wrap: wrap; justify-content: flex-end; }
  .result-actions { padding: var(--space-3); }
  .an-kp-name { max-width: 130px; }
  .pi-label { width: 68px; }
  .pi-row { gap: var(--space-2); }
}

/* ===== PDF 导出专用渲染 =====
   刻意使用「静态流式块」：与改造前的 #paper-content 定位条件完全一致，
   那是本工程里唯一被验证过确实能出 PDF 的形态。
   两条禁令（都是踩过的坑）：
   1. 不要给它加 position: fixed / absolute —— 推到视口外会被漏渲染；
   2. 更不要在它上面盖遮罩 / 加载层 —— html2canvas 会把同一区域内 z-index
      更高的覆盖层一起画进画面，截出来只剩一片底色，表现为导出「全空白」。
   需要表达「正在导出」时，用工具栏按钮自身的 loading 状态即可。 */
.paper-export {
  max-width: 760px;
  margin: 0 auto;
  padding: 24px 28px;
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  color: var(--color-text-primary);
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  line-height: var(--lh-loose);
}

/* 导出期间取消工具栏吸顶，避免它在窄屏下浮到截图区域里 */
.paper-detail-page.is-exporting .toolbar { position: static; }
.pe-title {
  font-size: var(--fs-xl);
  font-weight: var(--fw-bold);
  text-align: center;
  letter-spacing: 2px;
}
.pe-meta {
  margin-top: 4px;
  text-align: center;
  font-size: var(--fs-xs);
  color: var(--color-text-secondary);
}
.pe-compose {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin-top: 12px;
  padding: 8px 12px;
  background: var(--color-primary-bg);
  border-radius: var(--radius-sm);
  font-family: var(--font-sans);
  font-size: var(--fs-xs);
  line-height: var(--lh-normal);
  color: var(--color-text-secondary);
}
.pe-section { margin-top: 22px; }
.pe-section-header {
  margin-bottom: 10px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--color-border);
  font-size: var(--fs-sm);
  font-weight: var(--fw-bold);
  page-break-after: avoid;
  break-after: avoid;
}
.pe-q {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  page-break-inside: avoid;
  break-inside: avoid;
}
.pe-q-num { min-width: 26px; text-align: right; font-weight: var(--fw-bold); }
.pe-q-body { flex: 1; min-width: 0; }
.pe-q-content { white-space: pre-wrap; }
.pe-material {
  margin-top: 6px;
  padding: 8px 10px;
  background: var(--color-bg);
  border-left: 2px solid var(--color-text-muted);
  font-size: var(--fs-xs);
  line-height: var(--lh-relaxed);
  color: var(--color-text-secondary);
}
.pe-material-label { font-weight: var(--fw-bold); }
.pe-material-text { white-space: pre-wrap; }
.pe-options { display: flex; flex-direction: column; gap: 2px; margin-top: 6px; }
.pe-option { padding-left: 16px; font-size: var(--fs-sm); }
.pe-option.is-correct { font-weight: var(--fw-bold); color: var(--color-success-text); }
.pe-answer {
  margin-top: 6px;
  padding: 8px 10px;
  background: var(--color-success-bg);
  border-radius: var(--radius-sm);
  font-family: var(--font-sans);
  font-size: var(--fs-sm);
  line-height: var(--lh-normal);
}
.pe-answer-line { font-weight: var(--fw-semibold); color: var(--color-success-text); }
.pe-analysis { margin-top: 4px; color: var(--color-text-secondary); }
.pe-kps { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.pe-kp {
  padding: 1px 8px;
  border-radius: var(--radius-full);
  background: var(--color-primary-bg);
  color: var(--color-primary-dark);
  font-size: var(--fs-2xs);
}
</style>
