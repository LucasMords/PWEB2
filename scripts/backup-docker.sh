#!/bin/sh
# Backup do banco que roda no Docker Compose (servico db).
#
#   sh scripts/backup-docker.sh
#
# Grava em backups/ um arquivo no formato custom do pg_dump e mantem os 14 mais recentes.
# Para agendar, use o cron do servidor, por exemplo todo dia as 2h:
#   0 2 * * * cd /caminho/do/projeto && sh scripts/backup-docker.sh
#
# Restaurar:
#   docker compose exec -T db pg_restore -U clinica -d clinica --clean --if-exists < backups/<arquivo>.dump
set -eu

cd "$(dirname "$0")/.."
mkdir -p backups
arquivo="backups/clinica-$(date +%Y%m%d-%H%M%S).dump"

docker compose exec -T db pg_dump -U clinica -d clinica -Fc > "$arquivo"

ls -1t backups/clinica-*.dump | tail -n +15 | xargs -r rm --
echo "Backup gravado em $arquivo"
