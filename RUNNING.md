# Running the whole CivicConnect system locally

```
Browser ──> React 19 app (Vite dev server, http://localhost:8080)
               │  /api/*  (proxied)
               ▼
            Tomcat 10.1 (http://localhost:8081/civicconnect)
               ├─ /api/*                → Java REST API  (Person 2: auth, lifecycle, notifications)
               └─ /dashboard, /reports  → JSP reports    (Person 3: management reporting)
               │  JDBC (pgjdbc)
               ▼
            PostgreSQL 16 (database "civicconnect", schema "civic")
```

## 1. Database (once)

From the `database/` folder (pgAdmin's Query Tool works too; run the files in this order):

```bash
psql -U postgres -v app_password='APP_PW' -v owner_password='OWNER_PW' -f V0__create_database_and_roles.sql
psql -h localhost -U civic_owner -d civicconnect -f V1__schema.sql
psql -h localhost -U civic_owner -d civicconnect -f V2__reporting_views.sql
psql -h localhost -U civic_owner -d civicconnect -f V3__reference_data.sql
psql -h localhost -U civic_owner -d civicconnect -f V4__sample_data_dev_only.sql     # demo data
psql -h localhost -U civic_owner -d civicconnect -f V5__accounts_notifications_feedback.sql
psql -h localhost -U civic_owner -d civicconnect -f V6__demo_logins_dev_only.sql     # demo passwords
```

Choose your own passwords. They are never stored in the repository.

## 2. Backend on Tomcat (port 8081)

1. Install JDK 21 and Tomcat 10.1. Copy `postgresql-42.7.x.jar` into Tomcat's `lib/` folder.
2. Set the environment variables from `.env.example`: `CIVIC_DB_URL`, `CIVIC_DB_USER=civic_app`, `CIVIC_DB_PASSWORD`.
3. Add this line to Tomcat's `conf/catalina.properties`:
   `org.apache.tomcat.util.digester.PROPERTY_SOURCE=org.apache.tomcat.util.digester.EnvironmentPropertySource`
4. **Use port 8081**, because the React dev server uses 8080:
   - IntelliJ: Run → Edit Configurations → Tomcat Server → *HTTP port* `8081`; Deployment → `civicconnect:war exploded`, application context `/civicconnect`.
   - Plain Tomcat: in `conf/server.xml`, change `<Connector port="8080"` to `8081`, then run `mvn package` and copy `target/civicconnect.war` into `webapps/`.
5. Check it: <http://localhost:8081/civicconnect/api/health> should show `{"status":"UP","database":"UP"}`.

## 3. Frontend

```bash
cd CivicConnect-Frontend
bun install        # or: npm install
bun run dev        # or: npm run dev   ->  http://localhost:8080
```

If Tomcat is not on `localhost:8081`, start the dev server with `CIVIC_API_TARGET=http://host:port`.

## 4. Demo accounts

All five use the password `CivicConnect-Demo-2026` (V6, development only):

| Sign in as | Role | Lands on |
|---|---|---|
| `resident1@mail.example` | Resident | My dashboard |
| `t.dlamini@civicconnect.example` | Coordinator | Work queue (assign / reject) |
| `k.mahlangu@civicconnect.example` | Staff (Water and Sanitation) | Work queue (start / resolve) |
| `n.mokoena@civicconnect.example` | Manager | Work queue + **Management dashboard** button |
| `admin@civicconnect.example` | Admin | Work queue |

## 5. Tests

```bash
mvn test     # unit tests: lifecycle (all 42 status pairs), auth, request service, outbox, reporting
```

Database tests also run when `CIVIC_TEST_DB_URL` points at a **throw-away** database (see README).
GitHub Actions runs both on every pull request (`.github/workflows/ci.yml`).
