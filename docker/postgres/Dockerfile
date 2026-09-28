# Test image for Testcontainers. Mirrors production: the CloudNativePG cluster
# postgresql-cnpg runs PostgreSQL 18 with pg_cron, pg_partman and pgvector
# (image ghcr.io/gdinizds/postgresql-cnpg:18).
FROM postgres:18

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      postgresql-18-cron \
      postgresql-18-partman \
      postgresql-18-pgvector \
 && rm -rf /var/lib/apt/lists/*
