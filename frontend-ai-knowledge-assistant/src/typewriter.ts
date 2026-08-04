/**
 * SSE 增量缓冲 + 打字机逐字吐出（先思考队列，再回答队列）。
 * 效果接近 DeepSeek 深度思考：思考区先逐字展开，再输出正式回答。
 */
export type TypewriterQueues = {
  pushThinking: (chunk: string) => void
  pushAnswer: (chunk: string) => void
  /** SSE 已结束；队列排空后 resolve */
  finish: () => Promise<void>
  /** 立刻清空并停止（切会话/新提问） */
  reset: () => void
  /** 历史回显：瞬间填满，不走打字机 */
  snap: (thinking: string, answer: string) => void
}

export function createTypewriter(options: {
  onThinking: (text: string) => void
  onAnswer: (text: string) => void
  /** 每帧吐出的字符数；越大越快 */
  charsPerTick?: number
  /** 帧间隔 ms */
  intervalMs?: number
  onAnswerStarted?: () => void
}): TypewriterQueues {
  const charsPerTick = options.charsPerTick ?? 1
  const intervalMs = options.intervalMs ?? 22

  let thinkPending = ''
  let answerPending = ''
  let thinkShown = ''
  let answerShown = ''
  let timer: number | null = null
  let sseDone = false
  let answerStarted = false
  let finishResolve: (() => void) | null = null

  function stopTimer() {
    if (timer != null) {
      window.clearInterval(timer)
      timer = null
    }
  }

  function tryResolve() {
    if (sseDone && !thinkPending && !answerPending) {
      stopTimer()
      const resolve = finishResolve
      finishResolve = null
      resolve?.()
    }
  }

  function tick() {
    if (thinkPending) {
      const n = Math.min(charsPerTick, thinkPending.length)
      thinkShown += thinkPending.slice(0, n)
      thinkPending = thinkPending.slice(n)
      options.onThinking(thinkShown)
      return
    }

    if (answerPending) {
      if (!answerStarted) {
        answerStarted = true
        options.onAnswerStarted?.()
      }
      const n = Math.min(charsPerTick, answerPending.length)
      answerShown += answerPending.slice(0, n)
      answerPending = answerPending.slice(n)
      options.onAnswer(answerShown)
      return
    }

    tryResolve()
  }

  function ensureTimer() {
    if (timer != null) return
    timer = window.setInterval(tick, intervalMs)
  }

  return {
    pushThinking(chunk: string) {
      if (!chunk) return
      thinkPending += chunk
      ensureTimer()
    },
    pushAnswer(chunk: string) {
      if (!chunk) return
      answerPending += chunk
      ensureTimer()
    },
    finish() {
      sseDone = true
      if (!thinkPending && !answerPending) {
        stopTimer()
        return Promise.resolve()
      }
      ensureTimer()
      return new Promise<void>((resolve) => {
        finishResolve = resolve
      })
    },
    reset() {
      stopTimer()
      thinkPending = ''
      answerPending = ''
      thinkShown = ''
      answerShown = ''
      sseDone = false
      answerStarted = false
      finishResolve = null
      options.onThinking('')
      options.onAnswer('')
    },
    snap(thinking: string, answer: string) {
      stopTimer()
      thinkPending = ''
      answerPending = ''
      thinkShown = thinking || ''
      answerShown = answer || ''
      sseDone = true
      answerStarted = Boolean(answer)
      finishResolve = null
      options.onThinking(thinkShown)
      options.onAnswer(answerShown)
    },
  }
}
