package com.mpx.infra_manager.dao;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: 新增：定期權限審查資料存取（BACKLOG 第 158 項 R2；SA §5.10）
//           審查範圍 REVIEW_SCOPE_COND 與 Java 端 ReviewRule.matches 同口徑（APPROVED 且 分類 IS_REVIEW_TRIGGER＝1 或 DOOR 或特權），
//           資產分類的 JOIN 必須帶 AC.STATUS = 1（Java 端只取啟用中分類；AcctDao.SELECT_BASE 的 JOIN 不過濾，不能共用）；
//           審查狀態 REVIEW_STATE_EXPR 不存欄位、以 :today 綁參數算 ABNORMAL／OVERDUE／DUE／NORMAL，不在範圍為 NULL；
//           「在範圍」寫成正向 WHEN（分類查無時 IS_REVIEW_TRIGGER 為 NULL，寫 NOT (範圍) 會讓 CASE 落到逾期那支）；
//           清單 FIND_LIST 以子查詢 X 包住後再篩（Oracle WHERE 不能用別名）：排除不在範圍、:stateCode 為空＝異常＋逾期＋待複審，
//           關鍵字比對帳號單號、帳號名稱、使用人、所屬系統（LIKE 加 ESCAPE '\'，關鍵字由 Service 跳脫），
//           並 LEFT JOIN 進行中那一輪（IM_ACCT_REVIEW OPEN）帶出兩半簽核人；排序 異常 → 逾期（最舊先）→ 待複審 → 正常
// ============================================================

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager.model.ReviewItem;

@Repository
public class ReviewDao {

	/**
	 * 資產分類 JOIN：只接啟用中的分類，與 Java 端 CodeService.findAssetClass（只回 STATUS＝1）同口徑。
	 * 別名 A＝IM_ACCT、AC＝IM_ASSET_CLASS；用 REVIEW_SCOPE_COND／REVIEW_STATE_EXPR 的 SQL 一律要帶這句
	 */
	public static final String JOIN_ASSET_CLASS = "LEFT JOIN IM_ASSET_CLASS AC ON AC.ASSET_CLASS_ID = A.ASSET_CLASS_ID AND AC.STATUS = 1";

	/**
	 * 審查範圍（SA §5.10；與 ReviewRule.matches 同口徑）：生效中且（分類為審查觸發、或門禁卡、或特權）。
	 * 分類查無時 AC.IS_REVIEW_TRIGGER 為 NULL，整句可能是 NULL（非 TRUE），所以只能當正向條件用、不得寫 NOT (…)
	 */
	public static final String REVIEW_SCOPE_COND = "(A.ACCT_STATE_CODE = 'APPROVED'"
			+ " AND (AC.IS_REVIEW_TRIGGER = 1 OR A.CATG_ID = 'DOOR' OR A.IS_PRIV = 1))";

	/**
	 * 審查狀態（裁示 ⑨A 異常優先、⑥A 日期為空算逾期、⑩A 旗標不分來源）；需綁 :today（當天 00:00）。
	 * ABNORMAL 審查異常（掛旗標且未刪除）→ 不在範圍 NULL → OVERDUE 逾期未審（上次或下次為空、或下次 < 今天）
	 * → DUE 待複審（下次 ≤ 今天＋30，含當天）→ NORMAL 正常。清單、首頁卡、J02、J04、鈴鐺、簽核交易內重讀共用，不建 DB 檢視
	 */
	public static final String REVIEW_STATE_EXPR = "CASE WHEN A.IS_REVIEW_FLAG = 1 AND A.ACCT_STATE_CODE <> 'DELETED' THEN 'ABNORMAL'\n"
			+ "            WHEN " + REVIEW_SCOPE_COND + " THEN\n"
			+ "                 CASE WHEN A.LAST_REVIEW_DATE IS NULL OR A.NEXT_REVIEW_DATE IS NULL OR A.NEXT_REVIEW_DATE < :today THEN 'OVERDUE'\n"
			+ "                      WHEN A.NEXT_REVIEW_DATE <= :today + 30 THEN 'DUE'\n"
			+ "                      ELSE 'NORMAL' END\n"
			+ "       END";

	/**
	 * 清單（R2）：帳號主表要顯示的欄位＋類別名、分類名、原申請人姓名、審查狀態，並帶進行中那一輪（R，同帳號最多一筆）的
	 * 兩半簽核人與時間（R3 簽核後前端用來顯示「Infra 已簽／治理未簽」）；姓名一律 LEFT JOIN 同步表 PX_SYNC_EMP
	 */
	static final String SELECT_BASE = """
			SELECT A.ACCT_ID, A.CATG_ID, C.CATG_NAME, A.ACCT_NAME, A.SYS_NAME, A.ASSET_CLASS_ID, AC.ASSET_CLASS_NAME, A.IS_PRIV,
			       A.HOLDER_NAME, A.HOLDER_DEPT, A.ACCESS_LEVEL_CODE, A.ACCT_STATE_CODE, A.APPLY_BY,
			""" + "       " + EmpSql.nameExpr("UA", "A.APPLY_BY") + " AS APPLY_BY_NAME,\n" + """
			       A.LAST_REVIEW_DATE, A.NEXT_REVIEW_DATE, A.IS_REVIEW_FLAG, A.REVIEW_FLAG_REASON, A.REVIEW_FLAG_SOURCE_CODE,
			       A.REVIEW_FLAG_DATE,
			""" + "       " + REVIEW_STATE_EXPR + " AS REVIEW_STATE_CODE,\n" + """
			       R.REVIEW_ID, R.VERSION_NO AS REVIEW_VERSION_NO, R.INFRA_SIGN_BY,
			""" + "       " + EmpSql.nameExpr("UI", "R.INFRA_SIGN_BY") + " AS INFRA_SIGN_BY_NAME,\n" + """
			       R.INFRA_SIGN_DATE, R.GOV_SIGN_BY,
			""" + "       " + EmpSql.nameExpr("UG", "R.GOV_SIGN_BY") + " AS GOV_SIGN_BY_NAME,\n" + """
			       R.GOV_SIGN_DATE
			  FROM IM_ACCT A
			  LEFT JOIN IM_ACCT_CATG C ON C.CATG_ID = A.CATG_ID
			""" + "  " + JOIN_ASSET_CLASS + "\n" + """
			  LEFT JOIN IM_ACCT_REVIEW R ON R.ACCT_ID = A.ACCT_ID AND R.REVIEW_STATE_CODE = 'OPEN' AND R.STATUS = 1
			""" + "  " + EmpSql.joinEmp("UA", "A.APPLY_BY") + "\n"
			+ "  " + EmpSql.joinEmp("UI", "R.INFRA_SIGN_BY") + "\n"
			+ "  " + EmpSql.joinEmp("UG", "R.GOV_SIGN_BY") + "\n";

	/** :stateCode 為空＝需要處理的三種（異常、逾期、待複審）；給 NORMAL 才看正常的 */
	public static final String FIND_LIST = """
			SELECT X.*
			  FROM (""" + SELECT_BASE + """
			       ) X
			 WHERE X.REVIEW_STATE_CODE IS NOT NULL
			   AND ((:stateCode IS NULL AND X.REVIEW_STATE_CODE <> 'NORMAL') OR X.REVIEW_STATE_CODE = :stateCode)
			   AND (:keyword IS NULL
			        OR UPPER(X.ACCT_ID) LIKE '%' || UPPER(:keyword) || '%' ESCAPE '\\'
			        OR UPPER(X.ACCT_NAME) LIKE '%' || UPPER(:keyword) || '%' ESCAPE '\\'
			        OR UPPER(X.HOLDER_NAME) LIKE '%' || UPPER(:keyword) || '%' ESCAPE '\\'
			        OR UPPER(X.SYS_NAME) LIKE '%' || UPPER(:keyword) || '%' ESCAPE '\\')
			 ORDER BY CASE X.REVIEW_STATE_CODE WHEN 'ABNORMAL' THEN 1 WHEN 'OVERDUE' THEN 2 WHEN 'DUE' THEN 3 ELSE 4 END,
			          X.NEXT_REVIEW_DATE NULLS FIRST, X.ACCT_ID
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public ReviewDao(DbClient dbClient, @Value("${itflow.db.alias}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	/** keyword 已跳脫、null＝不限；stateCode null＝異常＋逾期＋待複審；today＝今天（綁 00:00） */
	public List<ReviewItem> findList(String keyword, String stateCode, LocalDate today) {
		Map<String, Object> params = new HashMap<>();
		params.put("keyword", keyword);
		params.put("stateCode", stateCode);
		params.put("today", DaoSupport.ts(today));
		return dbClient.query(itflowDb, FIND_LIST, params, ReviewItem.class);
	}
}
