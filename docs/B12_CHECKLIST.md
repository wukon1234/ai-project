# B12 安全与配置对照自检清单（技术方案 §15 / §8）

- [x] 内部入库 API 走 `/api/v1/internal/**` + API Key，C 端无上传入口
- [x] JWT 鉴权；SSO mock 可签发本地 Token
- [x] 忘记密码：Redis TTL 30m，邮件发送 stub 日志
- [x] 分享：会话 share_token；文档 Redis 短链 + 过期；敏感库可禁外链
- [x] Vision：仅 NEED_VISION 页；可配置开关与每文档最大页数；失败不影响 OCR
- [x] 混合检索：`kb.rag.hybrid-enabled` + RRF；开启前后日志可对比
- [x] 问答限流：Redis 用户级默认 10/min，错误码 42901
- [x] traceId：过滤器注入 MDC / 响应头，SSE meta 携带
- [x] Nginx 示例：TLS、SSE `proxy_buffering off`、上传大小
- [ ] 生产环境关闭 `kb.sso.mock-enabled`
- [ ] 生产配置真实 Azure AD clientId/secret/tenant
- [ ] 生产开启 HTTPS 与强 JWT secret
