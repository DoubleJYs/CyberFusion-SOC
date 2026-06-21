# Upstream Preservation Record

- Upstream repository: `https://github.com/SigmaHQ/sigma`
- Local upstream commit SHA at implementation time:
  `994da16651194500b607a3007186c29779e1f961`
- Upstream license retained in repository root: `LICENSE`
- License name recorded for imported rules: Detection Rule License (DRL) 1.1

## Preservation Policy

- Do not remove or rewrite Sigma upstream license text.
- Keep imported rule records linked to `source_path`, `source_sha`,
  `upstream_sha`, `author`, `date`, `modified`, and raw YAML.
- Keep local modifications as management metadata or version records instead of
  silently changing upstream rule provenance.
- Store uploaded rules, conversion output, logs, SQLite databases, and generated
  reports under `/Users/zhangjiyan/Environment`.

## Rule Source Groups

The importer reads these local source groups when present:

- `rules`
- `rules-threat-hunting`
- `rules-emerging-threats`
- `rules-compliance`
- `rules-placeholder`
- `rules-dfir`
- `unsupported`
- `deprecated`

Unsupported and deprecated rules are imported as governed detection assets; they
are not automatically enabled for production use.
