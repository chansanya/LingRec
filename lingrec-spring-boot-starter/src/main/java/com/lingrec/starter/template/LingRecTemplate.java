package com.lingrec.starter.template;

import com.lingrec.core.enums.ActionType;
import com.lingrec.core.model.BehaviorRequest;
import com.lingrec.core.model.BehaviorResult;
import com.lingrec.core.model.RecommendPageResult;
import com.lingrec.core.model.UserProfileDTO;

import java.util.List;
import java.util.Map;

/**
 * LingRec 推荐引擎统一业务操作门面接口，供外部 Spring Boot 宿主项目直接注入使用。
 */
public interface LingRecTemplate {
    /**
     * 快捷记录或取消行为（点赞与收藏支持首次记录、二次点击自动取消）。
     *
     * @param userId 用户主键 ID
     * @param resourceId 资源主键 ID
     * @param action 交互行为类型
     * @return 包含状态、激活态与热度分值的结构化结果
     */
    BehaviorResult recordBehavior(Long userId, Long resourceId, ActionType action);

    /**
     * 根据完整请求对象记录或取消行为。
     *
     * @param request 行为请求对象（可携带 cancel 显式指定模式）
     * @return 包含状态、激活态与热度分值的结构化结果
     */
    BehaviorResult recordBehavior(BehaviorRequest request);

    /**
     * 显式取消指定用户的点赞或收藏行为。
     *
     * @param userId 用户主键 ID
     * @param resourceId 资源主键 ID
     * @param action 行为类型（仅支持 LIKE 与 FAVORITE）
     * @return 包含取消状态与扣减后热度的结构化结果
     */
    BehaviorResult cancelBehavior(Long userId, Long resourceId, ActionType action);

    /**
     * 为用户创建推荐会话并拉取第一页推荐结果。
     *
     * @param userId 目标用户主键 ID
     * @param categoryId 可选目标分类主键（为空查询全局推荐）
     * @return 推荐第一页数据与会话 ID
     */
    RecommendPageResult getRecommendations(Long userId, Long categoryId);

    /**
     * 按照推荐会话 ID 与游标偏移量切片读取下一页资源。
     *
     * @param userId 目标用户主键 ID
     * @param sessionId 推荐会话唯一 ID
     * @param cursor 起始游标偏移量
     * @param size 请求加载的条数
     * @return 游标切片分页推荐结果
     */
    RecommendPageResult getRecommendationPage(Long userId, String sessionId, int cursor, int size);

    /**
     * 查询用户小类画像分值列表。
     *
     * @param userId 目标用户主键 ID
     * @return 用户画像分值模型列表
     */
    List<UserProfileDTO> getUserProfile(Long userId);

    /**
     * 查询指定用户产生过交互的全部去重资源 ID 集合（供前端快速判定实心/高亮态）。
     *
     * @param userId 目标用户主键 ID
     * @param action 行为类型
     * @return 去重资源主键列表
     */
    List<Long> getInteractedResourceIds(Long userId, ActionType action);

    /**
     * 统计指定用户各交互类型的累计发生次数。
     *
     * @param userId 目标用户主键 ID
     * @return 行为次数映射
     */
    Map<String, Long> getBehaviorStats(Long userId);
}
