#!/usr/bin/env python3
"""
Convert the old SQLite database (db/legacy/studio.db) into a MySQL script.

    python3 db/sqlite_to_mysql.py                   # writes db/data.sql
    python3 db/sqlite_to_mysql.py in.db out.sql     # custom paths

Only the Python standard library is needed. The generated file contains
the MySQL schema (src/main/resources/db/schema.sql) followed by all the data,
with the original IDs preserved. Import it ONCE into an empty database:

    mysql -u root -p < db/data.sql
"""
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "db" / "legacy" / "studio.db"
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "db" / "data.sql"
SCHEMA = ROOT / "src" / "main" / "resources" / "db" / "schema.sql"
DB_NAME = "studio_admin"

# (sqlite table, mysql table, columns, has AUTO_INCREMENT)  - in foreign-key order
TABLES = [
    ("admin", "admin", ["UserName", "Password"], False),
    ("Studio", "studio", ["Studio_ID", "Studio_name", "Studio_mobile", "Studio_Email",
                          "Owner_name", "Password", "First_Login", "Studio_Logo"], False),
    ("customer", "customer", ["customer_id", "customer_name", "phone", "address", "studio_ID"], True),
    ("customer_images", "customer_images", ["image_id", "customer_id", "image_name", "image_data"], True),
    ("customer_selection_links", "customer_selection_links",
     ["link_id", "customer_id", "token", "created_at"], True),
    ("customer_selected_images", "customer_selected_images",
     ["selection_id", "token", "customer_id", "image_id"], True),
    ("customer_albums", "customer_albums",
     ["album_id", "customer_id", "album_name", "created_at", "token"], True),
    ("customer_album_images", "customer_album_images", ["album_image_id", "album_id", "image_id"], True),
    ("customer_album_links", "customer_album_links",
     ["link_id", "customer_id", "token", "created_at", "active"], True),
    ("subscription", "subscription",
     ["id", "name", "phone", "email", "plan", "amount", "razorpay_order_id", "razorpay_payment_id",
      "razorpay_signature", "start_date", "expiry_date", "status", "created_at"], True),
]


def literal(value):
    """Return a MySQL literal for a Python value read from SQLite."""
    if value is None:
        return "NULL"
    if isinstance(value, (bytes, bytearray, memoryview)):
        return "X'" + bytes(value).hex() + "'"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        return repr(value)
    text = str(value)
    if all(32 <= ord(c) < 127 for c in text):          # plain printable ASCII
        return "'" + text.replace("\\", "\\\\").replace("'", "''") + "'"
    # anything else (newlines, unicode, control chars): hex is always safe
    return "CONVERT(X'" + text.encode("utf-8").hex() + "' USING utf8mb4)"


def main():
    src = sqlite3.connect(SRC)
    seq = dict(src.execute("SELECT name, seq FROM sqlite_sequence").fetchall())
    out = []
    out.append("-- =====================================================================")
    out.append("-- studio_admin : MySQL schema + data (generated from studio.db)")
    out.append("-- Import ONCE into an empty database:  mysql -u root -p < studio_admin_mysql.sql")
    out.append("-- =====================================================================")
    out.append("SET NAMES utf8mb4;")
    out.append(f"CREATE DATABASE IF NOT EXISTS `{DB_NAME}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
    out.append(f"USE `{DB_NAME}`;")
    out.append("")
    out.append(SCHEMA.read_text(encoding="utf-8").strip())
    out.append("")
    out.append("-- ------------------------------ DATA ------------------------------")

    summary = []
    for s_table, m_table, cols, auto in TABLES:
        rows = src.execute(f'SELECT {", ".join(chr(34) + c + chr(34) for c in cols)} FROM "{s_table}"').fetchall()
        summary.append((m_table, len(rows)))
        if not rows:
            continue
        out.append(f"-- {m_table}: {len(rows)} row(s)")
        collist = ", ".join(f"`{c}`" for c in cols)
        for row in rows:                       # one row per statement: keeps big photos well below max_allowed_packet
            out.append(f"INSERT INTO `{m_table}` ({collist}) VALUES (" +
                       ", ".join(literal(v) for v in row) + ");")
        if auto and s_table in seq:
            out.append(f"ALTER TABLE `{m_table}` AUTO_INCREMENT = {int(seq[s_table]) + 1};")
        out.append("")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"written {OUT}  ({OUT.stat().st_size / 1e6:.1f} MB)")
    for name, n in summary:
        print(f"  {name:28s} {n} rows")


if __name__ == "__main__":
    main()
