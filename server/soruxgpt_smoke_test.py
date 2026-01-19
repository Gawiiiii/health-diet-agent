import argparse
import base64
import json
import os
import shutil
import subprocess
import tempfile
import time
from pathlib import Path

import httpx

try:
    from dotenv import load_dotenv

    root_env = Path(__file__).resolve().parents[1] / ".env"
    server_env = Path(__file__).resolve().parent / ".env"
    load_dotenv(root_env)
    load_dotenv(server_env)
    load_dotenv()
except Exception:
    pass


IMAGE_CAPTION_PROMPT = (
    "Describe the dishes and ingredients in the food image. "
    "Keep the response short and factual."
)
TEXT_TO_JSON_PROMPT = (
    "Convert the image description into JSON only. "
    "Schema: {{\"menu_items\":[{{\"name\":\"...\", \"ingredients\":[\"...\"]}}]}}. "
    "Image description: {caption}"
)


def get_env(name: str, default: str = "") -> str:
    value = os.getenv(name, default)
    if value is None:
        return default
    return value.strip().strip('"').strip("'")


def find_default_image() -> str | None:
    repo_root = Path(__file__).resolve().parents[1]
    photos_dir = repo_root / "photos"
    if not photos_dir.is_dir():
        return None
    candidates = sorted(photos_dir.glob("*.png"))
    if not candidates:
        candidates = sorted(photos_dir.glob("*.PNG"))
    if candidates:
        return str(candidates[0])
    return None


def build_data_url(image_bytes: bytes, mime_type: str) -> str:
    safe_type = mime_type if mime_type else "image/png"
    image_b64 = base64.b64encode(image_bytes).decode("ascii")
    return f"data:{safe_type};base64,{image_b64}"


def sorux_base_url() -> str:
    base = get_env("SORUXGPT_BASE_URL", "https://gpt.soruxgpt.com/api/api/v1")
    return base.rstrip("/")


def call_sorux_chat(
    messages: list[dict],
    model: str,
    timeout: float
) -> tuple[str | None, str | None]:
    api_key = get_env("SORUXGPT_API_KEY")
    if not api_key:
        return None, "SORUXGPT_API_KEY is not set."
    url = f"{sorux_base_url()}/chat/completions"
    payload = {"model": model, "messages": messages, "temperature": 0.2}
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
        return None, f"SoruxGPT {response.status_code}: {response.text}"
    if isinstance(data, dict):
        choices = data.get("choices")
        if isinstance(choices, list) and choices:
            message = choices[0].get("message", {})
            content = message.get("content")
            if isinstance(content, str) and content.strip():
                return content.strip(), None
    return None, "SoruxGPT response missing content."


def prepare_image_bytes(
    image_path: str,
    max_size: int,
    quality: int
) -> tuple[bytes, str]:
    source = Path(image_path)
    if max_size <= 0 or quality <= 0:
        return source.read_bytes(), "image/png"
    if not shutil.which("sips"):
        print("warning: sips not found; using original image bytes.")
        return source.read_bytes(), "image/png"
    with tempfile.TemporaryDirectory() as temp_dir:
        output_path = Path(temp_dir) / "upload.jpg"
        cmd = [
            "sips",
            "-s",
            "format",
            "jpeg",
            "-s",
            "formatOptions",
            str(quality),
            "-Z",
            str(max_size),
            str(source),
            "--out",
            str(output_path),
        ]
        result = subprocess.run(
            cmd,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        if result.returncode != 0 or not output_path.exists():
            return source.read_bytes(), "image/png"
        return output_path.read_bytes(), "image/jpeg"


def run_image_caption(
    image_path: str,
    model: str,
    timeout: float,
    max_size: int,
    quality: int
) -> str | None:
    image_bytes, mime_type = prepare_image_bytes(
        image_path,
        max_size=max_size,
        quality=quality
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
    start = time.time()
    content, error = call_sorux_chat(messages, model, timeout)
    elapsed = time.time() - start
    if error:
        if "413" in error:
            print("hint: reduce image size via --max-size or --quality")
        print(f"image error={error} elapsed={elapsed:.1f}s")
        return None
    lowered = content.lower().replace("’", "'")
    if (
        "don't see an image" in lowered
        or "do not see an image" in lowered
        or "can't see the image" in lowered
        or "cannot see the image" in lowered
        or "please upload the image" in lowered
    ):
        print(
            "image error=model did not process the image; check --image-model"
        )
        return None
    print(
        f"image ok elapsed={elapsed:.1f}s model={model} bytes={len(image_bytes)}"
    )
    print(f"caption: {content}")
    return content


def run_text_to_json(
    caption: str,
    model: str,
    timeout: float
) -> None:
    prompt = TEXT_TO_JSON_PROMPT.format(caption=caption)
    messages = [{"role": "user", "content": prompt}]
    start = time.time()
    content, error = call_sorux_chat(messages, model, timeout)
    elapsed = time.time() - start
    if error:
        print(f"text error={error} elapsed={elapsed:.1f}s")
        return
    print(f"text ok elapsed={elapsed:.1f}s model={model}")
    try:
        print(json.dumps(json.loads(content), ensure_ascii=True, indent=2))
    except Exception:
        print(content)


def main() -> None:
    parser = argparse.ArgumentParser(description="SoruxGPT Inference smoke test.")
    parser.add_argument("--image", help="Path to image file. Defaults to photos/*.png.")
    parser.add_argument("--image-model", default=get_env("SORUXGPT_IMAGE_MODEL", "gpt-4o-mini"))
    parser.add_argument("--text-model", default=get_env("SORUXGPT_TEXT_MODEL", "gpt-3.5-turbo"))
    parser.add_argument("--timeout", type=float, default=180.0)
    parser.add_argument("--max-size", type=int, default=640)
    parser.add_argument("--quality", type=int, default=70)
    parser.add_argument("--skip-text", action="store_true")
    args = parser.parse_args()

    if not args.image:
        args.image = find_default_image()
        if not args.image:
            raise SystemExit("No PNG found under photos/. Provide --image.")
        print(f"Using default image: {args.image}")

    caption = run_image_caption(
        args.image,
        args.image_model,
        args.timeout,
        args.max_size,
        args.quality
    )
    if caption and not args.skip_text:
        run_text_to_json(caption, args.text_model, args.timeout)


if __name__ == "__main__":
    main()
