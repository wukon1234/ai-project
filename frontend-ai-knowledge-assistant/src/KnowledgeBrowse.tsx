import { useEffect, useState } from 'react'
import {
  ArrowLeft,
  BookOpen,
  Boxes,
  Eye,
  FileText,
  Headphones,
  MessageCircleQuestion,
  Search,
  Sparkles,
  Wrench,
} from 'lucide-react'
import { listLibraries, listLibraryDocuments, type LibraryDocItem, type LibraryItem } from './api'
import type { SourceDoc } from './DocumentReader'

type LibraryId = 'product' | 'hr' | 'tech' | 'support'
type DocCategory = 'all' | 'faq' | 'policy' | 'manual'

type KnowledgeLibrary = {
  id: LibraryId
  name: string
  intro: string
  docs: number
  updatedLabel: string
  tags: string[]
  tone: 'blue' | 'green' | 'cyan' | 'orange'
  icon: 'box' | 'book' | 'wrench' | 'headset'
}

type LibraryDoc = {
  id: string
  libraryId: LibraryId
  title: string
  category: Exclude<DocCategory, 'all'>
  pages: number
  updatedAt: string
  views: number
  page: number
  summary: string
}

const toneMap: Record<string, KnowledgeLibrary['tone']> = {
  product: 'blue',
  hr: 'green',
  tech: 'cyan',
  support: 'orange',
}

const iconMap: Record<string, KnowledgeLibrary['icon']> = {
  product: 'box',
  hr: 'book',
  tech: 'wrench',
  support: 'headset',
}

const categoryTabs: Array<{ id: DocCategory; label: string }> = [
  { id: 'all', label: '全部' },
  { id: 'faq', label: 'FAQ' },
  { id: 'policy', label: '制度' },
  { id: 'manual', label: '手册' },
]

function parseTags(tags?: string[] | string): string[] {
  if (!tags) return []
  if (Array.isArray(tags)) return tags
  return tags
    .split(/[,，\s]+/)
    .map((t) => t.trim())
    .filter(Boolean)
}

function mapLibrary(item: LibraryItem): KnowledgeLibrary | null {
  const code = item.code as LibraryId
  if (!['product', 'hr', 'tech', 'support'].includes(code)) return null
  return {
    id: code,
    name: item.name || code,
    intro: item.description || '',
    docs: item.docCount ?? 0,
    updatedLabel: item.updatedAt ? `最近更新 ${item.updatedAt}` : '暂无更新',
    tags: parseTags(item.tags),
    tone: toneMap[code] || 'blue',
    icon: iconMap[code] || 'box',
  }
}

function mapDoc(item: LibraryDocItem, libraryId: LibraryId): LibraryDoc {
  const cat = item.category
  const category: Exclude<DocCategory, 'all'> =
    cat === 'faq' || cat === 'policy' || cat === 'manual' ? cat : 'manual'
  return {
    id: String(item.id),
    libraryId,
    title: item.title || '未命名文档',
    category,
    pages: item.pages ?? 0,
    updatedAt: item.updatedAt || '',
    views: item.views ?? 0,
    page: item.page ?? 1,
    summary: item.summary || '',
  }
}

function LibraryIcon({ icon }: { icon: KnowledgeLibrary['icon'] }) {
  if (icon === 'book') return <BookOpen size={28} />
  if (icon === 'wrench') return <Wrench size={28} />
  if (icon === 'headset') return <Headphones size={28} />
  return <Boxes size={28} />
}

type KnowledgeBrowseProps = {
  onAsk: (prompt?: string) => void
  onRead: (doc: SourceDoc) => void
  onBackHome?: () => void
}

function KnowledgeBrowse({ onAsk, onRead, onBackHome }: KnowledgeBrowseProps) {
  const [libraries, setLibraries] = useState<KnowledgeLibrary[]>([])
  const [activeLibraryId, setActiveLibraryId] = useState<LibraryId | null>(null)
  const [category, setCategory] = useState<DocCategory>('all')
  const [query, setQuery] = useState('')
  const [committedQuery, setCommittedQuery] = useState('')
  const [docs, setDocs] = useState<LibraryDoc[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const activeLibrary = libraries.find((item) => item.id === activeLibraryId) ?? null

  useEffect(() => {
    let alive = true
    ;(async () => {
      setLoading(true)
      setError(null)
      try {
        const list = await listLibraries()
        if (!alive) return
        setLibraries((list || []).map(mapLibrary).filter(Boolean) as KnowledgeLibrary[])
      } catch {
        if (alive) {
          setLibraries([])
          // 技术异常走空态，不展示报错日志
          setError(null)
        }
      } finally {
        if (alive) setLoading(false)
      }
    })()
    return () => {
      alive = false
    }
  }, [])

  useEffect(() => {
    if (!activeLibraryId) return
    let alive = true
    const timer = window.setTimeout(async () => {
      setLoading(true)
      setError(null)
      try {
        const data = await listLibraryDocuments(activeLibraryId, {
          category,
          q: committedQuery,
          page: 1,
          size: 50,
        })
        if (!alive) return
        const list = (data?.list || []).map((d) => mapDoc(d, activeLibraryId))
        setDocs(list)
        setTotal(data?.total ?? list.length)
      } catch {
        if (!alive) return
        setDocs([])
        setTotal(0)
        // 技术异常走空态，不展示报错日志
        setError(null)
      } finally {
        if (alive) setLoading(false)
      }
    }, 200)
    return () => {
      alive = false
      window.clearTimeout(timer)
    }
  }, [activeLibraryId, category, committedQuery])

  if (activeLibrary) {
    return (
      <div className="kbPage">
        <header className="kbHeader">
          <button
            type="button"
            className="kbBackBtn"
            onClick={() => {
              setActiveLibraryId(null)
              setCategory('all')
              setQuery('')
              setCommittedQuery('')
              setDocs([])
            }}
          >
            <ArrowLeft size={16} />
            返回知识库
          </button>

          <div className="kbHeaderMain">
            <div>
              <h1 className="kbTitle">{activeLibrary.name}</h1>
              <p className="kbSubtitle">{activeLibrary.intro}</p>
            </div>
            <button type="button" className="kbAskJump" onClick={() => onAsk()}>
              <Sparkles size={16} />
              去问答
            </button>
          </div>
        </header>

        <div className="kbDetailBody">
          <div className="kbDetailToolbar">
            <div className="kbTabs" role="tablist" aria-label="文档分类">
              {categoryTabs.map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  role="tab"
                  aria-selected={category === tab.id}
                  className={`kbTab ${category === tab.id ? 'kbTabActive' : ''}`}
                  onClick={() => setCategory(tab.id)}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            <div className="kbSearchBox">
              <Search size={16} />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') setCommittedQuery(query.trim())
                }}
                placeholder="搜索本库文档…"
                aria-label="搜索本库文档"
              />
            </div>
          </div>

          <div className="kbDocMeta">
            {loading ? '加载中…' : error ? error : `共 ${total} 份可见文档 · 只读浏览`}
          </div>

          {!loading && !error && docs.length > 0 ? (
            <div className="kbDocList">
              {docs.map((doc) => (
                <article key={doc.id} className="kbDocCard">
                  <div className="kbDocIcon" aria-hidden="true">
                    <FileText size={18} />
                  </div>
                  <div className="kbDocContent">
                    <h2>{doc.title}</h2>
                    <p>{doc.summary}</p>
                    <div className="kbDocInfo">
                      <span>{doc.pages} 页</span>
                      <span>·</span>
                      <span>{doc.updatedAt} 更新</span>
                      <span>·</span>
                      <span className="kbViews">
                        <Eye size={14} />
                        {doc.views} 次浏览
                      </span>
                    </div>
                    <div className="kbDocActions">
                      <button
                        type="button"
                        className="kbPrimaryBtn"
                        onClick={() =>
                          onRead({
                            id: doc.id,
                            title: doc.title,
                            page: doc.page,
                            knowledgeBase: activeLibrary.name,
                            excerpt: doc.summary,
                          })
                        }
                      >
                        阅读
                      </button>
                      <button
                        type="button"
                        className="kbGhostBtn"
                        onClick={() => onAsk(`基于「${doc.title}」继续提问：`)}
                      >
                        <MessageCircleQuestion size={14} />
                        提问
                      </button>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          ) : null}

          {!loading && !error && docs.length === 0 ? (
            <div className="kbEmpty">
              <div className="kbEmptyOrb" aria-hidden="true" />
              <h3>本分类下暂无可见文档</h3>
              <p>可以切换分类，或直接去 AI 问答检索。</p>
              <button type="button" className="kbPrimaryBtn" onClick={() => onAsk()}>
                <Sparkles size={16} />
                去 AI 问答
              </button>
            </div>
          ) : null}
        </div>
      </div>
    )
  }

  return (
    <div className="kbPage">
      <header className="kbHeader">
        <div className="kbHeaderMain">
          <div>
            <h1 className="kbTitle">探索知识库</h1>
            <p className="kbSubtitle">浏览您有权限访问的企业知识</p>
          </div>
          <div className="kbHeaderActions">
            {onBackHome ? (
              <button type="button" className="kbGhostBtn" onClick={onBackHome}>
                返回问答
              </button>
            ) : null}
            <button type="button" className="kbAskJump" onClick={() => onAsk()}>
              <Sparkles size={16} />
              去问答
            </button>
          </div>
        </div>
      </header>

      <main className="kbGridWrap">
        {loading ? <div className="kbDocMeta">加载中…</div> : null}
        {error ? <div className="kbDocMeta">{error}</div> : null}
        <div className="kbGrid">
          {libraries.map((lib) => (
            <article key={lib.id} className="kbCard">
              <div className={`kbCover kbCover-${lib.tone}`}>
                <LibraryIcon icon={lib.icon} />
              </div>
              <div className="kbCardBody">
                <h2>{lib.name}</h2>
                <p>{lib.intro}</p>
                <div className="kbStats">
                  {lib.docs} 份文档 · {lib.updatedLabel}
                </div>
                <div className="kbTags">
                  {lib.tags.map((tag) => (
                    <span key={tag}>{tag}</span>
                  ))}
                </div>
                <button
                  type="button"
                  className="kbEnterBtn"
                  onClick={() => setActiveLibraryId(lib.id)}
                >
                  进入浏览
                </button>
              </div>
            </article>
          ))}
        </div>
        {!loading && !error && libraries.length === 0 ? (
          <div className="kbEmpty">
            <h3>暂无可访问的知识库</h3>
            <p>请联系管理员开通知识库权限。</p>
          </div>
        ) : null}
        <div className="kbReadonlyHint">无创建知识库 / 上传文档 / 权限配置入口</div>
      </main>
    </div>
  )
}

export default KnowledgeBrowse
