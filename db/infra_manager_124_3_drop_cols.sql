-- ============================================================================
-- 機房巡檢與帳號權限管理系統：第 124 項「登入改用外部驗證 API」腳本 D（既有環境升級用）
-- ============================================================================
-- 內容：刪除 IM_ACCT_USER 不再使用的 9 欄（使用者裁示 D1A），並把表與欄位 COMMENT 改成最終結構的寫法：
--         USER_EN_NAME（英文名）、USER_NAME（中文姓名）、DEPT_NAME（部門）、EMAIL、TEL_NO（分機）、
--         PWD_HASH（密碼雜湊）、PWD_UPDATE_DATE（密碼設定時間）、LOGIN_FAIL_CNT（失敗次數）、LOCK_DATE（鎖定時間）
--       刪完 IM_ACCT_USER 只剩 USER_ID、LAST_LOGIN_DATE 與共用五欄（STATUS、CREATE_DATE、CREATE_BY、UPDATE_DATE、UPDATE_BY），
--       與 db\infra_manager_create_table.sql 一致。另改寫兩個人名快照欄的 COMMENT（不再是「英文名 (中文名)」）。
--       新版程式不讀也不寫這 9 欄（SqlConstantScanTest 強制），姓名、Email、部門讀同步表 PX_SYNC_EMP，密碼由 WPX 驗證。
-- 不可逆：刪掉的欄位與資料無法用本檔還原（D1A 已接受「WPX 出事時無法退回舊版密碼登入」）；要復原只能從 E5 備份。
-- 執行時機：只在 UAT 切換窗口第 ④ 步——舊版程式已停止、腳本 R（infra_manager_124_2_name_snapshot.sql）已跑完並核對之後。
--       跑之前 DBA 先完成 E5 備份（匯出 IM_ACCT_USER 與四張快照表）。舊版程式會讀寫這些欄，刪欄後舊版無法登入。
-- 對象：已建好 IM_ACCT_USER 的既有環境（測試 DB itflow_uat，開發機與 UAT 共用，只跑一次、兩邊同時生效）。
--       全新環境（含尚未上線的正式環境）：不必跑，直接用建表檔建立就是最終結構。
-- 防呆：第 1 段若英文名、中文名欄還在，先檢查人名快照是否還有「英文名 (中文名)」——有就代表腳本 R 沒跑，報錯結束、不刪任何欄。
-- 可重跑：第 2 段先查 USER_TAB_COLUMNS，只刪還存在的欄；全部已刪就印「已無舊欄，略過」。COMMENT 每次都重寫（內容相同）。
-- 前提：以表的擁有者（RD_USER）執行。SQL Developer 請用「執行指令碼」（F5）；SQL*Plus 執行前先 set NLS_LANG=.AL32UTF8
--       ——COMMENT 的中文編碼不對會無聲變成亂碼（不會報錯）。DDL 會自動提交；執行中 IM_ACCT_USER 若有未結束的交易
--       會報 ORA-00054 並結束（遇錯即停），確認舊版程式已停、沒有其他連線在改本表後重跑即可。
-- 注意：本檔不含任何帳密、主機位址、人名。
-- 施工計畫：backlog/124-登入改用外部驗證API.md「上線序列」與「階段清單」階段 8、9。
-- ============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

-- ===== 1. 防呆：腳本 R 已跑（英文名、中文名欄已刪則略過本檢查）
DECLARE
  v_cols NUMBER;
  v_left NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_cols
    FROM USER_TAB_COLUMNS
   WHERE TABLE_NAME = 'IM_ACCT_USER' AND COLUMN_NAME IN ('USER_EN_NAME', 'USER_NAME');
  IF v_cols < 2 THEN
    DBMS_OUTPUT.PUT_LINE('英文名或中文名欄已刪，略過腳本 R 檢查');
  ELSE
    -- 欄位可能已不存在，用動態 SQL（靜態 SQL 會在編譯時就因欄位不存在而失敗）
    EXECUTE IMMEDIATE q'[
      WITH OLD_NAME AS (
        SELECT TRIM(USER_EN_NAME) || ' (' || TRIM(USER_NAME) || ')' AS OLD_TEXT
          FROM IM_ACCT_USER
         WHERE TRIM(USER_EN_NAME) IS NOT NULL AND TRIM(USER_NAME) IS NOT NULL
      )
      SELECT (SELECT COUNT(*) FROM IM_ACCT_HIST WHERE ACT_BY_NAME IN (SELECT OLD_TEXT FROM OLD_NAME) OR ACT_AS_NAME IN (SELECT OLD_TEXT FROM OLD_NAME))
           + (SELECT COUNT(*) FROM IM_INSP WHERE INSP_BY_NAME IN (SELECT OLD_TEXT FROM OLD_NAME) OR ACT_AS_NAME IN (SELECT OLD_TEXT FROM OLD_NAME))
           + (SELECT COUNT(*) FROM IM_INSP_RESULT WHERE FIX_BY_NAME IN (SELECT OLD_TEXT FROM OLD_NAME) OR FIX_ACT_AS_NAME IN (SELECT OLD_TEXT FROM OLD_NAME))
           + (SELECT COUNT(*) FROM IM_ACCT_ADMIN_LOG WHERE ACT_BY_NAME IN (SELECT OLD_TEXT FROM OLD_NAME))
           + (SELECT COUNT(*) FROM IM_ACCT_ADMIN_LOG T
               WHERE EXISTS (SELECT 1 FROM OLD_NAME O WHERE INSTR(T.CONTENT_JSON, '"targetUserName":"' || O.OLD_TEXT || '"') > 0))
        FROM DUAL]' INTO v_left;
    IF v_left > 0 THEN
      RAISE_APPLICATION_ERROR(-20002, '人名快照仍有 ' || v_left || ' 列是「英文名 (中文名)」，腳本 R 尚未跑或未完成；本檔不刪任何欄，請先跑腳本 R');
    END IF;
    DBMS_OUTPUT.PUT_LINE('腳本 R 檢查通過：人名快照已無「英文名 (中文名)」');
  END IF;
END;
/

-- ===== 2. 刪除仍存在的舊欄
DECLARE
  v_list VARCHAR2(1000);
BEGIN
  SELECT LISTAGG(COLUMN_NAME, ', ') WITHIN GROUP (ORDER BY COLUMN_ID) INTO v_list
    FROM USER_TAB_COLUMNS
   WHERE TABLE_NAME = 'IM_ACCT_USER'
     AND COLUMN_NAME IN ('USER_EN_NAME', 'USER_NAME', 'DEPT_NAME', 'EMAIL', 'TEL_NO',
                         'PWD_HASH', 'PWD_UPDATE_DATE', 'LOGIN_FAIL_CNT', 'LOCK_DATE');
  IF v_list IS NULL THEN
    DBMS_OUTPUT.PUT_LINE('IM_ACCT_USER 已無舊欄，略過');
  ELSE
    EXECUTE IMMEDIATE 'ALTER TABLE IM_ACCT_USER DROP (' || v_list || ')';
    DBMS_OUTPUT.PUT_LINE('IM_ACCT_USER 已刪除：' || v_list);
  END IF;
END;
/

-- ===== 3. COMMENT 改成最終結構的寫法（與 db\infra_manager_create_table.sql 相同）
COMMENT ON TABLE IM_ACCT_USER IS '使用者。本系統的帳號列：密碼由 WPX 驗證、不存本表；姓名、Email、部門讀同步表 PX_SYNC_EMP，不存本表。STATUS＝1 且同步表查得到才能登入，並自動擁有 GENERAL 角色權限。資料來源：資訊部人員首次登入自動建立（CREATE_BY＝SYSTEM），資格外人員由 DB 預建（無維護畫面）。主鍵：USER_ID。保留：永久';
COMMENT ON COLUMN IM_ACCT_USER.USER_ID IS '工號（數字字串、最多 7 碼），即登入帳號；對應同步表 PX_SYNC_EMP.EMP_ID';
COMMENT ON COLUMN IM_ACCT_USER.STATUS IS '資料狀態：1＝啟用（WPX 驗證通過且同步表查得到即可登入、自動擁有 GENERAL 權限）、0＝停用（不可登入）。default 1';
COMMENT ON COLUMN IM_ACCT_ADMIN_LOG.ACT_BY_NAME IS '操作人顯示名快照（中文名，取自同步表；查無為工號）';
COMMENT ON COLUMN IM_ACCT_HIST.ACT_BY_NAME IS '實際操作人顯示名快照（中文名，取自同步表；查無為工號）；系統排程為「系統排程」';

-- ===== 4. 執行後自我檢查（唯讀）
-- 期望：第一句 7 列，依序 USER_ID、LAST_LOGIN_DATE、STATUS、CREATE_DATE、CREATE_BY、UPDATE_DATE、UPDATE_BY；
--       第二句 TOTAL 與刪欄前相同（本檔不刪列；測試 DB 2026-10-22 為 13 筆）。
-- 以下解除遇錯即停：互動式 SQL*Plus 用 @ 執行本檔後，同一連線再打錯指令才不會直接結束 SQL*Plus。
WHENEVER SQLERROR CONTINUE
SELECT COLUMN_NAME, DATA_TYPE, NULLABLE FROM USER_TAB_COLUMNS
 WHERE TABLE_NAME = 'IM_ACCT_USER' ORDER BY COLUMN_ID;
SELECT COUNT(*) TOTAL, SUM(CASE WHEN STATUS = 1 THEN 1 ELSE 0 END) ACTIVE FROM IM_ACCT_USER;
