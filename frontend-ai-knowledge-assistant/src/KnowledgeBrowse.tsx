import { useMemo, useState } from 'react'
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

const libraries: KnowledgeLibrary[] = [
  {
    id: 'product',
    name: '产品知识库',
    intro: '产品规格、FAQ、竞品对比等',
    docs: 128,
    updatedLabel: '最近更新 2 天前',
    tags: ['#FAQ', '#规格', '#对比'],
    tone: 'blue',
    icon: 'box'
  },
  {
    id: 'hr',
    name: '人事制度库',
    intro: '员工手册、考勤、报销与休假制度',
    docs: 64,
    updatedLabel: '最近更新 5 天前',
    tags: ['#制度', '#手册', '#FAQ'],
    tone: 'green',
    icon: 'book'
  },
  {
    id: 'tech',
    name: '技术文档库',
    intro: '接口说明、架构设计、排障手册',
    docs: 97,
    updatedLabel: '最近更新 1 天前',
    tags: ['#手册', '#FAQ'],
    tone: 'cyan',
    icon: 'wrench'
  },
  {
    id: 'support',
    name: '售后 FAQ',
    intro: '常见故障、售后流程、服务话术',
    docs: 52,
    updatedLabel: '最近更新 3 天前',
    tags: ['#FAQ', '#流程'],
    tone: 'orange',
    icon: 'headset'
  }
]

const libraryDocs: LibraryDoc[] = [
  {
    id: 'ld1',
    libraryId: 'product',
    title: '产品 A 技术规格说明书',
    category: 'manual',
    pages: 28,
    updatedAt: '2026-04-02',
    views: 188,
    page: 6,
    summary: '覆盖产品 A 的关键参数、规格对照与竞品差异说明。'
  },
  {
    id: 'ld2',
    libraryId: 'product',
    title: '产品 FAQ 合集（2026）',
    category: 'faq',
    pages: 16,
    updatedAt: '2026-03-21',
    views: 266,
    page: 1,
    summary: '常见购买、安装与兼容性问题解答。'
  },
  {
    id: 'ld3',
    libraryId: 'product',
    title: 'A 产品 vs B 产品对比手册',
    category: 'manual',
    pages: 12,
    updatedAt: '2026-02-18',
    views: 143,
    page: 3,
    summary: '从性能、价格、适用场景对比 A/B 两款产品。'
  },
  {
    id: 'ld4',
    libraryId: 'hr',
    title: '《员工手册 2026 版》',
    category: 'manual',
    pages: 86,
    updatedAt: '2026-02-20',
    views: 512,
    page: 23,
    summary: '涵盖入职、休假、行为规范等人事制度全文。'
  },
  {
    id: 'ld5',
    libraryId: 'hr',
    title: '《2026 年度报销管理制度》',
    category: 'policy',
    pages: 12,
    updatedAt: '2026-03-15',
    views: 234,
    page: 5,
    summary: '报销时效、票据要求与审批链路说明。'
  },
  {
    id: 'ld6',
    libraryId: 'hr',
    title: '考勤制度与弹性工时说明',
    category: 'policy',
    pages: 4,
    updatedAt: '2026-01-18',
    views: 301,
    page: 1,
    summary: '标准工时、补卡规则与弹性考勤适用范围。'
  },
  {
    id: 'ld7',
    libraryId: 'tech',
    title: '内部 API 网关接入指南',
    category: 'manual',
    pages: 19,
    updatedAt: '2026-05-10',
    views: 97,
    page: 2,
    summary: '鉴权方式、请求签名与错误码速查。'
  },
  {
    id: 'ld8',
    libraryId: 'tech',
    title: '线上故障排查 FAQ',
    category: 'faq',
    pages: 9,
    updatedAt: '2026-04-26',
    views: 121,
    page: 1,
    summary: '常见超时、鉴权失败与流量限流处理建议。'
  },
  {
    id: 'ld9',
    libraryId: 'support',
    title: '售后常见故障排查手册',
    category: 'manual',
    pages: 42,
    updatedAt: '2026-03-28',
    views: 156,
    page: 3,
    summary: '开机异常、联网失败与保修流程指引。'
  },
  {
    id: 'ld10',
    libraryId: 'support',
    title: '售后服务话术 FAQ',
    category: 'faq',
    pages: 7,
    updatedAt: '2026-03-01',
    views: 88,
    page: 1,
    summary: '标准应答话术与升级处理路径。'
  }
]

const categoryTabs: Array<{ id: DocCategory; label: string }> = [
  { id: 'all', label: '全部' },
  { id: 'faq', label: 'FAQ' },
  { id: 'policy', label: '制度' },
  { id: 'manual', label: '手册' }
]

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
  const [activeLibraryId, setActiveLibraryId] = useState<LibraryId | null>(null)
  const [category, setCategory] = useState<DocCategory>('all')
  const [query, setQuery] = useState('')

  const activeLibrary = libraries.find((item) => item.id === activeLibraryId) ?? null

  const docs = useMemo(() => {
    if (!activeLibraryId) return []
    const q = query.trim().toLowerCase()
    return libraryDocs.filter((doc) => {
      if (doc.libraryId !== activeLibraryId) return false
      if (category !== 'all' && doc.category !== category) return false
      if (!q) return true
      return (
        doc.title.toLowerCase().includes(q) ||
        doc.summary.toLowerCase().includes(q)
      )
    })
  }, [activeLibraryId, category, query])

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
                placeholder="搜索本库文档…"
                aria-label="搜索本库文档"
              />
            </div>
          </div>

          <div className="kbDocMeta">共 {docs.length} 份可见文档 · 只读浏览</div>

          {docs.length > 0 ? (
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
                            excerpt: doc.summary
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
          ) : (
            <div className="kbEmpty">
              <div className="kbEmptyOrb" aria-hidden="true" />
              <h3>本分类下暂无可见文档</h3>
              <p>可以切换分类，或直接去 AI 问答检索。</p>
              <button type="button" className="kbPrimaryBtn" onClick={() => onAsk()}>
                <Sparkles size={16} />
                去 AI 问答
              </button>
            </div>
          )}
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
        <div className="kbReadonlyHint">无创建知识库 / 上传文档 / 权限配置入口</div>
      </main>
    </div>
  )
}

export default KnowledgeBrowse
