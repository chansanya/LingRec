# LingRec 灵荐推荐系统

一个完整的推荐系统演示项目，包含数据自动生成、用户行为采集、兴趣画像计算、智能推荐和可视化操作面板。

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端 | Spring Boot 2.7.18 / Java 8 |
| 数据访问 | MyBatis-Plus 3.5.7 |
| 数据库 | MySQL 8 |
| 缓存 | Spring Cache |
| 推荐快照 | Redis |
| 算法服务 | Python 3 + FastAPI |
| 前端面板 | Vue 3 + Element Plus + ECharts + Lucide |

## 环境要求

- JDK 8
- Maven 3.8+
- MySQL 8
- Redis
- Python 3.10 或 3.11

## MySQL 配置

默认连接参数：

```text
地址：localhost:3306
数据库：lingrec
用户名：root
密码：root
```

可通过环境变量覆盖：

```text
MYSQL_HOST
MYSQL_PORT
MYSQL_DATABASE
MYSQL_USERNAME
MYSQL_PASSWORD
```

建表脚本保存在：

```text
rec-server/src/main/resources/schema.sql
```

Spring Boot 启动时会自动执行该脚本。连接用户需要具有创建数据库和数据表的权限。也可以先手动执行：

```sql
CREATE DATABASE IF NOT EXISTS lingrec
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
```

然后在 `lingrec` 数据库中执行 `schema.sql`。

## Redis 配置

推荐会话快照存储在 Redis，默认连接参数：

```text
地址：192.168.1.181
端口：6379
数据库：15
密码：1223
```

可通过环境变量覆盖：

```text
REDIS_HOST
REDIS_PORT
REDIS_DATABASE
REDIS_PASSWORD
```

## 快速启动

### 1. 启动 Python 推荐算法服务

```bash
cd rec-algorithm
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
.venv\Scripts\python.exe main.py
```
py
算法服务端口为 `8000`。

### 2. 启动 Spring Boot 后端

在 IDEA 中运行：

```text
com.lingrec.RecServerApplication
```

也可以在命令行运行：

```bash
cd rec-server
mvn spring-boot:run
```

后端端口为 `8080`。

### 3. 打开操作面板

访问：

```text
http://localhost:8080/
```

## 前端静态文件热更新

### IDEA 本地开发

在 Spring Boot 运行配置的 Program arguments 中增加：

```text
--spring.profiles.active=dev
```

并确保 Working directory 为：

```text
D:\\Users\\yf\\IdeaProjects\\LingRec\\rec-server
```

`dev` 环境会直接读取：

```text
src/main/resources/static/
```

修改 HTML、CSS 或 JavaScript 后直接刷新浏览器即可，不需要重新编译或复制到 `target/classes/static`。

### 服务器外置静态目录

应用默认优先读取运行目录下的 `static` 文件夹，没有外部文件时才回退到 JAR 内置资源。推荐部署结构：

```text
lingrec/
├── rec-server.jar
└── static/
    ├── index.html
    ├── style.css
    └── app.js
```

在 `lingrec` 目录中启动 JAR：

```bash
java -jar rec-server.jar
```

之后只需要替换 `static` 目录中的前端文件并刷新浏览器，不需要重新打包或重启服务。静态资源缓存默认关闭。

需要使用其他目录时，可设置：

```text
STATIC_LOCATIONS=file:/opt/lingrec/static/,classpath:/static/
```

Windows 服务器示例：

```text
STATIC_LOCATIONS=file:D:/lingrec/static/,classpath:/static/
```

外部目录必须放在 `classpath:/static/` 前面，保证外部文件优先。

## 项目结构（V3 多模块 SDK 架构）

```text
LingRec/
├── pom.xml                           # 根聚合 POM 与全局依赖管理
├── lingrec-core/                     # 核心契约与 SPI 抽象模块
│   └── src/main/java/com/lingrec/core/
│       ├── client/                   # AlgorithmClient 算法通信契约
│       ├── enums/                    # ActionType 行为权重枚举
│       ├── model/                    # ResourceItem / BehaviorResult 等通用模型
│       └── spi/                      # ResourceItemProvider 全闭环数据源 SPI
├── lingrec-spring-boot-starter/      # 嵌入式推荐引擎 Starter 模块
│   └── src/main/
│       ├── java/com/lingrec/starter/
│       │   ├── config/               # LingRecAutoConfiguration & LingRecProperties
│       │   ├── template/             # LingRecTemplate 面向宿主工程的统一门面
│       │   ├── provider/             # DefaultResourceItemProvider 默认标准表 SPI 实现
│       │   ├── service/              # Behavior / Profile / Recommend 三大核心引擎
│       │   ├── mapper/               # 内置标准表 Mapper
│       │   └── entity/               # 内置标准表持久化实体
│       └── resources/
│           ├── META-INF/             # Spring Boot 自动装配注册文件
│           └── db/schema.sql         # 标准表初始化 DDL
├── rec-server/                       # 演示应用与管理台模块（依赖 Starter）
│   └── src/main/
│       ├── java/com/lingrec/         # REST Controller & 模拟数据生成器
│       └── resources/static/         # Vue 3 + Element Plus + ECharts 操作面板
├── rec-algorithm/                    # 独立部署的 Python 推荐算法服务
│   ├── Dockerfile                    # 生产级多进程容器构建文件
│   ├── docker-compose.yml            # 一键容器化编排文件
│   ├── main.py
│   ├── recommender.py
│   ├── models.py
│   └── requirements.txt
├── docs/                             # 架构与全链路原理文档库
└── README.md
```

## 详细运行与原理文档

- 🌟 [LingRecSys V3 嵌入式 Starter 架构与全闭环 SPI 接入指南](docs/LingRecSys_V3_SDK架构与SPI接入指南.md)（最新 SDK 架构、全闭环 SPI 挂接自有业务表实战、Docker 独立算法部署指南）
- [LingRecSys V2 运行说明与全链路调用原理](docs/LingRecSys_V2_运行说明.md)（包含状态机翻转、分类隔离召回、双级缓存容灾及 Python 排序公式）
- [LingRecSys V1 运行说明](docs/LingRecSys_V1_运行说明.md)

## 功能说明

- **可插拔 Starter 与全闭环 SPI（V3）**：拆分为 `lingrec-core` + `lingrec-spring-boot-starter`，外部 Spring Boot 业务系统既可开箱即用默认表，也可仅实现 `ResourceItemProvider` 接口直接挂载自有的商品/文章/视频表，完整接管召回、详情、热度双向写回与画像重算
- **Python 算法独立容器化（V3）**：提供 `Dockerfile` 与 `docker-compose.yml`，支持多 Worker 独立部署与健康检查，算法离线时 Java 端自动无缝触发热度降级
- **行为状态机翻转（V2）**：点赞（LIKE）与收藏（FAVORITE）支持首次点击记录、二次点击自动取消；取消时自动物理删除记录、精准扣减对应资源热度（保底非负），并实时触发兴趣画像重算与视图 $O(1)$ 同步
- **分类召回严格隔离（V2）**：彻底修复分类筛选下的跨类内容污染；当选择大类或小类时，热门、新鲜以及画像兴趣召回均严格锁定在目标分类范围内，杜绝跨大类渗透
- **双级缓存容灾降级（V2）**：推荐会话快照优先写入 Redis，在 Redis 异常、超时或未配置时自动平滑降级至本地内存会话（`ConcurrentHashMap` + TTL），保证推荐服务 100% 高可用，无 500 报错
- **回到顶部悬浮交互（V2）**：内嵌于推荐卡片面板的微磨砂毛玻璃悬浮按钮，向下滚动超过 150px 柔和淡入，点击平滑回顶
- **数据自动生成**：空数据库启动时自动生成 5 大类、20 小类、200 条资源、10 个模拟用户和 500+ 条行为数据
- **用户切换**：切换不同模拟用户并查看个性化推荐
- **分类拉取**：支持全部资源、大类和小类推荐，例如游戏 / FPS、编程 / Java；服务端先召回有界候选池，结果保存为 Redis 推荐快照
- **游标分页**：首次创建推荐会话返回第一页，滚动到底部时按 sessionId 和 cursor 继续加载下一页，每页 20 条，排序稳定不重复
- **行为记录**：在一个行为面板内通过浏览（VIEW）、点赞（LIKE）、收藏（FAVORITE）标签切换对应记录
- **实时反馈**：行为触发后只更新统计、画像和行为列表；推荐顺序仅通过“刷新推荐”按钮重新创建推荐会话
- **分类降级**：算法服务不可用时，仍在当前分类范围内返回热门资源
- **冷启动**：无行为用户展示当前候选范围内的热门推荐
