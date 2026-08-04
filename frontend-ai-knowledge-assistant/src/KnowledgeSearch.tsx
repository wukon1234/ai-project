import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import {
  ArrowUpRight,
  Eye,
  FileImage,
  FileSpreadsheet,
  FileText,
  MessageCircleQuestion,
  Presentation,
  Search,
  Sparkles,
} from 'lucide-react'
import { searchHot, searchKnowledge, type SearchResultItem } from './api'
import type { SourceDoc } from './DocumentReader'

type FilterId = 'all' | 'product' | 'hr' | 'tech' | 'support'
type SortId = 'relevant' | 'newest' | 'popular'
type FileType = 'pdf' | 'word' | 'excel' | 'ppt' | 'image'

type SearchResult = {
  id: string
  title: string
  fileType: FileType
  category: Exclude<FilterId, 'all'>
  knowledgeBase: string
  pages: number
  updatedAt: string
  views: number
  excerptBefore: string
  highlights: string[]
  excerptAfter: string
  page: number
}

const filters: Array<{ id: FilterId; label: string }> = [
  { id: 'all', label: '全部' },
  { id: 'product', label: '产品' },
  { id: 'hr', label: '人事' },
  { id: 'tech', label: '技术' },
  { id: 'support', label: '售后' },
]

const sorts: Array<{ id: SortId; label: string }> = [
  { id: 'relevant', label: '最相关' },
  { id: 'newest', label: '最新更新' },
  { id: 'popular', label: '最多浏览' },
]

const sortApiMap: Record<SortId, 'relevance' | 'updated' | 'views'> = {
  relevant: 'relevance',
  newest: 'updated',
  popular: 'views',
}

const HOT_FALLBACK = ['报销流程', '年假规定', '产品A规格', '考勤制度']

function toFileType(value: string | undefined): FileType {
  const t = (value || 'pdf').toLowerCase()
  if (t === 'word' || t === 'excel' || t === 'ppt' || t === 'image' || t === 'pdf') return t
  return 'pdf'
}

function toCategory(value: string | undefined): Exclude<FilterId, 'all'> {
  if (value === 'product' || value === 'hr' || value === 'tech' || value === 'support') return value
  return 'hr'
}

function mapResult(item: SearchResultItem): SearchResult {
  return {
    id: String(item.id),
    title: item.title || '未命名文档',
    fileType: toFileType(item.fileType),
    category: toCategory(item.category),
    knowledgeBase: item.knowledgeBase || item.category || '',
    pages: item.pages ?? 0,
    updatedAt: item.updatedAt || '',
    views: item.views ?? 0,
    excerptBefore: item.excerptBefore || '',
    highlights: Array.isArray(item.highlights) ? item.highlights : [],
    excerptAfter: item.excerptAfter || '',
    page: item.page ?? 1,
  }
}

function FileTypeIcon({ type }: { type: FileType }) {
  const map: Record<FileType, { icon: ReactNode; label: string; className: string }> = {
    pdf: { icon: <FileText size={18} />, label: 'PDF', className: 'ksFilePdf' },
    word: { icon: <FileText size={18} />, label: 'Word', className: 'ksFileWord' },
    excel: { icon: <FileSpreadsheet size={18} />, label: 'Excel', className: 'ksFileExcel' },
    ppt: { icon: <Presentation size={18} />, label: 'PPT', className: 'ksFilePpt' },
    image: { icon: <FileImage size={18} />, label: '图片', className: 'ksFileImage' },
  }
  const item = map[type]
  return (
    <div className={`ksFileIcon ${item.className}`} title={item.label}>
      {item.icon}
      <span>{item.label}</span>
    </div>
  )
}

function highlightExcerpt(result: SearchResult) {
  const nodes: ReactNode[] = [result.excerptBefore]
  result.highlights.forEach((word, index) => {
    nodes.push(<mark key={`${result.id}-h-${index}`}>{word}</mark>)
    if (index < result.highlights.length - 1) nodes.push(' ')
  })
  nodes.push(result.excerptAfter)
  return nodes
}

type KnowledgeSearchProps = {
  onAsk: (prompt?: string) => void
  onRead: (doc: SourceDoc) => void
  onAskAboutDoc: (title: string) => void
}

function KnowledgeSearch({ onAsk, onRead, onAskAboutDoc }: KnowledgeSearchProps) {
  const [query, setQuery] = useState('')
  const [committedQuery, setCommittedQuery] = useState('')
  const [filter, setFilter] = useState<FilterId>('all')
  const [sort, setSort] = useState<SortId>('relevant')
  const [results, setResults] = useState<SearchResult[]>([])
  const [total, setTotal] = useState(0)
  const [hotSearches, setHotSearches] = useState<string[]>(HOT_FALLBACK)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    let alive = true
    ;(async () => {
      try {
        const hot = await searchHot()
        if (alive && Array.isArray(hot) && hot.length) setHotSearches(hot)
      } catch {
        // 热搜失败时保留种子词
      }
    })()
    return () => {
      alive = false
    }
  }, [])

  useEffect(() => {
    let alive = true
    const timer = window.setTimeout(async () => {
      setLoading(true)
      setError(null)
      try {
        const data = await searchKnowledge({
          q: committedQuery || undefined,
          category: filter,
          sort: sortApiMap[sort],
          page: 1,
          size: 20,
        })
        if (!alive) return
        const list = (data?.list || []).map(mapResult)
        setResults(list)
        setTotal(data?.total ?? list.length)
        setError(null)
      } catch (err) {
        if (!alive) return
        setResults([])
        setTotal(0)
        const msg = err instanceof Error ? err.message : '搜索失败'
        setError(msg === '系统错误' ? '搜索暂时不可用，请稍后重试' : msg)
      } finally {
        if (alive) setLoading(false)
      }
    }, 250)
    return () => {
      alive = false
      window.clearTimeout(timer)
    }
  }, [committedQuery, filter, sort, reloadToken])

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    setCommittedQuery(query.trim())
  }

  return (
    <div className="ksPage">
      <header className="ksHeader">
        <div className="ksBrandRow">
          <div className="ksBrand">智识云</div>
          <button type="button" className="ksAskJump" onClick={() => onAsk()}>
            <Sparkles size={16} />
            <span>去问答</span>
          </button>
        </div>

        <form className="ksSearchForm" onSubmit={onSubmit}>
          <div className="ksSearchBox">
            <Search size={18} className="ksSearchIcon" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="搜索文档、制度、产品说明…"
              aria-label="知识搜索"
            />
          </div>
        </form>

        <div className="ksFilterRow">
          <div className="ksChips" role="tablist" aria-label="知识范围筛选">
            {filters.map((item) => (
              <button
                key={item.id}
                type="button"
                role="tab"
                aria-selected={filter === item.id}
                className={`ksChip ${filter === item.id ? 'ksChipActive' : ''}`}
                onClick={() => setFilter(item.id)}
              >
                {item.label}
              </button>
            ))}
          </div>

          <div className="ksSorts" role="group" aria-label="排序">
            {sorts.map((item) => (
              <button
                key={item.id}
                type="button"
                className={`ksSortBtn ${sort === item.id ? 'ksSortBtnActive' : ''}`}
                onClick={() => setSort(item.id)}
              >
                {item.label}
              </button>
            ))}
          </div>
        </div>
      </header>

      <div className="ksBody">
        <main className="ksMain">
          <div className="ksResultMeta">
            {loading
              ? '搜索中…'
              : error
                ? error
                : results.length > 0
                  ? `找到 ${total} 条相关知识`
                  : '没有匹配结果'}
          </div>

          {!loading && !error && results.length > 0 ? (
            <div className="ksResultList">
              {results.map((item) => (
                <article key={item.id} className="ksResultCard">
                  <FileTypeIcon type={item.fileType} />

                  <div className="ksResultContent">
                    <h2 className="ksResultTitle">{item.title}</h2>
                    <p className="ksResultExcerpt">{highlightExcerpt(item)}</p>
                    <div className="ksResultInfo">
                      <span>{item.knowledgeBase}</span>
                      <span>·</span>
                      <span>{item.pages} 页</span>
                      <span>·</span>
                      <span>{item.updatedAt} 更新</span>
                      <span>·</span>
                      <span className="ksViews">
                        <Eye size={14} />
                        {item.views} 次浏览
                      </span>
                    </div>

                    <div className="ksResultActions">
                      <button
                        type="button"
                        className="ksPrimaryBtn"
                        onClick={() =>
                          onRead({
                            id: item.id,
                            title: item.title,
                            page: item.page,
                            knowledgeBase: item.knowledgeBase,
                            excerpt: item.highlights.join(' '),
                          })
                        }
                      >
                        阅读
                        <ArrowUpRight size={14} />
                      </button>
                      <button
                        type="button"
                        className="ksGhostBtn"
                        onClick={() => onAskAboutDoc(item.title)}
                      >
                        <MessageCircleQuestion size={14} />
                        基于此文提问
                      </button>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          ) : null}

          {!loading && error ? (
            <div className="ksEmpty">
              <div className="ksEmptyOrb" aria-hidden="true" />
              <h3>搜索失败</h3>
              <p>{error}</p>
              <button
                type="button"
                className="ksEmptyBtn"
                onClick={() => setReloadToken((n) => n + 1)}
              >
                重试
              </button>
            </div>
          ) : null}

          {!loading && !error && results.length === 0 ? (
            <div className="ksEmpty">
              <div className="ksEmptyOrb" aria-hidden="true" />
              <h3>
                {committedQuery
                  ? '未找到相关内容，试试 AI 问答？'
                  : '当前范围内暂无已入库文档'}
              </h3>
              <p>
                {committedQuery
                  ? '问答会在你有权限的知识库中检索，并附上来源可追溯。'
                  : '请确认账号已开通知识库权限，或到管理后台上传并完成入库后再试。'}
              </p>
              <button type="button" className="ksEmptyBtn" onClick={() => onAsk(query || committedQuery)}>
                <Sparkles size={16} />
                去 AI 问答
              </button>
            </div>
          ) : null}
        </main>

        <aside className="ksHotPanel" aria-label="热门搜索">
          <div className="ksHotTitle">热门搜索</div>
          <ol className="ksHotList">
            {hotSearches.map((term, index) => (
              <li key={term}>
                <button
                  type="button"
                  onClick={() => {
                    setQuery(term)
                    setCommittedQuery(term)
                  }}
                >
                  <span className="ksHotRank">{index + 1}</span>
                  <span>{term}</span>
                </button>
              </li>
            ))}
          </ol>
          <div className="ksHotHint">只读浏览 · 无上传 / 删除 / 编辑入口</div>
        </aside>
      </div>
    </div>
  )
}

export default KnowledgeSearch
