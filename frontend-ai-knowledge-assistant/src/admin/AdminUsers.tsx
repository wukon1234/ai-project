import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Plus, Search } from 'lucide-react'
import { AdminApiError, USE_ADMIN_MOCK, realAdminApi } from './api'
import { ROLE_LABEL } from './auth'
import { DEPT_OPTIONS, librariesForUser, loadUsers, nowStamp, saveUsers } from './mock'
import type { AdminRole, SysUserRecord, SysUserStatus } from './types'
import { useAdminToast } from './useAdminToast'

type Props = {
  initialStatus?: string
}

type StatusFilter = 'all' | '0' | '1' | '2'
type RoleFilter = 'all' | AdminRole

const STATUS_LABEL: Record<SysUserStatus, string> = {
  0: '待审核',
  1: '正常',
  2: '禁用',
}

type CreateForm = {
  name: string
  email: string
  mobile: string
  empNo: string
  deptCode: string
  role: AdminRole
  password: string
}

const EMPTY_CREATE: CreateForm = {
  name: '',
  email: '',
  mobile: '',
  empNo: '',
  deptCode: 'RD',
  role: 'EMPLOYEE',
  password: '',
}

function errMsg(err: unknown, fallback: string) {
  return err instanceof AdminApiError ? err.message : fallback
}

export default function AdminUsers({ initialStatus = '' }: Props) {
  const [users, setUsers] = useState<SysUserRecord[]>(() => (USE_ADMIN_MOCK ? loadUsers() : []))
  const [loading, setLoading] = useState(!USE_ADMIN_MOCK)
  const [statusFilter, setStatusFilter] = useState<StatusFilter>(
    initialStatus === '0' || initialStatus === '1' || initialStatus === '2'
      ? initialStatus
      : 'all',
  )
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('all')
  const [keyword, setKeyword] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const [creating, setCreating] = useState(false)
  const [createForm, setCreateForm] = useState<CreateForm>(EMPTY_CREATE)
  const [detail, setDetail] = useState<SysUserRecord | null>(null)
  const [roleEdit, setRoleEdit] = useState<SysUserRecord | null>(null)
  const [nextRole, setNextRole] = useState<AdminRole>('EMPLOYEE')
  const [resetTarget, setResetTarget] = useState<SysUserRecord | null>(null)
  const [busy, setBusy] = useState(false)
  const { showToast, toastNode } = useAdminToast()

  const refresh = useCallback(async () => {
    if (USE_ADMIN_MOCK) {
      setUsers(loadUsers())
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const page = await realAdminApi.listUsers({
        status: statusFilter === 'all' ? undefined : statusFilter,
        role: roleFilter === 'all' ? undefined : roleFilter,
        keyword: keyword.trim() || undefined,
        page: 1,
        size: 100,
      })
      setUsers(page.records)
    } catch (err) {
      showToast(errMsg(err, '用户列表加载失败'))
    } finally {
      setLoading(false)
    }
  }, [statusFilter, roleFilter, keyword, showToast])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    if (initialStatus === '0' || initialStatus === '1' || initialStatus === '2') {
      setStatusFilter(initialStatus)
    }
  }, [initialStatus])

  function persist(next: SysUserRecord[]) {
    setUsers(next)
    saveUsers(next)
  }

  const filtered = useMemo(() => {
    if (!USE_ADMIN_MOCK) return users
    return users.filter((user) => {
      if (statusFilter !== 'all' && String(user.status) !== statusFilter) return false
      if (roleFilter !== 'all' && user.role !== roleFilter) return false
      const q = keyword.trim().toLowerCase()
      if (!q) return true
      return (
        user.name.toLowerCase().includes(q) ||
        user.empNo.toLowerCase().includes(q) ||
        user.email.toLowerCase().includes(q) ||
        user.mobile.includes(q) ||
        user.deptName.toLowerCase().includes(q)
      )
    })
  }, [users, statusFilter, roleFilter, keyword])

  const pendingCount = useMemo(
    () => users.filter((u) => u.status === 0).length,
    [users],
  )

  function toggleSelect(id: string) {
    setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
  }

  function toggleSelectAll() {
    if (selected.length === filtered.length) setSelected([])
    else setSelected(filtered.map((u) => u.id))
  }

  async function approve(ids: string[]) {
    if (USE_ADMIN_MOCK) {
      persist(users.map((u) => (ids.includes(u.id) && u.status === 0 ? { ...u, status: 1 } : u)))
      setSelected((prev) => prev.filter((id) => !ids.includes(id)))
      showToast(ids.length > 1 ? '已批量通过' : '已通过审核')
      return
    }
    setBusy(true)
    try {
      for (const id of ids) {
        await realAdminApi.approveUser(id)
      }
      setSelected((prev) => prev.filter((id) => !ids.includes(id)))
      showToast(ids.length > 1 ? '已批量通过' : '已通过审核')
      await refresh()
    } catch (err) {
      showToast(errMsg(err, '审核失败'))
    } finally {
      setBusy(false)
    }
  }

  async function reject(id: string) {
    if (USE_ADMIN_MOCK) {
      persist(users.map((u) => (u.id === id ? { ...u, status: 2 } : u)))
      showToast('已拒绝（标记为禁用）')
      return
    }
    try {
      await realAdminApi.rejectUser(id)
      showToast('已拒绝（标记为禁用）')
      await refresh()
    } catch (err) {
      showToast(errMsg(err, '拒绝失败'))
    }
  }

  async function setStatus(ids: string[], status: SysUserStatus) {
    if (USE_ADMIN_MOCK) {
      persist(users.map((u) => (ids.includes(u.id) ? { ...u, status } : u)))
      setSelected((prev) => prev.filter((id) => !ids.includes(id)))
      showToast(status === 1 ? '已启用' : '已禁用')
      return
    }
    setBusy(true)
    try {
      for (const id of ids) {
        if (status === 1) await realAdminApi.enableUser(id)
        else await realAdminApi.disableUser(id)
      }
      setSelected((prev) => prev.filter((id) => !ids.includes(id)))
      showToast(status === 1 ? '已启用' : '已禁用')
      await refresh()
    } catch (err) {
      showToast(errMsg(err, status === 1 ? '启用失败' : '禁用失败'))
    } finally {
      setBusy(false)
    }
  }

  async function saveRole() {
    if (!roleEdit) return
    if (USE_ADMIN_MOCK) {
      persist(users.map((u) => (u.id === roleEdit.id ? { ...u, role: nextRole } : u)))
      setRoleEdit(null)
      showToast('角色已更新')
      return
    }
    try {
      await realAdminApi.changeUserRole(roleEdit.id, nextRole)
      setRoleEdit(null)
      showToast('角色已更新')
      await refresh()
    } catch (err) {
      showToast(errMsg(err, '角色更新失败'))
    }
  }

  async function onCreate(e: FormEvent) {
    e.preventDefault()
    if (!createForm.name.trim() || !createForm.email.trim() || !createForm.password) {
      showToast('请完善必填项')
      return
    }
    const dept = DEPT_OPTIONS.find((d) => d.code === createForm.deptCode)

    if (USE_ADMIN_MOCK) {
      if (users.some((u) => u.email.toLowerCase() === createForm.email.trim().toLowerCase())) {
        showToast('邮箱已存在')
        return
      }
      const next: SysUserRecord = {
        id: `u-${Date.now()}`,
        name: createForm.name.trim(),
        empNo: createForm.empNo.trim() || `T${Date.now().toString().slice(-5)}`,
        email: createForm.email.trim().toLowerCase(),
        mobile: createForm.mobile.trim(),
        deptName: dept?.name || createForm.deptCode,
        deptCode: createForm.deptCode,
        role: createForm.role,
        status: 1,
        createdAt: nowStamp(),
      }
      persist([next, ...users])
      setCreating(false)
      setCreateForm(EMPTY_CREATE)
      showToast('用户已创建')
      return
    }

    setBusy(true)
    try {
      await realAdminApi.createUser({
        name: createForm.name.trim(),
        email: createForm.email.trim().toLowerCase(),
        mobile: createForm.mobile.trim() || undefined,
        empNo: createForm.empNo.trim() || undefined,
        deptName: dept?.name || createForm.deptCode,
        deptCode: createForm.deptCode,
        role: createForm.role,
        password: createForm.password,
      })
      setCreating(false)
      setCreateForm(EMPTY_CREATE)
      showToast('用户已创建')
      await refresh()
    } catch (err) {
      showToast(errMsg(err, '创建用户失败'))
    } finally {
      setBusy(false)
    }
  }

  async function confirmResetPassword() {
    if (!resetTarget) return
    if (USE_ADMIN_MOCK) {
      showToast('已发送重置邮件')
      setResetTarget(null)
      return
    }
    try {
      const res = await realAdminApi.resetPassword(resetTarget.id)
      showToast(res.message || '密码已重置')
      setResetTarget(null)
    } catch (err) {
      showToast(errMsg(err, '重置密码失败'))
    }
  }

  function librariesPreview(user: SysUserRecord) {
    if (user.role === 'SYS_ADMIN' || user.role === 'KB_ADMIN') {
      return '全部知识库（管理角色）'
    }
    if (!USE_ADMIN_MOCK) return '—'
    return librariesForUser(user.id, user.deptCode).join('、') || '无（受 ACL 限制）'
  }

  const batchPending = selected
    .map((id) => users.find((u) => u.id === id))
    .filter((u): u is SysUserRecord => !!u && u.status === 0)
  const batchActive = selected
    .map((id) => users.find((u) => u.id === id))
    .filter((u): u is SysUserRecord => !!u && u.status === 1)

  return (
    <div className="adminPage">
      {toastNode}
      <div className="adminPageHeader">
        <div>
          <h1>用户管理</h1>
          <p className="adminMuted">审核注册、启用禁用、调整角色</p>
        </div>
        <button type="button" className="adminBtnPrimary" onClick={() => setCreating(true)}>
          <Plus size={16} />
          新建用户
        </button>
      </div>

      <div className="adminSegment">
        {(
          [
            ['all', '全部'],
            ['0', '待审核'],
            ['1', '正常'],
            ['2', '禁用'],
          ] as const
        ).map(([value, label]) => (
          <button
            key={value}
            type="button"
            className={statusFilter === value ? 'isActive' : undefined}
            onClick={() => setStatusFilter(value)}
          >
            {label}
            {value === '0' ? ` (${pendingCount})` : ''}
          </button>
        ))}
      </div>

      <div className="adminToolbar adminToolbarWrap">
        <label className="adminInlineField">
          角色
          <select
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value as RoleFilter)}
          >
            <option value="all">全部</option>
            <option value="EMPLOYEE">普通员工</option>
            <option value="KB_ADMIN">知识管理员</option>
            <option value="SYS_ADMIN">系统管理员</option>
          </select>
        </label>
        <label className="adminSearch">
          <Search size={16} />
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="姓名 / 工号 / 邮箱 / 手机 / 部门"
          />
        </label>
        <div className="adminHeaderActions">
          <button
            type="button"
            className="adminGhostBtn"
            disabled={!batchPending.length || busy}
            onClick={() => void approve(batchPending.map((u) => u.id))}
          >
            批量通过
          </button>
          <button
            type="button"
            className="adminGhostBtn"
            disabled={!batchActive.length || busy}
            onClick={() => void setStatus(batchActive.map((u) => u.id), 2)}
          >
            批量禁用
          </button>
        </div>
      </div>

      <div className="adminTableWrap">
        <table className="adminTable">
          <thead>
            <tr>
              <th>
                <input
                  type="checkbox"
                  checked={filtered.length > 0 && selected.length === filtered.length}
                  onChange={toggleSelectAll}
                  aria-label="全选"
                />
              </th>
              <th>姓名</th>
              <th>工号</th>
              <th>邮箱</th>
              <th>手机</th>
              <th>部门</th>
              <th>角色</th>
              <th>状态</th>
              <th>注册时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={10} className="adminTableEmpty">
                  加载中…
                </td>
              </tr>
            ) : filtered.length === 0 ? (
              <tr>
                <td colSpan={10} className="adminTableEmpty">
                  暂无匹配用户
                </td>
              </tr>
            ) : (
              filtered.map((user) => (
                <tr key={user.id}>
                  <td>
                    <input
                      type="checkbox"
                      checked={selected.includes(user.id)}
                      onChange={() => toggleSelect(user.id)}
                      aria-label={`选择 ${user.name}`}
                    />
                  </td>
                  <td>
                    <button type="button" className="adminTextLink" onClick={() => setDetail(user)}>
                      {user.name}
                    </button>
                  </td>
                  <td>{user.empNo}</td>
                  <td>{user.email}</td>
                  <td>{user.mobile || '—'}</td>
                  <td>{user.deptName}</td>
                  <td>{ROLE_LABEL[user.role]}</td>
                  <td>
                    <span className={`adminUserStatus adminUserStatus--${user.status}`}>
                      {STATUS_LABEL[user.status]}
                    </span>
                  </td>
                  <td>{user.createdAt}</td>
                  <td>
                    <div className="adminRowActions">
                      {user.status === 0 && (
                        <>
                          <button type="button" onClick={() => void approve([user.id])}>
                            通过
                          </button>
                          <button type="button" onClick={() => void reject(user.id)}>
                            拒绝
                          </button>
                        </>
                      )}
                      {user.status === 1 && (
                        <>
                          <button type="button" onClick={() => void setStatus([user.id], 2)}>
                            禁用
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              setRoleEdit(user)
                              setNextRole(user.role)
                            }}
                          >
                            调整角色
                          </button>
                          <button type="button" onClick={() => setResetTarget(user)}>
                            重置密码
                          </button>
                        </>
                      )}
                      {user.status === 2 && (
                        <button type="button" onClick={() => void setStatus([user.id], 1)}>
                          启用
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {creating && (
        <div className="adminDrawerMask" onClick={() => setCreating(false)} role="presentation">
          <aside className="adminDrawer" onClick={(e) => e.stopPropagation()} role="dialog">
            <h2>新建用户</h2>
            <form className="adminForm" onSubmit={(e) => void onCreate(e)}>
              <label>
                姓名
                <input
                  value={createForm.name}
                  onChange={(e) => setCreateForm((s) => ({ ...s, name: e.target.value }))}
                  required
                />
              </label>
              <label>
                企业邮箱
                <input
                  type="email"
                  value={createForm.email}
                  onChange={(e) => setCreateForm((s) => ({ ...s, email: e.target.value }))}
                  required
                />
              </label>
              <label>
                手机
                <input
                  value={createForm.mobile}
                  onChange={(e) => setCreateForm((s) => ({ ...s, mobile: e.target.value }))}
                />
              </label>
              <label>
                工号
                <input
                  value={createForm.empNo}
                  onChange={(e) => setCreateForm((s) => ({ ...s, empNo: e.target.value }))}
                />
              </label>
              <label>
                部门
                <select
                  value={createForm.deptCode}
                  onChange={(e) => setCreateForm((s) => ({ ...s, deptCode: e.target.value }))}
                >
                  {DEPT_OPTIONS.map((d) => (
                    <option key={d.code} value={d.code}>
                      {d.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                角色
                <select
                  value={createForm.role}
                  onChange={(e) =>
                    setCreateForm((s) => ({ ...s, role: e.target.value as AdminRole }))
                  }
                >
                  <option value="EMPLOYEE">普通员工</option>
                  <option value="KB_ADMIN">知识管理员</option>
                  <option value="SYS_ADMIN">系统管理员</option>
                </select>
              </label>
              <label>
                初始密码
                <input
                  type="password"
                  value={createForm.password}
                  onChange={(e) => setCreateForm((s) => ({ ...s, password: e.target.value }))}
                  required
                />
              </label>
              <div className="adminModalActions">
                <button type="button" className="adminGhostBtn" onClick={() => setCreating(false)}>
                  取消
                </button>
                <button type="submit" className="adminBtnPrimary" disabled={busy}>
                  创建
                </button>
              </div>
            </form>
          </aside>
        </div>
      )}

      {detail && (
        <div className="adminDrawerMask" onClick={() => setDetail(null)} role="presentation">
          <aside className="adminDrawer" onClick={(e) => e.stopPropagation()} role="dialog">
            <h2>用户详情</h2>
            <dl className="adminDescList">
              <div>
                <dt>姓名</dt>
                <dd>{detail.name}</dd>
              </div>
              <div>
                <dt>工号</dt>
                <dd>{detail.empNo}</dd>
              </div>
              <div>
                <dt>邮箱</dt>
                <dd>{detail.email}</dd>
              </div>
              <div>
                <dt>手机</dt>
                <dd>{detail.mobile || '—'}</dd>
              </div>
              <div>
                <dt>部门</dt>
                <dd>
                  {detail.deptName} ({detail.deptCode})
                </dd>
              </div>
              <div>
                <dt>角色</dt>
                <dd>{ROLE_LABEL[detail.role]}</dd>
              </div>
              <div>
                <dt>状态</dt>
                <dd>{STATUS_LABEL[detail.status]}</dd>
              </div>
              <div>
                <dt>最近登录</dt>
                <dd>{detail.lastLoginAt || '—'}</dd>
              </div>
              <div>
                <dt>可访问知识库</dt>
                <dd>{librariesPreview(detail)}</dd>
              </div>
            </dl>
            <button type="button" className="adminGhostBtn" onClick={() => setDetail(null)}>
              关闭
            </button>
          </aside>
        </div>
      )}

      {roleEdit && (
        <div className="adminModalMask" onClick={() => setRoleEdit(null)} role="presentation">
          <div className="adminModal" onClick={(e) => e.stopPropagation()} role="dialog">
            <h2>调整角色 · {roleEdit.name}</h2>
            <label className="adminInlineField" style={{ width: '100%' }}>
              角色
              <select value={nextRole} onChange={(e) => setNextRole(e.target.value as AdminRole)}>
                <option value="EMPLOYEE">普通员工</option>
                <option value="KB_ADMIN">知识管理员</option>
                <option value="SYS_ADMIN">系统管理员</option>
              </select>
            </label>
            <div className="adminModalActions">
              <button type="button" className="adminGhostBtn" onClick={() => setRoleEdit(null)}>
                取消
              </button>
              <button type="button" className="adminBtnPrimary" onClick={() => void saveRole()}>
                保存
              </button>
            </div>
          </div>
        </div>
      )}

      {resetTarget && (
        <div className="adminModalMask" onClick={() => setResetTarget(null)} role="presentation">
          <div className="adminModal" onClick={(e) => e.stopPropagation()} role="dialog">
            <h2>重置密码</h2>
            <p>
              确认为「{resetTarget.name}」
              {USE_ADMIN_MOCK ? '发送密码重置邮件？（Mock）' : '重置密码？'}
            </p>
            <div className="adminModalActions">
              <button type="button" className="adminGhostBtn" onClick={() => setResetTarget(null)}>
                取消
              </button>
              <button
                type="button"
                className="adminBtnPrimary"
                onClick={() => void confirmResetPassword()}
              >
                {USE_ADMIN_MOCK ? '确认发送' : '确认重置'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
