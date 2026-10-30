package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: 新增：定期審查清單的一列（BACKLOG 第 158 項 R2）：帳號主表要顯示的欄位、類別名與分類名、原申請人姓名、
//           即時計算的 reviewStateCode（ABNORMAL／OVERDUE／DUE／NORMAL，由 ReviewDao.REVIEW_STATE_EXPR 算出，不存欄位），
//           以及進行中那一輪（IM_ACCT_REVIEW OPEN）的 reviewId、版本與兩半簽核人（尚無進行中輪時皆為 null）
// ============================================================

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public class ReviewItem {

	private String acctId;
	private String catgId;
	private String catgName;
	private String acctName;
	private String sysName;
	private String assetClassId;
	private String assetClassName;
	private Integer isPriv;
	private String holderName;
	private String holderDept;
	private String accessLevelCode;
	private String acctStateCode;
	private String applyBy;
	private String applyByName;
	@JsonFormat(pattern = "yyyy-MM-dd")
	private LocalDateTime lastReviewDate;
	@JsonFormat(pattern = "yyyy-MM-dd")
	private LocalDateTime nextReviewDate;
	private Integer isReviewFlag;
	private String reviewFlagReason;
	private String reviewFlagSourceCode;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime reviewFlagDate;
	/** 審查狀態（即時計算）：ABNORMAL 審查異常、OVERDUE 逾期未審、DUE 待複審、NORMAL 正常；清單不會回不在範圍（null）的帳號 */
	private String reviewStateCode;
	/** 進行中那一輪的流水號；尚未有人簽或駁回時為 null */
	private Long reviewId;
	private Integer reviewVersionNo;
	private String infraSignBy;
	private String infraSignByName;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime infraSignDate;
	private String govSignBy;
	private String govSignByName;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime govSignDate;

	public String getAcctId() {
		return acctId;
	}

	public void setAcctId(String acctId) {
		this.acctId = acctId;
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

	public String getAcctName() {
		return acctName;
	}

	public void setAcctName(String acctName) {
		this.acctName = acctName;
	}

	public String getSysName() {
		return sysName;
	}

	public void setSysName(String sysName) {
		this.sysName = sysName;
	}

	public String getAssetClassId() {
		return assetClassId;
	}

	public void setAssetClassId(String assetClassId) {
		this.assetClassId = assetClassId;
	}

	public String getAssetClassName() {
		return assetClassName;
	}

	public void setAssetClassName(String assetClassName) {
		this.assetClassName = assetClassName;
	}

	public Integer getIsPriv() {
		return isPriv;
	}

	public void setIsPriv(Integer isPriv) {
		this.isPriv = isPriv;
	}

	public String getHolderName() {
		return holderName;
	}

	public void setHolderName(String holderName) {
		this.holderName = holderName;
	}

	public String getHolderDept() {
		return holderDept;
	}

	public void setHolderDept(String holderDept) {
		this.holderDept = holderDept;
	}

	public String getAccessLevelCode() {
		return accessLevelCode;
	}

	public void setAccessLevelCode(String accessLevelCode) {
		this.accessLevelCode = accessLevelCode;
	}

	public String getAcctStateCode() {
		return acctStateCode;
	}

	public void setAcctStateCode(String acctStateCode) {
		this.acctStateCode = acctStateCode;
	}

	public String getApplyBy() {
		return applyBy;
	}

	public void setApplyBy(String applyBy) {
		this.applyBy = applyBy;
	}

	public String getApplyByName() {
		return applyByName;
	}

	public void setApplyByName(String applyByName) {
		this.applyByName = applyByName;
	}

	public LocalDateTime getLastReviewDate() {
		return lastReviewDate;
	}

	public void setLastReviewDate(LocalDateTime lastReviewDate) {
		this.lastReviewDate = lastReviewDate;
	}

	public LocalDateTime getNextReviewDate() {
		return nextReviewDate;
	}

	public void setNextReviewDate(LocalDateTime nextReviewDate) {
		this.nextReviewDate = nextReviewDate;
	}

	public Integer getIsReviewFlag() {
		return isReviewFlag;
	}

	public void setIsReviewFlag(Integer isReviewFlag) {
		this.isReviewFlag = isReviewFlag;
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

	public LocalDateTime getReviewFlagDate() {
		return reviewFlagDate;
	}

	public void setReviewFlagDate(LocalDateTime reviewFlagDate) {
		this.reviewFlagDate = reviewFlagDate;
	}

	public String getReviewStateCode() {
		return reviewStateCode;
	}

	public void setReviewStateCode(String reviewStateCode) {
		this.reviewStateCode = reviewStateCode;
	}

	public Long getReviewId() {
		return reviewId;
	}

	public void setReviewId(Long reviewId) {
		this.reviewId = reviewId;
	}

	public Integer getReviewVersionNo() {
		return reviewVersionNo;
	}

	public void setReviewVersionNo(Integer reviewVersionNo) {
		this.reviewVersionNo = reviewVersionNo;
	}

	public String getInfraSignBy() {
		return infraSignBy;
	}

	public void setInfraSignBy(String infraSignBy) {
		this.infraSignBy = infraSignBy;
	}

	public String getInfraSignByName() {
		return infraSignByName;
	}

	public void setInfraSignByName(String infraSignByName) {
		this.infraSignByName = infraSignByName;
	}

	public LocalDateTime getInfraSignDate() {
		return infraSignDate;
	}

	public void setInfraSignDate(LocalDateTime infraSignDate) {
		this.infraSignDate = infraSignDate;
	}

	public String getGovSignBy() {
		return govSignBy;
	}

	public void setGovSignBy(String govSignBy) {
		this.govSignBy = govSignBy;
	}

	public String getGovSignByName() {
		return govSignByName;
	}

	public void setGovSignByName(String govSignByName) {
		this.govSignByName = govSignByName;
	}

	public LocalDateTime getGovSignDate() {
		return govSignDate;
	}

	public void setGovSignDate(LocalDateTime govSignDate) {
		this.govSignDate = govSignDate;
	}
}
