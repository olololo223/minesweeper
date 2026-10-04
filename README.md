# Minesweeper — клиент-серверная игра

Сетевая версия «Сапёра» с рейтингом, статистикой и админ-панелью.

## Архитектура

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│  Desktop client │◄───────►│   Netty server  │◄───────►│      MySQL      │
│ (JavaFX/Swing)  │  TLS    │  (бизнес-логика)│  JDBC   │                 │
└─────────────────┘         └─────────────────┘         └─────────────────┘
                                      ▲
                                      │ HTTPS
                            ┌─────────┴─────────┐
                            │  Admin web panel  │
                            │   (Spring Boot)   │
                            └───────────────────┘
```

## Модули

| Модуль | Назначение | Порт |
|--------|-----------|------|
| `common` | Протокол + DTO (общее для клиента и сервера) | — |
| `server` | Игровой сервер на Netty, работает с БД | 9000 (TCP, TLS) |
| `client` | Игра (Swing), подключается к серверу | — |
| `admin` | Веб-админка на Spring Boot | 8443 (HTTPS) |

## Требования

- Java 17+
- Maven 3.8+
- MySQL 8+

## Подготовка БД

```sql
CREATE DATABASE minesweeper CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'minesweeper'@'localhost' IDENTIFIED BY 'minesweeper_pass';
CREATE USER 'minesweeper_admin'@'localhost' IDENTIFIED BY 'admin_pass';
GRANT SELECT, INSERT, UPDATE, DELETE ON minesweeper.* TO 'minesweeper'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON minesweeper.* TO 'minesweeper_admin'@'localhost';
FLUSH PRIVILEGES;
```

Затем выполни `db/schema.sql` (таблицы `users`, `game_records`, `leaderboard`, `admin_actions`).

## Сборка

```bash
mvn clean package
```

## Запуск

### 1. Игровой сервер

Без TLS (для разработки):

```bash
java -jar server/target/minesweeper-server-1.0-SNAPSHOT.jar
```

С TLS:

```bash
java -Dtls.cert=server-cert.pem -Dtls.key=server-key.pem \
     -jar server/target/minesweeper-server-1.0-SNAPSHOT.jar
```

Конфиг БД читается из `db.properties` в текущей директории (см. `db.properties.example`).

### 2. Клиент

```bash
java -jar client/target/minesweeper-client-1.0-SNAPSHOT.jar
```

В диалоге логина:
- Имя и пароль — любые (при первом входе нужно нажать «Регистрация»)
- Сервер / порт — по умолчанию `localhost:9000`
- Галочка TLS — включи, если сервер с TLS
- Сертификат — путь к `server-cert.pem`

### 3. Админка

```bash
java -jar admin/target/minesweeper-admin-1.0-SNAPSHOT.jar
```

Расположена на https://localhost:8443

## Протокол

Собственный бинарный протокол поверх TCP:

```
+---------+--------+------------------+
| length  |  type  |     payload      |
| 4 байта | 1 байт |  length байт     |
+---------+--------+------------------+
```

- `length` — длина payload в байтах
- `type` — ordinal `MessageType`
- `payload` — JSON (Jackson)

Порядок в pipeline: `TLS → Decoder → Encoder → RateLimiter → Handler`.

## Безопасность

- **Пароли игроков** — bcrypt (`spring-security-crypto`)
- **Пароль админа** — bcrypt, хранится хэшем в конфиге
- **Транспорт клиента** — TLS (опционально)
- **Админка** — HTTPS + CSRF-защита
- **Аудит** — все действия админа логируются в `admin_actions`
- **Rate limiting** — token bucket на каждый канал

## Лицензия

MIT