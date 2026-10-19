package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：GET /admin/role-perms 回應 roles[] 的一個角色（BACKLOG 第 89 項階段 3）：readOnly（GENERAL 為 true，裁示 ⑪-1A）、
//           啟用中成員數（GENERAL＝啟用中使用者總數）、已授予的可見碼、已授予但隱藏的碼數（畫面註明「另有 N 個尚未上線的權限碼未顯示」）
// ============================================================

import java.util.List;

public class AdminRolePerms {

	private String roleId;
	private String roleName;
	private String roleDescr;
	private boolean readOnly;
	private long memberCount;
	private List<String> grantedPermIds;
	private int hiddenGrantedCount;

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

	public boolean isReadOnly() {
		return readOnly;
	}

	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}

	public long getMemberCount() {
		return memberCount;
	}

	public void setMemberCount(long memberCount) {
		this.memberCount = memberCount;
	}

	public List<String> getGrantedPermIds() {
		return grantedPermIds;
	}

	public void setGrantedPermIds(List<String> grantedPermIds) {
		this.grantedPermIds = grantedPermIds;
	}

	public int getHiddenGrantedCount() {
		return hiddenGrantedCount;
	}

	public void setHiddenGrantedCount(int hiddenGrantedCount) {
		this.hiddenGrantedCount = hiddenGrantedCount;
	}
}
