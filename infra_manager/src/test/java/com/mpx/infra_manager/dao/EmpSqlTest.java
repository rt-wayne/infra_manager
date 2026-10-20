package com.mpx.infra_manager.dao;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: 新增：同步表片段 EmpSql 的參數守門測試（BACKLOG 第 124 項階段 3 回合 1；階段 2 審查 S2，使用者裁示 A）——
//           別名只收大寫英數底線、不得 PX 開頭；工號運算式只收「別名.欄位」或綁定參數名；誤用片段內部別名（PXE、PXDE、PXD）
//           會讓條件恆真（首次登入資格人人符合），必須在組 SQL 時就丟例外
// ============================================================

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmpSqlTest {

	@ParameterizedTest
	@ValueSource(strings = { "U", "E1", "UA", "ACCT_USER" })
	void 別名_大寫英數底線可用(String alias) {
		assertThatCode(() -> EmpSql.joinEmp(alias, "U.USER_ID")).doesNotThrowAnyException();
		assertThatCode(() -> EmpSql.activeUserCond(alias)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = { "PXE", "PXDE", "PXD", "PX_ANY", "u", "E 1", "E;", "1E", "" })
	void 別名_PX開頭或非大寫英數底線_丟例外(String alias) {
		assertThatThrownBy(() -> EmpSql.joinEmp(alias, "U.USER_ID")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> EmpSql.nameExpr(alias, "U.USER_ID")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> EmpSql.activeUserCond(alias)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 別名_null丟例外() {
		assertThatThrownBy(() -> EmpSql.joinEmp(null, "U.USER_ID")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> EmpSql.inSyncCond(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "U.USER_ID", "R.APPLY_BY", "T.ACT_AS_USER_ID", ":userId", ":targetId" })
	void 工號運算式_別名欄位或綁定參數可用(String expr) {
		assertThatCode(() -> EmpSql.inSyncCond(expr)).doesNotThrowAnyException();
		assertThatCode(() -> EmpSql.primaryDeptExpr(expr)).doesNotThrowAnyException();
		assertThatCode(() -> EmpSql.qualifiedCond(expr)).doesNotThrowAnyException();
		assertThatCode(() -> EmpSql.nameExpr("E", expr)).doesNotThrowAnyException();
	}

	/** 撞到片段內部別名會變成 PXE.EMP_ID = PXE.EMP_ID（恆真）；字面值、函式、小寫、多段路徑也都不收 */
	@ParameterizedTest
	@ValueSource(strings = { "PXE.EMP_ID", "PXDE.EMP_ID", "PXD.DEPT_ID", "'0000011'", "USER_ID", "u.user_id", "NVL(U.USER_ID, 'x')",
			"U.USER_ID OR 1=1", "S.U.USER_ID", ":1", "" })
	void 工號運算式_其餘丟例外(String expr) {
		assertThatThrownBy(() -> EmpSql.inSyncCond(expr)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> EmpSql.primaryDeptExpr(expr)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> EmpSql.qualifiedCond(expr)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> EmpSql.joinEmp("E", expr)).isInstanceOf(IllegalArgumentException.class);
	}
}
