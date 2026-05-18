# 项目进度记录

## 2026-05-16 Phase 1：项目脚手架与基础设施

### Step 1.1 创建后端 Maven 项目

- [x] 在 `F:\ai\aisay\backend` 创建 Spring Boot 3.2.12 Maven 项目骨架。
- [x] 在 `backend/pom.xml` 配置阶段一要求的核心依赖：Web、Security、WebSocket、MyBatis-Plus、MySQL、JJWT、Redis、AMQP、Knife4j、Lombok、Commons Lang3。
- [x] 创建主启动类 `com.aisay.manga.AisayMangaApplication`。
- [x] 创建 `backend/src/main/resources/application.yml`，服务端口为 `8085`。
- [x] 根据 `config.txt` 写入 MySQL、Redis、RabbitMQ 连接信息；当前 MySQL 使用 `config.txt` 中的 `springcloud` 数据库连接串。
- [x] 创建后端包结构：`controller`、`service`、`service.impl`、`repository`、`entity`、`dto.request`、`dto.response`、`config`、`utils`。
- [x] 新增 `settings.phase1.xml`，用于将 Maven 本地仓库放在项目内 `backend/mvn_repo`，避免写入全局 Maven 目录。

验证结果：

- [x] `mvn -gs ..\settings.phase1.xml compile` 成功。
- [x] `mvn -gs ..\settings.phase1.xml "-Dspring-boot.run.arguments=--spring.main.web-application-type=none" spring-boot:run` 成功，Spring Boot 应用上下文正常启动。
- [x] 启动日志已出现 Spring Boot、Redis repository 扫描、MyBatis-Plus 标识。

说明：

- 首次直接执行 Maven 时，全局 Maven 仓库目录 `D:\software\maven\apache-maven-3.9.4\mvn_repo` 无写入权限；已通过项目级 `settings.phase1.xml` 规避。
- 后端当前没有 Mapper，启动日志出现 `No MyBatis mapper was found` 属于阶段一预期，后续 Phase 3 增加 Mapper 后会消失。

### Step 1.2 创建前端 Vite + Vue 3 项目

- [x] 在 `F:\ai\aisay\frontend` 创建 Vite + Vue 3 + TypeScript 项目骨架。
- [x] 配置依赖：Vue Router、Pinia、Axios、Element Plus、Dayjs、Markdown-it、SockJS Client、STOMP.js。
- [x] 配置开发依赖：Vite、Vue 插件、TypeScript、Vue TSC、Sass。
- [x] 配置 `frontend/vite.config.ts`：开发端口 `8080`，`/api` 代理到 `http://localhost:8085`，`/ws` 代理到 `ws://localhost:8085`。
- [x] 创建前端目录结构：`views`、`components/chat`、`components/story`、`components/common`、`stores`、`api`、`router`、`types`、`styles`。
- [x] 创建 `src/main.ts`，完成 App、Element Plus、Pinia、Vue Router 挂载。
- [x] 创建 `src/App.vue`，提供基础 `<router-view />` 布局。
- [x] 创建 `src/router/index.ts`，当前为空路由表，等待后续阶段逐步填充。

验证结果：

- [x] `npm install --cache .\.npm-cache` 成功，生成 `package-lock.json` 和本地依赖。
- [x] `npm run build` 成功，TypeScript 与 Vite 构建通过。
- [x] `npm run dev -- --host 127.0.0.1` 短时烟测成功，Vite 在 `http://127.0.0.1:8080/` 就绪。

说明：

- `npm install` 报告 2 个 moderate 级别依赖审计提示，阶段一未执行强制升级，避免破坏锁定依赖树；后续可单独安排 `npm audit` 处理。
- `npm run build` 输出了 Element Plus 体积导致的 chunk size 提示，属于当前全量引入 UI 库的预期提示，后续可通过按需引入或分包优化。

### 阶段一完成状态

- [x] 后端脚手架完成。
- [x] 前端脚手架完成。
- [x] 基础配置完成。
- [x] 编译与启动烟测完成。
- [x] 进度文档已记录。
- [x] 架构文档已补充。

## 2026-05-16 Phase 2：数据库初始化

### Step 2.1 创建 MySQL 数据库与表结构

- [x] 创建 `backend/src/main/resources/db/init.sql`，用于手动初始化数据库。
- [x] SQL 默认使用 `config.txt` 和 `application.yml` 中的 `springcloud` 数据库。
- [x] SQL 中包含 `CREATE DATABASE IF NOT EXISTS springcloud CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`。
- [x] SQL 中包含实现计划要求的业务用户授权：`aisay` / `aisay_pass`，授权范围为 `springcloud.*`。
- [x] SQL 中包含 7 张核心表：`users`、`chat_sessions`、`messages`、`stories`、`characters`、`scenes`、`dialogues`。
- [x] 表结构包含主键、唯一键、常用索引、JSON 字段、外键约束和级联删除规则。

验证结果：

- [x] SQL 文件已生成。
- [ ] 未执行 SQL，按用户要求由用户自行执行。
- [ ] 未执行 `SHOW TABLES FROM springcloud;`，等待用户执行 SQL 后验证。

说明：

- 设计文档和实现计划示例库名为 `aisay_manga`，但 `config.txt` 提供的连接串指向 `springcloud`；本阶段选择与当前运行配置保持一致。
- 如果后续决定改用 `aisay_manga`，需要同时替换 `init.sql` 中库名和 `backend/src/main/resources/application.yml` 的 datasource url。

### Step 2.2 配置 MyBatis-Plus 连接

- [x] 在 `application.yml` 中新增 `mybatis-plus.mapper-locations`。
- [x] 在 `application.yml` 中新增 `mybatis-plus.type-aliases-package`，指向 `com.aisay.manga.entity`。
- [x] 在 `application.yml` 中启用下划线转驼峰映射。
- [x] 在 `application.yml` 中配置全局主键类型和逻辑删除值。
- [x] 创建 `com.aisay.manga.config.MyBatisPlusConfig`，注册 `MybatisPlusInterceptor`。
- [x] 在 `MyBatisPlusConfig` 中加入 MySQL 分页插件 `PaginationInnerInterceptor`。
- [x] 在 `MyBatisPlusConfig` 中加入乐观锁插件 `OptimisticLockerInnerInterceptor`。
- [x] 创建 `com.aisay.manga.config.RedisConfig`。
- [x] 在 `RedisConfig` 中配置 `RedisTemplate<String, Object>`，key 使用字符串序列化，value 使用 JSON 序列化。
- [x] 创建 `backend/src/main/resources/mapper/.gitkeep`，保留 XML Mapper 目录。

验证结果：

- [x] `mvn -gs ..\settings.phase1.xml compile` 成功。
- [x] `mvn -gs ..\settings.phase1.xml "-Dspring-boot.run.arguments=--spring.main.web-application-type=none" spring-boot:run` 成功，Spring Boot 应用上下文正常启动。
- [x] 启动日志出现 MyBatis-Plus 3.5.5 标识和 Redis repository 扫描日志。

说明：

- 当前还没有 Entity 和 Mapper，启动日志中的 `No MyBatis mapper was found` 仍属于预期，Phase 3 创建 Mapper 后会自然消失。
- 本阶段未实际连接并查询 MySQL 表，因为 SQL 按要求未由我执行。

## 2026-05-16 Phase 3：后端实体与数据访问层

### Step 3.1 创建所有 Entity 类

- [x] 创建 `User.java`，映射 `users` 表，主键使用 `@TableId(type = IdType.AUTO)`。
- [x] 创建 `ChatSession.java`，映射 `chat_sessions` 表，`contextData` 使用 `JacksonTypeHandler`。
- [x] 创建 `Message.java`，映射 `messages` 表，`metadata` 使用 `JacksonTypeHandler`。
- [x] 创建 `Story.java`，映射 `stories` 表。
- [x] 创建 `Character.java`，映射 `characters` 表，`appearance` 使用 `JacksonTypeHandler`。
- [x] 创建 `Scene.java`，映射 `scenes` 表，`visualElements` 使用 `JacksonTypeHandler`。
- [x] 创建 `Dialogue.java`，映射 `dialogues` 表，`order` 字段使用反引号列名映射。
- [x] 所有 Entity 使用 Lombok `@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`。
- [x] 为 `Character.java` 添加 MyBatis `@Alias("MangaCharacter")`，避免与 `java.lang.Character` 类型别名冲突。

验证结果：

- [x] `mvn -gs ..\settings.phase1.xml compile` 成功。

### Step 3.2 创建 Mapper 接口

- [x] 创建 `UserMapper.java`，继承 `BaseMapper<User>`。
- [x] 创建 `ChatSessionMapper.java`，继承 `BaseMapper<ChatSession>`，并提供按 `userId + status` 查询的方法。
- [x] 创建 `MessageMapper.java`，继承 `BaseMapper<Message>`，并提供按 `sessionId` 与创建时间升序查询的方法。
- [x] 创建 `StoryMapper.java`，继承 `BaseMapper<Story>`，并提供按 `userId` 分页查询的方法。
- [x] 创建 `CharacterMapper.java`，继承 `BaseMapper<Character>`。
- [x] 创建 `SceneMapper.java`，继承 `BaseMapper<Scene>`。
- [x] 创建 `DialogueMapper.java`，继承 `BaseMapper<Dialogue>`。
- [x] 在 `AisayMangaApplication` 添加 `@MapperScan("com.aisay.manga.repository")`。
- [x] 创建 `UserMapperTest`，用于 MySQL insert + select + delete 集成验证。

验证结果：

- [x] `mvn -gs ..\settings.phase1.xml compile` 成功。
- [x] `mvn -gs ..\settings.phase1.xml test` 成功；`UserMapperTest` 默认跳过，避免数据库未初始化时误失败。
- [x] `mvn -gs ..\settings.phase1.xml "-Dspring-boot.run.arguments=--spring.main.web-application-type=none" spring-boot:run` 成功，Spring Boot 应用上下文正常启动。
- [x] 启动日志不再出现 `No MyBatis mapper was found`。

需要你做的事：

- [ ] 如果还没有执行 Phase 2 的 SQL，请先在 MySQL 中执行 `backend/src/main/resources/db/init.sql`。
- [ ] 执行 SQL 后，建议在 MySQL 中运行 `SHOW TABLES FROM springcloud;`，确认 7 张表都已存在。
- [ ] 数据库表确认存在后，可在 `backend` 目录运行 `mvn -gs ..\settings.phase1.xml "-Daisay.integration.mysql=true" test`，开启真实 MySQL 版 `UserMapperTest`。
- [ ] 如果你决定把库名从当前 `springcloud` 切换为设计文档中的 `aisay_manga`，请同步修改 `backend/src/main/resources/db/init.sql` 和 `backend/src/main/resources/application.yml`。

说明：

- `UserMapperTest` 默认使用 `@EnabledIfSystemProperty` 保护，只有显式传入 `-Daisay.integration.mysql=true` 才会访问真实 MySQL。
- 当前阶段只实现数据模型和访问层，不包含 DTO、Service、Controller、认证和业务接口；这些属于 Phase 4 之后。

## 2026-05-16 Phase 4：后端 DTO 层

### Step 4.1 创建请求 DTO

- [x] 在 `pom.xml` 中新增 `spring-boot-starter-validation`，用于支持 Jakarta Bean Validation。
- [x] 创建 `RegisterRequest.java`：`username`、`email`、`password`，包含非空、邮箱格式和长度校验。
- [x] 创建 `LoginRequest.java`：`username`、`password`，包含非空和长度校验。
- [x] 创建 `ChatStartRequest.java`：`title` 可选，包含最大长度校验。
- [x] 创建 `SendMessageRequest.java`：`sessionId`、`content`，包含非空和消息长度校验。
- [x] 创建 `StoryGenerateRequest.java`：`sessionId`，包含非空校验。
- [x] 创建 `StoryUpdateRequest.java`：`title`、`genre`、`style`、`synopsis` 均可选，包含长度校验。
- [x] 创建 `UserUpdateRequest.java`：`username`、`email`、`avatarPath` 均可选，包含长度和邮箱格式校验。

验证结果：

- [x] `mvn -gs ..\settings.phase1.xml compile` 成功。

### Step 4.2 创建响应 DTO

- [x] 创建 `ApiResponse<T>.java`：统一响应体 `code`、`message`、`data`、`timestamp`，并提供 `success` / `fail` 静态工厂方法。
- [x] 创建 `LoginResponse.java`：`token`、`userId`、`username`。
- [x] 创建 `ChatSessionResponse.java`：`id`、`sessionKey`、`title`、`status`、`startedAt`、`lastActive`。
- [x] 创建 `MessageResponse.java`：`id`、`sessionId`、`role`、`content`、`createdAt`。
- [x] 创建 `StoryResponse.java`：`id`、`title`、`genre`、`style`、`synopsis`、`status`、`coverImagePath`、`createdAt`。
- [x] 创建 `StoryDetailResponse.java`：继承 `StoryResponse`，补充 `fullContent`、`characters`、`scenes`。
- [x] 在 `StoryDetailResponse` 中定义 `CharacterItem` 与 `SceneItem`，用于承载详情页所需的角色和场景数据。
- [x] 创建 `UserProfileResponse.java`：`id`、`username`、`email`、`avatarPath`、`createdAt`。

验证结果：

- [x] `mvn -gs ..\settings.phase1.xml compile` 成功。
- [x] `mvn -gs ..\settings.phase1.xml test` 成功；真实 MySQL 版 `UserMapperTest` 仍默认跳过。
- [x] `mvn -gs ..\settings.phase1.xml "-Dspring-boot.run.arguments=--spring.main.web-application-type=none" spring-boot:run` 成功，Spring Boot 应用上下文正常启动。

需要你做的事：

- [ ] Phase 4 本身不需要你手动操作数据库或中间件。
- [ ] 如果还没执行 Phase 2 的 SQL，仍建议先执行 `backend/src/main/resources/db/init.sql`，否则 Phase 5 之后的真实接口测试会因为表不存在而失败。
- [ ] 如果想提前验证真实 MySQL Mapper，请在确认表存在后运行 `mvn -gs ..\settings.phase1.xml "-Daisay.integration.mysql=true" test`。

说明：

- DTO 只负责 API 入参与出参承载，不直接依赖 Mapper，也不写业务逻辑。
- 请求 DTO 当前只包含基础格式校验；用户名/邮箱唯一性、会话归属、故事归属等业务校验会放在后续 Service 层。

## 2026-05-16 Phase 5：后端安全与认证

### Step 5.1 JWT 工具类

- [x] 创建 `utils/JwtUtil.java`。
- [x] 实现 `generateToken(Long userId, String username)`，JWT 中包含 `userId`、`username`、签发时间和过期时间。
- [x] 实现 `validateToken(String token)`，非法或过期 token 返回 `false`。
- [x] 实现 `getUserIdFromToken(String token)`。
- [x] 实现 `getUsernameFromToken(String token)`。
- [x] 在 `application.yml` 中新增 `jwt.secret` 和 `jwt.expiration`，默认有效期 24 小时。
- [x] 创建 `JwtUtilTest`，覆盖 token 生成、验证和解析。

验证结果：

- [x] `mvn -gs ..\settings.phase1.xml test` 成功，`JwtUtilTest` 通过。

### Step 5.2 Spring Security 配置

- [x] 创建 `config/SecurityConfig.java`。
- [x] 禁用 CSRF、表单登录和 HTTP Basic。
- [x] 配置无状态会话 `SessionCreationPolicy.STATELESS`。
- [x] 放行 `/api/auth/**`、`/doc.html`、`/v3/api-docs/**`、`/webjars/**`、`/swagger-ui/**`、`/ws/**`。
- [x] 其余接口均要求认证。
- [x] 添加 JSON 格式 401 响应，返回统一 `ApiResponse`。
- [x] 创建 `JwtAuthenticationFilter.java`，从 `Authorization: Bearer <token>` 提取 JWT，验证后写入 `SecurityContext`。
- [x] 创建 `CorsConfig.java`，允许 `http://localhost:8080` 和 `http://127.0.0.1:8080` 跨域。
- [x] 提供 `PasswordEncoder` Bean，使用 BCrypt。
- [x] 提供占位 `UserDetailsService` Bean，避免 Spring Security 自动生成默认开发密码。
- [x] 创建 `SecurityUtils.java`，用于从当前认证上下文读取用户 ID。
- [x] 创建 `SecurityConfigTest`，验证无 token 访问 `/api/user/profile` 返回 401。

验证结果：

- [x] `mvn -gs ..\settings.phase1.xml test` 成功，`SecurityConfigTest` 通过。
- [x] `mvn -gs ..\settings.phase1.xml "-Dspring-boot.run.arguments=--spring.main.web-application-type=none" spring-boot:run` 成功，Spring Boot 应用上下文正常启动。
- [x] 启动日志显示 `JwtAuthenticationFilter` 已进入 Spring Security FilterChain。
- [x] 启动日志不再出现 Spring Security 生成默认密码的提示。

### Step 5.3 用户注册与登录

- [x] 创建 `service/UserService.java`，定义 `register`、`login`、`getProfile`、`updateProfile`。
- [x] 创建 `service/impl/UserServiceImpl.java`。
- [x] 注册逻辑：校验 username/email 唯一性，BCrypt 加密密码，保存用户，返回 profile。
- [x] 登录逻辑：按 username 查询用户，BCrypt 校验密码，生成 JWT，返回 `LoginResponse`。
- [x] 获取 profile：按当前认证用户 ID 查询并返回 `UserProfileResponse`。
- [x] 更新 profile：支持修改 username、email、avatarPath，并做唯一性校验。
- [x] 创建 `controller/UserController.java`。
- [x] 实现 `POST /api/auth/register`。
- [x] 实现 `POST /api/auth/login`。
- [x] 实现 `GET /api/user/profile`，需要认证。
- [x] 实现 `PUT /api/user/profile`，需要认证。

验证结果：

- [x] `mvn -gs ..\settings.phase1.xml compile` 成功。
- [x] `mvn -gs ..\settings.phase1.xml test` 成功；总计 5 个测试，4 个执行通过，1 个真实 MySQL Mapper 测试默认跳过。
- [x] `mvn -gs ..\settings.phase1.xml "-Dspring-boot.run.arguments=--spring.main.web-application-type=none" spring-boot:run` 成功。
- [ ] 未执行真实注册/登录 curl 联调，因为该验证依赖你已执行 Phase 2 的 `init.sql` 并确认 `users` 表存在。

需要你做的事：

- [ ] 如果还没有执行 Phase 2 的 SQL，请先在 MySQL 中执行 `backend/src/main/resources/db/init.sql`。
- [ ] 执行 SQL 后，运行 `SHOW TABLES FROM springcloud;`，确认包含 `users` 等 7 张表。
- [ ] 启动后端：在 `backend` 目录运行 `mvn -gs ..\settings.phase1.xml spring-boot:run`。
- [ ] 注册测试用户：
  `curl -X POST http://localhost:8085/api/auth/register -H "Content-Type: application/json" -d "{\"username\":\"test\",\"email\":\"test@test.com\",\"password\":\"123456\"}"`
- [ ] 登录获取 token：
  `curl -X POST http://localhost:8085/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"test\",\"password\":\"123456\"}"`
- [ ] 使用登录返回的 token 获取 profile：
  `curl http://localhost:8085/api/user/profile -H "Authorization: Bearer <token>"`
- [ ] 生产或长期运行前，请把 `application.yml` 中的 `jwt.secret` 改成只在本地保存的强随机密钥。

说明：

- 当前还没有 Phase 8 的全局异常处理，因此参数校验、用户名重复、密码错误等错误响应会在后续阶段统一美化。
- Phase 5 已经完成认证主链路，后续聊天、漫剧、用户中心接口可以通过 `SecurityUtils.getCurrentUserId()` 获取当前用户 ID。

## 2026-05-16 Phase 6：后端聊天服务

### Step 6.1 聊天会话管理

- [x] 创建 `service/ChatService.java`。
- [x] 创建 `service/impl/ChatServiceImpl.java`。
- [x] 实现 `startSession(Long userId, ChatStartRequest request)`，生成 UUID `sessionKey`，初始化 `status=active`、`currentStage=INITIAL`、`progressPercentage=0`。
- [x] 实现 `getHistory(Long sessionId, Long userId)`，校验会话归属后按创建时间升序返回消息。
- [x] 实现 `deleteSession(Long sessionId, Long userId)`，校验会话归属后将会话状态改为 `deleted`。
- [x] 实现 `getUserSessions(Long userId)`，按 `lastActive` 倒序返回未删除会话。
- [x] 创建 `controller/ChatController.java`。
- [x] 实现 `POST /api/chat/start`。
- [x] 实现 `GET /api/chat/history/{sessionId}`。
- [x] 实现 `DELETE /api/chat/session/{sessionId}`。
- [x] 实现 `GET /api/chat/sessions`。

验证结果：

- [x] `mvn -gs ..\settings.phase1.xml compile` 成功。

### Step 6.2 消息发送与模拟 AI 回复

- [x] 扩展 `ChatService`，实现 `sendMessage(Long userId, SendMessageRequest request)`。
- [x] 发送消息时校验会话归属用户。
- [x] 保存用户消息，`role=user`。
- [x] 生成固定 AI 回复：`你好！我是AI漫剧创作助手。关于漫剧创作的功能正在开发中，敬请期待！`。
- [x] 保存 AI 回复，`role=ai`。
- [x] 更新会话 `lastActive`。
- [x] HTTP 接口返回 AI 消息 `MessageResponse`。
- [x] 实现 `POST /api/chat/message`。

验证结果：

- [x] `mvn -gs ..\settings.phase1.xml test` 成功；总计 5 个测试，4 个执行通过，1 个真实 MySQL Mapper 测试默认跳过。
- [ ] 未执行真实聊天 curl 联调，因为该验证依赖你已执行 Phase 2 的 `init.sql` 并完成 Phase 5 注册/登录获取 token。

### Step 6.3 WebSocket 实时消息推送

- [x] 创建 `config/WebSocketConfig.java`。
- [x] 启用 STOMP WebSocket Message Broker。
- [x] 配置 STOMP 端点 `/ws/chat`，支持 SockJS。
- [x] 配置 broker 前缀 `/topic`。
- [x] 配置应用消息前缀 `/app`。
- [x] 创建 `controller/ChatWebSocketController.java`。
- [x] 实现 `@MessageMapping("/chat.send")`。
- [x] WebSocket 消息发送时从 STOMP native header 读取 `Authorization: Bearer <token>` 并解析用户身份。
- [x] WebSocket 与 HTTP 共用 `ChatService.sendMessage`。
- [x] AI 回复通过 `SimpMessagingTemplate` 推送到 `/topic/chat/{sessionId}`。

验证结果：

- [x] 短时启动烟测成功：Tomcat 启动在 `8085`，`SimpleBrokerMessageHandler` 启动并可用。
- [x] 启动日志显示 `JwtAuthenticationFilter` 仍在 Spring Security FilterChain 中。
- [ ] 未执行真实 WebSocket 客户端联调，因为需要先完成 SQL 初始化、用户登录并创建会话。

需要你做的事：

- [ ] 如果还没有执行 Phase 2 的 SQL，请先在 MySQL 中执行 `backend/src/main/resources/db/init.sql`。
- [ ] 启动后端：在 `backend` 目录运行 `mvn -gs ..\settings.phase1.xml spring-boot:run`。
- [ ] 如果还没有测试 Phase 5，请先注册并登录获取 token。
- [ ] 开始会话：
  `curl -X POST http://localhost:8085/api/chat/start -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d "{\"title\":\"测试会话\"}"`
- [ ] 发送消息：
  `curl -X POST http://localhost:8085/api/chat/message -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d "{\"sessionId\":1,\"content\":\"你好\"}"`
- [ ] 查看历史：
  `curl http://localhost:8085/api/chat/history/1 -H "Authorization: Bearer <token>"`
- [ ] 查看会话列表：
  `curl http://localhost:8085/api/chat/sessions -H "Authorization: Bearer <token>"`
- [ ] 删除会话：
  `curl -X DELETE http://localhost:8085/api/chat/session/1 -H "Authorization: Bearer <token>"`
- [ ] WebSocket 验证时，请连接 SockJS/STOMP 端点 `http://localhost:8085/ws/chat`，订阅 `/topic/chat/{sessionId}`，向 `/app/chat.send` 发送 JSON：`{"sessionId":1,"content":"你好"}`，并在 STOMP native header 中携带 `Authorization: Bearer <token>`。

说明：

- 当前聊天 AI 回复为固定模拟字符串，符合实现计划对 MVP 的限定；未接入 Python/LangChain AI 引擎。
- WebSocket 握手路径 `/ws/**` 按 Phase 5 安全配置放行，但发送消息时仍要求 STOMP header 中携带有效 JWT。
- 当前还没有 Phase 8 的全局异常处理，非法会话、越权访问、WebSocket token 缺失等错误会在后续统一收口为标准 JSON 错误响应。

## 2026-05-17 Phase 7：后端漫剧服务

### Step 7.1 漫剧 CRUD

- [x] 新增 `service/StoryService.java`，定义漫剧生成、详情、列表、更新、删除能力。
- [x] 新增 `service/impl/StoryServiceImpl.java`，集中处理漫剧归属校验、分页查询、详情组装、更新和删除。
- [x] 新增 `controller/StoryController.java`，提供 `POST /api/story/generate`、`GET /api/story/{id}`、`GET /api/story/list`、`PUT /api/story/{id}`、`DELETE /api/story/{id}`。
- [x] `getStoryDetail` 会查询 `stories` 主记录，并加载对应 `characters` 与 `scenes`，组装为 `StoryDetailResponse`。
- [x] `getUserStories` 使用 MyBatis-Plus `Page` 和 `StoryMapper.selectPageByUserId`，按 `updated_at DESC, id DESC` 返回当前用户的漫剧列表。
- [x] `updateStory` 只更新请求中非 `null` 的字段，并刷新 `updatedAt`。
- [x] `deleteStory` 先校验当前用户是否拥有该漫剧，再物理删除 `stories` 记录；关联的 `characters`、`scenes`、`dialogues` 依赖 `init.sql` 中的外键级联规则删除。

验证结果：

- [x] `mvn -gs ..\settings.phase1.xml compile` 成功。
- [x] `mvn -gs ..\settings.phase1.xml test` 成功；共 5 个测试，4 个执行通过，1 个真实 MySQL Mapper 测试默认跳过。
- [x] 随机端口短启动烟测成功：Tomcat 正常启动，`SimpleBrokerMessageHandler` 可用，说明应用上下文和 WebSocket broker 正常。
- [ ] 固定端口 `8085` 短启动烟测两次遇到端口占用；最终监听检查未发现 8085 残留监听。正式联调前如果再次遇到该问题，请先确认是否已有后端窗口占用 8085。
- [ ] 真实 CRUD curl 联调未执行，因为需要你先确认数据库 SQL 已执行，并使用登录接口获取有效 token。

### Step 7.2 模拟漫剧生成接口

- [x] `generateStory(Long userId, StoryGenerateRequest request)` 已实现。
- [x] 生成前会校验 `sessionId` 对应的会话存在、未删除并属于当前登录用户。
- [x] 生成模拟 `Story`：标题 `示例漫剧`、类型 `科幻`、风格 `日漫`、状态 `draft`，并写入示例摘要和正文。
- [x] 自动创建 2 个默认角色：`林澈`、`星野`。
- [x] 自动创建 2 个默认场景：`新海市废弃天文台`、`霓虹雨巷`。
- [x] 生成接口返回 `StoryResponse`，详情接口可返回完整正文和角色列表；场景列表字段保留兼容但前端当前不展示。

需要你做的事：

- [ ] 如果还没有执行 Phase 2 的 SQL，请先在 MySQL 中执行 `backend/src/main/resources/db/init.sql`。
- [ ] 执行 SQL 后，建议运行 `SHOW TABLES FROM springcloud;`，确认 `users`、`chat_sessions`、`messages`、`stories`、`characters`、`scenes`、`dialogues` 都存在。
- [ ] 启动后端：在 `backend` 目录运行 `mvn -gs ..\settings.phase1.xml spring-boot:run`。
- [ ] 注册并登录获取 token；如果已有测试账号，可以直接登录。
- [ ] 先创建会话，因为模拟漫剧生成需要 `sessionId`：
  `curl -X POST http://localhost:8085/api/chat/start -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d "{\"title\":\"漫剧生成测试\"}"`
- [ ] 生成漫剧：
  `curl -X POST http://localhost:8085/api/story/generate -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d "{\"sessionId\":1}"`
- [ ] 查询列表：
  `curl "http://localhost:8085/api/story/list?page=1&size=10" -H "Authorization: Bearer <token>"`
- [ ] 查询详情：
  `curl http://localhost:8085/api/story/1 -H "Authorization: Bearer <token>"`
- [ ] 更新漫剧：
  `curl -X PUT http://localhost:8085/api/story/1 -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d "{\"title\":\"新的漫剧标题\",\"genre\":\"冒险\",\"style\":\"国漫\"}"`
- [ ] 删除漫剧：
  `curl -X DELETE http://localhost:8085/api/story/1 -H "Authorization: Bearer <token>"`

说明：

- 当前阶段仍是 MVP 模拟生成，不调用 Python/LangChain AI 引擎，也不使用 RabbitMQ 异步队列。
- 当前还没有 Phase 8 的全局异常处理，因此不存在、越权、参数校验失败等错误会在后续阶段统一整理为更稳定的 JSON 错误响应。
- `GET /api/story/list` 返回的是 MyBatis-Plus 分页对象，前端 Phase 12 可以读取其中的 `records`、`current`、`size`、`total` 等字段。

## 2026-05-17 Phase 8：后端文件存储与全局异常处理

### Step 8.1 本地文件存储工具

- [x] 新增 `config/StorageConfig.java`，集中配置本地存储根目录、最大单文件大小和最大请求大小。
- [x] 在 `application.yml` 新增 `storage.root-path=./storage`、`storage.max-file-size=10MB`、`storage.max-request-size=50MB`。
- [x] 新增 `utils/LocalFileStorageUtil.java`，实现 `saveFile`、`loadFile`、`deleteFile`、`getFileUrl`。
- [x] 文件保存时使用 UUID 重命名，目录结构为 `{category}/{yyyy-MM-dd}/{uuid.ext}`。
- [x] 文件校验包含空文件检查、大小限制、扩展名白名单、Content-Type 白名单、分类名校验、日期路径校验、文件名路径穿越检查。
- [x] 默认允许扩展名：`jpg`、`jpeg`、`png`、`gif`、`bmp`、`pdf`、`epub`。
- [x] 新增 `dto/response/FileUploadResponse.java`，上传后返回原始文件名、相对路径、访问 URL、大小、Content-Type。
- [x] 新增 `controller/FileController.java`。
- [x] 实现 `POST /api/files/upload`，通过 multipart 表单上传文件，默认分类为 `resources`。
- [x] 实现 `GET /api/files/{category}/{date}/{filename}`，读取本地文件并以内联资源返回。
- [x] 实现 `DELETE /api/files/{category}/{date}/{filename}`，删除本地文件。

验证结果：

- [x] `mvn -gs ..\settings.phase1.xml compile` 成功。
- [x] `mvn -gs ..\settings.phase1.xml test` 成功；共 5 个测试，4 个执行通过，1 个真实 MySQL Mapper 测试默认跳过。
- [x] 使用 `--server.port=8099` 做短启动烟测成功，Tomcat、Spring Security FilterChain、WebSocket broker 均正常启动。
- [ ] 未执行真实文件上传 curl，因为该接口默认需要有效 JWT token，且需要你提供本地测试文件。

### Step 8.2 全局异常处理

- [x] 新增 `config/GlobalExceptionHandler.java`。
- [x] `MethodArgumentNotValidException` 返回 HTTP 400，并在 `data` 中返回字段级校验错误。
- [x] `ConstraintViolationException` 返回 HTTP 400，并在 `data` 中返回参数级校验错误。
- [x] `IllegalArgumentException`、multipart 参数缺失、上传大小超限等请求错误返回 HTTP 400。
- [x] `AccessDeniedException` 返回 HTTP 403。
- [x] `NoSuchElementException` 返回 HTTP 404。
- [x] `NoHandlerFoundException` 和 `NoResourceFoundException` 返回 HTTP 404，消息为 `接口不存在`。
- [x] 文件 IO 异常返回 HTTP 500，消息为 `文件处理失败`。
- [x] 其他未预期异常返回 HTTP 500，统一使用 `ApiResponse`。
- [x] 新增 `controller/ApiErrorController.java`，兜底 `/error`，避免 Spring Boot 默认错误页破坏统一响应格式。
- [x] 在 `SecurityConfig` 放行 `/error`，确保错误转发不会被认证拦截。

验证结果：

- [x] 访问不存在接口 `GET /api/auth/not-exist` 返回 HTTP 404 JSON：`{"code":404,"message":"接口不存在","data":null,...}`。
- [x] 未带 token 访问受保护接口仍返回 HTTP 401 JSON，原有安全测试通过。

需要你做的事：

- [ ] 如果还没有执行 Phase 2 的 SQL，请先在 MySQL 中执行 `backend/src/main/resources/db/init.sql`；文件上传本身不依赖数据库，但完整联调仍需要用户登录和 token。
- [ ] 启动后端：在 `backend` 目录运行 `mvn -gs ..\settings.phase1.xml spring-boot:run`。
- [ ] 通过注册/登录接口获取 `<token>`。
- [ ] 准备一个本地测试文件，例如 `F:\ai\aisay\test.png`，文件扩展名需在白名单内且不超过 10MB。
- [ ] 上传文件：
  `curl -X POST http://localhost:8085/api/files/upload -H "Authorization: Bearer <token>" -F "file=@F:\ai\aisay\test.png" -F "category=resources"`
- [ ] 上传成功后，响应中的 `filePath` 类似 `resources/2026-05-17/<uuid>.png`，`fileUrl` 类似 `/api/files/resources/2026-05-17/<uuid>.png`。
- [ ] 访问文件：
  `curl http://localhost:8085/api/files/resources/2026-05-17/<uuid>.png -H "Authorization: Bearer <token>"`
- [ ] 删除文件：
  `curl -X DELETE http://localhost:8085/api/files/resources/2026-05-17/<uuid>.png -H "Authorization: Bearer <token>"`
- [ ] 如果正式运行时再次遇到 `8085` 端口占用，请先确认是否已有后端窗口正在运行；不要同时启动两个后端实例。

说明：

- 当前 `/api/files/**` 沿用全局安全规则，上传、访问、删除都需要 JWT token；后续如果希望封面图公开访问，可以在安全配置中单独放行 GET 文件接口。
- 文件默认保存在后端工作目录下的 `storage` 目录中，应用启动时会自动创建 `storage`、`avatars`、`resources`、`covers` 基础目录。
- 当前文件记录未写入数据库，只返回路径给前端或业务模块保存；后续头像、封面图等字段可以直接保存 `filePath` 或 `fileUrl`。

## 2026-05-17 Phase 9：前端路由与布局

### Step 9.1 Vue Router 路由配置

- [x] 更新 `frontend/src/router/index.ts`，补齐阶段九要求的前端路由表。
- [x] `/` 重定向到 `/chat`。
- [x] `/chat` 懒加载 `ChatView.vue`。
- [x] `/chat/:sessionId` 懒加载 `ChatView.vue`，并通过 props 接收 `sessionId`。
- [x] `/stories` 懒加载 `StoryList.vue`。
- [x] `/story/:id` 懒加载 `StoryDetailView.vue`，并通过 props 接收 `id`。
- [x] `/user` 懒加载 `UserCenter.vue`。
- [x] `/login` 懒加载 `LoginView.vue`。
- [x] `/register` 懒加载 `RegisterView.vue`。
- [x] 新增路由守卫：除 `/login`、`/register` 外，未登录用户会跳转 `/login?redirect=<原路径>`。
- [x] 新增访客页保护：已登录用户访问 `/login` 或 `/register` 时会跳转 `/chat`。
- [x] 新增兜底路由，将未知前端路径重定向到 `/chat`。

验证结果：

- [x] `npm run build` 成功，`vue-tsc` 类型检查和 Vite 生产构建均通过。
- [x] 构建产物按懒加载拆出了 `ChatView`、`StoryList`、`StoryDetailView`、`UserCenter`、`LoginView`、`RegisterView`、`AppLayout` 等独立 chunk。
- [x] `npm run dev -- --host 127.0.0.1` 短启动烟测成功，Vite 在 `http://127.0.0.1:8080/` 返回 HTTP 200。
- [ ] 未打开浏览器手动点选路由；阶段九已通过构建和 dev server 验证，后续你可启动前端后手动访问各路径确认跳转体验。

构建提示：

- [ ] Vite 提示主 chunk 大于 500KB，主要来自当前阶段全量引入 Element Plus；后续可以通过按需引入或手动分包优化。
- [ ] Dart Sass 输出 legacy JS API deprecation warning，这是依赖链提示，不影响构建结果。
- [ ] Rollup 对 `@vueuse/core` 的 `#__PURE__` 注释位置给出提示，不影响构建结果。
- [ ] 沙箱内短启动 Vite 时曾遇到 `spawn EPERM`，授权外运行后成功，说明是本地子进程启动权限限制，不是代码问题。

### Step 9.2 基础布局组件

- [x] 新增 `frontend/src/components/common/AppLayout.vue`。
- [x] 顶部导航包含 Logo、`对话`、`漫剧列表` 两个主导航入口。
- [x] 用户菜单包含 `个人中心` 与 `退出登录`。
- [x] 退出登录会移除 `localStorage.token` 和 `localStorage.userInfo`，并跳转 `/login`。
- [x] 布局使用 `<RouterView />` 承载受保护页面内容。
- [x] 布局已做移动端适配，窄屏下导航会换行。
- [x] 新增阶段九占位页面：`ChatView.vue`、`StoryList.vue`、`StoryDetailView.vue`、`UserCenter.vue`、`LoginView.vue`、`RegisterView.vue`。
- [x] `LoginView.vue` 在 Phase 9 曾提供临时本地登录按钮；Phase 10 已替换为真实 `userStore.login()`。

需要你做的事：

- [ ] 启动前端：在 `frontend` 目录运行 `npm run dev`，访问 `http://localhost:8080`。
- [ ] 未登录状态访问 `http://localhost:8080/chat`，应跳转到 `/login?redirect=/chat`。
- [ ] 在登录页点击 `进入系统`，会写入临时 token 并跳回目标页面。
- [ ] 登录后访问 `http://localhost:8080/chat`、`/chat/1`、`/stories`、`/story/1`、`/user`，确认页面都能展示。
- [ ] 点击顶部导航的 `对话`、`漫剧列表`，确认路由切换正常。
- [ ] 点击右上角用户菜单的 `个人中心`，确认跳转 `/user`。
- [ ] 点击右上角用户菜单的 `退出登录`，确认清理 token 并回到 `/login`。
- [x] Phase 10 已将 `LoginView.vue` 中的临时本地登录逻辑替换为真实 `userStore.login()`。

说明：

- 阶段九只实现路由与基础布局，不调用后端接口；MySQL、Redis、RabbitMQ 在线状态不会影响本阶段构建。
- 当前登录判断使用 `localStorage.getItem("token")`，这是为了和后续 Phase 10 的用户 Store 持久化策略保持一致。
- 当前页面是可编译占位页，真实聊天、漫剧列表、漫剧详情、用户中心内容会在 Phase 10 到 Phase 13 继续填充。

## 2026-05-17 Phase 10：前端认证模块

### Step 10.1 API 层与 Axios 封装

- [x] 新增 `frontend/src/api/index.ts`，创建统一 Axios 实例。
- [x] Axios `baseURL` 保持为空，继续走 Vite `/api` 代理转发到后端。
- [x] Axios 超时时间设置为 `15000ms`。
- [x] 请求拦截器会从 `localStorage.token` 读取 JWT，并注入 `Authorization: Bearer <token>`。
- [x] 响应拦截器处理 HTTP 401：清理 `token` 和 `userInfo`，跳转 `/login?redirect=<当前路径>`，并提示重新登录。
- [x] 响应拦截器处理其他错误：读取后端 `ApiResponse.message` 并通过 Element Plus `ElMessage` 展示。
- [x] 新增 `frontend/src/types/auth.ts`，定义 `ApiResponse`、登录/注册请求、登录响应、用户资料响应和资料更新请求类型。

验证结果：

- [x] `npm run build` 成功，`vue-tsc` 类型检查和 Vite 生产构建均通过。
- [x] `npm run dev -- --host 127.0.0.1` 短启动烟测成功，访问 `http://127.0.0.1:8080/login` 返回 HTTP 200。
- [ ] 未执行真实浏览器注册/登录联调，因为需要你先确认数据库 SQL 已执行并启动后端。

### Step 10.2 Auth API 与 User Store

- [x] 新增 `frontend/src/api/authApi.ts`。
- [x] 实现 `register(data)`，调用 `POST /api/auth/register`。
- [x] 实现 `login(data)`，调用 `POST /api/auth/login`。
- [x] 实现 `getProfile()`，调用 `GET /api/user/profile`。
- [x] 实现 `updateProfile(data)`，调用 `PUT /api/user/profile`。
- [x] 新增 `frontend/src/stores/userStore.ts`。
- [x] Store state 包含 `token`、`userInfo`、`isLoggedIn`。
- [x] Store actions 包含 `login`、`register`、`fetchProfile`、`updateProfile`、`logout`。
- [x] Store 会从 `localStorage` 初始化 token 和用户资料，并在登录、更新资料、退出登录时同步持久化。
- [x] 登录成功后会先保存 JWT，再调用 `getProfile()` 拉取完整用户资料；如果资料拉取失败，会清理 token，避免伪登录态。
- [x] `AppLayout.vue` 已改为使用 `userStore` 展示用户名和处理退出登录。

### Step 10.3 登录/注册页面

- [x] `LoginView.vue` 已从阶段九临时 token 写入逻辑改为真实 `userStore.login()`。
- [x] 登录表单包含用户名和密码。
- [x] 登录表单校验：用户名必填且 3-50 字符，密码必填且 6-100 字符。
- [x] 登录成功后会跳转到 `redirect` 参数指定页面，默认 `/chat`。
- [x] `RegisterView.vue` 已接入真实 `userStore.register()`。
- [x] 注册表单包含用户名、邮箱、密码、确认密码。
- [x] 注册表单校验：用户名长度、邮箱格式、密码长度、确认密码一致。
- [x] 注册成功后提示成功并跳转 `/login`。

构建提示：

- [ ] Vite 仍提示主 chunk 大于 500KB，主要来自当前阶段全量引入 Element Plus；后续可用按需引入或手动分包优化。
- [ ] Dart Sass 仍输出 legacy JS API deprecation warning，这是依赖链提示，不影响构建结果。
- [ ] Rollup 仍对 `@vueuse/core` 的 `#__PURE__` 注释位置给出提示，不影响构建结果。

需要你做的事：

- [ ] 如果还没有执行 Phase 2 的 SQL，请先在 MySQL 中执行 `backend/src/main/resources/db/init.sql`，否则注册/登录会因为 `users` 表不存在而失败。
- [ ] 启动后端：在 `backend` 目录运行 `mvn -gs ..\settings.phase1.xml spring-boot:run`。
- [ ] 启动前端：在 `frontend` 目录运行 `npm run dev`，访问 `http://localhost:8080`。
- [ ] 打开 `http://localhost:8080/register`，填写用户名、邮箱、密码和确认密码，验证注册成功后跳转登录页。
- [ ] 打开 `http://localhost:8080/login`，使用刚注册的用户名和密码登录。
- [ ] 登录成功后打开浏览器 DevTools Network，确认后续请求头包含 `Authorization: Bearer <token>`。
- [ ] 登录后访问 `http://localhost:8080/user`，确认不会被路由守卫踢回登录页。
- [ ] 点击右上角 `退出登录`，确认 `localStorage.token` 和 `localStorage.userInfo` 被清理，并跳转 `/login`。

说明：

- 阶段十只实现前端认证，不新增后端接口；它依赖 Phase 5 已完成的 `/api/auth/register`、`/api/auth/login`、`/api/user/profile`、`/api/user/profile` 更新接口。
- 当前前端登录使用用户名而不是邮箱，与后端 `LoginRequest` 保持一致。
- 当前注册成功后不会自动登录，因为后端注册接口返回的是用户资料而非 JWT；用户需要跳转登录页再登录。

## 2026-05-17 Phase 11：前端聊天核心功能

### Step 11.1 Chat API 与 Chat Store

- [x] 新增 `frontend/src/types/chat.ts`，定义 `ChatSessionResponse`、`MessageResponse`、`ChatStartRequest`、`SendMessageRequest`。
- [x] 新增 `frontend/src/api/chatApi.ts`。
- [x] 实现 `startSession(title?)`，调用 `POST /api/chat/start`。
- [x] 实现 `sendMessage(sessionId, content)`，调用 `POST /api/chat/message`。
- [x] 实现 `getHistory(sessionId)`，调用 `GET /api/chat/history/{sessionId}`。
- [x] 实现 `getSessions()`，调用 `GET /api/chat/sessions`。
- [x] 实现 `deleteSession(sessionId)`，调用 `DELETE /api/chat/session/{sessionId}`。
- [x] 新增 `frontend/src/stores/chatStore.ts`。
- [x] Store state 包含 `currentSessionId`、`sessions`、`messages`、`isLoading`、`isSending`、`isConnected`。
- [x] Store actions 包含 `startNewSession`、`switchSession`、`sendMessage`、`loadHistory`、`loadSessions`、`deleteSession`、`connectWebSocket`、`disconnectWebSocket`。
- [x] `sendMessage` 会先在前端追加乐观用户消息，再调用 HTTP 接口获取 AI 固定回复并追加到消息列表。

验证结果：

- [x] `npm run build` 成功，`vue-tsc` 类型检查和 Vite 构建均通过。
- [x] `npm run dev -- --host 127.0.0.1` 短启动烟测成功，访问 `http://127.0.0.1:8080/chat` 返回 HTTP 200。
- [ ] 未执行真实聊天接口联调，因为需要你确认 Phase 2 SQL 已执行、后端已启动，并用真实账号登录获取 token。

### Step 11.2 消息气泡组件

- [x] 新增 `frontend/src/components/chat/MessageBubble.vue`。
- [x] 支持 `role`、`content`、`timestamp` props。
- [x] 用户消息右对齐，AI 消息左对齐。
- [x] 用户与 AI 使用不同头像标识、颜色和气泡圆角。
- [x] 使用 `dayjs` 格式化消息时间。
- [x] 使用 `markdown-it` 渲染 Markdown，且关闭 HTML 解析以降低 XSS 风险。

### Step 11.3 输入区域组件

- [x] 新增 `frontend/src/components/chat/InputArea.vue`。
- [x] 使用 Element Plus `el-input` textarea。
- [x] 支持 Enter 发送、Shift+Enter 换行。
- [x] 发送中会展示 loading，输入框和按钮会禁用。
- [x] 发送后自动清空输入框。
- [x] 通过 `send` 事件向父组件传递文本内容。

### Step 11.4 会话列表组件

- [x] 新增 `frontend/src/components/chat/SessionList.vue`。
- [x] 左侧列表展示所有会话标题和最后活跃时间。
- [x] 提供“新建对话”按钮。
- [x] 当前会话高亮。
- [x] 每个会话提供删除入口，并通过事件交给父组件确认。
- [x] 支持 `create`、`select`、`delete` 事件。

### Step 11.5 对话主页 ChatView

- [x] 重写 `frontend/src/views/ChatView.vue`，从阶段九占位页升级为真实聊天主页面。
- [x] 左侧为 `SessionList`，右侧为消息列表和输入区。
- [x] 支持无会话欢迎页。
- [x] 支持从 `/chat/:sessionId` 路由参数自动加载对应会话历史。
- [x] 新建会话后自动跳转 `/chat/{sessionId}`。
- [x] 切换会话时加载历史消息。
- [x] 删除当前会话后回到 `/chat`。
- [x] 消息列表在新增消息后自动滚动到底部。
- [x] 移动端布局会从左右两栏切换为上下布局。

### Step 11.6 WebSocket 集成

- [x] `chatStore` 集成 `SockJS + STOMP.js`。
- [x] 创建或切换会话时连接 `/ws/chat`。
- [x] 订阅 `/topic/chat/{sessionId}`。
- [x] STOMP `CONNECT` header 会携带 `Authorization: Bearer <token>`。
- [x] 收到服务端推送的 `ApiResponse<MessageResponse>` 后，会把当前会话的消息追加到 `messages`。
- [x] 组件卸载或删除当前会话时会断开 WebSocket。
- [x] 当前发送消息仍优先使用 HTTP 接口，确保与后端 Phase 6 的固定 AI 回复接口保持一致；WebSocket 作为实时推送通道已预留并可接收服务端推送。

构建提示：

- [ ] Vite 仍提示主 chunk 大于 500KB，主要来自 Element Plus 全量引入；后续可做按需引入或 manualChunks。
- [ ] Dart Sass 仍输出 legacy JS API deprecation warning，这是依赖链提示，不影响构建结果。
- [ ] Rollup 仍对 `@vueuse/core` 的 `#__PURE__` 注释位置给出提示，不影响构建结果。

需要你做的事：

- [ ] 如果还没有执行 Phase 2 的 SQL，请先在 MySQL 中执行 `backend/src/main/resources/db/init.sql`，否则注册、登录、会话和消息接口会失败。
- [ ] 启动后端：在 `backend` 目录运行 `mvn -gs ..\settings.phase1.xml spring-boot:run`。
- [ ] 启动前端：在 `frontend` 目录运行 `npm run dev`，访问 `http://localhost:8080`。
- [ ] 如果还没有账号，请先访问 `/register` 注册，再访问 `/login` 登录。
- [ ] 登录后访问 `/chat`，点击左侧 `+` 创建新会话。
- [ ] 在输入框发送 `你好`，应看到右侧出现用户消息和后端固定 AI 回复。
- [ ] 刷新页面或访问 `/chat/{sessionId}`，应能加载该会话历史。
- [ ] 点击左侧会话切换不同对话，确认消息列表随会话变化。
- [ ] 点击会话上的 `删除`，确认删除后列表刷新，当前会话删除后回到 `/chat`。
- [ ] 如需验证 WebSocket，请打开浏览器 DevTools Network 的 WS 面板，确认连接 `/ws/chat`，并观察 STOMP 订阅 `/topic/chat/{sessionId}`。

说明：

- 阶段十一仍使用后端 Phase 6 的固定 AI 回复，不接入真实 AI 引擎。
- HTTP 发送和 WebSocket 推送共用同一后端业务模型；当前前端发送选择 HTTP 是为了避免重复追加用户消息和 AI 消息。
- `markdown-it` 已关闭 HTML 渲染，用户输入中的 Markdown 会被渲染，但原始 HTML 不会执行。

## 2026-05-17 前端空白页与登录跳转问题排查

### 问题现象

- [x] 旧浏览器访问 `http://localhost:8080/` 时出现空白页。
- [x] 更换新浏览器后页面可以正常显示登录页。
- [x] 登录后地址停留在 `http://localhost:8080/login?redirect=/chat`，没有正常进入聊天页。

### 排查结论

- [x] `HTTP 304 Not Modified` 属于浏览器缓存协商结果，不是前端空白页错误。
- [x] `Deprecation Warning [legacy-js-api]` 是 Sass 依赖链弃用提醒，不影响 Vite 构建和页面运行。
- [x] 当前前端构建通过，说明不是 TypeScript 或 Vite 编译失败导致的白屏。
- [x] 空白页只在旧浏览器出现，而新浏览器正常，优先判断为旧浏览器缓存了历史错误模块、旧 HMR 状态或旧 `localStorage` 登录态。
- [x] 登录不跳转的可疑点定位到 `userStore.login()`：原逻辑在登录接口返回 token 后，会继续请求 `/api/user/profile`；如果资料接口异常，会清理 token 并抛错，页面就会停留在 `/login?redirect=/chat`。

### 已完成修复

- [x] 更新 `frontend/src/stores/userStore.ts`：登录接口成功后先保存 JWT 和基础用户信息，随后再尝试拉取完整 profile。
- [x] 如果 `/api/user/profile` 返回 401，仍会清理登录态并留在登录页，避免无效 token 进入系统。
- [x] 如果 `/api/user/profile` 出现非 401 异常，不再阻断登录跳转，用户可以先进入系统，后续再由用户中心或接口错误提示暴露问题。
- [x] 更新 `frontend/src/views/LoginView.vue`：登录失败时在表单内显示明确错误信息，避免只看到页面没有跳转。
- [x] 再次更新 `frontend/src/views/LoginView.vue`：登录成功后改用 Promise 表单校验、`router.replace()` 等待导航完成，并增加 `window.location.assign()` 兜底跳转。
- [x] 登录按钮增加 `native-type="button"`，避免浏览器原生表单提交行为干扰 SPA 路由跳转。
- [x] 更新 `frontend/src/router/index.ts`：路由守卫会解析 JWT 过期时间，发现过期 token 时自动清理 `localStorage` 并跳转登录页。
- [x] 更新 `/` 根路径重定向逻辑：有有效 token 时进入 `/chat`，无 token 或 token 过期时进入 `/login`。
- [x] 更新 `frontend/src/views/ChatView.vue`：聊天页加载会话或发送消息失败时显示错误提示和“重新加载”按钮，不再只留下空白页面。
- [x] 更新 `frontend/src/stores/chatStore.ts`：会话列表和消息历史接口返回异常结构时兜底为空数组，避免组件渲染崩溃。
- [x] 更新 `frontend/vite.config.ts` 和 `frontend/index.html`：开发环境响应增加 `no-store` 缓存策略，降低旧浏览器继续使用旧模块导致白屏的概率。

### 验证结果

- [x] `npm run build` 成功。
- [x] 修复登录跳转兜底后再次执行 `npm run build` 成功。
- [x] 修复 `/chat` 白屏与缓存策略后再次执行 `npm run build` 成功。
- [x] 构建仍出现 Sass legacy JS API deprecation warning，这是非阻断警告。
- [x] 构建仍出现 Element Plus 主 chunk 体积提示，这是性能优化提示，不影响当前功能。

### 需要你做的事

- [ ] 停止当前前端开发服务并重新运行 `npm run dev`，否则 `vite.config.ts` 中新增的缓存响应头不会生效。
- [ ] 在出现空白页的旧浏览器中清理 `localhost:8080` 的站点数据，或在控制台执行 `localStorage.clear(); sessionStorage.clear(); location.reload();`。
- [ ] 清理后按 `Ctrl + F5` 强制刷新页面，避免继续使用旧缓存。
- [ ] 如果登录仍不跳转，请打开浏览器 DevTools 的 Network 面板，重新登录后检查 `POST /api/auth/login` 和 `GET /api/user/profile` 的状态码与响应内容。
- [ ] `POST /api/auth/login` 必须返回 200 且响应中包含 token；如果不是 200，请检查用户名密码是否正确、账号是否已注册、后端是否连接到已初始化的 MySQL。
- [ ] 如果 `GET /api/user/profile` 返回 401，说明 token 未被后端接受，需要重点检查后端 JWT 配置、后端是否重启后使用了不同密钥、请求头是否携带 `Authorization: Bearer <token>`。
- [ ] 如果 `/chat` 页面显示错误提示，请根据提示优先确认后端 `8085` 已启动、MySQL 表已初始化、当前账号能正常调用 `GET /api/chat/sessions`。

## 2026-05-17 Phase 12：前端漫剧模块

### Step 12.1 Story API 与 Story Store

- [x] 新增 `frontend/src/types/story.ts`，定义 `StoryResponse`、`StoryDetailResponse`、`StoryCharacter`、`StoryScene`、`StoryUpdateRequest`、`StoryPageResponse`。
- [x] 新增 `frontend/src/api/storyApi.ts`。
- [x] 实现 `getStories(page, size)`，调用 `GET /api/story/list`。
- [x] 实现 `getStoryDetail(id)`，调用 `GET /api/story/{id}`。
- [x] 实现 `generateStory(sessionId)`，调用 `POST /api/story/generate`。
- [x] 实现 `updateStory(id, data)`，调用 `PUT /api/story/{id}`。
- [x] 实现 `deleteStory(id)`，调用 `DELETE /api/story/{id}`。
- [x] 新增 `frontend/src/stores/storyStore.ts`。
- [x] Store state 包含 `stories`、`currentStory`、`pagination`、`isLoading`、`isGenerating`。
- [x] Store actions 包含 `fetchStories`、`fetchStoryDetail`、`generateStory`、`updateStory`、`deleteStory`。
- [x] `fetchStories` 会读取后端 MyBatis-Plus 分页结构，并同步 `current/size/total/pages`。

验证结果：

- [x] `npm run build` 成功，`vue-tsc` 类型检查和 Vite 构建均通过。

### Step 12.2 漫剧列表页

- [x] 重写 `frontend/src/views/StoryList.vue`。
- [x] 页面顶部展示漫剧库说明和“去对话生成”入口。
- [x] 卡片网格展示漫剧封面占位、标题、类型、状态、摘要和创建时间。
- [x] 支持分页组件，分页变化会重新调用 `storyStore.fetchStories`。
- [x] 支持当前页内标题/摘要搜索。
- [x] 支持当前页内类型筛选和状态筛选。
- [x] 点击卡片或“查看”按钮跳转 `/story/{id}`。
- [x] 删除按钮带确认弹窗，确认后调用后端删除接口并刷新本地列表状态。

验证结果：

- [x] 未登录访问 `/stories` 会被路由守卫跳转到 `/login?redirect=/stories`，浏览器控制台无错误。
- [ ] 未执行真实列表数据联调，因为需要已登录账号、后端运行和数据库中存在当前用户的漫剧数据。

### Step 12.3 漫剧详情页

- [x] 重写 `frontend/src/views/StoryDetailView.vue`。
- [x] 顶部展示标题、类型、风格、状态和操作按钮。
- [x] 内容区展示故事摘要、剧情大纲和主要角色设定。
- [x] 新增 `frontend/src/components/story/CharacterCard.vue`，展示角色头像占位、名称、角色定位、描述、性格和外观字段。
- [x] 详情页支持编辑基础信息：标题、类型、风格、摘要。
- [x] 保存编辑会调用 `storyStore.updateStory`，并同步当前详情状态。
- [x] 删除详情会调用 `storyStore.deleteStory`，成功后回到 `/stories`。
- [x] “生成封面”按钮已作为后续 AI 图片能力预留，目前显示提示，不调用后端。

验证结果：

- [x] `npm run build` 成功。
- [ ] 未访问真实 `/story/{id}` 联调，因为需要当前登录用户拥有对应 story 数据。

### Step 12.4 对话中触发漫剧生成

- [x] 更新 `frontend/src/views/ChatView.vue`。
- [x] 在聊天页头部加入“生成漫剧”按钮。
- [x] 按钮仅在当前会话存在时可用，生成中显示 loading。
- [x] 点击后调用 `storyStore.generateStory(currentSessionId)`。
- [x] 生成成功后向聊天消息区追加一条本地 AI 提示消息。
- [x] 生成成功后自动跳转到 `/story/{id}` 查看详情。
- [x] 更新 `frontend/src/stores/chatStore.ts`，新增 `addLocalAiMessage`，用于追加前端本地提示消息。

验证结果：

- [x] `npm run build` 成功。
- [ ] 未执行真实“对话生成漫剧”联调，因为需要登录后创建真实会话，并确保后端 `POST /api/story/generate` 可访问。

构建提示：

- [ ] Vite 仍提示主 chunk 大于 500KB，主要来自 Element Plus 全量引入；这是性能优化提示，不影响阶段十二功能。
- [ ] Dart Sass 仍输出 legacy JS API deprecation warning，这是依赖链提示，不影响构建结果。
- [ ] Rollup 仍对 `@vueuse/core` 的 `#__PURE__` 注释位置给出提示，不影响构建结果。

需要你做的事：

- [ ] 确认已经执行 Phase 2 的 SQL，数据库中存在 `stories`、`characters`、`scenes`、`chat_sessions` 等表。
- [ ] 启动后端：在 `backend` 目录运行 `mvn -gs ..\settings.phase1.xml spring-boot:run`。
- [ ] 启动前端：在 `frontend` 目录运行 `npm run dev`。
- [ ] 登录后访问 `/chat`，先创建或选择一个会话，再点击“生成漫剧”。
- [ ] 生成成功后应自动进入 `/story/{id}`，并看到故事摘要、剧情大纲和主要角色卡片。
- [ ] 访问 `/stories`，确认新生成的漫剧出现在列表中。
- [ ] 在详情页尝试编辑标题、类型、风格、摘要，确认保存成功后页面内容更新。
- [ ] 删除漫剧会同时删除关联角色、场景等后端级联数据；删除前请确认该测试数据不需要保留。

说明：

- 阶段十二只接入后端已有的模拟漫剧生成接口，不调用 Python/LangChain AI 引擎。
- 当前列表筛选是前端当前页内筛选，因为后端接口暂未支持搜索、类型和状态查询参数。
- 当前后端 `StoryResponse` 未返回 `updatedAt`，列表时间暂显示 `createdAt`。
- 封面生成入口已预留，后续需要 AI 图片生成或文件上传能力后再接真实接口。

## 2026-05-17 Phase 13：前端用户中心

### Step 13.1 用户中心页面

- [x] 重写 `frontend/src/views/UserCenter.vue`。
- [x] 页面顶部展示头像、用户名、邮箱、编辑资料按钮和刷新数据按钮。
- [x] 使用 Element Plus `el-avatar` 展示头像；无头像或头像加载失败时显示用户名首字。
- [x] 展示用户基础信息：用户 ID、用户名、邮箱、头像路径、注册时间。
- [x] 新增统计卡片：漫剧数量、对话数量、注册日期。
- [x] 漫剧数量通过 `storyStore.fetchStories(1, 1)` 的分页 `total` 获取。
- [x] 对话数量通过 `chatStore.loadSessions()` 的当前用户会话数量获取。
- [x] 提供快捷入口：继续对话、查看漫剧。

### Step 13.2 编辑用户资料

- [x] 编辑资料使用 `el-dialog` 弹窗。
- [x] 表单字段包含用户名、邮箱。
- [x] 表单校验包含用户名必填、用户名长度 3-50、邮箱必填、邮箱格式。
- [x] 保存时调用 `userStore.updateProfile()`，对接后端 `PUT /api/user/profile`。
- [x] 保存成功后更新 Pinia 状态和 `localStorage.userInfo`。

### Step 13.3 头像上传

- [x] 新增 `frontend/src/types/file.ts`，定义 `FileUploadResponse`。
- [x] 新增 `frontend/src/api/fileApi.ts`。
- [x] 实现 `uploadFile(file, category)`，调用 `POST /api/files/upload`。
- [x] 实现 `loadFileBlob(filePathOrUrl)`，通过 Axios 携带 JWT 加载受保护文件 Blob。
- [x] 用户中心使用 Element Plus `el-upload` 上传头像。
- [x] 上传前校验文件类型必须为图片。
- [x] 上传前校验文件大小不超过 10MB。
- [x] 上传分类固定为 `avatars`。
- [x] 上传成功后调用 `userStore.updateProfile({ avatarPath: result.fileUrl })` 保存头像路径。
- [x] 头像预览通过受保护文件 Blob 转换为 `objectURL` 展示，避免 `/api/files/**` 需要 Authorization 时普通 img 无法访问。
- [x] 组件卸载或头像变更时会释放旧的 `objectURL`。

验证结果：

- [x] `npm run build` 成功，`vue-tsc` 类型检查和 Vite 构建均通过。
- [x] 未登录访问 `/user` 会被路由守卫跳转到 `/login?redirect=/user`，浏览器控制台无错误。
- [ ] 未执行真实头像上传联调，因为需要已登录账号和本地图片文件。
- [ ] 未执行真实编辑资料联调，因为需要已登录账号和后端运行。

构建提示：

- [ ] Vite 仍提示主 chunk 大于 500KB，主要来自 Element Plus 全量引入；这是性能优化提示，不影响阶段十三功能。
- [ ] Dart Sass 仍输出 legacy JS API deprecation warning，这是依赖链提示，不影响构建结果。
- [ ] Rollup 仍对 `@vueuse/core` 的 `#__PURE__` 注释位置给出提示，不影响构建结果。

需要你做的事：

- [ ] 确认已经执行 Phase 2 的 SQL，数据库中存在 `users`、`stories`、`chat_sessions` 等表。
- [ ] 启动后端：在 `backend` 目录运行 `mvn -gs ..\settings.phase1.xml spring-boot:run`。
- [ ] 启动前端：在 `frontend` 目录运行 `npm run dev`。
- [ ] 登录后访问 `/user`，确认头像、用户名、邮箱、注册日期、漫剧数量和对话数量展示正常。
- [ ] 点击“编辑资料”，修改用户名或邮箱并保存，确认页面和右上角用户名同步更新。
- [ ] 点击“上传头像”，选择 `jpg`、`jpeg`、`png`、`gif` 或 `bmp` 图片，文件大小不超过 10MB。
- [ ] 上传成功后确认头像即时刷新；如果没有刷新，请打开 Network 检查 `POST /api/files/upload` 和 `PUT /api/user/profile` 是否都返回 200。
- [ ] 头像会保存到后端 `storage/avatars/{yyyy-MM-dd}/...`，如果需要清理测试头像，可后续通过文件删除接口或手动清理测试文件。

说明：

- 当前统计复用已有列表接口，不新增后端统计接口。
- 当前 `/api/files/**` 仍按后端安全配置要求 JWT，前端已通过 Axios Blob 方式处理头像预览。
- 如果头像文件被手动删除，用户中心会自动回退到文字头像，不影响资料页其他功能。

## 2026-05-17 剧情大纲生成流程调整

### 变更目标

- [x] 将原“生成漫剧”流程调整为“生成漫剧剧情大纲”流程。
- [x] 点击生成时要求用户输入漫剧大纲题材，题材必填。
- [x] 点击生成时允许用户输入大致剧情，大致剧情可选。
- [x] 后端搭建 Java 调用 Python FastAPI 的函数边界，真实 Python 生成逻辑稍后再实现。

### 后端变更

- [x] 更新 `backend/src/main/java/com/aisay/manga/dto/request/StoryGenerateRequest.java`。
- [x] `StoryGenerateRequest` 新增 `genre`，必填，最大 100 字符。
- [x] `StoryGenerateRequest` 新增 `plot`，可选，最大 5000 字符。
- [x] 新增 `backend/src/main/java/com/aisay/manga/dto/ai/StoryOutlineGenerateRequest.java`，作为 Java 调用 Python 的请求 DTO。
- [x] 新增 `backend/src/main/java/com/aisay/manga/dto/ai/StoryOutlineGenerateResponse.java`，作为 Python 返回 Java 的响应 DTO。
- [x] 新增 `backend/src/main/java/com/aisay/manga/utils/AiEngineClient.java`。
- [x] `AiEngineClient` 默认调用 `http://localhost:5000/api/story/outline`。
- [x] 更新 `backend/src/main/resources/application.yml`，新增 `ai.engine.base-url` 和 `ai.engine.story-outline-path`。
- [x] 更新 `StoryServiceImpl.generateStory`：先校验会话归属，再调用 Python 剧情大纲接口，最后把返回的大纲保存到 `stories.full_content`。
- [x] 生成结果仍保存为 `draft` 草稿，详情页可继续查看和编辑。

### Python FastAPI 骨架

- [x] 新增 `python-ai/requirements.txt`。
- [x] 新增 `python-ai/main.py`。
- [x] 新增 FastAPI 路由 `POST /api/story/outline`。
- [x] Python 路由当前是占位实现，用输入题材和剧情拼出示例大纲。
- [x] 后续真实生成逻辑只需要替换 `generate_story_outline` 函数内部实现。
- [x] 更新 `generate_story_outline`，使用 LangChain `with_structured_output()` 约束模型输出。
- [x] 更新 `NovelOutlineOutput` 结构化输出模型，固定输出 `novel_name`、`story_summary`、`outline`、`main_characters`。
- [x] Python 内部结构化输出会映射为 Java 当前需要的 `novelName`、`storySummary`、`outline`、`mainCharacters`。
- [x] 更新 `python-ai/requirements.txt`，补充 `langchain-core`、`langchain-community`、`dashscope`。
- [x] 更新 `python-ai/main.py`，启动时读取 `python-ai/api.yml` 中的 `tongyi.api_key` 并写入 `DASHSCOPE_API_KEY`。
- [x] 更新 `python-ai/requirements.txt`，补充 `PyYAML`。
- [x] 更新 `.gitignore`，忽略 `python-ai/api.yml`，避免本地 API Key 被误提交。
- [x] 新增 Python 路由 `POST /api/story/outline/revise`，作为后续 LangChain 大纲修改链路占位。
- [x] 新增 `MainCharacterSetting` 结构，角色设定包含姓名、角色定位、描述、性格、外观。

### 角色入库与大纲修改链路

- [x] 更新 `StoryOutlineGenerateResponse`，接收 `novelName`、`storySummary`、`outline`、`mainCharacters`。
- [x] 新增 `StoryOutlineReviseRequest` / `StoryOutlineReviseResponse` Java AI DTO。
- [x] 新增 `dto/request/StoryOutlineReviseRequest.java`，校验修改意见必填且不超过 5000 字符。
- [x] 更新 `AiEngineClient`，新增 `reviseStoryOutline`，默认调用 `/api/story/outline/revise`。
- [x] 更新 `application.yml`，新增 `ai.engine.story-outline-revise-path`。
- [x] 更新 `StoryService` 和 `StoryController`，新增 `POST /api/story/{id}/outline/revise`。
- [x] 更新 `StoryServiceImpl.generateStory`，把 Python 返回的主要角色写入 `characters` 表。
- [x] 更新 `StoryServiceImpl.getStoryDetail`，详情响应不再查询和返回场景列表内容。
- [x] 更新 `StoryServiceImpl.reviseStoryOutline`，调用 Python 大纲修改接口，并把返回的大纲和角色设定同步保存到数据库。
- [x] 更新前端 `storyApi` / `storyStore`，新增 `reviseStoryOutline`。
- [x] 更新 `StoryDetailView.vue`，删除场景列表展示，保留剧情大纲和主要角色设定。
- [x] 更新 `StoryDetailView.vue`，新增“大纲修改”按钮和修改意见弹窗。

### 前端变更

- [x] 更新 `frontend/src/types/story.ts`，新增 `StoryGenerateRequest`。
- [x] 更新 `frontend/src/api/storyApi.ts`，`generateStory` 改为提交 `sessionId`、`genre`、`plot`。
- [x] 更新 `frontend/src/stores/storyStore.ts`，`generateStory` 改为接收生成请求对象。
- [x] 更新 `frontend/src/views/ChatView.vue`。
- [x] 聊天页按钮文案从“生成漫剧”调整为“生成剧情大纲”。
- [x] 点击按钮后弹出表单，包含题材和大致剧情。
- [x] 题材支持选择预设题材，也支持手动输入。
- [x] 大致剧情使用多行文本框，最多 5000 字符。
- [x] 生成成功后追加本地 AI 提示消息，并跳转 `/story/{id}`。
- [x] 更新 `frontend/src/views/StoryDetailView.vue`，将内容区标题改为“剧情大纲”。

### 验证结果

- [x] `mvn -gs ..\settings.phase1.xml compile` 成功。
- [x] `npm run build` 成功。
- [x] 使用 `python-ai/venv` 做 Python 语法检查成功。
- [x] 使用 `python-ai/venv` 导入 `main.py` 成功。
- [x] 本地确认 `ChatTongyi` 支持 `with_structured_output` 方法。
- [x] 本地确认 `PyYAML` 可导入。
- [ ] 未执行真实端到端调用，因为需要同时启动后端、前端和 Python FastAPI 服务。

### 需要你做的事

- [ ] 启动 Python FastAPI：在 `python-ai` 目录运行 `pip install -r requirements.txt`。
- [ ] 启动 Python FastAPI：在 `python-ai` 目录运行 `uvicorn main:app --host 0.0.0.0 --port 5000 --reload`。
- [ ] 启动后端：在 `backend` 目录运行 `mvn -gs ..\settings.phase1.xml spring-boot:run`。
- [ ] 启动前端：在 `frontend` 目录运行 `npm run dev`。
- [ ] 登录后进入 `/chat`，创建或选择一个会话。
- [ ] 点击“生成剧情大纲”，输入题材，按需填写大致剧情，然后点击“生成大纲”。
- [ ] 如果后端返回 `Python 剧情大纲接口暂不可用`，请先确认 FastAPI 是否运行在 `http://localhost:5000`，并确认 `/api/story/outline` 路由存在。
- [ ] 点击详情页“大纲修改”，输入修改意见后提交；当前 Python 只会返回占位修订内容，真实 LangChain 修订逻辑稍后在 `revise_story_outline` 中开发。
- [ ] 后续实现真实 AI 逻辑时，优先修改 `python-ai/main.py` 的 `generate_story_outline` 和 `revise_story_outline` 函数，保持请求/响应字段不变即可。

### 本次收口补充

- [x] 清理 `StoryServiceImpl` 中旧的默认场景创建逻辑，避免生成剧情大纲时继续写入场景数据。
- [x] 修复 `StoryServiceImpl` 中遗留乱码字符串导致的 Java 编译风险，保留生成大纲、角色入库、大纲修改同步保存的核心逻辑。
- [x] 再次执行 `mvn -gs ..\settings.phase1.xml compile`，后端编译成功。
- [x] 再次执行 `npm run build`，前端类型检查和生产构建成功；仍有 Sass legacy JS API、Rollup 注释和大 chunk 提示，均非阻断问题。
- [x] 执行 `git diff --check`，仅出现 Windows LF/CRLF 换行提示，无尾随空格等阻断问题。

### 2026-05-17 剧情大纲生成等待与进度输出调整

- [x] 更新 `AiEngineClient`，Java 调用 Python FastAPI 时设置连接超时为 10 秒、读取超时为无限等待，避免大模型生成耗时较长时被后端提前中断。
- [x] 更新 `python-ai/main.py`，Tongyi 模型开启 `streaming=True`。
- [x] 新增 `ConsoleStreamingCallback`，Python 控制台会打印请求接收、模型开始、流式 token、模型完成、响应封装等进度。
- [x] 保留 `with_structured_output(NovelOutlineOutput)`，最终返回字段仍固定为 `novelName`、`storySummary`、`outline`、`mainCharacters`。
- [x] 执行 Python 导入检查成功：`import main`。
- [x] 执行后端编译成功：`mvn -gs ..\settings.phase1.xml compile`。

需要你做的事：

- [ ] 重启 Java 后端服务，让新的无限读取超时配置生效。
- [ ] 重启 Python FastAPI 服务，让新的控制台进度日志和流式 callback 生效。
- [ ] 如果控制台只显示阶段日志、不显示 token，说明当前 Tongyi structured output 链路没有把 token 事件透出；这不影响最终 JSON 结构化结果返回。

## 2026-05-17 剧情大纲手动编辑与分卷大纲生成

### 数据库

- [x] 新增 SQL 文件 `backend/src/main/resources/db/20260518_create_story_volume_outlines.sql`，用于创建 `story_volume_outlines` 分卷大纲表。
- [x] 同步更新 `backend/src/main/resources/db/init.sql`，新环境初始化时会包含 `story_volume_outlines` 表。
- [x] 移除 `Story` 实体中的 `volumeOutline` 字段，避免 `stories` 表缺少 `volume_outline` 时 `selectById` 报错。
- [x] 新增 `StoryVolumeOutline` 实体和 `StoryVolumeOutlineMapper`，故事与分卷大纲为一对多关系。
- [x] 更新 `StoryDetailResponse`，详情接口返回 `volumeOutlines` 列表。

### Java 后端

- [x] 新增 `PUT /api/story/{id}/detail`，用于手动保存故事摘要、剧情大纲和角色设定。
- [x] 手动保存角色设定时，会以页面提交的角色列表整体替换当前 story 的 `characters` 数据。
- [x] 新增 `POST /api/story/{id}/volume-outline/generate`，用于根据当前剧情大纲调用 Python 生成分卷大纲。
- [x] 新增 Java AI DTO `StoryVolumeOutlineGenerateRequest` / `StoryVolumeOutlineGenerateResponse`。
- [x] 更新 `AiEngineClient`，新增 `/api/story/volume-outline` 调用。
- [x] 分卷大纲生成成功后会先清理当前 story 的旧分卷，再按卷写入 `story_volume_outlines` 表，并返回最新 story 详情。

### Python FastAPI

- [x] 新增 `POST /api/story/volume-outline`。
- [x] 新增分卷大纲提示词，明确要求分卷剧情尽可能丰富，包含更多情节点、冲突、反转、角色选择、情绪钩子和卷末悬念。
- [x] 分卷大纲使用 `with_structured_output(VolumeOutlineOutput)` 固定返回 `volumes` 数组，每一项包含卷号、卷名、摘要、详细内容和卷末钩子。
- [x] 分卷大纲生成复用控制台进度输出，日志前缀为 `volume-outline`。

### 前端

- [x] 更新 `StoryDetailView.vue`，新增“手动修改大纲/角色”按钮。
- [x] 手动修改弹窗会基于现有故事摘要、剧情大纲和角色设定预填，支持在现有文本基础上局部修改。
- [x] 角色设定支持新增、删除、修改姓名、定位、描述、性格/弧光和外观 JSON。
- [x] 新增“分卷大纲生成”按钮。
- [x] 新增“分卷大纲”展示板块，生成后自动刷新并按卡片展示数据库中的 `volumeOutlines`。
- [x] 更新 `storyApi`、`storyStore`、`types/story.ts`，接入手动保存和分卷生成接口。

### 验证结果

- [x] 后端编译成功：`mvn -gs ..\settings.phase1.xml compile`。
- [x] 前端构建成功：`npm run build`。
- [x] Python 模块导入检查成功。
- [ ] 未执行真实端到端分卷大纲生成，因为需要你本地执行新增 SQL 并同时启动 MySQL、Java 后端、Python FastAPI 和前端。

### 需要你做的事

- [ ] 执行 SQL 文件：`backend/src/main/resources/db/20260518_create_story_volume_outlines.sql`。
- [ ] 重启 Java 后端，让 `story_volume_outlines` 表映射和新增接口生效。
- [ ] 重启 Python FastAPI，让 `/api/story/volume-outline` 生效。
- [ ] 登录前端后进入任意 story 详情页，先尝试“手动修改大纲/角色”保存，再点击“分卷大纲生成”。
- [ ] 如果分卷生成报表不存在，请确认上面的 SQL 已经在当前 Java 连接的 `springcloud` 数据库执行。
- [ ] 上一版 `stories.volume_outline` 单字段方案已经废弃；如果你已经执行过旧 SQL，保留该字段也不影响当前代码运行。

### 2026-05-18 分卷大纲表结构调整

- [x] 根据“一篇故事对应多个分卷大纲”的要求，将分卷大纲从 `stories.volume_outline` 单字段改为 `story_volume_outlines` 一对多表。
- [x] 删除旧 SQL 文件 `backend/src/main/resources/db/20260517_add_volume_outline.sql`，避免继续引导执行旧的单字段方案。
- [x] Java 详情查询不再读取 `stories.volume_outline`，解决未执行旧字段 SQL 时 `storyMapper.selectById(storyId)` 因字段不存在报错的问题。
- [x] Python 分卷生成响应从 `volumeOutline` 字符串改为 `volumes` 数组。
- [x] 前端分卷大纲板块从单段文本改为多卷卡片列表。
- [x] 后端编译成功：`mvn -gs ..\settings.phase1.xml compile`。
- [x] 前端构建成功：`npm run build`。
- [x] Python 模块导入检查成功。
