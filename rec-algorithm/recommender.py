import logging
import random
from datetime import datetime, timezone
from typing import List, Dict, Tuple
from models import RecommendRequest, RecommendResponse, ResourceItem

logger = logging.getLogger(__name__)

class Recommender:
    # 评分权重设定 (Weights for scoring)
    W_INTEREST = 0.4    # 用户兴趣匹配度 (User interest match)
    W_HEAT = 0.25       # 资源热度 (Resource popularity)
    W_FRESHNESS = 0.2   # 资源新鲜度 (How new the resource is)
    W_DIVERSITY = 0.15  # 多样性惩罚 (Diversity penalty for same category)
    
    def recommend(self, request: RecommendRequest) -> RecommendResponse:
        """主入口：召回并排序当前范围内的全部候选资源 ID。"""
        try:
            if not request.candidates:
                logger.warning(f"用户 {request.userId} 的候选资源列表为空")
                return RecommendResponse(resourceIds=[], strategy="empty_candidates")

            if not request.userProfile:
                # 冷启动：返回热门资源 (Cold start: return hot resources)
                logger.info(f"用户 {request.userId} 缺乏画像，使用冷启动策略")
                return self._cold_start(request)
            
            # 1. 召回阶段 (Recall phase)
            recalled = self._recall(request)
            
            # 2. 排序阶段 (Rank phase)
            ranked = self._rank(recalled, request)
            
            # 3. 返回完整排序序列，由前端按批次展示
            top_ids = [r.id for r in ranked]
            return RecommendResponse(resourceIds=top_ids, strategy="personalized")
        except Exception as e:
            logger.error(f"推荐计算过程出错: {e}", exc_info=True)
            # 发生错误时降级返回热门资源 (Fallback to cold start on error)
            return self._cold_start(request)
    
    def _cold_start(self, request: RecommendRequest) -> RecommendResponse:
        """冷启动：对于没有画像的新用户，返回按热度排序的资源"""
        sorted_by_heat = sorted(request.candidates, key=lambda x: x.heat, reverse=True)
        ids = [r.id for r in sorted_by_heat]
        return RecommendResponse(resourceIds=ids, strategy="cold_start_hot")
    
    def _recall(self, request: RecommendRequest) -> List[ResourceItem]:
        """多路召回策略 (Multi-strategy recall)"""
        candidates = request.candidates
        
        # 策略1：兴趣召回 - 匹配用户最感兴趣的类别 (Interest recall)
        top_categories = sorted(request.userProfile, key=lambda x: x.score, reverse=True)
        top_cat_ids = [c.categoryId for c in top_categories[:5]]
        interest_recalled = [r for r in candidates if r.categoryId in top_cat_ids]
        
        # 策略2：热门召回 - 全局热门资源 (Hot recall)
        hot_recalled = sorted(candidates, key=lambda x: x.heat, reverse=True)[:30]
        
        # 策略3：多样性召回 - 从非兴趣类别中探索 (Diversity recall)
        non_interest = [r for r in candidates if r.categoryId not in top_cat_ids]
        diversity_recalled = random.sample(non_interest, min(10, len(non_interest)))
        
        # 合并并去重 (Merge and deduplicate)
        seen = set()
        merged = []
        for r in interest_recalled + hot_recalled + diversity_recalled + candidates:
            if r.id not in seen:
                seen.add(r.id)
                merged.append(r)
        
        logger.debug(f"召回结果: 兴趣={len(interest_recalled)}, 热门={len(hot_recalled)}, 探索={len(diversity_recalled)}, 完整候选={len(candidates)}, 去重后总计={len(merged)}")
        return merged
    
    def _rank(self, candidates: List[ResourceItem], request: RecommendRequest) -> List[ResourceItem]:
        """打分与排序候选集 (Score and rank candidates)"""
        profile_map = {p.categoryId: p.score for p in request.userProfile}
        max_heat = max(r.heat for r in candidates) if candidates else 1
        max_interest = max(profile_map.values()) if profile_map else 1
        
        # 用于多样性惩罚的类别计数 (Track category counts for diversity)
        category_counts: Dict[int, int] = {}
        scored: List[Tuple[float, ResourceItem]] = []
        
        for r in candidates:
            # 兴趣得分 (0-1) (Interest score)
            interest = profile_map.get(r.categoryId, 0) / max_interest if max_interest > 0 else 0
            
            # 热度得分 (0-1) (Heat score)
            heat = r.heat / max_heat if max_heat > 0 else 0
            
            # 新鲜度得分 (0-1) (Freshness score) - 越新得分越高
            freshness = self._calc_freshness(r.createdAt)
            
            # 多样性惩罚 (Diversity penalty)
            cat_count = category_counts.get(r.categoryId, 0)
            diversity_penalty = min(cat_count * 0.1, 0.5)
            category_counts[r.categoryId] = cat_count + 1
            
            # 综合最终得分 (Final score)
            score = (
                self.W_INTEREST * interest +
                self.W_HEAT * heat +
                self.W_FRESHNESS * freshness -
                self.W_DIVERSITY * diversity_penalty
            )
            
            scored.append((score, r))
        
        # 根据得分降序排列 (Sort by score descending)
        scored.sort(key=lambda x: x[0], reverse=True)
        return [r for _, r in scored]
    
    def _calc_freshness(self, created_at_str: str) -> float:
        """计算新鲜度得分 0-1，越新得分越高 (Calculate freshness score 0-1, newer = higher)"""
        if not created_at_str:
            return 0.5
        try:
            # 解析ISO格式，处理可能存在的 'Z'
            # Parse ISO format, handle various formats
            clean_time_str = created_at_str.replace('Z', '+00:00').split('.')[0]
            created = datetime.fromisoformat(clean_time_str)
            
            now = datetime.now(timezone.utc) if created.tzinfo else datetime.now()
            
            days_old = (now - created).days
            days_old = max(0, days_old)  # 防止未来时间导致负数
            
            # 衰减公式: 0天 = 1.0, 30天 = 0.5, 60+天 = ~0.2
            # Decay: 0 days = 1.0, 30 days = 0.5, 60+ days = ~0.2
            return max(0.1, 1.0 / (1.0 + days_old / 30.0))
        except Exception as e:
            logger.debug(f"解析日期异常 '{created_at_str}': {e}")
            return 0.5
