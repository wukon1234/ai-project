import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ChevronDown, ChevronRight, Info, ShieldCheck } from 'lucide-react'
import { AdminApiError, USE_ADMIN_MOCK, realAdminApi, type ModelConfigPayload } from './api'
import { useAdminToast } from './useAdminToast'

const CONFIG_KEY = 'zn-admin-model-config'

type ModelConfig = {
  llm: {
    provider: 'openai-compatible' | 'other'
    baseUrl: string
    modelName: string
    apiKey: string
    temperature: number
    maxTokens: number
    timeoutSec: number
  }
  embedding: {
    shareWithLlm: boolean
    baseUrl: string
    apiKey: string
    modelName: string
    dimension: 1536 | 1024
  }
  ocr: {
    enabled: boolean
    endpoint: string
    timeoutSec: number
    concurrency: number
  }
  vision: {
    enabled: boolean
    modelName: string
  }
  rag: {
    topK: number
    scoreThreshold: number
    maxCitations: number
    askPerMinute: number
  }
}

const DEFAULT_CONFIG: ModelConfig = {
  llm: {
    provider: 'openai-compatible',
    baseUrl: 'https://api.openai.com/v1',
    modelName: 'gpt-4o-mini',
    apiKey: 'sk-mock-admin-key-zhishiyun',
    temperature: 0.2,
    maxTokens: 2048,
    timeoutSec: 60,
  },
  embedding: {
    shareWithLlm: true,
    baseUrl: 'https://api.openai.com/v1',
    apiKey: 'sk-mock-admin-key-zhishiyun',
    modelName: 'text-embedding-3-small',
    dimension: 1536,
  },
  ocr: {
    enabled: true,
    endpoint: 'http://localhost:8866/ocr',
    timeoutSec: 30,
    concurrency: 2,
  },
  vision: {
    enabled: false,
    modelName: 'gpt-4o-mini',
  },
  rag: {
    topK: 6,
    scoreThreshold: 0.35,
    maxCitations: 5,
    askPerMinute: 10,
  },
}

function loadLocalConfig(): ModelConfig {
  const raw = localStorage.getItem(CONFIG_KEY)
  if (!raw) {
    localStorage.setItem(CONFIG_KEY, JSON.stringify(DEFAULT_CONFIG))
    return structuredClone(DEFAULT_CONFIG)
  }
  try {
    return { ...structuredClone(DEFAULT_CONFIG), ...(JSON.parse(raw) as ModelConfig) }
  } catch {
    return structuredClone(DEFAULT_CONFIG)
  }
}

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' ? (v as Record<string, unknown>) : {}
}

function num(v: unknown, fallback: number) {
  const n = Number(v)
  return Number.isFinite(n) ? n : fallback
}

function str(v: unknown, fallback = '') {
  return typeof v === 'string' ? v : v == null ? fallback : String(v)
}

/** Backend payload -> FE form (ocr.baseUrl -> endpoint, rag.citeLimit -> maxCitations) */
function fromPayload(raw: ModelConfigPayload): ModelConfig {
  const llm = asRecord(raw.llm)
  const emb = asRecord(raw.embedding)
  const ocr = asRecord(raw.ocr)
  const vision = asRecord(raw.vision)
  const rag = asRecord(raw.rag)
  const dim = num(emb.dimension, 1536)
  return {
    llm: {
      provider:
        str(llm.provider, 'openai-compatible') === 'other' ? 'other' : 'openai-compatible',
      baseUrl: str(llm.baseUrl, DEFAULT_CONFIG.llm.baseUrl),
      modelName: str(llm.modelName, DEFAULT_CONFIG.llm.modelName),
      apiKey: str(llm.apiKey, ''),
      temperature: num(llm.temperature, DEFAULT_CONFIG.llm.temperature),
      maxTokens: num(llm.maxTokens, DEFAULT_CONFIG.llm.maxTokens),
      timeoutSec: num(llm.timeoutSec, DEFAULT_CONFIG.llm.timeoutSec),
    },
    embedding: {
      shareWithLlm: Boolean(emb.shareWithLlm),
      baseUrl: str(emb.baseUrl, DEFAULT_CONFIG.embedding.baseUrl),
      apiKey: str(emb.apiKey, ''),
      modelName: str(emb.modelName, DEFAULT_CONFIG.embedding.modelName),
      dimension: dim === 1024 ? 1024 : 1536,
    },
    ocr: {
      enabled: Boolean(ocr.enabled ?? true),
      endpoint: str(ocr.endpoint || ocr.baseUrl, DEFAULT_CONFIG.ocr.endpoint),
      timeoutSec: num(ocr.timeoutSec, DEFAULT_CONFIG.ocr.timeoutSec),
      concurrency: num(ocr.concurrency, DEFAULT_CONFIG.ocr.concurrency),
    },
    vision: {
      enabled: Boolean(vision.enabled),
      modelName: str(vision.modelName, DEFAULT_CONFIG.vision.modelName),
    },
    rag: {
      topK: num(rag.topK, DEFAULT_CONFIG.rag.topK),
      scoreThreshold: num(rag.scoreThreshold, DEFAULT_CONFIG.rag.scoreThreshold),
      maxCitations: num(rag.citeLimit ?? rag.maxCitations, DEFAULT_CONFIG.rag.maxCitations),
      askPerMinute: num(rag.askPerMinute, DEFAULT_CONFIG.rag.askPerMinute),
    },
  }
}

/** FE form -> backend payload (endpoint -> ocr.baseUrl, maxCitations -> citeLimit) */
function toPayload(cfg: ModelConfig): ModelConfigPayload {
  return {
    llm: { ...cfg.llm },
    embedding: { ...cfg.embedding },
    ocr: {
      enabled: cfg.ocr.enabled,
      baseUrl: cfg.ocr.endpoint,
      endpoint: cfg.ocr.endpoint,
      timeoutSec: cfg.ocr.timeoutSec,
      concurrency: cfg.ocr.concurrency,
    },
    vision: { ...cfg.vision },
    rag: {
      topK: cfg.rag.topK,
      scoreThreshold: cfg.rag.scoreThreshold,
      citeLimit: cfg.rag.maxCitations,
      maxCitations: cfg.rag.maxCitations,
      askPerMinute: cfg.rag.askPerMinute,
    },
  }
}

function maskKey(key: string) {
  if (!key) return ''
  if (key.length <= 6) return '****'
  return `${key.slice(0, 3)}****${key.slice(-4)}`
}

function errMsg(err: unknown, fallback: string) {
  return err instanceof AdminApiError ? err.message : fallback
}

export default function AdminModels() {
  const [config, setConfig] = useState<ModelConfig>(() =>
    USE_ADMIN_MOCK ? loadLocalConfig() : structuredClone(DEFAULT_CONFIG),
  )
  const [savedSnapshot, setSavedSnapshot] = useState<ModelConfig>(() =>
    structuredClone(USE_ADMIN_MOCK ? loadLocalConfig() : DEFAULT_CONFIG),
  )
  const [loading, setLoading] = useState(!USE_ADMIN_MOCK)
  const [editingLlmKey, setEditingLlmKey] = useState(false)
  const [editingEmbedKey, setEditingEmbedKey] = useState(false)
  const [visionOpen, setVisionOpen] = useState(false)
  const [testing, setTesting] = useState(false)
  const [saving, setSaving] = useState(false)
  const [confirmEmbed, setConfirmEmbed] = useState(false)
  const [pendingSave, setPendingSave] = useState<ModelConfig | null>(null)
  const { showToast, toastNode } = useAdminToast()

  const refresh = useCallback(async () => {
    if (USE_ADMIN_MOCK) {
      const local = loadLocalConfig()
      setConfig(local)
      setSavedSnapshot(structuredClone(local))
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const raw = await realAdminApi.getModels()
      const mapped = fromPayload(raw)
      setConfig(mapped)
      setSavedSnapshot(structuredClone(mapped))
    } catch (err) {
      showToast(errMsg(err, '模型配置加载失败'))
    } finally {
      setLoading(false)
    }
  }, [showToast])

  useEffect(() => {
    void refresh()
  }, [refresh])

  function update<K extends keyof ModelConfig>(section: K, patch: Partial<ModelConfig[K]>) {
    setConfig((prev) => ({
      ...prev,
      [section]: { ...prev[section], ...patch },
    }))
  }

  function embeddingChanged(next: ModelConfig) {
    return (
      next.embedding.modelName !== savedSnapshot.embedding.modelName ||
      next.embedding.dimension !== savedSnapshot.embedding.dimension
    )
  }

  async function doSave(next: ModelConfig) {
    const toStore = structuredClone(next)
    if (toStore.embedding.shareWithLlm) {
      toStore.embedding.baseUrl = toStore.llm.baseUrl
      toStore.embedding.apiKey = toStore.llm.apiKey
    }

    if (USE_ADMIN_MOCK) {
      localStorage.setItem(CONFIG_KEY, JSON.stringify(toStore))
      setConfig(toStore)
      setSavedSnapshot(structuredClone(toStore))
      setEditingLlmKey(false)
      setEditingEmbedKey(false)
      setConfirmEmbed(false)
      setPendingSave(null)
      showToast('模型配置已保存')
      return
    }

    setSaving(true)
    try {
      const saved = await realAdminApi.saveModels(toPayload(toStore))
      const mapped = fromPayload(saved)
      setConfig(mapped)
      setSavedSnapshot(structuredClone(mapped))
      setEditingLlmKey(false)
      setEditingEmbedKey(false)
      setConfirmEmbed(false)
      setPendingSave(null)
      showToast('模型配置已保存')
    } catch (err) {
      showToast(errMsg(err, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    const next = structuredClone(config)
    if (embeddingChanged(next)) {
      setPendingSave(next)
      setConfirmEmbed(true)
      return
    }
    void doSave(next)
  }

  async function testConnection() {
    setTesting(true)
    if (USE_ADMIN_MOCK) {
      await new Promise((r) => setTimeout(r, 900))
      setTesting(false)
      const ok = Boolean(config.llm.baseUrl && config.llm.modelName && config.llm.apiKey)
      showToast(ok ? '连接测试成功（Mock）' : '连接测试失败：请检查 LLM 配置')
      return
    }
    try {
      const res = await realAdminApi.testModel('llm')
      showToast(res.ok ? res.message || '连接测试成功' : res.message || '连接测试失败')
    } catch (err) {
      showToast(errMsg(err, '连接测试失败'))
    } finally {
      setTesting(false)
    }
  }

  if (loading) {
    return (
      <div className="adminPage">
        {toastNode}
        <div className="adminEmpty">
          <h2>加载中…</h2>
          <p className="adminMuted">正在获取模型配置</p>
        </div>
      </div>
    )
  }

  return (
    <div className="adminPage">
      {toastNode}
      <div className="adminPageHeader">
        <div>
          <h1>模型设置</h1>
          <p className="adminMuted">
            Embedding / LLM / OCR / Vision / RAG
            {USE_ADMIN_MOCK ? '（Mock 持久化，不改服务端配置）' : ''}
          </p>
        </div>
        <span className="adminSysBadge">
          <ShieldCheck size={14} />
          仅系统管理员可修改
        </span>
      </div>

      <div className="adminNotice">
        <Info size={16} />
        <span>
          API Key 仅掩码展示；请勿在控制台打印完整密钥。变更 Embedding 模型/维度需全量重建向量。
        </span>
      </div>

      <form className="adminModelForm" onSubmit={onSubmit}>
        <section className="adminPanel">
          <div className="adminPanelHead">
            <h2>对话模型（LLM）</h2>
            <button type="button" className="adminGhostBtn" disabled={testing} onClick={() => void testConnection()}>
              {testing ? '测试中…' : '测试连接'}
            </button>
          </div>
          <div className="adminFormGrid">
            <label>
              Provider
              <select
                value={config.llm.provider}
                onChange={(e) =>
                  update('llm', { provider: e.target.value as ModelConfig['llm']['provider'] })
                }
              >
                <option value="openai-compatible">OpenAI 兼容</option>
                <option value="other">其他</option>
              </select>
            </label>
            <label>
              Base URL
              <input
                value={config.llm.baseUrl}
                onChange={(e) => update('llm', { baseUrl: e.target.value })}
              />
            </label>
            <label>
              Model Name
              <input
                value={config.llm.modelName}
                onChange={(e) => update('llm', { modelName: e.target.value })}
                placeholder="gpt-4o-mini"
              />
            </label>
            <label>
              API Key
              {editingLlmKey ? (
                <input
                  type="password"
                  value={config.llm.apiKey}
                  onChange={(e) => update('llm', { apiKey: e.target.value })}
                  autoComplete="new-password"
                />
              ) : (
                <div className="adminMaskedKey">
                  <code>{maskKey(config.llm.apiKey)}</code>
                  <button type="button" className="adminTextLink" onClick={() => setEditingLlmKey(true)}>
                    重新输入
                  </button>
                </div>
              )}
            </label>
            <label>
              Temperature
              <input
                type="number"
                min={0}
                max={2}
                step={0.1}
                value={config.llm.temperature}
                onChange={(e) => update('llm', { temperature: Number(e.target.value) })}
              />
            </label>
            <label>
              Max Tokens
              <input
                type="number"
                min={256}
                value={config.llm.maxTokens}
                onChange={(e) => update('llm', { maxTokens: Number(e.target.value) })}
              />
            </label>
            <label>
              超时（秒）
              <input
                type="number"
                min={5}
                value={config.llm.timeoutSec}
                onChange={(e) => update('llm', { timeoutSec: Number(e.target.value) })}
              />
            </label>
          </div>
        </section>

        <section className="adminPanel">
          <div className="adminPanelHead">
            <h2>Embedding</h2>
          </div>
          <label className="adminSwitch" style={{ marginBottom: 12 }}>
            <input
              type="checkbox"
              checked={config.embedding.shareWithLlm}
              onChange={(e) => update('embedding', { shareWithLlm: e.target.checked })}
            />
            与 LLM 共用 Base URL / API Key
          </label>
          <div className="adminFormGrid">
            <label>
              Model Name
              <select
                value={config.embedding.modelName}
                onChange={(e) => {
                  const modelName = e.target.value
                  update('embedding', {
                    modelName,
                    dimension: modelName.includes('bge') ? 1024 : 1536,
                  })
                }}
              >
                <option value="text-embedding-3-small">text-embedding-3-small</option>
                <option value="bge-m3">bge-m3</option>
              </select>
            </label>
            <label>
              Dimension
              <input value={config.embedding.dimension} disabled />
              <span className="adminFieldHint">变更维度需全量重建向量</span>
            </label>
            {!config.embedding.shareWithLlm && (
              <>
                <label>
                  Base URL
                  <input
                    value={config.embedding.baseUrl}
                    onChange={(e) => update('embedding', { baseUrl: e.target.value })}
                  />
                </label>
                <label>
                  API Key
                  {editingEmbedKey ? (
                    <input
                      type="password"
                      value={config.embedding.apiKey}
                      onChange={(e) => update('embedding', { apiKey: e.target.value })}
                      autoComplete="new-password"
                    />
                  ) : (
                    <div className="adminMaskedKey">
                      <code>{maskKey(config.embedding.apiKey)}</code>
                      <button
                        type="button"
                        className="adminTextLink"
                        onClick={() => setEditingEmbedKey(true)}
                      >
                        重新输入
                      </button>
                    </div>
                  )}
                </label>
              </>
            )}
          </div>
        </section>

        <section className="adminPanel">
          <div className="adminPanelHead">
            <h2>OCR</h2>
            <label className="adminSwitch">
              <input
                type="checkbox"
                checked={config.ocr.enabled}
                onChange={(e) => update('ocr', { enabled: e.target.checked })}
              />
              启用
            </label>
          </div>
          <div className="adminFormGrid">
            <label>
              PaddleOCR 服务地址
              <input
                value={config.ocr.endpoint}
                disabled={!config.ocr.enabled}
                onChange={(e) => update('ocr', { endpoint: e.target.value })}
              />
            </label>
            <label>
              超时（秒）
              <input
                type="number"
                min={5}
                disabled={!config.ocr.enabled}
                value={config.ocr.timeoutSec}
                onChange={(e) => update('ocr', { timeoutSec: Number(e.target.value) })}
              />
            </label>
            <label>
              并发
              <input
                type="number"
                min={1}
                max={8}
                disabled={!config.ocr.enabled}
                value={config.ocr.concurrency}
                onChange={(e) => update('ocr', { concurrency: Number(e.target.value) })}
              />
            </label>
          </div>
        </section>

        <section className="adminPanel">
          <button
            type="button"
            className="adminCollapseHead"
            onClick={() => setVisionOpen((v) => !v)}
          >
            <h2>Vision（可选）</h2>
            {visionOpen ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
          </button>
          {visionOpen && (
            <>
              <label className="adminSwitch" style={{ marginBottom: 12 }}>
                <input
                  type="checkbox"
                  checked={config.vision.enabled}
                  onChange={(e) => update('vision', { enabled: e.target.checked })}
                />
                启用（扫描件 / 图片描述）
              </label>
              <div className="adminFormGrid">
                <label>
                  Model Name
                  <input
                    value={config.vision.modelName}
                    disabled={!config.vision.enabled}
                    onChange={(e) => update('vision', { modelName: e.target.value })}
                  />
                </label>
              </div>
            </>
          )}
        </section>

        <section className="adminPanel">
          <div className="adminPanelHead">
            <h2>RAG 参数</h2>
          </div>
          <div className="adminFormGrid">
            <label>
              topK
              <input
                type="number"
                min={1}
                max={20}
                value={config.rag.topK}
                onChange={(e) => update('rag', { topK: Number(e.target.value) })}
              />
            </label>
            <label>
              scoreThreshold
              <input
                type="number"
                min={0}
                max={1}
                step={0.01}
                value={config.rag.scoreThreshold}
                onChange={(e) => update('rag', { scoreThreshold: Number(e.target.value) })}
              />
            </label>
            <label>
              单问答引用上限
              <input
                type="number"
                min={1}
                max={20}
                value={config.rag.maxCitations}
                onChange={(e) => update('rag', { maxCitations: Number(e.target.value) })}
              />
            </label>
            <label>
              每用户每分钟问答次数
              <input
                type="number"
                min={1}
                max={100}
                value={config.rag.askPerMinute}
                onChange={(e) => update('rag', { askPerMinute: Number(e.target.value) })}
              />
            </label>
          </div>
        </section>

        <div className="adminHeaderActions">
          <button type="submit" className="adminBtnPrimary" disabled={saving}>
            {saving ? '保存中…' : '保存配置'}
          </button>
        </div>
      </form>

      {confirmEmbed && pendingSave && (
        <div className="adminModalMask" onClick={() => setConfirmEmbed(false)} role="presentation">
          <div className="adminModal" onClick={(e) => e.stopPropagation()} role="dialog">
            <h2>需全量重建索引</h2>
            <p>
              Embedding 模型或维度已变更，保存后需对全部文档重建向量索引。确认继续保存？
            </p>
            <div className="adminModalActions">
              <button
                type="button"
                className="adminGhostBtn"
                onClick={() => {
                  setConfirmEmbed(false)
                  setPendingSave(null)
                }}
              >
                取消
              </button>
              <button
                type="button"
                className="adminBtnDanger"
                disabled={saving}
                onClick={() => void doSave(pendingSave)}
              >
                确认保存
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
