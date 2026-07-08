@echo off
echo =======================================
echo    TOKIT STO Platform Bootstrapper
echo =======================================

echo 1. Starting infrastructure containers (PostgreSQL, Redis, RabbitMQ)...
docker-compose up -d

echo 2. Starting Backend (Spring Boot)...
start cmd /k "cd backend && gradlew.bat bootRun"

echo 3. Starting Frontend (Next.js)...
start cmd /k "cd frontend && npm run dev"

echo =======================================
echo All microservices launched successfully!
echo =======================================
pause
