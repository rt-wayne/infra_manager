package com.mpx.infra_manager.model.request;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: 新增：定期審查駁回表單（BACKLOG 第 158 項 R3b）：half＝INFRA／GOV（以哪一半的身分駁回）、reason＝駁回原因（必填、500 字）、
//           versionNo＝進行中那一輪的版本（還沒有進行中一輪時帶 0，同簽核的裁示 ①B）
// ============================================================

public class ReviewRejectForm {

	private String half;
	private String reason;
	private Integer versionNo;

	public String getHalf() {
		return half;
	}

	public void setHalf(String half) {
		this.half = half;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public Integer getVersionNo() {
		return versionNo;
	}

	public void setVersionNo(Integer versionNo) {
		this.versionNo = versionNo;
	}
}
