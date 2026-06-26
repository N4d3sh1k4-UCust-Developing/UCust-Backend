# UCust Ops

Infrastructure stack for UCust microservices.

## Services

| Service    | Internal Port | External Port |
|------------|--------------|---------------|
| PostgreSQL | 5432         | 5440          |
| RabbitMQ   | 5672 / 15672 | 5680 / 15673  |
| MinIO      | 9000 / 9001  | 9020 / 9021   |

## Quick Start

```bash
# Create shared network (one time only)
docker network create ucust-net

# Start infrastructure
docker compose up -d

# Stop
docker compose down
```

## Backup

```bash
./scripts/backup.sh
```

## Environment

Copy `.env.example` to `.env` and adjust credentials before first run.
