param(
    [string]$ServerHost,
    [string]$UserName = 'tmx_tunnel',
    [string]$IdentityFile = (Join-Path $env:USERPROFILE '.ssh\tmx_db_tunnel'),
    [ValidateRange(1, 65535)][int]$SshPort = 22,
    [ValidateRange(1, 65535)][int]$MySqlPort = 13306,
    [ValidateRange(1, 65535)][int]$RedisPort = 16379
)

$ErrorActionPreference = 'Stop'

# 连接地址从运行环境读取；专用私钥留在用户目录，不能提交到项目。
if (-not $ServerHost) {
    $ServerHost = $env:TMX_SSH_HOST
    if (-not $ServerHost) {
        $ServerHost = [Environment]::GetEnvironmentVariable('TMX_SSH_HOST', 'User')
    }
}
if (-not $ServerHost -or $ServerHost -notmatch '^[A-Za-z0-9][A-Za-z0-9.-]*$') {
    throw '请通过 -ServerHost 或 TMX_SSH_HOST 指定服务器 IP 或域名。'
}
if ($UserName -notmatch '^[A-Za-z0-9_-]+$') { throw 'SSH 用户名格式不正确。' }
if ($IdentityFile -match '["\r\n]' -or -not (Test-Path -LiteralPath $IdentityFile -PathType Leaf)) {
    throw "未找到专用私钥：$IdentityFile。请先配置服务器隧道账号和密钥。"
}
if ($MySqlPort -eq $RedisPort) { throw 'MySQL 与 Redis 的本机端口不能相同。' }

$ssh = (Get-Command ssh.exe -ErrorAction Stop).Source
$mysqlForward = "127.0.0.1:${MySqlPort}:127.0.0.1:3306"
$redisForward = "127.0.0.1:${RedisPort}:127.0.0.1:6379"
$target = "${UserName}@${ServerHost}"
$listeners = @(Get-NetTCPConnection -State Listen -LocalPort $MySqlPort, $RedisPort -ErrorAction SilentlyContinue)
if ($listeners.Count) {
    # 仅复用目标地址与两个转发端口均匹配的 SSH 进程，避免误判其他服务。
    $owners = @($listeners.OwningProcess | Select-Object -Unique)
    if ($listeners.Count -eq 2 -and $owners.Count -eq 1) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($owners[0])"
        if ($process.Name -eq 'ssh.exe' -and $process.CommandLine -and
            $process.CommandLine.Contains($target) -and
            $process.CommandLine.Contains($mysqlForward) -and
            $process.CommandLine.Contains($redisForward)) {
            Write-Host "隧道已运行，PID=$($owners[0])；MySQL=$MySqlPort，Redis=$RedisPort。"
            return
        }
    }
    throw "端口 $MySqlPort 或 $RedisPort 被其他进程占用，请先检查占用进程。"
}

# 只监听本机回环地址；断网导致退出后，重新运行脚本即可恢复。
$sshArgs = @(
    '-N', '-n', '-i', $IdentityFile, '-p', $SshPort.ToString(),
    '-o', 'BatchMode=yes', '-o', 'IdentitiesOnly=yes',
    '-o', 'StrictHostKeyChecking=yes', '-o', 'ExitOnForwardFailure=yes',
    '-o', 'ServerAliveInterval=30', '-o', 'ServerAliveCountMax=3',
    '-L', $mysqlForward, '-L', $redisForward, $target
)
Write-Host "MySQL：127.0.0.1:$MySqlPort；Redis：127.0.0.1:$RedisPort。"
Write-Host '请保持本窗口运行；按 Ctrl+C 关闭隧道。'
& $ssh @sshArgs
if ($LASTEXITCODE -ne 0) { throw "SSH 隧道退出，返回码：$LASTEXITCODE。" }
