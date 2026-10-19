package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：IM_ACCT_PERM 的一列（BACKLOG 第 89 項階段 3；角色權限設定頁的可見權限碼清單，依 SORT_SEQ 排序、由 Service 依 MODULE_CODE 分組）
// ============================================================

public class PermRow {

	private String permId;
	private String permName;
	private String moduleCode;
	private Integer sortSeq;

	public String getPermId() {
		return permId;
	}

	public void setPermId(String permId) {
		this.permId = permId;
	}

	public String getPermName() {
		return permName;
	}

	public void setPermName(String permName) {
		this.permName = permName;
	}

	public String getModuleCode() {
		return moduleCode;
	}

	public void setModuleCode(String moduleCode) {
		this.moduleCode = moduleCode;
	}

	public Integer getSortSeq() {
		return sortSeq;
	}

	public void setSortSeq(Integer sortSeq) {
		this.sortSeq = sortSeq;
	}
}
