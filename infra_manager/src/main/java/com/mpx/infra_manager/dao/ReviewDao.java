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
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: BACKLOG 第 158 項 R3a（簽核與完成）：清單多回兩半的實際操作人（⑪A 同人判斷用）；單筆 FIND_BY_ACCT（簽核交易內重讀狀態）；
//           序列取號 NEXT_REVIEW_ID；首簽建本輪 INSERT_OPEN（⑮A；撞 IND_IM_ACCT_REVIEW_02 由 Service 重讀改 UPDATE）；
//           簽半 UPDATE_SIGN_INFRA／GOV（條件：版本相符、進行中、本半尚未簽；VERSION_NO＋1，0 筆＝409）；
//           雙簽完成 UPDATE_DONE（條件：版本相符、進行中、兩半都已簽）。所有時間綁參數
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: BACKLOG 第 158 項 R3b（駁回）：欄位清單抽成 SELECT_COLS 讓清單（接進行中一輪）與單輪查詢 FIND_ROUND（以 REVIEW_ID 接、
//           不限狀態、多帶該輪結果與駁回五欄，駁回信提交後重讀用）共用；首次動作就是駁回 INSERT_REJECTED（直接 REJECTED、不佔 IND_02，
//           靠 Service 先鎖帳號列避免與同時的首簽各建一輪）；已有一輪 UPDATE_REJECT（版本相符、進行中、被駁回的那一半尚未簽，裁示 ①A）
// ============================================================

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.model.CountRow;
import com.mpx.infra_manager.model.ReviewItem;
import com.mpx.infra_manager.model.ReviewRound;

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
	 * 共用欄位（R2／R3b）：帳號主表要顯示的欄位＋類別名、分類名、原申請人姓名、審查狀態，加某一輪（R）的流水號、版本、
	 * 兩半簽核人與時間（前端顯示「Infra 已簽／治理未簽」）；姓名一律 LEFT JOIN 同步表 PX_SYNC_EMP。
	 * R 接哪一輪由 SELECT_BASE（進行中那一輪）或 FIND_ROUND（指定 REVIEW_ID）決定
	 */
	static final String SELECT_COLS = """
			SELECT A.ACCT_ID, A.CATG_ID, C.CATG_NAME, A.ACCT_NAME, A.SYS_NAME, A.ASSET_CLASS_ID, AC.ASSET_CLASS_NAME, A.IS_PRIV,
			       A.HOLDER_NAME, A.HOLDER_DEPT, A.ACCESS_LEVEL_CODE, A.ACCT_STATE_CODE, A.APPLY_BY,
			""" + "       " + EmpSql.nameExpr("UA", "A.APPLY_BY") + " AS APPLY_BY_NAME,\n" + """
			       A.LAST_REVIEW_DATE, A.NEXT_REVIEW_DATE, A.IS_REVIEW_FLAG, A.REVIEW_FLAG_REASON, A.REVIEW_FLAG_SOURCE_CODE,
			       A.REVIEW_FLAG_DATE,
			""" + "       " + REVIEW_STATE_EXPR + " AS REVIEW_STATE_CODE,\n" + """
			       R.REVIEW_ID, R.VERSION_NO AS REVIEW_VERSION_NO, R.INFRA_SIGN_BY,
			""" + "       " + EmpSql.nameExpr("UI", "R.INFRA_SIGN_BY") + " AS INFRA_SIGN_BY_NAME,\n" + """
			       R.INFRA_SIGN_ACT_BY, R.INFRA_SIGN_DATE, R.GOV_SIGN_BY,
			""" + "       " + EmpSql.nameExpr("UG", "R.GOV_SIGN_BY") + " AS GOV_SIGN_BY_NAME,\n" + """
			       R.GOV_SIGN_ACT_BY, R.GOV_SIGN_DATE""";

	/** 帳號主表起手的 FROM／JOIN（不含 R）：類別、啟用中分類、三組姓名 */
	private static final String FROM_ACCT = """
			  FROM IM_ACCT A
			  LEFT JOIN IM_ACCT_CATG C ON C.CATG_ID = A.CATG_ID
			""" + "  " + JOIN_ASSET_CLASS + "\n";

	private static final String JOIN_NAMES = "  " + EmpSql.joinEmp("UA", "A.APPLY_BY") + "\n"
			+ "  " + EmpSql.joinEmp("UI", "R.INFRA_SIGN_BY") + "\n"
			+ "  " + EmpSql.joinEmp("UG", "R.GOV_SIGN_BY") + "\n";

	/** 清單與簽核重讀：R＝進行中那一輪（同帳號最多一筆，沒有就 NULL） */
	static final String SELECT_BASE = SELECT_COLS + "\n" + FROM_ACCT
			+ "  LEFT JOIN IM_ACCT_REVIEW R ON R.ACCT_ID = A.ACCT_ID AND R.REVIEW_STATE_CODE = 'OPEN' AND R.STATUS = 1\n"
			+ JOIN_NAMES;

	/**
	 * 某一輪（R3b；駁回信提交後重讀）：以 REVIEW_ID 接、不限該輪狀態，多帶該輪結果 ROUND_STATE_CODE 與駁回五欄（駁回人姓名 UR）。
	 * 仍需綁 :today（帳號的審查狀態欄用）
	 */
	public static final String FIND_ROUND = SELECT_COLS + ",\n"
			+ "       R.REVIEW_STATE_CODE AS ROUND_STATE_CODE, R.REJECT_HALF_CODE, R.REJECT_BY,\n"
			+ "       " + EmpSql.nameExpr("UR", "R.REJECT_BY") + " AS REJECT_BY_NAME,\n"
			+ "       R.REJECT_ACT_BY, R.REJECT_DATE, R.REJECT_REASON\n" + FROM_ACCT
			+ "  JOIN IM_ACCT_REVIEW R ON R.ACCT_ID = A.ACCT_ID AND R.REVIEW_ID = :reviewId AND R.STATUS = 1\n"
			+ JOIN_NAMES + "  " + EmpSql.joinEmp("UR", "R.REJECT_BY") + "\n";

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

	/** 單筆（簽核交易內重讀狀態用）：不過濾狀態，不在範圍時 REVIEW_STATE_CODE 為 NULL，由 Service 判斷 */
	public static final String FIND_BY_ACCT = SELECT_BASE + """
			 WHERE A.ACCT_ID = :acctId
			""";

	public static final String NEXT_REVIEW_ID = "SELECT SEQ_IM_ACCT_REVIEW.NEXTVAL AS CNT FROM DUAL";

	/**
	 * 首簽建本輪（⑮A）：兩組簽核欄只有簽的那一半有值、另一半 NULL；VERSION_NO＝0、STATUS＝1；
	 * 同帳號已有進行中一輪時由 IND_IM_ACCT_REVIEW_02 丟 DuplicateKeyException（Service 重讀後改走 UPDATE）
	 */
	public static final String INSERT_OPEN = """
			INSERT INTO IM_ACCT_REVIEW (REVIEW_ID, ACCT_ID, DUE_DATE, REVIEW_STATE_CODE,
			       INFRA_SIGN_BY, INFRA_SIGN_ACT_BY, INFRA_SIGN_DATE, GOV_SIGN_BY, GOV_SIGN_ACT_BY, GOV_SIGN_DATE,
			       VERSION_NO, STATUS, CREATE_DATE, CREATE_BY)
			VALUES (:reviewId, :acctId, :dueDate, 'OPEN',
			       :infraSignBy, :infraSignActBy, :infraSignDate, :govSignBy, :govSignActBy, :govSignDate,
			       0, 1, :now, :createBy)
			""";

	/** 簽 Infra 半：版本相符、進行中、本半尚未簽才更新；VERSION_NO＋1（樂觀鎖，0 筆＝409）。signBy 有效身分、actBy 實際操作人 */
	public static final String UPDATE_SIGN_INFRA = """
			UPDATE IM_ACCT_REVIEW
			   SET INFRA_SIGN_BY = :signBy, INFRA_SIGN_ACT_BY = :actBy, INFRA_SIGN_DATE = :now,
			       VERSION_NO = VERSION_NO + 1, UPDATE_DATE = :now, UPDATE_BY = :actBy
			 WHERE REVIEW_ID = :reviewId
			   AND VERSION_NO = :versionNo
			   AND REVIEW_STATE_CODE = 'OPEN'
			   AND INFRA_SIGN_BY IS NULL
			""";

	/** 簽治理半：同 UPDATE_SIGN_INFRA */
	public static final String UPDATE_SIGN_GOV = """
			UPDATE IM_ACCT_REVIEW
			   SET GOV_SIGN_BY = :signBy, GOV_SIGN_ACT_BY = :actBy, GOV_SIGN_DATE = :now,
			       VERSION_NO = VERSION_NO + 1, UPDATE_DATE = :now, UPDATE_BY = :actBy
			 WHERE REVIEW_ID = :reviewId
			   AND VERSION_NO = :versionNo
			   AND REVIEW_STATE_CODE = 'OPEN'
			   AND GOV_SIGN_BY IS NULL
			""";

	/** 雙簽完成：改 DONE、記完成時間與寫進主表的新下次應審查日；條件兩半都已簽（第二半剛簽完、版本已＋1，帶更新後的版本） */
	public static final String UPDATE_DONE = """
			UPDATE IM_ACCT_REVIEW
			   SET REVIEW_STATE_CODE = 'DONE', DONE_DATE = :now, NEXT_DUE_DATE = :nextDueDate,
			       VERSION_NO = VERSION_NO + 1, UPDATE_DATE = :now, UPDATE_BY = :actBy
			 WHERE REVIEW_ID = :reviewId
			   AND VERSION_NO = :versionNo
			   AND REVIEW_STATE_CODE = 'OPEN'
			   AND INFRA_SIGN_BY IS NOT NULL
			   AND GOV_SIGN_BY IS NOT NULL
			""";

	/**
	 * 首次動作就是駁回（R3b）：直接建一列 REJECTED，兩半簽核欄皆空、駁回五欄都有值；VERSION_NO＝0。
	 * 不是 OPEN 所以不受 IND_IM_ACCT_REVIEW_02 約束——與同時的首簽互斥靠 Service 先鎖帳號列（R3a 裁示 ①A）
	 */
	public static final String INSERT_REJECTED = """
			INSERT INTO IM_ACCT_REVIEW (REVIEW_ID, ACCT_ID, DUE_DATE, REVIEW_STATE_CODE,
			       REJECT_HALF_CODE, REJECT_BY, REJECT_ACT_BY, REJECT_DATE, REJECT_REASON,
			       VERSION_NO, STATUS, CREATE_DATE, CREATE_BY)
			VALUES (:reviewId, :acctId, :dueDate, 'REJECTED',
			       :half, :rejectBy, :actBy, :now, :reason,
			       0, 1, :now, :actBy)
			""";

	/**
	 * 駁回進行中的一輪（R3b；裁示 ③A 整輪結束、已簽那一半留存）：版本相符、進行中、且被駁回的那一半尚未有人簽（裁示 ①A：
	 * 簽了就是簽了，同半的人不能再推翻，0 筆＝409）；VERSION_NO＋1
	 */
	public static final String UPDATE_REJECT = """
			UPDATE IM_ACCT_REVIEW
			   SET REVIEW_STATE_CODE = 'REJECTED', REJECT_HALF_CODE = :half, REJECT_BY = :rejectBy, REJECT_ACT_BY = :actBy,
			       REJECT_DATE = :now, REJECT_REASON = :reason,
			       VERSION_NO = VERSION_NO + 1, UPDATE_DATE = :now, UPDATE_BY = :actBy
			 WHERE REVIEW_ID = :reviewId
			   AND VERSION_NO = :versionNo
			   AND REVIEW_STATE_CODE = 'OPEN'
			   AND ((:half = 'INFRA' AND INFRA_SIGN_BY IS NULL) OR (:half = 'GOV' AND GOV_SIGN_BY IS NULL))
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

	/** 查無回 null；reviewStateCode 為 null＝不在審查範圍 */
	public ReviewItem findByAcct(String acctId, LocalDate today) {
		Map<String, Object> params = new HashMap<>();
		params.put("acctId", acctId);
		params.put("today", DaoSupport.ts(today));
		return DaoSupport.first(dbClient.query(itflowDb, FIND_BY_ACCT, params, ReviewItem.class));
	}

	/** 某一輪（不限狀態）；查無回 null */
	public ReviewRound findRound(long reviewId, LocalDate today) {
		Map<String, Object> params = new HashMap<>();
		params.put("reviewId", reviewId);
		params.put("today", DaoSupport.ts(today));
		return DaoSupport.first(dbClient.query(itflowDb, FIND_ROUND, params, ReviewRound.class));
	}

	public long nextReviewId() {
		return DaoSupport.count(dbClient.query(itflowDb, NEXT_REVIEW_ID, new HashMap<>(), CountRow.class));
	}

	/** 首簽建本輪；half＝INFRA／GOV 決定哪一組簽核欄有值；signBy 有效身分、actBy 實際操作人（同時是 CREATE_BY） */
	public int insertOpen(long reviewId, String acctId, LocalDate dueDate, String half, String signBy, String actBy,
			LocalDateTime now) {
		boolean infra = AcctConst.HALF_INFRA.equals(half);
		Map<String, Object> params = new HashMap<>();
		params.put("reviewId", reviewId);
		params.put("acctId", acctId);
		params.put("dueDate", DaoSupport.ts(dueDate));
		params.put("infraSignBy", infra ? signBy : null);
		params.put("infraSignActBy", infra ? actBy : null);
		params.put("infraSignDate", infra ? DaoSupport.ts(now) : null);
		params.put("govSignBy", infra ? null : signBy);
		params.put("govSignActBy", infra ? null : actBy);
		params.put("govSignDate", infra ? null : DaoSupport.ts(now));
		params.put("now", DaoSupport.ts(now));
		params.put("createBy", actBy);
		return dbClient.update(itflowDb, INSERT_OPEN, params);
	}

	/** 簽一半；版本不符、已結束或本半已簽回 0 */
	public int signHalf(long reviewId, int versionNo, String half, String signBy, String actBy, LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("reviewId", reviewId);
		params.put("versionNo", versionNo);
		params.put("signBy", signBy);
		params.put("actBy", actBy);
		params.put("now", DaoSupport.ts(now));
		return dbClient.update(itflowDb, AcctConst.HALF_INFRA.equals(half) ? UPDATE_SIGN_INFRA : UPDATE_SIGN_GOV, params);
	}

	/** 首次動作就是駁回：建 REJECTED 列；rejectBy 有效身分、actBy 實際操作人（同時是 CREATE_BY）；reason 已由 Service 檢查長度 */
	public int insertRejected(long reviewId, String acctId, LocalDate dueDate, String half, String rejectBy, String actBy,
			String reason, LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("reviewId", reviewId);
		params.put("acctId", acctId);
		params.put("dueDate", DaoSupport.ts(dueDate));
		params.put("half", half);
		params.put("rejectBy", rejectBy);
		params.put("actBy", actBy);
		params.put("reason", reason);
		params.put("now", DaoSupport.ts(now));
		return dbClient.update(itflowDb, INSERT_REJECTED, params);
	}

	/** 駁回進行中的一輪；版本不符、已結束或被駁回的那一半已有人簽回 0 */
	public int rejectRound(long reviewId, int versionNo, String half, String rejectBy, String actBy, String reason,
			LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("reviewId", reviewId);
		params.put("versionNo", versionNo);
		params.put("half", half);
		params.put("rejectBy", rejectBy);
		params.put("actBy", actBy);
		params.put("reason", reason);
		params.put("now", DaoSupport.ts(now));
		return dbClient.update(itflowDb, UPDATE_REJECT, params);
	}

	/** 雙簽完成；versionNo＝簽第二半之後的版本；兩半未齊或版本不符回 0 */
	public int markDone(long reviewId, int versionNo, LocalDate nextDueDate, LocalDateTime now, String actBy) {
		Map<String, Object> params = new HashMap<>();
		params.put("reviewId", reviewId);
		params.put("versionNo", versionNo);
		params.put("nextDueDate", DaoSupport.ts(nextDueDate));
		params.put("now", DaoSupport.ts(now));
		params.put("actBy", actBy);
		return dbClient.update(itflowDb, UPDATE_DONE, params);
	}
}
