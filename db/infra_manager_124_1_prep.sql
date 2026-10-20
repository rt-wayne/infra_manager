-- ============================================================================
-- 機房巡檢與帳號權限管理系統：第 124 項「登入改用外部驗證 API」前置腳本 S1（既有環境升級用）
-- ============================================================================
-- 內容：只把 IM_ACCT_USER.USER_EN_NAME（英文顯示名）由 NOT NULL 改為可空，不動資料、不改 COMMENT、不需 GRANT。
--       新版程式不再寫英文名（人名改讀同步表 PX_SYNC_EMP），首次登入自動建立的帳號列不會有英文名；
--       先放寬這一欄，舊版程式照常寫入、不受影響，新舊版本都能跑在同一個 DB 上。
--       英文名欄與其他不用的欄在 UAT 切換窗口由腳本 D（infra_manager_124_3_drop_cols.sql）刪除。
-- 對象：已建好 IM_ACCT_USER 的既有環境。測試 DB itflow_uat 開發機與 UAT 共用，只跑一次、兩邊同時生效。
--       全新環境（含尚未上線的正式環境）：不必跑本檔，屆時直接用第 124 項完成後的建表檔建立。
--       db\infra_manager_create_table.sql 本欄暫仍寫 NOT NULL，第 124 項階段 8 改成最終結構時一併處理（使用者裁示 A，2026-10-20）。
-- 可重跑：先查 USER_TAB_COLUMNS，已可空就印「已可空，略過」；欄位已被腳本 D 刪除就印「欄位不存在，略過」。
-- 前提：以表的擁有者（RD_USER）執行。SQL Developer 請用「執行指令碼」（F5）；SQL*Plus 執行前先 set NLS_LANG=.AL32UTF8
--       （本檔只有註解與輸出訊息含中文，亂碼不影響結果）。DDL 會自動提交，執行中 IM_ACCT_USER 若有未結束的交易會等它結束。
-- 注意：本檔不含任何帳密、主機位址。
-- 施工計畫：backlog/124-登入改用外部驗證API.md「上線序列」與「階段清單」階段 2。
-- ============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

-- ===== IM_ACCT_USER.USER_EN_NAME 改可空
DECLARE
  v_nullable USER_TAB_COLUMNS.NULLABLE%TYPE;
BEGIN
  SELECT MAX(NULLABLE) INTO v_nullable
    FROM USER_TAB_COLUMNS
   WHERE TABLE_NAME = 'IM_ACCT_USER' AND COLUMN_NAME = 'USER_EN_NAME';
  IF v_nullable IS NULL THEN
    DBMS_OUTPUT.PUT_LINE('IM_ACCT_USER.USER_EN_NAME 欄位不存在，略過');
  ELSIF v_nullable = 'N' THEN
    EXECUTE IMMEDIATE 'ALTER TABLE IM_ACCT_USER MODIFY (USER_EN_NAME NULL)';
    DBMS_OUTPUT.PUT_LINE('IM_ACCT_USER.USER_EN_NAME 已改為可空');
  ELSE
    DBMS_OUTPUT.PUT_LINE('IM_ACCT_USER.USER_EN_NAME 已可空，略過');
  END IF;
END;
/

-- ===== 執行後自我檢查（唯讀）
-- 期望：第一句 1 列，NULLABLE＝Y；
--       第二句 TOTAL 與 EN_NOT_NULL 相同（本檔不動資料，舊版程式也照常寫英文名；測試 DB 2026-10-20 為 13 筆）。
-- 以下解除遇錯即停：互動式 SQL*Plus 用 @ 執行本檔後，同一連線再打錯指令才不會直接結束 SQL*Plus。
WHENEVER SQLERROR CONTINUE
SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE FROM USER_TAB_COLUMNS
 WHERE TABLE_NAME = 'IM_ACCT_USER' AND COLUMN_NAME = 'USER_EN_NAME';
SELECT COUNT(*) TOTAL, COUNT(USER_EN_NAME) EN_NOT_NULL FROM IM_ACCT_USER;
