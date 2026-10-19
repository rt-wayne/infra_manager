package com.mpx.infra_manager.controller;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：系統管理——角色指派維護 API（BACKLOG 第 89 項階段 1）：GET /admin/roles、GET /admin/users、
//           POST /admin/roles/{roleId}/members、POST /admin/roles/{roleId}/members/{userId}/revoke；
//           只用 GET／POST（殼 jar BackendForwarder 只有 get、post）；只轉呼叫 service，不寫權限邏輯
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 階段 2 回合 1：執行人對照 3 支——GET /admin/executors、POST /admin/executors/{catgId}/members、
//           POST /admin/executors/{catgId}/members/{userId}/revoke
// ============================================================

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.model.AdminExecCatg;
import com.mpx.infra_manager.model.AdminRole;
import com.mpx.infra_manager.model.AdminUser;
import com.mpx.infra_manager.model.MemberChangeResult;
import com.mpx.infra_manager.model.request.MemberForm;
import com.mpx.infra_manager.model.request.ReasonForm;
import com.mpx.infra_manager.service.RoleAdminService;

@RestController
@RequestMapping("/api/v1")
public class AdminController {

	private final RoleAdminService roleAdminService;

	public AdminController(RoleAdminService roleAdminService) {
		this.roleAdminService = roleAdminService;
	}

	@GetMapping("/admin/roles")
	public List<AdminRole> roles(Identity id) {
		return roleAdminService.roles(id);
	}

	@GetMapping("/admin/users")
	public List<AdminUser> users(Identity id) {
		return roleAdminService.users(id);
	}

	@PostMapping("/admin/roles/{roleId}/members")
	public MemberChangeResult grantRole(Identity id, @PathVariable("roleId") String roleId,
			@RequestBody(required = false) MemberForm form) {
		return roleAdminService.grantRole(id, roleId, form);
	}

	@PostMapping("/admin/roles/{roleId}/members/{userId}/revoke")
	public MemberChangeResult revokeRole(Identity id, @PathVariable("roleId") String roleId,
			@PathVariable("userId") String userId, @RequestBody(required = false) ReasonForm form) {
		return roleAdminService.revokeRole(id, roleId, userId, form);
	}

	@GetMapping("/admin/executors")
	public List<AdminExecCatg> executors(Identity id) {
		return roleAdminService.executors(id);
	}

	@PostMapping("/admin/executors/{catgId}/members")
	public MemberChangeResult grantExecutor(Identity id, @PathVariable("catgId") String catgId,
			@RequestBody(required = false) MemberForm form) {
		return roleAdminService.grantExecutor(id, catgId, form);
	}

	@PostMapping("/admin/executors/{catgId}/members/{userId}/revoke")
	public MemberChangeResult revokeExecutor(Identity id, @PathVariable("catgId") String catgId,
			@PathVariable("userId") String userId, @RequestBody(required = false) ReasonForm form) {
		return roleAdminService.revokeExecutor(id, catgId, userId, form);
	}
}
