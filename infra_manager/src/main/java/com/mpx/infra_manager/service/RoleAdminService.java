package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：角色指派維護（BACKLOG 第 89 項階段 1；SA 4.7、裁示 ①B／②A／③A／⑤B／⑥A／⑦A）
//           清單：GET /admin/roles 需 SYS_ROLE（不含 GENERAL 與停用角色；成員只列 STATUS＝1 指派，對象停用 userActive＝false）；
//           GET /admin/users 需 SYS_ROLE 或 SYS_EXECUTOR（啟用中使用者、含自己）。查詢被拒只回 403、不寫軌跡（沒有對應動作碼）。
//           指派：無權限 403＋DENY → 切換中 E25 → 缺對象 E02 → 原因 E10／E11 → 角色不合法 400 → 對象不存在或停用 404
//                 → 交易：取全域鎖（逾時 E26）→ 已在名單 E21 → UPDATE 0→1（重新指派，保留 CREATE_*）或 INSERT → SUCCESS 軌跡同交易。
//           移除：無權限 403＋DENY → 切換中 E25 → 原因 E10／E11 → 角色不合法 400（對象停用或查無仍允許移除）
//                 → 交易：取鎖 → 不在名單 E22 → HolderGuard（SYS_ROLE 歸零 E24、在途 E23）→ UPDATE 1→0 → SUCCESS 軌跡；
//                 E23／E24 先回滾再於交易外寫 DENY。改自己允許（裁示 ⑤B），SYS_ROLE 歸零檢查防止全系統無人能維護。
//           ORA-30006（FOR UPDATE WAIT 逾時）由 Spring 轉成 PessimisticLockingFailureException 家族（classpath 沒有自訂
//           sql-error-codes.xml 時走 SQLState 分類，不保證是子類別 CannotAcquireLockException；死結 ORA-00060 同家族），
//           攔父類別、在交易外轉 E26、不寫軌跡（階段 1 審查 M1；實際型別待階段 2 實機鎖測試確認）
// ============================================================

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.AcctMsg;
import com.mpx.infra_manager.common.ApiException;
import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.common.Texts;
import com.mpx.infra_manager.dao.RoleAdminDao;
import com.mpx.infra_manager.dao.UserDao;
import com.mpx.infra_manager.model.AcctUser;
import com.mpx.infra_manager.model.AdminMember;
import com.mpx.infra_manager.model.AdminRole;
import com.mpx.infra_manager.model.AdminUser;
import com.mpx.infra_manager.model.MemberChangeResult;
import com.mpx.infra_manager.model.MemberRow;
import com.mpx.infra_manager.model.RolePermRow;
import com.mpx.infra_manager.model.RoleRow;
import com.mpx.infra_manager.model.request.MemberForm;
import com.mpx.infra_manager.model.request.ReasonForm;

@Service
public class RoleAdminService {

	private static final Logger log = LoggerFactory.getLogger(RoleAdminService.class);
	/** ROLE_ID 欄寬 */
	static final int ROLE_ID_MAX = 20;
	/** USER_ID 欄寬 */
	static final int USER_ID_MAX = 30;

	/** HolderGuard 擋下：在交易內丟出讓交易回滾，交易外轉成 DENY 軌跡與 E23／E24 */
	static class GuardReject extends RuntimeException {
		private static final long serialVersionUID = 1L;
		final transient HolderGuard.Result result;

		GuardReject(HolderGuard.Result result) {
			this.result = result;
		}
	}

	private final PermissionService perm;
	private final RoleAdminDao roleAdminDao;
	private final UserDao userDao;
	private final HolderGuard holderGuard;
	private final AdminLogWriter logWriter;
	private final TransactionTemplate tx;
	private final Clock clock;

	public RoleAdminService(PermissionService perm, RoleAdminDao roleAdminDao, UserDao userDao, HolderGuard holderGuard,
			AdminLogWriter logWriter, TransactionTemplate tx, Clock clock) {
		this.perm = perm;
		this.roleAdminDao = roleAdminDao;
		this.userDao = userDao;
		this.holderGuard = holderGuard;
		this.logWriter = logWriter;
		this.tx = tx;
		this.clock = clock;
	}

	// ------------------------------------------------------------
	// 清單
	// ------------------------------------------------------------

	public List<AdminRole> roles(Identity id) {
		perm.require(id, AcctConst.PERM_SYS_ROLE);
		Map<String, List<String>> permNames = new LinkedHashMap<>();
		for (RolePermRow p : roleAdminDao.findRolePerms()) {
			permNames.computeIfAbsent(p.getRoleId(), k -> new ArrayList<>()).add(p.getPermName());
		}
		Map<String, List<AdminMember>> members = new LinkedHashMap<>();
		for (MemberRow m : roleAdminDao.findRoleMembers()) {
			members.computeIfAbsent(m.getGroupId(), k -> new ArrayList<>()).add(member(m));
		}
		List<AdminRole> out = new ArrayList<>();
		for (RoleRow r : roleAdminDao.findRoles()) {
			AdminRole a = new AdminRole();
			a.setRoleId(r.getRoleId());
			a.setRoleName(r.getRoleName());
			a.setRoleDescr(r.getRoleDescr());
			a.setSortSeq(r.getSortSeq());
			a.setPermNames(permNames.getOrDefault(r.getRoleId(), List.of()));
			a.setMembers(members.getOrDefault(r.getRoleId(), List.of()));
			out.add(a);
		}
		return out;
	}

	static AdminMember member(MemberRow m) {
		AdminMember a = new AdminMember();
		a.setUserId(m.getUserId());
		a.setUserName(Texts.displayName(m.getUserEnName(), m.getUserName(), m.getUserId()));
		a.setUserActive(Integer.valueOf(1).equals(m.getUserStatus()));
		a.setGrantDate(m.getGrantDate());
		a.setGrantBy(m.getGrantBy());
		return a;
	}

	/** 指派候選人：兩頁共用，持有任一維護權限即可 */
	public List<AdminUser> users(Identity id) {
		perm.requireAny(id, AcctConst.PERM_SYS_ROLE, AcctConst.PERM_SYS_EXECUTOR);
		List<AdminUser> out = new ArrayList<>();
		for (AcctUser u : roleAdminDao.findCandidates()) {
			AdminUser a = new AdminUser();
			a.setUserId(u.getUserId());
			a.setUserName(Texts.displayName(u.getUserEnName(), u.getUserName(), u.getUserId()));
			a.setDeptName(u.getDeptName());
			out.add(a);
		}
		return out;
	}

	// ------------------------------------------------------------
	// 角色指派
	// ------------------------------------------------------------

	public MemberChangeResult grantRole(Identity id, String roleId, MemberForm form) {
		LocalDateTime now = now();
		String targetId = Texts.trimToNull(form == null ? null : form.getUserId());
		String reason = Texts.trimToNull(form == null ? null : form.getReason());
		requirePermOrDeny(id, AcctConst.PERM_SYS_ROLE, AcctConst.ADM_ROLE_GRANT, roleId, targetId, reason, now);
		requireNotImpersonating(id);
		if (targetId == null) {
			throw AcctMsg.required(AcctMsg.WHAT_TARGET);
		}
		checkReason(reason);
		RoleRow role = requireRole(roleId);
		AcctUser target = targetId.length() > USER_ID_MAX ? null : userDao.findById(targetId);
		if (target == null || !Integer.valueOf(1).equals(target.getStatus())) {
			throw AcctMsg.notFound();
		}
		String targetName = Texts.displayName(target.getUserEnName(), target.getUserName(), targetId);

		locked(() -> {
			Integer status = roleAdminDao.findRoleMemberStatus(roleId, targetId);
			if (Integer.valueOf(1).equals(status)) {
				throw ApiException.conflict(AcctMsg.E21);
			}
			if (Integer.valueOf(0).equals(status)) {
				// 重新指派：只寫 UPDATE_*、保留第一次的 CREATE_*（裁示 ⑦A）
				if (roleAdminDao.updateRoleMemberStatus(roleId, targetId, 0, 1, now, id.getRealUserId()) != 1) {
					throw ApiException.conflict(AcctMsg.E21);
				}
			} else {
				try {
					roleAdminDao.insertRoleMember(roleId, targetId, now, id.getRealUserId());
				} catch (DuplicateKeyException e) {
					throw ApiException.conflict(AcctMsg.E21);
				}
			}
			Map<String, Object> content = roleContent(role, targetName);
			content.put("before", statusMap(status));
			content.put("after", statusMap(1));
			logWriter.success(now, id, AcctConst.ADM_ROLE_GRANT, targetId, content, reason);
		});
		log.info("角色指派 roleId={} target={} by={}", roleId, targetId, id.getRealUserId());
		return result(roleId, targetId, MemberChangeResult.GRANTED);
	}

	public MemberChangeResult revokeRole(Identity id, String roleId, String userId, ReasonForm form) {
		LocalDateTime now = now();
		String targetId = Texts.trimToNull(userId);
		String reason = Texts.trimToNull(form == null ? null : form.getReason());
		requirePermOrDeny(id, AcctConst.PERM_SYS_ROLE, AcctConst.ADM_ROLE_REVOKE, roleId, targetId, reason, now);
		requireNotImpersonating(id);
		checkReason(reason);
		RoleRow role = requireRole(roleId);
		// 對象停用、或使用者表查無的列都允許移除；查得到才帶顯示名
		AcctUser target = targetId == null || targetId.length() > USER_ID_MAX ? null : userDao.findById(targetId);
		String targetName = target == null ? targetId : Texts.displayName(target.getUserEnName(), target.getUserName(), targetId);
		Map<String, Object> content = roleContent(role, targetName);

		try {
			locked(() -> {
				Integer status = roleAdminDao.findRoleMemberStatus(roleId, targetId);
				if (!Integer.valueOf(1).equals(status)) {
					throw ApiException.conflict(AcctMsg.E22);
				}
				HolderGuard.Result r = holderGuard.check(roleAdminDao.findPermIdsOfRole(roleId),
						HolderGuard.Exclusion.member(roleId, targetId));
				if (!r.ok()) {
					throw new GuardReject(r);
				}
				if (roleAdminDao.updateRoleMemberStatus(roleId, targetId, 1, 0, now, id.getRealUserId()) != 1) {
					throw ApiException.conflict(AcctMsg.E22);
				}
				content.put("before", statusMap(1));
				content.put("after", statusMap(0));
				logWriter.success(now, id, AcctConst.ADM_ROLE_REVOKE, targetId, content, reason);
			});
		} catch (GuardReject e) {
			throw denyByGuard(id, AcctConst.ADM_ROLE_REVOKE, targetId, content, reason, now, e.result);
		}
		log.info("角色指派移除 roleId={} target={} by={}", roleId, targetId, id.getRealUserId());
		return result(roleId, targetId, MemberChangeResult.REVOKED);
	}

	// ------------------------------------------------------------
	// 共用（階段 2 執行人對照沿用）
	// ------------------------------------------------------------

	LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
	}

	/** 無權限 → 寫 DENY（原因有帶就存；內容只記路徑上的代碼）→ 403 */
	void requirePermOrDeny(Identity id, String permId, String actionCode, String groupId, String targetId, String reason,
			LocalDateTime now) {
		if (perm.can(id, permId)) {
			return;
		}
		Map<String, Object> content = AdminLogWriter.content();
		content.put(AcctConst.ADM_ROLE_GRANT.equals(actionCode) || AcctConst.ADM_ROLE_REVOKE.equals(actionCode) ? "roleId" : "catgId",
				groupId != null && groupId.length() <= ROLE_ID_MAX ? groupId : null);
		content.put("denyCode", AcctConst.DENY_NO_PERM);
		logWriter.deny(now, id, actionCode, targetId, content, reason);
		log.info("名單維護被拒（無權限）action={} userId={}", actionCode, id.getRealUserId());
		throw AcctMsg.forbidden();
	}

	/** 切換身分中一律拒絕（裁示 ⑤A）：軌跡永遠是本人 */
	static void requireNotImpersonating(Identity id) {
		if (id.isImpersonating()) {
			throw ApiException.conflict(AcctMsg.E25);
		}
	}

	static void checkReason(String reason) {
		if (reason == null) {
			throw AcctMsg.fillIn(AcctMsg.WHAT_REASON);
		}
		Texts.maxChars(AcctMsg.WHAT_REASON, reason, AcctConst.ADMIN_REASON_MAX);
	}

	/** 可維護的角色：存在、啟用中、不是 GENERAL（裁示 ⑪-1A 後端也擋） */
	RoleRow requireRole(String roleId) {
		RoleRow r = roleId == null || roleId.length() > ROLE_ID_MAX ? null : roleAdminDao.findRoleById(roleId);
		if (r == null || !Integer.valueOf(1).equals(r.getStatus()) || AcctConst.ROLE_GENERAL.equals(r.getRoleId())) {
			throw AcctMsg.badCode(AcctMsg.WHAT_ROLE);
		}
		return r;
	}

	/** 交易內第一句取全域鎖再執行；等待逾時（或死結）在交易外轉 E26。攔父類別：Spring 走 SQLState 分類時不一定給子類別 */
	void locked(Runnable body) {
		try {
			tx.executeWithoutResult(s -> {
				roleAdminDao.lockGlobal();
				body.run();
			});
		} catch (PessimisticLockingFailureException e) {
			log.info("名單維護取鎖逾時 {}", e.getMessage());
			throw ApiException.conflict(AcctMsg.E26);
		}
	}

	/** HolderGuard 擋下（交易已回滾）：交易外寫 DENY，回 E24（優先）或 E23 */
	ApiException denyByGuard(Identity id, String actionCode, String targetId, Map<String, Object> content, String reason,
			LocalDateTime now, HolderGuard.Result r) {
		List<String> ids = AdminLogWriter.putBlocked(content, r);
		logWriter.deny(now, id, actionCode, targetId, content, List.of(ids), reason);
		if (r.sysRoleGone()) {
			return ApiException.conflict(AcctMsg.E24);
		}
		return AcctMsg.inFlight(HolderGuard.describe(r));
	}

	static Map<String, Object> roleContent(RoleRow role, String targetName) {
		Map<String, Object> c = AdminLogWriter.content();
		c.put("roleId", role.getRoleId());
		c.put("roleName", role.getRoleName());
		c.put("targetUserName", targetName);
		return c;
	}

	/** before／after：{"status": null｜0｜1}（null＝沒有這一列） */
	static Map<String, Object> statusMap(Integer status) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("status", status);
		return m;
	}

	static MemberChangeResult result(String roleId, String userId, String result) {
		MemberChangeResult r = new MemberChangeResult();
		r.setRoleId(roleId);
		r.setUserId(userId);
		r.setResult(result);
		return r;
	}
}
