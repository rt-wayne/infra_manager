package com.mpx.infra_manager.model.request;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：名單指派的 body {userId, reason}（BACKLOG 第 89 項；移除沿用 ReasonForm）
// ============================================================

public class MemberForm {

	private String userId;
	private String reason;

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}
}
