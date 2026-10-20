package com.mpx.infra_manager.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：登入 token IM_ACCT_LOGIN_TOKEN 存取（spec v2.0 §2.1、§2.5）
//           只存 TOKEN_HASH（SHA-256），token 本身不入庫；查詢附實際使用者與代理身分的 STATUS（有效性與自動切回判斷用）；
//           登出寫 LOGOUT_DATE；切換身分／切回只改 ACT_AS_USER_ID；LAST_ACCESS_DATE 由 Service 節流後更新
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-14
// 變更說明: DB 別名 key 由 db.connect.itflow 改名為 itflow.db.alias（使用者改名）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: BACKLOG 第 124 項階段 3 回合 1（裁示 D0B、E1）：FIND 另以兩個 EXISTS 子查詢回 USER_IN_SYNC、ACT_AS_IN_SYNC
//           （實際使用者／代理身分在同步表 PX_SYNC_EMP 查得到、只認 STATUS＝1）；保留 JOIN IM_ACCT_USER 取 STATUS，不 INNER JOIN 同步表
//           （同步表查無與逾時要分得出來、代理身分查無時本人不跟著 401）
// ============================================================

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager.model.LoginToken;

@Repository
public class LoginTokenDao {

	public static final String INSERT = """
			INSERT INTO IM_ACCT_LOGIN_TOKEN (TOKEN_HASH, USER_ID, EXPIRE_DATE, LAST_ACCESS_DATE, CREATE_DATE, CREATE_BY)
			VALUES (:tokenHash, :userId, :expireDate, :now, :now, :userId)
			""";

	/** 每個請求查一次；USER_IN_SYNC＝0 時 token 無效（本人同步表查無），ACT_AS_IN_SYNC＝0 時自動切回（代理身分為空也是 0） */
	public static final String FIND = """
			SELECT T.TOKEN_HASH, T.USER_ID, T.ACT_AS_USER_ID, T.EXPIRE_DATE, T.LAST_ACCESS_DATE, T.LOGOUT_DATE,
			       U.STATUS AS USER_STATUS, AU.STATUS AS ACT_AS_STATUS,
			""" + "       CASE WHEN " + EmpSql.inSyncCond("T.USER_ID") + " THEN 1 ELSE 0 END AS USER_IN_SYNC,\n"
			+ "       CASE WHEN " + EmpSql.inSyncCond("T.ACT_AS_USER_ID") + " THEN 1 ELSE 0 END AS ACT_AS_IN_SYNC\n" + """
			  FROM IM_ACCT_LOGIN_TOKEN T
			  JOIN IM_ACCT_USER U ON U.USER_ID = T.USER_ID
			  LEFT JOIN IM_ACCT_USER AU ON AU.USER_ID = T.ACT_AS_USER_ID
			 WHERE T.TOKEN_HASH = :tokenHash
			   AND T.STATUS = 1
			""";

	public static final String UPDATE_TOUCH = """
			UPDATE IM_ACCT_LOGIN_TOKEN
			   SET LAST_ACCESS_DATE = :now
			 WHERE TOKEN_HASH = :tokenHash
			   AND LOGOUT_DATE IS NULL
			""";

	public static final String UPDATE_LOGOUT = """
			UPDATE IM_ACCT_LOGIN_TOKEN
			   SET LOGOUT_DATE = :now, UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE TOKEN_HASH = :tokenHash
			   AND LOGOUT_DATE IS NULL
			""";

	public static final String UPDATE_ACT_AS = """
			UPDATE IM_ACCT_LOGIN_TOKEN
			   SET ACT_AS_USER_ID = :actAsUserId, UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE TOKEN_HASH = :tokenHash
			   AND LOGOUT_DATE IS NULL
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public LoginTokenDao(DbClient dbClient, @Value("${itflow.db.alias}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	public int insert(String tokenHash, String userId, LocalDateTime expireDate, LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("tokenHash", tokenHash);
		params.put("userId", userId);
		params.put("expireDate", DaoSupport.ts(expireDate));
		params.put("now", DaoSupport.ts(now));
		return dbClient.update(itflowDb, INSERT, params);
	}

	public LoginToken find(String tokenHash) {
		Map<String, Object> params = new HashMap<>();
		params.put("tokenHash", tokenHash);
		return DaoSupport.first(dbClient.query(itflowDb, FIND, params, LoginToken.class));
	}

	public int touch(String tokenHash, LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("tokenHash", tokenHash);
		params.put("now", DaoSupport.ts(now));
		return dbClient.update(itflowDb, UPDATE_TOUCH, params);
	}

	public int logout(String tokenHash, String userId, LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("tokenHash", tokenHash);
		params.put("userId", userId);
		params.put("now", DaoSupport.ts(now));
		return dbClient.update(itflowDb, UPDATE_LOGOUT, params);
	}

	/** actAsUserId 為 null＝切回 */
	public int setActAs(String tokenHash, String actAsUserId, String userId, LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("tokenHash", tokenHash);
		params.put("actAsUserId", actAsUserId);
		params.put("userId", userId);
		params.put("now", DaoSupport.ts(now));
		return dbClient.update(itflowDb, UPDATE_ACT_AS, params);
	}
}
