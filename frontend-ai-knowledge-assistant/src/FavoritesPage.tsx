import { useState } from 'react'
import { BookmarkMinus, BookOpenText, ChevronDown, ChevronUp, MessageCircleQuestion } from 'lucide-react'
import type { SourceDoc } from './DocumentReader'

type FavDoc = {
  id: string
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

const initialDocs: FavDoc[] = [
  {
    id: 'fd1',
    title: '《员工手册 2026 版》',
    knowledgeBase: '人事制度库',
    savedAt: '2026-07-28 15:10',
    page: 23
  },
  {
    id: 'fd2',
    title: '《2026 年度报销管理制度》',
    knowledgeBase: '人事制度库',
    savedAt: '2026-07-26 10:22',
    page: 5
  }
]

const initialAnswers: FavAnswer[] = [
  {
    id: 'fa1',
    summary: '入职满 1 年不满 10 年年休假 5 天；不满 1 年不享受带薪年假。',
    source: '《员工手册 2026 版》· 第 23 页',
    savedAt: '2026-07-28 14:40',
    context: [
      '问：请问公司年假有几天？入职不满一年怎么算？',
      '答：根据公司规定，员工年假天数与工龄相关…'
    ],
    topic: '年假规定'
  },
  {
    id: 'fa2',
    summary: '报销需在费用发生后 30 日内提交，并附齐合规票据。',
    source: '《2026 年度报销管理制度》· 第 5 页',
    savedAt: '2026-07-25 09:18',
    context: [
      '问：报销最晚什么时候提交？',
      '答：需在费用发生后 30 日内提交…'
    ],
    topic: '报销时效'
  }
]

type FavoritesPageProps = {
  onBack: () => void
  onRead: (doc: SourceDoc) => void
  onAsk: (prompt: string) => void
}

function FavoritesPage({ onBack, onRead, onAsk }: FavoritesPageProps) {
  const [tab, setTab] = useState<'docs' | 'answers'>('docs')
  const [docs, setDocs] = useState(initialDocs)
  const [answers, setAnswers] = useState(initialAnswers)
  const [expanded, setExpanded] = useState<string | null>(null)

  const empty = tab === 'docs' ? docs.length === 0 : answers.length === 0

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
        {empty ? (
          <div className="fvEmpty">
            <div className="fvEmptyOrb" aria-hidden="true" />
            <h3>收藏常用文档和优质回答，方便下次快速查阅</h3>
            <p>在阅读原文或优质回答时，点击收藏即可加入这里。</p>
          </div>
        ) : tab === 'docs' ? (
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
                          id: doc.id,
                          title: doc.title,
                          page: doc.page,
                          knowledgeBase: doc.knowledgeBase,
                          excerpt: doc.title
                        })
                      }
                    >
                      阅读
                    </button>
                    <button
                      type="button"
                      className="fvGhostBtn"
                      onClick={() => setDocs((prev) => prev.filter((item) => item.id !== doc.id))}
                    >
                      <BookmarkMinus size={14} />
                      取消收藏
                    </button>
                  </div>
                </div>
              </article>
            ))}
          </div>
        ) : (
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
                        onClick={() =>
                          setAnswers((prev) => prev.filter((item) => item.id !== answer.id))
                        }
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
        )}
      </main>
    </div>
  )
}

export default FavoritesPage
