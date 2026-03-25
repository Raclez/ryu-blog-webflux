# Ryu Blog

<div align="center">

现代化响应式博客系统 | AI 内容生成 | 异步任务调度 | 多存储策略

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.8-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[特性](#-核心特性) • [快速开始](#-快速开始) • [文档](#-文档) • [架构](#-系统架构)

</div>

---

## 项目简介

Ryu Blog 是一个基于 Spring WebFlux 的现代化响应式博客系统，采用全链路非阻塞架构，支持高并发、低延迟的内容管理。系统集成了 AI 内容生成能力，提供智能化的博客创作辅助，同时具备完善的权限管理、文件存储、异步任务调度等企业级功能。

### 设计理念

- **响应式优先**: 基于 Spring WebFlux 和 Project Reactor，全链路非阻塞
- **AI 赋能**: 集成 Spring AI，支持多 AI 提供商（OpenAI、Azure、Anthropic、DeepSeek、Gemini、Grok、Qwen）
- **可扩展架构**: 插件化任务类型、存储策略、AI 模板设计
- **生产就绪**: 熔断降级、限流保护、监控指标、健康检查

---

## 核心特性

### AI 内容生成

- **多模式生成**: 自由模式、模板模式、引导模式
- **多提供商支持**: OpenAI、Azure OpenAI、Anthropic Claude、DeepSeek、Gemini、Grok、Qwen
- **模板系统**: 17+ 预置模板（技术教程、问题解决、架构设计等）
- **内容精炼**: 扩展、缩短、改写、优化等操作
- **熔断保护**: Resilience4j 熔断器防止级联失败

### 异步任务系统

- **通用框架**: 支持多种任务类型扩展（AI 生成、邮件、报表等）
- **优先级调度**: HIGH、NORMAL、LOW 三级优先级
- **实时通知**: WebSocket 推送任务状态和进度
- **任务重试**: 失败自动重试
- **配额控制**: 防止资源滥用

### 内容管理

- **文章系统**: 发布、编辑、删除、版本控制
- **分类标签**: 多维度内容组织
- **评论互动**: 评论、回复、点赞、收藏
- **Markdown 支持**: 完整的 Markdown 渲染
- **文章版本**: 历史版本管理和对比

### 文件存储

- **多存储策略**: 本地、MinIO、阿里云 OSS、腾讯云 COS
- **动态切换**: 运行时切换存储策略
- **资源分组**: 文件分组管理
- **缩略图**: 自动生成图片缩略图

### 系统功能

- **RBAC 权限**: 基于角色的访问控制
- **JWT 认证**: 无状态认证
- **系统监控**: CPU、内存、线程、GC 监控
- **操作日志**: 完整的操作审计

---

## 技术栈

### 核心框架

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.2.8 | 应用框架 |
| Spring WebFlux | 6.x | 响应式 Web 框架 |
| Spring Data R2DBC | 3.x | 响应式数据库访问 |
| Spring AI | 1.0.0-M5 | AI 集成框架 |
| Project Reactor | 3.x | 响应式编程库 |

### 数据存储

| 技术 | 说明 |
|------|------|
| MySQL 8.0+ | 关系型数据库 (R2DBC) |
| Redis 6.0+ | 缓存和消息队列 |

### 安全认证

| 技术 | 版本 | 说明 |
|------|------|------|
| Sa-Token | 1.37.0 | 认证授权框架 |
| Sa-Token JWT | 1.38.0 | JWT 支持 |

### 监控防护

| 技术 | 说明 |
|------|------|
| Resilience4j | 熔断器和限流 |
| Spring Actuator | 应用监控 |
| Knife4j / SpringDoc | API 文档 |

---

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+

### 配置

1. 复制配置文件：

```bash
cp .env.example .env
```

2. 编辑 `.env` 配置数据库、Redis、AI API Key 等

### 启动

```bash
# 本地开发
./start.sh local

# 或使用 Maven
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 访问

- API: http://localhost:5300/api
- Knife4j 文档: http://localhost:5300/doc.html
- 健康检查: http://localhost:5300/actuator/health

---

## 项目结构

```
ryu-blog-monolith/
├── src/main/java/com/ryu/blog/
│   ├── config/              # 配置类
│   ├── controller/          # 控制器层
│   ├── service/             # 服务层
│   │   ├── impl/           # 服务实现
│   │   └── ai/             # AI 服务
│   ├── repository/         # 数据访问层
│   ├── entity/             # 实体类
│   ├── dto/                # 数据传输对象
│   ├── vo/                 # 视图对象
│   ├── enums/              # 枚举类
│   ├── strategy/           # 策略模式
│   └── utils/              # 工具类
├── src/main/resources/
│   ├── application.yml     # 主配置
│   ├── application-*.yml   # 环境配置
│   └── db/                 # 数据库脚本
├── start.sh                # 启动脚本
├── .env.example            # 环境变量模板
└── pom.xml
```

---

## 配置说明

### 环境变量 (.env)

```bash
# 数据库
DB_URL=r2dbc:mysql://localhost:3306/ryu_blog
DB_USERNAME=root
DB_PASSWORD=

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT
JWT_SECRET=

# 文件上传
UPLOAD_PATH=./uploads
SERVER_PORT=5300
```

### AI 配置

```yaml
ai:
  openai:
    api-key: sk-xxx
    base-url: https://api.openai.com
  deepseek:
    api-key: xxx
    base-url: https://api.deepseek.com
  anthropic:
    api-key: sk-ant-xxx
```

### 存储配置

```yaml
storage:
  default-strategy: local
  local:
    base-path: ./uploads
    url-prefix: http://localhost:5300/files
  minio:
    endpoint: http://localhost:9000
    access-key: minioadmin
    secret-key: minioadmin
    bucket: ryu-blog
```

---

## 构建

```bash
# 打包
mvn clean package -DskipTests

# 运行
java -jar target/ryu-blog-monolith-1.0-SNAPSHOT.jar
```

---

## 文档

- [异步任务系统概览](ASYNC_TASK_SYSTEM_README.md)
- [异步任务使用指南](ASYNC_TASK_GUIDE.md)
- [AI 生成模式指南](AI_GENERATION_MODES_GUIDE.md)
- [API 完整文档](API_DOCUMENTATION.md)

---

## 许可证

[MIT License](LICENSE)

---

<div align="center">

**如果这个项目对你有帮助，请给个 ⭐️ Star 支持一下！**

Made with ❤️ by Ryu

</div>
