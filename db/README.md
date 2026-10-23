# MySQL setup

The app uses MySQL 8 (same setup as the other studio_admin project).

## 1. Database
Nothing to do by hand if your MySQL user may create databases: the app creates
`studio_admin` on first start. To create it yourself:
```sql
CREATE DATABASE studio_admin CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

## 2. Configure (environment variables)
| Variable | Default |
|---|---|
| `DB_URL` / `MYSQL_URL` (optional full JDBC URL, overrides host/port/name) | - |
| `DB_HOST` / `MYSQLHOST` | `db.host` in application.properties (`localhost`) |
| `DB_PORT` / `MYSQLPORT` | `db.port` (`3306`) |
| `DB_NAME` / `MYSQLDATABASE` | `db.name` (`studio_admin`) |
| `DB_USER` / `MYSQLUSER` | `db.username` (`infyrail`) |
| `DB_PASSWORD` / `MYSQLPASSWORD` | `db.password` |

Environment variables win; if one is not set, the matching `db.*` value in
`src/main/resources/application.properties` is used.

Windows PowerShell example:
```powershell
$env:DB_USER="root"; $env:DB_PASSWORD="your_password"
.\mvnw.cmd spring-boot:run
```

For a hosted MySQL that requires TLS, set `DB_URL`, e.g.
`jdbc:mysql://HOST:3306/DB?sslMode=REQUIRED&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`

## 3. Tables
Automatic: tables are created from `src/main/resources/db/schema.sql` on every
startup (`CREATE TABLE IF NOT EXISTS`), and older data is migrated in place.

## 4. Old data (optional, once, into an EMPTY database)
`db/data.sql` is a full backup (schema + data) generated from `db/legacy/studio.db`:
```bash
mysql -u root -p < db/data.sql
```
To regenerate it: `python3 db/sqlite_to_mysql.py`

## Fresh install (no old data)
Set `ADMIN_USERNAME` and `ADMIN_PASSWORD` for the first run; a BCrypt-hashed
admin is created when the `admin` table is empty.

## Notes
* `db/data.sql` and `db/legacy/` contain real customer photos/credentials and are git-ignored.
* Timestamps are stored in UTC.
