# REG·LOG — Full Stack Ecommerce (React + Spring Boot + MySQL)

A complete, runnable ecommerce application built as an academic project. It contains
**two application files** — one React frontend and one Spring Boot backend — plus the
scaffolding needed to compile and run them.

```
Default Project/
├── reg-log frontend app/            # 1. REACT APPLICATION  (React + Vite)
│   └── src/App.jsx                  #    ★ the frontend file: signup.jsx + login.jsx + home page
├── reg-log backend app/             # 2. SPRING BOOT APPLICATION  (Spring Boot + MySQL)
│   └── src/main/java/com/reglog/RegLogBackendApp.java   # ★ the backend file
├── scripts/                         # one command start / stop for the local stack
├── docker-compose.yml               # optional: the same stack in docker
└── README.md
```

---

## 1. Architecture

```
 ┌──────────────────────────── React frontend (reg-log frontend app) ───────────────────────────┐
 │  /signup        /login          /home                                                      │
 │  signup.jsx     login.jsx       home page                                                  │
 │      │               │               │                                                     │
 │      └──── axios (JSON payload) ────┘                                                     │
 └──────────────────────────────────────┬──────────────────────────────────────────────────────┘
                                        │  HTTP  http://localhost:8080/api/...
                                        ▼
 ┌──────────────────────── Spring Boot backend (reg-log backend app) ─────────────────────────┐
 │  CONTROLLER   AuthController · ProductController · HealthController                       │
 │      │  reads the JSON payload, creates the JWT on a successful login                     │
 │      ▼                                                                                     │
 │  SERVICE      UserService (validation + password verification) · ProductService           │
 │      │  business rules, BCrypt verification, role assignment                              │
 │      ▼                                                                                     │
 │  REPOSITORY   UserRepository · ProductRepository   (Spring Data JPA)                      │
 │      │  the only layer that touches the database                                          │
 │      ▼                                                                                     │
 │  MySQL  →  reglog_ecommerce  →  tables: users, products                                   │
 └────────────────────────────────────────────────────────────────────────────────────────────┘
```

Every response coming back to the frontend uses the same envelope:

```json
{ "code": 200, "message": "Login successful", "data": { }, "timestamp": "2026-10-21T05:00:00Z" }
```

### The two page resources

| Page | Route | File | Backend call |
|---|---|---|---|
| Sign up | `/signup` | `App.jsx` → `SignupPage` | `POST /api/auth/signup` |
| Login | `/login` | `App.jsx` → `LoginPage` | `POST /api/auth/login` (returns the JWT access token) |
| Home (ecommerce) | `/home` | `App.jsx` → `HomePage` | `GET /api/auth/me`, `GET /api/products` |
| Admin tools (only for the ADMIN role) | `/home` | `App.jsx` → `HomePage` | `POST / PUT / DELETE /api/products` |

### What each page does

* **Sign up** — full name, gmail, phone, user id, password + confirm, gender, role, address, city,
  state and pincode. The whole thing is posted as one JSON payload; the backend replies with
  `{code, message, data}` and the page shows the message.
* **Login** — username + password are posted as JSON. On success the JWT access token is stored
  (`localStorage` when "keep me signed in" is ticked, otherwise `sessionStorage`) and sent back
  with every following request. Wrong credentials show the backend message.
* **Home** — a profile card (name, gmail, phone, user id, **role badge**, city, member since),
  a searchable and filterable product catalogue (8 seeded products with price, rating, stock and
  a working "add to cart" counter), and — for an **ADMIN** account — an extra *Product manager*
  panel that creates, updates and deletes catalogue entries straight from the UI.

---

## 2. Run it locally (Windows)

### One command

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-all.ps1 -OpenBrowser
```

This starts MySQL → creates the schema → starts the Spring Boot backend on `:8080` →
starts the React dev server on `:5173` → opens the browser.

Stop everything again with:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-all.ps1
```

### Step by step (if you prefer)

```powershell
# 1. MySQL (project local instance on port 3307, so no root password of another server is needed)
powershell -ExecutionPolicy Bypass -File .\scripts\start-mysql.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\setup-database.ps1

# 2. Backend  ->  http://localhost:8080/api/health
cd "reg-log backend app"
$env:DB_PORT="3307"; $env:DB_USER="reglog"; $env:DB_PASSWORD="reglog@123"
mvn -DskipTests package
java -jar target\reg-log-backend-app.jar

# 3. Frontend  ->  http://localhost:5173
cd "..\reg-log frontend app"
npm install
npm run dev
```

### Demo credentials

| Role | Username | Password |
|---|---|---|
| ADMIN | `admin` | `admin@123` |
| USER | any account you create on `/signup` | the password you chose |

The first account created after a fresh database start is promoted to **ADMIN** automatically;
every later signup is a **USER** (you can also pick the role explicitly in the signup form).

---

## 3. Run it locally (Linux / macOS)

```bash
# database
sudo mysqld --initialize-insecure --datadir=./.local/mysql/data
sudo mysqld --datadir=./.local/mysql/data --port=3307 --bind-address=127.0.0.1 &
mysql -u root -P 3307 -e "CREATE DATABASE reglog_ecommerce; CREATE USER 'reglog'@'%' IDENTIFIED BY 'reglog@123'; GRANT ALL ON reglog_ecommerce.* TO 'reglog'@'%';"

# backend
cd "reg-log backend app"
DB_PORT=3307 DB_USER=reglog DB_PASSWORD='reglog@123' mvn spring-boot:run

# frontend
cd "../reg-log frontend app"
npm install && npm run dev
```

### Or with docker (if docker is installed)

```bash
docker compose up --build     # frontend on http://localhost:8081
```

---

## 4. API reference

| Method | Endpoint | Auth | Body / Query | Description |
|---|---|---|---|---|
| POST | `/api/auth/signup` | – | `SignupRequest` | Create an account, returns the saved user |
| POST | `/api/auth/login` | – | `{username,password}` | Verify + return the JWT access token |
| GET | `/api/auth/me` | JWT | – | Profile of the logged in user |
| GET | `/api/auth/users` | ADMIN | – | List all users |
| PUT | `/api/auth/users/{id}` | ADMIN | `SignupRequest` | Update a user |
| DELETE | `/api/auth/users/{id}` | ADMIN | – | Delete a user |
| GET | `/api/products` | – | `?category=&search=` | List / filter / search products |
| GET | `/api/products/categories` | – | – | Distinct categories |
| GET | `/api/products/{id}` | – | – | One product |
| POST | `/api/products` | ADMIN | `ProductRequest` | Create a product |
| PUT | `/api/products/{id}` | ADMIN | `ProductRequest` | Update a product |
| DELETE | `/api/products/{id}` | ADMIN | – | Delete a product |
| GET | `/api/health` | – | – | Health probe |

### Signup payload (JSON)

```json
{
  "fullName": "Priya Nair",
  "gmail": "priya.nair@gmail.com",
  "phone": "9812345678",
  "userId": "priya.nair",
  "password": "priya@2026",
  "gender": "Female",
  "address": "44 Linking Road",
  "city": "Mumbai",
  "state": "Maharashtra",
  "pincode": "400050",
  "role": "USER"
}
```

### Login payload and response

```json
{ "username": "admin", "password": "admin@123" }
```

```json
{
  "code": 200,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400000,
    "username": "admin",
    "role": "ADMIN",
    "user": { "id": 1, "fullName": "Store Admin", "gmail": "admin@reglog.com", "role": "ADMIN" }
  }
}
```

The frontend stores the token (`localStorage` when "keep me signed in" is ticked, otherwise
`sessionStorage`) and sends it on every protected request:

```
Authorization: Bearer <jwt>
```

The JWT is created by the **controller** from the **role** and the **username**, signed with
HMAC-SHA512 and verified by `JwtAuthFilter` on every request.

---

## 5. Database

MySQL database `reglog_ecommerce`, two tables (created automatically by Hibernate):

```sql
users
  id           BIGINT PK AUTO_INCREMENT
  full_name    VARCHAR(120)
  gmail        VARCHAR(160) UNIQUE
  phone        VARCHAR(25)
  user_id      VARCHAR(60)  UNIQUE   <- the username used on the login page
  password_hash VARCHAR(120)          <- BCrypt hash, never plain text
  gender, address, city, state, pincode
  role         VARCHAR(20)            <- USER | ADMIN
  created_at   TIMESTAMP

products
  id, name, description, price DECIMAL(12,2), category, image_url, stock, rating, created_at
```

8 demo products are seeded on the first start.

---

## 6. Security notes

* Passwords are stored as **BCrypt** hashes (`spring-security-crypto`).
* The JWT is signed with a secret from `app.jwt.secret` — change it via the `JWT_SECRET`
  environment variable for anything that is not a local demo.
* `GET /api/products` is public, everything else needs a valid token.
* Product management and user management are restricted to the **ADMIN** role (HTTP 403 otherwise).
* A missing or expired token returns HTTP 401 and the frontend automatically logs the user out.

---

## 7. Project layout

```
reg-log backend app/
├── pom.xml                                  spring boot 3.5, jjwt 0.12, mysql connector
├── Dockerfile                               optional container build
└── src/main/
    ├── java/com/reglog/RegLogBackendApp.java ★ controller + service + repository + entities
    │                                            + JWT util + security config + seeder + error handler
    └── resources/application.properties     datasource, jpa and jwt settings (env overridable)

reg-log frontend app/
├── package.json                             react 18, react-router 6, axios, vite 5
├── vite.config.js                           dev server + /api proxy to :8080
├── index.html
├── Dockerfile + nginx.conf                  optional container build
└── src/
    ├── App.jsx                              ★ api client, auth context, signup page,
    │                                          login page, home page, router, guards
    ├── index.css                            minimalist design system
    └── main.jsx                             react entry point

scripts/                                     start-all / stop-all / start-mysql / setup-database
                                            start-backend / start-frontend
```

---

## 8. Troubleshooting

| Problem | Fix |
|---|---|
| `Communications link failure` | MySQL is not running → `scripts\start-mysql.ps1` |
| `Access denied for user 'reglog'` | run `scripts\setup-database.ps1` |
| Frontend shows "Cannot reach the backend" | the Spring Boot app is not on `:8080` → `scripts\start-backend.ps1` |
| Port 3306 already in use | the project uses **3307** for its own MySQL instance, so it never collides with the MySQL service already installed on the machine |
| `npm` cannot be loaded (PowerShell policy) | use `npm.cmd` instead of `npm` |
