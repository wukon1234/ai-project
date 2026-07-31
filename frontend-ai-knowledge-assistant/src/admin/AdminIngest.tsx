import { useCallback, useEffect, useRef, useState, type DragEvent } from 'react'
import {
  File,
  FileImage,
  FileSpreadsheet,
  FileText,
  Presentation,
  Upload,
  X,
} from 'lucide-react'
import { AdminApiError, USE_ADMIN_MOCK, realAdminApi } from './api'
import {
  loadIngestTasks,
  loadLibraries,
  nowStamp,
  saveIngestTasks,
  saveLibraries,
} from './mock'
import type {
  DocCategory,
  FileType,
  IngestTask,
  IngestTaskStatus,
  LibraryRecord,
} from './types'
import { useAdminToast } from './useAdminToast'

type Props = {
  initialLibrary?: string
  initialStatus?: string
}

type QueueItem = {
  id: string
  file: File
  fileType: FileType
  error?: string
}

/** 与后端 IngestService 白名单对齐：仅 PDF / png / jpg / jpeg */
const ALLOWED_EXT: Record<string, FileType> = {
  pdf: 'pdf',
  png: 'image',
  jpg: 'image',
  jpeg: 'image',
}

const MAX_BYTES = 50 * 1024 * 1024

const CATEGORY_LABEL: Record<DocCategory, string> = {
  faq: 'FAQ',
  policy: '制度',
  manual: '手册',
}

function detectType(name: string): FileType {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  return ALLOWED_EXT[ext] || 'unknown'
}

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function typeIcon(type: FileType) {
  const props = { size: 16 as const }
  if (type === 'pdf' || type === 'word') return <FileText {...props} />
  if (type === 'excel') return <FileSpreadsheet {...props} />
  if (type === 'ppt') return <Presentation {...props} />
  if (type === 'image') return <FileImage {...props} />
  return <File {...props} />
}

function statusClass(status: string) {
  return `adminStatus adminStatus--${status.toLowerCase()}`
}

const INGEST_STATUS_LABELS: Record<string, string> = {
  PENDING: '等待中',
  RUNNING: '处理中',
  SUCCESS: '成功',
  FAILED: '失败',
}

function ingestStatusLabel(status: string) {
  return INGEST_STATUS_LABELS[status] || status
}

function errMsg(err: unknown, fallback: string) {
  return err instanceof AdminApiError ? err.message : fallback
}

export default function AdminIngest({ initialLibrary = '', initialStatus = '' }: Props) {
  const [libraries, setLibraries] = useState<LibraryRecord[]>(() =>
    USE_ADMIN_MOCK ? loadLibraries() : [],
  )
  const [libraryCode, setLibraryCode] = useState(initialLibrary || '')
  const [category, setCategory] = useState<DocCategory>('manual')
  const [queue, setQueue] = useState<QueueItem[]>([])
  const [tasks, setTasks] = useState<IngestTask[]>(() =>
    USE_ADMIN_MOCK ? loadIngestTasks() : [],
  )
  const [tasksLoading, setTasksLoading] = useState(!USE_ADMIN_MOCK)
  const [uploading, setUploading] = useState(false)
  const [filterLib, setFilterLib] = useState(initialLibrary || '')
  const [filterStatus, setFilterStatus] = useState<IngestTaskStatus | ''>(
    (initialStatus as IngestTaskStatus) || '',
  )
  const [keyword, setKeyword] = useState('')
  const [detail, setDetail] = useState<IngestTask | null>(null)
  const [errorTask, setErrorTask] = useState<IngestTask | null>(null)
  const [confirmReindex, setConfirmReindex] = useState<IngestTask | null>(null)
  const [dragging, setDragging] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)
  const { showToast, toastNode } = useAdminToast()

  const refreshLibraries = useCallback(async () => {
    if (USE_ADMIN_MOCK) {
      const libs = loadLibraries()
      setLibraries(libs)
      setLibraryCode((prev) => prev || initialLibrary || libs[0]?.code || '')
      return
    }
    try {
      const libs = await realAdminApi.listLibraries()
      setLibraries(libs)
      setLibraryCode((prev) => prev || initialLibrary || libs[0]?.code || '')
    } catch (err) {
      showToast(errMsg(err, '知识库加载失败'))
    }
  }, [initialLibrary, showToast])

  const refreshTasks = useCallback(async () => {
    if (USE_ADMIN_MOCK) {
      setTasks(loadIngestTasks())
      setTasksLoading(false)
      return
    }
    setTasksLoading(true)
    try {
      const page = await realAdminApi.listIngestTasks({
        libraryCode: filterLib || undefined,
        status: filterStatus || undefined,
        keyword: keyword.trim() || undefined,
        page: 1,
        size: 100,
      })
      setTasks(page.records)
    } catch (err) {
      showToast(errMsg(err, '入库任务加载失败'))
    } finally {
      setTasksLoading(false)
    }
  }, [filterLib, filterStatus, keyword, showToast])

  useEffect(() => {
    void refreshLibraries()
  }, [refreshLibraries])

  useEffect(() => {
    void refreshTasks()
  }, [refreshTasks])

  useEffect(() => {
    if (initialLibrary) {
      setLibraryCode(initialLibrary)
      setFilterLib(initialLibrary)
    }
  }, [initialLibrary])

  useEffect(() => {
    if (initialStatus) setFilterStatus(initialStatus as IngestTaskStatus)
  }, [initialStatus])

  // MOCK: fake progress timer
  useEffect(() => {
    if (!USE_ADMIN_MOCK) return
    const timer = window.setInterval(() => {
      setTasks((prev) => {
        let changed = false
        const next = prev.map((task) => {
          if (task.status === 'PENDING') {
            changed = true
            return { ...task, status: 'RUNNING' as const, progress: 8 }
          }
          if (task.status === 'RUNNING') {
            changed = true
            const progress = Math.min(100, task.progress + 18)
            if (progress >= 100) {
              return {
                ...task,
                status: 'SUCCESS' as const,
                progress: 100,
                pages: task.pages || Math.floor(Math.random() * 40) + 5,
                summary: task.summary || `「${task.title}」已完成解析与向量化（Mock）。`,
              }
            }
            return { ...task, progress }
          }
          return task
        })
        if (changed) saveIngestTasks(next)
        return changed ? next : prev
      })
    }, 1600)
    return () => window.clearInterval(timer)
  }, [])

  // REAL: poll busy tasks every 2s
  useEffect(() => {
    if (USE_ADMIN_MOCK) return
    const busy = tasks.some((t) => t.status === 'PENDING' || t.status === 'RUNNING')
    if (!busy) return
    const timer = window.setInterval(() => {
      void (async () => {
        try {
          const page = await realAdminApi.listIngestTasks({
            libraryCode: filterLib || undefined,
            status: filterStatus || undefined,
            keyword: keyword.trim() || undefined,
            page: 1,
            size: 100,
          })
          setTasks(page.records)
        } catch {
          /* keep current list on poll failure */
        }
      })()
    }, 2000)
    return () => window.clearInterval(timer)
  }, [tasks, filterLib, filterStatus, keyword])

  useEffect(() => {
    const busy = tasks.some((t) => t.status === 'PENDING' || t.status === 'RUNNING')
    if (!busy) return
    const onLeave = (e: BeforeUnloadEvent) => {
      e.preventDefault()
      e.returnValue = ''
    }
    window.addEventListener('beforeunload', onLeave)
    return () => window.removeEventListener('beforeunload', onLeave)
  }, [tasks])

  function persist(next: IngestTask[]) {
    setTasks(next)
    saveIngestTasks(next)
  }

  function addFiles(fileList: FileList | File[]) {
    if (!libraryCode) {
      showToast('请先选择目标知识库')
      return
    }
    const incoming = Array.from(fileList).map((file) => {
      const fileType = detectType(file.name)
      let error: string | undefined
      if (fileType === 'unknown') error = '类型不在白名单'
      else if (file.size > MAX_BYTES) error = '超过 50MB 限制'
      return { id: `${Date.now()}-${file.name}-${Math.random()}`, file, fileType, error }
    })
    setQueue((prev) => [...prev, ...incoming])
  }

  function onDrop(e: DragEvent) {
    e.preventDefault()
    setDragging(false)
    if (e.dataTransfer.files?.length) addFiles(e.dataTransfer.files)
  }

  async function startIngest() {
    if (!libraryCode) {
      showToast('请先选择目标知识库')
      return
    }
    const valid = queue.filter((item) => !item.error)
    if (!valid.length) {
      showToast('没有可入库的有效文件')
      return
    }

    if (USE_ADMIN_MOCK) {
      const lib = libraries.find((l) => l.code === libraryCode)
      const created: IngestTask[] = valid.map((item) => ({
        id: `t-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        docId: `d-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        title: item.file.name,
        libraryCode,
        libraryName: lib?.name || libraryCode,
        fileType: item.fileType,
        category,
        status: 'PENDING',
        progress: 0,
        createdAt: nowStamp(),
      }))
      persist([...created, ...tasks])
      const libs = loadLibraries().map((l) =>
        l.code === libraryCode
          ? { ...l, docCount: l.docCount + created.length, updatedAt: nowStamp() }
          : l,
      )
      saveLibraries(libs)
      setLibraries(libs)
      setQueue((prev) => prev.filter((item) => item.error))
      showToast('已加入入库队列')
      return
    }

    setUploading(true)
    let ok = 0
    let fail = 0
    try {
      for (const item of valid) {
        try {
          await realAdminApi.uploadDocument(item.file, libraryCode, category)
          ok += 1
        } catch {
          fail += 1
        }
      }
      setQueue((prev) => prev.filter((item) => item.error))
      await refreshTasks()
      await refreshLibraries()
      if (fail === 0) showToast(`已上传 ${ok} 个文件`)
      else showToast(`成功 ${ok}，失败 ${fail}`)
    } catch (err) {
      showToast(errMsg(err, '上传失败'))
    } finally {
      setUploading(false)
    }
  }

  async function retryTask(task: IngestTask) {
    if (USE_ADMIN_MOCK) {
      persist(
        tasks.map((t) =>
          t.id === task.id
            ? { ...t, status: 'PENDING', progress: 0, errorMsg: undefined }
            : t,
        ),
      )
      showToast('已重新加入队列')
      return
    }
    try {
      await realAdminApi.retryIngest(task.id)
      showToast('已重新加入队列')
      await refreshTasks()
    } catch (err) {
      showToast(errMsg(err, '重试失败'))
    }
  }

  async function confirmRebuild() {
    if (!confirmReindex) return
    if (USE_ADMIN_MOCK) {
      showToast('已触发重建向量（Mock）')
      setConfirmReindex(null)
      return
    }
    try {
      await realAdminApi.reindexDocument(confirmReindex.docId)
      showToast('已触发重建向量')
      setConfirmReindex(null)
      await refreshTasks()
    } catch (err) {
      showToast(errMsg(err, '重建向量失败'))
    }
  }

  // MOCK filters client-side; REAL already filtered by API
  const filtered = USE_ADMIN_MOCK
    ? tasks.filter((task) => {
        if (filterLib && task.libraryCode !== filterLib) return false
        if (filterStatus && task.status !== filterStatus) return false
        const q = keyword.trim().toLowerCase()
        if (q && !task.title.toLowerCase().includes(q)) return false
        return true
      })
    : tasks

  return (
    <div className="adminPage">
      {toastNode}
      <div className="adminPageHeader">
        <div>
          <h1>文档入库</h1>
          <p className="adminMuted">支持 PDF / PNG / JPG；单文件建议 ≤50MB</p>
        </div>
      </div>

      <section className="adminPanel adminUploadPanel">
        <div className="adminUploadControls">
          <label>
            目标知识库
            <select value={libraryCode} onChange={(e) => setLibraryCode(e.target.value)}>
              <option value="">请选择</option>
              {libraries.map((lib) => (
                <option key={lib.code} value={lib.code}>
                  {lib.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            分类
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value as DocCategory)}
            >
              {(Object.keys(CATEGORY_LABEL) as DocCategory[]).map((key) => (
                <option key={key} value={key}>
                  {CATEGORY_LABEL[key]}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div
          className={`adminDropzone${dragging ? ' isDragging' : ''}${!libraryCode ? ' isDisabled' : ''}`}
          onDragOver={(e) => {
            e.preventDefault()
            if (libraryCode) setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
          onClick={() => libraryCode && fileRef.current?.click()}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') fileRef.current?.click()
          }}
        >
          <Upload size={22} />
          <strong>{libraryCode ? '拖拽文件到此处，或点击选择' : '请先选择目标知识库'}</strong>
          <span className="adminMuted">可多选；类型白名单校验</span>
          <input
            ref={fileRef}
            type="file"
            accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
            multiple
            hidden
            disabled={!libraryCode}
            onChange={(e) => {
              if (e.target.files?.length) addFiles(e.target.files)
              e.target.value = ''
            }}
          />
        </div>

        {queue.length > 0 && (
          <ul className="adminQueueList">
            {queue.map((item) => (
              <li key={item.id}>
                <span className="adminFileIcon">{typeIcon(item.fileType)}</span>
                <div className="adminTaskMain">
                  <strong>{item.file.name}</strong>
                  <span className="adminMuted">
                    {formatSize(item.file.size)} · {item.fileType}
                    {item.error ? ` · ${item.error}` : ''}
                  </span>
                </div>
                {item.error ? (
                  <span className="adminStatus adminStatus--failed">无效</span>
                ) : (
                  <span className="adminStatus adminStatus--pending">待提交</span>
                )}
                <button
                  type="button"
                  className="adminIconBtn"
                  aria-label="移除"
                  onClick={() => setQueue((prev) => prev.filter((q) => q.id !== item.id))}
                >
                  <X size={14} />
                </button>
              </li>
            ))}
          </ul>
        )}

        <div className="adminHeaderActions">
          <button
            type="button"
            className="adminBtnPrimary"
            disabled={!queue.some((q) => !q.error) || !libraryCode || uploading}
            onClick={() => void startIngest()}
          >
            {uploading ? '上传中…' : '开始入库'}
          </button>
        </div>
      </section>

      <section className="adminPanel">
        <div className="adminPanelHead">
          <h2>入库任务</h2>
        </div>
        <div className="adminToolbar adminToolbarWrap">
          <label className="adminInlineField">
            知识库
            <select value={filterLib} onChange={(e) => setFilterLib(e.target.value)}>
              <option value="">全部</option>
              {libraries.map((lib) => (
                <option key={lib.code} value={lib.code}>
                  {lib.name}
                </option>
              ))}
            </select>
          </label>
          <label className="adminInlineField">
            状态
            <select
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.target.value as IngestTaskStatus | '')}
            >
              <option value="">全部</option>
              <option value="PENDING">等待中</option>
              <option value="RUNNING">处理中</option>
              <option value="SUCCESS">成功</option>
              <option value="FAILED">失败</option>
            </select>
          </label>
          <label className="adminInlineField adminInlineField--grow">
            关键词
            <span className="adminSearch">
              <input
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="搜索文档标题…"
              />
            </span>
          </label>
        </div>

        <div className="adminTableWrap">
          <table className="adminTable">
            <thead>
              <tr>
                <th>文档标题</th>
                <th>库</th>
                <th>类型</th>
                <th>状态</th>
                <th>进度</th>
                <th>错误</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {tasksLoading ? (
                <tr>
                  <td colSpan={8} className="adminTableEmpty">
                    加载中…
                  </td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={8} className="adminTableEmpty">
                    暂无匹配任务
                  </td>
                </tr>
              ) : (
                filtered.map((task) => (
                  <tr key={task.id}>
                    <td>
                      <div className="adminCellWithIcon">
                        {typeIcon(task.fileType)}
                        <strong>{task.title}</strong>
                      </div>
                    </td>
                    <td>{task.libraryName}</td>
                    <td>{task.fileType}</td>
                    <td>
                      <span className={statusClass(task.status)}>{ingestStatusLabel(task.status)}</span>
                    </td>
                    <td style={{ minWidth: 120 }}>
                      <div className="adminProgress">
                        <div style={{ width: `${task.progress}%` }} />
                      </div>
                      <span className="adminMuted">{task.progress}%</span>
                    </td>
                    <td>
                      <div className="adminClamp">{task.errorMsg || '—'}</div>
                    </td>
                    <td>{task.createdAt}</td>
                    <td>
                      <div className="adminRowActions">
                        {(task.status === 'PENDING' || task.status === 'RUNNING') && (
                          <button type="button" disabled>
                            处理中
                          </button>
                        )}
                        {task.status === 'FAILED' && (
                          <>
                            <button type="button" onClick={() => setErrorTask(task)}>
                              查看错误
                            </button>
                            <button type="button" onClick={() => void retryTask(task)}>
                              重试
                            </button>
                          </>
                        )}
                        {task.status === 'SUCCESS' && (
                          <>
                            <button type="button" onClick={() => setDetail(task)}>
                              元信息
                            </button>
                            <button type="button" onClick={() => setConfirmReindex(task)}>
                              重建向量
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {detail && (
        <div className="adminDrawerMask" onClick={() => setDetail(null)} role="presentation">
          <aside className="adminDrawer" onClick={(e) => e.stopPropagation()} role="dialog">
            <h2>文档元信息</h2>
            <dl className="adminDescList">
              <div>
                <dt>标题</dt>
                <dd>{detail.title}</dd>
              </div>
              <div>
                <dt>知识库</dt>
                <dd>{detail.libraryName}</dd>
              </div>
              <div>
                <dt>类型</dt>
                <dd>{detail.fileType}</dd>
              </div>
              <div>
                <dt>分类</dt>
                <dd>{CATEGORY_LABEL[detail.category]}</dd>
              </div>
              <div>
                <dt>页数</dt>
                <dd>{detail.pages ?? '—'}</dd>
              </div>
              <div>
                <dt>状态</dt>
                <dd>
                  <span className={statusClass(detail.status)}>{ingestStatusLabel(detail.status)}</span>
                </dd>
              </div>
              <div>
                <dt>摘要</dt>
                <dd>{detail.summary || '—'}</dd>
              </div>
            </dl>
            <button type="button" className="adminGhostBtn" onClick={() => setDetail(null)}>
              关闭
            </button>
          </aside>
        </div>
      )}

      {errorTask && (
        <div className="adminModalMask" onClick={() => setErrorTask(null)} role="presentation">
          <div className="adminModal" onClick={(e) => e.stopPropagation()} role="dialog">
            <h2>错误详情</h2>
            <p>{errorTask.errorMsg || '未知错误'}</p>
            <div className="adminModalActions">
              <button type="button" className="adminGhostBtn" onClick={() => setErrorTask(null)}>
                关闭
              </button>
              <button
                type="button"
                className="adminBtnPrimary"
                onClick={() => {
                  void retryTask(errorTask)
                  setErrorTask(null)
                }}
              >
                重试
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmReindex && (
        <div className="adminModalMask" onClick={() => setConfirmReindex(null)} role="presentation">
          <div className="adminModal" onClick={(e) => e.stopPropagation()} role="dialog">
            <h2>重建向量</h2>
            <p>
              确认为「{confirmReindex.title}」触发重建向量？
              {USE_ADMIN_MOCK ? '此为 Mock 操作，不会调用真实服务。' : ''}
            </p>
            <div className="adminModalActions">
              <button type="button" className="adminGhostBtn" onClick={() => setConfirmReindex(null)}>
                取消
              </button>
              <button type="button" className="adminBtnPrimary" onClick={() => void confirmRebuild()}>
                确认重建
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
