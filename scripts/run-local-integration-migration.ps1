[CmdletBinding()]
param(
    [string[]]$ApprovedHost = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$migrationPath = Join-Path $repositoryRoot 'src/main/resources/db/migration/V2__integration_identity_and_sync.sql'
. (Join-Path $PSScriptRoot 'LocalIntegrationMigration.Common.ps1')

if (-not (Test-Path -LiteralPath $migrationPath -PathType Leaf)) {
    throw 'Required migration V2__integration_identity_and_sync.sql was not found'
}

Import-SagaLocalEnvironment $repositoryRoot
$database = Get-SagaLocalDatabase
Assert-SagaLocalDatabaseTarget $database $ApprovedHost

Write-Host 'LOCAL DATABASE MIGRATION ONLY'
Write-Host "Target host: $($database.Host)"
Write-Host "Target database: $($database.Database)"
Write-Host "Active profile: $($database.Profile)"
Write-Host 'Password is intentionally not displayed.'
$confirmation = Read-Host 'Type MIGRATE_LOCAL_V2 to baseline legacy schema at version 1 and apply V2'
if ($confirmation -cne 'MIGRATE_LOCAL_V2') {
    throw 'Migration cancelled: explicit confirmation was not provided'
}

$preflightOutput = Invoke-SagaJdbcTool $repositoryRoot 'MIGRATE' $database
$preflightOutput | ForEach-Object { Write-Host $_ }
$historyValue = Get-SagaJdbcToolValue $preflightOutput 'flyway_history_exists'
if ($historyValue -notin @('true', 'false')) {
    throw "JDBC schema tool returned an invalid flyway_history_exists value: $historyValue"
}
$historyExists = [System.Convert]::ToBoolean($historyValue)

$configPath = $null
try {
    $configPath = New-SagaFlywayConfigFile $database (Split-Path -Parent $migrationPath)
    if (-not $historyExists) {
        Write-Host 'No Flyway history found. Creating baseline version 1.'
        Invoke-SagaFlywayMavenGoal $repositoryRoot $configPath 'flyway:baseline'
    }
    Write-Host 'Applying pending migrations through Maven Wrapper.'
    Invoke-SagaFlywayMavenGoal $repositoryRoot $configPath 'flyway:migrate'
} finally {
    Remove-SagaFlywayConfigFile $configPath
}

Write-Host 'Migration command completed. Verify with:'
Write-Host @'
SELECT installed_rank, version, description, type, script, checksum, success
FROM flyway_schema_history
ORDER BY installed_rank;
'@
