# Run the app locally: `make backend` in one terminal, `make frontend` in another.

SHELL := /bin/bash

# Vite needs Node 20.19+ or 22.12+; the system Node can be older, so use nvm's when it has one
USE_NODE = if [ -s "$$HOME/.nvm/nvm.sh" ]; then . "$$HOME/.nvm/nvm.sh"; nvm use --silent 24 2>/dev/null || nvm use --silent 22 2>/dev/null; fi

.DEFAULT_GOAL := help
.PHONY: help backend frontend

help:
	@echo "Run each of these in its own terminal:"
	@echo "  make backend    Spring Boot API on http://localhost:8080"
	@echo "  make frontend   Vite dev server on http://localhost:5173"

backend:
	@test -f backend/.env || { echo "backend/.env is missing. Run: cp backend/.env.example backend/.env, then fill it in."; exit 1; }
	cd backend && ./mvnw spring-boot:run

frontend: frontend/node_modules/.package-lock.json
	@$(USE_NODE); cd frontend && npm run dev

# Reinstalls only when the lockfile changes, e.g. after pulling a new dependency
frontend/node_modules/.package-lock.json: frontend/package-lock.json
	@$(USE_NODE); cd frontend && npm install
	@touch $@
