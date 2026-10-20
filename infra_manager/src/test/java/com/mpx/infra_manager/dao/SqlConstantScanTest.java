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
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

class SqlConstantScanTest {

	static final List<Class<?>> DAOS = List.of(AcctDao.class, AcctHistDao.class, AcctReqDao.class, AdminLogDao.class,
			AttachDao.class, CodeDao.class, DocSeqDao.class, EmgDao.class, HomeDao.class, InspDao.class, JobRunDao.class,
			LoginTokenDao.class, NotifyLogDao.class, PermDao.class, RoleAdminDao.class, UserDao.class);

	static final List<Class<?>> CONTROLLERS = List.of(AccountController.class, AdminController.class, AttachmentController.class,
			AuthController.class, EmergencyController.class, HomeController.class, InspectionController.class, JobController.class,
			RequestController.class);

	/** 既有模組使用者表（IM_ACCT_USER 不算） */
	static final Pattern IM_USER = Pattern.compile("(?<![\\w])IM_USER\\b");
	static final Pattern RD_USER = Pattern.compile("\\bRD_USER\\.");
	static final Pattern SYSDATE = Pattern.compile("\\bSYSDATE\\b");
	static final Pattern ABOLISHED_CODE = Pattern.compile("'(EXTEND|VOID|CONFIRM|NA|TO_EXEC|EXTEND_DONE|IMPORT\\w*)'");
	static final Pattern ABOLISHED_COLUMN = Pattern.compile(
			"\\b(ORIG_REQ_ID|ORIG_ACCT_ID|NEW_ACCT_ID|IMPORT_ID|DISABLE_TYPE_CODE|REVIEW_BASIS_CODE|IS_DIRECT|IS_SHOW|APPLY_TEL_NO|APPLY_EMAIL|EXCPT_CNT\\s*,\\s*INSP_STATE_CODE)\\b");
	/** END 後面直接接英文字母（END_DATE 之類有底線的欄名不算） */
	static final Pattern GLUED_END = Pattern.compile("\\bEND[A-Z]");
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
		}
		assertThat(bad).isEmpty();
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
		assertThat(EmpSql.activeUserCond("U")).isEqualTo("U.STATUS = 1 AND " + EmpSql.inSyncCond("U.USER_ID"));
		assertThat(EmpSql.primaryDeptExpr(":userId"))
				// ⑪：主管的部門優先、其次 DEPT_ID 最小；全名為字串 'NULL' 當空、退回部門名
				.contains("KEEP (DENSE_RANK FIRST ORDER BY PXDE.IS_MGR DESC, PXDE.DEPT_ID)")
				.contains("NVL(NULLIF(TRIM(PXD.DEPT_FULL_NAME), 'NULL'), PXD.DEPT_NAME)")
				// E2：部門列與部門都只認 STATUS＝1
				.contains("PXD.STATUS = 1").contains("PXDE.STATUS = 1").contains("PXDE.EMP_ID = :userId");
		// D9B：資格只看主要部門（不是任一部門）；D8A：關鍵字寫在 Java 常數
		assertThat(EmpSql.QUALIFIED_DEPT_KEYWORD).isEqualTo("資訊部");
		assertThat(EmpSql.qualifiedCond(":userId")).isEqualTo(EmpSql.inSyncCond(":userId") + " AND "
				+ EmpSql.primaryDeptExpr(":userId") + " LIKE '%資訊部%'");
	}

	@Test
	void 人員姓名一律取IM_ACCT_USER或快照() {
		assertThat(AcctReqDao.FIND_BY_ID).contains("LEFT JOIN IM_ACCT_USER UA ON UA.USER_ID = R.APPLY_BY");
		assertThat(AcctHistDao.FIND_BY_ACCT).contains("H.ACT_BY_NAME").doesNotContain("IM_ACCT_USER");
		assertThat(InspDao.INSP_SUMMARY).contains("I.INSP_BY_NAME").doesNotContain("IM_ACCT_USER");
		assertThat(EmgDao.SELECT_BASE).contains("LEFT JOIN IM_ACCT_USER UA ON UA.USER_ID = E.APPLY_BY")
				.contains("LEFT JOIN IM_ACCT_USER UD ON UD.USER_ID = E.DECIDE_BY")
				.contains("LEFT JOIN IM_ACCT_USER UC ON UC.USER_ID = E.CLOSE_BY")
				// S5：四個實際操作人也回姓名（送出 CREATE_BY、回報／處理／結案 *_ACT_BY）
				.contains("E.CREATE_BY,").contains("AS CREATE_BY_NAME").contains("LEFT JOIN IM_ACCT_USER UX ON UX.USER_ID = E.CREATE_BY")
				.contains("AS REPORT_ACT_BY_NAME").contains("LEFT JOIN IM_ACCT_USER URA ON URA.USER_ID = E.REPORT_ACT_BY")
				.contains("AS DECIDE_ACT_BY_NAME").contains("LEFT JOIN IM_ACCT_USER UDA ON UDA.USER_ID = E.DECIDE_ACT_BY")
				.contains("AS CLOSE_ACT_BY_NAME").contains("LEFT JOIN IM_ACCT_USER UCA ON UCA.USER_ID = E.CLOSE_ACT_BY");
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
				.contains("COALESCE(R.APPLY_BY, EM.APPLY_BY) AS REQ_APPLY_BY");
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
		assertThat(RoleAdminDao.COUNT_HOLDERS).contains("U.STATUS = 1").contains("RP.PERM_ID = :permId")
				.contains("NOT (RP.ROLE_ID = NVL(:exclRoleId, '-') AND RP.PERM_ID = NVL(:exclPermId, '-'))")
				.contains("NOT (RU.ROLE_ID = NVL(:exclRoleId, '-') AND RU.USER_ID = NVL(:exclUserId, '-'))")
				.contains("RP.ROLE_ID = :generalRoleId").contains(PermDao.ACTIVE_ROLE_PERM);
		assertThat(RoleAdminDao.FIND_PERM_IDS_OF_ROLE).contains(PermDao.ACTIVE_ROLE_PERM).contains("RP.ROLE_ID = :roleId");
		assertThat(RoleAdminDao.FIND_IN_FLIGHT_BY_STAGE_PERM).contains("R.REQ_STATE_CODE IN ('REVIEW', 'VERIFY')")
				.contains("S.PERM_ID = :permId").contains("JOIN IM_ACCT_APPR_STAGE S ON S.STAGE_ID = R.CURR_STAGE_ID");
		assertThat(RoleAdminDao.FIND_IN_FLIGHT_EMG).contains(EmgDao.UNDECIDED_COND).contains(EmgDao.RETURNED_COND)
				.doesNotContain(":now");
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
		assertThat(RoleAdminDao.COUNT_EXECUTORS).contains("JOIN IM_ACCT_USER U ON U.USER_ID = M.USER_ID AND U.STATUS = 1")
				.contains("M.CATG_ID = :catgId").contains("M.STATUS = 1").contains("M.USER_ID <> NVL(:exclUserId, '-')");
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
				.contains("JOIN IM_ACCT_USER U ON U.USER_ID = RU.USER_ID AND U.STATUS = 1").contains("RU.STATUS = 1")
				.contains("GROUP BY RU.ROLE_ID");
		assertThat(RoleAdminDao.COUNT_ACTIVE_USERS).contains("FROM IM_ACCT_USER").contains("STATUS = 1");
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
	void 可見權限碼清單_涵蓋每個PERM常數與關卡碼_恰20碼_隱藏碼不在內() throws Exception {
		List<String> permConsts = new ArrayList<>();
		for (Field f : AcctConst.class.getDeclaredFields()) {
			if (f.getName().startsWith("PERM_") && f.getType() == String.class) {
				permConsts.add((String) f.get(null));
			}
		}
		assertThat(permConsts).hasSizeGreaterThan(10);
		assertThat(AcctConst.ADMIN_VISIBLE_PERMS).containsAll(permConsts).containsAll(AcctConst.PERM_STAGE_CODES)
				.doesNotHaveDuplicates().hasSize(20)
				.doesNotContain("SYS_AUDIT_VIEW", "REVIEW_LIST", "REVIEW_SIGN_INFRA", "REVIEW_SIGN_GOV", "STATS_VIEW", "INSP_EXPORT",
						"MAINT_ASSET_CODE", "MAINT_INSP_ITEM");
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
		assertThat(apis).isEqualTo(48);
		assertThat(missing).isEmpty();
	}
}
