/*
 * 資料表重建腳本 —— ★ 這支會刪掉所有資料 ★
 *
 * 使用時機：schema 結構改變、或想清空重來。
 * 執行順序：先跑這一支，再跑 db/schema.sql。順序不能反。
 *
 * 依外鍵相依順序刪除：translation_segment 參考 translation_query，
 * 所以子表先走。CASCADE 讓相依的約束一併移除。
 *
 * 執行方式：
 *   docker cp db\reset-postgres.sql postgres:/tmp/reset.sql
 *   docker exec postgres psql -U postgres -d language_project -f /tmp/reset.sql
 */

DROP TABLE IF EXISTS translation_segment CASCADE;
DROP TABLE IF EXISTS translation_query   CASCADE;
DROP TABLE IF EXISTS vocabulary          CASCADE;
DROP TABLE IF EXISTS audio_asset         CASCADE;
DROP TABLE IF EXISTS api_usage_log       CASCADE;
