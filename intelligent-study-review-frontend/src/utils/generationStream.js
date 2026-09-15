/**
 * 试卷生成进度流（SSE）。
 *
 * 为什么用 EventSource 而不是普通 fetch：生成要跑几十秒到几分钟（最多 3 轮多 Agent 循环），
 * 同步请求会被网关/浏览器掐断；SSE 是服务端单向推送，天生就是进度通道。
 *
 * 注意：EventSource 只支持 GET，所以流程是
 * 「先 POST /exam-paper/add 落库拿到 paperId → 再订阅 /exam-paper/generate-stream/{paperId}」。
 *
 * 用法：
 *   const close = createGenerationStream(paperId, {
 *     stage: d => {}, review: d => {}, done: d => {}, error: d => {}
 *   })
 *   // 组件卸载 / 流程结束时调用 close()
 *
 * @param {string|number} paperId 试卷ID
 * @param {object} handlers 事件回调：stage / review / token / done / error
 * @returns {() => void} 关闭连接
 */
export function createGenerationStream(paperId, handlers = {}) {
  const es = new EventSource(`/api/exam-paper/generate-stream/${paperId}`)
  let closed = false
  let receivedAny = false

  const close = () => {
    if (closed) return
    closed = true
    es.close()
  }

  const bind = (name) => (e) => {
    receivedAny = true
    try {
      handlers[name]?.(JSON.parse(e.data))
    } catch (err) {
      // 单条事件解析失败不应该影响后续事件
      console.error('[generation-stream] 事件解析失败：', name, err)
    }
  }

  // ping 是心跳，前端无需展示，但监听它也算"连接是活的"
  ;['stage', 'review', 'token', 'done', 'ping'].forEach(n => es.addEventListener(n, bind(n)))

  es.addEventListener('error', () => {
    if (closed) return
    // 收完 done 之后我们会主动 close，所以走到这里都是真的异常
    if (!receivedAny) {
      handlers.error?.({ message: '无法建立生成进度连接，请确认后端已重启并包含流式接口' })
    } else {
      handlers.error?.({ message: '进度连接已中断，生成可能仍在后台继续，稍后可到试卷列表查看' })
    }
    // 主动关闭，避免 EventSource 自动重连造成重复生成
    close()
  })

  return close
}
