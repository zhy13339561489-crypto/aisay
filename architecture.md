# AI漫剧生成系统模块架构说明

## 当前阶段架构范围

Phase 1 已完成前后端工程骨架搭建，当前架构重点是建立清晰的模块边界、运行入口和基础设施连接配置。AI 引擎、数据库表结构、实体、认证、聊天和漫剧业务逻辑会在后续阶段逐步落入这些模块。

```mermaid
flowchart LR
    Browser["浏览器 / Vue 前端 :8080"]
    Backend["Spring Boot 后端 :8085"]
    MySQL["MySQL"]
    Redis["Redis"]
    RabbitMQ["RabbitMQ"]

    Browser -->|/api HTTP 代理| Backend
    Browser -->|/ws WebSocket 代理| Backend
    Backend --> MySQL
    Backend --> Redis
    Backend --> RabbitMQ
```

## 后端模块

后端工程位于 `F:\ai\aisay\backend`，主包为 `com.aisay.manga`。当前采用标准 Spring Boot 分层结构，为后续功能留下稳定落点。

- `controller`：REST API 与 WebSocket 控制器入口，后续承载用户、聊天、漫剧、文件等接口。
- `service`：业务接口层，定义用户、聊天、漫剧生成等业务能力。
- `service.impl`：业务实现层，封装流程编排、权限校验、事务处理和模拟 AI 回复逻辑。
- `repository`：数据访问层，后续放置 MyBatis-Plus Mapper。
- `entity`：数据库实体层，对应 users、chat_sessions、messages、stories 等核心表。
- `dto.request`：请求 DTO，隔离外部 API 入参与内部实体。
- `dto.response`：响应 DTO，统一对前端返回的数据结构。
- `config`：Spring Security、MyBatis-Plus、Redis、WebSocket、CORS、Knife4j 等配置类。
- `utils`：JWT、本地文件存储、通用转换等工具类。

后端配置集中在 `backend/src/main/resources/application.yml`。阶段一已经接入 `config.txt` 中的 MySQL、Redis 和 RabbitMQ 地址与账号信息，并设置服务端口为 `8085`。

## 前端模块

前端工程位于 `F:\ai\aisay\frontend`，采用 Vue 3、TypeScript、Vite、Pinia、Vue Router 和 Element Plus。当前入口和目录结构已就绪，页面与业务状态会在后续阶段增量实现。

- `views`：页面级组件，如聊天页、登录页、注册页、漫剧列表、漫剧详情、用户中心。
- `components/chat`：聊天域组件，如消息气泡、输入框、会话列表。
- `components/story`：漫剧域组件，如角色卡片、分镜/故事展示组件。
- `components/common`：跨模块通用组件，如布局、加载态、错误展示。
- `stores`：Pinia 状态管理，后续拆分用户、聊天、漫剧 store。
- `api`：Axios API 封装，后续按认证、聊天、漫剧等领域拆分。
- `router`：Vue Router 配置，阶段一保持空路由表，后续按实现计划逐步加入页面路由和鉴权守卫。
- `types`：前端共享 TypeScript 类型定义。
- `styles`：全局样式、变量和主题入口。

`frontend/vite.config.ts` 已配置开发端口 `8080`，并将 `/api` 与 `/ws` 分别代理到后端 REST 与 WebSocket 服务，保证前端开发阶段不需要硬编码后端地址。

## 基础设施连接

当前配置遵循用户提供的 `config.txt`，不是设计文档中的示例账号。

- MySQL：`jdbc:mysql://localhost:3306/springcloud?...`，用户名 `root`，密码 `root`。
- Redis：`127.0.0.1:6379`，database `0`。
- RabbitMQ：`127.0.0.1:5672`，用户名 `guest`，密码 `guest`。

Phase 2 已选择与 `config.txt` 保持一致，初始化 SQL 默认面向 `springcloud`。如果后续需要切回设计文档中的 `aisay_manga`，需要同步调整 `init.sql` 和 `application.yml`。

## 数据库初始化模块

Phase 2 新增 `backend/src/main/resources/db/init.sql` 作为数据库结构的唯一初始化入口。该文件默认面向当前配置中的 `springcloud` 数据库，包含建库、业务用户授权和 7 张核心业务表。

```mermaid
erDiagram
    users ||--o{ chat_sessions : has
    users ||--o{ stories : creates
    chat_sessions ||--o{ messages : contains
    stories ||--o{ characters : has
    stories ||--o{ scenes : contains
    scenes ||--o{ dialogues : has
    characters ||--o{ dialogues : speaks
```

表职责如下：

- `users`：保存注册用户、邮箱、密码哈希、头像和偏好配置。
- `chat_sessions`：保存用户与 AI 创作助手的会话元数据、当前阶段和进度。
- `messages`：保存会话内用户消息与 AI 消息。
- `stories`：保存漫剧主记录，包括标题、类型、风格、摘要、全文、封面和统计数据。
- `characters`：保存某部漫剧下的角色设定。
- `scenes`：保存某部漫剧下的场景/分镜描述。
- `dialogues`：保存场景中的对白，并可关联到具体角色。

## MyBatis-Plus 配置模块

Phase 2 新增 `com.aisay.manga.config.MyBatisPlusConfig`。该配置类负责注册 MyBatis-Plus 拦截器链，目前包含分页插件和乐观锁插件。

- 分页插件面向 MySQL，用于后续漫剧列表、会话列表等分页查询。
- 乐观锁插件为后续需要版本控制的实体预留能力，避免并发更新覆盖。
- `application.yml` 中集中配置 XML Mapper 扫描路径、实体别名包、下划线转驼峰和全局逻辑删除值。

## Redis 配置模块

Phase 2 新增 `com.aisay.manga.config.RedisConfig`。该配置提供统一的 `RedisTemplate<String, Object>`，避免各业务模块自行配置序列化策略。

- Redis key 和 hash key 使用 `StringRedisSerializer`，保证可读性和跨工具兼容性。
- Redis value 和 hash value 使用 `GenericJackson2JsonRedisSerializer`，便于保存后续会话上下文、缓存对象和任务状态。
- 该配置为后续聊天上下文缓存、用户资料缓存和生成任务状态缓存提供基础设施。

## 实体与数据访问模块

Phase 3 在后端补齐了实体层和 Mapper 层，形成从数据库表到 Java 对象再到数据访问接口的基础通路。

```mermaid
flowchart TB
    Service["后续 Service 层"]
    Mapper["repository Mapper 接口"]
    Entity["entity 实体模型"]
    Database["MySQL 数据表"]

    Service --> Mapper
    Mapper --> Entity
    Mapper --> Database
    Entity -.字段映射.-> Database
```

实体层说明：

- `User` 对应 `users`，承载用户账号、邮箱、密码哈希、头像和偏好配置。
- `ChatSession` 对应 `chat_sessions`，承载会话状态、进度、上下文 JSON 和最后活跃时间。
- `Message` 对应 `messages`，承载用户/AI 消息、元数据 JSON 和创建时间。
- `Story` 对应 `stories`，承载漫剧主信息、全文内容、封面和统计字段。
- `Character` 对应 `characters`，承载角色设定；由于 Java 内置 `Character` 已占用 MyBatis 类型别名，该实体显式使用 `MangaCharacter` 别名。
- `Scene` 对应 `scenes`，承载场景序号、场景设定、描述和视觉元素 JSON。
- `Dialogue` 对应 `dialogues`，承载场景对白；SQL 保留字 `order` 通过反引号列名映射。

Mapper 层说明：

- 所有 Mapper 统一放在 `com.aisay.manga.repository`，由主启动类 `@MapperScan` 扫描。
- 基础 CRUD 由 MyBatis-Plus `BaseMapper<T>` 提供，减少重复 SQL。
- `ChatSessionMapper` 提供按用户和状态查询会话，服务后续会话列表。
- `MessageMapper` 提供按会话时间顺序查询消息，服务后续聊天历史。
- `StoryMapper` 提供按用户分页查询漫剧，服务后续个人作品列表。

测试策略：

- `UserMapperTest` 是真实 MySQL 集成测试，但默认跳过。
- 跳过的原因是数据库初始化 SQL 由用户手动执行，自动测试不能假设表已存在。
- 用户执行 `init.sql` 并确认表存在后，可通过 `-Daisay.integration.mysql=true` 显式开启真实数据库验证。

## DTO 模块

Phase 4 新增 DTO 层，用于隔离外部 API 契约、内部业务模型和数据库实体。DTO 不直接访问数据库，也不承载业务流程，只负责请求参数校验和响应数据结构。

```mermaid
flowchart LR
    Client["前端 / API 调用方"]
    RequestDTO["dto.request 请求 DTO"]
    Controller["后续 Controller"]
    Service["后续 Service"]
    Entity["entity 实体"]
    ResponseDTO["dto.response 响应 DTO"]

    Client --> RequestDTO
    RequestDTO --> Controller
    Controller --> Service
    Service --> Entity
    Service --> ResponseDTO
    ResponseDTO --> Client
```

请求 DTO 说明：

- 认证相关：`RegisterRequest`、`LoginRequest`。
- 用户资料相关：`UserUpdateRequest`。
- 聊天相关：`ChatStartRequest`、`SendMessageRequest`。
- 漫剧相关：`StoryGenerateRequest`、`StoryUpdateRequest`。
- 基础格式校验使用 Jakarta Bean Validation，例如 `@NotBlank`、`@NotNull`、`@Email`、`@Size`。

响应 DTO 说明：

- `ApiResponse<T>` 是统一响应包装，后续 Controller 默认返回该结构。
- `LoginResponse` 承载登录令牌和用户基础信息。
- `UserProfileResponse` 承载用户中心展示所需信息。
- `ChatSessionResponse` 和 `MessageResponse` 承载聊天页面所需数据。
- `StoryResponse` 承载漫剧列表卡片所需数据。
- `StoryDetailResponse` 继承 `StoryResponse`，扩展全文和角色列表，服务漫剧详情页；场景列表字段保留兼容，但前端当前不展示。

边界约定：

- DTO 不暴露 `passwordHash`、`preferences` 等内部字段，避免敏感信息或内部结构泄露到前端。
- DTO 的基础校验只处理格式与必填；唯一性、归属关系、权限控制由后续 Service 和 Security 层承担。
- Entity 与 DTO 之间的转换将在后续 Service 层实现，必要时可增加专门的 converter/assembler。

## 安全认证模块

Phase 5 新增基于 JWT 的无状态认证体系。登录成功后后端签发 token，前端后续请求通过 `Authorization: Bearer <token>` 携带身份；后端过滤器验证 token 后把用户 ID 写入 Spring Security 上下文。

```mermaid
sequenceDiagram
    participant Client as 前端
    participant AuthApi as UserController
    participant UserService as UserService
    participant Jwt as JwtUtil
    participant Filter as JwtAuthenticationFilter
    participant ProtectedApi as 受保护接口

    Client->>AuthApi: POST /api/auth/login
    AuthApi->>UserService: 校验用户名和密码
    UserService->>Jwt: 生成 JWT
    Jwt-->>Client: token
    Client->>Filter: Authorization: Bearer token
    Filter->>Jwt: 验证并解析 userId
    Filter->>ProtectedApi: 写入 SecurityContext 后放行
```

安全配置说明：

- `SecurityConfig` 关闭 CSRF、表单登录和 HTTP Basic，使用无状态会话。
- `/api/auth/**`、Knife4j/OpenAPI 文档资源和 `/ws/**` 放行。
- 其他接口默认需要认证。
- 未认证请求返回 JSON 格式的 `ApiResponse`，HTTP 状态码为 401。
- `CorsConfig` 允许前端开发地址 `localhost:8080` 和 `127.0.0.1:8080`。

认证业务说明：

- `JwtUtil` 负责 token 签发、验证和解析。
- `JwtAuthenticationFilter` 负责从请求头解析 Bearer token 并设置认证上下文。
- `SecurityUtils` 提供 `getCurrentUserId()`，后续业务接口用它获取当前登录用户。
- `UserServiceImpl` 负责注册、登录、获取和更新用户资料。
- 密码仅保存 BCrypt 哈希，不在响应 DTO 中返回。

接口边界：

- `POST /api/auth/register` 和 `POST /api/auth/login` 不需要 token。
- `GET /api/user/profile` 和 `PUT /api/user/profile` 需要有效 token。
- 当前异常响应将在 Phase 8 的全局异常处理阶段统一收口。

## 聊天服务模块

Phase 6 新增聊天会话、消息发送、固定 AI 回复和 WebSocket 推送能力。当前聊天服务仍是 MVP 模拟实现：后端保存用户消息后生成固定 AI 回复，不调用外部 AI 引擎。

```mermaid
flowchart LR
    Client["前端"]
    Rest["ChatController REST"]
    Ws["ChatWebSocketController STOMP"]
    Service["ChatService"]
    SessionMapper["ChatSessionMapper"]
    MessageMapper["MessageMapper"]
    Broker["/topic/chat/{sessionId}"]

    Client -->|HTTP /api/chat/*| Rest
    Client -->|STOMP /app/chat.send| Ws
    Rest --> Service
    Ws --> Service
    Service --> SessionMapper
    Service --> MessageMapper
    Ws --> Broker
    Broker --> Client
```

REST 接口：

- `POST /api/chat/start` 创建会话。
- `POST /api/chat/message` 发送用户消息并返回 AI 固定回复。
- `GET /api/chat/history/{sessionId}` 查询会话历史。
- `GET /api/chat/sessions` 查询当前用户会话列表。
- `DELETE /api/chat/session/{sessionId}` 将会话标记为 `deleted`。

服务层规则：

- 所有聊天操作都基于当前登录用户 ID。
- 读取、发送、删除会话前都会校验会话归属。
- 删除会话采用状态标记，不物理删除消息，便于后续审计或恢复策略扩展。
- 会话创建时默认阶段为 `INITIAL`，进度为 `0`。
- 消息角色使用 `user` 和 `ai`。

WebSocket 规则：

- STOMP 端点为 `/ws/chat`，SockJS 可用。
- 应用消息前缀为 `/app`，当前发送目的地为 `/app/chat.send`。
- 订阅目的地为 `/topic/chat/{sessionId}`。
- 握手路径放行，但发送消息时需要在 STOMP native header 中携带 `Authorization: Bearer <token>`。
- WebSocket 和 HTTP 共用 `ChatService.sendMessage`，避免双写不同业务逻辑。

## 后续演进

Phase 7 到 Phase 8 会在后端现有包结构中继续补齐漫剧、文件与异常处理。Phase 9 之后会在前端现有目录中逐步实现路由、布局、认证、聊天、漫剧和用户中心页面。

## 漫剧服务模块

Phase 7 在后端补齐 `StoryService` 与 `StoryController`，让“对话收集需求 -> 生成漫剧草稿 -> 查看/编辑/删除作品”的主链路有了可调用的 REST API。当前实现仍是 MVP 模拟生成，不接入 Python/LangChain AI 引擎，也不投递 RabbitMQ 异步任务。

```mermaid
flowchart LR
    Client["前端 / API 调用方"]
    Controller["StoryController"]
    Service["StoryServiceImpl"]
    StoryMapper["StoryMapper"]
    CharacterMapper["CharacterMapper"]
    SceneMapper["SceneMapper"]
    ChatSessionMapper["ChatSessionMapper"]
    MySQL["MySQL"]

    Client -->|/api/story/*| Controller
    Controller --> Service
    Service --> ChatSessionMapper
    Service --> StoryMapper
    Service --> CharacterMapper
    Service --> SceneMapper
    ChatSessionMapper --> MySQL
    StoryMapper --> MySQL
    CharacterMapper --> MySQL
    SceneMapper --> MySQL
```

模块职责：

- `StoryController` 只负责接收 HTTP 请求、读取当前登录用户 ID、调用服务层并包装 `ApiResponse`。
- `StoryServiceImpl` 负责业务规则：会话归属校验、漫剧归属校验、模拟生成、分页查询、详情组装、字段更新和删除。
- `StoryMapper` 负责 `stories` 主表 CRUD 与当前用户漫剧分页查询。
- `CharacterMapper` 和 `SceneMapper` 负责加载或写入漫剧详情页所需的角色、场景数据。
- `ChatSessionMapper` 在生成漫剧前用于确认 `sessionId` 合法，保证用户只能基于自己的会话生成作品。

接口边界：

- `POST /api/story/generate` 需要登录，并要求请求体包含 `sessionId`；当前会创建 1 条 `stories`、2 条 `characters`、2 条 `scenes`。
- `GET /api/story/list?page=1&size=10` 返回当前用户的分页漫剧列表，排序规则为 `updated_at DESC, id DESC`。
- `GET /api/story/{id}` 返回当前用户拥有的漫剧详情，包括 `fullContent`、`characters`、`scenes`。
- `PUT /api/story/{id}` 只更新请求中非 `null` 字段，适合前端做局部编辑。
- `DELETE /api/story/{id}` 删除当前用户拥有的漫剧；关联角色、场景、对白依赖数据库外键级联清理。

数据流：

```mermaid
sequenceDiagram
    participant Client as 前端
    participant StoryApi as StoryController
    participant StoryService as StoryServiceImpl
    participant SessionDb as chat_sessions
    participant StoryDb as stories
    participant DetailDb as characters/scenes

    Client->>StoryApi: POST /api/story/generate
    StoryApi->>StoryService: generateStory(userId, sessionId)
    StoryService->>SessionDb: 校验会话存在且属于当前用户
    StoryService->>StoryDb: 插入模拟 Story 草稿
    StoryService->>DetailDb: 插入默认角色和场景
    StoryService-->>Client: StoryResponse
    Client->>StoryApi: GET /api/story/{id}
    StoryApi->>StoryService: getStoryDetail(storyId, userId)
    StoryService->>StoryDb: 校验漫剧归属
    StoryService->>DetailDb: 加载角色和场景
    StoryService-->>Client: StoryDetailResponse
```

设计约束：

- 权限校验放在服务层，避免不同入口重复实现归属判断。
- DTO 不暴露数据库内部统计字段之外的敏感信息，详情页只返回前端当前阶段需要的故事、角色和场景。
- 当前生成内容是固定模拟数据，后续接入 AI 引擎时可以保持 Controller 契约不变，只替换 `StoryServiceImpl.generateStory` 内部编排逻辑。

## 文件存储与异常处理模块

Phase 8 新增本地文件存储和统一异常处理。文件服务把上传文件落到后端本地 `storage` 目录，返回相对路径和 API URL；异常处理把参数错误、越权、资源不存在、文件错误和兜底错误统一包装成 `ApiResponse`。

```mermaid
flowchart LR
    Client["前端 / API 调用方"]
    FileController["FileController"]
    StorageUtil["LocalFileStorageUtil"]
    Storage["backend/storage"]
    ExceptionHandler["GlobalExceptionHandler"]
    ErrorController["ApiErrorController"]

    Client -->|POST /api/files/upload| FileController
    Client -->|GET /api/files/{category}/{date}/{filename}| FileController
    Client -->|DELETE /api/files/{category}/{date}/{filename}| FileController
    FileController --> StorageUtil
    StorageUtil --> Storage
    FileController -.异常.-> ExceptionHandler
    Client -->|未知接口 /error| ErrorController
```

文件服务职责：

- `StorageConfig` 负责读取 `storage.root-path`、`storage.max-file-size`、`storage.max-request-size`，并创建 `LocalFileStorageUtil` Bean。
- `LocalFileStorageUtil` 负责文件保存、读取、删除、URL 生成和路径安全校验。
- `FileController` 负责 HTTP multipart 上传、资源读取和删除接口。
- `FileUploadResponse` 作为上传响应 DTO，承载 `originalFilename`、`filePath`、`fileUrl`、`size`、`contentType`。

文件路径规则：

- 根目录默认是后端工作目录下的 `./storage`。
- 保存路径为 `{category}/{yyyy-MM-dd}/{uuid.ext}`，例如 `resources/2026-05-17/xxxx.png`。
- 当前分类名只允许字母、数字、下划线和短横线，避免把用户输入直接变成任意目录。
- 当前扩展名白名单为 `jpg`、`jpeg`、`png`、`gif`、`bmp`、`pdf`、`epub`。
- 读取和删除文件前会做路径规范化，并确保最终路径仍在 `storage` 根目录内。

异常处理职责：

- `GlobalExceptionHandler` 处理 Controller 和 Service 抛出的业务异常、参数校验异常、multipart 异常、文件 IO 异常和未知异常。
- `ApiErrorController` 处理 Spring Boot 错误转发，保证未知接口也返回统一 JSON。
- `SecurityConfig` 放行 `/error`，避免错误转发被认证流程抢先变成 401。

统一响应约定：

- 参数校验失败返回 HTTP 400，响应体 `code=400`，字段级错误放在 `data` 中。
- 越权访问返回 HTTP 403，响应体 `code=403`。
- 资源不存在或接口不存在返回 HTTP 404，响应体 `code=404`。
- 未认证请求仍由 Spring Security entry point 返回 HTTP 401 JSON。
- 未预期异常返回 HTTP 500，避免把堆栈信息暴露给前端。

设计约束：

- 当前所有 `/api/files/**` 接口都沿用 JWT 认证规则；如果后续封面图需要公开展示，可以仅放行 `GET /api/files/**`，上传和删除继续要求认证。
- 当前文件元数据不单独入库，由业务字段保存返回的 `filePath` 或 `fileUrl`；例如用户头像可写入 `users.avatar_path`，漫剧封面可写入 `stories.cover_image_path`。
- 本地文件存储适合 MVP 和单机开发，后续如迁移到 MinIO/对象存储，可以保留 Controller 响应契约，只替换 `LocalFileStorageUtil` 的实现。

## 前端路由与基础布局模块

Phase 9 在前端补齐 Vue Router 路由表、登录守卫和应用主布局。当前阶段的页面以可编译占位内容为主，目的是先稳定页面骨架，让 Phase 10 到 Phase 13 可以在既有路由和布局中继续接入认证、聊天、漫剧和用户中心业务。

```mermaid
flowchart LR
    Browser["浏览器"]
    Router["Vue Router"]
    Guard["路由守卫"]
    Login["LoginView / RegisterView"]
    Layout["AppLayout"]
    Chat["ChatView"]
    Stories["StoryList"]
    Detail["StoryDetailView"]
    User["UserCenter"]
    Storage["localStorage.token"]

    Browser --> Router
    Router --> Guard
    Guard --> Storage
    Guard -->|未登录| Login
    Guard -->|已登录| Layout
    Layout --> Chat
    Layout --> Stories
    Layout --> Detail
    Layout --> User
```

路由结构：

- `/` 会根据本地 token 是否有效重定向到 `/chat` 或 `/login`。
- `/login` 和 `/register` 是访客页面，不套用主布局。
- `/chat`、`/chat/:sessionId`、`/stories`、`/story/:id`、`/user` 是受保护页面，统一挂在 `AppLayout` 下。
- 未知前端路径兜底重定向到 `/chat`，再由守卫决定是否需要登录。
- 所有页面组件均使用懒加载，减少首屏路由表对后续模块的耦合。

守卫规则：

- 受保护路由依赖 `localStorage.token` 判断登录态，并会解析 JWT `exp` 清理过期登录态。
- 未登录访问受保护路由时跳转 `/login?redirect=<原路径>`。
- 已登录访问 `/login` 或 `/register` 时跳转 `/chat`。
- 当前路由守卫保持轻量，Pinia 用户 Store 负责登录、退出和用户资料持久化。

布局职责：

- `AppLayout` 提供顶部导航、品牌入口、用户菜单和内容承载区。
- 顶部导航当前包含 `对话` 与 `漫剧列表`，对应 `/chat` 和 `/stories`。
- 用户菜单当前包含 `个人中心` 和 `退出登录`。
- 退出登录负责清理 `localStorage.token` 与 `localStorage.userInfo`，并跳转 `/login`。
- 布局通过 `<RouterView />` 渲染子页面，保证后续业务页面只关注自身内容。

页面边界：

- `ChatView` 已接入会话列表、消息列表、输入区、WebSocket 订阅和生成漫剧入口。
- `StoryList` 已接入漫剧卡片网格、分页、当前页筛选和删除入口。
- `StoryDetailView` 已接入摘要、剧情大纲、主要角色卡片、编辑、删除和大纲修改入口。
- `UserCenter` 已接入资料展示、资料编辑、头像上传和创作统计。
- `LoginView` 和 `RegisterView` 已接入真实认证接口。

设计约束：

- 路由层只做最小登录态判断和过期 token 清理，业务数据加载仍放在各页面 Store 中。
- 登录 token 来自 `POST /api/auth/login` 返回的 JWT，并通过 Axios 拦截器注入后续 API 请求。
- 视觉层保留明确的品牌感和响应式布局，但不抢占后续聊天、漫剧列表、用户中心的业务组件设计空间。

## 前端认证模块

Phase 10 在前端补齐认证 API、Axios 拦截器、用户 Store，以及真实登录/注册表单。该模块把后端 Phase 5 的 JWT 认证接口接入前端路由体系，为后续聊天、漫剧和用户中心页面提供统一登录态。

```mermaid
flowchart LR
    LoginView["LoginView / RegisterView"]
    UserStore["userStore"]
    AuthApi["authApi"]
    Axios["Axios request"]
    Backend["Spring Boot /api"]
    Storage["localStorage"]
    Router["Vue Router Guard"]
    Layout["AppLayout"]

    LoginView --> UserStore
    UserStore --> AuthApi
    AuthApi --> Axios
    Axios -->|/api/auth/login 等| Backend
    UserStore --> Storage
    Axios -->|Authorization: Bearer token| Backend
    Router --> Storage
    Layout --> UserStore
```

API 层职责：

- `api/index.ts` 创建统一 Axios 实例，`baseURL` 为空，由 Vite 代理把 `/api` 转发到后端 `8085`。
- 请求拦截器从 `localStorage.token` 读取 JWT，并注入 `Authorization: Bearer <token>`。
- 响应拦截器处理 401：清理本地登录态、跳转登录页、保留当前路径到 `redirect`。
- 响应拦截器处理非 401 错误：优先显示后端 `ApiResponse.message`。
- `api/authApi.ts` 封装注册、登录、获取资料、更新资料接口，页面和 Store 不直接写 URL。

用户状态职责：

- `stores/userStore.ts` 维护 `token`、`userInfo`、`isLoggedIn`。
- Store 初始化时从 `localStorage` 恢复登录态，保证刷新页面后仍可保留登录状态。
- `login` action 会调用后端登录接口获取 JWT，再调用 `getProfile` 获取完整用户资料。
- `register` action 调用注册接口但不自动登录，因为当前后端注册接口不返回 JWT。
- `logout` action 清理 Store 与 `localStorage`，供布局菜单和 401 拦截复用。

页面职责：

- `LoginView` 负责用户名/密码表单校验、调用 `userStore.login()`、登录成功后跳转 `redirect` 或 `/chat`。
- `RegisterView` 负责用户名/邮箱/密码/确认密码表单校验、调用 `userStore.register()`、注册成功后跳转 `/login`。
- `AppLayout` 从 `userStore.userInfo` 读取用户名，并通过 `userStore.logout()` 退出登录。

认证流：

```mermaid
sequenceDiagram
    participant User as 用户
    participant Login as LoginView
    participant Store as userStore
    participant Api as authApi/Axios
    participant Backend as 后端认证接口
    participant Storage as localStorage
    participant Router as Vue Router

    User->>Login: 输入用户名和密码
    Login->>Store: login(username, password)
    Store->>Api: POST /api/auth/login
    Api->>Backend: 登录请求
    Backend-->>Api: JWT
    Store->>Storage: 保存 token
    Store->>Storage: 保存基础 userInfo
    Store->>Api: GET /api/user/profile
    alt profile 返回 200
        Backend-->>Store: 用户资料
        Store->>Storage: 覆盖保存完整 userInfo
    else profile 返回 401
        Store->>Storage: 清理 token 和 userInfo
        Store-->>Login: 登录失效错误
    else profile 非 401 异常
        Store-->>Login: 保留 token 和基础 userInfo
    end
    Login->>Router: 跳转 redirect 或 /chat
```

设计约束：

- 路由守卫当前仍以 `localStorage.token` 作为最小登录态判断，避免在守卫初始化阶段引入 Pinia 安装顺序问题。
- 路由守卫会在进入受保护页面前解析 JWT `exp` 字段，发现过期 token 时主动清理本地登录态，避免旧 token 把根路径带入不可用的聊天页。
- 根路径 `/` 会根据本地 token 是否有效决定进入 `/chat` 或 `/login`，降低旧浏览器状态残留造成白屏的概率。
- 登录接口成功后，前端会先保存 JWT 和基础用户信息，再拉取完整 profile；这样资料接口短暂异常时不会阻断用户进入系统。
- 如果 profile 返回 401，说明 token 无效或已过期，前端会清理登录态并停留在登录页。
- 后续如果要做更严格的登录态校验，可以在受保护布局加载时调用 `userStore.fetchProfile()`，失败则退出登录。
- Token 只存储在浏览器 `localStorage`，适合当前 MVP；如果后续需要更高安全等级，可改为 HttpOnly Cookie 或增加刷新令牌机制。
- 前端只负责展示后端返回的错误消息，不在浏览器侧推断用户名重复、密码错误等业务原因。

## 前端聊天模块

Phase 11 在前端补齐聊天核心功能，包括 Chat API、Chat Store、消息气泡、输入区、会话列表、聊天主页和 WebSocket 订阅。该模块对接后端 Phase 6 的 `/api/chat/*` REST 接口，并预留 STOMP 实时消息通道。

```mermaid
flowchart LR
    ChatView["ChatView"]
    SessionList["SessionList"]
    MessageBubble["MessageBubble"]
    InputArea["InputArea"]
    ChatStore["chatStore"]
    ChatApi["chatApi"]
    Axios["Axios request"]
    Backend["Spring Boot /api/chat"]
    Stomp["SockJS + STOMP"]
    Topic["/topic/chat/{sessionId}"]

    ChatView --> SessionList
    ChatView --> MessageBubble
    ChatView --> InputArea
    SessionList --> ChatView
    InputArea --> ChatView
    ChatView --> ChatStore
    ChatStore --> ChatApi
    ChatApi --> Axios
    Axios --> Backend
    ChatStore --> Stomp
    Stomp --> Topic
    Topic --> ChatStore
```

API 层职责：

- `api/chatApi.ts` 封装 `startSession`、`sendMessage`、`getHistory`、`getSessions`、`deleteSession`。
- 所有请求复用 `api/index.ts` 的 Axios 实例，因此会自动携带 `Authorization: Bearer <token>`。
- HTTP 发送消息调用 `POST /api/chat/message`，当前后端会保存用户消息并返回固定 AI 回复。

Store 职责：

- `stores/chatStore.ts` 维护当前会话、会话列表、消息列表、加载状态、发送状态和 WebSocket 连接状态。
- `startNewSession` 创建会话后设置当前会话，并建立 WebSocket 订阅。
- `switchSession` 根据会话 ID 加载历史消息，并切换 WebSocket 订阅目标。
- `sendMessage` 会先添加乐观用户消息，再调用后端发送接口，最后追加 AI 回复。
- `deleteSession` 删除会话后同步本地列表；如果删除的是当前会话，会清空消息并断开 WebSocket。
- `connectWebSocket` 使用 SockJS 连接 `/ws/chat`，并通过 STOMP header 携带 JWT。

组件职责：

- `SessionList` 负责展示会话、创建入口、切换事件和删除事件。
- `MessageBubble` 负责用户/AI 气泡展示、Markdown 渲染和时间格式化。
- `InputArea` 负责输入框、发送按钮、Enter 发送、Shift+Enter 换行和发送 loading。
- `ChatView` 负责把路由参数、Store、会话列表、消息区和输入区编排成完整聊天页面。

聊天流：

```mermaid
sequenceDiagram
    participant User as 用户
    participant View as ChatView
    participant Store as chatStore
    participant Api as chatApi/Axios
    participant Backend as 后端 ChatController
    participant Ws as STOMP Topic

    User->>View: 点击新建会话
    View->>Store: startNewSession()
    Store->>Backend: POST /api/chat/start
    Backend-->>Store: ChatSessionResponse
    Store->>Ws: 订阅 /topic/chat/{sessionId}
    User->>View: 输入并发送消息
    View->>Store: sendMessage(content)
    Store->>View: 追加乐观用户消息
    Store->>Backend: POST /api/chat/message
    Backend-->>Store: MessageResponse(AI)
    Store->>View: 追加 AI 回复
```

WebSocket 约定：

- 连接端点为 `/ws/chat`，通过 Vite 代理转发到后端 `8085`。
- 订阅目标为 `/topic/chat/{sessionId}`。
- STOMP `CONNECT` header 携带 `Authorization: Bearer <token>`，与后端 WebSocket 控制器解析逻辑一致。
- 当前前端发送消息走 HTTP，WebSocket 主要用于接收服务端推送；这样避免 HTTP 和 WebSocket 双写造成消息重复。

设计约束：

- 当前 AI 回复仍来自后端固定字符串，符合 MVP 阶段边界。
- 前端乐观消息使用负数临时 ID，只存在于当前页面内；重新加载历史时以后端消息为准。
- Markdown 渲染关闭 HTML，降低用户输入造成 XSS 的风险。
- WebSocket 断线不会阻塞 HTTP 聊天流程，页面会显示 `HTTP 模式`，发送和历史查询仍可用。
- 聊天页初始化加载会话失败时会保留页面框架，并显示错误提示和重新加载入口，避免接口异常或登录态问题表现为纯白屏。
- 开发服务通过 Vite `server.headers` 返回 `Cache-Control: no-store`，减少开发阶段浏览器复用旧模块造成的前端状态错乱。

## 前端漫剧模块

Phase 12 补齐前端漫剧模块，围绕后端 Phase 7 的 `/api/story/*` REST 接口实现漫剧列表、详情、编辑、删除，以及从聊天会话触发剧情大纲生成。

```mermaid
flowchart LR
    ChatView["ChatView"]
    StoryList["StoryList"]
    StoryDetail["StoryDetailView"]
    CharacterCard["CharacterCard"]
    StoryStore["storyStore"]
    StoryApi["storyApi"]
    Axios["Axios request"]
    Backend["Spring Boot /api/story"]

    ChatView --> StoryStore
    StoryList --> StoryStore
    StoryDetail --> StoryStore
    StoryDetail --> CharacterCard
    StoryStore --> StoryApi
    StoryApi --> Axios
    Axios --> Backend
```

API 层职责：

- `api/storyApi.ts` 封装 `getStories`、`getStoryDetail`、`generateStory`、`updateStory`、`deleteStory`。
- 所有 story 请求复用统一 Axios 实例，自动携带 JWT，并沿用 401 统一跳转登录逻辑。
- `GET /api/story/list` 读取 MyBatis-Plus 分页对象，前端从 `records/current/size/total/pages` 维护分页状态。

Store 职责：

- `stores/storyStore.ts` 维护 `stories`、`currentStory`、`pagination`、`isLoading`、`isGenerating`。
- `fetchStories` 同步列表和分页状态，并对异常结构兜底为空数组。
- `fetchStoryDetail` 加载当前详情，包括 `fullContent` 和主要角色列表。
- `generateStory` 调用后端剧情大纲生成接口，并把新草稿插入本地列表顶部。
- `updateStory` 更新列表和当前详情，`deleteStory` 删除列表项并清理当前详情。

页面与组件职责：

- `StoryList` 使用卡片网格展示漫剧，支持当前页内标题/摘要搜索、类型筛选、状态筛选、分页和删除确认。
- `StoryDetailView` 展示标题、类型、风格、状态、故事摘要、剧情大纲和主要角色卡片，并提供编辑、删除和大纲修改入口。
- `CharacterCard` 负责角色头像占位、名称、角色定位、描述、性格和外观字段展示。
- `ChatView` 在当前会话存在时提供“生成剧情大纲”按钮，点击后要求输入题材和可选剧情，再调用后端生成接口。

生成流：

```mermaid
sequenceDiagram
    participant User as 用户
    participant Chat as ChatView
    participant Store as storyStore
    participant Api as storyApi/Axios
    participant Backend as StoryController
    participant Router as Vue Router

    User->>Chat: 点击生成剧情大纲
    Chat->>Chat: 输入题材和可选剧情
    Chat->>Store: generateStory(sessionId, genre, plot)
    Store->>Api: POST /api/story/generate
    Api->>Backend: sessionId + genre + plot
    Backend-->>Store: StoryResponse
    Store-->>Chat: 新剧情大纲草稿
    Chat->>Chat: 追加本地 AI 提示消息
    Chat->>Router: 跳转 /story/{id}
```

设计约束：

- 当前列表筛选为前端当前页筛选，因为后端 Phase 7 接口尚未提供搜索、类型、状态查询参数。
- 当前封面生成为预留按钮，真实图片生成需要后续 AI 图片能力或文件上传流程接入。
- 后端 `StoryResponse` 当前未返回 `updatedAt`，列表暂以 `createdAt` 展示时间；若后续需要精确更新时间，可以扩展后端 DTO。
- `/api/story/generate` 现在语义为生成剧情大纲，后端会调用 Python FastAPI 的 `/api/story/outline`。
- Java 后端通过 `AiEngineClient` 隔离 Python 调用细节；Python 真实生成逻辑后续只需要替换 `python-ai/main.py` 中的占位实现。

## Python AI 引擎调用骨架

当前 Java 后端已经搭建了调用 FastAPI 的函数边界，用于后续接入 LangChain 或其他 Python 生成逻辑。

```mermaid
flowchart LR
    ChatView["ChatView 大纲表单"]
    StoryController["StoryController /api/story/generate"]
    StoryService["StoryServiceImpl"]
    AiClient["AiEngineClient"]
    FastApi["FastAPI /api/story/outline"]
    MySQL["MySQL stories"]

    ChatView --> StoryController
    StoryController --> StoryService
    StoryService --> AiClient
    AiClient --> FastApi
    FastApi --> AiClient
    StoryService --> MySQL
```

接口契约：

- Java 对外接口仍为 `POST /api/story/generate`，请求体为 `sessionId`、`genre`、`plot`。
- Java 到 Python 的接口为 `POST ${ai.engine.base-url}${ai.engine.story-outline-path}`，默认 `http://localhost:5000/api/story/outline`。
- Python 请求字段为 `userId`、`sessionId`、`sessionTitle`、`genre`、`plot`。
- Python 内部使用 `with_structured_output(NovelOutlineOutput)` 约束模型输出，固定生成 `novel_name`、`story_summary`、`outline`、`main_characters`。
- Python 对 Java 的 HTTP 响应映射为 `novelName`、`storySummary`、`outline`、`mainCharacters`。
- Java 保存时将 `novelName` 写入 `stories.title`，将请求题材写入 `stories.genre`，将 `storySummary` 写入 `stories.synopsis`，将 `outline` 写入 `stories.full_content`，将 `mainCharacters` 写入 `characters` 表。
- Tongyi API Key 从 `python-ai/api.yml` 的 `tongyi.api_key` 读取，启动时写入 `DASHSCOPE_API_KEY` 环境变量。

设计约束：

- Java 后端负责用户鉴权、会话归属校验、保存剧情大纲草稿。
- Python FastAPI 负责生成剧情大纲内容，当前 `python-ai/main.py` 已搭好 LangChain `ChatTongyi.with_structured_output()` 调用链。
- 如果 FastAPI 服务未启动或接口未实现，Java 会返回清晰错误：`Python 剧情大纲接口暂不可用`。
- `python-ai/api.yml` 是本地密钥文件，已加入 `.gitignore`，不要提交到仓库。

大纲修改链路：

- 前端详情页提供“大纲修改”按钮，用户输入修改意见后调用 `POST /api/story/{id}/outline/revise`。
- Java 后端会校验 story 归属，将原始标题、摘要、大纲和修改意见转发给 Python FastAPI。
- Python FastAPI 暂提供 `/api/story/outline/revise` 占位路由，后续在该路由中替换为真实 LangChain 修订链。
- Java 收到修订响应后更新 `stories.synopsis`、`stories.full_content`，如返回新的主要角色，则替换 `characters` 表中的角色设定。

## 前端用户中心模块

Phase 13 补齐用户中心页面，将用户资料、头像上传、账号编辑和创作统计统一到 `/user`。该模块复用 Phase 10 的认证 Store、Phase 11 的聊天 Store、Phase 12 的漫剧 Store，并通过文件 API 接入后端 Phase 8 的本地文件存储。

```mermaid
flowchart LR
    UserCenter["UserCenter"]
    UserStore["userStore"]
    StoryStore["storyStore"]
    ChatStore["chatStore"]
    FileApi["fileApi"]
    AuthApi["authApi"]
    Axios["Axios request"]
    BackendUser["/api/user/profile"]
    BackendFile["/api/files"]
    BackendStory["/api/story/list"]
    BackendChat["/api/chat/sessions"]

    UserCenter --> UserStore
    UserCenter --> StoryStore
    UserCenter --> ChatStore
    UserCenter --> FileApi
    UserStore --> AuthApi
    AuthApi --> Axios
    FileApi --> Axios
    StoryStore --> Axios
    ChatStore --> Axios
    Axios --> BackendUser
    Axios --> BackendFile
    Axios --> BackendStory
    Axios --> BackendChat
```

页面职责：

- `UserCenter` 展示头像、用户名、邮箱、注册时间、用户 ID、头像路径、漫剧数量和对话数量。
- 页面初始化会调用 `userStore.fetchProfile()` 拉取最新用户资料。
- 漫剧统计复用 `storyStore.fetchStories(1, 1)` 的分页总数，避免为统计额外新增后端接口。
- 对话统计复用 `chatStore.loadSessions()` 的列表长度。
- 编辑弹窗通过 `userStore.updateProfile()` 更新用户名和邮箱，并同步 `localStorage.userInfo`。

头像上传职责：

- `api/fileApi.ts` 封装 `uploadFile(file, category)` 和 `loadFileBlob(filePathOrUrl)`。
- 头像上传使用 Element Plus `el-upload`，分类固定为 `avatars`。
- 上传成功后调用 `userStore.updateProfile({ avatarPath: fileUrl })`，把后端返回的文件 URL 写入用户资料。
- 因 `/api/files/**` 当前仍受 JWT 保护，头像预览不会直接使用 `<img src>` 拉取，而是通过 Axios 携带 Authorization 获取 Blob，再转换为 `objectURL` 展示。

设计约束：

- 统计卡片以现有接口聚合为主，不新增后端统计接口；如果后续数据量增大，可以新增 `/api/user/stats` 汇总接口。
- 头像只允许图片类型且前端限制 10MB，后端仍会执行扩展名、Content-Type、路径和大小校验。
- 如果头像文件不存在或访问失败，页面回退到文字头像，不阻断用户中心渲染。
- 文件 Blob 预览在组件卸载或头像变更时会主动 `URL.revokeObjectURL`，避免浏览器内存泄漏。

## 剧情大纲生成等待与进度输出

剧情大纲生成链路需要适配大模型调用耗时较长的特点，因此 Java 到 Python FastAPI 的调用只限制连接建立时间，读取响应不设置上限。这样 Python 服务已经接收请求并开始生成后，Java 后端会一直等待最终结构化 JSON 返回，不会因为生成时间长而主动超时中断。

Python `generate_story_outline` 继续使用 `with_structured_output(NovelOutlineOutput)` 约束输出结构，固定返回 `novelName`、`storySummary`、`outline`、`mainCharacters`。同时 Python 控制台会通过 LangChain callback 和阶段日志打印请求接收、模型调用开始、流式 token、模型完成、响应封装等进度；如果当前 Tongyi structured output 链路没有透出 token callback，仍会保留阶段级进度日志，不影响最终结果返回。

## 手动编辑与分卷大纲模块

剧情大纲详情现在拆为三个主要内容区：`stories.full_content` 保存剧情大纲，`characters` 表保存主要角色设定，`story_volume_outlines` 表保存分卷大纲。`stories` 与 `story_volume_outlines` 是一对多关系，一个故事可以拥有多个分卷大纲记录。前端详情页提供“手动修改大纲/角色”弹窗，用户可以基于现有文本直接修改故事摘要、剧情大纲和角色设定；保存时调用 `PUT /api/story/{id}/detail`，Java 后端校验 story 归属后更新 `stories.synopsis`、`stories.full_content`，并用提交的角色列表整体替换当前 story 的 `characters` 数据。

分卷大纲生成由前端详情页“分卷大纲生成”按钮触发，调用 `POST /api/story/{id}/volume-outline/generate`。Java 后端读取当前 story 的标题、摘要、剧情大纲和角色设定，转发给 Python FastAPI `/api/story/volume-outline`。Python 侧通过 LangChain `with_structured_output(VolumeOutlineOutput)` 固定返回 `volumes` 数组，提示词要求每卷尽可能提供更多剧情细节、冲突、反转、角色选择、情绪钩子和卷末悬念。Java 收到结果后会替换当前 story 的旧分卷，并逐条写入 `story_volume_outlines` 表，再返回最新详情给前端展示。

## 对话绑定漫剧

每个新建对话都必须绑定一个 story。前端在新建对话时会先读取漫剧列表，用户选择一个已有漫剧后再调用 `POST /api/chat/start`，请求体包含 `storyId` 和可选 `title`。后端在 `ChatService.startSession` 中校验 story 归属后，把 `stories.id` 写入 `chat_sessions.story_id`，前端 `ChatSessionResponse` 会拿到 `storyId` 并在聊天页显示“查看绑定漫剧”入口。这样聊天不再只是孤立消息流，而是围绕一部固定漫剧持续迭代。

剧情大纲生成现在以“会话绑定 story”为写入目标。`POST /api/story/generate` 会先校验会话归属，然后读取 `chat_sessions.story_id` 对应的 story，并把 Python 返回的小说名、故事摘要、剧情大纲和主要角色设定写回这个绑定 story。用户可以在同一个对话里随时重新生成剧情大纲，结果会覆盖并更新绑定 story，而不是创建新的 story。

对话内修改也作用于绑定 story。`POST /api/chat/message` 保存用户消息后，会调用 Python `/api/chat/agent` 做问题重写、路由分发和方法参数生成；Java 再按白名单执行对应方法，将结果同步保存到绑定 story。

## 对话 Agent 调度链

对话消息采用 Java 接入、Python 决策、Java 执行的工具调用模式。前端发送 `POST /api/chat/message` 后，Java 后端先校验当前用户、会话归属和绑定 story，再保存用户消息。随后 Java 通过 `AiEngineClient.runChatAgent` 调用 Python FastAPI `/api/chat/agent`。

Python Agent 会完成问题重写、路由分发和大模型结构化输出，固定返回 `rewrittenQuestion`、`route`、`javaMethod`、`javaMethodArgs`、`assistantMessage`。其中 `javaMethod` 表示希望 Java 执行的方法，`javaMethodArgs` 是对应参数。当前白名单支持 `story.updateOutline` 和 `story.none`：前者更新绑定漫剧的摘要、剧情大纲和主要角色设定，后者只返回回复不改数据库。

Java 不会执行 Python 返回的任意方法名，而是通过白名单分发。这样 Python 负责理解用户意图和组织参数，Java 负责权限校验、数据库写入和业务一致性。后续如果要加入分卷大纲生成、章节生成、角色增删等能力，只需要同时扩展 Python Agent 提示词和 Java 白名单方法。
