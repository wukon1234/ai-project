/** 审计 action / 对象类型中文展示（接口仍传英文码）。 */

export const AUDIT_ACTION_LABELS: Record<string, string> = {
  LOGIN: '登录',
  LOGIN_FAIL: '登录失败',
  SSO_LOGIN: 'SSO 登录',
  PASSWORD_RESET: '重置密码',
  DOWNLOAD: '下载',
  DOWNLOAD_DOC: '下载文档',
  PREVIEW_DOC: '预览文档',
  SHARE: '分享',
  SHARE_DOC: '分享文档',
  SHARE_SESSION: '分享会话',
  AUTH_DENY: '鉴权拒绝',
  USER_CREATE: '创建用户',
  USER_APPROVE: '通过审核',
  USER_REJECT: '拒绝审核',
  USER_DISABLE: '禁用用户',
  USER_ENABLE: '启用用户',
  USER_RESET_PASSWORD: '重置密码',
  ACL_UPDATE: '更新权限',
  INGEST_UPLOAD: '文档入库',
  INGEST_RETRY: '重试入库',
  INGEST_REINDEX: '重建索引',
  MODEL_UPDATE: '更新模型',
  ROLE_UPDATE: '更新角色',
  LIBRARY_CREATE: '创建知识库',
  LIBRARY_UPDATE: '更新知识库',
  LIBRARY_DELETE: '删除知识库',
}

export const AUDIT_TARGET_TYPE_LABELS: Record<string, string> = {
  user: '用户',
  document: '文档',
  library: '知识库',
  acl: '权限',
  system: '系统',
  auth: '认证',
  session: '会话',
}

export function auditActionLabel(action: string): string {
  if (!action) return '—'
  return AUDIT_ACTION_LABELS[action] || action
}

export function auditTargetTypeLabel(type: string): string {
  if (!type) return '—'
  return AUDIT_TARGET_TYPE_LABELS[type] || type
}

/** 详情若仍是英文 action 码，则展示中文操作名。 */
export function auditDetailLabel(detail: string, action: string): string {
  const text = (detail || '').trim()
  if (!text || text === action) return auditActionLabel(action)
  if (AUDIT_ACTION_LABELS[text]) return AUDIT_ACTION_LABELS[text]
  return text
}
