package com.mpx.infra_manager.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-10
// 變更說明: 新增：使用者 IM_ACCT_USER 存取（spec v2.0 §2.1、§6.2；取代既有模組使用者表的引用）
//           登入失敗計數與鎖定合成單句 UPDATE（達上限時同句寫 LOCK_DATE；已鎖定者不再累加）；
//           登入成功歸 0 並寫 LAST_LOGIN_DATE；切換身分名單＝STATUS＝1、不含自己，附啟用中的角色。
//           單句更新不需交易（spec §5 末段）；時間一律綁定 :now
//           修改：登入成功的 UPDATE 加 LOCK_DATE IS NULL，查詢後到更新前被鎖定者影響 0 筆，由呼叫端回 A02
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-14
// 變更說明: DB 別名 key 由 db.connect.itflow 改名為 itflow.db.alias（使用者改名）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: BACKLOG 第 124 項階段 3 回合 1（裁示 ⑦B、D2A、E1、E2）：FIND_BY_ID 的姓名與 Email 改讀同步表 PX_SYNC_EMP（LEFT JOIN、只認 STATUS＝1；
//           姓名查無退回工號），另回 IN_SYNC 旗標；不再讀 IM_ACCT_USER 的 USER_NAME、USER_EN_NAME、EMAIL（上線時要刪的欄）。
//           密碼、鎖定欄仍讀（階段 7 拿掉）、DEPT_NAME／TEL_NO 仍讀（階段 5、6 改）。FIND_ACTIVE_WITH_ROLES 只先拿掉 USER_EN_NAME（回合 2 改 E3、E4）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: BACKLOG 第 124 項階段 3 回合 2（裁示 E3、E4）：FIND_ACTIVE_WITH_ROLES 姓名改讀同步表，只列「本系統啟用且同步表查得到」的使用者
// ============================================================

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.mpx.common.db.DbClient;
import com.mpx.infra_manager.model.AcctUser;
import com.mpx.infra_manager.model.UserRoleRow;

@Repository
public class UserDao {

	/** 單一使用者：本系統列＋同步表姓名／Email；IN_SYNC＝同步表查得到（LEFT JOIN 已帶 STATUS＝1 條件，E 有列即查得到） */
	public static final String FIND_BY_ID = """
			SELECT U.USER_ID, U.DEPT_NAME, U.TEL_NO, U.PWD_HASH, U.LOGIN_FAIL_CNT, U.LOCK_DATE, U.LAST_LOGIN_DATE, U.STATUS,
			       E.EMAIL, CASE WHEN E.EMP_ID IS NULL THEN 0 ELSE 1 END AS IN_SYNC,
			""" + "       " + EmpSql.nameExpr("E", "U.USER_ID") + " AS USER_NAME\n" + """
			  FROM IM_ACCT_USER U
			""" + "  " + EmpSql.joinEmp("E", "U.USER_ID") + "\n" + """
			 WHERE U.USER_ID = :userId
			""";

	public static final String UPDATE_LOGIN_FAIL = """
			UPDATE IM_ACCT_USER
			   SET LOGIN_FAIL_CNT = LOGIN_FAIL_CNT + 1,
			       LOCK_DATE = CASE WHEN LOGIN_FAIL_CNT + 1 >= :maxFail THEN :now ELSE LOCK_DATE END,
			       UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE USER_ID = :userId
			   AND LOCK_DATE IS NULL
			""";

	public static final String UPDATE_LOGIN_OK = """
			UPDATE IM_ACCT_USER
			   SET LOGIN_FAIL_CNT = 0, LAST_LOGIN_DATE = :now, UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE USER_ID = :userId
			   AND LOCK_DATE IS NULL
			""";

	/** 切換身分下拉：有效使用者（E3：本系統啟用且同步表查得到；E4：不看最後登入）＋啟用中角色，姓名讀同步表 */
	public static final String FIND_ACTIVE_WITH_ROLES = """
			SELECT U.USER_ID, RU.ROLE_ID,
			""" + "       " + EmpSql.nameExpr("E", "U.USER_ID") + " AS USER_NAME\n" + """
			  FROM IM_ACCT_USER U
			""" + "  " + EmpSql.joinEmp("E", "U.USER_ID") + "\n" + """
			  LEFT JOIN IM_ACCT_ROLE_USER RU
			    ON RU.USER_ID = U.USER_ID
			   AND RU.STATUS = 1
			   AND EXISTS (SELECT 1 FROM IM_ACCT_ROLE R WHERE R.ROLE_ID = RU.ROLE_ID AND R.STATUS = 1)
			""" + " WHERE " + EmpSql.activeUserCond("U") + "\n" + """
			   AND U.USER_ID <> :excludeUserId
			 ORDER BY U.USER_ID, RU.ROLE_ID
			""";

	private final DbClient dbClient;
	private final String itflowDb;

	public UserDao(DbClient dbClient, @Value("${itflow.db.alias}") String itflowDb) {
		this.dbClient = dbClient;
		this.itflowDb = itflowDb;
	}

	/** 查無回 null（不論 STATUS） */
	public AcctUser findById(String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("userId", userId);
		return DaoSupport.first(dbClient.query(itflowDb, FIND_BY_ID, params, AcctUser.class));
	}

	public int loginFail(String userId, int maxFail, LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("userId", userId);
		params.put("maxFail", maxFail);
		params.put("now", DaoSupport.ts(now));
		return dbClient.update(itflowDb, UPDATE_LOGIN_FAIL, params);
	}

	public int loginOk(String userId, LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("userId", userId);
		params.put("now", DaoSupport.ts(now));
		return dbClient.update(itflowDb, UPDATE_LOGIN_OK, params);
	}

	public List<UserRoleRow> findActiveWithRoles(String excludeUserId) {
		Map<String, Object> params = new HashMap<>();
		params.put("excludeUserId", excludeUserId);
		return dbClient.query(itflowDb, FIND_ACTIVE_WITH_ROLES, params, UserRoleRow.class);
	}
}
