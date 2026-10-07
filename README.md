
# Hostel Complaints Ticketing System


## Frontend 
### 1. Install Node.js using NVM

Since you're using WSL, install Node through NVM inside WSL.

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
source ~/.bashrc
nvm install 24
nvm use 24
node --version
npm --version
```
To run frontend :

```bash
cd ~/Hostel-Complaints-Ticketing-System/frontend
npm install
npm run dev
```

## Backend - Run Without Docker

No additional dependencies are required apart from Java 17/21.

### 1. Run Spring Boot Backend

Create `backend/.env` from the example and set your Supabase database password and comma-separated administrator smail addresses:

```bash
cd ~/Hostel-Complaints-Ticketing-System/backend
cp .env.example .env
# Edit .env, replace the database password, and set ADMIN_EMAILS to the accounts that should be administrators.
chmod +x mvnw
./mvnw spring-boot:run
```

Spring Boot automatically loads `backend/.env` when started from the `backend` directory. The local `.env` file is ignored by Git; do not commit database credentials. Student accounts must use a Google-verified `@smail.iitm.ac.in` address; administrator accounts must use a Google-verified email explicitly listed in `ADMIN_EMAILS`.

### 2. Verify login and student dashboard locally

Start the backend as above, then confirm it responds:

```bash
curl http://localhost:8080/health
```

In a second terminal, start the frontend:

```bash
cd ~/Hostel-Complaints-Ticketing-System/frontend
nvm use 24
npm run dev
```

Open `http://localhost:5173` and sign in with Google. If Google rejects the local app origin, add `http://localhost:5173` as an authorized JavaScript origin for the configured Google OAuth client.

Use your `@smail.iitm.ac.in` account to check the student dashboard and complaint list. Accounts listed in `ADMIN_EMAILS` are assigned the admin role; other verified `@smail.iitm.ac.in` accounts are students. Admin emails may use any domain. Sign out and in again after changing the list so the backend refreshes the saved account role.

## Backend - Docker

### 1. Build the Docker image

```bash
cd backend
docker build -t hostel-complaints-backend .
docker run -d --name hostel-complaints-backend -p 8080:8080 -e DB_PASSWORD='YOUR_SUPABASE_PASSWORD' -e ADMIN_EMAILS='<comma-separated-admin-emails>' hostel-complaints-backend
docker ps
curl -i -u admin:YOUR_ADMIN_PASSWORD http://localhost:8080/health
docker stop hostel-complaints-backend

# To start existing container
docker start hostel-complaints-backend  
```