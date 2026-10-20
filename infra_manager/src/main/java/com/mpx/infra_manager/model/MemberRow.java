package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：名單成員一列（BACKLOG 第 89 項）。GROUP_ID 為 IM_ACCT_ROLE_USER.ROLE_ID 或 IM_EXEC_MAP.CATG_ID；
//           LEFT JOIN IM_ACCT_USER 取顯示名與啟用狀態（查無使用者列時 USER_STATUS 為空）；
//           指派時間與指派人取 NVL(UPDATE_*, CREATE_*)——重新指派只寫 UPDATE_*（裁示 ⑦A），啟用中的列其 UPDATE_* 必為最近一次指派
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: BACKLOG 第 124 項階段 3 回合 1：刪 userEnName（第 67 項併入，只顯示中文名）
// ============================================================

import java.time.LocalDateTime;

public class MemberRow {

	private String groupId;
	private String userId;
	private String userName;
	private Integer userStatus;
	private LocalDateTime grantDate;
	private String grantBy;

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public Integer getUserStatus() {
		return userStatus;
	}

	public void setUserStatus(Integer userStatus) {
		this.userStatus = userStatus;
	}

	public LocalDateTime getGrantDate() {
		return grantDate;
	}

	public void setGrantDate(LocalDateTime grantDate) {
		this.grantDate = grantDate;
	}

	public String getGrantBy() {
		return grantBy;
	}

	public void setGrantBy(String grantBy) {
		this.grantBy = grantBy;
	}
}
