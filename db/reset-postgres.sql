/*
 * 資料表重建腳本 —— ★ 這支會刪掉所有資料 ★
 *
 * 使用時機：schema 結構改變、或想清空重來。
 * 執行順序：先跑這一支，再跑 db/schema.sql 把資料表重新建起來。順序不能反。
 *
 * 依外鍵相依順序刪除：translation_segment 參考 translation_query，
 * 所以子表先走。CASCADE 讓相依的約束一併移除。
 *
 * ── ★ api_usage_log 刻意不刪 ★ ─────────────────────────────────────────
 *
 *   那是「花了多少錢」的稽核紀錄，與資料表結構無關，刪掉就永遠查不回
 *   歷史費用了。這是 spec 決策 15 的結論。
 *
 *   如果你確定連費用紀錄也要清空，把最下面那一行的註解拿掉再執行。
 *
 * ── 別忘了音檔 ──────────────────────────────────────────────────────────
 *
 *   資料表清空後，audio/ 底下的檔案就沒有任何紀錄指向它們了。
 *   刪掉那些檔案，並確認 audio/th 與 audio/zh 兩個子資料夾存在。
 *
 * 執行方式：
 *   docker cp db\reset-postgres.sql language-project-postgres:/tmp/reset.sql
 *   docker exec language-project-postgres psql -U postgres -d language_project -f /tmp/reset.sql
 */

DROP TABLE IF EXISTS translation_segment CASCADE;
DROP TABLE IF EXISTS translation_query   CASCADE;
DROP TABLE IF EXISTS vocabulary          CASCADE;
DROP TABLE IF EXISTS audio_asset         CASCADE;

/* ============================================================
 * 費用稽核紀錄 —— 預設「不」刪除
 *
 * 想連費用紀錄一起清空的話，把下面這行的 -- 拿掉。
 * 想清楚再動：這張表是唯一能回答「這個專案到目前為止花了多少錢」的地方。
 * ============================================================ */
-- DROP TABLE IF EXISTS api_usage_log CASCADE;
