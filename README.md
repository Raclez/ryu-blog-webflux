# Ryu Blog

<div align="center">

一个基于 Spring Boot WebFlux 的现代化博客系统，集成 AI 内容生成、异步任务调度、多存储策略等企业级特性

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.8-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[特性](#-核心特性) • [快速开始](#-快速开始) • [文档](#-文档) • [架构](#-系统架构) • [API](#-api-文档)

</div>

---

## 📖 项目简介

Ryu Blog 是一个功能完善的现代化博客系统，采用响应式编程模型构建，支持高并发、低延迟的内容管理。系统集成了 AI 内容生成能力，提供智能化的博客创作辅助，同时具备完善的权限管理、文件存储、异步任务调度等企业级功能。

### 🎯 设计理念

- **响应式优先**: 基于 Spring WebFlux 和 Project Reactor，全链路非阻塞
- **AI 赋能**: 集成 Spring AI，支持多 AI 提供商（OpenAI、Azure、Anthropic）
- **可扩展性**: 插件化架构，支持自定义任务类型、存储策略、AI 模板
- **开发友好**: 完善的 API 文档、统一异常处理、参数校验
- **生产就绪**: 监控指标、健康检查、日志追踪、性能优化

---

## ✨ 核心特性

### 🤖 AI 内容生成

- **多模式生成**: 自由模式、模板模式、引导模式
- **智能优化**: 自动提示词增强、内容质量分析、SEO 优化
- **多提供商支持**: OpenAI、Azure OpenAI、Anthropic Claude
- **模板系统**: 17+ 预置模板（技术教程、问题解决、架构设计等）
- **配额管理**: 用户级别的使用配额和统计
- **内容精炼**: 支持扩展、缩短、改写、优化等操作

### ⚡ 异步任务系统

- **通用框架**: 支持多种任务类型扩展（AI 生成、邮件、报表等）
- **优先级调度**: HIGH、NORMAL、LOW 三级优先级
- **实时通知**: WebSocket 推送任务状态和进度
- **离线通知**: Redis 存储离线消息，上线后获取
- **任务重试**: 失败自动重试，最多 3 次
- **配额控制**: 防止资源滥用
- **自动清理**: 定时清理过期任务

### 📝 内容管理

- **文章系统**: 发布、编辑、删除、版本控制
- **分类标签**: 多维度内容组织
- **评论互动**: 评论、回复、点赞、收藏
- **浏览历史**: 用户阅读记录和统计
- **Markdown 支持**: 完整的 Markdown 渲染
- **文章版本**: 历史版本管理和对比

### 🔐 权限管理

- **RBAC 模型**: 基于角色的访问控制
- **细粒度权限**: 菜单权限、操作权限、数据权限
- **JWT 认证**: 无状态认证，支持 Token 刷新
- **权限缓存**: Redis 缓存提升性能
- **动态权限**: 运行时权限变更即时生效

### 💾 文件存储

- **多存储策略**: 本地、MinIO、阿里云 OSS、腾讯云 COS
- **动态切换**: 运行时切换存储策略
- **文件管理**: 上传、下载、删除、预览
- **资源分组**: 文件分组管理
- **缩略图**: 自动生成图片缩略图
- **文件版本**: 文件版本控制

### 🛠️ 系统功能

- **系统监控**: CPU、内存、线程、GC 监控
- **配置管理**: 动态配置，支持热更新
- **数据字典**: 系统字典管理
- **操作日志**: 完整的操作审计
- **IP 定位**: 基于 IP2Region 的地理位置识别
- **验证码**: 图形验证码防护

---

## 🏗️ 技术栈

### 核心框架

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.2.8 | 应用框架 |
| Spring WebFlux | 6.x | 响应式 Web 框架 |
| Spring Data R2DBC | 3.x | 响应式数据库访问 |
| Spring AI | 1.0.0-M5 | AI 集成框架 |
| Project Reactor | 3.x | 响应式编程库 |

### 数据存储

| 技术 | 版本 | 说明 |
|------|------|------|
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 6.0+ | 缓存和消息队列 |
| R2DBC MySQL | 1.0.6 | 响应式 MySQL 驱动 |

### 安全认证

| 技术 | 版本 | 说明 |
|------|------|------|
| Sa-Token | 1.38.0 | 认证授权框架 |
| Sa-Token JWT | 1.38.0 | JWT 支持 |

### 工具库

| 技术 | 版本 | 说明 |
|------|------|------|
| Lombok | 1.18.34 | 简化代码 |
| MapStruct | 1.5.5 | 对象映射 |
| Hutool | 5.8.25 | 工具集 |
| Caffeine | 2.9.3 | 本地缓存 |
| CommonMark | 0.21.0 | Markdown 解析 |

### 存储服务

| 技术 | 版本 | 说明 |
|------|------|------|
| MinIO | 8.5.8 | 对象存储 |
| Aliyun OSS | 3.17.4 | 阿里云存储 |
| Tencent COS | 5.6.155 | 腾讯云存储 |

### 监控文档

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Actuator | 3.x | 应用监控 |
| Micrometer | 1.x | 指标收集 |
| SpringDoc OpenAPI | 2.0.2 | API 文档 |
| Knife4j | 4.1.0 | API 文档 UI |

---

## 🚀 快速开始

### 环境要求

- **JDK**: 17 或更高版本
- **Maven**: 3.8 或更高版本
- **MySQL**: 8.0 或更高版本
- **Redis**: 6.0 或更高版本
- **Docker**: 20.10+ (可选，用于容器化部署)

### 本地开发

#### 1. 克隆项目

```bash
git clone https://github.com/yourusername/ryu-blog.git
cd ryu-blog
```

#### 2. 配置数据库

创建 MySQL 数据库：

```sql
CREATE DATABASE ryu_blog CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

导入初始化脚本：

```bash
mysql -u root -p ryu_blog < src/main/resources/schema.sql
```

#### 3. 配置应用

编辑 `src/main/resources/application-local.yml`：

```yaml
spring:
  r2dbc:
    url: r2dbc:mysql://localhost:3306/ryu_blog
    username: your_username
    password: your_password
  
  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password  # 如果有密码

# AI 配置（可选）
ai:
  openai:
    api-key: your_openai_api_key
    base-url: https://api.openai.com
```

#### 4. 构建运行

```bash
# 编译打包
mvn clean package -DskipTests

# 运行应用
java -jar target/ryu-blog-monolith-1.0-SNAPSHOT.jar

# 或者直接运行
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

#### 5. 访问应用

- **API 接口**: http://localhost:5300/api
- **Knife4j 文档**: http://localhost:5300/doc.html
- **OpenAPI 规范**: http://localhost:5300/v3/api-docs
- **健康检查**: http://localhost:5300/actuator/health

### Docker 部署

#### 使用 Docker Compose（推荐）

```bash
# 启动所有服务（MySQL + Redis + 应用）
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

#### 单独构建镜像

```bash
# 构建镜像
docker build -t ryu-blog:latest .

# 运行容器
docker run -d \
  --name ryu-blog \
  -p 5300:5300 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_R2DBC_URL=r2dbc:mysql://mysql:3306/ryu_blog \
  -e SPRING_R2DBC_USERNAME=root \
  -e SPRING_R2DBC_PASSWORD=your_password \
  -e SPRING_DATA_REDIS_HOST=redis \
  ryu-blog:latest
```

### 多环境配置

项目支持多环境配置：

```bash
# 本地开发环境
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 开发环境
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 生产环境
java -jar target/ryu-blog-monolith-1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

---

## 📚 文档

### 系统文档

- [异步任务系统概览](ASYNC_TASK_SYSTEM_README.md) - 架构和特性介绍
- [异步任务使用指南](ASYNC_TASK_GUIDE.md) - 详细的使用说明
- [AI 生成模式指南](AI_GENERATION_MODES_GUIDE.md) - AI 内容生成使用说明
- [API 完整文档](API_DOCUMENTATION.md) - RESTful API 接口文档
- [任务类型扩展指南](TASK_TYPE_EXTENSION.md) - 如何添加新任务类型
- [快速参考手册](QUICK_REFERENCE.md) - 常用命令速查
- [WebSocket 使用说明](WEBSOCKET_USAGE.md) - 实时通知集成
- [博客提示词模板库](blog-prompt-templates.md) - AI 生成模板参考

### 在线文档

启动应用后访问：

- **Knife4j UI**: http://localhost:5300/doc.html
- **Swagger UI**: http://localhost:5300/swagger-ui.html
- **OpenAPI JSON**: http://localhost:5300/v3/api-docs

---

## 🏛️ 系统架构

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        客户端层                              │
│  Web Browser / Mobile App / Third-party Integration        │
└────────────────────┬────────────────────────────────────────┘
                     │ HTTP/WebSocket
┌────────────────────▼────────────────────────────────────────┐
│                      API Gateway 层                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │  认证    │  │  限流    │  │  日志    │  │  CORS    │   │
│  │ Filter   │  │ Filter   │  │ Filter   │  │ Filter   │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                     Controller 层                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │  文章    │  │  用户    │  │  任务    │  │  AI      │   │
│  │Controller│  │Controller│  │Controller│  │Controller│   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                      Service 层                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │  业务    │  │  AI      │  │  任务    │  │  存储    │   │
│  │  逻辑    │  │  服务    │  │  调度    │  │  策略    │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                   Repository 层                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │  R2DBC   │  │  Redis   │  │  Cache   │  │  Storage │   │
│  │  Repo    │  │  Repo    │  │  Manager │  │  Client  │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                     数据存储层                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │  MySQL   │  │  Redis   │  │  MinIO   │  │  OSS     │   │
│  │  (R2DBC) │  │ (Reactive)│  │  /COS    │  │  /Local  │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 核心模块

#### 1. 用户与权限模块
- 用户注册、登录、信息管理
- 基于 RBAC 的权限控制
- JWT 无状态认证
- 角色和权限动态管理

#### 2. 内容管理模块
- 文章 CRUD 操作
- 分类和标签管理
- 评论和互动（点赞、收藏）
- 文章版本控制
- 浏览历史统计

#### 3. AI 内容生成模块
- 多模式生成（自由/模板/引导）
- 提示词模板管理
- 内容质量分析
- 使用配额管理
- 生成历史记录

#### 4. 异步任务模块
- 通用任务框架
- 优先级队列调度
- WebSocket 实时通知
- 任务重试机制
- 配额和限流控制

#### 5. 文件存储模块
- 多存储策略（本地/MinIO/OSS/COS）
- 文件上传下载
- 资源分组管理
- 缩略图生成
- 文件版本控制

#### 6. 系统管理模块
- 系统配置管理
- 数据字典管理
- 系统监控
- 操作日志
- 健康检查

---

## 📂 项目结构

```
ryu-blog/
├── src/
│   ├── main/
│   │   ├── java/com/ryu/blog/
│   │   │   ├── config/              # 配置类
│   │   │   │   ├── AiConfig.java           # AI 配置
│   │   │   │   ├── SaTokenConfig.java      # 认证配置
│   │   │   │   ├── WebSocketConfig.java    # WebSocket 配置
│   │   │   │   ├── TaskExecutorConfig.java # 任务执行器配置
│   │   │   │   └── ...
│   │   │   ├── controller/          # 控制器层
│   │   │   │   ├── PostsController.java    # 文章接口
│   │   │   │   ├── UserController.java     # 用户接口
│   │   │   │   ├── TaskController.java     # 任务接口
│   │   │   │   ├── AiBlogController.java   # AI 生成接口
│   │   │   │   └── ...
│   │   │   ├── service/             # 服务层
│   │   │   │   ├── impl/                   # 服务实现
│   │   │   │   ├── ai/                     # AI 服务
│   │   │   │   ├── TaskService.java        # 任务服务
│   │   │   │   ├── AiBlogService.java      # AI 博客服务
│   │   │   │   └── ...
│   │   │   ├── repository/          # 数据访问层
│   │   │   │   ├── PostsRepository.java
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── AsyncTaskRepository.java
│   │   │   │   └── ...
│   │   │   ├── entity/              # 实体类
│   │   │   │   ├── Posts.java
│   │   │   │   ├── User.java
│   │   │   │   ├── AsyncTask.java
│   │   │   │   ├── AiContentTemplate.java
│   │   │   │   └── ...
│   │   │   ├── dto/                 # 数据传输对象
│   │   │   ├── vo/                  # 视图对象
│   │   │   ├── enums/               # 枚举类
│   │   │   ├── exception/           # 异常处理
│   │   │   ├── strategy/            # 策略模式（存储策略等）
│   │   │   ├── utils/               # 工具类
│   │   │   └── RyuBlogApplication.java  # 启动类
│   │   └── resources/
│   │       ├── application.yml              # 主配置
│   │       ├── application-local.yml        # 本地环境
│   │       ├── application-dev.yml          # 开发环境
│   │       ├── application-prod.yml         # 生产环境
│   │       ├── application-ai.yml           # AI 配置
│   │       ├── schema.sql                   # 数据库脚本
│   │       ├── db/migration/                # 数据库迁移
│   │       ├── logback-spring.xml           # 日志配置
│   │       └── static/                      # 静态资源
│   └── test/                        # 测试代码
│       └── java/com/ryu/blog/
│           ├── controller/                  # 控制器测试
│           ├── service/                     # 服务测试
│           └── ...
├── logs/                            # 日志文件
├── docs/                            # 文档
│   ├── ASYNC_TASK_SYSTEM_README.md
│   ├── AI_GENERATION_MODES_GUIDE.md
│   ├── API_DOCUMENTATION.md
│   └── ...
├── Dockerfile                       # Docker 构建文件
├── docker-compose.yml               # Docker Compose 配置
├── pom.xml                          # Maven 配置
└── README.md                        # 项目说明
```

---

## 🔌 API 文档

### 核心接口

#### 认证接口

```bash
# 用户登录
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}

# 响应
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userInfo": {
      "id": 1,
      "username": "admin",
      "nickname": "管理员"
    }
  }
}
```

#### 文章接口

```bash
# 获取文章列表
GET /api/posts?page=1&size=10&categoryId=1

# 创建文章
POST /api/posts
Authorization: Bearer {token}
Content-Type: application/json

{
  "title": "Spring Boot 响应式编程",
  "content": "文章内容...",
  "categoryId": 1,
  "tagIds": [1, 2, 3],
  "status": 1
}

# 获取文章详情
GET /api/posts/{id}

# 更新文章
PUT /api/posts/{id}
Authorization: Bearer {token}

# 删除文章
DELETE /api/posts/{id}
Authorization: Bearer {token}
```

#### AI 生成接口

```bash
# 提交 AI 生成任务（自由模式）
POST /api/ai/generate
Authorization: Bearer {token}
Content-Type: application/json

{
  "mode": "free",
  "prompt": "写一篇关于 Spring WebFlux 的技术博客",
  "language": "zh",
  "tone": "professional",
  "length": 2000,
  "style": "tutorial"
}

# 提交 AI 生成任务（模板模式）
POST /api/ai/generate
Authorization: Bearer {token}
Content-Type: application/json

{
  "mode": "template",
  "templateId": 1,
  "templateFields": {
    "topic": "Spring WebFlux",
    "level": "中级",
    "language": "Java",
    "wordCount": "2500"
  }
}

# 获取模板列表
GET /api/ai/templates?type=tutorial

# 内容精炼
POST /api/ai/refine
Authorization: Bearer {token}
Content-Type: application/json

{
  "content": "原始内容...",
  "operation": "EXPAND",
  "targetLength": 3000
}
```

#### 任务接口

```bash
# 提交任务
POST /api/tasks
Authorization: Bearer {token}
Content-Type: application/json

{
  "taskType": "AI_GENERATION",
  "priority": "NORMAL",
  "request": {
    "mode": "free",
    "prompt": "写一篇技术博客"
  }
}

# 查询任务状态
GET /api/tasks/{taskId}
Authorization: Bearer {token}

# 获取任务结果
GET /api/tasks/{taskId}/result
Authorization: Bearer {token}

# 取消任务
POST /api/tasks/{taskId}/cancel
Authorization: Bearer {token}

# 重试任务
POST /api/tasks/{taskId}/retry
Authorization: Bearer {token}

# 获取任务列表
GET /api/tasks?page=1&size=10&status=COMPLETED
Authorization: Bearer {token}
```

#### 文件接口

```bash
# 上传文件
POST /api/files/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: (binary)
groupId: 1

# 下载文件
GET /api/files/{fileId}/download
Authorization: Bearer {token}

# 获取文件列表
GET /api/files?page=1&size=10&groupId=1
Authorization: Bearer {token}

# 删除文件
DELETE /api/files/{fileId}
Authorization: Bearer {token}
```

### WebSocket 接口

```javascript
// 连接 WebSocket（任务通知）
const ws = new WebSocket('ws://localhost:5300/ws/tasks?token=YOUR_JWT_TOKEN');

ws.onmessage = (event) => {
  const notification = JSON.parse(event.data);
  console.log('任务通知:', notification);
  // {
  //   "taskId": 123,
  //   "status": "COMPLETED",
  //   "progress": 100,
  //   "message": "任务完成"
  // }
};
```

---

## 🎨 使用示例

### AI 内容生成完整流程

```bash
# 1. 登录获取 Token
TOKEN=$(curl -X POST http://localhost:5300/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.data.token')

# 2. 查看可用模板
curl http://localhost:5300/api/ai/templates \
  -H "Authorization: Bearer $TOKEN"

# 3. 提交 AI 生成任务
TASK_ID=$(curl -X POST http://localhost:5300/api/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "taskType": "AI_GENERATION",
    "priority": "NORMAL",
    "request": {
      "mode": "template",
      "templateId": 1,
      "templateFields": {
        "topic": "Spring WebFlux",
        "level": "中级",
        "language": "Java",
        "wordCount": "2500"
      }
    }
  }' | jq -r '.data.id')

# 4. 查询任务状态
curl http://localhost:5300/api/tasks/$TASK_ID \
  -H "Authorization: Bearer $TOKEN"

# 5. 获取生成结果
curl http://localhost:5300/api/tasks/$TASK_ID/result \
  -H "Authorization: Bearer $TOKEN"

# 6. 将生成的内容发布为文章
curl -X POST http://localhost:5300/api/posts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Spring WebFlux 深度解析",
    "content": "生成的内容...",
    "categoryId": 1,
    "tagIds": [1, 2],
    "status": 1
  }'
```

---

## 🔧 配置说明

### 数据库配置

```yaml
spring:
  r2dbc:
    url: r2dbc:mysql://localhost:3306/ryu_blog?useSSL=false
    username: root
    password: your_password
    pool:
      initial-size: 10
      max-size: 50
      max-idle-time: 30m
```

### Redis 配置

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: your_password
      database: 0
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
```

### AI 配置

```yaml
ai:
  # OpenAI 配置
  openai:
    api-key: sk-xxx
    base-url: https://api.openai.com
    model: gpt-4
    temperature: 0.7
    max-tokens: 4000
  
  # Azure OpenAI 配置
  azure:
    api-key: your-azure-key
    endpoint: https://your-resource.openai.azure.com
    deployment-name: gpt-4
  
  # Anthropic Claude 配置
  anthropic:
    api-key: sk-ant-xxx
    model: claude-3-opus-20240229
```

### 存储配置

```yaml
storage:
  # 默认存储策略
  default-strategy: local
  
  # 本地存储
  local:
    base-path: /data/uploads
    url-prefix: http://localhost:5300/files
  
  # MinIO 配置
  minio:
    endpoint: http://localhost:9000
    access-key: minioadmin
    secret-key: minioadmin
    bucket: ryu-blog
  
  # 阿里云 OSS
  aliyun:
    endpoint: oss-cn-hangzhou.aliyuncs.com
    access-key-id: your-access-key
    access-key-secret: your-secret
    bucket: ryu-blog
  
  # 腾讯云 COS
  tencent:
    region: ap-guangzhou
    secret-id: your-secret-id
    secret-key: your-secret-key
    bucket: ryu-blog-xxx
```

---

## 🧪 测试

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=TaskControllerIntegrationTest

# 跳过测试构建
mvn clean package -DskipTests
```

### 测试覆盖率

```bash
# 生成测试覆盖率报告
mvn clean test jacoco:report

# 查看报告
open target/site/jacoco/index.html
```

---

## 📊 监控与运维

### 健康检查

```bash
# 应用健康状态
curl http://localhost:5300/actuator/health

# 详细健康信息
curl http://localhost:5300/actuator/health/details
```

### 系统监控

```bash
# 系统信息
curl http://localhost:5300/api/monitor/system \
  -H "Authorization: Bearer $TOKEN"

# 线程信息
curl http://localhost:5300/api/monitor/threads \
  -H "Authorization: Bearer $TOKEN"

# GC 信息
curl http://localhost:5300/api/monitor/gc \
  -H "Authorization: Bearer $TOKEN"
```

### 日志管理

日志文件位置：`logs/ryu-blog.log`

```bash
# 查看实时日志
tail -f logs/ryu-blog.log

# 查看错误日志
grep ERROR logs/ryu-blog.log

# 按日期查看日志
ls logs/ryu-blog.log.*
```

---

## 🚀 性能优化

### 缓存策略

- **本地缓存**: Caffeine 缓存热点数据
- **分布式缓存**: Redis 缓存用户会话、权限信息
- **多级缓存**: 本地缓存 + Redis 缓存
- **缓存预热**: 应用启动时预加载常用数据

### 数据库优化

- **连接池**: R2DBC 连接池配置
- **索引优化**: 关键字段添加索引
- **查询优化**: 避免 N+1 查询
- **批量操作**: 批量插入和更新

### 响应式优化

- **背压处理**: Reactor 背压机制
- **并行处理**: 使用 `flatMap` 并行执行
- **资源管理**: 及时释放资源
- **错误处理**: 优雅的错误处理和重试

---

## 🔐 安全

### 认证授权

- JWT 无状态认证
- Token 过期和刷新机制
- 基于角色的访问控制（RBAC）
- 细粒度权限控制

### 数据安全

- 密码加密存储（BCrypt）
- 敏感信息加密
- SQL 注入防护
- XSS 攻击防护

### 接口安全

- 请求限流
- 接口签名验证
- CORS 跨域配置
- HTTPS 支持

---

## 🤝 贡献

欢迎贡献代码、报告问题或提出建议！

### 贡献流程

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

### 代码规范

- 遵循 Java 编码规范
- 使用 Lombok 简化代码
- 添加必要的注释和文档
- 编写单元测试

---

## 📝 更新日志

### v1.0.0 (2024-01-17)

- ✨ 初始版本发布
- ✨ 完整的博客系统功能
- ✨ AI 内容生成集成
- ✨ 异步任务调度系统
- ✨ 多存储策略支持
- ✨ 完善的权限管理
- ✨ WebSocket 实时通知
- ✨ 系统监控和健康检查

---

## 📄 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

---

## 👥 联系方式

- **作者**: Ryu
- **邮箱**: 475118582@qq.com
- **GitHub**: https://github.com/yourusername/ryu-blog

---

## 🙏 致谢

感谢以下开源项目：

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Spring WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Spring AI](https://spring.io/projects/spring-ai)
- [Project Reactor](https://projectreactor.io/)
- [Sa-Token](https://sa-token.cc/)
- [Hutool](https://hutool.cn/)

---

<div align="center">

**如果这个项目对你有帮助，请给个 ⭐️ Star 支持一下！**

Made with ❤️ by Ryu

</div> 