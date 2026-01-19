import base64
import json
import os
import re
from pathlib import Path
from typing import List, Optional, Tuple

import httpx
from fastapi import FastAPI, HTTPException, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

try:
    from dotenv import load_dotenv

    root_env = Path(__file__).resolve().parents[1] / ".env"
    server_env = Path(__file__).resolve().parent / ".env"
    load_dotenv(root_env)
    load_dotenv(server_env)
    load_dotenv()
except Exception:
    pass

app = FastAPI(title="Menu Analyzer", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


class Preferences(BaseModel):
    allergies: List[str] = Field(default_factory=list)
    dislikes: List[str] = Field(default_factory=list)
    health_goals: List[str] = Field(default_factory=list)


class AnalyzeRequest(BaseModel):
    text: str
    preferences: Preferences = Field(default_factory=Preferences)


class MenuItem(BaseModel):
    name: str
    ingredients: List[str] = Field(default_factory=list)


class RiskHit(BaseModel):
    term: str
    reason: str
    level: str


class AnalyzeResponse(BaseModel):
    menu_items: List[MenuItem]
    risk_level: str
    hits: List[RiskHit]
    suggestions: List[str]


RISK_ORDER = {"LOW": 0, "MEDIUM": 1, "HIGH": 2}
DEFAULT_IMAGE_PROMPT = (
    "Identify the dishes and ingredients in the food photo. "
    "Return JSON only with the schema: "
    '{"menu_items":[{"name":"...", "ingredients":["..."]}]}'
)
IMAGE_CAPTION_PROMPT = (
    "Describe the dishes and ingredients in the food image. "
    "Keep the response short and factual."
)
TEXT_TO_JSON_PROMPT = (
    "Convert the image description into JSON only. "
    "Schema: {{\"menu_items\":[{{\"name\":\"...\", \"ingredients\":[\"...\"]}}]}}. "
    "Image description: {caption}"
)
ANALYZE_PROMPT = (
    "You are a food safety assistant. "
    "Given the image description and user preferences, extract dishes and ingredients, "
    "determine risk_level (LOW|MEDIUM|HIGH), list hits with term/reason/level, "
    "and give concise suggestions. "
    "Return JSON only with the schema: "
    "{{\"menu_items\":[{{\"name\":\"...\", \"ingredients\":[\"...\"]}}], "
    "\"risk_level\":\"LOW|MEDIUM|HIGH\", "
    "\"hits\":[{{\"term\":\"...\",\"reason\":\"Allergy match|Preference match|Health goal conflict\","
    "\"level\":\"LOW|MEDIUM|HIGH\"}}], "
    "\"suggestions\":[\"...\"]}}. "
    "Image description: {caption}. "
    "Allergies: {allergies}. Dislikes: {dislikes}. Health goals: {goals}."
)


def get_env(name: str, default: str = "") -> str:
    value = os.getenv(name, default)
    if value is None:
        return default
    return value.strip().strip('"').strip("'")


def get_env_float(name: str, default: float) -> float:
    raw = get_env(name)
    if not raw:
        return default
    try:
        value = float(raw)
    except ValueError:
        return default
    if value <= 0:
        return default
    return value


def normalize_term(term: str) -> str:
    return re.sub(r"\s+", "", term.strip().lower())


def normalize_text(text: str) -> str:
    return re.sub(r"\s+", " ", text.lower())


def extract_json_block(text: str) -> Optional[str]:
    start = text.find("{")
    end = text.rfind("}")
    if start == -1 or end == -1 or end <= start:
        return None
    return text[start : end + 1]


def parse_menu_items(data: dict) -> List[MenuItem]:
    items = []
    for raw in data.get("menu_items", []):
        if not isinstance(raw, dict):
            continue
        name = str(raw.get("name", "")).strip()
        ingredients = raw.get("ingredients", [])
        if not isinstance(ingredients, list):
            ingredients = []
        ingredients = [str(item).strip() for item in ingredients if str(item).strip()]
        if name:
            items.append(MenuItem(name=name, ingredients=ingredients))
    return items


def naive_items_from_text(text: str) -> List[MenuItem]:
    cleaned = [line.strip(" \t-•") for line in text.splitlines()]
    cleaned = [line for line in cleaned if line]
    items = []
    for line in cleaned:
        if ":" in line or "：" in line:
            splitter = ":" if ":" in line else "："
            name, rest = line.split(splitter, 1)
            ingredients = [
                item.strip()
                for item in re.split(r"[，,、/]", rest)
                if item.strip()
            ]
            name = name.strip()
            if name:
                items.append(MenuItem(name=name, ingredients=ingredients))
        else:
            items.append(MenuItem(name=line, ingredients=[]))
    if not items and text.strip():
        items.append(MenuItem(name=text.strip()[:80], ingredients=[]))
    return items


def get_timeout_seconds(name: str, default: float) -> float:
    return get_env_float(name, get_env_float("SORUXGPT_TIMEOUT_SECONDS", default))


def sorux_base_url() -> str:
    base = get_env("SORUXGPT_BASE_URL", "https://gpt.soruxgpt.com/api/api/v1")
    return base.rstrip("/")


def extract_sorux_error(data: object) -> Optional[str]:
    if not isinstance(data, dict):
        return None
    error = data.get("error")
    if isinstance(error, dict):
        message = error.get("message")
        if isinstance(message, str) and message.strip():
            return message.strip()
        error_type = error.get("type")
        if isinstance(error_type, str) and error_type.strip():
            return error_type.strip()
    if isinstance(error, str) and error.strip():
        return error.strip()
    message = data.get("message")
    if isinstance(message, str) and message.strip():
        return message.strip()
    return None


def looks_like_missing_image(text: str) -> bool:
    normalized = text.lower().replace("’", "'")
    markers = [
        "don't see an image",
        "do not see an image",
        "can't see the image",
        "cannot see the image",
        "unable to see the image",
        "please upload the image",
        "no image provided",
        "no image was provided",
    ]
    return any(marker in normalized for marker in markers)


def call_sorux_chat(
    messages: List[dict],
    model: str,
    timeout: float
) -> Tuple[Optional[str], Optional[str]]:
    api_key = get_env("SORUXGPT_API_KEY")
    if not api_key:
        return None, "SORUXGPT_API_KEY is not set."
    url = f"{sorux_base_url()}/chat/completions"
    payload = {
        "model": model,
        "messages": messages,
        "temperature": 0.2
    }
    timeout_config = httpx.Timeout(
        timeout,
        connect=10.0,
        read=timeout,
        write=timeout,
        pool=timeout
    )
    try:
        response = httpx.post(
            url,
            headers={"Authorization": f"Bearer {api_key}"},
            json=payload,
            timeout=timeout_config
        )
    except httpx.TimeoutException:
        return None, f"timeout after {timeout}s"
    except Exception as exc:
        return None, str(exc)
    try:
        data = response.json()
    except Exception:
        data = None
    if response.status_code >= 400:
        detail = extract_sorux_error(data)
        return None, f"SoruxGPT {response.status_code}: {detail or response.text}"
    if isinstance(data, dict):
        detail = extract_sorux_error(data)
        if detail:
            return None, detail
        choices = data.get("choices")
        if isinstance(choices, list) and choices:
            message = choices[0].get("message", {})
            content = message.get("content")
            if isinstance(content, str) and content.strip():
                return content.strip(), None
    return None, "SoruxGPT response missing content."


def build_data_url(image_bytes: bytes, mime_type: str) -> str:
    safe_type = mime_type if mime_type else "image/jpeg"
    image_b64 = base64.b64encode(image_bytes).decode("ascii")
    return f"data:{safe_type};base64,{image_b64}"


def call_sorux_for_menu_items(text: str) -> Optional[List[MenuItem]]:
    model = get_env("SORUXGPT_TEXT_MODEL", "gpt-3.5-turbo")
    system_prompt = (
        "You extract menu items and their ingredients from OCR text. "
        "Return JSON only with the schema: "
        '{"menu_items":[{"name":"...", "ingredients":["..."]}]}'
    )
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": text},
    ]
    timeout = get_timeout_seconds("SORUXGPT_TEXT_TIMEOUT_SECONDS", 120.0)
    content, error = call_sorux_chat(messages, model, timeout)
    if error or not content:
        return None
    json_block = extract_json_block(content)
    if not json_block:
        return None
    try:
        data = json.loads(json_block)
        return parse_menu_items(data)
    except Exception:
        return None


def call_sorux_image_caption(
    image_bytes: bytes, mime_type: str
) -> Tuple[Optional[str], Optional[str]]:
    model = get_env(
        "SORUXGPT_IMAGE_MODEL",
        get_env("SORUXGPT_TEXT_MODEL", "gpt-3.5-turbo")
    )
    image_url = build_data_url(image_bytes, mime_type)
    messages = [
        {"role": "system", "content": IMAGE_CAPTION_PROMPT},
        {
            "role": "user",
            "content": [
                {"type": "text", "text": "Describe the food image."},
                {"type": "image_url", "image_url": {"url": image_url}},
            ],
        },
    ]
    timeout = get_timeout_seconds("SORUXGPT_IMAGE_TIMEOUT_SECONDS", 180.0)
    content, error = call_sorux_chat(messages, model, timeout)
    if error or not content:
        return None, error or "SoruxGPT response missing content."
    if looks_like_missing_image(content):
        return None, "Model did not receive image data. Check SORUXGPT_IMAGE_MODEL."
    return content, None


def call_sorux_text_to_json(caption: str) -> Optional[List[MenuItem]]:
    model = get_env("SORUXGPT_TEXT_MODEL", "gpt-3.5-turbo")
    prompt = TEXT_TO_JSON_PROMPT.format(caption=caption)
    messages = [{"role": "user", "content": prompt}]
    timeout = get_timeout_seconds("SORUXGPT_TEXT_TIMEOUT_SECONDS", 120.0)
    content, error = call_sorux_chat(messages, model, timeout)
    if error or not content:
        return None
    json_block = extract_json_block(content)
    if not json_block:
        return None
    try:
        parsed = json.loads(json_block)
        return parse_menu_items(parsed)
    except Exception:
        return None


def call_sorux_image_to_json(
    image_bytes: bytes, mime_type: str, prompt: str
) -> Tuple[Optional[List[MenuItem]], Optional[str]]:
    model = get_env(
        "SORUXGPT_IMAGE_MODEL",
        get_env("SORUXGPT_TEXT_MODEL", "gpt-3.5-turbo")
    )
    image_url = build_data_url(image_bytes, mime_type)
    messages = [
        {"role": "system", "content": prompt},
        {
            "role": "user",
            "content": [
                {"type": "text", "text": "Analyze the food image."},
                {"type": "image_url", "image_url": {"url": image_url}},
            ],
        },
    ]
    timeout = get_timeout_seconds("SORUXGPT_IMAGE_TIMEOUT_SECONDS", 180.0)
    content, error = call_sorux_chat(messages, model, timeout)
    if error or not content:
        return None, error
    if looks_like_missing_image(content):
        return None, "Model did not receive image data. Check SORUXGPT_IMAGE_MODEL."
    json_block = extract_json_block(content)
    if not json_block:
        return None, "SoruxGPT response missing JSON."
    try:
        parsed = json.loads(json_block)
        return parse_menu_items(parsed), None
    except Exception:
        return None, "SoruxGPT response could not be parsed."


def parse_analyze_response(data: object) -> Optional[AnalyzeResponse]:
    if not isinstance(data, dict):
        return None
    menu_items = parse_menu_items(data)
    risk_level = str(data.get("risk_level", "LOW")).upper()
    if risk_level not in RISK_ORDER:
        risk_level = "LOW"
    hits: List[RiskHit] = []
    raw_hits = data.get("hits", [])
    if isinstance(raw_hits, list):
        for raw in raw_hits:
            if not isinstance(raw, dict):
                continue
            term = str(raw.get("term", "")).strip()
            reason = str(raw.get("reason", "")).strip()
            level = str(raw.get("level", "")).upper()
            if not term or level not in RISK_ORDER:
                continue
            hits.append(RiskHit(term=term, reason=reason, level=level))
    suggestions = data.get("suggestions", [])
    if not isinstance(suggestions, list):
        suggestions = []
    suggestions = [str(item) for item in suggestions if str(item).strip()]
    return AnalyzeResponse(
        menu_items=menu_items,
        risk_level=risk_level,
        hits=hits,
        suggestions=suggestions
    )


def call_sorux_text_analyze(
    caption: str, preferences: Preferences
) -> Optional[AnalyzeResponse]:
    model = get_env("SORUXGPT_TEXT_MODEL", "gpt-3.5-turbo")
    allergies = ", ".join(preferences.allergies) or "none"
    dislikes = ", ".join(preferences.dislikes) or "none"
    goals = ", ".join(preferences.health_goals) or "none"
    prompt = ANALYZE_PROMPT.format(
        caption=caption,
        allergies=allergies,
        dislikes=dislikes,
        goals=goals
    )
    messages = [{"role": "user", "content": prompt}]
    timeout = get_timeout_seconds("SORUXGPT_TEXT_TIMEOUT_SECONDS", 120.0)
    content, error = call_sorux_chat(messages, model, timeout)
    if error or not content:
        return None
    json_block = extract_json_block(content)
    if not json_block:
        return None
    try:
        parsed = json.loads(json_block)
    except Exception:
        return None
    return parse_analyze_response(parsed)


def preferences_from_json(raw: str) -> Preferences:
    if not raw.strip():
        return Preferences()
    try:
        data = json.loads(raw)
        return Preferences(**data)
    except Exception:
        return Preferences()


def menu_items_to_text(items: List[MenuItem]) -> str:
    fragments = []
    for item in items:
        fragments.append(item.name)
        if item.ingredients:
            fragments.append(" ".join(item.ingredients))
    return " ".join(fragments)


def collect_hits(
    text: str, items: List[MenuItem], preferences: Preferences
) -> List[RiskHit]:
    flat_text = normalize_text(text)
    flat_haystack = normalize_term(flat_text)
    for item in items:
        flat_haystack += normalize_term(item.name)
        flat_haystack += "".join(normalize_term(ing) for ing in item.ingredients)
    hits: List[RiskHit] = []

    for term in preferences.allergies:
        needle = normalize_term(term)
        if needle and needle in flat_haystack:
            hits.append(RiskHit(term=term, reason="Allergy match", level="HIGH"))

    for term in preferences.dislikes:
        needle = normalize_term(term)
        if needle and needle in flat_haystack:
            hits.append(RiskHit(term=term, reason="Preference match", level="MEDIUM"))

    goal_keywords = {
        "low_sugar": ["sugar", "syrup", "honey", "sweet", "糖", "甜"],
        "low_salt": ["salt", "sodium", "soy", "酱油", "盐"],
        "low_fat": ["oil", "fried", "cream", "butter", "油", "炸", "奶油"],
    }
    for goal in preferences.health_goals:
        key = normalize_term(goal)
        for keyword in goal_keywords.get(key, []):
            if normalize_term(keyword) in flat_haystack:
                hits.append(
                    RiskHit(
                        term=goal,
                        reason="Health goal conflict",
                        level="LOW",
                    )
                )
                break
    return hits


def pick_risk_level(hits: List[RiskHit]) -> str:
    if not hits:
        return "LOW"
    return max(hits, key=lambda hit: RISK_ORDER.get(hit.level, 0)).level


def build_suggestions(hits: List[RiskHit]) -> List[str]:
    if not hits:
        return ["No obvious conflicts found. Consider portion size and ingredients."]
    suggestions = []
    for hit in hits:
        if hit.level == "HIGH":
            suggestions.append(f"Avoid items containing '{hit.term}'.")
        elif hit.level == "MEDIUM":
            suggestions.append(f"Consider skipping items containing '{hit.term}'.")
        else:
            suggestions.append(f"Review items for '{hit.term}' related concerns.")
    return suggestions


@app.post("/analyze", response_model=AnalyzeResponse)
def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    if not request.text.strip():
        raise HTTPException(status_code=400, detail="OCR text is empty.")
    menu_items = call_sorux_for_menu_items(request.text)
    if menu_items is None:
        menu_items = naive_items_from_text(request.text)
    preferences = request.preferences or Preferences()
    hits = collect_hits(request.text, menu_items, preferences)
    risk_level = pick_risk_level(hits)
    suggestions = build_suggestions(hits)
    return AnalyzeResponse(
        menu_items=menu_items,
        risk_level=risk_level,
        hits=hits,
        suggestions=suggestions,
    )


@app.post("/analyze-image", response_model=AnalyzeResponse)
async def analyze_image(
    image: UploadFile = File(...),
    preferences: str = Form("")
) -> AnalyzeResponse:
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Invalid image type.")
    image_bytes = await image.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="Image data is empty.")
    prefs = preferences_from_json(preferences)
    sorux_key = get_env("SORUXGPT_API_KEY")
    if not sorux_key:
        raise HTTPException(
            status_code=501,
            detail="SORUXGPT_API_KEY is not set."
        )

    caption = None
    sorux_error = None
    caption, sorux_error = call_sorux_image_caption(
        image_bytes,
        image.content_type or "application/octet-stream"
    )

    if caption:
        analysis = call_sorux_text_analyze(caption, prefs)
        if analysis:
            return analysis

    menu_items = None
    if caption:
        menu_items = call_sorux_text_to_json(caption)
        if menu_items is None:
            menu_items = naive_items_from_text(caption)
    if menu_items is None:
        menu_items, sorux_error = call_sorux_image_to_json(
            image_bytes=image_bytes,
            mime_type=image.content_type,
            prompt=DEFAULT_IMAGE_PROMPT
        )
    if menu_items is None:
        detail = "Image analysis failed."
        if sorux_error:
            detail = f"{detail} SoruxGPT error: {sorux_error}"
        raise HTTPException(
            status_code=502,
            detail=detail
        )
    text_for_hits = menu_items_to_text(menu_items)
    hits = collect_hits(text_for_hits, menu_items, prefs)
    risk_level = pick_risk_level(hits)
    suggestions = build_suggestions(hits)
    return AnalyzeResponse(
        menu_items=menu_items,
        risk_level=risk_level,
        hits=hits,
        suggestions=suggestions,
    )
