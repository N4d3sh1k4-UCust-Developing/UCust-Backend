# UCust

**Микросервисное приложение на Spring Boot 4 / Java 25** — AI-маркетолог для малого бизнеса: генерация и планирование контента по сетям.

---

## Структура репозиториев

Проект разбит на **приватные сабмодули** — каждый сервис живёт в своём репозитории и подключается в этот.

| Репозиторий | Назначение |
|-------------|-----------|
| **ucust-dev** (этот) | Точка входа: Gradle-обвязка, `docker-compose`, CI/CD, контракт API, документация |
| **backend-security-service** | Аутентификация, OAuth2, токены, rate limiting |
| **backend-user-service** | Профили пользователей, аватары (MinIO) |
| **backend-business-service** | Проекты (бренды), логотипы (MinIO) |
| **backend-api_gateway-service** | Spring Cloud Gateway (WebFlux) |
| **backend-notification-service** | Email-уведомления (RabbitMQ consumer + Thymeleaf) |
| **billing-service** | Тарифы и квоты |
| **generative-orchestration-service** | Оркестрация генерации постов, медиа, анализ бизнеса и каналов |
| **backend-common-library** | Общие DTO, `ApiResponse`, `GlobalExceptionHandler` |
| **ops** | Инфраструктура: PostgreSQL, RabbitMQ, MinIO (деплоится отдельно) |

Все сервисные репозитории **приватные**, поэтому клонирование требует прав:

```bash
git clone --recurse-submodules https://github.com/N4d3sh1k4-UCust-Developing/ucust-dev.git
# или после обычного клона:
git submodule update --init --recursive
```

CI использует `secrets.SUBMODULES_PAT` для чекаута сабмодулей (`submodules: recursive`).

---

## Архитектура

```
                         ┌──────────────┐
                         │     Nginx    │  TLS-терминация, /s3/** → MinIO
                         └──────┬───────┘
                                │ 8100
                         ┌──────▼───────┐
                         │ api-gateway  │  Spring Cloud Gateway (WebFlux)
                         └──┬───┬───┬───┘
              ┌─────────────┤   │   ├──────────────┬──────────────┐
              │             │   │                │              │
        ┌─────▼─────┐ ┌─────▼─────┐      ┌───────▼──────┐ ┌─────▼─────┐
        │ security  │ │   user    │      │   business   │ │  billing  │
        │  :8101   │ │  :8102    │      │    :8104     │ │   :8106   │
        └─────┬─────┘ └─────┬─────┘      └───────┬──────┘ └───────────┘
              │             │                    │            │
              │             │                    │      ┌─────▼───────────┐
              │             │                    │      │   generative-   │
              │             │                    │      │  orchestration  │
              │             │                    │      │     :8107       │
              │             │                    │      └─────┬───────────┘
              └─────────────┴────────────────────┴────────────┘
                            │        RabbitMQ
                     ┌──────▼──────────────────────────────────┐
                     │  user-exchange (topic) + DLX           │
                     │  9 очерей: почта, профиль, проекты,    │
                     │  каскадное удаление                    │
                     └──────┬────────────────────────────────┘
                            │
                     ┌──────▼───────┐        ┌────────────────────┐
                     │ notification │        │ PostgreSQL :5432   │
                     │   :8103      │        │ 6 баз, по одной на  │
                     └──────────────┘        │ сервис             │
                                             └────────────────────┘
                        ┌──────────────────────────┐
                        │  MinIO (S3)               │
                        │  user-service             │
                        │  business-service         │
                        │  generative-orchestration │
                        └──────────────────────────┘

   Внешний контур: Единый Шлюз Оркестратора (AI) по WireGuard
        10.0.0.2:8000  ──►  callback  POST /api/v1/ai/callback
```

---

## Микросервисы

| Сервис | Порт | Ответственность |
|--------|------|----------------|
| **api-gateway** | `8100` | Единая точка входа. Валидация JWT, маршрутизация, публичный `/s3/**` для MinIO, `/api/v1/ai/**` для callback'а AI-контура |
| **security-service** | `8101` | Регистрация, логин (JWT access + refresh), OAuth2 (Яндекс/VK), сброс пароля, блокировка, rate limiting (Bucket4j) |
| **user-service** | `8102` | Профили пользователей, аватары (MinIO), backfill телефона |
| **business-service** | `8104` | Проекты (бренды), логотипы (MinIO), публикация событий создания/удаления |
| **notification-service** | `8103` | Email: подтверждение, сброс пароля, блокировка, уведомление о входе, смена email |
| **billing-service** | `8106` | Тарифы и квоты (посты, проекты, AI-генерации). S2S-эндпоинт `/internal/quota/**` |
| **generative-orchestration-service** | `8107` | Генерация постов (sync/async), перегенерация, AI-аудит, планирование и «отложить», анализ бизнеса, анализ Telegram-канала, верификация соцсетей, медиа в MinIO |

**Служебный модуль:**
- **configuration-service** — Spring Cloud Config Server (в текущей версии отключён, каждый сервис имеет локальный `application.yml`)
- **common** — shared library: `ApiResponse`, `BaseException`, `GlobalExceptionHandler`. Ответы оборачиваются в `ApiResponse`, кроме `/internal/**` (raw-body S2S-контракт)

---

## Инфраструктура

| Сервис | Образ | Внутренний | Внешний |
|--------|-------|-----------|---------|
| **PostgreSQL** | `postgres:16` | `5432` | `5440` |
| **RabbitMQ** | `rabbitmq:3-management` | `5672` (AMQP), `15672` (UI) | `5680`, `15673` |
| **MinIO** | `io.minio:minio:9.0.0` | `9000` (API), `9001` (Console) | `9020`, `9021` |

Инфраструктура вынесена в отдельный репозиторий **ops** (PostgreSQL, RabbitMQ, MinIO, бэкапы, общая docker-сеть `ucust-net`).

---

## Обмен сообщениями (RabbitMQ)

Exchange **`user-exchange`** (topic), DLX через `user-exchange.dlx`. vhost — `universal-host`.

| Очередь | Routing key | Потребитель | Событие |
|---------|-------------|-----------|---------|
| `mail-notification-queue` | `user.registration.email`, `user.password.reset`, `user.account.locked`, `user.login.email` | notification | письма + уведомление о входе (IP, user-agent, город) |
| `mail-email-change-{init,new,done}-queue` | `user.email.change.*` | notification | смена email |
| `user-profile-queue` | `user.created` | user | создание профиля |
| `user-profile-email-change-queue` | `user.email.change.done` | user | синхронизация email в профиле |
| `user-profile-phone-backfill-queue` | `user.phone.backfill` | user | дозаполнение телефона из OAuth-провайдера |
| `project-generation-queue` | `project.created` | generative-orchestration | async-генерация постов при создании проекта |
| `project-deletion-queue` | `project.deleted` | generative-orchestration | каскадное удаление постов, задач, анализов и медиа |

> Издатели обязаны слать **JSON**: `RabbitTemplate` с `JacksonJsonMessageConverter`. Дефолтный `SimpleMessageConverter` даёт Java-сериализацию, и слушатель падает на `Could not convert ... [serialized object]`.

---

## CI/CD

`push → main` → **build-and-push** (7 образов в `ghcr.io`) → **deploy** (Tailscale VPN → SCP compose → SSH `pull` + `up -d`).

Секреты передаются через `envs` и `.env` на сервере, а не хранятся в репозитории: `DB_PASSWORD`, `JWT_SECRET_ACCESS`, `GEO_API_URL`, `SERVER_TAILSCALE_IP`, `AI_SERVICE_URI`, `AI_ORCHESTRATOR_ENDPOINT`, `INTERNAL_API_SECRET`, `MAIL_*`, `MINIO_*`, `TELEGRAM_BOT_TOKEN`.

Инфраструктура деплоится отдельно из **ops** (свои секреты: `POSTGRES_PASSWORD`, `RABBITMQ_PASS`, `MINIO_ROOT_PASSWORD`, SSH-ключи, Tailscale OAuth).

---

## Локальная разработка

```bash
# Только инфраструктура, сервисы запускаются из IntelliJ
docker compose -f docker-compose.local.yml up postgres-db rabbitmq minio minio-init -d

# Или весь стек в Docker
docker compose -f docker-compose.local.yml up -d
```

`application.yml` содержит дефолтные значения для dev, `application-prod.yml` — строгие `${VAR}` без дефолтов. Секреты в `.env` (в `.gitignore`).

---

## Стек технологий

| Компонент | Технология |
|-----------|-----------|
| **Язык** | Java 25 |
| **Framework** | Spring Boot 4.0.x |
| **Cloud** | Spring Cloud 2025.1.1 |
| **БД** | PostgreSQL 16 + Hibernate/JPA |
| **Асинхронность** | RabbitMQ (AMQP) |
| **Объектное хранилище** | MinIO (S3-compatible) |
| **Аутентификация** | JWT (JJWT + Bouncy Castle), OAuth2 (Яндекс, VK) |
| **Gateway** | Spring Cloud Gateway (WebFlux/Netty) |
| **Email** | Spring Mail + Thymeleaf |
| **AI-контур** | Единый Шлюз Оркестратора, WireGuard, Moondream VQA, RAG (pgvector) |
| **Rate Limiting** | Bucket4j |
| **Сборка** | Gradle (multi-module) |

---

## Структура

```
ucust-dev/
├── .github/workflows/main.yml   # build-and-push (7 сервисов) + deploy
├── api-gateway/                 # ← сабмодуль
├── security-service/            # ← сабмодуль
├── user-service/                # ← сабмодуль
├── business-service/            # ← сабмодуль
├── notification-service/        # ← сабмодуль
├── billing-service/             # ← сабмодуль
├── generative-orchestration-service/  # ← сабмодуль
├── common/                      # ← сабмодуль (shared library)
├── configuration-service/       # Config Server (в корне, не сабмодуль)
├── settings.gradle              # подключает 9 модулей
├── docker-compose.yml           # app-стек (продакшен)
├── docker-compose.local.yml     # локальная разработка
├── api-endpoints.json               # полный контракт (в т.ч. /internal)
├── api-endpoints-public.json         # публичный контракт для фронта
└── AGENTS.md                    # журнал архитектурных решений
```

Подробности реализации и история изменений — в [`AGENTS.md`](AGENTS.md).