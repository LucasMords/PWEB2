# Backup do banco da clinica no PostgreSQL instalado nesta maquina.
#
#   powershell -ExecutionPolicy Bypass -File scripts\backup-banco.ps1
#
# Usa as mesmas variaveis da aplicacao (DB_USUARIO e DB_SENHA). Grava em backups\ um
# arquivo no formato custom do pg_dump (compactado) e mantem so os 14 mais recentes.
# Para agendar todo dia, crie uma tarefa no Agendador de Tarefas do Windows com este comando.
#
# Restaurar (tudo ou nada: um erro desfaz a restauracao inteira):
#   pg_restore -h localhost -U clinica -d clinica --clean --if-exists --single-transaction backups\<arquivo>.dump

param(
    [string]$Banco = "clinica",
    [string]$Servidor = "localhost",
    [int]$Manter = 14
)

$ErrorActionPreference = "Stop"

# Ordena pela versao (pasta 18, 9.6...) como numero: como texto, 9.6 viria antes de 18.
$pgDump = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\pg_dump.exe" -ErrorAction SilentlyContinue |
        Sort-Object { "$($_.Directory.Parent.Name).0" -as [version] } -Descending | Select-Object -First 1
if (-not $pgDump) { throw "pg_dump nao encontrado. O PostgreSQL esta instalado?" }

$usuario = if ($env:DB_USUARIO) { $env:DB_USUARIO } else { "clinica" }
if (-not $env:DB_SENHA) { throw "Defina a variavel de ambiente DB_SENHA." }
$env:PGPASSWORD = $env:DB_SENHA

$pasta = Join-Path $PSScriptRoot "..\backups"
New-Item -ItemType Directory -Force $pasta | Out-Null
$arquivo = Join-Path $pasta ("{0}-{1}.dump" -f $Banco, (Get-Date -Format "yyyyMMdd-HHmmss"))

# Grava com outro nome e so renomeia se o pg_dump terminar bem: um backup que falhou nao
# pode ocupar a vaga de um valido na rotacao. O throw encerra o script com codigo 1.
$parcial = "$arquivo.parcial"
try {
    & $pgDump.FullName -h $Servidor -U $usuario -d $Banco -Fc -f $parcial
    if ($LASTEXITCODE -ne 0) { throw "pg_dump falhou (codigo $LASTEXITCODE)." }
    Move-Item -LiteralPath $parcial -Destination $arquivo
} finally {
    if (Test-Path -LiteralPath $parcial) { Remove-Item -LiteralPath $parcial -Confirm:$false }
}

# Rotacao: apaga os backups mais antigos alem dos $Manter mais recentes. O padrao exige a data logo
# depois do nome do banco, para nao pegar backups de outro banco (clinica-outra-...).
$padrao = "^" + [regex]::Escape($Banco) + "-\d{8}-\d{6}\.dump$"
Get-ChildItem $pasta -File | Where-Object { $_.Name -match $padrao } |
        Sort-Object Name -Descending | Select-Object -Skip $Manter | Remove-Item -Confirm:$false

Write-Output "Backup gravado em $((Resolve-Path $arquivo).Path)"
