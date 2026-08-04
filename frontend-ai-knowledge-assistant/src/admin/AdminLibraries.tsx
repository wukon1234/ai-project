import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Info, Plus, Search } from 'lucide-react'
import { AdminApiError, USE_ADMIN_MOCK, realAdminApi } from './api'
import { loadIngestTasks, loadLibraries, nowStamp, saveLibraries } from './mock'
import type { LibraryRecord } from './types'
import { useAdminToast } from './useAdminToast'

type Props = {
  openCreate?: boolean
  onOpenAcl: (libraryCode: string) => void
  onOpenIngest: (libraryCode: string) => void
  onConsumedCreateQuery?: () => void
}

type FormState = {
  code: string
  name: string
  description: string
  tagsText: string
}

type LibraryDocItem = {
  id: string
  title: string
  category: string
  pages: number
  updatedAt?: string
  summary?: string
}

type DocMeta = {
  id: number
  title: string
  knowledgeBase: string
  fileType?: string
  pages?: number
  summary?: string
  updatedAt?: string
  category?: string
}

const EMPTY_FORM: FormState = {
  code: '',
  name: '',
  description: '',
  tagsText: '',
}

const CODE_RE = /^[a-z][a-z0-9_-]*$/

const CATEGORY_LABEL: Record<string, string> = {
  faq: 'FAQ',
  policy: '制度',
  manual: '手册',
}

function parseTags(text: string) {
  return text
    .split(/[,，\s]+/)
    .map((t) => t.trim())
    .filter(Boolean)
    .map((t) => (t.startsWith('#') ? t : `#${t}`))
}

function errMsg(err: unknown, fallback: string) {
  return err instanceof AdminApiError ? err.message : fallback
}

export default function AdminLibraries({
  openCreate = false,
  onOpenAcl,
  onOpenIngest,
  onConsumedCreateQuery,
}: Props) {
  const [libraries, setLibraries] = useState<LibraryRecord[]>(() =>
    USE_ADMIN_MOCK ? loadLibraries() : [],
  )
  const [loading, setLoading] = useState(!USE_ADMIN_MOCK)
  const [keyword, setKeyword] = useState('')
  const [editing, setEditing] = useState<LibraryRecord | null>(null)
  const [creating, setCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [error, setError] = useState<string | null>(null)
  const [docsLib, setDocsLib] = useState<LibraryRecord | null>(null)
  const [docs, setDocs] = useState<LibraryDocItem[]>([])
  const [docsLoading, setDocsLoading] = useState(false)
  const [docsKeyword, setDocsKeyword] = useState('')
  const [preview, setPreview] = useState<{ meta: DocMeta; fileUrl: string | null } | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const { showToast, toastNode } = useAdminToast()

  const refresh = useCallback(async () => {
    if (USE_ADMIN_MOCK) {
      setLibraries(loadLibraries())
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const list = await realAdminApi.listLibraries()
      setLibraries(list)
    } catch (err) {
      showToast(errMsg(err, '知识库加载失败'))
    } finally {
      setLoading(false)
    }
  }, [showToast])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    if (!openCreate) return
    setCreating(true)
    setEditing(null)
    setForm(EMPTY_FORM)
    setError(null)
    setConfirmDelete(false)
    onConsumedCreateQuery?.()
  }, [openCreate, onConsumedCreateQuery])

  useEffect(() => {
    return () => {
      if (preview?.fileUrl) URL.revokeObjectURL(preview.fileUrl)
    }
  }, [preview?.fileUrl])

  const filtered = useMemo(() => {
    const q = keyword.trim().toLowerCase()
    if (!q) return libraries
    return libraries.filter(
      (item) =>
        item.name.toLowerCase().includes(q) ||
        item.code.toLowerCase().includes(q) ||
        item.description.toLowerCase().includes(q),
    )
  }, [libraries, keyword])

  const filteredDocs = useMemo(() => {
    const q = docsKeyword.trim().toLowerCase()
    if (!q) return docs
    return docs.filter(
      (d) =>
        d.title.toLowerCase().includes(q) ||
        (d.summary || '').toLowerCase().includes(q),
    )
  }, [docs, docsKeyword])

  function persist(next: LibraryRecord[]) {
    setLibraries(next)
    saveLibraries(next)
  }

  function openCreateModal() {
    setCreating(true)
    setEditing(null)
    setForm(EMPTY_FORM)
    setError(null)
    setConfirmDelete(false)
  }

  function openEdit(item: LibraryRecord) {
    setEditing(item)
    setCreating(false)
    setConfirmDelete(false)
    setForm({
      code: item.code,
      name: item.name,
      description: item.description,
      tagsText: item.tags.join(' '),
    })
    setError(null)
  }

  function closeModal() {
    setCreating(false)
    setEditing(null)
    setError(null)
    setConfirmDelete(false)
  }

  async function openDocs(item: LibraryRecord) {
    setDocsLib(item)
    setDocsKeyword('')
    setDocs([])
    setDocsLoading(true)
    try {
      if (USE_ADMIN_MOCK) {
        const tasks = loadIngestTasks().filter((t) => t.libraryCode === item.code)
        setDocs(
          tasks.map((t) => ({
            id: t.docId,
            title: t.title,
            category: t.category,
            pages: t.pages || 0,
            updatedAt: t.createdAt,
            summary: t.summary,
          })),
        )
      } else {
        const page = await realAdminApi.listLibraryDocuments(item.code, { size: 100 })
        setDocs(
          (page.list || []).map((d) => ({
            id: String(d.id),
            title: d.title,
            category: d.category,
            pages: d.pages || 0,
            updatedAt: d.updatedAt,
            summary: d.summary,
          })),
        )
      }
    } catch (err) {
      showToast(errMsg(err, '文档列表加载失败'))
      setDocsLib(null)
    } finally {
      setDocsLoading(false)
    }
  }

  function closeDocs() {
    setDocsLib(null)
    setDocs([])
    setDocsKeyword('')
  }

  async function openPreview(docId: string, fallbackTitle: string) {
    setPreviewLoading(true)
    try {
      if (USE_ADMIN_MOCK) {
        const task = loadIngestTasks().find((t) => t.docId === docId)
        setPreview({
          meta: {
            id: Number(docId.replace(/\D/g, '') || 0) || 0,
            title: task?.title || fallbackTitle,
            knowledgeBase: task?.libraryName || docsLib?.name || '',
            fileType: task?.fileType,
            pages: task?.pages,
            summary: task?.summary || 'Mock 模式仅展示元信息，无原文文件。',
            updatedAt: task?.createdAt,
            category: task?.category,
          },
          fileUrl: null,
        })
        return
      }
      const meta = await realAdminApi.getDocumentMeta(docId)
      let fileUrl: string | null = null
      try {
        const blob = await realAdminApi.getDocumentFileBlob(docId)
        fileUrl = URL.createObjectURL(blob)
      } catch {
        // 无原文时仍展示元信息与摘要
      }
      if (preview?.fileUrl) URL.revokeObjectURL(preview.fileUrl)
      setPreview({ meta, fileUrl })
    } catch (err) {
      showToast(errMsg(err, '打开文档失败'))
    } finally {
      setPreviewLoading(false)
    }
  }

  function closePreview() {
    if (preview?.fileUrl) URL.revokeObjectURL(preview.fileUrl)
    setPreview(null)
  }

  async function onDelete() {
    if (!editing) return
    if (editing.docCount > 0) {
      setError(`库内仍有 ${editing.docCount} 份文档，请先清空后再删除`)
      setConfirmDelete(false)
      return
    }
    if (USE_ADMIN_MOCK) {
      persist(libraries.filter((item) => item.id !== editing.id))
      showToast('知识库已删除')
      closeModal()
      return
    }
    setDeleting(true)
    try {
      await realAdminApi.deleteLibrary(editing.code)
      showToast('知识库已删除')
      closeModal()
      await refresh()
    } catch (err) {
      setError(errMsg(err, '删除失败'))
      setConfirmDelete(false)
    } finally {
      setDeleting(false)
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    const name = form.name.trim()
    const code = form.code.trim().toLowerCase()
    const description = form.description.trim()
    const tags = parseTags(form.tagsText)

    if (!name) {
      setError('请填写知识库名称')
      return
    }

    if (creating) {
      if (!code) {
        setError('请填写知识库 code')
        return
      }
      if (!CODE_RE.test(code)) {
        setError('code 需为小写字母开头，仅含小写字母/数字/_/-')
        return
      }
      if (libraries.some((item) => item.code === code)) {
        setError('code 已存在，请换一个')
        return
      }

      if (USE_ADMIN_MOCK) {
        const next: LibraryRecord = {
          id: String(Date.now()),
          code,
          name,
          description,
          tags,
          docCount: 0,
          updatedAt: nowStamp(),
        }
        persist([next, ...libraries])
        showToast('知识库已创建')
        closeModal()
        return
      }

      setSaving(true)
      try {
        await realAdminApi.createLibrary({ code, name, description, tags })
        showToast('知识库已创建')
        closeModal()
        await refresh()
      } catch (err) {
        setError(errMsg(err, '创建失败'))
      } finally {
        setSaving(false)
      }
      return
    }

    if (!editing) return

    if (USE_ADMIN_MOCK) {
      const next = libraries.map((item) =>
        item.id === editing.id
          ? {
              ...item,
              name,
              description,
              tags,
              updatedAt: nowStamp(),
            }
          : item,
      )
      persist(next)
      showToast('知识库已更新')
      closeModal()
      return
    }

    setSaving(true)
    try {
      await realAdminApi.updateLibrary(editing.code, { name, description, tags })
      showToast('知识库已更新')
      closeModal()
      await refresh()
    } catch (err) {
      setError(errMsg(err, '更新失败'))
    } finally {
      setSaving(false)
    }
  }

  const modalOpen = creating || !!editing

  return (
    <div className="adminPage">
      {toastNode}
      <div className="adminNotice">
        <Info size={16} />
        <span>用户侧仅只读浏览与问答；创建 / 上传 / 权限仅在此管理后台。</span>
      </div>

      <div className="adminPageHeader">
        <div>
          <h1>知识库</h1>
          <p className="adminMuted">管理 kb_library：创建、编辑、查看文档与运营入口</p>
        </div>
        <button type="button" className="adminBtnPrimary" onClick={openCreateModal}>
          <Plus size={16} />
          创建知识库
        </button>
      </div>

      <div className="adminToolbar adminToolbarWrap">
        <label className="adminInlineField adminInlineField--grow">
          关键词
          <span className="adminSearch">
            <Search size={16} />
            <input
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="搜索名称 / code…"
            />
          </span>
        </label>
      </div>

      {loading ? (
        <div className="adminEmpty">
          <h2>加载中…</h2>
          <p className="adminMuted">正在获取知识库列表</p>
        </div>
      ) : filtered.length === 0 ? (
        <div className="adminEmpty">
          <h2>{libraries.length === 0 ? '还没有知识库' : '没有匹配结果'}</h2>
          <p className="adminMuted">
            {libraries.length === 0 ? '创建第一个知识库，开始运营企业知识资产。' : '试试其他关键词。'}
          </p>
          {libraries.length === 0 && (
            <button type="button" className="adminBtnPrimary" onClick={openCreateModal}>
              创建第一个知识库
            </button>
          )}
        </div>
      ) : (
        <div className="adminTableWrap">
          <table className="adminTable">
            <thead>
              <tr>
                <th>名称</th>
                <th>code</th>
                <th>简介</th>
                <th>标签</th>
                <th>文档数</th>
                <th>最近更新</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((item) => (
                <tr key={item.id}>
                  <td>
                    <strong>{item.name}</strong>
                  </td>
                  <td>
                    <code>{item.code}</code>
                  </td>
                  <td>
                    <div className="adminClamp">{item.description || '—'}</div>
                  </td>
                  <td>
                    <div className="adminTagRow">
                      {item.tags.length
                        ? item.tags.map((tag) => (
                            <span key={tag} className="adminTag">
                              {tag}
                            </span>
                          ))
                        : '—'}
                    </div>
                  </td>
                  <td>{item.docCount}</td>
                  <td>{item.updatedAt}</td>
                  <td>
                    <div className="adminRowActions">
                      <button type="button" onClick={() => openEdit(item)}>
                        编辑
                      </button>
                      <button type="button" onClick={() => onOpenAcl(item.code)}>
                        权限配置
                      </button>
                      <button type="button" onClick={() => onOpenIngest(item.code)}>
                        上传文档
                      </button>
                      <button type="button" onClick={() => void openDocs(item)}>
                        查看文档
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modalOpen && (
        <div className="adminModalMask" role="presentation" onClick={closeModal}>
          <div
            className="adminModal"
            role="dialog"
            aria-modal="true"
            aria-label={creating ? '创建知识库' : '编辑知识库'}
            onClick={(e) => e.stopPropagation()}
          >
            <h2>{creating ? '创建知识库' : '编辑知识库'}</h2>
            <form onSubmit={onSubmit} className="adminForm">
              <label>
                code
                <input
                  value={form.code}
                  onChange={(e) => setForm((s) => ({ ...s, code: e.target.value }))}
                  placeholder="legal"
                  disabled={!creating}
                  required={creating}
                />
                <span className="adminFieldHint">
                  {creating ? '创建后不可修改；小写字母开头' : '创建后不可修改'}
                </span>
              </label>
              <label>
                名称
                <input
                  value={form.name}
                  onChange={(e) => setForm((s) => ({ ...s, name: e.target.value }))}
                  placeholder="法务知识库"
                  required
                />
              </label>
              <label>
                简介
                <textarea
                  value={form.description}
                  onChange={(e) => setForm((s) => ({ ...s, description: e.target.value }))}
                  rows={3}
                  placeholder="简要说明库内知识范围"
                />
              </label>
              <label>
                标签
                <input
                  value={form.tagsText}
                  onChange={(e) => setForm((s) => ({ ...s, tagsText: e.target.value }))}
                  placeholder="#制度 #FAQ（空格或逗号分隔）"
                />
              </label>
              {error && <p className="adminFormError">{error}</p>}
              <div className="adminModalActions adminModalActions--split">
                {!creating && editing && (
                  <button
                    type="button"
                    className="adminBtnDanger"
                    disabled={saving || deleting}
                    onClick={() => {
                      setError(null)
                      setConfirmDelete(true)
                    }}
                  >
                    删除知识库
                  </button>
                )}
                <div className="adminModalActionsRight">
                  <button type="button" className="adminGhostBtn" onClick={closeModal}>
                    取消
                  </button>
                  <button type="submit" className="adminBtnPrimary" disabled={saving || deleting}>
                    {saving ? '提交中…' : creating ? '创建' : '保存'}
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}

      {confirmDelete && editing && (
        <div className="adminModalMask" role="presentation" onClick={() => setConfirmDelete(false)}>
          <div
            className="adminModal"
            role="dialog"
            aria-modal="true"
            aria-label="确认删除知识库"
            onClick={(e) => e.stopPropagation()}
          >
            <h2>删除知识库</h2>
            <p>
              确认删除「{editing.name}」（<code>{editing.code}</code>）？
              {editing.docCount > 0
                ? ` 当前库内有 ${editing.docCount} 份文档，需先清空后才能删除。`
                : ' 仅空库可删，相关 ACL 将一并清除。'}
            </p>
            <div className="adminModalActions">
              <button type="button" className="adminGhostBtn" onClick={() => setConfirmDelete(false)}>
                取消
              </button>
              <button
                type="button"
                className="adminBtnDanger"
                disabled={deleting || editing.docCount > 0}
                onClick={() => void onDelete()}
              >
                {deleting ? '删除中…' : '确认删除'}
              </button>
            </div>
          </div>
        </div>
      )}

      {docsLib && (
        <div className="adminDrawerMask" role="presentation" onClick={closeDocs}>
          <aside
            className="adminDrawer adminDrawerWide"
            role="dialog"
            aria-label={`${docsLib.name} 文档列表`}
            onClick={(e) => e.stopPropagation()}
          >
            <h2>{docsLib.name} · 文档</h2>
            <p className="adminMuted" style={{ marginTop: -4 }}>
              点击文档查看内容（摘要 / 原文预览）
            </p>
            <label className="adminInlineField" style={{ marginBottom: 12 }}>
              搜索
              <span className="adminSearch">
                <Search size={16} />
                <input
                  value={docsKeyword}
                  onChange={(e) => setDocsKeyword(e.target.value)}
                  placeholder="标题 / 摘要…"
                />
              </span>
            </label>
            {docsLoading || previewLoading ? (
              <p className="adminMuted">{previewLoading ? '正在打开文档…' : '加载中…'}</p>
            ) : filteredDocs.length === 0 ? (
              <div className="adminEmpty" style={{ padding: '24px 0' }}>
                <h2>暂无文档</h2>
                <p className="adminMuted">可先上传文档到该知识库</p>
                <button
                  type="button"
                  className="adminBtnPrimary"
                  onClick={() => {
                    const code = docsLib.code
                    closeDocs()
                    onOpenIngest(code)
                  }}
                >
                  去上传
                </button>
              </div>
            ) : (
              <div className="adminDocList">
                {filteredDocs.map((doc) => (
                  <button
                    key={doc.id}
                    type="button"
                    className="adminDocListItem"
                    onClick={() => void openPreview(doc.id, doc.title)}
                  >
                    <strong>{doc.title}</strong>
                    <span className="adminMuted">
                      {CATEGORY_LABEL[doc.category] || doc.category}
                      {doc.pages ? ` · ${doc.pages} 页` : ''}
                      {doc.updatedAt ? ` · ${doc.updatedAt}` : ''}
                    </span>
                    {doc.summary ? <div className="adminClamp">{doc.summary}</div> : null}
                  </button>
                ))}
              </div>
            )}
            <button type="button" className="adminGhostBtn" style={{ marginTop: 16 }} onClick={closeDocs}>
              关闭
            </button>
          </aside>
        </div>
      )}

      {preview && (
        <div className="adminDrawerMask" role="presentation" onClick={closePreview}>
          <aside
            className="adminDrawer adminDrawerDoc"
            role="dialog"
            aria-label="文档内容"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="adminDrawerHead">
              <div>
                <h2>{preview.meta.title}</h2>
                <p className="adminMuted">
                  {preview.meta.knowledgeBase}
                  {preview.meta.pages ? ` · ${preview.meta.pages} 页` : ''}
                  {preview.meta.category
                    ? ` · ${CATEGORY_LABEL[preview.meta.category] || preview.meta.category}`
                    : ''}
                </p>
              </div>
              <button type="button" className="adminGhostBtn" onClick={closePreview}>
                关闭
              </button>
            </div>
            <dl className="adminDescList">
              <div>
                <dt>摘要</dt>
                <dd>{preview.meta.summary || '—'}</dd>
              </div>
              <div>
                <dt>更新时间</dt>
                <dd>{preview.meta.updatedAt || '—'}</dd>
              </div>
            </dl>
            <div className="adminDocPreview">
              {preview.fileUrl ? (
                <iframe title={preview.meta.title} src={preview.fileUrl} />
              ) : (
                <div className="adminEmpty" style={{ padding: '32px 12px' }}>
                  <h2>暂无原文预览</h2>
                  <p className="adminMuted">可查看上方摘要；原文文件缺失或类型暂不支持内嵌预览。</p>
                </div>
              )}
            </div>
          </aside>
        </div>
      )}
    </div>
  )
}
