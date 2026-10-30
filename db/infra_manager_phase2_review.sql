-- ============================================================================
-- 機房巡檢與帳號權限管理系統：第二階段「定期權限審查」遞增腳本（既有環境升級用）
-- ============================================================================
-- 內容：新增 1 張表（IM_ACCT_REVIEW 定期審查簽核紀錄，含主鍵）、1 個序列（SEQ_IM_ACCT_REVIEW）、
--       2 個索引（其中 IND_IM_ACCT_REVIEW_02 為唯一函數式索引：同一帳號最多一筆進行中）、
--       新表全部欄位 COMMENT；更新既有欄位 COMMENT（IM_ACCT_HIST 的 REVIEW_ID／SOURCE_CODE／ACTION_CODE
--       啟用定期審查值、通知紀錄的 REVIEW_DUE／REVIEW_REJECT 事件與參照鍵、排程 J04 與 J02 新筆數定義、
--       IM_ACCT 的審查日期與審查異常旗標說明）；表與序列的 PUBLIC synonym 與授權給 AP_USER
--       （比照 infra_manager_phase2_emg.sql，權限採最小範圍：表無刪除 → SELECT／INSERT／UPDATE；序列 SELECT）。
--       不加既有表欄位、不寫任何資料列。
-- 對象：已依 infra_manager_create_table.sql 建好 23 張表的既有環境（測試、UAT、正式）。
--       全新環境：infra_manager_create_table.sql 已含這張表、序列、索引與全部 COMMENT，但建表檔沒有 synonym 與授權，
--       建完表仍須執行本檔（表、序列、索引會印「已存在，略過」，只會補上檔尾的 PUBLIC synonym 與 GRANT）。
-- 可重跑：每個 CREATE 先查 USER_TABLES／USER_SEQUENCES／USER_INDEXES／ALL_SYNONYMS，已存在就略過（印出「已存在，略過」）；
--       COMMENT 與 GRANT 本身可重覆執行。跑到一半失敗直接從頭再跑即可。
-- 前提：以表的擁有者（RD_USER）登入執行，需 CREATE TABLE、CREATE SEQUENCE、CREATE PUBLIC SYNONYM 權限與表空間配額。
--       表放 FLOW_DATA、索引（含主鍵索引）放 FLOW_INDX（與 infra_manager_create_table.sql 一致）；已存在時略過，不會搬移。
--       檔首預檢：登入帳號必須等於目前 schema（擋 ALTER SESSION SET CURRENT_SCHEMA 代跑），且該帳號已有
--       第一階段的 IM_ACCT 表（擋用別的帳號登入），任一不符就報錯結束、什麼都不建。
--       檔案編碼 UTF-8；SQL*Plus 執行前先 set NLS_LANG=.AL32UTF8、SQL Developer 開檔選 UTF-8，
--       否則 COMMENT 的中文會無聲變成亂碼（不會報錯）。SQL Developer 請用「執行指令碼」（F5）。
-- 命名：依公司 DB 規範 V1（2026-10-02 版，不在 repo）：單字超過 6 碼的改用短字或字典縮寫
--       （REVIEW、REJECT、CANCEL 剛好 6 碼；INFRA、GOV、SIGN、DUE、DONE、HALF 為短字，使用者 2026-10-30 裁示照本檔欄名、
--       不另查字典）；旗標 IS_ 前綴；每表最後固定五欄。
-- 注意：本檔不含任何帳密、主機位址。被授權帳號 AP_USER 是殼 jar 的連線帳號名，不是機密。
-- 施工計畫：backlog/158-定期審查.md（第 158 項）。
-- ============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

-- ===== 預檢：必須以表的擁有者登入（不符就報錯結束，什麼都不建）
-- 下方用 USER_TABLES 判斷表在不在、synonym 指向 USER：用別的帳號登入會把表建進那個帳號的 schema；
-- 用 ALTER SESSION SET CURRENT_SCHEMA 代跑則表建進目前 schema、synonym 卻指向登入帳號。兩者都在這裡擋下。
DECLARE
  v_cnt NUMBER;
BEGIN
  IF USER <> SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') THEN
    RAISE_APPLICATION_ERROR(-20001, '請以表的擁有者（RD_USER）登入執行，不要用 ALTER SESSION SET CURRENT_SCHEMA 代跑'
      || '（目前登入 ' || USER || '、schema ' || SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') || '）');
  END IF;
  SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'IM_ACCT';
  IF v_cnt = 0 THEN
    RAISE_APPLICATION_ERROR(-20002, '目前登入帳號 ' || USER || ' 沒有 IM_ACCT 表：請以表的擁有者（RD_USER）登入，'
      || '且先執行 infra_manager_create_table.sql');
  END IF;
END;
/

-- ===== TABLE IM_ACCT_REVIEW（定期審查簽核紀錄）
DECLARE
  v_cnt NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_cnt FROM USER_TABLES WHERE TABLE_NAME = 'IM_ACCT_REVIEW';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE '
CREATE TABLE IM_ACCT_REVIEW
   (	REVIEW_ID NUMBER(12,0) NOT NULL,
	ACCT_ID VARCHAR2(20) NOT NULL,
	DUE_DATE DATE NOT NULL,
	REVIEW_STATE_CODE VARCHAR2(10) NOT NULL,
	INFRA_SIGN_BY VARCHAR2(30),
	INFRA_SIGN_ACT_BY VARCHAR2(30),
	INFRA_SIGN_DATE DATE,
	GOV_SIGN_BY VARCHAR2(30),
	GOV_SIGN_ACT_BY VARCHAR2(30),
	GOV_SIGN_DATE DATE,
	REJECT_HALF_CODE VARCHAR2(10),
	REJECT_BY VARCHAR2(30),
	REJECT_ACT_BY VARCHAR2(30),
	REJECT_DATE DATE,
	REJECT_REASON VARCHAR2(500 CHAR),
	DONE_DATE DATE,
	NEXT_DUE_DATE DATE,
	CANCEL_DATE DATE,
	CANCEL_REQ_ID VARCHAR2(20),
	CANCEL_DESCR VARCHAR2(200 CHAR),
	VERSION_NO NUMBER(9,0) DEFAULT 0 NOT NULL,
	STATUS NUMBER(1,0) DEFAULT 1 NOT NULL,
	CREATE_DATE DATE DEFAULT SYSDATE NOT NULL,
	CREATE_BY VARCHAR2(30) NOT NULL,
	UPDATE_DATE DATE,
	UPDATE_BY VARCHAR2(30),
	 CONSTRAINT PK_IM_ACCT_REVIEW PRIMARY KEY (REVIEW_ID) USING INDEX TABLESPACE FLOW_INDX
   ) TABLESPACE FLOW_DATA';
    DBMS_OUTPUT.PUT_LINE('IM_ACCT_REVIEW 已建立');
  ELSE
    DBMS_OUTPUT.PUT_LINE('IM_ACCT_REVIEW 已存在，略過');
  END IF;
END;
/

-- ===== SEQUENCE SEQ_IM_ACCT_REVIEW（低頻取號，NOCACHE，比照 SEQ_IM_ACCT_JOB_RUN）
DECLARE
  v_cnt NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_cnt FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_IM_ACCT_REVIEW';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_IM_ACCT_REVIEW MINVALUE 1 INCREMENT BY 1 START WITH 1 NOCACHE NOORDER NOCYCLE';
    DBMS_OUTPUT.PUT_LINE('SEQ_IM_ACCT_REVIEW 已建立');
  ELSE
    DBMS_OUTPUT.PUT_LINE('SEQ_IM_ACCT_REVIEW 已存在，略過');
  END IF;
END;
/

-- ===== INDEX（兩個；已存在就略過）
DECLARE
  v_cnt NUMBER;
  PROCEDURE create_index(p_name VARCHAR2, p_ddl VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_INDEXES WHERE INDEX_NAME = p_name;
    IF v_cnt = 0 THEN
      EXECUTE IMMEDIATE p_ddl;
      DBMS_OUTPUT.PUT_LINE(p_name || ' 已建立');
    ELSE
      DBMS_OUTPUT.PUT_LINE(p_name || ' 已存在，略過');
    END IF;
  END;
BEGIN
  -- 由帳號反查歷輪、清單帶出本輪進度
  create_index('IND_IM_ACCT_REVIEW_01',
    'CREATE INDEX IND_IM_ACCT_REVIEW_01 ON IM_ACCT_REVIEW (ACCT_ID, DUE_DATE) TABLESPACE FLOW_INDX');
  -- 唯一函數式索引：同一帳號最多一筆進行中（OPEN）；結束的輪（DONE／REJECTED／CANCELED）不佔用。
  -- 寫法同 IND_IM_ACCT_REQ_04。程式靠例外訊息裡的索引名稱辨識「兩人同時第一次簽」的撞鍵並改走 UPDATE，
  -- 所以本索引建後不得改名，也不得改成一般唯一約束（會連已結束的輪都擋住、同一帳號就不能有第二輪）。
  create_index('IND_IM_ACCT_REVIEW_02',
    'CREATE UNIQUE INDEX IND_IM_ACCT_REVIEW_02 ON IM_ACCT_REVIEW (CASE REVIEW_STATE_CODE WHEN ''OPEN'' THEN ACCT_ID END) TABLESPACE FLOW_INDX');
END;
/

-- ===== COMMENTS：新表（與 db\infra_manager_create_table.sql 同句，兩檔同步維護）
COMMENT ON TABLE IM_ACCT_REVIEW IS '定期審查簽核紀錄。每個帳號每一輪審查一筆：Infra 主管半與資訊治理主管半各簽一半、順序不限，兩半都簽完為 DONE（同一交易把 IM_ACCT 的上次審查日改為完成日、下次應審查日＝完成日＋180 天）；任一半駁回為 REJECTED（帳號標審查異常旗標，來源 REVIEW）；審查中帳號被申請單關卡 6 覆蓋（修改、停用、重新啟用、刪除生效）為 CANCELED。本輪紀錄在第一次簽核或駁回時才建立，待複審通知不建紀錄（防重寄看 IM_ACCT_NOTIFY_LOG）。審查狀態（正常／待複審／逾期未審／審查異常）不存欄位，由 IM_ACCT 的審查日期與旗標即時計算。資料來源：定期審查畫面、申請單關卡 6。主鍵：REVIEW_ID（序列 SEQ_IM_ACCT_REVIEW）。保留：永久';

COMMENT ON COLUMN IM_ACCT_REVIEW.REVIEW_ID IS '定期審查流水號；序列 SEQ_IM_ACCT_REVIEW 取號。IM_ACCT_HIST.REVIEW_ID 參照這欄';
COMMENT ON COLUMN IM_ACCT_REVIEW.ACCT_ID IS '受審查的帳號，參照 IM_ACCT.ACCT_ID';
COMMENT ON COLUMN IM_ACCT_REVIEW.DUE_DATE IS '本輪的應審查日：建立本輪時 IM_ACCT.NEXT_REVIEW_DATE 的值；主表為空（從未起算）時寫建立當天';
COMMENT ON COLUMN IM_ACCT_REVIEW.REVIEW_STATE_CODE IS '本輪結果：OPEN＝進行中（至少一半已簽）、DONE＝完成（兩半都簽）、REJECTED＝駁回（任一半駁回，已簽的另一半留存不帶到下一輪）、CANCELED＝取消（審查中帳號被申請單關卡 6 覆蓋）';
COMMENT ON COLUMN IM_ACCT_REVIEW.INFRA_SIGN_BY IS 'Infra 主管半簽核人工號（有效身分；需 REVIEW_SIGN_INFRA）';
COMMENT ON COLUMN IM_ACCT_REVIEW.INFRA_SIGN_ACT_BY IS 'Infra 主管半簽核的實際操作人工號；切換身分時與 INFRA_SIGN_BY 不同';
COMMENT ON COLUMN IM_ACCT_REVIEW.INFRA_SIGN_DATE IS 'Infra 主管半簽核時間';
COMMENT ON COLUMN IM_ACCT_REVIEW.GOV_SIGN_BY IS '資訊治理主管半簽核人工號（有效身分；需 REVIEW_SIGN_GOV）。與 Infra 半不得為同一有效身分或同一實際操作人';
COMMENT ON COLUMN IM_ACCT_REVIEW.GOV_SIGN_ACT_BY IS '資訊治理主管半簽核的實際操作人工號；切換身分時與 GOV_SIGN_BY 不同';
COMMENT ON COLUMN IM_ACCT_REVIEW.GOV_SIGN_DATE IS '資訊治理主管半簽核時間';
COMMENT ON COLUMN IM_ACCT_REVIEW.REJECT_HALF_CODE IS '駁回的那一半：INFRA＝Infra 主管半、GOV＝資訊治理主管半；REVIEW_STATE_CODE＝REJECTED 時必填';
COMMENT ON COLUMN IM_ACCT_REVIEW.REJECT_BY IS '駁回人工號（有效身分）';
COMMENT ON COLUMN IM_ACCT_REVIEW.REJECT_ACT_BY IS '駁回的實際操作人工號；切換身分時與 REJECT_BY 不同';
COMMENT ON COLUMN IM_ACCT_REVIEW.REJECT_DATE IS '駁回時間';
COMMENT ON COLUMN IM_ACCT_REVIEW.REJECT_REASON IS '駁回原因；駁回時必填。同時寫入 IM_ACCT.REVIEW_FLAG_REASON（主表旗標可能被之後的緊急存取覆蓋，本欄為原始紀錄）';
COMMENT ON COLUMN IM_ACCT_REVIEW.DONE_DATE IS '雙簽完成時間（到秒）；其日期＝IM_ACCT 新的 LAST_REVIEW_DATE';
COMMENT ON COLUMN IM_ACCT_REVIEW.NEXT_DUE_DATE IS '完成時寫進 IM_ACCT.NEXT_REVIEW_DATE 的新下次應審查日＝完成日＋180 天（稽核用）';
COMMENT ON COLUMN IM_ACCT_REVIEW.CANCEL_DATE IS '取消時間（＝造成取消的申請單關卡 6 回報時間）';
COMMENT ON COLUMN IM_ACCT_REVIEW.CANCEL_REQ_ID IS '造成取消的申請單號，參照 IM_ACCT_REQ.REQ_ID';
COMMENT ON COLUMN IM_ACCT_REVIEW.CANCEL_DESCR IS '取消說明（申請類型，例「修改申請生效」）';
COMMENT ON COLUMN IM_ACCT_REVIEW.VERSION_NO IS '樂觀鎖版本號，每次狀態變更＋1；更新時 WHERE 帶原值，影響 0 筆即回 409。default 0';
COMMENT ON COLUMN IM_ACCT_REVIEW.STATUS IS '資料狀態：固定 1。default 1';
COMMENT ON COLUMN IM_ACCT_REVIEW.CREATE_DATE IS '建立日期（含時分秒）。default SYSDATE';
COMMENT ON COLUMN IM_ACCT_REVIEW.CREATE_BY IS '建立人員工號（實際操作人）';
COMMENT ON COLUMN IM_ACCT_REVIEW.UPDATE_DATE IS '最後更新日期（含時分秒）。新增時為空';
COMMENT ON COLUMN IM_ACCT_REVIEW.UPDATE_BY IS '最後更新人員工號（實際操作人）';

-- ===== COMMENTS：既有欄位（定期審查啟用的代碼值與說明；只改說明、不動結構；重覆執行無害）
-- 與 db\infra_manager_create_table.sql 同句，兩檔同步維護。程式面分批上線（第 158 項 R3 簽核、R4 帳號異動連動、R7 排程），
-- COMMENT 一次寫到定案文字，避免每批都要再跑一次 DB。
COMMENT ON TABLE IM_ACCT IS '帳號主表。被管理的帳號／憑證／門禁卡清冊，存目前生效的內容；新增申請送出時建立（狀態 REVIEW），關卡 6 執行回報時依申請內容更新；審查異常旗標 5 欄（IS_REVIEW_FLAG、REVIEW_FLAG_REASON、REVIEW_FLAG_SOURCE_CODE、REVIEW_FLAG_DATE、REVIEW_FLAG_BY）由緊急存取與定期審查駁回寫入（後標記者覆蓋），修改／停用／重新啟用申請在關卡 7 複驗通過時清除（決策 D42）；審查日期兩欄由關卡 6 生效與定期審查雙簽完成寫入。帳號單號（ACCT_ID）＝建立它的新增申請 REQ_ID。資料來源：IM_ACCT_REQ 流程寫入、緊急存取旗標、定期審查（旗標與審查日期）。主鍵：ACCT_ID。保留：永久（刪除為狀態 DELETED）';
COMMENT ON COLUMN IM_ACCT.LAST_REVIEW_DATE IS '上次審查日。帳號生效（關卡 6 第一次回報）且符合審查條件時寫生效日：新增、重新啟用一律寫，修改在新內容符合且（舊內容不符合或本欄為空）時寫；定期審查雙簽完成時改為完成日。審查條件＝資產分類 IS_REVIEW_TRIGGER＝1、類別 DOOR、IS_PRIV＝1 任一';
COMMENT ON COLUMN IM_ACCT.NEXT_REVIEW_DATE IS '下次應審查日＝LAST_REVIEW_DATE＋180 天。審查狀態依此即時計算（不存欄位）：掛審查異常旗標＝審查異常；距本欄 30 天內含當天＝待複審；已過本欄或 LAST_REVIEW_DATE 為空＝逾期未審；其餘正常。只對已核准（APPROVED）且符合審查條件的帳號計算';
COMMENT ON COLUMN IM_ACCT.REVIEW_FLAG_SOURCE_CODE IS '審查異常來源：EMG＝緊急存取（退回且申請人回報為異動、或異常結案登記有帳號異動）、REVIEW＝定期審查駁回。單一旗標，後標記者覆蓋前者（原始原因另見 IM_EMG_ACCESS、IM_ACCT_REVIEW）';
COMMENT ON COLUMN IM_ACCT_HIST.REVIEW_ID IS '來源定期審查流水號；SOURCE_CODE＝REVIEW 時有值，參照 IM_ACCT_REVIEW.REVIEW_ID';
COMMENT ON COLUMN IM_ACCT_HIST.SOURCE_CODE IS '事件來源：REQ＝簽核流程、SYS＝系統排程、EMG＝緊急存取、REVIEW＝定期審查';
COMMENT ON COLUMN IM_ACCT_HIST.ACTION_CODE IS '動作：SUBMIT＝送出申請、RESUB＝重新送出、APPROVE＝核准、REJECT＝退件、EXEC_DONE＝回報執行完畢（帳號生效）、VERIFY_OK＝複驗通過、VERIFY_REJECT＝複驗退回、EXPIRE＝到期轉為已逾期、EMG_MODIFY＝緊急存取異動（依申請人回報，主管處理時寫入）、EMG_CLOSE_MODIFY＝緊急存取結案登記異動、FLAG_CLEAR＝審查異常旗標清除、REVIEW_DONE＝定期審查完成（雙簽，寫第二半簽核時）、REVIEW_REJECT＝定期審查駁回（單半簽核與取消不寫歷史，細節在 IM_ACCT_REVIEW）';
COMMENT ON COLUMN IM_ACCT_NOTIFY_LOG.EVENT_CODE IS '通知事件：SUBMIT＝申請送出、RESUB＝重新送出、NEXT_STAGE＝進入下一關（關卡 2～4 核准）、TO_EXEC＝待執行（關卡 5 核准）、REJECT＝退件、EXEC_DONE＝回報執行完畢、VERIFY_REJECT＝複驗退回、EXPIRY_7D＝到期前 7 天、EXPIRED＝轉為已逾期、WEEKLY_SUMMARY＝每週一彙總、JOB_FAIL＝排程失敗、EMG_SUBMIT＝緊急存取送出、EMG_OVERDUE＝緊急存取逾期未補核准、EMG_RETURN＝緊急存取退回、REVIEW_DUE＝進入待複審（排程 J04，每輪每收件人一次）、REVIEW_REJECT＝定期審查駁回';
COMMENT ON COLUMN IM_ACCT_NOTIFY_LOG.REF_TYPE_CODE IS '參照類型：REQ＝申請單、ACCT＝帳號、WEEK＝週彙總、JOB＝排程、EMG＝緊急存取單、REVIEW＝定期審查';
COMMENT ON COLUMN IM_ACCT_NOTIFY_LOG.REF_ID IS '參照鍵：REQ＝REQ_ID；ACCT＝ACCT_ID_到期日yyyymmdd（到期日改變即視為新的一輪）；WEEK＝該週一 yyyymmdd；JOB＝RUN_ID；EMG＝EMG_ID（EMG_OVERDUE 以 EVENT_CODE＋REF_ID 判斷是否已通知）；REVIEW＝REVIEW_DUE 為 ACCT_ID_下次應審查日yyyymmdd（本輪是否已寄的判斷鍵，雙簽完成後下次應審查日改變即為新的一輪）、REVIEW_REJECT 為 REVIEW_ID';
COMMENT ON COLUMN IM_ACCT_JOB_RUN.JOB_CODE IS '排程代碼：J01＝到期處理、J02＝每週彙總、J03＝緊急存取逾期通知、J04＝定期審查待複審通知';
COMMENT ON COLUMN IM_ACCT_JOB_RUN.AFFECTED_CNT IS '處理筆數（J01＝轉逾期筆數＋寄出轉逾期信的帳號數＋寄出到期前 7 天信的帳號數；J02＝彙總列出的帳號數（已逾期帳號＋逾期未審帳號），本週已寄過則為 0；J03＝本次寫入逾期通知紀錄的緊急存取單數，每張算 1 不論收件人幾位；J04＝本次寄出待複審信的帳號數，每個帳號算 1 不論收件人幾位）。default 0';

-- ===== PUBLIC SYNONYM 與授權給 AP_USER（synonym 已存在就略過；GRANT 重覆執行無害）
-- 比照 infra_manager_phase2_emg.sql：表與序列建 PUBLIC synonym（同名、指向目前連線帳號 USER，不寫死 schema 名）。
-- 權限採最小範圍：IM_ACCT_REVIEW 只有狀態更新、無刪除 → SELECT／INSERT／UPDATE；序列 → SELECT（取號 NEXTVAL 用）。
-- 若同名 PUBLIC synonym 已存在但指向別的 schema，計數為 0 → CREATE 會以 ORA-00955 停下，這是真的撞名，要人工處理。
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
  create_public_synonym('IM_ACCT_REVIEW');
  create_public_synonym('SEQ_IM_ACCT_REVIEW');
END;
/
GRANT SELECT, INSERT, UPDATE ON IM_ACCT_REVIEW TO AP_USER;
GRANT SELECT ON SEQ_IM_ACCT_REVIEW TO AP_USER;

-- ===== 執行後自我檢查（唯讀）
-- 期望：第一句 1 列（IM_ACCT_REVIEW，TABLESPACE_NAME 為 FLOW_DATA）；
--       第二句 3 列（PK_IM_ACCT_REVIEW UNIQUE、IND_IM_ACCT_REVIEW_01 NONUNIQUE、IND_IM_ACCT_REVIEW_02 UNIQUE；TABLESPACE_NAME 皆 FLOW_INDX）；
--       第三句 1 列（IND_IM_ACCT_REVIEW_02 的運算式為 CASE "REVIEW_STATE_CODE" WHEN 'OPEN' THEN "ACCT_ID" END）；
--       第四句 1 列（SEQ_IM_ACCT_REVIEW，LAST_NUMBER 1）；
--       第五句 COL_CNT 26、NO_COMMENT_CNT 0；
--       第六句 2 列（PUBLIC synonym IM_ACCT_REVIEW、SEQ_IM_ACCT_REVIEW，TABLE_OWNER 皆為 RD_USER）；
--       第七句 4 列（AP_USER：IM_ACCT_REVIEW 的 INSERT、SELECT、UPDATE；SEQ_IM_ACCT_REVIEW 的 SELECT）。
-- 以下解除遇錯即停：互動式 SQL*Plus 用 @ 執行本檔後，同一連線再打錯指令才不會直接結束 SQL*Plus。
WHENEVER SQLERROR CONTINUE
SELECT TABLE_NAME, TABLESPACE_NAME FROM USER_TABLES WHERE TABLE_NAME = 'IM_ACCT_REVIEW';
SELECT INDEX_NAME, UNIQUENESS, TABLESPACE_NAME FROM USER_INDEXES WHERE TABLE_NAME = 'IM_ACCT_REVIEW' ORDER BY INDEX_NAME;
SELECT INDEX_NAME, COLUMN_EXPRESSION FROM USER_IND_EXPRESSIONS WHERE INDEX_NAME = 'IND_IM_ACCT_REVIEW_02';
SELECT SEQUENCE_NAME, LAST_NUMBER FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_IM_ACCT_REVIEW';
SELECT COUNT(*) AS COL_CNT, SUM(CASE WHEN COMMENTS IS NULL THEN 1 ELSE 0 END) AS NO_COMMENT_CNT
  FROM USER_COL_COMMENTS WHERE TABLE_NAME = 'IM_ACCT_REVIEW';
SELECT SYNONYM_NAME, TABLE_OWNER FROM ALL_SYNONYMS
 WHERE OWNER = 'PUBLIC' AND SYNONYM_NAME IN ('IM_ACCT_REVIEW', 'SEQ_IM_ACCT_REVIEW') ORDER BY SYNONYM_NAME;
SELECT GRANTEE, TABLE_NAME, PRIVILEGE FROM USER_TAB_PRIVS
 WHERE TABLE_NAME IN ('IM_ACCT_REVIEW', 'SEQ_IM_ACCT_REVIEW') ORDER BY TABLE_NAME, PRIVILEGE;
