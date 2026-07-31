import { useEffect, useMemo, useState } from 'react'
import {
  Bookmark,
  BookOpen,
  ChevronRight,
  Download,
  MessageCircle,
  MessagesSquare,
  Star,
  Target,
  ThumbsDown,
  ThumbsUp,
  Timer,
  Trophy,
} from 'lucide-react'
import { exportStats, getStatsOverview, type StatsOverview } from './api'

type RangeId = '7d' | '30d' | 'quarter' | 'custom'

const ranges: Array<{ id: RangeId; label: string }> = [
  { id: '7d', label: '近7天' },
  { id: '30d', label: '近30天' },
  { id: 'quarter', label: '本季度' },
  { id: 'custom', label: '自定义' },
]

const libName: Record<string, string> = {
  hr: '人事制度',
  product: '产品知识',
  tech: '技术文档',
  support: '售后 FAQ',
  all: '全部',
  unknown: '未分类',
}

const libColor = ['#2563EB', '#22C55E', '#8B5CF6', '#F59E0B', '#64748B']
const weekLabels = ['日', '一', '二', '三', '四', '五', '六']
const achieveIcons = [Trophy, Target, BookOpen, MessagesSquare]

type UsageStatsPageProps = {
  onBack: () => void
  onAskAgain?: (question: string) => void
}

function UsageStatsPage({ onBack, onAskAgain }: UsageStatsPageProps) {
  const [range, setRange] = useState<RangeId>('30d')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')
  const [data, setData] = useState<StatsOverview | null>(null)
  const [loading, setLoading] = useState(false)
  const [toast, setToast] = useState<string | null>(null)

  function showToast(msg: string) {
    setToast(msg)
    window.setTimeout(() => setToast(null), 1800)
  }

  useEffect(() => {
    if (range === 'custom' && (!customFrom || !customTo)) return
    let alive = true
    ;(async () => {
      setLoading(true)
      try {
        const overview = await getStatsOverview({
          range,
          from: range === 'custom' ? customFrom : undefined,
          to: range === 'custom' ? customTo : undefined,
        })
        if (alive) setData(overview)
      } catch (err) {
        if (alive) showToast(err instanceof Error ? err.message : '统计加载失败')
      } finally {
        if (alive) setLoading(false)
      }
    })()
    return () => {
      alive = false
    }
  }, [range, customFrom, customTo])

  const kpi = data?.kpi
  const askTrend = data?.askTrend || []
  const kbItems = useMemo(() => {
    return (data?.libraryDistribution || []).map((item, i) => ({
      name: libName[item.libraryCode] || item.libraryCode,
      pct: Number(item.percent) || 0,
      count: item.count || 0,
      color: libColor[i % libColor.length],
    }))
  }, [data])
  const topQuestions = (data?.topQuestions || []).map((q, i) => ({
    rank: i + 1,
    title: q.question,
    count: q.count,
  }))
  const maxTop = Math.max(...topQuestions.map((q) => q.count), 1)
  const feedback = data?.feedbackOverview
  const helpfulPct = Number(feedback?.helpfulPercent) || 0
  const achievements = data?.achievements || []
  const habit = data?.sourceHabit

  const heatmap = useMemo(() => {
    const grid: number[][] = Array.from({ length: 7 }, () => Array.from({ length: 24 }, () => 0))
    let max = 1
    for (const cell of data?.activeHeatmap || []) {
      const d = cell.weekday ?? 0
      const h = cell.hour ?? 0
      if (d >= 0 && d < 7 && h >= 0 && h < 24) {
        grid[d][h] = cell.count
        max = Math.max(max, cell.count)
      }
    }
    return grid.map((row) => row.map((c) => Math.min(4, Math.round((c / max) * 4))))
  }, [data])

  function heatColor(level: number) {
    const palette = ['#E5E7EB', '#BFDBFE', '#93C5FD', '#3B82F6', '#1D4ED8']
    return palette[level] ?? palette[0]
  }

  async function onExport() {
    try {
      await exportStats({
        range,
        from: range === 'custom' ? customFrom : undefined,
        to: range === 'custom' ? customTo : undefined,
      })
      showToast('报告已导出')
    } catch (err) {
      showToast(err instanceof Error ? err.message : '导出失败')
    }
  }

  const mom = kpi?.askMomPercent ?? 0
  const avgMin = habit?.avgReadMinutes

  return (
    <div className="usPage">
      <header className="usHeader">
        <div className="usHeaderLeft">
          <button type="button" className="usGhostBtn" onClick={onBack}>
            返回个人中心
          </button>
          <nav className="usBreadcrumb" aria-label="面包屑">
            <button type="button" className="usCrumbLink" onClick={onBack}>
              个人中心
            </button>
            <ChevronRight size={14} />
            <span>我的使用统计</span>
          </nav>
          <h1>我的使用统计</h1>
        </div>

        <div className="usHeaderRight">
          <div className="usSegment" role="tablist" aria-label="时间范围">
            {ranges.map((item) => (
              <button
                key={item.id}
                type="button"
                role="tab"
                aria-selected={range === item.id}
                className={`usSegBtn ${range === item.id ? 'usSegBtnActive' : ''}`}
                onClick={() => {
                  setRange(item.id)
                  showToast(`已切换为「${item.label}」`)
                }}
              >
                {item.label}
              </button>
            ))}
          </div>
          {range === 'custom' ? (
            <div className="usCustomRange">
              <input type="date" value={customFrom} onChange={(e) => setCustomFrom(e.target.value)} />
              <span>~</span>
              <input type="date" value={customTo} onChange={(e) => setCustomTo(e.target.value)} />
            </div>
          ) : null}
          <button type="button" className="usExportBtn" onClick={onExport}>
            <Download size={16} />
            导出报告
          </button>
          <div className="usUpdated">{data?.updatedAt || (loading ? '加载中…' : '—')} 更新</div>
        </div>
      </header>

      <main className="usBody">
        <section className="usKpiRow">
          <article className="usCard usKpiCard">
            <div className="usKpiTop">
              <div className="usIconCircle usIconBlue">
                <MessageCircle size={18} />
              </div>
              <div className={`usDelta ${mom >= 0 ? 'usDeltaUp' : 'usDeltaDown'}`}>
                {mom >= 0 ? '↑' : '↓'} {Math.abs(mom)}% 环比
              </div>
            </div>
            <div className="usKpiValue">{kpi?.askCount ?? 0}</div>
            <div className="usKpiLabel">提问次数</div>
          </article>

          <article className="usCard usKpiCard">
            <div className="usKpiTop">
              <div className="usIconCircle usIconGreen">
                <Timer size={18} />
              </div>
            </div>
            <div className="usKpiValue">
              {kpi?.savedHours ?? 0} <span className="usKpiUnit">小时</span>
            </div>
            <div className="usKpiLabel">预估节省查询时间</div>
          </article>

          <article className="usCard usKpiCard">
            <div className="usKpiTop">
              <div className="usIconCircle usIconOrange">
                <Star size={18} />
              </div>
            </div>
            <div className="usKpiValue">{kpi?.avgRating ?? 0}</div>
            <div className="usKpiLabel">我给出的平均评分</div>
            <div className="usKpiSub">已评价 {kpi?.ratingCount ?? 0} 次</div>
          </article>

          <article className="usCard usKpiCard">
            <div className="usKpiTop">
              <div className="usIconCircle usIconPurple">
                <Bookmark size={18} />
              </div>
            </div>
            <div className="usKpiValue">{kpi?.favoriteCount ?? 0}</div>
            <div className="usKpiLabel">收藏文档 / 回答</div>
            <div className="usKpiSub">本月新增 {kpi?.favoriteMonthNew ?? 0} 条</div>
          </article>
        </section>

        <section className="usRow usRow23">
          <article className="usCard">
            <div className="usCardHead">
              <div>
                <h2>提问趋势</h2>
                <p>
                  {data?.from || ''} ~ {data?.to || ''}
                </p>
              </div>
            </div>
            <ul className="usTrendList">
              {askTrend.length === 0 ? <li>暂无数据</li> : null}
              {askTrend.slice(-14).map((d) => (
                <li key={d.date}>
                  <span>{d.date.slice(5)}</span>
                  <div className="usTrendBarTrack">
                    <div
                      className="usTrendBarFill"
                      style={{
                        width: `${(d.count / Math.max(...askTrend.map((x) => x.count), 1)) * 100}%`,
                      }}
                    />
                  </div>
                  <em>{d.count}</em>
                </li>
              ))}
            </ul>
          </article>

          <article className="usCard">
            <div className="usCardHead">
              <div>
                <h2>知识库分布</h2>
                <p>提问所属知识库占比</p>
              </div>
            </div>
            <ul className="usKbLegend">
              {kbItems.length === 0 ? <li>暂无数据</li> : null}
              {kbItems.map((item) => (
                <li key={item.name}>
                  <span className="usKbLeft">
                    <i style={{ background: item.color }} />
                    {item.name}
                  </span>
                  <span className="usKbRight">
                    <strong>{item.pct}%</strong>
                    <em>{item.count} 次</em>
                  </span>
                </li>
              ))}
            </ul>
          </article>
        </section>

        <section className="usRow usRowHalf">
          <article className="usCard">
            <div className="usCardHead">
              <div>
                <h2>我最常问的问题</h2>
                <p>按提问次数排序</p>
              </div>
            </div>
            <ul className="usTopList">
              {topQuestions.length === 0 ? <li>暂无数据</li> : null}
              {topQuestions.map((q) => (
                <li key={q.rank}>
                  <div className="usTopRank">{q.rank}</div>
                  <div className="usTopMain">
                    <div className="usTopTitleRow">
                      <span className="usTopTitle">{q.title}</span>
                      <span className="usTopCount">{q.count} 次</span>
                    </div>
                    <div className="usTopBarTrack">
                      <div className="usTopBarFill" style={{ width: `${(q.count / maxTop) * 100}%` }} />
                    </div>
                  </div>
                  <button
                    type="button"
                    className="usGhostAsk"
                    onClick={() => {
                      onAskAgain?.(q.title)
                      showToast(`已发起再次提问：「${q.title}」`)
                    }}
                  >
                    再次提问
                  </button>
                </li>
              ))}
            </ul>
          </article>

          <article className="usCard">
            <div className="usCardHead">
              <div>
                <h2>我的反馈概览</h2>
                <p>对回答质量的评价分布</p>
              </div>
            </div>
            <div className="usFeedbackSplit">
              <div className="usFeedbackCol usFeedbackGood">
                <ThumbsUp size={20} />
                <div className="usFeedbackNum">{feedback?.helpful ?? 0}</div>
                <div className="usFeedbackLabel">有帮助</div>
              </div>
              <div className="usFeedbackCol usFeedbackBad">
                <ThumbsDown size={20} />
                <div className="usFeedbackNum">{feedback?.unhelpful ?? 0}</div>
                <div className="usFeedbackLabel">没帮助</div>
              </div>
            </div>
            <div className="usStackBar" aria-hidden="true">
              <div className="usStackGood" style={{ width: `${helpfulPct || 0}%` }}>
                有帮助 {helpfulPct || 0}%
              </div>
              <div className="usStackBad" style={{ width: `${100 - (helpfulPct || 0)}%` }}>
                {100 - (helpfulPct || 0)}%
              </div>
            </div>
            <div className="usSuccessHint">{feedback?.optimizedHint || '暂无反馈数据'}</div>
          </article>
        </section>

        <section className="usCard usAchieveCard">
          <div className="usCardHead">
            <div>
              <h2>使用成就 / 里程碑</h2>
              <p>持续使用，解锁更多成长徽章</p>
            </div>
          </div>
          <div className="usAchieveRow">
            {achievements.map((item, idx) => {
              const Icon = achieveIcons[idx % achieveIcons.length]
              const pct = item.progress ?? 0
              return (
                <div
                  key={item.name}
                  className={`usAchieveItem ${item.completed ? 'usAchieveDone' : ''}`}
                >
                  <div className="usAchieveIcon">
                    <Icon size={18} />
                  </div>
                  <div className="usAchieveBody">
                    <div className="usAchieveTitle">{item.name}</div>
                    <div className="usAchieveDesc">{item.description}</div>
                    <div className="usAchieveMeta">
                      {item.completed ? '已达成' : `${item.current ?? 0}/${item.target ?? 0}`}
                    </div>
                    <div className="usAchieveTrack">
                      <div className="usAchieveFill" style={{ width: `${pct}%` }} />
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        </section>

        <section className="usRow usRowHalf">
          <article className="usCard">
            <div className="usCardHead">
              <div>
                <h2>活跃时段</h2>
                <p>一周 × 小时提问密度</p>
              </div>
            </div>
            <div className="usHeatWrap">
              <div className="usHeatGrid">
                {heatmap.map((row, wi) => (
                  <div key={weekLabels[wi]} className="usHeatRow">
                    <span className="usHeatDay">{weekLabels[wi]}</span>
                    <div className="usHeatCells">
                      {row.map((level, hi) => (
                        <span
                          key={`${wi}-${hi}`}
                          className="usHeatCell"
                          style={{ background: heatColor(level) }}
                          title={`${weekLabels[wi]} ${hi}:00`}
                        />
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </article>

          <article className="usCard">
            <div className="usCardHead">
              <div>
                <h2>溯源习惯</h2>
                <p>点击原文与阅读行为</p>
              </div>
            </div>
            <div className="usSourceMetrics">
              <div>
                <strong>{habit?.openSourceCount ?? 0}</strong>
                <span>点击查看来源</span>
              </div>
              <div>
                <strong>{habit?.readCompleteCount ?? 0}</strong>
                <span>完整阅读文档</span>
              </div>
              <div>
                <strong>{avgMin == null ? '—' : `${avgMin} 分`}</strong>
                <span>平均阅读时长</span>
              </div>
            </div>
          </article>
        </section>

        <footer className="usFooterNote">
          统计仅展示您本人的使用数据 · 节省时间按配置系数估算
        </footer>
      </main>

      {toast ? (
        <div className="usToast" role="status">
          {toast}
        </div>
      ) : null}
    </div>
  )
}

export default UsageStatsPage
