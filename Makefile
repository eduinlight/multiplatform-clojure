.DEFAULT_GOAL := help

-include .env
export

SHELL := /bin/bash
COMPOSE := docker compose
API_PORT ?= 8080
WEB_PORT ?= 8280
MONGO_EXPRESS_PORT ?= 8082
MONGO_ROOT_USERNAME ?= app
MONGO_ROOT_PASSWORD ?= app
MONGODB_DATABASE ?= app
API_BASE_URL ?= http://localhost:$(API_PORT)
MONGOSH := $(COMPOSE) exec -T mongo mongosh --quiet -u $(MONGO_ROOT_USERNAME) -p $(MONGO_ROOT_PASSWORD) --authenticationDatabase admin

.PHONY: help env install uninstall reinstall up down restart logs ps \
	api-dev api-repl api-test api-build api-image \
	web-dev web-repl web-build \
	mobile-dev mobile-repl mobile-prebuild mobile-android mobile-ios mobile-build \
	desktop-dev desktop-build desktop-bundle \
	mongo-shell mongo-drop seed \
	lint fmt fmt-check test outdated clean rename

help:
	@echo "multiplatform-clojure"
	@echo ""
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

env:
	@test -f .env || (cp .env.dist .env && echo "created .env from .env.dist")

install: env up seed ## bootstrap the whole dev environment
	@echo ""
	@echo "ready:"
	@echo "    api            http://localhost:$(API_PORT)/api/v1/health"
	@echo "    web            http://localhost:$(WEB_PORT)"
	@echo "    mongo-express  http://localhost:$(MONGO_EXPRESS_PORT)"
	@echo ""
	@echo "next:  make desktop-dev   |   make mobile-ios   |   make mobile-android"

uninstall: ## stop everything and delete volumes
	$(COMPOSE) down -v --remove-orphans

reinstall: uninstall install ## rebuild from scratch

up: env ## start all containers
	$(COMPOSE) up -d --build --wait mongo mongo-express api web mobile

down: ## stop all containers
	$(COMPOSE) down

restart: down up ## restart all containers

logs: ## tail container logs
	$(COMPOSE) logs -f --tail=100

ps: ## show container status
	$(COMPOSE) ps

api-dev: env ## run the api container in the foreground
	$(COMPOSE) up --build api

api-repl: ## connect a repl to the running api container
	@echo "nrepl listening on localhost:$${API_NREPL_PORT:-7888}"
	clojure -Sdeps '{:deps {nrepl/nrepl {:mvn/version "1.3.0"}}}' \
		-M -m nrepl.cmdline --connect --host localhost --port $${API_NREPL_PORT:-7888}

api-test: ## run api tests against a throwaway database
	$(COMPOSE) up -d --wait mongo
	cd apps/api && MONGODB_URI="mongodb://$(MONGO_ROOT_USERNAME):$(MONGO_ROOT_PASSWORD)@localhost:$${MONGO_PORT:-27017}/?authSource=admin" \
		MONGODB_DATABASE="$(MONGODB_DATABASE)_test" clojure -M:test

api-build: ## build the api uberjar classpath locally
	cd apps/api && clojure -M -e "(compile 'app.api.main)"

api-image: ## build the production api image
	docker build -f apps/api/Dockerfile -t app-api:latest .

web-dev: env ## run the shadow-cljs watcher for web
	$(COMPOSE) up --build web

web-repl: ## connect a cljs repl to the web build
	$(COMPOSE) exec web npx shadow-cljs cljs-repl app

web-build: ## produce an optimized web bundle
	$(COMPOSE) run --rm -e API_BASE_URL=$(API_BASE_URL) web npx shadow-cljs release app

mobile-dev: env ## run the shadow-cljs watcher for mobile
	$(COMPOSE) up --build mobile

mobile-repl: ## connect a cljs repl to the mobile build
	$(COMPOSE) exec mobile npx shadow-cljs cljs-repl app

mobile-prebuild: ## generate native ios/android projects on the host
	cd apps/mobile && npm install && npx expo prebuild --clean

mobile-android: ## build and run on android (needs host sdk)
	cd apps/mobile && npx expo run:android

mobile-ios: ## build and run on ios (needs host xcode, macos only)
	cd apps/mobile && npx expo run:ios

mobile-build: ## produce an optimized mobile bundle
	$(COMPOSE) run --rm mobile npx shadow-cljs release app

desktop-dev: ## open the tauri window against the web dev server
	cd apps/desktop && npm install && npx tauri dev

desktop-build: ## build a release desktop binary for this host
	$(MAKE) web-build
	cd apps/desktop && npm install && npx tauri build --no-bundle

desktop-bundle: ## build installers for this host
	$(MAKE) web-build
	cd apps/desktop && npm install && npx tauri build

mongo-shell: ## open a mongo shell
	$(COMPOSE) exec mongo mongosh -u $(MONGO_ROOT_USERNAME) -p $(MONGO_ROOT_PASSWORD) --authenticationDatabase admin $(MONGODB_DATABASE)

mongo-drop: ## drop the application database
	@$(MONGOSH) --eval 'db.getSiblingDB("$(MONGODB_DATABASE)").dropDatabase();'
	@echo "dropped $(MONGODB_DATABASE)"

seed: ## create indexes and a demo account
	@$(COMPOSE) up -d --wait mongo api >/dev/null
	@./scripts/seed.sh

lint: ## run clj-kondo across the monorepo
	clojure -M:kondo --lint apps packages

fmt: ## format all clojure sources
	clojure -M:cljfmt fix apps packages deps.edn

fmt-check: ## verify formatting
	clojure -M:cljfmt check apps packages deps.edn

test: api-test ## run the full test suite

outdated: ## report outdated dependencies
	clojure -M:outdated --directory=apps/api --directory=apps/web --directory=apps/mobile --directory=packages/shared --directory=packages/ui --directory=packages/client

clean: ## remove build artifacts
	rm -rf apps/web/public/js apps/mobile/app apps/desktop/src-tauri/target
	find . -name .cpcache -type d -prune -exec rm -rf {} +
	find . -name .shadow-cljs -type d -prune -exec rm -rf {} +

rename: ## rename the template, e.g. make rename NAME=acme
	@test -n "$(NAME)" || (echo "usage: make rename NAME=yourproject" && exit 1)
	@./scripts/rename.sh "$(NAME)"
