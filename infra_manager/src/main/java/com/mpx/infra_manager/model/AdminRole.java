package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：GET /admin/roles 回應的一個角色（BACKLOG 第 89 項）：角色資料、授予的權限中文名、啟用中的成員指派
// ============================================================

import java.util.List;

public class AdminRole {

	private String roleId;
	private String roleName;
	private String roleDescr;
	private Integer sortSeq;
	private List<String> permNames;
	private List<AdminMember> members;

	public String getRoleId() {
		return roleId;
	}

	public void setRoleId(String roleId) {
		this.roleId = roleId;
	}

	public String getRoleName() {
		return roleName;
	}

	public void setRoleName(String roleName) {
		this.roleName = roleName;
	}

	public String getRoleDescr() {
		return roleDescr;
	}

	public void setRoleDescr(String roleDescr) {
		this.roleDescr = roleDescr;
	}

	public Integer getSortSeq() {
		return sortSeq;
	}

	public void setSortSeq(Integer sortSeq) {
		this.sortSeq = sortSeq;
	}

	public List<String> getPermNames() {
		return permNames;
	}

	public void setPermNames(List<String> permNames) {
		this.permNames = permNames;
	}

	public List<AdminMember> getMembers() {
		return members;
	}

	public void setMembers(List<AdminMember> members) {
		this.members = members;
	}
}
