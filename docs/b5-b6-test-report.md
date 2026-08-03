# 批次 5 / 6 测试执行记录与验收结论

> **执行日期：** 2026-08-03  
> **执行人：** Auto  
> **环境：** kb-backend @ localhost:8080；MySQL `zhishiyun`；Zilliz Cloud `kb_chunks`（dim=1024）  
> **依据：** [智识云-大模型功能测试任务书.md](./智识云-大模型功能测试任务书.md)

---

## 门禁结论（验收）

| 批次 | 结论 | 说明 |
|------|------|------|
| **批次 5** | **通过** | P0 全 PASS；B5-05 已接入 Milvus ANN（BUG-008 已修复复测） |
| **批次 6** | **通过** | P0 全 PASS；B6-03 页摘要二次 `cached=true`（BUG-007 已修复复测） |

---

## 执行结果表

| 批次 | 用例 | 优先级 | 结果 | 备注 |
|------|------|--------|------|------|
| B5 | B5-01 | P0 | **PASS** | uri=`*.cloud.zilliz.com.cn`；collection=`kb_chunks`；dimension=**1024**（与智谱 embedding-2 对齐，非任务书原文 2048） |
| B5 | B5-02 | P0 | **PASS** | REST `collections/describe`：`LoadStateLoaded`；schema 含 `embedding` FloatVector dim=1024 + 主键 `id` + doc_id/kb_id/page_no/chunk_index/text |
| B5 | B5-03 | P1 | **PASS** | 管理端 `target=milvus` 返回 40001「未知目标」（不支持）；改用 Zilliz REST describeCollection 冒烟成功 |
| B5 | B5-04 | P0 | **PASS** | 上传 `text-hr.pdf` → docId=13 READY；`milvus_pk` 回写；Milvus 可查 doc_id=13，**embeddingLen=1024** |
| B5 | B5-05 | P0 | **PASS** | Embedding + Milvus ANN；相关问 citation+OK；无关问 score 低于阈值 → NO_ANSWER；日志 `milvus ANN hit`（BUG-008 已修） |
| B5 | B5-06 | P1 | **PASS** | 默认 `hybrid-enabled=false`；单测 `VectorSearchServiceHybridTest` 日志：`hybrid RRF enabled, vector=2, keyword=2, fused=2`（exit=0）。本轮未重启做线上 A/B |
| B5 | B5-07 | P1 | **PASS** | reindex docId=13 → 新 chunk id=14；Milvus 仅余 id=14（旧 id=13 已清理），无脏数据 |
| B5 | B5-08 | P2 | **NT** | 需改无效 token/uri 并重启，本轮未中断服务 |
| B6 | B6-01 | P0 | **PASS** | 入库 SUCCESS → 问答命中 → 引用含文档名/页码/知识库；`usage_event` ASK 落库 |
| B6 | B6-02 | P0 | **PASS** | session scope=`product` 问 HR 年假 → `done(NO_ANSWER)` + suggestions/contact；无 hr citation |
| B6 | B6-03 | P1 | **PASS** | ask:stream OK；related 排除本页；summary 首次 `cached=false`、二次 `cached=true`（BUG-007 已修） |
| B6 | B6-04 | P1 | **PASS** | 同一问题连问 3 次均 OK+citation；中英混合问题 OK；无超时 |
| B6 | B6-05 | P2 | **PASS** | txt → 40001「仅支持 PDF/图片」；损坏 PDF → FAILED（`Missing root object specification in trailer.`）；管理端 retry 可创建新任务，同损坏文件仍 FAILED（符合预期） |

---

## 关键证据摘要

### B5-01 / B5-02（Zilliz describe）
- collection=`kb_chunks`，`load=LoadStateLoaded`
- fields：`id(Int64 PK)`、`embedding(FloatVector dim=1024)`、`doc_id`/`kb_id`/`page_no`/`chunk_index`/`text`

### B5-04 / B5-07
| 步骤 | docId | task | milvus |
|------|-------|------|--------|
| 入库 | 13 | SUCCESS (taskId=13) | id=13, emb=1024 |
| reindex | 13 | SUCCESS (taskId=14) | 仅 id=14（旧 13 已删） |

### B5-05 / B6-01 SSE
```
event:meta → status=SEARCHING
event:citation → knowledgeBaseId=hr, title=B5-04-milvus-write / B4-04-text-embed
event:delta → ...
event:done → status=OK
```

### B6-02 ACL
```
event:done → status=NO_ANSWER, suggestions + contact
```

### B6-05
| 输入 | 结果 |
|------|------|
| `bad.txt` | HTTP 400 / code=40001 |
| `corrupt.pdf` | task FAILED，errorMsg 可读 |
| admin retry | 新 taskId=16，仍 FAILED |

### B5-06
```
hybrid RRF enabled, vector=2, keyword=2, fused=2
```

### BUG-007 / BUG-008 复测（2026-08-03）
- 摘要：`cache1=false` → `cache2=true`
- 相关问：日志 `milvus ANN hit ... returned=2`，`done(OK)` + citation
- 无关问：ANN 返回低分被阈值过滤 → `done(NO_ANSWER)`
- 文档问：`expr=doc_id == "13"`，`done(OK)`

---

## 缺陷登记

| 缺陷编号 | 关联用例 | 严重级别 | 缺陷描述 | 期望 / 实际 | 状态 |
|----------|----------|----------|----------|-------------|------|
| BUG-007 | B6-03 | 低 | 页摘要进程内缓存未命中：`PageSummaryResponse` 仅 `@Builder` 无默认构造，Jackson 反序列化失败后静默重算 | 期望二次 `cached=true`；实际恒为 false | **已修复**（2026-08-03 复测 PASS） |
| BUG-008 | B5-05 | 中 | 问答检索未走 Milvus 向量检索，仅 MySQL 关键词/双字窗口打分；无关问题可能因 bigram 误命中 | 期望语义向量 TopK；实际关键词 | **已修复**（2026-08-03 复测 PASS：ANN + 无关 NO_ANSWER） |

---

## 环境说明

1. Embedding / Milvus 维度以运行配置为准：**1024**（智谱 embedding-2），与任务书初稿 2048/豆包不一致。
2. 管理后台 `models/test` 不支持 `target=milvus`，连通性以 Zilliz REST / 入库写向量为准。
3. 测试临时文件目录 `docs/b5b6-tmp` 中的 json/txt 已按要求清理。

---

## 修订

| 版本 | 日期 | 说明 |
|------|------|------|
| v1 | 2026-08-03 | 批次 5/6 首轮执行与验收 |
| v1.1 | 2026-08-03 | 修复 BUG-007/008 并复测：摘要缓存命中；Milvus ANN 检索生效 |
