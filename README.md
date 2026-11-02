<a href="https://ucust.ru">
  <img width="150" alt="UCustAva" src="https://github.com/user-attachments/assets/f885c148-ddf3-4e1b-ae08-8d2d3ab47e12" />
</a>

## Архитектура

<img alt="Celery Equation Solving Pipeline-2026-10-30-182019" src="https://github.com/user-attachments/assets/44eaf5ed-25f8-45a3-8f70-81194122bbe2" />

---

## Микросервисы

| Сервис | Порт | Ответственность |
|--------|------|----------------|
| **api-gateway** | `8100` | Единая точка входа. Маршрутизация: `/api/auth/**` → security, `/api/users/**` → user, `/api/business/**` → business. Валидация JWT. |
| **security-service** | `8101` | Регистрация, логин (JWT access + refresh), OAuth2 (Яндекс), верификация email, сброс пароля, блокировка аккаунта. |
| **user-service** | `8102` | CRUD профилей пользователей, аватарки (MinIO), слушает `UserCreatedEvent` из RabbitMQ. |
| **business-service** | `8104` | CRUD проектов (бизнес-идеи), логотипы (MinIO). |
| **notification-service** | `8103` | Email-уведомления (подтверждение регистрации, сброс пароля, блокировка). RabbitMQ consumer. |
---

## Инфраструктура

| Сервис | Образ |
|--------|-------|
| **PostgreSQL** | `postgres:16` |
| **RabbitMQ** | `rabbitmq:3-management` |
| **MinIO** | `minio/minio` |
---

## CI/CD

```
┌─────────────────────────────────────────────┐
│     push → master / main                    │
│             │                               │
│     ┌───────▼───────────────┐               │
│     │  build-and-push       │               │
│     │  Docker build + push  │               │
│     │  → ghcr.io/*/service  │               │
│     └───────┬───────────────┘               │
│             │ needs                         │
│     ┌───────▼───────────────┐               │
│     │  deploy               │               │
│     │  1. Tailscale VPN     │               │
│     │  2. SCP docker-compose│               │
│     │  3. SSH: pull + up -d │               │
│     └───────────────────────┘               │
│                                             │
└─────────────────────────────────────────────┘
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
