# Инвентаризация переменных окружения UCust (02.11.2026)

Полный срез: **68 уникальных имён** из `application*.yml`, `docker-compose.yml`,
`ops/docker-compose.yml` и оба Actions-workflow. Секретные значения не
приводятся — только назначение, дефолт и факт наличия.

## Легенда источников

| Метка | Где задаётся |
|---|---|
| **CI-S** | GitHub Actions **secret** (репозиторий `UCust-Backend`), CI передаёт в `docker compose up` |
| **CI-V** | GitHub Actions **variable** (`vars.*`) |
| **OPS-S** | GitHub Actions **secret** (репозиторий `ops`) |
| **ENV** | `~/projects/ucust/.env` на сервере (и корневой `.env` для `docker-compose.local.yml`) |
| **COMPOSE** | дефолт в `docker-compose.yml` (`${VAR:-default}`) |
| **OPS-C** | дефолт в `ops/docker-compose.yml` |
| **YML** | дефолт в `application.yml` (тюнинг, обычно не задаётся) |

Правило Spring: `${VAR}` без дефолта — обязательна, но **пустая строка считается
валидной подстановкой** (сервис стартует и ломается позже). Поэтому «пусто»
опаснее, чем «не задано».

---

## 1. Бэкенд: GitHub Actions secrets (`UCust-Backend`)

| Переменная | Кто читает | Дефолт | Статус 02.11 |
|---|---|---|---|
| `SUBMODULES_PAT` | CI checkout | — | задан (иначе сборка не стартует) |
| `SERVER_TAILSCALE_IP` | CI ssh/scp | — | задан |
| `SERVER_USER` | CI ssh/scp | — | задан |
| `SERVER_SSH_KEY` | CI ssh/scp | — | задан |
| `TS_OAUTH_CLIENT_ID` | CI tailscale | — | задан |
| `TS_OAUTH_SECRET` | CI tailscale | — | задан |
| `DB_PASSWORD` | security, user, business, billing, gen-ort, content, audit | `devpassword123` (dev-YML) | задан |
| `JWT_SECRET_ACCESS` | gateway, security, billing, gen-ort | dev-строка в YML | задан |
| `DEFAULT_ADMIN_EMAIL` | security | `admin@email.nl` (dev) | задан (`superadmin@adminparadise.ru`) |
| `DEFAULT_ADMIN_PASSWORD` | security | `123456nl` (dev) | задан |
| `YANDEX_CLIENT_ID` / `_SECRET` | security | — | заданы |
| `VK_CLIENT_ID` / `_SECRET` | security | — | заданы |
| `GEO_API_URL` | security | — | задан (`http://ip-api.com/json/{ip}?lang=ru&fields=status,country,city`) |
| `INTERNAL_API_SECRET` | gen-ort, billing, AI-контур | `ucust-super-secret-service-token-2026` (dev) | **пусто** |
| `AI_ORCHESTRATOR_ENDPOINT` | gen-ort | `http://localhost:8000` (dev) | **пусто** |
| `AI_SERVICE_URI` | никто | — | **пусто**, переменная мёртвая |
| `SUPPORT_EMAIL` | notification | `support@ucust.com` | задан |
| `FRONTEND_URL` | notification | `http://localhost:3000` | задан (`https://ucust.ru`) |
| `MAIL_HOST` | notification | `smtp.gmail.com` в `.env`; dev-YML `mail.hosting.reg.ru` | задан |
| `MAIL_PORT` | notification | `587` | задан |
| `MAIL_USERNAME` | notification | dev-YML `noreply@ucust.ru` | задан |
| `MAIL_PASSWORD` | notification | пусто (dev) | задан |
| `MAIL_SSL` | notification | `false` | задан |
| `MAIL_FROM` | notification | `contact@n4d3sh1k4.site` | задан |
| `MAIL_FROM_NAME` | notification | `UCust` | не проверено |
| `CONTENT_MINIO_ACCESS_KEY` | content | `content-service-user` (COMPOSE) | не проверено |
| `CONTENT_MINIO_SECRET_KEY` | content | `ct-strong-pass` (COMPOSE) | не проверено |
| `CONTENT_MINIO_PUBLIC_BASE_URL` | content | `https://api.ucust.n4d3sh1k4.site/s3/content-service` | не проверено |
| `TELEGRAM_BOT_TOKEN` | gen-ort | пусто | **не задан** (в `.env` тоже нет) |
| `TELEGRAM_BOT_PROXY` | gen-ort | `http://xray:1080` (COMPOSE) | не проверен |

## 2. Бэкенд: GitHub Actions variable

| Переменная | Кто читает | Дефолт | Статус |
|---|---|---|---|
| `CORS_ALLOWED_ORIGINS` | gateway (`cors.allowed-origins`) | список прод-доменов в `application-prod.yml` и COMPOSE | **не задана** (работает дефолт) |

## 3. Бэкенд: только серверный `.env` (CI не передаёт)

| Переменная | Кто читает | Дефолт | Значение на сервере |
|---|---|---|---|
| `QUOTA_FREE_UNLIMITED` | billing | `false` | `true` (FREE-тариф без лимитов) |
| `MINIO_ENDPOINT` | content, gen-ort | `http://minio:9000` | не задан |
| `MINIO_ACCESS_KEY` | gen-ort | — | `admin` (root MinIO) |
| `MINIO_SECRET_KEY` | gen-ort | — | `password123` (root MinIO) |
| `MINIO_PUBLIC_BASE_URL` | gen-ort | `http://localhost:8100/s3/...` (dev) | `https://api.ucust.n4d3sh1k4.site/s3/generative-orchestration` |
| `USER_MINIO_ACCESS_KEY` | user (аватары) | `admin` | не задан |
| `USER_MINIO_SECRET_KEY` | user | `password123` | не задан |
| `MINIO_S3_URI` | gateway (`/s3/**`) | `http://minio:9000` | не задан |

> В серверном `.env` сейчас продублированы и CI-переменные (`DB_PASSWORD`,
> `JWT_SECRET_ACCESS`, `MAIL_*` и т.д.). Это не обязательно при деплое через
> Actions, но удобно для ручного `docker compose up` — подробности в
> `docs/github-secrets.md`.

## 4. OPS: GitHub Actions secrets (`ops`)

| Переменная | Кто читает | Статус |
|---|---|---|
| `TS_OAUTH_CLIENT_ID` / `TS_OAUTH_SECRET` | CI tailscale | заданы (тот же клиент, что у бэкенда) |
| `SERVER_TAILSCALE_IP` / `SERVER_USER` / `SERVER_SSH_KEY` | CI ssh/scp | заданы |
| `POSTGRES_PASSWORD` | postgres-db | задан; **= `DB_PASSWORD`** бэкенда |
| `RABBITMQ_PASS` | rabbitmq | задан |
| `MINIO_ROOT_PASSWORD` | minio | задан |

## 5. OPS: дефолты compose (в CI не передаются)

| Переменная | Дефолт | Комментарий |
|---|---|---|
| `POSTGRES_USER` | `ETA_DBUser` | захардкожен в prod-YML сервисов и в `ensure-databases.sh` |
| `POSTGRES_PORT` | `5440` | внутри `ucust-net` — 5432 |
| `RABBITMQ_AMQP_PORT` | `5680` | |
| `RABBITMQ_MGMT_PORT` | `15673` | UI на `/admin/rabbit` |
| `MINIO_ROOT_USER` | `admin` | совпадает с `MINIO_ACCESS_KEY` |
| `MINIO_API_PORT` / `MINIO_CONSOLE_PORT` | `9020` / `9021` | |
| `PGADMIN_EMAIL` | `admin@ucust.ru` | |
| `PGADMIN_PASSWORD` | `devpassword123` | ⚠️ слабый дефолт |
| `PGADMIN_PORT` | `9022` | pgadmin только по `--profile tools` |
| `DOZZLE_PORT` | `8081` | |

## 6. Внутренние адреса и dev-креды (руками не задаются)

| Переменная | Кто читает | Дефолт | Примечание |
|---|---|---|---|
| `BILLING_SERVICE_URI` | gen-ort | `http://billing-service:8106` | в prod-YML тем же именем задан и `business-service.uri` — **опечатка**, работает только на дефолте |
| `BUSINESS_SERVICE_URI` | gen-ort (dev) | `http://localhost:8104` | в prod используется `BILLING_SERVICE_URI` (см. выше) |
| `MINIO_ADMIN_URI` | gateway | константа в compose `http://minio-ucust:9001` | ссылка для админ-панели |
| `RABBITMQ_ADMIN_URI` | gateway | `http://rabbitmq-ucust:15672` | |
| `PGADMIN_URI` | gateway | `http://pgadmin-ucust:80` | |
| `SERVICES_DOZZLE_URI` | gateway | `http://dozzle-ucust:8080` | |
| `NOMINATIM_URI` | business | `https://nominatim.openstreetmap.org` | геокодинг адресов проекта |
| `NOMINATIM_USER_AGENT` | business | `UCust/1.0 (contact@ucust.n4d3sh1k4.site)` | требование Nominatim |
| `DB_USER` | все сервисы (dev) | `ETA_DBUser` | в prod-YML пользователь захардкожен |
| `RABBITMQ_USER` | все сервисы (dev) | `service-user` | в prod-YML захардкожен |
| `RABBITMQ_PASS` | все сервисы (dev) + ops | `servicepassword` | ⚠️ в prod-YML захардкожен; менять только вместе с кодом |

## 7. Тюнинги приложения (есть дефолт в YML, задавать не нужно)

| Переменная | Где | Дефолт |
|---|---|---|
| `GEO_CACHE_MAX_SIZE` | business | `5000` |
| `GEO_CACHE_TTL_MINUTES` | business | `1440` |
| `GEO_OUTBOUND_MIN_INTERVAL_MS` | business | `1100` (лимит Nominatim 1 rps) |
| `GEO_PER_USER_PER_MINUTE` | business | `30` |
| `NOMINATIM_CONNECT_TIMEOUT_MS` | business | `3000` |
| `NOMINATIM_READ_TIMEOUT_MS` | business | `5000` |
| `IDEA_BANK_COUNTRY` | gen-ort | `россия` |
| `YANDEX_OAUTH2_FORCE_CONFIRM` | security | `yes` (пусто = выключить экран выбора аккаунта) |

---

## 8. Найденные расхождения (требуют решения)

| № | Что | Почему важно |
|---|---|---|
| 1 | `TELEGRAM_BOT_TOKEN` / `TELEGRAM_BOT_PROXY` не передавались в CI | токен мог попасть только из `.env`, где его нет → verify TG всегда `false`. **Исправлено**: добавлены в `envs`/`env` workflow |
| 2 | `INTERNAL_API_SECRET` пустой | billing отдаёт 401 на `/internal/quota/*` → квоты блокируют генерацию; callback контура не проходит |
| 3 | `AI_ORCHESTRATOR_ENDPOINT` пустой | все генерации/анализы падают; снаружи `10.0.0.2:8000` не отвечает |
| 4 | `AI_SERVICE_URI` | нигде не читается — удалить из secrets/workflow/compose |
| 5 | `business-service.uri: ${BILLING_SERVICE_URI:http://business-service:8104}` в gen-ort prod-YML | при заданном `BILLING_SERVICE_URI` business-запросы уйдут в billing |
| 6 | `SERVER_TAILSCALE_IP` | в Java/YML не читается; прокидывается в user/business зря — легаси |
| 7 | Дефолты-секреты в репозитории | `devpassword123`, `admin`/`password123`, `123456nl`, `ucust-super-secret-service-token-2026`, Mailtrap-креды (`a9f3052b45461a` / `ae4e25c063dcb8`) в `docker-compose.yml` и dev-YML |
| 8 | `MAIL_FROM` дефолт `-` в compose | при пустой переменной в заголовок `From` попадёт мусор |
| 9 | `QUOTA_FREE_UNLIMITED=true` на сервере | на демо-стенде ожидаемо; в проде дефолт `false` |

## 9. Сводка по серверу (02.11.2026)

- Стек приложения **снесён**: контейнеров `*-ucust` нет (кроме независимого `ucust-admin-spa`).
- `~/projects/ucust/xray/config.json` на месте (803 B, VLESS → `api.ferrin.uk:443`), xray поднимется из app-стека.
- `~/projects/ucust/ucust-ops` содержит compose от 10.09 без content-service; `.env` в нём нет.
- В `~/projects/ucust/.env`: `AI_ORCHESTRATOR_ENDPOINT`, `AI_SERVICE_URI`, `INTERNAL_API_SECRET` — пустые; `TELEGRAM_BOT_TOKEN`/`TELEGRAM_BOT_PROXY` — отсутствуют; `QUOTA_FREE_UNLIMITED=true`.
- `10.0.0.2:8000` — ping идёт, TCP закрыт; `wg-ucust` (11.0.0.2) без handshake.

## 10. Шаблоны для заполнения

- `.env.example` — серверный `.env` бэкенда (и локальный для `docker-compose.local.yml`).
- `ops/.env.example` — переопределения для ops-стека (по умолчанию всё на дефолтах compose).
