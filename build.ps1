param([ValidateSet('all','forge','fabric')][string]$Loader='all',[string]$MavenMirror='',[switch]$Offline)
. (Join-Path $PSScriptRoot 'tools\environment.ps1')
$previousJava = $env:JAVA_HOME
$previousOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_HOME = Get-FoodCraftJava
    $env:JAVA_TOOL_OPTIONS = "$previousOptions -Dfile.encoding=UTF-8 -Duser.language=en"
    $workRoot = Get-FoodCraftWorkRoot
    $loaders = if ($Loader -eq 'all') { @('forge','fabric') } else { @($Loader) }
    $dist = Join-Path $PSScriptRoot 'dist'
    New-Item -ItemType Directory -Force -Path $dist | Out-Null
    foreach ($target in $loaders) {
        $task = if ($target -eq 'forge') { ':forge:build' } else { ':fabric:build' }
        Invoke-FoodCraftGradle -Tasks @($task) -Loader $target -Log (Join-Path $workRoot "logs\build-$target.log") -MavenMirror $MavenMirror -Offline:$Offline
        $name = "foodcraft-1.20.1-$target-2.0.0.jar"
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot "$target\build\libs\$name") -Destination (Join-Path $dist $name)
    }
    Get-ChildItem -LiteralPath $dist -Filter '*.jar' | ForEach-Object { '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $_.Name } | Set-Content -Encoding ascii (Join-Path $dist 'SHA256SUMS.txt')
} finally { $env:JAVA_HOME = $previousJava; $env:JAVA_TOOL_OPTIONS = $previousOptions }
