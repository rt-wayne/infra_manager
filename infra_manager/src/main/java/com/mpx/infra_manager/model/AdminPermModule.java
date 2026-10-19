package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：GET /admin/role-perms 回應 modules[] 的一組（BACKLOG 第 89 項階段 3）：IM_ACCT_PERM.MODULE_CODE 與該模組的可見權限碼，
//           順序依碼的 SORT_SEQ 首次出現；中文組名與「簽核」拆組由前端決定
// ============================================================

import java.util.List;

public class AdminPermModule {

	private String moduleCode;
	private List<AdminPerm> perms;

	public String getModuleCode() {
		return moduleCode;
	}

	public void setModuleCode(String moduleCode) {
		this.moduleCode = moduleCode;
	}

	public List<AdminPerm> getPerms() {
		return perms;
	}

	public void setPerms(List<AdminPerm> perms) {
		this.perms = perms;
	}
}
