
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

```bash
cd ~/Hostel-Complaints-Ticketing-System/backend
chmod +x mvnw
export DB_PASSWORD='YOUR_SUPABASE_PASSWORD'
./mvnw spring-boot:run
```

## Backend - Docker

### 1. Build the Docker image

```bash
cd backend
docker build -t hostel-complaints-backend .
docker run -d --name hostel-complaints-backend -p 8080:8080 -e DB_PASSWORD='YOUR_SUPABASE_PASSWORD' hostel-complaints-backend
docker ps
curl -i -u admin:YOUR_ADMIN_PASSWORD http://localhost:8080/health
docker stop hostel-complaints-backend

# To start existing container
docker start hostel-complaints-backend  
```