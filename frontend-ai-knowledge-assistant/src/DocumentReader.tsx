import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  ArrowLeft,
  Bookmark,
  ChevronLeft,
  ChevronRight,
  Download,
  MessageCircle,
  Send,
  Share2,
  Sparkles,
  ZoomIn,
  ZoomOut,
} from 'lucide-react'

export type SourceDoc = {
  id: string
  title: string
  page: number
  knowledgeBase: string
  excerpt: string
}

type ContextTab = 'summary' | 'related' | 'ask'

type ZoomMode = 'fit' | '100' | 'zoom'

const relatedSnippets = [
  {
    id: 'r1',
    page: 22,
    title: '休假申请流程',
    text: '员工应提前至少 3 个工作日在 OA 提交休假申请，经直属主管审批后生效。'
  },
  {
    id: 'r2',
    page: 23,
    title: '年假天数对照表',
    text: '入职满 1 年不满 10 年：年休假 5 天；满 10 年不满 20 年：年休假 10 天。',
    active: true
  },
  {
    id: 'r3',
    page: 24,
    title: '未休年假处理',
    text: '当年未使用完的年假，原则上不结转至下一年度；特殊情况需经 HR 备案。'
  },
  {
    id: 'r4',
    page: 31,
    title: '病假与事假说明',
    text: '病假需提供医疗机构证明；事假不计薪，累计超过规定天数将影响考勤评定。'
  }
]

type DocumentReaderProps = {
  doc: SourceDoc
  onBack: () => void
}

function DocumentReader({ doc, onBack }: DocumentReaderProps) {
  const [page, setPage] = useState(doc.page)
  const [zoom, setZoom] = useState<ZoomMode>('fit')
  const [tab, setTab] = useState<ContextTab>('summary')
  const [bookmarked, setBookmarked] = useState(false)
  const [askText, setAskText] = useState('')
  const [askHint, setAskHint] = useState<string | null>(null)
  const [mobileAskOpen, setMobileAskOpen] = useState(false)
  const highlightRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    setPage(doc.page)
  }, [doc.page])

  useEffect(() => {
    if (page !== 23) return
    const timer = window.setTimeout(() => {
      highlightRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }, 80)
    return () => window.clearTimeout(timer)
  }, [page, zoom])

  function onAskSubmit(e: FormEvent) {
    e.preventDefault()
    if (!askText.trim()) return
    setAskHint(`已针对「${doc.title}」发起提问（mock）`)
    setAskText('')
    setMobileAskOpen(false)
  }

  const zoomLabel = zoom === 'fit' ? '适合宽度' : zoom === '100' ? '100%' : '放大'

  return (
    <div className="docPage">
      <header className="docTopbar">
        <div className="docTopLeft">
          <button className="docBackBtn" type="button" onClick={onBack}>
            <ArrowLeft size={16} />
            <span>返回对话</span>
          </button>

          <div className="docTitleBlock">
            <h1 className="docTitle">{doc.title}</h1>
            <span className="docKbTag">{doc.knowledgeBase}</span>
          </div>
        </div>

        <div className="docTopActions">
          <button
            type="button"
            className={`docActionBtn ${bookmarked ? 'docActionBtnActive' : ''}`}
            onClick={() => setBookmarked((v) => !v)}
          >
            <Bookmark size={16} />
            <span>收藏</span>
          </button>
          <button
            type="button"
            className="docActionBtn"
            onClick={() => setAskHint('分享链接已复制（mock）')}
          >
            <Share2 size={16} />
            <span>分享</span>
          </button>
          <button
            type="button"
            className="docActionBtn"
            onClick={() => setAskHint('开始下载文档（mock）')}
          >
            <Download size={16} />
            <span>下载</span>
          </button>
        </div>
      </header>

      <div className="docBody">
        <section className="docReaderPane" aria-label="文档阅读器">
          <div className="docReaderToolbar">
            <div className="docZoomGroup" role="group" aria-label="缩放">
              <button
                type="button"
                className={`docZoomBtn ${zoom === 'fit' ? 'docZoomBtnActive' : ''}`}
                onClick={() => setZoom('fit')}
              >
                适合宽度
              </button>
              <button
                type="button"
                className={`docZoomBtn ${zoom === '100' ? 'docZoomBtnActive' : ''}`}
                onClick={() => setZoom('100')}
              >
                100%
              </button>
              <button
                type="button"
                className={`docZoomBtn ${zoom === 'zoom' ? 'docZoomBtnActive' : ''}`}
                onClick={() => setZoom('zoom')}
              >
                <ZoomIn size={14} />
                放大
              </button>
            </div>
            <div className="docZoomHint">
              <ZoomOut size={14} />
              当前：{zoomLabel}
            </div>
          </div>

          <div className={`docCanvasWrap docZoom-${zoom}`}>
            <article className="docPaper" aria-label={`第 ${page} 页`}>
              <div className="docPaperHeader">
                <span>企业内网文档 · PDF 预览</span>
                <span>第 {page} 页 / 共 86 页</span>
              </div>

              {page === 23 ? (
                <>
                  <h2 className="docPaperH2">第四章 · 休假制度</h2>
                  <h3 className="docPaperH3">4.2 带薪年休假</h3>
                  <p className="docPaperP">
                    公司依据国家相关法律法规，结合员工工龄情况，提供带薪年休假。年假天数按以下标准执行：
                  </p>

                  <div ref={highlightRef} className="docHighlightBlock">
                    <p>
                      <mark>
                        员工年假规定：入职满1年不满10年，年休假5天；入职满10年不满20年，年休假10天；入职不满1年，不享受带薪年假。
                      </mark>
                    </p>
                    <p className="docHighlightNote">来自问答引用 · 自动定位到本段</p>
                  </div>

                  <p className="docPaperP">
                    符合年假条件的员工，可在 OA 系统提交年假申请，由直属主管审批后生效。审批通过后，员工应在休假前完成工作交接。
                  </p>
                  <p className="docPaperP">
                    HR 将于每年初同步当年可休年假余额；如遇法定节假日与年假重叠，不重复计算休假天数。
                  </p>
                </>
              ) : (
                <>
                  <h2 className="docPaperH2">第四章 · 休假制度</h2>
                  <h3 className="docPaperH3">
                    {page === 22 ? '4.1 休假总则' : page === 24 ? '4.3 年假结转说明' : `第 ${page} 页内容`}
                  </h3>
                  <p className="docPaperP">
                    {page === 22
                      ? '本章规定适用于公司全体正式员工。休假申请应提前提交，并确保不影响所在团队关键业务连续性。'
                      : page === 24
                        ? '当年未使用完的年假，原则上不结转至下一年度；特殊情况需经 HR 备案，并在下一年度第一季度内使用完毕。'
                        : '此处为文档预览占位内容。从问答跳转时会自动定位到来源页并高亮关键段落。'}
                  </p>
                  <p className="docPaperP">
                    点击右侧「相关片段」可在同文档内快速跳转；也可使用「同文档问答」仅针对本文档继续追问。
                  </p>
                </>
              )}
            </article>
          </div>

          <div className="docPager">
            <button
              type="button"
              className="docPagerBtn"
              disabled={page <= 1}
              onClick={() => setPage((p) => Math.max(1, p - 1))}
            >
              <ChevronLeft size={16} />
              <span>{page - 1 > 0 ? page - 1 : '—'}</span>
            </button>
            <div className="docPagerCurrent">{page}</div>
            <button
              type="button"
              className="docPagerBtn"
              disabled={page >= 86}
              onClick={() => setPage((p) => Math.min(86, p + 1))}
            >
              <span>{page + 1 <= 86 ? page + 1 : '—'}</span>
              <ChevronRight size={16} />
            </button>
          </div>
        </section>

        <aside className="docContextPane" aria-label="上下文面板">
          <div className="docTabs" role="tablist">
            {(
              [
                { id: 'summary', label: 'AI 摘要' },
                { id: 'related', label: '相关片段' },
                { id: 'ask', label: '同文档问答' }
              ] as const
            ).map((item) => (
              <button
                key={item.id}
                type="button"
                role="tab"
                aria-selected={tab === item.id}
                className={`docTab ${tab === item.id ? 'docTabActive' : ''}`}
                onClick={() => setTab(item.id)}
              >
                {item.label}
              </button>
            ))}
          </div>

          <div className="docTabBody">
            {tab === 'summary' ? (
              <div className="docSummaryCard">
                <div className="docSummaryHead">
                  <span className="docAiOrb" aria-hidden="true" />
                  <span>本段内容概述</span>
                </div>
                <p>
                  说明员工年假天数与工龄的对应关系：入职满 1 年不满 10 年可休 5
                  天，满 10 年不满 20 年可休 10 天；入职不满 1
                  年暂不享受带薪年假。符合条件者可在 OA 提交申请。
                </p>
                <div className="docSummaryMeta">基于第 {page} 页 · 人事制度库</div>
              </div>
            ) : null}

            {tab === 'related' ? (
              <div className="docRelatedList">
                {relatedSnippets.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    className={`docRelatedItem ${item.active && page === 23 ? 'docRelatedItemActive' : ''}`}
                    onClick={() => setPage(item.page)}
                  >
                    <div className="docRelatedTop">
                      <span className="docRelatedTitle">{item.title}</span>
                      <span className="docRelatedPage">第 {item.page} 页</span>
                    </div>
                    <p>{item.text}</p>
                  </button>
                ))}
              </div>
            ) : null}

            {tab === 'ask' ? (
              <div className="docAskPanel">
                <div className="docAskIntro">
                  <Sparkles size={16} />
                  <span>仅检索本文档内容，回答更聚焦</span>
                </div>
                <form className="docAskForm" onSubmit={onAskSubmit}>
                  <textarea
                    className="docAskInput"
                    rows={4}
                    placeholder="关于这份文档继续提问…"
                    value={askText}
                    onChange={(e) => setAskText(e.target.value)}
                    aria-label="同文档问答输入"
                  />
                  <button className="docAskSend" type="submit" disabled={!askText.trim()}>
                    <Send size={16} />
                    <span>提问</span>
                  </button>
                </form>
                {askHint ? (
                  <div className="docAskHint" role="status">
                    {askHint}
                  </div>
                ) : null}
              </div>
            ) : null}
          </div>
        </aside>
      </div>

      {askHint && tab !== 'ask' ? (
        <div className="docToast" role="status">
          {askHint}
        </div>
      ) : null}

      <button
        type="button"
        className="docMobileAskFab"
        onClick={() => {
          setTab('ask')
          setMobileAskOpen(true)
        }}
      >
        <MessageCircle size={18} />
        <span>关于本文档提问</span>
      </button>

      {mobileAskOpen ? (
        <div className="docMobileAskSheet" role="dialog" aria-label="同文档问答">
          <div className="docMobileAskSheetInner">
            <div className="docMobileAskSheetHead">
              <span>同文档问答</span>
              <button type="button" onClick={() => setMobileAskOpen(false)}>
                关闭
              </button>
            </div>
            <form className="docAskForm" onSubmit={onAskSubmit}>
              <textarea
                className="docAskInput"
                rows={4}
                placeholder="关于这份文档继续提问…"
                value={askText}
                onChange={(e) => setAskText(e.target.value)}
              />
              <button className="docAskSend" type="submit" disabled={!askText.trim()}>
                <Send size={16} />
                <span>提问</span>
              </button>
            </form>
          </div>
        </div>
      ) : null}
    </div>
  )
}

export default DocumentReader
