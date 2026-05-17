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
- `StoryDetailResponse` 继承 `StoryResponse`，扩展全文、角色列表和场景列表，服务漫剧详情页。

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
