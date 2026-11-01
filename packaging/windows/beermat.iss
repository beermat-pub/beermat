; The Windows installer (docs/release.md): Inno Setup on the app image that
; `./gradlew :app:createDistributable` makes. Built by .github/workflows/release.yml:
;   iscc /DAppVersion=1.0.0 packaging\windows\beermat.iss
; Our own pictures (packaging/icons, from the logo): no stock installer art.

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#define AppImage "..\..\app\build\compose\binaries\main\app\Beermat"

[Setup]
AppId={{8C1E2B47-3D5A-4F60-9B1C-7E2A5D4F3B10}
AppName=Beermat
AppVersion={#AppVersion}
AppVerName=Beermat {#AppVersion}
AppPublisher=beermat.pub
AppPublisherURL=https://beermat.pub
AppSupportURL=https://github.com/beermat-pub/beermat/issues
AppUpdatesURL=https://beermat.pub
DefaultDirName={autopf}\Beermat
DefaultGroupName=Beermat
DisableProgramGroupPage=yes
; Install for everyone (admin) or just me (no admin), as the user chooses.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
; The licence page ("I accept"): the no-warranty notice, then the MIT licence.
LicenseFile=..\licence.txt
OutputDir=..\..\build\installers
OutputBaseFilename=Beermat-{#AppVersion}-windows-setup
SetupIconFile=..\icons\beermat.ico
UninstallDisplayIcon={app}\Beermat.exe
WizardStyle=modern
WizardImageFile=..\icons\installer-side.bmp,..\icons\installer-side-2x.bmp
WizardSmallImageFile=..\icons\installer-small.bmp,..\icons\installer-small-2x.bmp
WizardImageStretch=no
WizardImageBackColor=$E3EFF6
Compression=lzma2/max
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
ChangesAssociations=yes
; The beermat command's folder may be added to the PATH (docs/cli.md#installing).
ChangesEnvironment=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "associate"; Description: "Open .bbm worksheets with Beermat"; GroupDescription: "Files:"
Name: "addtopath"; Description: "Add the beermat command to the PATH (for terminals and AI assistants)"; GroupDescription: "Command line:"

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\Beermat"; Filename: "{app}\Beermat.exe"
Name: "{autodesktop}\Beermat"; Filename: "{app}\Beermat.exe"; Tasks: desktopicon

[Registry]
Root: HKA; Subkey: "Software\Classes\.bbm"; ValueType: string; ValueName: ""; ValueData: "Beermat.Worksheet"; Flags: uninsdeletevalue; Tasks: associate
Root: HKA; Subkey: "Software\Classes\Beermat.Worksheet"; ValueType: string; ValueName: ""; ValueData: "Beermat worksheet"; Flags: uninsdeletekey; Tasks: associate
Root: HKA; Subkey: "Software\Classes\Beermat.Worksheet\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\Beermat.exe,0"; Tasks: associate
Root: HKA; Subkey: "Software\Classes\Beermat.Worksheet\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\Beermat.exe"" ""%1"""; Tasks: associate

[Run]
Filename: "{app}\Beermat.exe"; Description: "{cm:LaunchProgram,Beermat}"; Flags: nowait postinstall skipifsilent

[Code]
// The PATH that the "addtopath" task changes: the user's, or the system's for an install for everyone.
function EnvRoot: Integer;
begin
  if IsAdminInstallMode then Result := HKEY_LOCAL_MACHINE else Result := HKEY_CURRENT_USER;
end;

function EnvKey: String;
begin
  if IsAdminInstallMode then Result := 'SYSTEM\CurrentControlSet\Control\Session Manager\Environment' else Result := 'Environment';
end;

procedure AddToPath(Dir: String);
var
  Path: String;
begin
  if not RegQueryStringValue(EnvRoot, EnvKey, 'Path', Path) then Path := '';
  if Pos(';' + Uppercase(Dir) + ';', ';' + Uppercase(Path) + ';') > 0 then exit;
  if (Path <> '') and (Copy(Path, Length(Path), 1) <> ';') then Path := Path + ';';
  RegWriteExpandStringValue(EnvRoot, EnvKey, 'Path', Path + Dir);
end;

procedure RemoveFromPath(Dir: String);
var
  Path: String;
  P: Integer;
begin
  if not RegQueryStringValue(EnvRoot, EnvKey, 'Path', Path) then exit;
  P := Pos(';' + Uppercase(Dir) + ';', ';' + Uppercase(Path) + ';');
  if P = 0 then exit;
  // Dir starts at P in Path; take it with the separator before it (or after it, when it comes first).
  if P > 1 then Delete(Path, P - 1, Length(Dir) + 1) else Delete(Path, 1, Length(Dir) + 1);
  RegWriteExpandStringValue(EnvRoot, EnvKey, 'Path', Path);
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if (CurStep = ssPostInstall) and WizardIsTaskSelected('addtopath') then AddToPath(ExpandConstant('{app}\bin'));
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then RemoveFromPath(ExpandConstant('{app}\bin'));
end;
