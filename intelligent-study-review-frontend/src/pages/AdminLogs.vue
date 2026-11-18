<script setup>
import { ref, computed, onMounted } from 'vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiSpinner from '@/components/ui/UiSpinner.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import PageDecor from '@/components/PageDecor.vue'
import { toastError } from '@/utils/ui.js'

const API_BASE = '/api'

const loading = ref(false)
const statsLoading = ref(false)
const logs = ref([])
const stats = ref(null)
const pageInfo = ref({ current: 1, size: 10, total: 0 })
const days = ref(7)
const expandedId = ref(null)

const filters = ref({ status: '', keyword: '', startTime: '', endTime: '' })

const hasFilter = computed(() =>
  !!(filters.value.status || filters.value.keyword || filters.value.startTime || filters.value.endTime))

/** 趋势图里最高的那根柱子，用来算百分比高度 */
const maxDailyTokens = computed(() => {
  const list = stats.value?.daily || []
  return Math.max(1, ...list.map(d => d.tokens || 0))
})

const hasDailyData = computed(() =>
  (stats.value?.daily || []).some(d => d.count > 0))

/**
 * 横轴标签间隔：30 天时每根柱子都标日期会糊成一团。
 * 7 天全标、14 天隔一个、30 天隔四个 —— 只影响标签，柱子始终全量绘制。
 */
const labelStep = computed(() => {
  const n = stats.value?.daily?.length || 0
  if (n > 20) return 5
  if (n > 10) return 2
  return 1
})

onMounted(() => {
  loadStats()
  loadLogs(1)
})

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await fetch(`${API_BASE}/generation-log/stats?days=${days.value}`).then(r => r.json())
    if (res.code === 0) {
      stats.value = res.data
    } else {
      toastError(res.message || '统计数据加载失败')
    }
  } catch {
    toastError('网络错误，统计数据加载失败')
  } finally {
    statsLoading.value = false
  }
}

async function loadLogs(page = 1) {
  loading.value = true
  try {
    const query = new URLSearchParams({ pageNum: page, pageSize: pageInfo.value.size })
    if (filters.value.status) query.set('status', filters.value.status)
    if (filters.value.keyword) query.set('keyword', filters.value.keyword.trim())
    if (filters.value.startTime) query.set('startTime', filters.value.startTime)
    if (filters.value.endTime) query.set('endTime', filters.value.endTime)

    const res = await fetch(`${API_BASE}/generation-log/page?${query}`).then(r => r.json())
    if (res.code === 0) {
      logs.value = res.data.records || []
      pageInfo.value = {
        current: Number(res.data.current) || 1,
        size: Number(res.data.size) || 10,
        total: Number(res.data.total) || 0
      }
      expandedId.value = null
    } else {
      toastError(res.message || '日志加载失败')
    }
  } catch {
    toastError('网络错误，日志加载失败')
  } finally {
    loading.value = false
  }
}

function switchDays(d) {
  if (days.value === d) return
  days.value = d
  loadStats()
}

function search() {
  loadLogs(1)
}

function reset() {
  filters.value = { status: '', keyword: '', startTime: '', endTime: '' }
  loadLogs(1)
}

function toggleRow(id) {
  expandedId.value = expandedId.value === id ? null : id
}

const totalPages = computed(() => Math.max(1, Math.ceil(pageInfo.value.total / pageInfo.value.size)))

// ===== 展示用的格式化 =====
function fmtNum(n) {
  return Number(n || 0).toLocaleString('zh-CN')
}

/** token 数：0 一律显示「—」——可能真的是 0，也可能是网关没返回，不能让 0 冒充真实数据 */
function fmtTokens(n, source) {
  if (!n) return source === 'none' ? '—' : '0'
  return fmtNum(n)
}

function fmtK(n) {
  const v = Number(n || 0)
  if (v >= 1000) return `${(v / 1000).toFixed(1)}k`
  return String(v)
}

function fmtDuration(ms) {
  const v = Number(ms || 0)
  if (!v) return '—'
  if (v < 60000) return `${(v / 1000).toFixed(1)}s`
  const m = Math.floor(v / 60000)
  const s = Math.round((v % 60000) / 1000)
  return `${m}m${s}s`
}

function fmtTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 19)
}

function barHeight(tokens) {
  const pct = Math.round((Number(tokens || 0) / maxDailyTokens.value) * 100)
  return `${Math.max(pct, 2)}%`
}

function sourceHint(source) {
  if (source === 'gateway') return '用量由模型网关返回'
  if (source === 'estimated') return '用量为本地估算'
  return '网关未返回用量数据'
}
</script>

<template>
  <div class="admin-logs">
    <PageDecor />

    <div class="page-header">
      <div>
        <h1 class="page-title">生成日志</h1>
        <p class="page-desc">每次试卷生成的耗时、轮次与 token 消耗。仅管理员可见。</p>
      </div>
      <UiButton size="sm" :loading="loading || statsLoading" @click="loadStats(); loadLogs(pageInfo.current)">
        刷新
      </UiButton>
    </div>

    <!-- ===== 概览卡片 ===== -->
    <div class="stat-grid">
      <div class="stat-card">
        <span class="stat-label">生成次数</span>
        <span class="stat-value">{{ statsLoading ? '···' : fmtNum(stats?.totalCount) }}</span>
        <span class="stat-sub">
          成功 {{ fmtNum(stats?.successCount) }} · 失败 {{ fmtNum(stats?.failCount) }}
        </span>
      </div>
      <div class="stat-card">
        <span class="stat-label">成功率</span>
        <span class="stat-value" :class="{ 'is-warn': stats && stats.successRate < 90 }">
          {{ statsLoading ? '···' : (stats?.successRate ?? 0) }}<i>%</i>
        </span>
        <span class="stat-sub">近 {{ stats?.days ?? days }} 天</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">总 token</span>
        <span class="stat-value">{{ statsLoading ? '···' : fmtNum(stats?.totalTokens) }}</span>
        <span class="stat-sub">单次均值 {{ fmtNum(stats?.avgTokens) }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">平均耗时</span>
        <span class="stat-value">{{ statsLoading ? '···' : fmtDuration(stats?.avgDurationMs) }}</span>
        <span class="stat-sub">平均 {{ stats?.avgRounds ?? 0 }} 轮生成-审查</span>
      </div>
    </div>

    <!-- ===== 趋势 ===== -->
    <div class="chart-card">
      <div class="chart-head">
        <span class="chart-title">每日消耗趋势</span>
        <div class="chart-tabs">
          <button v-for="d in [7, 14, 30]" :key="d" class="chart-tab" :class="{ active: days === d }"
            @click="switchDays(d)">近 {{ d }} 天</button>
        </div>
      </div>

      <div v-if="hasDailyData" class="chart">
        <div v-for="(d, i) in stats.daily" :key="d.date" class="chart-col"
          :title="`${d.date} · ${d.count} 次 · ${fmtNum(d.tokens)} tokens`">
          <div class="chart-bar-area">
            <div class="chart-bar" :class="{ 'is-empty': !d.tokens }" :style="{ height: barHeight(d.tokens) }"></div>
          </div>
          <span v-if="i % labelStep === 0" class="chart-label" :class="{ 'has-fail': d.failCount }"
            :title="d.failCount ? `${d.failCount} 次失败` : ''">
            <i v-if="d.failCount" class="chart-fail"></i>{{ d.date.slice(5) }}
          </span>
        </div>
      </div>
      <div v-else class="chart-empty">这段时间还没有生成记录</div>
    </div>

    <!-- ===== 筛选 ===== -->
    <div class="filter-bar">
      <select v-model="filters.status" class="filter-input filter-select" @change="search">
        <option value="">全部状态</option>
        <option value="1">成功</option>
        <option value="2">失败</option>
      </select>
      <input v-model="filters.keyword" class="filter-input" placeholder="试卷名关键字" @keyup.enter="search" />
      <input v-model="filters.startTime" class="filter-input" type="date" />
      <span class="filter-sep">至</span>
      <input v-model="filters.endTime" class="filter-input" type="date" />
      <UiButton size="sm" variant="primary" @click="search">查询</UiButton>
      <UiButton v-if="hasFilter" size="sm" @click="reset">重置</UiButton>
    </div>

    <!-- ===== 列表 ===== -->
    <div class="table-card">
      <div v-if="loading" class="table-loading">
        <UiSpinner :size="20" />
        <span>加载中...</span>
      </div>

      <UiEmpty v-else-if="!logs.length" :card="false"
        :text="hasFilter ? '没有符合条件的记录' : '还没有生成记录'"
        :description="hasFilter ? '换个条件试试，或点「重置」' : '生成一份试卷后，这里会出现耗时与 token 明细'" />

      <div v-else class="table-scroll">
        <table class="log-table">
          <thead>
            <tr>
              <th>时间</th>
              <th class="c-user">用户</th>
              <th>试卷</th>
              <th>状态</th>
              <th class="c-rounds">轮次</th>
              <th class="c-score">评分</th>
              <th class="c-token">Token</th>
              <th class="c-duration">耗时</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="log in logs" :key="log.id">
              <tr class="log-row" :class="{ 'is-fail': log.status === 2 }" @click="toggleRow(log.id)">
                <td class="c-time">{{ fmtTime(log.createTime) }}</td>
                <td class="c-user">
                  <span class="user-account">{{ log.userAccount || log.userId || '—' }}</span>
                </td>
                <td class="c-paper">
                  <span class="paper-name" :title="log.paperName">{{ log.paperName || '—' }}</span>
                  <span class="paper-sub">{{ log.subject || '—' }}<template v-if="log.gradeOrLevel"> · {{ log.gradeOrLevel }}</template></span>
                </td>
                <td>
                  <span class="status" :class="log.status === 1 ? 'is-ok' : 'is-bad'">
                    <i></i>{{ log.status === 1 ? '成功' : '失败' }}
                  </span>
                </td>
                <td class="c-rounds num">{{ log.rounds ?? 0 }}</td>
                <td class="c-score num">{{ log.qualityScore ?? '—' }}</td>
                <td class="c-token">
                  <span class="num">{{ fmtTokens(log.totalTokens, log.usageSource) }}</span>
                  <span v-if="log.totalTokens" class="tk-sub">{{ fmtK(log.promptTokens) }} / {{ fmtK(log.completionTokens) }}</span>
                </td>
                <td class="c-duration num">{{ fmtDuration(log.durationMs) }}</td>
              </tr>

              <!-- 展开：失败原因 / 按 Agent 分摊 / 追踪信息 -->
              <tr v-if="expandedId === log.id" class="detail-row">
                <td colspan="8">
                  <div class="detail">
                    <div v-if="log.errorMsg" class="detail-block">
                      <span class="detail-label">失败原因</span>
                      <p class="detail-error">{{ log.errorMsg }}</p>
                    </div>

                    <div v-if="log.byAgent" class="detail-block">
                      <span class="detail-label">Token 分摊</span>
                      <div class="agent-chips">
                        <span v-for="(v, k) in log.byAgent" :key="k" class="agent-chip">
                          {{ k }}<b>{{ fmtNum(v) }}</b>
                        </span>
                      </div>
                    </div>

                    <div class="detail-meta">
                      <span>耗时 <b>{{ fmtDuration(log.durationMs) }}</b></span>
                      <span>模型调用 <b>{{ log.llmCalls ?? 0 }}</b> 次</span>
                      <span>用量来源 <b :title="sourceHint(log.usageSource)">{{ log.usageSource || '—' }}</b></span>
                      <span>模型 <b>{{ log.model || '—' }}</b></span>
                      <span v-if="log.paperId">试卷ID <b>{{ log.paperId }}</b></span>
                      <span v-if="log.traceId" class="trace">trace <b>{{ log.traceId }}</b></span>
                    </div>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="pageInfo.total > pageInfo.size" class="pagination">
      <button class="page-btn" :disabled="pageInfo.current <= 1" @click="loadLogs(pageInfo.current - 1)">上一页</button>
      <span class="page-info">{{ pageInfo.current }} / {{ totalPages }} · 共 {{ pageInfo.total }} 条</span>
      <button class="page-btn" :disabled="pageInfo.current >= totalPages"
        @click="loadLogs(pageInfo.current + 1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
.admin-logs {
  width: 100%;
}

/* ===== 页头 ===== */
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.page-title {
  font-size: var(--fs-2xl);
  font-weight: var(--fw-heavy);
  color: var(--color-text-primary);
}

.page-desc {
  margin-top: 4px;
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}

/* ===== 概览卡片 ===== */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--space-4) var(--space-5);
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.stat-label {
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}

.stat-value {
  font-size: var(--fs-xl);
  font-weight: var(--fw-bold);
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.stat-value i {
  font-style: normal;
  font-size: var(--fs-sm);
  color: var(--color-text-muted);
  margin-left: 2px;
}

.stat-value.is-warn {
  color: var(--color-error-text);
}

.stat-sub {
  font-size: var(--fs-2xs);
  color: var(--color-text-muted);
}

/* ===== 趋势图 ===== */
.chart-card {
  padding: var(--space-4) var(--space-5) var(--space-3);
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  margin-bottom: var(--space-4);
}

.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.chart-title {
  font-size: var(--fs-sm);
  font-weight: var(--fw-bold);
  color: var(--color-text-primary);
}

.chart-tabs {
  display: flex;
  gap: 4px;
}

.chart-tab {
  padding: 4px 10px;
  font-size: var(--fs-2xs);
  color: var(--color-text-muted);
  border-radius: var(--radius-full);
  transition: all var(--transition-fast);
}

.chart-tab:hover {
  color: var(--color-primary);
  background: var(--color-primary-bg);
}

.chart-tab.active {
  color: var(--color-primary-dark);
  background: var(--color-primary-bg);
  font-weight: var(--fw-semibold);
}

.chart {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 140px;
  padding-top: 10px;
}

.chart-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  cursor: default;
}

.chart-bar-area {
  position: relative;
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.chart-bar {
  position: relative;
  width: 70%;
  max-width: 22px;
  border-radius: var(--radius-sm) var(--radius-sm) 2px 2px;
  background: var(--color-primary-light);
  transition: background var(--transition-fast);
}

.chart-col:hover .chart-bar {
  background: var(--color-primary);
}

.chart-bar.is-empty {
  background: var(--color-border-light);
}

.chart-label {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-top: 6px;
  font-size: var(--fs-2xs);
  color: var(--color-text-muted);
  white-space: nowrap;
}

.chart-label.has-fail {
  color: var(--color-error-text);
}

/* 失败标记放在日期前面：挂在柱顶会因为"零消耗那天柱高为 0"而飘在空白处 */
.chart-fail {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--color-error);
  flex-shrink: 0;
}

.chart-empty {
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}

/* ===== 筛选 ===== */
.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.filter-input {
  height: 34px;
  padding: 0 10px;
  font-size: var(--fs-sm);
  color: var(--color-text-primary);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  transition: border-color var(--transition-fast);
}

.filter-input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.filter-select {
  padding-right: 6px;
  cursor: pointer;
}

.filter-sep {
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}

/* ===== 表格 ===== */
.table-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.table-scroll {
  overflow-x: auto;
}

.log-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--fs-sm);
}

.log-table th {
  padding: 10px 12px;
  font-size: var(--fs-2xs);
  font-weight: var(--fw-semibold);
  color: var(--color-text-muted);
  text-align: left;
  white-space: nowrap;
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-border-light);
}

.log-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-border-light);
  vertical-align: middle;
}

.log-row {
  cursor: pointer;
  transition: background var(--transition-fast);
}

.log-row:hover {
  background: var(--color-primary-soft);
}

.log-row.is-fail td {
  background: rgba(0, 0, 0, 0);
}

.c-time {
  white-space: nowrap;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.user-account {
  color: var(--color-text-secondary);
}

.c-paper {
  max-width: 280px;
}

.paper-name {
  display: block;
  color: var(--color-text-primary);
  font-weight: var(--fw-semibold);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.paper-sub {
  display: block;
  margin-top: 2px;
  font-size: var(--fs-2xs);
  color: var(--color-text-muted);
}

.num {
  font-variant-numeric: tabular-nums;
  color: var(--color-text-primary);
  white-space: nowrap;
}

.c-token .tk-sub {
  display: block;
  margin-top: 2px;
  font-size: var(--fs-2xs);
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: var(--fs-2xs);
  font-weight: var(--fw-semibold);
  white-space: nowrap;
}

.status i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.status.is-ok {
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

.status.is-ok i {
  background: var(--color-success);
}

.status.is-bad {
  background: var(--color-error-bg);
  color: var(--color-error-text);
}

.status.is-bad i {
  background: var(--color-error);
}

/* ===== 展开详情 ===== */
.detail-row td {
  padding: 0;
  background: var(--color-bg);
}

.detail {
  padding: var(--space-3) var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.detail-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-label {
  font-size: var(--fs-2xs);
  font-weight: var(--fw-bold);
  color: var(--color-text-muted);
}

.detail-error {
  font-size: var(--fs-xs);
  line-height: var(--lh-relaxed);
  color: var(--color-error-text);
  background: var(--color-error-bg);
  border-left: 2px solid var(--color-error);
  border-radius: var(--radius-sm);
  padding: 6px 10px;
  word-break: break-all;
}

.agent-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.agent-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 9px;
  font-size: var(--fs-2xs);
  color: var(--color-text-secondary);
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-full);
}

.agent-chip b {
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  font-size: var(--fs-2xs);
  color: var(--color-text-muted);
}

.detail-meta b {
  color: var(--color-text-secondary);
  font-weight: var(--fw-semibold);
}

.detail-meta .trace b {
  font-family: inherit;
  letter-spacing: 0;
}

/* ===== 加载 / 分页 ===== */
.table-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: 60px 20px;
  color: var(--color-text-muted);
  font-size: var(--fs-sm);
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  margin-top: var(--space-5);
}

.page-btn {
  padding: 8px 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: var(--fs-sm);
  transition: all var(--transition-fast);
}

.page-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: var(--fs-sm);
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .c-user,
  .c-rounds {
    display: none;
  }
}

@media (max-width: 640px) {
  .c-score,
  .c-duration {
    display: none;
  }

  .c-paper {
    max-width: 160px;
  }

  .chart {
    height: 110px;
  }
}
</style>
