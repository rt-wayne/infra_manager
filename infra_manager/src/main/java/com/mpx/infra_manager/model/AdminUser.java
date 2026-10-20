package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：GET /admin/users 回應的指派候選人（BACKLOG 第 89 項）：工號、顯示名、部門
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: BACKLOG 第 124 項階段 3 回合 2（裁示 ⑬-2）：刪 deptName——指派下拉只顯示名字，不再算部門
// ============================================================

public class AdminUser {

	private String userId;
	private String userName;

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}
}
