import { useEffect, useState } from 'react'
import { BookmarkMinus, BookOpenText, ChevronDown, ChevronUp, MessageCircleQuestion } from 'lucide-react'
import {
  deleteFavoriteAnswer,
  deleteFavoriteDocument,
  listFavoriteAnswers,
  listFavoriteDocuments,
  type FavAnswerApi,
  type FavDocApi,
} from './api'
import type { SourceDoc } from './DocumentReader'

type FavDoc = {
  id: string
  docId: string
  title: string
  knowledgeBase: string
  savedAt: string
  page: number
}

type FavAnswer = {
  id: string
  summary: string
  source: string
  savedAt: string
  context: string[]
  topic: string
}

function mapDoc(item: FavDocApi): FavDoc {
  return {
    id: String(item.id),
    docId: String(item.docId),
    title: item.title || '未命名文档',
    knowledgeBase: item.knowledgeBase || item.category || '',
    savedAt: item.savedAt || '',
    page: item.page ?? 1,
  }
}

function mapAnswer(item: FavAnswerApi): FavAnswer {
  return {
    id: String(item.id),
    summary: item.summary || '',
    source: item.source || '知识库回答',
    savedAt: item.savedAt || '',
    context: Array.isArray(item.context) ? item.context : [],
    topic: item.topic || '收藏回答',
  }
}

type FavoritesPageProps = {
  onBack: () => void
  onRead: (doc: SourceDoc) => void
  onAsk: (prompt: string) => void
}

function FavoritesPage({ onBack, onRead, onAsk }: FavoritesPageProps) {
  const [tab, setTab] = useState<'docs' | 'answers'>('docs')
  const [docs, setDocs] = useState<FavDoc[]>([])
  const [answers, setAnswers] = useState<FavAnswer[]>([])
  const [expanded, setExpanded] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function refresh() {
    setLoading(true)
    setError(null)
    try {
      const [d, a] = await Promise.all([listFavoriteDocuments(), listFavoriteAnswers()])
      setDocs((d || []).map(mapDoc))
      setAnswers((a || []).map(mapAnswer))
    } catch (err) {
      const msg = err instanceof Error ? err.message : '加载收藏失败'
      // 无数据场景走空态文案，不展示「系统错误」
      if (msg !== '系统错误') setError(msg)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
  }, [])

  const empty = tab === 'docs' ? docs.length === 0 : answers.length === 0

  async function removeDoc(doc: FavDoc) {
    try {
      await deleteFavoriteDocument(Number(doc.docId))
      setDocs((prev) => prev.filter((item) => item.id !== doc.id))
    } catch (err) {
      setError(err instanceof Error ? err.message : '取消收藏失败')
    }
  }

  async function removeAnswer(id: string) {
    try {
      await deleteFavoriteAnswer(Number(id))
      setAnswers((prev) => prev.filter((item) => item.id !== id))
    } catch (err) {
      setError(err instanceof Error ? err.message : '取消收藏失败')
    }
  }

  return (
    <div className="fvPage">
      <header className="fvHeader">
        <div className="fvHeaderTop">
          <div>
            <h1>我的收藏</h1>
            <p>常用文档和优质回答，方便下次快速查阅</p>
          </div>
          <button type="button" className="fvGhostBtn" onClick={onBack}>
            返回
          </button>
        </div>

        <div className="fvTabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'docs'}
            className={`fvTab ${tab === 'docs' ? 'fvTabActive' : ''}`}
            onClick={() => setTab('docs')}
          >
            收藏的文档
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'answers'}
            className={`fvTab ${tab === 'answers' ? 'fvTabActive' : ''}`}
            onClick={() => setTab('answers')}
          >
            收藏的回答
          </button>
        </div>
      </header>

      <main className="fvBody">
        {loading ? <div className="fvMeta">加载中…</div> : null}
        {error ? <div className="fvMeta">{error}</div> : null}
        {!loading && empty ? (
          <div className="fvEmpty">
            <div className="fvEmptyOrb" aria-hidden="true" />
            <h3>收藏常用文档和优质回答，方便下次快速查阅</h3>
            <p>在阅读原文或优质回答时，点击收藏即可加入这里。</p>
          </div>
        ) : null}
        {!loading && !empty && tab === 'docs' ? (
          <div className="fvList">
            {docs.map((doc) => (
              <article key={doc.id} className="fvCard">
                <div className="fvDocIcon" aria-hidden="true">
                  <BookOpenText size={18} />
                </div>
                <div className="fvCardMain">
                  <h2>{doc.title}</h2>
                  <div className="fvMeta">
                    {doc.knowledgeBase} · 收藏于 {doc.savedAt}
                  </div>
                  <div className="fvActions">
                    <button
                      type="button"
                      className="fvPrimaryBtn"
                      onClick={() =>
                        onRead({
                          id: doc.docId,
                          title: doc.title,
                          page: doc.page,
                          knowledgeBase: doc.knowledgeBase,
                          excerpt: doc.title,
                        })
                      }
                    >
                      阅读
                    </button>
                    <button type="button" className="fvGhostBtn" onClick={() => removeDoc(doc)}>
                      <BookmarkMinus size={14} />
                      取消收藏
                    </button>
                  </div>
                </div>
              </article>
            ))}
          </div>
        ) : null}
        {!loading && !empty && tab === 'answers' ? (
          <div className="fvList">
            {answers.map((answer) => {
              const open = expanded === answer.id
              return (
                <article key={answer.id} className="fvCard fvAnswerCard">
                  <div className="fvCardMain">
                    <h2>{answer.summary}</h2>
                    <div className="fvMeta">
                      来源：{answer.source} · 收藏于 {answer.savedAt}
                    </div>

                    <button
                      type="button"
                      className="fvExpandBtn"
                      onClick={() => setExpanded(open ? null : answer.id)}
                    >
                      {open ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                      {open ? '收起对话上下文' : '展开完整对话上下文'}
                    </button>

                    {open ? (
                      <div className="fvContext">
                        {answer.context.map((line) => (
                          <p key={line}>{line}</p>
                        ))}
                      </div>
                    ) : null}

                    <div className="fvActions">
                      <button
                        type="button"
                        className="fvPrimaryBtn"
                        onClick={() => onAsk(`继续基于「${answer.topic}」提问：`)}
                      >
                        <MessageCircleQuestion size={14} />
                        继续基于此话题提问
                      </button>
                      <button
                        type="button"
                        className="fvGhostBtn"
                        onClick={() => removeAnswer(answer.id)}
                      >
                        <BookmarkMinus size={14} />
                        取消收藏
                      </button>
                    </div>
                  </div>
                </article>
              )
            })}
          </div>
        ) : null}
      </main>
    </div>
  )
}

export default FavoritesPage
