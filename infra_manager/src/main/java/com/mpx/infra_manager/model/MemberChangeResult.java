package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：名單指派／移除 API 的回應（BACKLOG 第 89 項）：roleId 或 catgId 擇一有值、對象工號、result＝GRANTED／REVOKED
// ============================================================

public class MemberChangeResult {

	public static final String GRANTED = "GRANTED";
	public static final String REVOKED = "REVOKED";

	private String roleId;
	private String catgId;
	private String userId;
	private String result;

	public String getRoleId() {
		return roleId;
	}

	public void setRoleId(String roleId) {
		this.roleId = roleId;
	}

	public String getCatgId() {
		return catgId;
	}

	public void setCatgId(String catgId) {
		this.catgId = catgId;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}
}
