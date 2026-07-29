import { useMemo, useState } from 'react'
import {
  Bookmark,
  Download,
  MessageCircle,
  Star,
  Target,
  Timer,
  Trophy,
  BookOpen,
  MessagesSquare,
  ThumbsDown,
  ThumbsUp,
  ChevronRight,
} from 'lucide-react'

type RangeId = '7d' | '30d' | 'quarter' | 'custom'

const ranges: Array<{ id: RangeId; label: string }> = [
  { id: '7d', label: '近7天' },
  { id: '30d', label: '近30天' },
  { id: 'quarter', label: '本季度' },
  { id: 'custom', label: '自定义' }
]

const sparklineAsk = [2, 3, 1, 4, 5, 3, 6, 4, 7, 5, 8, 4, 3, 5, 6, 4, 2, 5, 7, 4, 6, 8, 5, 3, 4, 6, 5, 4, 3, 5]

const trendDays = [
  { label: '6/30', ask: 2, rate: 90 },
  { label: '7/2', ask: 3, rate: 88 },
  { label: '7/4', ask: 1, rate: 100 },
  { label: '7/6', ask: 4, rate: 85 },
  { label: '7/8', ask: 5, rate: 92 },
  { label: '7/10', ask: 3, rate: 87 },
  { label: '7/12', ask: 6, rate: 90 },
  { label: '7/14', ask: 4, rate: 88 },
  { label: '7/15', ask: 5, rate: 91 },
  { label: '7/17', ask: 7, rate: 86 },
  { label: '7/19', ask: 4, rate: 93 },
  { label: '7/21', ask: 6, rate: 89 },
  { label: '7/22', ask: 8, rate: 94 },
  { label: '7/24', ask: 5, rate: 90 },
  { label: '7/26', ask: 3, rate: 88 },
  { label: '7/28', ask: 5, rate: 92 }
]

const kbDistribution = [
  { name: '人事制度', pct: 35, count: 16, color: '#2563EB' },
  { name: '产品知识', pct: 28, count: 13, color: '#22C55E' },
  { name: '技术文档', pct: 22, count: 10, color: '#8B5CF6' },
  { name: '售后 FAQ', pct: 15, count: 8, color: '#F59E0B' }
]

const topQuestions = [
  { rank: 1, title: '年假相关规定', count: 8 },
  { rank: 2, title: '报销流程咨询', count: 6 },
  { rank: 3, title: '产品参数对比', count: 5 },
  { rank: 4, title: '考勤制度说明', count: 4 },
  { rank: 5, title: '新人入职流程', count: 3 }
]

const achievements = [
  {
    id: 'explorer',
    icon: Trophy,
    title: '知识探索者',
    desc: '累计提问满 50 次',
    progress: 47,
    total: 50,
    done: false
  },
  {
    id: 'precise',
    icon: Target,
    title: '精准提问',
    desc: '连续 7 天有有效回答',
    progress: 7,
    total: 7,
    done: true
  },
  {
    id: 'deep',
    icon: BookOpen,
    title: '深度阅读',
    desc: '点击查看来源 20+ 次',
    progress: 28,
    total: 20,
    done: true
  },
  {
    id: 'multi',
    icon: MessagesSquare,
    title: '多轮达人',
    desc: '单会话超过 5 轮对话 3 次',
    progress: 3,
    total: 3,
    done: true
  }
]

/** Weekday × hour heatmap intensity 0–4 */
const heatmap: number[][] = [
  // Mon–Fri focus around 10–11 and 14–16
  [0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 4, 3, 1, 2, 4, 4, 3, 1, 0, 0, 0, 0, 0, 0],
  [0, 0, 0, 0, 0, 0, 0, 0, 1, 3, 4, 2, 1, 3, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0],
  [0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 3, 1, 2, 4, 4, 2, 1, 0, 0, 0, 0, 0, 0],
  [0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 2, 1, 3, 4, 3, 2, 0, 0, 0, 0, 0, 0, 0],
  [0, 0, 0, 0, 0, 0, 0, 0, 1, 3, 4, 2, 0, 2, 3, 4, 2, 1, 0, 0, 0, 0, 0, 0],
  [0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0],
  [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
]

const weekLabels = ['一', '二', '三', '四', '五', '六', '日']
const hourLabels = [8, 10, 12, 14, 16, 18]

const sourceClicksWeek = [3, 5, 2, 6, 4, 1, 7]

type UsageStatsPageProps = {
  onBack: () => void
  onAskAgain?: (question: string) => void
}

function Sparkline({ values, color = '#60A5FA' }: { values: number[]; color?: string }) {
  const w = 120
  const h = 32
  const max = Math.max(...values, 1)
  const min = Math.min(...values, 0)
  const span = max - min || 1
  const points = values
    .map((v, i) => {
      const x = (i / (values.length - 1)) * w
      const y = h - ((v - min) / span) * (h - 4) - 2
      return `${x},${y}`
    })
    .join(' ')

  return (
    <svg className="usSparkline" viewBox={`0 0 ${w} ${h}`} width="100%" height={32} aria-hidden="true">
      <polyline fill="none" stroke={color} strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" points={points} />
    </svg>
  )
}

function AreaTrendChart({
  data,
  hoverIndex,
  onHover
}: {
  data: typeof trendDays
  hoverIndex: number | null
  onHover: (i: number | null) => void
}) {
  const w = 560
  const h = 220
  const pad = { t: 24, r: 16, b: 36, l: 36 }
  const innerW = w - pad.l - pad.r
  const innerH = h - pad.t - pad.b
  const maxAsk = Math.max(...data.map((d) => d.ask), 1)

  const coords = data.map((d, i) => {
    const x = pad.l + (i / (data.length - 1)) * innerW
    const y = pad.t + innerH - (d.ask / maxAsk) * innerH
    return { x, y, ...d }
  })

  const linePath = coords.map((c, i) => `${i === 0 ? 'M' : 'L'} ${c.x} ${c.y}`).join(' ')
  const areaPath = `${linePath} L ${coords[coords.length - 1].x} ${pad.t + innerH} L ${coords[0].x} ${pad.t + innerH} Z`
  const peak = coords.reduce((a, b) => (b.ask > a.ask ? b : a))

  return (
    <svg className="usTrendSvg" viewBox={`0 0 ${w} ${h}`} role="img" aria-label="提问趋势面积图">
      <defs>
        <linearGradient id="usAreaFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#DBEAFE" stopOpacity="0.95" />
          <stop offset="100%" stopColor="#DBEAFE" stopOpacity="0" />
        </linearGradient>
      </defs>

      {[0, 0.25, 0.5, 0.75, 1].map((t) => {
        const y = pad.t + innerH * (1 - t)
        return (
          <g key={t}>
            <line x1={pad.l} y1={y} x2={w - pad.r} y2={y} className="usGridLine" />
            <text x={pad.l - 8} y={y + 4} className="usAxisText" textAnchor="end">
              {Math.round(maxAsk * t)}
            </text>
          </g>
        )
      })}

      <path d={areaPath} fill="url(#usAreaFill)" />
      <path d={linePath} fill="none" stroke="#2563EB" strokeWidth="2.5" strokeLinejoin="round" />

      {coords.map((c, i) => (
        <g key={c.label}>
          <circle
            cx={c.x}
            cy={c.y}
            r={hoverIndex === i ? 5 : 3}
            fill="#2563EB"
            className="usTrendDot"
            onMouseEnter={() => onHover(i)}
            onMouseLeave={() => onHover(null)}
          />
          {(i % 3 === 0 || i === coords.length - 1) && (
            <text x={c.x} y={h - 10} className="usAxisText" textAnchor="middle">
              {c.label}
            </text>
          )}
        </g>
      ))}

      <g className="usPeakBadge">
        <rect x={peak.x - 52} y={peak.y - 28} width={104} height={22} rx={8} />
        <text x={peak.x} y={peak.y - 13} textAnchor="middle">
          最高 {peak.ask} 次 · 7月22日
        </text>
      </g>

      {hoverIndex !== null ? (
        <g className="usTooltip">
          <rect
            x={coords[hoverIndex].x - 58}
            y={coords[hoverIndex].y - 42}
            width={116}
            height={28}
            rx={8}
          />
          <text x={coords[hoverIndex].x} y={coords[hoverIndex].y - 23} textAnchor="middle">
            {coords[hoverIndex].label.replace('/', '月')}日 · {coords[hoverIndex].ask} 次提问
          </text>
        </g>
      ) : null}
    </svg>
  )
}

function DonutChart({ items, center }: { items: typeof kbDistribution; center: string }) {
  const size = 168
  const stroke = 22
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  let offset = 0

  return (
    <div className="usDonutWrap">
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true">
        <g transform={`rotate(-90 ${size / 2} ${size / 2})`}>
          {items.map((item) => {
            const len = (item.pct / 100) * c
            const dash = `${len} ${c - len}`
            const el = (
              <circle
                key={item.name}
                cx={size / 2}
                cy={size / 2}
                r={r}
                fill="none"
                stroke={item.color}
                strokeWidth={stroke}
                strokeDasharray={dash}
                strokeDashoffset={-offset}
                strokeLinecap="butt"
              />
            )
            offset += len
            return el
          })}
        </g>
      </svg>
      <div className="usDonutCenter">
        <strong>{center}</strong>
        <span>提问合计</span>
      </div>
    </div>
  )
}

function MiniStars({ value }: { value: number }) {
  return (
    <div className="usMiniStars" aria-label={`${value}/5`}>
      {[1, 2, 3, 4, 5].map((n) => {
        const fill = Math.min(1, Math.max(0, value - (n - 1)))
        return (
          <span key={n} className="usStarSlot">
            <Star size={14} className="usStarEmpty" />
            <span className="usStarFill" style={{ width: `${fill * 100}%` }}>
              <Star size={14} />
            </span>
          </span>
        )
      })}
    </div>
  )
}

function UsageStatsPage({ onBack, onAskAgain }: UsageStatsPageProps) {
  const [range, setRange] = useState<RangeId>('30d')
  const [hoverTrend, setHoverTrend] = useState<number | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const maxTop = useMemo(() => Math.max(...topQuestions.map((q) => q.count)), [])

  function showToast(msg: string) {
    setToast(msg)
    window.setTimeout(() => setToast(null), 1800)
  }

  function heatColor(level: number) {
    const palette = ['#E5E7EB', '#BFDBFE', '#93C5FD', '#3B82F6', '#1D4ED8']
    return palette[level] ?? palette[0]
  }

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
          <button
            type="button"
            className="usExportBtn"
            onClick={() => showToast('报告已导出（mock）')}
          >
            <Download size={16} />
            导出报告
          </button>
          <div className="usUpdated">今天 09:00 更新</div>
        </div>
      </header>

      <main className="usBody">
        {/* KPI row */}
        <section className="usKpiRow">
          <article className="usCard usKpiCard">
            <div className="usKpiTop">
              <div className="usIconCircle usIconBlue">
                <MessageCircle size={18} />
              </div>
              <div className="usDelta usDeltaUp">↑ 23% 较上月</div>
            </div>
            <div className="usKpiValue">47</div>
            <div className="usKpiLabel">本月提问</div>
            <Sparkline values={sparklineAsk} color="#60A5FA" />
          </article>

          <article className="usCard usKpiCard">
            <div className="usKpiTop">
              <div className="usIconCircle usIconGreen">
                <Timer size={18} />
              </div>
              <div className="usDelta usDeltaUp">↑ 0.8h</div>
            </div>
            <div className="usKpiValue">
              3.2 <span className="usKpiUnit">小时</span>
            </div>
            <div className="usKpiLabel">预估节省查询时间</div>
            <div className="usKpiSub">约等于少翻 64 份文档</div>
          </article>

          <article className="usCard usKpiCard">
            <div className="usKpiTop">
              <div className="usIconCircle usIconOrange">
                <Star size={18} />
              </div>
              <MiniStars value={4.6} />
            </div>
            <div className="usKpiValue">4.6</div>
            <div className="usKpiLabel">我给出的平均评分</div>
            <div className="usKpiSub">已评价 38 次</div>
          </article>

          <article className="usCard usKpiCard">
            <div className="usKpiTop">
              <div className="usIconCircle usIconPurple">
                <Bookmark size={18} />
              </div>
            </div>
            <div className="usKpiValue">12</div>
            <div className="usKpiLabel">收藏文档 / 回答</div>
            <div className="usKpiSub">本月新增 5 条</div>
          </article>
        </section>

        {/* Trend + Distribution */}
        <section className="usRow usRow23">
          <article className="usCard">
            <div className="usCardHead">
              <div>
                <h2>提问趋势</h2>
                <p>近 30 天每日提问量</p>
              </div>
              <div className="usLegend">
                <span>
                  <i className="usLegDot usLegBlue" />
                  提问次数
                </span>
                <span>
                  <i className="usLegDot usLegGreenDash" />
                  有效回答率
                </span>
              </div>
            </div>
            <AreaTrendChart data={trendDays} hoverIndex={hoverTrend} onHover={setHoverTrend} />
          </article>

          <article className="usCard">
            <div className="usCardHead">
              <div>
                <h2>知识库分布</h2>
                <p>提问所属知识库占比</p>
              </div>
            </div>
            <DonutChart items={kbDistribution} center="47 次" />
            <ul className="usKbLegend">
              {kbDistribution.map((item) => (
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

        {/* Top Q + Feedback */}
        <section className="usRow usRowHalf">
          <article className="usCard">
            <div className="usCardHead">
              <div>
                <h2>我最常问的问题</h2>
                <p>按提问次数排序</p>
              </div>
            </div>
            <ul className="usTopList">
              {topQuestions.map((q) => (
                <li key={q.rank}>
                  <div className="usTopRank">{q.rank}</div>
                  <div className="usTopMain">
                    <div className="usTopTitleRow">
                      <span className="usTopTitle">{q.title}</span>
                      <span className="usTopCount">{q.count} 次</span>
                    </div>
                    <div className="usTopBarTrack">
                      <div
                        className="usTopBarFill"
                        style={{ width: `${(q.count / maxTop) * 100}%` }}
                      />
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
                <div className="usFeedbackNum">32</div>
                <div className="usFeedbackLabel">有帮助</div>
              </div>
              <div className="usFeedbackCol usFeedbackBad">
                <ThumbsDown size={20} />
                <div className="usFeedbackNum">6</div>
                <div className="usFeedbackLabel">没帮助</div>
              </div>
            </div>
            <div className="usStackBar" aria-hidden="true">
              <div className="usStackGood" style={{ width: '84%' }}>
                有帮助 84%
              </div>
              <div className="usStackBad" style={{ width: '16%' }}>
                16%
              </div>
            </div>
            <div className="usSuccessHint">您的反馈帮助优化了 3 条知识</div>
          </article>
        </section>

        {/* Achievements */}
        <section className="usCard usAchieveCard">
          <div className="usCardHead">
            <div>
              <h2>使用成就 / 里程碑</h2>
              <p>持续使用，解锁更多成长徽章</p>
            </div>
          </div>
          <div className="usAchieveRow">
            {achievements.map((item) => {
              const Icon = item.icon
              const pct = Math.min(100, Math.round((item.progress / item.total) * 100))
              return (
                <div
                  key={item.id}
                  className={`usAchieveItem ${item.done ? 'usAchieveDone' : ''}`}
                >
                  <div className="usAchieveIcon">
                    <Icon size={18} />
                  </div>
                  <div className="usAchieveBody">
                    <div className="usAchieveTitle">{item.title}</div>
                    <div className="usAchieveDesc">{item.desc}</div>
                    <div className="usAchieveMeta">
                      {item.done ? '已达成' : `${item.progress}/${item.total}`}
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

        {/* Heatmap + Source habits */}
        <section className="usRow usRowHalf">
          <article className="usCard">
            <div className="usCardHead">
              <div>
                <h2>活跃时段</h2>
                <p>一周 × 小时提问密度</p>
              </div>
            </div>
            <div className="usHeatWrap">
              <div className="usHeatHours">
                {hourLabels.map((h) => (
                  <span key={h}>{h}:00</span>
                ))}
              </div>
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
                          title={`周${weekLabels[wi]} ${hi}:00 · 强度 ${level}`}
                        />
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>
            <p className="usHeatHint">
              您通常在工作日 10:00–11:00、14:00–16:00 使用最多
            </p>
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
                <strong>28</strong>
                <span>点击查看来源</span>
              </div>
              <div>
                <strong>11</strong>
                <span>完整阅读文档</span>
              </div>
              <div>
                <strong>2分18秒</strong>
                <span>平均阅读时长</span>
              </div>
            </div>
            <div className="usSourceBars">
              <div className="usSourceBarsTitle">本周每日「点击原文」次数</div>
              <div className="usMiniBars">
                {sourceClicksWeek.map((v, i) => (
                  <div key={i} className="usMiniBarCol">
                    <div className="usMiniBar" style={{ height: `${(v / 7) * 72}px` }} />
                    <span>{['一', '二', '三', '四', '五', '六', '日'][i]}</span>
                  </div>
                ))}
              </div>
            </div>
          </article>
        </section>

        <footer className="usFooterNote">
          统计仅展示您本人的使用数据，数据每日更新 · 节省时间按行业平均检索时长估算
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
