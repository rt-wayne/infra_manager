package com.mpx.infra_manager.model.request;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：角色權限存檔的 body {added[], removed[], reason}（BACKLOG 第 89 項階段 3；裁示 ⑯A 只送差異、不加版本欄）
// ============================================================

import java.util.List;

public class RolePermForm {

	private List<String> added;
	private List<String> removed;
	private String reason;

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

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}
}
