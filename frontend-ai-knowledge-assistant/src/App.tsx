import { useEffect, useState } from 'react'
import AuthPage from './AuthPage'
import ChatPage from './ChatPage'
import DocumentReader, { type SourceDoc } from './DocumentReader'
import FavoritesPage from './FavoritesPage'
import HelpPage from './HelpPage'
import HistoryPage from './HistoryPage'
import KnowledgeBrowse from './KnowledgeBrowse'
import KnowledgeSearch from './KnowledgeSearch'
import ProfilePage from './ProfilePage'
import ShareView from './ShareView'
import UsageStatsPage from './UsageStatsPage'
import AdminApp from './admin/AdminApp'
import { clearTokens, logout as logoutApi, me, saveTokens } from './api'
import './App.css'

type AppView = 'chat' | 'search' | 'browse' | 'history' | 'profile' | 'favorites' | 'help' | 'stats'
type AppSurface = 'user' | 'admin'

type ShareState = { kind: 'session' | 'document'; token: string } | null

const SURFACE_KEY = 'zn-app-surface'

function readSurface(): AppSurface {
  const params = new URLSearchParams(window.location.search)
  const app = params.get('app')
  if (app === 'admin') return 'admin'
  if (app === 'user') return 'user'
  return localStorage.getItem(SURFACE_KEY) === 'admin' ? 'admin' : 'user'
}

function writeSurface(surface: AppSurface) {
  localStorage.setItem(SURFACE_KEY, surface)
  const url = new URL(window.location.href)
  if (surface === 'admin') {
    url.searchParams.set('app', 'admin')
  } else {
    url.searchParams.delete('app')
    url.searchParams.delete('adminView')
    url.searchParams.delete('action')
    url.searchParams.delete('library')
    url.searchParams.delete('status')
  }
  window.history.replaceState({}, '', url.pathname + url.search + url.hash)
}

function consumeBootstrapParams() {
  const params = new URLSearchParams(window.location.search)
  const accessToken = params.get('accessToken')
  const refreshToken = params.get('refreshToken')
  const view = params.get('view')
  const token = params.get('token')
  let share: ShareState = null
  if (view === 'share-session' && token) share = { kind: 'session', token }
  if (view === 'share-document' && token) share = { kind: 'document', token }

  if (accessToken || refreshToken || share) {
    const url = new URL(window.location.href)
    url.searchParams.delete('accessToken')
    url.searchParams.delete('refreshToken')
    url.searchParams.delete('sso')
    url.searchParams.delete('view')
    url.searchParams.delete('token')
    window.history.replaceState({}, '', url.pathname + url.search + url.hash)
  }

  return { accessToken, refreshToken, share }
}

function App() {
  const [surface, setSurface] = useState<AppSurface>(() => readSurface())
  const [authed, setAuthed] = useState(false)
  const [booting, setBooting] = useState(true)
  const [view, setView] = useState<AppView>('chat')
  const [activeDoc, setActiveDoc] = useState<SourceDoc | null>(null)
  const [chatSeed, setChatSeed] = useState<string | undefined>(undefined)
  const [chatSessionId, setChatSessionId] = useState<number | undefined>(undefined)
  const [share, setShare] = useState<ShareState | null>(null)

  function switchSurface(next: AppSurface) {
    writeSurface(next)
    setSurface(next)
  }

  function loginSuccess() {
    setAuthed(true)
    setView('chat')
  }

  async function logout() {
    try {
      await logoutApi()
    } catch (_err) {
      // ignore logout network errors in UI
    }
    clearTokens()
    setAuthed(false)
    setView('chat')
    setActiveDoc(null)
  }

  useEffect(() => {
    let alive = true
    async function bootstrap() {
      const boot = consumeBootstrapParams()
      if (boot.share) setShare(boot.share)
      if (boot.accessToken) {
        saveTokens(boot.accessToken, boot.refreshToken || undefined)
      }
      try {
        await me()
        if (alive) setAuthed(true)
      } catch (_err) {
        if (!boot.share) clearTokens()
        if (alive && !boot.accessToken) setAuthed(false)
        else if (alive && boot.accessToken) {
          try {
            await me()
            if (alive) setAuthed(true)
          } catch {
            if (alive) setAuthed(false)
          }
        }
      } finally {
        if (alive) setBooting(false)
      }
    }
    bootstrap()
    return () => {
      alive = false
    }
  }, [])

  if (surface === 'admin') {
    return <AdminApp onBackToUser={() => switchSurface('user')} />
  }

  if (booting) {
    return <div style={{ padding: 24 }}>加载中...</div>
  }

  if (share) {
    return (
      <ShareView
        kind={share.kind}
        token={share.token}
        onClose={() => {
          setShare(null)
          setView(authed ? 'chat' : 'chat')
        }}
      />
    )
  }

  if (!authed) {
    return <AuthPage onSuccess={loginSuccess} onOpenAdmin={() => switchSurface('admin')} />
  }

  if (activeDoc) {
    return <DocumentReader doc={activeDoc} onBack={() => setActiveDoc(null)} />
  }

  if (view === 'search') {
    return (
      <KnowledgeSearch
        onAsk={(prompt) => {
          setChatSeed(prompt?.trim() ? prompt : undefined)
          setView('chat')
        }}
        onRead={setActiveDoc}
        onAskAboutDoc={(title) => {
          setChatSeed(`基于「${title}」继续提问：`)
          setView('chat')
        }}
      />
    )
  }

  if (view === 'browse') {
    return (
      <KnowledgeBrowse
        onAsk={(prompt) => {
          setChatSeed(prompt?.trim() ? prompt : undefined)
          setView('chat')
        }}
        onRead={setActiveDoc}
        onBackHome={() => setView('chat')}
      />
    )
  }

  if (view === 'history') {
    return (
      <HistoryPage
        onContinue={(sessionId) => {
          setChatSessionId(sessionId)
          setChatSeed(undefined)
          setView('chat')
        }}
        onAskFirst={() => {
          setChatSessionId(undefined)
          setChatSeed(undefined)
          setView('chat')
        }}
        onBack={() => setView('chat')}
      />
    )
  }

  if (view === 'profile') {
    return (
      <ProfilePage
        onBack={() => setView('chat')}
        onOpenFavorites={() => setView('favorites')}
        onOpenHistory={() => setView('history')}
        onOpenHelp={() => setView('help')}
        onOpenStats={() => setView('stats')}
        onLogout={logout}
      />
    )
  }

  if (view === 'stats') {
    return (
      <UsageStatsPage
        onBack={() => setView('profile')}
        onAskAgain={(question) => {
          setChatSeed(question)
          setView('chat')
        }}
      />
    )
  }

  if (view === 'help') {
    return (
      <HelpPage
        onBack={() => setView('profile')}
        onAsk={() => setView('chat')}
        onOpenSearch={() => setView('search')}
      />
    )
  }

  if (view === 'favorites') {
    return (
      <FavoritesPage
        onBack={() => setView('profile')}
        onRead={setActiveDoc}
        onAsk={(prompt) => {
          setChatSeed(prompt)
          setView('chat')
        }}
      />
    )
  }

  return (
    <ChatPage
      key={`${chatSessionId || 'new'}-${chatSeed || 'default-chat'}`}
      onOpenSource={setActiveDoc}
      onOpenSearch={() => setView('search')}
      onOpenBrowse={() => setView('browse')}
      onOpenHistory={() => setView('history')}
      onOpenProfile={() => setView('profile')}
      initialQuestion={chatSeed}
      initialSessionId={chatSessionId}
    />
  )
}

export default App
