package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：GET /admin/roles、/admin/executors 回應的名單成員（BACKLOG 第 89 項）：工號、顯示名、對象是否啟用、最近一次指派時間與指派人
// ============================================================

import java.time.LocalDateTime;

public class AdminMember {

	private String userId;
	private String userName;
	private Boolean userActive;
	private LocalDateTime grantDate;
	private String grantBy;

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

	public Boolean getUserActive() {
		return userActive;
	}

	public void setUserActive(Boolean userActive) {
		this.userActive = userActive;
	}

	public LocalDateTime getGrantDate() {
		return grantDate;
	}

	public void setGrantDate(LocalDateTime grantDate) {
		this.grantDate = grantDate;
	}

	public String getGrantBy() {
		return grantBy;
	}

	public void setGrantBy(String grantBy) {
		this.grantBy = grantBy;
	}
}
