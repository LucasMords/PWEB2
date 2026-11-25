#!/bin/sh
# Backup do banco que roda no Docker Compose (servico db).
#
#   sh scripts/backup-docker.sh
#
# Grava em backups/ um arquivo no formato custom do pg_dump e mantem os 14 mais recentes.
# Para agendar, use o cron do servidor, por exemplo todo dia as 2h:
#   0 2 * * * cd /caminho/do/projeto && sh scripts/backup-docker.sh
#
# Restaurar (tudo ou nada: um erro desfaz a restauracao inteira):
#   docker compose exec -T db pg_restore -U clinica -d clinica --clean --if-exists --single-transaction < backups/<arquivo>.dump
set -eu

cd "$(dirname "$0")/.."
mkdir -p backups
arquivo="backups/clinica-$(date +%Y%m%d-%H%M%S).dump"

# Grava com outro nome e so renomeia se o pg_dump terminar bem: um backup que falhou nao
# pode ocupar a vaga de um valido na rotacao.
parcial="$arquivo.parcial"
trap 'rm -f -- "$parcial"' EXIT
trap 'exit 1' HUP INT TERM

if ! docker compose exec -T db pg_dump -U clinica -d clinica -Fc > "$parcial"; then
    echo "pg_dump falhou; nenhum backup foi gravado nem apagado." >&2
    exit 1
fi
mv -- "$parcial" "$arquivo"

# Rotacao: a data esta no nome, entao a ordem do nome e a ordem dos backups.
ls -1r backups | grep -E '^clinica-[0-9]{8}-[0-9]{6}\.dump$' | tail -n +15 |
    while IFS= read -r antigo; do rm -- "backups/$antigo"; done
echo "Backup gravado em $arquivo"
