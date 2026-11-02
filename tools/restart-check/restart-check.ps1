<#
.SYNOPSIS
    Headless server restart check (E3-S10, #200): starts runServer, runs a command list, stops it, starts it again
    and runs a second list. Exits 0 only if every step passed. Procedure and list format: docs/restart-check.md.

.DESCRIPTION
    Commands go over RCON because console stdin does not reach the server under Gradle's runServer. The script
    edits run/server/server.properties and run/server/config/orevault-server.toml for the run and puts both back
    afterwards, so the dev world, the dev ports and the debug flag are left as they were.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$First,
    [Parameter(Mandatory = $true)][string]$Second,
    # Optional PowerShell script run once while the server is down. Receives -WorldDir. Non-zero exit fails the run.
    [string]$Between = '',
    # Folder name under run/server. Fresh by default; name an existing folder to run against a prepared world.
    [string]$World = '',
    [int]$StartTimeoutSec = 300,
    [int]$StopTimeoutSec = 120,
    [int]$LogTimeoutSec = 30,
    [int]$GamePort = 25598,
    [int]$RconPort = 25599
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ServerDir = Join-Path $RepoRoot 'run\server'
$ConfigDir = Join-Path $ServerDir 'config'
$BackupDir = Join-Path $ServerDir 'restart-check-backup'
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$OutDir = Join-Path $ServerDir "restart-check-logs\$Stamp"
$Transcript = Join-Path $OutDir 'transcript.txt'
$Edited = @(
    @{ Path = (Join-Path $ServerDir 'server.properties'); Backup = (Join-Path $BackupDir 'server.properties') },
    @{ Path = (Join-Path $ConfigDir 'orevault-server.toml'); Backup = (Join-Path $BackupDir 'orevault-server.toml') }
)
$Script:Gradle = $null
$Script:Tcp = $null
$Script:Stream = $null
$Script:NextId = 1
$Script:Touched = $false
$Script:Passed = 0
$Script:JavaBefore = @()

function Say([string]$Text) {
    [Console]::Out.WriteLine($Text)
    if (Test-Path $OutDir) { Add-Content -Path $Transcript -Value $Text -Encoding UTF8 }
}

function Fail([string]$Text) { throw "FAIL: $Text" }

# ---- Command lists ---------------------------------------------------------------------------------------------

function Read-List([string]$Path) {
    $steps = New-Object System.Collections.ArrayList
    $current = $null
    $number = 0
    foreach ($raw in [IO.File]::ReadAllLines($Path)) {
        $number++
        $line = $raw.Trim()
        if ($line -eq '' -or $line.StartsWith('#')) { continue }
        $where = "${Path}:$number"
        $m = [regex]::Match($line, '^(run|expect|reject|log|nolog|sleep)\s+(.+)$')
        if (-not $m.Success) { Fail "$where is not a directive: $line" }
        $verb = $m.Groups[1].Value
        $value = $m.Groups[2].Value
        if ($verb -ne 'run' -and $verb -ne 'sleep') {
            try { [void][regex]::new($value) } catch { Fail "$where has a bad regex: $value" }
        }
        switch ($verb) {
            'run' {
                if ($value -match '^/?stop\b') { Fail "$where stops the server; the script does that" }
                if ($null -ne $current -and $current.Expect.Count -eq 0) { Fail "run '$($current.Value)' in $Path has no expect" }
                $current = @{ Verb = 'run'; Value = $value; Expect = New-Object System.Collections.ArrayList; Reject = New-Object System.Collections.ArrayList }
                [void]$steps.Add($current)
            }
            'expect' {
                if ($null -eq $current) { Fail "$where has no run before it" }
                [void]$current.Expect.Add($value)
            }
            'reject' {
                if ($null -eq $current) { Fail "$where has no run before it" }
                [void]$current.Reject.Add($value)
            }
            'sleep' {
                if ($value -notmatch '^\d+$') { Fail "$where needs whole seconds" }
                [void]$steps.Add(@{ Verb = 'sleep'; Value = $value })
            }
            default { [void]$steps.Add(@{ Verb = $verb; Value = $value }) }
        }
    }
    if ($null -ne $current -and $current.Expect.Count -eq 0) { Fail "run '$($current.Value)' in $Path has no expect" }
    if ($steps.Count -eq 0) { Fail "$Path has no steps" }
    return , $steps
}

# ---- Files edited for the run ----------------------------------------------------------------------------------

function Restore-Edited {
    foreach ($file in $Edited) {
        if (Test-Path $file.Backup) { Move-Item -LiteralPath $file.Backup -Destination $file.Path -Force }
    }
    if (Test-Path $BackupDir) { Remove-Item -LiteralPath $BackupDir -Force }
}

function Set-RunFiles([string]$LevelName) {
    New-Item -ItemType Directory -Path $BackupDir | Out-Null
    foreach ($file in $Edited) { Copy-Item -LiteralPath $file.Path -Destination $file.Backup }
    $Script:Touched = $true

    $properties = [ordered]@{
        'level-name'                = $LevelName
        'level-seed'                = 'orevault-restart-check'
        'server-ip'                 = '127.0.0.1'
        'server-port'               = "$GamePort"
        'enable-rcon'               = 'true'
        'rcon.port'                 = "$RconPort"
        'rcon.password'             = $Script:RconPassword
        # An empty server stops ticking after this long, which would stop every check that needs time to pass.
        'pause-when-empty-seconds'  = '0'
    }
    $path = $Edited[0].Path
    $lines = New-Object System.Collections.ArrayList
    foreach ($line in [IO.File]::ReadAllLines($path)) {
        $key = ($line -split '=', 2)[0]
        if (-not $properties.Contains($key)) { [void]$lines.Add($line) }
    }
    foreach ($key in $properties.Keys) { [void]$lines.Add("$key=$($properties[$key])") }
    [IO.File]::WriteAllLines($path, [string[]]$lines)

    $toml = $Edited[1].Path
    $text = [IO.File]::ReadAllText($toml)
    if ($text -notmatch 'enable_debug_commands\s*=') { Fail "$toml has no enable_debug_commands" }
    [IO.File]::WriteAllText($toml, ($text -replace 'enable_debug_commands\s*=\s*\w+', 'enable_debug_commands = true'))
}

# ---- RCON ------------------------------------------------------------------------------------------------------

function Send-Packet([int]$Id, [int]$Type, [string]$Body) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Body)
    $memory = New-Object IO.MemoryStream
    $writer = New-Object IO.BinaryWriter($memory)
    $writer.Write([int]($bytes.Length + 10))
    $writer.Write($Id)
    $writer.Write($Type)
    $writer.Write($bytes)
    $writer.Write([byte]0)
    $writer.Write([byte]0)
    $data = $memory.ToArray()
    $Script:Stream.Write($data, 0, $data.Length)
}

function Read-Exactly([int]$Count) {
    $buffer = New-Object byte[] $Count
    $offset = 0
    while ($offset -lt $Count) {
        $read = $Script:Stream.Read($buffer, $offset, $Count - $offset)
        if ($read -le 0) { throw 'RCON connection closed' }
        $offset += $read
    }
    return , $buffer
}

function Read-Packet {
    $length = [BitConverter]::ToInt32((Read-Exactly 4), 0)
    $payload = Read-Exactly $length
    return @{
        Id   = [BitConverter]::ToInt32($payload, 0)
        Body = [Text.Encoding]::UTF8.GetString($payload, 8, $length - 10)
    }
}

function Connect-Rcon {
    $tcp = New-Object Net.Sockets.TcpClient
    try {
        $tcp.Connect('127.0.0.1', $RconPort)
    } catch {
        $tcp.Close()
        return $false
    }
    $tcp.ReceiveTimeout = 60000
    $tcp.SendTimeout = 60000
    $tcp.NoDelay = $true
    $Script:Tcp = $tcp
    $Script:Stream = $tcp.GetStream()
    Send-Packet 1 3 $Script:RconPassword
    if ((Read-Packet).Id -ne 1) { Fail 'RCON refused the password' }
    return $true
}

function Close-Rcon {
    if ($null -ne $Script:Tcp) { $Script:Tcp.Close() }
    $Script:Tcp = $null
    $Script:Stream = $null
}

# The server's read loop takes one packet per socket read and drops the connection if a read holds anything else
# (RconClient.run), so nothing is sent until the reply to the last packet is in. It also splits a reply at 4096
# characters with no end marker: after a full packet, a type the server does not know gets an answer that comes
# after the rest of the reply.
function Invoke-Rcon([string]$Command) {
    if ([Text.Encoding]::UTF8.GetByteCount($Command) -gt 1440) { Fail "command over the 1460 byte read: $Command" }
    $id = $Script:NextId += 2
    Send-Packet $id 2 $Command
    $packet = Read-Packet
    $reply = $packet.Body
    if ($reply.Length -lt 4096) { return $reply }
    Send-Packet ($id + 1) 200 ''
    while ($true) {
        $packet = Read-Packet
        if ($packet.Id -eq ($id + 1)) { return $reply }
        $reply += $packet.Body
    }
}

# ---- Server lifetime -------------------------------------------------------------------------------------------

function Get-ListenerPids {
    $ports = @($GamePort, $RconPort)
    return @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
            Where-Object { $ports -contains $_.LocalPort } |
            ForEach-Object { $_.OwningProcess } | Sort-Object -Unique)
}

function Get-JavaProcesses {
    return @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine })
}

# Anything holding the run's ports, plus any JVM started since the run began that is this repo's runServer (only
# that run passes --nogui, so a client or a gametest server started meanwhile is spared). The second covers a server that hung before it bound a port.
function Get-ServerPids {
    $pids = @(Get-ListenerPids)
    $pids += @(Get-JavaProcesses | Where-Object {
            $Script:JavaBefore -notcontains $_.ProcessId -and
            $_.CommandLine -notmatch 'GradleDaemon|GradleWorkerMain|gradle-wrapper\.jar' -and
            $_.CommandLine.Contains('--nogui') -and
            $_.CommandLine.Contains((Join-Path $RepoRoot 'build\classes\java\main'))
        } | ForEach-Object { $_.ProcessId })
    return @($pids | Sort-Object -Unique)
}

function Read-ServerLog {
    $path = Join-Path $ServerDir 'logs\latest.log'
    $stream = [IO.File]::Open($path, 'Open', 'Read', 'ReadWrite,Delete')
    try {
        $reader = New-Object IO.StreamReader($stream)
        return $reader.ReadToEnd()
    } finally {
        $stream.Close()
    }
}

function Start-Server([int]$Lifetime) {
    Say "== lifetime ${Lifetime}: starting runServer"
    $outFile = Join-Path $OutDir "gradle-$Lifetime.out"
    $errFile = Join-Path $OutDir "gradle-$Lifetime.err"
    $Script:Gradle = Start-Process -FilePath (Join-Path $RepoRoot 'gradlew.bat') `
        -ArgumentList 'runServer', '--console=plain' -WorkingDirectory $RepoRoot `
        -RedirectStandardOutput $outFile -RedirectStandardError $errFile -NoNewWindow -PassThru
    [void]$Script:Gradle.Handle # ExitCode stays null without this
    $clock = [Diagnostics.Stopwatch]::StartNew()
    while ($clock.Elapsed.TotalSeconds -lt $StartTimeoutSec) {
        if ($Script:Gradle.HasExited) { Fail "runServer exited with $($Script:Gradle.ExitCode) before RCON came up; see $outFile" }
        # RCON opens after "Done", so a connection means the server finished starting.
        if (Connect-Rcon) {
            Say ("   up after {0:N0}s, pid {1}" -f $clock.Elapsed.TotalSeconds, ((Get-ListenerPids) -join ','))
            return
        }
        Start-Sleep -Seconds 2
    }
    Fail "no RCON after $StartTimeoutSec s; see $outFile"
}

function Stop-Server([int]$Lifetime) {
    Say "== lifetime ${Lifetime}: stop"
    try { [void](Invoke-Rcon 'stop') } catch { Say "   connection dropped on stop: $($_.Exception.Message)" }
    Close-Rcon
    if (-not $Script:Gradle.WaitForExit($StopTimeoutSec * 1000)) { Fail "server still running $StopTimeoutSec s after stop" }
    $code = $Script:Gradle.ExitCode
    $Script:Gradle = $null
    $log = Read-ServerLog
    Copy-Item -LiteralPath (Join-Path $ServerDir 'logs\latest.log') -Destination (Join-Path $OutDir "server-$Lifetime.log")
    if ($code -ne 0) { Fail "runServer exited with $code" }
    if ($log -notmatch 'All dimensions are saved') { Fail "lifetime $Lifetime never logged a final save" }
    if ($log -match '/FATAL\]') { Fail "lifetime $Lifetime logged a FATAL line; see server-$Lifetime.log" }
    Say "   exit 0, final save logged"
}

function Invoke-Steps($Steps) {
    foreach ($step in $Steps) {
        switch ($step.Verb) {
            'run' {
                Say "> $($step.Value)"
                $reply = Invoke-Rcon $step.Value
                Say "< $reply"
                # What Brigadier answers to a command it could not parse, whichever list forgot to reject it.
                if ($reply -cmatch 'Unknown or incomplete command|Incorrect argument|<--\[HERE\]') { Fail "not a command: $($step.Value)" }
                foreach ($regex in $step.Expect) {
                    if ($reply -cnotmatch $regex) { Fail "expected /$regex/ from '$($step.Value)', got: $reply" }
                }
                foreach ($regex in $step.Reject) {
                    if ($reply -cmatch $regex) { Fail "rejected /$regex/ from '$($step.Value)', got: $reply" }
                }
            }
            'log' {
                $clock = [Diagnostics.Stopwatch]::StartNew()
                while ((Read-ServerLog) -cnotmatch $step.Value) {
                    if ($clock.Elapsed.TotalSeconds -ge $LogTimeoutSec) { Fail "log never matched /$($step.Value)/ in $LogTimeoutSec s" }
                    Start-Sleep -Milliseconds 500
                }
                Say "  log matched /$($step.Value)/"
            }
            'nolog' {
                if ((Read-ServerLog) -cmatch $step.Value) { Fail "log matched /$($step.Value)/" }
                Say "  log free of /$($step.Value)/"
            }
            'sleep' {
                Say "  sleep $($step.Value)s"
                Start-Sleep -Seconds ([int]$step.Value)
            }
        }
        $Script:Passed++
    }
}

function Invoke-Between([string]$WorldDir) {
    Say "== server down: $Between"
    $hook = Start-Process -FilePath 'powershell.exe' -NoNewWindow -PassThru -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$Between`"", '-WorldDir', "`"$WorldDir`""
    [void]$hook.Handle
    $hook.WaitForExit()
    if ($hook.ExitCode -ne 0) { Fail "$Between exited with $($hook.ExitCode)" }
}

# Killing the Gradle client does not stop the server JVM its daemon forked, and a failure during start-up can come
# before that JVM exists. So this keeps killing servers as they appear until the client has exited (it does once its
# server dies), and only kills the client after that. The edited files are put back only after this returns, so a
# late server never starts on the dev world.
function Stop-Leftovers {
    $clock = [Diagnostics.Stopwatch]::StartNew()
    while ($clock.Elapsed.TotalSeconds -lt $StopTimeoutSec) {
        foreach ($id in @(Get-ServerPids)) {
            Say "killing leftover server pid $id"
            Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
        }
        if ($null -eq $Script:Gradle -or $Script:Gradle.HasExited) { break }
        Start-Sleep -Seconds 1
    }
    if ($null -ne $Script:Gradle -and -not $Script:Gradle.HasExited) {
        Say "killing the Gradle client, pid $($Script:Gradle.Id), which never started a server"
        Stop-Process -Id $Script:Gradle.Id -Force -ErrorAction SilentlyContinue
    }
}

# ---- Main ------------------------------------------------------------------------------------------------------

$failure = $null
try {
    if (-not (Test-Path (Join-Path $ServerDir 'eula.txt'))) { Fail 'run/server/eula.txt is missing; it needs eula=true' }
    if (Test-Path $BackupDir) {
        [Console]::Out.WriteLine('Restoring files a killed run left edited.')
        Restore-Edited
    }
    $busy = @(Get-ListenerPids)
    if ($busy.Count -gt 0) { Fail "ports $GamePort or $RconPort are in use by pid $($busy -join ',')" }
    $serverPort = ([IO.File]::ReadAllLines($Edited[0].Path) | Where-Object { $_ -like 'server-port=*' }) -replace 'server-port=', ''
    if ($serverPort -and (Get-NetTCPConnection -State Listen -LocalPort $serverPort -ErrorAction SilentlyContinue)) {
        Fail "a dev server is listening on $serverPort; it shares run/server with this script"
    }

    $firstSteps = Read-List (Resolve-Path $First).Path
    $secondSteps = Read-List (Resolve-Path $Second).Path
    if ($Between) { $Between = (Resolve-Path $Between).Path }
    if (-not $World) { $World = "restart-check-$Stamp" }
    $worldDir = Join-Path $ServerDir $World

    New-Item -ItemType Directory -Path $OutDir | Out-Null
    $Script:JavaBefore = @(Get-JavaProcesses | ForEach-Object { $_.ProcessId })
    $Script:RconPassword = [guid]::NewGuid().ToString('N')
    Say "restart-check $Stamp, world run/server/$World, HEAD $(& git -C $RepoRoot rev-parse --short HEAD)"
    $crashBefore = @(Get-ChildItem (Join-Path $ServerDir 'crash-reports') -ErrorAction SilentlyContinue).Count
    Set-RunFiles $World

    Start-Server 1
    Invoke-Steps $firstSteps
    Stop-Server 1
    if ($Between) { Invoke-Between $worldDir }
    Start-Server 2
    Invoke-Steps $secondSteps
    Stop-Server 2

    $crashAfter = @(Get-ChildItem (Join-Path $ServerDir 'crash-reports') -ErrorAction SilentlyContinue).Count
    if ($crashAfter -ne $crashBefore) { Fail 'a new crash report appeared' }
} catch {
    $failure = $_.Exception.Message
} finally {
    Close-Rcon
    if ($Script:Touched) {
        Stop-Leftovers
        Start-Sleep -Seconds 1
        Restore-Edited
        $left = @(Get-ServerPids)
        if ($left.Count -gt 0) {
            $text = "server pid $($left -join ',') is still alive"
            if ($null -eq $failure) { $failure = "FAIL: $text" } else { $failure += "; $text" }
        }
    }
}

if ($null -ne $failure) {
    Say $failure
    Say "RESTART CHECK FAILED after $Script:Passed steps passed. Logs: $OutDir"
    exit 1
}
Say "RESTART CHECK PASSED: $Script:Passed steps. Logs: $OutDir"
exit 0
