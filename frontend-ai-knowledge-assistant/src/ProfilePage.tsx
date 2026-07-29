import { useState } from 'react'
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
import { useTheme, type ThemeMode } from './theme'

type ScopeId = 'product' | 'hr' | 'tech' | 'support'

const scopeOptions: Array<{ id: ScopeId; label: string }> = [
  { id: 'product', label: '产品知识库' },
  { id: 'hr', label: '人事制度库' },
  { id: 'tech', label: '技术文档库' },
  { id: 'support', label: '售后 FAQ' }
]

type ProfilePageProps = {
  onBack: () => void
  onOpenFavorites: () => void
  onOpenHistory: () => void
  onOpenHelp: () => void
  onLogout: () => void
}

function ProfilePage({
  onBack,
  onOpenFavorites,
  onOpenHistory,
  onOpenHelp,
  onLogout
}: ProfilePageProps) {
  const { theme, setTheme } = useTheme()
  const [scopes, setScopes] = useState<ScopeId[]>(['hr', 'product'])
  const [notifyKnowledge, setNotifyKnowledge] = useState(true)
  const [notifyMention, setNotifyMention] = useState(true)
  const [toast, setToast] = useState<string | null>(null)

  function toggleScope(id: ScopeId) {
    setScopes((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
  }

  function showToast(message: string) {
    setToast(message)
    window.setTimeout(() => setToast(null), 1800)
  }

  function onThemeChange(next: ThemeMode, label: string) {
    setTheme(next)
    showToast(`外观已切换为「${label}」`)
  }

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
            <div className="pfName">张明</div>
            <div className="pfDept">研发部 · 工号 100234</div>
            <div className="pfRole">普通员工</div>
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
                  onChange={(e) => setNotifyKnowledge(e.target.checked)}
                />
                知识更新
              </label>
              <label>
                <input
                  type="checkbox"
                  checked={notifyMention}
                  onChange={(e) => setNotifyMention(e.target.checked)}
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
                  { id: 'system', label: '跟随系统', icon: null }
                ] as const
              ).map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={`pfThemeBtn ${theme === item.id ? 'pfThemeBtnActive' : ''}`}
                  onClick={() => onThemeChange(item.id, item.label)}
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
                <div className="pfMenuDesc">多选偏好，问答时优先使用</div>
              </div>
            </div>
            <div className="pfScopeChips">
              {scopeOptions.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={`pfScopeChip ${scopes.includes(item.id) ? 'pfScopeChipActive' : ''}`}
                  onClick={() => toggleScope(item.id)}
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

          <div className="pfMenuItem">
            <div className="pfMenuLeft">
              <BarChart3 size={18} />
              <div>
                <div className="pfMenuTitle">我的使用统计</div>
                <div className="pfMenuDesc">本月提问 47 次，节省约 3.2 小时</div>
              </div>
            </div>
          </div>

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
