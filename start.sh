#!/bin/bash
echo "=== TOKIT STO Platform Bootstrapper ==="

echo "1. Starting infrastructure containers (PostgreSQL, Redis, RabbitMQ)..."
docker-compose up -d

echo "2. Starting Backend (Spring Boot) in a new terminal window..."
osascript -e 'tell app "Terminal" to do script "cd '"$(pwd)"'/backend && ./gradlew bootRun"'

echo "3. Starting Frontend (Next.js) in a new terminal window..."
osascript -e 'tell app "Terminal" to do script "cd '"$(pwd)"'/frontend && npm run dev"'

echo "======================================="
echo "All microservices launched successfully!"
echo "======================================="
