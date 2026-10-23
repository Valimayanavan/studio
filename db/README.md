# MySQL setup

The app now uses MySQL 8 (was SQLite `studio.db`).

## 1. Create the database
```sql
CREATE DATABASE studio_admin CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

## 2. Configure (environment variables)
| Variable | Default |
|---|---|
| `DB_URL` (optional full JDBC URL, overrides the next three) | – |
| `DB_HOST` | `localhost` |
| `DB_PORT` | `3306` |
| `DB_NAME` | `studio_admin` |
| `DB_USER` | `root` |
| `DB_PASSWORD` | *(empty)* |

For a hosted MySQL that requires TLS, set `DB_URL`, e.g.
`jdbc:mysql://HOST:3306/DB?sslMode=REQUIRED&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`

## 3. Create tables
Automatic: tables are created from `src/main/resources/db/schema.sql` on every startup
(`CREATE TABLE IF NOT EXISTS`).

## 4. Import your existing SQLite data (once, into the empty DB)
```bash
# start the app once so the tables exist (or: mysql ... < src/main/resources/db/schema.sql)
python3 db/sqlite_to_mysql.py db/legacy/studio.db db/data.sql   # already generated for you
mysql -u root -p studio_admin < db/data.sql
```
Do this **before** setting `ADMIN_USERNAME/ADMIN_PASSWORD` (those only seed an admin
when the `admin` table is empty).

## Fresh install (no old data)
Set `ADMIN_USERNAME` and `ADMIN_PASSWORD` for the first run; a BCrypt-hashed admin is created.

## Notes
* `db/data.sql` and `db/legacy/` contain real customer photos/credentials and are git-ignored.
* `studio_ID` columns use a binary collation so IDs stay case-sensitive like before.
* Timestamps are stored in UTC (same as SQLite's `CURRENT_TIMESTAMP`).
