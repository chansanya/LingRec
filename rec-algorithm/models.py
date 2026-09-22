from pydantic import BaseModel, Field
from typing import List, Optional

class ProfileItem(BaseModel):
    categoryId: int = Field(..., description="类别ID")
    categoryName: str = Field(..., description="类别名称")
    score: float = Field(..., description="用户对该类别的兴趣得分")

class ResourceItem(BaseModel):
    id: int = Field(..., description="资源ID")
    title: str = Field(..., description="资源标题")
    categoryId: int = Field(..., description="类别ID")
    categoryName: str = Field(..., description="类别名称")
    heat: int = Field(..., description="资源热度")
    createdAt: Optional[str] = Field(None, description="资源创建时间 (ISO格式)")

class RecommendRequest(BaseModel):
    userId: int = Field(..., description="请求推荐的用户ID")
    userProfile: List[ProfileItem] = Field(..., description="用户画像特征")
    candidates: List[ResourceItem] = Field(..., description="候选资源列表")

class RecommendResponse(BaseModel):
    resourceIds: List[int] = Field(..., description="排序后的资源 ID 列表，由前端按需增量展示")
    strategy: str = Field(..., description="使用的推荐策略")
