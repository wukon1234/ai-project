# 智识云 · PaddleOCR 服务

## 本地启动（Mock，无需安装 Paddle）

```bash
cd services/paddle-ocr
pip install -r requirements.txt
set OCR_MOCK=1
uvicorn app:app --host 0.0.0.0 --port 8866
```

## 正式 CPU 版

1. 在 `requirements.txt` 取消注释 `paddlepaddle` / `paddleocr`
2. `pip install -r requirements.txt`
3. `set OCR_MOCK=0` 后启动

## Docker

```bash
docker build -t zhishiyun-paddle-ocr .
docker run --rm -p 8866:8866 -e OCR_MOCK=1 zhishiyun-paddle-ocr
```

## 后端对接

```yaml
kb:
  ocr:
    base-url: http://127.0.0.1:8866
    retry: 3
    page-fail-strategy: fail
```

接口：`GET /health`，`POST /ocr/image`，`POST /ocr/pdf-page`（multipart `file`）。
