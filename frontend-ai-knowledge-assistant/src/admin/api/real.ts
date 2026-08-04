import type {
  AclRule,
  AuditEvent,
  IngestTask,
  LibraryRecord,
  SysUserRecord,
} from '../types'
import { adminRequest, adminRequestBlob, qs } from './http'

export type PageResult<T> = {
  total: number
  pageNum: number
  pageSize: number
  records: T[]
}

export type DashboardData = {
  libraryCount: number
  readyDocCount: number
  totalDocCount: number
  failedIngestCount: number
  pendingUserCount: number | null
  recentIngestTasks: IngestTask[]
  recentAudits: AuditEvent[]
}

export type RoleCard = {
  code: string
  name: string
  description: string
  userCount: number
  permissions: Record<string, boolean>
}

export type ModelConfigPayload = Record<string, unknown>

export type IngestUploadResult = {
  docId: number
  taskId: number
}

function normalizeLibrary(raw: LibraryRecord): LibraryRecord {
  return {
    ...raw,
    id: String(raw.id),
    tags: raw.tags || [],
    docCount: raw.docCount ?? 0,
    description: raw.description || '',
    updatedAt: raw.updatedAt || '',
  }
}

function normalizeTask(raw: IngestTask): IngestTask {
  return {
    ...raw,
    id: String(raw.id),
    docId: String(raw.docId ?? ''),
    progress: raw.progress ?? 0,
    fileType: (raw.fileType as IngestTask['fileType']) || 'unknown',
    category: (raw.category as IngestTask['category']) || 'manual',
    status: raw.status,
  }
}

function normalizeUser(raw: SysUserRecord): SysUserRecord {
  return {
    ...raw,
    id: String(raw.id),
    role: raw.role,
    status: raw.status as SysUserRecord['status'],
    empNo: raw.empNo || '',
    mobile: raw.mobile || '',
    deptName: raw.deptName || '',
    deptCode: raw.deptCode || '',
  }
}

function normalizeAudit(raw: AuditEvent): AuditEvent {
  return {
    ...raw,
    id: String(raw.id),
    knowledgeRelated: Boolean(raw.knowledgeRelated),
    targetType: (raw.targetType as AuditEvent['targetType']) || 'system',
    detail: raw.detail || '',
    ip: raw.ip || '',
  }
}

function normalizeAcl(raw: AclRule): AclRule {
  return {
    ...raw,
    id: String(raw.id),
    perm: 'READ',
    subjectType: raw.subjectType as AclRule['subjectType'],
    source: raw.source || '',
  }
}

export const realAdminApi = {
  dashboard() {
    return adminRequest<DashboardData>('/api/v1/admin/dashboard').then((d) => ({
      ...d,
      recentIngestTasks: (d.recentIngestTasks || []).map(normalizeTask),
      recentAudits: (d.recentAudits || []).map(normalizeAudit),
    }))
  },

  listLibraries(keyword?: string) {
    return adminRequest<LibraryRecord[]>(
      `/api/v1/admin/libraries${qs({ keyword })}`,
    ).then((list) => (list || []).map(normalizeLibrary))
  },

  createLibrary(body: {
    code: string
    name: string
    description?: string
    tags?: string[]
    publicRead?: boolean
  }) {
    return adminRequest<LibraryRecord>('/api/v1/admin/libraries', {
      method: 'POST',
      body: JSON.stringify(body),
    }).then(normalizeLibrary)
  },

  updateLibrary(
    code: string,
    body: { name: string; description?: string; tags?: string[]; publicRead?: boolean },
  ) {
    return adminRequest<LibraryRecord>(`/api/v1/admin/libraries/${encodeURIComponent(code)}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }).then(normalizeLibrary)
  },

  deleteLibrary(code: string) {
    return adminRequest<null>(`/api/v1/admin/libraries/${encodeURIComponent(code)}`, {
      method: 'DELETE',
    })
  },

  listLibraryDocuments(
    code: string,
    params?: { category?: string; q?: string; page?: number; size?: number },
  ) {
    return adminRequest<{
      page: number
      size: number
      total: number
      list: Array<{
        id: string | number
        libraryId: string
        title: string
        category: string
        pages: number
        updatedAt?: string
        views?: number
        summary?: string
        fileType?: string
      }>
    }>(`/api/v1/libraries/${encodeURIComponent(code)}/documents${qs({
      category: params?.category,
      q: params?.q,
      page: params?.page,
      size: params?.size ?? 50,
    })}`)
  },

  getDocumentMeta(id: string | number) {
    return adminRequest<{
      id: number
      title: string
      knowledgeBase: string
      knowledgeBaseId: string
      fileType: string
      pages: number
      summary?: string
      updatedAt?: string
      views?: number
      category?: string
    }>(`/api/v1/documents/${id}`)
  },

  async getDocumentFileBlob(id: string | number) {
    return adminRequestBlob(`/api/v1/documents/${id}/file`)
  },

  listAcl(libraryCode: string) {
    return adminRequest<AclRule[]>(
      `/api/v1/admin/libraries/${encodeURIComponent(libraryCode)}/acl`,
    ).then((list) => (list || []).map(normalizeAcl))
  },

  addAcl(
    libraryCode: string,
    body: { subjectType: string; subjectId: string; subjectLabel?: string; perm?: string },
  ) {
    return adminRequest<AclRule>(
      `/api/v1/admin/libraries/${encodeURIComponent(libraryCode)}/acl`,
      { method: 'POST', body: JSON.stringify(body) },
    ).then(normalizeAcl)
  },

  removeAcl(id: string) {
    return adminRequest<null>(`/api/v1/admin/acl/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    })
  },

  setPublicRead(libraryCode: string, publicRead: boolean) {
    return adminRequest<null>(
      `/api/v1/admin/libraries/${encodeURIComponent(libraryCode)}/public-read`,
      { method: 'PUT', body: JSON.stringify({ publicRead }) },
    )
  },

  listIngestTasks(params: {
    libraryCode?: string
    status?: string
    keyword?: string
    page?: number
    size?: number
  }) {
    return adminRequest<PageResult<IngestTask>>(
      `/api/v1/admin/ingest/tasks${qs(params)}`,
    ).then((page) => ({
      ...page,
      records: (page.records || []).map(normalizeTask),
    }))
  },

  getIngestTask(id: string) {
    return adminRequest<IngestTask>(`/api/v1/admin/ingest/tasks/${encodeURIComponent(id)}`).then(
      normalizeTask,
    )
  },

  uploadDocument(file: File, libraryCode: string, category: string, title?: string) {
    const form = new FormData()
    form.append('file', file)
    form.append('libraryCode', libraryCode)
    form.append('category', category)
    if (title) form.append('title', title)
    return adminRequest<IngestUploadResult>('/api/v1/admin/ingest/documents', {
      method: 'POST',
      body: form,
    })
  },

  retryIngest(id: string) {
    return adminRequest<IngestUploadResult>(
      `/api/v1/admin/ingest/tasks/${encodeURIComponent(id)}/retry`,
      { method: 'POST' },
    )
  },

  reindexDocument(docId: string) {
    return adminRequest<IngestUploadResult>(
      `/api/v1/admin/ingest/reindex/${encodeURIComponent(docId)}`,
      { method: 'POST' },
    )
  },

  listUsers(params: {
    status?: number | string
    role?: string
    keyword?: string
    page?: number
    size?: number
  }) {
    return adminRequest<PageResult<SysUserRecord>>(`/api/v1/admin/users${qs(params)}`).then(
      (page) => ({
        ...page,
        records: (page.records || []).map(normalizeUser),
      }),
    )
  },

  createUser(body: {
    name: string
    email: string
    mobile?: string
    empNo?: string
    deptName?: string
    deptCode?: string
    role?: string
    password: string
  }) {
    return adminRequest<SysUserRecord>('/api/v1/admin/users', {
      method: 'POST',
      body: JSON.stringify(body),
    }).then(normalizeUser)
  },

  approveUser(id: string) {
    return adminRequest<SysUserRecord>(`/api/v1/admin/users/${id}/approve`, {
      method: 'PATCH',
    }).then(normalizeUser)
  },

  rejectUser(id: string) {
    return adminRequest<SysUserRecord>(`/api/v1/admin/users/${id}/reject`, {
      method: 'PATCH',
    }).then(normalizeUser)
  },

  disableUser(id: string) {
    return adminRequest<SysUserRecord>(`/api/v1/admin/users/${id}/disable`, {
      method: 'PATCH',
    }).then(normalizeUser)
  },

  enableUser(id: string) {
    return adminRequest<SysUserRecord>(`/api/v1/admin/users/${id}/enable`, {
      method: 'PATCH',
    }).then(normalizeUser)
  },

  changeUserRole(id: string, role: string) {
    return adminRequest<SysUserRecord>(`/api/v1/admin/users/${id}/role`, {
      method: 'PATCH',
      body: JSON.stringify({ role }),
    }).then(normalizeUser)
  },

  resetPassword(id: string) {
    return adminRequest<{ message: string }>(`/api/v1/admin/users/${id}/reset-password`, {
      method: 'POST',
    })
  },

  listRoles() {
    return adminRequest<RoleCard[]>('/api/v1/admin/roles')
  },

  getRolePermissions(code: string) {
    return adminRequest<Record<string, boolean>>(
      `/api/v1/admin/roles/${encodeURIComponent(code)}/permissions`,
    )
  },

  saveRolePermissions(code: string, body: Record<string, boolean>) {
    return adminRequest<Record<string, boolean>>(
      `/api/v1/admin/roles/${encodeURIComponent(code)}/permissions`,
      { method: 'PUT', body: JSON.stringify(body) },
    )
  },

  getModels() {
    return adminRequest<ModelConfigPayload>('/api/v1/admin/models')
  },

  saveModels(body: ModelConfigPayload) {
    return adminRequest<ModelConfigPayload>('/api/v1/admin/models', {
      method: 'PUT',
      body: JSON.stringify(body),
    })
  },

  testModel(target = 'llm') {
    return adminRequest<{ ok: boolean; message?: string; httpStatus?: number }>(
      `/api/v1/admin/models/test${qs({ target })}`,
      { method: 'POST' },
    )
  },

  listAudit(params: {
    range?: string
    from?: string
    to?: string
    actor?: string
    actions?: string
    targetType?: string
    keyword?: string
    page?: number
    size?: number
  }) {
    return adminRequest<PageResult<AuditEvent>>(`/api/v1/admin/audit${qs(params)}`).then(
      (page) => ({
        ...page,
        records: (page.records || []).map(normalizeAudit),
      }),
    )
  },

  exportAudit(params: {
    range?: string
    from?: string
    to?: string
    actor?: string
    actions?: string
    targetType?: string
    keyword?: string
  }) {
    return adminRequestBlob(`/api/v1/admin/audit/export${qs(params)}`)
  },
}
