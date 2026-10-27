$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$testBuild=Join-Path $projectRoot ('build/tests-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $testBuild | Out-Null
$sources=@('EndpointPolicy','PasswordPolicy','ApiErrorPolicy','RetentionPolicy','LoginTicketStore','AddressPolicy','RebootPolicy','RebootClient','BandwidthSeries','RefreshPolicy','RefreshGate','ReadLifecycle','BridgeDispatch') | ForEach-Object { Join-Path $projectRoot ('src/app/vultrpocket/mobile/'+$_+'.java') }
$tests=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'app/vultrpocket/mobile') -Filter '*Test.java' -File | ForEach-Object FullName
& javac -encoding UTF-8 -d $testBuild @sources @tests
if($LASTEXITCODE-ne 0){throw 'JVM test compilation failed'}
foreach($name in @('EndpointPolicyTest','PasswordPolicyTest','ApiErrorPolicyTest','RetentionPolicyTest','LoginTicketStoreTest','AddressPolicyTest','RebootPolicyTest','RebootClientTest','BandwidthSeriesTest','RefreshPolicyTest','RefreshGateTest','ReadLifecycleTest','BridgeDispatchTest')) { & java -cp $testBuild ('app.vultrpocket.mobile.'+$name);if($LASTEXITCODE-ne 0){throw ($name+' failed')} }
& node (Join-Path $PSScriptRoot 'native-bridge.test.cjs')
if($LASTEXITCODE-ne 0){throw 'Native bridge checks failed'}
& node (Join-Path $PSScriptRoot 'startup-flow.test.cjs')
if($LASTEXITCODE-ne 0){throw 'Startup flow checks failed'}

& node (Join-Path $PSScriptRoot 'daily-flow.test.cjs')
if($LASTEXITCODE-ne 0){throw 'Daily traffic checks failed'}

& node (Join-Path $PSScriptRoot 'refresh-flow.test.cjs')
if($LASTEXITCODE-ne 0){throw 'Refresh lifecycle checks failed'}


& node (Join-Path $PSScriptRoot 'account-log-flow.test.cjs')
if($LASTEXITCODE-ne 0){throw 'Account and log checks failed'}


& node (Join-Path $PSScriptRoot 'currency-flow.test.cjs')
if($LASTEXITCODE-ne 0){throw 'Currency checks failed'}


& node (Join-Path $PSScriptRoot 'reboot-monitor.test.cjs')
if($LASTEXITCODE-ne 0){throw 'Reboot monitor checks failed'}


& node (Join-Path $PSScriptRoot 'refresh-cooldown.test.cjs')
if($LASTEXITCODE-ne 0){throw 'Refresh cooldown checks failed'}
& node (Join-Path $PSScriptRoot 'first-setup.test.cjs')
if($LASTEXITCODE-ne 0){throw 'First setup checks failed'}
& node (Join-Path $PSScriptRoot 'preview.test.cjs')
if($LASTEXITCODE-ne 0){throw 'Synthetic preview checks failed'}
