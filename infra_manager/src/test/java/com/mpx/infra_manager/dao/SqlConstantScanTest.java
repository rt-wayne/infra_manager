package com.mpx.infra_manager.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：SQL 常數與代碼常數掃描（取代 ExistingUserTableSqlTest；spec v2.0 §1.5、§3、Table v0.2 第 11 節）
//           所有 DAO 的 SQL 常數：不得引用既有模組 IM_USER 或任何 RD_USER. 限定名稱、不得用 SYSDATE（業務時間由 Clock 綁定）、
//           不得出現已廢除的代碼（EXTEND、VOID、CONFIRM、NA、TO_EXEC、EXTEND_DONE、FLAG_CLEAR、IMPORT）與已刪欄位
//           （ORIG_REQ_ID、ORIG_ACCT_ID、NEW_ACCT_ID、IMPORT_ID、DISABLE_TYPE_CODE、REVIEW_BASIS_CODE、IS_DIRECT、IS_SHOW、
//           APPLY_TEL_NO、APPLY_EMAIL、INSP_STATE_CODE 欄位寫入）；AcctConst 的字串常數也不得含已廢除代碼；
//           Controller 除 POST /auth/login 外每個 API 方法都宣告 Identity（＝都驗 token）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-10
// 變更說明: UPDATE_EXEC_DONE 須含門禁卡新增時申請單 ACCT_NAME 寫卡號
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S2：掃描清單加 EmgDao、EmergencyController；API 數 32 → 35；
//           緊急存取人名取 IM_ACCT_USER（申請人／處理人／結案人三個 LEFT JOIN）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: 新增：DAO SQL 常數的 CASE END 不得與下一個字黏在一起（EmgDao 曾拼出 ENDAS，S3 瀏覽器實測發現）；
//           掃描清單 DAOS 須與 dao 套件的 @Repository 類別一致（防新增 DAO 漏列）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4a：API 數 35 → 36（POST /emergencies/{id}/report）；EmgDao.UPDATE_REPORT 須帶樂觀鎖與前置條件
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-1：API 數 36 → 38（/approve、/return）；EmgDao.UPDATE_DECIDE 樂觀鎖與不看 DUE_DATE；
//           AcctHistDao 寫入帶 EMG_ID、查詢 COALESCE 兩種來源單的申請人；AcctDao.UPDATE_REVIEW_FLAG 欄位
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S5：EmgDao.SELECT_BASE 須回 CREATE_BY 與四個實際操作人姓名（各自 LEFT JOIN IM_ACCT_USER）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S6：API 數 38 → 39（/close）；EmgDao.UPDATE_CLOSE 樂觀鎖、限已退回且未結案
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S7：EmgDao.FIND_OVERDUE_UNNOTIFIED 須以計算狀態 OVERDUE 篩選（同 STATE_EXPR、:now 綁參數）、
//           以 NOT EXISTS 通知紀錄（:eventCode、EMG）排除已寄過的單
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S8：FIND_LIST 篩選 UNDECIDED＝PENDING＋OVERDUE；首頁卡計數條件 UNDECIDED_COND／RETURNED_COND
//           對應 STATE_EXPR 的支、含 STATUS = 1、不需 :now；EMG_STATE_FILTERS＝五種計算狀態＋UNDECIDED
// AI版本  : Claude Fable 5 (claude-fable-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S10：FLAG_CLEAR 啟用（D42 清旗標歷史），自「已廢除代碼」兩清單移除；
//           AcctDao.CLEAR_REVIEW_FLAG 欄位掃描（5 欄歸零／NULL、帶 IS_REVIEW_FLAG = 1 條件）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S10 審查修正 ①B：CLEAR_REVIEW_FLAG 帶 REVIEW_FLAG_DATE <= :execDate（只清回報前的旗標）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-14
// 變更說明: BACKLOG 第 46 項 S10 複審：清旗標類型斷言的註解改寫成直接寫內容「重新啟用也清（S10 開工裁示）」
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 1：掃描清單加 RoleAdminDao；名單維護 SQL 斷言——全域鎖 FOR UPDATE WAIT 5、持有人數排除參數以 NVL 哨兵、
//           在途申請單依關卡 PERM_ID、緊急存取沿用 UNDECIDED_COND／RETURNED_COND、名單列狀態轉移帶 oldStatus；AdminLogDao.INSERT 的 REASON 改綁參數；
//           回合 2：CONTROLLERS 加 AdminController，API 數 39 → 43
//           階段 2 回合 1：執行人對照 SQL 斷言（名單同形狀、執行人數排除哨兵、在途只看 EXEC 關卡 6 同類別），API 數 43 → 46
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 階段 3 回合 1：ADMIN_VISIBLE_PERMS 常數測試——每個 PERM_* 常數與關卡碼都在清單內、恰 20 碼不重複、隱藏 8 碼不在內
//           （裁示 ⑮A：新功能上線時加常數的同一 commit 要放進清單）；角色權限列 SQL 斷言（含 GENERAL 的角色清單、
//           可見碼 IN 綁參數、成員數只算啟用中使用者、權限列現況不過濾 STATUS、狀態轉移帶 oldStatus、INSERT 不寫 UPDATE_*）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-20
// 變更說明: BACKLOG 第 124 項階段 2 回合 2：同步表片段 EmpSql 併入前三項掃描（不得 RD_USER. 前綴等）；
//           所有 DAO 常數與片段不得 INNER JOIN PX_SYNC_EMP；片段口徑斷言（E2 只認 STATUS＝1、D2A 查無回工號、
//           ⑪ 主管優先其次 DEPT_ID 最小且 'NULL' 字串當空、D9B 資格只看主要部門、E3 有效使用者）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: 第 124 項階段 3 回合 1：組合條件片段改有括號；LoginTokenDao.FIND 兩個同步表旗標、UserDao.FIND_BY_ID 姓名與 Email 讀同步表
//           且不再讀 IM_ACCT_USER 的 USER_NAME／USER_EN_NAME／EMAIL；四段名單 SQL 不含 USER_EN_NAME
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: 第 124 項階段 3 回合 2：切換身分下拉與候選名單 SQL 讀同步表姓名、WHERE 用 E3 有效使用者片段；
//           角色成員與執行人 SQL 另 LEFT JOIN 同步表取名＋USER_IN_SYNC 旗標、不過濾對象；候選名單不帶 DEPT_NAME
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: 第 124 項階段 4 回合 1：四個計數句與收件人前綴改用 E3 有效使用者片段、收件人姓名與 Email 讀同步表；
//           既有 COUNT_* 斷言 contains("U.STATUS = 1") 會被 RU.STATUS = 1 假滿足（階段 3 審查 B3），改比對片段全文並以正規式擋單獨的 U.STATUS = 1
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: 第 124 項階段 5 回合 1：申請單（含待辦）、附件、緊急存取（含影響帳號）共 13 個人名欄逐一斷言讀同步表片段，
//           且這五段 SQL 不含 IM_ACCT_USER、USER_EN_NAME（原「人員姓名一律取IM_ACCT_USER或快照」改名）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: 第 124 項階段 6（裁示 ⑫A）：UserDao.FIND_BY_ID 不得含 TEL_NO
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: 第 124 項階段 7 回合 1（登入改走 WPX）：FIND_BY_ID 不得含 PWD_HASH、LOGIN_FAIL_CNT、LOCK_DATE；
//           UPDATE_LOGIN_OK 只寫最後登入時間；MERGE_FIRST_LOGIN 含資格片段、只有 WHEN NOT MATCHED、只寫本系統自己的欄
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: 第 124 項階段 8 回合 1（上線序列第一點，腳本 D 刪欄前的保護）：IM_ACCT_USER 要刪的 9 欄——
//           他表沒有同名欄的 6 欄（USER_EN_NAME、TEL_NO、PWD_HASH、PWD_UPDATE_DATE、LOGIN_FAIL_CNT、LOCK_DATE）所有 DAO 常數與片段一律不得出現；
//           USER_NAME、DEPT_NAME、EMAIL 同步表或輸出別名也會用到，改為：引用 IM_ACCT_USER 的常數，其別名只能取
//           USER_ID、STATUS、LAST_LOGIN_DATE 與共用四欄，且不得不帶別名使用這三個欄名（AS 輸出別名除外）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: BACKLOG 第 158 項 R2：掃描清單加 ReviewDao、ReviewController；API 數 48 → 49（GET /reviews）；
//           可見權限碼 20 → 23（REVIEW_* 三碼改為必須在內，隱藏碼剩 5）；新增定期審查 SQL 斷言——範圍三條件與 ReviewRule.matches 對齊、
//           分類 JOIN 帶 STATUS = 1、範圍寫成正向 WHEN（不得 NOT (…)）、狀態順序異常 → 範圍 → 逾期 → 待複審 → 正常、清單篩選與排序
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: BACKLOG 第 158 項 R3a：API 數 49 → 50（POST /reviews/{acctId}/sign）；歷史 INSERT／SELECT 須含 REVIEW_ID；
//           簽核 SQL 斷言——INSERT_OPEN 建 OPEN、版本 0；UPDATE_SIGN_INFRA／GOV 帶版本、進行中、本半未簽並 VERSION_NO＋1；
//           UPDATE_DONE 要兩半都已簽；清單多回兩半實際操作人
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: BACKLOG 第 158 項 R3a 審查修正：①A AcctDao.LOCK_FOR_REVIEW 鎖帳號列（FOR UPDATE、綁 :acctId）；
//           M2 ③A 歷史查詢 REQ_APPLY_BY 第三個來源為帳號主表（只對 SOURCE_CODE = 'REVIEW' 的列 JOIN）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: BACKLOG 第 158 項 R3b：API 數 50 → 51（POST /reviews/{acctId}/reject）；駁回 SQL 斷言——INSERT_REJECTED 直接 REJECTED 不寫簽核欄、
//           UPDATE_REJECT 帶版本／進行中／被駁回那一半尚未簽（①A）且不動簽核欄（③A）、FIND_ROUND 以 REVIEW_ID 接並帶駁回欄、清單不帶駁回欄；
//           第 2 單位：RoleAdminDao.FIND_IN_FLIGHT_REVIEW（進行中且 :half 未簽、DOC_ID＝帳號單號）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.controller.AccountController;
import com.mpx.infra_manager.controller.AdminController;
import com.mpx.infra_manager.controller.AttachmentController;
import com.mpx.infra_manager.controller.AuthController;
import com.mpx.infra_manager.controller.EmergencyController;
import com.mpx.infra_manager.controller.HomeController;
import com.mpx.infra_manager.controller.InspectionController;
import com.mpx.infra_manager.controller.JobController;
import com.mpx.infra_manager.controller.RequestController;
import com.mpx.infra_manager.controller.ReviewController;

class SqlConstantScanTest {

	static final List<Class<?>> DAOS = List.of(AcctDao.class, AcctHistDao.class, AcctReqDao.class, AdminLogDao.class,
			AttachDao.class, CodeDao.class, DocSeqDao.class, EmgDao.class, HomeDao.class, InspDao.class, JobRunDao.class,
			LoginTokenDao.class, NotifyLogDao.class, PermDao.class, ReviewDao.class, RoleAdminDao.class, UserDao.class);

	static final List<Class<?>> CONTROLLERS = List.of(AccountController.class, AdminController.class, AttachmentController.class,
			AuthController.class, EmergencyController.class, HomeController.class, InspectionController.class, JobController.class,
			RequestController.class, ReviewController.class);

	/** 既有模組使用者表（IM_ACCT_USER 不算） */
	static final Pattern IM_USER = Pattern.compile("(?<![\\w])IM_USER\\b");
	static final Pattern RD_USER = Pattern.compile("\\bRD_USER\\.");
	static final Pattern SYSDATE = Pattern.compile("\\bSYSDATE\\b");
	static final Pattern ABOLISHED_CODE = Pattern.compile("'(EXTEND|VOID|CONFIRM|NA|TO_EXEC|EXTEND_DONE|IMPORT\\w*)'");
	static final Pattern ABOLISHED_COLUMN = Pattern.compile(
			"\\b(ORIG_REQ_ID|ORIG_ACCT_ID|NEW_ACCT_ID|IMPORT_ID|DISABLE_TYPE_CODE|REVIEW_BASIS_CODE|IS_DIRECT|IS_SHOW|APPLY_TEL_NO|APPLY_EMAIL|EXCPT_CNT\\s*,\\s*INSP_STATE_CODE)\\b");
	/** 第 124 項 D1A：IM_ACCT_USER 要刪、且本系統與同步表都沒有同名欄的 6 欄 */
	static final Pattern USER_DROPPED_COLUMN = Pattern.compile(
			"\\b(USER_EN_NAME|TEL_NO|PWD_HASH|PWD_UPDATE_DATE|LOGIN_FAIL_CNT|LOCK_DATE)\\b");
	/** 第 124 項 D1A：IM_ACCT_USER 刪欄後留下的欄（腳本 D 後的最終結構） */
	static final List<String> USER_KEPT_COLUMNS = List.of("USER_ID", "STATUS", "LAST_LOGIN_DATE", "CREATE_DATE", "CREATE_BY",
			"UPDATE_DATE", "UPDATE_BY");
	static final Pattern USER_TABLE = Pattern.compile("\\bIM_ACCT_USER\\b");
	/** IM_ACCT_USER 後面接的表別名（接 ON、SET、USING、WHERE、括號或換行後 SET 的不是別名） */
	static final Pattern USER_TABLE_ALIAS = Pattern.compile("\\bIM_ACCT_USER\\s+(?!(?:ON|SET|USING|WHERE)\\b)([A-Z][A-Z0-9_]*)\\b");
	/** 要刪的 9 欄中他處有同名的 3 欄，不帶別名直接使用（「AS USER_NAME」輸出別名、「E.EMAIL」帶別名的不算） */
	static final Pattern BARE_SHARED_NAME_COLUMN = Pattern.compile("(?<!AS )(?<![\\w.])(USER_NAME|DEPT_NAME|EMAIL)\\b");

	/** END 後面直接接英文字母（END_DATE 之類有底線的欄名不算） */
	static final Pattern GLUED_END = Pattern.compile("\\bEND[A-Z]");
	/** NULL 後面直接接 AND／OR／AS（例 NULLAND）：第 148 項審查必1，text block 單行片段前後空白被整個剝掉；右括號黏字 Oracle 可接受，不掃 */
	static final Pattern GLUED_NULL = Pattern.compile("\\bNULL(AND|OR|AS)\\b");
	/** 對同步人員檔做 LEFT JOIN 以外的 JOIN（PX_SYNC_EMP_MGR 不算；同步表查無的人會整列消失） */
	static final Pattern INNER_JOIN_SYNC_EMP = Pattern.compile("(?<!LEFT )(?<!OUTER )\\bJOIN\\s+PX_SYNC_EMP\\b");

	/** EmpSql 片段是方法（要帶別名），以範例別名展開後與 DAO 常數一起掃 */
	static Map<String, String> empSqlSamples() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("EmpSql.joinEmp", EmpSql.joinEmp("E1", "U.USER_ID"));
		m.put("EmpSql.nameExpr", EmpSql.nameExpr("E1", "U.USER_ID"));
		m.put("EmpSql.inSyncCond", EmpSql.inSyncCond("U.USER_ID"));
		m.put("EmpSql.activeUserCond", EmpSql.activeUserCond("U"));
		m.put("EmpSql.primaryDeptExpr", EmpSql.primaryDeptExpr("U.USER_ID"));
		m.put("EmpSql.qualifiedCond", EmpSql.qualifiedCond("U.USER_ID"));
		return m;
	}

	static Map<String, String> daoSqlsWithEmpSql() throws IllegalAccessException {
		Map<String, String> m = constants(DAOS);
		m.putAll(empSqlSamples());
		return m;
	}

	static Map<String, String> constants(List<Class<?>> classes) throws IllegalAccessException {
		Map<String, String> m = new LinkedHashMap<>();
		for (Class<?> c : classes) {
			for (Field f : c.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers()) && f.getType() == String.class) {
					f.setAccessible(true);
					m.put(c.getSimpleName() + "." + f.getName(), (String) f.get(null));
				}
			}
		}
		return m;
	}

	@Test
	void DAO_SQL常數_不引用既有模組使用者表_不用SYSDATE_無已廢除代碼與欄位() throws Exception {
		Map<String, String> sqls = daoSqlsWithEmpSql();
		assertThat(sqls).hasSizeGreaterThan(60);
		List<String> bad = new ArrayList<>();
		for (Map.Entry<String, String> e : sqls.entrySet()) {
			String s = e.getValue();
			if (IM_USER.matcher(s).find()) {
				bad.add(e.getKey() + " 引用 IM_USER");
			}
			if (RD_USER.matcher(s).find()) {
				bad.add(e.getKey() + " 使用 RD_USER. 限定名稱");
			}
			if (SYSDATE.matcher(s).find()) {
				bad.add(e.getKey() + " 使用 SYSDATE");
			}
			if (ABOLISHED_CODE.matcher(s).find()) {
				bad.add(e.getKey() + " 含已廢除代碼");
			}
			if (ABOLISHED_COLUMN.matcher(s).find()) {
				bad.add(e.getKey() + " 含已刪欄位");
			}
			if (USER_DROPPED_COLUMN.matcher(s).find()) {
				bad.add(e.getKey() + " 含 IM_ACCT_USER 要刪的欄");
			}
		}
		assertThat(bad).isEmpty();
	}

	/**
	 * 第 124 項階段 8（上線序列第一點）：腳本 D 刪掉 IM_ACCT_USER 的 9 欄前，程式不得再讀寫其中任何一欄。
	 * USER_NAME、DEPT_NAME、EMAIL 同步表也有、也常當輸出別名，所以不用全域禁字，改看「IM_ACCT_USER 的別名取了哪些欄」
	 */
	@Test
	void 引用IM_ACCT_USER的SQL_只取刪欄後留下的欄() throws Exception {
		// 規則本身：別名判斷與不帶別名的同名欄判斷
		assertThat(userTableAliases("JOIN IM_ACCT_USER U ON U.USER_ID = T.USER_ID LEFT JOIN IM_ACCT_USER AU ON AU.USER_ID = X"))
				.containsExactly("U", "AU");
		assertThat(userTableAliases("UPDATE IM_ACCT_USER\n   SET LAST_LOGIN_DATE = :now")).isEmpty();
		assertThat(userTableAliases("MERGE INTO IM_ACCT_USER U\nUSING (SELECT 1 FROM DUAL) S")).containsExactly("U");
		assertThat(BARE_SHARED_NAME_COLUMN.matcher("SELECT USER_NAME FROM IM_ACCT_USER").find()).isTrue();
		assertThat(BARE_SHARED_NAME_COLUMN.matcher("INSERT INTO IM_ACCT_USER (USER_ID, EMAIL)").find()).isTrue();
		assertThat(BARE_SHARED_NAME_COLUMN.matcher("NVL(TRIM(E.EMP_NAME), U.USER_ID) AS USER_NAME, E.EMAIL").find()).isFalse();

		Map<String, String> sqls = daoSqlsWithEmpSql();
		List<String> checked = new ArrayList<>();
		List<String> bad = new ArrayList<>();
		for (Map.Entry<String, String> e : sqls.entrySet()) {
			String s = e.getValue();
			if (!USER_TABLE.matcher(s).find()) {
				continue;
			}
			checked.add(e.getKey());
			for (String alias : userTableAliases(s)) {
				Matcher m = Pattern.compile("(?<![\\w.])" + alias + "\\.([A-Z][A-Z0-9_]*)").matcher(s);
				while (m.find()) {
					if (!USER_KEPT_COLUMNS.contains(m.group(1))) {
						bad.add(e.getKey() + " 取 " + alias + "." + m.group(1));
					}
				}
			}
			Matcher bare =BARE_SHARED_NAME_COLUMN.matcher(s);
			if (bare.find()) {
				bad.add(e.getKey() + " 不帶別名使用 " + bare.group(1));
			}
		}
		// 涵蓋面：token 檢查、使用者查詢、首次登入、登入時間、名單、收件人、計數都引用 IM_ACCT_USER
		assertThat(checked).contains("LoginTokenDao.FIND", "UserDao.FIND_BY_ID", "UserDao.MERGE_FIRST_LOGIN",
				"UserDao.UPDATE_LOGIN_OK", "UserDao.FIND_ACTIVE_WITH_ROLES", "PermDao.RCPT_SELECT", "RoleAdminDao.FIND_ROLE_MEMBERS",
				"RoleAdminDao.FIND_EXEC_MEMBERS", "RoleAdminDao.FIND_CANDIDATES", "RoleAdminDao.COUNT_ACTIVE_USERS");
		assertThat(bad).isEmpty();
	}

	private static List<String> userTableAliases(String sql) {
		List<String> aliases = new ArrayList<>();
		Matcher m = USER_TABLE_ALIAS.matcher(sql);
		while (m.find()) {
			aliases.add(m.group(1));
		}
		return aliases;
	}

	/** 掃描清單 DAOS 須涵蓋 dao 套件所有 @Repository（新增 DAO 忘了列進來就整支不會被掃） */
	@Test
	void 掃描清單涵蓋dao套件所有Repository() {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Repository.class));
		List<String> found = scanner.findCandidateComponents(SqlConstantScanTest.class.getPackageName()).stream()
				.map(BeanDefinition::getBeanClassName).sorted().toList();
		List<String> listed = DAOS.stream().map(Class::getName).sorted().toList();
		assertThat(found).hasSizeGreaterThan(10).isEqualTo(listed);
	}

	/** CASE 的 END 與下一個字黏在一起（例 ENDAS）：text block 串接片段時前導空白被剝掉，Oracle 回 ORA-00905 */
	@Test
	void DAO_SQL常數_END不與下一個字黏在一起() throws Exception {
		List<String> bad = new ArrayList<>();
		for (Map.Entry<String, String> e : daoSqlsWithEmpSql().entrySet()) {
			if (GLUED_END.matcher(e.getValue()).find()) {
				bad.add(e.getKey());
			}
		}
		assertThat(bad).isEmpty();
	}

	/** NULL 與下一個 AND／OR／AS 黏在一起（例 NULLAND）：第 148 項審查必1，HomeDao.FIND_STATS 曾拼出 IS NULLAND( */
	@Test
	void DAO_SQL常數_NULL不與AND_OR_AS黏在一起() throws Exception {
		assertThat(GLUED_NULL.matcher("E.DECIDE_CODE IS NULLAND(:x IS NULL").find()).isTrue();
		assertThat(GLUED_NULL.matcher("E.DECIDE_CODE IS NULL AND (:x IS NULL").find()).isFalse();
		assertThat(GLUED_NULL.matcher("NULLS FIRST").find()).isFalse();
		assertThat(GLUED_NULL.matcher("NULLABLE_COL").find()).isFalse();
		List<String> bad = new ArrayList<>();
		for (Map.Entry<String, String> e : daoSqlsWithEmpSql().entrySet()) {
			if (GLUED_NULL.matcher(e.getValue()).find()) {
				bad.add(e.getKey());
			}
		}
		assertThat(bad).isEmpty();
	}

	/** 第 124 項：本系統人員列不得 INNER JOIN 同步人員檔（同步表查無的人要照樣列出、人名退回工號） */
	@Test
	void DAO_SQL常數與同步表片段_不INNER_JOIN同步人員檔() throws Exception {
		assertThat(INNER_JOIN_SYNC_EMP.matcher("JOIN PX_SYNC_EMP E ON").find()).isTrue();
		assertThat(INNER_JOIN_SYNC_EMP.matcher("INNER JOIN PX_SYNC_EMP E ON").find()).isTrue();
		assertThat(INNER_JOIN_SYNC_EMP.matcher("LEFT JOIN PX_SYNC_EMP E ON").find()).isFalse();
		assertThat(INNER_JOIN_SYNC_EMP.matcher("LEFT OUTER JOIN PX_SYNC_EMP E ON").find()).isFalse();
		assertThat(INNER_JOIN_SYNC_EMP.matcher("JOIN PX_SYNC_EMP_MGR M ON").find()).isFalse();
		List<String> bad = new ArrayList<>();
		for (Map.Entry<String, String> e : daoSqlsWithEmpSql().entrySet()) {
			if (INNER_JOIN_SYNC_EMP.matcher(e.getValue()).find()) {
				bad.add(e.getKey());
			}
		}
		assertThat(bad).isEmpty();
	}

	/** 第 124 項同步表片段口徑：E2 只認 STATUS＝1、D2A 查無回工號、⑪ 主要部門、D9B 資格、E3 有效使用者 */
	@Test
	void 同步表片段_只認STATUS1_查無回工號_主要部門與資格與有效使用者口徑() {
		assertThat(EmpSql.joinEmp("E1", "R.APPLY_BY"))
				.isEqualTo("LEFT JOIN PX_SYNC_EMP E1 ON E1.EMP_ID = R.APPLY_BY AND E1.STATUS = 1");
		assertThat(EmpSql.nameExpr("E1", "R.APPLY_BY")).isEqualTo("NVL(TRIM(E1.EMP_NAME), R.APPLY_BY)");
		assertThat(EmpSql.inSyncCond("U.USER_ID"))
				.isEqualTo("EXISTS (SELECT 1 FROM PX_SYNC_EMP PXE WHERE PXE.EMP_ID = U.USER_ID AND PXE.STATUS = 1)");
		assertThat(EmpSql.activeUserCond("U")).isEqualTo("(U.STATUS = 1 AND " + EmpSql.inSyncCond("U.USER_ID") + ")");
		assertThat(EmpSql.primaryDeptExpr(":userId"))
				// ⑪：主管的部門優先、其次 DEPT_ID 最小；全名為字串 'NULL' 當空、退回部門名
				.contains("KEEP (DENSE_RANK FIRST ORDER BY PXDE.IS_MGR DESC, PXDE.DEPT_ID)")
				.contains("NVL(NULLIF(TRIM(PXD.DEPT_FULL_NAME), 'NULL'), PXD.DEPT_NAME)")
				// E2：部門列與部門都只認 STATUS＝1
				.contains("PXD.STATUS = 1").contains("PXDE.STATUS = 1").contains("PXDE.EMP_ID = :userId");
		// D9B：資格只看主要部門（不是任一部門）；D8A：關鍵字寫在 Java 常數
		assertThat(EmpSql.QUALIFIED_DEPT_KEYWORD).isEqualTo("資訊部");
		assertThat(EmpSql.qualifiedCond(":userId")).isEqualTo("(" + EmpSql.inSyncCond(":userId") + " AND "
				+ EmpSql.primaryDeptExpr(":userId") + " LIKE '%資訊部%')");
	}

	/** 第 124 項階段 3 回合 1：token 檢查與單一使用者查詢改讀同步表（D0B、E1）；名單 SQL 不再讀 USER_EN_NAME（第 67 項併入） */
	@Test
	void 登入身分_token旗標與使用者查詢讀同步表_名單不含英文名() {
		assertThat(LoginTokenDao.FIND).contains("JOIN IM_ACCT_USER U ON U.USER_ID = T.USER_ID")
				.contains("CASE WHEN " + EmpSql.inSyncCond("T.USER_ID") + " THEN 1 ELSE 0 END AS USER_IN_SYNC")
				.contains("CASE WHEN " + EmpSql.inSyncCond("T.ACT_AS_USER_ID") + " THEN 1 ELSE 0 END AS ACT_AS_IN_SYNC");
		assertThat(UserDao.FIND_BY_ID).contains(EmpSql.joinEmp("E", "U.USER_ID"))
				.contains(EmpSql.nameExpr("E", "U.USER_ID") + " AS USER_NAME").contains("E.EMAIL")
				.contains("CASE WHEN E.EMP_ID IS NULL THEN 0 ELSE 1 END AS IN_SYNC")
				// 上線時要刪的欄：姓名、英文名、Email、部門、分機、密碼、失敗次數、鎖定都不再從 IM_ACCT_USER 讀
				.doesNotContain("U.USER_NAME").doesNotContain("USER_EN_NAME").doesNotContain("U.EMAIL").doesNotContain("DEPT_NAME")
				.doesNotContain("TEL_NO").doesNotContain("PWD_HASH").doesNotContain("LOGIN_FAIL_CNT").doesNotContain("LOCK_DATE");
		// 第 124 項階段 7 回合 1：登入成功只寫最後登入時間（失敗次數與鎖定交給 WPX）
		assertThat(UserDao.UPDATE_LOGIN_OK).contains("SET LAST_LOGIN_DATE = :now, UPDATE_DATE = :now, UPDATE_BY = :userId")
				.doesNotContain("LOGIN_FAIL_CNT").doesNotContain("LOCK_DATE");
		// 首次登入自動建立：資格片段在同一句、只在沒有帳號列時 INSERT（沒有 WHEN MATCHED，不會改到既有帳號）、只寫本系統自己的欄
		assertThat(UserDao.MERGE_FIRST_LOGIN).startsWith("MERGE INTO IM_ACCT_USER U\n")
				.contains("SELECT :userId AS USER_ID FROM DUAL\n        WHERE " + EmpSql.qualifiedCond(":userId") + ") S\n")
				.contains("ON (U.USER_ID = S.USER_ID)").contains("WHEN NOT MATCHED THEN")
				.contains("INSERT (USER_ID, STATUS, CREATE_DATE, CREATE_BY)").contains("VALUES (S.USER_ID, 1, :now, :createBy)")
				.doesNotContain("WHEN MATCHED").doesNotContain("UPDATE SET");
		// 第 124 項階段 5 回合 2：/me 部門＝同步表主要部門片段（⑪），逐人子查詢只用在單一使用者
		assertThat(UserDao.FIND_PRIMARY_DEPT).isEqualTo("SELECT " + EmpSql.primaryDeptExpr(":userId") + " AS CODE FROM DUAL\n");
		for (String sql : List.of(UserDao.FIND_ACTIVE_WITH_ROLES, PermDao.RCPT_SELECT, RoleAdminDao.FIND_ROLE_MEMBERS,
				RoleAdminDao.FIND_EXEC_MEMBERS, RoleAdminDao.FIND_CANDIDATES)) {
			assertThat(sql).doesNotContain("USER_EN_NAME");
		}
	}

	/**
	 * 第 124 項階段 4 回合 1：四個計數句與收件人前綴都用有效使用者片段（E3），且不再以「U.STATUS = 1」單獨判斷
	 * （以正規式比對，避免被 RU.STATUS = 1 子字串假滿足）；收件人姓名與 Email 讀同步表
	 */
	@Test
	void 計數句與收件人_有效使用者E3_收件人Email讀同步表() {
		Pattern bareUserStatus = Pattern.compile("(?<![A-Z_])U\\.STATUS = 1(?! AND EXISTS \\(SELECT 1 FROM PX_SYNC_EMP)");
		Map<String, String> sqls = new LinkedHashMap<>();
		sqls.put("COUNT_HOLDERS", RoleAdminDao.COUNT_HOLDERS);
		sqls.put("COUNT_EXECUTORS", RoleAdminDao.COUNT_EXECUTORS);
		sqls.put("COUNT_ROLE_MEMBERS", RoleAdminDao.COUNT_ROLE_MEMBERS);
		sqls.put("COUNT_ACTIVE_USERS", RoleAdminDao.COUNT_ACTIVE_USERS);
		sqls.put("RCPT_SELECT", PermDao.RCPT_SELECT);
		sqls.put("FIND_USERS_WITH_PERM", PermDao.FIND_USERS_WITH_PERM);
		sqls.put("FIND_USERS_IN_ROLES", PermDao.FIND_USERS_IN_ROLES);
		sqls.put("FIND_EXEC_USERS", PermDao.FIND_EXEC_USERS);
		sqls.put("FIND_ACTIVE_USER", PermDao.FIND_ACTIVE_USER);
		for (Map.Entry<String, String> e : sqls.entrySet()) {
			assertThat(e.getValue()).as(e.getKey()).contains(EmpSql.activeUserCond("U"));
			assertThat(bareUserStatus.matcher(e.getValue()).find()).as(e.getKey() + " 不得只判斷 U.STATUS = 1").isFalse();
		}
		assertThat(PermDao.RCPT_SELECT).contains(EmpSql.joinEmp("E", "U.USER_ID")).contains("E.EMAIL")
				.contains(EmpSql.nameExpr("E", "U.USER_ID") + " AS USER_NAME")
				// 上線時要刪的欄不再從 IM_ACCT_USER 讀
				.doesNotContain("U.USER_NAME").doesNotContain("U.EMAIL");
		for (String sql : List.of(PermDao.FIND_USERS_WITH_PERM, PermDao.FIND_USERS_IN_ROLES, PermDao.FIND_EXEC_USERS,
				PermDao.FIND_ACTIVE_USER)) {
			assertThat(sql).startsWith(PermDao.RCPT_SELECT);
		}
	}

	/** 第 124 項階段 3 回合 2：名單 SQL 姓名讀同步表；有效使用者 E3（不看最後登入 E4）；候選名單只取工號與名字（⑬-2） */
	@Test
	void 名單SQL_姓名讀同步表_有效使用者E3_候選名單不帶部門() {
		for (String sql : List.of(UserDao.FIND_ACTIVE_WITH_ROLES, RoleAdminDao.FIND_CANDIDATES)) {
			assertThat(sql).contains(EmpSql.joinEmp("E", "U.USER_ID"))
					.contains(EmpSql.nameExpr("E", "U.USER_ID") + " AS USER_NAME")
					.contains("WHERE " + EmpSql.activeUserCond("U"))
					.doesNotContain("U.USER_NAME").doesNotContain("LAST_LOGIN");
		}
		assertThat(RoleAdminDao.FIND_CANDIDATES).doesNotContain("DEPT_NAME");
		// 名單成員：IM_ACCT_USER 仍 LEFT JOIN 取停用狀態（既有斷言），另 LEFT JOIN 同步表取名與查得到旗標；對象查無仍列出（不過濾）
		assertThat(RoleAdminDao.FIND_ROLE_MEMBERS).contains(EmpSql.joinEmp("E", "RU.USER_ID"))
				.contains(EmpSql.nameExpr("E", "RU.USER_ID") + " AS USER_NAME")
				.contains("CASE WHEN E.EMP_ID IS NULL THEN 0 ELSE 1 END AS USER_IN_SYNC")
				.contains("U.STATUS AS USER_STATUS").doesNotContain("U.USER_NAME").doesNotContain(EmpSql.activeUserCond("U"));
		assertThat(RoleAdminDao.FIND_EXEC_MEMBERS).contains(EmpSql.joinEmp("E", "M.USER_ID"))
				.contains(EmpSql.nameExpr("E", "M.USER_ID") + " AS USER_NAME")
				.contains("CASE WHEN E.EMP_ID IS NULL THEN 0 ELSE 1 END AS USER_IN_SYNC")
				.contains("U.STATUS AS USER_STATUS").doesNotContain("U.USER_NAME").doesNotContain(EmpSql.activeUserCond("U"));
	}

	/** 第 124 項階段 5 回合 1：申請單、待辦、附件、緊急存取的人名一律讀同步表（只顯示中文名、查無退回該欄工號），不再 JOIN IM_ACCT_USER；歷史與巡檢用快照 */
	@Test
	void 人員姓名一律取同步表或快照() {
		assertNameFromSync(AcctReqDao.FIND_BY_ID, "UA", "R.APPLY_BY", "APPLY_BY_NAME");
		assertNameFromSync(AcctReqDao.FIND_BY_ID, "UE", "R.EXEC_BY", "EXEC_BY_NAME");
		assertNameFromSync(AcctReqDao.FIND_BY_ID, "UV", "R.VERIFY_BY", "VERIFY_BY_NAME");
		assertNameFromSync(AcctReqDao.FIND_TODO_APPROVE, "UA", "R.APPLY_BY", "APPLY_BY_NAME");
		assertNameFromSync(AttachDao.FIND_ACTIVE_BY_REQ, "U", "T.CREATE_BY", "CREATE_BY_NAME");
		// S5：四個實際操作人也回姓名（送出 CREATE_BY、回報／處理／結案 *_ACT_BY）
		assertNameFromSync(EmgDao.SELECT_BASE, "UA", "E.APPLY_BY", "APPLY_BY_NAME");
		assertNameFromSync(EmgDao.SELECT_BASE, "UX", "E.CREATE_BY", "CREATE_BY_NAME");
		assertNameFromSync(EmgDao.SELECT_BASE, "URA", "E.REPORT_ACT_BY", "REPORT_ACT_BY_NAME");
		assertNameFromSync(EmgDao.SELECT_BASE, "UD", "E.DECIDE_BY", "DECIDE_BY_NAME");
		assertNameFromSync(EmgDao.SELECT_BASE, "UDA", "E.DECIDE_ACT_BY", "DECIDE_ACT_BY_NAME");
		assertNameFromSync(EmgDao.SELECT_BASE, "UC", "E.CLOSE_BY", "CLOSE_BY_NAME");
		assertNameFromSync(EmgDao.SELECT_BASE, "UCA", "E.CLOSE_ACT_BY", "CLOSE_ACT_BY_NAME");
		assertNameFromSync(EmgDao.FIND_AFFECTS, "UR", "A.RECORD_BY", "RECORD_BY_NAME");
		for (String sql : List.of(AcctReqDao.FIND_BY_ID, AcctReqDao.FIND_TODO_APPROVE, AttachDao.FIND_ACTIVE_BY_REQ,
				EmgDao.SELECT_BASE, EmgDao.FIND_AFFECTS)) {
			assertThat(sql).doesNotContain("IM_ACCT_USER").doesNotContain("USER_EN_NAME");
		}
		assertThat(AcctHistDao.FIND_BY_ACCT).contains("H.ACT_BY_NAME").doesNotContain("IM_ACCT_USER");
		assertThat(InspDao.INSP_SUMMARY).contains("I.INSP_BY_NAME").doesNotContain("IM_ACCT_USER");
	}

	private static void assertNameFromSync(String sql, String alias, String idExpr, String column) {
		assertThat(sql).as(column).contains(EmpSql.joinEmp(alias, idExpr))
				.contains(EmpSql.nameExpr(alias, idExpr) + " AS " + column);
	}

	@Test
	void 緊急存取_狀態即時計算_時間綁參數_列表與明細共用() {
		assertThat(EmgDao.STATE_EXPR).contains(":now").doesNotContain("SYSDATE");
		assertThat(EmgDao.FIND_LIST).contains(EmgDao.STATE_EXPR).contains(":applyBy IS NULL OR X.APPLY_BY = :applyBy");
		assertThat(EmgDao.FIND_BY_ID).contains(EmgDao.STATE_EXPR).contains("E.EMG_ID = :emgId");
		assertThat(EmgDao.INSERT_EMG).contains("VERSION_NO").contains(":dueDate").doesNotContain("DECIDE_CODE");
		// S7：J03 逾期掃描與列表同口徑（計算狀態 OVERDUE、:now 綁基準時間點），每案一次靠通知紀錄 NOT EXISTS
		assertThat(EmgDao.FIND_OVERDUE_UNNOTIFIED).contains(EmgDao.STATE_EXPR).contains("X.EMG_STATE_CODE = 'OVERDUE'")
				.contains("NOT EXISTS (SELECT 1 FROM IM_ACCT_NOTIFY_LOG N").contains("N.EVENT_CODE = :eventCode")
				.contains("N.REF_TYPE_CODE = 'EMG'").contains("N.REF_ID = X.EMG_ID").contains("ORDER BY X.DUE_DATE, X.EMG_ID")
				.doesNotContain("SYSDATE").doesNotContain("DECIDE_CODE IS NULL AND E.DUE_DATE < :now");
		// S8：清單篩選 UNDECIDED＝PENDING＋OVERDUE；首頁卡兩個計數條件對應 STATE_EXPR 的支（同 SELECT_BASE 只算 STATUS = 1）
		assertThat(EmgDao.FIND_LIST).contains("(:stateCode = 'UNDECIDED' AND X.EMG_STATE_CODE IN ('PENDING', 'OVERDUE'))");
		assertThat(EmgDao.STATE_EXPR).contains("WHEN E.DECIDE_CODE IS NULL THEN 'OVERDUE'")
				.contains("WHEN E.DECIDE_CODE = 'APPROVE' THEN 'APPROVED'").contains("WHEN E.CLOSE_DATE IS NULL THEN 'RETURNED'");
		assertThat(EmgDao.SELECT_BASE).contains("E.STATUS = 1");
		assertThat(EmgDao.UNDECIDED_COND).contains("E.STATUS = 1").contains("E.DECIDE_CODE IS NULL").doesNotContain(":now");
		assertThat(EmgDao.RETURNED_COND).contains("E.STATUS = 1").contains("E.DECIDE_CODE = 'RETURN'")
				.contains("E.CLOSE_DATE IS NULL").doesNotContain(":now");
	}

	/** 第 158 項 R2：審查範圍 SQL 與 ReviewRule.matches 同口徑；狀態順序 異常 → 不在範圍 → 逾期 → 待複審 → 正常（裁示 ⑨A／⑥A） */
	@Test
	void 定期審查SQL_範圍與Java同口徑_狀態順序_清單篩選() {
		// 範圍三條件逐一對應 ReviewRule.matches 的三個判斷；只接生效中帳號
		assertThat(ReviewDao.REVIEW_SCOPE_COND).contains("A.ACCT_STATE_CODE = 'APPROVED'").contains("AC.IS_REVIEW_TRIGGER = 1")
				.contains("A.CATG_ID = '" + AcctConst.CATG_DOOR + "'").contains("A.IS_PRIV = 1").startsWith("(").endsWith(")");
		// 分類 JOIN 一定要過濾 STATUS = 1（Java 端 CodeService.findAssetClass 只回啟用中分類；AcctDao.SELECT_BASE 的 JOIN 沒過濾，不能共用）
		assertThat(ReviewDao.JOIN_ASSET_CLASS).contains("LEFT JOIN IM_ASSET_CLASS AC").contains("AC.STATUS = 1");
		assertThat(ReviewDao.SELECT_BASE).contains(ReviewDao.JOIN_ASSET_CLASS).contains(ReviewDao.REVIEW_STATE_EXPR)
				.contains("AS REVIEW_STATE_CODE").contains("R.REVIEW_STATE_CODE = 'OPEN' AND R.STATUS = 1");
		// 範圍必須是正向 WHEN：分類查無時 IS_REVIEW_TRIGGER 為 NULL，NOT (範圍) 也是 NULL，會落到逾期那支
		assertThat(ReviewDao.REVIEW_STATE_EXPR).contains("WHEN " + ReviewDao.REVIEW_SCOPE_COND + " THEN").doesNotContain("NOT (")
				.contains("A.IS_REVIEW_FLAG = 1 AND A.ACCT_STATE_CODE <> 'DELETED'").contains(":today").doesNotContain("SYSDATE")
				.contains("A.LAST_REVIEW_DATE IS NULL OR A.NEXT_REVIEW_DATE IS NULL OR A.NEXT_REVIEW_DATE < :today")
				.contains("A.NEXT_REVIEW_DATE <= :today + " + AcctConst.REVIEW_DUE_DAYS);
		int abnormal = ReviewDao.REVIEW_STATE_EXPR.indexOf("'" + AcctConst.RVS_ABNORMAL + "'");
		int scope = ReviewDao.REVIEW_STATE_EXPR.indexOf(ReviewDao.REVIEW_SCOPE_COND);
		int overdue = ReviewDao.REVIEW_STATE_EXPR.indexOf("'" + AcctConst.RVS_OVERDUE + "'");
		int due = ReviewDao.REVIEW_STATE_EXPR.indexOf("'" + AcctConst.RVS_DUE + "'");
		int normal = ReviewDao.REVIEW_STATE_EXPR.indexOf("'" + AcctConst.RVS_NORMAL + "'");
		assertThat(abnormal).isGreaterThan(-1).isLessThan(scope);
		assertThat(scope).isLessThan(overdue);
		assertThat(overdue).isLessThan(due);
		assertThat(due).isLessThan(normal);
		// 清單：排除不在範圍、:stateCode 空＝異常＋逾期＋待複審、關鍵字四欄 LIKE 都有 ESCAPE、排序異常 → 逾期 → 待複審 → 正常
		assertThat(ReviewDao.FIND_LIST).contains("X.REVIEW_STATE_CODE IS NOT NULL")
				.contains("((:stateCode IS NULL AND X.REVIEW_STATE_CODE <> 'NORMAL') OR X.REVIEW_STATE_CODE = :stateCode)")
				.contains("UPPER(X.ACCT_ID) LIKE").contains("UPPER(X.ACCT_NAME) LIKE").contains("UPPER(X.HOLDER_NAME) LIKE")
				.contains("UPPER(X.SYS_NAME) LIKE")
				.contains("ORDER BY CASE X.REVIEW_STATE_CODE WHEN 'ABNORMAL' THEN 1 WHEN 'OVERDUE' THEN 2 WHEN 'DUE' THEN 3 ELSE 4 END")
				.contains("X.NEXT_REVIEW_DATE NULLS FIRST, X.ACCT_ID");
		assertThat(ReviewDao.FIND_LIST.split("ESCAPE '\\\\'", -1)).hasSize(5);
		assertThat(AcctConst.REVIEW_STATES).containsExactly("ABNORMAL", "OVERDUE", "DUE", "NORMAL");
	}

	/** 第 158 項 R3a：簽核 SQL——首簽建輪、簽半樂觀鎖、雙簽完成；歷史帶 REVIEW_ID */
	@Test
	void 定期審查SQL_簽核_首簽建輪_簽半樂觀鎖_雙簽完成_歷史帶REVIEW_ID() {
		assertThat(ReviewDao.SELECT_BASE).contains("R.INFRA_SIGN_ACT_BY").contains("R.GOV_SIGN_ACT_BY");
		assertThat(ReviewDao.FIND_BY_ACCT).contains(ReviewDao.REVIEW_STATE_EXPR).contains("WHERE A.ACCT_ID = :acctId")
				.doesNotContain(":stateCode").doesNotContain(":keyword");
		assertThat(ReviewDao.NEXT_REVIEW_ID).contains("SEQ_IM_ACCT_REVIEW.NEXTVAL AS CNT");
		// 首簽建輪（⑮A）：狀態 OPEN、版本 0、兩組簽核欄都綁參數（Service 決定哪一組有值）、時間綁 :now
		assertThat(ReviewDao.INSERT_OPEN).contains("INSERT INTO IM_ACCT_REVIEW").contains("'OPEN'").contains(":dueDate")
				.contains(":infraSignBy, :infraSignActBy, :infraSignDate, :govSignBy, :govSignActBy, :govSignDate")
				.contains("0, 1, :now, :createBy").doesNotContain("SEQ_IM_ACCT_REVIEW").doesNotContain("REJECT");
		// 簽半：版本相符、進行中、本半尚未簽，VERSION_NO＋1；只動自己那一半的欄位
		assertThat(ReviewDao.UPDATE_SIGN_INFRA).contains("INFRA_SIGN_BY = :signBy, INFRA_SIGN_ACT_BY = :actBy, INFRA_SIGN_DATE = :now")
				.contains("VERSION_NO = VERSION_NO + 1").contains("VERSION_NO = :versionNo").contains("REVIEW_STATE_CODE = 'OPEN'")
				.contains("INFRA_SIGN_BY IS NULL").doesNotContain("GOV_SIGN");
		assertThat(ReviewDao.UPDATE_SIGN_GOV).contains("GOV_SIGN_BY = :signBy, GOV_SIGN_ACT_BY = :actBy, GOV_SIGN_DATE = :now")
				.contains("VERSION_NO = VERSION_NO + 1").contains("VERSION_NO = :versionNo").contains("REVIEW_STATE_CODE = 'OPEN'")
				.contains("GOV_SIGN_BY IS NULL").doesNotContain("INFRA_SIGN");
		// 雙簽完成：兩半都已簽才改 DONE，寫完成時間與新下次應審查日
		assertThat(ReviewDao.UPDATE_DONE).contains("REVIEW_STATE_CODE = 'DONE'").contains("DONE_DATE = :now")
				.contains("NEXT_DUE_DATE = :nextDueDate").contains("VERSION_NO = :versionNo").contains("REVIEW_STATE_CODE = 'OPEN'")
				.contains("INFRA_SIGN_BY IS NOT NULL").contains("GOV_SIGN_BY IS NOT NULL");
		// ①A：簽核交易先鎖帳號主表那一列（列鎖、綁參數；不用 WAIT n，同帳號的寫入排隊）
		assertThat(AcctDao.LOCK_FOR_REVIEW).contains("FROM IM_ACCT").contains("WHERE ACCT_ID = :acctId").contains("FOR UPDATE")
				.doesNotContain("WAIT");
		// 歷史（㉑A）：寫入與查詢都帶 REVIEW_ID
		assertThat(AcctHistDao.INSERT_HIST).contains("REVIEW_ID").contains(":reviewId");
		assertThat(AcctHistDao.FIND_BY_ACCT).contains("H.REVIEW_ID");
		assertThat(AcctConst.REVIEW_SIGNABLE_STATES).containsExactly("DUE", "OVERDUE");
		assertThat(AcctConst.REVIEW_HALVES).containsExactly("INFRA", "GOV");
	}

	/** 第 158 項 R3b：駁回 SQL——首次動作就是駁回直接建 REJECTED、駁回進行中一輪（①A 被駁回那一半尚未簽）、單輪查詢 */
	@Test
	void 定期審查SQL_駁回_直接建REJECTED_駁回進行中一輪條件_單輪查詢帶駁回欄() {
		// 首次動作就是駁回：狀態 REJECTED、駁回五欄都綁參數、版本 0；不寫兩半簽核欄
		assertThat(ReviewDao.INSERT_REJECTED).contains("INSERT INTO IM_ACCT_REVIEW").contains("'REJECTED'")
				.contains("REJECT_HALF_CODE, REJECT_BY, REJECT_ACT_BY, REJECT_DATE, REJECT_REASON")
				.contains(":half, :rejectBy, :actBy, :now, :reason").contains("0, 1, :now, :actBy")
				.doesNotContain("SIGN").doesNotContain("'OPEN'").doesNotContain("SYSDATE");
		// 駁回進行中一輪：版本相符、進行中、被駁回的那一半尚未有人簽（①A）；VERSION_NO＋1；不動兩半簽核欄（③A 已簽的留存）
		assertThat(ReviewDao.UPDATE_REJECT).contains("REVIEW_STATE_CODE = 'REJECTED'")
				.contains("REJECT_HALF_CODE = :half, REJECT_BY = :rejectBy, REJECT_ACT_BY = :actBy")
				.contains("REJECT_DATE = :now, REJECT_REASON = :reason").contains("VERSION_NO = VERSION_NO + 1")
				.contains("VERSION_NO = :versionNo").contains("REVIEW_STATE_CODE = 'OPEN'")
				.contains("((:half = 'INFRA' AND INFRA_SIGN_BY IS NULL) OR (:half = 'GOV' AND GOV_SIGN_BY IS NULL))")
				.doesNotContain("INFRA_SIGN_BY =").doesNotContain("GOV_SIGN_BY =");
		// 單輪查詢：以 REVIEW_ID 接、不限該輪狀態，多帶該輪結果與駁回五欄（含駁回人姓名）；仍含帳號審查狀態欄
		assertThat(ReviewDao.FIND_ROUND).contains("R.REVIEW_ID = :reviewId").contains("R.REVIEW_STATE_CODE AS ROUND_STATE_CODE")
				.contains("R.REJECT_HALF_CODE, R.REJECT_BY").contains("AS REJECT_BY_NAME").contains("R.REJECT_ACT_BY, R.REJECT_DATE, R.REJECT_REASON")
				.contains(ReviewDao.REVIEW_STATE_EXPR).doesNotContain("REVIEW_STATE_CODE = 'OPEN'");
		// 清單仍只接進行中那一輪，不帶駁回欄
		assertThat(ReviewDao.SELECT_BASE).contains("R.REVIEW_STATE_CODE = 'OPEN' AND R.STATUS = 1").doesNotContain("REJECT_");
		assertThat(AcctConst.FLAG_SRC_REVIEW).isEqualTo("REVIEW");
		assertThat(AcctConst.EV_REVIEW_REJECT).isEqualTo("REVIEW_REJECT");
		assertThat(AcctConst.REF_REVIEW).isEqualTo("REVIEW");
	}

	@Test
	void 樂觀鎖_狀態轉移都帶版本與預期狀態並遞增版本() {
		for (String sql : List.of(AcctReqDao.UPDATE_APPROVE, AcctReqDao.UPDATE_REJECT, AcctReqDao.UPDATE_EXEC_DONE,
				AcctReqDao.UPDATE_VERIFY_OK, AcctReqDao.UPDATE_VERIFY_REJECT, AcctReqDao.UPDATE_RESUBMIT)) {
			assertThat(sql).contains("VERSION_NO = :versionNo").contains("VERSION_NO = VERSION_NO + 1")
					.contains("REQ_STATE_CODE =").contains("CURR_STAGE_ID");
		}
		assertThat(AcctReqDao.UPDATE_EXEC_DONE).contains("EFFECT_DATE = NVL(EFFECT_DATE, :now)")
				// 門禁卡新增：申請單 ACCT_NAME 與 CARD_NO 同條件寫卡號
				.contains("ACCT_NAME = CASE WHEN :setCard = 1 THEN :cardNo ELSE ACCT_NAME END");
		assertThat(AcctReqDao.UPDATE_REJECT).contains("REJECT_CNT = REJECT_CNT + 1");
		assertThat(AcctReqDao.UPDATE_VERIFY_REJECT).contains("VERIFY_REJECT_CNT = VERIFY_REJECT_CNT + 1");
		assertThat(AcctReqDao.UPDATE_RESUBMIT).doesNotContain("APPLY_DATE");
		// 緊急存取回報（S4a）：帶版本、限本人、限尚未回報且主管未處理
		assertThat(EmgDao.UPDATE_REPORT).contains("VERSION_NO = :versionNo").contains("VERSION_NO = VERSION_NO + 1")
				.contains("APPLY_BY = :applyBy").contains("REPORT_TYPE_CODE IS NULL").contains("DECIDE_CODE IS NULL")
				.contains("STATUS = 1");
		// 緊急存取主管處理（S4b）：帶版本、限尚未處理；逾期後仍可處理所以不看 DUE_DATE
		assertThat(EmgDao.UPDATE_DECIDE).contains("VERSION_NO = :versionNo").contains("VERSION_NO = VERSION_NO + 1")
				.contains("DECIDE_CODE IS NULL").contains("STATUS = 1").contains("IS_SELF_DECIDE = :isSelfDecide")
				.doesNotContain("DUE_DATE");
		// 緊急存取異常結案（S6）：帶版本、限已退回且尚未結案
		assertThat(EmgDao.UPDATE_CLOSE).contains("VERSION_NO = :versionNo").contains("VERSION_NO = VERSION_NO + 1")
				.contains("DECIDE_CODE = 'RETURN'").contains("CLOSE_DATE IS NULL").contains("STATUS = 1")
				.contains("IS_SELF_CLOSE = :isSelfClose");
	}

	@Test
	void 帳號歷史_寫入帶EMG_ID_查詢取申請單或緊急存取單申請人() {
		assertThat(AcctHistDao.INSERT_HIST).contains("EMG_ID").contains(":emgId");
		assertThat(AcctHistDao.FIND_BY_ACCT).contains("LEFT JOIN IM_EMG_ACCESS EM ON EM.EMG_ID = H.EMG_ID")
				.contains("COALESCE(R.APPLY_BY, EM.APPLY_BY, A.APPLY_BY) AS REQ_APPLY_BY")
				// 第 158 項 R3a M2 ③A：定期審查列的原申請人取帳號主表，且只對 REVIEW 來源 JOIN（其他來源仍取各自那張單）
				.contains("LEFT JOIN IM_ACCT A ON A.ACCT_ID = H.ACCT_ID AND H.SOURCE_CODE = 'REVIEW'");
		assertThat(AcctDao.UPDATE_REVIEW_FLAG).contains("IS_REVIEW_FLAG = 1").contains("REVIEW_FLAG_SOURCE_CODE = :sourceCode")
				.contains("REVIEW_FLAG_BY = :flagBy").doesNotContain("SYSDATE");
		// S10：清旗標須 5 欄歸零／NULL，且帶 IS_REVIEW_FLAG = 1 條件（已被清過影響 0 筆、不重寫 FLAG_CLEAR 歷史）
		assertThat(AcctDao.CLEAR_REVIEW_FLAG).contains("IS_REVIEW_FLAG = 0").contains("REVIEW_FLAG_REASON = NULL")
				.contains("REVIEW_FLAG_SOURCE_CODE = NULL").contains("REVIEW_FLAG_DATE = NULL")
				.contains("REVIEW_FLAG_BY = NULL").contains("AND IS_REVIEW_FLAG = 1")
				.contains("AND REVIEW_FLAG_DATE <= :execDate").doesNotContain("SYSDATE");
	}

	/** 第 89 項名單維護：全域鎖、持有人數（排除以哨兵處理 null）、在途查詢、名單列狀態轉移、軌跡 REASON 綁參數 */
	@Test
	void 名單維護_全域鎖_持有人數排除哨兵_在途查詢_狀態轉移帶現況() {
		assertThat(RoleAdminDao.LOCK_GLOBAL).contains("FROM IM_ACCT_ROLE").contains("ROLE_ID = :generalRoleId")
				.contains("FOR UPDATE WAIT " + AcctConst.ADMIN_LOCK_WAIT_SECONDS).doesNotContain("NOWAIT");
		assertThat(RoleAdminDao.COUNT_HOLDERS).contains("WHERE " + EmpSql.activeUserCond("U")).contains("RP.PERM_ID = :permId")
				.contains("NOT (RP.ROLE_ID = NVL(:exclRoleId, '-') AND RP.PERM_ID = NVL(:exclPermId, '-'))")
				.contains("NOT (RU.ROLE_ID = NVL(:exclRoleId, '-') AND RU.USER_ID = NVL(:exclUserId, '-'))")
				.contains("RP.ROLE_ID = :generalRoleId").contains(PermDao.ACTIVE_ROLE_PERM);
		assertThat(RoleAdminDao.FIND_PERM_IDS_OF_ROLE).contains(PermDao.ACTIVE_ROLE_PERM).contains("RP.ROLE_ID = :roleId");
		assertThat(RoleAdminDao.FIND_IN_FLIGHT_BY_STAGE_PERM).contains("R.REQ_STATE_CODE IN ('REVIEW', 'VERIFY')")
				.contains("S.PERM_ID = :permId").contains("JOIN IM_ACCT_APPR_STAGE S ON S.STAGE_ID = R.CURR_STAGE_ID");
		assertThat(RoleAdminDao.FIND_IN_FLIGHT_EMG).contains(EmgDao.UNDECIDED_COND).contains(EmgDao.RETURNED_COND)
				.doesNotContain(":now");
		// 第 158 項 R3b：在途定期審查＝進行中且 :half 那一半尚未簽；單號欄放帳號單號
		assertThat(RoleAdminDao.FIND_IN_FLIGHT_REVIEW).contains("R.ACCT_ID AS DOC_ID").contains("AS STAGE_NAME")
				.contains("R.REVIEW_STATE_CODE = 'OPEN'").contains("R.STATUS = 1")
				.contains("((:half = 'INFRA' AND R.INFRA_SIGN_BY IS NULL) OR (:half = 'GOV' AND R.GOV_SIGN_BY IS NULL))");
		assertThat(RoleAdminDao.UPDATE_ROLE_MEMBER_STATUS).contains("STATUS = :newStatus").contains("AND STATUS = :oldStatus")
				.contains("UPDATE_DATE = :now, UPDATE_BY = :userId").doesNotContain("CREATE_");
		assertThat(RoleAdminDao.INSERT_ROLE_MEMBER).contains("STATUS, CREATE_DATE, CREATE_BY").doesNotContain("UPDATE_");
		assertThat(RoleAdminDao.FIND_ROLE_MEMBERS).contains("NVL(RU.UPDATE_DATE, RU.CREATE_DATE) AS GRANT_DATE")
				.contains("LEFT JOIN IM_ACCT_USER U ON U.USER_ID = RU.USER_ID").contains("RU.STATUS = 1");
		assertThat(RoleAdminDao.FIND_ROLES).contains("ROLE_ID <> :generalRoleId").contains("STATUS = 1");
		assertThat(AdminLogDao.INSERT).contains(":reason").doesNotContain("NULL,");
	}

	/** 第 89 項階段 2：執行人對照——與角色指派同形狀的名單 SQL、執行人數排除哨兵、在途只看 EXEC 關卡 6 同類別 */
	@Test
	void 執行人對照_名單SQL同形狀_執行人數排除哨兵_在途只看關卡6同類別() {
		assertThat(RoleAdminDao.FIND_EXEC_MEMBERS).contains("M.CATG_ID AS GROUP_ID").contains("FROM IM_EXEC_MAP M")
				.contains("NVL(M.UPDATE_DATE, M.CREATE_DATE) AS GRANT_DATE").contains("LEFT JOIN IM_ACCT_USER U ON U.USER_ID = M.USER_ID")
				.contains("M.STATUS = 1");
		assertThat(RoleAdminDao.FIND_EXEC_MEMBER_STATUS).contains("FROM IM_EXEC_MAP").contains("CATG_ID = :catgId")
				.contains("USER_ID = :targetId");
		assertThat(RoleAdminDao.UPDATE_EXEC_MEMBER_STATUS).contains("UPDATE IM_EXEC_MAP").contains("STATUS = :newStatus")
				.contains("AND STATUS = :oldStatus").contains("UPDATE_DATE = :now, UPDATE_BY = :userId").doesNotContain("CREATE_");
		assertThat(RoleAdminDao.INSERT_EXEC_MEMBER).contains("INSERT INTO IM_EXEC_MAP (CATG_ID, USER_ID, STATUS, CREATE_DATE, CREATE_BY)")
				.doesNotContain("UPDATE_");
		assertThat(RoleAdminDao.COUNT_EXECUTORS).contains("JOIN IM_ACCT_USER U ON U.USER_ID = M.USER_ID\n")
				.contains("AND " + EmpSql.activeUserCond("U")).contains("M.CATG_ID = :catgId").contains("M.STATUS = 1").contains("M.USER_ID <> NVL(:exclUserId, '-')");
		assertThat(RoleAdminDao.FIND_IN_FLIGHT_EXEC_BY_CATG).contains("R.REQ_STATE_CODE = :execState")
				.contains("R.CURR_STAGE_ID = :execStage").contains("R.CATG_ID = :catgId")
				.contains("JOIN IM_ACCT_APPR_STAGE S ON S.STAGE_ID = R.CURR_STAGE_ID").doesNotContain("PERM_ID");
	}

	/** 第 89 項階段 3：角色權限設定頁的 SQL——角色含 GENERAL、可見碼綁參數、成員數口徑、權限列現況含 STATUS＝0、狀態轉移保留 CREATE_* */
	@Test
	void 角色權限設定_角色含GENERAL_可見碼綁參數_成員數口徑_權限列現況與狀態轉移() {
		assertThat(RoleAdminDao.FIND_ROLES_WITH_GENERAL).contains("STATUS = 1").doesNotContain("generalRoleId")
				.contains("ORDER BY SORT_SEQ, ROLE_ID");
		assertThat(RoleAdminDao.FIND_PERMS_BY_IDS).contains("FROM IM_ACCT_PERM").contains("STATUS = 1")
				.contains("PERM_ID IN (:permIds)").contains("ORDER BY SORT_SEQ, PERM_ID").doesNotContain("PHASE_CODE");
		assertThat(RoleAdminDao.COUNT_ROLE_MEMBERS).contains("RU.ROLE_ID AS CODE, COUNT(*) AS CNT")
				.contains("JOIN IM_ACCT_USER U ON U.USER_ID = RU.USER_ID\n").contains("WHERE RU.STATUS = 1")
				.contains("AND " + EmpSql.activeUserCond("U")).contains("GROUP BY RU.ROLE_ID");
		assertThat(RoleAdminDao.COUNT_ACTIVE_USERS).contains("FROM IM_ACCT_USER U").contains("WHERE " + EmpSql.activeUserCond("U"));
		// 現況要含 STATUS＝0 與隱藏碼（決定 UPDATE 0→1 還是 INSERT；隱藏碼原樣保留），所以不過濾 STATUS、不 JOIN 權限表
		assertThat(RoleAdminDao.FIND_ROLE_PERM_STATUSES).contains("PERM_ID AS CODE, STATUS").contains("FROM IM_ACCT_ROLE_PERM")
				.contains("ROLE_ID = :roleId").doesNotContain("STATUS = 1").doesNotContain("JOIN");
		assertThat(RoleAdminDao.UPDATE_ROLE_PERM_STATUS).contains("UPDATE IM_ACCT_ROLE_PERM").contains("STATUS = :newStatus")
				.contains("AND STATUS = :oldStatus").contains("PERM_ID = :permId").contains("UPDATE_DATE = :now, UPDATE_BY = :userId")
				.doesNotContain("CREATE_");
		assertThat(RoleAdminDao.INSERT_ROLE_PERM).contains("INSERT INTO IM_ACCT_ROLE_PERM (ROLE_ID, PERM_ID, STATUS, CREATE_DATE, CREATE_BY)")
				.doesNotContain("UPDATE_");
	}

	/** 裁示 ⑮A：每個 PERM_* 常數都要在角色權限設定頁的可見清單內，否則新功能上線後管理員無法在畫面授予（計畫風險 9） */
	@Test
	void 可見權限碼清單_涵蓋每個PERM常數與關卡碼_恰23碼_隱藏碼不在內() throws Exception {
		List<String> permConsts = new ArrayList<>();
		for (Field f : AcctConst.class.getDeclaredFields()) {
			if (f.getName().startsWith("PERM_") && f.getType() == String.class) {
				permConsts.add((String) f.get(null));
			}
		}
		assertThat(permConsts).hasSizeGreaterThan(10);
		assertThat(AcctConst.ADMIN_VISIBLE_PERMS).containsAll(permConsts).containsAll(AcctConst.PERM_STAGE_CODES)
				.doesNotHaveDuplicates().hasSize(23)
				.contains("REVIEW_LIST", "REVIEW_SIGN_INFRA", "REVIEW_SIGN_GOV")
				.doesNotContain("SYS_AUDIT_VIEW", "STATS_VIEW", "INSP_EXPORT", "MAINT_ASSET_CODE", "MAINT_INSP_ITEM");
		assertThat(AcctConst.PERM_STAGE_CODES).containsExactly("APPR_G2", "APPR_G3", "APPR_G4", "APPR_G5", "VERIFY_G7");
		assertThat(AcctConst.ADM_ROLE_PERM_SET).hasSizeLessThanOrEqualTo(30);
	}

	@Test
	void 代碼常數_無已廢除值() throws Exception {
		List<String> abolished = List.of("EXTEND", "VOID", "CONFIRM", "NA", "EXTEND_DONE", "AC", "AI",
				"EM", "RD_USER.IM_USER");
		for (Map.Entry<String, String> e : constants(List.of(AcctConst.class)).entrySet()) {
			assertThat(abolished).as(e.getKey()).doesNotContain(e.getValue());
		}
		assertThat(AcctConst.REQ_TYPES).containsExactly("ADD", "MODIFY", "DISABLE", "ENABLE", "DELETE");
		// 清旗標類型＝D42 的修改／停用，重新啟用也清（S10 開工裁示）；新增與刪除不清
		assertThat(AcctConst.FLAG_CLEAR_REQ_TYPES).containsExactly("MODIFY", "DISABLE", "ENABLE");
		assertThat(AcctConst.REQ_STATES).containsExactly("REVIEW", "EXEC", "VERIFY", "APPROVED", "REJECTED");
		assertThat(AcctConst.ACCT_STATES).containsExactly("REVIEW", "APPROVED", "EXPIRED", "DISABLED", "REJECTED", "DELETED");
		assertThat(AcctConst.RESULT_CODES).containsExactly("NORMAL", "EXCPT");
		assertThat(AcctConst.EMG_STATES).containsExactly("PENDING", "OVERDUE", "APPROVED", "RETURNED", "CLOSED");
		assertThat(AcctConst.EMG_STATE_FILTERS).containsExactly("PENDING", "OVERDUE", "APPROVED", "RETURNED", "CLOSED", "UNDECIDED");
		assertThat(AcctConst.STAGE_EXEC).isEqualTo(6);
		assertThat(AcctConst.STAGE_VERIFY).isEqualTo(7);
	}

	@Test
	void Controller_除登入外每個API都宣告Identity() {
		List<String> missing = new ArrayList<>();
		int apis = 0;
		for (Class<?> c : CONTROLLERS) {
			for (Method m : c.getDeclaredMethods()) {
				boolean api = m.isAnnotationPresent(GetMapping.class) || m.isAnnotationPresent(PostMapping.class)
						|| m.isAnnotationPresent(PutMapping.class) || m.isAnnotationPresent(DeleteMapping.class)
						|| m.isAnnotationPresent(RequestMapping.class);
				if (!api) {
					continue;
				}
				apis++;
				boolean login = c == AuthController.class && m.getName().equals("login");
				boolean hasId = List.of(m.getParameterTypes()).contains(Identity.class);
				if (login == hasId) {
					missing.add(c.getSimpleName() + "." + m.getName());
				}
			}
		}
		// 第 89 項階段 1：/admin/roles、/admin/users、/admin/roles/{roleId}/members、…/{userId}/revoke（39 → 43）
		// 第 89 項階段 2：/admin/executors、/admin/executors/{catgId}/members、…/{userId}/revoke（43 → 46）
		// 第 89 項階段 3：/admin/role-perms、/admin/roles/{roleId}/perms（46 → 48）
		// 第 158 項 R2：GET /reviews（48 → 49）；R3a：POST /reviews/{acctId}/sign（49 → 50）；R3b：POST /reviews/{acctId}/reject（50 → 51）
		assertThat(apis).isEqualTo(51);
		assertThat(missing).isEmpty();
	}
}
