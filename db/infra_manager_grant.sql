-- ============================================================================
-- 機房巡檢與帳號權限管理系統：第一階段 21 張表與 5 個序列的 PUBLIC synonym 與授權給 AP_USER
-- ============================================================================
-- 內容：第一階段 21 張表、5 個序列建 PUBLIC synonym（同名、指向執行本檔的帳號），並授權給殼 jar 的
--       連線帳號 AP_USER：21 張表各 SELECT／INSERT／UPDATE／DELETE，5 個序列各 SELECT（取號 NEXTVAL 用）。
--       不建表、不改結構、不寫任何資料列。緊急存取兩表（IM_EMG_ACCESS、IM_EMG_AFFECT_ACCT）的
--       synonym 與授權在 infra_manager_phase2_emg.sql，本檔不碰。
-- 為什麼需要：程式的 SQL 只寫表名、不加 schema 前綴（SqlConstantScanTest 擋 RD_USER. 前綴），
--       殼 jar 以 AP_USER 連線，沒有 synonym 會 ORA-00942（表不存在）、沒有授權會 ORA-01031（權限不足）。
--       建表檔 infra_manager_create_table.sql 兩者都沒有（BACKLOG 原第 48 項）。
-- 權限範圍：照抄測試 DB（2026-10-27 使用者以 RD_USER 查 USER_TAB_PRIVS：21 張表 AP_USER 皆四種全給），
--       使用者裁示 2026-10-27 ①B。已知取捨：程式其實沒有任何 DELETE，且 IM_ACCT_ADMIN_LOG 等紀錄表
--       只增不改，四種全給讓程式帳號可改、可刪稽核紀錄（BACKLOG 第 91 項，正式環境同樣適用）。
-- 對象：全新環境（含正式環境）。順序：infra_manager_create_table.sql → infra_manager_init_data.sql →
--       infra_manager_phase2_emg.sql → 本檔。已有 synonym 與授權的測試 DB 跑了也無害。
-- 可重跑：synonym 先查 ALL_SYNONYMS，已存在就印「已存在，略過」；GRANT 本身可重覆執行。
--       跑到一半失敗直接從頭再跑即可。
-- 前提：以表的擁有者（RD_USER）登入執行，需 CREATE PUBLIC SYNONYM 權限（測試 DB 2026-10-13 已確認具備；
--       正式 DB 未查，沒有的話 CREATE 會以 ORA-01031 停下：請 DBA 授 CREATE PUBLIC SYNONYM 給 RD_USER 後，
--       由 RD_USER 重跑。不要由 DBA 用自己的帳號代跑——synonym 會指向 DBA 帳號，檔首預檢會擋下）。
--       檔首預檢：登入帳號必須等於目前 schema（擋 ALTER SESSION SET CURRENT_SCHEMA 代跑），且該帳號擁有
--       下方 21 張表與 5 個序列（擋用別的帳號登入、或建表中途失敗表不齊），任一不符就報錯結束、什麼都不建。
--       被授權帳號 AP_USER 須已存在，否則 GRANT 以 ORA-01917 停下。
--       SQL*Plus 執行前先 set NLS_LANG=.AL32UTF8（本檔只有輸出訊息含中文）；SQL Developer 請用「執行指令碼」（F5）。
-- 注意：本檔不含任何帳密、主機位址。被授權帳號 AP_USER 是殼 jar 的連線帳號名，不是機密。
-- ============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

-- ===== 預檢：必須以表的擁有者登入、且 21 張表與 5 個序列都在（不符就報錯結束，什麼都不建）
-- synonym 指向 USER（登入帳號）：用別的帳號登入，會建出 26 個指向錯誤 schema 的 PUBLIC synonym，
-- 改用 RD_USER 重跑時撞 ORA-00955，得先逐一 DROP；用 ALTER SESSION SET CURRENT_SCHEMA 代跑時 GRANT 會成功、
-- 檔尾第一句也照樣列 26 列，看似通過，上線才報 ORA-00980。兩者都在這裡擋下。
DECLARE
  v_cnt NUMBER;
BEGIN
  IF USER <> SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') THEN
    RAISE_APPLICATION_ERROR(-20001, '請以表的擁有者（RD_USER）登入執行，不要用 ALTER SESSION SET CURRENT_SCHEMA 代跑'
      || '（目前登入 ' || USER || '、schema ' || SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') || '）');
  END IF;
  SELECT COUNT(*) INTO v_cnt FROM USER_OBJECTS
   WHERE (OBJECT_TYPE = 'TABLE' AND OBJECT_NAME IN (
          'IM_ACCT', 'IM_ACCT_ADMIN_LOG', 'IM_ACCT_APPR_STAGE', 'IM_ACCT_ATTACH', 'IM_ACCT_CATG', 'IM_ACCT_HIST',
          'IM_ACCT_JOB_RUN', 'IM_ACCT_LOGIN_TOKEN', 'IM_ACCT_NOTIFY_LOG', 'IM_ACCT_PERM', 'IM_ACCT_REQ', 'IM_ACCT_ROLE',
          'IM_ACCT_ROLE_PERM', 'IM_ACCT_ROLE_USER', 'IM_ACCT_USER', 'IM_ASSET_CLASS', 'IM_DOC_SEQ', 'IM_EXEC_MAP',
          'IM_INSP', 'IM_INSP_ITEM', 'IM_INSP_RESULT'))
      OR (OBJECT_TYPE = 'SEQUENCE' AND OBJECT_NAME IN (
          'SEQ_IM_ACCT_ADMIN_LOG', 'SEQ_IM_ACCT_ATTACH', 'SEQ_IM_ACCT_HIST', 'SEQ_IM_ACCT_JOB_RUN', 'SEQ_IM_ACCT_NOTIFY_LOG'));
  IF v_cnt <> 26 THEN
    RAISE_APPLICATION_ERROR(-20002, '目前登入帳號 ' || USER || ' 只找到 ' || v_cnt || ' 個（應為 21 張表＋5 個序列＝26）：'
      || '請以表的擁有者（RD_USER）登入，且先完整執行 infra_manager_create_table.sql');
  END IF;
END;
/

-- ===== PUBLIC SYNONYM（已存在就略過）
-- synonym 指向用 USER（目前連線帳號）而不寫死 schema 名；若同名 PUBLIC synonym 已存在但指向別的 schema，
-- 計數為 0 → CREATE 會以 ORA-00955 停下，這是真的撞名，要人工處理。
DECLARE
  v_cnt NUMBER;
  PROCEDURE create_public_synonym(p_name VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO v_cnt FROM ALL_SYNONYMS
     WHERE OWNER = 'PUBLIC' AND SYNONYM_NAME = p_name AND TABLE_OWNER = USER AND TABLE_NAME = p_name;
    IF v_cnt = 0 THEN
      EXECUTE IMMEDIATE 'CREATE PUBLIC SYNONYM ' || p_name || ' FOR ' || USER || '.' || p_name;
      DBMS_OUTPUT.PUT_LINE('PUBLIC SYNONYM ' || p_name || ' 已建立');
    ELSE
      DBMS_OUTPUT.PUT_LINE('PUBLIC SYNONYM ' || p_name || ' 已存在，略過');
    END IF;
  END;
BEGIN
  -- 21 張表
  create_public_synonym('IM_ACCT');
  create_public_synonym('IM_ACCT_ADMIN_LOG');
  create_public_synonym('IM_ACCT_APPR_STAGE');
  create_public_synonym('IM_ACCT_ATTACH');
  create_public_synonym('IM_ACCT_CATG');
  create_public_synonym('IM_ACCT_HIST');
  create_public_synonym('IM_ACCT_JOB_RUN');
  create_public_synonym('IM_ACCT_LOGIN_TOKEN');
  create_public_synonym('IM_ACCT_NOTIFY_LOG');
  create_public_synonym('IM_ACCT_PERM');
  create_public_synonym('IM_ACCT_REQ');
  create_public_synonym('IM_ACCT_ROLE');
  create_public_synonym('IM_ACCT_ROLE_PERM');
  create_public_synonym('IM_ACCT_ROLE_USER');
  create_public_synonym('IM_ACCT_USER');
  create_public_synonym('IM_ASSET_CLASS');
  create_public_synonym('IM_DOC_SEQ');
  create_public_synonym('IM_EXEC_MAP');
  create_public_synonym('IM_INSP');
  create_public_synonym('IM_INSP_ITEM');
  create_public_synonym('IM_INSP_RESULT');
  -- 5 個序列
  create_public_synonym('SEQ_IM_ACCT_ADMIN_LOG');
  create_public_synonym('SEQ_IM_ACCT_ATTACH');
  create_public_synonym('SEQ_IM_ACCT_HIST');
  create_public_synonym('SEQ_IM_ACCT_JOB_RUN');
  create_public_synonym('SEQ_IM_ACCT_NOTIFY_LOG');
END;
/

-- ===== 授權給 AP_USER（重覆執行無害）
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ACCT TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ACCT_ADMIN_LOG TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ACCT_APPR_STAGE TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ACCT_ATTACH TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ACCT_CATG TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ACCT_HIST TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ACCT_JOB_RUN TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ACCT_LOGIN_TOKEN TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ACCT_NOTIFY_LOG TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ACCT_PERM TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ACCT_REQ TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ACCT_ROLE TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ACCT_ROLE_PERM TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ACCT_ROLE_USER TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ACCT_USER TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_ASSET_CLASS TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_DOC_SEQ TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_EXEC_MAP TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_INSP TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_INSP_ITEM TO AP_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON IM_INSP_RESULT TO AP_USER;
GRANT SELECT ON SEQ_IM_ACCT_ADMIN_LOG TO AP_USER;
GRANT SELECT ON SEQ_IM_ACCT_ATTACH TO AP_USER;
GRANT SELECT ON SEQ_IM_ACCT_HIST TO AP_USER;
GRANT SELECT ON SEQ_IM_ACCT_JOB_RUN TO AP_USER;
GRANT SELECT ON SEQ_IM_ACCT_NOTIFY_LOG TO AP_USER;

-- ===== 執行後自我檢查（唯讀）
-- 名單寫死成 IN 清單、不用 LIKE 'IM\_%'：同一 schema 可能有其他模組的 IM_ 開頭表（測試 DB 就有）。
-- 期望：第一句 26 列（21 張表＋5 個序列的 PUBLIC synonym，TABLE_OWNER 皆為 RD_USER）；
--       第二句 26 列：21 張表 PRIV_CNT 皆 4、PRIVS 皆 DELETE,INSERT,SELECT,UPDATE；5 個序列 PRIV_CNT 皆 1、PRIVS 皆 SELECT。
--       少列＝該物件沒有 synonym 或沒授權到；緊急存取兩表不在本清單，見 infra_manager_phase2_emg.sql 檔尾。
-- 以下解除遇錯即停：互動式 SQL*Plus 用 @ 執行本檔後，同一連線再打錯指令才不會直接結束 SQL*Plus。
WHENEVER SQLERROR CONTINUE
SELECT SYNONYM_NAME, TABLE_OWNER FROM ALL_SYNONYMS
 WHERE OWNER = 'PUBLIC' AND TABLE_OWNER = USER AND SYNONYM_NAME IN (
       'IM_ACCT', 'IM_ACCT_ADMIN_LOG', 'IM_ACCT_APPR_STAGE', 'IM_ACCT_ATTACH', 'IM_ACCT_CATG', 'IM_ACCT_HIST',
       'IM_ACCT_JOB_RUN', 'IM_ACCT_LOGIN_TOKEN', 'IM_ACCT_NOTIFY_LOG', 'IM_ACCT_PERM', 'IM_ACCT_REQ', 'IM_ACCT_ROLE',
       'IM_ACCT_ROLE_PERM', 'IM_ACCT_ROLE_USER', 'IM_ACCT_USER', 'IM_ASSET_CLASS', 'IM_DOC_SEQ', 'IM_EXEC_MAP',
       'IM_INSP', 'IM_INSP_ITEM', 'IM_INSP_RESULT',
       'SEQ_IM_ACCT_ADMIN_LOG', 'SEQ_IM_ACCT_ATTACH', 'SEQ_IM_ACCT_HIST', 'SEQ_IM_ACCT_JOB_RUN', 'SEQ_IM_ACCT_NOTIFY_LOG')
 ORDER BY SYNONYM_NAME;
SELECT TABLE_NAME, COUNT(*) AS PRIV_CNT, LISTAGG(PRIVILEGE, ',') WITHIN GROUP (ORDER BY PRIVILEGE) AS PRIVS
  FROM USER_TAB_PRIVS
 WHERE GRANTEE = 'AP_USER' AND TABLE_NAME IN (
       'IM_ACCT', 'IM_ACCT_ADMIN_LOG', 'IM_ACCT_APPR_STAGE', 'IM_ACCT_ATTACH', 'IM_ACCT_CATG', 'IM_ACCT_HIST',
       'IM_ACCT_JOB_RUN', 'IM_ACCT_LOGIN_TOKEN', 'IM_ACCT_NOTIFY_LOG', 'IM_ACCT_PERM', 'IM_ACCT_REQ', 'IM_ACCT_ROLE',
       'IM_ACCT_ROLE_PERM', 'IM_ACCT_ROLE_USER', 'IM_ACCT_USER', 'IM_ASSET_CLASS', 'IM_DOC_SEQ', 'IM_EXEC_MAP',
       'IM_INSP', 'IM_INSP_ITEM', 'IM_INSP_RESULT',
       'SEQ_IM_ACCT_ADMIN_LOG', 'SEQ_IM_ACCT_ATTACH', 'SEQ_IM_ACCT_HIST', 'SEQ_IM_ACCT_JOB_RUN', 'SEQ_IM_ACCT_NOTIFY_LOG')
 GROUP BY TABLE_NAME ORDER BY TABLE_NAME;
