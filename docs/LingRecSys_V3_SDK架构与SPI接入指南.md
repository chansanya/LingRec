# LingRecSys 灵荐推荐系统 V3：嵌入式 Starter 架构与全闭环 SPI 接入指南

版本：v3.0.0（Maven 多模块 SDK 演进版）  
日期：2026-09-23  
适用范围：Spring Boot 业务系统快速集成推荐能力、自定义库表 SPI 扩展及 Python 算法微服务独立部署

---

## 一、V3 架构演进总览

在 V1（基础推荐）与 V2（状态机翻转、分类隔离召回、双级缓存容灾）的基础上，**V3 完成了从“单体演示应用”向“工业级可插拔推荐引擎 SDK（Spring Boot Starter）”的彻底蜕变**。

任何外部 Spring Boot 业务系统（如电商平台、内容社区、短视频应用、知识库系统）只需引入 `lingrec-spring-boot-starter` 依赖，即可在 **不改动自身已有商品/文章表结构** 的前提下，通过全闭环 SPI 接口直接赋予系统千人千面推荐、实时画像重算与状态切换能力。

```text
╔═══════════════════════════════════════════════════════════════════════════════════════════╗
║                        LingRec V3 多模块工程与运行边界架构图                              ║
╚═══════════════════════════════════════════════════════════════════════════════════════════╝

  ┌───────────────────────────────────────────────────────────────────────────────────────┐
  │                 外部宿主 Spring Boot 业务系统 (或内置演示台 rec-server)                 │
  │                                                                                       │
  │   业务 Controller / 业务 Service                                                      │
  │         │                                                                             │
  │         │ @Autowired 直接调用                                                         │
  │         ▼                                                                             │
  │   ╔═══════════════════════════════════════════════════════════════════════════════╗   │
  │   ║                     LingRecTemplate (高阶统一推荐门面)                        ║   │
  │   ╚═════════════╤═════════════════════════════╤═══════════════════════════╤═══════╝   │
  │                 │                             │                           │           │
  │                 ▼                             ▼                           ▼           │
  │      ┌─────────────────────┐       ┌─────────────────────┐     ┌────────────────────┐ │
  │      │   BehaviorService   │       │   ProfileService    │     │  RecommendService  │ │
  │      │ - 点赞/收藏二次取消 │       │ - 行为权重动态累加  │     │ - Scoped 分类隔离  │ │
  │      │ - 交互 ID O(1) 聚合 │       │ - 最高类 100 分归一 │     │ - L1/L2 双级会话池 │ │
  │      └──────────┬──────────┘       └──────────┬──────────┘     └──────────┬─────────┘ │
  │                 │ 1. updateHeat()             │ 2. getByIds()             │ 3. 多路   │
  │                 │    (热度加减回调)           │    getCategoryMetadata()  │    召回   │
  │                 └──────────────────────┐      │      ┌────────────────────┘           │
  │                                        ▼      ▼      ▼                                │
  │   ╔═══════════════════════════════════════════════════════════════════════════════╗   │
  │   ║                ResourceItemProvider (全闭环业务数据源 SPI 契约)               ║   │
  │   ╚═══════════════════════════════════════════╤═══════════════════════════════════╝   │
  │                                               │ @ConditionalOnMissingBean             │
  │                        ┌──────────────────────┴──────────────────────┐                │
  │                        ▼ [模式 A: 默认开箱即用]                      ▼ [模式 B: 自定义]│
  │          ┌───────────────────────────────┐             ┌────────────────────────────┐ │
  │          │  DefaultResourceItemProvider  │             │   宿主自定义 Provider 实现 │ │
  │          │  (操作内置 resource/category) │             │  (直连业务 t_goods/t_video)│ │
  │          └───────────────────────────────┘             └────────────────────────────┘ │
  └───────────────────────────────────────────────────────────────────────────┬───────────┘
                                                                              │
                                                           AlgorithmClient    │ HTTP POST
                                                           (超时自适应 + 降级)│ :8000/recommend
                                                                              ▼
                                                           ┌──────────────────────────────┐
                                                           │  Python 独立算法容器 (Docker) │
                                                           │  - FastAPI + Uvicorn 4-Worker│
                                                           │  - 画像余弦匹配 + 多样性惩罚 │
                                                           │  - 无状态纯计算，不连数据库  │
                                                           └──────────────────────────────┘
```

---

## 二、Maven 多模块职责划分

工程根 `pom.xml`（`com.lingrec:lingrec-parent:2.0.0`）统一管控版本，下设三大 Java 模块与一个独立 Python 服务：

| 模块名称 | 产物类型 | 职责定位与核心组件 |
|---|---|---|
| **`lingrec-core`** | 纯 Java Jar | **核心契约与 SPI 抽象层**（零重型框架依赖）：<br>- 枚举：`ActionType`（VIEW=1.0, LIKE=3.0, FAVORITE=5.0）<br>- 契约模型：`ResourceItem`、`BehaviorRequest`、`BehaviorResult`、`UserProfileDTO`、`RecommendPageResult`<br>- SPI 接口：`ResourceItemProvider`（全闭环业务资源适配器）<br>- 算法客户端契约：`AlgorithmClient` |
| **`lingrec-spring-boot-starter`** | Starter Jar | **嵌入式推荐引擎核心与自动装配层**：<br>- `LingRecAutoConfiguration`：兼容 Spring Boot 2.7+ 与 3.x 的自动配置<br>- `LingRecProperties`：统一绑定 `lingrec.*` 配置树<br>- `LingRecTemplate`：供宿主工程直接注入的统一操作门面<br>- `BehaviorService` / `ProfileService` / `RecommendService`：三大核心引擎<br>- `DefaultResourceItemProvider`：基于内置表的默认 SPI 实现<br>- `DefaultAlgorithmClient`：内置自适应超时的 HTTP 算法通信器 |
| **`rec-server`** | Spring Boot App | **演示工程与可视化管理台**：<br>- 作为纯接入方依赖 `lingrec-spring-boot-starter`<br>- 提供 RESTful API 与 Vue 3 + Element Plus + ECharts 交互面板 |
| **`rec-algorithm`** | Python Docker | **独立部署的算法打分微服务**：<br>- 内置 `Dockerfile` 与 `docker-compose.yml`<br>- 接收画像与候选集，执行特征加权与同类惩罚重排，返回有序 ID 列表 |

---

## 三、全闭环 SPI 设计原理：为什么能彻底解耦业务表？

在普通推荐组件中，往往只有“推荐拉取”做了接口抽象，而“点赞改热度”和“重算兴趣画像”依然写死去查组件自带的数据库表。这会导致宿主工程如果用自己的业务表（如 `t_goods`），点赞后热度不更新、画像算不出分。

V3 的 **`ResourceItemProvider` 实现了读写双向全闭环**，涵盖 **7 个标准方法**，打通了三大引擎的全部数据触点：

### 1. 候选召回与范围解析（服务于 `RecommendService`）
- `List<Long> resolveCategoryIds(Long categoryId)`：给定父分类或子分类 ID，返回其管辖的所有分类 ID 集合。
- `List<ResourceItem> getHotCandidates(List<Long> categoryIds, int limit)`：在指定分类范围内拉取高热度候选资源。
- `List<ResourceItem> getFreshCandidates(List<Long> categoryIds, int limit)`：在指定分类范围内拉取新发布候选资源。
- `List<ResourceItem> getInterestCandidates(List<Long> interestCategoryIds, int limit)`：在交集过滤后的兴趣分类内拉取候选资源。

### 2. 详情组装与画像归类（同时服务于 `RecommendService` 与 `ProfileService`）
- `List<ResourceItem> getByIds(List<Long> resourceIds)`：
  - 在 `RecommendService` 中，用于根据 Python 排好序的 ID 列表回填卡片展示详情；
  - 在 `ProfileService.recalculateProfile()` 中，用于根据用户历史交互过的 `resourceId` 批量查出它们所属的 `categoryId`，进而按小类汇总行为权重分值！

### 3. 热度双向写回通道（服务于 `BehaviorService`）
- `int updateHeat(Long resourceId, int delta)`：
  - 当用户首次点击浏览（`delta = +1`）、点赞（`delta = +3`）、收藏（`delta = +5`）时，引擎自动回调此方法增加业务表热度；
  - 当用户二次点击取消点赞（`delta = -3`）或取消收藏（`delta = -5`）时，引擎自动回调此方法扣减业务表热度，并返回最新热度值给前端实时展示！

### 4. 分类名称元数据回填（服务于 `ProfileService`）
- `ResourceItem getCategoryMetadata(Long categoryId)`：
  - 在 `ProfileService.getUserProfile()` 生成用户画像分值表时，回调此方法获取对应分类的中文名称（`categoryName`）及上级大类名称（`parentCategoryName`），无需读取内置 `category` 表。

---

## 四、外部 Spring Boot 项目集成实战指南

### 步骤 1：引入 Starter 依赖

在宿主 Spring Boot 项目的 `pom.xml` 中加入：

```xml
<dependency>
    <groupId>com.lingrec</groupId>
    <artifactId>lingrec-spring-boot-starter</artifactId>
    <version>2.0.0</version>
</dependency>
```

### 步骤 2：配置 `application.yml`

```yaml
lingrec:
  enabled: true
  algorithm:
    url: http://192.168.1.181:8000     # 独立部署的 Python 算法服务地址
    timeout-ms: 3000                   # 超时阈值，超时或离线时自动触发热度降级
  recommend:
    candidate-pool-limit: 1000         # 候选池最大容量
    hot-candidate-limit: 400           # 热门召回配额
    fresh-candidate-limit: 200         # 新鲜召回配额
    interest-candidate-limit: 300      # 兴趣召回配额
    session-ttl-seconds: 600           # 推荐分页快照存活秒数
    page-size: 20                      # 默认每页大小
  cache:
    prefer-redis: true                 # 优先使用宿主 Redis，故障或未配置时自动降级为本地内存 Map
```

### 步骤 3：选择数据对接模式

#### 模式 A：默认内置标准表模式（零代码开箱即用）
如果你希望直接使用标准表，只需在数据库中执行 `lingrec-spring-boot-starter/src/main/resources/db/schema.sql`（包含 `category`、`resource`、`user_behavior`、`user_profile` 四张表）。Starter 会自动装配 `DefaultResourceItemProvider`，无需编写任何适配器代码。

#### 模式 B：挂载宿主自有业务表（自定义 SPI 模式，推荐生产使用）
假设你的项目已有文章表 `cms_article` 和栏目表 `cms_channel`，只需在数据库中保留轻量行为记录表（`user_behavior` 与 `user_profile`），并在你的项目中编写一个 `@Component` 实现 `ResourceItemProvider`：

```java
package com.example.cms.rec;

import com.lingrec.core.model.ResourceItem;
import com.lingrec.core.spi.ResourceItemProvider;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 挂载自有 cms_article 与 cms_channel 表的推荐数据源适配器
 */
@Component
@RequiredArgsConstructor
public class CmsArticleRecProvider implements ResourceItemProvider {

    private final CmsArticleMapper articleMapper;
    private final CmsChannelMapper channelMapper;

    @Override
    public List<Long> resolveCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return channelMapper.selectAllChannelIds();
        }
        List<Long> subIds = channelMapper.selectSubChannelIds(categoryId);
        return subIds.isEmpty() ? Collections.singletonList(categoryId) : subIds;
    }

    @Override
    public List<ResourceItem> getHotCandidates(List<Long> categoryIds, int limit) {
        return articleMapper.selectTopByViews(categoryIds, limit).stream()
                .map(this::toResourceItem).collect(Collectors.toList());
    }

    @Override
    public List<ResourceItem> getFreshCandidates(List<Long> categoryIds, int limit) {
        return articleMapper.selectLatest(categoryIds, limit).stream()
                .map(this::toResourceItem).collect(Collectors.toList());
    }

    @Override
    public List<ResourceItem> getInterestCandidates(List<Long> interestCategoryIds, int limit) {
        return articleMapper.selectTopByViews(interestCategoryIds, limit).stream()
                .map(this::toResourceItem).collect(Collectors.toList());
    }

    @Override
    public List<ResourceItem> getByIds(List<Long> resourceIds) {
        return articleMapper.selectBatchIds(resourceIds).stream()
                .map(this::toResourceItem).collect(Collectors.toList());
    }

    @Override
    public int updateHeat(Long resourceId, int delta) {
        CmsArticle article = articleMapper.selectById(resourceId);
        if (article == null) return 0;
        int newScore = Math.max(0, article.getHotScore() + delta);
        if (delta != 0) {
            article.setHotScore(newScore);
            articleMapper.updateById(article);
        }
        return newScore;
    }

    @Override
    public ResourceItem getCategoryMetadata(Long categoryId) {
        CmsChannel ch = channelMapper.selectById(categoryId);
        if (ch == null) return null;
        CmsChannel parent = ch.getParentId() != null ? channelMapper.selectById(ch.getParentId()) : null;
        return ResourceItem.builder()
                .categoryId(ch.getId())
                .categoryName(ch.getChannelName())
                .parentCategoryName(parent != null ? parent.getChannelName() : null)
                .build();
    }

    private ResourceItem toResourceItem(CmsArticle a) {
        return ResourceItem.builder()
                .id(a.getId())
                .title(a.getTitle())
                .description(a.getSummary())
                .categoryId(a.getChannelId())
                .coverUrl(a.getCoverImage())
                .heat(a.getHotScore())
                .createdAt(a.getCreateTime())
                .build();
    }
}
```

### 步骤 4：在业务代码中调用 `LingRecTemplate`

```java
@RestController
@RequestMapping("/cms/rec")
@RequiredArgsConstructor
public class CmsRecController {

    private final LingRecTemplate lingRecTemplate;

    // 1. 获取个性化推荐首页（categoryId 为 null 查全站，非 null 严格限定在指定栏目内）
    @GetMapping("/feed")
    public RecommendPageResult getFeed(@RequestParam Long userId,
                                       @RequestParam(required = false) Long channelId) {
        return lingRecTemplate.getRecommendations(userId, channelId);
    }

    // 2. 无限滚动加载下一页
    @GetMapping("/feed/next")
    public RecommendPageResult getNextPage(@RequestParam Long userId,
                                           @RequestParam String sessionId,
                                           @RequestParam int cursor) {
        return lingRecTemplate.getRecommendationPage(userId, sessionId, cursor, 20);
    }

    // 3. 点赞/收藏（首次点击自动记录 + 加热度，二次点击自动取消 + 扣热度 + 重算画像）
    @PostMapping("/interact")
    public BehaviorResult interact(@RequestParam Long userId,
                                   @RequestParam Long articleId,
                                   @RequestParam ActionType action) {
        return lingRecTemplate.recordBehavior(userId, articleId, action);
    }
}
```

---

## 五、Python 算法服务独立部署说明

`rec-algorithm` 已完全容器化，可独立部署在任意 GPU/CPU 计算节点或 Kubernetes 集群中。

### 1. Docker Compose 一键部署（推荐）

进入 `rec-algorithm` 目录执行：

```bash
cd rec-algorithm
docker-compose up -d --build
```

容器内置健康检查探针（每 15 秒自动探测 `http://localhost:8000/health`），默认开启 4 个 Uvicorn 工作进程支撑高并发打分。

### 2. 裸机 / 虚拟环境独立部署

```bash
cd rec-algorithm
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
.venv\Scripts\uvicorn.exe main:app --host 0.0.0.0 --port 8000 --workers 4
```

### 3. 算法服务故障降级保障

当 Python 容器升级重启或网络不可达时，`DefaultAlgorithmClient` 捕获连接超时并返回空序列，`RecommendService` 会**自动无缝切换至作用域内的热度降级排序（Fallback by Heat）**，确保宿主业务系统的推荐接口永不报错、永不空窗。
