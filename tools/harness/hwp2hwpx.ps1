<#
  HWP -> HWPX 일괄 변환 (한글 COM 자동화, HWPFrame.HwpObject)

  - 원본은 건드리지 않는다. 출력: <OutDir>\<원본 상대경로>.hwpx
  - 이미 출력 파일이 있으면 건너뛴다 (중단 후 다시 실행하면 이어서 진행)
  - 한글 인스턴스 하나로 순서대로 처리한다. 보안 승인 모듈이 등록되어 있지 않으면
    처음 파일을 열 때 한 번 보안 창이 뜨고, "모두 허용"을 누르면 그 인스턴스에서는 다시 묻지 않는다.
  - 문서 하나가 TimeoutSec 를 넘기면 이 스크립트가 띄운 한글 프로세스만 종료하고, 다음 파일부터 새 인스턴스로 계속한다
    (새 인스턴스에서는 보안 창이 다시 뜰 수 있다)
  - 결과: <OutDir>\results.tsv (file, status, ms, outKB, error)
  - 암호/DRM 문서는 열 때 입력 창이 뜨므로 건너뛴다 (SKIP_PASSWORD). 목록은 HwpFlagScan 으로 미리 만든다:
      java -cp tools/harness/out/classes harness.HwpFlagScan <Root> tools/harness/work/hwp2hwpx-flags.tsv

  사용 예:
    powershell -ExecutionPolicy Bypass -File tools\harness\hwp2hwpx.ps1
    powershell -ExecutionPolicy Bypass -File tools\harness\hwp2hwpx.ps1 -Root D:\docs -Limit 20
#>
param(
    [string]$Root = "D:\eclipse-workspace\HWP",
    [string]$OutDir = (Join-Path $PSScriptRoot "work\hwp2hwpx"),
    [int]$TimeoutSec = 120,
    [int]$Limit = 0,
    [string]$FlagList = (Join-Path $PSScriptRoot "work\hwp2hwpx-flags.tsv")
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path $Root).Path.TrimEnd('\')
New-Item -ItemType Directory -Force $OutDir | Out-Null
$OutDir = (Resolve-Path $OutDir).Path.TrimEnd('\')
$results = Join-Path $OutDir "results.tsv"
$marker  = Join-Path $OutDir "current.txt"
$infoFile = Join-Path $OutDir "hwpinfo.txt"
$utf8 = New-Object System.Text.UTF8Encoding($false)

# 암호/DRM 문서 목록 (skip=1)
$skipSet = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
if (Test-Path -LiteralPath $FlagList) {
    Import-Csv -LiteralPath $FlagList -Delimiter "`t" -Encoding UTF8 | Where-Object { $_.skip -eq "1" } | ForEach-Object { [void]$skipSet.Add($_.file) }
} else {
    Write-Host "암호 문서 목록($FlagList)이 없습니다. 암호 문서를 만나면 입력 창이 뜹니다 (취소하면 OPEN_FAIL 로 기록)." -ForegroundColor Yellow
}

# 처리 대상: 출력이 아직 없는 .hwp
$todo = New-Object System.Collections.Generic.List[string]
$skipped = New-Object System.Collections.Generic.List[string]
Get-ChildItem -Path $Root -Recurse -File -Filter "*.hwp" | Where-Object { $_.Extension -ieq ".hwp" } | Sort-Object FullName | ForEach-Object {
    $rel = $_.FullName.Substring($Root.Length).TrimStart('\')
    $dst = Join-Path $OutDir ([System.IO.Path]::ChangeExtension($rel, ".hwpx"))
    if (-not (Test-Path -LiteralPath $dst)) {
        if ($skipSet.Contains($rel)) { $skipped.Add($rel) } else { $todo.Add($rel) }
    }
}
if ($Limit -gt 0 -and $todo.Count -gt $Limit) { $todo = $todo.GetRange(0, $Limit) }
if (-not (Test-Path -LiteralPath $results)) {
    [System.IO.File]::WriteAllText($results, "file`tstatus`tms`toutKB`terror`r`n", $utf8)
}
# 건너뛴 암호 문서는 결과에 한 번만 기록 (이전 실행에서 기록된 파일은 다시 쓰지 않음)
$recorded = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
Import-Csv -LiteralPath $results -Delimiter "`t" -Encoding UTF8 | Where-Object { $_.status -eq "SKIP_PASSWORD" } | ForEach-Object { [void]$recorded.Add($_.file) }
foreach ($rel in $skipped) {
    if (-not $recorded.Contains($rel)) {
        [System.IO.File]::AppendAllText($results, "$rel`tSKIP_PASSWORD`t0`t0`t`r`n", $utf8)
    }
}
Write-Host ("대상 {0}개 (이미 변환된 파일 제외, 암호 문서 {1}개 건너뜀), 출력: {2}" -f $todo.Count, $skipped.Count, $OutDir)
if ($todo.Count -eq 0) { exit 0 }

# 작업 프로세스(Job) 안에서 한글 인스턴스 하나로 목록을 차례로 처리한다
$worker = {
    param($root, $outDir, $files, $results, $marker, $infoFile)
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    $hwp = New-Object -ComObject HWPFrame.HwpObject
    $registered = $false
    try { $registered = [bool]$hwp.RegisterModule("FilePathCheckDLL", "FilePathCheckerModule") } catch { }
    try { $hwp.XHwpWindows.Item(0).Visible = $false } catch { }
    [System.IO.File]::WriteAllText($infoFile, "init`t" + $hwp.Version + "`t" + $registered, $utf8)
    for ($i = 0; $i -lt $files.Count; $i++) {
        $rel = $files[$i]
        $src = Join-Path $root $rel
        $dst = Join-Path $outDir ([System.IO.Path]::ChangeExtension($rel, ".hwpx"))
        New-Item -ItemType Directory -Force (Split-Path $dst) | Out-Null
        [System.IO.File]::WriteAllText($marker, "$i`t" + [DateTime]::UtcNow.Ticks, $utf8)
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $status = "OK"; $err = ""
        try {
            if (-not $hwp.Open($src, "HWP", "forceopen:true")) { $status = "OPEN_FAIL" }
            elseif (-not $hwp.SaveAs($dst, "HWPX", "")) { $status = "SAVE_FAIL" }
            $hwp.Clear(1)    # 문서 닫기 (저장 안 함)
        } catch {
            $status = "ERROR"; $err = ($_.Exception.Message -replace "[`t`r`n]", " ")
        }
        $kb = 0
        if (Test-Path -LiteralPath $dst) { $kb = [int]((Get-Item -LiteralPath $dst).Length / 1024) }
        [System.IO.File]::AppendAllText($results, "$rel`t$status`t$($sw.ElapsedMilliseconds)`t$kb`t$err`r`n", $utf8)
    }
    [System.IO.File]::WriteAllText($marker, "done", $utf8)
    try { $hwp.Quit() } catch { }
}

$index = 0
$informed = $false
while ($index -lt $todo.Count) {
    $remaining = $todo.GetRange($index, $todo.Count - $index)
    $before = @(Get-Process hwp -ErrorAction SilentlyContinue | ForEach-Object { $_.Id })
    Remove-Item -LiteralPath $marker -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $infoFile -ErrorAction SilentlyContinue
    $job = Start-Job -ScriptBlock $worker -ArgumentList $Root, $OutDir, $remaining.ToArray(), $results, $marker, $infoFile
    $timedOutAt = -1
    $lastShown = -1
    while ($true) {
        Start-Sleep -Milliseconds 1000
        $m = $null
        if (Test-Path -LiteralPath $marker) { $m = [System.IO.File]::ReadAllText($marker, $utf8) }
        if (-not $informed -and (Test-Path -LiteralPath $infoFile)) {
            $parts = [System.IO.File]::ReadAllText($infoFile, $utf8).Split("`t")
            Write-Host ("한글 버전: {0}" -f $parts[1])
            if ($parts[2] -ne "True") {
                Write-Host "보안 승인 모듈이 없습니다. 첫 파일을 열 때 뜨는 보안 창에서 '모두 허용'을 눌러 주세요." -ForegroundColor Yellow
            }
            $informed = $true
        }
        if ($job.State -ne "Running") { break }
        if ($m -ne $null -and $m -match "^(\d+)`t(\d+)$") {
            $cur = [int]$Matches[1]
            $elapsed = ([DateTime]::UtcNow.Ticks - [long]$Matches[2]) / 1e7
            if ($cur -ne $lastShown) {
                Write-Host ("[{0}/{1}] {2}" -f ($index + $cur + 1), $todo.Count, $remaining[$cur])
                $lastShown = $cur
            }
            # 첫 파일은 보안 창 응답을 기다리므로 제한 시간을 넉넉히 준다
            $limitSec = $TimeoutSec
            if ($cur -eq 0) { $limitSec = [Math]::Max($TimeoutSec, 600) }
            if ($elapsed -gt $limitSec) { $timedOutAt = $cur; break }
        }
    }

    if ($timedOutAt -ge 0) {
        # 이 스크립트가 띄운 한글 프로세스만 종료
        Stop-Job $job -ErrorAction SilentlyContinue
        Get-Process hwp -ErrorAction SilentlyContinue | Where-Object { $before -notcontains $_.Id } | ForEach-Object {
            Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
        }
        $rel = $remaining[$timedOutAt]
        [System.IO.File]::AppendAllText($results, "$rel`tTIMEOUT`t$($TimeoutSec * 1000)`t0`t`r`n", $utf8)
        Write-Host ("  시간 초과: {0} (한글을 다시 시작합니다)" -f $rel) -ForegroundColor Yellow
        $index += $timedOutAt + 1
        $informed = $false
    } else {
        Receive-Job $job -ErrorAction SilentlyContinue | Out-Null
        $index = $todo.Count
    }
    Remove-Job $job -Force -ErrorAction SilentlyContinue
}
Remove-Item -LiteralPath $marker -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $infoFile -ErrorAction SilentlyContinue

# 요약
$rows = Import-Csv -LiteralPath $results -Delimiter "`t" -Encoding UTF8
$rows | Group-Object status | Sort-Object Count -Descending | ForEach-Object { Write-Host ("{0,-10} {1}" -f $_.Name, $_.Count) }
Write-Host "결과: $results"
