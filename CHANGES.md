# What changed

## Database: SQLite -> MySQL
- `pom.xml`: `sqlite-jdbc` replaced by `mysql-connector-j` (version managed by Spring Boot).
- `application.properties`: SQLite/JPA settings removed (they were unused), MySQL settings added.
- `models/connectionDb.java`: now opens MySQL connections (same `getConnection()` method, so no controller changed shape).
- New `DatabaseInitializer.java` + `db/schema.sql`: creates the MySQL tables on startup.
  Replaces the SQLite-only `CREATE TABLE ... AUTOINCREMENT` code that ran on *every request* and
  would have failed on MySQL.
- `SubscriptionController`: had its own private SQLite connection; now uses the shared MySQL one.
- Table name `Studio` -> `studio` everywhere (MySQL on Linux/Docker/Render is case-sensitive, SQLite is not).
- Data migrated with original IDs (`db/data.sql`, verified cell-by-cell against `studio.db`).
- Real foreign keys with `ON DELETE CASCADE` (SQLite had them declared but never enforced).
- Front end (pages, styling, JavaScript, URLs) is unchanged except for the bug fixes below.

## Bugs fixed
1. **`/subscriptions` always crashed** - the admin dashboard links to it but `subscriptions.html` did not exist. Page created (with counts, revenue, Active/Expired/Pending badges).
2. **Registration errors were invisible** - e.g. "email already registered" or a failed payment check were stored in the session but the registration page never showed them, so a user who had just paid saw a blank form. Now displayed.
3. **Deleting a studio left its customers and photos behind**, and anyone registering a studio with the same ID later would have inherited them. Deleting a studio now removes its customers, photos, albums and links too (one transaction).
4. **Any logged-in studio could download any other studio's photos** via `/download_album_image/{id}` - now restricted to the owning studio. File name in the download header is also sanitised.
5. **Customer selection links were wrong behind a proxy** (Render etc.: `http://...:10000/...`). `server.forward-headers-strategy=framework` added.
6. **Studio IDs could contain spaces** when registered through the public form but not through the admin form; both now use `_`.
7. **Stale messages**: the login page kept showing an old error / "registered successfully" banner for the whole session. Messages now show once.
8. **Admin password was compared in plain text** - stored hash now used; the old plain-text value is upgraded to BCrypt on the first successful login (timing-safe compare until then).
9. **Admin "Add Studio"** accepted non-numeric mobile numbers and crashed with a database error - now validated.
10. **Opening the bare site address (`/`) gave a 404** - now goes to the studio login page.
11. The login page no longer prints raw database error text to visitors.

## Customer gallery and album storage
- Customer links open the gallery portal, where studio and customer uploads share the
  `customer_images` gallery and albums use `customer_albums` plus `customer_album_images`.
- `customer_images.uploaded_by` records whether an image was uploaded by the studio or customer.
- Startup schema migration moves older photo-folder data into the canonical album/gallery tables
  while retaining the legacy tables and existing selection records.

## Things I found but did NOT change (your decision)
- **Subscription expiry is never enforced.** A studio whose plan ended can still log in and use everything.
  (Right now studios `c_8870223705` and `d_1234567890` have expired plans.)
- **`/customer_image/{id}` is public**: anyone who guesses a number can view a customer's photo, because
  the customer selection page uses it without a login. Fixing it properly needs the token added to the
  image URLs in three pages.
- 4 customers in the old data (IDs 1, 2, 3, 5) belong to studios that no longer exist (`NULL`, `std2`,
  `antona`, `rajin_studio_9386789042`) - nobody can open them. They were migrated as they were.
- Razorpay secret key is stored in `application.properties`.
- Two empty unused SQLite tables (`customer_selection`, `album_image`) were not carried over.
  `customer_album_links` (1 row, not used by any code) was.

## Aligned with the second studio_admin project (build + database process)
- `pom.xml` is now identical to the other project (same Spring Boot 4.1.0, Java 21, MySQL driver, Razorpay, BCrypt crypto).
- `connectionDb.java` reads the same environment variables (`DB_URL/MYSQL_URL`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, plus the `MYSQL...` names) and builds the same JDBC URL (UTC time zone). `application.properties` `db.*` values are only fallbacks.
- `schema.sql` moved to `src/main/resources/db/schema.sql`; `DatabaseInitializer` also seeds a first admin from `ADMIN_USERNAME` / `ADMIN_PASSWORD` when the admin table is empty.
- Folder layout: `db/README.md`, `db/sqlite_to_mysql.py`, `db/data.sql`, `db/legacy/`, plus `.gitignore`, `README.md` like the other project.
- No controller, template, CSS or table definition was changed.
