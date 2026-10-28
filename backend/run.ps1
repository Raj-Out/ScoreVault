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

# The database password is typed here and kept only in this window's memory. It is never saved to a file.
$secure = Read-Host "Azure SQL password for scoreadmin" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

# A fresh random JWT signing secret for each run (tokens stop working after a restart).
if (-not $env:JWT_SECRET) {
    $bytes = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
}

Write-Host "Starting ScoreVault on http://localhost:8080  (first start downloads libraries; the first database call may take a minute)"
& $mvn spring-boot:run
