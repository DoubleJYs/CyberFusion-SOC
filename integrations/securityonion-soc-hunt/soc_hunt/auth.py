from __future__ import annotations

import os
from dataclasses import dataclass


ROLE_LEVELS = {
    "viewer": 1,
    "analyst": 2,
    "lead": 3,
    "admin": 4,
}


@dataclass(frozen=True)
class Principal:
    actor: str
    role: str
    authenticated: bool


class AuthConfig:
    """Small token/RBAC gate for private deployments.

    `SOC_HUNT_AUTH_MODE=required` enables enforcement. Tokens are supplied as a
    comma-separated `token:role:actor` list in `SOC_HUNT_TOKENS`, for example:
    `viewer-token:viewer:alice,analyst-token:analyst:bob`.
    """

    def __init__(self, mode: str | None = None, token_spec: str | None = None) -> None:
        self.mode = (mode or os.environ.get("SOC_HUNT_AUTH_MODE") or "off").lower()
        self.tokens = parse_tokens(token_spec if token_spec is not None else os.environ.get("SOC_HUNT_TOKENS", ""))

    @property
    def required(self) -> bool:
        return self.mode in {"required", "on", "true", "1"}

    def authenticate(self, authorization_header: str | None) -> Principal | None:
        if not self.required:
            return Principal(actor="local-dev", role="admin", authenticated=False)
        token = bearer_token(authorization_header)
        if not token:
            return None
        return self.tokens.get(token)

    def authorize(self, principal: Principal | None, required_role: str) -> bool:
        if principal is None:
            return False
        return ROLE_LEVELS.get(principal.role, 0) >= ROLE_LEVELS[required_role]


def parse_tokens(spec: str) -> dict[str, Principal]:
    tokens: dict[str, Principal] = {}
    for chunk in spec.split(","):
        chunk = chunk.strip()
        if not chunk:
            continue
        parts = chunk.split(":")
        if len(parts) != 3:
            continue
        token, role, actor = (part.strip() for part in parts)
        if token and role in ROLE_LEVELS and actor:
            tokens[token] = Principal(actor=actor, role=role, authenticated=True)
    return tokens


def bearer_token(header: str | None) -> str:
    if not header:
        return ""
    scheme, _, token = header.partition(" ")
    if scheme.lower() != "bearer":
        return ""
    return token.strip()
