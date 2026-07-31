import { useCallback, useEffect, useMemo, useState } from 'react'
import { Info, Lock, Plus, Shield } from 'lucide-react'
import { AdminApiError, USE_ADMIN_MOCK, realAdminApi, type RoleCard } from './api'
import { ROLE_LABEL } from './auth'
import { loadUsers } from './mock'
import type { AdminRole } from './types'
import { useAdminToast } from './useAdminToast'

const MATRIX_KEY = 'zn-admin-role-matrix'

type Capability = {
  key: string
  label: string
  group: string
}

const CAPABILITIES: Capability[] = [
  { key: 'admin.access', label: '登录管理后台', group: '准入' },
  { key: 'library.read', label: '查看知识库', group: '知识管理' },
  { key: 'library.write', label: '创建/编辑知识库', group: '知识管理' },
  { key: 'ingest.upload', label: '上传文档', group: '知识管理' },
  { key: 'ingest.reindex', label: '重建向量', group: '知识管理' },
  { key: 'acl.manage', label: '配置 ACL', group: '知识管理' },
  { key: 'user.manage', label: '用户管理', group: '账号治理' },
  { key: 'user.approve', label: '审核注册', group: '账号治理' },
  { key: 'role.manage', label: '角色配置', group: '账号治理' },
  { key: 'model.manage', label: '模型设置', group: '系统设置' },
  { key: 'audit.read', label: '查看审计日志', group: '系统设置' },
]

type RoleMatrix = Record<AdminRole, Record<string, boolean>>

const DEFAULT_MATRIX: RoleMatrix = {
  EMPLOYEE: Object.fromEntries(CAPABILITIES.map((c) => [c.key, false])),
  KB_ADMIN: Object.fromEntries(
    CAPABILITIES.map((c) => [
      c.key,
      [
        'admin.access',
        'library.read',
        'library.write',
        'ingest.upload',
        'ingest.reindex',
        'acl.manage',
        'audit.read',
      ].includes(c.key),
    ]),
  ),
  SYS_ADMIN: Object.fromEntries(CAPABILITIES.map((c) => [c.key, true])),
}

const ROLE_META: Record<AdminRole, { description: string; tone: string }> = {
  EMPLOYEE: {
    description: '仅用户端问答/搜索/浏览；无管理后台',
    tone: 'employee',
  },
  KB_ADMIN: {
    description: '知识库、入库、ACL、知识相关审计',
    tone: 'kb',
  },
  SYS_ADMIN: {
    description: '全部管理能力，含用户/角色/模型',
    tone: 'sys',
  },
}

function loadMatrix(): RoleMatrix {
  const raw = localStorage.getItem(MATRIX_KEY)
  if (!raw) {
    localStorage.setItem(MATRIX_KEY, JSON.stringify(DEFAULT_MATRIX))
    return structuredClone(DEFAULT_MATRIX)
  }
  try {
    const parsed = JSON.parse(raw) as RoleMatrix
    return {
      EMPLOYEE: { ...DEFAULT_MATRIX.EMPLOYEE, ...parsed.EMPLOYEE, 'admin.access': false },
      KB_ADMIN: { ...DEFAULT_MATRIX.KB_ADMIN, ...parsed.KB_ADMIN },
      SYS_ADMIN: Object.fromEntries(CAPABILITIES.map((c) => [c.key, true])),
    }
  } catch {
    return structuredClone(DEFAULT_MATRIX)
  }
}

function isLocked(role: AdminRole, key: string) {
  if (role === 'EMPLOYEE' && key === 'admin.access') return true
  if (role === 'SYS_ADMIN') return true
  return false
}

function errMsg(err: unknown, fallback: string) {
  return err instanceof AdminApiError ? err.message : fallback
}

function toRoleCode(code: string): AdminRole | null {
  if (code === 'EMPLOYEE' || code === 'KB_ADMIN' || code === 'SYS_ADMIN') return code
  return null
}

export default function AdminRoles() {
  const [matrix, setMatrix] = useState<RoleMatrix>(() =>
    USE_ADMIN_MOCK ? loadMatrix() : structuredClone(DEFAULT_MATRIX),
  )
  const [counts, setCounts] = useState<Record<AdminRole, number>>({
    EMPLOYEE: 0,
    KB_ADMIN: 0,
    SYS_ADMIN: 0,
  })
  const [loading, setLoading] = useState(!USE_ADMIN_MOCK)
  const [editing, setEditing] = useState<AdminRole | null>(null)
  const [draft, setDraft] = useState<Record<string, boolean> | null>(null)
  const [saving, setSaving] = useState(false)
  const { showToast, toastNode } = useAdminToast()

  const refresh = useCallback(async () => {
    if (USE_ADMIN_MOCK) {
      const users = loadUsers()
      setCounts({
        EMPLOYEE: users.filter((u) => u.role === 'EMPLOYEE').length,
        KB_ADMIN: users.filter((u) => u.role === 'KB_ADMIN').length,
        SYS_ADMIN: users.filter((u) => u.role === 'SYS_ADMIN').length,
      })
      setMatrix(loadMatrix())
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const roles = await realAdminApi.listRoles()
      const nextMatrix = structuredClone(DEFAULT_MATRIX)
      const nextCounts: Record<AdminRole, number> = {
        EMPLOYEE: 0,
        KB_ADMIN: 0,
        SYS_ADMIN: 0,
      }
      roles.forEach((card: RoleCard) => {
        const code = toRoleCode(card.code)
        if (!code) return
        nextCounts[code] = card.userCount ?? 0
        nextMatrix[code] = {
          ...DEFAULT_MATRIX[code],
          ...(card.permissions || {}),
        }
        if (code === 'EMPLOYEE') nextMatrix[code]['admin.access'] = false
        if (code === 'SYS_ADMIN') {
          nextMatrix[code] = Object.fromEntries(CAPABILITIES.map((c) => [c.key, true]))
        }
      })
      setMatrix(nextMatrix)
      setCounts(nextCounts)
    } catch (err) {
      showToast(errMsg(err, '角色列表加载失败'))
    } finally {
      setLoading(false)
    }
  }, [showToast])

  useEffect(() => {
    void refresh()
  }, [refresh])

  async function openConfig(role: AdminRole) {
    setEditing(role)
    if (USE_ADMIN_MOCK) {
      setDraft({ ...matrix[role] })
      return
    }
    try {
      const perms = await realAdminApi.getRolePermissions(role)
      setDraft({
        ...DEFAULT_MATRIX[role],
        ...perms,
        ...(role === 'EMPLOYEE' ? { 'admin.access': false } : {}),
        ...(role === 'SYS_ADMIN'
          ? Object.fromEntries(CAPABILITIES.map((c) => [c.key, true]))
          : {}),
      })
    } catch (err) {
      showToast(errMsg(err, '权限加载失败'))
      setDraft({ ...matrix[role] })
    }
  }

  function toggle(key: string) {
    if (!editing || !draft || isLocked(editing, key)) return
    setDraft((prev) => (prev ? { ...prev, [key]: !prev[key] } : prev))
  }

  async function save() {
    if (!editing || !draft) return
    const nextDraft =
      editing === 'EMPLOYEE'
        ? { ...draft, 'admin.access': false }
        : editing === 'SYS_ADMIN'
          ? Object.fromEntries(CAPABILITIES.map((c) => [c.key, true]))
          : draft

    if (USE_ADMIN_MOCK) {
      const next = { ...matrix, [editing]: nextDraft }
      setMatrix(next)
      localStorage.setItem(MATRIX_KEY, JSON.stringify(next))
      setEditing(null)
      setDraft(null)
      showToast('角色权限已更新')
      return
    }

    setSaving(true)
    try {
      await realAdminApi.saveRolePermissions(editing, nextDraft)
      setMatrix((prev) => ({ ...prev, [editing]: nextDraft }))
      setEditing(null)
      setDraft(null)
      showToast('角色权限已更新')
    } catch (err) {
      showToast(errMsg(err, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  const groups = useMemo(() => [...new Set(CAPABILITIES.map((c) => c.group))], [])

  return (
    <div className="adminPage">
      {toastNode}
      <div className="adminNotice">
        <Info size={16} />
        <span>
          用户侧知识库可见性由 ACL 控制，不在本矩阵；本矩阵只治理「管理后台能力」。
        </span>
      </div>

      <div className="adminPageHeader">
        <div>
          <h1>角色配置</h1>
          <p className="adminMuted">预置三角色；可调整菜单级能力开关</p>
        </div>
        <button type="button" className="adminGhostBtn" disabled title="即将支持">
          <Plus size={14} />
          新建角色
        </button>
      </div>

      {loading ? (
        <div className="adminEmpty">
          <h2>加载中…</h2>
        </div>
      ) : (
        <div className="adminRoleGrid">
          {(Object.keys(ROLE_META) as AdminRole[]).map((role) => (
            <article key={role} className={`adminRoleCard adminRoleCard--${ROLE_META[role].tone}`}>
              <div className="adminRoleCardHead">
                <span className="adminRoleIcon">
                  <Shield size={18} />
                </span>
                <div>
                  <strong>{ROLE_LABEL[role]}</strong>
                  <code>{role}</code>
                </div>
              </div>
              <p>{ROLE_META[role].description}</p>
              <div className="adminRoleMeta">
                <span>人数 {counts[role]}</span>
                <button
                  type="button"
                  className="adminBtnPrimary"
                  onClick={() => void openConfig(role)}
                >
                  配置权限
                </button>
              </div>
            </article>
          ))}
        </div>
      )}

      {editing && draft && (
        <div className="adminDrawerMask" onClick={() => setEditing(null)} role="presentation">
          <aside
            className="adminDrawer adminDrawerWide"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-label={`配置 ${ROLE_LABEL[editing]}`}
          >
            <h2>配置权限 · {ROLE_LABEL[editing]}</h2>
            <p className="adminMuted">
              {editing === 'EMPLOYEE' && '普通员工不可登录管理后台，admin.access 已锁定关闭。'}
              {editing === 'SYS_ADMIN' && '系统管理员能力全开，开关锁定以防误关。'}
              {editing === 'KB_ADMIN' && '可按需调整知识运营相关能力。'}
            </p>

            <div className="adminMatrix">
              {groups.map((group) => (
                <div key={group} className="adminMatrixGroup">
                  <h3>{group}</h3>
                  {CAPABILITIES.filter((c) => c.group === group).map((cap) => {
                    const locked = isLocked(editing, cap.key)
                    return (
                      <label key={cap.key} className={`adminMatrixRow${locked ? ' isLocked' : ''}`}>
                        <span>
                          <strong>{cap.label}</strong>
                          <code>{cap.key}</code>
                        </span>
                        <span className="adminMatrixToggle">
                          {locked && <Lock size={12} />}
                          <input
                            type="checkbox"
                            checked={!!draft[cap.key]}
                            disabled={locked}
                            onChange={() => toggle(cap.key)}
                          />
                        </span>
                      </label>
                    )
                  })}
                </div>
              ))}
            </div>

            <div className="adminModalActions">
              <button type="button" className="adminGhostBtn" onClick={() => setEditing(null)}>
                取消
              </button>
              <button
                type="button"
                className="adminBtnPrimary"
                disabled={saving}
                onClick={() => void save()}
              >
                {saving ? '保存中…' : '保存'}
              </button>
            </div>
          </aside>
        </div>
      )}
    </div>
  )
}
