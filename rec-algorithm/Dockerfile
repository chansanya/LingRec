FROM python:3.11-slim

WORKDIR /app

# 设置环境变量，禁用 Python 输出缓冲和 pyc 生成
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1

# 复制依赖定义并安装
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制算法核心源码
COPY main.py models.py recommender.py ./

# 暴露 FastAPI 算法服务端口
EXPOSE 8000

# 健康检查
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1

# 启动多 worker 高性能服务
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "4"]
