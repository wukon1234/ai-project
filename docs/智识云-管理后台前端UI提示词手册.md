# 智识云 · 管理后台 — 前端 UI 提示词手册

> **文档说明**  
> 本文档为「智识云」**管理后台（Admin Console）**前端实现提供可直接粘贴给 AI Coding Agent 的 UI 提示词。  
> 用户侧前端（`frontend-ai-knowledge-assistant`）明确**不包含**管理能力；本后台为独立应用，供知识管理员 / 系统管理员使用。  
> **版本：** v1.0  
> **编写日期：** 2026-07-31  
> **关联文档：**  
> - `docs/智识云-前端需求规格说明书.md`（用户侧边界对照）  
> - `docs/智识云-后端技术方案.md`（数据模型、ACL、入库、审计）  
> - `docs/sql/V1__init_schema.sql`  

---

## 0. 总览

### 0.1 产品定位

| 项 | 内容 |
|----|------|
| 产品名 | 智识云 · 管理后台 |
| 英文名 | ZhishiYun Admin Console |
| 定位 | 企业知识库运营与治理控制台 |
| 核心价值 | 建库入库、权限治理、账号角色、模型运维、操作审计 |
| 与用户侧关系 | **独立前端工程**；不混入用户侧问答/搜索/浏览页面 |

### 0.2 角色与准入

| 角色 code | 中文名 | 可访问模块 |
|-----------|--------|------------|
| `KB_ADMIN` | 知识管理员 | 知识库、文档入库、权限配置、本库相关审计 |
| `SYS_ADMIN` | 系统管理员 | 全部：用户、角色、模型、全局审计 + 知识管理 |
| `EMPLOYEE` | 普通员工 | **禁止进入管理后台**（登录后提示无权限） |

> 现有库表 `sys_user.role_code` 仅有 `EMPLOYEE` / `KB_ADMIN`。UI 需预留 `SYS_ADMIN`；若后端尚未落地，Mock 中模拟即可，并在对接说明中标注。

### 0.3 信息架构（侧栏导航）

```text
智识云 · 管理后台
├── 工作台（Dashboard）
├── 知识管理
│   ├── 知识库列表（创建 / 编辑）
│   ├── 文档入库（上传 / 任务）
│   └── 权限配置（ACL）
├── 账号治理
│   ├── 用户管理
│   └── 角色配置
├── 系统设置
│   ├── 模型设置
│   └── 审计日志
└── 返回用户端（外链，可选）
```

### 0.4 技术与视觉全局约束（所有提示词继承）

1. **技术栈：** React 19 + TypeScript + Vite；图标 `lucide-react`；**与用户侧同一工程** `frontend-ai-knowledge-assistant`，管理端放在 `src/admin/`，通过 `?app=admin` 进入（不要再拆独立工程）。
2. **视觉延续用户侧：** 品牌主色信任蓝 `#2563eb`；浅色默认 + 支持深色；侧栏 + 主内容分区；圆角 12px 级、轻阴影；**不要**做成紫色炫酷 AI 风或报纸排版风。
3. **布局：** 固定左侧导航（可折叠）+ 顶栏（面包屑、当前管理员、主题切换、退出）+ 主内容区。首屏是**工作台仪表盘**，不是营销落地页。
4. **数据策略：** Mock-first；用本地静态数据 + 可选 `localStorage`；接口契约对齐后端 `/api/v1` 与 `/internal/ingest/**`，但本批可不接真后端。
5. **视图切换：** 可用轻量路由（`react-router`）或 view 状态机；管理后台**建议使用 URL 路由**，便于深链与刷新保持。
6. **文案语言：** 默认中文；关键按钮/空态齐全。
7. **明确不做：** 用户侧问答/搜索/阅读器；真实 LLM 调用；后端接口实现。

### 0.5 如何向 Agent 下达任务

```text
请先阅读：
1. docs/智识云-管理后台前端UI提示词手册.md → 批次 Axx
2. docs/智识云-后端技术方案.md（相关章节）
3. docs/sql/V1__init_schema.sql（相关表）

然后严格执行该批次提示词，不要超出范围，不要改动用户侧 frontend-ai-knowledge-assistant 业务页（除非批次明确要求抽公共主题变量）。

【粘贴该批次完整提示词】
```

### 0.6 批次路线图

```text
A00 管理后台工程骨架 + 布局壳 + 登录准入
 └─ A01 工作台 Dashboard
     ├─ A02 知识库管理（创建/编辑）
     ├─ A03 文档上传与入库任务
     ├─ A04 权限配置（ACL）
     ├─ A05 用户管理
     ├─ A06 角色配置
     ├─ A07 模型设置
     └─ A08 审计日志
         └─ A09 联调适配层（可选，对接真实 API）
```

---

## A00 — 工程骨架 + 布局壳 + 管理员登录

### 元信息

| 项 | 内容 |
|----|------|
| 目标 | 可运行的管理后台壳：登录、侧栏导航、主题、路由占位页 |
| 依赖 | 无 |
| 完成门禁 | 管理员可登录进入；员工账号被拒；侧栏 8 个入口可切换到占位页 |

### 提示词

```text
你是资深 React 前端工程师。请在现有工程 `frontend-ai-knowledge-assistant`（Vite + React 19 + TypeScript）内新增 `src/admin/` 管理端模块，实现「智识云 · 管理后台」应用壳。本批次只做骨架与导航，业务页用占位即可。禁止再拆独立前端工程。

【产品】
- 名称：智识云 · 管理后台
- 定位：企业知识库运营与治理控制台
- 与用户侧同仓同工程：通过 `?app=admin` / 登录页「管理后台」入口切换；禁止把管理 CRUD 塞进用户侧个人中心业务页

【技术】
- 复用现有 Vite + React 19 + TypeScript + lucide-react
- 管理端可用 view 状态机（与用户侧一致），不必强上 react-router
- 主题复用现有 ThemeProvider（data-theme）；主色 #2563eb
- 登录态：localStorage `zn-admin-authed` + `zn-admin-user`（Mock）；surface：`zn-app-surface`

【页面结构】
1. /login — 管理员登录页
   - 简洁：左品牌「智识云 · 管理后台」+ 副文案「知识运营与治理控制台」；右登录表单
   - 字段：企业邮箱、密码；按钮「登录」
   - Mock 账号：
     - admin@zhishiyun.com / admin123 → SYS_ADMIN（全模块）
     - kbadmin@zhishiyun.com / kb123 → KB_ADMIN（知识管理+本库审计）
     - zhangming@zhishiyun.com / any → EMPLOYEE → Toast「无管理后台权限」并拒绝进入
   - 页脚：安全合规 · 仅限授权管理员

2. 已登录 Layout
   - 左栏：品牌、导航分组（工作台 / 知识管理 / 账号治理 / 系统设置）
   - 导航项与路由：
     /dashboard 工作台
     /libraries 知识库
     /ingest 文档入库
     /acl 权限配置
     /users 用户管理
     /roles 角色配置
     /models 模型设置
     /audit 审计日志
   - KB_ADMIN 隐藏：用户管理、角色配置、模型设置；审计日志可进但文案标注「与知识相关」
   - 顶栏：面包屑、管理员姓名/角色徽章、主题切换、退出
   - 主区：各路由 Outlet；本批用「模块建设中」占位卡即可

【视觉】
- 延续信任蓝与用户侧质感：侧栏半透明/轻 blur、12px 圆角、清晰分区
- 不要紫色霓虹、不要大面积营销 Hero
- 桌面优先（≥1280）；侧栏可折叠

【交付】
- npm/pnpm 可 dev 启动
- 登录/权限门禁/导航切换可用
- 不要实现各业务 CRUD（后续批次）
```

---

## A01 — 工作台 Dashboard

### 元信息

| 项 | 内容 |
|----|------|
| 目标 | 管理员一进后台看到运营总览与快捷入口 |
| 依赖 | A00 |
| 完成门禁 | 4 类 KPI + 快捷操作 + 最近任务/审计摘要可见 |

### 提示词

```text
在 `frontend-ai-knowledge-admin` 实现管理后台「工作台」页 `/dashboard`。Mock-first。

【页面目标】
让知识/系统管理员 3 秒内知道：库与文档健康度、待审用户、入库失败、近期风险操作。

【布局（单一工作台，不要堆砌营销模块）】
1. 页头：欢迎语「你好，{姓名}」+ 今日日期；右侧主 CTA：「上传文档」「创建知识库」
2. KPI 四格（非卡片堆叠炫技，简洁指标块即可）：
   - 知识库数量
   - 已就绪文档 / 总文档
   - 入库失败任务数（可点进 /ingest?status=FAILED）
   - 待审核用户数（SYS_ADMIN 可见；KB_ADMIN 显示「—」或隐藏）
3. 两栏：
   - 左：最近入库任务（文档名、所属库、状态徽章 PENDING/RUNNING/SUCCESS/FAILED、进度条、时间）
   - 右：最近审计事件（谁、做了什么、对象、时间）
4. 快捷入口条（图标+标题+一句说明，点击跳转）：
   - 创建知识库 → /libraries?action=create
   - 上传文档 → /ingest
   - 配置权限 → /acl
   - 用户审核 → /users?status=0（仅 SYS_ADMIN）
   - 模型设置 → /models（仅 SYS_ADMIN）
   - 审计日志 → /audit

【状态】
- 加载骨架屏；空数据友好文案
- 状态色：SUCCESS 绿、FAILED 红、RUNNING 蓝、PENDING 灰

【不做】
- 真实图表大屏；用户侧使用统计页复刻
```

---

## A02 — 知识库管理（创建 / 编辑）

### 元信息

| 项 | 内容 |
|----|------|
| 目标 | 管理 `kb_library`：列表、创建、编辑、查看文档概览入口 |
| 依赖 | A00 |
| 参考 | 技术方案 `kb_library`；用户侧四库：product/hr/tech/support |

### 提示词

```text
实现管理后台「知识库」模块 `/libraries`。对齐表结构 kb_library（code/name/description/tags/doc_count）。

【列表页】
- 工具栏：搜索（名称/code）、「创建知识库」主按钮
- 表格或清晰列表行（管理后台可用表格，这是交互容器）：
  列：名称、code、简介、标签、文档数、最近更新、操作
- 操作：编辑、权限配置（跳 /acl?library=code）、上传文档（跳 /ingest?library=code）、查看文档（可先跳入库列表过滤）
- 预置四库 Mock：产品知识库 product、人事制度库 hr、技术文档库 tech、售后 FAQ support（与用户侧一致）

【创建知识库（抽屉或居中弹窗）】
字段：
- code：英文小写+下划线/短横，必填，创建后不可改；placeholder 如 `legal`
- name：必填
- description：多行
- tags：chip 输入（如 #制度 #FAQ）
校验：code 唯一；非法字符提示
提交成功 Toast「知识库已创建」，列表刷新

【编辑】
- 可改 name / description / tags
- code 只读展示
- 不提供「删除知识库」危险操作于 MVP（或二次确认 + 仅空库可删，默认隐藏）

【空态】
无库时：插画/简洁空态 +「创建第一个知识库」

【页内声明】
顶部提示条：用户侧仅只读浏览与问答；创建/上传/权限仅在此管理后台。

【不做】
- 真实 MinIO；文档物理删除级联
```

---

## A03 — 文档上传与入库任务

### 元信息

| 项 | 内容 |
|----|------|
| 目标 | 上传文档、选择目标库、跟踪 ingest_task 进度、失败重试/重建索引入口 |
| 依赖 | A00、A02（库列表） |
| 参考 | `/internal/ingest/documents`、`ingest_task`、`kb_document.status` |

### 提示词

```text
实现管理后台「文档入库」页 `/ingest`。Mock 上传与任务进度。

【页头】
标题「文档入库」；说明：支持 pdf/word/excel/ppt/image；单文件建议 ≤50MB（与后端 multipart 一致）。

【上传区】
1. 选择目标知识库（下拉，必选；支持 URL ?library= 预填）
2. 分类 category：faq / policy / manual（默认 manual）
3. 拖拽上传区 + 文件选择；多文件队列
4. 每个文件行：文件名、大小、类型图标、状态、进度、移除
5. 主按钮「开始入库」

【任务列表】
- 筛选：知识库、状态（全部/PENDING/RUNNING/SUCCESS/FAILED）、关键词（文档标题）
- 列：文档标题、库、文件类型、状态、进度、错误信息、创建时间、操作
- 操作：
  - 进行中：禁用
  - 失败：查看错误、重试（Mock 改状态）
  - 成功：查看文档元信息（只读抽屉：pages/summary/status=READY）、重建向量（确认框）
- 状态徽章与进度条动画（Mock：定时把 RUNNING 推到 SUCCESS）

【交互细节】
- 未选库禁止上传
- 类型不在白名单 → 行内错误
- 上传中离开页面 → beforeunload 提示（可选）
- Toast：已加入入库队列 / 入库成功 / 入库失败

【契约注释（供后续联调，勿实现后端）】
- POST /internal/ingest/documents multipart
- GET /internal/ingest/tasks/{taskId}
- POST /internal/ingest/reindex/{docId}

【不做】
- 原文阅读器；用户侧浏览页改造
```

---

## A04 — 权限配置（ACL）

### 元信息

| 项 | 内容 |
|----|------|
| 目标 | 配置谁可以读哪个知识库（用户级 / 部门级） |
| 依赖 | A00、A02 |
| 参考 | `kb_acl`：user_id / dept_code ↔ library_id，perm=READ |

### 提示词

```text
实现管理后台「权限配置」页 `/acl`。

【产品规则】
- 用户侧问答/搜索/浏览只展示有权库；本页是唯一 ACL 配置入口
- 权限粒度 MVP：READ（可读）；不做复杂写权限矩阵
- 支持两种主体：指定用户、指定部门（dept_code）
- 角色兜底说明文案：「KB_ADMIN 默认可管理全部库；普通员工按 ACL + 可选全员可读策略」

【布局】
1. 左侧：知识库选择列表（高亮当前库）
2. 右侧：该库 ACL 规则表
   列：主体类型（用户/部门）、主体名称/工号/部门码、权限、来源说明、操作（移除）
3. 工具栏：「添加规则」「批量导入（Mock 入口禁用+Tooltip：后续支持）」

【添加规则弹窗】
- 主体类型：用户 | 部门
- 用户：可搜索 Mock 用户列表（姓名/工号/邮箱）多选
- 部门：下拉或输入 dept_code（如 RD / HR / SALES）
- 权限：READ（固定）
- 可选开关：「设为全员可读」（写一条特殊规则或库级 flag；UI 要有，Mock 用 library.publicRead）

【空态】
该库暂无额外 ACL → 提示将导致普通员工不可见（除非全员可读）

【安全提示】
危险操作（关闭全员可读、清空规则）需确认对话框。

【不做】
- 字段级/文档级 ACL；审批流
```

---

## A05 — 用户管理

### 元信息

| 项 | 内容 |
|----|------|
| 目标 | 审核注册、启用/禁用、调整角色、查看基本资料 |
| 依赖 | A00 |
| 参考 | `sys_user`：status 0待审/1正常/2禁用；role_code |

### 提示词

```text
实现管理后台「用户管理」`/users`（仅 SYS_ADMIN 可访问；KB_ADMIN 路由守卫重定向到 /dashboard）。

【列表】
筛选：
- 状态 Tab/Segment：全部 / 待审核 / 正常 / 禁用
- 角色：全部 / EMPLOYEE / KB_ADMIN / SYS_ADMIN
- 搜索：姓名、工号、邮箱、手机、部门

表格列：
姓名、工号、邮箱、手机、部门、角色、状态、注册时间、操作

操作：
- 待审核：通过 / 拒绝（拒绝可 Mock 为禁用或删除标记）
- 正常：禁用、调整角色、重置密码（二次确认，Mock Toast「已发送重置邮件」）
- 禁用：启用

【新建用户（可选 MVP）】
抽屉字段：姓名、企业邮箱、手机、工号、部门、角色、初始密码
提交后 status=1

【详情抽屉】
只读资料 + 最近登录（Mock）+ 可访问知识库列表（来自 ACL 汇总）

【批量】
多选：批量通过、批量禁用（待审/正常分别启用）

【验收】
- URL ?status=0 进入即过滤待审
- 无权限角色无法打开本页
- 所有状态变更有 Toast 与列表即时更新（Mock）

【不做】
- 真实邮件；组织架构树同步 LDAP
```

---

## A06 — 角色配置

### 元信息

| 项 | 内容 |
|----|------|
| 目标 | 展示并配置角色权限矩阵（管理后台菜单级） |
| 依赖 | A00、A05 |
| 说明 | 后端当前以 role_code 枚举为主；本页做「角色-能力」配置 UI，Mock 持久化到 localStorage |

### 提示词

```text
实现「角色配置」页 `/roles`（仅 SYS_ADMIN）。

【角色列表】
预置三角色卡片/表：
1. EMPLOYEE 普通员工 — 仅用户端；无管理后台
2. KB_ADMIN 知识管理员 — 知识库/入库/ACL/知识相关审计
3. SYS_ADMIN 系统管理员 — 全部

每角色展示：code、名称、人数（Mock）、描述、操作「配置权限」

【权限矩阵（点击配置后）】
行 = 能力，列 = 允许开关：
能力建议：
- admin.access 登录管理后台
- library.read / library.write
- ingest.upload / ingest.reindex
- acl.manage
- user.manage / user.approve
- role.manage
- model.manage
- audit.read

交互：
- EMPLOYEE 的 admin.access 强制关闭且禁用
- SYS_ADMIN 全开且部分只读锁定（防误关）
- 保存写入 localStorage `zn-admin-role-matrix`
- Toast「角色权限已更新」

【说明区】
用 info callout 写清：用户侧知识库可见性由 ACL 控制，不在本矩阵；本矩阵只治理「管理后台能力」。

【不做】
- 自定义新建角色（可灰掉「即将支持」）
- 与 Spring Security 真注解同步
```

---

## A07 — 模型设置

### 元信息

| 项 | 内容 |
|----|------|
| 目标 | 配置 Embedding / LLM / OCR / Vision 等模型与连接参数（脱敏展示） |
| 依赖 | A00 |
| 参考 | 技术方案 embedding、LLM、OCR、Vision；`application.yml` 中 kb.* |

### 提示词

```text
实现「模型设置」页 `/models`（仅 SYS_ADMIN）。

【页面结构：分组表单，单页保存】

1. 对话模型（LLM）
- provider：OpenAI 兼容 / 其他
- baseUrl、modelName（如 gpt-4o-mini）
- apiKey：密码框，回显掩码 sk-****；支持「重新输入」
- temperature、maxTokens
- 超时秒数

2. Embedding
- modelName（text-embedding-3-small / bge-m3）
- dimension（1536/1024）只读提示「变更维度需全量重建向量」
- 同 baseUrl/apiKey 可「与 LLM 共用」开关

3. OCR
- 启用开关
- provider：PaddleOCR 服务地址
- 超时、并发

4. Vision（可选折叠）
- 启用开关、modelName、用于扫描件/图片描述

5. RAG 参数（只读+可调）
- topK、scoreThreshold、单问答引用上限
- 限流：每用户每分钟问答次数

【操作】
- 「测试连接」按钮：Mock 延迟后成功/失败 Toast
- 「保存配置」：写 localStorage `zn-admin-model-config`；成功提示
- 危险提示：修改 Embedding 模型/维度 → 确认框「需对全部文档重建索引」

【安全】
- 永不在 UI 明文日志打印完整 apiKey
- 页头徽章「仅系统管理员可修改」

【不做】
- 真调用 OpenAI；改服务器 application.yml 文件
```

---

## A08 — 审计日志

### 元信息

| 项 | 内容 |
|----|------|
| 目标 | 查询 `audit_log` 类事件：登录、下载、分享、鉴权失败、管理操作 |
| 依赖 | A00 |
| 参考 | audit_log：action/target_type/target_id/detail/ip/created_at |

### 提示词

```text
实现「审计日志」页 `/audit`。

【筛选栏】
- 时间范围：今天 / 7天 / 30天 / 自定义
- 操作人（用户搜索）
- action 多选：LOGIN、LOGIN_FAIL、DOWNLOAD、SHARE、AUTH_DENY、USER_APPROVE、ACL_UPDATE、INGEST_UPLOAD、MODEL_UPDATE、ROLE_UPDATE 等
- 对象类型 target_type：user/document/library/acl/system
- 关键词（detail 模糊）
- 查询 / 重置；导出 CSV（Mock 下载）

【表格】
列：时间、操作人、action 徽章、对象、详情摘要、IP
行点击 → 右侧抽屉看完整 JSON/detail

【权限】
- SYS_ADMIN：全部
- KB_ADMIN：仅知识相关 action（INGEST_*、ACL_*、DOWNLOAD、SHARE）；无 MODEL_/ROLE_/USER_ 类

【空态与性能】
- 无结果空态
- 分页（每页 20）
- 前端 Mock 50~100 条足够

【不做】
- 日志删改；实时 websocket
```

---

## A09 — 联调适配层（可选）

### 元信息

| 项 | 内容 |
|----|------|
| 目标 | 将 Mock 替换为 API client，对接后端管理/内部接口 |
| 依赖 | A01–A08；后端需具备对应 Admin API（若尚无，本批先定义 TS 类型与 TODO） |

### 提示词

```text
为 `frontend-ai-knowledge-admin` 增加 API 适配层，保持 UI 不变。

【要求】
1. 建立 `src/api/`：http 客户端（Authorization Bearer）、统一处理 {code,message,data}
2. 按模块拆分：auth/users/roles/libraries/ingest/acl/models/audit
3. 用环境变量 VITE_API_BASE_URL
4. 提供 Mock/Real 切换：VITE_USE_MOCK=true 时走现有 Mock
5. 入库进度：轮询 GET /internal/ingest/tasks/{id} 每 2s，直至 SUCCESS/FAILED
6. 错误：401 跳登录；403 Toast 无权限；业务 code≠0 展示 message

【若后端尚无管理 API】
- 在 `src/api/types.ts` 写齐请求响应类型
- 用 README 一节列出期望端点表（不要新增长篇 docs），标 TODO
- 保持 Mock 可演示

【不要】
- 修改用户侧工程业务逻辑
- 提交真实密钥
```

---

## 1. 全局 UI 文案与组件约定

| 场景 | 约定 |
|------|------|
| 主按钮 | 实心信任蓝 |
| 次按钮 | 线框/幽灵 |
| 危险操作 | 红字 + 确认对话框 |
| Toast | 约 1.8s～2.5s |
| 空态 | 一句原因 + 一个主操作 |
| 加载 | 表格骨架或 Spinner，避免整页白屏 |
| 徽章 | 角色/状态用柔和底色，不要霓虹 glow |
| 表格 | 管理后台允许使用；用户侧产品页仍避免无意义卡片墙 |

---

## 2. 与用户侧边界对照（防做错地方）

| 能力 | 用户侧 | 管理后台 |
|------|--------|----------|
| 智能问答 / 搜索 / 浏览 / 阅读 | ✅ | ❌ |
| 创建知识库 | ❌（明示无入口） | ✅ |
| 上传文档 | ❌ | ✅ |
| 权限配置 | ❌ | ✅ |
| 用户管理 / 角色配置 | ❌（个人中心明示无） | ✅ |
| 模型设置 | ❌ | ✅ |
| 审计日志 | ❌ | ✅ |

---

## 3. 建议 Mock 数据种子

```ts
// 示例结构，实现时可放到 src/mock/
libraries: product/hr/tech/support
users: 含待审 2 人、禁用 1 人、KB_ADMIN 1 人、SYS_ADMIN 1 人
acl: hr 全员可读；tech 仅 RD 部门；product 指定用户
ingestTasks: 各状态各 1～2 条
audit: 覆盖登录失败、下载、ACL 变更、入库
modelConfig: 与技术方案默认值一致的占位
```

---

## 4. 验收总表

| 批次 | 必须通过 |
|------|----------|
| A00 | 独立工程启动；角色门禁；侧栏齐全 |
| A01 | Dashboard KPI 与快捷入口跳转正确 |
| A02 | 创建/编辑知识库；code 唯一只读 |
| A03 | 上传队列与任务状态流转可演示 |
| A04 | 按库配置用户/部门 READ；全员可读开关 |
| A05 | 待审通过/禁用/改角色 |
| A06 | 权限矩阵保存与角色锁定规则 |
| A07 | 模型表单保存与测连；Key 掩码 |
| A08 | 筛选分页；KB_ADMIN 视野受限 |
| A09 | Mock/Real 可切换（可选） |

---

## 5. 一键总提示词（若希望 Agent 一次做完 A00–A08）

> 范围大，推荐仍按批次执行；仅在需要「一次性出高保真原型」时使用以下压缩版。

```text
请在现有工程 frontend-ai-knowledge-assistant 内新增 src/admin/（Vite+React19+TS+lucide-react），实现「智识云·管理后台」高保真 Mock 原型。与用户端同项目，通过 ?app=admin 进入；不要新建独立前端工程。

品牌主色 #2563eb，侧栏+顶栏+主区布局，支持亮暗主题。登录门禁：SYS_ADMIN 全模块，KB_ADMIN 仅知识库/入库/ACL/知识审计，EMPLOYEE 拒绝。

必须完成页面：
1) Dashboard 运营总览与快捷入口
2) 知识库 CRUD（code 创建后只读；预置 product/hr/tech/support）
3) 文档入库上传队列 + 任务状态进度（对齐 ingest_task）
4) 权限配置（按库 ACL：用户/部门 READ + 全员可读）
5) 用户管理（待审/启用禁用/改角色）
6) 角色配置（菜单能力矩阵，localStorage）
7) 模型设置（LLM/Embedding/OCR/Vision/RAG，Key 掩码，测试连接 Mock）
8) 审计日志（筛选分页，角色视野隔离）

约束：
- 同仓同工程 src/admin/，不要往用户侧个人中心塞管理 CRUD；入口用 ?app=admin / 登录页「管理后台」
- Mock-first，数据形状对齐 docs/sql/V1__init_schema.sql 与后端技术方案
- 管理页可用表格；视觉克制专业，不要紫色霓虹风
- 空态/加载/Toast/确认框齐全
- 不要写长篇新文档；不要接真实密钥与真实 LLM
- 本阶段不要改后端代码
```
