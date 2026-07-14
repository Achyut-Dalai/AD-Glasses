# MyClone export MVP notes

This document tracks the export format needed by:

- `/home/fertroll10/Documents/ML/BrainResearch/MyClone`
- contract doc: `MyClone/data_contracts/android_export_contract.md`

## Required export bundle layout

```text
export_bundle_YYYYMMDD/
  manifest.json
  events.jsonl
  labels.csv
  screenshots/
  images/
  audio/
  video/
```

## Validation script

Use the local validator in this repo:

```bash
python tools/validate_myclone_export.py --bundle-root /path/to/export_bundle_YYYYMMDD
```

The script checks:

- required files exist
- required manifest keys exist
- required event fields exist
- duplicate/blank `event_id` values

## Interim compatibility path

Until native MyClone bundle export lands in-app, MyClone currently supports converting existing `cyanbridge_backup_v1` zip backups into contract bundles using:

```bash
python /home/fertroll10/Documents/ML/BrainResearch/MyClone/scripts/convert_cyanbridge_backup.py \
  --backup-zip /path/to/cyanbridge_backup.zip \
  --output-bundle /path/to/export_bundle_YYYYMMDD
```

## Supervision actions (new)

For MyClone supervision targets, the app now appends JSONL rows at:

`cyanbridge_backup_v1/files/local_agent_memory/supervision_actions/supervision_actions.jsonl`

Current action types:

- `daily_review_confirm`
- `daily_review_reject`
- `morning_recall_confirm`
- `morning_recall_fail`
- `quick_label`

Each row includes `action_id`, `action_type`, `ts_ms`, and optional target fields (`target_event_ids`, `target_fact_id`, `raw_fact_text`) plus optional quick label fields (`quick_label_name`, `quick_label_value`).

## Export-only decrypted OCR view for MyClone

Backups now also include an export-time, decrypted screen OCR artifact for MyClone ingestion:

`cyanbridge_backup_v1/derived/myclone/screen_captures_decrypted/YYYY-MM-DD.jsonl`

Notes:

- this is generated only during manual backup export
- it does not change regular at-rest vault encryption behavior
- rows come from vault refs with prefix `file:screen_captures/`

## Morning recall review

Morning reminder pipeline now exists:

- reminder receiver: `MorningRecallReminderReceiver`
- scheduler: `MorningRecallReminderScheduler`
- opens `ChatThreadActivity` in morning recall mode with yesterday date prefilled

In morning recall mode, user messages like `remember M1` / `forgot M2` are logged as supervision actions:

- `morning_recall_confirm`
- `morning_recall_fail`
