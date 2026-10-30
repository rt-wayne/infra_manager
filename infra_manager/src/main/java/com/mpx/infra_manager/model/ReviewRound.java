package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: 新增：定期審查「某一輪」（BACKLOG 第 158 項 R3b）：ReviewItem 的帳號與兩半簽核欄之外，再帶該輪的結果狀態與駁回五欄；
//           由 ReviewDao.findRound 以 REVIEW_ID 查（不限進行中），目前供駁回信在交易提交後重讀。清單仍回 ReviewItem，不多出這些欄
// ============================================================

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public class ReviewRound extends ReviewItem {

	/** 該輪結果：OPEN／DONE／REJECTED／CANCELED（ReviewItem.reviewStateCode 是帳號的審查狀態，兩者不同） */
	private String roundStateCode;
	/** 駁回的那一半：INFRA／GOV */
	private String rejectHalfCode;
	private String rejectBy;
	private String rejectByName;
	private String rejectActBy;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime rejectDate;
	private String rejectReason;

	public String getRoundStateCode() {
		return roundStateCode;
	}

	public void setRoundStateCode(String roundStateCode) {
		this.roundStateCode = roundStateCode;
	}

	public String getRejectHalfCode() {
		return rejectHalfCode;
	}

	public void setRejectHalfCode(String rejectHalfCode) {
		this.rejectHalfCode = rejectHalfCode;
	}

	public String getRejectBy() {
		return rejectBy;
	}

	public void setRejectBy(String rejectBy) {
		this.rejectBy = rejectBy;
	}

	public String getRejectByName() {
		return rejectByName;
	}

	public void setRejectByName(String rejectByName) {
		this.rejectByName = rejectByName;
	}

	public String getRejectActBy() {
		return rejectActBy;
	}

	public void setRejectActBy(String rejectActBy) {
		this.rejectActBy = rejectActBy;
	}

	public LocalDateTime getRejectDate() {
		return rejectDate;
	}

	public void setRejectDate(LocalDateTime rejectDate) {
		this.rejectDate = rejectDate;
	}

	public String getRejectReason() {
		return rejectReason;
	}

	public void setRejectReason(String rejectReason) {
		this.rejectReason = rejectReason;
	}
}
