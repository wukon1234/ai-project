import { useState, type FormEvent } from 'react'
import {
  Eye,
  EyeOff,
  FileSpreadsheet,
  FileText,
  FolderKanban,
  Globe,
  Image as ImageIcon,
  Lock,
  Mail,
  Presentation,
  Smartphone,
  UserRound,
} from 'lucide-react'

type AuthMode = 'login' | 'register'
type LoginTab = 'email' | 'phone'

type AuthPageProps = {
  onSuccess: () => void
}

const t = {
  zh: {
    product: '智识云 · AI 知识库',
    productSub: '企业级 AI 知识库 Agent 平台',
    slogan: '让企业知识，一问即达',
    sloganSub: '构建企业专属知识大脑，赋能智能问答与业务自动化',
    login: '登录',
    register: '注册',
    sso: '企业 SSO 登录',
    ssoHint: 'OAuth 2.0 · Azure AD',
    orAccount: '或使用账号登录',
    emailTab: '邮箱登录',
    phoneTab: '手机号登录',
    emailPh: '请输入邮箱 / 手机号',
    emailOnlyPh: '请输入企业邮箱',
    phonePh: '请输入手机号',
    passwordPh: '请输入密码',
    setPasswordPh: '设置登录密码',
    namePh: '请输入姓名',
    remember: '记住我',
    forgot: '忘记密码',
    submitLogin: '登录',
    submitRegister: '注册并登录',
    registerHint: '使用企业邮箱注册，管理员审核通过后即可登录',
    copyright: '© 2026 企业知识大脑 · 安全合规 · TLS 加密',
    lang: '简体中文 / English',
    errEmail: '请输入邮箱或手机号',
    errPhone: '请输入手机号',
    errPassword: '请输入密码',
    errRegister: '请完整填写注册信息',
    doc: '文档',
    image: '图片',
    sheet: '表格',
    ppt: '演示',
    enterprise: '企业数据'
  },
  en: {
    product: 'Zhishi Cloud · AI Knowledge',
    productSub: 'Enterprise AI Knowledge Agent Platform',
    slogan: 'Enterprise knowledge, one question away',
    sloganSub: 'Build your private knowledge brain for Q&A and automation',
    login: 'Sign in',
    register: 'Sign up',
    sso: 'Enterprise SSO Login',
    ssoHint: 'OAuth 2.0 · Azure AD',
    orAccount: 'Or continue with account',
    emailTab: 'Email',
    phoneTab: 'Phone',
    emailPh: 'Email or phone number',
    emailOnlyPh: 'Work email',
    phonePh: 'Phone number',
    passwordPh: 'Password',
    setPasswordPh: 'Create a password',
    namePh: 'Full name',
    remember: 'Remember me',
    forgot: 'Forgot password',
    submitLogin: 'Sign in',
    submitRegister: 'Create account',
    registerHint: 'Register with work email. Access after admin approval.',
    copyright: '© 2026 Enterprise Knowledge Brain · Secure · TLS',
    lang: 'English / 简体中文',
    errEmail: 'Please enter email or phone',
    errPhone: 'Please enter phone number',
    errPassword: 'Please enter password',
    errRegister: 'Please complete all fields',
    doc: 'Docs',
    image: 'Images',
    sheet: 'Sheets',
    ppt: 'Slides',
    enterprise: 'Enterprise'
  }
} as const

function AuthPage({ onSuccess }: AuthPageProps) {
  const [mode, setMode] = useState<AuthMode>('login')
  const [loginTab, setLoginTab] = useState<LoginTab>('email')
  const [showPassword, setShowPassword] = useState(false)
  const [rememberMe, setRememberMe] = useState(true)
  const [lang, setLang] = useState<'zh' | 'en'>('zh')
  const [account, setAccount] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const copy = t[lang]

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (mode === 'login') {
      if (!account.trim()) {
        setError(loginTab === 'phone' ? copy.errPhone : copy.errEmail)
        return
      }
      if (!password.trim()) {
        setError(copy.errPassword)
        return
      }
    } else if (!name.trim() || !account.trim() || !password.trim()) {
      setError(copy.errRegister)
      return
    }
    setError(null)
    onSuccess()
  }

  return (
    <div className="authPage">
      <section className="authBrand" aria-label="品牌展示">
        <div className="authBrandDecor" aria-hidden="true" />
        <div className="authBrandGlow" aria-hidden="true" />
        <div className="authParticles" aria-hidden="true">
          {Array.from({ length: 18 }).map((_, i) => (
            <span key={i} className={`authParticle authParticle${i + 1}`} />
          ))}
        </div>

        <div className="authBrandContent">
          <div className="authOrbit" aria-hidden="true">
            <svg className="authNeural" viewBox="0 0 360 360" fill="none">
              <defs>
                <linearGradient id="authBeam" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="#93C5FD" stopOpacity="0.1" />
                  <stop offset="50%" stopColor="#60A5FA" stopOpacity="0.9" />
                  <stop offset="100%" stopColor="#C4B5FD" stopOpacity="0.15" />
                </linearGradient>
                <radialGradient id="authNodeGlow" cx="50%" cy="50%" r="50%">
                  <stop offset="0%" stopColor="#DBEAFE" stopOpacity="1" />
                  <stop offset="100%" stopColor="#2563EB" stopOpacity="0.2" />
                </radialGradient>
              </defs>

              <g className="authNeuralLines" stroke="url(#authBeam)" strokeWidth="1.2">
                <path d="M180 180 L72 78" />
                <path d="M180 180 L288 70" />
                <path d="M180 180 L318 180" />
                <path d="M180 180 L286 292" />
                <path d="M180 180 L74 286" />
                <path d="M180 180 L42 176" />
                <path d="M72 78 L120 120" />
                <path d="M288 70 L240 118" />
                <path d="M318 180 L250 180" />
                <path d="M286 292 L236 246" />
                <path d="M74 286 L124 244" />
                <path d="M42 176 L110 180" />
                <path d="M72 78 L288 70" opacity="0.35" />
                <path d="M288 70 L318 180" opacity="0.35" />
                <path d="M318 180 L286 292" opacity="0.3" />
                <path d="M286 292 L74 286" opacity="0.3" />
                <path d="M74 286 L42 176" opacity="0.3" />
                <path d="M42 176 L72 78" opacity="0.35" />
              </g>

              <g className="authNeuralNodes" fill="url(#authNodeGlow)">
                <circle className="authNodePulse" cx="72" cy="78" r="4.5" />
                <circle className="authNodePulse authNodeDelay2" cx="288" cy="70" r="4" />
                <circle className="authNodePulse authNodeDelay3" cx="318" cy="180" r="5" />
                <circle className="authNodePulse authNodeDelay1" cx="286" cy="292" r="4" />
                <circle className="authNodePulse authNodeDelay2" cx="74" cy="286" r="4.5" />
                <circle className="authNodePulse authNodeDelay3" cx="42" cy="176" r="4" />
                <circle className="authNodePulse" cx="120" cy="120" r="3" />
                <circle className="authNodePulse authNodeDelay1" cx="240" cy="118" r="3" />
                <circle className="authNodePulse authNodeDelay2" cx="250" cy="180" r="3.5" />
                <circle className="authNodePulse authNodeDelay3" cx="236" cy="246" r="3" />
                <circle className="authNodePulse" cx="124" cy="244" r="3" />
                <circle className="authNodePulse authNodeDelay1" cx="110" cy="180" r="3.5" />
              </g>

              <g className="authDataPackets" stroke="#93C5FD" strokeWidth="2" strokeLinecap="round">
                <circle className="authPacket authPacket1" r="2.5" fill="#BFDBFE">
                  <animateMotion dur="3.2s" repeatCount="indefinite" path="M72 78 L180 180" />
                </circle>
                <circle className="authPacket authPacket2" r="2.5" fill="#C4B5FD">
                  <animateMotion dur="2.8s" begin="0.6s" repeatCount="indefinite" path="M318 180 L180 180" />
                </circle>
                <circle className="authPacket authPacket3" r="2.5" fill="#93C5FD">
                  <animateMotion dur="3.6s" begin="1.1s" repeatCount="indefinite" path="M74 286 L180 180" />
                </circle>
                <circle className="authPacket authPacket4" r="2.5" fill="#A5B4FC">
                  <animateMotion dur="3s" begin="1.8s" repeatCount="indefinite" path="M288 70 L180 180" />
                </circle>
              </g>
            </svg>

            <div className="authOrbitRing" />
            <div className="authOrbitRing authOrbitRing2" />
            <div className="authOrbitRing authOrbitRing3" />
            <div className="authScan" />

            <div className="authCore">
              <div className="authCoreInner">
                <span>AI</span>
              </div>
              <div className="authCoreHex" />
            </div>

            <div className="authTrack authTrack1">
              <div className="authFloat">
                <span className="authFloatIcon authFloatIconDoc">
                  <FileText size={16} />
                </span>
                <span>{copy.doc}</span>
              </div>
            </div>
            <div className="authTrack authTrack2">
              <div className="authFloat">
                <span className="authFloatIcon authFloatIconImg">
                  <ImageIcon size={16} />
                </span>
                <span>{copy.image}</span>
              </div>
            </div>
            <div className="authTrack authTrack3">
              <div className="authFloat">
                <span className="authFloatIcon authFloatIconSheet">
                  <FileSpreadsheet size={16} />
                </span>
                <span>{copy.sheet}</span>
              </div>
            </div>
            <div className="authTrack authTrack4">
              <div className="authFloat">
                <span className="authFloatIcon authFloatIconPpt">
                  <Presentation size={16} />
                </span>
                <span>{copy.ppt}</span>
              </div>
            </div>
            <div className="authTrack authTrack5">
              <div className="authFloat">
                <span className="authFloatIcon authFloatIconEnt">
                  <FolderKanban size={16} />
                </span>
                <span>{copy.enterprise}</span>
              </div>
            </div>
          </div>

          <div className="authSloganBlock">
            <div className="authBadge">Knowledge Brain</div>
            <h1>{copy.slogan}</h1>
            <p>{copy.sloganSub}</p>
          </div>
        </div>
      </section>

      <section className="authPanel">
        <div className="authPanelInner">
          <div className="authLogoRow">
            <div className="authLogoMark" aria-hidden="true">
              <svg viewBox="0 0 32 32" width="22" height="22" fill="none">
                <path
                  d="M16 3L28 10V22L16 29L4 22V10L16 3Z"
                  stroke="white"
                  strokeWidth="2"
                  strokeLinejoin="round"
                />
                <path d="M16 11L22 14.5V21.5L16 25L10 21.5V14.5L16 11Z" fill="white" />
              </svg>
            </div>
            <div>
              <div className="authProduct">{copy.product}</div>
              <div className="authProductSub">{copy.productSub}</div>
            </div>
          </div>

          <div className="authModeSwitch" role="tablist" aria-label="登录或注册">
            <button
              type="button"
              className={mode === 'login' ? 'authModeActive' : ''}
              onClick={() => {
                setMode('login')
                setError(null)
              }}
            >
              {copy.login}
            </button>
            <button
              type="button"
              className={mode === 'register' ? 'authModeActive' : ''}
              onClick={() => {
                setMode('register')
                setError(null)
              }}
            >
              {copy.register}
            </button>
          </div>

          {mode === 'login' ? (
            <>
              <button type="button" className="authSsoBtn" onClick={onSuccess}>
                <span className="authMsLogo" aria-hidden="true">
                  <i />
                  <i />
                  <i />
                  <i />
                </span>
                <span className="authSsoText">
                  <strong>{copy.sso}</strong>
                  <em>{copy.ssoHint}</em>
                </span>
              </button>

              <div className="authDivider">
                <span>{copy.orAccount}</span>
              </div>

              <div className="authTabs" role="tablist">
                <button
                  type="button"
                  role="tab"
                  aria-selected={loginTab === 'email'}
                  className={loginTab === 'email' ? 'authTabActive' : ''}
                  onClick={() => setLoginTab('email')}
                >
                  {copy.emailTab}
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={loginTab === 'phone'}
                  className={loginTab === 'phone' ? 'authTabActive' : ''}
                  onClick={() => setLoginTab('phone')}
                >
                  {copy.phoneTab}
                </button>
              </div>
            </>
          ) : (
            <div className="authRegisterHint">{copy.registerHint}</div>
          )}

          <form className="authForm" onSubmit={onSubmit}>
            {mode === 'register' ? (
              <label className="authField">
                <span className="authFieldIcon">
                  <UserRound size={16} />
                </span>
                <input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder={copy.namePh}
                  aria-label={copy.namePh}
                />
              </label>
            ) : null}

            <label className="authField">
              <span className="authFieldIcon">
                {loginTab === 'phone' && mode === 'login' ? (
                  <Smartphone size={16} />
                ) : (
                  <Mail size={16} />
                )}
              </span>
              <input
                value={account}
                onChange={(e) => setAccount(e.target.value)}
                placeholder={
                  mode === 'register'
                    ? copy.emailOnlyPh
                    : loginTab === 'phone'
                      ? copy.phonePh
                      : copy.emailPh
                }
                aria-label="账号"
                autoComplete="username"
              />
            </label>

            <label className="authField">
              <span className="authFieldIcon">
                <Lock size={16} />
              </span>
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={mode === 'register' ? copy.setPasswordPh : copy.passwordPh}
                aria-label="密码"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              />
              <button
                type="button"
                className="authEyeBtn"
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? '隐藏密码' : '显示密码'}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </label>

            {mode === 'login' ? (
              <div className="authOptions">
                <label className="authRemember">
                  <input
                    type="checkbox"
                    checked={rememberMe}
                    onChange={(e) => setRememberMe(e.target.checked)}
                  />
                  {copy.remember}
                </label>
                <button type="button" className="authLinkBtn">
                  {copy.forgot}
                </button>
              </div>
            ) : null}

            {error ? <div className="authError">{error}</div> : null}

            <button type="submit" className="authSubmit">
              {mode === 'login' ? copy.submitLogin : copy.submitRegister}
            </button>
          </form>

          <div className="authFooter">
            <span>{copy.copyright}</span>
          </div>
        </div>

        <button
          type="button"
          className="authLang"
          onClick={() => setLang((v) => (v === 'zh' ? 'en' : 'zh'))}
        >
          <Globe size={14} />
          {copy.lang}
        </button>
      </section>
    </div>
  )
}

export default AuthPage
