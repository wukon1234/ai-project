export type AdminRole = 'SYS_ADMIN' | 'KB_ADMIN' | 'EMPLOYEE'

export type AdminUser = {
  email: string
  name: string
  role: AdminRole
}

export type AdminView =
  | 'dashboard'
  | 'libraries'
  | 'ingest'
  | 'acl'
  | 'users'
  | 'roles'
  | 'models'
  | 'audit'

export type LibraryRecord = {
  id: string
  code: string
  name: string
  description: string
  tags: string[]
  docCount: number
  updatedAt: string
  publicRead?: boolean
}

export type IngestTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export type FileType = 'pdf' | 'word' | 'excel' | 'ppt' | 'image' | 'unknown'

export type DocCategory = 'faq' | 'policy' | 'manual'

export type IngestTask = {
  id: string
  docId: string
  title: string
  libraryCode: string
  libraryName: string
  fileType: FileType
  category: DocCategory
  status: IngestTaskStatus
  progress: number
  errorMsg?: string
  createdAt: string
  pages?: number
  summary?: string
}

export type AuditTargetType = 'user' | 'document' | 'library' | 'acl' | 'system'

export type AuditEvent = {
  id: string
  actor: string
  actorEmail?: string
  action: string
  target: string
  targetType: AuditTargetType
  targetId?: string
  detail: string
  ip: string
  createdAt: string
  knowledgeRelated: boolean
}

export type AclSubjectType = 'user' | 'dept'

export type AclRule = {
  id: string
  libraryCode: string
  subjectType: AclSubjectType
  subjectId: string
  subjectLabel: string
  perm: 'READ'
  source: string
}

export type SysUserStatus = 0 | 1 | 2

export type SysUserRecord = {
  id: string
  name: string
  empNo: string
  email: string
  mobile: string
  deptName: string
  deptCode: string
  role: AdminRole
  status: SysUserStatus
  createdAt: string
  lastLoginAt?: string
}

export type MockPerson = {
  id: string
  name: string
  empNo: string
  email: string
  deptCode: string
  deptName: string
}
