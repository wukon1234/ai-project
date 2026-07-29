import { useMemo, useState, type FormEvent, type ReactNode } from 'react'
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
  { id: 'support', label: '售后' }
]

const sorts: Array<{ id: SortId; label: string }> = [
  { id: 'relevant', label: '最相关' },
  { id: 'newest', label: '最新更新' },
  { id: 'popular', label: '最多浏览' }
]

const hotSearches = ['报销流程', '年假规定', '产品 A 规格', '考勤制度']

const allResults: SearchResult[] = [
  {
    id: 'd1',
    title: '《2026 年度报销管理制度》',
    fileType: 'pdf',
    category: 'hr',
    knowledgeBase: '人事制度库',
    pages: 12,
    updatedAt: '2026-03-15',
    views: 234,
    excerptBefore: '…',
    highlights: ['报销', '30 日内'],
    excerptAfter: '提交…',
    page: 5
  },
  {
    id: 'd2',
    title: '《员工手册 2026 版》',
    fileType: 'word',
    category: 'hr',
    knowledgeBase: '人事制度库',
    pages: 86,
    updatedAt: '2026-02-20',
    views: 512,
    excerptBefore: '员工',
    highlights: ['年假'],
    excerptAfter: '天数与工龄相关，入职满 1 年不满 10 年可休 5 天…',
    page: 23
  },
  {
    id: 'd3',
    title: '产品 A 技术规格说明书',
    fileType: 'ppt',
    category: 'product',
    knowledgeBase: '产品知识库',
    pages: 28,
    updatedAt: '2026-04-02',
    views: 188,
    excerptBefore: '产品 A 支持双模通信，关键',
    highlights: ['规格'],
    excerptAfter: '参数详见第 6 章对照表…',
    page: 6
  },
  {
    id: 'd4',
    title: '考勤制度与弹性工时说明',
    fileType: 'excel',
    category: 'hr',
    knowledgeBase: '人事制度库',
    pages: 4,
    updatedAt: '2026-01-18',
    views: 301,
    excerptBefore: '标准工时为每日 8 小时，',
    highlights: ['考勤'],
    excerptAfter: '异常需在 3 个工作日内完成补卡…',
    page: 1
  },
  {
    id: 'd5',
    title: '售后常见故障排查手册',
    fileType: 'pdf',
    category: 'support',
    knowledgeBase: '售后 FAQ',
    pages: 42,
    updatedAt: '2026-03-28',
    views: 156,
    excerptBefore: '设备无法开机时，请先检查电源与',
    highlights: ['售后'],
    excerptAfter: '热线登记流程…',
    page: 3
  },
  {
    id: 'd6',
    title: '内部 API 网关接入指南',
    fileType: 'word',
    category: 'tech',
    knowledgeBase: '技术文档库',
    pages: 19,
    updatedAt: '2026-05-10',
    views: 97,
    excerptBefore: '鉴权采用 Bearer Token，请求头需携带',
    highlights: ['技术'],
    excerptAfter: '签名校验字段…',
    page: 2
  },
  {
    id: 'd7',
    title: '产品包装与标识规范图示',
    fileType: 'image',
    category: 'product',
    knowledgeBase: '产品知识库',
    pages: 1,
    updatedAt: '2026-02-08',
    views: 76,
    excerptBefore: '外包装需印制防伪码与',
    highlights: ['产品'],
    excerptAfter: '批次追溯二维码…',
    page: 1
  }
]

function FileTypeIcon({ type }: { type: FileType }) {
  const map: Record<FileType, { icon: ReactNode; label: string; className: string }> = {
    pdf: { icon: <FileText size={18} />, label: 'PDF', className: 'ksFilePdf' },
    word: { icon: <FileText size={18} />, label: 'Word', className: 'ksFileWord' },
    excel: { icon: <FileSpreadsheet size={18} />, label: 'Excel', className: 'ksFileExcel' },
    ppt: { icon: <Presentation size={18} />, label: 'PPT', className: 'ksFilePpt' },
    image: { icon: <FileImage size={18} />, label: '图片', className: 'ksFileImage' }
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
    nodes.push(
      <mark key={`${result.id}-h-${index}`}>
        {word}
      </mark>
    )
    if (index < result.highlights.length - 1) {
      nodes.push(index === 0 ? '需在费用发生后 ' : ' ')
    }
  })
  // Special-case first sample card to match the brief wording closely.
  if (result.id === 'd1') {
    return (
      <>
        …<mark>报销</mark>需在费用发生后 <mark>30 日内</mark>提交…
      </>
    )
  }
  nodes.push(result.excerptAfter)
  return nodes
}

type KnowledgeSearchProps = {
  onAsk: (prompt?: string) => void
  onRead: (doc: SourceDoc) => void
  onAskAboutDoc: (title: string) => void
}

function KnowledgeSearch({ onAsk, onRead, onAskAboutDoc }: KnowledgeSearchProps) {
  const [query, setQuery] = useState('报销')
  const [filter, setFilter] = useState<FilterId>('all')
  const [sort, setSort] = useState<SortId>('relevant')

  const results = useMemo(() => {
    const q = query.trim().toLowerCase()
    let list = allResults.filter((item) => {
      const matchFilter = filter === 'all' || item.category === filter
      if (!matchFilter) return false
      if (!q) return true
      return (
        item.title.toLowerCase().includes(q) ||
        item.excerptBefore.toLowerCase().includes(q) ||
        item.excerptAfter.toLowerCase().includes(q) ||
        item.highlights.some((h) => h.toLowerCase().includes(q)) ||
        item.knowledgeBase.toLowerCase().includes(q)
      )
    })

    if (sort === 'newest') {
      list = [...list].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    } else if (sort === 'popular') {
      list = [...list].sort((a, b) => b.views - a.views)
    }

    return list
  }, [filter, query, sort])

  function onSubmit(e: FormEvent) {
    e.preventDefault()
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
            {results.length > 0
              ? `找到 ${results.length} 条相关知识`
              : '没有匹配结果'}
          </div>

          {results.length > 0 ? (
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
                            excerpt: item.highlights.join(' ')
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
          ) : (
            <div className="ksEmpty">
              <div className="ksEmptyOrb" aria-hidden="true" />
              <h3>未找到相关内容，试试 AI 问答？</h3>
              <p>问答会在你有权限的知识库中检索，并附上来源可追溯。</p>
              <button type="button" className="ksEmptyBtn" onClick={() => onAsk(query)}>
                <Sparkles size={16} />
                去 AI 问答
              </button>
            </div>
          )}
        </main>

        <aside className="ksHotPanel" aria-label="热门搜索">
          <div className="ksHotTitle">热门搜索</div>
          <ol className="ksHotList">
            {hotSearches.map((term, index) => (
              <li key={term}>
                <button type="button" onClick={() => setQuery(term)}>
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
