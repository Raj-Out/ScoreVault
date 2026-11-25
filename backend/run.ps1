# Starts the ScoreVault backend. Run from the backend folder:
#   powershell -ExecutionPolicy Bypass -File .\run.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "Java was not found. Install it with:  winget install Microsoft.OpenJDK.17"
    Write-Host "Then CLOSE this window, open a new PowerShell, and run this script again."
    exit 1
}

# Use the installed Maven if there is one, otherwise download a local copy into .tools (not committed).
$mvn = "mvn"
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    $mvnHome = Join-Path $PSScriptRoot ".tools\apache-maven-3.9.9"
    $mvn = Join-Path $mvnHome "bin\mvn.cmd"
    if (-not (Test-Path $mvn)) {
        Write-Host "Downloading Maven (one time only)..."
        New-Item -ItemType Directory -Force -Path (Join-Path $PSScriptRoot ".tools") | Out-Null
        $zip = Join-Path $PSScriptRoot ".tools\maven.zip"
        Invoke-WebRequest -Uri "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip" -OutFile $zip
        Expand-Archive -Path $zip -DestinationPath (Join-Path $PSScriptRoot ".tools") -Force
        Remove-Item $zip
    }
}

# Ask for the developer's own Azure SQL endpoint. It is kept in this PowerShell process only.
if ([string]::IsNullOrWhiteSpace($env:DB_URL)) {
    $env:DB_URL = Read-Host "Azure SQL JDBC URL (jdbc:sqlserver://<server>.database.windows.net:1433;database=<database>;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=120)"
}
if ([string]::IsNullOrWhiteSpace($env:DB_URL)) {
    throw "DB_URL is required. Enter your own Azure SQL JDBC URL or set DB_URL before running this script."
}

# The database password is typed here and kept only in this window's memory. It is never saved to a file.
if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) {
    $dbUser = Read-Host "Database user (press Enter for scorevault_app)"
    if ([string]::IsNullOrWhiteSpace($dbUser)) { $dbUser = "scorevault_app" }
    $env:DB_USERNAME = $dbUser
}

if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
    $secure = Read-Host "Password for $($env:DB_USERNAME)" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}

# A fresh random JWT signing secret for each run (tokens stop working after a restart).
if (-not $env:JWT_SECRET) {
    $bytes = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
}

# Google OAuth client IDs are public identifiers. Leave empty until a Web OAuth client is configured.
if ([string]::IsNullOrWhiteSpace($env:GOOGLE_CLIENT_ID)) {
    $googleClientId = Read-Host "Google OAuth Web client ID (optional; press Enter to skip)"
    if (-not [string]::IsNullOrWhiteSpace($googleClientId)) { $env:GOOGLE_CLIENT_ID = $googleClientId.Trim() }
}

Write-Host "Starting ScoreVault on http://localhost:8080  (first start downloads libraries; the first database call may take a minute)"
& $mvn spring-boot:run
