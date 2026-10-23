# Run Studio Admin on Windows

Follow these steps the first time you set up the project. You need an internet connection
for downloads.

## 1. Keep the project together

If you received a ZIP file, right-click it and choose **Extract All**. Open the extracted
folder. It must contain `mvnw.cmd` and `pom.xml`. Do not run the project from inside the ZIP.

## 2. Install Java

Install **Java 21 JDK** for Windows from <https://adoptium.net/temurin/releases/?version=21>.
Use the `.msi` installer and its recommended options. If asked, enable **Add to PATH**.
You do not need to install Maven.

## 3. Install MySQL

Install **MySQL Server 8** from <https://dev.mysql.com/downloads/installer/>.
During setup, create a MySQL `root` password and keep it private. Leave the MySQL service
running.

## 4. Connect the app to MySQL

1. In the project folder, open `src\main\resources\application.properties` with Notepad.
2. Find the lines starting with `db.username=` and `db.password=`. (Or set the environment
   variables `DB_USER` and `DB_PASSWORD`; they win over this file. See `db\README.md`.)
3. Set them to your MySQL account. For a new local setup using the root account, use:

   ```properties
   db.username=root
   db.password=YOUR_MYSQL_ROOT_PASSWORD
   ```

   Replace `YOUR_MYSQL_ROOT_PASSWORD` with the password you created in step 3. Do not add
   quote marks. Save the file.

Keep the password private. If you already have a MySQL username and password from the person
who gave you this project, use those instead.

## Do I need to upload the schema file?

**No.** You do not need to upload `schema.sql` or run it yourself. When the app starts, it
reads `src\main\resources\db\schema.sql` and creates the database tables automatically. It can
also create the `studio_admin` database if your MySQL account is allowed to create databases.

The file `db\data.sql` is different: it is an optional backup that may
contain data from an earlier installation. Do not import it for a fresh, empty setup. Only
import it if you were told to restore those old records.

## 5. Start the app

1. Open the project folder in File Explorer.
2. Click the address bar, type `powershell`, and press Enter.
3. In the PowerShell window, type this command and press Enter:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

The first start can take a few minutes while files download. Wait until the window says
`MySQL database ready` and the app has started. Keep the window open; closing it stops the app.

## 6. Open the website

Open your browser on the same computer and visit <http://localhost:8080>.

Register or sign in as a studio owner. Once signed in, add customers, upload studio photos,
and create a private gallery link to send to each customer. Customers can use their link to
view photos, upload their own photos, and create albums.

Registration may use the payment service set up for this project. A new empty database may
not have an administrator account; ask the person who gave you the project if you need one.

## Stop or start it another day

- To stop: click the PowerShell window and press **Ctrl+C**.
- To start again: repeat step 5. You do not need to install Java or MySQL again.
- Keep MySQL installed and running. It stores your customers, photos, and albums.

## If it does not work

- **Java not found:** restart Windows. If it still fails, install Java 21 again and make sure
  **Add to PATH** is selected.
- **MySQL connection error:** make sure MySQL is running and check the username and password
  in `application.properties`. Stop the app with **Ctrl+C** and start it again after fixing.
- **Command not found:** make sure PowerShell was opened from the folder containing `mvnw.cmd`.
- **Still stuck:** copy the error message and ask the person who supplied the project or a
  computer-support person. Never send them your password.

This setup runs the app only on your computer. Do not share customer gallery links publicly.
