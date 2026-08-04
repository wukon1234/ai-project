import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import {
  BookOpenText,
  Bookmark,
  Brain,
  Check,
  ChevronDown,
  ChevronRight,
  Copy,
  History,
  Library,
  MessageSquarePlus,
  ScanSearch,
  Search,
  Send,
  Settings,
  Share2,
  Sparkles,
  Star,
  ThumbsDown,
  ThumbsUp,
  Trash2,
  UserCircle2,
} from 'lucide-react'
import AnswerContent from './AnswerContent'
import FeedbackModal from './FeedbackModal'
import type { SourceDoc } from './DocumentReader'
import { createTypewriter, type TypewriterQueues } from './typewriter'
import {
  askStream,
  clearSession,
  createSession,
  feedbackHelpful,
  feedbackRating,
  getPreferences,
  getSession,
  listSessions,
  me,
  patchSessionScope,
  regenerateStream,
  saveFavoriteAnswer,
  shareSession,
  type ChatSession,
  type StreamCitation,
  type StreamDone,
  type StreamMeta,
} from './api'

type KnowledgeScopeId = 'all' | 'product' | 'hr' | 'tech' | 'support'

const knowledgeScopes: Array<{ id: KnowledgeScopeId; label: string }> = [
  { id: 'all', label: '全部知识库' },
  { id: 'product', label: '产品知识库' },
  { id: 'hr', label: '人事制度库' },
  { id: 'tech', label: '技术文档库' },
  { id: 'support', label: '售后 FAQ' },
]

const scopeLabel = (scope?: string) =>
  knowledgeScopes.find((s) => s.id === scope)?.label || scope || '知识库'

type ChatPageProps = {
  onOpenSource: (doc: SourceDoc) => void
  onOpenSearch: () => void
  onOpenBrowse: () => void
  onOpenHistory: () => void
  onOpenProfile: () => void
  initialQuestion?: string
  initialSessionId?: number
}

function ChatPage({
  onOpenSource,
  onOpenSearch,
  onOpenBrowse,
  onOpenHistory,
  onOpenProfile,
  initialQuestion,
  initialSessionId,
}: ChatPageProps) {
  const [sessions, setSessions] = useState<ChatSession[]>([])
  const [sessionId, setSessionId] = useState<number | null>(null)
  const [selectedScope, setSelectedScope] = useState<KnowledgeScopeId>('hr')
  const [scopeOpen, setScopeOpen] = useState(false)
  const [question, setQuestion] = useState(initialQuestion || '')
  const [lastActionHint, setLastActionHint] = useState<string | null>(null)
  const [isSending, setIsSending] = useState(false)
  const [bootError, setBootError] = useState<string | null>(null)
  const [sessionQuery, setSessionQuery] = useState('')
  const [sessionKeyword, setSessionKeyword] = useState('')
  const [userQuestion, setUserQuestion] = useState<string | null>(null)
  const [answerText, setAnswerText] = useState('')
  const [citations, setCitations] = useState<StreamCitation[]>([])
  const [doneInfo, setDoneInfo] = useState<StreamDone | null>(null)
  const [streamPhase, setStreamPhase] = useState<StreamMeta | null>(null)
  const [thinkingText, setThinkingText] = useState('')
  const [thinkOpen, setThinkOpen] = useState(true)
  const [recognizeOpen, setRecognizeOpen] = useState(true)
  const [lastUserMessageId, setLastUserMessageId] = useState<number | null>(null)
  const [assistantMessageId, setAssistantMessageId] = useState<number | null>(null)
  const [feedbackOpen, setFeedbackOpen] = useState(false)
  const [ratingOpen, setRatingOpen] = useState(false)
  const [feedbackBusy, setFeedbackBusy] = useState(false)
  const [userName, setUserName] = useState('加载中…')
  const [userSub, setUserSub] = useState('设置')
  const scopeRef = useRef<HTMLDivElement | null>(null)
  const booted = useRef(false)
  const sessionsReady = useRef(false)
  const typewriterRef = useRef<TypewriterQueues | null>(null)

  const placeholder = useMemo(() => '继续追问…', [])
  const selectedScopeLabel =
    knowledgeScopes.find((scope) => scope.id === selectedScope)?.label ?? '人事制度库'

  function getTypewriter() {
    if (!typewriterRef.current) {
      typewriterRef.current = createTypewriter({
        charsPerTick: 1,
        intervalMs: 20,
        onThinking: (text) => setThinkingText(text),
        onAnswer: (text) => setAnswerText(text),
        onAnswerStarted: () => setThinkOpen(false),
      })
    }
    return typewriterRef.current
  }

  function rebuildThinkingFromCitations(cites: StreamCitation[]) {
    if (!cites.length) return ''
    const lines = cites.map(
      (c) => `识别来源 [${c.index}] 《${c.title}》第 ${c.page} 页`,
    )
    lines.push('结合上述片段核对关键事实，按类别整理要点，并在句末标注引用编号。')
    return lines.join('\n')
  }

  function parseFollowUps(raw: unknown): string[] {
    if (Array.isArray(raw)) {
      return raw.map((x) => String(x || '').trim()).filter(Boolean).slice(0, 3)
    }
    if (typeof raw === 'string' && raw.trim()) {
      try {
        const parsed = JSON.parse(raw) as unknown
        if (Array.isArray(parsed)) {
          return parsed.map((x) => String(x || '').trim()).filter(Boolean).slice(0, 3)
        }
      } catch {
        return raw
          .split(/\n/)
          .map((x) => x.trim())
          .filter(Boolean)
          .slice(0, 3)
      }
    }
    return []
  }

  function friendlyError(err: unknown, fallback: string) {
    const msg = err instanceof Error ? err.message : fallback
    // 后端兜底「系统错误」对空态/创建失败不够友好，改为业务文案
    if (!msg || msg === '系统错误') return fallback
    return msg
  }

  function applySessionDetail(detail: Awaited<ReturnType<typeof getSession>>) {
    const scope = (detail.session.scope?.split(',')[0] || 'hr') as KnowledgeScopeId
    if (knowledgeScopes.some((s) => s.id === scope)) setSelectedScope(scope)

    setStreamPhase(null)
    setRatingOpen(false)
    setFeedbackOpen(false)
    getTypewriter().reset()

    const msgs = detail.messages || []
    const lastUser = [...msgs].reverse().find((m) => m.role === 'user')
    const lastAi = [...msgs].reverse().find((m) => m.role === 'assistant')

    if (lastUser) {
      setUserQuestion(lastUser.content || '')
      setLastUserMessageId(lastUser.id)
    } else {
      setUserQuestion(null)
      setLastUserMessageId(null)
    }

    if (lastAi) {
      const cites = (detail.citations || [])
        .filter((c) => c.messageId === lastAi.id)
        .map((c, idx) => ({
          index: c.citeIndex || idx + 1,
          docId: String(c.docId),
          title: c.title,
          page: c.pageNo,
          knowledgeBase: c.libraryName || '',
          knowledgeBaseId: c.libraryCode || '',
          excerpt: c.excerpt,
        }))
      setCitations(cites)
      const thinking =
        (lastAi.thinkingContent && lastAi.thinkingContent.trim()) ||
        rebuildThinkingFromCitations(cites)
      getTypewriter().snap(thinking, lastAi.content || '')
      setThinkOpen(Boolean(thinking))
      setRecognizeOpen(true)
      setAssistantMessageId(lastAi.id)
      setDoneInfo({
        messageId: lastAi.id,
        elapsedMs: lastAi.elapsedMs || 0,
        status: lastAi.answerStatus || 'OK',
        followUps: parseFollowUps(lastAi.followUps),
      })
    } else {
      setAnswerText('')
      setThinkingText('')
      setAssistantMessageId(null)
      setDoneInfo(null)
      setCitations([])
    }
  }

  async function loadSessionDetail(id: number) {
    const detail = await getSession(id)
    applySessionDetail(detail)
    return detail
  }

  async function refreshSessions(preferId?: number | null, keyword?: string) {
    const list = await listSessions(keyword ?? (sessionKeyword || undefined))
    setSessions(list)
    const target =
      (preferId && list.find((s) => s.id === preferId)) ||
      list.find((s) => s.id === sessionId) ||
      list[0]
    if (target) {
      setSessionId(target.id)
      const scope = (target.scope?.split(',')[0] || 'hr') as KnowledgeScopeId
      if (knowledgeScopes.some((s) => s.id === scope)) setSelectedScope(scope)
    }
    return list
  }

  useEffect(() => {
    let alive = true
    ;(async () => {
      try {
        const auth = await me()
        if (!alive) return
        const u = auth.user
        setUserName(u?.name || '用户')
        const sub = [u?.deptName, u?.empNo ? `工号 ${u.empNo}` : '']
          .filter(Boolean)
          .join(' · ')
        setUserSub(sub || '设置')
      } catch {
        if (!alive) return
        setUserName('用户')
        setUserSub('设置')
      }
    })()
    return () => {
      alive = false
    }
  }, [])

  useEffect(() => {
    if (booted.current) return
    booted.current = true
    ;(async () => {
      try {
        try {
          const prefs = await getPreferences()
          const first = (prefs.defaultKbScopes || [])[0] as KnowledgeScopeId | undefined
          if (first && knowledgeScopes.some((s) => s.id === first)) setSelectedScope(first)
        } catch {
          // 偏好可选
        }

        let list: ChatSession[] = []
        try {
          list = await listSessions()
        } catch (err) {
          // 列表失败时走空态，避免侧栏直接展示「系统错误」
          setBootError(friendlyError(err, ''))
          setSessions([])
          return
        }

        if (initialSessionId && list.find((s) => s.id === initialSessionId)) {
          setSessions(list)
          setSessionId(initialSessionId)
          try {
            await loadSessionDetail(initialSessionId)
          } catch {
            // 详情失败仍保留会话列表
          }
          return
        }

        if (!list.length) {
          try {
            const created = await createSession()
            list = [created]
          } catch {
            // 空列表且自动建会话失败：保持空态，不报「系统错误」
            setSessions([])
            setBootError(null)
            return
          }
        }
        setSessions(list)
        setSessionId(list[0].id)
        const scope = (list[0].scope?.split(',')[0] || 'hr') as KnowledgeScopeId
        if (knowledgeScopes.some((s) => s.id === scope)) setSelectedScope(scope)
      } catch (err) {
        setBootError(friendlyError(err, '会话初始化失败'))
        setSessions([])
      } finally {
        sessionsReady.current = true
      }
    })()
  }, [initialSessionId])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSessionKeyword(sessionQuery.trim())
    }, 250)
    return () => window.clearTimeout(timer)
  }, [sessionQuery])

  useEffect(() => {
    if (!sessionsReady.current) return
    let alive = true
    ;(async () => {
      try {
        const list = await listSessions(sessionKeyword || undefined)
        if (!alive) return
        setSessions(list)
        setBootError(null)
      } catch (err) {
        if (!alive) return
        setSessions([])
        setBootError(friendlyError(err, sessionKeyword ? '未找到匹配会话' : ''))
      }
    })()
    return () => {
      alive = false
    }
  }, [sessionKeyword])

  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (!scopeRef.current?.contains(e.target as Node)) setScopeOpen(false)
    }
    document.addEventListener('mousedown', onDocClick)
    return () => document.removeEventListener('mousedown', onDocClick)
  }, [])

  async function ensureSession(): Promise<number> {
    if (sessionId) return sessionId
    const created = await createSession(selectedScope)
    await refreshSessions(created.id)
    return created.id
  }

  async function runAsk(q: string, sid: number) {
    setIsSending(true)
    setLastActionHint(null)
    setUserQuestion(q)
    setCitations([])
    setDoneInfo(null)
    setStreamPhase({ status: 'SEARCHING', phase: 'search', message: '正在理解问题并检索知识库…' })
    setThinkOpen(true)
    setRecognizeOpen(true)
    setLastUserMessageId(null)
    setAssistantMessageId(null)
    setRatingOpen(false)

    const tw = getTypewriter()
    tw.reset()
    const cites: StreamCitation[] = []
    const hold: { done: StreamDone | null; error: string | null } = { done: null, error: null }

    await askStream(sid, q, {
      onMeta: (meta) => {
        if (meta.messageId) setLastUserMessageId(Number(meta.messageId))
        setStreamPhase(meta)
      },
      onCitation: (c) => {
        cites.push(c)
        setCitations([...cites])
      },
      onThinking: (t) => {
        tw.pushThinking(t.content || '')
      },
      onDelta: (d) => {
        tw.pushAnswer(d.content || '')
      },
      onDone: (done) => {
        hold.done = done
        setStreamPhase(null)
      },
      onError: (err) => {
        hold.error = err.message || '问答失败'
        setStreamPhase(null)
      },
    })

    await tw.finish()

    if (hold.error) {
      setLastActionHint(hold.error)
      setDoneInfo({ elapsedMs: 0, status: 'ERROR' })
    } else if (hold.done) {
      setDoneInfo(hold.done)
      if (hold.done.messageId != null) setAssistantMessageId(Number(hold.done.messageId))
      setLastActionHint(
        hold.done.status === 'NO_ANSWER'
          ? '未找到相关知识'
          : `回答完成 · ${hold.done.elapsedMs ?? 0}ms`,
      )
    }

    setIsSending(false)
    try {
      await refreshSessions(sid)
    } catch {
      // ignore refresh errors
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    const q = question.trim()
    if (!q || isSending) return
    try {
      const sid = await ensureSession()
      setQuestion('')
      await runAsk(q, sid)
    } catch (err) {
      setIsSending(false)
      setLastActionHint(err instanceof Error ? err.message : '发送失败')
    }
  }

  async function onNewChat() {
    try {
      const created = await createSession(selectedScope === 'all' ? undefined : selectedScope)
      getTypewriter().reset()
      setUserQuestion(null)
      setAnswerText('')
      setCitations([])
      setDoneInfo(null)
      setStreamPhase(null)
      setThinkingText('')
      setAssistantMessageId(null)
      setLastUserMessageId(null)
      setSessionQuery('')
      setSessionKeyword('')
      setBootError(null)
      await refreshSessions(created.id, '')
      const scope = (created.scope?.split(',')[0] || selectedScope) as KnowledgeScopeId
      if (knowledgeScopes.some((s) => s.id === scope)) setSelectedScope(scope)
      setLastActionHint('已创建新对话')
    } catch (err) {
      setLastActionHint(friendlyError(err, '创建失败，请稍后重试'))
    }
  }

  async function onSelectSession(id: number) {
    if (id === sessionId && (userQuestion || answerText)) {
      setLastActionHint('已在当前会话')
      return
    }
    if (isSending) {
      setLastActionHint('请等待当前回答完成后再切换')
      return
    }
    setSessionId(id)
    try {
      await loadSessionDetail(id)
      setLastActionHint('已切换会话')
    } catch (err) {
      setLastActionHint(friendlyError(err, '加载会话失败，请稍后重试'))
    }
  }

  async function onChangeScope(scope: KnowledgeScopeId) {
    setSelectedScope(scope)
    setScopeOpen(false)
    if (!sessionId) return
    try {
      await patchSessionScope(sessionId, scope)
      await refreshSessions(sessionId)
      setLastActionHint(`已切换到「${scopeLabel(scope)}」`)
    } catch (err) {
      setLastActionHint(err instanceof Error ? err.message : '切换范围失败')
    }
  }

  async function onClear() {
    if (!sessionId) {
      setLastActionHint('请先创建或选择一个会话')
      return
    }
    try {
      await clearSession(sessionId)
      getTypewriter().reset()
      setUserQuestion(null)
      setAnswerText('')
      setCitations([])
      setDoneInfo(null)
      setStreamPhase(null)
      setThinkingText('')
      setAssistantMessageId(null)
      setLastUserMessageId(null)
      await refreshSessions(sessionId)
      setLastActionHint('已清空当前对话')
    } catch (err) {
      setLastActionHint(friendlyError(err, '清空失败，请稍后重试'))
    }
  }

  async function onShare() {
    if (!sessionId) {
      setLastActionHint('请先创建或选择一个会话')
      return
    }
    try {
      const data = await shareSession(sessionId)
      const url = typeof data === 'string' ? data : data.shareUrl || data.shareToken || ''
      if (url && navigator.clipboard?.writeText) {
        try {
          await navigator.clipboard.writeText(url)
          setLastActionHint('分享链接已复制')
        } catch {
          setLastActionHint(url)
        }
      } else {
        setLastActionHint(url || '已生成分享链接')
      }
    } catch (err) {
      setLastActionHint(friendlyError(err, '分享失败，请稍后重试'))
    }
  }

  async function onRegenerate() {
    if (!lastUserMessageId || isSending) return
    setIsSending(true)
    setCitations([])
    setDoneInfo(null)
    setStreamPhase({ status: 'SEARCHING', phase: 'search', message: '正在重新检索并生成回答…' })
    setThinkOpen(true)
    setRecognizeOpen(true)
    setAssistantMessageId(null)
    setRatingOpen(false)

    const tw = getTypewriter()
    tw.reset()
    const cites: StreamCitation[] = []
    const hold: { done: StreamDone | null; error: string | null } = { done: null, error: null }

    await regenerateStream(lastUserMessageId, {
      onMeta: (meta) => {
        if (meta.messageId) setLastUserMessageId(Number(meta.messageId))
        setStreamPhase(meta)
      },
      onCitation: (c) => {
        cites.push(c)
        setCitations([...cites])
      },
      onThinking: (t) => {
        tw.pushThinking(t.content || '')
      },
      onDelta: (d) => {
        tw.pushAnswer(d.content || '')
      },
      onDone: (done) => {
        hold.done = done
        setStreamPhase(null)
      },
      onError: (err) => {
        hold.error = err.message || '重新回答失败'
        setStreamPhase(null)
      },
    })

    await tw.finish()

    if (hold.error) {
      setLastActionHint(hold.error)
    } else if (hold.done) {
      setDoneInfo(hold.done)
      if (hold.done.messageId != null) setAssistantMessageId(Number(hold.done.messageId))
      setLastActionHint(`已重新回答 · ${hold.done.elapsedMs ?? 0}ms`)
    }
    setIsSending(false)
  }

  async function onFollowUp(prompt: string) {
    const q = prompt.trim()
    if (!q || isSending) return
    try {
      const sid = await ensureSession()
      setQuestion('')
      await runAsk(q, sid)
    } catch (err) {
      setIsSending(false)
      setLastActionHint(err instanceof Error ? err.message : '发送失败')
    }
  }

  function openCitation(source: StreamCitation) {
    onOpenSource({
      id: String(source.docId),
      title: source.title,
      page: source.page,
      knowledgeBase: source.knowledgeBase,
      excerpt: source.excerpt,
    })
  }

  async function onHelpful() {
    if (!assistantMessageId || feedbackBusy) return
    setFeedbackBusy(true)
    try {
      const message = await feedbackHelpful(assistantMessageId)
      setLastActionHint(message)
    } catch (err) {
      setLastActionHint(err instanceof Error ? err.message : '反馈失败')
    } finally {
      setFeedbackBusy(false)
    }
  }

  async function onRate(score: number) {
    if (!assistantMessageId || feedbackBusy) return
    setFeedbackBusy(true)
    try {
      const message = await feedbackRating(assistantMessageId, score)
      setLastActionHint(`${message} · ${score} 分`)
      setRatingOpen(false)
    } catch (err) {
      setLastActionHint(err instanceof Error ? err.message : '评分失败')
    } finally {
      setFeedbackBusy(false)
    }
  }

  async function onFavoriteAnswer() {
    if (!assistantMessageId || feedbackBusy) return
    setFeedbackBusy(true)
    try {
      await saveFavoriteAnswer({
        messageId: assistantMessageId,
        summary: answerText.slice(0, 200),
        topic: userQuestion?.slice(0, 40) || '收藏回答',
      })
      setLastActionHint('回答已收藏')
    } catch (err) {
      setLastActionHint(err instanceof Error ? err.message : '收藏失败')
    } finally {
      setFeedbackBusy(false)
    }
  }

  const showEmptyAnswer = doneInfo?.status === 'NO_ANSWER'
  const hasAnswer = Boolean(userQuestion)

  return (
    <div className="qaPage">
      <aside className="qaSidebar">
        <div className="qaBrand">智识云</div>

        <button className="qaPrimaryGhost" type="button" onClick={onNewChat}>
          <MessageSquarePlus size={18} />
          <span>+ 新对话</span>
        </button>

        <button className="qaPrimaryGhost qaNavSearchBtn" type="button" onClick={onOpenSearch}>
          <Search size={18} />
          <span>知识搜索</span>
        </button>

        <button className="qaPrimaryGhost qaNavSearchBtn" type="button" onClick={onOpenBrowse}>
          <Library size={18} />
          <span>探索知识库</span>
        </button>

        <button className="qaPrimaryGhost qaNavSearchBtn" type="button" onClick={onOpenHistory}>
          <History size={18} />
          <span>我的对话</span>
        </button>

        <div className="qaHistorySearch">
          <Search size={16} />
          <input
            value={sessionQuery}
            onChange={(e) => setSessionQuery(e.target.value)}
            placeholder="搜索历史对话…"
            aria-label="搜索历史对话"
          />
        </div>

        <div className="qaHistoryGroups">
          <section className="qaHistoryGroup">
            <div className="qaHistoryTitle">会话</div>
            <div className="qaHistoryList">
              {sessions.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={`qaHistoryItem ${item.id === sessionId ? 'qaHistoryItemActive' : ''}`}
                  onClick={() => onSelectSession(item.id)}
                >
                  <div className="qaHistoryMain">
                    <div className="qaHistoryText">{item.title || '新对话'}</div>
                    <div className="qaHistoryMeta">
                      {scopeLabel(item.scope?.split(',')[0])}
                      {item.updatedAt ? ` · ${item.updatedAt}` : ''}
                    </div>
                  </div>
                </button>
              ))}
              {!sessions.length ? (
                <div className="qaHistoryMeta" style={{ padding: '8px 12px' }}>
                  {bootError || (sessionKeyword ? '未找到匹配会话' : '暂无会话')}
                </div>
              ) : null}
            </div>
          </section>
        </div>

        <div className="qaSidebarFooter">
          <button className="qaUserCard" type="button" onClick={onOpenProfile}>
            <div className="qaUserMeta">
              <UserCircle2 size={22} />
              <div>
                <div className="qaUserName">{userName}</div>
                <div className="qaUserSub">{userSub}</div>
              </div>
            </div>
            <Settings size={16} />
          </button>
        </div>
      </aside>

      <main className="qaMain">
        <div className="qaContentWrap">
          <header className="qaTopbar">
            <div className="qaScopeWrap" ref={scopeRef}>
              <button
                className={`qaScopeBtn ${scopeOpen ? 'qaScopeBtnOpen' : ''}`}
                type="button"
                aria-haspopup="listbox"
                aria-expanded={scopeOpen}
                onClick={() => setScopeOpen((v) => !v)}
              >
                <span>{selectedScopeLabel}</span>
                <ChevronDown size={16} />
              </button>

              {scopeOpen ? (
                <div className="qaScopeMenu" role="listbox" aria-label="选择知识库范围">
                  {knowledgeScopes.map((scope) => (
                    <button
                      key={scope.id}
                      type="button"
                      role="option"
                      aria-selected={selectedScope === scope.id}
                      className={`qaScopeOption ${selectedScope === scope.id ? 'qaScopeOptionActive' : ''}`}
                      onClick={() => onChangeScope(scope.id)}
                    >
                      <span>{scope.label}</span>
                      {selectedScope === scope.id ? <Check size={16} /> : null}
                    </button>
                  ))}
                </div>
              ) : null}
            </div>

            <div className="qaTopActions">
              <button type="button" className="qaTextBtn" onClick={onShare}>
                <Share2 size={16} />
                <span>分享对话</span>
              </button>
              <button type="button" className="qaTextBtn" onClick={onClear}>
                <Trash2 size={16} />
                <span>清空对话</span>
              </button>
            </div>
          </header>

          <section className="qaConversation">
            {!hasAnswer ? (
              <div className="qaMessage qaMessageAi">
                <div className="qaAnswerColumn">
                  <div className="qaBubble qaBubbleAi">
                    <div className="qaAnswerText">
                      你好，我是智识云助手。选择知识库范围后直接提问即可。
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              <>
                <div className="qaMessage qaMessageUser">
                  <div className="qaBubble qaBubbleUser">{userQuestion}</div>
                </div>

                {!showEmptyAnswer ? (
                  <div className="qaMessage qaMessageAi">
                    <div className="qaAiAvatar" aria-hidden="true">
                      <span className="qaAiOrb" />
                    </div>
                    <div className="qaAnswerColumn">
                      {(isSending || thinkingText || citations.length > 0) && (isSending || thinkingText) ? (
                        <div className="qaProcessStack">
                          <section className="qaProcessPanel">
                            <button
                              type="button"
                              className="qaProcessToggle"
                              onClick={() => setRecognizeOpen((v) => !v)}
                              aria-expanded={recognizeOpen}
                            >
                              {recognizeOpen ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                              <ScanSearch size={16} />
                              <span className="qaProcessLabel">
                                {streamPhase?.phase === 'search' || streamPhase?.status === 'SEARCHING'
                                  ? streamPhase?.message || '正在检索知识库…'
                                  : citations.length
                                    ? `已识别 ${citations.length} 个相关来源`
                                    : streamPhase?.message || '识别过程'}
                              </span>
                              {isSending && (!citations.length || streamPhase?.phase === 'search') ? (
                                <span className="qaProcessPulse" />
                              ) : null}
                            </button>
                            {recognizeOpen ? (
                              <div className="qaProcessBody">
                                {citations.length ? (
                                  <ul className="qaRecognizeList">
                                    {citations.map((c) => (
                                      <li key={`${c.docId}-${c.index}`}>
                                        <button type="button" className="qaRecognizeLink" onClick={() => openCitation(c)}>
                                          <span className="qaRecognizeIndex">[{c.index}]</span>
                                          <span>
                                            {c.title} · 第 {c.page} 页
                                          </span>
                                          <span className="qaRecognizeKb">{c.knowledgeBase}</span>
                                        </button>
                                      </li>
                                    ))}
                                  </ul>
                                ) : (
                                  <div className="qaProcessHint">
                                    {streamPhase?.message || '正在理解问题并检索知识库…'}
                                  </div>
                                )}
                              </div>
                            ) : null}
                          </section>

                          {(thinkingText || isSending) && (
                            <section className="qaProcessPanel qaThinkPanel">
                              <button
                                type="button"
                                className="qaProcessToggle"
                                onClick={() => setThinkOpen((v) => !v)}
                                aria-expanded={thinkOpen}
                              >
                                {thinkOpen ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                                <Brain size={16} />
                                <span className="qaProcessLabel">
                                  {isSending && !answerText
                                    ? streamPhase?.message || '思考中…'
                                    : doneInfo?.elapsedMs
                                      ? `已完成思考（${(doneInfo.elapsedMs / 1000).toFixed(1)}s）`
                                      : '思考过程'}
                                </span>
                                {isSending && !answerText ? <span className="qaProcessPulse" /> : null}
                              </button>
                              {thinkOpen ? (
                                <div className="qaProcessBody qaThinkBody">
                                  {thinkingText || streamPhase?.message || '正在分析检索片段…'}
                                  {isSending && !answerText ? (
                                    <span className="qaStreamCaret" aria-hidden="true" />
                                  ) : null}
                                </div>
                              ) : null}
                            </section>
                          )}
                        </div>
                      ) : null}

                      <div className="qaBubble qaBubbleAi">
                        {answerText || !isSending ? (
                          <AnswerContent
                            text={answerText}
                            citations={citations}
                            streaming={isSending && Boolean(answerText)}
                            onOpenCitation={openCitation}
                          />
                        ) : (
                          <div className="qaAnswerPending">
                            {streamPhase?.message || '正在生成回答…'}
                          </div>
                        )}
                      </div>

                      {citations.length && !isSending ? (
                        <section className="qaSources">
                          <div className="qaSourcesTitle">参考来源（{citations.length}）</div>
                          <div className="qaSourceList">
                            {citations.map((source) => (
                              <article key={`${source.docId}-${source.index}`} className="qaSourceCard">
                                <div className="qaSourceHeader">
                                  <div className="qaSourceDoc">
                                    <BookOpenText size={16} />
                                    <span>
                                      [{source.index}] {source.title} · 第 {source.page} 页
                                    </span>
                                  </div>
                                  <button
                                    type="button"
                                    className="qaLinkBtn"
                                    onClick={() => openCitation(source)}
                                  >
                                    查看原文 →
                                  </button>
                                </div>
                                <div className="qaSourceExcerpt">
                                  <mark>{source.excerpt}</mark>
                                </div>
                              </article>
                            ))}
                          </div>
                        </section>
                      ) : null}

                      <div className="qaAnswerToolbar">
                        <button
                          type="button"
                          className="qaInlineBtn"
                          onClick={onHelpful}
                          disabled={!assistantMessageId || feedbackBusy}
                        >
                          <ThumbsUp size={16} />
                          <span>有帮助</span>
                        </button>
                        <button
                          type="button"
                          className="qaInlineBtn"
                          onClick={() => setFeedbackOpen(true)}
                          disabled={!assistantMessageId || feedbackBusy}
                        >
                          <ThumbsDown size={16} />
                          <span>没帮助</span>
                        </button>
                        <button
                          type="button"
                          className="qaInlineBtn"
                          onClick={() => setRatingOpen((v) => !v)}
                          disabled={!assistantMessageId || feedbackBusy}
                        >
                          <Star size={16} />
                          <span>评分</span>
                        </button>
                        <button
                          type="button"
                          className="qaInlineBtn"
                          onClick={onFavoriteAnswer}
                          disabled={!assistantMessageId || feedbackBusy}
                        >
                          <Bookmark size={16} />
                          <span>收藏回答</span>
                        </button>
                        <button
                          type="button"
                          className="qaInlineBtn"
                          onClick={async () => {
                            if (answerText && navigator.clipboard?.writeText) {
                              await navigator.clipboard.writeText(answerText)
                              setLastActionHint('回答已复制')
                            }
                          }}
                        >
                          <Copy size={16} />
                          <span>复制</span>
                        </button>
                        <button type="button" className="qaInlineBtn" onClick={onRegenerate} disabled={isSending}>
                          <Sparkles size={16} />
                          <span>重新回答</span>
                        </button>
                      </div>

                      {!isSending && doneInfo?.status === 'OK' && (doneInfo.followUps?.length || 0) > 0 ? (
                        <section className="qaFollowUps" aria-label="推荐追问">
                          {doneInfo.followUps!.map((item) => (
                            <button
                              key={item}
                              type="button"
                              className="qaFollowUpBtn"
                              disabled={isSending}
                              onClick={() => onFollowUp(item)}
                            >
                              <span>{item}</span>
                              <ChevronRight size={16} />
                            </button>
                          ))}
                        </section>
                      ) : null}

                      {ratingOpen ? (
                        <div className="qaRatingRow" role="group" aria-label="回答评分">
                          {[1, 2, 3, 4, 5].map((score) => (
                            <button
                              key={score}
                              type="button"
                              className="qaInlineBtn"
                              disabled={feedbackBusy}
                              onClick={() => onRate(score)}
                            >
                              {score} 分
                            </button>
                          ))}
                        </div>
                      ) : null}
                    </div>
                  </div>
                ) : (
                  <div className="qaMessage qaMessageAi">
                    <div className="qaAiAvatar" aria-hidden="true">
                      <span className="qaAiOrb" />
                    </div>
                    <div className="qaAnswerColumn">
                      <div className="qaBubble qaBubbleAi qaEmptyState">
                        <div className="qaEmptyTitle">
                          抱歉，我在您有权访问的知识库中未找到相关信息。
                        </div>
                        <div className="qaEmptyIntro">您可以尝试：</div>
                        <ul className="qaEmptyList">
                          {(doneInfo?.suggestions || ['换个问法', '切换到全部知识库', '联系业务负责人']).map(
                            (tip) => (
                              <li key={tip}>{tip}</li>
                            ),
                          )}
                        </ul>
                        {doneInfo?.contact ? (
                          <div className="qaContactCard">
                            <div className="qaContactLabel">业务联系人</div>
                            <div className="qaContactName">
                              {doneInfo.contact.name} · {doneInfo.contact.title}
                            </div>
                            <div className="qaContactSub">
                              {[doneInfo.contact.wecom, doneInfo.contact.extNo ? `分机 ${doneInfo.contact.extNo}` : '']
                                .filter(Boolean)
                                .join(' / ') || '企业微信'}
                            </div>
                          </div>
                        ) : null}
                      </div>
                    </div>
                  </div>
                )}
              </>
            )}
          </section>

          <footer className="qaComposerDock">
            {lastActionHint ? (
              <div className="qaActionHint" role="status" aria-live="polite">
                {lastActionHint}
              </div>
            ) : null}

            <form className="qaComposer" onSubmit={onSubmit}>
              <textarea
                className="qaComposerInput"
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                placeholder={placeholder}
                rows={3}
                aria-label="继续追问"
              />

              <div className="qaComposerBar">
                <div className="qaComposerMeta">
                  {doneInfo?.disclaimer || 'AI 可能出错，请以原文为准'}
                  {doneInfo?.elapsedMs ? ` · 回答耗时 ${(doneInfo.elapsedMs / 1000).toFixed(1)}s` : ''}
                </div>

                <button className="qaSendBtn" type="submit" disabled={isSending || !question.trim()}>
                  {isSending ? <Sparkles className="qaSpin" size={18} /> : <Send size={18} />}
                  <span>发送</span>
                </button>
              </div>
            </form>
          </footer>
        </div>
      </main>

      <FeedbackModal
        open={feedbackOpen}
        messageId={assistantMessageId}
        onClose={() => setFeedbackOpen(false)}
        onSubmitted={(message) => setLastActionHint(message)}
      />
    </div>
  )
}

export default ChatPage
