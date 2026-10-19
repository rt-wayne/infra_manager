package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：GET /admin/executors 回應的一個帳號類別（BACKLOG 第 89 項階段 2）：類別資料、是否開放新申請、啟用中的執行人對照
// ============================================================

import java.util.List;

public class AdminExecCatg {

	private String catgId;
	private String catgName;
	/** IS_ENABLED：是否開放新申請；關閉的類別仍可能有在途單，照樣列出與維護 */
	private Boolean enabled;
	private Integer sortSeq;
	private List<AdminMember> members;

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

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	public Integer getSortSeq() {
		return sortSeq;
	}

	public void setSortSeq(Integer sortSeq) {
		this.sortSeq = sortSeq;
	}

	public List<AdminMember> getMembers() {
		return members;
	}

	public void setMembers(List<AdminMember> members) {
		this.members = members;
	}
}
