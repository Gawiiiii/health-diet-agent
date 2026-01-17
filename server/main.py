import json
import os
import re
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

try:
    from dotenv import load_dotenv

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


def call_openai_for_menu_items(text: str) -> Optional[List[MenuItem]]:
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        return None
    model = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
    system_prompt = (
        "You extract menu items and their ingredients from OCR text. "
        "Return JSON only with the schema: "
        '{"menu_items":[{"name":"...", "ingredients":["..."]}]}'
    )
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": text},
    ]
    try:
        try:
            from openai import OpenAI

            client = OpenAI(api_key=api_key)
            response = client.chat.completions.create(model=model, messages=messages)
            content = response.choices[0].message.content or ""
        except Exception:
            import openai

            openai.api_key = api_key
            response = openai.ChatCompletion.create(model=model, messages=messages)
            content = response.choices[0].message["content"]
        json_block = extract_json_block(content)
        if not json_block:
            return None
        data = json.loads(json_block)
        return parse_menu_items(data)
    except Exception:
        return None


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
    menu_items = call_openai_for_menu_items(request.text)
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
