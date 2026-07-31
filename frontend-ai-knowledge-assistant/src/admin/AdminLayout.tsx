import type { ReactNode } from 'react'
import {
  BookOpen,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  FileUp,
  KeyRound,
  LayoutDashboard,
  LogOut,
  Moon,
  Settings2,
  Shield,
  Sun,
  Users,
} from 'lucide-react'
import { ROLE_LABEL } from './auth'
import type { AdminRole, AdminUser, AdminView } from './types'
import { useTheme } from '../theme'

type NavItem = {
  view: AdminView
  label: string
  icon: ReactNode
  group: 'workspace' | 'knowledge' | 'account' | 'system'
  sysOnly?: boolean
  auditNote?: boolean
}

const NAV: NavItem[] = [
  { view: 'dashboard', label: '工作台', icon: <LayoutDashboard size={18} />, group: 'workspace' },
  { view: 'libraries', label: '知识库', icon: <BookOpen size={18} />, group: 'knowledge' },
  { view: 'ingest', label: '文档入库', icon: <FileUp size={18} />, group: 'knowledge' },
  { view: 'acl', label: '权限配置', icon: <KeyRound size={18} />, group: 'knowledge' },
  { view: 'users', label: '用户管理', icon: <Users size={18} />, group: 'account', sysOnly: true },
  { view: 'roles', label: '角色配置', icon: <Shield size={18} />, group: 'account', sysOnly: true },
  { view: 'models', label: '模型设置', icon: <Settings2 size={18} />, group: 'system', sysOnly: true },
  {
    view: 'audit',
    label: '审计日志',
    icon: <ClipboardList size={18} />,
    group: 'system',
    auditNote: true,
  },
]

const GROUP_LABEL = {
  workspace: '工作台',
  knowledge: '知识管理',
  account: '账号治理',
  system: '系统设置',
} as const

const VIEW_TITLE: Record<AdminView, string> = {
  dashboard: '工作台',
  libraries: '知识库',
  ingest: '文档入库',
  acl: '权限配置',
  users: '用户管理',
  roles: '角色配置',
  models: '模型设置',
  audit: '审计日志',
}

type Props = {
  user: AdminUser
  view: AdminView
  collapsed: boolean
  onToggleCollapse: () => void
  onNavigate: (view: AdminView) => void
  onLogout: () => void
  onBackToUser: () => void
  children: ReactNode
}

function visibleNav(role: AdminRole) {
  return NAV.filter((item) => !(item.sysOnly && role !== 'SYS_ADMIN'))
}

export default function AdminLayout({
  user,
  view,
  collapsed,
  onToggleCollapse,
  onNavigate,
  onLogout,
  onBackToUser,
  children,
}: Props) {
  const { resolvedTheme, setTheme } = useTheme()
  const items = visibleNav(user.role)
  const groups = (['workspace', 'knowledge', 'account', 'system'] as const).filter((g) =>
    items.some((item) => item.group === g),
  )

  return (
    <div className={`adminShell${collapsed ? ' isCollapsed' : ''}`}>
      <aside className="adminSidebar">
        <div className="adminBrand">
          <div className="adminBrandMark">
            <Shield size={18} />
          </div>
          {!collapsed && (
            <div>
              <strong>智识云</strong>
              <span>管理后台</span>
            </div>
          )}
        </div>

        <nav className="adminNav">
          {groups.map((group) => (
            <div key={group} className="adminNavGroup">
              {!collapsed && <div className="adminNavGroupLabel">{GROUP_LABEL[group]}</div>}
              {items
                .filter((item) => item.group === group)
                .map((item) => (
                  <button
                    key={item.view}
                    type="button"
                    className={`adminNavItem${view === item.view ? ' isActive' : ''}`}
                    onClick={() => onNavigate(item.view)}
                    title={item.label}
                  >
                    {item.icon}
                    {!collapsed && (
                      <span>
                        {item.label}
                        {item.auditNote && user.role === 'KB_ADMIN' ? (
                          <em className="adminNavNote">知识相关</em>
                        ) : null}
                      </span>
                    )}
                  </button>
                ))}
            </div>
          ))}
        </nav>

        <button type="button" className="adminCollapseBtn" onClick={onToggleCollapse}>
          {collapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
          {!collapsed && <span>收起侧栏</span>}
        </button>
      </aside>

      <div className="adminMain">
        <header className="adminTopbar">
          <div className="adminBreadcrumb">
            <span>管理后台</span>
            <span>/</span>
            <strong>{VIEW_TITLE[view]}</strong>
          </div>
          <div className="adminTopActions">
            <button
              type="button"
              className="adminIconBtn"
              onClick={() => setTheme(resolvedTheme === 'dark' ? 'light' : 'dark')}
              aria-label="切换主题"
            >
              {resolvedTheme === 'dark' ? <Sun size={16} /> : <Moon size={16} />}
            </button>
            <div className="adminUserBadge">
              <strong>{user.name}</strong>
              <span>{ROLE_LABEL[user.role]}</span>
            </div>
            <button type="button" className="adminGhostBtn" onClick={onBackToUser}>
              用户端
            </button>
            <button type="button" className="adminGhostBtn" onClick={onLogout}>
              <LogOut size={14} />
              退出
            </button>
          </div>
        </header>
        <main className="adminContent">{children}</main>
      </div>
    </div>
  )
}
