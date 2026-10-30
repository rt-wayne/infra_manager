package com.mpx.infra_manager.model.request;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: 新增：定期審查簽核表單（BACKLOG 第 158 項 R3a）：half＝INFRA／GOV（簽哪一半）、versionNo＝進行中那一輪的版本
//           （還沒有進行中一輪時帶 0，裁示 ①B）
// ============================================================

public class ReviewSignForm {

	private String half;
	private Integer versionNo;

	public String getHalf() {
		return half;
	}

	public void setHalf(String half) {
		this.half = half;
	}

	public Integer getVersionNo() {
		return versionNo;
	}

	public void setVersionNo(Integer versionNo) {
		this.versionNo = versionNo;
	}
}
