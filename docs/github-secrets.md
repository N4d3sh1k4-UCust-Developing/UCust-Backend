# GitHub Actions: secrets и variables (UCust Backend)

Где задаются: **Settings → Secrets and variables → Actions**.
Environments не используются — всё на уровне репозитория.
Workflow: `.github/workflows/main.yml` (build 9 образов + деплой на `rinhbaby`).

> ⚠️ Реальные значения секретов в этом файле **не дублируются** — они живут
> только в GitHub. Ниже указано, что там лежит, зачем и в каком формате.
> Несекретные значения (URL, email, адреса) приведены как есть — они уже
> публичны в коде/конфигах.
>
> ⚠️ Любой push в `master`/`main` запускает пересборку и **деплой** (в workflow
> нет `paths`-фильтра). Правка этой документации тоже выкатит стек.

## Secrets

| Переменная | Обяз. | Где используется | Значение / формат |
|---|---|---|---|
| `SUBMODULES_PAT` | да | `actions/checkout` build-джобы (`token` + `submodules: recursive`) | PAT с правом read на все `backend-*` и сервисные репозитории организации. Без него сабмодули не склонируются |
| `SERVER_TAILSCALE_IP` | да | ssh/scp в deploy-джобе (host) | Tailscale-IP сервера, не публичный. В Java-коде не читается; в compose прокидывается в user/business, но там не используется (легаси) |
| `SERVER_USER` | да | ssh/scp | `n4d3sh1k4` |
| `SERVER_SSH_KEY` | да | ssh/scp | приватный ключ (PEM/OpenSSH) для этого пользователя |
| `TS_OAUTH_CLIENT_ID` | да | `tailscale/github-action` | OAuth-клиент Tailscale, тег `tag:ci` |
| `TS_OAUTH_SECRET` | да | `tailscale/github-action` | секрет того же клиента |
| `DB_PASSWORD` | да | security, user, business, billing, gen-ort, content, audit | пароль пользователя `ETA_DBUser` в PostgreSQL (общий на все БД) |
| `JWT_SECRET_ACCESS` | да | gateway, security, billing, gen-ort | база для подписи access-токенов, base64, **одинаковая** во всех сервисах |
| `DEFAULT_ADMIN_EMAIL` | да | security | `superadmin@adminparadise.ru` (админ создаётся при первом старте) |
| `DEFAULT_ADMIN_PASSWORD` | да | security | пароль дефолтного админа; применяется только при создании пользователя |
| `YANDEX_CLIENT_ID` / `YANDEX_CLIENT_SECRET` | да | security | OAuth-приложение Яндекса (веб + mobile) |
| `VK_CLIENT_ID` / `VK_CLIENT_SECRET` | да | security | OAuth-приложение VK |
| `GEO_API_URL` | да | security | `http://ip-api.com/json/{ip}?lang=ru&fields=status,country,city` — определение города по IP для claim `city` |
| `INTERNAL_API_SECRET` | **да** | gen-ort (исходящие к AI + входящий callback), billing (`/internal/quota/*`), AI-контур | общий S2S-секрет для заголовка `X-Internal-Secret`. Должен **совпадать** с `.env` AI-контура. Пустое значение = 401 на `/internal/**` и блокировка квот |
| `AI_ORCHESTRATOR_ENDPOINT` | **да** | gen-ort | база Единого Шлюза: `http://10.0.0.2:8000` (без пути). Через неё идут `/api/v1/orchestrator/execute`, `/api/v1/ai/rag/ingest`, `/api/v1/ai/critic/review` и скачивание картинок контура |
| `AI_SERVICE_URI` | нет | нигде | **не используется в коде** (остаток от удалённого `AIServiceClient`). Можно удалить из secrets/workflow |
| `SUPPORT_EMAIL` | да | notification | `support@ucust.com` — подставляется в письма |
| `FRONTEND_URL` | да | notification | `https://ucust.ru` — ссылки в письмах |
| `MAIL_HOST` | да | notification | `smtp.gmail.com` |
| `MAIL_PORT` | да | notification | `587` |
| `MAIL_USERNAME` | да | notification | логин SMTP (полный адрес) |
| `MAIL_PASSWORD` | да | notification | пароль приложения SMTP |
| `MAIL_SSL` | да | notification | `false` (для 587 — STARTTLS) |
| `MAIL_FROM` | да | notification | `contact@n4d3sh1k4.site` — адрес в заголовке `From` |
| `MAIL_FROM_NAME` | нет | notification | отображаемое имя отправителя, дефолт `UCust` |
| `CONTENT_MINIO_ACCESS_KEY` | да | content-service | `content-service-user` (scoped-юзер MinIO) |
| `CONTENT_MINIO_SECRET_KEY` | да | content-service | пароль этого юзера; в `minio-init` — `ct-strong-pass` |
| `CONTENT_MINIO_PUBLIC_BASE_URL` | нет | content-service | `https://api.ucust.n4d3sh1k4.site/s3/content-service` (дефолт в compose) |

`GITHUB_TOKEN` создавать не нужно — его выдаёт сам Actions (используется для
логина в `ghcr.io` и push образов).

## Variables (`vars.*`)

| Переменная | Обяз. | Где используется | Значение |
|---|---|---|---|
| `CORS_ALLOWED_ORIGINS` | нет | gateway (`cors.allowed-origins`) | список origin'ов через запятую **без пробелов**, напр. `https://ucust.ru,https://www.ucust.ru,https://adm.ucust.n4d3sh1k4.site`. Если не задана — compose/`application-prod.yml` подставят дефолт: `https://api.ucust.n4d3sh1k4.site,https://adm.ucust.n4d3sh1k4.site,https://ucust.ru,https://www.ucust.ru` |

## Не идут через Actions (только `.env` на сервере / дефолты compose)

Эти значения CI не передаёт — они берутся из `~/projects/ucust/.env` (или
дефолта в `docker-compose.yml`). При перезаливе их надо заполнить руками.

| Переменная | Дефолт | Где используется | Сейчас на сервере |
|---|---|---|---|
| `QUOTA_FREE_UNLIMITED` | `false` | billing (FREE-тариф = `-1` во всех лимитах) | `true` |
| `MINIO_ENDPOINT` | `http://minio:9000` | content, gen-ort | не задан (дефолт) |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | — | gen-ort (запись медиа) | `admin` / `password123` (root-креды) |
| `MINIO_PUBLIC_BASE_URL` | — | gen-ort | `https://api.ucust.n4d3sh1k4.site/s3/generative-orchestration` |
| `USER_MINIO_ACCESS_KEY` / `USER_MINIO_SECRET_KEY` | `admin` / `password123` | user-service (аватары) | не заданы (root-креды) |
| `TELEGRAM_BOT_TOKEN` | пусто | gen-ort (verify канала через Bot API) | **отсутствует** → fallback на t.me (из RU-сети не работает) |
| `TELEGRAM_BOT_PROXY` | `http://xray:1080` | gen-ort | не задан (дефолт работает, xray в app-стеке) |
| `MINIO_S3_URI` | `http://minio:9000` | gateway (публичный роут `/s3/**`) | не задан (дефолт) |

## Быстрая проверка перед деплоем

```bash
gh secret list   --repo N4d3sh1k4-UCust-Developing/UCust-Backend
gh variable list --repo N4d3sh1k4-UCust-Developing/UCust-Backend
```

Минимум, без которого UCust работает «наполовину»: `INTERNAL_API_SECRET`,
`AI_ORCHESTRATOR_ENDPOINT`, `TELEGRAM_BOT_TOKEN` (последний — только для
верификации Telegram-каналов).
