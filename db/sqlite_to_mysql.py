#!/usr/bin/env python3
"""
Convert the legacy SQLite database (studio.db) into a MySQL-importable
SQL file containing only data (INSERT statements).

Usage:
    python3 db/sqlite_to_mysql.py [db/legacy/studio.db] [db/data.sql]

Then load it (the schema must exist first - start the app once, or run
src/main/resources/db/schema.sql):

    mysql -u root -p studio_admin < db/data.sql

Run it ONCE against an empty database. Needs only the Python stdlib.
"""
import sqlite3
import sys

SRC = sys.argv[1] if len(sys.argv) > 1 else "db/legacy/studio.db"
DST = sys.argv[2] if len(sys.argv) > 2 else "db/data.sql"

# Parent tables first so foreign keys are satisfied.
TABLES = [
    "admin",
    "studio",
    "customer",
    "customer_images",
    "customer_album_links",
    "customer_albums",
    "customer_album_images",
    "customer_selection_links",
    "customer_selected_images",
    "customer_selection",
    "album_image",
    "subscription",
]


def lit(v):
    if v is None:
        return "NULL"
    if isinstance(v, (bytes, bytearray)):
        return "X'%s'" % bytes(v).hex() if v else "''"
    if isinstance(v, (int, float)):
        return repr(v)
    s = str(v).replace("\\", "\\\\").replace("'", "''")
    return "'%s'" % s


def main():
    con = sqlite3.connect(SRC)
    existing = {r[0].lower(): r[0] for r in con.execute(
        "SELECT name FROM sqlite_master WHERE type='table'")}
    seqs = {k.lower(): v for k, v in con.execute("SELECT name, seq FROM sqlite_sequence")}

    with open(DST, "w", encoding="utf-8") as out:
        out.write("SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n\n")
        total = {}
        for t in TABLES:
            src_name = existing.get(t)
            if src_name is None:
                continue
            cur = con.execute('SELECT * FROM "%s"' % src_name)
            cols = [d[0] for d in cur.description]
            col_sql = ", ".join("`%s`" % c for c in cols)
            n = 0
            for row in cur:
                out.write("INSERT INTO `%s` (%s) VALUES (%s);\n"
                          % (t, col_sql, ", ".join(lit(v) for v in row)))
                n += 1
            total[t] = n
            if t in seqs:
                # keep AUTO_INCREMENT counters where SQLite left them
                out.write("ALTER TABLE `%s` AUTO_INCREMENT = %d;\n" % (t, seqs[t] + 1))
            out.write("\n")
        out.write("SET FOREIGN_KEY_CHECKS=1;\n")

    for t, n in total.items():
        print("%-26s %d rows" % (t, n))
    print("written:", DST)


if __name__ == "__main__":
    main()
