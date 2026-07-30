Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Import-SagaLocalEnvironment {
    param([Parameter(Mandatory)][string]$RepositoryRoot)

    foreach ($fileName in @('.env', '.env.local')) {
        $path = Join-Path $RepositoryRoot $fileName
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            continue
        }
        foreach ($line in Get-Content -LiteralPath $path) {
            $trimmed = $line.Trim()
            if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
                continue
            }
            $separator = $trimmed.IndexOf('=')
            if ($separator -lt 1) {
                throw "Invalid environment entry in $fileName"
            }
            $name = $trimmed.Substring(0, $separator).Trim()
            $value = $trimmed.Substring($separator + 1).Trim()
            if ($value.Length -ge 2 -and (
                    ($value.StartsWith('"') -and $value.EndsWith('"')) -or
                    ($value.StartsWith("'") -and $value.EndsWith("'"))
                )) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
                throw "Invalid environment variable name in $fileName"
            }
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}

function Get-SagaLocalDatabase {
    $profile = [Environment]::GetEnvironmentVariable(
            'SPRING_PROFILES_ACTIVE', 'Process'
    )
    if ([string]::IsNullOrWhiteSpace($profile)) {
        throw 'SPRING_PROFILES_ACTIVE must explicitly include local'
    }
    $profiles = $profile -split '[,;\s]+' | ForEach-Object {
        $_.Trim().ToLowerInvariant()
    }
    if ($profiles -contains 'prod' -or $profiles -contains 'production') {
        throw 'Refusing to run with a production profile'
    }
    if ($profiles -notcontains 'local') {
        throw 'Refusing to run unless SPRING_PROFILES_ACTIVE includes local'
    }

    $url = [Environment]::GetEnvironmentVariable('DATABASE_JDBC_URL', 'Process')
    $user = [Environment]::GetEnvironmentVariable('DATABASE_USERNAME', 'Process')
    $password = [Environment]::GetEnvironmentVariable('DATABASE_PASSWORD', 'Process')
    if ([string]::IsNullOrWhiteSpace($url) -or
            [string]::IsNullOrWhiteSpace($user) -or
            [string]::IsNullOrWhiteSpace($password)) {
        throw 'DATABASE_JDBC_URL, DATABASE_USERNAME and DATABASE_PASSWORD are required'
    }
    if ($url -notmatch '^jdbc:mysql://(?<host>[^/:?]+)(:(?<port>[0-9]+))?/(?<database>[^?;/]+)') {
        throw 'DATABASE_JDBC_URL must be a jdbc:mysql://host[:port]/database URL'
    }
    $hostName = $Matches.host
    $port = if ($Matches.port) { [int]$Matches.port } else { 3306 }
    $database = $Matches.database
    if ($hostName -notmatch '^[A-Za-z0-9.-]+$' -or
            $database -notmatch '^[A-Za-z0-9_$-]+$') {
        throw 'The database host or name contains unsupported characters'
    }
    if ($password.Contains("`r") -or $password.Contains("`n")) {
        throw 'DATABASE_PASSWORD must not contain line breaks'
    }
    return [PSCustomObject]@{
        Profile = $profile
        Url = $url
        User = $user
        Password = $password
        Host = $hostName
        Port = $port
        Database = $database
    }
}

function Assert-SagaLocalDatabaseTarget {
    param(
        [Parameter(Mandatory)]$Database,
        [string[]]$ApprovedHost = @()
    )

    $hostName = $Database.Host.ToLowerInvariant()
    if ($hostName -match 'railway|amazonaws|rds\.|production|prod') {
        throw 'Refusing a Railway, AWS RDS, or production-like database host'
    }
    $isLoopback = $hostName -in @('localhost', '127.0.0.1', '::1')
    $isApproved = $ApprovedHost | Where-Object {
        $_.Trim().Equals($Database.Host, [StringComparison]::OrdinalIgnoreCase)
    }
    if (-not $isLoopback -and -not $isApproved) {
        throw 'Refusing a non-local host. Re-run with -ApprovedHost <exact-dev-host> after manual approval.'
    }
}

function Invoke-SagaJdbcTool {
    param(
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [Parameter(Mandatory)][ValidateSet('CHECK', 'MIGRATE')][string]$Mode,
        [Parameter(Mandatory)]$Database
    )

    $wrapper = Join-Path $RepositoryRoot 'mvnw.cmd'
    if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) {
        throw 'mvnw.cmd was not found at the repository root'
    }

    $toolArguments = $Mode
    $hostName = $Database.Host.ToLowerInvariant()
    $isLoopback = $hostName -in @('localhost', '127.0.0.1', '::1')
    if (-not $isLoopback) {
        # The Java tool repeats exact-host approval before it opens a JDBC connection.
        $toolArguments = "$Mode --approved-host $($Database.Host)"
    }

    $output = & $wrapper '-q' 'test-compile' 'exec:java' `
            '-Dexec.mainClass=com.saga.be.tools.LocalIntegrationSchemaTool' `
            '-Dexec.classpathScope=test' `
            "-Dexec.args=$toolArguments"
    if ($LASTEXITCODE -ne 0) {
        throw "JDBC schema $Mode preflight failed"
    }
    return @($output)
}

function Get-SagaJdbcToolValue {
    param(
        [Parameter(Mandatory)][string[]]$Output,
        [Parameter(Mandatory)][string]$Name
    )

    $line = $Output | Where-Object { $_ -like "$Name=*" } | Select-Object -Last 1
    if ($null -eq $line) {
        throw "JDBC schema tool did not report $Name"
    }
    return $line.Substring($Name.Length + 1)
}

function New-SagaFlywayConfigFile {
    param(
        [Parameter(Mandatory)]$Database,
        [Parameter(Mandatory)][string]$MigrationDirectory
    )

    $tempPath = Join-Path ([System.IO.Path]::GetTempPath()) (
            'saga-flyway-' + [Guid]::NewGuid().ToString('N') + '.conf'
    )
    [System.IO.File]::WriteAllText(
            $tempPath,
            '',
            [System.Text.UTF8Encoding]::new($false)
    )
    $currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = [System.Security.AccessControl.FileSecurity]::new()
    $acl.SetAccessRuleProtection($true, $false)
    $acl.SetOwner($currentUser)
    $acl.AddAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new(
            $currentUser,
            [System.Security.AccessControl.FileSystemRights]::FullControl,
            [System.Security.AccessControl.AccessControlType]::Allow
    ))
    Set-Acl -LiteralPath $tempPath -AclObject $acl
    $content = @(
        "flyway.url=$($Database.Url)",
        "flyway.user=$($Database.User)",
        "flyway.password=$($Database.Password)",
        "flyway.locations=filesystem:$MigrationDirectory",
        'flyway.baselineVersion=1'
    )
    [System.IO.File]::WriteAllLines(
            $tempPath,
            $content,
            [System.Text.UTF8Encoding]::new($false)
    )
    return $tempPath
}

function Remove-SagaFlywayConfigFile {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path -LiteralPath $Path)) {
        return
    }
    $resolved = (Resolve-Path -LiteralPath $Path).Path
    $tempRoot = [System.IO.Path]::GetTempPath().TrimEnd('\')
    if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to remove a Flyway config file outside the temporary directory'
    }
    Remove-Item -LiteralPath $resolved -Force
}

function Invoke-SagaFlywayMavenGoal {
    param(
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [Parameter(Mandatory)][string]$ConfigPath,
        [Parameter(Mandatory)][string]$Goal
    )
    $wrapper = Join-Path $RepositoryRoot 'mvnw.cmd'
    if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) {
        throw 'mvnw.cmd was not found at the repository root'
    }
    & $wrapper "-Dflyway.configFiles=$ConfigPath" $Goal
    if ($LASTEXITCODE -ne 0) {
        throw "Flyway Maven goal failed: $Goal"
    }
}
