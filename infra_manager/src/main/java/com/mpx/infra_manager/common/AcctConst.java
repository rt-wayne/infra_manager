package com.mpx.infra_manager.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：代碼值常數（值域依 im_acct_insp_create_table.sql 的 COMMENT）與共用訊息（spec §3.2）
//           測試第 1 輪修正：IM_USER 改 schema 限定名稱（新增 EXISTING_USER_TABLE）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0 重寫（spec_im_acct_insp_v2.0.md；Table 規劃 v0.2 第 5、6 節）：
//           單號 AR／IN；申請類型 5 種（刪 EXTEND）與前提；申請單狀態 5 種（刪 VOID）；帳號狀態 6 種（加 EXPIRED、刪 CONFIRM／VOID）；
//           關卡 1～7；歷史動作（刪 TO_EXEC／EXTEND_DONE／VOID／FLAG_CLEAR，加 EXPIRE）；附件、通知事件、排程、管理軌跡代碼；
//           第一階段用到的權限碼；登入時效常數（spec §2.1、§9.4：程式常數）。
//           移除 EXISTING_USER_TABLE（本模組不再引用既有模組使用者表）、角色碼常數（程式不判斷角色名稱，spec §2.3）、
//           審查依據碼（M14 不再存 REVIEW_BASIS_CODE）；訊息移到 AcctMsg
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 變更單 #2：新增通知收件角色常數 MAIL_ROLE_EXPIRY（GOV）、MAIL_ROLE_JOB_FAIL（SYS_ADMIN），
//           取代 properties key（依 SA 7.3 固定）；只用來查收件人，不用於權限判斷
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 28 項：TOKEN_IDLE_MINUTES 由 30 改為 TOKEN_MAX_HOURS * 60（480 分，實際只剩登入滿 8 小時失效；
//           前端 token 改跨分頁共用，一天登入一次）。閒置檢查保留，日後要縮短只改此值
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S2（緊急存取）：單號 EA；計算狀態碼 5 種（PENDING／OVERDUE／APPROVED／RETURNED／CLOSED）；
//           回報、處理、結案、受影響帳號登記來源代碼；補核准期限 24 小時與預估時長 1～24；
//           通知事件 EMG_SUBMIT／EMG_OVERDUE／EMG_RETURN、參照 EMG；權限碼 EMG_APPLY／EMG_VIEW_ALL／EMG_DECIDE
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4a：回報受影響帳號上限 EMG_AFFECT_MAX＝20（S4-③）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-1：歷史來源 EMG 與動作 EMG_MODIFY（S4-⑧）、審查異常旗標來源 EMG（S4-①）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S6：歷史動作 EMG_CLOSE_MODIFY、自核標籤「自行結案」（S6-③）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S7：排程代碼加 J03（緊急存取逾期通知）並納入 JOB_CODES
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S8：緊急存取清單篩選值加 UNDECIDED（未處理＝待補核准＋逾期未補核准，首頁卡「待補核准」點進來用；
//           不是計算狀態、不會出現在 EMG_STATE_CODE），篩選可接受的值另列 EMG_STATE_FILTERS（＝EMG_STATES＋UNDECIDED，五種狀態只寫一份）
// AI版本  : Claude Fable 5 (claude-fable-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S10：啟用歷史動作 FLAG_CLEAR（審查異常旗標清除，裁示㉔）；
//           FLAG_CLEAR_REQ_TYPES＝關卡 7 複驗通過時清旗標的申請類型（D42 修改／停用＋S10 裁示 ①B 加重新啟用）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S10 審查修正：加 isFlagHintStage（明細帶旗標警示的關卡 2～5、7，前後端只此一份）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-14
// 變更說明: BACKLOG 第 46 項 S10 複審：FLAG_CLEAR_REQ_TYPES 註解改寫成直接寫內容「重新啟用也清（S10 開工裁示）」；
//           上方 Fable 區塊的「S10 裁示 ①B」即指這項
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 1（角色權限維護介面）：權限碼 SYS_ROLE／SYS_EXECUTOR；管理軌跡動作 ROLE_GRANT／ROLE_REVOKE／
//           EXEC_GRANT／EXEC_REVOKE（DDL 第 619 行預留值）；DENY 代碼 NO_PERM／LAST_SYS_ROLE／IN_FLIGHT；
//           名單維護常數（原因欄寬、全域鎖等待秒數、被擋單號在訊息與軌跡的上限、CONTENT_JSON 位元組上限）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 3 回合 1（角色權限設定頁後端基礎）：軌跡動作 ROLE_PERM_SET（一次存檔一筆）；
//           權限碼 ACC_EXPORT（後端原本沒有此常數、只在前端判斷）；ADMIN_VISIBLE_PERMS＝角色權限設定頁可見的 20 碼
//           （裁示 ⑩A／⑮A：有功能的碼才放出來、不看 DB 的 PHASE_CODE；新功能上線時加 PERM_* 常數的同一 commit 要加進清單，
//           SqlConstantScanTest 強制每個 PERM_* 常數都在清單內）；DENY 軌跡每碼單號上限 ADMIN_BLOCKED_PER_PERM_LOG_MAX
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 7 回合 2：刪 LOGIN_MAX_FAIL（登入改走 WPX，連錯鎖定交給 WPX）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-29
// 變更說明: 加 STAGE_HEAD_DECIDE（關卡 4 核准時判斷是否跳過關卡 5 資訊主管；不符合審查條件即跳過）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: BACKLOG 第 158 項 R2（定期權限審查）：權限碼 REVIEW_LIST／REVIEW_SIGN_INFRA／REVIEW_SIGN_GOV 進可見清單（20 → 23 碼）；
//           審查狀態碼 4 種（ABNORMAL／OVERDUE／DUE／NORMAL，由 ReviewDao.REVIEW_STATE_EXPR 算出）與待複審天數 REVIEW_DUE_DAYS＝30
// ============================================================

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class AcctConst {

	private AcctConst() {
	}

	// 單號前綴（IM_DOC_SEQ.DOC_TYPE_ID，Table 第 5 節）
	public static final String DOC_AR = "AR";
	public static final String DOC_IN = "IN";
	public static final String DOC_EA = "EA";

	// 申請類型 REQ_TYPE_CODE（Table 6.5）
	public static final String REQ_ADD = "ADD";
	public static final String REQ_MODIFY = "MODIFY";
	public static final String REQ_DISABLE = "DISABLE";
	public static final String REQ_ENABLE = "ENABLE";
	public static final String REQ_DELETE = "DELETE";
	public static final List<String> REQ_TYPES = List.of(REQ_ADD, REQ_MODIFY, REQ_DISABLE, REQ_ENABLE, REQ_DELETE);
	/** 對既有帳號可發起的類型，順序即 allowedReqTypes 的回傳順序 */
	public static final List<String> CHANGE_REQ_TYPES = List.of(REQ_MODIFY, REQ_DISABLE, REQ_ENABLE, REQ_DELETE);
	/** 只填原因、內容複製主表的類型（spec §4.3） */
	public static final List<String> REASON_ONLY_TYPES = List.of(REQ_DISABLE, REQ_DELETE);
	/** 關卡 7 複驗通過時清審查異常旗標的類型（D42：修改／停用；重新啟用也清（S10 開工裁示），否則已停用帳號的旗標沒有正規清除路徑） */
	public static final List<String> FLAG_CLEAR_REQ_TYPES = List.of(REQ_MODIFY, REQ_DISABLE, REQ_ENABLE);
	public static final Map<String, String> REQ_TYPE_NAMES;

	// 申請單狀態 REQ_STATE_CODE
	public static final String RS_REVIEW = "REVIEW";
	public static final String RS_EXEC = "EXEC";
	public static final String RS_VERIFY = "VERIFY";
	public static final String RS_APPROVED = "APPROVED";
	public static final String RS_REJECTED = "REJECTED";
	public static final List<String> REQ_STATES = List.of(RS_REVIEW, RS_EXEC, RS_VERIFY, RS_APPROVED, RS_REJECTED);
	public static final List<String> RS_IN_FLIGHT = List.of(RS_REVIEW, RS_EXEC, RS_VERIFY);

	// 帳號狀態 ACCT_STATE_CODE（與申請單狀態是不同集合）
	public static final String AS_REVIEW = "REVIEW";
	public static final String AS_APPROVED = "APPROVED";
	public static final String AS_EXPIRED = "EXPIRED";
	public static final String AS_DISABLED = "DISABLED";
	public static final String AS_REJECTED = "REJECTED";
	public static final String AS_DELETED = "DELETED";
	public static final List<String> ACCT_STATES = List.of(AS_REVIEW, AS_APPROVED, AS_EXPIRED, AS_DISABLED, AS_REJECTED,
			AS_DELETED);

	/** 申請前提：目標帳號 ACCT_STATE_CODE（Table 6.5；ADD 無前提） */
	public static final Map<String, List<String>> REQ_PREREQ;

	// 帳號來源
	public static final String SOURCE_APPLY = "APPLY";

	// 關卡（IM_ACCT_APPR_STAGE.STAGE_ID，Table 6.4）
	public static final int STAGE_SUBMIT = 1;
	public static final int STAGE_FIRST_APPR = 2;
	public static final int STAGE_LAST_APPR = 5;
	/** 此關核准時判斷是否跳過關卡 5（資訊主管）：申請內容不符合審查條件（ReviewRule.matches）就直接進關卡 6 */
	public static final int STAGE_HEAD_DECIDE = 4;
	public static final int STAGE_EXEC = 6;
	public static final int STAGE_VERIFY = 7;

	/** 申請單明細帶審查異常警示的關卡：簽核 2～5、複驗 7（spec §3.4；關卡 6 執行回報不提示） */
	public static boolean isFlagHintStage(Integer stageId) {
		return stageId != null && ((stageId >= STAGE_FIRST_APPR && stageId <= STAGE_LAST_APPR) || stageId == STAGE_VERIFY);
	}

	// 歷史 IM_ACCT_HIST
	public static final String HIST_SRC_REQ = "REQ";
	public static final String HIST_SRC_SYS = "SYS";
	/** 緊急存取事件（EMG_ID 有值、REQ_ID／STAGE_ID／APPLY_DEPT 空） */
	public static final String HIST_SRC_EMG = "EMG";
	public static final String ACT_SUBMIT = "SUBMIT";
	public static final String ACT_RESUB = "RESUB";
	public static final String ACT_APPROVE = "APPROVE";
	public static final String ACT_REJECT = "REJECT";
	public static final String ACT_EXEC_DONE = "EXEC_DONE";
	public static final String ACT_VERIFY_OK = "VERIFY_OK";
	public static final String ACT_VERIFY_REJECT = "VERIFY_REJECT";
	public static final String ACT_EXPIRE = "EXPIRE";
	/** 緊急存取異動：主管處理（補核准或退回）時依申請人回報的受影響帳號逐筆寫入（S4-⑧） */
	public static final String ACT_EMG_MODIFY = "EMG_MODIFY";
	/** 緊急存取結案登記異動：異常結案發現有異動時依結案登記的受影響帳號逐筆寫入，不覆蓋申請人回報（D13，S6） */
	public static final String ACT_EMG_CLOSE_MODIFY = "EMG_CLOSE_MODIFY";
	/** 審查異常旗標清除：FLAG_CLEAR_REQ_TYPES 的申請在關卡 7 複驗通過時寫入（D42、裁示㉔；S10） */
	public static final String ACT_FLAG_CLEAR = "FLAG_CLEAR";
	/** 判斷 IS_SELF_ACTION 的動作（spec §4.8） */
	public static final List<String> SELF_CHECK_ACTIONS = List.of(ACT_APPROVE, ACT_REJECT, ACT_EXEC_DONE, ACT_VERIFY_OK,
			ACT_VERIFY_REJECT);
	public static final String SELF_LABEL_APPLICANT = "自行核准";
	public static final String SELF_LABEL_SAME_PERSON = "同人兼任";
	/** EMG_CLOSE_MODIFY 的結案人即申請人（S6-③） */
	public static final String SELF_LABEL_CLOSE = "自行結案";

	// 附件 ATTACH_TYPE_CODE
	public static final String ATTACH_DOC = "DOC";
	public static final String ATTACH_PROOF = "PROOF";

	// 通知事件 EVENT_CODE、參照 REF_TYPE_CODE、結果 RESULT_CODE（Table 6.5）
	public static final String EV_SUBMIT = "SUBMIT";
	public static final String EV_RESUB = "RESUB";
	public static final String EV_NEXT_STAGE = "NEXT_STAGE";
	public static final String EV_TO_EXEC = "TO_EXEC";
	public static final String EV_REJECT = "REJECT";
	public static final String EV_EXEC_DONE = "EXEC_DONE";
	public static final String EV_VERIFY_REJECT = "VERIFY_REJECT";
	public static final String EV_EXPIRY_7D = "EXPIRY_7D";
	public static final String EV_EXPIRED = "EXPIRED";
	public static final String EV_WEEKLY_SUMMARY = "WEEKLY_SUMMARY";
	public static final String EV_JOB_FAIL = "JOB_FAIL";
	public static final String EV_EMG_SUBMIT = "EMG_SUBMIT";
	public static final String EV_EMG_OVERDUE = "EMG_OVERDUE";
	public static final String EV_EMG_RETURN = "EMG_RETURN";
	public static final String REF_REQ = "REQ";
	public static final String REF_ACCT = "ACCT";
	public static final String REF_WEEK = "WEEK";
	public static final String REF_JOB = "JOB";
	public static final String REF_EMG = "EMG";
	public static final String NR_SUCCESS = "SUCCESS";
	public static final String NR_FAIL = "FAIL";
	public static final String NR_SKIP = "SKIP";

	// 排程 JOB_CODE、RESULT_CODE
	public static final String JOB_J01 = "J01";
	public static final String JOB_J02 = "J02";
	/** J03 緊急存取逾期通知（BACKLOG 第 46 項 S7）：基準為時間點（到秒），J01／J02 為日期 */
	public static final String JOB_J03 = "J03";
	public static final List<String> JOB_CODES = List.of(JOB_J01, JOB_J02, JOB_J03);
	public static final String JR_RUNNING = "RUNNING";
	public static final String JR_SUCCESS = "SUCCESS";
	public static final String JR_PARTIAL = "PARTIAL";
	public static final String JR_FAIL = "FAIL";

	// 管理軌跡 IM_ACCT_ADMIN_LOG
	public static final String ADM_IMPERSONATE = "IMPERSONATE";
	public static final String ADM_IMPERSONATE_END = "IMPERSONATE_END";
	/** 角色指派／移除、執行人對照指派／移除（BACKLOG 第 89 項；值為 DDL 第 619 行預留） */
	public static final String ADM_ROLE_GRANT = "ROLE_GRANT";
	public static final String ADM_ROLE_REVOKE = "ROLE_REVOKE";
	public static final String ADM_EXEC_GRANT = "EXEC_GRANT";
	public static final String ADM_EXEC_REVOKE = "EXEC_REVOKE";
	/** 角色權限設定存檔（BACKLOG 第 89 項第三頁；一次存檔可同時有加有拿、只寫一筆；TARGET_USER_ID 留空、roleId 在 CONTENT_JSON） */
	public static final String ADM_ROLE_PERM_SET = "ROLE_PERM_SET";
	public static final String ADM_SUCCESS = "SUCCESS";
	public static final String ADM_DENY = "DENY";
	/** DENY 軌跡 CONTENT_JSON.denyCode：無權限、移除後角色指派權限（SYS_ROLE）無人持有、移除後在途案件無人能處理 */
	public static final String DENY_NO_PERM = "NO_PERM";
	public static final String DENY_LAST_SYS_ROLE = "LAST_SYS_ROLE";
	public static final String DENY_IN_FLIGHT = "IN_FLIGHT";
	/** 名單維護的原因欄寬（IM_ACCT_ADMIN_LOG.REASON VARCHAR2(500 CHAR)） */
	public static final int ADMIN_REASON_MAX = 500;
	/** 名單維護全域互斥鎖（IM_ACCT_ROLE 的 GENERAL 列 FOR UPDATE WAIT n）的等待秒數；逾時 ORA-30006 → E26 */
	public static final int ADMIN_LOCK_WAIT_SECONDS = 5;
	/** 在途擋下訊息（E23）最多列幾筆單號，其餘以「等共 N 件」帶過 */
	public static final int ADMIN_BLOCKED_MSG_MAX = 20;
	/** DENY 軌跡 CONTENT_JSON 最多列幾筆被擋單號 */
	public static final int ADMIN_BLOCKED_LOG_MAX = 50;
	/**
	 * 權限設定存檔（ROLE_PERM_SET）的 DENY 軌跡依碼分組列單號時，每碼最多幾筆：最多 6 個卡單碼 × 20 筆約 2.9 KB，
	 * 放到 50 筆會到約 5.4 KB 超過 4000 bytes 上限，所以比名單維護的 50 筆少
	 */
	public static final int ADMIN_BLOCKED_PER_PERM_LOG_MAX = 20;
	/** CONTENT_JSON 寫入前的 UTF-8 位元組上限：欄位宣告 VARCHAR2(2000 CHAR) 仍受單欄 4000 bytes 限制（BACKLOG 第 11 項），超過就逐筆丟單號 */
	public static final int ADMIN_CONTENT_MAX_BYTES = 3900;

	/** 所有啟用中使用者自動擁有、不存 IM_ACCT_ROLE_USER 的角色（Table 6.1）；只用來展開權限，不判斷角色名稱 */
	public static final String ROLE_GENERAL = "GENERAL";
	/** 到期前 7 天、轉已逾期、每週彙總信的收件角色（SA 7.3；spec §4.12 變更單 #2）；只用來查收件人，不判斷權限 */
	public static final String MAIL_ROLE_EXPIRY = "GOV";
	/** 排程失敗信的收件角色（SA 7.3；spec §4.12 變更單 #2）；只用來查收件人，不判斷權限 */
	public static final String MAIL_ROLE_JOB_FAIL = "SYS_ADMIN";

	// 權限碼（IM_ACCT_PERM，Table 6.2；只列第一階段程式會判斷的；關卡權限取自 IM_ACCT_APPR_STAGE.PERM_ID）
	public static final String PERM_ACC_APPLY = "ACC_APPLY";
	public static final String PERM_ACC_SELF_SERVICE = "ACC_SELF_SERVICE";
	public static final String PERM_ACC_LIST_FULL = "ACC_LIST_FULL";
	public static final String PERM_ACC_EXPIRY_ALL = "ACC_EXPIRY_ALL";
	/** 帳號權限清單匯出：後端沒有判斷點、只在前端判斷（BACKLOG 第 81 項）；放進可見清單讓角色權限設定頁能授予 */
	public static final String PERM_ACC_EXPORT = "ACC_EXPORT";
	public static final String PERM_INSP_VIEW = "INSP_VIEW";
	public static final String PERM_INSP_CREATE = "INSP_CREATE";
	public static final String PERM_INSP_RESOLVE = "INSP_RESOLVE";
	public static final String PERM_SYS_IMPERSONATE = "SYS_IMPERSONATE";
	public static final String PERM_SYS_JOB_RUN = "SYS_JOB_RUN";
	/** 角色指派維護、執行人對照維護（BACKLOG 第 89 項） */
	public static final String PERM_SYS_ROLE = "SYS_ROLE";
	public static final String PERM_SYS_EXECUTOR = "SYS_EXECUTOR";
	public static final String PERM_EMG_APPLY = "EMG_APPLY";
	public static final String PERM_EMG_VIEW_ALL = "EMG_VIEW_ALL";
	public static final String PERM_EMG_DECIDE = "EMG_DECIDE";
	/** 定期審查（BACKLOG 第 158 項）：清單與看全部（裁示 ⑬A）、Infra 半簽核、治理半簽核（簽核判斷點 R3 上線） */
	public static final String PERM_REVIEW_LIST = "REVIEW_LIST";
	public static final String PERM_REVIEW_SIGN_INFRA = "REVIEW_SIGN_INFRA";
	public static final String PERM_REVIEW_SIGN_GOV = "REVIEW_SIGN_GOV";
	/** 關卡 2～5 核准與關卡 7 複驗：程式從 IM_ACCT_APPR_STAGE.PERM_ID 取，不另設 PERM_* 常數；角色權限設定頁要能授予，所以列在這裡 */
	public static final List<String> PERM_STAGE_CODES = List.of("APPR_G2", "APPR_G3", "APPR_G4", "APPR_G5", "VERIFY_G7");
	/**
	 * 角色權限設定頁可見的權限碼（裁示 ⑩A／⑮A）：目前有功能的 23 碼，隱藏的 5 碼（SYS_AUDIT_VIEW、STATS_VIEW、INSP_EXPORT、
	 * MAINT_*）沒有判斷點，授了也沒效果，不放出來；隱藏碼既有的授予列原樣保留。可見與否不看 DB 的 PHASE_CODE（語意對不上）。
	 * 新功能上線時：加 PERM_* 常數的同一 commit 要加進這裡（SqlConstantScanTest 強制）
	 */
	public static final List<String> ADMIN_VISIBLE_PERMS = Stream.concat(
			Stream.of(PERM_ACC_APPLY, PERM_ACC_SELF_SERVICE, PERM_ACC_LIST_FULL, PERM_ACC_EXPIRY_ALL, PERM_ACC_EXPORT),
			Stream.concat(PERM_STAGE_CODES.stream(),
					Stream.of(PERM_INSP_VIEW, PERM_INSP_CREATE, PERM_INSP_RESOLVE, PERM_SYS_ROLE, PERM_SYS_EXECUTOR,
							PERM_SYS_IMPERSONATE, PERM_SYS_JOB_RUN, PERM_EMG_APPLY, PERM_EMG_VIEW_ALL, PERM_EMG_DECIDE,
							PERM_REVIEW_LIST, PERM_REVIEW_SIGN_INFRA, PERM_REVIEW_SIGN_GOV)))
			.toList();

	// 類別、期限、門禁（Table 6.5、spec §4.9）
	public static final String CATG_DOOR = "DOOR";
	public static final String EXPIRY_PERM = "PERM";
	public static final String EXPIRY_DATE = "DATE";
	public static final List<String> EXPIRY_TYPES = List.of(EXPIRY_PERM, EXPIRY_DATE);
	public static final List<String> DOOR_SCOPES = List.of("TEST", "FULL");
	/** 一般帳號可選的權限範圍（DOOR 只給門禁卡，由後端強制） */
	public static final List<String> ACCESS_LEVELS = List.of("RO", "OP", "ADMIN");
	public static final String DOOR_SYS_NAME = "機房門禁管制系統";
	public static final String DOOR_ACCESS_LEVEL = "DOOR";
	public static final String DOOR_NO_CARD_NAME = "（門禁卡，待發卡）";

	// 審查、到期
	public static final int REVIEW_CYCLE_DAYS = 180;
	/** 待複審範圍：下次應審查日 ≤ 今天＋30（SA §5.10；BACKLOG 第 158 項） */
	public static final int REVIEW_DUE_DAYS = 30;
	/**
	 * 審查狀態（不存欄位，由 ReviewDao.REVIEW_STATE_EXPR 算出；BACKLOG 第 158 項裁示 ⑨A／⑥A／⑩A）：
	 * 審查異常（掛旗標且未刪除，優先）、逾期未審（上次或下次為空、或下次 < 今天）、待複審（下次 ≤ 今天＋30）、正常；不在審查範圍為 null
	 */
	public static final String RVS_ABNORMAL = "ABNORMAL";
	public static final String RVS_OVERDUE = "OVERDUE";
	public static final String RVS_DUE = "DUE";
	public static final String RVS_NORMAL = "NORMAL";
	public static final List<String> REVIEW_STATES = List.of(RVS_ABNORMAL, RVS_OVERDUE, RVS_DUE, RVS_NORMAL);
	/** 到期提醒範圍：到期日 ≤ 今天＋30（spec §4.11） */
	public static final int EXPIRY_WARN_DAYS = 30;
	/** J01 到期前 7 天信 */
	public static final int EXPIRY_NOTICE_DAYS = 7;

	// 巡檢
	public static final String RESULT_NORMAL = "NORMAL";
	public static final String RESULT_EXCPT = "EXCPT";
	public static final List<String> RESULT_CODES = List.of(RESULT_NORMAL, RESULT_EXCPT);
	public static final String INSP_NORMAL = "NORMAL";
	public static final String INSP_EXCPT = "EXCPT";
	public static final String INSP_FIXED = "FIXED";
	public static final List<String> INSP_STATES = List.of(INSP_NORMAL, INSP_EXCPT, INSP_FIXED);

	// 緊急存取（BACKLOG 第 46 項；值域依 IM_EMG_ACCESS 的 COMMENT）
	/** 計算狀態 EMG_STATE_CODE（不存欄位，由 EmgDao.STATE_EXPR 算出）：待補核准、逾期未補核准、已補核准、已退回、已退回（已結案） */
	public static final String ES_PENDING = "PENDING";
	public static final String ES_OVERDUE = "OVERDUE";
	public static final String ES_APPROVED = "APPROVED";
	public static final String ES_RETURNED = "RETURNED";
	public static final String ES_CLOSED = "CLOSED";
	public static final List<String> EMG_STATES = List.of(ES_PENDING, ES_OVERDUE, ES_APPROVED, ES_RETURNED, ES_CLOSED);
	/** 清單篩選專用（S8）：未處理＝PENDING＋OVERDUE＝DECIDE_CODE 為空，與首頁卡「待補核准」同口徑；不是計算狀態 */
	public static final String ES_UNDECIDED = "UNDECIDED";
	/** 清單狀態篩選可接受的值：五種計算狀態＋UNDECIDED */
	public static final List<String> EMG_STATE_FILTERS = Stream.concat(EMG_STATES.stream(), Stream.of(ES_UNDECIDED)).toList();
	/** 申請人回報 REPORT_TYPE_CODE */
	public static final String EMG_REPORT_NONE = "NONE";
	public static final String EMG_REPORT_MODIFIED = "MODIFIED";
	/** 主管處理 DECIDE_CODE */
	public static final String EMG_DECIDE_APPROVE = "APPROVE";
	public static final String EMG_DECIDE_RETURN = "RETURN";
	/** 結案發現 CLOSE_RESULT_CODE */
	public static final String EMG_CLOSE_NO_CHANGE = "NO_CHANGE";
	public static final String EMG_CLOSE_CHANGED = "CHANGED";
	/** 受影響帳號登記來源 IM_EMG_AFFECT_ACCT.AFFECT_TYPE_CODE */
	public static final String AFFECT_REPORT = "REPORT";
	public static final String AFFECT_CLOSE = "CLOSE";
	/** 審查異常旗標來源 IM_ACCT.REVIEW_FLAG_SOURCE_CODE：緊急存取（退回且申請人回報為異動，S4-①） */
	public static final String FLAG_SRC_EMG = "EMG";
	/** 補核准期限＝送出＋24 小時（SA §5.11） */
	public static final int EMG_DUE_HOURS = 24;
	/** 預估時長（小時，整數）的範圍（裁示②） */
	public static final int EMG_HOUR_MIN = 1;
	public static final int EMG_HOUR_MAX = 24;
	/** 回報「異動了既有帳號」時受影響帳號的上限（S4-③；下限 1） */
	public static final int EMG_AFFECT_MAX = 20;

	// 系統寫入
	/** 系統排程寫入時的 CREATE_BY／UPDATE_BY（Table 第 2 節） */
	public static final String SYSTEM_USER = "SYSTEM";
	/** 系統排程事件的 ACT_BY_NAME（spec §4.8） */
	public static final String SYSTEM_ACTOR_NAME = "系統排程";

	// 登入（spec §2.1；程式常數，spec §9.4）；連錯鎖定交給 WPX，本系統不計次數
	public static final int TOKEN_MAX_HOURS = 8;
	/** 閒置上限；與 TOKEN_MAX_HOURS 同長，等於不另設閒置限制（BACKLOG 第 28 項）；要縮短時改成分鐘數字 */
	public static final int TOKEN_IDLE_MINUTES = TOKEN_MAX_HOURS * 60;
	/** LAST_ACCESS_DATE 節流：距上次寫入達此秒數才更新 */
	public static final int TOKEN_TOUCH_SECONDS = 60;
	public static final int TOKEN_BYTES = 32;

	// 附件（spec §4.10）
	public static final long ATTACH_MAX_BYTES = 10L * 1024 * 1024;

	static {
		Map<String, String> names = new LinkedHashMap<>();
		names.put(REQ_ADD, "新增");
		names.put(REQ_MODIFY, "修改");
		names.put(REQ_DISABLE, "停用");
		names.put(REQ_ENABLE, "重新啟用");
		names.put(REQ_DELETE, "刪除");
		REQ_TYPE_NAMES = Map.copyOf(names);

		Map<String, List<String>> pre = new LinkedHashMap<>();
		pre.put(REQ_MODIFY, List.of(AS_APPROVED));
		pre.put(REQ_DISABLE, List.of(AS_APPROVED, AS_EXPIRED));
		pre.put(REQ_ENABLE, List.of(AS_DISABLED));
		pre.put(REQ_DELETE, List.of(AS_APPROVED, AS_DISABLED, AS_EXPIRED));
		REQ_PREREQ = Map.copyOf(pre);
	}

	/** 申請類型中文名稱；未知回原碼 */
	public static String reqTypeName(String reqTypeCode) {
		return reqTypeCode == null ? "" : REQ_TYPE_NAMES.getOrDefault(reqTypeCode, reqTypeCode);
	}
}
