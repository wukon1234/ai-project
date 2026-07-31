import { useEffect, useRef, useState } from 'react'
import {
  BarChart3,
  Bell,
  Bookmark,
  ChevronRight,
  CircleHelp,
  History,
  LogOut,
  Moon,
  Phone,
  Sun,
  UserCircle2,
} from 'lucide-react'
import { getProfile, updatePreferences, type ProfileInfo, type UserPreferences } from './api'
import { useTheme, type ThemeMode } from './theme'

type ScopeId = 'product' | 'hr' | 'tech' | 'support'

const scopeOptions: Array<{ id: ScopeId; label: string }> = [
  { id: 'product', label: '产品知识库' },
  { id: 'hr', label: '人事制度库' },
  { id: 'tech', label: '技术文档库' },
  { id: 'support', label: '售后 FAQ' },
]

type ProfilePageProps = {
  onBack: () => void
  onOpenFavorites: () => void
  onOpenHistory: () => void
  onOpenHelp: () => void
  onOpenStats: () => void
  onLogout: () => void
}

function ProfilePage({
  onBack,
  onOpenFavorites,
  onOpenHistory,
  onOpenHelp,
  onOpenStats,
  onLogout,
}: ProfilePageProps) {
  const { theme, setTheme } = useTheme()
  const [profile, setProfile] = useState<ProfileInfo | null>(null)
  const [scopes, setScopes] = useState<ScopeId[]>(['hr', 'product'])
  const [notifyKnowledge, setNotifyKnowledge] = useState(true)
  const [notifyMention, setNotifyMention] = useState(true)
  const [toast, setToast] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const ready = useRef(false)

  function showToast(message: string) {
    setToast(message)
    window.setTimeout(() => setToast(null), 1800)
  }

  useEffect(() => {
    let alive = true
    ;(async () => {
      try {
        const data = await getProfile()
        if (!alive) return
        setProfile(data)
        const prefs = data.preferences
        if (prefs) {
          const nextScopes = (prefs.defaultKbScopes || []).filter((s): s is ScopeId =>
            scopeOptions.some((o) => o.id === s),
          )
          if (nextScopes.length) setScopes(nextScopes)
          setNotifyKnowledge(Number(prefs.notifyKbUpdate) === 1)
          setNotifyMention(Number(prefs.notifyMention) === 1)
          if (prefs.themeMode === 'light' || prefs.themeMode === 'dark' || prefs.themeMode === 'system') {
            setTheme(prefs.themeMode)
          }
        }
      } catch (err) {
        if (alive) showToast(err instanceof Error ? err.message : '加载个人资料失败')
      } finally {
        ready.current = true
      }
    })()
    return () => {
      alive = false
    }
  }, [setTheme])

  async function persist(partial?: Partial<UserPreferences>, toastMsg?: string) {
    if (!ready.current) return
    if (scopes.length === 0) {
      showToast('请至少选择一个默认知识库')
      return
    }
    setSaving(true)
    try {
      const body: UserPreferences = {
        notifyKbUpdate: notifyKnowledge ? 1 : 0,
        notifyMention: notifyMention ? 1 : 0,
        themeMode: theme,
        defaultKbScopes: scopes,
        ...partial,
      }
      const saved = await updatePreferences(body)
      setNotifyKnowledge(Number(saved.notifyKbUpdate) === 1)
      setNotifyMention(Number(saved.notifyMention) === 1)
      if (saved.themeMode === 'light' || saved.themeMode === 'dark' || saved.themeMode === 'system') {
        setTheme(saved.themeMode)
      }
      const nextScopes = (saved.defaultKbScopes || []).filter((s): s is ScopeId =>
        scopeOptions.some((o) => o.id === s),
      )
      if (nextScopes.length) setScopes(nextScopes)
      if (toastMsg) showToast(toastMsg)
    } catch (err) {
      showToast(err instanceof Error ? err.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  function toggleScope(id: ScopeId) {
    setScopes((prev) => {
      const next = prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
      window.setTimeout(() => {
        void persist({ defaultKbScopes: next }, '默认知识库已更新')
      }, 0)
      return next
    })
  }

  function onThemeChange(next: ThemeMode, label: string) {
    setTheme(next)
    void persist({ themeMode: next }, `外观已切换为「${label}」`)
  }

  const roleLabel =
    profile?.roleCode === 'admin' || profile?.roleCode === 'ADMIN' ? '管理员' : '普通员工'

  return (
    <div className="pfPage">
      <header className="pfHeader">
        <button type="button" className="pfGhostBtn" onClick={onBack}>
          返回问答
        </button>
        <h1>个人中心</h1>
      </header>

      <main className="pfBody">
        <section className="pfUserCard">
          <div className="pfAvatar">
            <UserCircle2 size={42} />
          </div>
          <div>
            <div className="pfName">{profile?.name || '加载中…'}</div>
            <div className="pfDept">
              {profile?.deptName || '—'} · 工号 {profile?.empNo || '—'}
            </div>
            <div className="pfRole">{roleLabel}</div>
          </div>
        </section>

        <section className="pfMenuCard">
          <div className="pfMenuItem">
            <div className="pfMenuLeft">
              <Bell size={18} />
              <div>
                <div className="pfMenuTitle">消息通知设置</div>
                <div className="pfMenuDesc">知识更新、@我的</div>
              </div>
            </div>
            <div className="pfSwitchGroup">
              <label>
                <input
                  type="checkbox"
                  checked={notifyKnowledge}
                  disabled={saving}
                  onChange={(e) => {
                    const checked = e.target.checked
                    setNotifyKnowledge(checked)
                    void persist({ notifyKbUpdate: checked ? 1 : 0 }, '通知设置已保存')
                  }}
                />
                知识更新
              </label>
              <label>
                <input
                  type="checkbox"
                  checked={notifyMention}
                  disabled={saving}
                  onChange={(e) => {
                    const checked = e.target.checked
                    setNotifyMention(checked)
                    void persist({ notifyMention: checked ? 1 : 0 }, '通知设置已保存')
                  }}
                />
                @我的
              </label>
            </div>
          </div>

          <div className="pfMenuItem">
            <div className="pfMenuLeft">
              <Moon size={18} />
              <div>
                <div className="pfMenuTitle">外观</div>
                <div className="pfMenuDesc">浅色 / 深色 / 跟随系统</div>
              </div>
            </div>
            <div className="pfThemeGroup">
              {(
                [
                  { id: 'light', label: '浅色', icon: <Sun size={14} /> },
                  { id: 'dark', label: '深色', icon: <Moon size={14} /> },
                  { id: 'system', label: '跟随系统', icon: null },
                ] as const
              ).map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={`pfThemeBtn ${theme === item.id ? 'pfThemeBtnActive' : ''}`}
                  onClick={() => onThemeChange(item.id, item.label)}
                  disabled={saving}
                >
                  {item.icon}
                  {item.label}
                </button>
              ))}
            </div>
          </div>

          <div className="pfMenuItem pfMenuItemColumn">
            <div className="pfMenuLeft">
              <Bookmark size={18} />
              <div>
                <div className="pfMenuTitle">默认知识库范围</div>
                <div className="pfMenuDesc">多选偏好，新建会话时优先使用</div>
              </div>
            </div>
            <div className="pfScopeChips">
              {scopeOptions.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={`pfScopeChip ${scopes.includes(item.id) ? 'pfScopeChipActive' : ''}`}
                  onClick={() => toggleScope(item.id)}
                  disabled={saving}
                >
                  {item.label}
                </button>
              ))}
            </div>
          </div>

          <button type="button" className="pfLinkRow" onClick={onOpenFavorites}>
            <div className="pfMenuLeft">
              <Bookmark size={18} />
              <div>
                <div className="pfMenuTitle">我的收藏</div>
                <div className="pfMenuDesc">收藏的文档和回答</div>
              </div>
            </div>
            <ChevronRight size={16} />
          </button>

          <button type="button" className="pfLinkRow" onClick={onOpenHistory}>
            <div className="pfMenuLeft">
              <History size={18} />
              <div>
                <div className="pfMenuTitle">我的对话历史</div>
                <div className="pfMenuDesc">按时间回顾提问记录</div>
              </div>
            </div>
            <ChevronRight size={16} />
          </button>

          <button type="button" className="pfLinkRow" onClick={onOpenStats}>
            <div className="pfMenuLeft">
              <BarChart3 size={18} />
              <div>
                <div className="pfMenuTitle">我的使用统计</div>
                <div className="pfMenuDesc">查看提问与反馈概览</div>
              </div>
            </div>
            <ChevronRight size={16} />
          </button>

          <button type="button" className="pfLinkRow" onClick={onOpenHelp}>
            <div className="pfMenuLeft">
              <CircleHelp size={18} />
              <div>
                <div className="pfMenuTitle">使用帮助 / 常见问题</div>
                <div className="pfMenuDesc">快速上手与常见疑问</div>
              </div>
            </div>
            <ChevronRight size={16} />
          </button>
        </section>

        <section className="pfContactCard">
          <div className="pfContactTitle">
            <Phone size={16} />
            联系知识管理员
          </div>
          <div className="pfContactName">王婷 · 知识运营（研发部对接）</div>
          <div className="pfContactMeta">企业微信 / 分机 8066</div>
        </section>

        <button type="button" className="pfLogout" onClick={onLogout}>
          <LogOut size={16} />
          退出登录
        </button>

        <div className="pfHint">无用户管理、角色配置、模型设置、审计日志入口</div>
      </main>

      {toast ? (
        <div className="pfToast" role="status">
          {toast}
        </div>
      ) : null}
    </div>
  )
}

export default ProfilePage
