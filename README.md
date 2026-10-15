## Запуск в Windows

Откройте в IDEA **корневой pom.xml** рядом с этим README. PostgreSQL и база
`user_db` используются из предыдущей работы. Нужен пароль своего PostgreSQL.

В терминале из корня проекта:

```powershell
docker compose up -d --wait
mvn clean verify
```

`verify` запускает все тесты. PostgreSQL для интеграционных тестов создаётся
отдельно через Testcontainers; рабочая база не затрагивается. Kafka в тестах
поднимается через EmbeddedKafka, почта — через GreenMail на случайном порту.
Тестовые письма не уходят в интернет.

Первый терминал — user-service:

```powershell
$credential = Get-Credential -UserName postgres -Message 'Пароль PostgreSQL'
$env:DB_USERNAME = $credential.UserName
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password
java -jar user-service/target/user-service-1.0-SNAPSHOT.jar
```

Второй терминал, также из корня проекта:

```powershell
java -jar notification-service/target/notification-service-1.0-SNAPSHOT.jar
```

- Пользователи и веб-интерфейс: http://localhost:8080
- Почтовый ящик Mailpit: http://localhost:8025
- API уведомлений: http://localhost:8081/api/notifications

Создайте пользователя через веб-интерфейс и откройте Mailpit: появится письмо
о создании. Затем удалите пользователя — придёт письмо об удалении.
Обновление пользователя письма не отправляет. Старые записи при запуске
приложения не вызывают уведомлений. Mailpit перехватывает письма для любого
адреса: это локальная проверка, а не отправка на реальную внешнюю почту.

## Отдельный API уведомлений

`POST /api/notifications` на порту 8081. JSON:

```json
{"operation":"CREATED","email":"ivan@example.com"}
```

Допустимые операции: `CREATED` и `DELETED`. Успех — `204 No Content` после
того, как SMTP-сервер принял письмо. Неверный запрос — `400`, ошибка SMTP —
`503`. API отправляет письмо напрямую и не создаёт/удаляет пользователя.

```powershell
$body = @{ operation = 'CREATED'; email = 'ivan@example.com' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/api/notifications' -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

Для удаления замените `CREATED` на `DELETED`. 

## Настройки

Пароли передаются через переменные окружения, не через Git. Файл `.env`
Spring Boot автоматически не читает; используйте переменные терминала или
Environment variables в конфигурации запуска IDEA.

| Переменная | Значение по умолчанию / назначение |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/user_db` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | Обязателен для запуска user-service |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` для обоих сервисов |
| `KAFKA_TOPIC` | `user-events`, одинаковый в обоих сервисах |
| `SERVER_PORT` | 8080 у user-service, 8081 у notification-service |
| `MAIL_HOST`, `MAIL_PORT` | `localhost`, `1025` (Mailpit) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Пустые |
| `MAIL_AUTH` | `false`, для SMTP с авторизацией — `true` |
| `MAIL_STARTTLS` | `false`, для SMTP STARTTLS — `true` |
| `MAIL_FROM` | `no-reply@example.test`, для реального SMTP — разрешённый адрес |
| `SITE_NAME` | `ваш сайт` |


## Тесты

```powershell
mvn test
mvn clean verify
```

- `test`: быстрые тесты сущности, сервиса, контроллеров и outbox publisher.
- `verify`: дополнительно PostgreSQL CRUD, атомарность outbox, публикация в Kafka,
  отправка писем через API и Kafka, UTF-8, валидация, повтор после ошибки SMTP,
  перенос неверного события в DLT и обработка следующего корректного события.

Тесты изолированы: отдельные PostgreSQL-контейнеры, очистка таблиц и почтового
ящика, отдельные тестовые топики и группы, случайные порты. Параллельный запуск
JUnit отключён. Отчёты — в `*/target/surefire-reports` и `*/target/failsafe-reports`.

Только интеграционные проверки почты, без Docker:

```powershell
mvn -pl notification-service verify
```

Остановка сервисов: Ctrl+C в каждом терминале. Остановка инфраструктуры:
`docker compose down`. Том Kafka сохраняется. Обычная остановка Mailpit
с пересозданием контейнера очищает его временный ящик.
