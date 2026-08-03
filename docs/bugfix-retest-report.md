# Bug 修复复测记录

> **日期：** 2026-08-03  
> **范围：** BUG-001 / BUG-002 / BUG-003

## 修复说明

| 缺陷 | 改动 |
|------|------|
| BUG-001 | 新增 `LlmClient`；`ChatStreamService` / `DocumentAskStreamService` 的 `buildAnswer` 改为基于检索片段调用 LLM `/chat/completions` |
| BUG-002 | `IngestController` 的 `X-Internal-Api-Key` 改为 `required=false`，缺失/错误统一 `40101`；`GlobalExceptionHandler` 补充 `MissingRequestHeaderException` |
| BUG-003 | 新增 `AdminModelConfigSanitizer`：启动时若库内 llm 残留 `api.openai.com` 且 yml 非 OpenAI，则回写 yml 默认配置 |

## 复测结果

| 用例 | 结果 | 证据 |
|------|------|------|
| B1-05 | **PASS** | 缺 Key / 错 Key 均 HTTP 401、`code=40101` |
| B2-03 | **PASS** | `meta → citation* → delta* → done(OK)`，回答由 LLM 生成并含 `[n]` 引用 |
| B2-05 | **PASS** | 文档 ask:stream `status=OK`，LLM 生成 |
| B2-06 | **PASS** | 无效 apiKey 后提问返回 `event:error` `code=50001`（LLM 调用失败 HTTP 401），无堆栈泄漏 |
| BUG-003 | **PASS** | 写入 OpenAI 脏配置后重启，日志 `Reset stale admin.model.config`；GET models 恢复 `deepseek-v4-flash` / `api.deepseek.com` |

## 结论

三缺陷均已修复并通过复测；批次 2 原 BLOCKED 的 B2-03/B2-05 现可验收为 PASS。
