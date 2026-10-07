# API Inventory UCust — что есть и чего не хватает для админ-панели

Отчёт по результатам ручного чтения всех 24 контроллеров, 5 SecurityConfig и
`ApiGatewayApplication.java` (289 строк, 26 роутов) репозитория
`D:\Projects\IdeaProjects\N4d3sh1k4-UCust_Dev`.

Дата анализа: 2026-10-06. Обновлено: 2026-10-07 (см. §2 — все 7 критичных багов исправлены; см. §6 — реализован P0-бэклог для админ-панели). Обновлено: 2026-10-08 (P1 «биллинг и модерация»: 14 новых admin-эндпоинтов в billing/business/GOS, см. §1 №28–41 и §4).
Фронтенд-панель: `D:\Projects\UcustAdminVibecode`.

**Модель доступа.** Gateway прокидывает `X-User-Id` / `X-User-Roles` фильтром
`AuthenticationGatewayFilterFactory`, требование `ROLE_ADMIN` проверяется в каждом сервисе через
`.requestMatchers("/admin/**").hasRole("ADMIN")`. `@PreAuthorize` не используется нигде.
С 2026-10-07: входные `X-User-Id`/`X-User-Roles` срезаются на публичных роутах
(`security-service-public`, `billing-tariffs-public`, `ai-callback-public`), а `/tariffs/**`
дополнительно закрыт `hasRole("ADMIN")` на стороне billing.

**Итого через gateway:** 103 API-метода (33 security + 7 user + 11 business + 16 billing + 36 GOS).
**Admin-методов с `ROLE_ADMIN`: 41** (27 было + P0 2026-10-07 + P1 2026-10-08: биллинг 6, проекты 2, посты 3, задачи 3).

---

## 1. Все 41 admin-эндпоинтов

| # | Метод | Путь через gateway | Что делает | Сервис |
|---|---|---|---|---|
| 1 | POST | `/api/v0/admin/bans` | Забанить: `CreateBanRequest{userId, reason, expiresAt?}` → 201 `BanResponse` | security |
| 2 | POST | `/api/v0/admin/bans/{id}/unban` | Снять бан → 200 | security |
| 3 | GET | `/api/v0/admin/bans` | Все активные баны → `List<BanResponse>` | security |
| 4 | GET | `/api/v0/admin/bans/user/{userId}` | Активный бан; **204**, если бана нет | security |
| 5 | GET | `/api/v0/admin/bans/user/{userId}/history` | История банов | security |
| 6 | GET | `/api/v0/admin/bans/user/{userId}/check` | Забанен ли → `boolean` | security |
| 7 | DELETE | `/api/v0/admin/accounts/{userId}` | Полное удаление аккаунта (идентичность, токены, баны) + outbox `user.deleted` → каскад → 204. Запрет удаления себя (`CANNOT_DELETE_SELF`) | security |
| 8 | GET | `/api/v0/admin/users?page&limit&search` | `page` default 0, `limit` default 20 (1..100), `search` по email/имени/фамилии → `AdminUsersPage{users,total,page,limit}` | user |
| 9 | GET | `/api/v0/admin/users/{userId}` | `ProfileResponse{id, firstName, middleName, lastName, email, phone, position, fullAvatarUrl}` | user |
| 10 | GET | `/api/v0/admin/projects/{id}` | Проект без проверки владельца | business |
| 11 | PATCH | `/api/v0/admin/projects/{id}` | Частичное обновление любого проекта | business |
| 12 | DELETE | `/api/v0/admin/projects/{id}` | Удаление → 204; каскад (MinIO, посты, задачи, медиа через RabbitMQ) | business |
| 13 | PUT | `/api/v0/admin/billing/subscriptions/{userId}/tariff/{tariffId}` | Выдать тариф (сброс квот) → `SubscriptionResponse{userId,tariffId,tariffName,startDate,nextResetDate}` | billing |
| 14 | GET | `/api/v0/admin/billing/subscriptions/{userId}` | Подписка; нет — 404 | billing |
| 15 | GET | `/api/v0/admin/orchestration/posts/{id}` | Любой пост с метриками | GOS |
| 16 | PATCH | `/api/v0/admin/orchestration/posts/{id}` | Редактирование любого поста | GOS |
| 17 | DELETE | `/api/v0/admin/orchestration/posts/{id}` | Удаление, **включая опубликованные** → 204 | GOS |
| 18 | GET | `/api/v0/admin/orchestration/projects/{projectId}/posts` | Посты любого проекта с метриками | GOS |
| 19 | GET | `/api/v0/admin/accounts/{userId}/sessions` | Активные refresh-сессии пользователя (`current` всегда `false`) | security |
| 20 | DELETE | `/api/v0/admin/accounts/{userId}/sessions` | Принудительный logout чужого пользователя → 204 | security |
| 21 | GET | `/api/v0/admin/accounts/{userId}` | Расширенная карточка: email, provider, все привязанные провайдеры, роли, enabled/lockTime/failedAttempts, активный бан | security |
| 22 | PUT | `/api/v0/admin/accounts/{userId}/roles` | Полная замена ролей. Защиты: `CANNOT_MODIFY_OWN_ROLES`, `ROLE_NOT_FOUND`, `LAST_ADMIN` (нельзя снять последнего админа) → 200 с обновлённой карточкой | security |
| 23 | POST | `/api/v0/admin/accounts/{userId}/block` | Мягкое отключение: `enabled=false` + кик всех refresh-сессий → 204. Запрет себе (`CANNOT_BLOCK_SELF`); идемпотентно | security |
| 24 | DELETE | `/api/v0/admin/accounts/{userId}/block` | `enabled=true` — снять мягкое отключение → 204. Идемпотентно | security |
| 25 | GET | `/api/v0/admin/roles` | Справочник ролей → `List<RoleResponse{id,name}>` (сорт по имени) | security |
| 26 | PATCH | `/api/v0/admin/users/{userId}` | Правка чужого профиля (та же валидация, что `PATCH /user/me`) → `ProfileResponse` | user |
| 27 | POST | `/api/v0/admin/users/{userId}/avatar` | Замена аватара чужого профиля (multipart) → URL | user |
| 28 | GET | `/api/v0/admin/billing/subscriptions?active=&tariffId=&page=&size=` | Список подписок (фильтры опциональны, size 1..100) → `AdminSubscriptionsPage{items,total,page,size}` | billing |
| 29 | DELETE | `/api/v0/admin/billing/subscriptions/{userId}` | Отмена подписки (soft): `paid=false` → 204; нет подписки — 404 | billing |
| 30 | POST | `/api/v0/admin/billing/subscriptions/{userId}/extend?months=1..120` | Продление: `paid=true`, `nextResetDate` += месяцев (база `max(nextResetDate, now)`, UTC) → 204; нет подписки — 404 | billing |
| 31 | GET | `/api/v0/admin/billing/quota/{userId}` | Агрегат квот (`AdminQuotaResponse{quota,used,remaining}`); нет подписки — 404 | billing |
| 32 | GET | `/api/v0/admin/billing/quota/usage/{userId}` | По-модельным `UsageCounterResponse{model,used,total}`; нет подписки — пустой список | billing |
| 33 | POST | `/api/v0/admin/billing/quota/reset/{userId}` | Ручной сброс всех квот → 204; нет подписки — 404 | billing |
| 34 | GET | `/api/v0/admin/projects?search=&ownerId=&page=&size=` | Глобальный список/поиск по имени (case-insensitive)/фильтр по владельцу (size 1..200) → `AdminProjectsPage{items,total,page,size}` | business |
| 35 | POST | `/api/v0/admin/projects` | Создание проекта от имени пользователя: body `{ownerId, project:{...}}` → `ProjectResponse` | business |
| 36 | GET | `/api/v0/admin/orchestration/posts?status=&userId=&projectId=&page=&size=` | Глобальный список постов с метриками, фильтры опциональны (size 1..200) → `AdminPostsPage` | GOS |
| 37 | POST | `/api/v0/admin/orchestration/posts/{id}/publish` | Публикация чужого поста: отправка в AI-контур каналом владельца + `PUBLISHED` → `PostResponse` | GOS |
| 38 | POST | `/api/v0/admin/orchestration/posts/{id}/reject` | Перевод чужого поста в `REJECTED` → `PostResponse` | GOS |
| 39 | GET | `/api/v0/admin/orchestration/tasks?status=&userId=&page=&size=` | Глобальный список задач генерации (фильтры опциональны, size 1..200) → `AdminTasksPage` | GOS |
| 40 | POST | `/api/v0/admin/orchestration/tasks/{id}/retry` | Перезапуск упавшей задачи: сброс ошибки, новый плейсхолдер, фоновая генерация → 202 `AsyncGenerateResponse`; COMPLETED / in-flight — 409 `TASK_ALREADY_COMPLETED`/`TASK_IN_FLIGHT`; нет — 404 | GOS |
| 41 | DELETE | `/api/v0/admin/orchestration/tasks/{id}` | Удаление задачи: снятие незаполненных заглушек, готовые посты сохраняются → 204; in-flight — 409 | GOS |

**Тарифы (не под `/admin/**`, но с ролью):** `GET /api/v0/tariffs[/{id}]` — публичный
(`billing-tariffs-public`, GET-only, заголовки `X-User-Id`/`X-User-Roles` срезаются);
`POST/PUT/DELETE /api/v0/tariffs[/{id}]` — через `billing-tariffs-admin` (auth-фильтр gateway)
**и** `hasRole("ADMIN")` в `billing-service SecurityConfig`. Это баги §2 №1 и №7.

**Инфра-роуты за `adminAuthFilter`** (ROLE_ADMIN + JWT): `GET /v3/api-docs/swagger-config`,
`/admin/swagger/**`, `/admin/api-docs/{security|user|business|billing|generative-orchestration-service}`,
`/admin/rabbit/**`, `/admin/minio/**`, `/admin/monitoring/**`.

**Не роутятся через gateway (404):** `GET /users/check-me` (security),
`POST /internal/quota/check`, `POST /internal/quota/use/{userId}/{feature}` (billing).

---

## 2. Критичные баги и дыры — чинить первым

**Статус 2026-10-07: все 7 багов исправлены и покрыты тестами.**

| # | Проблема | Файл / путь | Суть | Статус |
|---|---|---|---|---|
| 1 | **CRUD тарифов недоступен (401)** | `api-gateway\...\ApiGatewayApplication.java`, `.route("billing-tariffs-public", ...)` | Роут идёт **без** `AuthenticationGatewayFilterFactory` → `X-User-Id` не подставляется, токен не валидируется → `anyRequest().authenticated()` даёт 401 даже админу. GET работает (public), POST/PUT/DELETE через gateway физически недоступны | ✅ исправлен: `billing-tariffs-public` теперь GET-only, рядом `billing-tariffs-admin` с `authFilter`; на public-роуте срезаются `X-User-Id`/`X-User-Roles` |
| 2 | **Баны не применяются** | `security-service\...\service\BanService.java` | grep по репо: `isUserBanned` вызывается **только** из `BanController`. Ни `AuthService.loginUser`, ни gateway бан не проверяют → забанённый спокойно логинится. Эндпоинты банов — запись в БД без эффекта | ✅ исправлен: `BanService.requireNotBanned(userId)` → `403 USER_BANNED`; вызывается в `loginUser`, `refreshToken`, `oauthBootstrap`, `YandexAuthService`, `OAuth2SuccessHandler` (веб-вход редиректит на `login?error=banned`). Попутно `isUserBanned` был `@Transactional(readOnly=true)` — деактивация истёкшего бана не флушилась |
| 3 | **pgAdmin без авторизации** | `ApiGatewayApplication.java`, `.route("pgadmin-ui-route", ...)` | Единственный admin-UI-роут **без** `adminAuthFilter` (rabbit/minio/monitoring/swagger — с ним). Прямой доступ к БД всех сервисов при известных кредах pgAdmin | ✅ исправлен: добавлен `adminAuthFilter` |
| 4 | `/internal/quota/*` в `permitAll` | `billing-service\...\config\SecurityConfig.java`, `QuotaController.java` (`/internal/quota`) | Внутренние эндпоинты инкремента квот без аутентификации. Спасает только отсутствие gateway-роута; при ошибке роутинга — публичная дыра на любые квоты | ✅ исправлен: `InternalSecretFilter` (заголовок `X-Internal-Secret`, `MessageDigest.isEqual`, fail-closed); `QuotaClient` в GOS шлёт секрет |
| 5 | `GET /orchestration/tasks/{taskId}` без проверки владельца | `generative-orchestration-service\...\AsyncGenerationController.java` | Нет параметра `Authentication` — любой авторизованный читает статус любой чужей AI-задачи (UUID угадывается) | ✅ исправлен: `checkTask(taskId, userId)`, чужая задача → **404** (`ContentNotFoundException`), как у постов |
| 6 | Нет admin-варианта «разлогинить везде» | `security-service\...\AuthController.java` | `DELETE /auth/sessions` → `deleteByUserIdExceptToken` — завершает всё **кроме текущей** и только для себя. Принудительный logout чужого пользователя невозможен, хотя `deleteByUser` / `findAllByUserId` готовы | ✅ исправлен: `GET/DELETE /admin/accounts/{userId}/sessions` (`AdminUserService.listSessions`/`revokeAllSessions`) |
| 7 | У тарифов не проверяется роль | `billing-service\...\TariffController.java` (`@RequestMapping("/tariffs")`) + `SecurityConfig.java` | Путь не под `/admin/**` → `hasRole("ADMIN")` не срабатывает: при прямом обращении на `:8106` изменить тариф сможет **любой** аутентифицированный | ✅ исправлен: `GET /tariffs/**` → permitAll, остальные методы `/tariffs/**` → `hasRole("ADMIN")` (порядок матчеров: first-match-wins) |

**Найдено при фиксах (не было в отчёте):** входные `X-User-Id`/`X-User-Roles` срезались только на
роутах с `authFilter`. Публичные роуты `security-service-public`, `ai-callback-public` и
`minio-s3-public` пускали подделанные заголовки в сервис (на практике реальный impact был только у
тарифов). `security-service-public` и `ai-callback-public` теперь срезают оба заголовка;
`minio-s3-public` ведёт в MinIO и на них не смотрит.

**Замечание по тестам:** `billing-service WebMvcTestConfiguration.testSecurityFilterChain`
(`securityMatcher("/tariffs/**", "/quota/**", "/admin/**", "/internal/**")` + `permitAll`)
перехватывает продовую цепочку в `@SpringBootTest` — `TariffSecurityIntegrationTest` исключает её из
скана, иначе правило роли не проверялось бы.

### Смежное
- `api-endpoints.json` / `api-endpoints-public.json` в корне репо устарели: нет
  `POST /orchestration/posts/{id}/regenerate` и `/regenerate/image`; `/admin/bans` и `/admin/projects`
  ошибочно указаны «напрямую в сервис» — через gateway они роутятся.
- `ucust-admin-spa` не проверяет роль на клиенте (403 приходит только с бэкенда) и не содержит ни
  одной бизнес-страницы — только Login + iframes Dozzle/MinIO/RabbitMQ/Swagger/PgAdmin.
- `GET /api/v1/ai/callback` (GOS) защищён только `X-Internal-Secret` (`MessageDigest.isEqual`),
  JWT не требуется — осознанный дизайн, но секрет в env/`application.yml`.
- Service discovery и config-service отсутствуют: URI сервисов захардкожены в
  `application.yml`, `configuration-service` — пустая заглушка.

---

## 3. Сущности без admin-эндпоинтов

### Полностью отсутствуют

| Сущность | Что это | Что есть сейчас | Чего нет |
|---|---|---|---|
| `Role`, `Privilege` | Роли/привилегии | `GET /admin/roles` (справочник) + `PUT /admin/accounts/{id}/roles` (полная замена с защитами `CANNOT_MODIFY_OWN_ROLES`/`ROLE_NOT_FOUND`/`LAST_ADMIN`) — **сделано 2026-10-07**; сидер `DataInitializer` создаёт `ROLE_ADMIN` + дефолтного админа, всем новым ставится `USER` | Создание/переименование ролей, выдача прав (Привилегии), фильтр пользователей по роли |
| `UserIdentity` | OAuth-идентичности | только косвенно через auth-флоу | Привязанные соцсети пользователя, отвязка админом |
| `Token` | Одноразовые токены (подтверждение почты, сброс пароля) | косвенно через `/auth/confirm-email`, `/auth/reset-password`, `/auth/change-email/*` | Активные/просроченные токены, принудительное подтверждение почты |
| `GenerationTask` (GOS) | Задачи AI-генерации | `GET /orchestration/tasks/{taskId}` (с 2026-10-07 — с проверкой владельца) | ✅ **P1 2026-10-08**: `GET /admin/orchestration/tasks?status=&userId=`, `POST .../tasks/{id}/retry`, `DELETE .../tasks/{id}` (без отмены живого потока — защита 409). Не сделано: очередь зависших |
| `PostStatistics` (GOS) | Метрики постов | только поля внутри `PostResponse` | Списки и агрегаты по проекту/пользователю/периоду |
| `BusinessAnalysis` (GOS) | Онбординг-анализ бизнеса | `POST /orchestration/analysis`, `GET .../analysis/{id}` — **self** | Список всех, чужие, удаление |
| `ChannelAnalysis` (GOS) | Анализ Telegram-канала | `POST /orchestration/channel/analyze`, `GET .../analysis/{id}` — **self** | Список всех, чужие |
| `ProjectChannelSettings` (GOS) | Настройки автоскана канала | `GET/PUT /orchestration/channel/settings?projectId` — **self** | Просмотр/правка настроек любого проекта |
| `OutboxEvent` | Событийный outbox | нет (инфраструктура) | Журнал событий/ошибок доставки |
| `notification-service` | Email по событиям, 8 Thymeleaf-шаблонов | **нет ни БД, ни контроллеров, ни HTTP** | История писем, повторная отправка, шаблоны |
| Платежи (`Payment`/`Transaction`/`Invoice`) | — | `POST /quota/me/purchase` — **заглушка**: «здесь будет вызов платёжного провайдера (mock success)» | Вся платёжная история. Сущности нет ни в одной БД |
| Системные настройки | — | всё через env/`application.yml` | CRUD настроек (дефолтные лимиты, фиче-флаги, дефолтный тариф) |
| Аудит действий админа | — | `RequestLoggingFilter` (лог-файл gateway) + поле `bannedBy` | Кто/когда/что изменил или удалил |

### Есть, но только self-варианты или частично

| Сущность | Self / что есть | Admin-чего не хватает | Стадия |
|---|---|---|---|
| `RefreshToken` (сессии) | `GET/DELETE /auth/sessions`, `DELETE /auth/sessions/{id}` — только для себя | `GET/DELETE /admin/accounts/{id}/sessions` | ✅ **сделано 2026-10-07** (`AdminUserService.listSessions`/`revokeAllSessions` + контроллер) |
| `UserProfile` | `GET/PATCH /user/me`, `POST /user/me/avatar`; admin было только чтение | `PATCH /admin/users/{id}`, `POST .../avatar` | ✅ **сделано 2026-10-07** (admin-обёртки над `ProfileService.update/uploadAvatar`) |
| `Project` | self полный CRUD; admin `GET/PATCH/DELETE /admin/projects/{id}` | Список/поиск/фильтр по владельцу + создание от имени пользователя | ✅ **P1 2026-10-08** (`listPostsAsAdmin`-аналог: `listAsAdmin` + `createAsAdmin`) |
| `Post` (GOS) | self 13 эндпоинтов; admin 4 — по id/projectId | Глобальный список с фильтрами (модерационная очередь); publish/reject | ✅ **P1 2026-10-08** (`GET /admin/orchestration/posts?status=&userId=&projectId=`, `POST .../publish`, `POST .../reject`) |
| `UserSubscription` | self `GET /quota/me/tariff`, `POST /quota/me/purchase`; admin — по одному userId | Список всех подписок, отмена, продление, история | ✅ **P1 2026-10-08** (список + `DELETE` cancel + `POST .../extend`). Не сделано: история изменений; **в billing нет ни одного `@Scheduled`** (ручной reset квот) |
| `UsageCounter` (квоты) | только агрегатом в `GET /quota/me/tariff` | `GET /admin/billing/quota/{userId}`, `.../usage/{userId}`, ручной reset | ✅ **P1 2026-10-08** |
| `Tariff` | self `GET /tariffs[/{id}]`; `POST/PUT/DELETE /tariffs` | `/admin/billing/tariffs` как отдельный путь | ✅ **CRUD работает 2026-10-07** через `POST/PUT/DELETE /api/v0/tariffs[/{id}]` (см. §2 №1, №7) |
| `Ban` | admin-эндпоинты есть (6 шт.) | Уведомление о бане, self-проверка | ✅ **вход блокируется 2026-10-07** (`requireNotBanned` во всех точках входа); публикации события в `createBan` по-прежнему нет |
| `User` (роли/статус) | — | Расширенный `GET /admin/accounts/{id}` (email, provider, все провайдеры, роли, enabled/lockTime/failedAttempts, активный бан); `PUT .../roles`; `POST/DELETE .../block` | ✅ **сделано 2026-10-07**. Не сделано: **дата регистрации** (колонки `created_at` нет ни в `users`, ни в `user_profile` — нужна миграция + бэкфилл), «разблокировать по rate-limit» (нет метода) |

---

## 4. Итоговый список недостающих эндпоинтов

Стадии: **нет вообще** · **сервис-метод есть, контроллера нет** · **есть только self-endpoint** · **контроллер есть, нерабочий**.

### Пользователи
| Эндпоинт | Стадия |
|---|---|
| `GET /admin/accounts/{id}` расширенный (email, провайдер, все провайдеры, роли, enabled/locked/lockTime, активный бан) | ✅ **сделано 2026-10-07** (путь через security, а не user-service — там нет ролей/банов). Даты регистрации нет: колонки `created_at` не существует |
| `PUT /admin/accounts/{id}/roles` — выдать/отобрать роли | ✅ **сделано 2026-10-07** (`GET /admin/roles` — справочник). Путь `/admin/users/{id}/roles` не используется — роут gateway `/admin/users/**` идёт в user-service, где ролей нет |
| `GET /admin/roles` — справочник ролей | ✅ сделано (новый gateway-роут `security-service-admin-roles`) |
| `POST/DELETE /admin/accounts/{id}/block` — мягкое отключение + кик сессий | ✅ сделано (`enabled=false` + `deleteByUser`; `CANNOT_BLOCK_SELF`; идемпотентно) |
| `PATCH /admin/users/{id}` — правка профиля админом | ✅ сделано (обёртка над `ProfileService.update`) |
| `POST /admin/users/{id}/avatar` | ✅ сделано (обёртка над `ProfileService.uploadAvatar`) |
| `POST /admin/users/{id}/resend-confirmation` / подтверждение за пользователя | частично (`AuthService.resendConfirmToken(email)` есть) |
| `DELETE /admin/users/{id}` — soft-delete / деактивация | нет вообще (есть только жёсткий `DELETE /admin/accounts/{id}`) |

### Проекты / посты (модерация)
| Эндпоинт | Стадия |
|---|---|
| `GET /admin/projects?search=&ownerId=&page=&limit=` | ✅ **P1 2026-10-08** (`listAsAdmin`, search по имени case-insensitive; параметр `limit`=size, 1..200) |
| `GET /admin/posts?status=&userId=&projectId=&from=&to=&page=` | ✅ **P1 2026-10-08** частично: `GET /admin/orchestration/posts?status=&userId=&projectId=` через `JpaSpecificationExecutor<Post>`; диапазонов дат (`from`/`to`) нет |
| `POST /admin/projects` — создание от имени пользователя (поддержка/тесты) | ✅ **P1 2026-10-08** (`createAsAdmin(ownerId, request)` → `ProjectService.create`, событие `project.created` уходит владельцу) |
| `POST /admin/orchestration/posts/{id}/publish` | ✅ **P1 2026-10-08** (owner-проверка снята, AI-контур зовётся каналом владельца) |
| `POST /admin/orchestration/posts/{id}/reject` | ✅ **P1 2026-10-08** |
| `GET /admin/orchestration/tasks?status=&userId=&page=` | ✅ **P1 2026-10-08** |
| `POST .../tasks/{id}/retry`, `DELETE .../tasks/{id}` | ✅ **P1 2026-10-08** (retry: только non-COMPLETED, 409 для in-flight; delete: только не in-flight, placeholders снимаются, готовые посты сохраняются) |
| `GET /admin/orchestration/channel/analysis`, `GET /admin/orchestration/analysis` (списки) | есть только self-endpoint |

### Биллинг
| Эндпоинт | Стадия |
|---|---|
| `GET/POST/PUT/DELETE /tariffs[/{id}]` (через gateway `/api/v0/tariffs`) | ✅ работает (2026-10-07, §2 №1/№7) — отдельного `/admin/billing/tariffs` пути нет |
| `GET /admin/billing/subscriptions?active=&tariffId=&page=` | ✅ **P1 2026-10-08** (`listSubscriptions` по `UserSubscriptionRepository`) |
| `DELETE /admin/billing/subscriptions/{userId}` — отмена | ✅ **P1 2026-10-08** (soft-cancel: `paid=false`, FREE остаётся активной) |
| `POST /admin/billing/subscriptions/{userId}/extend` | ✅ **P1 2026-10-08** (`?months=1..120`, `paid=true` + `nextResetDate` += месяцев) |
| `POST /admin/billing/quota/reset/{userId}` | ✅ **P1 2026-10-08** (405-кода нет: path `/quota/reset/{userId}`) |
| `GET /admin/billing/quota/{userId}`, `GET /admin/billing/usage/{userId}` | ✅ **P1 2026-10-08** (прямое чтение агрегата и по-модельных счётчиков; usage без подписки — пустой список) |
| `GET /admin/billing/payments` | нет вообще (сущности нет, purchase — мок) |
| `@Scheduled` сброса квот по `nextResetDate` | нет вообще (в billing нет ни одного) |

### Безопасность / сессии
| Эндпоинт | Стадия |
|---|---|
| `GET /admin/accounts/{id}/sessions` | ✅ сделано 2026-10-07 |
| `DELETE /admin/accounts/{id}/sessions` — принудительный logout везде | ✅ сделано 2026-10-07 |
| Блокировка входа при активном бане | ✅ сделано 2026-10-07 (`requireNotBanned`, §2 №2) |
| Публикация события / email о бане | нет вообще (в `createBan` события нет) |
| `GET /admin/sessions` — глобальный список | нет вообще (возможно, не нужен) |
| `GET /admin/users/{id}/login-history` | нет вообще (ip/userAgent только в живых `RefreshToken`) |
| Self-проверка бана/блокировки | ✅ сделано 2026-10-07: `GET /api/v0/status/ban` → `AccountStatusResponse{blocked, banned, ban}` (security, новые gateway-путь + `BanService.getAccountStatus`) |

### Аналитика / статистика — **нет вообще**
| Эндпоинт | Примечание |
|---|---|
| `GET /admin/stats/overview` | пользователи, проекты, посты, подписки, квоты |
| `GET /admin/posts/statistics?projectId=&from=&to=` | `PostStatistics` пишется, но не читается |
| `GET /admin/stats/users?from=&to=` | регистрации/активность по дням |
| `GET /admin/stats/billing` | MRR, распределение по тарифам |
| `GET /admin/stats/generation` | расход AI-задач, ошибки, доля успешных |
| `GET /admin/stats/system` | есть только `/actuator/health` и Dozzle |

### Системное — **нет вообще**
| Эндпоинт | Примечание |
|---|---|
| `GET/PUT /admin/settings` | нет ни сущности, ни таблицы |
| `GET /admin/audit-logs?actor=&action=&from=&to=` | только лог-файлы и `bannedBy` |
| `GET /admin/notifications`, `POST .../resend` | notification-service без БД и HTTP |
| `GET/PUT /admin/settings/notifications` | 8 шаблонов Thymeleaf захардкожены |
| `GET /admin/outbox` | таблица `OutboxEvent` есть |
| `GET /admin/services` | URI в `application.yml`, discovery отсутствует |

---

## 5. Что уже покрыто текущей панелью (`UcustAdminVibecode`)

- Пользователи: `GET /admin/users` (пагинация/поиск), карточка, подписка, история банов, выдача тарифа
- Блокировки: полный набор из 6 эндпоинтов
- Тарифы: `GET /tariffs` + просмотр/выдача подписки
- Сессии: `GET/DELETE /auth/sessions[/{id}]` (только свои)

**Не используются из уже существующих:** `GET /admin/users/{id}`, `GET/PATCH/DELETE /admin/projects/{id}`,
`GET/PATCH/DELETE /admin/orchestration/posts/{id}`, `GET /admin/orchestration/projects/{id}/posts`,
`DELETE /admin/accounts/{id}`.

**Появилось 2026-10-07 (панель может подключать):** `GET/DELETE /admin/accounts/{userId}/sessions`,
`POST/PUT/DELETE /api/v0/tariffs[/{id}]` (админ-запись тарифов наконец работает).
**P0 (2026-10-07, см. §6):** `GET /admin/accounts/{userId}`, `PUT .../roles`, `POST/DELETE .../block`,
`GET /admin/roles`, `PATCH /admin/users/{userId}`, `POST /admin/users/{userId}/avatar`,
`GET /status/ban` (self).
**P1 (2026-10-08, см. §7):** списки и действия по подпискам/квотам/проектам/постам/задачам — панель может
заменить self-чтения на глобальные и добавить кнопки «Отменить подписку», «Продлить», «Опубликовать»,
«Отклонить», «Перезапустить задачу».

---

## 6. Реализованный P0 — карточка/роли/блокировка пользователя

Реализовано 2026-10-07 после приоритизации §3/§4. Все эндпоинты имеют тесты.

- **security-service** (`/api/v0/admin/accounts/**`, gateway-роут `security-service-admin-users` уже был):
  - `GET /admin/accounts/{userId}` → `AdminUserDetailResponse{id, email, provider, linkedProviders, roles, enabled, accountNonLocked, lockTime, failedAttempts, activeBan}`;
  - `PUT /admin/accounts/{userId}/roles` (body `{roles: ["USER","ADMIN"]}`) — полная замена.
    Защиты: `CANNOT_MODIFY_OWN_ROLES` (409), `ROLE_NOT_FOUND` (400), `LAST_ADMIN` (409, `UserRepository.countByRoles_Name`).
    Замечание: access-токен несёт старые роли до следующего login/refresh;
  - `POST /admin/accounts/{userId}/block` → `enabled=false` + сброс всех refresh-сессий (кик). `CANNOT_BLOCK_SELF` (409). Идемпотентно;
  - `DELETE /admin/accounts/{userId}/block` → `enabled=true`. Идемпотентно;
  - `GET /status/ban` (self, роут `/api/v0/status/ban`) → `AccountStatusResponse{blocked, banned, ban}` —
    `BanService.getAccountStatus`, бан и мягкое отключение показываются вместе.
- **security-service, справочник ролей:** `GET /admin/roles` → `List<RoleResponse{id,name}>`;
  новый gateway-роут `security-service-admin-roles` (`/admin/roles`, `/admin/roles/**`).
- **user-service** (`/api/v0/admin/users/**`): `PATCH /admin/users/{userId}` и
  `POST /admin/users/{userId}/avatar` — обёртки над существующими `ProfileService.update/uploadAvatar`,
  та же валидация, что у self-версий.
- **gateway:** добавлен `/status/ban` в `security-service-private`; маршрут `/admin/roles/**` → security.
- **Тесты:** security 279 (без падений), user 37 (1 экологическое `contextLoads`: нужен живой PostgreSQL).
  Новое: `AdminRoleControllerTest` (1), +ещё 13 в `AdminUserControllerTest`/`AdminUserServiceTest`/`StatusControllerTest`,
  user: `AdminUserControllerTest` (5).
- **Сознательно НЕ сделано:** дата регистрации (нет колонки `created_at`; деактивация rate-limit
  отдельным методом; уведомление/email о бане).

---

## 7. Реализованный P1 — биллинг и модерация

Реализовано 2026-10-08 по приоритизации §3/§4. Все эндпоинты под `ROLE_ADMIN` (gateway-роуты
`/api/v0/admin/**` уже существовали). Все имеют тесты; полные сьюты billing/business/GOS зелёные.

- **billing-service** (6 эндпоинтов):
  - `GET /admin/billing/subscriptions?active=&tariffId=&page=&size=` → `AdminSubscriptionsPage`
    (фильтр `active` по `paid`, `tariffId` по подписке; size 1..100);
  - `DELETE /admin/billing/subscriptions/{userId}` — soft-cancel (`paid=false`); FREE остаётся активной,
    платные перестают считаться активными; нет подписки — 404;
  - `POST /admin/billing/subscriptions/{userId}/extend?months=1..120` — `paid=true` + `nextResetDate` += месяцев
    (база = `max(nextResetDate, now)`, UTC); нет подписки — 404;
  - `GET /admin/billing/quota/{userId}` → `AdminQuotaResponse{quota, used, remaining}` (агрегат);
    нет подписки — 404;
  - `GET /admin/billing/quota/usage/{userId}` → `List<UsageCounterResponse{model, used, total}>`;
    нет подписки — пустой список;
  - `POST /admin/billing/quota/reset/{userId}` → 204 (не трогает дату сброса `nextResetDate`);
    нет подписки — 404.
- **business-service** (2 эндпоинта):
  - `GET /admin/projects?search=&ownerId=&page=&size=` → `AdminProjectsPage{items,total,page,size}` —
    `search` по имени case-insensitive, size 1..200;
  - `POST /admin/projects` — body `{ownerId, project:{...}}` → создание от имени владельца
    (`ProjectService.create`; событие `project.created` уходит реальному владельцу через RabbitMQ).
- **generative-orchestration-service** (6 эндпоинтов):
  - `GET /admin/orchestration/posts?status=&userId=&projectId=&page=&size=` → `AdminPostsPage` —
    фильтры через `JpaSpecificationExecutor<Post>` (раньше проверки владельца обходились per-id);
  - `POST /admin/orchestration/posts/{id}/publish` — публикация чужого поста (owner-проверка снята,
    AI-контур зовётся каналом владельца); `POST .../reject` — перевод в `REJECTED`;
  - `GET /admin/orchestration/tasks?status=&userId=&page=&size=` → `AdminTasksPage` (derived pageable);
  - `POST /admin/orchestration/tasks/{id}/retry` → 202 — сброс ошибки, новый плейсхолдер, фоновый запуск
    тем же путём, что обычная генерация (`schedule` + `runGeneration`); COMPLETED — 409 `TASK_ALREADY_COMPLETED`,
    in-flight — 409 `TASK_IN_FLIGHT`, нет задачи — 404;
  - `DELETE /admin/orchestration/tasks/{id}` → 204 — снятие незаполненных заглушек
    (`discardGeneratingPosts`), готовые посты задачи сохраняются; in-flight — 409.
- **Тесты:** billing `QuotaServiceTest` (+9), `SubscriptionServiceTest` (+9), `AdminBillingControllerTest` (4),
  `AdminSubscriptionControllerTest` (+6); business `ProjectServiceTest` (+7), `AdminProjectControllerTest` (5);
  GOS `GenerationServiceTest` (+8), `AsyncGenerationServiceTest` (+9), `AdminPostControllerTest` (5),
  `AdminTaskControllerTest` (7). Сводно: billing 115+, business 170+, GOS 260+ (полные сьюты зелёные;
  экологических падений нет).
- **Сознательно НЕ сделано:** `@Scheduled`-автосброс квот по `nextResetDate` (в billing нет ни одного;
  ручной reset работает); range-фильтры `from`/`to` в списке постов; история изменений подписки;
  списки `channel/analysis` и `analysis` (self-only).
