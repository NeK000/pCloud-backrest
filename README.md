# pCloud Backrest

Production-oriented Java 17 CLI for backing up and restoring a local folder to pCloud with the official pCloud Java SDK.

## Features

- `MODE=backup` uploads a local folder into pCloud.
- `MODE=restore` downloads a pCloud folder into a local folder.
- Recursively preserves relative folder structure.
- Uploads/downloads only changed files using size, timestamp, and SHA-1 checksum when available.
- Streams file transfers through the SDK without loading whole files into memory.
- Continues after individual file failures and exits non-zero if any file failed.
- Optional dry run and guarded restore deletion.
- No token logging.

## Build

Requires Java 17+ and Gradle.

```bash
gradle clean test build
```

The runnable jar is:

```bash
build/libs/app.jar
```

## Environment Variables

Required:

| Variable | Description |
| --- | --- |
| `PCLOUD_ACCESS_TOKEN` | OAuth access token for pCloud. |
| `PCLOUD_REMOTE_FOLDER` | Remote folder, for example `/Backups/Immich`. Must not be `/`. |
| `LOCAL_BACKUP_FOLDER` | Local source/target folder. Dangerous paths like `/`, `/etc`, `/tmp`, `/usr`, and other system roots are rejected. |
| `MODE` | `backup` or `restore`. |

Optional:

| Variable | Default | Description |
| --- | --- | --- |
| `DRY_RUN` | `false` | Log intended changes without changing files. |
| `DELETE_EXTRA_FILES_ON_RESTORE` | `false` | When `true`, restore deletes local files missing from pCloud. Nothing is deleted unless this is explicitly `true`. |
| `LOG_LEVEL` | `INFO` | Logback root level, for example `DEBUG`. |
| `BACKUP_TIMESTAMPED` | `false` | When `true` in backup mode, appends a UTC timestamp folder below `PCLOUD_REMOTE_FOLDER`. |
| `PCLOUD_API_HOST` | SDK default | Optional override, usually `api.pcloud.com` for US or `eapi.pcloud.com` for EU. The SDK handles standard host use; set this only if needed. |

## Examples

Backup:

```bash
MODE=backup \
PCLOUD_ACCESS_TOKEN=xxx \
PCLOUD_REMOTE_FOLDER=/Backups/Immich \
LOCAL_BACKUP_FOLDER=/data/photos \
java -jar build/libs/app.jar
```

Restore:

```bash
MODE=restore \
PCLOUD_ACCESS_TOKEN=xxx \
PCLOUD_REMOTE_FOLDER=/Backups/Immich \
LOCAL_BACKUP_FOLDER=/data/photos \
java -jar build/libs/app.jar
```

Dry run:

```bash
MODE=backup \
DRY_RUN=true \
PCLOUD_ACCESS_TOKEN=xxx \
PCLOUD_REMOTE_FOLDER=/Backups/Immich \
LOCAL_BACKUP_FOLDER=/data/photos \
java -jar build/libs/app.jar
```

Restore with local cleanup:

```bash
MODE=restore \
DELETE_EXTRA_FILES_ON_RESTORE=true \
PCLOUD_ACCESS_TOKEN=xxx \
PCLOUD_REMOTE_FOLDER=/Backups/Immich \
LOCAL_BACKUP_FOLDER=/data/photos \
java -jar build/libs/app.jar
```

## Docker

Build:

```bash
docker build -t pcloud-backrest .
```

Backup:

```bash
docker run --rm \
  -e MODE=backup \
  -e PCLOUD_ACCESS_TOKEN=xxx \
  -e PCLOUD_REMOTE_FOLDER=/Backups/Immich \
  -e LOCAL_BACKUP_FOLDER=/data/photos \
  -v /path/to/photos:/data/photos \
  pcloud-backrest
```

Restore:

```bash
docker run --rm \
  -e MODE=restore \
  -e PCLOUD_ACCESS_TOKEN=xxx \
  -e PCLOUD_REMOTE_FOLDER=/Backups/Immich \
  -e LOCAL_BACKUP_FOLDER=/data/photos \
  -v /path/to/photos:/data/photos \
  pcloud-backrest
```

The included `docker-compose.yml` shows the same configuration in Compose form.

## Scheduled Container Use

Run this container from cron, systemd timers, Kubernetes CronJobs, or any scheduler. The process exits:

- `0` when the mode completed without file failures.
- `1` for configuration or fatal startup errors.
- `2` when one or more individual file transfers failed after continuing through the remaining files.

For restore jobs, keep `DELETE_EXTRA_FILES_ON_RESTORE=false` unless you intentionally want mirror behavior.
