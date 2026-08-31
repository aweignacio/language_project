<#
 ══════════════════════════════════════════════════════════════════════════
  把本機的資料與音檔整包推上雲端（★ 覆蓋，不是合併）
 ══════════════════════════════════════════════════════════════════════════

  用途：你平常在電腦上查東西，資料進的是本機的 postgres 容器；
  手機上的 PWA 打的是 Cloud Run，資料進的是 Cloud SQL。
  兩份資料庫各自長大，誰也不知道對方有什麼。這支腳本負責
  「把本機那份推上去，讓手機也看得到」。

  方向與 backup-from-gcp.ps1 完全相反，其餘設計刻意保持對稱。

 ── ★★ 這是覆蓋，不是合併 ★★ ─────────────────────────────────────────

  執行 -Overwrite 之後，雲端那五張表會先被刪掉再灌入本機的內容。
  意思是：

      ★ 你在手機上查過、而本機沒有的句子，會消失。
      ★ 雲端的 api_usage_log（手機那邊花了多少錢的紀錄）也會被換掉。

  為什麼不能只灌不刪：dump 裡是 CREATE TABLE（沒有 IF NOT EXISTS），
  表已經存在的話那幾行會報 relation already exists，後面的 COPY 跟著
  不執行 —— 症狀是「跑完好像沒事，但資料一筆都沒進去」。

  ★ 所以第 2 步一定會先把雲端現況倒回本機存著。那份就是你的後路，
    後悔的時候拿它反推回去（用法見檔尾）。

 ── 為什麼推上去就能用 ─────────────────────────────────────────────────

  audio_asset.file_path 存的是 "th/a1b2c3d4e5f6.wav" 這種相對路徑，
  LocalDiskAudioStorage 與 GoogleCloudAudioStorage 產生路徑的格式
  刻意做成一模一樣。所以本機的資料列推上去之後，
  ★ 一行程式都不用改就能對到 bucket 裡的 th/、zh/ 底下的檔案。

 ── 流程：你在 PowerShell 打下這行之後發生什麼事 ───────────────────────

  第 1 步｜你執行

      .\db\push-to-gcp.ps1

    預設是「只看不動」—— 把雲端現況備份下來、把本機資料倒出來，
    然後印出兩邊的筆數讓你先看清楚會覆蓋掉什麼。雲端資料不會變。

  第 2 步｜Test-Prerequisite 檢查 gcloud 登入了沒、容器在不在

    順便比對兩邊的 PostgreSQL 大版本。★ 本機比雲端新的話會警告 ——
    新版 pg_dump 產生的語法舊版可能不認得，匯入會在中途失敗。

  第 3 步｜Backup-CloudDatabase 先把雲端現況倒回本機（後路）

      gcloud sql export sql thailan-db `
          gs://thailan-audio-awei/backup/gcp-before-overwrite-20260831-143000.sql `
          --database=language_project

    再下載到 db\backup\ 底下。★ 這一步不能省，也不受 -SkipAudio 影響。

  第 4 步｜Export-LocalDatabase 把本機資料倒出來

      docker exec language-project-postgres pg_dump -U postgres -d language_project `
          --no-owner --no-acl -f /tmp/local-dump.sql

    ★ --no-owner --no-acl 是為了雲端：本機的 dump 會夾帶
      「這張表屬於 postgres」這類指令，Cloud SQL 的權限模型不一樣，
      帶著它們匯入會在那些行上失敗。內容資料完全不受影響。

  第 5 步｜New-ImportFile 把「先刪表」接在 dump 前面

      DROP TABLE IF EXISTS translation_segment CASCADE;   ← 子表先走
      DROP TABLE IF EXISTS translation_query   CASCADE;
      DROP TABLE IF EXISTS vocabulary          CASCADE;
      DROP TABLE IF EXISTS audio_asset         CASCADE;
      DROP TABLE IF EXISTS api_usage_log       CASCADE;
      （接著才是本機 dump 的全部內容）

    ★ 為什麼要用「接在前面」這種手法：Cloud SQL 不對外開放（見
      「筆記-部署與更新.md」第十二節），本機沒辦法連上去下一句 DROP。
      而 gcloud sql import sql 的行為就是「執行這個 .sql 檔」——
      所以把 DROP 寫進同一個檔案，就等於在雲端執行了它。

  ── 到這裡預設模式就結束了。以下只有加 -Overwrite 才會執行 ──

  第 6 步｜Push-Audio 把本機音檔推上 bucket

      gcloud storage rsync -r <專案>\audio\th gs://thailan-audio-awei/th
      gcloud storage rsync -r <專案>\audio\zh gs://thailan-audio-awei/zh

    ★ 順序很重要：音檔一定要在資料庫之前推上去。
      反過來的話，中間那段空窗期手機查到的列會找不到音檔，
      程式的反應是「有紀錄卻讀不到檔案，那就重新合成一次」——
      畫面完全正常，錢卻又付了一次。

    ★ 沒有加 --delete-unmatched-destination-objects：
      本機沒有的檔案，雲端也不會被刪。rsync 在這裡只做「補齊」。

  第 7 步｜Import-CloudDatabase 上傳並匯入

      gcloud storage cp <import 檔> gs://thailan-audio-awei/backup/
      gcloud sql import sql thailan-db <那個網址> `
          --database=language_project --user=postgres

  第 8 步｜Test-Push 驗證，這才是真正的驗收

    再把雲端倒一次回來，數每張表幾筆，跟本機 dump 的筆數比對。

    ★ 為什麼要繞這一圈才驗得到：Cloud SQL 不對外開放，本機沒辦法直接
      下 SELECT count(*)。「再倒一次回來數」是唯一不必改網路設定的方法。

    音檔則比對「本機幾個檔案 vs bucket 幾個物件」。

 ── 用法 ───────────────────────────────────────────────────────────────

    .\db\push-to-gcp.ps1                     只比對，不動雲端（安全，可重跑）
    .\db\push-to-gcp.ps1 -Overwrite          ★ 真的用本機覆蓋雲端
    .\db\push-to-gcp.ps1 -Overwrite -SkipAudio
                                             只覆蓋資料庫，不推音檔
    .\db\push-to-gcp.ps1 -Overwrite -SkipAudio -DumpFile db\backup\gcp-before-overwrite-20260831-143000.sql
                                             ★ 後悔了：拿第 3 步的備份把雲端推回去

 ── 只需做一次的前置 ───────────────────────────────────────────────────

  第一次跑如果卡在匯出或匯入，訊息大概是「does not have storage.objects...
  access」。那是 Cloud SQL 的服務帳號沒有讀寫 bucket 的權限，
  腳本會把該執行的那一行印出來，照著貼一次就好。
  （如果你之前跑過 backup-from-gcp.ps1 並授權過 objectAdmin，這裡就不會卡。）
 ══════════════════════════════════════════════════════════════════════════
#>

param(
    # 加了才會真的動雲端。★ 這是破壞性操作，雲端現有資料會被本機的取代。
    [switch]$Overwrite,

    # 跳過音檔同步。音檔是整個流程最慢的一段，只想更新資料庫時用。
    [switch]$SkipAudio,

    # 指定既有的 dump 檔，跳過「重新匯出本機資料」。
    # ★ 也可以餵第 3 步產生的雲端備份檔 —— 那就等於把雲端還原回覆蓋前的樣子。
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
 檢查 gcloud 與 docker 的狀態。
 沒過關就直接停 —— 讓它在第一步失敗，比跑到一半才炸掉好查。

 ★ 這支腳本任何模式都需要 docker：本機資料就是從那個容器倒出來的。
#>
function Test-Prerequisite {
    if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
        throw ' 找不到 gcloud。開一個新的 PowerShell 視窗再試，或確認 Google Cloud CLI 已安裝。'
    }

    $account = gcloud config get-value account 2>$null
    if ([string]::IsNullOrWhiteSpace($account) -or $account -eq '(unset)') {
        throw ' gcloud 尚未登入。請先執行：gcloud auth login'
    }
    Write-Ok "gcloud 身分：$account"

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw ' 找不到 docker。本機資料要從容器裡倒出來，沒有它做不了事。'
    }

    $running = docker ps --filter "name=$ContainerName" --format '{{.Names}}'
    if ($running -ne $ContainerName) {
        throw " 容器 $ContainerName 沒有在跑。請先在專案根目錄執行：docker compose up -d"
    }
    Write-Ok "本機資料庫容器：$ContainerName（執行中）"
}

<#
 比對本機與雲端的 PostgreSQL 大版本。

 ★ 為什麼要管這件事：pg_dump 產生的檔案是「給同版本或更新的資料庫吃的」。
   本機是 18、雲端是 15 的話，dump 裡可能出現 15 不認識的語法，
   匯入會在中途失敗 —— 而那時雲端的表已經被 DROP 掉了。

 不強制中止，只警告：多數情況仍然能匯入成功，硬擋反而擋住正常的用法。
#>
function Test-VersionMatch {
    $cloudVersion = gcloud sql instances describe $SqlInstance `
        --project=$ProjectId --format='value(databaseVersion)' 2>$null

    if ([string]::IsNullOrWhiteSpace($cloudVersion)) {
        Write-Warn '查不到 Cloud SQL 的版本，跳過版本比對。'
        return
    }

    # databaseVersion 長這樣：POSTGRES_15
    $cloudMajor = 0
    if ($cloudVersion -match 'POSTGRES_(\d+)') {
        $cloudMajor = [int]$Matches[1]
    }

    # server_version_num 長這樣：180000（18.0）
    $localNum   = docker exec $ContainerName psql -U $DbUser -d $DatabaseName -t -A `
        -c "SELECT current_setting('server_version_num')"
    $localMajor = [int]([int]($localNum.ToString().Trim()) / 10000)

    Write-Ok "PostgreSQL 版本：本機 $localMajor、雲端 $cloudMajor"

    if ($localMajor -gt $cloudMajor) {
        Write-Warn "本機（$localMajor）比雲端（$cloudMajor）新。新版 pg_dump 產生的語法舊版可能不認得，"
        Write-Warn '匯入有機會在中途失敗。真的失敗的話，改用與雲端同版本的映像檔跑 pg_dump：'
        Write-Warn "  docker run --rm --network host postgres:$cloudMajor pg_dump ..."
    }
}

<#
 把 Cloud SQL 現況倒成一份 SQL 丟進 bucket，再抓回 db\backup\。
 回傳本機那份備份的完整路徑 —— ★ 這是覆蓋之後唯一的後路。
#>
function Backup-CloudDatabase {
    if (-not (Test-Path $BackupDir)) {
        New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    }

    $fileName  = "gcp-before-overwrite-$Stamp.sql"
    $gcsUri    = "gs://$Bucket/backup/$fileName"
    $localPath = Join-Path $BackupDir $fileName

    Write-Host "   匯出 Cloud SQL → $gcsUri"
    Write-Host '   （資料量大時這步要等一兩分鐘，沒有進度條是正常的）'

    gcloud sql export sql $SqlInstance $gcsUri `
        --database=$DatabaseName `
        --project=$ProjectId

    if ($LASTEXITCODE -ne 0) {
        Show-BucketPermissionHint
        throw ' Cloud SQL 匯出失敗。為安全起見中止，雲端資料一動也沒動。'
    }

    Write-Host "   下載 → $localPath"
    gcloud storage cp $gcsUri $localPath
    if ($LASTEXITCODE -ne 0) {
        throw ' 備份下載失敗。為安全起見中止，雲端資料一動也沒動。'
    }

    $sizeMb = [math]::Round((Get-Item $localPath).Length / 1MB, 2)
    Write-Ok "雲端現況已存檔 $fileName（$sizeMb MB）。後悔的話用這份推回去。"

    return $localPath
}

<#
 匯出失敗時最常見的原因就是權限。把該貼的那行直接印出來，省得去翻文件。
#>
function Show-BucketPermissionHint {
    $sqlServiceAccount = gcloud sql instances describe $SqlInstance `
        --project=$ProjectId --format='value(serviceAccountEmailAddress)' 2>$null

    Write-Host ''
    Write-Warn '如果訊息裡有 storage.objects.*，是 Cloud SQL 的服務帳號沒有讀寫 bucket'
    Write-Warn '的權限。執行下面這一行授權（只需做一次）：'
    Write-Host ''
    Write-Host "     gcloud storage buckets add-iam-policy-binding gs://$Bucket ``" -ForegroundColor White
    Write-Host "         --member=serviceAccount:$sqlServiceAccount ``" -ForegroundColor White
    Write-Host '         --role=roles/storage.objectAdmin' -ForegroundColor White
    Write-Host ''
}

<#
 把本機容器裡的資料倒成一份 dump，回傳它的路徑。
#>
function Export-LocalDatabase {
    if (-not (Test-Path $BackupDir)) {
        New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    }

    $localPath = Join-Path $BackupDir "local-$Stamp.sql"

    Write-Host "   匯出本機資料庫 → $localPath"

    # ★ 刻意讓 pg_dump 直接寫成容器裡的檔案，再整個 docker cp 出來，
    #   而不是用 PowerShell 的管線接它的輸出。
    #   管線會把位元組轉成字串再轉回去，泰文在這一來一回可能變成亂碼 ——
    #   而 dump 壞掉這件事，是灌上雲端之後才會發現的。
    #
    # ★ --no-owner --no-acl：本機的 dump 會夾帶「這張表屬於 postgres」
    #   這類指令，Cloud SQL 的權限模型不一樣，帶著它們匯入會失敗。
    docker exec $ContainerName pg_dump -U $DbUser -d $DatabaseName `
        --no-owner --no-acl -f /tmp/local-dump.sql
    if ($LASTEXITCODE -ne 0) {
        throw ' 本機匯出失敗。'
    }

    docker cp "${ContainerName}:/tmp/local-dump.sql" $localPath
    if ($LASTEXITCODE -ne 0) {
        throw ' 本機 dump 無法複製出容器。'
    }
    docker exec $ContainerName rm -f /tmp/local-dump.sql | Out-Null

    if (-not (Test-Path $localPath)) {
        throw ' 本機 dump 不存在，中止。'
    }

    $sizeKb = [math]::Round((Get-Item $localPath).Length / 1KB, 1)
    Write-Ok "本機資料已匯出（$sizeKb KB）"

    return $localPath
}

<#
 讀 pg_dump 的純文字 dump，數出每張表帶了幾筆資料。

 dump 裡的格式固定是這樣，中間夾的每一行就是一筆：
     COPY public.audio_asset (id, language, ...) FROM stdin;
     1	TH	เหล้า	th/a1b2c3d4e5f6.wav
     \.
 用 ReadLines 逐行串流，dump 幾百 MB 也不會把記憶體吃光。

 ★ 這個函式與 backup-from-gcp.ps1 裡的同名函式一模一樣 ——
   兩邊讀的都是 pg_dump 產生的檔案，格式相同。
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
 產生真正要餵給 Cloud SQL 的檔案：「先刪五張表」＋ dump 的全部內容。
 回傳新檔案的路徑。

 ★ 為什麼要這樣繞：Cloud SQL 不對外開放，本機沒辦法連上去下一句 DROP。
   而 gcloud sql import sql 的行為就是「執行這個 .sql 檔」，
   所以把 DROP 寫進同一個檔案的開頭，就等於在雲端執行了它。

 ★ 為什麼用位元組層級拼接，不用 Get-Content / Set-Content：
   那兩個指令會把整份檔案轉成 PowerShell 字串再轉回位元組，
   泰文與中文有機會在這一來一回變成亂碼，而且 Set-Content 可能在
   開頭補上 BOM —— SQL 檔開頭多三個位元組會讓第一行指令解析失敗。
   直接搬位元組就完全不碰編碼。
#>
function New-ImportFile {
    param([string]$DumpPath)

    $importPath = Join-Path $BackupDir "import-$Stamp.sql"

    $dropSql = ($TableNames | ForEach-Object { "DROP TABLE IF EXISTS $_ CASCADE;" }) -join "`n"
    $header  = "-- 由 push-to-gcp.ps1 產生於 $Stamp`n" +
               "-- 先清掉舊表，才輪得到下面 dump 裡的 CREATE TABLE。`n" +
               "$dropSql`n`n"

    # $false = 不要 BOM
    $utf8NoBom   = New-Object System.Text.UTF8Encoding($false)
    $headerBytes = $utf8NoBom.GetBytes($header)

    $output = [System.IO.File]::Create($importPath)
    try {
        $output.Write($headerBytes, 0, $headerBytes.Length)

        # ★ 這裡不可以取名 $input —— 那是 PowerShell 的保留變數（函式的管線輸入），
        #   蓋掉它會在不相干的地方出現很難查的行為。
        $source = [System.IO.File]::OpenRead($DumpPath)
        try {
            $source.CopyTo($output)
        }
        finally {
            $source.Dispose()
        }
    }
    finally {
        $output.Dispose()
    }

    Write-Ok "匯入檔已產生：$(Split-Path $importPath -Leaf)"

    return $importPath
}

<#
 把本機的音檔補齊到 bucket。只補不刪，所以可以隨時重跑。

 ★ 一定要在資料庫覆蓋「之前」執行，理由見檔頭第 6 步。
#>
function Push-Audio {
    foreach ($folder in $AudioFolders) {
        $source = Join-Path $AudioRoot $folder

        if (-not (Test-Path $source)) {
            Write-Warn "本機沒有 audio\$folder，跳過。"
            continue
        }

        $localCount = (Get-ChildItem $source -File -ErrorAction SilentlyContinue | Measure-Object).Count
        Write-Host "   同步 $source → gs://$Bucket/$folder（本機 $localCount 個檔案）"

        gcloud storage rsync -r $source "gs://$Bucket/$folder"
        if ($LASTEXITCODE -ne 0) {
            throw " 音檔上傳失敗（$folder）。★ 資料庫還沒動，可以直接重跑。"
        }

        Write-Ok "audio\$folder 已推上 bucket"
    }
}

<#
 上傳匯入檔並叫 Cloud SQL 執行它。這一步之後雲端資料就換掉了。
#>
function Import-CloudDatabase {
    param([string]$ImportPath)

    $fileName = Split-Path $ImportPath -Leaf
    $gcsUri   = "gs://$Bucket/backup/$fileName"

    Write-Host "   上傳 → $gcsUri"
    gcloud storage cp $ImportPath $gcsUri
    if ($LASTEXITCODE -ne 0) {
        throw ' 匯入檔上傳失敗。雲端資料還沒動。'
    }

    Write-Host '   匯入 Cloud SQL（這步要等一兩分鐘，沒有進度條是正常的）'

    # --quiet：不要跳「你確定嗎」的互動提示，腳本裡沒有人可以回答它。
    gcloud sql import sql $SqlInstance $gcsUri `
        --database=$DatabaseName `
        --user=$DbUser `
        --project=$ProjectId `
        --quiet

    if ($LASTEXITCODE -ne 0) {
        Show-BucketPermissionHint
        Write-Warn '★ 匯入失敗時雲端可能停在「表已經被刪掉、但資料還沒進去」的半路狀態。'
        Write-Warn '  用第 3 步的備份檔推回去（見結尾印出的指令）。'
        throw ' Cloud SQL 匯入失敗。'
    }

    Write-Ok '匯入完成'
}

<#
 驗收。回傳 $true 代表全部對得上。

 ① 每張表：本機 dump 說幾筆、雲端現在幾筆
 ② 音檔：本機幾個檔案、bucket 幾個物件

 ★ 為什麼要把雲端「再倒一次回來」才數得到：Cloud SQL 不對外開放，
   本機沒辦法直接下 SELECT count(*)。
#>
function Test-Push {
    param([hashtable]$LocalCounts, [bool]$CheckAudio)

    $fileName  = "gcp-after-push-$Stamp.sql"
    $gcsUri    = "gs://$Bucket/backup/$fileName"
    $localPath = Join-Path $BackupDir $fileName

    Write-Host '   把雲端倒回來數筆數（這步要等一兩分鐘）'

    gcloud sql export sql $SqlInstance $gcsUri --database=$DatabaseName --project=$ProjectId
    if ($LASTEXITCODE -ne 0) {
        Write-Warn '驗證用的匯出失敗，無法比對筆數。資料可能已經進去了，請自行到手機上確認。'
        return $false
    }

    gcloud storage cp $gcsUri $localPath
    if ($LASTEXITCODE -ne 0) {
        Write-Warn '驗證用的 dump 下載失敗，無法比對筆數。'
        return $false
    }

    $cloudCounts = Get-DumpRowCount -Path $localPath
    $allMatch    = $true

    Write-Host ''
    Write-Host '   資料表           本機    雲端    結果'
    Write-Host '   ─────────────────────────────────────'

    foreach ($table in $TableNames) {
        $expected = 0
        if ($LocalCounts.ContainsKey($table)) { $expected = $LocalCounts[$table] }

        $actual = 0
        if ($cloudCounts.ContainsKey($table)) { $actual = $cloudCounts[$table] }

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

    if (-not $CheckAudio) {
        Write-Host ''
        Write-Warn '已指定 -SkipAudio，沒有比對音檔數量。'
        return $allMatch
    }

    # ★ 音檔沒上去的話，手機查到那一句會「有紀錄卻讀不到檔案」，
    #   於是重新合成一次 —— 畫面完全正常，錢卻又付了一次。
    Write-Host ''
    foreach ($folder in $AudioFolders) {
        $source = Join-Path $AudioRoot $folder
        if (-not (Test-Path $source)) { continue }

        $localFiles = (Get-ChildItem $source -File -ErrorAction SilentlyContinue | Measure-Object).Count
        $cloudFiles = @(gcloud storage ls "gs://$Bucket/$folder/**" 2>$null).Count

        if ($cloudFiles -ge $localFiles) {
            Write-Ok "音檔 $folder：本機 $localFiles、雲端 $cloudFiles"
        }
        else {
            $allMatch = $false
            Write-Host "   ✗ 音檔 $folder：本機 $localFiles、雲端只有 $cloudFiles" -ForegroundColor Red
            Write-Warn '缺的那些句子在手機上會被重新合成一次（要花錢）。'
            Write-Warn '解法：重跑一次本腳本（不加 -SkipAudio）把音檔補齊。'
        }
    }

    return $allMatch
}

# ══════════════════════════════════════════════════════════════════════════
#  主流程
# ══════════════════════════════════════════════════════════════════════════

Write-Host ''
Write-Host '═══ 把本機資料與音檔推上雲端 ═══' -ForegroundColor Cyan
if ($Overwrite) {
    Write-Warn '-Overwrite 模式：雲端那五張表會被刪掉再灌入本機的內容。'
    Write-Warn '★ 你在手機上查過、而本機沒有的句子會消失，雲端的費用紀錄也會被換掉。'
    Write-Warn '  執行前會先自動把雲端現況備份回本機。'
}
else {
    Write-Host '   預設模式：只比對兩邊有多少資料，不會動到雲端。'
}

Write-Step '步驟 1／5：前置檢查'
Test-Prerequisite
Test-VersionMatch

Write-Step '步驟 2／5：備份雲端現況（覆蓋之後的唯一後路）'
$cloudBackup = Backup-CloudDatabase
$cloudCounts = Get-DumpRowCount -Path $cloudBackup

Write-Step '步驟 3／5：匯出本機資料'
if ([string]::IsNullOrWhiteSpace($DumpFile)) {
    $dumpPath = Export-LocalDatabase
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
Write-Host '   即將發生的事：'
Write-Host '   資料表           雲端現在  換成    '
Write-Host '   ─────────────────────────────────────'
foreach ($table in $TableNames) {
    $before = 0
    if ($cloudCounts.ContainsKey($table)) { $before = $cloudCounts[$table] }

    $after = 0
    if ($dumpCounts.ContainsKey($table)) { $after = $dumpCounts[$table] }

    Write-Host ("   {0,-16}{1,8}{2,8} 筆" -f $table, $before, $after)
}

Write-Step '步驟 4／5：推送音檔'
if (-not $Overwrite) {
    Write-Host '   未指定 -Overwrite，到此為止。雲端一動也沒動，備份已經在：'
    Write-Host "     $cloudBackup"
    Write-Host ''
    Write-Host '   真的要覆蓋雲端時，再跑一次並加上 -Overwrite。' -ForegroundColor Cyan
    Write-Host ''
    exit 0
}

if ($SkipAudio) {
    Write-Warn '已指定 -SkipAudio，跳過。'
}
else {
    Push-Audio
}

Write-Step '步驟 5／5：覆蓋雲端資料庫'
$importPath = New-ImportFile -DumpPath $dumpPath
Import-CloudDatabase -ImportPath $importPath

Write-Step '驗證'
$verified = Test-Push -LocalCounts $dumpCounts -CheckAudio (-not $SkipAudio.IsPresent)

Write-Host ''
Write-Host '   後悔的話，用這一行把雲端推回覆蓋前的樣子：' -ForegroundColor Cyan
Write-Host "     .\db\push-to-gcp.ps1 -Overwrite -SkipAudio -DumpFile $cloudBackup"

Write-Host ''
if ($verified) {
    Write-Host '═══ 完成，雲端與本機對得上 ═══' -ForegroundColor Green
    Write-Host '   手機上的 App 現在看得到你在電腦查過的東西了。'
    Write-Host '   ★ 手機可能還快取著舊清單，下拉重新整理一次。'
    Write-Host ''
    exit 0
}
else {
    Write-Host '═══ 完成，但有項目對不上（見上方紅字）═══' -ForegroundColor Red
    Write-Host ''
    exit 1
}
