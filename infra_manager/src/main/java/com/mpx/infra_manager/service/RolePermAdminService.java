package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：角色權限設定（角色 × 權限碼，IM_ACCT_ROLE_PERM；BACKLOG 第 89 項階段 3；裁示 ⑨A～⑰A）
//           GET /admin/role-perms 需 SYS_ROLE：modules＝可見碼（AcctConst.ADMIN_VISIBLE_PERMS 在 IM_ACCT_PERM 啟用中的列）依 MODULE_CODE
//           分組、roles＝啟用中角色含 GENERAL（readOnly）、啟用中成員數、已授予的可見碼、已授予但隱藏的碼數。查詢被拒只回 403 不寫軌跡。
//           POST /admin/roles/{roleId}/perms 驗證 11 步：① 無 SYS_ROLE 403＋DENY（只記 roleId 與碼數）② 切換中 E25 ③ 原因 E10／E11
//           ④ 角色不存在／停用／GENERAL 400「角色」⑤ 兩邊皆空 400「沒有任何變更」⑥ 碼重複、同碼在兩邊、不在可見清單 400「權限碼」
//           ⑦ 交易取全域鎖（逾時 E26）⑧ 任一格現況與請求不符 E27 ⑨ 拿掉後 SYS_ROLE 由 ≥ 1 變 0 → E24 ⑩ 在途檢查全部碼一次收集 → E23
//           ⑪ removed UPDATE 1→0、added UPDATE 0→1 影響 0 筆才 INSERT（保留 CREATE_*）、同交易一筆 SUCCESS 軌跡（ROLE_PERM_SET，TARGET_USER_ID 空）。
//           E24 與 E23 同時成立先回 E24；⑨⑩ 被擋先回滾再在交易外寫 DENY（依碼分組）；④～⑥ 的 400 不寫軌跡（裁示 ⑥A）。
//           隱藏碼不在可見清單，送來就是 400，既有授予列原樣保留。
//           取鎖、角色檢查、原因檢查、切換中檢查沿用 RoleAdminService 的共用方法
// ============================================================

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.AcctMsg;
import com.mpx.infra_manager.common.ApiException;
import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.common.Texts;
import com.mpx.infra_manager.dao.RoleAdminDao;
import com.mpx.infra_manager.model.AdminPerm;
import com.mpx.infra_manager.model.AdminPermModule;
import com.mpx.infra_manager.model.AdminRolePerms;
import com.mpx.infra_manager.model.PermRow;
import com.mpx.infra_manager.model.RolePermMatrix;
import com.mpx.infra_manager.model.RolePermRow;
import com.mpx.infra_manager.model.RolePermSaveResult;
import com.mpx.infra_manager.model.RoleRow;
import com.mpx.infra_manager.model.request.RolePermForm;

@Service
public class RolePermAdminService {

	private static final Logger log = LoggerFactory.getLogger(RolePermAdminService.class);

	private final PermissionService perm;
	private final RoleAdminDao roleAdminDao;
	private final HolderGuard holderGuard;
	private final AdminLogWriter logWriter;
	private final RoleAdminService shared;

	public RolePermAdminService(PermissionService perm, RoleAdminDao roleAdminDao, HolderGuard holderGuard,
			AdminLogWriter logWriter, RoleAdminService shared) {
		this.perm = perm;
		this.roleAdminDao = roleAdminDao;
		this.holderGuard = holderGuard;
		this.logWriter = logWriter;
		this.shared = shared;
	}

	// ------------------------------------------------------------
	// 清單
	// ------------------------------------------------------------

	public RolePermMatrix matrix(Identity id) {
		perm.require(id, AcctConst.PERM_SYS_ROLE);
		List<PermRow> visible = visiblePerms();
		Set<String> visibleIds = new HashSet<>();
		Map<String, AdminPermModule> modules = new LinkedHashMap<>();
		for (PermRow p : visible) {
			visibleIds.add(p.getPermId());
			AdminPermModule m = modules.computeIfAbsent(p.getModuleCode(), k -> {
				AdminPermModule x = new AdminPermModule();
				x.setModuleCode(k);
				x.setPerms(new ArrayList<>());
				return x;
			});
			AdminPerm a = new AdminPerm();
			a.setPermId(p.getPermId());
			a.setPermName(p.getPermName());
			a.setSortSeq(p.getSortSeq());
			m.getPerms().add(a);
		}

		Map<String, List<String>> granted = new LinkedHashMap<>();
		Map<String, Integer> hidden = new LinkedHashMap<>();
		for (RolePermRow rp : roleAdminDao.findRolePerms()) {
			if (visibleIds.contains(rp.getPermId())) {
				granted.computeIfAbsent(rp.getRoleId(), k -> new ArrayList<>()).add(rp.getPermId());
			} else {
				hidden.merge(rp.getRoleId(), 1, Integer::sum);
			}
		}

		Map<String, Long> memberCounts = roleAdminDao.countRoleMembers();
		List<AdminRolePerms> roles = new ArrayList<>();
		for (RoleRow r : roleAdminDao.findRolesWithGeneral()) {
			boolean general = AcctConst.ROLE_GENERAL.equals(r.getRoleId());
			AdminRolePerms a = new AdminRolePerms();
			a.setRoleId(r.getRoleId());
			a.setRoleName(r.getRoleName());
			a.setRoleDescr(r.getRoleDescr());
			a.setReadOnly(general);
			a.setMemberCount(general ? roleAdminDao.countActiveUsers() : memberCounts.getOrDefault(r.getRoleId(), 0L));
			a.setGrantedPermIds(granted.getOrDefault(r.getRoleId(), List.of()));
			a.setHiddenGrantedCount(hidden.getOrDefault(r.getRoleId(), 0));
			roles.add(a);
		}

		RolePermMatrix out = new RolePermMatrix();
		out.setModules(new ArrayList<>(modules.values()));
		out.setRoles(roles);
		return out;
	}

	/** 可見碼在 IM_ACCT_PERM 的啟用中列（常數有、DB 停用或不存在的碼不會出現） */
	List<PermRow> visiblePerms() {
		return roleAdminDao.findPermsByIds(AcctConst.ADMIN_VISIBLE_PERMS);
	}

	// ------------------------------------------------------------
	// 存檔
	// ------------------------------------------------------------

	public RolePermSaveResult save(Identity id, String roleId, RolePermForm form) {
		LocalDateTime now = shared.now();
		List<String> added = codes(form == null ? null : form.getAdded());
		List<String> removed = codes(form == null ? null : form.getRemoved());
		String reason = Texts.trimToNull(form == null ? null : form.getReason());
		// ①
		requirePermOrDeny(id, roleId, added.size(), removed.size(), reason, now);
		// ②③④
		RoleAdminService.requireNotImpersonating(id);
		RoleAdminService.checkReason(reason);
		RoleRow role = shared.requireRole(roleId);
		// ⑤
		if (added.isEmpty() && removed.isEmpty()) {
			throw ApiException.badRequest(AcctMsg.NO_CHANGE);
		}
		// ⑥：重複、同碼在兩邊、不在可見清單（含不存在、停用、隱藏）
		Map<String, String> names = new LinkedHashMap<>();
		for (PermRow p : visiblePerms()) {
			names.put(p.getPermId(), p.getPermName());
		}
		Set<String> seen = new HashSet<>();
		for (String p : concat(added, removed)) {
			if (p == null || !names.containsKey(p) || !seen.add(p)) {
				throw AcctMsg.badCode(AcctMsg.WHAT_PERM);
			}
		}

		Map<String, Object> content = AdminLogWriter.content();
		content.put("roleId", role.getRoleId());
		content.put("roleName", role.getRoleName());
		content.put("memberCount", roleAdminDao.countRoleMembers().getOrDefault(role.getRoleId(), 0L));
		content.put("added", added);
		content.put("removed", removed);
		try {
			// ⑦～⑪
			shared.locked(() -> {
				Map<String, Integer> current = roleAdminDao.findRolePermStatuses(role.getRoleId());
				// ⑧ 逐格核對現況
				for (String p : added) {
					if (Integer.valueOf(1).equals(current.get(p))) {
						throw ApiException.conflict(AcctMsg.E27);
					}
				}
				for (String p : removed) {
					if (!Integer.valueOf(1).equals(current.get(p))) {
						throw ApiException.conflict(AcctMsg.E27);
					}
				}
				// ⑨⑩
				HolderGuard.Result r = holderGuard.checkRolePerms(role.getRoleId(), removed);
				if (!r.ok()) {
					throw new RoleAdminService.GuardReject(r);
				}
				// ⑪
				for (String p : removed) {
					if (roleAdminDao.updateRolePermStatus(role.getRoleId(), p, 1, 0, now, id.getRealUserId()) != 1) {
						throw ApiException.conflict(AcctMsg.E27);
					}
				}
				for (String p : added) {
					if (current.get(p) == null) {
						try {
							roleAdminDao.insertRolePerm(role.getRoleId(), p, now, id.getRealUserId());
						} catch (DuplicateKeyException e) {
							throw ApiException.conflict(AcctMsg.E27);
						}
					} else if (roleAdminDao.updateRolePermStatus(role.getRoleId(), p, 0, 1, now, id.getRealUserId()) != 1) {
						// 重新授予：只寫 UPDATE_*、保留第一次的 CREATE_*（比照裁示 ⑦A）
						throw ApiException.conflict(AcctMsg.E27);
					}
				}
				logWriter.success(now, id, AcctConst.ADM_ROLE_PERM_SET, null, content, reason);
			});
		} catch (RoleAdminService.GuardReject e) {
			List<List<String>> trimmable = AdminLogWriter.putBlockedByPerm(content, e.result);
			logWriter.deny(now, id, AcctConst.ADM_ROLE_PERM_SET, null, content, trimmable, reason);
			if (e.result.sysRoleGone()) {
				throw ApiException.conflict(AcctMsg.E24_PERM);
			}
			throw AcctMsg.inFlightPerm(HolderGuard.describeByPerm(e.result, names::get));
		}
		log.info("角色權限存檔 roleId={} added={} removed={} by={}", role.getRoleId(), added.size(), removed.size(),
				id.getRealUserId());
		RolePermSaveResult out = new RolePermSaveResult();
		out.setRoleId(role.getRoleId());
		out.setAdded(added);
		out.setRemoved(removed);
		out.setResult(RolePermSaveResult.SAVED);
		return out;
	}

	/** 無權限 → 寫 DENY（只記路徑 roleId（先檢查長度）與送來的碼數，不記整個陣列）→ 403 */
	void requirePermOrDeny(Identity id, String roleId, int addedCount, int removedCount, String reason, LocalDateTime now) {
		if (perm.can(id, AcctConst.PERM_SYS_ROLE)) {
			return;
		}
		Map<String, Object> content = AdminLogWriter.content();
		content.put("roleId", roleId != null && roleId.length() <= RoleAdminService.ROLE_ID_MAX ? roleId : null);
		content.put("addedCount", addedCount);
		content.put("removedCount", removedCount);
		content.put("denyCode", AcctConst.DENY_NO_PERM);
		logWriter.deny(now, id, AcctConst.ADM_ROLE_PERM_SET, null, content, reason);
		log.info("角色權限存檔被拒（無權限）userId={}", id.getRealUserId());
		throw AcctMsg.forbidden();
	}

	/** 去頭尾空白；null 清單視為空；空字串元素保留為 null，留給 ⑥ 回 400 */
	static List<String> codes(List<String> raw) {
		List<String> out = new ArrayList<>();
		if (raw != null) {
			for (String s : raw) {
				out.add(Texts.trimToNull(s));
			}
		}
		return out;
	}

	static List<String> concat(List<String> a, List<String> b) {
		List<String> out = new ArrayList<>(a);
		out.addAll(b);
		return out;
	}
}
