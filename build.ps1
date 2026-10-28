param(
  [Parameter(Mandatory=$true)][string]$BuildTools,
  [Parameter(Mandatory=$true)][string]$AndroidJar,
  [Parameter(Mandatory=$true)][string]$SigningKey,
  [string]$KeyAlias = 'vultr-pocket',
  [string]$OutputApk = (Join-Path $PSScriptRoot 'Vultr-Pocket-Android.apk')
)
$ErrorActionPreference='Stop'
& node (Join-Path $PSScriptRoot 'tools/sync-locales.cjs')
if($LASTEXITCODE-ne 0){throw 'Locale generation failed'}
$assetRoot=Join-Path $PSScriptRoot 'assets'
$allowedAssets=@('build-info.json','web/index.html','web/app.js','web/style.css','web/manifest.webmanifest','web/icon.svg','web/icon-192.png','web/icon-512.png','web/NOTICE.txt','web/i18n.js','web/i18n-data.js','web/locales/en.json','web/fonts/OFL.txt','web/fonts/Tinos-Regular.ttf','web/fonts/Tinos-Bold.ttf')
foreach($assetFile in Get-ChildItem -LiteralPath $assetRoot -Recurse -File){
  $assetPath=[System.IO.Path]::GetRelativePath($assetRoot,$assetFile.FullName).Replace('\','/')
  if($assetPath -notin $allowedAssets){throw ('Unexpected asset; review before packaging: '+$assetPath)}
}
if (-not $env:VP_STORE_PASSWORD) { throw 'Set VP_STORE_PASSWORD and VP_KEY_PASSWORD environment variables' }
if (-not $env:VP_KEY_PASSWORD) { $env:VP_KEY_PASSWORD=$env:VP_STORE_PASSWORD }
$buildRoot=Join-Path $PSScriptRoot ('build/run-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $buildRoot,(Join-Path $buildRoot 'classes'),(Join-Path $buildRoot 'dex') | Out-Null
$versionName=([xml](Get-Content -LiteralPath (Join-Path $PSScriptRoot 'AndroidManifest.xml') -Raw)).manifest.versionName
if ($versionName -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid versionName' }
$compileUtc=[DateTimeOffset]::UtcNow
$compileStamp=$compileUtc.ToString('yyyy-MM-dd HH:mm:ss')+' UTC'
$generated=Join-Path $buildRoot 'generated/AppBuild.java'
New-Item -ItemType Directory -Force (Split-Path -Parent $generated) | Out-Null
@"
package app.vultrpocket.mobile;
final class AppBuild {
    static final String VERSION = "$versionName";
    static final String COMPILED_AT = "$compileStamp";
}
"@ | Set-Content -LiteralPath $generated -Encoding utf8NoBOM
@{version=$versionName;build_type='Release';compiled_at=$compileStamp;compiled_utc=$compileUtc.ToString('o');architecture='Java DEX; compatible with arm64-v8a; no native libraries'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'assets/build-info.json') -Encoding utf8NoBOM
$aapt2=Join-Path $BuildTools 'aapt2.exe'
$sourceFiles=@(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' -File | ForEach-Object FullName)+@($generated)
$bootClasspath=$AndroidJar+[System.IO.Path]::PathSeparator+(Join-Path $BuildTools 'core-lambda-stubs.jar')
& javac '-J-Duser.language=en' '-J-Duser.country=US' -encoding UTF-8 -Xlint:-options -source 8 -target 8 -bootclasspath $bootClasspath -d (Join-Path $buildRoot 'classes') @sourceFiles
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$classesJar=Join-Path $buildRoot 'classes.jar'
if (Test-Path -LiteralPath $classesJar) { Remove-Item -LiteralPath $classesJar }
[System.IO.Compression.ZipFile]::CreateFromDirectory((Join-Path $buildRoot 'classes'),$classesJar)
& (Join-Path $BuildTools 'd8.bat') --release --min-api 26 --lib $AndroidJar --output (Join-Path $buildRoot 'dex') $classesJar
if ($LASTEXITCODE -ne 0) { throw 'DEX conversion failed' }
$resources=Join-Path $buildRoot 'resources.zip'
& $aapt2 compile --dir (Join-Path $PSScriptRoot 'res') -o $resources
if ($LASTEXITCODE -ne 0) { throw 'Resource compilation failed' }
$unsigned=Join-Path $buildRoot 'unsigned.apk'
& $aapt2 link -I $AndroidJar --manifest (Join-Path $PSScriptRoot 'AndroidManifest.xml') -A (Join-Path $PSScriptRoot 'assets') -o $unsigned $resources
if ($LASTEXITCODE -ne 0) { throw 'Resource linking failed' }
$zip=[System.IO.Compression.ZipFile]::Open($unsigned,[System.IO.Compression.ZipArchiveMode]::Update)
try {
  # Windows aapt2 can retain backslashes under nested asset directories.
  # Android AssetManager requires ZIP members with forward slashes.
  foreach ($entry in @($zip.Entries)) {
    if ($entry.FullName.Contains('\')) {
      $normalized=$entry.FullName.Replace('\','/')
      if ($zip.GetEntry($normalized)) { throw 'Duplicate normalized asset path' }
      $memory=[System.IO.MemoryStream]::new()
      $inputStream=$entry.Open()
      try { $inputStream.CopyTo($memory) } finally { $inputStream.Dispose() }
      $entry.Delete()
      $newEntry=$zip.CreateEntry($normalized,[System.IO.Compression.CompressionLevel]::Optimal)
      $outputStream=$newEntry.Open()
      try { $memory.Position=0; $memory.CopyTo($outputStream) } finally { $outputStream.Dispose(); $memory.Dispose() }
    }
  }
  [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $buildRoot 'dex/classes.dex'),'classes.dex',[System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
} finally { $zip.Dispose() }
$aligned=Join-Path $buildRoot 'aligned.apk'
& (Join-Path $BuildTools 'zipalign.exe') -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed' }
& (Join-Path $BuildTools 'apksigner.bat') sign --ks $SigningKey --ks-key-alias $KeyAlias --ks-pass env:VP_STORE_PASSWORD --key-pass env:VP_KEY_PASSWORD --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out $OutputApk $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
& (Join-Path $BuildTools 'apksigner.bat') verify --verbose --print-certs $OutputApk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
& (Join-Path $BuildTools 'zipalign.exe') -c -p 4 $OutputApk
if ($LASTEXITCODE -ne 0) { throw 'APK alignment verification failed' }
$verifyArchive=[System.IO.Compression.ZipFile]::OpenRead($OutputApk)
try {
  foreach ($assetName in @('AndroidManifest.xml','classes.dex','assets/web/index.html','assets/web/app.js','assets/web/style.css','assets/web/NOTICE.txt','assets/web/i18n.js','assets/web/i18n-data.js','assets/web/locales/en.json','assets/web/fonts/OFL.txt')) {
    if (-not $verifyArchive.GetEntry($assetName)) { throw ('Missing final APK member: '+$assetName) }
  }
  if (@($verifyArchive.Entries | Where-Object { $_.FullName.Contains('\') }).Count) { throw 'Invalid Android asset separator' }
} finally { $verifyArchive.Dispose() }
Write-Output ('Built: '+$OutputApk)
