# Перезалив тестового стенда UCust (ноябрь 2026)

Стенд: `rinhbaby` (192.168.0.101), деплой в `~/projects/ucust` (приложение) и
`~/projects/ucust/ucust-ops` (инфраструктура). Полный снос оправдан: стенд
тестовый, а схема БД за 2.5 недели отставания накопила бы ручные миграции
(`tariffs.highlighted`, отсутствующие таблицы, отсутствующие БД).

После перезалива **не нужны** никакие ручные SQL-миграции: `ddl-auto: update`
создаёт схему с нуля, `init.sql` создаёт все БД, `minio-init` — бакеты и
scoped-юзеров, `DataInitializer` — роли и админа.

---

## 0. Предусловия (проверить до сноса)

- [ ] Родительский репозиторий запушен: локальный `master` был впереди
      `origin/master` на 5 коммитов (content-service, audit-log-service,
      матрица ролей). Без пуша CI не соберёт образы.
- [ ] В `ops` закоммичены изменения: `postgres/init.sql`,
      `scripts/ensure-databases.sh`, `docker-compose.yml` (бакет и юзер
      `content-service`), `minio/policies/content-service-rw.json`.
- [ ] В `~/projects/ucust/.env` заполнены пустые сейчас ключи:
      `AI_ORCHESTRATOR_ENDPOINT`, `AI_SERVICE_URI`, `INTERNAL_API_SECRET`,
      `TELEGRAM_BOT_TOKEN`.
- [ ] (опционально) Repository variable `CORS_ALLOWED_ORIGINS` в GitHub Actions
      (Settings → Secrets and variables → Actions → Variables) — список origin'ов
      для CORS гейта через запятую, без пробелов. Если не задана, используется
      дефолт из `api-gateway/application-prod.yml` (прод-домены).
- [ ] Полный список переменных и их назначение — `docs/env-inventory.md`;
      шаблон серверного `.env` — `.env.example` (для ops — `ops/.env.example`).
- [ ] AI-шлюз доступен с сервера: `curl -m 5 http://<host>:8000/`.
      На 02.11.2026 `10.0.0.2:8000` пингуется, но TCP закрыт; `wg-ucust`
      до `11.0.0.2` без handshake. Пока шлюз не поднят, генерации и анализы
      будут падать в `FAILED` (как и сейчас: 6 `business_analysis`,
      5 `channel_analysis` в статусе FAILED, 9 постов в `GENERATING`).
- [ ] Свободно ~4 ГБ RAM под пик рестарта (сейчас ~1.5 ГБ; помогает
      `docker compose down` соседних стеков, если решите их тоже перезапустить).

## 1. Снести текущий стек

```bash
# приложение
cd ~/projects/ucust
docker compose down --remove-orphans

# инфраструктура вместе с данными (тест — данные не нужны)
cd ~/projects/ucust/ucust-ops
docker compose down -v

# освободить диск от старых образов
docker image prune -a -f
```

Соседние стеки (`reshala`, `pemz`, `me-xp`) **не трогаем** — они живут в своих
каталогах и на общий `ucust-net` не завязаны.

## 2. Поднять инфраструктуру

```bash
# вариант А (штатный): push в репозиторий ops -> workflow "Deploy Infrastructure"
#   скопирует конфиги, выполнит docker compose up -d и scripts/ensure-databases.sh

# вариант Б (вручную, если CI недоступен):
cd ~/projects/ucust/ucust-ops
docker compose up -d
bash scripts/ensure-databases.sh
```

Проверка:

```bash
docker exec postgres-db-ucust psql -U ETA_DBUser -tAc \
  "select datname from pg_database where datistemplate=false order by 1"
# ожидаем: audit_log_service_db, billing_service_db, business_service_db,
#          content_service_db, generative_orchestration_service_db,
#          notification_service_db, security_service_db, user_service_db

docker exec minio-ucust sh -c 'ls /data'
# ожидаем: business-service, content-service, generative-orchestration, user-service

docker exec minio-ucust sh -c 'ls /data/.minio.sys/config/iam/users'
# ожидаем: business-service-user, content-service-user,
#          generative-orchestration-user, user-service-user
```

`pgadmin` теперь **не поднимается** автоматически (профиль `tools`, экономит
~325 МБ). При необходимости:

```bash
cd ~/projects/ucust/ucust-ops && docker compose --profile tools up -d pgadmin
```

## 3. Поднять приложение

Push в `master` родительского репозитория → workflow «Full Stack Deployment»
собирает 9 образов и делает `docker compose pull && up -d --remove-orphans`.

Если нужно без CI:

```bash
cd ~/projects/ucust
docker compose pull
docker compose up -d --remove-orphans
```

## 4. Проверки после деплоя

```bash
# все 9 сервисов + xray в статусе Up
docker ps --format '{{.Names}}\t{{.Status}}' | grep ucust

# новые сервисы поднялись и засидировали данные
docker logs content-service-ucust 2>&1 | grep -iE 'Seeding|Filled content|ERROR'
docker logs audit-log-service-ucust 2>&1 | grep -iE 'audit-log-queue|ERROR'

# ни один сервис не ругнулся на схему
for c in $(docker ps --format '{{.Names}}' | grep ucust); do
  docker logs "$c" 2>&1 | grep -iE 'does not exist|Unsuccessful|SchemaManagement' && echo "^^ $c"
done

# публичный контент (без JWT)
curl -s https://api.ucust.n4d3sh1k4.site/api/v0/content/faq | head -c 300
curl -s https://api.ucust.n4d3sh1k4.site/api/v0/content/legal | head -c 300

# тарифы: 5 штук, включая ADMIN
docker exec postgres-db-ucust psql -U ETA_DBUser -d billing_service_db -tAc \
  "select name, highlighted, show_on_homepage from tariffs order by name"

# роли и админ
docker exec postgres-db-ucust psql -U ETA_DBUser -d security_service_db -tAc \
  "select name from roles order by name"
# ожидаем: ADMIN, GEN_ADMIN, MODERATOR, SUPPORT, USER

# память: ни один контейнер не должен быть близко к лимиту
docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}' | grep ucust
```

## 5. Если что-то пошло не так

| симптом | причина | что делать |
|---|---|---|
| контейнер в `Restarting`, exit code 137 | превышен memory-лимит | поднять лимит на 64–128 МБ в `docker-compose.yml`, `up -d` |
| `column ... does not exist` в логах | Hibernate не смог сделать DDL | проверить, что БД пустая (перезалив делался с `down -v`) |
| `database "content_service_db" does not exist` | не отработал `ensure-databases.sh` | выполнить `bash scripts/ensure-databases.sh` |
| `Access Denied` при загрузке PDF/логотипа | нет юзера `content-service-user` в MinIO | перезапустить `minio-init` (`docker compose up -d minio-init`) |
| генерация падает `AI orchestrator execution failed` | пустой/недоступный `AI_ORCHESTRATOR_ENDPOINT` | заполнить `.env`, `docker compose up -d` |
| логин админа не работает | изменился `DEFAULT_ADMIN_EMAIL/PASSWORD` | админ создаётся при первом старте из `.env`; сменить пароль через API |

## Память: как это устроено

`JAVA_OPTS` из compose **не работает**: Dockerfile'ы используют exec-форму
ENTRYPOINT, шелл не участвует, переменная в командную строку не попадает.
Используется `JAVA_TOOL_OPTIONS` — её JVM читает сама.

Лимиты выставлены по фактическим пикам cgroup (замер 02.11.2026):

| сервис | heap | лимит | фактический пик до правки |
|---|---|---|---|
| api-gateway | 128 M | 288 M | 245 M |
| security-service | 192 M | 448 M | 377 M |
| user-service | 160 M | 384 M | 324 M |
| business-service | 160 M | 384 M | 337 M |
| content-service | 160 M | 384 M | новый |
| audit-log-service | 128 M | 288 M | новый |
| notification-service | 160 M | 288 M | 210 M |
| billing-service | 160 M | 384 M | 303 M |
| generative-orchestration-service | 192 M | 448 M | 387 M |
| xray | — | 64 M | 41 M |
| **итого приложения** | | **3.3 ГБ** | |
| postgres / rabbitmq / minio / dozzle | — | 512 / 384 / 256 / 64 M | 146 / 141 / 146 / 12 M |
| pgadmin (профиль `tools`, по требованию) | — | 384 M | 325 M |

Правило при добавлении сервиса: `memory-лимит ≈ Xmx + 200 МБ`.
Свободный слот под 10-й сервис — medium (`-Xmx160m`, лимит 384 M).

Ожидаемое фактическое потребление: ~2.4 ГБ приложения + ~0.5 ГБ инфраструктуры.
