Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:ProjectRoot = Split-Path $PSScriptRoot -Parent

function Get-FoodCraftWorkRoot {
    if ($env:FOODCRAFT_WORK_DIR) { return [IO.Path]::GetFullPath($env:FOODCRAFT_WORK_DIR) }
    if ((Split-Path (Split-Path $script:ProjectRoot -Parent) -Leaf) -eq 'outputs') {
        return [IO.Path]::GetFullPath((Join-Path $script:ProjectRoot '..\..\work'))
    }
    return [IO.Path]::GetFullPath((Join-Path $script:ProjectRoot '..\foodcraft-work'))
}

function Get-FoodCraftJava {
    $workRoot = Get-FoodCraftWorkRoot
    $javaRoot = Join-Path $workRoot 'toolchains\microsoft-jdk17\jdk-17.0.20.1+1'
    if ($env:FOODCRAFT_JAVA_HOME) { $javaRoot = $env:FOODCRAFT_JAVA_HOME }
    if (-not (Test-Path -LiteralPath (Join-Path $javaRoot 'bin\javac.exe'))) {
        if ($env:FOODCRAFT_JAVA_HOME) { throw 'FOODCRAFT_JAVA_HOME must point to a complete JDK 17.' }
        $archive = Join-Path $workRoot 'toolchains\microsoft-jdk17.zip'
        New-Item -ItemType Directory -Force -Path (Split-Path $archive -Parent) | Out-Null
        if (-not (Test-Path -LiteralPath $archive)) {
            & curl.exe --fail --location --retry 3 --output $archive 'https://aka.ms/download-jdk/microsoft-jdk-17.0.20.1-windows-x64.zip'
            if ($LASTEXITCODE -ne 0) { throw 'JDK 17 download failed.' }
        }
        $expected = '3D9006956FC8AF5601CD24FFC4F468BEF48279C7EBD8171B9BDF90D0AABFBF1F'
        if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) { throw 'JDK archive SHA-256 mismatch.' }
        Expand-Archive -LiteralPath $archive -DestinationPath (Split-Path $javaRoot -Parent) -Force
    }
    $java = Join-Path $javaRoot 'bin\java.exe'
    $version = & $java -version 2>&1 | Out-String
    if ($version -notmatch 'version "17\.') { throw "A JDK 17 is required: $version" }
    return $javaRoot
}

function Invoke-FoodCraftGradle {
    param([string[]]$Tasks,[string]$Loader,[string]$Log,[string]$MavenMirror='',[switch]$Offline)
    $arguments = @($Tasks) + @("-PfoodcraftLoader=$Loader", '--no-daemon', '--console=plain')
    if ($MavenMirror) { $arguments += "-PfoodcraftMavenMirror=$MavenMirror" }
    if ($Offline) { $arguments += '--offline' }
    New-Item -ItemType Directory -Force -Path (Split-Path $Log -Parent) | Out-Null
    Push-Location $script:ProjectRoot
    try {
        & (Join-Path $script:ProjectRoot 'gradlew.bat') @arguments 2>&1 | Tee-Object -FilePath $Log
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed; see $Log" }
    } finally { Pop-Location }
}
