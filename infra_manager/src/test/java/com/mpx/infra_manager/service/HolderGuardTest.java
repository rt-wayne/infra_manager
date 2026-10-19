package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：「最後一位持有人」檢查單元測試（BACKLOG 第 89 項階段 1；SA 4.7、裁示 ①B／②B／③A）
//           三條件擋下：變更前 ≥ 1、變更後＝0、有在途才擋；變更後仍有人不擋；變更前就是 0（移除已停用者、沒有成員的角色）不擋；
//           SYS_ROLE 歸零不看在途；EMG_DECIDE 的在途含緊急存取；沒有卡單概念的碼不擋；
//           單號在軌跡最多 50 筆、訊息最多 20 筆並加「等共 N 件」；兩種排除方式傳給 DAO 的參數
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.dao.RoleAdminDao;
import com.mpx.infra_manager.model.InFlightRow;
import com.mpx.infra_manager.service.HolderGuard.Exclusion;
import com.mpx.infra_manager.service.HolderGuard.Result;

class HolderGuardTest {

	RoleAdminDao dao;
	HolderGuard guard;

	@BeforeEach
	void setUp() {
		dao = mock(RoleAdminDao.class);
		guard = new HolderGuard(dao);
		when(dao.findInFlightByStagePerm(anyString())).thenReturn(List.of());
		when(dao.findInFlightEmg()).thenReturn(List.of());
	}

	/** before：不排除的人數；after：排除後的人數 */
	private void holders(String permId, long before, long after) {
		when(dao.countHolders(eq(permId), isNull(), isNull(), isNull())).thenReturn(before);
		when(dao.countHolders(eq(permId), eq("R"), any(), any())).thenReturn(after);
	}

	private static List<InFlightRow> docs(String prefix, int n, String stage) {
		List<InFlightRow> list = new ArrayList<>();
		for (int i = 1; i <= n; i++) {
			list.add(new InFlightRow(prefix + String.format("%04d", i), stage));
		}
		return list;
	}

	@Test
	void 變更後無人持有且有在途_擋下並列單號() {
		holders("APPR_G5", 1, 0);
		when(dao.findInFlightByStagePerm("APPR_G5")).thenReturn(docs("AR", 3, "資訊主管"));
		Result r = guard.check(List.of("APPR_G5"), Exclusion.member("R", "U1"));
		assertThat(r.ok()).isFalse();
		assertThat(r.sysRoleGone()).isFalse();
		assertThat(r.blockedPermIds()).containsExactly("APPR_G5");
		assertThat(r.blockedTotal()).isEqualTo(3);
		assertThat(r.blockedDocIds()).containsExactly("AR0001", "AR0002", "AR0003");
		assertThat(HolderGuard.describe(r)).isEqualTo("AR0001（資訊主管）、AR0002（資訊主管）、AR0003（資訊主管）");
	}

	@Test
	void 變更後仍有人持有_不擋_不查在途() {
		holders("APPR_G5", 2, 1);
		assertThat(guard.check(List.of("APPR_G5"), Exclusion.member("R", "U1")).ok()).isTrue();
		verify(dao, never()).findInFlightByStagePerm(any());
		// 變更後有人就不必查變更前
		verify(dao, never()).countHolders("APPR_G5", null, null, null);
	}

	@Test
	void 變更前就無人持有_移除已停用者或無成員角色的碼_不擋() {
		holders("APPR_G5", 0, 0);
		when(dao.findInFlightByStagePerm("APPR_G5")).thenReturn(docs("AR", 2, "資訊主管"));
		assertThat(guard.check(List.of("APPR_G5"), Exclusion.member("R", "U1")).ok()).isTrue();
		holders("SYS_ROLE", 0, 0);
		assertThat(guard.check(List.of("SYS_ROLE"), Exclusion.rolePerm("R", "SYS_ROLE")).sysRoleGone()).isFalse();
	}

	@Test
	void 變更後無人持有但沒有在途_不擋() {
		holders("APPR_G3", 1, 0);
		holders("ACC_LIST_FULL", 1, 0);
		Result r = guard.check(List.of("APPR_G3", "ACC_LIST_FULL"), Exclusion.member("R", "U1"));
		assertThat(r.ok()).isTrue();
		assertThat(r.blocked()).isEmpty();
	}

	@Test
	void SYS_ROLE歸零_不論在途一律標記_其他碼照算() {
		holders("SYS_ROLE", 1, 0);
		holders("SYS_IMPERSONATE", 1, 0);
		Result r = guard.check(List.of("SYS_ROLE", "SYS_IMPERSONATE"), Exclusion.member("R", "U1"));
		assertThat(r.sysRoleGone()).isTrue();
		assertThat(r.ok()).isFalse();
		assertThat(r.blocked()).isEmpty();
		verify(dao, never()).findInFlightByStagePerm("SYS_ROLE");
	}

	@Test
	void EMG_DECIDE_在途含緊急存取_其他碼不查緊急存取() {
		holders("EMG_DECIDE", 1, 0);
		holders("APPR_G4", 1, 0);
		when(dao.findInFlightEmg()).thenReturn(List.of(new InFlightRow("EA0001", "待補核准"), new InFlightRow("EA0002", "已退回待結案")));
		when(dao.findInFlightByStagePerm("APPR_G4")).thenReturn(docs("AR", 1, "資訊治理"));
		Result r = guard.check(List.of("APPR_G4", "EMG_DECIDE"), Exclusion.member("R", "U1"));
		assertThat(r.blockedPermIds()).containsExactly("APPR_G4", "EMG_DECIDE");
		assertThat(r.blockedTotal()).isEqualTo(3);
		assertThat(HolderGuard.describe(r)).isEqualTo("AR0001（資訊治理）、EA0001（待補核准）、EA0002（已退回待結案）");
		verify(dao).findInFlightEmg();
	}

	@Test
	void 單號上限_軌跡50筆_訊息20筆加等共N件() {
		holders("APPR_G2", 1, 0);
		when(dao.findInFlightByStagePerm("APPR_G2")).thenReturn(docs("AR", 73, "機房管理員"));
		Result r = guard.check(List.of("APPR_G2"), Exclusion.member("R", "U1"));
		assertThat(r.blocked().get(0).docs()).hasSize(AcctConst.ADMIN_BLOCKED_LOG_MAX);
		assertThat(r.blockedTotal()).isEqualTo(73);
		String text = HolderGuard.describe(r);
		assertThat(text).startsWith("AR0001（機房管理員）、").contains("AR0020（機房管理員）").doesNotContain("AR0021")
				.endsWith("（等共 73 件）");
		assertThat(text.split("、")).hasSize(AcctConst.ADMIN_BLOCKED_MSG_MAX);
	}

	@Test
	void 排除方式_成員指派與角色權限列傳給DAO的參數不同() {
		when(dao.countHolders(any(), any(), any(), any())).thenReturn(1L);
		guard.check(List.of("APPR_G5"), Exclusion.member("GOV", "U1"));
		verify(dao).countHolders("APPR_G5", "GOV", "U1", null);
		guard.check(List.of("VERIFY_G7"), Exclusion.rolePerm("GOV", "VERIFY_G7"));
		verify(dao).countHolders("VERIFY_G7", "GOV", null, "VERIFY_G7");
	}

	@Test
	void 沒有碼要檢查_直接通過() {
		Result r = guard.check(List.of(), Exclusion.member("R", "U1"));
		assertThat(r.ok()).isTrue();
		assertThat(HolderGuard.describe(r)).isEmpty();
		verify(dao, never()).countHolders(any(), any(), any(), any());
	}
}
