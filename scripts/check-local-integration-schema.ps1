[CmdletBinding()]
param(
    [string[]]$ApprovedHost = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'LocalIntegrationMigration.Common.ps1')

Import-SagaLocalEnvironment $repositoryRoot
$database = Get-SagaLocalDatabase
Assert-SagaLocalDatabaseTarget $database $ApprovedHost

Write-Host "Target: $($database.Host):$($database.Port)/$($database.Database)"
Write-Host "Profile: $($database.Profile)"

Invoke-SagaJdbcTool $repositoryRoot 'CHECK' $database | ForEach-Object {
    Write-Host $_
}
Write-Host 'Read-only preflight completed. No schema changes were made.'
