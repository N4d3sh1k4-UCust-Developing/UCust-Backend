#!/bin/bash
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <backup_file.sql> [database]"
    echo "Restores a PostgreSQL backup into the specified database."
    exit 1
fi

BACKUP_FILE=$1
DB_NAME=${2:-user_service_db}

if [ ! -f "$BACKUP_FILE" ]; then
    echo "Backup file not found: $BACKUP_FILE"
    exit 1
fi

echo "Restoring $BACKUP_FILE into $DB_NAME..."
docker exec -i postgres-db psql -U ETA_DBUser -d "$DB_NAME" < "$BACKUP_FILE"
echo "Restore completed."
