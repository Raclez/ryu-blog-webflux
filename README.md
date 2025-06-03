# Ryu Blog

一个基于Spring Boot WebFlux和响应式编程的个人博客系统。

## 技术栈

- **后端**：
  - Spring Boot 3.x
  - Spring WebFlux
  - Spring Data R2DBC
  - Spring Data Redis Reactive
  - Sa-Token JWT
  - MySQL 8
  - Redis

- **特性**：
  - 响应式编程
  - 非阻塞I/O
  - JWT无状态认证
  - 缓存优化
  - 统一响应处理
  - 全局异常处理
  - 参数校验
  - API文档（SpringDoc OpenAPI）

## 系统架构

本系统采用单体架构设计，基于响应式编程模型，主要包括以下模块：

- **用户模块**：用户注册、登录、个人信息管理
- **文章模块**：文章的CRUD、分类、标签管理
- **评论模块**：文章评论功能
- **文件模块**：文件上传和管理
- **安全模块**：基于Sa-Token的JWT认证和授权

## 主要功能

- 用户管理
  - 用户注册、登录
  - 用户信息管理
  - 角色权限控制

- 文章管理
  - 文章发布、编辑、删除
  - 文章分类和标签
  - 文章置顶和推荐
  - 文章搜索

- 评论管理
  - 发表评论
  - 回复评论
  - 评论审核

- 文件管理
  - 图片上传
  - 文件存储

## 项目结构

```
ryu-blog/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── ryu/
│   │   │           └── blog/
│   │   │               ├── config/        # 配置类
│   │   │               ├── controller/    # 控制器
│   │   │               ├── entity/        # 实体类
│   │   │               ├── repository/    # 数据访问层
│   │   │               ├── service/       # 服务层
│   │   │               │   └── impl/      # 服务实现
│   │   │               ├── utils/         # 工具类
│   │   │               └── BlogApplication.java  # 启动类
│   │   └── resources/
│   │       ├── application.yml            # 主配置文件
│   │       ├── application-dev.yml        # 开发环境配置
│   │       ├── application-prod.yml       # 生产环境配置
│   │       └── schema.sql                 # 数据库初始化脚本
│   └── test/                              # 测试代码
├── Dockerfile                             # Docker构建文件
├── docker-compose.yml                     # Docker Compose配置
├── pom.xml                                # Maven配置
└── README.md                              # 项目说明
```

## 开发环境

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+
- Docker & Docker Compose（可选）

## 快速开始

### 本地运行

1. 克隆项目
   ```bash
   git clone https://github.com/yourusername/ryu-blog.git
   cd ryu-blog
   ```

2. 配置数据库
   - 创建MySQL数据库 `ryu_blog`
   - 导入 `src/main/resources/schema.sql` 初始化表结构和数据

3. 修改配置
   - 编辑 `src/main/resources/application-dev.yml` 配置数据库连接信息

4. 构建运行
   ```bash
   mvn clean package -DskipTests
   java -jar target/ryu-blog-1.0.0.jar
   ```

5. 访问应用
   - API接口：http://localhost:8080/api
   - Swagger文档：http://localhost:8080/swagger-ui.html

### Docker部署

1. 使用Docker Compose启动
   ```bash
   docker-compose up -d
   ```

2. 访问应用
   - API接口：http://localhost:8080/api
   - Swagger文档：http://localhost:8080/swagger-ui.html

## API文档

项目集成了Swagger文档，启动应用后可通过以下地址访问：

- Swagger UI：http://localhost:8080/swagger-ui.html
- OpenAPI规范：http://localhost:8080/v3/api-docs

## 默认账户

系统初始化后，会创建一个默认的管理员账户：

- 用户名：admin
- 密码：admin123

## 许可证

[MIT License](LICENSE) 