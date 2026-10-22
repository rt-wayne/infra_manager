package com.mpx.infra_manager.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：錯誤訊息文案（spec v2.0 §4.15，代號 E01～E20、A01～A03、P01、N01）與對應的 ApiException 工廠
//           文案原樣給前端顯示；帶參數的（E02、E10、E11）由方法組字；文件未定文案的 400 類集中在下方
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S2：緊急存取送出欄位的 E10 受詞（帳號類別、帳號或系統、緊急事由、預估時長）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4a：緊急存取回報欄位的 E10／E11 受詞（回報結果、受影響帳號、回報說明、帳號異動說明）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-1：退回原因受詞；補核准前須有申請人回報的文案（S4-⑦）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S6：異常結案欄位的 E10／E11 受詞（結案發現、結案說明）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 1（角色權限維護介面）：名單維護錯誤訊息 E21～E27 與 inFlight()（E23 由呼叫端帶單號明細）；
//           E02／badCode 的受詞「對象」「角色」「帳號類別」
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 3（角色權限設定頁）：E23／E24 沿用代號換文案（受詞由「人」換成「權限」）、
//           400「沒有任何變更」、badCode 受詞「權限碼」
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 7 回合 1（登入改走 WPX，裁示 ⑨、D5A）：新增 A04「帳號無法使用」、A05「登入服務異常，請聯絡管理者」；
//           A01 沿用為「帳號或密碼錯誤」；A02（本系統鎖定）登入已不再回，常數於階段 7 回合 2 刪除
// ============================================================

import java.util.List;

public final class AcctMsg {

	private AcctMsg() {
	}

	public static final String E01 = "請勾選帳號唯一性聲明";
	public static final String E02_PREFIX = "請填寫必填欄位：";
	public static final String E03 = "請填寫到期日";
	public static final String E04 = "到期日不可早於今天";
	public static final String E05 = "此帳號類別尚未開放申請";
	public static final String E06 = "此資產分類碼不可選用";
	public static final String E07 = "此帳號目前已有一筆申請正在處理中";
	public static final String E08 = "此申請已由他人處理，請重新整理";
	public static final String E09 = "此帳號目前的狀態不可提出此類申請";
	public static final String E10_PREFIX = "請填寫";
	public static final String E11_PREFIX = "欄位長度超過上限：";
	public static final String E12 = "請上傳執行佐證附件";
	public static final String E13 = "請填寫門禁卡號";
	public static final String E14 = "附件格式僅限 .pdf .doc .docx .jpg .png";
	public static final String E15 = "附件大小不可超過 10 MB";
	public static final String E16 = "排程執行中，請稍後再試";
	public static final String E17 = "此環境不允許指定基準日";
	public static final String E18 = "巡檢項目不完整，請重新整理後再填寫";
	public static final String E19 = "此項目已處理或不是異常項目";
	public static final String E20 = "目前未切換身分";
	// 名單維護（BACKLOG 第 89 項；E21～E26 為 409）
	public static final String E21 = "此人已在名單中，請重新整理";
	public static final String E22 = "此人已不在名單中，請重新整理";
	/** E23 後面接「單號（關卡名）、…」，由 HolderGuard.describe 組 */
	public static final String E23_PREFIX = "移除後沒有人能處理下列在途案件，請先加入接替人員：";
	public static final String E24 = "不能移除最後一位具角色指派權限的人員";
	public static final String E25 = "切換身分中不能調整角色或執行人，請先切回";
	public static final String E26 = "其他管理員正在調整名單，請稍後再試";
	public static final String E27 = "權限設定已被他人調整，請重新整理";
	// 角色權限設定頁沿用 E23／E24 代號、換文案（E21／E22 的「此人」套在權限碼上不通，改用 E27）
	/** E23 後面接「【權限名（碼）】單號、…；【…】…」，由 HolderGuard.describeByPerm 組 */
	public static final String E23_PERM_PREFIX = "拿掉下列權限後沒有人能處理在途案件，請先讓其他角色持有該權限：";
	public static final String E24_PERM = "拿掉後將沒有人持有角色指派權限（SYS_ROLE），不能存檔";
	/** 權限存檔 added、removed 皆空（400） */
	public static final String NO_CHANGE = "沒有任何變更";
	public static final String A01 = "帳號或密碼錯誤";
	public static final String A02 = "此帳號已鎖定，請聯絡系統管理員";
	public static final String A03 = "登入已逾時，請重新登入";
	/** WPX 回留職停薪／鎖定／時段／分店或 IP（-2～-10），或 WPX 驗過但本系統不讓用：資格不符的首次登入、停用、同步表查無（裁示 ⑨、D5A） */
	public static final String A04 = "帳號無法使用";
	/** WPX 檢查層錯誤（-18～-27）、未列出的代碼、連不上、本系統 WPX 設定不全（裁示 ⑨） */
	public static final String A05 = "登入服務異常，請聯絡管理者";
	public static final String P01 = "您沒有執行此操作的權限";
	public static final String N01 = "查無資料";

	// E10 的受詞
	public static final String WHAT_REASON = "原因";
	public static final String WHAT_EXCPT = "異常說明";
	public static final String WHAT_FIX = "處理方式與結果";
	// 緊急存取送出欄位（BACKLOG 第 46 項）
	public static final String WHAT_EMG_CATG = "帳號類別";
	public static final String WHAT_EMG_TARGET = "帳號或系統";
	public static final String WHAT_EMG_REASON = "緊急事由";
	public static final String WHAT_EMG_HOURS = "預估時長";
	// 緊急存取回報欄位（BACKLOG 第 46 項 S4a）
	public static final String WHAT_EMG_REPORT_TYPE = "回報結果";
	public static final String WHAT_EMG_AFFECT = "受影響帳號";
	public static final String WHAT_EMG_REPORT_DESCR = "回報說明";
	public static final String WHAT_EMG_AFFECT_DESCR = "帳號異動說明";
	// 緊急存取主管處理欄位（BACKLOG 第 46 項 S4b）
	public static final String WHAT_EMG_RETURN_REASON = "退回原因";
	// 緊急存取異常結案欄位（BACKLOG 第 46 項 S6）
	public static final String WHAT_EMG_CLOSE_RESULT = "結案發現";
	public static final String WHAT_EMG_CLOSE_DESCR = "結案說明";
	// 名單維護（BACKLOG 第 89 項）：E02 的受詞「對象」；badCode 的受詞「角色」「帳號類別」
	public static final String WHAT_TARGET = "對象";
	public static final String WHAT_ROLE = "角色";
	public static final String WHAT_CATG = "帳號類別";
	/** 權限存檔的 badCode 受詞：碼重複、同碼在兩邊、不在可見清單（含不存在、停用、隱藏） */
	public static final String WHAT_PERM = "權限碼";

	// 文件未定文案、由 developer 補的（400 類）
	public static final String BAD_CODE_PREFIX = "欄位內容不正確：";
	/** 補核准須先有申請人回報（S4-⑦，刻意偏離 SA v0.5 §5.11；未回報只能退回） */
	public static final String EMG_APPROVE_NO_REPORT = "申請人尚未回報，不能補核准";
	public static final String BAD_DATE = "日期格式錯誤（yyyy-MM-dd）";
	public static final String BAD_DATE_TIME = "時間格式錯誤（yyyy-MM-dd HH:mm:ss）";
	public static final String ADD_WITH_ACCT = "新增申請不可指定帳號";
	public static final String IMPERSONATE_SELF = "不可切換為自己";
	public static final String INSP_DATE_FUTURE = "巡檢時間不可晚於現在";

	public static ApiException required(List<String> fields) {
		return ApiException.badRequest(E02_PREFIX + String.join("、", fields));
	}

	public static ApiException required(String field) {
		return ApiException.badRequest(E02_PREFIX + field);
	}

	/** E10：請填寫{原因／異常說明／處理方式與結果} */
	public static ApiException fillIn(String what) {
		return ApiException.badRequest(E10_PREFIX + what);
	}

	public static ApiException tooLong(String field) {
		return ApiException.badRequest(E11_PREFIX + field);
	}

	public static ApiException badCode(String field) {
		return ApiException.badRequest(BAD_CODE_PREFIX + field);
	}

	public static ApiException forbidden() {
		return ApiException.forbidden(P01);
	}

	public static ApiException notFound() {
		return ApiException.notFound(N01);
	}

	public static ApiException concurrent() {
		return ApiException.conflict(E08);
	}

	/** E23：移除後在途案件無人能處理；detail 為「單號（關卡名）、…」 */
	public static ApiException inFlight(String detail) {
		return ApiException.conflict(E23_PREFIX + detail);
	}

	/** 角色權限設定頁的 E23：拿掉權限後在途案件無人能處理；detail 為「【權限名（碼）】單號、…；…」 */
	public static ApiException inFlightPerm(String detail) {
		return ApiException.conflict(E23_PERM_PREFIX + detail);
	}

	public static ApiException unauthorized() {
		return ApiException.unauthorized(A03);
	}
}
