import type {
  AclRule,
  AuditEvent,
  IngestTask,
  LibraryRecord,
  MockPerson,
  SysUserRecord,
} from './types'

const LIBRARY_KEY = 'zn-admin-libraries'
const INGEST_KEY = 'zn-admin-ingest-tasks'
const ACL_KEY = 'zn-admin-acl-rules'
const USERS_KEY = 'zn-admin-users'

const SEED_LIBRARIES: LibraryRecord[] = [
  {
    id: '1',
    code: 'product',
    name: '产品知识库',
    description: '产品规格、FAQ、竞品对比等',
    tags: ['#FAQ', '#规格', '#对比'],
    docCount: 128,
    updatedAt: '2026-07-28 16:20',
    publicRead: false,
  },
  {
    id: '2',
    code: 'hr',
    name: '人事制度库',
    description: '员工手册、考勤、报销与休假制度',
    tags: ['#制度', '#手册', '#FAQ'],
    docCount: 64,
    updatedAt: '2026-07-29 09:10',
    publicRead: true,
  },
  {
    id: '3',
    code: 'tech',
    name: '技术文档库',
    description: '接口说明、架构设计、排障手册',
    tags: ['#手册', '#FAQ'],
    docCount: 97,
    updatedAt: '2026-07-30 14:02',
    publicRead: false,
  },
  {
    id: '4',
    code: 'support',
    name: '售后 FAQ',
    description: '常见故障、售后流程、服务话术',
    tags: ['#FAQ', '#流程'],
    docCount: 52,
    updatedAt: '2026-07-27 11:45',
    publicRead: false,
  },
]

const SEED_INGEST: IngestTask[] = [
  {
    id: 't1',
    docId: 'd1',
    title: '员工手册2026.pdf',
    libraryCode: 'hr',
    libraryName: '人事制度库',
    fileType: 'pdf',
    category: 'policy',
    status: 'SUCCESS',
    progress: 100,
    createdAt: '2026-07-31 10:12',
    pages: 48,
    summary: '覆盖考勤、休假、报销与行为规范的员工制度手册。',
  },
  {
    id: 't2',
    docId: 'd2',
    title: '产品规格说明书-v3.docx',
    libraryCode: 'product',
    libraryName: '产品知识库',
    fileType: 'word',
    category: 'manual',
    status: 'RUNNING',
    progress: 62,
    createdAt: '2026-07-31 11:05',
  },
  {
    id: 't3',
    docId: 'd3',
    title: '接口排障手册.pdf',
    libraryCode: 'tech',
    libraryName: '技术文档库',
    fileType: 'pdf',
    category: 'manual',
    status: 'FAILED',
    progress: 40,
    errorMsg: 'OCR 服务超时',
    createdAt: '2026-07-31 09:40',
  },
  {
    id: 't4',
    docId: 'd4',
    title: '售后服务话术.xlsx',
    libraryCode: 'support',
    libraryName: '售后 FAQ',
    fileType: 'excel',
    category: 'faq',
    status: 'PENDING',
    progress: 0,
    createdAt: '2026-07-31 11:20',
  },
]

export const MOCK_PEOPLE: MockPerson[] = [
  { id: 'u1', name: '张明', empNo: '100234', email: 'zhangming@zhishiyun.com', deptCode: 'RD', deptName: '研发部' },
  { id: 'u2', name: '李晓雯', empNo: '100301', email: 'lixiaowen@zhishiyun.com', deptCode: 'HR', deptName: '人力资源' },
  { id: 'u3', name: '陈浩', empNo: '100188', email: 'chenhao@zhishiyun.com', deptCode: 'RD', deptName: '研发部' },
  { id: 'u4', name: '赵倩', empNo: '100410', email: 'zhaoqian@zhishiyun.com', deptCode: 'SALES', deptName: '销售部' },
  { id: 'u5', name: '周宁', empNo: '100255', email: 'zhouning@zhishiyun.com', deptCode: 'SUPPORT', deptName: '售后服务' },
]

const SEED_ACL: AclRule[] = [
  {
    id: 'acl1',
    libraryCode: 'tech',
    subjectType: 'dept',
    subjectId: 'RD',
    subjectLabel: '研发部 (RD)',
    perm: 'READ',
    source: '部门授权',
  },
  {
    id: 'acl2',
    libraryCode: 'product',
    subjectType: 'user',
    subjectId: 'u1',
    subjectLabel: '张明 · 100234',
    perm: 'READ',
    source: '用户授权',
  },
  {
    id: 'acl3',
    libraryCode: 'product',
    subjectType: 'user',
    subjectId: 'u4',
    subjectLabel: '赵倩 · 100410',
    perm: 'READ',
    source: '用户授权',
  },
  {
    id: 'acl4',
    libraryCode: 'support',
    subjectType: 'dept',
    subjectId: 'SUPPORT',
    subjectLabel: '售后服务 (SUPPORT)',
    perm: 'READ',
    source: '部门授权',
  },
]

const SEED_USERS: SysUserRecord[] = [
  {
    id: 'u1',
    name: '张明',
    empNo: '100234',
    email: 'zhangming@zhishiyun.com',
    mobile: '13800001234',
    deptName: '研发部',
    deptCode: 'RD',
    role: 'EMPLOYEE',
    status: 1,
    createdAt: '2026-06-01 09:00',
    lastLoginAt: '2026-07-31 08:40',
  },
  {
    id: 'u2',
    name: '李晓雯',
    empNo: '100301',
    email: 'lixiaowen@zhishiyun.com',
    mobile: '13800001301',
    deptName: '人力资源',
    deptCode: 'HR',
    role: 'EMPLOYEE',
    status: 0,
    createdAt: '2026-07-30 15:20',
  },
  {
    id: 'u3',
    name: '陈浩',
    empNo: '100188',
    email: 'chenhao@zhishiyun.com',
    mobile: '13800001188',
    deptName: '研发部',
    deptCode: 'RD',
    role: 'EMPLOYEE',
    status: 0,
    createdAt: '2026-07-29 11:05',
  },
  {
    id: 'u4',
    name: '赵倩',
    empNo: '100410',
    email: 'zhaoqian@zhishiyun.com',
    mobile: '13800001410',
    deptName: '销售部',
    deptCode: 'SALES',
    role: 'EMPLOYEE',
    status: 2,
    createdAt: '2026-05-12 10:30',
    lastLoginAt: '2026-07-01 19:12',
  },
  {
    id: 'u5',
    name: '王婷',
    empNo: '100066',
    email: 'kbadmin@zhishiyun.com',
    mobile: '13800001066',
    deptName: '知识运营',
    deptCode: 'KO',
    role: 'KB_ADMIN',
    status: 1,
    createdAt: '2026-03-08 14:00',
    lastLoginAt: '2026-07-31 09:10',
  },
  {
    id: 'u6',
    name: '系统管理员',
    empNo: '100001',
    email: 'admin@zhishiyun.com',
    mobile: '13800001001',
    deptName: '信息中心',
    deptCode: 'IT',
    role: 'SYS_ADMIN',
    status: 1,
    createdAt: '2026-01-01 00:00',
    lastLoginAt: '2026-07-31 07:55',
  },
  {
    id: 'u7',
    name: '周宁',
    empNo: '100255',
    email: 'zhouning@zhishiyun.com',
    mobile: '13800001255',
    deptName: '售后服务',
    deptCode: 'SUPPORT',
    role: 'EMPLOYEE',
    status: 1,
    createdAt: '2026-04-18 16:40',
    lastLoginAt: '2026-07-30 17:22',
  },
]

function pad(n: number) {
  return String(n).padStart(2, '0')
}

function formatAuditTime(d: Date) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function daysAgo(days: number, hour = 10, minute = 0) {
  const d = new Date(2026, 6, 31, hour, minute) // 2026-07-31 baseline
  d.setDate(d.getDate() - days)
  return d
}

type AuditSeedInput = {
  action: string
  actor: string
  actorEmail?: string
  target: string
  targetType: AuditEvent['targetType']
  targetId?: string
  detail: string
  ip: string
  knowledgeRelated: boolean
  at: Date
}

function buildAudit(id: string, input: AuditSeedInput): AuditEvent {
  return {
    id,
    actor: input.actor,
    actorEmail: input.actorEmail,
    action: input.action,
    target: input.target,
    targetType: input.targetType,
    targetId: input.targetId,
    detail: input.detail,
    ip: input.ip,
    createdAt: formatAuditTime(input.at),
    knowledgeRelated: input.knowledgeRelated,
  }
}

function buildAuditSeed(): AuditEvent[] {
  const templates: AuditSeedInput[] = [
    {
      action: 'INGEST_UPLOAD',
      actor: '王婷',
      actorEmail: 'kbadmin@zhishiyun.com',
      target: '员工手册2026.pdf',
      targetType: 'document',
      targetId: 'd1',
      detail: '上传文档至人事制度库并启动解析任务',
      ip: '10.12.3.21',
      knowledgeRelated: true,
      at: daysAgo(0, 10, 12),
    },
    {
      action: 'ACL_UPDATE',
      actor: '系统管理员',
      actorEmail: 'admin@zhishiyun.com',
      target: 'tech / 研发部',
      targetType: 'acl',
      targetId: 'tech',
      detail: '为技术文档库新增部门 READ：RD',
      ip: '10.12.1.8',
      knowledgeRelated: true,
      at: daysAgo(0, 9, 55),
    },
    {
      action: 'LOGIN_FAIL',
      actor: '未知',
      target: 'zhangming@zhishiyun.com',
      targetType: 'user',
      targetId: 'u1',
      detail: '密码错误，连续失败 1 次',
      ip: '203.0.113.44',
      knowledgeRelated: false,
      at: daysAgo(0, 9, 2),
    },
    {
      action: 'USER_APPROVE',
      actor: '系统管理员',
      actorEmail: 'admin@zhishiyun.com',
      target: '李晓雯',
      targetType: 'user',
      targetId: 'u2',
      detail: '审核通过注册申请，角色 EMPLOYEE',
      ip: '10.12.1.8',
      knowledgeRelated: false,
      at: daysAgo(1, 18, 21),
    },
    {
      action: 'DOWNLOAD',
      actor: '王婷',
      actorEmail: 'kbadmin@zhishiyun.com',
      target: '产品规格说明书-v3.docx',
      targetType: 'document',
      targetId: 'd2',
      detail: '下载原文附件',
      ip: '10.12.3.21',
      knowledgeRelated: true,
      at: daysAgo(1, 16, 8),
    },
    {
      action: 'LOGIN',
      actor: '系统管理员',
      actorEmail: 'admin@zhishiyun.com',
      target: '管理后台',
      targetType: 'system',
      detail: '管理员登录成功',
      ip: '10.12.1.8',
      knowledgeRelated: false,
      at: daysAgo(0, 7, 55),
    },
    {
      action: 'SHARE',
      actor: '张明',
      actorEmail: 'zhangming@zhishiyun.com',
      target: '员工手册2026.pdf',
      targetType: 'document',
      targetId: 'd1',
      detail: '创建只读分享链接，有效期 7 天',
      ip: '10.20.5.16',
      knowledgeRelated: true,
      at: daysAgo(2, 11, 30),
    },
    {
      action: 'AUTH_DENY',
      actor: '赵倩',
      actorEmail: 'zhaoqian@zhishiyun.com',
      target: 'tech',
      targetType: 'library',
      targetId: 'tech',
      detail: '无技术文档库访问权限，问答检索被拒绝',
      ip: '10.20.8.33',
      knowledgeRelated: true,
      at: daysAgo(2, 14, 5),
    },
    {
      action: 'MODEL_UPDATE',
      actor: '系统管理员',
      actorEmail: 'admin@zhishiyun.com',
      target: 'LLM/Embedding',
      targetType: 'system',
      detail: '更新 embedding 模型为 text-embedding-3-small',
      ip: '10.12.1.8',
      knowledgeRelated: false,
      at: daysAgo(3, 9, 40),
    },
    {
      action: 'ROLE_UPDATE',
      actor: '系统管理员',
      actorEmail: 'admin@zhishiyun.com',
      target: 'KB_ADMIN',
      targetType: 'system',
      detail: '调整知识管理员能力矩阵：开启 ingest.reindex',
      ip: '10.12.1.8',
      knowledgeRelated: false,
      at: daysAgo(3, 15, 12),
    },
    {
      action: 'INGEST_REINDEX',
      actor: '王婷',
      actorEmail: 'kbadmin@zhishiyun.com',
      target: '接口排障手册.pdf',
      targetType: 'document',
      targetId: 'd3',
      detail: '触发文档向量重建',
      ip: '10.12.3.21',
      knowledgeRelated: true,
      at: daysAgo(4, 10, 18),
    },
    {
      action: 'ACL_UPDATE',
      actor: '王婷',
      actorEmail: 'kbadmin@zhishiyun.com',
      target: 'product / 张明',
      targetType: 'acl',
      targetId: 'product',
      detail: '为产品知识库授权用户 READ',
      ip: '10.12.3.21',
      knowledgeRelated: true,
      at: daysAgo(5, 13, 44),
    },
  ]

  const actors = [
    { name: '王婷', email: 'kbadmin@zhishiyun.com', ip: '10.12.3.21' },
    { name: '系统管理员', email: 'admin@zhishiyun.com', ip: '10.12.1.8' },
    { name: '张明', email: 'zhangming@zhishiyun.com', ip: '10.20.5.16' },
    { name: '周宁', email: 'zhouning@zhishiyun.com', ip: '10.20.9.11' },
    { name: '陈浩', email: 'chenhao@zhishiyun.com', ip: '10.20.5.42' },
  ]

  const extraActions: Array<Omit<AuditSeedInput, 'actor' | 'actorEmail' | 'ip' | 'at'> & { day: number }> = [
    { action: 'LOGIN', target: '用户端', targetType: 'system', detail: '账号密码登录成功', knowledgeRelated: false, day: 0 },
    { action: 'DOWNLOAD', target: '售后服务话术.xlsx', targetType: 'document', targetId: 'd4', detail: '下载售后 FAQ 附件', knowledgeRelated: true, day: 1 },
    { action: 'SHARE', target: '会话分享', targetType: 'document', detail: '分享问答会话只读链接', knowledgeRelated: true, day: 1 },
    { action: 'INGEST_UPLOAD', target: '竞品对比表.pptx', targetType: 'document', detail: '上传至产品知识库', knowledgeRelated: true, day: 2 },
    { action: 'AUTH_DENY', target: 'support', targetType: 'library', detail: '无售后库权限', knowledgeRelated: true, day: 3 },
    { action: 'LOGIN_FAIL', target: 'unknown@ext.com', targetType: 'user', detail: '非企业邮箱尝试登录', knowledgeRelated: false, day: 4 },
    { action: 'USER_APPROVE', target: '待审用户', targetType: 'user', detail: '批量通过注册申请', knowledgeRelated: false, day: 6 },
    { action: 'MODEL_UPDATE', target: 'OCR', targetType: 'system', detail: '启用 PaddleOCR 并调整并发=2', knowledgeRelated: false, day: 7 },
    { action: 'ROLE_UPDATE', target: 'EMPLOYEE', targetType: 'system', detail: '确认普通员工无管理后台权限', knowledgeRelated: false, day: 8 },
    { action: 'ACL_UPDATE', target: 'hr 全员可读', targetType: 'acl', detail: '开启人事制度库全员可读', knowledgeRelated: true, day: 9 },
    { action: 'INGEST_UPLOAD', target: '排班制度.pdf', targetType: 'document', detail: '入库失败后重试成功', knowledgeRelated: true, day: 10 },
    { action: 'DOWNLOAD', target: '架构设计手册.pdf', targetType: 'document', detail: '下载技术文档原文', knowledgeRelated: true, day: 12 },
    { action: 'LOGIN', target: '管理后台', targetType: 'system', detail: '知识管理员登录', knowledgeRelated: false, day: 14 },
    { action: 'SHARE', target: '产品 FAQ', targetType: 'library', detail: '尝试外链分享被策略拦截后改为内部分享', knowledgeRelated: true, day: 16 },
    { action: 'AUTH_DENY', target: '原文下载', targetType: 'document', detail: '会话过期导致鉴权失败', knowledgeRelated: true, day: 18 },
    { action: 'INGEST_REINDEX', target: '产品规格说明书-v3.docx', targetType: 'document', detail: 'Embedding 变更后重建', knowledgeRelated: true, day: 20 },
    { action: 'USER_APPROVE', target: '周宁', targetType: 'user', detail: '启用已禁用账号', knowledgeRelated: false, day: 22 },
    { action: 'MODEL_UPDATE', target: 'RAG', targetType: 'system', detail: '调整 topK=6 scoreThreshold=0.35', knowledgeRelated: false, day: 25 },
    { action: 'ACL_UPDATE', target: 'support / SUPPORT', targetType: 'acl', detail: '售后部门授权', knowledgeRelated: true, day: 27 },
    { action: 'LOGIN_FAIL', target: 'admin@zhishiyun.com', targetType: 'user', detail: '异地登录失败（Mock）', knowledgeRelated: false, day: 28 },
  ]

  const list: AuditEvent[] = templates.map((t, i) => buildAudit(`a${i + 1}`, t))

  extraActions.forEach((item, idx) => {
    const actor = actors[idx % actors.length]
    for (let copy = 0; copy < 3; copy++) {
      const at = daysAgo(item.day + copy, 8 + ((idx + copy) % 10), (idx * 3 + copy * 7) % 60)
      list.push(
        buildAudit(`a${list.length + 1}`, {
          action: item.action,
          actor: actor.name,
          actorEmail: actor.email,
          target: item.target,
          targetType: item.targetType,
          targetId: item.targetId,
          detail: `${item.detail}（样本 ${copy + 1}）`,
          ip: actor.ip,
          knowledgeRelated: item.knowledgeRelated,
          at,
        }),
      )
    }
  })

  return list.sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
}

export const MOCK_AUDIT_EVENTS: AuditEvent[] = buildAuditSeed()

export function loadAuditEvents(): AuditEvent[] {
  return MOCK_AUDIT_EVENTS
}

export const AUDIT_ACTIONS = [
  'LOGIN',
  'LOGIN_FAIL',
  'SSO_LOGIN',
  'DOWNLOAD_DOC',
  'PREVIEW_DOC',
  'SHARE_DOC',
  'SHARE_SESSION',
  'AUTH_DENY',
  'USER_APPROVE',
  'USER_CREATE',
  'ACL_UPDATE',
  'INGEST_UPLOAD',
  'INGEST_RETRY',
  'INGEST_REINDEX',
  'LIBRARY_CREATE',
  'LIBRARY_UPDATE',
  'MODEL_UPDATE',
  'ROLE_UPDATE',
] as const

export const KB_ADMIN_AUDIT_ACTIONS = new Set([
  'DOWNLOAD',
  'DOWNLOAD_DOC',
  'PREVIEW_DOC',
  'SHARE',
  'SHARE_DOC',
  'SHARE_SESSION',
  'AUTH_DENY',
  'ACL_UPDATE',
  'INGEST_UPLOAD',
  'INGEST_RETRY',
  'INGEST_REINDEX',
  'LIBRARY_CREATE',
  'LIBRARY_UPDATE',
])

export const MOCK_READY_DOCS = 312
export const MOCK_TOTAL_DOCS = 341
export const MOCK_PENDING_USERS = 2

export const DEPT_OPTIONS = [
  { code: 'RD', name: '研发部' },
  { code: 'HR', name: '人力资源' },
  { code: 'SALES', name: '销售部' },
  { code: 'SUPPORT', name: '售后服务' },
  { code: 'KO', name: '知识运营' },
  { code: 'IT', name: '信息中心' },
]

/** @deprecated use loadIngestTasks */
export const MOCK_INGEST_TASKS = SEED_INGEST

function cloneLibraries() {
  return SEED_LIBRARIES.map((item) => ({ ...item, tags: [...item.tags] }))
}

function loadList<T>(key: string, seed: T[]): T[] {
  const raw = localStorage.getItem(key)
  if (!raw) {
    localStorage.setItem(key, JSON.stringify(seed))
    return JSON.parse(JSON.stringify(seed)) as T[]
  }
  try {
    const parsed = JSON.parse(raw) as T[]
    return Array.isArray(parsed) ? parsed : (JSON.parse(JSON.stringify(seed)) as T[])
  } catch {
    return JSON.parse(JSON.stringify(seed)) as T[]
  }
}

export function loadLibraries(): LibraryRecord[] {
  return loadList(LIBRARY_KEY, cloneLibraries())
}

export function saveLibraries(list: LibraryRecord[]) {
  localStorage.setItem(LIBRARY_KEY, JSON.stringify(list))
}

export function loadIngestTasks(): IngestTask[] {
  return loadList(INGEST_KEY, SEED_INGEST)
}

export function saveIngestTasks(list: IngestTask[]) {
  localStorage.setItem(INGEST_KEY, JSON.stringify(list))
}

export function loadAclRules(): AclRule[] {
  return loadList(ACL_KEY, SEED_ACL)
}

export function saveAclRules(list: AclRule[]) {
  localStorage.setItem(ACL_KEY, JSON.stringify(list))
}

export function loadUsers(): SysUserRecord[] {
  return loadList(USERS_KEY, SEED_USERS)
}

export function saveUsers(list: SysUserRecord[]) {
  localStorage.setItem(USERS_KEY, JSON.stringify(list))
}

export function librariesForUser(userId: string, deptCode: string): string[] {
  const libs = loadLibraries()
  const rules = loadAclRules()
  return libs
    .filter((lib) => {
      if (lib.publicRead) return true
      return rules.some(
        (r) =>
          r.libraryCode === lib.code &&
          ((r.subjectType === 'user' && r.subjectId === userId) ||
            (r.subjectType === 'dept' && r.subjectId === deptCode)),
      )
    })
    .map((lib) => lib.name)
}

export function nowStamp() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
