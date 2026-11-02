# GitHub Actions: secrets и variables

Два независимых набора — по репозиториям. Пересекаются только `TS_*`, `SERVER_*`
и пароли инфраструктуры.

| Где | Репозиторий | Workflow | Что делает |
|---|---|---|---|
| **Бэкенд** | `N4d3sh1k4-UCust-Developing/UCust-Backend` | `.github/workflows/main.yml` | собирает 9 образов и деплоит приложение в `~/projects/ucust` |
| **OPS** | `N4d3sh1k4-UCust-Developing/ops` | `.github/workflows/deploy.yml` | поднимает PostgreSQL / RabbitMQ / MinIO, создаёт БД |

Задаются в **Settings → Secrets and variables → Actions** соответствующего
репозитория. Environments не используются.

> Реальные значения секретов в файле не дублируются — только смысл, формат и
> несекретные значения (URL, email). Секреты живут только в GitHub.
>
> ⚠️ Push в `master`/`main` бэкенда запускает пересборку **и деплой** (в
> workflow нет `paths`-фильтра). У ops есть `paths`-фильтр: правка только
> документации деплой не запускает.

---

# Часть 1. Бэкенд (`UCust-Backend`)

## Secrets — обязательные

| Переменная | Где используется | Значение / формат |
|---|---|---|
| `SUBMODULES_PAT` | `actions/checkout` (`submodules: recursive`) | PAT с read-доступом ко всем `backend-*` и сервисным репозиториям организации. Без него сабмодули не склонируются |
| `SERVER_TAILSCALE_IP` | ssh/scp в deploy-джобе (host) | Tailscale-IP сервера, не публичный. В Java-коде не читается |
| `SERVER_USER` | ssh/scp | `n4d3sh1k4` |
| `SERVER_SSH_KEY` | ssh/scp | приватный SSH-ключ (PEM/OpenSSH) |
| `TS_OAUTH_CLIENT_ID` | `tailscale/github-action` | OAuth-клиент Tailscale, тег `tag:ci` |
| `TS_OAUTH_SECRET` | `tailscale/github-action` | секрет того же клиента |
| `POSTGRES_PASSWORD` | security, user, business, billing, gen-ort, content, audit | пароль `ETA_DBUser` (общий на все БД). Должен совпадать с `POSTGRES_PASSWORD` в ops. `DB_PASSWORD` оставлен как fallback в workflow/compose |
| `POSTGRES_USER` | те же сервисы | опционально, дефолт `ETA_DBUser` (совпадает с ops) |
| `RABBITMQ_USER` | security, user, business, notification, billing, gen-ort, audit | опционально, дефолт `service-user` |
| `RABBITMQ_PASS` | те же сервисы | пароль `service-user`; дефолт `servicepassword`. Должен совпадать с `RABBITMQ_PASS` в ops |
| `JWT_SECRET_ACCESS` | gateway, security, billing, gen-ort | база подписи access-токенов, base64, одинаковая во всех сервисах |
| `DEFAULT_ADMIN_EMAIL` | security | `superadmin@adminparadise.ru` — админ создаётся при первом старте |
| `DEFAULT_ADMIN_PASSWORD` | security | пароль дефолтного админа; применяется только при создании |
| `YANDEX_CLIENT_ID` / `YANDEX_CLIENT_SECRET` | security | OAuth Яндекса (веб + mobile) |
| `VK_CLIENT_ID` / `VK_CLIENT_SECRET` | security | OAuth VK |
| `GEO_API_URL` | security | `http://ip-api.com/json/{ip}?lang=ru&fields=status,country,city` — claim `city` |
| `INTERNAL_API_SECRET` | gen-ort (исходящие к AI + входящий callback), billing (`/internal/quota/*`), AI-контур | общий S2S-секрет `X-Internal-Secret`. Должен **совпадать** с `.env` AI-контура. Пустой = 401 на `/internal/**` и блокировка квот |
| `AI_ORCHESTRATOR_ENDPOINT` | gen-ort | база Единого Шлюза: `http://10.0.0.2:8000` (без пути) |
| `SUPPORT_EMAIL` | notification | `support@ucust.com` |
| `FRONTEND_URL` | notification | `https://ucust.ru` — ссылки в письмах |
| `MAIL_HOST` | notification | `smtp.gmail.com` |
| `MAIL_PORT` | notification | `587` |
| `MAIL_USERNAME` | notification | логин SMTP (полный адрес) |
| `MAIL_PASSWORD` | notification | пароль приложения SMTP |
| `MAIL_SSL` | notification | `false` (для 587 — STARTTLS) |
| `MAIL_FROM` | notification | `contact@n4d3sh1k4.site` |
| `MAIL_FROM_NAME` | notification | отображаемое имя, дефолт `UCust` |
| `CONTENT_MINIO_ACCESS_KEY` | content-service | `content-service-user` |
| `CONTENT_MINIO_SECRET_KEY` | content-service | пароль этого юзера (`ct-strong-pass` в `minio-init`) |
| `CONTENT_MINIO_PUBLIC_BASE_URL` | content-service | `https://api.ucust.n4d3sh1k4.site/s3/content-service` |
| `MINIO_ACCESS_KEY` | gen-ort (медиа постов) | `generative-orchestration-user` (scoped-юзер из `minio-init`) |
| `MINIO_SECRET_KEY` | gen-ort (медиа постов) | пароль этого юзера (`gen-strong-pass` в `minio-init`) |

## Secrets — необязательные

| Переменная | Где используется | Значение / формат |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | gen-ort (verify канала) | токен любого бота из @BotFather. Без него verify идёт через `t.me`, что из RU-сети не работает |
| `TELEGRAM_BOT_PROXY` | gen-ort (verify канала) | HTTP-прокси, дефолт `http://xray:1080` (xray в app-стеке) |

`GITHUB_TOKEN` создавать не нужно — выдаётся Actions автоматически (login в `ghcr.io`).

## Variables (`vars.*`)

| Переменная | Обяз. | Значение |
|---|---|---|
| `CORS_ALLOWED_ORIGINS` | нет | origin'ы через запятую **без пробелов**, напр. `https://ucust.ru,https://www.ucust.ru,https://adm.ucust.n4d3sh1k4.site`. Не задана → дефолт из compose/`application-prod.yml` |

## Не идут через Actions (только `.env` на сервере)

Читаются из `~/projects/ucust/.env` или дефолтов `docker-compose.yml`.

| Переменная | Дефолт | Где используется | Было на сервере |
|---|---|---|---|
| `QUOTA_FREE_UNLIMITED` | `false` | billing (FREE = `-1` во всех лимитах) | `true` |
| `MINIO_ENDPOINT` | `http://minio:9000` | content, gen-ort | не задан |
| `MINIO_PUBLIC_BASE_URL` | — | gen-ort | `https://api.ucust.n4d3sh1k4.site/s3/generative-orchestration` |
| `USER_MINIO_ACCESS_KEY` / `USER_MINIO_SECRET_KEY` | `user-service-user` / `usr-strong-pass` | user-service (аватары) | не заданы |
| `BUSINESS_MINIO_ACCESS_KEY` / `BUSINESS_MINIO_SECRET_KEY` | `business-service-user` / `bus-strong-pass` | business-service (логотипы) | не заданы |
| `MINIO_S3_URI` | `http://minio:9000` | gateway (`/s3/**`) | не задан |

## Что ломается, если секрет пустой

| Пустой | Последствие |
|---|---|
| `AI_ORCHESTRATOR_ENDPOINT` | все генерации/анализы падают (`AI orchestrator execution failed`) |
| `INTERNAL_API_SECRET` | billing отдаёт 401 на `/internal/**` → квоты не проверяются, генерация блокируется; callback контура не проходит |
| `TELEGRAM_BOT_TOKEN` | verify Telegram → fallback `t.me` → `verified: false` из RU-сети |
| `CONTENT_MINIO_*` | content-service стартует, но загрузка PDF/логотипа падает (compose подставит дефолт `content-service-user`, если секрет передан пустым) |

`AI_SERVICE_URI` в коде **не используется** (остаток от удалённого
`AIServiceClient`) — добавлять не нужно, можно удалить из secrets.

---

# Часть 2. OPS (`ops`)

Отдельный репозиторий, отдельные secrets. Variables не используются.

## Secrets

| Переменная | Обязательна | Где используется | Значение / формат |
|---|---|---|---|
| `TS_OAUTH_CLIENT_ID` / `TS_OAUTH_SECRET` | да | `tailscale/github-action` | тот же OAuth-клиент, что в бэкенде |
| `SERVER_TAILSCALE_IP` | да | ssh/scp | Tailscale-IP сервера |
| `SERVER_USER` | да | ssh/scp | `n4d3sh1k4` |
| `SERVER_SSH_KEY` | да | ssh/scp | тот же ключ, что в бэкенде |
| `POSTGRES_PASSWORD` | да | `postgres-db` | пароль `ETA_DBUser`; **равен** `POSTGRES_PASSWORD` бэкенда (бывш. `DB_PASSWORD`) |
| `RABBITMQ_USER` | нет | `rabbitmq` | дефолт `service-user`; совпадает с `RABBITMQ_USER` бэкенда |
| `RABBITMQ_PASS` | да | `rabbitmq` | пароль `service-user`; сервисы читают тот же `RABBITMQ_PASS` |
| `MINIO_ROOT_PASSWORD` | да | `minio` | пароль root MinIO. Если сервисы ходят под `admin` (`MINIO_*` не заданы) — менять синхронно |

## Дефолты compose (в CI не передаются)

| Переменная | Дефолт | Комментарий |
|---|---|---|
| `POSTGRES_USER` | `ETA_DBUser` | захардкожен также в `ensure-databases.sh` |
| `POSTGRES_PORT` | `5440` | внутри сети — 5432 |
| `RABBITMQ_AMQP_PORT` | `5680` | |
| `RABBITMQ_MGMT_PORT` | `15673` | UI `/admin/rabbit` |
| `MINIO_ROOT_USER` | `admin` | совпадает с `MINIO_ACCESS_KEY` бэкенда |
| `MINIO_API_PORT` / `MINIO_CONSOLE_PORT` | `9020` / `9021` | |
| `PGADMIN_EMAIL` / `PGADMIN_PASSWORD` | `admin@ucust.ru` / `devpassword123` | ⚠️ слабый дефолт; сервис только по `--profile tools` |
| `PGADMIN_PORT` | `9022` | |
| `DOZZLE_PORT` | `8081` | |

`.env` в `~/projects/ucust/ucust-ops` на сервере **нет** — всё берётся из этих
дефолтов. Меняется деплоем: `docker compose up -d --remove-orphans` +
`scripts/ensure-databases.sh`.

---

# Часть 3. Общее

## Что должно совпадать между репозиториями

| OPS | Бэкенд | Комментарий |
|---|---|---|
| `POSTGRES_PASSWORD` | `POSTGRES_PASSWORD` (бывш. `DB_PASSWORD`) | иначе сервисы не подключатся к БД |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | — | root нужен только ops и админке: приложение ходит под scoped-юзерами (`MINIO_*`, `USER_MINIO_*`, `BUSINESS_MINIO_*`, `CONTENT_MINIO_*`) |
| `RABBITMQ_USER` / `RABBITMQ_PASS` | `RABBITMQ_USER` / `RABBITMQ_PASS` | имена унифицированы; в prod-YML больше нет хардкода |
| `TS_*`, `SERVER_*` | те же имена | один Tailscale/SSH-доступ |

## Проверить

```powershell
gh secret list   --repo N4d3sh1k4-UCust-Developing/UCust-Backend
gh variable list --repo N4d3sh1k4-UCust-Developing/UCust-Backend
gh secret list   --repo N4d3sh1k4-UCust-Developing/ops
```

Полный список переменных системы (включая серверный `.env`, дефолты compose и
тюнинги приложения) — `docs/env-inventory.md` (файл **локальный**, в git не
хранится: там проставлены реальные значения). Шаблоны для заполнения —
`.env.example` (бэкенд) и `ops/.env.example`.
