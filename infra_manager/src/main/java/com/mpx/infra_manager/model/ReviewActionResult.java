package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: 新增：定期審查簽核／駁回的回應（BACKLOG 第 158 項 R3a）：帳號單號、本輪流水號、本輪狀態（OPEN＝還差另一半、
//           DONE＝雙簽完成）、動作後的版本號（前端下一次動作要帶的值）
// ============================================================

public class ReviewActionResult {

	private String acctId;
	private Long reviewId;
	private String reviewStateCode;
	private Integer versionNo;

	public String getAcctId() {
		return acctId;
	}

	public void setAcctId(String acctId) {
		this.acctId = acctId;
	}

	public Long getReviewId() {
		return reviewId;
	}

	public void setReviewId(Long reviewId) {
		this.reviewId = reviewId;
	}

	public String getReviewStateCode() {
		return reviewStateCode;
	}

	public void setReviewStateCode(String reviewStateCode) {
		this.reviewStateCode = reviewStateCode;
	}

	public Integer getVersionNo() {
		return versionNo;
	}

	public void setVersionNo(Integer versionNo) {
		this.versionNo = versionNo;
	}
}
