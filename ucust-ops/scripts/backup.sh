#!/bin/bash
set -euo pipefail

BACKUP_DIR=${BACKUP_DIR:-./backups}
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
mkdir -p "$BACKUP_DIR"

databases=("user_service_db" "business_service_db" "security_service_db")

for db in "${databases[@]}"; do
    echo "Backing up $db..."
    docker exec postgres-db pg_dump -U ETA_DBUser "$db" > "$BACKUP_DIR/${db}_${TIMESTAMP}.sql"
done

echo "Backup completed: $BACKUP_DIR"
