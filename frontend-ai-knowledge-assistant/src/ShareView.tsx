import { useEffect, useState } from 'react'
import { getSharedDocument, getSharedSession } from './api'

type ShareViewProps = {
  kind: 'session' | 'document'
  token: string
  onClose: () => void
}

function ShareView({ kind, token, onClose }: ShareViewProps) {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [session, setSession] = useState<{
    title?: string
    scope?: string
    messages?: Array<{ role?: string; content?: string }>
  } | null>(null)
  const [document, setDocument] = useState<{
    title?: string
    libraryCode?: string
    pages?: number
    summary?: string
  } | null>(null)

  useEffect(() => {
    let alive = true
    ;(async () => {
      setLoading(true)
      setError(null)
      try {
        if (kind === 'session') {
          const data = await getSharedSession(token)
          if (alive) setSession(data)
        } else {
          const data = await getSharedDocument(token)
          if (alive) setDocument(data)
        }
      } catch (err) {
        if (alive) setError(err instanceof Error ? err.message : '分享内容加载失败')
      } finally {
        if (alive) setLoading(false)
      }
    })()
    return () => {
      alive = false
    }
  }, [kind, token])

  return (
    <div className="sharePage">
      <header className="shareHeader">
        <div>
          <h1>分享只读预览</h1>
          <p>{kind === 'session' ? '会话分享' : '文档分享'}</p>
        </div>
        <button type="button" className="shareClose" onClick={onClose}>
          关闭
        </button>
      </header>
      <main className="shareBody">
        {loading ? <p>加载中…</p> : null}
        {error ? <p className="shareError">{error}</p> : null}
        {!loading && !error && kind === 'session' ? (
          <section>
            <h2>{session?.title || '未命名会话'}</h2>
            <p className="shareMeta">范围：{session?.scope || '—'}</p>
            <div className="shareMessages">
              {(session?.messages || []).map((m, i) => (
                <article key={i} className={`shareMsg shareMsg-${m.role || 'assistant'}`}>
                  <strong>{m.role === 'user' ? '用户' : '助手'}</strong>
                  <p>{m.content}</p>
                </article>
              ))}
            </div>
          </section>
        ) : null}
        {!loading && !error && kind === 'document' ? (
          <section>
            <h2>{document?.title || '未命名文档'}</h2>
            <p className="shareMeta">
              知识库 {document?.libraryCode || '—'} · {document?.pages ?? 0} 页
            </p>
            <p>{document?.summary || '暂无摘要'}</p>
          </section>
        ) : null}
      </main>
    </div>
  )
}

export default ShareView
