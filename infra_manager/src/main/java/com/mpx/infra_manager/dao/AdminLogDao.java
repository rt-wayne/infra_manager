package com.mpx.infra_manager.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：系統管理軌跡 IM_ACCT_ADMIN_LOG（spec v2.0 §2.5；只增不改）
//           第一階段只記 IMPERSONATE／IMPERSONATE_END（SUCCESS）與無權限切換（DENY）；LOG_ID 取 SEQ_IM_ACCT_ADMIN_LOG
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-14
// 變更說明: DB 別名 key 由 db.connect.itflow 改名為 itflow.db.alias（使用者改名）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 1：REASON 由寫死 NULL 改為參數（名單維護原因必填；切換身分仍傳 null）
// ============================================================

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;

@Repository
public class AdminLogDao {

	public static final String INSERT = """
			INSERT INTO IM_ACCT_ADMIN_LOG (LOG_ID, ACT_DATE, ACT_BY, ACT_BY_NAME, ACTION_CODE, TARGET_USER_ID, CONTENT_JSON,
			       REASON, RESULT_CODE, CREATE_DATE, CREATE_BY)
			VALUES (SEQ_IM_ACCT_ADMIN_LOG.NEXTVAL, :now, :actBy, :actByName, :actionCode, :targetUserId, :contentJson,
			       :reason, :resultCode, :now, :actBy)
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public AdminLogDao(DbClient dbClient, @Value("${itflow.db.alias}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	/** reason：名單維護時為操作人填的原因（可空，DENY 時有帶就存）；切換身分傳 null */
	public int insert(LocalDateTime now, String actBy, String actByName, String actionCode, String targetUserId,
			String contentJson, String reason, String resultCode) {
		Map<String, Object> params = new HashMap<>();
		params.put("now", DaoSupport.ts(now));
		params.put("actBy", actBy);
		params.put("actByName", actByName);
		params.put("actionCode", actionCode);
		params.put("targetUserId", targetUserId);
		params.put("contentJson", contentJson);
		params.put("reason", reason);
		params.put("resultCode", resultCode);
		return dbClient.update(itflowDb, INSERT, params);
	}
}
