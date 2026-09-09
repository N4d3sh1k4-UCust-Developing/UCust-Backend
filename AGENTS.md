# Project: UCust

## Architecture
Microservices on Spring Boot 4 / Java 25:
- **api-gateway** (`:8100`) — Spring Cloud Gateway (WebFlux), routes: /api/auth/** → security-service, /api/users/** → user-service, /api/business/** → business-service
- **security-service** (`:8101`) — auth (JWT + OAuth2 Yandex), registration, tokens, rate limiting (Bucket4j). JWT access-токен содержит claims: `sub` (userId), `roles`, `city` (определяется по IP через `UserGeoService` → ip-api.com, best-effort, никогда не блокирует логин; локальные адреса возвращают null → claim отсутствует). Все точки входа (login/refresh/yandex-mobile/link-social/OAuth2) кладут `city` в токен.
- **user-service** (`:8102`) — user profiles, avatars (MinIO)
- **business-service** (`:8104`) — project/business management, logos (MinIO)
- **notification-service** (`:8103`) — email (RabbitMQ consumer + Spring Mail + Thymeleaf)
- **billing-service** (`:8106`) — billing/billing_quota management
- **generative-orchestration-service** (`:8107`) — generative AI orchestration (calls billing-service quota; external AI через Единый Шлюз Оркестратора `POST /api/v1/orchestrator/execute` + callback `POST /api/v1/ai/callback` с `X-Internal-Secret`)
- **configuration-service** (no port) — Spring Cloud Config (currently disabled)
- **common** — shared lib (ApiResponse, GlobalExceptionHandler, BaseException). `GlobalResponseHandler` оборачивает в ApiResponse все ответы, **кроме** `/internal/**` (raw-body S2S-контракт для сервис-ту-сервис, напр. `/internal/quota/*` для QuotaClient).

## Key Changes Made in This Session

### 1. Eureka removed
- All `eureka-client` dependencies removed from build.gradle
- All `eureka.*` config blocks removed from all services
- `netflix-eureka` module deleted from project, settings.gradle, .gitmodules, git
- API Gateway uses static routes (container names / localhost)

### 2. Ports changed
- `8180→8100`, `8181→8101`, `8182→8102`, `8183→8103`, `8184→8104`

### 3. MySQL → PostgreSQL
- `mysql-connector-j` → `postgresql:42.7.5` in all build.gradle
- JDBC URLs: `jdbc:postgresql://...`
- `init.sql` → PostgreSQL syntax
- PostgreSQL image `postgres:16`, host port `5440`

### 4. Configs converted .properties → .yml
- All services now use `application.yml` + `application-prod.yml`
- Default credentials via `${VAR:default}` in dev, strict `${VAR}` in prod
- `.env` file at root (in .gitignore) for local secrets
- Dev defaults use simple passwords without `$` (Docker Compose interprets `$` as variable)

### 5. OPS repo created (`ucust-ops/`)
- Separate stack: PostgreSQL, RabbitMQ, MinIO
- Shared network: `ucust-net`
- Full RabbitMQ topology in `definitions.json` (exchange + 2 queues + 4 bindings)
- MinIO auto-init: creates `user-service` bucket
- Scripts: backup.sh, restore.sh

### 6. CI/CD updated
- Build: 7 services (api-gateway, security, user, business, notification, billing, generative-orchestration; Dockerfile + application-prod.yml created for billing/generative)
- Deploy: SCP docker-compose.yml → SSH pull + up -d
- Secrets passed via `envs` (DB_PASSWORD, JWT_SECRET_ACCESS, SERVER_TAILSCALE_IP, ..., AI_SERVICE_URI)

### 7. Docker network
- Infrastructure (`ucust-ops`) creates `ucust-net` (driver: bridge)
- App stack (`docker-compose.yml`) uses `ucust-net` as external

### 8. Database creation
- `ops/postgres/init.sql` создаёт БД только при первом старте пустого volume
- Новая БД добавляется в `ops/scripts/ensure-databases.sh` (идемпотентно создаёт недостающие при каждом деплое ops)

### 9. AI: Единый Шлюз Оркестратора (v2.5.0)
- **Внешний контур**: AI на отдельном сервере, связь через WireGuard (`10.0.0.2` → `10.0.0.1`).
- **Бэкенд → AI**: `POST {AI_ORCHESTRATOR_ENDPOINT}/api/v1/orchestrator/execute`, envelope `{task_type, user_id, session_id, payload, sync_backend}`, заголовок `X-Internal-Secret` (общий секрет `INTERNAL_API_SECRET`). `brand_id`/`tenant_id` кладётся **внутрь payload**.
- **AI → бэкенд**: Auto-Push на `POST /api/v1/ai/callback` (при `sync_backend: true`), тоже с `X-Internal-Secret`. Роут в gateway **публичный** (`/api/v1/ai/**`), проверка секрета в `AiCallbackController`. Callback URL для AI-ноды: `http://10.0.0.1:8100/api/v1/ai/callback`.
- **generative-orchestration-service**: `OrchestratorClient` (execute), `AiCallbackService` (generate_post → Post), `AiGenerationService` (проверка квоты `post` + инкремент), `PostFactory`. Эндпоинты: `POST /orchestration/ai/execute` (generic passthrough), `POST /orchestration/generate` (с квотой). Перед генерацией `AiGenerationService` запрашивает проект (`BusinessServiceClient` → business-service `GET /projects/{id}` с заголовками `X-User-Id`/`X-User-Roles`, best-effort) и обогащает payload полями бренда (company_name, niche, description, city, target_audience, tone_of_voice, website/instagram/telegram, logo_url, business_hours; значения из запроса имеют приоритет, `BUSINESS_SERVICE_URI` задаётся на клиенте) + структурированными flat-ключами из `brandProfile` проекта (usp ← positioning, key_benefits ← swot.strengths, brand_goals ← goals, products_catalog ← services); `brand_profile` в payload строкой НЕ отправляется (риск SecurityGuard шлюза) — вместо этого `RagClient` best-effort индексирует брендбук в RAG AI-контура (`POST /api/v1/ai/rag/ingest`, doc_id=`brand_profile:{projectId}`, X-Internal-Secret, SimpleClientHttpRequestFactory).
- **Посты**: `POST /orchestration/posts/{id}/confirm`, `POST /orchestration/posts/{id}/publish`, `GET /orchestration/posts/{id}`, `GET /orchestration/projects/{projectId}/posts`, `PATCH /orchestration/posts/{id}` (partial-обновление text/imageUrl/hashtags/targetPlatforms/scheduledAt), `POST /orchestration/posts/{id}/ai-review` (AI-аудит поста через `CriticClient` → контур `/api/v1/ai/critic/review` (критик Чарли Мангера): передаются текст поста + `industry` проекта как niche, возвращается `CriticReviewResponse {status, critic, data, error}`, правки применяются на клиенте через PATCH). Все операции принимают `Authentication` (principal = userId string) и проверяют владельца: пост, `userId` которого не совпадает, отдаётся как 404 (`ContentNotFoundException`). Список постов — только по `findByProjectIdAndUserIdOrderByCreatedAtDesc`. `@EnableScheduling` — на `GenerativeOrchestrationServiceApplication`. `PostFactory` толерантен к форме ответа Шлюза: `generated_post {telegram_html → vk_text, media{kind,src,palette}, hashtags[]}` (реальная структура контура) либо flat-конвенция (`post_text_html`/`post_text`/`image_url`); `media.src` → `imageUrl`, массивы склеиваются через пробел. `telegram_buttons`/`security_passed`/`title` в `posts` не персистятся (нет колонок).
- **Async-генерация** (`POST /orchestration/generate/async`, `GET /orchestration/tasks/{id}`): `AsyncGenerationService` НЕ использует удалённый `AIServiceClient` — submitAsync дергает Единый Шлюз через `OrchestratorClient.execute` (`task_type=generate_post`, `session_id=task.id`, `sync_backend=true`), обогащает payload данными проекта (`BusinessServiceClient.findProject`, best-effort) + индексирует брендбук в RAG (`ragClient`). Если sync-ответ Шлюза содержит текст — посты создаются сразу (задача `COMPLETED`, `resultPostId`); иначе задача `PROCESSING`, результат ждём пушем на `/api/v1/ai/callback` (`AiCallbackService` идемпотентен: задача `COMPLETED`/`FAILED` → callback пропускается). `checkTask` — чистое чтение БД. `TaskStatusResponse` содержит `generatedText` и alias `postId` (фронт по ним достаёт текст готового поста). Ошибка Шлюза → задача `FAILED`.
- **Планирование и «отложить»**: `POST /orchestration/posts/{id}/schedule` (body `{scheduledAt}` ISO-8601) ставит `SCHEDULED`+`scheduledAt`; крон `PostPublishScheduler` каждые 30с переводит `SCHEDULED`+`scheduledAt<=now` → `PUBLISHED`+`publishedAt` (реальной отправки в соцсети нет). `POST /orchestration/posts/{id}/reject` → `REJECTED` («отложить»). Фронт: `schedulePost(id, scheduledAt)` (`combineDateTime` в поясе Europe/Moscow, offset +03:00), `rejectPost(id)` — кнопка «Отложить» в `PostEditView`.
- **RAG-БД**: PostgreSQL 16 + pgvector (+ HNSW) **локально на AI-сервере** (`ai_smm`, postgres/postgres) — бэкенд напрямую не подключается, только `rag_ingest`/`rag_query` через шлюз.
- **Медиа в постах (шаги А–В спеки)**: `POST /orchestration/posts/generate` (multipart: часть `request` = JSON `AiGenerateRequest`, часть `files` = бинарники) — файлы сохраняются в MinIO (`io.minio:minio:9.0.0`, бакет `generative-orchestration`, ключ `media/{projectId}/{filename}` с sanitize от path-traversal, бакет ensure best-effort), в payload оркестратора уходят как `attachments: [{name, url}]` (визуальный контекст для Moondream VQA + SaigaLLM на стороне Шлюза; сам бэкенд картинок не читает). Публичный доступ к файлам — через gateway-роут `/s3/**` (br `minio-s3-public`, публичный, stripPrefix(1), target `minio.s3.uri`; в prod `MINIO_S3_URI` = `http://minio:9000`, НЕ `localhost:9020`), т.е. URL вида `https://api.ucust.n4d3sh1k4.site/s3/generative-orchestration/media/...`. Конфиг: `minio.endpoint/access-key/secret-key/bucket/public-base-url` (dev `http://127.0.0.1:9020/generative-orchestration`, prod `MINIO_*` strict; `MINIO_PUBLIC_BASE_URL` должен быть доступен AI-контуру на 10.0.0.2, напр. `https://api.ucust.n4d3sh1k4.site/s3/generative-orchestration`). `MediaStorageService` → `List<Attachment>`, `AiGenerateRequest.attachments` теперь `List<Attachment>`, пустой список = поведение как раньше (`generate`).
- **Анализ бизнеса (онбординг)**: `POST /orchestration/analysis` (gateway `/api/v0/orchestration/analysis`) принимает `{companyName?, url?, notes?, documents? (string[] data-URL)}`, `BusinessAnalysisService.start()` синхронно дергает Шлюз (`task_type` выбирается: `quick_scan` при url / `analyze_documents` при только документах / `onboard_user` иначе, `payload{company_name, urls, documents, raw_notes, images:[], fast_mode:true}`, `sync_backend:true`), на ошибку/таймаут помечает `FAILED` без падения. Callback-пуш контура (`quick_scan`/`analyze_documents`/`onboard_user`/`onboarding`, session_id = наш `sessionId`) → `BusinessAnalysisService.complete()` (идемпотентно), результат нормализуется `BusinessAnalysisMapper` (толерантный, aliases) в `profile` формы BrandProfile + `ProjectPatch` и best-effort применяется через PATCH `/projects/{id}` (`BusinessServiceClient.updateProject`, X-User-Id/X-User-Roles). Сущность `business_analysis`. Фронт: `lib/api/analysis.ts` → fallback на ручной режим при `FAILED`/недоступном AI. `BusinessAnalysisMapper` дополнительно понимает сырую выдачу `WebsiteCollector` из контура (`meta.title/description`, `og_image`, `headings`, `paragraphs`, `extracted_links` → website/instagram/telegram, `full_extracted_text` как запасное описание) — профиль/патч собираются, даже если Шлюз вернёт необработанный парс сайта.
- **Верификация соцсетей (онбординг, этап A)**: `POST /orchestration/socials/verify` (gateway `/api/v0/orchestration/socials/verify`) принимает `{channel, reference}` → `SocialVerifyResponse {channel, reference, verified, provider, error}`. `SocialVerificationService` (best-effort, SSRF-защита: DNS-резолв, отклоняются приватные/локальные адреса): telegram — Bot API `getChat` при `TELEGRAM_BOT_TOKEN` (иначе проверка публичной страницы `t.me`, provider=`t.me`); vk/ok/max/instagram/zen/rutube/avito — HTTP reachability 2xx/3xx; остальные (whatsapp и т.п.) → `UNSUPPORTED_CHANNEL`. `verified=true` означает лишь «страница/канал существует», права на публикацию дадут только реальные коннекторы (этап B: Telegram Bot API без OAuth; VK/OK OAuth2 с зарегистрированными приложениями; Instagram — FB Graph API; Zen без публичного API). Конфиг: `services.socials.telegram-bot-token: ${TELEGRAM_BOT_TOKEN:}` (опциональный). Фронт: `StepChannels` — ввод @хэндла/ссылки → verify → «Подключено» только при `verified`; хэндл кладётся в `WizardInput.channelHandles`.
- Экспозиция через gateway: `POST /api/v0/orchestration/generate/async` (202), `GET /api/v0/orchestration/tasks/{taskId}`, `POST /api/v0/orchestration/ai/execute`, `POST /api/v0/orchestration/generate`, `POST /api/v0/orchestration/posts/generate` (multipart), `POST /api/v0/orchestration/analysis` + `GET /api/v0/orchestration/analysis/{id}`, `POST /api/v0/orchestration/socials/verify`, `POST /api/v1/ai/**` (callback, public).
- **Планируется — Telegram-канал проекта (дизайн, не начато)**: новый `task_type` в Шлюзе (напр. `collect_channel`, `payload{channel:"@...", limit:N}`, `sync_backend:true`) → `TelethonCollector` возвращает историю публикаций (`views`/`forwards`/`comments_count`/`media_type` + `top_objections_from_comments` — возражения аудитории). На бэке новые `ChannelAnalysisService` + сущность `channel_analysis` (project_id, channel, fetched_at, objections[]): нормализация → `RagClient.ingest` doc_id=`channel_context:{projectId}` (возражения/топики как контекст генерации постов) → эндпоинты `POST /orchestration/channel/analyze` (async) + `GET /orchestration/channel/analysis/{id}`. Генератор постов получает `audience_objections` в payload. Фронт: раздел «Анализ канала» в проекте → список возражений + кнопка «Создать пост-ответ». На онбординге не используется — у нового проекта TG-канала ещё нет (см. ответ Телеграм-парсера).

## Local Development

```bash
# Infra only (services run in IntelliJ)
docker compose -f docker-compose.local.yml up postgres-db rabbitmq minio minio-init -d

# Full stack in Docker
docker compose -f docker-compose.local.yml up -d
```

## Ports

| Service | Internal | External |
|---------|----------|----------|
| PostgreSQL | 5432 | 5440 |
| RabbitMQ AMQP | 5672 | 5680 |
| RabbitMQ UI | 15672 | 15673 |
| MinIO API | 9000 | 9020 |
| MinIO Console | 9001 | 9021 |
| API Gateway | — | 8100 |
| Security Service | — | 8101 |
| User Service | — | 8102 |
| Notification Service | — | 8103 |
| Business Service | — | 8104 |
| Billing Service | — | 8106 |
| Generative Orchestration | — | 8107 |

## RabbitMQ Topology

```
Exchange: user-exchange (topic)
  → mail-notification-queue: user.registration.email, user.password.reset, user.account.locked, user.login.email (Уведомление о входе: email, IP, user-agent, город по IP)
  → mail-email-change-init-queue: user.email.change.init
  → mail-email-change-new-queue: user.email.change.new
  → mail-email-change-done-queue: user.email.change.done
  → user-profile-queue: user.created
  → user-profile-email-change-queue: user.email.change.done (обновляет email в user-profile)
```

## MinIO Buckets

- `user-service` (used by user-service and business-service)

## CI/CD Workflows

1. **ucust-dev** (`.github/workflows/main.yml`): push → build+push 7 images → SSH deploy to `~/reshala-project/`
2. **ucust-ops** (`ucust-ops/.github/workflows/deploy.yml`): push infra configs → SCP → SSH restart

## Needed Secrets (GitHub)

### ucust-dev repo
- `DB_PASSWORD`
- `JWT_SECRET_ACCESS`
- `GEO_API_URL` (geo-сервис для claim `city`, напр. `http://ip-api.com/json/{ip}?lang=ru&fields=status,country,city`; prod-строгий)
- `SERVER_TAILSCALE_IP`
- `AI_SERVICE_URI` (external generative AI endpoint)
- `AI_ORCHESTRATOR_ENDPOINT` (Единый Шлюз Оркестратора, напр. `http://10.0.0.2:8000`)
- `INTERNAL_API_SECRET` (общий секрет для заголовка `X-Internal-Secret`; бэкенд и AI-контур)
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SSL`, `MAIL_FROM` (SMTP; if unset — Mailtrap defaults, MAIL_SSL=false, MAIL_FROM=MAIL_USERNAME)

### ucust-dev repo (server .env, not GitHub)
- `QUOTA_FREE_UNLIMITED` — если `true`, FREE-тариф в billing становится безлимитным (posts/projects/aiGenerations=-1) идемпотентно при каждом старте. Docker-дефолт `false`, локальный dev-профиль — `true`. Для презентации задать `QUOTA_FREE_UNLIMITED=true` в .env на демо-сервере.
- `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` — scoped-юзер gen-ort для записи медиа в MinIO (`generative-orchestration-user` / `gen-strong-pass`, создаётся minio-init). `MINIO_ENDPOINT` в проде с дефолтом `http://minio:9000`. `MINIO_PUBLIC_BASE_URL` — публичный URL до бакета через gateway-роут `/s3/**`: `https://api.ucust.n4d3sh1k4.site/s3/generative-orchestration` (должен быть доступен AI-контуру на 10.0.0.2). Все строгие.
- `USER_MINIO_ACCESS_KEY`/`USER_MINIO_SECRET_KEY` (опционально, дефолт admin/password123) — креды user-service для аватарок; рекомендуется заменить на scoped `user-service-user`/`usr-strong-pass` из minio-init.
- `MINIO_S3_URI` (опционально, дефолт `http://minio:9000`) — куда gateway проксирует публичный `/s3/**` на MinIO (в docker prod `http://minio:9000`, НЕ `localhost:9020`).

### ucust-ops repo
- `POSTGRES_PASSWORD`
- `RABBITMQ_PASS`
- `MINIO_ROOT_PASSWORD`
- `SERVER_TAILSCALE_IP`, `SERVER_USER`, `SERVER_SSH_KEY`
- `TS_OAUTH_CLIENT_ID`, `TS_OAUTH_SECRET`
