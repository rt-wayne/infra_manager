package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：GET /admin/role-perms 的回應（BACKLOG 第 89 項階段 3）：modules（可見權限碼依模組分組）＋ roles（含 GENERAL 唯讀列）
// ============================================================

import java.util.List;

public class RolePermMatrix {

	private List<AdminPermModule> modules;
	private List<AdminRolePerms> roles;

	public List<AdminPermModule> getModules() {
		return modules;
	}

	public void setModules(List<AdminPermModule> modules) {
		this.modules = modules;
	}

	public List<AdminRolePerms> getRoles() {
		return roles;
	}

	public void setRoles(List<AdminRolePerms> roles) {
		this.roles = roles;
	}
}
