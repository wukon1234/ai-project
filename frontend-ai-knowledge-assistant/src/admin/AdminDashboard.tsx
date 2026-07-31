import { useEffect, useState } from 'react'
import {
  BookOpen,
  ClipboardList,
  FileUp,
  KeyRound,
  Settings2,
  UserCheck,
} from 'lucide-react'
import { AdminApiError, USE_ADMIN_MOCK, realAdminApi, type DashboardData } from './api'
import {
  MOCK_AUDIT_EVENTS,
  MOCK_READY_DOCS,
  MOCK_TOTAL_DOCS,
  loadIngestTasks,
  loadLibraries,
  loadUsers,
} from './mock'
import type { AdminRole, AdminView, AuditEvent, IngestTask } from './types'
import { useAdminToast } from './useAdminToast'

type Props = {
  userName: string
  role: AdminRole
  onNavigate: (view: AdminView, query?: Record<string, string>) => void
}

function statusClass(status: string) {
  return `adminStatus adminStatus--${status.toLowerCase()}`
}

function loadMockDashboard(role: AdminRole): DashboardData {
  const libraries = loadLibraries()
  const ingestTasks = loadIngestTasks()
  const failedCount = ingestTasks.filter((t) => t.status === 'FAILED').length
  const pendingUsers = loadUsers().filter((u) => u.status === 0).length
  const audits =
    role === 'KB_ADMIN'
      ? MOCK_AUDIT_EVENTS.filter((e) => e.knowledgeRelated).slice(0, 5)
      : MOCK_AUDIT_EVENTS.slice(0, 5)
  return {
    libraryCount: libraries.length,
    readyDocCount: MOCK_READY_DOCS,
    totalDocCount: MOCK_TOTAL_DOCS,
    failedIngestCount: failedCount,
    pendingUserCount: role === 'SYS_ADMIN' ? pendingUsers : null,
    recentIngestTasks: ingestTasks.slice(0, 4),
    recentAudits: audits,
  }
}

export default function AdminDashboard({ userName, role, onNavigate }: Props) {
  const [data, setData] = useState<DashboardData | null>(
    USE_ADMIN_MOCK ? loadMockDashboard(role) : null,
  )
  const [loading, setLoading] = useState(!USE_ADMIN_MOCK)
  const { showToast, toastNode } = useAdminToast()

  useEffect(() => {
    if (USE_ADMIN_MOCK) {
      setData(loadMockDashboard(role))
      return
    }
    let cancelled = false
    setLoading(true)
    realAdminApi
      .dashboard()
      .then((res) => {
        if (!cancelled) setData(res)
      })
      .catch((err) => {
        if (!cancelled) {
          showToast(err instanceof AdminApiError ? err.message : '工作台加载失败')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [role, showToast])

  const today = new Date().toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  })

  const shortcuts = [
    {
      title: '创建知识库',
      desc: '新增库并配置标签',
      icon: <BookOpen size={18} />,
      action: () => onNavigate('libraries', { action: 'create' }),
      show: true,
    },
    {
      title: '上传文档',
      desc: '进入入库队列',
      icon: <FileUp size={18} />,
      action: () => onNavigate('ingest'),
      show: true,
    },
    {
      title: '配置权限',
      desc: '用户 / 部门 ACL',
      icon: <KeyRound size={18} />,
      action: () => onNavigate('acl'),
      show: true,
    },
    {
      title: '用户审核',
      desc: '处理待审注册',
      icon: <UserCheck size={18} />,
      action: () => onNavigate('users', { status: '0' }),
      show: role === 'SYS_ADMIN',
    },
    {
      title: '模型设置',
      desc: 'LLM / Embedding / OCR',
      icon: <Settings2 size={18} />,
      action: () => onNavigate('models'),
      show: role === 'SYS_ADMIN',
    },
    {
      title: '审计日志',
      desc: '操作与风险追踪',
      icon: <ClipboardList size={18} />,
      action: () => onNavigate('audit'),
      show: true,
    },
  ].filter((item) => item.show)

  const ingestTasks: IngestTask[] = data?.recentIngestTasks || []
  const audits: AuditEvent[] = data?.recentAudits || []
  const failedCount = data?.failedIngestCount ?? 0
  const pendingUsers = data?.pendingUserCount

  return (
    <div className="adminPage">
      {toastNode}
      <div className="adminPageHeader">
        <div>
          <h1>你好，{userName}</h1>
          <p className="adminMuted">{today}</p>
        </div>
        <div className="adminHeaderActions">
          <button type="button" className="adminGhostBtn" onClick={() => onNavigate('ingest')}>
            上传文档
          </button>
          <button
            type="button"
            className="adminBtnPrimary"
            onClick={() => onNavigate('libraries', { action: 'create' })}
          >
            创建知识库
          </button>
        </div>
      </div>

      {loading && !data ? (
        <div className="adminEmpty">
          <p className="adminMuted">工作台加载中…</p>
        </div>
      ) : (
        <>
          <div className="adminKpiRow">
            <div className="adminKpi">
              <span>知识库数量</span>
              <strong>{data?.libraryCount ?? 0}</strong>
            </div>
            <div className="adminKpi">
              <span>已就绪 / 总文档</span>
              <strong>
                {data?.readyDocCount ?? 0}
                <em> / {data?.totalDocCount ?? 0}</em>
              </strong>
            </div>
            <button
              type="button"
              className="adminKpi isClickable"
              onClick={() => onNavigate('ingest', { status: 'FAILED' })}
            >
              <span>入库失败任务</span>
              <strong className={failedCount ? 'isDanger' : undefined}>{failedCount}</strong>
            </button>
            <div className="adminKpi">
              <span>待审核用户</span>
              <strong>{role === 'SYS_ADMIN' ? (pendingUsers ?? 0) : '—'}</strong>
            </div>
          </div>

          <div className="adminTwoCol">
            <section className="adminPanel">
              <div className="adminPanelHead">
                <h2>最近入库任务</h2>
                <button type="button" className="adminTextLink" onClick={() => onNavigate('ingest')}>
                  查看全部
                </button>
              </div>
              <ul className="adminTaskList">
                {ingestTasks.slice(0, 4).map((task) => (
                  <li key={task.id}>
                    <div className="adminTaskMain">
                      <strong>{task.title}</strong>
                      <span className="adminMuted">
                        {task.libraryName} · {task.createdAt}
                      </span>
                    </div>
                    <span className={statusClass(task.status)}>{task.status}</span>
                    <div className="adminProgress">
                      <div style={{ width: `${task.progress}%` }} />
                    </div>
                  </li>
                ))}
                {!ingestTasks.length && <li className="adminMuted">暂无入库任务</li>}
              </ul>
            </section>

            <section className="adminPanel">
              <div className="adminPanelHead">
                <h2>最近审计事件</h2>
                <button type="button" className="adminTextLink" onClick={() => onNavigate('audit')}>
                  查看全部
                </button>
              </div>
              <ul className="adminAuditList">
                {audits.map((event) => (
                  <li key={event.id}>
                    <strong>{event.actor}</strong>
                    <span className="adminActionChip">{event.action}</span>
                    <span className="adminMuted">{event.target}</span>
                    <time>{event.createdAt}</time>
                  </li>
                ))}
                {!audits.length && <li className="adminMuted">暂无审计事件</li>}
              </ul>
            </section>
          </div>
        </>
      )}

      <section className="adminShortcutRow">
        {shortcuts.map((item) => (
          <button key={item.title} type="button" className="adminShortcut" onClick={item.action}>
            <span className="adminShortcutIcon">{item.icon}</span>
            <strong>{item.title}</strong>
            <span>{item.desc}</span>
          </button>
        ))}
      </section>
    </div>
  )
}
