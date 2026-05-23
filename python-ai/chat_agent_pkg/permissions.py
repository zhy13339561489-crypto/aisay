ROLE_LEVELS = {
    "USER": 1,
    "ADMIN": 2,
    "ROOT": 3,
}


def normalize_role(role: str | None) -> str:
    normalized = (role or "USER").strip().upper()
    return normalized if normalized in ROLE_LEVELS else "USER"


def has_permission(user_role: str | None, required_role: str | None) -> bool:
    user_level = ROLE_LEVELS.get(normalize_role(user_role), 1)
    required_level = ROLE_LEVELS.get(normalize_role(required_role), 1)
    return user_level >= required_level


def module_required_role(module: str | None) -> str:
    module_name = (module or "general").strip().lower()
    if module_name == "user_permission":
        return "ROOT"
    if module_name in {"outline_config", "prompt_management"}:
        return "ADMIN"
    return "USER"
