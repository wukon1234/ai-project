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
import UsageStatsPage from './UsageStatsPage'
import { clearTokens, logout as logoutApi, me } from './api'
import './App.css'

type AppView = 'chat' | 'search' | 'browse' | 'history' | 'profile' | 'favorites' | 'help' | 'stats'

function App() {
  const [authed, setAuthed] = useState(false)
  const [booting, setBooting] = useState(true)
  const [view, setView] = useState<AppView>('chat')
  const [activeDoc, setActiveDoc] = useState<SourceDoc | null>(null)
  const [chatSeed, setChatSeed] = useState<string | undefined>(undefined)
  const [chatSessionId, setChatSessionId] = useState<number | undefined>(undefined)

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
      try {
        await me()
        if (alive) setAuthed(true)
      } catch (_err) {
        clearTokens()
        if (alive) setAuthed(false)
      } finally {
        if (alive) setBooting(false)
      }
    }
    bootstrap()
    return () => {
      alive = false
    }
  }, [])

  if (booting) {
    return <div style={{ padding: 24 }}>加载中...</div>
  }

  if (!authed) {
    return <AuthPage onSuccess={loginSuccess} />
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
