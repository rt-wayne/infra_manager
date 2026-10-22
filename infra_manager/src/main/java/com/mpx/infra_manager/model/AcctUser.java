package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：使用者 IM_ACCT_USER 一列（spec v2.0 §2.1；Table v0.2 7.1）；只在後端使用，pwdHash 不輸出
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: BACKLOG 第 124 項階段 3 回合 1：刪 userEnName（第 67 項併入）；userName、email 改由同步表 PX_SYNC_EMP 帶出
//           （userName 查無時 SQL 已退回工號）；加 inSync（同步表查得到＝1，裁示 E1／E3）。deptName、telNo 仍讀 IM_ACCT_USER（階段 5、6 改）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 5 回合 2：刪 deptName（不再讀 IM_ACCT_USER.DEPT_NAME，部門改由 /me 查同步表）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 6（裁示 ⑫A）：刪 telNo（不再讀 IM_ACCT_USER.TEL_NO，申請人分機改由使用者自填）
// ============================================================

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class AcctUser {

	private String userId;
	private String userName;
	private String email;
	@JsonIgnore
	private String pwdHash;
	private Integer loginFailCnt;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime lockDate;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime lastLoginDate;
	private Integer status;
	/** 同步表 PX_SYNC_EMP 查得到（STATUS＝1）＝1，查無＝0 */
	private Integer inSync;

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public Integer getInSync() {
		return inSync;
	}

	public void setInSync(Integer inSync) {
		this.inSync = inSync;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPwdHash() {
		return pwdHash;
	}

	public void setPwdHash(String pwdHash) {
		this.pwdHash = pwdHash;
	}

	public Integer getLoginFailCnt() {
		return loginFailCnt;
	}

	public void setLoginFailCnt(Integer loginFailCnt) {
		this.loginFailCnt = loginFailCnt;
	}

	public LocalDateTime getLockDate() {
		return lockDate;
	}

	public void setLockDate(LocalDateTime lockDate) {
		this.lockDate = lockDate;
	}

	public LocalDateTime getLastLoginDate() {
		return lastLoginDate;
	}

	public void setLastLoginDate(LocalDateTime lastLoginDate) {
		this.lastLoginDate = lastLoginDate;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}
}
