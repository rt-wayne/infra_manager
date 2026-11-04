package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: 新增：BACKLOG 第 158 項 R9——GET /home/review-abnormal 鈴鐺類別 2（審查異常未處理）一筆；
//           applyBy（原新增申請人）只給 DAO 過濾與測試用，欄位與 getter 都 @JsonIgnore、不輸出（比照 ExpiringAcct）
// ============================================================

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ReviewAbnormalItem {

	private String acctId;
	private String acctName;
	private String catgId;
	private String catgName;
	/** 審查異常旗標標記日期（REVIEW_FLAG_DATE；舊資料可能為空） */
	@JsonFormat(pattern = "yyyy-MM-dd")
	private LocalDateTime reviewFlagDate;
	private String reviewFlagReason;
	/** 旗標來源（REVIEW 駁回／EMG 緊急存取退回或異常結案） */
	private String reviewFlagSourceCode;
	@JsonIgnore
	private String applyBy;

	public String getAcctId() {
		return acctId;
	}

	public void setAcctId(String acctId) {
		this.acctId = acctId;
	}

	public String getAcctName() {
		return acctName;
	}

	public void setAcctName(String acctName) {
		this.acctName = acctName;
	}

	public String getCatgId() {
		return catgId;
	}

	public void setCatgId(String catgId) {
		this.catgId = catgId;
	}

	public String getCatgName() {
		return catgName;
	}

	public void setCatgName(String catgName) {
		this.catgName = catgName;
	}

	public LocalDateTime getReviewFlagDate() {
		return reviewFlagDate;
	}

	public void setReviewFlagDate(LocalDateTime reviewFlagDate) {
		this.reviewFlagDate = reviewFlagDate;
	}

	public String getReviewFlagReason() {
		return reviewFlagReason;
	}

	public void setReviewFlagReason(String reviewFlagReason) {
		this.reviewFlagReason = reviewFlagReason;
	}

	public String getReviewFlagSourceCode() {
		return reviewFlagSourceCode;
	}

	public void setReviewFlagSourceCode(String reviewFlagSourceCode) {
		this.reviewFlagSourceCode = reviewFlagSourceCode;
	}

	@JsonIgnore
	public String getApplyBy() {
		return applyBy;
	}

	public void setApplyBy(String applyBy) {
		this.applyBy = applyBy;
	}
}
