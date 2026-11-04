package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /home/stats 首頁卡片（spec §4.1、§3.7）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S8：加 emgUndecided（緊急存取待補核准，含逾期）、emgReturned（異常待追蹤）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-29
// 變更說明: BACKLOG 第 148 項：加三個範圍旗標讓前端決定標題與區塊——acctAll（帳號四張看全部還是只看我申請的）、
//           emgAll（緊急存取兩張看全部還是只看我申請的）、inspVisible（看不到巡檢區塊時兩個巡檢數字為 null）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: BACKLOG 第 158 項 R8：加 reviewOverdue（逾期未定期審查）、reviewAbnormal（審查異常待處理）與旗標 reviewAll（有 REVIEW_LIST 看全部）
// ============================================================

public class HomeStats {

	private Long acctTotal;
	private Long acctReviewing;
	private Long acctExpiring;
	private Long acctPriv;
	private Long inspThisMonth;
	private Long inspExcptThisMonth;
	private Long emgUndecided;
	private Long emgReturned;
	private Long reviewOverdue;
	private Long reviewAbnormal;
	private boolean acctAll;
	private boolean emgAll;
	private boolean inspVisible;
	private boolean reviewAll;

	public Long getReviewOverdue() {
		return reviewOverdue;
	}

	public void setReviewOverdue(Long reviewOverdue) {
		this.reviewOverdue = reviewOverdue;
	}

	public Long getReviewAbnormal() {
		return reviewAbnormal;
	}

	public void setReviewAbnormal(Long reviewAbnormal) {
		this.reviewAbnormal = reviewAbnormal;
	}

	public boolean isReviewAll() {
		return reviewAll;
	}

	public void setReviewAll(boolean reviewAll) {
		this.reviewAll = reviewAll;
	}

	public boolean isAcctAll() {
		return acctAll;
	}

	public void setAcctAll(boolean acctAll) {
		this.acctAll = acctAll;
	}

	public boolean isEmgAll() {
		return emgAll;
	}

	public void setEmgAll(boolean emgAll) {
		this.emgAll = emgAll;
	}

	public boolean isInspVisible() {
		return inspVisible;
	}

	public void setInspVisible(boolean inspVisible) {
		this.inspVisible = inspVisible;
	}

	public Long getAcctTotal() {
		return acctTotal;
	}

	public void setAcctTotal(Long acctTotal) {
		this.acctTotal = acctTotal;
	}

	public Long getAcctReviewing() {
		return acctReviewing;
	}

	public void setAcctReviewing(Long acctReviewing) {
		this.acctReviewing = acctReviewing;
	}

	public Long getAcctExpiring() {
		return acctExpiring;
	}

	public void setAcctExpiring(Long acctExpiring) {
		this.acctExpiring = acctExpiring;
	}

	public Long getAcctPriv() {
		return acctPriv;
	}

	public void setAcctPriv(Long acctPriv) {
		this.acctPriv = acctPriv;
	}

	public Long getInspThisMonth() {
		return inspThisMonth;
	}

	public void setInspThisMonth(Long inspThisMonth) {
		this.inspThisMonth = inspThisMonth;
	}

	public Long getInspExcptThisMonth() {
		return inspExcptThisMonth;
	}

	public void setInspExcptThisMonth(Long inspExcptThisMonth) {
		this.inspExcptThisMonth = inspExcptThisMonth;
	}

	public Long getEmgUndecided() {
		return emgUndecided;
	}

	public void setEmgUndecided(Long emgUndecided) {
		this.emgUndecided = emgUndecided;
	}

	public Long getEmgReturned() {
		return emgReturned;
	}

	public void setEmgReturned(Long emgReturned) {
		this.emgReturned = emgReturned;
	}
}
