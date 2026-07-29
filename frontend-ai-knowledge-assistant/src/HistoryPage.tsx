import { useMemo, useState } from 'react'
import { MessageSquarePlus, Search, Star, Trash2 } from 'lucide-react'

type HistoryItem = {
  id: string
  title: string
  lastQuestion: string
  scope: string
  time: string
  group: '今天' | '昨天' | '本周' | '更早'
  rating?: number
}

const initialHistory: HistoryItem[] = [
  {
    id: 'h1',
    title: '关于年假和报销的咨询',
    lastQuestion: '那入职不满一年呢？',
    scope: '人事制度库',
    time: '2026-07-28 14:32',
    group: '今天',
    rating: 5
  },
  {
    id: 'h2',
    title: '报销流程咨询',
    lastQuestion: '差旅发票需要附行程单吗？',
    scope: '人事制度库',
    time: '2026-07-28 11:20',
    group: '今天',
    rating: 4
  },
  {
    id: 'h3',
    title: 'A 产品 vs B 产品对比',
    lastQuestion: '两款产品的售后周期分别是多久？',
    scope: '产品知识库',
    time: '2026-07-27 18:06',
    group: '昨天'
  },
  {
    id: 'h4',
    title: '新人 onboarding 材料',
    lastQuestion: '入职第一周要完成哪些培训？',
    scope: '人事制度库',
    time: '2026-07-24 09:41',
    group: '本周',
    rating: 5
  },
  {
    id: 'h5',
    title: 'API 鉴权失败排查',
    lastQuestion: '401 和 403 怎么区分？',
    scope: '技术文档库',
    time: '2026-07-10 16:18',
    group: '更早',
    rating: 3
  }
]

const groups: HistoryItem['group'][] = ['今天', '昨天', '本周', '更早']

type HistoryPageProps = {
  onContinue: (title: string) => void
  onAskFirst: () => void
  onBack: () => void
}

function HistoryPage({ onContinue, onAskFirst, onBack }: HistoryPageProps) {
  const [query, setQuery] = useState('')
  const [items, setItems] = useState(initialHistory)
  const [selected, setSelected] = useState<string[]>([])
  const [batchMode, setBatchMode] = useState(false)

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return items
    return items.filter(
      (item) =>
        item.title.toLowerCase().includes(q) ||
        item.lastQuestion.toLowerCase().includes(q) ||
        item.scope.toLowerCase().includes(q)
    )
  }, [items, query])

  function toggleSelect(id: string) {
    setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
  }

  function deleteOne(id: string) {
    setItems((prev) => prev.filter((item) => item.id !== id))
    setSelected((prev) => prev.filter((x) => x !== id))
  }

  function deleteSelected() {
    setItems((prev) => prev.filter((item) => !selected.includes(item.id)))
    setSelected([])
    setBatchMode(false)
  }

  return (
    <div className="hiPage">
      <header className="hiHeader">
        <div className="hiHeaderTop">
          <div>
            <h1 className="hiTitle">我的对话</h1>
            <p className="hiSubtitle">按时间回顾你问过的问题</p>
          </div>
          <div className="hiHeaderActions">
            <button type="button" className="hiGhostBtn" onClick={onBack}>
              返回问答
            </button>
            <button
              type="button"
              className={`hiGhostBtn ${batchMode ? 'hiGhostBtnActive' : ''}`}
              onClick={() => {
                setBatchMode((v) => !v)
                setSelected([])
              }}
            >
              {batchMode ? '完成' : '批量管理'}
            </button>
          </div>
        </div>

        <div className="hiSearchBox">
          <Search size={16} />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="搜索历史对话…"
            aria-label="搜索历史对话"
          />
        </div>

        {batchMode && selected.length > 0 ? (
          <div className="hiBatchBar">
            <span>已选 {selected.length} 条</span>
            <button type="button" className="hiDangerBtn" onClick={deleteSelected}>
              <Trash2 size={14} />
              批量删除
            </button>
          </div>
        ) : null}
      </header>

      <main className="hiBody">
        {filtered.length === 0 ? (
          <div className="hiEmpty">
            <div className="hiEmptyOrb" aria-hidden="true" />
            <h3>还没有对话记录，去提第一个问题吧</h3>
            <p>问答会基于你有权限的企业知识，并附上来源。</p>
            <button type="button" className="hiPrimaryBtn" onClick={onAskFirst}>
              <MessageSquarePlus size={16} />
              开始提问
            </button>
          </div>
        ) : (
          groups.map((group) => {
            const list = filtered.filter((item) => item.group === group)
            if (list.length === 0) return null
            return (
              <section key={group} className="hiGroup">
                <div className="hiGroupTitle">{group}</div>
                <div className="hiList">
                  {list.map((item) => (
                    <article key={item.id} className="hiCard hiSwipeable">
                      {batchMode ? (
                        <label className="hiCheck">
                          <input
                            type="checkbox"
                            checked={selected.includes(item.id)}
                            onChange={() => toggleSelect(item.id)}
                          />
                        </label>
                      ) : null}

                      <div className="hiCardMain">
                        <div className="hiCardTop">
                          <h2>{item.title}</h2>
                          <span className="hiScope">{item.scope}</span>
                        </div>
                        <p className="hiPreview">预览：{item.lastQuestion}</p>
                        <div className="hiMeta">
                          <span>{item.time}</span>
                          {item.rating ? (
                            <span className="hiStars" aria-label={`评分 ${item.rating} 星`}>
                              {Array.from({ length: item.rating }).map((_, i) => (
                                <Star key={i} size={12} fill="currentColor" />
                              ))}
                            </span>
                          ) : (
                            <span className="hiNoRate">未评分</span>
                          )}
                        </div>
                        <div className="hiActions">
                          <button
                            type="button"
                            className="hiPrimaryBtn"
                            onClick={() => onContinue(item.title)}
                          >
                            继续对话
                          </button>
                          <button
                            type="button"
                            className="hiGhostBtn hiDeleteBtn"
                            onClick={() => deleteOne(item.id)}
                          >
                            <Trash2 size={14} />
                            删除
                          </button>
                        </div>
                      </div>

                      <button
                        type="button"
                        className="hiSwipeDelete"
                        onClick={() => deleteOne(item.id)}
                        aria-label="左滑删除"
                      >
                        删除
                      </button>
                    </article>
                  ))}
                </div>
              </section>
            )
          })
        )}
      </main>
    </div>
  )
}

export default HistoryPage
