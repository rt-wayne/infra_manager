package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /todos 待辦一筆；actionKind＝APPROVE／EXEC／VERIFY（spec §4.1）
//           變更單 #1：補 catgId、isPriv
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.3）：加 source（固定 ACCT_REQ）、title、applyBy、versionNo、verifyRejectCnt、verifyRejectReason
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: BACKLOG 第 158 項 R6（待辦整合）：第二種來源 REVIEW（定期審查）——source＝REVIEW、actionKind＝REVIEW、reqId 填帳號單號（前端列鍵用來源＋編號＋身分），
//           加 acctId、reviewId、half（我可簽的身分 INFRA／GOV）、review（整筆 ReviewItem，前端直接開簽核彈窗；裁示 ①A）；申請單列這四欄為 null
// ============================================================

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public class TodoItem {

	/** 來源：ACCT_REQ 申請單／REVIEW 定期審查 */
	private String source;
	/** 編號：申請單號；審查列填帳號單號（與該帳號的新增單號相同，所以前端列鍵要加來源） */
	private String reqId;
	private String acctId;
	private Long reviewId;
	/** 審查列：這一列要以哪個身分簽（INFRA／GOV） */
	private String half;
	/** 審查列：進行中那一輪的完整資料，前端直接開 ReviewSignModal */
	private ReviewItem review;
	private String title;
	private String reqTypeCode;
	private String catgId;
	private String catgName;
	private String acctName;
	private Integer isPriv;
	private String applyBy;
	private String applyByName;
	private Integer stageId;
	private String stageName;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime submitDate;
	private String actionKind;
	private Integer versionNo;
	private Integer verifyRejectCnt;
	private String verifyRejectReason;

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getReqId() {
		return reqId;
	}

	public void setReqId(String reqId) {
		this.reqId = reqId;
	}

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

	public String getHalf() {
		return half;
	}

	public void setHalf(String half) {
		this.half = half;
	}

	public ReviewItem getReview() {
		return review;
	}

	public void setReview(ReviewItem review) {
		this.review = review;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getReqTypeCode() {
		return reqTypeCode;
	}

	public void setReqTypeCode(String reqTypeCode) {
		this.reqTypeCode = reqTypeCode;
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

	public Integer getIsPriv() {
		return isPriv;
	}

	public void setIsPriv(Integer isPriv) {
		this.isPriv = isPriv;
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

	public Integer getStageId() {
		return stageId;
	}

	public void setStageId(Integer stageId) {
		this.stageId = stageId;
	}

	public String getStageName() {
		return stageName;
	}

	public void setStageName(String stageName) {
		this.stageName = stageName;
	}

	public LocalDateTime getSubmitDate() {
		return submitDate;
	}

	public void setSubmitDate(LocalDateTime submitDate) {
		this.submitDate = submitDate;
	}

	public String getActionKind() {
		return actionKind;
	}

	public void setActionKind(String actionKind) {
		this.actionKind = actionKind;
	}

	public Integer getVersionNo() {
		return versionNo;
	}

	public void setVersionNo(Integer versionNo) {
		this.versionNo = versionNo;
	}

	public Integer getVerifyRejectCnt() {
		return verifyRejectCnt;
	}

	public void setVerifyRejectCnt(Integer verifyRejectCnt) {
		this.verifyRejectCnt = verifyRejectCnt;
	}

	public String getVerifyRejectReason() {
		return verifyRejectReason;
	}

	public void setVerifyRejectReason(String verifyRejectReason) {
		this.verifyRejectReason = verifyRejectReason;
	}
}
