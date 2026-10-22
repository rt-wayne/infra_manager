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
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 5 回合 2（裁示 ⑪）：FIND_BY_ID 不再讀 DEPT_NAME；新增 FIND_PRIMARY_DEPT（同步表主要部門，只給 /me 用）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 6（裁示 ⑫A）：FIND_BY_ID 不再讀 TEL_NO（申請人分機改自填）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 7 回合 1（登入改走 WPX；裁示 ①B、②A、③B、D3A、D9B）：FIND_BY_ID 拿掉 PWD_HASH、LOGIN_FAIL_CNT、LOCK_DATE；
//           刪 UPDATE_LOGIN_FAIL（鎖定交給 WPX）；UPDATE_LOGIN_OK 只寫最後登入時間（不再歸 0 失敗次數、不看鎖定）；
//           新增 MERGE_FIRST_LOGIN：本系統沒有帳號列、且符合資格（同步表查得到＋主要部門含「資訊部」）才建立，CREATE_BY＝SYSTEM
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 8 回合 2：MERGE_FIRST_LOGIN 註解改寫（建表檔已是最終結構）；程式未改
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
import com.mpx.infra_manager.model.UserRoleRow;

@Repository
public class UserDao {

	/** 單一使用者：本系統列＋同步表姓名／Email；IN_SYNC＝同步表查得到（LEFT JOIN 已帶 STATUS＝1 條件，E 有列即查得到） */
	public static final String FIND_BY_ID = """
			SELECT U.USER_ID, U.LAST_LOGIN_DATE, U.STATUS,
			       E.EMAIL, CASE WHEN E.EMP_ID IS NULL THEN 0 ELSE 1 END AS IN_SYNC,
			""" + "       " + EmpSql.nameExpr("E", "U.USER_ID") + " AS USER_NAME\n" + """
			  FROM IM_ACCT_USER U
			""" + "  " + EmpSql.joinEmp("E", "U.USER_ID") + "\n" + """
			 WHERE U.USER_ID = :userId
			""";

	/** 一人的主要部門（⑪：主管的部門優先、其次 DEPT_ID 最小；全名空或字串 'NULL' 用部門名）；查無為 NULL。只給 /me 用（逐人子查詢，不放進名單） */
	public static final String FIND_PRIMARY_DEPT = "SELECT " + EmpSql.primaryDeptExpr(":userId") + " AS CODE FROM DUAL\n";

	/**
	 * 首次登入自動建立（裁示 ①B、D3A、D9B）：本系統沒有帳號列、且資格符合才 INSERT，否則影響 0 筆。
	 * 資格與建立在同一句判斷；兩人同時首次登入同一工號時，後到者撞主鍵丟 DuplicateKeyException，由呼叫端重查。
	 * 只寫本系統自己的欄位（USER_ID、STATUS、共用欄），即建表檔的最終結構；腳本 D 刪欄前的既有環境，
	 * 舊欄的失敗次數用 DB 預設 0、英文名欄已由腳本 S1 改可空，這句照樣能用。
	 */
	public static final String MERGE_FIRST_LOGIN = """
			MERGE INTO IM_ACCT_USER U
			USING (SELECT :userId AS USER_ID FROM DUAL
			""" + "        WHERE " + EmpSql.qualifiedCond(":userId") + ") S\n" + """
			   ON (U.USER_ID = S.USER_ID)
			 WHEN NOT MATCHED THEN
			      INSERT (USER_ID, STATUS, CREATE_DATE, CREATE_BY)
			      VALUES (S.USER_ID, 1, :now, :createBy)
			""";

	/** 登入成功：只寫最後登入時間（失敗次數與鎖定交給 WPX，裁示 ③B） */
	public static final String UPDATE_LOGIN_OK = """
			UPDATE IM_ACCT_USER
			   SET LAST_LOGIN_DATE = :now, UPDATE_DATE = :now, UPDATE_BY = :userId
			 WHERE USER_ID = :userId
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

	/** 主要部門名稱；同步表查無或沒有有效部門回 null */
	public String findPrimaryDept(String userId) {
		Map<String, Object> params = new HashMap<>();
		params.put("userId", userId);
		CodeRow row = DaoSupport.first(dbClient.query(itflowDb, FIND_PRIMARY_DEPT, params, CodeRow.class));
		return row == null ? null : row.getCode();
	}

	/**
	 * 首次登入自動建立；建立回 1，已有帳號列或資格不符回 0。
	 * 同一工號並發建立時後到者丟 {@link org.springframework.dao.DuplicateKeyException}（呼叫端重查即可）。
	 */
	public int createOnFirstLogin(String userId, LocalDateTime now) {
		Map<String, Object> params = new HashMap<>();
		params.put("userId", userId);
		params.put("now", DaoSupport.ts(now));
		params.put("createBy", AcctConst.SYSTEM_USER);
		return dbClient.update(itflowDb, MERGE_FIRST_LOGIN, params);
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
