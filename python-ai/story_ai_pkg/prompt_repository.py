# Read-only MySQL-backed prompt repository.
# Python only reads prompt templates from the Java-managed MySQL table at runtime.
# Create/update/delete management stays in the Java backend.

from __future__ import annotations

import os
import re
import time
from pathlib import Path
from typing import Any

import yaml
from langchain_core.prompts import PromptTemplate


_CACHE_TTL_SECONDS = 30
_prompt_cache: dict[str, tuple[float, str]] = {}


def load_prompt_content(prompt_key: str, fallback_content: str) -> str:
    """Load a prompt template from MySQL by key, falling back to prompt.py.

    Args:
        prompt_key:        Stable prompt key, such as "generate_story_outline".
        fallback_content:  Existing prompt.py content used when DB is unavailable.

    Returns:
        str: Enabled prompt template content.
    """
    cached = _prompt_cache.get(prompt_key)
    now = time.monotonic()
    if cached and now - cached[0] <= _CACHE_TTL_SECONDS:
        return cached[1]

    try:
        content = _load_prompt_content_from_mysql(prompt_key)
    except Exception as exc:
        print(f"[prompt-repository] fallback to prompt.py for {prompt_key}: {exc}")
        content = fallback_content

    if not content or not content.strip():
        content = fallback_content

    _prompt_cache[prompt_key] = (now, content)
    return content


def get_prompt_template(prompt_key: str, fallback_content: str) -> PromptTemplate:
    """Build a LangChain PromptTemplate from a DB-managed prompt.

    Args:
        prompt_key:       Stable prompt key.
        fallback_content: Existing prompt.py fallback content.

    Returns:
        PromptTemplate: LangChain template object used by chains.
    """
    return PromptTemplate.from_template(load_prompt_content(prompt_key, fallback_content))


def clear_prompt_cache() -> None:
    """Clear the local read cache only.

    This is not prompt management; it never writes to MySQL.
    """
    _prompt_cache.clear()


def _load_prompt_content_from_mysql(prompt_key: str) -> str | None:
    import pymysql

    config = _load_mysql_config()
    config["cursorclass"] = pymysql.cursors.DictCursor
    with pymysql.connect(**config) as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT template_content
                FROM ai_prompts
                WHERE prompt_key = %s AND enabled = 1
                LIMIT 1
                """,
                (prompt_key,),
            )
            row = cursor.fetchone()
            if not row:
                return None
            return row["template_content"]


def _load_mysql_config() -> dict[str, Any]:
    workspace_root = Path(__file__).resolve().parents[2]
    config_path = workspace_root / "config.txt"
    file_config = _load_config_txt(config_path)
    mysql_config = file_config.get("mysql", {}).get("datasource", {})
    jdbc_url = os.getenv("AISAY_MYSQL_URL") or mysql_config.get("url") or ""
    host, port, database = _parse_jdbc_url(jdbc_url)

    return {
        "host": os.getenv("AISAY_MYSQL_HOST") or host or "127.0.0.1",
        "port": int(os.getenv("AISAY_MYSQL_PORT") or port or 3306),
        "user": os.getenv("AISAY_MYSQL_USER") or mysql_config.get("username") or "root",
        "password": os.getenv("AISAY_MYSQL_PASSWORD") or mysql_config.get("password") or "root",
        "database": os.getenv("AISAY_MYSQL_DATABASE") or database or "springcloud",
        "charset": "utf8mb4",
        "connect_timeout": 5,
        "read_timeout": 10,
        "write_timeout": 10,
        "autocommit": True,
    }


def _load_config_txt(config_path: Path) -> dict[str, Any]:
    if not config_path.is_file():
        return {}
    with config_path.open("r", encoding="utf-8") as file:
        return yaml.safe_load(file) or {}


def _parse_jdbc_url(jdbc_url: str) -> tuple[str | None, int | None, str | None]:
    match = re.search(r"jdbc:mysql://([^:/?]+)(?::(\d+))?/([^?]+)", jdbc_url)
    if not match:
        return None, None, None
    host = match.group(1)
    port = int(match.group(2)) if match.group(2) else 3306
    database = match.group(3)
    return host, port, database
