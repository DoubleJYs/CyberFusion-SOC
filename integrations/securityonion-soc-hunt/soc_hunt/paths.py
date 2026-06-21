from __future__ import annotations

import os
from pathlib import Path


DEFAULT_ENV_ROOT = Path("/Users/zhangjiyan/Environment")


class RuntimePaths:
    def __init__(self, env_root: Path | None = None) -> None:
        root = Path(
            env_root
            or os.environ.get("SOC_HUNT_ENV_ROOT")
            or DEFAULT_ENV_ROOT
        )
        self.env_root = root
        self.database_dir = root / "02-databases" / "02-securityonion"
        self.cache_dir = root / "10-cache" / "02-securityonion"
        self.log_dir = root / "11-logs" / "02-securityonion"
        self.upload_dir = root / "13-uploads" / "02-securityonion"
        self.docs_dir = root / "08-docs" / "02-securityonion"
        self.db_path = Path(os.environ.get("SOC_HUNT_DB", self.database_dir / "soc_hunt.sqlite3"))

    def ensure(self) -> None:
        for directory in (
            self.database_dir,
            self.cache_dir,
            self.log_dir,
            self.upload_dir,
            self.docs_dir,
        ):
            directory.mkdir(parents=True, exist_ok=True)
