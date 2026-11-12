/**
 * KaTeX 由 CDN 引入（defer），组件挂载时不一定已就绪。
 * 这里统一等待：就绪后回调一次；没就绪就一直轮询（间隔逐步放大到 1s）。
 *
 * 不能「重试 N 次就放弃」—— 弱网下 KaTeX 晚到 10 秒也是常态，
 * 放弃意味着整页公式永久退回 LaTeX 原文。轮询本身很便宜（只有一个定时器）。
 */
let waiters = []
let timer = null

function flush() {
  const list = waiters
  waiters = []
  list.forEach(fn => {
    try { fn() } catch (e) { console.warn('[katex] 渲染回调失败', e) }
  })
}

export function whenKatexReady(cb) {
  if (window.renderMathInElement) { cb(); return }
  waiters.push(cb)
  if (timer) return

  let delay = 60
  const tick = () => {
    if (window.renderMathInElement) {
      timer = null
      flush()
      return
    }
    delay = Math.min(delay * 1.6, 1000)
    timer = setTimeout(tick, delay)
  }
  timer = setTimeout(tick, delay)
}
