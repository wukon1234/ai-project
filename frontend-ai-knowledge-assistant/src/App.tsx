import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import {
  BookOpenText,
  Check,
  ChevronDown,
  Copy,
  Ellipsis,
  History,
  Library,
  MessageSquarePlus,
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
import AuthPage from './AuthPage'
import DocumentReader, { type SourceDoc } from './DocumentReader'
import FavoritesPage from './FavoritesPage'
import FeedbackModal from './FeedbackModal'
import HelpPage from './HelpPage'
import HistoryPage from './HistoryPage'
import KnowledgeBrowse from './KnowledgeBrowse'
import KnowledgeSearch from './KnowledgeSearch'
import ProfilePage from './ProfilePage'
import UsageStatsPage from './UsageStatsPage'
import { clearTokens, logout as logoutApi, me } from './api'
import './App.css'

type AppView = 'chat' | 'search' | 'browse' | 'history' | 'profile' | 'favorites' | 'help' | 'stats'

type KnowledgeScopeId = 'all' | 'product' | 'hr' | 'tech' | 'support'

const knowledgeScopes: Array<{ id: KnowledgeScopeId; label: string }> = [
  { id: 'all', label: '全部知识库' },
  { id: 'product', label: '产品知识库' },
  { id: 'hr', label: '人事制度库' },
  { id: 'tech', label: '技术文档库' },
  { id: 'support', label: '售后 FAQ' }
]

const conversationGroups = [
  {
    title: '今天',
    items: [
      { id: 'c1', title: '年假相关规定', scope: '产品知识库', time: '14:32', active: true },
      { id: 'c2', title: '报销流程咨询', scope: '人事制度', time: '11:20' }
    ]
  },
  {
    title: '昨天',
    items: [{ id: 'c3', title: 'A 产品 vs B 产品对比', scope: '产品知识库', time: '18:06' }]
  }
]

const answerSources = [
  {
    id: 's1',
    title: '《员工手册 2026 版》',
    page: 23,
    pageLabel: '第 23 页',
    knowledgeBase: '人事制度库',
    excerpt: '员工年假规定：入职满1年不满10年，年休假5天；满10年不满20年，年休假10天。'
  },
  {
    id: 's2',
    title: '《HR 常见问题 FAQ》',
    page: 8,
    pageLabel: '第 8 页',
    knowledgeBase: '人事制度库',
    excerpt: '如员工当年符合年假条件，可在 OA 系统提交年假申请，由直属主管审批后生效。'
  }
]

const emptyStateTips = [
  '换个问法，例如“司龄 8 个月可以休年假吗？”',
  '切换到「全部知识库」范围',
  '联系 HR 部门'
]

function ChatPage({
  onOpenSource,
  onOpenSearch,
  onOpenBrowse,
  onOpenHistory,
  onOpenProfile,
  initialQuestion
}: {
  onOpenSource: (doc: SourceDoc) => void
  onOpenSearch: () => void
  onOpenBrowse: () => void
  onOpenHistory: () => void
  onOpenProfile: () => void
  initialQuestion?: string
}) {
  const [selectedScope, setSelectedScope] = useState<KnowledgeScopeId>('hr')
  const [scopeOpen, setScopeOpen] = useState(false)
  const [question, setQuestion] = useState(initialQuestion || '继续追问…')
  const [lastActionHint, setLastActionHint] = useState<string | null>(null)
  const [isSending, setIsSending] = useState(false)
  const [showEmptyAnswer, setShowEmptyAnswer] = useState(false)
  const [feedbackOpen, setFeedbackOpen] = useState(false)
  const scopeRef = useRef<HTMLDivElement | null>(null)

  const placeholder = useMemo(() => '继续追问…', [])

  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (!scopeRef.current?.contains(e.target as Node)) {
        setScopeOpen(false)
      }
    }
    document.addEventListener('mousedown', onDocClick)
    return () => document.removeEventListener('mousedown', onDocClick)
  }, [])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!question.trim()) return

    setIsSending(true)
    setLastActionHint(null)

    await new Promise((r) => setTimeout(r, 650))

    const nextEmptyState = /联系人|hr|人事电话|没找到|找不到/i.test(question)
    setShowEmptyAnswer(nextEmptyState)
    setLastActionHint(nextEmptyState ? '已展示无答案状态（mock）' : '已重新生成回答（mock）')
    setIsSending(false)
  }

  const selectedScopeLabel =
    knowledgeScopes.find((scope) => scope.id === selectedScope)?.label ?? '人事制度库'

  return (
    <div className="qaPage">
      <aside className="qaSidebar">
        <div className="qaBrand">智识云</div>

        <button className="qaPrimaryGhost" type="button">
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
          <input placeholder="搜索历史对话…" aria-label="搜索历史对话" />
        </div>

        <div className="qaHistoryGroups">
          {conversationGroups.map((group) => (
            <section key={group.title} className="qaHistoryGroup">
              <div className="qaHistoryTitle">{group.title}</div>
              <div className="qaHistoryList">
                {group.items.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    className={`qaHistoryItem ${item.active ? 'qaHistoryItemActive' : ''}`}
                    onClick={() => setLastActionHint(`切换到会话「${item.title}」（mock）`)}
                  >
                    <div className="qaHistoryMain">
                      <div className="qaHistoryText">{item.title}</div>
                      <div className="qaHistoryMeta">
                        {item.scope} · {item.time}
                      </div>
                    </div>
                    <div className="qaHistoryActions" aria-hidden="true">
                      <Ellipsis size={16} />
                    </div>
                  </button>
                ))}
              </div>
            </section>
          ))}
        </div>

        <div className="qaSidebarFooter">
          <button className="qaUserCard" type="button" onClick={onOpenProfile}>
            <div className="qaUserMeta">
              <UserCircle2 size={22} />
              <div>
                <div className="qaUserName">张明</div>
                <div className="qaUserSub">设置</div>
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
                      onClick={() => {
                        setSelectedScope(scope.id)
                        setScopeOpen(false)
                        setLastActionHint(`已切换到「${scope.label}」`)
                      }}
                    >
                      <span>{scope.label}</span>
                      {selectedScope === scope.id ? <Check size={16} /> : null}
                    </button>
                  ))}
                </div>
              ) : null}
            </div>

            <div className="qaTopActions">
              <button
                type="button"
                className="qaTextBtn"
                onClick={() => setLastActionHint('已复制分享链接（mock）')}
              >
                <Share2 size={16} />
                <span>分享对话</span>
              </button>
              <button
                type="button"
                className="qaTextBtn"
                onClick={() => setLastActionHint('已清空当前对话（mock）')}
              >
                <Trash2 size={16} />
                <span>清空对话</span>
              </button>
            </div>
          </header>

          <section className="qaConversation">
            <div className="qaMessage qaMessageUser">
              <div className="qaBubble qaBubbleUser">
                请问公司年假有几天？入职不满一年怎么算？
              </div>
            </div>

            {!showEmptyAnswer ? (
              <div className="qaMessage qaMessageAi">
                <div className="qaAiAvatar" aria-hidden="true">
                  <span className="qaAiOrb" />
                </div>

                <div className="qaAnswerColumn">
                  <div className="qaBubble qaBubbleAi">
                    <div className="qaAnswerText">
                      根据公司规定，员工年假天数与工龄相关[1][2]：
                    </div>
                    <ul className="qaFactList">
                      <li>
                        入职满 1 年不满 10 年：年休假 <strong>5 天</strong>
                      </li>
                      <li>
                        入职满 10 年不满 20 年：年休假 <strong>10 天</strong>
                      </li>
                      <li>
                        入职不满 1 年：不享受带薪年假[1]
                      </li>
                    </ul>
                    <div className="qaAnswerText">
                      如需申请，可在 OA 系统提交年假申请[2]。
                    </div>
                  </div>

                  <section className="qaSources">
                    <div className="qaSourcesTitle">📎 参考来源（2）</div>
                    <div className="qaSourceList">
                      {answerSources.map((source) => (
                        <article key={source.id} className="qaSourceCard">
                          <div className="qaSourceHeader">
                            <div className="qaSourceDoc">
                              <BookOpenText size={16} />
                              <span>
                                {source.title} · {source.pageLabel}
                              </span>
                            </div>
                            <button
                              type="button"
                              className="qaLinkBtn"
                              onClick={() =>
                                onOpenSource({
                                  id: source.id,
                                  title: source.title,
                                  page: source.page,
                                  knowledgeBase: source.knowledgeBase,
                                  excerpt: source.excerpt
                                })
                              }
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

                  <div className="qaAnswerToolbar">
                    <button type="button" className="qaInlineBtn">
                      <ThumbsUp size={16} />
                      <span>有帮助</span>
                    </button>
                    <button
                      type="button"
                      className="qaInlineBtn"
                      onClick={() => setFeedbackOpen(true)}
                    >
                      <ThumbsDown size={16} />
                      <span>没帮助</span>
                    </button>
                    <button type="button" className="qaInlineBtn">
                      <Star size={16} />
                      <span>评分</span>
                    </button>
                    <button
                      type="button"
                      className="qaInlineBtn"
                      onClick={() => setLastActionHint('回答已复制（mock）')}
                    >
                      <Copy size={16} />
                      <span>复制</span>
                    </button>
                    <button
                      type="button"
                      className="qaInlineBtn"
                      onClick={() => setLastActionHint('已重新回答（mock）')}
                    >
                      <Sparkles size={16} />
                      <span>重新回答</span>
                    </button>
                  </div>
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
                      {emptyStateTips.map((tip) => (
                        <li key={tip}>{tip}</li>
                      ))}
                    </ul>
                    <div className="qaContactCard">
                      <div className="qaContactLabel">HR 联系人</div>
                      <div className="qaContactName">李晓雯 · 人力资源BP</div>
                      <div className="qaContactSub">企业微信 / 分机 8021</div>
                    </div>
                  </div>
                </div>
              </div>
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
                  AI 可能出错，请以原文为准 · 回答耗时 2.3s
                </div>

                <button
                  className="qaSendBtn"
                  type="submit"
                  disabled={isSending || !question.trim()}
                >
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
        onClose={() => setFeedbackOpen(false)}
        onSubmitted={(message) => setLastActionHint(message)}
      />
    </div>
  )
}

function App() {
  const [authed, setAuthed] = useState(false)
  const [booting, setBooting] = useState(true)
  const [view, setView] = useState<AppView>('chat')
  const [activeDoc, setActiveDoc] = useState<SourceDoc | null>(null)
  const [chatSeed, setChatSeed] = useState<string | undefined>(undefined)

  function loginSuccess() {
    setAuthed(true)
    setView('chat')
  }

  async function logout() {
    try {
      await logoutApi()
    } catch (_err) {
      // ignore logout network errors in UI
    }
    clearTokens()
    setAuthed(false)
    setView('chat')
    setActiveDoc(null)
  }

  useEffect(() => {
    let alive = true
    async function bootstrap() {
      try {
        await me()
        if (alive) setAuthed(true)
      } catch (_err) {
        clearTokens()
        if (alive) setAuthed(false)
      } finally {
        if (alive) setBooting(false)
      }
    }
    bootstrap()
    return () => {
      alive = false
    }
  }, [])

  if (booting) {
    return <div style={{ padding: 24 }}>加载中...</div>
  }

  if (!authed) {
    return <AuthPage onSuccess={loginSuccess} />
  }

  if (activeDoc) {
    return (
      <DocumentReader
        doc={activeDoc}
        onBack={() => setActiveDoc(null)}
      />
    )
  }

  if (view === 'search') {
    return (
      <KnowledgeSearch
        onAsk={(prompt) => {
          setChatSeed(prompt?.trim() ? prompt : '继续追问…')
          setView('chat')
        }}
        onRead={setActiveDoc}
        onAskAboutDoc={(title) => {
          setChatSeed(`基于「${title}」继续提问：`)
          setView('chat')
        }}
      />
    )
  }

  if (view === 'browse') {
    return (
      <KnowledgeBrowse
        onAsk={(prompt) => {
          setChatSeed(prompt?.trim() ? prompt : '继续追问…')
          setView('chat')
        }}
        onRead={setActiveDoc}
        onBackHome={() => setView('chat')}
      />
    )
  }

  if (view === 'history') {
    return (
      <HistoryPage
        onContinue={(title) => {
          setChatSeed(`继续对话：「${title}」`)
          setView('chat')
        }}
        onAskFirst={() => {
          setChatSeed('继续追问…')
          setView('chat')
        }}
        onBack={() => setView('chat')}
      />
    )
  }

  if (view === 'profile') {
    return (
      <ProfilePage
        onBack={() => setView('chat')}
        onOpenFavorites={() => setView('favorites')}
        onOpenHistory={() => setView('history')}
        onOpenHelp={() => setView('help')}
        onOpenStats={() => setView('stats')}
        onLogout={logout}
      />
    )
  }

  if (view === 'stats') {
    return (
      <UsageStatsPage
        onBack={() => setView('profile')}
        onAskAgain={(question) => {
          setChatSeed(question)
          setView('chat')
        }}
      />
    )
  }

  if (view === 'help') {
    return (
      <HelpPage
        onBack={() => setView('profile')}
        onAsk={() => setView('chat')}
        onOpenSearch={() => setView('search')}
      />
    )
  }

  if (view === 'favorites') {
    return (
      <FavoritesPage
        onBack={() => setView('profile')}
        onRead={setActiveDoc}
        onAsk={(prompt) => {
          setChatSeed(prompt)
          setView('chat')
        }}
      />
    )
  }

  return (
    <ChatPage
      key={chatSeed || 'default-chat'}
      onOpenSource={setActiveDoc}
      onOpenSearch={() => setView('search')}
      onOpenBrowse={() => setView('browse')}
      onOpenHistory={() => setView('history')}
      onOpenProfile={() => setView('profile')}
      initialQuestion={chatSeed}
    />
  )
}

export default App
