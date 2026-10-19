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
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 階段 2 回合 1：執行人對照維護（裁示 4.A 在途只算停在關卡 6 的同類別單）——GET /admin/executors 需 SYS_EXECUTOR（8 類全列、
//           含 IS_ENABLED＝0 的類別，因為仍可能有在途單）；指派／移除執行人與角色指派同一條驗證順序與交易流程，差異（名單表、守門）
//           抽成 Roster 由兩個名單共用；類別不合法回 400「欄位內容不正確：帳號類別」（STATUS＝0 或不存在；IS_ENABLED＝0 仍可維護）；
//           執行人對照與 SYS_ROLE 無關，不會有 E24
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
import com.mpx.infra_manager.model.AdminExecCatg;
import com.mpx.infra_manager.model.AdminMember;
import com.mpx.infra_manager.model.AdminRole;
import com.mpx.infra_manager.model.AdminUser;
import com.mpx.infra_manager.model.Category;
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
	/** CATG_ID 欄寬 */
	static final int CATG_ID_MAX = 10;

	/** HolderGuard 擋下：在交易內丟出讓交易回滾，交易外轉成 DENY 軌跡與 E23／E24 */
	static class GuardReject extends RuntimeException {
		private static final long serialVersionUID = 1L;
		final transient HolderGuard.Result result;

		GuardReject(HolderGuard.Result result) {
			this.result = result;
		}
	}

	/** 名單的差異集中在這裡（角色指派 IM_ACCT_ROLE_USER／執行人對照 IM_EXEC_MAP）；指派與移除的流程由 grant／revoke 共用 */
	interface Roster {
		/** 名單列現況：查無列 null、0、1 */
		Integer status(String groupId, String targetId);

		int update(String groupId, String targetId, int oldStatus, int newStatus, LocalDateTime now, String by);

		int insert(String groupId, String targetId, LocalDateTime now, String by);

		/** 移除前的「最後一位」檢查 */
		HolderGuard.Result guard(String groupId, String targetId);
	}

	private final PermissionService perm;
	private final RoleAdminDao roleAdminDao;
	private final UserDao userDao;
	private final CodeService codeService;
	private final HolderGuard holderGuard;
	private final AdminLogWriter logWriter;
	private final TransactionTemplate tx;
	private final Clock clock;

	private final Roster roleRoster = new Roster() {
		@Override
		public Integer status(String roleId, String targetId) {
			return roleAdminDao.findRoleMemberStatus(roleId, targetId);
		}

		@Override
		public int update(String roleId, String targetId, int oldStatus, int newStatus, LocalDateTime now, String by) {
			return roleAdminDao.updateRoleMemberStatus(roleId, targetId, oldStatus, newStatus, now, by);
		}

		@Override
		public int insert(String roleId, String targetId, LocalDateTime now, String by) {
			return roleAdminDao.insertRoleMember(roleId, targetId, now, by);
		}

		@Override
		public HolderGuard.Result guard(String roleId, String targetId) {
			// 失去的碼＝該角色授予的全部碼
			return holderGuard.check(roleAdminDao.findPermIdsOfRole(roleId), HolderGuard.Exclusion.member(roleId, targetId));
		}
	};

	private final Roster execRoster = new Roster() {
		@Override
		public Integer status(String catgId, String targetId) {
			return roleAdminDao.findExecMemberStatus(catgId, targetId);
		}

		@Override
		public int update(String catgId, String targetId, int oldStatus, int newStatus, LocalDateTime now, String by) {
			return roleAdminDao.updateExecMemberStatus(catgId, targetId, oldStatus, newStatus, now, by);
		}

		@Override
		public int insert(String catgId, String targetId, LocalDateTime now, String by) {
			return roleAdminDao.insertExecMember(catgId, targetId, now, by);
		}

		@Override
		public HolderGuard.Result guard(String catgId, String targetId) {
			return holderGuard.checkExecutor(catgId, targetId);
		}
	};

	public RoleAdminService(PermissionService perm, RoleAdminDao roleAdminDao, UserDao userDao, CodeService codeService,
			HolderGuard holderGuard, AdminLogWriter logWriter, TransactionTemplate tx, Clock clock) {
		this.perm = perm;
		this.roleAdminDao = roleAdminDao;
		this.userDao = userDao;
		this.codeService = codeService;
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

	/** 執行人對照：8 類全列（含 IS_ENABLED＝0——關閉新申請的類別仍可能有在途單要執行） */
	public List<AdminExecCatg> executors(Identity id) {
		perm.require(id, AcctConst.PERM_SYS_EXECUTOR);
		Map<String, List<AdminMember>> members = new LinkedHashMap<>();
		for (MemberRow m : roleAdminDao.findExecMembers()) {
			members.computeIfAbsent(m.getGroupId(), k -> new ArrayList<>()).add(member(m));
		}
		List<AdminExecCatg> out = new ArrayList<>();
		for (Category c : codeService.getCodes().getCategories()) {
			AdminExecCatg a = new AdminExecCatg();
			a.setCatgId(c.getCatgId());
			a.setCatgName(c.getCatgName());
			a.setEnabled(c.getEnabled());
			a.setSortSeq(c.getSortSeq());
			a.setMembers(members.getOrDefault(c.getCatgId(), List.of()));
			out.add(a);
		}
		return out;
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
		Map<String, Object> content = roleContent(role, requireActiveTarget(targetId));
		grant(id, now, AcctConst.ADM_ROLE_GRANT, roleId, targetId, reason, content, roleRoster);
		log.info("角色指派 roleId={} target={} by={}", roleId, targetId, id.getRealUserId());
		return result(roleId, null, targetId, MemberChangeResult.GRANTED);
	}

	public MemberChangeResult revokeRole(Identity id, String roleId, String userId, ReasonForm form) {
		LocalDateTime now = now();
		String targetId = Texts.trimToNull(userId);
		String reason = Texts.trimToNull(form == null ? null : form.getReason());
		requirePermOrDeny(id, AcctConst.PERM_SYS_ROLE, AcctConst.ADM_ROLE_REVOKE, roleId, targetId, reason, now);
		requireNotImpersonating(id);
		checkReason(reason);
		RoleRow role = requireRole(roleId);
		Map<String, Object> content = roleContent(role, targetNameOrId(targetId));
		revoke(id, now, AcctConst.ADM_ROLE_REVOKE, roleId, targetId, reason, content, roleRoster);
		log.info("角色指派移除 roleId={} target={} by={}", roleId, targetId, id.getRealUserId());
		return result(roleId, null, targetId, MemberChangeResult.REVOKED);
	}

	// ------------------------------------------------------------
	// 執行人對照
	// ------------------------------------------------------------

	public MemberChangeResult grantExecutor(Identity id, String catgId, MemberForm form) {
		LocalDateTime now = now();
		String targetId = Texts.trimToNull(form == null ? null : form.getUserId());
		String reason = Texts.trimToNull(form == null ? null : form.getReason());
		requirePermOrDeny(id, AcctConst.PERM_SYS_EXECUTOR, AcctConst.ADM_EXEC_GRANT, catgId, targetId, reason, now);
		requireNotImpersonating(id);
		if (targetId == null) {
			throw AcctMsg.required(AcctMsg.WHAT_TARGET);
		}
		checkReason(reason);
		Category catg = requireCatg(catgId);
		Map<String, Object> content = catgContent(catg, requireActiveTarget(targetId));
		grant(id, now, AcctConst.ADM_EXEC_GRANT, catgId, targetId, reason, content, execRoster);
		log.info("執行人指派 catgId={} target={} by={}", catgId, targetId, id.getRealUserId());
		return result(null, catgId, targetId, MemberChangeResult.GRANTED);
	}

	public MemberChangeResult revokeExecutor(Identity id, String catgId, String userId, ReasonForm form) {
		LocalDateTime now = now();
		String targetId = Texts.trimToNull(userId);
		String reason = Texts.trimToNull(form == null ? null : form.getReason());
		requirePermOrDeny(id, AcctConst.PERM_SYS_EXECUTOR, AcctConst.ADM_EXEC_REVOKE, catgId, targetId, reason, now);
		requireNotImpersonating(id);
		checkReason(reason);
		Category catg = requireCatg(catgId);
		Map<String, Object> content = catgContent(catg, targetNameOrId(targetId));
		revoke(id, now, AcctConst.ADM_EXEC_REVOKE, catgId, targetId, reason, content, execRoster);
		log.info("執行人移除 catgId={} target={} by={}", catgId, targetId, id.getRealUserId());
		return result(null, catgId, targetId, MemberChangeResult.REVOKED);
	}

	// ------------------------------------------------------------
	// 兩個名單共用的流程
	// ------------------------------------------------------------

	/** 指派：交易內取鎖 → 已在名單 E21 → UPDATE 0→1（重新指派，保留 CREATE_*）或 INSERT → SUCCESS 軌跡同交易 */
	void grant(Identity id, LocalDateTime now, String actionCode, String groupId, String targetId, String reason,
			Map<String, Object> content, Roster roster) {
		locked(() -> {
			Integer status = roster.status(groupId, targetId);
			if (Integer.valueOf(1).equals(status)) {
				throw ApiException.conflict(AcctMsg.E21);
			}
			if (Integer.valueOf(0).equals(status)) {
				// 重新指派：只寫 UPDATE_*、保留第一次的 CREATE_*（裁示 ⑦A）
				if (roster.update(groupId, targetId, 0, 1, now, id.getRealUserId()) != 1) {
					throw ApiException.conflict(AcctMsg.E21);
				}
			} else {
				try {
					roster.insert(groupId, targetId, now, id.getRealUserId());
				} catch (DuplicateKeyException e) {
					throw ApiException.conflict(AcctMsg.E21);
				}
			}
			content.put("before", statusMap(status));
			content.put("after", statusMap(1));
			logWriter.success(now, id, actionCode, targetId, content, reason);
		});
	}

	/** 移除：交易內取鎖 → 不在名單 E22 → 守門（擋下就回滾、交易外寫 DENY）→ UPDATE 1→0 → SUCCESS 軌跡同交易 */
	void revoke(Identity id, LocalDateTime now, String actionCode, String groupId, String targetId, String reason,
			Map<String, Object> content, Roster roster) {
		try {
			locked(() -> {
				Integer status = roster.status(groupId, targetId);
				if (!Integer.valueOf(1).equals(status)) {
					throw ApiException.conflict(AcctMsg.E22);
				}
				HolderGuard.Result r = roster.guard(groupId, targetId);
				if (!r.ok()) {
					throw new GuardReject(r);
				}
				if (roster.update(groupId, targetId, 1, 0, now, id.getRealUserId()) != 1) {
					throw ApiException.conflict(AcctMsg.E22);
				}
				content.put("before", statusMap(1));
				content.put("after", statusMap(0));
				logWriter.success(now, id, actionCode, targetId, content, reason);
			});
		} catch (GuardReject e) {
			throw denyByGuard(id, actionCode, targetId, content, reason, now, e.result);
		}
	}

	/** 指派對象必須存在且啟用中（404），回顯示名 */
	String requireActiveTarget(String targetId) {
		AcctUser target = targetId.length() > USER_ID_MAX ? null : userDao.findById(targetId);
		if (target == null || !Integer.valueOf(1).equals(target.getStatus())) {
			throw AcctMsg.notFound();
		}
		return Texts.displayName(target.getUserEnName(), target.getUserName(), targetId);
	}

	/** 移除對象：停用、或使用者表查無的列都允許移除；查得到才帶顯示名，否則退回工號 */
	String targetNameOrId(String targetId) {
		AcctUser target = targetId == null || targetId.length() > USER_ID_MAX ? null : userDao.findById(targetId);
		return target == null ? targetId : Texts.displayName(target.getUserEnName(), target.getUserName(), targetId);
	}

	// ------------------------------------------------------------
	// 共用
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
		boolean isRole = AcctConst.ADM_ROLE_GRANT.equals(actionCode) || AcctConst.ADM_ROLE_REVOKE.equals(actionCode);
		int max = isRole ? ROLE_ID_MAX : CATG_ID_MAX;
		Map<String, Object> content = AdminLogWriter.content();
		content.put(isRole ? "roleId" : "catgId", groupId != null && groupId.length() <= max ? groupId : null);
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

	/** 可維護的帳號類別：存在、STATUS＝1（IS_ENABLED＝0 只是不開放新申請，仍可維護執行人） */
	Category requireCatg(String catgId) {
		Category c = catgId == null || catgId.length() > CATG_ID_MAX ? null : codeService.findCategory(catgId);
		if (c == null) {
			throw AcctMsg.badCode(AcctMsg.WHAT_CATG);
		}
		return c;
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

	static Map<String, Object> catgContent(Category catg, String targetName) {
		Map<String, Object> c = AdminLogWriter.content();
		c.put("catgId", catg.getCatgId());
		c.put("catgName", catg.getCatgName());
		c.put("targetUserName", targetName);
		return c;
	}

	/** before／after：{"status": null｜0｜1}（null＝沒有這一列） */
	static Map<String, Object> statusMap(Integer status) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("status", status);
		return m;
	}

	/** roleId 與 catgId 擇一有值 */
	static MemberChangeResult result(String roleId, String catgId, String userId, String result) {
		MemberChangeResult r = new MemberChangeResult();
		r.setRoleId(roleId);
		r.setCatgId(catgId);
		r.setUserId(userId);
		r.setResult(result);
		return r;
	}
}
