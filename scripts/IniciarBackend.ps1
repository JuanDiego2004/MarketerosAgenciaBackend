param(
    [string]$Java25 = 'C:\Users\User\.jdks\ms-25.0.4.1',
    [int]$Puerto = 8080
)
$ErrorActionPreference = 'Stop'
$crmRaiz = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path -LiteralPath "$Java25\bin\java.exe")) { throw 'Indica la ruta de tu JDK 25 con -Java25.' }
$crmRelease = Get-Content -LiteralPath "$Java25\release"
if (-not ($crmRelease -match '^JAVA_VERSION="25[.\"]')) { throw 'El backend requiere Java 25.' }
if (Test-Path -LiteralPath "$crmRaiz\.env") {
    foreach ($crmLinea in Get-Content -LiteralPath "$crmRaiz\.env") {
        if ($crmLinea -match '^\s*([A-Z0-9_]+)\s*=\s*(.*)$') {
            $crmClave = $matches[1]
            $crmValor = $matches[2].Trim().Trim('"').Trim("'")
            [Environment]::SetEnvironmentVariable($crmClave, $crmValor, 'Process')
        }
    }
}
$env:JAVA_HOME = $Java25
$env:Path = "$Java25\bin;$env:Path"
Push-Location -LiteralPath $crmRaiz
try { & .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=$Puerto" }
finally { Pop-Location }
