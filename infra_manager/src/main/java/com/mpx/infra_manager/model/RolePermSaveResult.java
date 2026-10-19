package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：POST /admin/roles/{roleId}/perms 的回應（BACKLOG 第 89 項階段 3）：roleId、實際加入與拿掉的碼、result＝SAVED
// ============================================================

import java.util.List;

public class RolePermSaveResult {

	public static final String SAVED = "SAVED";

	private String roleId;
	private List<String> added;
	private List<String> removed;
	private String result;

	public String getRoleId() {
		return roleId;
	}

	public void setRoleId(String roleId) {
		this.roleId = roleId;
	}

	public List<String> getAdded() {
		return added;
	}

	public void setAdded(List<String> added) {
		this.added = added;
	}

	public List<String> getRemoved() {
		return removed;
	}

	public void setRemoved(List<String> removed) {
		this.removed = removed;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}
}
