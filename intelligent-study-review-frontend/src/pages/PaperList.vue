<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { confirmDialog, toastError } from '@/utils/ui.js'
import UiButton from '@/components/ui/UiButton.vue'
import UiSpinner from '@/components/ui/UiSpinner.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'

const API_BASE = '/api'
const router = useRouter()
const paperList = ref([])
const loading = ref(false)
const deletingId = ref(null)
const pageInfo = ref({ current: 1, size: 10, total: 0 })

async function fetchPapers(page = 1) {
  loading.value = true
  try {
    const res = await fetch(`${API_BASE}/exam-paper/listPapers?pageNum=${page}&pageSize=10`).then(r => r.json())
    if (res.code === 0) {
      paperList.value = res.data.records || []
      pageInfo.value = { current: res.data.current, size: res.data.size, total: res.data.total }
    }
  } catch (e) {
    console.error('获取试卷列表失败', e)
  } finally {
    loading.value = false
  }
}

function goDetail(id) {
  router.push(`/paper/${id}`)
}

async function handleDelete(e, id) {
  e.stopPropagation()
  const ok = await confirmDialog({
    title: '删除试卷',
    message: '确定要删除这份试卷吗？删除后不可恢复。',
    confirmText: '删除',
    danger: true
  })
  if (!ok) return

  deletingId.value = id
  try {
    const res = await fetch(`${API_BASE}/exam-paper/delete/${id}`, { method: 'POST' }).then(r => r.json())
    if (res.code === 0) {
      paperList.value = paperList.value.filter(p => p.id !== id)
    } else {
      toastError(res.message || '删除失败')
    }
  } catch (err) {
    toastError('网络错误，删除失败')
  } finally {
    deletingId.value = null
  }
}

function formatTime(timeStr) {
  if (!timeStr) return ''
  return timeStr.substring(0, 16).replace('T', ' ')
}

onMounted(() => fetchPapers())
</script>

<template>
  <div class="paper-list-page">
    <div class="page-header">
      <h1 class="page-title">我的试卷</h1>
      <UiButton to="/" variant="primary" size="sm">
        <template #icon>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        </template>
        新建试卷
      </UiButton>
    </div>

    <div v-if="loading" class="loading-state">
      <UiSpinner :size="20" />
      <span>加载中...</span>
    </div>

    <UiEmpty v-else-if="paperList.length === 0" text="还没有试卷，去生成一份吧" description="填写学科与题型配置，AI 会自动为你生成一份完整试卷">
      <template #action>
        <UiButton to="/" variant="primary" size="sm">开始生成</UiButton>
      </template>
    </UiEmpty>

    <div v-else class="paper-grid">
      <div v-for="paper in paperList" :key="paper.id" class="paper-card">
        <div class="paper-card-body" @click="goDetail(paper.id)">
          <div class="paper-card-top">
            <h3 class="paper-name">{{ paper.paperName }}</h3>
            <div class="paper-meta">
              <span class="meta-item">{{ paper.totalScore }} 分</span>
              <span class="meta-divider">·</span>
              <span class="meta-item">{{ paper.durationMinutes }} 分钟</span>
              <span class="meta-divider">·</span>
              <span class="meta-item">{{ paper.questions?.length || 0 }} 题</span>
            </div>
          </div>
          <div class="paper-card-bottom">
            <span class="paper-time">{{ formatTime(paper.createTime) }}</span>
            <span class="paper-view-btn">查看详情 →</span>
          </div>
        </div>
        <button
          class="btn-delete"
          :disabled="deletingId === paper.id"
          @click="handleDelete($event, paper.id)"
          title="删除试卷"
        >
          <UiSpinner v-if="deletingId === paper.id" :size="14" tone="current" label="删除中" />
          <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="3 6 5 6 21 6"/>
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
          </svg>
        </button>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="pageInfo.total > pageInfo.size" class="pagination">
      <button class="page-btn" :disabled="pageInfo.current <= 1" @click="fetchPapers(pageInfo.current - 1)">上一页</button>
      <span class="page-info">{{ pageInfo.current }} / {{ Math.ceil(pageInfo.total / pageInfo.size) }}</span>
      <button class="page-btn" :disabled="pageInfo.current >= Math.ceil(pageInfo.total / pageInfo.size)" @click="fetchPapers(pageInfo.current + 1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
/* 容器宽度由 layout 统一控制，此处不再自设 max-width */
.paper-list-page {
  width: 100%;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.page-title {
  font-size: var(--fs-2xl);
  font-weight: var(--fw-heavy);
  color: var(--color-text-primary);
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: 60px 20px;
  color: var(--color-text-muted);
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
  font-size: var(--fs-sm);
}

/* 卡片网格：宽屏自动多列，避免行宽被拉满后内容稀疏 */
.paper-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
  gap: var(--space-4);
}

.paper-card {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  border: 1px solid transparent;
  display: flex;
  align-items: stretch;
  overflow: hidden;
  transition: all var(--transition-fast);
}

.paper-card:hover {
  border-color: var(--color-primary-light);
  box-shadow: var(--shadow-md);
}

.paper-card-body {
  flex: 1;
  min-width: 0;
  padding: var(--space-5);
  cursor: pointer;
}

.paper-card-top { margin-bottom: var(--space-3); }

.paper-name {
  font-size: var(--fs-lg);
  font-weight: var(--fw-bold);
  color: var(--color-text-primary);
  margin-bottom: var(--space-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.paper-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}

.meta-divider { color: var(--color-border); }

.paper-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-border-light);
}

.paper-time {
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.paper-view-btn {
  font-size: var(--fs-xs);
  font-weight: var(--fw-semibold);
  color: var(--color-primary);
  white-space: nowrap;
}

.btn-delete {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  border: none;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: all var(--transition-fast);
  flex-shrink: 0;
}

.btn-delete:hover:not(:disabled) {
  color: var(--color-error);
  background: var(--color-error-bg);
}

.btn-delete:disabled {
  cursor: wait;
  color: var(--color-text-muted);
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  margin-top: var(--space-6);
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

@media (max-width: 640px) {
  .paper-grid {
    grid-template-columns: 1fr;
  }

  .btn-delete {
    width: 44px;
  }

  .paper-card-body {
    padding: var(--space-4);
  }
}
</style>
