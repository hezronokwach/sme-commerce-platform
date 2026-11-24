# Infra

Deployment and environment files that are not part of one app, for example the
Render Blueprint and full-stack Docker Compose.

Local Postgres for development lives in `backend/compose.yaml`, next to the app, because
Spring Boot's Docker Compose support looks for it in the backend's working directory.

## Full stack in Docker

`compose.yaml` builds the backend image (`backend/Dockerfile`) and runs it with Postgres, the way
Render will run it. It reads secrets from `backend/.env`.

```bash
docker compose -f infra/compose.yaml up --build
```

## Render

`render.yaml` is the Render Blueprint for the backend. The database is on Neon (ADR 0021). The full steps are in
[docs/deploy.md](../docs/deploy.md).
