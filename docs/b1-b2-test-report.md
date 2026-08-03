# 批次 1 / 2 测试执行记录与验收结论

> **执行日期：** 2026-08-03  
> **执行人：** Auto  
> **环境：** kb-backend @ localhost:8080；MySQL `zhishiyun` 已连通  
> **依据：** [智识云-大模型功能测试任务书.md](./智识云-大模型功能测试任务书.md)

---

## 门禁结论（验收）

| 批次 | 结论 | 说明 |
|------|------|------|
| **批次 1** | **通过** | P0（B1-01 / B1-02 / B1-04）全部 PASS；P1 通过；P2 B1-06 标记 NT |
| **批次 2** | **有条件通过** | P0：B2-01 PASS；B2-03 **BLOCKED**（任务书标注「🔧 依赖实现」：问答链路仍为检索片段拼装，未调用 LLM） |

**进入下一批次建议：** 批次 1 门禁满足，可进入批次 3。批次 2 主链路 LLM 生成需实现就绪后复测 B2-03 / B2-05 / B2-06（完整容错）。

---

## 执行结果表

| 批次 | 用例 | 优先级 | 结果 | 备注 |
|------|------|--------|------|------|
| B1 | B1-01 | P0 | **PASS** | `health=UP`；`db=UP`；`libraryCount=6` |
| B1 | B1-02 | P0 | **PASS** | 各段齐全；`apiKey` 已掩码（如 `sk-****`）；`apiKeyConfigured=true`；dimension=2048 |
| B1 | B1-03 | P1 | **PASS** | 保存成功；含 `****` 的 apiKey 不覆盖；`audit_log` 新增 `MODEL_UPDATE` |
| B1 | B1-04 | P0 | **PASS** | 恢复 DeepSeek/豆包/SiliconFlow 配置后：llm/embedding/vision/ocr 均 `ok=true` 且 `httpStatus<500`（探测为 GET baseUrl，401/404 仍算可达） |
| B1 | B1-05 | P1 | **PASS** | 错误 Key → 401；正确 Key → `docId/taskId`；缺 Header → 500（见缺陷） |
| B1 | B1-06 | P2 | **NT** | 需以 `KB_LLM_MODEL` 重启验证，本轮未中断服务 |
| B2 | B2-01 | P0 | **PASS** | 直连 DeepSeek `chat/completions` HTTP 200，`choices[0].message.content` 非空 |
| B2 | B2-02 | P1 | **PASS** | `POST .../models/test?target=llm` → `ok=true`（恢复配置后） |
| B2 | B2-03 | P0 | **BLOCKED** | SSE 可达：`meta(SEARCHING)` → `done`；当前无命中走 NO_ANSWER。代码 `buildAnswer` 仍为片段拼装，LLM 未接入 |
| B2 | B2-04 | P1 | **PASS** | `status=NO_ANSWER`，含 `suggestions` + `contact`；无 `event:delta` |
| B2 | B2-05 | P1 | **BLOCKED** | 文档 ask:stream 可调；同 B2-03，依赖 LLM 生成接入 |
| B2 | B2-06 | P1 | **PASS** | 无效 Key 直连返回 401；业务 SSE 正常结束无堆栈泄漏。完整「改配置后问答 error」待 LLM 接入后复测 |
| B2 | B2-07 | P2 | **PASS** | `usage_event` 已落库 `event_type=ASK`，含 `user_id/ref_id/extra_json(question)` |
| B2 | B2-08 | P2 | **NT** | `kb.rate-limit.enabled` 默认 false，需改配置重启压测 |

---

## 关键证据摘要

### B1-01
```json
{"service":"kb-backend","status":"UP"}
{"libraryCount":6,"status":"UP"}
```

### B1-04（恢复配置后）
| target | ok | httpStatus |
|--------|-----|------------|
| llm | true | 401 |
| embedding | true | 401 |
| vision | true | 404 |
| ocr | true | 401 |

### B2-01
DeepSeek 返回非空 content（示例）：`Hi, I'm DeepSeek, an AI assistant...`

### B2-03 / B2-04 SSE
```
event:meta
data:{"status":"SEARCHING",...}

event:done
data:{"status":"NO_ANSWER","suggestions":[...],"contact":{...},...}
```

### B2-07 SQL 抽查
| id | user_id | event_type | ref_id | extra_json |
|----|---------|------------|--------|------------|
| 7 | 2001 | ASK | 9 | `{"question":"mars purple unicorn XYZ999 unrelated"}` |
| 4 | 2001 | ASK | 5 | `{"question":"how to apply annual leave?"}` |

---

## 缺陷登记

| 缺陷编号 | 关联用例 | 严重级别 | 缺陷描述 | 期望 / 实际 | 状态 |
|----------|----------|----------|----------|-------------|------|
| BUG-001 | B2-03 / B2-05 | 高 | 问答回答未接入 LLM，仍为检索片段拼装（`ChatStreamService.buildAnswer` / `DocumentAskStreamService.buildAnswer`） | 期望 LLM 基于片段生成；实际拼装原文 | 待修复（任务书已标注依赖实现） |
| BUG-002 | B1-05 | 中 | 内部入库缺 `X-Internal-Api-Key` 时返回 HTTP 500 / `50001`，而非 401 | 期望 401/业务鉴权错误；实际 500（缺必填 Header 未优雅处理） | 待修复 |
| BUG-003 | B1-04 / B2-02 | 中 | 库内 `admin.model.config` 曾残留 OpenAI 配置（`gpt-4o-mini` / `api.openai.com`），覆盖 yml 默认 DeepSeek，导致连通性探测失败 | 期望与 `application.yml` 一致或可识别脏配置；实际默默覆盖 | 本轮已手动恢复为 DeepSeek；建议增加启动校验或重置入口 |

---

## 环境与执行说明

1. 测试前启动：`mvn spring-boot:run`（MySQL 3306 可用；存储走本地 `data/storage`，MinIO/Redis 本轮未启动，不影响批次 1/2）。
2. 登录字段为 `account`（非 `email`）：`{"account":"admin@zhishiyun.com","password":"admin123"}`。
3. B1-04 首次失败因 DB 脏配置；按任务书核对 yml 后 PUT 恢复，复测通过。
4. 可复现脚本：`docs/run-b1-b2-tests.ps1`（含外网 DeepSeek 直连，注意 Key 安全）。

---

## 修订

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-08-03 | 完成批次 1、2 执行与验收 |
