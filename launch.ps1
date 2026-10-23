param(
    [string]$CharlesHome = $env:CHARLES_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string[]]$CharlesArgs = @()
)
$ErrorActionPreference = 'Stop'
try {
    if (-not $CharlesHome) {
        foreach ($candidate in @($PSScriptRoot, (Split-Path $PSScriptRoot -Parent), "$env:ProgramFiles/Charles")) {
            if (Test-Path -LiteralPath (Join-Path $candidate 'lib/charles.jar')) { $CharlesHome = $candidate; break }
        }
    }
    if (-not $CharlesHome -or -not (Test-Path -LiteralPath (Join-Path $CharlesHome 'lib/charles.jar'))) {
        throw 'Set CHARLES_HOME or pass -CharlesHome pointing to the Charles installation directory.'
    }
    $CharlesHome = (Resolve-Path -LiteralPath $CharlesHome).Path
    $addonJar = Join-Path $PSScriptRoot 'charles-pem-addon.jar'
    if (-not (Test-Path -LiteralPath $addonJar)) { throw 'Download charles-pem-addon.jar from the v0.1 release and place it beside launch.ps1.' }
    $javaCandidates = @()
    if ($JavaHome) { $javaCandidates += Join-Path $JavaHome 'bin/javaw.exe' }
    $javaCandidates += Join-Path $CharlesHome 'jdk/bin/javaw.exe'
    $javaCandidates += @(Get-ChildItem -Path "$CharlesHome/addon/tools/jdk25/*/bin/javaw.exe" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
    $onPath = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($onPath) { $javaCandidates += $onPath.Source }
    $javaw = $null
    foreach ($candidate in $javaCandidates) {
        if (-not (Test-Path -LiteralPath $candidate)) { continue }
        $release = Join-Path (Split-Path (Split-Path $candidate -Parent) -Parent) 'release'
        if (-not (Test-Path -LiteralPath $release)) { continue }
        $version = Select-String -LiteralPath $release -Pattern '^JAVA_VERSION="(\d+)'
        if ($version -and [int]$version.Matches[0].Groups[1].Value -ge 25) { $javaw = $candidate; break }
    }
    if (-not $javaw) { throw 'A Java 25+ runtime with javaw.exe is required. Set JAVA_HOME or pass -JavaHome.' }
    $logDirectory = Join-Path $env:APPDATA 'Charles-PEM-Logs'
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    $logId = [Guid]::NewGuid().ToString('N')
    $arguments = @(
        '-Dsun.java2d.d3d=false', "-Dcharles.pem.home=$CharlesHome",
        "-Djava.library.path=$CharlesHome/lib", "-Djna.boot.library.path=$CharlesHome/lib", '-Djna.nounpack=true',
        '--add-opens=java.base/sun.security.ssl=ALL-UNNAMED',
        '--add-opens=java.desktop/java.awt.event=ALL-UNNAMED',
        '--add-opens=java.base/java.io=ALL-UNNAMED', '--enable-native-access=ALL-UNNAMED',
        '-cp', "$addonJar;$CharlesHome/lib/*", 'addon.Launcher'
    ) + $CharlesArgs
    # Start-Process joins ArgumentList into one Windows command line; quote every argument explicitly.
    $quoted = @($arguments | ForEach-Object {
        if ($_ -match '["\r\n]') { throw 'Arguments must not contain quotes or newlines.' }
        '"' + ([regex]::Replace($_, '(\\+)$', '$1$1')) + '"'
    })
    Start-Process -FilePath $javaw -ArgumentList $quoted -WorkingDirectory $CharlesHome -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logDirectory "$logId.stdout.log") `
        -RedirectStandardError (Join-Path $logDirectory "$logId.stderr.log") | Out-Null
} catch {
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Charles PEM add-on') | Out-Null
    exit 1
}
