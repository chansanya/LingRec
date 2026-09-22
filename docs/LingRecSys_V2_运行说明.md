# LingRecSys 灵荐推荐系统 V2 运行说明与全链路调用原理

版本：v2.0.0  
日期：2026-09-22  
适用范围：LingRecSys V2 推荐系统演示平台（Java + Python + MySQL + Redis + Vue3）

---

## 一、系统架构与服务职责划分

LingRecSys V2 由四个核心层级组成：**前端交互面板**、**Spring Boot 业务推荐核心**、**持久化与多级会话存储（MySQL / Redis / 本地内存）** 以及 **Python FastAPI 算法打分服务**。

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                 浏览器客户端 (Vue 3 + Element Plus)                          │
│  - 推荐卡片瀑布流 (游标滚动加载)      - 点赞/收藏状态切换 (O(1) 集合判定)   - 回到顶部悬浮球  │
│  - 侧边栏行为历史与统计卡片          - ECharts 兴趣画像实时柱状图         - 分类两级筛选器  │
└───────────────────────────────────────────────┬─────────────────────────────────────────────┘
                                                │ HTTP RESTful API (8899)
                                                ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                 Spring Boot 推荐后端核心 (Java 8)                           │
│  - BehaviorService: 行为状态机翻转 (点赞/收藏二次取消)、热度维护、全量交互 ID 聚合          │
│  - ProfileService: 行为权重动态衰减重算、全量去重分类归一化画像                              │
│  - RecommendService: 分类约束隔离召回 (Scoped Candidate Recall)、双级缓存容灾降级会话池    │
│  - ResourceService: 分类树层级解析、热门候选与新鲜候选多路管道                              │
└───────────────────────┬───────────────────────┬───────────────────────────────┬─────────────┘
                        │                       │                               │
             MyBatis-Plus (MySQL 8)   Jedis/Lettuce + Memory Fallback      HTTP POST (8000)
                        │                       │                               │
                        ▼                       ▼                               ▼
      ┌─────────────────────────┐   ┌─────────────────────────┐   ┌───────────────────────────┐
      │   MySQL 关系数据库      │   │   会话快照与双级缓存    │   │  Python FastAPI 算法服务  │
      │ - users (模拟用户)      │   │ [L1] 本地内存 Map (TTL) │   │ (无状态打分与重排引擎)    │
      │ - category (层级分类)   │   │ [L2] Redis 键值集群     │   │ - 画像余弦向量相似度匹配  │
      │ - resource (资源及热度) │   │ (会话 ID -> 排序序列)   │   │ - 热门热度非线性归一化    │
      │ - user_behavior (明细)  │   │ 保证高并发稳定游标翻页  │   │ - 同类目多样性惩罚重排    │
      │ - user_profile (画像分) │   │ 断网/鉴权失败无缝降级   │   │ 纯计算引擎，不依赖数据库  │
      └─────────────────────────┘   └─────────────────────────┘   └───────────────────────────┘
```

### 服务关键分工原则

1. **Java 后端做“海关”与“事务管理”**：
   - 全面把控数据库读写事务（`@Transactional`）；
   - 严格进行业务约束（如分类范围过滤、防跨类候选污染）；
   - 维护用户行为真实状态与资源即时热度。
2. **Python 算法端做“纯无状态打分器”**：
   - 不直连数据库、不维护会话状态、不保存推荐结果；
   - 仅输入 Java 提供的 `userProfile` 与 `candidates`，输出打分后排好序的 `resourceIds`。
3. **会话存储做“浏览体验防抖”**：
   - 用户滚动翻页时，保持同一次推荐会话内内容顺序不发生“闪烁跳变”；
   - 具备本地内存兜底能力，Redis 发生异常时绝不影响推荐主链路。

---

## 二、点赞与收藏状态机：二次点击取消全链路原理

在 V1 中，行为记录为纯盲目追加模式，造成重复点赞与虚假刷量。V2 彻底重构为**基于状态机翻转（Toggle）的全链路闭环机制**。

### 1. 行为权重与热度规则

系统内置三种标准交互行为：

| 行为类型 | 语义 | 画像计算权重 | 单次增加/扣减资源热度 | 是否允许取消 |
|---|---|---|---|---|
| `VIEW` | 浏览内容 | 1.0 | +1 | 否（历史足迹不可取消） |
| `LIKE` | 赞同内容 | 3.0 | +3 | **是（二次点击取消）** |
| `FAVORITE` | 深度收藏 | 5.0 | +5 | **是（二次点击取消）** |

### 2. 状态切换核心判定流程

当用户点击卡片上的点赞或收藏按钮时，执行如下时序：

```text
客户端点击爱心 (resourceId=91, action=LIKE)
  │
  ├── 1. 前端防御:
  │      检查 submittingActions 锁是否包含 91_LIKE:
  │      - 若已存在: 处于提交中，静默拦截，防止用户连击产生并发脏数据
  │      - 若不存在: 加锁，发起 POST /api/behaviors
  │
  ├── 2. 后端 BehaviorService.persistBehavior(request, refreshProfile=true, allowToggle=true):
  │      校验用户存在性与资源存在性
  │      查询该用户在该资源上的既有行为:
  │      SELECT * FROM user_behavior WHERE user_id = 1 AND resource_id = 91 AND action = 'LIKE'
  │
  │      [分支 A: 已存在记录 (触发二次点击取消)]
  │      ├── 物理删除记录: DELETE FROM user_behavior WHERE user_id = 1 AND resource_id = 91 AND action = 'LIKE'
  │      ├── 扣减资源热度: newHeat = max(0, heat - action.weight * deletedCount)
  │      ├── 触发画像重算: profileService.recalculateProfile(userId)
  │      └── 返回响应: { "status": "CANCELLED", "active": false, "heat": newHeat }
  │
  │      [分支 B: 不存在记录 (触发首次点击记录)]
  │      ├── 插入行为数据: INSERT INTO user_behavior (user_id, resource_id, action, created_at)
  │      ├── 累加资源热度: newHeat = heat + action.weight
  │      ├── 触发画像重算: profileService.recalculateProfile(userId)
  │      └── 返回响应: { "status": "RECORDED", "active": true, "heat": newHeat }
  │
  └── 3. 前端响应与视图同步:
         - 根据 status 更新 Set: likedResourceIds.delete(91) 或 .add(91)
         - 卡片按钮样式即时翻转 (实心高亮 <-> 朴素白底)
         - 提示 Tooltip 翻转 ("取消点赞" <-> "点赞")
         - 动态更新卡片上的火焰热度 badge
         - 弹出轻量级 Toast 提示 ("已取消点赞" / "点赞已记录")
         - 异步触发 loadUserProfile() 与 loadBehaviors() 刷新侧边栏统计与画像柱状图
```

### 3. 全量交互状态的 $O(1)$ 判定优化

- **V1 缺陷**：早期前端通过侧边栏查询接口 `GET /api/behaviors/{userId}?limit=20` 的结果数组做判定。一旦用户点赞超过 20 条，向下滚动无限分页时，超出的卡片无法命中，导致已点赞卡片恢复成灰框。
- **V2 解决**：`/api/users/{id}/profile` 接口统一返回该用户产生过交互的**全量去重资源主键数组**：
  ```json
  {
    "likedResourceIds": [1, 9, 45, 88, 120],
    "favoritedResourceIds": [9, 33],
    "viewedResourceIds": [1, 2, 5, 9, 45, ...]
  }
  ```
  前端将其解析为 JavaScript 原生 `Set<number>`，卡片激活判断 `hasInteracted(id, action)` 达到纯粹的 $O(1)$ 速度，滚动到任意深度均精准展示实心/空心状态。

---

## 三、分类筛选与候选池防跨类污染原理（Scoped Recall）

这是 V2 最核心的推荐精准度重构，彻底解决了**“选了影视短视频，推荐出来的却是游戏攻略”**的行业常见缺陷。

### 1. V1 缺陷病理剖析

在 V1 早期逻辑中，候选集构建分为了热门、新鲜与兴趣偏好三路召回：
```java
// V1 缺陷代码示意
List<Resource> hot = resourceService.getHotCandidates(categoryIds, hotCandidateLimit);       // 受限
List<Resource> fresh = resourceService.getFreshCandidates(categoryIds, freshCandidateLimit);   // 受限
List<Long> interestCategoryIds = resolveInterestCategoryIds(profile);                         // 全局偏好！
List<Resource> interest = resourceService.getInterestCandidates(interestCategoryIds, limit);  // 未受限！
```
当用户画像中最高兴趣为“游戏”（分值为 100 分），即使在前端点击了【影视 -> 短视频】：
- 兴趣召回通道仍然无视当前分类，从全库捞出一批【游戏 / RPG】热门大作塞入候选池；
- Python 排序模型根据用户画像计算余弦得分时，游戏资源的匹配度高达 0.98，短视频匹配度仅 0.1；
- 最终游戏资源强势霸榜，导致分类筛选彻底形同虚设。

### 2. V2 分类范围隔离模型（Scoped Candidate Recall）

V2 引入了严格的**作用域隔离召回策略**：

```text
用户前端触发推荐请求: GET /api/recommend/{userId}?categoryId=20 (短视频)
  │
  ▼
1. 层级范围解析 ResourceService.resolveCategoryIds(categoryId):
   - 若 categoryId 为 null: 返回全部分类 ID（全局推荐模式，isScoped = false）
   - 若 categoryId 为子分类 (如 20 短视频): 返回 [20]（单细分类模式，isScoped = true）
   - 若 categoryId 为父分类 (如 5 影视): 查询其下全部子类，返回 [17, 18, 19, 20]（大类模式，isScoped = true）
  │
  ▼
2. 候选池有界构建 RecommendService.buildCandidatePool:
   ┌────────────────────────────────────────────────────────────────────────┐
   │ [isScoped = true: 细分范围严格召回]                                    │
   │ 1. 热门候选 hot   -> SQL: WHERE category_id IN (20) ORDER BY heat      │
   │ 2. 新鲜候选 fresh -> SQL: WHERE category_id IN (20) ORDER BY created_at│
   │ 3. 兴趣候选 interest -> 计算用户画像兴趣分类与 [20] 的交集:            │
   │    targetInterest = profileCategories ∩ categoryIds                     │
   │    - 若交集不为空: 仅在该细分偏好内召回                                │
   │    - 若交集为空 (用户在该分类无历史): 兜底使用 [20] 本身按热度召回     │
   │    * 绝对禁止跨出 [20] 范围！                                          │
   │                                                                        │
   │ 4. 候选归并强校验 (Double-Check Filter):                               │
   │    for (Resource r : concat(hot, fresh, interest)) {                   │
   │        if (!categoryIds.contains(r.getCategoryId())) continue;         │
   │        merged.add(r);                                                  │
   │    }                                                                   │
   └────────────────────────────────────────────────────────────────────────┘
  │
  ▼
3. 算法打分与重排:
   送入 Python 算法引擎的候选资源 100% 均为短视频分类，算法只能在短视频池子内根据画像调优顺序，
   彻底根除了跨大类内容渗透的问题。
```

---

## 四、推荐会话与双级缓存容灾机制（Redis + In-Memory Fallback）

为了保证用户在单次浏览过程中的稳定性（例如滚动分页时不出现内容重复或顺序跳跃），系统实现了有状态推荐会话。

### 1. 为什么需要会话快照？

推荐算法的计算包含时间衰减、多样性扰动与画像实时变动。如果前端每翻一页都向 Python 重新发起实时打分：
- 前一页刚看过的资源可能因为分数浮动再次出现在第二页；
- 用户在当前页点赞某项资源导致画像改变后，继续向下滚动会导致未看内容顺序剧烈震荡。

因此，**一次推荐生成唯一的 `sessionId`，并持久化完整排序序列**，后续无限滚动分页仅基于游标（`cursor`）和快照切片读取：
```java
// 游标切片公式
int from = Math.min(cursor, total);
int to = Math.min(cursor + size, total);
List<Long> pageIds = orderedIds.subList(from, to);
```

### 2. 双级存储与无缝容灾架构

在实际生产或测试环境中，外部 Redis 容易因网络抖动、端口配置偏差或密码鉴权错误引发异常。V2 引入了 **L1 本地内存 + L2 分布式 Redis 双级缓存架构**：

```text
写入推荐会话 (saveSession):
  │
  ├── 1. 优先写入本地 L1 缓存:
  │      ConcurrentHashMap<sessionId, LocalSession> (内存常驻，自带 System.currentTimeMillis() TTL 淘汰)
  │
  └── 2. 尝试同步写入 L2 Redis:
         try {
             redisTemplate.opsForValue().set(key, joinedIds, 600s);
         } catch (Exception e) {
             log.warn("Redis 离线或鉴权异常，已平滑降级走本地内存存储");
         }

读取推荐分页 (loadSession):
  │
  ├── 1. 尝试从 L2 Redis 读取:
  │      try { value = redisTemplate.opsForValue().get(key); } catch (Exception e) { ... }
  │
  └── 2. 命中校验与本地回退:
         - 若 Redis 读取成功且有效: 解析返回
         - 若 Redis 失败/超时/无数据: 回退检查 L1 本地内存缓存且确认未过期
         - 均未命中: 抛出 404 会话已过期
```

**容灾收益**：即使外部 Redis 发生故障，系统仅打印 Warning 告警，客户端体验零中断，杜绝了“加载推荐失败”的 500 级报错。

---

## 五、用户画像动态重算数学模型

用户画像反映了用户对各个资源分类的偏好程度，存储在 `user_profile` 表中。

### 1. 计算公式

每次产生或取消行为后，系统全量拉取该用户的有效行为历史：

1. **分类加权累加**：
   $$Score_{raw}(c) = \sum_{b \in Behaviors_c} Weight(action_b)$$
   其中：$Weight(VIEW) = 1.0$，$Weight(LIKE) = 3.0$，$Weight(FAVORITE) = 5.0$。

2. **相对偏好归一化（Min-Max Normalization）**：
   为了消除不同用户总行为次数差异对推荐打分尺度的影响，将最高分分类归一化为 100：
   $$MaxScore = \max_{k} Score_{raw}(k)$$
   $$Score_{final}(c) = \left( \frac{Score_{raw}(c)}{MaxScore} \right) \times 100.0$$

### 2. 取消行为对画像的逆向纠偏效应

- 当用户点赞了【编程 / Java】资源：Java 分类总权重增加 3.0，重算后其画像百分比上升；
- 当用户二次点击取消点赞：该条记录从 `user_behavior` 彻底物理抹除，Java 分类总权重扣除 3.0；
- 画像分重新归一化后，前端 ECharts 柱状图实时响应回落，下游推荐候选池中的偏好权重随之修正。

---

## 六、Python 算法端协同机制（FastAPI）

Python 服务运行在 `http://localhost:8000`，提供无状态的 `/recommend` 计算接口。

### 1. 接口协议

- **请求体（`RecommendRequest`）**：
  ```json
  {
    "userId": 1,
    "userProfile": [
      { "categoryId": 1, "categoryName": "FPS", "score": 100.0 },
      { "categoryId": 2, "categoryName": "RPG", "score": 60.0 }
    ],
    "candidates": [
      { "id": 101, "title": "短视频剪辑", "categoryId": 20, "heat": 320, "createdAt": "2026-09-20T10:00:00" },
      ...
    ]
  }
  ```
- **响应体（`RecommendResponse`）**：
  ```json
  {
    "resourceIds": [105, 101, 102, ...],
    "strategy": "personalized"
  }
  ```

### 2. 核心排序打分公式

排序引擎结合了三个核心维度计算综合分数 $S(r)$：

$$S(r) = W_{profile} \cdot S_{match}(r) + W_{heat} \cdot S_{heat}(r) - W_{diversity} \cdot P_{diversity}(r)$$

- **画像匹配分 $S_{match}(r)$**（权重 0.55）：候选资源所在分类在用户画像中的分值映射（0.0 ~ 1.0）；
- **热度非线性归一化分 $S_{heat}(r)$**（权重 0.30）：$\frac{heat_r - heat_{min}}{heat_{max} - heat_{min} + \epsilon}$；
- **同类目聚集惩罚 $P_{diversity}(r)$**（权重 0.15）：若候选资源与已排入前序序列的资源同属一个细分类，施加惩罚以提升前排内容多样性。

---

## 七、交互体验与微动效规范（UI / UX）

1. **纯粹图标规范**：
   - 全系统严格遵照项目准则，界面全程禁止使用 Unicode Emoji 表情；
   - 统一使用 Lucide 矢量图标（如 `<app-icon name="ArrowUp">`、`<app-icon name="Heart">` 等）。
2. **回到顶部悬浮系统**：
   - 容器基于 `.main-content` 相对定位，停驻在推荐卡片面板内右下角（`bottom: 28px; right: 28px;`）；
   - 结合微磨砂毛玻璃效果（`backdrop-filter: blur(8px)`）与柔和双层立体阴影；
   - 监听 `.resource-scroll` 滚动偏移，在 `scrollTop > 150px` 时柔和滑入（Fade + Slide），点击执行原生平滑回顶。
3. **按钮交互防变形设计**：
   - 杜绝在 32px 紧凑圆形按钮内部塞入菊花 loading 旋转动画；
   - 采用纯即时样式高亮切换配合底层静默防抖并发锁，保证极致的操作响应与干净的视觉版式。

---

## 八、常见故障自查指南

| 现象 | 可能原因 | 解决排查步骤 |
|---|---|---|
| **加载推荐失败 (HTTP 500)** | Redis 端口配置错误或密码鉴权失败且未配置容灾 | 确认 `application.yml` 中 Redis 端口为 `6379`，密码为 `1223`；确认已应用 V2 的本地内存兜底代码。 |
| **选了某个分类仍出现其他分类大作** | Java 后端未更新，使用了 V1 跨类兴趣召回 | 确认 `RecommendService.java` 中 `buildCandidatePool` 已加入 `isScoped` 强校验；重启 Java 进程。 |
| **回到顶部按钮不出现** | 页面滚动距离不足或静态资源被浏览器缓存 | 向下滚动超过 150px；按 `Ctrl + F5` 强制刷新加载最新 `app.js` 与 `style.css`。 |
| **算法服务连不上 (Warning 告警)** | Python 8000 端口未启动 | Java 会自动降级按热度排序；启动 `python rec-algorithm/main.py` 即可恢复个性化排序。 |
