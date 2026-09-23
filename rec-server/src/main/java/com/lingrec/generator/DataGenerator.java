package com.lingrec.generator;

import com.lingrec.core.enums.ActionType;
import com.lingrec.core.model.BehaviorRequest;
import com.lingrec.mapper.UserMapper;
import com.lingrec.model.entity.User;
import com.lingrec.starter.entity.Category;
import com.lingrec.starter.entity.Resource;
import com.lingrec.starter.mapper.CategoryMapper;
import com.lingrec.starter.mapper.ResourceMapper;
import com.lingrec.starter.service.BehaviorService;
import com.lingrec.starter.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@Order(1)
@ConditionalOnProperty(name = "lingrec.generator.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataGenerator implements CommandLineRunner {

    private final CategoryMapper categoryMapper;
    private final ResourceMapper resourceMapper;
    private final UserMapper userMapper;
    private final BehaviorService behaviorService;
    private final ProfileService profileService;

    private final Random random = new Random();

    /**
     * 在核心数据表为空时生成分类、资源、用户、行为和画像；发现部分残缺数据时终止启动。
     *
     * @param args Spring Boot 传入的命令行参数，本生成器不直接使用
     * @throws IllegalStateException 用户、分类和资源表仅部分存在数据时抛出
     */
    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting Data Generation...");
        long userCount = userMapper.selectCount(null);
        long categoryCount = categoryMapper.selectCount(null);
        long resourceCount = resourceMapper.selectCount(null);
        if (userCount > 0 && categoryCount > 0 && resourceCount > 0) {
            log.info("Data already exists, skipping generation.");
            return;
        }
        if (userCount > 0 || categoryCount > 0 || resourceCount > 0) {
            throw new IllegalStateException("检测到不完整的 Demo 数据，请清空相关表后重新启动");
        }

        Map<String, List<Category>> categories = generateCategories();
        List<Resource> resources = generateResources(categories);
        List<User> users = generateUsers();
        generateBehaviors(users, resources, categories);
        
        for (User user : users) {
            profileService.recalculateProfile(user.getId());
        }

        log.info("Data Generation Completed Successfully!");
    }

    /**
     * 生成五个父分类及其二十个子分类，并设置图标名称和显示顺序。
     *
     * @return 以父分类名称为键、对应子分类列表为值的分类映射
     */
    private Map<String, List<Category>> generateCategories() {
        log.info("Generating Categories...");
        Map<String, String[]> catDef = new LinkedHashMap<>();
        catDef.put("游戏:Gamepad2", new String[]{"FPS", "RPG", "MOBA", "SLG"});
        catDef.put("编程:Code2", new String[]{"Java", "Python", "Go", "JavaScript"});
        catDef.put("设计:Palette", new String[]{"UI设计", "平面设计", "3D建模", "插画"});
        catDef.put("音乐:Music2", new String[]{"流行", "古典", "电子", "摇滚"});
        catDef.put("影视:Clapperboard", new String[]{"电影", "纪录片", "动画", "短视频"});

        Map<String, List<Category>> result = new HashMap<>();

        int parentOrder = 0;
        for (Map.Entry<String, String[]> entry : catDef.entrySet()) {
            String[] parts = entry.getKey().split(":");
            Category parent = Category.builder()
                    .name(parts[0])
                    .icon(parts[1])
                    .sortOrder(parentOrder++)
                    .build();
            categoryMapper.insert(parent);

            List<Category> children = new ArrayList<>();
            int childOrder = 0;
            for (String childName : entry.getValue()) {
                Category child = Category.builder()
                        .name(childName)
                        .parentId(parent.getId())
                        .sortOrder(childOrder++)
                        .build();
                categoryMapper.insert(child);
                children.add(child);
            }
            result.put(parts[0], children);
        }
        return result;
    }

    /**
     * 为每个子分类生成十条资源，并随机设置初始热度和过去九十天内的创建时间。
     *
     * @param categories 父分类名称与子分类列表组成的映射
     * @return 已持久化并生成主键的资源列表
     */
    private List<Resource> generateResources(Map<String, List<Category>> categories) {
        log.info("Generating Resources...");
        List<Resource> resources = new ArrayList<>();
        int idCounter = 1;
        
        Map<String, String[]> resourceTitles = new HashMap<>();
        resourceTitles.put("FPS", new String[]{"CS2 竞技技巧教程", "使命召唤 战术分析", "瓦罗兰特 进阶指南", "守望先锋 上分秘籍", "彩虹六号 攻防思路", "APEX 英雄身法教学", "绝地求生 钢枪教学", "逃离塔科夫 跑刀路线", "战地 风云回顾", "求生之路 合作技巧"});
        resourceTitles.put("RPG", new String[]{"巫师3 剧情解析", "上古卷轴 MOD推荐", "赛博朋克 捏脸指南", "女神异闻录 攻略", "最终幻想 设定集", "原神 探索指南", "崩坏星穹铁道 配队", "塞尔达 呀哈哈收集", "黑暗之魂 受苦指南", "血源诅咒 剧情解析"});
        resourceTitles.put("MOBA", new String[]{"英雄联盟 运营教学", "DOTA2 视野控制", "王者荣耀 英雄克制", "风暴英雄 机制解析", "LOL 大局观", "DOTA 团战思路", "王者 意识提升", "LOL 补刀技巧", "DOTA2 英雄出装", "MOBA 走位教学"});
        resourceTitles.put("SLG", new String[]{"文明6 开局思路", "钢铁雄心 战报", "群星 帝国发展", "三国志 破局指南", "全面战争 阵型推荐", "红警 战术演示", "星际争霸 运营流程", "魔兽争霸 种族对抗", "帝国时代 经济建设", "文明 奇观推荐"});
        resourceTitles.put("Java", new String[]{"Spring Boot 实战入门", "JVM 性能调优指南", "微服务架构设计", "Java 并发编程", "MyBatis 源码剖析", "Spring Cloud 实践", "Java 面试指南", "Netty 网络编程", "Java 设计模式", "Redis 在 Java 中的应用"});
        resourceTitles.put("Python", new String[]{"Python 数据分析", "Django 开发实战", "爬虫技术进阶", "机器学习 入门", "深度学习 框架对比", "FastAPI 高并发", "Python 自动化运维", "Flask Web 开发", "Python 图像处理", "Pandas 数据清洗"});
        resourceTitles.put("Go", new String[]{"Go 语言并发模型", "Gin 框架实战", "Go 微服务开发", "Kubernetes 原理", "Docker 容器化", "Go 内存管理", "Go 网络编程", "Go 性能优化", "Go 项目实战", "Go 面试题解析"});
        resourceTitles.put("JavaScript", new String[]{"Vue3 响应式原理", "React Hooks 详解", "Node.js 异步编程", "TypeScript 进阶", "前端工程化", "Webpack 配置指南", "JavaScript 闭包解析", "CSS 布局技巧", "前端 性能优化", "Vite 原理浅析"});
        resourceTitles.put("UI设计", new String[]{"Figma 组件库搭建", "移动端界面设计规范", "暗色主题设计指南", "B端 产品设计", "交互设计 心理学", "UI 配色技巧", "图标 绘制教程", "动效设计 入门", "设计系统 建设", "排版 基础原则"});
        resourceTitles.put("平面设计", new String[]{"Photoshop 合成教程", "Illustrator 矢量绘图", "海报 设计思路", "字体 设计方法", "Logo 创作过程", "版式 设计解析", "色彩 构成原理", "品牌 视觉设计", "包装 设计分享", "画册 排版技巧"});
        resourceTitles.put("3D建模", new String[]{"Blender 基础教程", "Maya 角色绑定", "C4D 材质渲染", "ZBrush 雕刻技巧", "3ds Max 建筑表现", "Substance 贴图制作", "UE5 场景搭建", "Unity 模型导入", "3D 打印基础", "拓扑 流程解析"});
        resourceTitles.put("插画", new String[]{"Procreate 笔刷推荐", "日系 插画上色", "厚涂 技法分享", "人体 结构解析", "场景 透视基础", "光影 表现手法", "水彩 风格插画", "角色 设计思路", "构图 技巧总结", "速写 练习方法"});
        resourceTitles.put("流行", new String[]{"华语 流行金曲", "欧美 榜单推荐", "K-pop 舞蹈教学", "流行 演唱技巧", "编曲 基础教程", "作词 经验分享", "混音 入门指南", "翻唱 翻车盘点", "现场 演唱会回顾", "独立 音乐人推荐"});
        resourceTitles.put("古典", new String[]{"交响乐 赏析", "钢琴 名曲弹奏", "小提琴 考级指南", "古典 音乐史", "莫扎特 作品解析", "贝多芬 奏鸣曲", "巴赫 赋格曲", "乐理 基础知识", "指挥 艺术浅谈", "歌剧 经典片段"});
        resourceTitles.put("电子", new String[]{"EDM 制作基础", "DJ 打碟教学", "合成器 参数解析", "百大 DJ 现场", "House 音乐推荐", "Techno 风格解析", "Trance 经典曲目", "电子乐 发展史", "音效 制作技巧", "采样 包推荐"});
        resourceTitles.put("摇滚", new String[]{"摇滚 乐队推荐", "电吉他 拨片技巧", "架子鼓 节奏练习", "贝斯 Slap教学", "重金属 风格分类", "朋克 精神解析", "英伦 摇滚回顾", "摇滚 现场震撼", "吉他 效果器搭配", "乐队 排练经验"});
        resourceTitles.put("电影", new String[]{"奥斯卡 获奖影片", "科幻 巨制盘点", "悬疑 烧脑推荐", "喜剧 电影合集", "动作 片打戏解析", "恐怖 片气氛营造", "爱情 电影经典", "导演 风格浅析", "编剧 结构拉片", "影评 写作指南"});
        resourceTitles.put("纪录片", new String[]{"自然 奇观纪录片", "历史 探秘寻踪", "人文 风情展现", "科学 前沿探索", "美食 纪录片推荐", "社会 现象观察", "传记 伟人生平", "太空 探索揭秘", "深海 探险记录", "动物 迁徙壮举"});
        resourceTitles.put("动画", new String[]{"日漫 经典神作", "国漫 崛起之作", "美漫 英雄宇宙", "定格 动画制作", "分镜 设计基础", "原画 师访谈", "声优 配音现场", "三维 动画流程", "二维 骨骼绑定", "动画 剧本创作"});
        resourceTitles.put("短视频", new String[]{"Vlog 拍摄技巧", "剪映 教程分享", "抖音 爆款拆解", "B站 涨粉秘籍", "转场 特效制作", "文案 撰写套路", "手机 摄影指南", "打光 基础布阵", "麦克风 收音测试", "短视频 变现方式"});

        for (List<Category> catList : categories.values()) {
            for (Category cat : catList) {
                String[] titles = resourceTitles.getOrDefault(cat.getName(), new String[]{"通用资源 " + cat.getName()});
                for (int i = 0; i < 10; i++) {
                    String title = i < titles.length ? titles[i] : titles[0] + " " + i;
                    Resource res = Resource.builder()
                            .title(title)
                            .description(title + "的详细介绍和内容。")
                            .categoryId(cat.getId())
                            .coverUrl("https://picsum.photos/seed/" + idCounter + "/300/200")
                            .heat(random.nextInt(491) + 10)
                            .createdAt(LocalDateTime.now().minusDays(random.nextInt(91)))
                            .build();
                    resourceMapper.insert(res);
                    resources.add(res);
                    idCounter++;
                }
            }
        }
        return resources;
    }

    /**
     * 生成具有不同兴趣定位的十个模拟用户，其中最后一个用户用于冷启动演示。
     *
     * @return 已持久化并生成主键的模拟用户列表
     */
    private List<User> generateUsers() {
        log.info("Generating Users...");
        String[][] userDefs = {
                {"游戏达人小明", "Gamepad2"}, {"程序员老张", "Code2"}, {"设计师小美", "Palette"},
                {"音乐发烧友", "Music2"}, {"影视达人", "Clapperboard"}, {"全能学霸", "BookOpen"},
                {"游戏+编程", "Binary"}, {"文艺青年", "Feather"}, {"技术宅", "Wrench"},
                {"新用户小白", "UserRound"}
        };
        
        List<User> users = new ArrayList<>();
        for (String[] def : userDefs) {
            User user = User.builder()
                    .nickname(def[0])
                    .avatar(def[1])
                    .createdAt(LocalDateTime.now().minusDays(random.nextInt(365)))
                    .build();
            userMapper.insert(user);
            users.add(user);
        }
        return users;
    }

    /**
     * 按用户预设兴趣分布生成浏览、点赞和收藏行为；最后一个冷启动用户不生成行为。
     *
     * @param users 已生成的模拟用户列表
     * @param resources 已生成的全部资源列表
     * @param categories 父分类名称与子分类列表组成的映射
     */
    private void generateBehaviors(List<User> users, List<Resource> resources, Map<String, List<Category>> categories) {
        log.info("Generating Behaviors...");
        
        Map<String, List<Resource>> resourcesByParentCat = new HashMap<>();
        for (Map.Entry<String, List<Category>> entry : categories.entrySet()) {
            List<Resource> catResources = new ArrayList<>();
            for (Category c : entry.getValue()) {
                resources.stream().filter(r -> r.getCategoryId().equals(c.getId())).forEach(catResources::add);
            }
            resourcesByParentCat.put(entry.getKey(), catResources);
        }

        for (int i = 0; i < users.size() - 1; i++) { 
            User user = users.get(i);
            int behaviorCount = random.nextInt(21) + 60; 
            
            String primaryCat = "";
            String secondaryCat = "";
            
            switch (i) {
                case 0: primaryCat = "游戏"; break;
                case 1: primaryCat = "编程"; break;
                case 2: primaryCat = "设计"; break;
                case 3: primaryCat = "音乐"; break;
                case 4: primaryCat = "影视"; break;
                case 5: primaryCat = "ALL"; break;
                case 6: primaryCat = "游戏"; secondaryCat = "编程"; break;
                case 7: primaryCat = "设计"; secondaryCat = "音乐"; break; 
                case 8: primaryCat = "编程"; secondaryCat = "游戏"; break;
            }

            for (int j = 0; j < behaviorCount; j++) {
                Resource targetResource = null;
                
                if (primaryCat.equals("ALL")) {
                    targetResource = resources.get(random.nextInt(resources.size()));
                } else {
                    double r = random.nextDouble();
                    if (r < 0.7 && !primaryCat.isEmpty()) {
                        List<Resource> pool = resourcesByParentCat.get(primaryCat);
                        targetResource = pool.get(random.nextInt(pool.size()));
                    } else if (r < 0.9 && !secondaryCat.isEmpty()) {
                        List<Resource> pool = resourcesByParentCat.get(secondaryCat);
                        targetResource = pool.get(random.nextInt(pool.size()));
                    } else {
                        targetResource = resources.get(random.nextInt(resources.size()));
                    }
                }

                ActionType action = ActionType.VIEW;
                double ar = random.nextDouble();
                if (ar > 0.85) action = ActionType.FAVORITE;
                else if (ar > 0.6) action = ActionType.LIKE;

                behaviorService.recordBehaviorWithoutProfileRefresh(BehaviorRequest.builder()
                        .userId(user.getId())
                        .resourceId(targetResource.getId())
                        .action(action.name())
                        .build());
            }
        }
    }
}
