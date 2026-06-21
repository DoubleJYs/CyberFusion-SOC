# Deployment Notes

## Local Development

```bash
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/05-sigma
python3 sigma_manager/app.py init
python3 sigma_manager/app.py import --limit 300
python3 sigma_manager/app.py serve --host 127.0.0.1 --port 8055
```

The server is intentionally local-only by default. Use a reverse proxy,
authentication, and TLS before exposing it outside the workstation.

## Environment Layout

Create or let the app create these folders:

```text
/Users/zhangjiyan/Environment/02-databases/05-sigma
/Users/zhangjiyan/Environment/13-uploads/05-sigma
/Users/zhangjiyan/Environment/11-logs/05-sigma
/Users/zhangjiyan/Environment/08-docs/05-sigma
```

Override the base Environment root only when needed:

```bash
SIGMA_MANAGER_ENV_ROOT=/path/to/Environment python3 sigma_manager/app.py serve
```

## Operational Checks

```bash
python3 -m py_compile sigma_manager/app.py
python3 sigma_manager/app.py init
python3 sigma_manager/app.py import --limit 20
python3 sigma_manager/app.py import-path rules/application --limit 5
python3 sigma_manager/app.py report --json
```

Zip archives can be imported with:

```bash
python3 sigma_manager/app.py import-path /path/to/sigma-rules.zip
```

## Data Handling

- Do not commit `Environment` runtime data into this repository.
- Do not place SQLite databases, uploads, conversion logs, or generated reports
  under `05-sigma`.
- Review conversion results before deployment to a SIEM platform.
- Keep the default bind address at `127.0.0.1` unless authentication, audit
  retention, TLS, and reverse-proxy controls are added.
