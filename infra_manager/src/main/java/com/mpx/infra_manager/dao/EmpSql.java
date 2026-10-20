package com.mpx.infra_manager.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-20
// 變更說明: 新增：讀公司同步表（PX_SYNC_EMP、PX_SYNC_DEPT_EMP、PX_SYNC_DEPT）的共用 SQL 片段
//           （BACKLOG 第 124 項階段 2 回合 2；裁示 ⑦B 全系統直接讀同步表、E2 一律只認 STATUS＝1、
//           D2A 查無顯示工號、⑪ 主要部門、D8A／D9B 資格寫在 Java 常數且只看主要部門、E3 有效使用者口徑）。
//           同步表以 PUBLIC 別名存取、不加 RD_USER. 前綴（D11）；本批尚無 DAO 引用，階段 3 起逐步接上
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: 第 124 項階段 3 回合 1（階段 2 審查 S2、S3，使用者裁示 A）：
//           參數守門——別名只收大寫英數底線且不得 PX 開頭、工號運算式只收「別名.欄位」或綁定參數名，不符丟 IllegalArgumentException
//           （呼叫端若誤用片段內部別名 PXE／PXDE／PXD，條件會恆真、首次登入資格等於人人符合，且不會報錯）；
//           activeUserCond、qualifiedCond 回傳值包一層括號（接在 NOT 後或與 OR 混用才不會算錯）
// ============================================================

import java.util.regex.Pattern;

/**
 * 同步表 SQL 片段。人名、Email、部門一律從這裡取，口徑才不會各處寫成不一樣。
 * <p>
 * 規則：
 * <ul>
 * <li>同步表任何一張都只認 {@code STATUS = 1}（裁示 E2：表註解寫「資料列狀態」，0 很可能代表已刪除）</li>
 * <li>本系統的人員列不得 INNER JOIN {@code PX_SYNC_EMP}：同步表查無的人要照樣列出（人名退回工號），
 * 判斷「查得到」用 {@link #inSyncCond}；{@code SqlConstantScanTest} 會擋</li>
 * <li>參數只能是程式寫死的別名、「別名.欄位」或綁定參數名（{@code :userId}），不得是值；{@code PX} 開頭的別名保留給片段內部
 * （PXE、PXDE、PXD），傳入會丟 {@link IllegalArgumentException}——這是靜態組 SQL 時的防呆，不是執行期輸入檢查</li>
 * <li>同一個綁定參數名可能在片段裡出現兩次（例 {@link #qualifiedCond}），本專案用具名參數沒問題，不要改成 {@code ?} 位置參數</li>
 * </ul>
 */
final class EmpSql {

	private EmpSql() {
	}

	/** 首次登入自動建立的資格：主要部門名稱含此字（裁示 ①、D8A、D9B；放寬範圍改這裡，會進 git） */
	static final String QUALIFIED_DEPT_KEYWORD = "資訊部";

	/** 片段內部保留的別名開頭 */
	static final String RESERVED_ALIAS_PREFIX = "PX";
	private static final Pattern ALIAS = Pattern.compile("[A-Z][A-Z0-9_]*");
	private static final Pattern COLUMN_REF = Pattern.compile("([A-Z][A-Z0-9_]*)\\.[A-Z][A-Z0-9_]*");
	private static final Pattern BIND_PARAM = Pattern.compile(":[A-Za-z]\\w*");

	/** 別名：大寫英數底線、不得 PX 開頭 */
	static String alias(String a) {
		if (a == null || !ALIAS.matcher(a).matches() || a.startsWith(RESERVED_ALIAS_PREFIX)) {
			throw new IllegalArgumentException("EmpSql 別名不合法：" + a);
		}
		return a;
	}

	/** 工號運算式：「別名.欄位」（別名不得 PX 開頭）或綁定參數名 */
	static String idExpr(String expr) {
		if (expr != null) {
			if (BIND_PARAM.matcher(expr).matches()) {
				return expr;
			}
			java.util.regex.Matcher m = COLUMN_REF.matcher(expr);
			if (m.matches() && !m.group(1).startsWith(RESERVED_ALIAS_PREFIX)) {
				return expr;
			}
		}
		throw new IllegalArgumentException("EmpSql 工號運算式不合法：" + expr);
	}

	/**
	 * LEFT JOIN 同步人員檔。
	 *
	 * @param e      同步人員檔別名
	 * @param idExpr 工號運算式（例：{@code U.USER_ID}、{@code R.APPLY_BY}）
	 */
	static String joinEmp(String e, String idExpr) {
		alias(e);
		idExpr(idExpr);
		return "LEFT JOIN PX_SYNC_EMP " + e + " ON " + e + ".EMP_ID = " + idExpr + " AND " + e + ".STATUS = 1";
	}

	/**
	 * 人名：同步表中文名，查無或空白時顯示工號（裁示 D2A、⑬B）；工號為空時為 NULL。須搭配 {@link #joinEmp} 同一組別名。
	 */
	static String nameExpr(String e, String idExpr) {
		alias(e);
		idExpr(idExpr);
		return "NVL(TRIM(" + e + ".EMP_NAME), " + idExpr + ")";
	}

	/** 同步人員檔查得到此工號（只認 STATUS＝1）。可放在 WHERE，或包成 CASE WHEN … THEN 1 ELSE 0 END 當旗標（裁示 E1） */
	static String inSyncCond(String idExpr) {
		idExpr(idExpr);
		return "EXISTS (SELECT 1 FROM PX_SYNC_EMP PXE WHERE PXE.EMP_ID = " + idExpr + " AND PXE.STATUS = 1)";
	}

	/**
	 * 有效使用者：本系統啟用中且同步表查得到（裁示 E3）。人數計算、「最後一位持有人」保護、收件人、人員下拉一律用這個。
	 * 回傳值已包括號，可直接接在 NOT 後面。
	 *
	 * @param u IM_ACCT_USER 別名
	 */
	static String activeUserCond(String u) {
		alias(u);
		return "(" + u + ".STATUS = 1 AND " + inSyncCond(u + ".USER_ID") + ")";
	}

	/**
	 * 主要部門名稱（純量子查詢；裁示 ⑪）：本人為主管（IS_MGR＝1）的部門優先，其次 DEPT_ID 最小（字串排序，DEPT_ID 為英數字串）；
	 * 名稱取部門全名，全名為空或字串 'NULL' 時取部門名。沒有有效部門列時為 NULL。
	 * <p>
	 * PX_SYNC_DEPT_EMP 主鍵以 DEPT_ID 開頭，以工號查會整表掃描，只在登入資格判斷與 /me 這類單人查詢使用，不要放進名單逐列查
	 * （裁示 ⑬-2：指派下拉只顯示名字，名單不需要部門）。
	 */
	static String primaryDeptExpr(String idExpr) {
		idExpr(idExpr);
		return "(SELECT MAX(NVL(NULLIF(TRIM(PXD.DEPT_FULL_NAME), 'NULL'), PXD.DEPT_NAME))"
				+ " KEEP (DENSE_RANK FIRST ORDER BY PXDE.IS_MGR DESC, PXDE.DEPT_ID)"
				+ " FROM PX_SYNC_DEPT_EMP PXDE JOIN PX_SYNC_DEPT PXD ON PXD.DEPT_ID = PXDE.DEPT_ID AND PXD.STATUS = 1"
				+ " WHERE PXDE.EMP_ID = " + idExpr + " AND PXDE.STATUS = 1)";
	}

	/**
	 * 首次登入自動建立的資格：同步表查得到，且主要部門名稱含 {@link #QUALIFIED_DEPT_KEYWORD}（裁示 ①、D9B：兼任不算）。
	 * 只有部門列、同步人員檔沒有的人不算（測試 DB 2026-10-20 有一例）。內含 {@link #primaryDeptExpr}，只用於單人判斷：
	 * 對全公司逐列算一次要約 110 秒（測試 DB 實測），單人為毫秒級。回傳值已包括號。
	 */
	static String qualifiedCond(String idExpr) {
		return "(" + inSyncCond(idExpr) + " AND " + primaryDeptExpr(idExpr) + " LIKE '%" + QUALIFIED_DEPT_KEYWORD + "%')";
	}
}
