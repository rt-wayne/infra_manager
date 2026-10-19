package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：只取 STATUS 欄的單筆查詢結果（BACKLOG 第 89 項；名單列現況：查無列、0、1 三種）
// ============================================================

public class StatusRow {

	private Integer status;

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}
}
