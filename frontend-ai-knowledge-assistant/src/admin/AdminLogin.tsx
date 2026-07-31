import { useState, type FormEvent } from 'react'
import { Shield } from 'lucide-react'
import { USE_ADMIN_MOCK } from './api/config'
import { loginAdmin } from './auth'
import type { AdminUser } from './types'
import { useAdminToast } from './useAdminToast'

type Props = {
  onSuccess: (user: AdminUser) => void
  onBackToUser: () => void
}

export default function AdminLogin({ onSuccess, onBackToUser }: Props) {
  const [email, setEmail] = useState('admin@zhishiyun.com')
  const [password, setPassword] = useState('admin123')
  const [submitting, setSubmitting] = useState(false)
  const { showToast, toastNode } = useAdminToast()

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    try {
      const result = await loginAdmin(email, password)
      if (!result.ok) {
        showToast(result.reason === 'forbidden' ? '无管理后台权限' : '邮箱或密码错误')
        return
      }
      onSuccess(result.user)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="adminLogin">
      {toastNode}
      <div className="adminLoginBrand">
        <div className="adminLoginMark">
          <Shield size={28} />
        </div>
        <h1>智识云 · 管理后台</h1>
        <p>知识运营与治理控制台</p>
        <ul>
          <li>创建知识库 / 上传文档 / 权限配置</li>
          <li>用户审核、角色与模型治理</li>
          <li>操作审计与入库任务跟踪</li>
        </ul>
      </div>

      <div className="adminLoginPanel">
        <form className="adminLoginForm" onSubmit={onSubmit}>
          <h2>管理员登录</h2>
          <label>
            企业邮箱
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@company.com"
              required
              autoComplete="username"
            />
          </label>
          <label>
            密码
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="请输入密码"
              required
              autoComplete="current-password"
            />
          </label>
          <button type="submit" className="adminBtnPrimary" disabled={submitting}>
            {submitting ? '登录中…' : '登录'}
          </button>
          <button type="button" className="adminTextLink" onClick={onBackToUser}>
            返回用户端
          </button>
          <p className="adminLoginHint">
            {USE_ADMIN_MOCK ? 'Mock 模式' : '联调模式'}：admin@zhishiyun.com / admin123（系统管理员）
            <br />
            kbadmin@zhishiyun.com / kb123（知识管理员）
          </p>
        </form>
        <footer>安全合规 · 仅限授权管理员</footer>
      </div>
    </div>
  )
}
