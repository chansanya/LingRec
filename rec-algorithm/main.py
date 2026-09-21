import logging
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from models import RecommendRequest, RecommendResponse
from recommender import Recommender
import uvicorn

# 配置日志 (Configure logging)
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="LingRecSys Algorithm Service", 
    version="1.0.0",
    description="Recommendation Algorithm Service for LingRec"
)

# 允许跨域请求 (CORS middleware)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

recommender = Recommender()

@app.get("/health", summary="健康检查接口 (Health check)")
def health():
    """返回服务状态"""
    return {"status": "ok", "service": "rec-algorithm"}

@app.post("/recommend", response_model=RecommendResponse, summary="获取推荐资源 (Get recommendations)")
def recommend(request: RecommendRequest):
    """
    根据用户画像和候选集返回推荐的资源ID列表。
    (Returns a list of recommended resource IDs based on user profile and candidates)
    """
    logger.info(f"收到推荐请求 | UserID: {request.userId} | 兴趣标签数: {len(request.userProfile)} | 候选资源数: {len(request.candidates)}")
    
    try:
        result = recommender.recommend(request)
        logger.info(f"推荐完成 | 返回 {len(result.resourceIds)} 个资源 | 策略: {result.strategy}")
        return result
    except Exception as e:
        logger.error(f"推荐服务内部错误: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Recommendation algorithm failed")

if __name__ == "__main__":
    # 启动服务 (Start the server)
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=False)
