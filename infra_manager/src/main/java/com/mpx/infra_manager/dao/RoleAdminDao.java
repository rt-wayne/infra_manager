package com.mpx.infra_manager.dao;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：角色指派與執行人對照維護的資料存取（BACKLOG 第 89 項階段 1；SA 4.7、裁示 ①B／⑦A）
//           全域互斥：LOCK_GLOBAL 對 IM_ACCT_ROLE 的 GENERAL 列 FOR UPDATE WAIT n（一定存在、IS_SYSTEM＝1、程式從不更新、列鎖不擋一般讀取），
//           五支寫入 API 交易第一句取鎖後才做檢查——READ COMMITTED 下帶條件 UPDATE 擋不住 write skew（兩位管理員同時移除最後兩人會歸零）；
//           等待逾時 ORA-30006 由 Spring 轉 PessimisticLockingFailureException 家族（不保證是子類別），Service 攔父類別轉 E26。
//           持有人數 COUNT_HOLDERS：與 PermDao.FIND_USERS_WITH_PERM 同口徑（啟用中使用者、啟用中角色權限列、啟用中成員指派、含 GENERAL），
//           可排除「某角色＋某人」那一組成員指派（移除成員用）或「某角色＋某碼」那一格角色權限（第三頁存檔用）；
//           排除參數以 NVL(:x, '-') 哨兵處理 null——NOT (A AND NULL) 會是 NULL 而把整列排掉，不能直接綁 NULL。
//           在途案件：申請單依 IM_ACCT_APPR_STAGE.PERM_ID 資料驅動（REVIEW 關卡 2～5、VERIFY 關卡 7；關卡 6 PERM_ID 為空不會比中）；
//           緊急存取（EMG_DECIDE）＝未處理（含逾期）＋已退回未結案，沿用 EmgDao.UNDECIDED_COND／RETURNED_COND。
//           移除＝UPDATE STATUS 1→0；重新指派＝UPDATE 0→1（寫 UPDATE_*、保留 CREATE_*），影響 0 筆才 INSERT
// ============================================================

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.model.AcctUser;
import com.mpx.infra_manager.model.CodeRow;
import com.mpx.infra_manager.model.CountRow;
import com.mpx.infra_manager.model.InFlightRow;
import com.mpx.infra_manager.model.MemberRow;
import com.mpx.infra_manager.model.RolePermRow;
import com.mpx.infra_manager.model.RoleRow;
import com.mpx.infra_manager.model.StatusRow;

@Repository
public class RoleAdminDao {

	/** 全域互斥鎖：鎖 GENERAL 列，等待秒數為程式常數（Oracle 的 WAIT n 不能綁參數） */
	public static final String LOCK_GLOBAL = """
			SELECT ROLE_ID AS CODE
			  FROM IM_ACCT_ROLE
			 WHERE ROLE_ID = :generalRoleId
			   FOR UPDATE WAIT """ + " " + AcctConst.ADMIN_LOCK_WAIT_SECONDS + "\n";

	static final String ROLE_SELECT = """
			SELECT ROLE_ID, ROLE_NAME, ROLE_DESCR, IS_SYSTEM, SORT_SEQ, STATUS
			  FROM IM_ACCT_ROLE
			""";

	/** 可維護的角色：啟用中、不含 GENERAL（所有啟用中使用者自動擁有，不在畫面維護） */
	public static final String FIND_ROLES = ROLE_SELECT + """
			 WHERE STATUS = 1
			   AND ROLE_ID <> :generalRoleId
			 ORDER BY SORT_SEQ, ROLE_ID
			""";

	/** 單一角色（含停用，由 Service 判斷 400） */
	public static final String FIND_ROLE_BY_ID = ROLE_SELECT + """
			 WHERE ROLE_ID = :roleId
			""";

	/** 所有啟用中的角色權限列（角色與權限碼都啟用中），帶中文名；供角色清單顯示授予的權限 */
	public static final String FIND_ROLE_PERMS = """
			SELECT RP.ROLE_ID, P.PERM_ID, P.PERM_NAME, P.MODULE_CODE, P.SORT_SEQ
			  FROM
			""" + PermDao.ACTIVE_ROLE_PERM + """
			 WHERE RP.STATUS = 1
			 ORDER BY RP.ROLE_ID, P.SORT_SEQ, P.PERM_ID
			""";

	/** 某角色授予的權限碼（啟用中）；移除成員時用來算失去哪些碼 */
	public static final String FIND_PERM_IDS_OF_ROLE = """
			SELECT RP.PERM_ID AS CODE
			  FROM
			""" + PermDao.ACTIVE_ROLE_PERM + """
			 WHERE RP.STATUS = 1
			   AND RP.ROLE_ID = :roleId
			 ORDER BY P.SORT_SEQ, P.PERM_ID
			""";

	/** 啟用中的角色成員（全部角色一次查）；對象停用仍列出（USER_STATUS＝0），使用者表查無列時 USER_STATUS 為空 */
	public static final String FIND_ROLE_MEMBERS = """
			SELECT RU.ROLE_ID AS GROUP_ID, RU.USER_ID, U.USER_EN_NAME, U.USER_NAME, U.STATUS AS USER_STATUS,
			       NVL(RU.UPDATE_DATE, RU.CREATE_DATE) AS GRANT_DATE, NVL(RU.UPDATE_BY, RU.CREATE_BY) AS GRANT_BY
			  FROM IM_ACCT_ROLE_USER RU
			  LEFT JOIN IM_ACCT_USER U ON U.USER_ID = RU.USER_ID
			 WHERE RU.STATUS = 1
			 ORDER BY RU.ROLE_ID, RU.USER_ID
			""";

	/** 指派候選人：啟用中使用者、含自己（既有 /users 需 SYS_IMPERSONATE 且排除自己，不沿用） */
	public static final String FIND_CANDIDATES = """
			SELECT USER_ID, USER_EN_NAME, USER_NAME, DEPT_NAME, STATUS
			  FROM IM_ACCT_USER
			 WHERE STATUS = 1
			 ORDER BY USER_ID
			""";

	/** 某組角色指派的現況：查無列、0、1 */
	public static final String FIND_ROLE_MEMBER_STATUS = """
			SELECT STATUS
			  FROM IM_ACCT_ROLE_USER
			 WHERE ROLE_ID = :roleId
			   AND USER_ID = :targetId
			""";

	/** 指派（0→1）或移除（1→0）都走這句；帶 oldStatus 條件，現況不符影響 0 筆 */
	public static final String UPDATE_ROLE_MEMBER_STATUS = """
			UPDATE IM_ACCT_ROLE_USER
			   SET STATUS = :newStatus, UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE ROLE_ID = :roleId
			   AND USER_ID = :targetId
			   AND STATUS = :oldStatus
			""";

	public static final String INSERT_ROLE_MEMBER = """
			INSERT INTO IM_ACCT_ROLE_USER (ROLE_ID, USER_ID, STATUS, CREATE_DATE, CREATE_BY)
			VALUES (:roleId, :targetId, 1, :now, :userId)
			""";

	/**
	 * 持有權限 :permId 的啟用中使用者數（與 PermDao.FIND_USERS_WITH_PERM 同口徑），可排除一組授予：
	 * 成員指派（:exclRoleId＋:exclUserId）或角色權限列（:exclRoleId＋:exclPermId）；不排除的參數傳 null（NVL 成哨兵 '-'）
	 */
	public static final String COUNT_HOLDERS = """
			SELECT COUNT(*) AS CNT
			  FROM IM_ACCT_USER U
			 WHERE U.STATUS = 1
			   AND EXISTS (SELECT 1 FROM
			""" + PermDao.ACTIVE_ROLE_PERM + """
			                WHERE RP.STATUS = 1
			                  AND RP.PERM_ID = :permId
			                  AND NOT (RP.ROLE_ID = NVL(:exclRoleId, '-') AND RP.PERM_ID = NVL(:exclPermId, '-'))
			                  AND (RP.ROLE_ID = :generalRoleId
			                       OR EXISTS (SELECT 1 FROM IM_ACCT_ROLE_USER RU
			                                   WHERE RU.ROLE_ID = RP.ROLE_ID AND RU.USER_ID = U.USER_ID AND RU.STATUS = 1
			                                     AND NOT (RU.ROLE_ID = NVL(:exclRoleId, '-') AND RU.USER_ID = NVL(:exclUserId, '-')))))
			""";

	/** 停在需要權限 :permId 那一關的在途申請單（關卡權限資料驅動；關卡 6 的 PERM_ID 為空不會比中） */
	public static final String FIND_IN_FLIGHT_BY_STAGE_PERM = """
			SELECT R.REQ_ID AS DOC_ID, S.STAGE_NAME
			  FROM IM_ACCT_REQ R
			  JOIN IM_ACCT_APPR_STAGE S ON S.STAGE_ID = R.CURR_STAGE_ID
			 WHERE R.REQ_STATE_CODE IN ('REVIEW', 'VERIFY')
			   AND S.PERM_ID = :permId
			 ORDER BY R.SUBMIT_DATE, R.REQ_ID
			""";

	/** 需要 EMG_DECIDE 處理的在途緊急存取：未處理（含逾期）、已退回未結案（裁示 ③A） */
	public static final String FIND_IN_FLIGHT_EMG = """
			SELECT E.EMG_ID AS DOC_ID,
			       CASE WHEN E.DECIDE_CODE IS NULL THEN '待補核准' ELSE '已退回待結案' END AS STAGE_NAME
			  FROM IM_EMG_ACCESS E
			 WHERE (""" + EmgDao.UNDECIDED_COND + """
			)
			    OR (""" + EmgDao.RETURNED_COND + """
			)
			 ORDER BY E.SUBMIT_DATE, E.EMG_ID
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public RoleAdminDao(DbClient dbClient, @Value("${itflow.db.alias}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	/** 取全域鎖；須在交易內呼叫，等待逾時（ORA-30006）丟 PessimisticLockingFailureException 家族 */
	public void lockGlobal() {
		Map<String, Object> params = new HashMap<>();
		params.put("generalRoleId", AcctConst.ROLE_GENERAL);
		dbClient.query(itflowDb, LOCK_GLOBAL, params, CodeRow.class);
	}

	public List<RoleRow> findRoles() {
		Map<String, Object> params = new HashMap<>();
		params.put("generalRoleId", AcctConst.ROLE_GENERAL);
		return dbClient.query(itflowDb, FIND_ROLES, params, RoleRow.class);
	}

	/** 查無回 null */
	public RoleRow findRoleById(String roleId) {
		Map<String, Object> params = new HashMap<>();
		params.put("roleId", roleId);
		return DaoSupport.first(dbClient.query(itflowDb, FIND_ROLE_BY_ID, params, RoleRow.class));
	}

	public List<RolePermRow> findRolePerms() {
		return dbClient.query(itflowDb, FIND_ROLE_PERMS, null, RolePermRow.class);
	}

	public List<String> findPermIdsOfRole(String roleId) {
		Map<String, Object> params = new HashMap<>();
		params.put("roleId", roleId);
		return dbClient.query(itflowDb, FIND_PERM_IDS_OF_ROLE, params, CodeRow.class).stream().map(CodeRow::getCode).toList();
	}

	public List<MemberRow> findRoleMembers() {
		return dbClient.query(itflowDb, FIND_ROLE_MEMBERS, null, MemberRow.class);
	}

	public List<AcctUser> findCandidates() {
		return dbClient.query(itflowDb, FIND_CANDIDATES, null, AcctUser.class);
	}

	/** 某組角色指派的 STATUS；查無列回 null */
	public Integer findRoleMemberStatus(String roleId, String targetId) {
		Map<String, Object> params = new HashMap<>();
		params.put("roleId", roleId);
		params.put("targetId", targetId);
		StatusRow row = DaoSupport.first(dbClient.query(itflowDb, FIND_ROLE_MEMBER_STATUS, params, StatusRow.class));
		return row == null ? null : row.getStatus();
	}

	/** 回影響筆數（現況不是 oldStatus 時 0） */
	public int updateRoleMemberStatus(String roleId, String targetId, int oldStatus, int newStatus, LocalDateTime now,
			String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("roleId", roleId);
		params.put("targetId", targetId);
		params.put("oldStatus", oldStatus);
		params.put("newStatus", newStatus);
		params.put("now", DaoSupport.ts(now));
		params.put("userId", userId);
		return dbClient.update(itflowDb, UPDATE_ROLE_MEMBER_STATUS, params);
	}

	public int insertRoleMember(String roleId, String targetId, LocalDateTime now, String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("roleId", roleId);
		params.put("targetId", targetId);
		params.put("now", DaoSupport.ts(now));
		params.put("userId", userId);
		return dbClient.update(itflowDb, INSERT_ROLE_MEMBER, params);
	}

	/**
	 * 持有 permId 的啟用中使用者數。排除一組授予：成員指派傳 (exclRoleId, exclUserId, null)、角色權限列傳 (exclRoleId, null, exclPermId)；
	 * 都傳 null＝不排除（變更前的人數）
	 */
	public long countHolders(String permId, String exclRoleId, String exclUserId, String exclPermId) {
		Map<String, Object> params = new HashMap<>();
		params.put("permId", permId);
		params.put("generalRoleId", AcctConst.ROLE_GENERAL);
		params.put("exclRoleId", exclRoleId);
		params.put("exclUserId", exclUserId);
		params.put("exclPermId", exclPermId);
		return DaoSupport.count(dbClient.query(itflowDb, COUNT_HOLDERS, params, CountRow.class));
	}

	public List<InFlightRow> findInFlightByStagePerm(String permId) {
		Map<String, Object> params = new HashMap<>();
		params.put("permId", permId);
		return dbClient.query(itflowDb, FIND_IN_FLIGHT_BY_STAGE_PERM, params, InFlightRow.class);
	}

	public List<InFlightRow> findInFlightEmg() {
		return dbClient.query(itflowDb, FIND_IN_FLIGHT_EMG, null, InFlightRow.class);
	}
}
