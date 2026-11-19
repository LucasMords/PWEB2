# Backup do banco da clinica no PostgreSQL instalado nesta maquina.
#
#   powershell -ExecutionPolicy Bypass -File scripts\backup-banco.ps1
#
# Usa as mesmas variaveis da aplicacao (DB_USUARIO e DB_SENHA). Grava em backups\ um
# arquivo no formato custom do pg_dump (compactado) e mantem so os 14 mais recentes.
# Para agendar todo dia, crie uma tarefa no Agendador de Tarefas do Windows com este comando.
#
# Restaurar (num banco vazio):
#   pg_restore -h localhost -U clinica -d clinica --clean --if-exists backups\<arquivo>.dump

param(
    [string]$Banco = "clinica",
    [string]$Servidor = "localhost",
    [int]$Manter = 14
)

$ErrorActionPreference = "Stop"

$pgDump = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\pg_dump.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
if (-not $pgDump) { throw "pg_dump nao encontrado. O PostgreSQL esta instalado?" }

$usuario = if ($env:DB_USUARIO) { $env:DB_USUARIO } else { "clinica" }
if (-not $env:DB_SENHA) { throw "Defina a variavel de ambiente DB_SENHA." }
$env:PGPASSWORD = $env:DB_SENHA

$pasta = Join-Path $PSScriptRoot "..\backups"
New-Item -ItemType Directory -Force $pasta | Out-Null
$arquivo = Join-Path $pasta ("{0}-{1}.dump" -f $Banco, (Get-Date -Format "yyyyMMdd-HHmmss"))

& $pgDump.FullName -h $Servidor -U $usuario -d $Banco -Fc -f $arquivo
if ($LASTEXITCODE -ne 0) { throw "pg_dump falhou (codigo $LASTEXITCODE)." }

# Rotacao: apaga os backups mais antigos alem dos $Manter mais recentes.
Get-ChildItem $pasta -Filter "$Banco-*.dump" | Sort-Object Name -Descending |
        Select-Object -Skip $Manter | Remove-Item -Confirm:$false

Write-Output "Backup gravado em $((Resolve-Path $arquivo).Path)"
