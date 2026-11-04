-- ============================================================================
-- 機房巡檢與帳號權限管理系統：第 158 項「定期審查文案拿掉『半』」遞增腳本（既有環境升級用）
-- ============================================================================
-- 內容：只更新 COMMENT 與兩筆權限名稱、不動結構、不需 GRANT。
--       使用者 2026-11-04 裁示（第 158 項 R5 實測 ①A／②A）：定期審查所有使用者看得到的文字不再用「半」
--       （「Infra 主管半」「治理半」「另一半」），兩個簽核身分一律稱「Infra 主管」「資訊治理主管」；
--       同一人不得簽兩個身分的規則縮小為只比有效身分，切換身分代簽放行。
--       COMMENT：IM_ACCT_REVIEW 表說明與 REVIEW_STATE_CODE、兩組簽核三欄、REJECT_HALF_CODE 共 10 句（GOV_SIGN_BY 一併改寫
--       同人規則）；IM_ACCT_HIST.ACTION_CODE 一句（「第二半」「單半」改寫）。
--       資料：IM_ACCT_PERM 的 REVIEW_SIGN_INFRA／REVIEW_SIGN_GOV 權限名稱「定期審查 Infra 半簽核」「定期審查治理半簽核」
--       改為「定期審查 Infra 主管簽核」「定期審查資訊治理主管簽核」（角色權限設定頁會顯示）；只在仍是舊字時才改。
-- 對象：已跑過 infra_manager_phase2_review.sql 的既有環境（測試、UAT、正式）。
--       全新環境：infra_manager_create_table.sql 與 infra_manager_init_data.sql 已同步成新字，不必再跑本檔（跑了也無害）。
-- 可重跑：COMMENT 重覆執行無害；權限名稱只在仍是舊字時才改（第二次跑影響 0 筆）；跑到一半失敗直接從頭再跑即可。
-- 前提：以表的擁有者（RD_USER）執行。檔案編碼 UTF-8；SQL*Plus 執行前先 set NLS_LANG=.AL32UTF8、SQL Developer 開檔選 UTF-8，
--       否則 COMMENT 的中文會無聲變成亂碼（不會報錯）。SQL Developer 請用「執行指令碼」（F5）。
-- 注意：本檔不含任何帳密、主機位址。
-- 施工計畫：backlog/158-定期審查.md（第 158 項 R5）。
-- ============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

-- ===== COMMENTS：IM_ACCT_REVIEW（與 db\infra_manager_create_table.sql、db\infra_manager_phase2_review.sql 同句，三檔同步維護）
COMMENT ON TABLE IM_ACCT_REVIEW IS '定期審查簽核紀錄。每個帳號每一輪審查一筆：Infra 主管與資訊治理主管各簽核一次、順序不限，兩位都簽完為 DONE（同一交易把 IM_ACCT 的上次審查日改為完成日、下次應審查日＝完成日＋180 天）；任一位駁回為 REJECTED（帳號標審查異常旗標，來源 REVIEW）；審查中帳號被申請單關卡 6 覆蓋（修改、停用、重新啟用、刪除生效）為 CANCELED。本輪紀錄在第一次簽核或駁回時才建立，待複審通知不建紀錄（防重寄看 IM_ACCT_NOTIFY_LOG）。審查狀態（正常／待複審／逾期未審／審查異常）不存欄位，由 IM_ACCT 的審查日期與旗標即時計算。資料來源：定期審查畫面、申請單關卡 6。主鍵：REVIEW_ID（序列 SEQ_IM_ACCT_REVIEW）。保留：永久';
COMMENT ON COLUMN IM_ACCT_REVIEW.REVIEW_STATE_CODE IS '本輪結果：OPEN＝進行中（至少一位主管已簽）、DONE＝完成（兩位主管都簽）、REJECTED＝駁回（任一位主管駁回，另一位已完成的簽核留存不帶到下一輪）、CANCELED＝取消（審查中帳號被申請單關卡 6 覆蓋）';
COMMENT ON COLUMN IM_ACCT_REVIEW.INFRA_SIGN_BY IS 'Infra 主管簽核人工號（有效身分；需 REVIEW_SIGN_INFRA）';
COMMENT ON COLUMN IM_ACCT_REVIEW.INFRA_SIGN_ACT_BY IS 'Infra 主管簽核的實際操作人工號；切換身分時與 INFRA_SIGN_BY 不同';
COMMENT ON COLUMN IM_ACCT_REVIEW.INFRA_SIGN_DATE IS 'Infra 主管簽核時間';
COMMENT ON COLUMN IM_ACCT_REVIEW.GOV_SIGN_BY IS '資訊治理主管簽核人工號（有效身分；需 REVIEW_SIGN_GOV）。不得與 INFRA_SIGN_BY 為同一有效身分；實際操作人可相同（切換身分代簽放行）';
COMMENT ON COLUMN IM_ACCT_REVIEW.GOV_SIGN_ACT_BY IS '資訊治理主管簽核的實際操作人工號；切換身分時與 GOV_SIGN_BY 不同';
COMMENT ON COLUMN IM_ACCT_REVIEW.GOV_SIGN_DATE IS '資訊治理主管簽核時間';
COMMENT ON COLUMN IM_ACCT_REVIEW.REJECT_HALF_CODE IS '駁回身分：INFRA＝Infra 主管、GOV＝資訊治理主管；REVIEW_STATE_CODE＝REJECTED 時必填';

-- ===== COMMENTS：IM_ACCT_HIST.ACTION_CODE（與建表檔、緊急存取腳本、定期審查腳本同句，四檔同步維護）
COMMENT ON COLUMN IM_ACCT_HIST.ACTION_CODE IS '動作：SUBMIT＝送出申請、RESUB＝重新送出、APPROVE＝核准、REJECT＝退件、EXEC_DONE＝回報執行完畢（帳號生效）、VERIFY_OK＝複驗通過、VERIFY_REJECT＝複驗退回、EXPIRE＝到期轉為已逾期、EMG_MODIFY＝緊急存取異動（依申請人回報，主管處理時寫入）、EMG_CLOSE_MODIFY＝緊急存取結案登記異動、FLAG_CLEAR＝審查異常旗標清除、REVIEW_DONE＝定期審查完成（雙簽，寫第二位主管簽核時）、REVIEW_REJECT＝定期審查駁回（只有一位主管簽核時與取消不寫歷史，細節在 IM_ACCT_REVIEW）';

-- ===== 資料：兩筆權限名稱（角色權限設定頁會顯示；與 db\infra_manager_init_data.sql 同步）
-- 只在名稱仍是初始舊字時才改，避免蓋掉之後有人改過的值；不寫 UPDATE_*（比照初始資料，CREATE_BY＝SYSTEM 的資料修正）。
UPDATE IM_ACCT_PERM
   SET PERM_NAME = '定期審查 Infra 主管簽核'
 WHERE PERM_ID = 'REVIEW_SIGN_INFRA'
   AND PERM_NAME = '定期審查 Infra 半簽核';
UPDATE IM_ACCT_PERM
   SET PERM_NAME = '定期審查資訊治理主管簽核'
 WHERE PERM_ID = 'REVIEW_SIGN_GOV'
   AND PERM_NAME = '定期審查治理半簽核';
COMMIT;

-- ===== 執行後自我檢查（唯讀）
-- 期望：第一句 1 列，COMMENTS 不含「半」；
--       第二句 10 列（IM_ACCT_REVIEW 9 欄＋IM_ACCT_HIST.ACTION_CODE），COMMENTS 皆不含「半」；
--       第三句 2 列，PERM_NAME 為「定期審查 Infra 主管簽核」「定期審查資訊治理主管簽核」（若是別的字，代表有人改過，本檔不動它）。
-- 以下解除遇錯即停：互動式 SQL*Plus 用 @ 執行本檔後，同一連線再打錯指令才不會直接結束 SQL*Plus。
WHENEVER SQLERROR CONTINUE
SELECT TABLE_NAME, COMMENTS FROM USER_TAB_COMMENTS WHERE TABLE_NAME = 'IM_ACCT_REVIEW';
SELECT TABLE_NAME, COLUMN_NAME, COMMENTS FROM USER_COL_COMMENTS
 WHERE (TABLE_NAME = 'IM_ACCT_REVIEW' AND COLUMN_NAME IN ('REVIEW_STATE_CODE', 'INFRA_SIGN_BY', 'INFRA_SIGN_ACT_BY', 'INFRA_SIGN_DATE',
                                                          'GOV_SIGN_BY', 'GOV_SIGN_ACT_BY', 'GOV_SIGN_DATE', 'REJECT_HALF_CODE'))
    OR (TABLE_NAME = 'IM_ACCT_HIST' AND COLUMN_NAME = 'ACTION_CODE')
 ORDER BY TABLE_NAME, COLUMN_NAME;
SELECT PERM_ID, PERM_NAME FROM IM_ACCT_PERM WHERE PERM_ID IN ('REVIEW_SIGN_INFRA', 'REVIEW_SIGN_GOV') ORDER BY PERM_ID;
