import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Info, Plus, Search } from 'lucide-react'
import { AdminApiError, USE_ADMIN_MOCK, realAdminApi } from './api'
import { loadLibraries, nowStamp, saveLibraries } from './mock'
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

const EMPTY_FORM: FormState = {
  code: '',
  name: '',
  description: '',
  tagsText: '',
}

const CODE_RE = /^[a-z][a-z0-9_-]*$/

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
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [error, setError] = useState<string | null>(null)
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
    onConsumedCreateQuery?.()
  }, [openCreate, onConsumedCreateQuery])

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

  function persist(next: LibraryRecord[]) {
    setLibraries(next)
    saveLibraries(next)
  }

  function openCreateModal() {
    setCreating(true)
    setEditing(null)
    setForm(EMPTY_FORM)
    setError(null)
  }

  function openEdit(item: LibraryRecord) {
    setEditing(item)
    setCreating(false)
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
          <p className="adminMuted">管理 kb_library：创建、编辑与运营入口</p>
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
                      <button type="button" onClick={() => onOpenIngest(item.code)}>
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
              <div className="adminModalActions">
                <button type="button" className="adminGhostBtn" onClick={closeModal}>
                  取消
                </button>
                <button type="submit" className="adminBtnPrimary" disabled={saving}>
                  {saving ? '提交中…' : creating ? '创建' : '保存'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
