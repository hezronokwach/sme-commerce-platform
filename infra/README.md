# Infra

Deployment and environment files that are not part of one app, for example the
Render Blueprint and full-stack Docker Compose.

Local Postgres for development lives in `backend/compose.yaml`, next to the app, because
Spring Boot's Docker Compose support looks for it in the backend's working directory.
