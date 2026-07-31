import { useCallback, useEffect, useMemo, useState } from 'react'
import { Info, Plus, Search } from 'lucide-react'
import { AdminApiError, USE_ADMIN_MOCK, realAdminApi } from './api'
import {
  DEPT_OPTIONS,
  MOCK_PEOPLE,
  loadAclRules,
  loadLibraries,
  saveAclRules,
  saveLibraries,
} from './mock'
import type { AclRule, AclSubjectType, LibraryRecord, MockPerson } from './types'
import { useAdminToast } from './useAdminToast'

type Props = {
  initialLibrary?: string
}

function errMsg(err: unknown, fallback: string) {
  return err instanceof AdminApiError ? err.message : fallback
}

export default function AdminAcl({ initialLibrary = '' }: Props) {
  const [libraries, setLibraries] = useState<LibraryRecord[]>(() =>
    USE_ADMIN_MOCK ? loadLibraries() : [],
  )
  const [rules, setRules] = useState<AclRule[]>(() => (USE_ADMIN_MOCK ? loadAclRules() : []))
  const [people, setPeople] = useState<MockPerson[]>(() => (USE_ADMIN_MOCK ? MOCK_PEOPLE : []))
  const [loading, setLoading] = useState(!USE_ADMIN_MOCK)
  const [rulesLoading, setRulesLoading] = useState(false)
  const [activeCode, setActiveCode] = useState(initialLibrary || '')
  const [adding, setAdding] = useState(false)
  const [subjectType, setSubjectType] = useState<AclSubjectType>('user')
  const [userQuery, setUserQuery] = useState('')
  const [selectedUsers, setSelectedUsers] = useState<string[]>([])
  const [deptCode, setDeptCode] = useState(DEPT_OPTIONS[0]?.code || 'RD')
  const [confirmClear, setConfirmClear] = useState(false)
  const [confirmPublicOff, setConfirmPublicOff] = useState(false)
  const [mutating, setMutating] = useState(false)
  const { showToast, toastNode } = useAdminToast()

  const refreshLibraries = useCallback(async () => {
    if (USE_ADMIN_MOCK) {
      const libs = loadLibraries()
      setLibraries(libs)
      setActiveCode((prev) => prev || initialLibrary || libs[0]?.code || '')
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const libs = await realAdminApi.listLibraries()
      setLibraries(libs)
      setActiveCode((prev) => prev || initialLibrary || libs[0]?.code || '')
    } catch (err) {
      showToast(errMsg(err, '知识库加载失败'))
    } finally {
      setLoading(false)
    }
  }, [initialLibrary, showToast])

  const refreshAcl = useCallback(
    async (code: string) => {
      if (!code) {
        setRules([])
        return
      }
      if (USE_ADMIN_MOCK) {
        setRules(loadAclRules())
        return
      }
      setRulesLoading(true)
      try {
        const list = await realAdminApi.listAcl(code)
        setRules(list)
      } catch (err) {
        showToast(errMsg(err, 'ACL 加载失败'))
        setRules([])
      } finally {
        setRulesLoading(false)
      }
    },
    [showToast],
  )

  useEffect(() => {
    void refreshLibraries()
  }, [refreshLibraries])

  useEffect(() => {
    if (initialLibrary) setActiveCode(initialLibrary)
  }, [initialLibrary])

  useEffect(() => {
    void refreshAcl(activeCode)
  }, [activeCode, refreshAcl])

  useEffect(() => {
    if (USE_ADMIN_MOCK) {
      setPeople(MOCK_PEOPLE)
      return
    }
    let cancelled = false
    realAdminApi
      .listUsers({ status: 1, size: 100 })
      .then((page) => {
        if (cancelled) return
        setPeople(
          page.records.map((u) => ({
            id: u.id,
            name: u.name,
            empNo: u.empNo,
            email: u.email,
            deptCode: u.deptCode,
            deptName: u.deptName,
          })),
        )
      })
      .catch((err) => {
        if (!cancelled) showToast(errMsg(err, '用户列表加载失败'))
      })
    return () => {
      cancelled = true
    }
  }, [showToast])

  const activeLib = libraries.find((l) => l.code === activeCode) || null
  const libRules = useMemo(() => {
    if (USE_ADMIN_MOCK) return rules.filter((r) => r.libraryCode === activeCode)
    return rules
  }, [rules, activeCode])

  const peopleFiltered = useMemo(() => {
    const q = userQuery.trim().toLowerCase()
    if (!q) return people
    return people.filter(
      (p) =>
        p.name.toLowerCase().includes(q) ||
        p.empNo.includes(q) ||
        p.email.toLowerCase().includes(q),
    )
  }, [userQuery, people])

  function persistRules(next: AclRule[]) {
    setRules(next)
    saveAclRules(next)
  }

  function persistLibraries(next: LibraryRecord[]) {
    setLibraries(next)
    saveLibraries(next)
  }

  function togglePublicRead(next: boolean) {
    if (!activeLib) return
    if (!next && activeLib.publicRead) {
      setConfirmPublicOff(true)
      return
    }
    void applyPublicRead(next)
  }

  async function applyPublicRead(next: boolean) {
    if (!activeLib) return
    if (USE_ADMIN_MOCK) {
      persistLibraries(
        libraries.map((l) => (l.code === activeLib.code ? { ...l, publicRead: next } : l)),
      )
      showToast(next ? '已设为全员可读' : '已关闭全员可读')
      setConfirmPublicOff(false)
      return
    }
    setMutating(true)
    try {
      await realAdminApi.setPublicRead(activeLib.code, next)
      setLibraries((prev) =>
        prev.map((l) => (l.code === activeLib.code ? { ...l, publicRead: next } : l)),
      )
      showToast(next ? '已设为全员可读' : '已关闭全员可读')
      setConfirmPublicOff(false)
    } catch (err) {
      showToast(errMsg(err, '更新全员可读失败'))
    } finally {
      setMutating(false)
    }
  }

  async function removeRule(id: string) {
    if (USE_ADMIN_MOCK) {
      persistRules(rules.filter((r) => r.id !== id))
      showToast('规则已移除')
      return
    }
    try {
      await realAdminApi.removeAcl(id)
      showToast('规则已移除')
      await refreshAcl(activeCode)
    } catch (err) {
      showToast(errMsg(err, '移除失败'))
    }
  }

  async function clearRules() {
    if (USE_ADMIN_MOCK) {
      persistRules(rules.filter((r) => r.libraryCode !== activeCode))
      setConfirmClear(false)
      showToast('已清空本库 ACL 规则')
      return
    }
    setMutating(true)
    try {
      for (const rule of libRules) {
        await realAdminApi.removeAcl(rule.id)
      }
      setConfirmClear(false)
      showToast('已清空本库 ACL 规则')
      await refreshAcl(activeCode)
    } catch (err) {
      showToast(errMsg(err, '清空失败'))
    } finally {
      setMutating(false)
    }
  }

  async function submitRules() {
    if (!activeLib) return
    if (USE_ADMIN_MOCK) {
      const next = [...rules]
      if (subjectType === 'user') {
        if (!selectedUsers.length) {
          showToast('请至少选择一名用户')
          return
        }
        selectedUsers.forEach((uid) => {
          if (
            next.some(
              (r) =>
                r.libraryCode === activeCode && r.subjectType === 'user' && r.subjectId === uid,
            )
          ) {
            return
          }
          const person = people.find((p) => p.id === uid)
          if (!person) return
          next.unshift({
            id: `acl-${Date.now()}-${uid}`,
            libraryCode: activeCode,
            subjectType: 'user',
            subjectId: uid,
            subjectLabel: `${person.name} · ${person.empNo}`,
            perm: 'READ',
            source: '用户授权',
          })
        })
      } else {
        if (
          next.some(
            (r) =>
              r.libraryCode === activeCode &&
              r.subjectType === 'dept' &&
              r.subjectId === deptCode,
          )
        ) {
          showToast('该部门规则已存在')
          return
        }
        const dept = DEPT_OPTIONS.find((d) => d.code === deptCode)
        next.unshift({
          id: `acl-${Date.now()}-${deptCode}`,
          libraryCode: activeCode,
          subjectType: 'dept',
          subjectId: deptCode,
          subjectLabel: `${dept?.name || deptCode} (${deptCode})`,
          perm: 'READ',
          source: '部门授权',
        })
      }
      persistRules(next)
      setAdding(false)
      setSelectedUsers([])
      setUserQuery('')
      showToast('规则已添加')
      return
    }

    setMutating(true)
    try {
      if (subjectType === 'user') {
        if (!selectedUsers.length) {
          showToast('请至少选择一名用户')
          return
        }
        for (const uid of selectedUsers) {
          if (libRules.some((r) => r.subjectType === 'user' && r.subjectId === uid)) continue
          const person = people.find((p) => p.id === uid)
          if (!person) continue
          await realAdminApi.addAcl(activeCode, {
            subjectType: 'user',
            subjectId: uid,
            subjectLabel: `${person.name} · ${person.empNo}`,
            perm: 'READ',
          })
        }
      } else {
        if (libRules.some((r) => r.subjectType === 'dept' && r.subjectId === deptCode)) {
          showToast('该部门规则已存在')
          return
        }
        const dept = DEPT_OPTIONS.find((d) => d.code === deptCode)
        await realAdminApi.addAcl(activeCode, {
          subjectType: 'dept',
          subjectId: deptCode,
          subjectLabel: `${dept?.name || deptCode} (${deptCode})`,
          perm: 'READ',
        })
      }
      setAdding(false)
      setSelectedUsers([])
      setUserQuery('')
      showToast('规则已添加')
      await refreshAcl(activeCode)
    } catch (err) {
      showToast(errMsg(err, '添加失败'))
    } finally {
      setMutating(false)
    }
  }

  return (
    <div className="adminPage">
      {toastNode}
      <div className="adminNotice">
        <Info size={16} />
        <span>
          KB_ADMIN 默认可管理全部库；普通员工按 ACL + 可选全员可读策略。用户侧问答/搜索/浏览只展示有权库。
        </span>
      </div>

      <div className="adminPageHeader">
        <div>
          <h1>权限配置</h1>
          <p className="adminMuted">MVP 仅 READ；按用户 / 部门授权</p>
        </div>
      </div>

      <div className="adminAclLayout">
        <aside className="adminAclLibs">
          {loading ? (
            <p className="adminMuted" style={{ padding: 12 }}>
              加载中…
            </p>
          ) : libraries.length === 0 ? (
            <p className="adminMuted" style={{ padding: 12 }}>
              暂无知识库
            </p>
          ) : (
            libraries.map((lib) => (
              <button
                key={lib.code}
                type="button"
                className={`adminAclLibItem${activeCode === lib.code ? ' isActive' : ''}`}
                onClick={() => setActiveCode(lib.code)}
              >
                <strong>{lib.name}</strong>
                <span>
                  {lib.code}
                  {lib.publicRead ? ' · 全员可读' : ''}
                </span>
              </button>
            ))
          )}
        </aside>

        <section className="adminPanel adminAclMain">
          {!activeLib ? (
            <div className="adminEmpty">
              <h2>请选择知识库</h2>
            </div>
          ) : (
            <>
              <div className="adminPanelHead">
                <div>
                  <h2>{activeLib.name}</h2>
                  <p className="adminMuted">code: {activeLib.code}</p>
                </div>
                <div className="adminHeaderActions">
                  <label className="adminSwitch">
                    <input
                      type="checkbox"
                      checked={!!activeLib.publicRead}
                      disabled={mutating}
                      onChange={(e) => togglePublicRead(e.target.checked)}
                    />
                    全员可读
                  </label>
                  <button type="button" className="adminBtnPrimary" onClick={() => setAdding(true)}>
                    <Plus size={14} />
                    添加规则
                  </button>
                  <button type="button" className="adminGhostBtn" disabled title="后续支持">
                    批量导入
                  </button>
                  <button
                    type="button"
                    className="adminGhostBtn"
                    disabled={!libRules.length || mutating}
                    onClick={() => setConfirmClear(true)}
                  >
                    清空规则
                  </button>
                </div>
              </div>

              {rulesLoading ? (
                <div className="adminEmpty">
                  <h2>加载中…</h2>
                </div>
              ) : libRules.length === 0 ? (
                <div className="adminEmpty">
                  <h2>该库暂无额外 ACL</h2>
                  <p className="adminMuted">
                    {activeLib.publicRead
                      ? '当前已开启全员可读，普通员工可见。'
                      : '未配置规则且未开启全员可读时，普通员工将不可见本库。'}
                  </p>
                </div>
              ) : (
                <div className="adminTableWrap">
                  <table className="adminTable">
                    <thead>
                      <tr>
                        <th>主体类型</th>
                        <th>主体</th>
                        <th>权限</th>
                        <th>来源</th>
                        <th>操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {libRules.map((rule) => (
                        <tr key={rule.id}>
                          <td>{rule.subjectType === 'user' ? '用户' : '部门'}</td>
                          <td>{rule.subjectLabel}</td>
                          <td>
                            <span className="adminActionChip">{rule.perm}</span>
                          </td>
                          <td>{rule.source}</td>
                          <td>
                            <div className="adminRowActions">
                              <button type="button" onClick={() => void removeRule(rule.id)}>
                                移除
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}
        </section>
      </div>

      {adding && (
        <div className="adminModalMask" onClick={() => setAdding(false)} role="presentation">
          <div
            className="adminModal adminModalWide"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
          >
            <h2>添加 ACL 规则</h2>
            <div className="adminForm">
              <label>
                主体类型
                <select
                  value={subjectType}
                  onChange={(e) => setSubjectType(e.target.value as AclSubjectType)}
                >
                  <option value="user">用户</option>
                  <option value="dept">部门</option>
                </select>
              </label>

              {subjectType === 'user' ? (
                <>
                  <label className="adminSearch">
                    <Search size={16} />
                    <input
                      value={userQuery}
                      onChange={(e) => setUserQuery(e.target.value)}
                      placeholder="搜索姓名 / 工号 / 邮箱"
                    />
                  </label>
                  <div className="adminCheckList">
                    {peopleFiltered.map((person) => (
                      <label key={person.id} className="adminCheckItem">
                        <input
                          type="checkbox"
                          checked={selectedUsers.includes(person.id)}
                          onChange={(e) => {
                            setSelectedUsers((prev) =>
                              e.target.checked
                                ? [...prev, person.id]
                                : prev.filter((id) => id !== person.id),
                            )
                          }}
                        />
                        <span>
                          {person.name} · {person.empNo}
                          <em>{person.email}</em>
                        </span>
                      </label>
                    ))}
                  </div>
                </>
              ) : (
                <label>
                  部门
                  <select value={deptCode} onChange={(e) => setDeptCode(e.target.value)}>
                    {DEPT_OPTIONS.map((d) => (
                      <option key={d.code} value={d.code}>
                        {d.name} ({d.code})
                      </option>
                    ))}
                  </select>
                </label>
              )}

              <label>
                权限
                <input value="READ" disabled />
              </label>
            </div>
            <div className="adminModalActions">
              <button type="button" className="adminGhostBtn" onClick={() => setAdding(false)}>
                取消
              </button>
              <button
                type="button"
                className="adminBtnPrimary"
                disabled={mutating}
                onClick={() => void submitRules()}
              >
                添加
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmClear && (
        <div className="adminModalMask" onClick={() => setConfirmClear(false)} role="presentation">
          <div className="adminModal" onClick={(e) => e.stopPropagation()} role="dialog">
            <h2>清空 ACL 规则</h2>
            <p>
              确定清空「{activeLib?.name}」的全部 ACL 规则？普通员工可能立即不可见（除非全员可读）。
            </p>
            <div className="adminModalActions">
              <button type="button" className="adminGhostBtn" onClick={() => setConfirmClear(false)}>
                取消
              </button>
              <button
                type="button"
                className="adminBtnDanger"
                disabled={mutating}
                onClick={() => void clearRules()}
              >
                确认清空
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmPublicOff && (
        <div
          className="adminModalMask"
          onClick={() => setConfirmPublicOff(false)}
          role="presentation"
        >
          <div className="adminModal" onClick={(e) => e.stopPropagation()} role="dialog">
            <h2>关闭全员可读</h2>
            <p>关闭后，仅 ACL 命中的用户/部门可访问「{activeLib?.name}」。确认关闭？</p>
            <div className="adminModalActions">
              <button
                type="button"
                className="adminGhostBtn"
                onClick={() => setConfirmPublicOff(false)}
              >
                取消
              </button>
              <button
                type="button"
                className="adminBtnDanger"
                disabled={mutating}
                onClick={() => void applyPublicRead(false)}
              >
                确认关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
