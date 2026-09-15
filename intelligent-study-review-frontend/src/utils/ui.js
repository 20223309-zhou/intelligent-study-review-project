import { reactive } from 'vue'

/**
 * 全局轻量反馈层：替代原生 alert / confirm
 *
 * 设计说明：
 * - 原生 confirm 是同步阻塞的，无法 await、无法自定义样式，与项目已收敛的
 *   CSS 变量设计系统不兼容；这里改成 Promise 化的 confirmDialog。
 * - 全局只保留一个待处理弹窗，重复调用时前一个自动以 false 关闭，
 *   避免弹层堆叠导致 Promise 永久挂起。
 * - 视觉渲染交给 <UiFeedback />（挂载于 App.vue），本模块只负责状态与调度。
 */

let toastSeq = 0

export const uiState = reactive({
  /** 当前确认弹窗：null 或 { title, message, confirmText, cancelText, danger, resolve } */
  confirm: null,
  /** 吐司队列：[{ id, message, type }] */
  toasts: []
})

/**
 * 打开确认弹窗
 * @param {string|object} options 字符串等价于 { message }
 * @returns {Promise<boolean>} 用户点击确定返回 true，取消/关闭返回 false
 */
export function confirmDialog(options = {}) {
  const opts = typeof options === 'string' ? { message: options } : options
  return new Promise(resolve => {
    if (uiState.confirm) {
      const prev = uiState.confirm
      uiState.confirm = null
      prev.resolve(false)
    }
    uiState.confirm = {
      title: opts.title || '操作确认',
      message: opts.message || '',
      confirmText: opts.confirmText || '确定',
      cancelText: opts.cancelText || '取消',
      danger: !!opts.danger,
      resolve
    }
  })
}

/** 由 <UiFeedback /> 调用，关闭当前弹窗并回传结果 */
export function resolveConfirm(result) {
  const cur = uiState.confirm
  uiState.confirm = null
  if (cur) cur.resolve(!!result)
}

/**
 * 轻提示
 * @param {string} message
 * @param {'info'|'success'|'warn'|'error'} type
 * @param {number} duration 毫秒
 */
export function toast(message, type = 'info', duration = 2600) {
  const id = ++toastSeq
  uiState.toasts.push({ id, message: String(message), type })
  if (uiState.toasts.length > 4) uiState.toasts.splice(0, uiState.toasts.length - 4)
  setTimeout(() => {
    const i = uiState.toasts.findIndex(t => t.id === id)
    if (i >= 0) uiState.toasts.splice(i, 1)
  }, duration)
  return id
}

export const toastInfo = (m, d) => toast(m, 'info', d)
export const toastSuccess = (m, d) => toast(m, 'success', d)
export const toastWarn = (m, d) => toast(m, 'warn', d)
export const toastError = (m, d) => toast(m, 'error', d)
