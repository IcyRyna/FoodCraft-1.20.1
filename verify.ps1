param([ValidateSet('all','forge','fabric')][string]$Loader='all',[switch]$Soak,[string]$Python='',[string]$MavenMirror='',[switch]$Offline)
. (Join-Path $PSScriptRoot 'tools\environment.ps1')
$previousJava = $env:JAVA_HOME
$previousOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_HOME = Get-FoodCraftJava
    $env:JAVA_TOOL_OPTIONS = "$previousOptions -Dfile.encoding=UTF-8 -Duser.language=en"
    if (-not $Python) {
        $bundled = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
        if (Test-Path -LiteralPath $bundled) { $Python=$bundled } else { $Python=(Get-Command python -ErrorAction Stop).Source }
    }
    & $Python (Join-Path $PSScriptRoot 'tools\check_content.py')
    if ($LASTEXITCODE -ne 0) { throw 'Content/resource validation failed.' }
    & $Python (Join-Path $PSScriptRoot 'tools\check_languages.py')
    if ($LASTEXITCODE -ne 0) { throw 'Language validation failed.' }
    $workRoot = Get-FoodCraftWorkRoot
    Invoke-FoodCraftGradle -Tasks @(':core:test') -Loader none -Log (Join-Path $workRoot 'logs\core-test.log') -MavenMirror $MavenMirror -Offline:$Offline
    & (Join-Path $PSScriptRoot 'build.ps1') -Loader $Loader -MavenMirror $MavenMirror -Offline:$Offline
    if ($Soak) { $env:JAVA_TOOL_OPTIONS += ' -Dfoodcraft.soak=true' }
    $loaders = if ($Loader -eq 'all') { @('forge','fabric') } else { @($Loader) }
    foreach ($target in $loaders) {
        $task = if ($target -eq 'forge') { ':forge:runGameTestServer' } else { ':fabric:runGametest' }
        $log = Join-Path $workRoot "logs\gametest-$target.log"
        $env:JAVA_TOOL_OPTIONS = "$previousOptions -Dfile.encoding=UTF-8 -Duser.language=en -Dfoodcraft.export.dir=$workRoot/evidence/$target"
        if ($Soak) { $env:JAVA_TOOL_OPTIONS += ' -Dfoodcraft.soak=true' }
        Invoke-FoodCraftGradle -Tasks @($task) -Loader $target -Log $log -MavenMirror $MavenMirror -Offline:$Offline
        $text = Get-Content -LiteralPath $log -Raw
        if ($text -notmatch 'All \d+ required tests passed' -or $text -match 'required tests failed|failed!|Couldn.t parse|FOODCRAFT CLIENT FAILED') { throw "GameTest did not pass: $log" }
        if ($Soak -and $text -notmatch 'FOODCRAFT SOAK PASSED 36000 ticks') { throw "Thirty-minute soak marker missing: $log" }
    }
    if ($Loader -eq 'all') {
        & $Python (Join-Path $PSScriptRoot 'tools\compare_loaders.py') (Join-Path $workRoot 'evidence\forge\runtime-content.json') (Join-Path $workRoot 'evidence\fabric\runtime-content.json')
        if ($LASTEXITCODE -ne 0) { throw 'Forge/Fabric runtime content differs.' }
    }
    Write-Host 'FoodCraft verification passed. See work/logs and dist/SHA256SUMS.txt.'
} finally { $env:JAVA_HOME=$previousJava; $env:JAVA_TOOL_OPTIONS=$previousOptions }
