# The Microsoft Store package (docs/release.md#microsoft-store): the app image from
# `./gradlew :app:createDistributable`, with a manifest and logos, packed by the Windows SDK's
# makepri and makeappx. Unsigned: the Store signs what it publishes.
#
#   .\packaging\windows\msix.ps1 -Version 1.0.0
#
# The identity is Partner Center's (App management > Product identity; Dan, 2026-11-03). It is public:
# every package the Store publishes carries it.
param(
    [Parameter(Mandatory)] [string] $Version,
    [string] $Name = "beermat.pub.Beermat",
    [string] $Publisher = "CN=B791A185-BC04-43FC-9002-02CC5FEBA6FA",
    [string] $PublisherName = "beermat.pub",
    [string] $StoreId = "",
    [string] $Out = "build\installers\Beermat-windows.msix"
)
$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot\..\.."
$image = Join-Path $root "app\build\compose\binaries\main\app\Beermat"
if (-not (Test-Path "$image\Beermat.exe")) { throw "No app image: run ./gradlew :app:createDistributable first" }

# The Store wants four numbers, the last one 0, each upload higher than the one before. A preview of
# X.0.0 packs just below it, as (X-1).9.N: 1.0.0-dev3 as 0.9.3.0, -beta.2 as 0.9.52.0, -rc.1 as
# 0.9.91.0; then 1.0.0 as 1.0.0.0 (docs/release.md#microsoft-store).
$parts = $Version.TrimStart('v').Split('-', 2)
$plain = $parts[0]
if ($plain -notmatch '^(\d+)\.(\d+)\.(\d+)$') { throw "Version $Version isn't X.Y.Z" }
if ($parts.Count -eq 1) {
    $msixVersion = "$plain.0"
} else {
    $major = [int]$Matches[1]
    if ($major -lt 1 -or $Matches[2] -ne "0" -or $Matches[3] -ne "0") { Write-Warning "No Store number for the preview $Version (only previews of X.0.0 have one): no MSIX"; exit 0 }
    if ($parts[1] -notmatch '^(dev|beta|rc)\.?(\d+)$') { Write-Warning "No Store number for the preview $Version (dev, beta or rc, and a number): no MSIX"; exit 0 }
    $n = [int]$Matches[2] + @{ dev = 0; beta = 50; rc = 90 }[$Matches[1]]
    $msixVersion = "$($major - 1).9.$n.0"
}

# The newest Windows SDK that has the tools.
$sdk = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\10.*\x64\makeappx.exe" |
    Sort-Object { [version]$_.Directory.Parent.Name } | Select-Object -Last 1
if (-not $sdk) { throw "makeappx.exe not found: install the Windows SDK" }
$bin = $sdk.Directory.FullName

$work = Join-Path $root "build\msix"
$stage = Join-Path $work "Beermat"
if (Test-Path $work) { Remove-Item -Recurse -Force $work }
New-Item -ItemType Directory -Force $stage | Out-Null
Copy-Item -Recurse "$image\*" $stage
Copy-Item -Recurse (Join-Path $root "packaging\icons\msix") (Join-Path $stage "Assets")

# The Store build checks packaging/windows/store-version.txt for updates, and links to its Store page.
$store = if ($StoreId) { $StoreId } else { "pending" }
[IO.File]::AppendAllText((Join-Path $stage "app\Beermat.cfg"), "`r`njava-options=-Dbeermat.store=$store`r`n")

$manifest = (Get-Content -Raw -Encoding UTF8 (Join-Path $PSScriptRoot "AppxManifest.xml")).
    Replace("@NAME@", $Name).Replace("@PUBLISHER@", [Security.SecurityElement]::Escape($Publisher)).
    Replace("@PUBLISHER_NAME@", [Security.SecurityElement]::Escape($PublisherName)).Replace("@VERSION@", $msixVersion)
[IO.File]::WriteAllText((Join-Path $stage "AppxManifest.xml"), $manifest)

# resources.pri indexes the logos' target sizes, so the taskbar picks a sharp one.
& "$bin\makepri.exe" createconfig /cf "$work\priconfig.xml" /dq en-US /o | Out-Null
if ($LASTEXITCODE) { throw "makepri createconfig failed" }
& "$bin\makepri.exe" new /pr $stage /cf "$work\priconfig.xml" /mn "$stage\AppxManifest.xml" /of "$stage\resources.pri" /o | Out-Null
if ($LASTEXITCODE) { throw "makepri new failed" }

$target = Join-Path $root $Out
New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
& "$bin\makeappx.exe" pack /d $stage /p $target /o | Out-Null
if ($LASTEXITCODE) { throw "makeappx pack failed" }
Write-Host "MSIX $msixVersion written to $target"
