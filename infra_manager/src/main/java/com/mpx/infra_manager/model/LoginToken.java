package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：登入 token 列 IM_ACCT_LOGIN_TOKEN＋實際使用者與代理身分的 STATUS（spec v2.0 §2.1、§2.5）；只在後端使用
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: BACKLOG 第 124 項階段 3 回合 1（裁示 D0B、E1）：加 userInSync、actAsInSync——實際使用者與代理身分在同步表 PX_SYNC_EMP 查得到＝1
// ============================================================

import java.time.LocalDateTime;

public class LoginToken {

	private String tokenHash;
	private String userId;
	private String actAsUserId;
	private LocalDateTime expireDate;
	private LocalDateTime lastAccessDate;
	private LocalDateTime logoutDate;
	private Integer userStatus;
	private Integer actAsStatus;
	/** 實際使用者在同步表查得到＝1（查無則 token 無效，裁示 E1） */
	private Integer userInSync;
	/** 代理身分在同步表查得到＝1；代理身分為空時為 0 */
	private Integer actAsInSync;

	public Integer getUserInSync() {
		return userInSync;
	}

	public void setUserInSync(Integer userInSync) {
		this.userInSync = userInSync;
	}

	public Integer getActAsInSync() {
		return actAsInSync;
	}

	public void setActAsInSync(Integer actAsInSync) {
		this.actAsInSync = actAsInSync;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public void setTokenHash(String tokenHash) {
		this.tokenHash = tokenHash;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getActAsUserId() {
		return actAsUserId;
	}

	public void setActAsUserId(String actAsUserId) {
		this.actAsUserId = actAsUserId;
	}

	public LocalDateTime getExpireDate() {
		return expireDate;
	}

	public void setExpireDate(LocalDateTime expireDate) {
		this.expireDate = expireDate;
	}

	public LocalDateTime getLastAccessDate() {
		return lastAccessDate;
	}

	public void setLastAccessDate(LocalDateTime lastAccessDate) {
		this.lastAccessDate = lastAccessDate;
	}

	public LocalDateTime getLogoutDate() {
		return logoutDate;
	}

	public void setLogoutDate(LocalDateTime logoutDate) {
		this.logoutDate = logoutDate;
	}

	public Integer getUserStatus() {
		return userStatus;
	}

	public void setUserStatus(Integer userStatus) {
		this.userStatus = userStatus;
	}

	public Integer getActAsStatus() {
		return actAsStatus;
	}

	public void setActAsStatus(Integer actAsStatus) {
		this.actAsStatus = actAsStatus;
	}
}
