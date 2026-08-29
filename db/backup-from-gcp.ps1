<#
 ══════════════════════════════════════════════════════════════════════════
  把雲端的資料與音檔整包搬回本機
 ══════════════════════════════════════════════════════════════════════════

  用途：GCP 的 $300 試用額度到期（2026-11-14）之後專案會關閉。
  這支腳本負責在那之前，把 Cloud SQL 的資料和 Cloud Storage 的音檔
  抓回本機，讓已經付過錢的翻譯與語音不必再呼叫一次 API。

 ── 為什麼搬回來就能直接用 ─────────────────────────────────────────────

  audio_asset.file_path 存的是 "th/a1b2c3d4e5f6.wav" 這種相對路徑，
  GoogleCloudAudioStorage 與 LocalDiskAudioStorage 產生路徑的格式
  刻意做成一模一樣。所以雲端的資料列搬回本機之後，
  ★ 一行程式都不用改就能對到 audio/th、audio/zh 底下的檔案。

 ── 流程：你在 PowerShell 打下這行之後發生什麼事 ───────────────────────

  第 1 步｜你執行

      .\db\backup-from-gcp.ps1

    預設是「只抓回來，不動本機資料庫」—— 這樣可以每個月安心重跑一次。

  第 2 步｜Test-Prerequisite 檢查 gcloud 在不在、有沒有登入

    跑 gcloud config get-value account，拿到 black58gigi@gmail.com 這種
    字串就算過關；拿到空的或 (unset) 就直接停下來叫你先 gcloud auth login。

  第 3 步｜Sync-Audio 把 bucket 的音檔抓下來

      gcloud storage rsync -r gs://thailan-audio-awei/th <專案>\audio\th
      gcloud storage rsync -r gs://thailan-audio-awei/zh <專案>\audio\zh

    ★ 這裡刻意「一個語言資料夾抓一次」，而不是整個 bucket 一次抓完。
      因為第 4 步會把資料庫的 dump 也丟進同一個 bucket 的 backup/ 底下，
      整包 rsync 會連 dump 一起拉進 audio\backup\ —— 那是垃圾。

    ★ 沒有加 --delete-unmatched-destination-objects：
      雲端沒有的檔案，本機也不會被刪。rsync 在這裡只做「補齊」。

  第 4 步｜Export-CloudDatabase 把資料庫倒出來

      gcloud sql export sql thailan-db `
          gs://thailan-audio-awei/backup/dump-20260828-143000.sql `
          --database=language_project

    Cloud SQL 會產生一份 pg_dump 的純文字 SQL 丟進 bucket，內容長這樣：

        CREATE TABLE public.audio_asset (...);
        COPY public.audio_asset (id, language, source_text, file_path, ...) FROM stdin;
        1	TH	เหล้า	th/a1b2c3d4e5f6.wav	2026-08-15 10:22:31
        2	ZH	酒	zh/9f8e7d6c5b4a.wav	2026-08-15 10:22:33
        \.

    ★ 為什麼繞道 bucket 而不是直接連資料庫倒：Cloud SQL 不對外開放
      （見「筆記-部署與更新.md」第十二節），從本機直連要先開公開 IP
      和防火牆白名單。走 bucket 完全不用碰網路設定。

  第 5 步｜下載到 db\backup\dump-20260828-143000.sql

    db\backup\ 已列入 .gitignore，dump 裡有全部的查詢內容，不進版控。

  第 6 步｜Get-DumpRowCount 讀那份 dump，數出每張表有幾筆

    逐行掃過去，遇到 COPY xxx ... FROM stdin; 就開始數，遇到 \. 就結束。
    得到的是一個對照表：

        translation_query   184
        translation_segment 921
        vocabulary          467
        audio_asset         1352
        api_usage_log       203

    這就是「雲端有多少資料」的答案，印出來給你看。

  ── 到這裡預設模式就結束了。以下只有加 -Restore 才會執行 ──

  第 7 步｜Backup-LocalDatabase 先把本機現有的資料倒出來保命

      db\backup\local-before-restore-20260828-143000.sql

    ★ 這一步不能省。第 8 步會把本機的表整個 DROP 掉，
      其中包含 api_usage_log —— 那是「本機到目前為止花了多少錢」的
      唯一紀錄，reset-postgres.sql 甚至刻意不刪它。

  第 8 步｜Clear-LocalTable 依外鍵順序把五張表刪掉

      DROP TABLE IF EXISTS translation_segment CASCADE;   ← 子表先走
      DROP TABLE IF EXISTS translation_query   CASCADE;
      DROP TABLE IF EXISTS vocabulary          CASCADE;
      DROP TABLE IF EXISTS audio_asset         CASCADE;
      DROP TABLE IF EXISTS api_usage_log       CASCADE;

    ★ 為什麼一定要先刪：dump 裡是 CREATE TABLE（沒有 IF NOT EXISTS），
      表已經存在的話那幾行會報 relation already exists，
      而後面的 COPY 也就跟著不會執行 —— 症狀是「跑完好像沒事，
      但資料一筆都沒進來」。

  第 9 步｜Restore-Dump 灌進本機容器

      docker cp <dump> language-project-postgres:/tmp/restore.sql
      docker exec language-project-postgres psql -U postgres -d language_project -f /tmp/restore.sql

    ★ 這裡刻意「不」開 ON_ERROR_STOP。Cloud SQL 的 dump 會夾帶一些
      本機沒有的角色與權限設定（cloudsqlsuperuser 之類），開了會在
      那些無害的行上整份中止。改成把 ERROR 行抓出來印給你看，
      真正判斷成功與否的是第 10 步的筆數比對，不是離開碼。

  第 10 步｜Test-Restore 驗證，這才是真正的驗收

    ① 五張表逐一比對：dump 說幾筆、本機現在幾筆，數字要一樣
    ② 把 audio_asset 的 file_path 全部撈出來，逐一 Test-Path，
       數出有幾筆在本機找不到檔案

    ★ ② 才是「搬完之後真的能離線用嗎」的答案。筆數對得上但檔案沒下來，
      程式的行為是「發現有紀錄卻讀不到檔案，於是重新合成一次」——
      畫面完全正常，錢卻又付了一次。

 ── 用法 ───────────────────────────────────────────────────────────────

    .\db\backup-from-gcp.ps1                  只抓回本機存著（安全，可重跑）
    .\db\backup-from-gcp.ps1 -SkipAudio       只抓資料庫，跳過音檔（快）
    .\db\backup-from-gcp.ps1 -Restore         抓完並灌進本機資料庫（★ 會覆蓋本機）
    .\db\backup-from-gcp.ps1 -Restore -DumpFile db\backup\dump-20260828-143000.sql
                                              用已經抓好的 dump 灌，不重新匯出

 ── 只需做一次的前置 ───────────────────────────────────────────────────

  第一次跑如果卡在匯出那步，訊息大概是「does not have storage.objects.create
  access」。那是 Cloud SQL 的服務帳號沒有寫入 bucket 的權限，腳本會把
  該執行的那一行印出來，照著貼一次就好。
 ══════════════════════════════════════════════════════════════════════════
#>

param(
    # 加了才會真的把資料灌進本機資料庫。★ 這是破壞性操作，會覆蓋本機現有資料。
    [switch]$Restore,

    # 跳過音檔同步。音檔是整個流程最慢的一段，只想更新資料庫時用。
    [switch]$SkipAudio,

    # 指定既有的 dump 檔，跳過「重新匯出並下載」。
    [string]$DumpFile
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# ── 這個專案的雲端資源名稱（出處：筆記-部署與更新.md 第二節）──────────────
$ProjectId     = 'thai-language-505602'
$SqlInstance   = 'thailan-db'
$DatabaseName  = 'language_project'
$Bucket        = 'thailan-audio-awei'
$ContainerName = 'language-project-postgres'
$DbUser        = 'postgres'

# 音檔的語言子資料夾。與 SpeechLanguageEnum 的 folderName 一致，
# 那邊多一個語言，這裡也要跟著加。
$AudioFolders = @('th', 'zh')

# 依外鍵相依順序排列：子表在前，父表在後。刪除時必須照這個順序。
$TableNames = @('translation_segment', 'translation_query', 'vocabulary', 'audio_asset', 'api_usage_log')

$ProjectRoot = Split-Path $PSScriptRoot -Parent
$BackupDir   = Join-Path $PSScriptRoot 'backup'
$AudioRoot   = Join-Path $ProjectRoot 'audio'
$Stamp       = Get-Date -Format 'yyyyMMdd-HHmmss'

function Write-Step {
    param([string]$Message)
    Write-Host ''
    Write-Host "── $Message " -ForegroundColor Cyan
}

function Write-Ok {
    param([string]$Message)
    Write-Host "   ✓ $Message" -ForegroundColor Green
}

function Write-Warn {
    param([string]$Message)
    Write-Host "   ! $Message" -ForegroundColor Yellow
}

<#
 檢查 gcloud 裝了沒、登入了沒。
 沒過關就直接停 —— 讓它在第一步失敗，比跑到一半才炸掉好查。
#>
function Test-Prerequisite {
    param([bool]$NeedDocker)

    if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
        throw ' 找不到 gcloud。開一個新的 PowerShell 視窗再試，或確認 Google Cloud CLI 已安裝。'
    }

    $account = gcloud config get-value account 2>$null
    if ([string]::IsNullOrWhiteSpace($account) -or $account -eq '(unset)') {
        throw ' gcloud 尚未登入。請先執行：gcloud auth login'
    }
    Write-Ok "gcloud 身分：$account"

    if ($NeedDocker) {
        if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
            throw ' 找不到 docker，但 -Restore 需要它把資料灌進本機容器。'
        }

        $running = docker ps --filter "name=$ContainerName" --format '{{.Names}}'
        if ($running -ne $ContainerName) {
            throw " 容器 $ContainerName 沒有在跑。請先在專案根目錄執行：docker compose up -d"
        }
        Write-Ok "本機資料庫容器：$ContainerName（執行中）"
    }
}

<#
 把 bucket 的音檔補齊到本機 audio\th 與 audio\zh。
 只補不刪，所以可以隨時重跑。
#>
function Sync-Audio {
    foreach ($folder in $AudioFolders) {
        $target = Join-Path $AudioRoot $folder
        if (-not (Test-Path $target)) {
            New-Item -ItemType Directory -Path $target -Force | Out-Null
        }

        Write-Host "   同步 gs://$Bucket/$folder → $target"
        gcloud storage rsync -r "gs://$Bucket/$folder" $target
        if ($LASTEXITCODE -ne 0) {
            throw " 音檔同步失敗（$folder）。"
        }

        $count = (Get-ChildItem $target -File -ErrorAction SilentlyContinue | Measure-Object).Count
        Write-Ok "audio\$folder 現有 $count 個檔案"
    }
}

<#
 把 Cloud SQL 倒成一份 SQL 丟進 bucket，再抓回 db\backup\。
 回傳本機那份 dump 的完整路徑。
#>
function Export-CloudDatabase {
    if (-not (Test-Path $BackupDir)) {
        New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    }

    $fileName  = "dump-$Stamp.sql"
    $gcsUri    = "gs://$Bucket/backup/$fileName"
    $localPath = Join-Path $BackupDir $fileName

    Write-Host "   匯出 Cloud SQL → $gcsUri"
    Write-Host '   （資料量大時這步要等一兩分鐘，沒有進度條是正常的）'

    gcloud sql export sql $SqlInstance $gcsUri `
        --database=$DatabaseName `
        --project=$ProjectId

    if ($LASTEXITCODE -ne 0) {
        # 最常見的失敗就是權限。把該貼的那行直接印出來，省得去翻文件。
        $sqlServiceAccount = gcloud sql instances describe $SqlInstance `
            --project=$ProjectId --format='value(serviceAccountEmailAddress)' 2>$null

        Write-Host ''
        Write-Warn '匯出失敗。如果訊息裡有 storage.objects.create，是 Cloud SQL 的服務帳號'
        Write-Warn '沒有寫入 bucket 的權限。執行下面這一行授權（只需做一次）：'
        Write-Host ''
        Write-Host "     gcloud storage buckets add-iam-policy-binding gs://$Bucket ``" -ForegroundColor White
        Write-Host "         --member=serviceAccount:$sqlServiceAccount ``" -ForegroundColor White
        Write-Host '         --role=roles/storage.objectAdmin' -ForegroundColor White
        Write-Host ''
        throw ' Cloud SQL 匯出失敗。'
    }

    Write-Host "   下載 → $localPath"
    gcloud storage cp $gcsUri $localPath
    if ($LASTEXITCODE -ne 0) {
        throw ' dump 下載失敗。'
    }

    $sizeMb = [math]::Round((Get-Item $localPath).Length / 1MB, 2)
    Write-Ok "已存檔 $fileName（$sizeMb MB）"

    return $localPath
}

<#
 讀 pg_dump 的純文字 dump，數出每張表帶了幾筆資料。

 dump 裡的格式固定是這樣，中間夾的每一行就是一筆：
     COPY public.audio_asset (id, language, ...) FROM stdin;
     1	TH	เหล้า	th/a1b2c3d4e5f6.wav
     \.
 用 ReadLines 逐行串流，dump 幾百 MB 也不會把記憶體吃光。
#>
function Get-DumpRowCount {
    param([string]$Path)

    $counts  = @{}
    $current = $null
    $rows    = 0

    foreach ($line in [System.IO.File]::ReadLines($Path)) {
        if ($null -ne $current) {
            if ($line -eq '\.') {
                $counts[$current] = $rows
                $current = $null
                $rows    = 0
            }
            else {
                $rows++
            }
        }
        elseif ($line -match '^COPY (?:public\.)?"?(\w+)"?\s*\(.*\) FROM stdin;') {
            $current = $Matches[1]
            $rows    = 0
        }
    }

    return $counts
}

<#
 對本機容器下一句 SQL，把結果當純文字拿回來。
 -t 去掉標題列、-A 去掉對齊用的空白，這樣輸出可以直接當值用。
#>
function Invoke-LocalQuery {
    param([string]$Sql)

    $result = docker exec $ContainerName psql -U $DbUser -d $DatabaseName -t -A -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw " 本機查詢失敗：$Sql"
    }
    return $result
}

<#
 同上，但用在「答案只有一個數字」的場合，直接回傳整數。
 psql 就算只有一列也可能夾一行空白，所以取最後一個非空白的值。
#>
function Invoke-LocalCount {
    param([string]$Sql)

    $rows = @(Invoke-LocalQuery $Sql | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($rows.Count -eq 0) {
        return 0
    }
    return [int]($rows[-1].ToString().Trim())
}

<#
 灌資料之前先把本機現有的倒出來。
 ★ 這一步是保命用的：下一步會連 api_usage_log 一起刪掉，
   而那是本機唯一的費用紀錄。
#>
function Backup-LocalDatabase {
    $localBackup = Join-Path $BackupDir "local-before-restore-$Stamp.sql"

    Write-Host "   先備份本機現有資料 → $localBackup"

    # ★ 刻意讓 pg_dump 直接寫成容器裡的檔案，再整個 docker cp 出來，
    #   而不是用 PowerShell 的管線接它的輸出。
    #   管線會把位元組轉成字串再轉回去，泰文在這一來一回可能變成亂碼 ——
    #   而備份檔壞掉這件事，是你需要它的時候才會發現的。
    docker exec $ContainerName pg_dump -U $DbUser -d $DatabaseName -f /tmp/local-backup.sql
    if ($LASTEXITCODE -ne 0) {
        throw ' 本機備份失敗，為安全起見中止，不會動到你的資料。'
    }

    docker cp "${ContainerName}:/tmp/local-backup.sql" $localBackup
    if ($LASTEXITCODE -ne 0) {
        throw ' 本機備份無法複製出容器，為安全起見中止，不會動到你的資料。'
    }
    docker exec $ContainerName rm -f /tmp/local-backup.sql | Out-Null

    if (-not (Test-Path $localBackup)) {
        throw ' 本機備份失敗，為安全起見中止，不會動到你的資料。'
    }

    $sizeKb = [math]::Round((Get-Item $localBackup).Length / 1KB, 1)
    Write-Ok "本機備份完成（$sizeKb KB）。出事的話用這份還原。"
}

<#
 依外鍵順序把五張表刪掉，讓 dump 裡的 CREATE TABLE 有乾淨的地方可建。
#>
function Clear-LocalTable {
    $statements = ($TableNames | ForEach-Object { "DROP TABLE IF EXISTS $_ CASCADE;" }) -join ' '

    docker exec $ContainerName psql -U $DbUser -d $DatabaseName -c $statements | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw ' 清空本機資料表失敗。'
    }
    Write-Ok '本機資料表已清空'
}

<#
 把 dump 灌進容器。刻意不開 ON_ERROR_STOP，理由見檔頭第 9 步。
#>
function Restore-Dump {
    param([string]$Path)

    docker cp $Path "${ContainerName}:/tmp/restore.sql"
    if ($LASTEXITCODE -ne 0) {
        throw ' 無法把 dump 複製進容器。'
    }

    $output = docker exec $ContainerName psql -U $DbUser -d $DatabaseName -f /tmp/restore.sql
    $errors = $output | Select-String -Pattern 'ERROR' -SimpleMatch

    if ($errors) {
        Write-Warn "psql 過程中出現 $($errors.Count) 行 ERROR，內容如下（權限／角色類的通常無害）："
        $errors | Select-Object -First 10 | ForEach-Object { Write-Host "     $_" -ForegroundColor DarkYellow }
        Write-Warn '真正判斷成功與否請看下面的筆數比對。'
    }

    docker exec $ContainerName rm -f /tmp/restore.sql | Out-Null
}

<#
 驗收。回傳 $true 代表全部對得上。

 ① 每張表：dump 說幾筆、本機現在幾筆
 ② audio_asset 的 file_path 有幾筆在本機找不到對應檔案
#>
function Test-Restore {
    param([hashtable]$DumpCounts)

    $allMatch = $true

    Write-Host ''
    Write-Host '   資料表         dump    本機    結果'
    Write-Host '   ─────────────────────────────────────'

    foreach ($table in $TableNames) {
        $expected = 0
        if ($DumpCounts.ContainsKey($table)) { $expected = $DumpCounts[$table] }

        $actual = Invoke-LocalCount "SELECT count(*) FROM $table"

        if ($expected -eq $actual) {
            $mark  = '✓'
            $color = 'Green'
        }
        else {
            $mark     = '✗ 不符'
            $color    = 'Red'
            $allMatch = $false
        }

        Write-Host ("   {0,-16}{1,6}{2,8}    {3}" -f $table, $expected, $actual, $mark) -ForegroundColor $color
    }

    # ★ 這一項才是「離線之後真的還有聲音嗎」的答案。
    Write-Host ''
    $filePaths = Invoke-LocalQuery 'SELECT file_path FROM audio_asset WHERE file_path IS NOT NULL'
    $missing   = @()

    foreach ($relativePath in $filePaths) {
        if ([string]::IsNullOrWhiteSpace($relativePath)) { continue }

        $fullPath = Join-Path $AudioRoot ($relativePath -replace '/', '\')
        if (-not (Test-Path $fullPath)) {
            $missing += $relativePath
        }
    }

    $total = ($filePaths | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count

    if ($missing.Count -eq 0) {
        Write-Ok "音檔比對：$total 筆 audio_asset，本機檔案全部都在"
    }
    else {
        $allMatch = $false
        Write-Host "   ✗ 音檔比對：$total 筆 audio_asset，有 $($missing.Count) 筆在本機找不到檔案" -ForegroundColor Red
        $missing | Select-Object -First 5 | ForEach-Object { Write-Host "     缺少 $_" -ForegroundColor DarkYellow }
        Write-Warn '這些詞在本機播放時會被重新合成一次（要花錢）。'
        Write-Warn '解法：重跑一次本腳本（不加 -SkipAudio），把音檔補齊。'
    }

    return $allMatch
}

# ══════════════════════════════════════════════════════════════════════════
#  主流程
# ══════════════════════════════════════════════════════════════════════════

Write-Host ''
Write-Host '═══ 把雲端資料與音檔搬回本機 ═══' -ForegroundColor Cyan
if ($Restore) {
    Write-Warn '-Restore 模式：完成後會用雲端資料覆蓋本機資料庫（會先自動備份本機）。'
}
else {
    Write-Host '   預設模式：只抓回本機存檔，不會動到本機資料庫。'
}

Write-Step '步驟 1／4：前置檢查'
Test-Prerequisite -NeedDocker $Restore.IsPresent

Write-Step '步驟 2／4：同步音檔'
if ($SkipAudio) {
    Write-Warn '已指定 -SkipAudio，跳過。'
}
else {
    Sync-Audio
}

Write-Step '步驟 3／4：取得資料庫 dump'
if ([string]::IsNullOrWhiteSpace($DumpFile)) {
    $dumpPath = Export-CloudDatabase
}
else {
    if (-not (Test-Path $DumpFile)) {
        throw " 指定的 dump 檔不存在：$DumpFile"
    }
    $dumpPath = (Resolve-Path $DumpFile).Path
    Write-Ok "使用既有的 dump：$dumpPath"
}

$dumpCounts = Get-DumpRowCount -Path $dumpPath
Write-Host ''
Write-Host '   dump 內容：'
foreach ($table in $TableNames) {
    $count = 0
    if ($dumpCounts.ContainsKey($table)) { $count = $dumpCounts[$table] }
    Write-Host ("   {0,-20}{1,6} 筆" -f $table, $count)
}

Write-Step '步驟 4／4：還原到本機'
if (-not $Restore) {
    Write-Host '   未指定 -Restore，到此為止。雲端資料已經安全地存在本機：'
    Write-Host "     資料　$dumpPath"
    if (-not $SkipAudio) {
        Write-Host "     音檔　$AudioRoot"
    }
    Write-Host ''
    Write-Host '   真的要切換到本機使用時，再跑一次並加上 -Restore。' -ForegroundColor Cyan
    Write-Host ''
    exit 0
}

Backup-LocalDatabase
Clear-LocalTable
Restore-Dump -Path $dumpPath

Write-Step '驗證'
$verified = Test-Restore -DumpCounts $dumpCounts

Write-Host ''
if ($verified) {
    Write-Host '═══ 完成，資料與音檔都對得上 ═══' -ForegroundColor Green
    Write-Host '   本機現在是雲端資料的完整副本，重查舊內容不會再呼叫 API。'
    Write-Host ''
    exit 0
}
else {
    Write-Host '═══ 完成，但有項目對不上（見上方紅字）═══' -ForegroundColor Red
    Write-Host "   要退回還原前的狀態：docker exec -i $ContainerName psql -U $DbUser -d $DatabaseName < db\backup\local-before-restore-$Stamp.sql"
    Write-Host ''
    exit 1
}
