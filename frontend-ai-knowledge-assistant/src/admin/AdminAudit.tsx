import { useCallback, useEffect, useMemo, useState } from 'react'
import { Download, RotateCcw, Search } from 'lucide-react'
import { AdminApiError, USE_ADMIN_MOCK, realAdminApi } from './api'
import {
  auditActionLabel,
  auditDetailLabel,
  auditTargetTypeLabel,
} from './auditLabels'
import { AUDIT_ACTIONS, KB_ADMIN_AUDIT_ACTIONS, loadAuditEvents } from './mock'
import type { AdminRole, AuditEvent, AuditTargetType } from './types'
import { useAdminToast } from './useAdminToast'

type Props = {
  role: AdminRole
}

type RangeKey = 'today' | '7d' | '30d' | 'custom'

const PAGE_SIZE = 20

const TARGET_TYPES: Array<AuditTargetType | ''> = [
  '',
  'user',
  'document',
  'library',
  'acl',
  'system',
  'auth',
  'session',
]

function parseStamp(stamp: string) {
  const [date, time] = stamp.split(' ')
  const [y, m, d] = date.split('-').map(Number)
  const [hh, mm] = (time || '00:00').split(':').map(Number)
  return new Date(y, m - 1, d, hh, mm)
}

function startOfDay(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

function inRange(createdAt: string, range: RangeKey, customFrom: string, customTo: string) {
  const at = parseStamp(createdAt)
  const today = startOfDay(new Date(2026, 6, 31))
  if (range === 'today') {
    return at >= today
  }
  if (range === '7d') {
    const from = new Date(today)
    from.setDate(from.getDate() - 6)
    return at >= from
  }
  if (range === '30d') {
    const from = new Date(today)
    from.setDate(from.getDate() - 29)
    return at >= from
  }
  if (!customFrom && !customTo) return true
  if (customFrom) {
    const from = startOfDay(new Date(customFrom))
    if (at < from) return false
  }
  if (customTo) {
    const to = startOfDay(new Date(customTo))
    to.setHours(23, 59, 59, 999)
    if (at > to) return false
  }
  return true
}

function csvEscape(value: string) {
  if (/[",\n]/.test(value)) return `"${value.replace(/"/g, '""')}"`
  return value
}

function visibleActionsForRole(role: AdminRole) {
  if (role === 'SYS_ADMIN') return [...AUDIT_ACTIONS]
  return AUDIT_ACTIONS.filter(
    (a) => KB_ADMIN_AUDIT_ACTIONS.has(a) || a.startsWith('INGEST_') || a.startsWith('ACL_'),
  )
}

function errMsg(err: unknown, fallback: string) {
  return err instanceof AdminApiError ? err.message : fallback
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export default function AdminAudit({ role }: Props) {
  const allEvents = useMemo(() => (USE_ADMIN_MOCK ? loadAuditEvents() : []), [])
  const actionOptions = useMemo(() => visibleActionsForRole(role), [role])

  const [range, setRange] = useState<RangeKey>('30d')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')
  const [actorQuery, setActorQuery] = useState('')
  const [selectedActions, setSelectedActions] = useState<string[]>([])
  const [targetType, setTargetType] = useState<AuditTargetType | ''>('')
  const [keyword, setKeyword] = useState('')

  const [applied, setApplied] = useState({
    range: '30d' as RangeKey,
    customFrom: '',
    customTo: '',
    actorQuery: '',
    selectedActions: [] as string[],
    targetType: '' as AuditTargetType | '',
    keyword: '',
  })

  const [page, setPage] = useState(1)
  const [detail, setDetail] = useState<AuditEvent | null>(null)
  const [realRows, setRealRows] = useState<AuditEvent[]>([])
  const [realTotal, setRealTotal] = useState(0)
  const [loading, setLoading] = useState(!USE_ADMIN_MOCK)
  const [exporting, setExporting] = useState(false)
  const { showToast, toastNode } = useAdminToast()

  const scoped = useMemo(() => {
    if (!USE_ADMIN_MOCK) return []
    if (role === 'SYS_ADMIN') return allEvents
    return allEvents.filter(
      (e) =>
        e.knowledgeRelated &&
        (KB_ADMIN_AUDIT_ACTIONS.has(e.action) ||
          e.action.startsWith('INGEST_') ||
          e.action.startsWith('ACL_')),
    )
  }, [allEvents, role])

  const mockFiltered = useMemo(() => {
    if (!USE_ADMIN_MOCK) return []
    const qActor = applied.actorQuery.trim().toLowerCase()
    const qDetail = applied.keyword.trim().toLowerCase()
    return scoped.filter((event) => {
      if (!inRange(event.createdAt, applied.range, applied.customFrom, applied.customTo)) return false
      if (qActor) {
        const hay = `${event.actor} ${event.actorEmail || ''}`.toLowerCase()
        if (!hay.includes(qActor)) return false
      }
      if (applied.selectedActions.length && !applied.selectedActions.includes(event.action)) {
        return false
      }
      if (applied.targetType && event.targetType !== applied.targetType) return false
      if (
        qDetail &&
        !event.detail.toLowerCase().includes(qDetail) &&
        !event.target.toLowerCase().includes(qDetail)
      ) {
        return false
      }
      return true
    })
  }, [scoped, applied])

  const loadReal = useCallback(async () => {
    if (USE_ADMIN_MOCK) return
    setLoading(true)
    try {
      const pageResult = await realAdminApi.listAudit({
        range: applied.range,
        from: applied.range === 'custom' ? applied.customFrom || undefined : undefined,
        to: applied.range === 'custom' ? applied.customTo || undefined : undefined,
        actor: applied.actorQuery.trim() || undefined,
        actions: applied.selectedActions.length ? applied.selectedActions.join(',') : undefined,
        targetType: applied.targetType || undefined,
        keyword: applied.keyword.trim() || undefined,
        page,
        size: PAGE_SIZE,
      })
      setRealRows(pageResult.records)
      setRealTotal(pageResult.total)
    } catch (err) {
      showToast(errMsg(err, '审计日志加载失败'))
    } finally {
      setLoading(false)
    }
  }, [applied, page, showToast])

  useEffect(() => {
    void loadReal()
  }, [loadReal])

  const filteredCount = USE_ADMIN_MOCK ? mockFiltered.length : realTotal
  const totalPages = Math.max(1, Math.ceil(filteredCount / PAGE_SIZE))
  const pageSafe = Math.min(page, totalPages)
  const pageRows = USE_ADMIN_MOCK
    ? mockFiltered.slice((pageSafe - 1) * PAGE_SIZE, pageSafe * PAGE_SIZE)
    : realRows

  function applyFilters() {
    setApplied({
      range,
      customFrom,
      customTo,
      actorQuery,
      selectedActions: [...selectedActions],
      targetType,
      keyword,
    })
    setPage(1)
  }

  function resetFilters() {
    setRange('30d')
    setCustomFrom('')
    setCustomTo('')
    setActorQuery('')
    setSelectedActions([])
    setTargetType('')
    setKeyword('')
    setApplied({
      range: '30d',
      customFrom: '',
      customTo: '',
      actorQuery: '',
      selectedActions: [],
      targetType: '',
      keyword: '',
    })
    setPage(1)
  }

  function toggleAction(action: string) {
    setSelectedActions((prev) =>
      prev.includes(action) ? prev.filter((a) => a !== action) : [...prev, action],
    )
  }

  async function exportCsv() {
    if (USE_ADMIN_MOCK) {
      const header = ['时间', '操作人', '邮箱', '操作类型', '对象类型', '对象', '详情', 'IP']
      const lines = [
        header.join(','),
        ...mockFiltered.map((e) =>
          [
            e.createdAt,
            e.actor,
            e.actorEmail || '',
            auditActionLabel(e.action),
            auditTargetTypeLabel(e.targetType),
            e.target,
            auditDetailLabel(e.detail, e.action),
            e.ip,
          ]
            .map(csvEscape)
            .join(','),
        ),
      ]
      const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8;' })
      downloadBlob(blob, `audit-logs-${Date.now()}.csv`)
      showToast(`已导出 ${mockFiltered.length} 条（Mock）`)
      return
    }

    setExporting(true)
    try {
      const blob = await realAdminApi.exportAudit({
        range: applied.range,
        from: applied.range === 'custom' ? applied.customFrom || undefined : undefined,
        to: applied.range === 'custom' ? applied.customTo || undefined : undefined,
        actor: applied.actorQuery.trim() || undefined,
        actions: applied.selectedActions.length ? applied.selectedActions.join(',') : undefined,
        targetType: applied.targetType || undefined,
        keyword: applied.keyword.trim() || undefined,
      })
      downloadBlob(blob, 'audit.csv')
      showToast('已导出审计日志')
    } catch (err) {
      showToast(errMsg(err, '导出失败'))
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="adminPage">
      {toastNode}
      <div className="adminPageHeader">
        <div>
          <h1>审计日志</h1>
          <p className="adminMuted">
            {role === 'KB_ADMIN'
              ? '知识管理员仅可见入库 / ACL / 下载 / 分享等相关事件'
              : '登录、下载、分享、鉴权失败与管理操作全量可查'}
          </p>
        </div>
        <button
          type="button"
          className="adminGhostBtn"
          onClick={() => void exportCsv()}
          disabled={exporting || (!USE_ADMIN_MOCK ? filteredCount === 0 && !loading : !mockFiltered.length)}
        >
          <Download size={14} />
          {exporting ? '导出中…' : '导出 CSV'}
        </button>
      </div>

      <section className="adminPanel adminAuditFilters">
        <div className="adminSegment">
          {(
            [
              ['today', '今天'],
              ['7d', '7天'],
              ['30d', '30天'],
              ['custom', '自定义'],
            ] as const
          ).map(([key, label]) => (
            <button
              key={key}
              type="button"
              className={range === key ? 'isActive' : undefined}
              onClick={() => setRange(key)}
            >
              {label}
            </button>
          ))}
        </div>

        {range === 'custom' && (
          <div className="adminToolbar adminToolbarWrap">
            <label className="adminInlineField">
              开始日期
              <input type="date" value={customFrom} onChange={(e) => setCustomFrom(e.target.value)} />
            </label>
            <label className="adminInlineField">
              结束日期
              <input type="date" value={customTo} onChange={(e) => setCustomTo(e.target.value)} />
            </label>
          </div>
        )}

        <div className="adminToolbar adminToolbarWrap">
          <label className="adminInlineField adminInlineField--grow">
            操作人
            <span className="adminSearch">
              <Search size={16} />
              <input
                value={actorQuery}
                onChange={(e) => setActorQuery(e.target.value)}
                placeholder="姓名 / 邮箱"
              />
            </span>
          </label>
          <label className="adminInlineField">
            对象类型
            <select
              value={targetType}
              onChange={(e) => setTargetType(e.target.value as AuditTargetType | '')}
            >
              {TARGET_TYPES.map((t) => (
                <option key={t || 'all'} value={t}>
                  {t ? auditTargetTypeLabel(t) : '全部'}
                </option>
              ))}
            </select>
          </label>
          <label className="adminInlineField adminInlineField--grow">
            关键词
            <span className="adminSearch">
              <input
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="详情 / 对象"
              />
            </span>
          </label>
        </div>

        <div className="adminActionChips">
          <span className="adminMuted">操作类型</span>
          {actionOptions.map((action) => (
            <button
              key={action}
              type="button"
              className={`adminChip${selectedActions.includes(action) ? ' isActive' : ''}`}
              onClick={() => toggleAction(action)}
            >
              {auditActionLabel(action)}
            </button>
          ))}
        </div>

        <div className="adminHeaderActions">
          <button type="button" className="adminBtnPrimary" onClick={applyFilters}>
            查询
          </button>
          <button type="button" className="adminGhostBtn" onClick={resetFilters}>
            <RotateCcw size={14} />
            重置
          </button>
        </div>
      </section>

      <section className="adminPanel">
        <div className="adminPanelHead">
          <h2>结果 {filteredCount} 条</h2>
          <span className="adminMuted">
            第 {pageSafe} / {totalPages} 页
          </span>
        </div>

        {loading ? (
          <div className="adminEmpty">
            <h2>加载中…</h2>
          </div>
        ) : pageRows.length === 0 ? (
          <div className="adminEmpty">
            <h2>无匹配审计记录</h2>
            <p className="adminMuted">调整时间范围或筛选条件后再试</p>
          </div>
        ) : (
          <div className="adminTableWrap">
            <table className="adminTable adminTable--audit">
              <thead>
                <tr>
                  <th>时间</th>
                  <th>操作人</th>
                  <th>操作类型</th>
                  <th>对象</th>
                  <th>详情摘要</th>
                  <th>IP</th>
                </tr>
              </thead>
              <tbody>
                {pageRows.map((event) => (
                  <tr
                    key={event.id}
                    className="adminClickRow"
                    onClick={() => setDetail(event)}
                  >
                    <td>{event.createdAt || '—'}</td>
                    <td>
                      <strong>{event.actor || '—'}</strong>
                    </td>
                    <td>
                      <span className="adminActionChip">{auditActionLabel(event.action)}</span>
                    </td>
                    <td>
                      <div className="adminTaskMain">
                        <strong>{event.target || '—'}</strong>
                        <span className="adminMuted">{auditTargetTypeLabel(event.targetType)}</span>
                      </div>
                    </td>
                    <td>
                      <div className="adminClamp">{auditDetailLabel(event.detail, event.action)}</div>
                    </td>
                    <td>{event.ip || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="adminPagination">
          <button
            type="button"
            className="adminGhostBtn"
            disabled={pageSafe <= 1 || loading}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
          >
            上一页
          </button>
          <span className="adminMuted">
            {(pageSafe - 1) * PAGE_SIZE + (pageRows.length ? 1 : 0)}-
            {(pageSafe - 1) * PAGE_SIZE + pageRows.length} / {filteredCount}
          </span>
          <button
            type="button"
            className="adminGhostBtn"
            disabled={pageSafe >= totalPages || loading}
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
          >
            下一页
          </button>
        </div>
      </section>

      {detail && (
        <div className="adminDrawerMask" onClick={() => setDetail(null)} role="presentation">
          <aside
            className="adminDrawer adminDrawerWide"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
          >
            <h2>审计详情</h2>
            <dl className="adminDescList">
              <div>
                <dt>时间</dt>
                <dd>{detail.createdAt || '—'}</dd>
              </div>
              <div>
                <dt>操作人</dt>
                <dd>
                  {detail.actor || '—'}
                  {detail.actorEmail ? `（${detail.actorEmail}）` : ''}
                </dd>
              </div>
              <div>
                <dt>操作类型</dt>
                <dd>
                  <span className="adminActionChip">{auditActionLabel(detail.action)}</span>
                </dd>
              </div>
              <div>
                <dt>对象</dt>
                <dd>
                  {detail.target || '—'} · {auditTargetTypeLabel(detail.targetType)}
                  {detail.targetId ? ` · ${detail.targetId}` : ''}
                </dd>
              </div>
              <div>
                <dt>IP</dt>
                <dd>{detail.ip || '—'}</dd>
              </div>
              <div>
                <dt>详情摘要</dt>
                <dd>{auditDetailLabel(detail.detail, detail.action)}</dd>
              </div>
            </dl>
            <pre className="adminJsonBlock">{JSON.stringify(detail, null, 2)}</pre>
            <button type="button" className="adminGhostBtn" onClick={() => setDetail(null)}>
              关闭
            </button>
          </aside>
        </div>
      )}
    </div>
  )
}
