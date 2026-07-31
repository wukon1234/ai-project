import { useCallback, useEffect, useMemo, useState } from 'react'
import AdminAcl from './AdminAcl'
import AdminAudit from './AdminAudit'
import AdminDashboard from './AdminDashboard'
import AdminIngest from './AdminIngest'
import AdminLibraries from './AdminLibraries'
import AdminLayout from './AdminLayout'
import AdminLogin from './AdminLogin'
import AdminModels from './AdminModels'
import AdminRoles from './AdminRoles'
import AdminUsers from './AdminUsers'
import { canAccessAdminView, clearAdminSession, logoutAdmin, readAdminUser } from './auth'
import type { AdminUser, AdminView } from './types'
import './admin.css'

type Props = {
  onBackToUser: () => void
}

function readQuery() {
  return new URLSearchParams(window.location.search)
}

function writeAdminQuery(view: AdminView, extra?: Record<string, string>) {
  const url = new URL(window.location.href)
  url.searchParams.set('app', 'admin')
  url.searchParams.set('adminView', view)
  ;['action', 'library', 'status'].forEach((key) => url.searchParams.delete(key))
  if (extra) {
    Object.entries(extra).forEach(([k, v]) => {
      if (v) url.searchParams.set(k, v)
      else url.searchParams.delete(k)
    })
  }
  window.history.replaceState({}, '', url.pathname + url.search + url.hash)
}

export default function AdminApp({ onBackToUser }: Props) {
  const [user, setUser] = useState<AdminUser | null>(() => readAdminUser())
  const [collapsed, setCollapsed] = useState(false)
  const query = readQuery()
  const initialView = (query.get('adminView') as AdminView | null) || 'dashboard'
  const [view, setView] = useState<AdminView>(
    user && canAccessAdminView(user.role, initialView) ? initialView : 'dashboard',
  )
  const [createLibrary, setCreateLibrary] = useState(() => query.get('action') === 'create')
  const [libraryParam, setLibraryParam] = useState(() => query.get('library') || '')
  const [statusParam, setStatusParam] = useState(() => query.get('status') || '')

  useEffect(() => {
    const onUnauthorized = () => setUser(null)
    window.addEventListener('zn-admin-unauthorized', onUnauthorized)
    return () => window.removeEventListener('zn-admin-unauthorized', onUnauthorized)
  }, [])

  const navigate = useCallback(
    (next: AdminView, extra?: Record<string, string>) => {
      if (user && !canAccessAdminView(user.role, next)) {
        setView('dashboard')
        writeAdminQuery('dashboard')
        setLibraryParam('')
        setStatusParam('')
        setCreateLibrary(false)
        return
      }
      setView(next)
      writeAdminQuery(next, extra)
      setCreateLibrary(next === 'libraries' && extra?.action === 'create')
      setLibraryParam(extra?.library || '')
      setStatusParam(extra?.status || '')
    },
    [user],
  )

  const consumeCreateQuery = useCallback(() => {
    setCreateLibrary(false)
    const url = new URL(window.location.href)
    url.searchParams.delete('action')
    window.history.replaceState({}, '', url.pathname + url.search + url.hash)
  }, [])

  const content = useMemo(() => {
    if (!user) return null
    if (view === 'dashboard') {
      return (
        <AdminDashboard userName={user.name} role={user.role} onNavigate={navigate} />
      )
    }
    if (view === 'libraries') {
      return (
        <AdminLibraries
          openCreate={createLibrary}
          onConsumedCreateQuery={consumeCreateQuery}
          onOpenAcl={(library) => navigate('acl', { library })}
          onOpenIngest={(library) => navigate('ingest', { library })}
        />
      )
    }
    if (view === 'ingest') {
      return <AdminIngest initialLibrary={libraryParam} initialStatus={statusParam} />
    }
    if (view === 'acl') {
      return <AdminAcl initialLibrary={libraryParam} />
    }
    if (view === 'users') {
      return <AdminUsers initialStatus={statusParam} />
    }
    if (view === 'roles') {
      return <AdminRoles />
    }
    if (view === 'models') {
      return <AdminModels />
    }
    if (view === 'audit') {
      return <AdminAudit role={user.role} />
    }
    return null
  }, [user, view, navigate, createLibrary, consumeCreateQuery, libraryParam, statusParam])

  if (!user) {
    return (
      <AdminLogin
        onSuccess={(nextUser) => {
          setUser(nextUser)
          setView('dashboard')
          writeAdminQuery('dashboard')
        }}
        onBackToUser={onBackToUser}
      />
    )
  }

  return (
    <AdminLayout
      user={user}
      view={view}
      collapsed={collapsed}
      onToggleCollapse={() => setCollapsed((v) => !v)}
      onNavigate={(next) => navigate(next)}
      onLogout={() => {
        void logoutAdmin().finally(() => {
          clearAdminSession()
          setUser(null)
        })
      }}
      onBackToUser={onBackToUser}
    >
      {content}
    </AdminLayout>
  )
}
