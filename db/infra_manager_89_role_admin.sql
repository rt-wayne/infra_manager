-- ============================================================================
-- 機房巡檢與帳號權限管理系統：第 89 項「角色權限維護介面」遞增腳本（既有環境升級用）
-- ============================================================================
-- 內容：只更新 COMMENT、不動結構、不寫任何資料列、不需 GRANT——
--       角色指派（IM_ACCT_ROLE_USER）、執行人對照（IM_EXEC_MAP）改由畫面維護並寫系統管理軌跡（IM_ACCT_ADMIN_LOG），
--       三張表與軌跡欄位的說明從「第一階段 DB 維護」「預留」改成現況；重新指派只寫 UPDATE_*、保留 CREATE_*（裁示 ⑦A），
--       「指派人與時間即 CREATE_BY」的說法跟著修正。
--       AP_USER 對 IM_ACCT_ROLE、IM_ACCT_ROLE_USER、IM_EXEC_MAP、IM_ACCT_ADMIN_LOG、IM_ACCT_PERM、IM_ACCT_ROLE_PERM
--       已有 SELECT／INSERT／UPDATE／DELETE（2026-10-19 以 RD_USER 實查測試 DB；正式環境執行前請用檔尾第三句再查一次）。
--       第三頁（角色 × 權限碼，IM_ACCT_ROLE_PERM、ACTION_CODE 加 ROLE_PERM_SET）上線時再追加一段到本檔，可重跑。
-- 對象：已依 infra_manager_create_table.sql（＋infra_manager_phase2_emg.sql）建好 23 張表的既有環境（測試、正式）。
--       全新環境：infra_manager_create_table.sql 的 COMMENT 已與本檔同句，不必再跑本檔（跑了也無害）。
-- 可重跑：COMMENT 重覆執行無害；跑到一半失敗直接從頭再跑即可。
-- 前提：以表的擁有者（RD_USER）執行。檔案編碼 UTF-8；SQL*Plus 執行前先 set NLS_LANG=.AL32UTF8、SQL Developer 開檔選 UTF-8，
--       否則 COMMENT 的中文會無聲變成亂碼（不會報錯）。SQL Developer 請用「執行指令碼」（F5）。
-- 注意：本檔不含任何帳密、主機位址。AP_USER 是殼 jar 的連線帳號名，不是機密。
-- 施工計畫：backlog/89-角色權限維護介面.md「資料表」段。
-- ============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

-- ===== COMMENTS：系統管理軌跡 IM_ACCT_ADMIN_LOG（與 db\infra_manager_create_table.sql 同句，兩檔同步維護）
COMMENT ON TABLE IM_ACCT_ADMIN_LOG IS '系統管理軌跡。只增不改；記切換身分（IMPERSONATE）與切回（IMPERSONATE_END）、角色指派／移除（ROLE_GRANT／ROLE_REVOKE）、執行人指派／移除（EXEC_GRANT／EXEC_REVOKE）；無權限或被規則擋下（最後一位持有人、在途案件）記 DENY。資料來源：切換身分 API、名單維護 API。主鍵：LOG_ID（序列 SEQ_IM_ACCT_ADMIN_LOG）。保留：永久';
COMMENT ON COLUMN IM_ACCT_ADMIN_LOG.ACTION_CODE IS '動作：IMPERSONATE＝切換身分、IMPERSONATE_END＝切回、ROLE_GRANT＝指派角色、ROLE_REVOKE＝移除角色、EXEC_GRANT＝指派執行人、EXEC_REVOKE＝移除執行人';
COMMENT ON COLUMN IM_ACCT_ADMIN_LOG.TARGET_USER_ID IS '對象使用者工號（切換／切回的代理身分；名單維護的被指派或被移除者），參照 IM_ACCT_USER.USER_ID；超過欄寬的輸入存空';
COMMENT ON COLUMN IM_ACCT_ADMIN_LOG.CONTENT_JSON IS '內容（JSON 文字）：切換時記對象顯示名；名單維護成功記 roleId＋roleName 或 catgId＋catgName、targetUserName、before.status（null＝原無此列／0／1）、after.status；被拒記 denyCode（NO_PERM／LAST_SYS_ROLE／IN_FLIGHT）、blockedPerms、blockedTotal、blockedIds（最多 50 筆，超過 3900 bytes 逐筆丟單號保留總數）';
COMMENT ON COLUMN IM_ACCT_ADMIN_LOG.REASON IS '原因：名單維護必填（500 字內，被拒時有帶就存）；切換身分為空';

-- ===== COMMENTS：角色人員 IM_ACCT_ROLE_USER
COMMENT ON TABLE IM_ACCT_ROLE_USER IS '角色人員。一位人員可擁有多個角色；權限每次請求依當下名單判斷。不得存 GENERAL。移除＝STATUS 改 0；重新指派＝同列 STATUS 改回 1、只寫 UPDATE_*、保留第一次的 CREATE_*，所以最近一次指派人與時間＝NVL(UPDATE_*, CREATE_*)。資料來源：角色指派畫面（寫系統管理軌跡）；DB 直接改不留軌跡、不檢查在途案件，只作備援。主鍵：ROLE_ID＋USER_ID。保留：永久';
COMMENT ON COLUMN IM_ACCT_ROLE_USER.CREATE_BY IS '第一次指派人員工號；重新指派不改此欄';
COMMENT ON COLUMN IM_ACCT_ROLE_USER.UPDATE_DATE IS '最後更新日期（含時分秒）：移除或重新指派時寫入。新增時為空';
COMMENT ON COLUMN IM_ACCT_ROLE_USER.UPDATE_BY IS '最後更新人員工號：移除人或重新指派人';

-- ===== COMMENTS：執行人對照 IM_EXEC_MAP
COMMENT ON TABLE IM_EXEC_MAP IS '執行人對照。帳號類別對應關卡 6（執行回報）的執行人，同類別多人時任一人可執行。移除＝STATUS 改 0；重新指派＝同列 STATUS 改回 1、只寫 UPDATE_*、保留第一次的 CREATE_*。資料來源：執行人對照畫面（寫系統管理軌跡）；DB 直接改不留軌跡、不檢查在途案件，只作備援。主鍵：CATG_ID＋USER_ID。保留：永久';
COMMENT ON COLUMN IM_EXEC_MAP.CREATE_BY IS '第一次指派人員工號；重新指派不改此欄';
COMMENT ON COLUMN IM_EXEC_MAP.UPDATE_DATE IS '最後更新日期（含時分秒）：移除或重新指派時寫入。新增時為空';
COMMENT ON COLUMN IM_EXEC_MAP.UPDATE_BY IS '最後更新人員工號：移除人或重新指派人';

-- ===== 執行後自我檢查（唯讀）
-- 期望：第一句 3 列，COMMENTS 皆不含「第一階段」「預留」字樣；
--       第二句 5 列（IM_ACCT_ADMIN_LOG 的 ACTION_CODE、CONTENT_JSON、REASON、TARGET_USER_ID，加 IM_ACCT_ROLE_USER.CREATE_BY），
--       ACTION_CODE 那列含 EXEC_REVOKE；
--       第三句 AP_USER 對 IM_ACCT_ADMIN_LOG、IM_ACCT_ROLE、IM_ACCT_ROLE_USER、IM_EXEC_MAP 至少各有 SELECT 與 UPDATE、
--       IM_ACCT_ROLE_USER／IM_EXEC_MAP／IM_ACCT_ADMIN_LOG 另有 INSERT（名單維護 API 需要；沒有就要補 GRANT 並更新本檔）。
-- 以下解除遇錯即停：互動式 SQL*Plus 用 @ 執行本檔後，同一連線再打錯指令才不會直接結束 SQL*Plus。
WHENEVER SQLERROR CONTINUE
SELECT TABLE_NAME, COMMENTS FROM USER_TAB_COMMENTS
 WHERE TABLE_NAME IN ('IM_ACCT_ADMIN_LOG', 'IM_ACCT_ROLE_USER', 'IM_EXEC_MAP') ORDER BY TABLE_NAME;
SELECT TABLE_NAME, COLUMN_NAME, COMMENTS FROM USER_COL_COMMENTS
 WHERE (TABLE_NAME = 'IM_ACCT_ADMIN_LOG' AND COLUMN_NAME IN ('ACTION_CODE', 'TARGET_USER_ID', 'CONTENT_JSON', 'REASON'))
    OR (TABLE_NAME = 'IM_ACCT_ROLE_USER' AND COLUMN_NAME = 'CREATE_BY')
 ORDER BY TABLE_NAME, COLUMN_NAME;
SELECT GRANTEE, TABLE_NAME, PRIVILEGE FROM USER_TAB_PRIVS
 WHERE GRANTEE = 'AP_USER' AND TABLE_NAME IN ('IM_ACCT_ADMIN_LOG', 'IM_ACCT_ROLE', 'IM_ACCT_ROLE_USER', 'IM_EXEC_MAP')
 ORDER BY TABLE_NAME, PRIVILEGE;
