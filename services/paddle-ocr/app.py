"""PaddleOCR HTTP 服务（CPU）。

启动（开发）:
  cd services/paddle-ocr
  pip install -r requirements.txt
  # 无 GPU / 未装 paddle 时可开 mock：
  set OCR_MOCK=1
  uvicorn app:app --host 0.0.0.0 --port 8866

Docker:
  docker build -t zhishiyun-paddle-ocr .
  docker run --rm -p 8866:8866 -e OCR_MOCK=1 zhishiyun-paddle-ocr

后端配置:
  kb.ocr.base-url=http://127.0.0.1:8866
"""

from __future__ import annotations

import io
import os
from typing import Any, List, Optional

from fastapi import FastAPI, File, HTTPException, UploadFile
from pydantic import BaseModel

app = FastAPI(title="Zhishiyun PaddleOCR", version="1.0.0")

OCR_MOCK = os.getenv("OCR_MOCK", "0") == "1"
_ocr_engine = None


class OcrResponse(BaseModel):
    text: str
    boxes: List[Any] = []
    confidence: float = 0.0


def get_engine():
    global _ocr_engine
    if OCR_MOCK:
        return None
    if _ocr_engine is None:
        try:
            from paddleocr import PaddleOCR  # type: ignore

            _ocr_engine = PaddleOCR(use_angle_cls=True, lang="ch", use_gpu=False, show_log=False)
        except Exception as exc:  # noqa: BLE001
            raise RuntimeError(f"PaddleOCR 初始化失败: {exc}") from exc
    return _ocr_engine


def run_ocr(image_bytes: bytes) -> OcrResponse:
    if OCR_MOCK:
        # 便于本地联调：返回固定可读文本
        return OcrResponse(text="[OCR-MOCK] 扫描件识别文本", boxes=[], confidence=0.88)

    engine = get_engine()
    try:
        from PIL import Image  # type: ignore

        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        result = engine.ocr(image, cls=True)
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=502, detail=f"OCR 识别失败: {exc}") from exc

    lines: List[str] = []
    boxes: List[Any] = []
    scores: List[float] = []
    # paddleocr 返回 [[[box], (text, score)], ...]
    pages = result or []
    for page in pages:
        if not page:
            continue
        for item in page:
            try:
                box, pair = item[0], item[1]
                text, score = pair[0], float(pair[1])
                lines.append(str(text))
                boxes.append(box)
                scores.append(score)
            except Exception:  # noqa: BLE001
                continue
    text = "\n".join(lines).strip()
    confidence = sum(scores) / len(scores) if scores else 0.0
    return OcrResponse(text=text, boxes=boxes, confidence=confidence)


@app.get("/health")
def health():
    return {"status": "UP", "mock": OCR_MOCK}


@app.post("/ocr/image", response_model=OcrResponse)
async def ocr_image(file: UploadFile = File(...)):
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="empty file")
    return run_ocr(data)


@app.post("/ocr/pdf-page", response_model=OcrResponse)
async def ocr_pdf_page(file: UploadFile = File(...)):
    """与 /ocr/image 相同：调用方传入已渲染的页面图片。"""
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="empty file")
    return run_ocr(data)
