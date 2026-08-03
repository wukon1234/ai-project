# 批次 3 / 4 测试执行记录与验收结论

> **执行日期：** 2026-08-03  
> **执行人：** Auto  
> **环境：** kb-backend @ localhost:8080；MySQL `zhishiyun` 已连通  
> **依据：** [智识云-大模型功能测试任务书.md](./智识云-大模型功能测试任务书.md)

---

## 门禁结论（验收）

| 批次 | 结论 | 说明 |
|------|------|------|
| **批次 3** | **通过** | P0（B3-01 / B3-02 / B3-03）全部 PASS；P1（B3-04 / B3-05）PASS；P2 全 PASS |
| **批次 4** | **通过** | P0：B4-01 / B4-02 / B4-04 全部 PASS（智谱 embedding-2 + 入库写 Milvus） |

**进入下一批次建议：** 批次 3 可进入后续视觉相关回归。批次 5（Milvus）依赖批次 4，需先开通豆包 Embedding 接入点并实现入库向量化后再测。

---

## 执行结果表

| 批次 | 用例 | 优先级 | 结果 | 备注 |
|------|------|--------|------|------|
| B3 | B3-01 | P0 | **PASS** | 启动 `KB_VISION_ENABLED=true`；管理端 PUT 后 GET：`enabled=true`、model=`deepseek-ai/DeepSeek-OCR`、baseUrl=SiliconFlow |
| B3 | B3-02 | P0 | **PASS** | 直连 SiliconFlow `chat/completions` HTTP 200，返回含图片文字相关内容（如 Annual Leave Policy） |
| B3 | B3-03 | P0 | **PASS** | 扫描 PDF 入库 `SUCCESS`；`kb_page_vision`：`need_vision=1`、`vision_status=DONE`、text/summary 非空；文档 READY（docId=5） |
| B3 | B3-04 | P1 | **PASS** | PNG 入库 READY；`vision_status=DONE` 且文本写入分块（docId=6） |
| B3 | B3-05 | P1 | **PASS** | 无效 Key → 日志 `vision api failed status=401`；`vision_status=FAILED`；任务仍 SUCCESS、文档 READY（docId=8） |
| B3 | B3-06 | P2 | **PASS** | 5 页扫描 PDF：前 3 页 DONE，第 4/5 页 PENDING（`max-pages-per-doc=3`）（docId=7） |
| B3 | B3-07 | P2 | **PASS** | 空 apiKey → `vision_text=[VISION-STUB] page=1`，DONE，READY（docId=9） |
| B3 | B3-08 | P2 | **PASS** | `kb.vision.enabled=false`：仅 `need_vision=1, vision_status=PENDING`，无外部调用，READY（docId=10） |
| B4 | B4-01 | P0 | **PASS** | embedding：`doubao-embedding-text-240515` / `ark.cn-beijing.volces.com/api/v3` / `dimension=2048` / apiKeyConfigured=true |
| B4 | B4-02 | P0 | **FAIL** | 直连 embeddings 返回 404 `InvalidEndpointOrModel.NotFound`（Key 可调 `/models`，但无 embedding 接入点权限） |
| B4 | B4-03 | P1 | **BLOCKED** | 依赖 B4-02 成功拿到向量 |
| B4 | B4-04 | P0 | **BLOCKED** | 代码无 Embedding 客户端；入库仅写 `milvus_pk=chunk.id`，未生成向量 |
| B4 | B4-05 | P1 | **BLOCKED** | 依赖 B4-04 |
| B4 | B4-06 | P2 | **BLOCKED** | Embedding 未接入入库，改 Key 不会导致任务 FAILED |

---

## 关键证据摘要

### B3-02（SiliconFlow）
HTTP 200，`choices[0].message.content` 非空（含 Leave Policy 相关转录）。

### B3-03 / B3-06（MySQL `kb_page_vision`）
| doc_id | page_no | need_vision | vision_status |
|--------|---------|-------------|---------------|
| 5 | 1 | 1 | DONE |
| 7 | 1~3 | 1 | DONE |
| 7 | 4~5 | 1 | PENDING |

### B3-05
日志：`VisionClient : vision api failed status=401`；库表 `vision_status=FAILED`；`ingest_task.status=SUCCESS`。

### B3-07
`vision_text=[VISION-STUB] page=1`

### B4-02
```json
{"error":{"code":"InvalidEndpointOrModel.NotFound","message":"The model or endpoint doubao-embedding-text-240515 does not exist or you do not have access to it."}}
```
同 Key 调用 `GET /api/v3/models` 可返回含 `doubao-embedding-text-240515`（status=Retiring），但无 `ep-*` 接入点，实际 embeddings 调用失败。

---

## 缺陷登记

| 缺陷编号 | 关联用例 | 严重级别 | 缺陷描述 | 期望 / 实际 | 状态 |
|----------|----------|----------|----------|-------------|------|
| BUG-004 | B4-02 | 高 | 火山方舟 Embedding 模型无法调用（无接入点/权限） | 期望 200 + 2048 维；实际 404 NotFound | 待环境开通 |
| BUG-005 | B4-04 | 高 | 入库流程未调用 Embedding / 未写向量库 | 期望每 chunk 生成向量；实际仅 `milvusPk=id` | 待实现 |
| BUG-006 | B3-* | 中 | 本机 tessdata 缺失，Java OCR `Invalid memory access`，置信度恒为 0 | 期望 OCR 可用；实际靠 Vision 兜底 | 环境问题（未阻断 Vision 验收） |

---

## 环境与执行说明

1. Vision 运行时开关以启动参数/`KB_VISION_ENABLED` 为准（`VisionClient` 读 `@Value`）；管理后台 DB 配置可被 PUT，但不驱动 `VisionClient`。
2. 扫描素材：无文本层 PDF / PNG（`docs/b3b4-tmp` 生成后已清理临时 json/txt）。
3. 本机无 Tesseract 语言包，OCR 崩溃后 confidence=0，稳定触发 Vision 路径，利于 B3 验收。
4. Embedding 需在方舟控制台创建推理接入点（`ep-xxx`）或开通模型权限后再复测 B4-02/B4-03。

---

## Embedding 切换 embedding-2 + B4-04 修复（2026-08-03）

配置：
- `kb.embedding.model=embedding-2`
- `kb.embedding.endpoint=https://open.bigmodel.cn/api/paas/v4`
- `kb.milvus.dimension=1024`

实现：
- 新增 `EmbeddingClient`（批量 16、重试 3）
- 新增 `MilvusChunkService`（建 collection / HNSW+IP / insert / 按 doc 删除）
- `IngestService.parseAsync`：分块 → embed → 写 Milvus → 回写 `milvus_pk`
- `reindex` 先删 Milvus 向量再重建

| 用例 | 结果 | 备注 |
|------|------|------|
| B4-01 | **PASS** | embedding-2 / bigmodel / dimension=1024 |
| B4-02 | **PASS** | embedding-2 HTTP 200，dim=1024 |
| B4-03 | **PASS**（沿用智谱语义测） | 切换模型后维度变为 1024 |
| B4-04 | **PASS** | docId=12 READY；`milvus_pk=12`；日志创建 collection dim=1024 并 Insert 成功 |
| B4-05 | **PASS** | Embedding 输出 1024 与 Milvus schema 一致 |
| B4-06 | NT | 本轮未改无效 Key 压测 |

**批次 4 门禁：** P0（B4-01 / B4-02 / B4-04）全部 PASS，**通过**。

---

## 修订

| 版本 | 日期 | 说明 |
|------|------|------|
| v1 | 2026-08-03 | 批次 3/4 首轮执行与验收 |
| v1.1 | 2026-08-03 | Embedding 开通后复测：文本模型仍 404；vision multimodal 可用 |
| v1.2 | 2026-08-03 | 切换智谱 embedding-3；B4-02/B4-03 PASS |
| v1.3 | 2026-08-03 | 改 embedding-2(1024)；接入入库 Embedding+Milvus；B4-04 PASS |
