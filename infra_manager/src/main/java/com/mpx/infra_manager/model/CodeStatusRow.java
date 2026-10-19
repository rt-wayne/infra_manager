package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：代碼＋STATUS 的一列（別名 CODE、STATUS；BACKLOG 第 89 項階段 3）——某角色在 IM_ACCT_ROLE_PERM 的全部列（含 STATUS＝0），
//           權限存檔時逐格核對現況（E27）並決定 UPDATE 或 INSERT
// ============================================================

public class CodeStatusRow {

	private String code;
	private Integer status;

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}
}
