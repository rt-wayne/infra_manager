package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：在途案件一列（BACKLOG 第 89 項）：單號（申請單 AR／緊急存取 EA）與目前停在哪一關的名稱，
//           供 E23「移除後沒有人能處理」的訊息「單號（關卡名）」與 DENY 軌跡
// ============================================================

public class InFlightRow {

	private String docId;
	private String stageName;

	public InFlightRow() {
	}

	public InFlightRow(String docId, String stageName) {
		this.docId = docId;
		this.stageName = stageName;
	}

	public String getDocId() {
		return docId;
	}

	public void setDocId(String docId) {
		this.docId = docId;
	}

	public String getStageName() {
		return stageName;
	}

	public void setStageName(String stageName) {
		this.stageName = stageName;
	}
}
