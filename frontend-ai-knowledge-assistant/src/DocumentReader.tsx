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
import {
  downloadDocument,
  fetchDocumentBlobUrl,
  getDocumentMeta,
  shareDocument,
  viewDocument,
  type DocumentMeta,
} from './api'

export type SourceDoc = {
  id: string
  title: string
  page: number
  knowledgeBase: string
  excerpt: string
}

type ContextTab = 'summary' | 'related' | 'ask'
type ZoomMode = 'fit' | '100' | 'zoom'

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
  const [meta, setMeta] = useState<DocumentMeta | null>(null)
  const [fileUrl, setFileUrl] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const highlightRef = useRef<HTMLDivElement | null>(null)

  const totalPages = meta?.pages || 86
  const title = meta?.title || doc.title
  const knowledgeBase = meta?.knowledgeBase || doc.knowledgeBase

  useEffect(() => {
    setPage(doc.page)
  }, [doc.page, doc.id])

  useEffect(() => {
    let revoked: string | null = null
    let alive = true
    ;(async () => {
      setLoadError(null)
      setFileUrl(null)
      try {
        const m = await getDocumentMeta(doc.id)
        if (!alive) return
        setMeta(m)
        setBookmarked(Boolean(m.favorited))
        await viewDocument(doc.id, { pageNo: doc.page, eventType: 'OPEN_SOURCE' })
        try {
          const url = await fetchDocumentBlobUrl(doc.id)
          if (!alive) {
            URL.revokeObjectURL(url)
            return
          }
          revoked = url
          setFileUrl(url)
        } catch {
          // 无文件时保留文本预览兜底
        }
      } catch (err) {
        if (alive) setLoadError(err instanceof Error ? err.message : '加载文档失败')
      }
    })()
    return () => {
      alive = false
      if (revoked) URL.revokeObjectURL(revoked)
    }
  }, [doc.id, doc.page])

  useEffect(() => {
    if (!doc.excerpt) return
    const timer = window.setTimeout(() => {
      highlightRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }, 80)
    return () => window.clearTimeout(timer)
  }, [page, zoom, doc.excerpt])

  function onAskSubmit(e: FormEvent) {
    e.preventDefault()
    if (!askText.trim()) return
    setAskHint(`同文档问答将在后续批次接入：${askText.trim()}`)
    setAskText('')
    setMobileAskOpen(false)
  }

  async function onShare() {
    try {
      const data = await shareDocument(doc.id)
      if (data.shareUrl && navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(data.shareUrl)
        setAskHint(`分享链接已复制（${data.expireHours}h 有效）`)
      } else {
        setAskHint(data.shareUrl || '已生成分享链接')
      }
    } catch (err) {
      setAskHint(err instanceof Error ? err.message : '分享失败')
    }
  }

  async function onDownload() {
    try {
      await downloadDocument(doc.id, `${title}.pdf`)
      setAskHint('开始下载文档')
    } catch (err) {
      setAskHint(err instanceof Error ? err.message : '下载失败')
    }
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
            <h1 className="docTitle">{title}</h1>
            <span className="docKbTag">{knowledgeBase}</span>
            {meta?.views != null ? <span className="docKbTag">浏览 {meta.views}</span> : null}
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
          <button type="button" className="docActionBtn" onClick={onShare}>
            <Share2 size={16} />
            <span>分享</span>
          </button>
          <button type="button" className="docActionBtn" onClick={onDownload}>
            <Download size={16} />
            <span>下载</span>
          </button>
        </div>
      </header>

      {loadError ? (
        <div className="docToast" role="alert">
          {loadError}
        </div>
      ) : null}

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
            {fileUrl ? (
              <iframe
                title={title}
                src={`${fileUrl}#page=${page}`}
                style={{ width: '100%', height: '70vh', border: 'none', background: '#fff' }}
              />
            ) : (
              <article className="docPaper" aria-label={`第 ${page} 页`}>
                <div className="docPaperHeader">
                  <span>企业内网文档 · 文本预览</span>
                  <span>
                    第 {page} 页 / 共 {totalPages} 页
                  </span>
                </div>
                <h2 className="docPaperH2">{title}</h2>
                <div ref={highlightRef} className="docHighlightBlock">
                  <p>
                    <mark>{doc.excerpt || meta?.summary || '暂无摘录，可下载 PDF 查看原文。'}</mark>
                  </p>
                  <p className="docHighlightNote">来自问答引用 · 第 {page} 页</p>
                </div>
              </article>
            )}
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
              disabled={page >= totalPages}
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
            >
              <span>{page + 1 <= totalPages ? page + 1 : '—'}</span>
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
                { id: 'ask', label: '同文档问答' },
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
                  <span>文档摘要</span>
                </div>
                <p>{meta?.summary || doc.excerpt || '摘要将在入库完成后展示；页级 AI 摘要见后续批次。'}</p>
                <div className="docSummaryMeta">
                  基于第 {page} 页 · {knowledgeBase}
                </div>
              </div>
            ) : null}

            {tab === 'related' ? (
              <div className="docRelatedList">
                <p className="docAskHint">相关片段接口将在 B10 接通，当前展示引用来源摘录。</p>
                <button type="button" className="docRelatedItem docRelatedItemActive">
                  <div className="docRelatedTop">
                    <span className="docRelatedTitle">{title}</span>
                    <span className="docRelatedPage">第 {page} 页</span>
                  </div>
                  <p>{doc.excerpt}</p>
                </button>
              </div>
            ) : null}

            {tab === 'ask' ? (
              <div className="docAskPanel">
                <div className="docAskIntro">
                  <Sparkles size={16} />
                  <span>仅检索本文档内容（B10）</span>
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
