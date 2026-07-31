import { useEffect, useState } from 'react'
import { CircleHelp, MessageSquare, Search, Sparkles } from 'lucide-react'
import { listFaqs, type HelpFaq } from './api'

const FALLBACK_FAQS: HelpFaq[] = [
  {
    question: '如何开始提问？',
    answer: '打开「智能问答」页，在底部输入框直接提问。也可先选择知识库范围，让回答更聚焦。',
  },
  {
    question: '答案里的来源是什么？',
    answer: '每个回答会附带来源文档与原文摘录。点击「查看原文」可跳转到对应页并高亮关键段落。',
  },
  {
    question: '为什么有些知识搜不到？',
    answer: '平台只会检索你有权限访问的知识库。可尝试换个问法，或切换到「全部知识库」。',
  },
  {
    question: '如何收藏常用内容？',
    answer: '在原文阅读页可收藏文档；对优质回答也可收藏。之后在「我的收藏」里快速查阅。',
  },
  {
    question: '反馈“没帮助”会怎样？',
    answer: '你的反馈会用于优化检索与回答质量，不会影响个人绩效，也不会公开你的身份信息。',
  },
]

type HelpPageProps = {
  onBack: () => void
  onAsk: () => void
  onOpenSearch: () => void
}

function HelpPage({ onBack, onAsk, onOpenSearch }: HelpPageProps) {
  const [faqs, setFaqs] = useState<HelpFaq[]>(FALLBACK_FAQS)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let alive = true
    ;(async () => {
      try {
        const list = await listFaqs('zh-CN')
        if (alive && Array.isArray(list) && list.length) setFaqs(list)
      } catch (err) {
        if (alive) {
          const msg = err instanceof Error ? err.message : ''
          setError(msg === '系统错误' ? '暂无云端 FAQ，已显示本地说明' : msg || 'FAQ 加载失败，已显示本地文案')
        }
      }
    })()
    return () => {
      alive = false
    }
  }, [])

  return (
    <div className="hpPage">
      <header className="hpHeader">
        <button type="button" className="hpGhostBtn" onClick={onBack}>
          返回个人中心
        </button>
        <div>
          <h1>使用帮助 / 常见问题</h1>
          <p>快速了解如何提问、查看来源、收藏与反馈</p>
        </div>
      </header>

      <main className="hpBody">
        <section className="hpQuick">
          <button type="button" className="hpQuickCard" onClick={onAsk}>
            <Sparkles size={18} />
            <div>
              <strong>去提问</strong>
              <span>打开智能问答</span>
            </div>
          </button>
          <button type="button" className="hpQuickCard" onClick={onOpenSearch}>
            <Search size={18} />
            <div>
              <strong>去搜索</strong>
              <span>浏览企业知识</span>
            </div>
          </button>
          <div className="hpQuickCard hpQuickCardStatic">
            <MessageSquare size={18} />
            <div>
              <strong>追问技巧</strong>
              <span>补充场景与时间范围</span>
            </div>
          </div>
        </section>

        <section className="hpFaq">
          <div className="hpFaqTitle">
            <CircleHelp size={18} />
            常见问题
          </div>
          {error ? <p className="hpHint">{error}</p> : null}
          <div className="hpFaqList">
            {faqs.map((item) => (
              <article key={item.id ?? item.question} className="hpFaqItem">
                <h3>{item.question}</h3>
                <p>{item.answer}</p>
              </article>
            ))}
          </div>
        </section>
      </main>
    </div>
  )
}

export default HelpPage
