# UCust

**Микросервисное приложение на Spring Boot 4 / Java 25**

---

## Архитектура

```
                    ┌─────────────┐
                    │    Nginx    │  (на хосте, reverse proxy)
                    └──────┬──────┘
                           │ 8100
                    ┌──────▼──────┐
                    │ api-gateway │  Spring Cloud Gateway (WebFlux)
                    └──┬───┬───┬──┘
                       │   │   │
              ┌────────┤   │   ├────────┐
              │            │            │
         ┌────▼────┐ ┌────▼────┐ ┌─────▼─────┐
         │security │ │  user   │ │ business  │
         │ service │ │ service │ │  service  │
         │  :8101  │ │ :8102   │ │  :8104    │
         └────┬────┘ └────┬────┘ └─────┬─────┘
              │           │            │
              │           │            │
         ┌────▼───────────▼────────────▼──────────┐
         │         RabbitMQ                        │
         │  Exchange: user-exchange (Topic)        │
         │  Queues: mail-notification-queue,       │
         │          user-creation-queue            │
         └────┬────────────────────────────────────┘
              │
         ┌────▼──────────┐
         │ notification  │
         │   service     │
         │   :8103       │
         └───────────────┘

         ┌──────────────────────────────────────────┐
         │            PostgreSQL :5432              │
         │  ┌─────────────────┬──────────────────┐  │
         │  │ security_       │ user_            │  │
         │  │ service_db      │ service_db       │  │
         │  ├─────────────────┼──────────────────┤  │
         │  │ business_       │                  │  │
         │  │ service_db      │                  │  │
         │  └─────────────────┴──────────────────┘  │
         └──────────────────────────────────────────┘

         ┌─────────────────────────────┐
         │          MinIO              │
         │  Buckets: user-service,     │
         │           business-service  │
         └─────────────────────────────┘
```

---

## Микросервисы

| Сервис | Порт | Ответственность |
|--------|------|----------------|
| **api-gateway** | `8100` | Единая точка входа. Маршрутизация: `/api/auth/**` → security, `/api/users/**` → user, `/api/business/**` → business. Валидация JWT. |
| **security-service** | `8101` | Регистрация, логин (JWT access + refresh), OAuth2 (Яндекс), верификация email, сброс пароля, блокировка аккаунта. |
| **user-service** | `8102` | CRUD профилей пользователей, аватарки (MinIO), слушает `UserCreatedEvent` из RabbitMQ. |
| **business-service** | `8104` | CRUD проектов (бизнес-идеи), логотипы (MinIO). |
| **notification-service** | `8103` | Email-уведомления (подтверждение регистрации, сброс пароля, блокировка). RabbitMQ consumer. |

**Без порта (служебные):**
- **configuration-service** — Spring Cloud Config Server (в текущей версии не используется, каждый сервис имеет локальный `application.yml`).

---

## Инфраструктура

| Сервис | Образ | Внутренний порт | Внешний порт |
|--------|-------|----------------|--------------|
| **PostgreSQL** | `postgres:16` | `5432` | `5440` |
| **RabbitMQ** | `rabbitmq:3-management` | `5672` (AMQP), `15672` (UI) | `5680`, `15673` |
| **MinIO** | `minio/minio` | `9000` (API), `9001` (Console) | `9020`, `9021` |

Инфраструктура вынесена в отдельный репозиторий [`ucust-ops`](ucust-ops/).

---

## Обмен сообщениями (RabbitMQ)

```
security-service                       notification-service
  (publishes)                            (consumes)
       │                                         │
       │── UserRegisteredInternalEvent ─────────>│  → EmailService.sendRegistrationEmail()
       │── NotificationEmailEvent (resend) ─────>│  → EmailService.sendRegistrationEmail()
       │── PasswordResetEvent ──────────────────>│  → EmailService.sendPasswordResetEmail()
       │── AccountLockedEvent ──────────────────>│  → EmailService.sendAccountLockedEmail()

security-service                       user-service
       │                                         │
       │── UserRegisteredInternalEvent ─────────>│  → UserProfileService.createProfile()
```

- **Exchange:** `user-exchange` (Topic)
- **Routing keys:** `user.registration.email`, `user.password.reset`, `user.account.locked`
- **vhost:** `universal-host`

---

## CI/CD

```
┌─────────────────────────────────────────────┐
│     push → master / main                     │
│             │                                │
│     ┌───────▼───────────────┐                │
│     │  build-and-push       │                │
│     │  (5 сервисов, matrix) │                │
│     │  Docker build + push  │                │
│     │  → ghcr.io/*/service  │                │
│     └───────┬───────────────┘                │
│             │ needs                          │
│     ┌───────▼───────────────┐                │
│     │  deploy               │                │
│     │  1. Tailscale VPN     │                │
│     │  2. SCP docker-compose│                │
│     │  3. SSH: pull + up -d │                │
│     └───────────────────────┘                │
│                                              │
│     Secrets: DB_PASSWORD,                    │
│     JWT_SECRET_ACCESS,                       │
│     SERVER_TAILSCALE_IP                      │
└─────────────────────────────────────────────┘
```

Инфраструктура деплоится отдельно из [`ucust-ops`](ucust-ops/).

---

## Деплой (продакшен)

```bash
# 1. На сервере — один раз:
docker network create ucust-net
git clone <ucust-ops-url> ~/reshala-project/ucust-ops
cd ~/reshala-project/ucust-ops
cp .env.example .env   # отредактировать пароли
docker compose up -d

# 2. Деплой app-стека — автоматически через GitHub Actions
```

---

## Локальная разработка

### Вариант 1: Инфраструктура в Docker + сервисы в IntelliJ

```bash
# Инфраструктура
docker compose -f docker-compose.local.yml up postgres-db rabbitmq minio -d

# Сервисы — Run/Debug из IntelliJ (profile: default)
# application.yml уже содержит дефолтные credentials
```

### Вариант 2: Полностью в Docker

```bash
docker compose -f docker-compose.local.yml up -d
```

---

## Стек технологий

| Компонент | Технология |
|-----------|-----------|
| **Язык** | Java 25 |
| **Framework** | Spring Boot 4.0.x |
| **Cloud** | Spring Cloud 2025.1.1 |
| **БД** | PostgreSQL 16 + Hibernate/JPA |
| **Асинхронность** | RabbitMQ (AMQP) |
| **Объектное хранение** | MinIO (S3-compatible) |
| **Аутентификация** | JWT (JJWT + Bouncy Castle), OAuth2 (Яндекс) |
| **Gateway** | Spring Cloud Gateway (WebFlux/Netty) |
| **Email** | Spring Mail + Thymeleaf |
| **Документация API** | SpringDoc OpenAPI (swagger) |
| **Маппинг DTO** | MapStruct + Lombok |
| **Rate Limiting** | Bucket4j |
| **Сборка** | Gradle |

---

## Структура проекта

```
ucust-dev/
├── api-gateway/              # Spring Cloud Gateway
├── security-service/         # Аутентификация / авторизация
├── user-service/             # Профили пользователей
├── business-service/         # Проекты (бизнес-идеи)
├── notification-service/     # Email-уведомления
├── configuration-service/    # Config Server (опционально)
├── common/                   # Shared library (ApiResponse, exceptions)
├── ucust-ops/                # Инфраструктура (PostgreSQL, RabbitMQ, MinIO)
├── docker-compose.yml        # App-стек (продакшен)
├── docker-compose.local.yml  # Локальная разработка (всё вместе)
└── .env                      # Локальные секреты (в .gitignore)
```
