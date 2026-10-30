# The Microsoft Store package (docs/release.md#microsoft-store): the app image from
# `./gradlew :app:createDistributable`, with a manifest and logos, packed by the Windows SDK's
# makepri and makeappx. Unsigned: the Store signs what it publishes.
#
#   .\packaging\windows\msix.ps1 -Version 1.0.0
#
# Without the Partner Center identity (-Name, -Publisher, -PublisherName) it uses placeholders: the
# package builds and installs locally, but the Store rejects it.
param(
    [Parameter(Mandatory)] [string] $Version,
    [string] $Name = "Beermat",
    [string] $Publisher = "CN=Beermat",
    [string] $PublisherName = "beermat.pub",
    [string] $StoreId = "",
    [string] $Out = "build\installers\Beermat-windows.msix"
)
$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot\..\.."
$image = Join-Path $root "app\build\compose\binaries\main\app\Beermat"
if (-not (Test-Path "$image\Beermat.exe")) { throw "No app image: run ./gradlew :app:createDistributable first" }

# The Store wants four numbers, the last one 0: 1.0.0-beta.2 packs as 1.0.0.0.
$plain = $Version.TrimStart('v').Split('-')[0]
if ($plain -notmatch '^\d+\.\d+\.\d+$') { throw "Version $Version isn't X.Y.Z" }
$msixVersion = "$plain.0"

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
