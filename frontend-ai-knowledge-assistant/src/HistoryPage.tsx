import { useEffect, useState } from 'react'
import { MessageSquarePlus, Search, Star, Trash2 } from 'lucide-react'
import {
  batchDeleteSessions,
  deleteSession,
  listHistory,
  type HistoryItemApi,
} from './api'

type HistoryItem = {
  id: string
  title: string
  lastQuestion: string
  scope: string
  time: string
  group: '今天' | '昨天' | '本周' | '更早'
  rating?: number
}

const scopeLabels: Record<string, string> = {
  all: '全部知识库',
  product: '产品知识库',
  hr: '人事制度库',
  tech: '技术文档库',
  support: '售后 FAQ',
}

const groups: HistoryItem['group'][] = ['今天', '昨天', '本周', '更早']

function mapItem(item: HistoryItemApi): HistoryItem {
  const scopeCode = (item.scope || '').split(',')[0] || item.scope || ''
  const group = (['今天', '昨天', '本周', '更早'].includes(item.group || '')
    ? item.group
    : '更早') as HistoryItem['group']
  return {
    id: String(item.id),
    title: item.title || '未命名对话',
    lastQuestion: item.lastQuestion || '',
    scope: scopeLabels[scopeCode] || item.scope || scopeCode,
    time: item.updatedAt || '',
    group,
    rating: item.rating,
  }
}

type HistoryPageProps = {
  onContinue: (sessionId: number, title: string) => void
  onAskFirst: () => void
  onBack: () => void
}

function HistoryPage({ onContinue, onAskFirst, onBack }: HistoryPageProps) {
  const [query, setQuery] = useState('')
  const [committedQuery, setCommittedQuery] = useState('')
  const [items, setItems] = useState<HistoryItem[]>([])
  const [selected, setSelected] = useState<string[]>([])
  const [batchMode, setBatchMode] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function refresh(keyword?: string) {
    setLoading(true)
    setError(null)
    try {
      const list = await listHistory(keyword)
      setItems((list || []).map(mapItem))
    } catch (err) {
      setItems([])
      const msg = err instanceof Error ? err.message : '加载历史失败'
      // 无数据或系统兜底错误时走空态，避免误报「系统错误」
      if (msg !== '系统错误') setError(msg)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setCommittedQuery(query.trim())
    }, 250)
    return () => window.clearTimeout(timer)
  }, [query])

  useEffect(() => {
    void refresh(committedQuery)
  }, [committedQuery])

  function toggleSelect(id: string) {
    setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
  }

  async function deleteOne(id: string) {
    try {
      await deleteSession(Number(id))
      setItems((prev) => prev.filter((item) => item.id !== id))
      setSelected((prev) => prev.filter((x) => x !== id))
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除失败')
    }
  }

  async function deleteSelected() {
    try {
      await batchDeleteSessions(selected.map(Number))
      setItems((prev) => prev.filter((item) => !selected.includes(item.id)))
      setSelected([])
      setBatchMode(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : '批量删除失败')
    }
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
        {loading ? <div className="hiMeta">加载中…</div> : null}
        {error ? <div className="hiMeta">{error}</div> : null}
        {!loading && items.length === 0 ? (
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
            const list = items.filter((item) => item.group === group)
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
                            onClick={() => onContinue(Number(item.id), item.title)}
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
