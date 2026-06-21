# Sigma Detection Rule Management Platform

This is a local secondary-development layer on top of the existing SigmaHQ rule
repository in `05-sigma`. It does not replace upstream Sigma files and does not
execute attacks. It manages detection rules, metadata, tests, conversions, and
governance records.

## Runtime Paths

Source code stays in this repository. Runtime data is written to:

- Database: `/Users/zhangjiyan/Environment/02-databases/05-sigma/sigma_manager.sqlite3`
- Uploaded rules: `/Users/zhangjiyan/Environment/13-uploads/05-sigma/rules/`
- Conversion logs/results: `/Users/zhangjiyan/Environment/13-uploads/05-sigma/conversions/`
- Application logs: `/Users/zhangjiyan/Environment/11-logs/05-sigma/sigma_manager.log`
- Generated reports: `/Users/zhangjiyan/Environment/08-docs/05-sigma/`

## Quick Start

```bash
python3 sigma_manager/app.py init
python3 sigma_manager/app.py import --limit 300
python3 sigma_manager/app.py serve --host 127.0.0.1 --port 8055
```

Open `http://127.0.0.1:8055` after the server starts.

## Core Workflow

1. Import Sigma YAML rules from upstream rule directories, a single YAML file,
   a directory, a zip archive, or an uploaded YAML rule in the Environment
   upload area. Imports validate YAML shape and required Sigma fields.
2. Search and classify by title, level, status, tags, author, category,
   `logsource`, and source path.
3. View rule details, raw YAML preview, references, source path, upstream commit
   SHA, license, author, modified date, validation findings, quality findings,
   version history, and version diff.
4. Run non-offensive positive or negative sample-event tests against parsed
   detection selections.
5. Convert a rule into lightweight target-query drafts for Splunk, Lucene, EQL,
   or Sentinel KQL and keep conversion logs in Environment.
6. Move rules through the lifecycle `draft -> pending_review -> published ->
   disabled -> archived`, with approval and audit records.
7. Review governance data: quality score, missing fields, weak descriptions,
   no references, duplicate Sigma IDs, expired rules, approvals, audit logs,
   conversion failures, release distribution, coverage, and generated reports.

## API Summary

- `POST /api/import`: bulk import Sigma rule YAML files; accepts `roots`,
  `path`, or `content_base64` for zip payload import.
- CLI `python3 sigma_manager/app.py import-path <file|directory|zip>` imports
  a single file, directory, or zip archive.
- `GET /api/rules`: search rules.
- `POST /api/rules`: create a managed rule.
- `GET /api/rules/{id}`: view details, YAML, versions, conversions, samples.
- `PUT /api/rules/{id}`: update a managed rule and add a version record.
- `DELETE /api/rules/{id}`: soft delete from management database.
- `POST /api/rules/{id}/enable`: enable or disable a rule.
- `POST /api/rules/{id}/test`: run a sample event test.
- `POST /api/rules/{id}/convert`: validate and draft a target query.
- `POST /api/rules/{id}/approvals`: create an approval record.
- `POST /api/rules/{id}/transition`: move a rule through the release lifecycle.
- `POST /api/rules/{id}/rollback`: restore YAML metadata from a prior version.
- `GET /api/rules/{id}/quality`: return validation and quality findings.
- `GET /api/governance`: quality, duplicate, expiry, and conversion summary.
- `GET /api/audit`: recent audit log entries.
- `POST /api/report`: export a Markdown governance report.

## Scope Boundaries

- The platform only works with detection-rule metadata, matching samples, query
  conversion drafts, and governance records.
- It does not run exploit code, payloads, scanners, or offensive tooling.
- Generated query drafts must be reviewed before being deployed to a real SIEM.
- The standard-library YAML parser is designed for Sigma management metadata and
  validation; production conversion backends should still be calibrated against
  pySigma or a target SIEM parser before live deployment.
