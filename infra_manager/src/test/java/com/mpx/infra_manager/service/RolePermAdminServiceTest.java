package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：角色權限設定單元測試（BACKLOG 第 89 項階段 3；裁示 ⑨A～⑰A）
//           清單：無 SYS_ROLE 403 不寫軌跡；可見碼依模組分組且隱藏碼不出現；GENERAL readOnly 且成員數＝啟用中使用者總數；
//                 角色的 grantedPermIds 只列可見碼、hiddenGrantedCount 算隱藏碼；沒有成員的角色成員數 0
//           存檔 11 步每一個錯誤：① 403＋DENY（只記 roleId 與碼數、TARGET_USER_ID 空、不取鎖）② E25 ③ E10／E11 ④ 角色 GENERAL／停用／不存在 400
//                 ⑤ 兩邊皆空「沒有任何變更」⑥ 重複、同碼兩邊、隱藏碼、不存在碼、空字串 → 400「權限碼」且不取鎖 ⑦ 取鎖逾時 E26 不寫軌跡
//                 ⑧ added 已授予／removed 未授予 → E27 回滾 ⑨ SYS_ROLE 歸零 E24（換文案）＋DENY 回滾、E24 優先於 E23
//                 ⑩ 多碼同時被擋全部列出（依碼分組、每碼最多 20 筆）＋DENY blocked 分組、名單未改
//                 ⑪ removed UPDATE 1→0、added 現況 0 走 UPDATE 0→1、現況無列走 INSERT、主鍵衝突轉 E27、SUCCESS 一筆同交易、回應 SAVED
// ============================================================

import static com.mpx.infra_manager.service.RequestServiceTest.assertStatus;
import static com.mpx.infra_manager.support.TestFixture.HANK;
import static com.mpx.infra_manager.support.TestFixture.KEVIN;
import static com.mpx.infra_manager.support.TestFixture.NOW;
import static com.mpx.infra_manager.support.TestFixture.id;
import static com.mpx.infra_manager.support.TestFixture.impersonate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.AcctMsg;
import com.mpx.infra_manager.model.AdminRolePerms;
import com.mpx.infra_manager.model.InFlightRow;
import com.mpx.infra_manager.model.PermRow;
import com.mpx.infra_manager.model.RolePermMatrix;
import com.mpx.infra_manager.model.RolePermRow;
import com.mpx.infra_manager.model.RolePermSaveResult;
import com.mpx.infra_manager.model.request.RolePermForm;
import com.mpx.infra_manager.support.TestFixture;

class RolePermAdminServiceTest {

	/** 模擬 IM_ACCT_PERM 的可見碼：碼 → {名稱, 模組} */
	static final String[][] PERMS = { { "ACC_APPLY", "送出申請", "ACCT" }, { "ACC_LIST_FULL", "完整帳號列表", "ACCT" },
			{ "APPR_G5", "關卡 5 核准／退件", "ACCT" }, { "VERIFY_G7", "關卡 7 複驗", "ACCT" }, { "INSP_VIEW", "巡檢查詢", "INSP" },
			{ "SYS_ROLE", "角色指派", "SYS" }, { "SYS_IMPERSONATE", "切換身分", "SYS" }, { "EMG_DECIDE", "緊急存取補核准", "EMG" } };

	TestFixture fx;
	RolePermAdminService svc;
	/** 模擬 GOV 在 IM_ACCT_ROLE_PERM 的現況 */
	Map<String, Integer> govStatuses = new HashMap<>();

	@BeforeEach
	void setUp() {
		fx = new TestFixture();
		svc = fx.rolePermAdminService;
		when(fx.roleAdminDao.findRoleById(anyString())).thenAnswer(inv -> RoleAdminServiceTest.role(inv.getArgument(0), 1));
		when(fx.roleAdminDao.findRoleById("NOPE")).thenReturn(null);
		when(fx.roleAdminDao.findRoleById("OLD")).thenReturn(RoleAdminServiceTest.role("OLD", 0));
		when(fx.roleAdminDao.findPermsByIds(any())).thenAnswer(inv -> {
			List<String> ids = inv.getArgument(0);
			List<PermRow> out = new ArrayList<>();
			int seq = 0;
			for (String[] p : PERMS) {
				seq++;
				if (ids.contains(p[0])) {
					PermRow r = new PermRow();
					r.setPermId(p[0]);
					r.setPermName(p[1]);
					r.setModuleCode(p[2]);
					r.setSortSeq(seq);
					out.add(r);
				}
			}
			return out;
		});
		when(fx.roleAdminDao.countRoleMembers()).thenReturn(Map.of("GOV", 3L, "SYS_ADMIN", 1L));
		when(fx.roleAdminDao.countActiveUsers()).thenReturn(12L);
		govStatuses.put("APPR_G5", 1);
		govStatuses.put("VERIFY_G7", 1);
		govStatuses.put("INSP_VIEW", 0);
		govStatuses.put("REVIEW_LIST", 1);
		when(fx.roleAdminDao.findRolePermStatuses("GOV")).thenAnswer(inv -> new HashMap<>(govStatuses));
		when(fx.roleAdminDao.updateRolePermStatus(anyString(), anyString(), anyInt(), anyInt(), any(), anyString())).thenReturn(1);
		when(fx.roleAdminDao.insertRolePerm(anyString(), anyString(), any(), anyString())).thenReturn(1);
		// 預設：拿掉後仍有人持有 → 不擋
		when(fx.roleAdminDao.countHolders(anyString(), any(), any(), any())).thenReturn(2L);
		when(fx.roleAdminDao.findInFlightByStagePerm(anyString())).thenReturn(List.of());
		when(fx.roleAdminDao.findInFlightEmg()).thenReturn(List.of());
	}

	static RolePermForm form(List<String> added, List<String> removed, String reason) {
		RolePermForm f = new RolePermForm();
		f.setAdded(added);
		f.setRemoved(removed);
		f.setReason(reason);
		return f;
	}

	static RolePermRow rp(String roleId, String permId) {
		RolePermRow r = new RolePermRow();
		r.setRoleId(roleId);
		r.setPermId(permId);
		return r;
	}

	/** 碼 p：不排除時 before 人、排除「GOV＋p」後 after 人 */
	void holders(String permId, long before, long after) {
		when(fx.roleAdminDao.countHolders(eq(permId), isNull(), isNull(), isNull())).thenReturn(before);
		when(fx.roleAdminDao.countHolders(eq(permId), eq("GOV"), isNull(), eq(permId))).thenReturn(after);
	}

	void verifyNoLog() {
		verify(fx.adminLogDao, never()).insert(any(), any(), any(), any(), any(), any(), any(), any());
	}

	// ------------------------------------------------------------
	// 清單
	// ------------------------------------------------------------

	@Test
	void 清單_無SYS_ROLE_403且不寫軌跡() {
		assertStatus(() -> svc.matrix(id(KEVIN)), HttpStatus.FORBIDDEN, AcctMsg.P01);
		verifyNoLog();
	}

	@Test
	void 清單_可見碼依模組分組_GENERAL唯讀_授予碼只列可見_隱藏碼計數() {
		when(fx.roleAdminDao.findRolesWithGeneral()).thenReturn(List.of(RoleAdminServiceTest.role("GENERAL", 1),
				RoleAdminServiceTest.role("GOV", 1), RoleAdminServiceTest.role("INSP_STAFF", 1)));
		when(fx.roleAdminDao.findRolePerms()).thenReturn(List.of(rp("GENERAL", "ACC_APPLY"), rp("GENERAL", "STATS_VIEW"),
				rp("GOV", "APPR_G5"), rp("GOV", "VERIFY_G7"), rp("GOV", "REVIEW_LIST"), rp("GOV", "REVIEW_SIGN_GOV")));
		RolePermMatrix m = svc.matrix(id(HANK));
		// 只查可見清單的碼；分組順序依碼首次出現
		verify(fx.roleAdminDao).findPermsByIds(AcctConst.ADMIN_VISIBLE_PERMS);
		assertThat(m.getModules()).extracting("moduleCode").containsExactly("ACCT", "INSP", "SYS", "EMG");
		assertThat(m.getModules().get(0).getPerms()).extracting("permId").containsExactly("ACC_APPLY", "ACC_LIST_FULL", "APPR_G5",
				"VERIFY_G7");
		assertThat(m.getModules().get(2).getPerms().get(0).getPermName()).isEqualTo("角色指派");

		assertThat(m.getRoles()).hasSize(3);
		AdminRolePerms general = m.getRoles().get(0);
		assertThat(general.getRoleId()).isEqualTo("GENERAL");
		assertThat(general.isReadOnly()).isTrue();
		assertThat(general.getMemberCount()).isEqualTo(12);
		assertThat(general.getGrantedPermIds()).containsExactly("ACC_APPLY");
		assertThat(general.getHiddenGrantedCount()).isEqualTo(1);
		AdminRolePerms gov = m.getRoles().get(1);
		assertThat(gov.isReadOnly()).isFalse();
		assertThat(gov.getMemberCount()).isEqualTo(3);
		assertThat(gov.getGrantedPermIds()).containsExactly("APPR_G5", "VERIFY_G7");
		assertThat(gov.getHiddenGrantedCount()).isEqualTo(2);
		AdminRolePerms insp = m.getRoles().get(2);
		assertThat(insp.getMemberCount()).isZero();
		assertThat(insp.getGrantedPermIds()).isEmpty();
		assertThat(insp.getHiddenGrantedCount()).isZero();
		verifyNoLog();
	}

	// ------------------------------------------------------------
	// 存檔：①～⑥（交易外）
	// ------------------------------------------------------------

	@Test
	void 存檔_無權限_403並寫DENY_只記roleId與碼數_對象空_不取鎖() {
		assertStatus(() -> svc.save(id(KEVIN), "GOV", form(List.of("INSP_VIEW", "SYS_ROLE"), List.of("APPR_G5"), "想要")),
				HttpStatus.FORBIDDEN, AcctMsg.P01);
		verify(fx.adminLogDao).insert(eq(NOW), eq(KEVIN), eq("Kevin (李凱鈞)"), eq(AcctConst.ADM_ROLE_PERM_SET), isNull(),
				eq("{\"roleId\":\"GOV\",\"addedCount\":2,\"removedCount\":1,\"denyCode\":\"NO_PERM\"}"), eq("想要"), eq(AcctConst.ADM_DENY));
		verify(fx.roleAdminDao, never()).lockGlobal();
		assertThat(fx.txm.begins).isZero();

		// roleId 超過欄寬存 null、原因沒帶存 null
		assertStatus(() -> svc.save(id(KEVIN), "X".repeat(21), null), HttpStatus.FORBIDDEN, AcctMsg.P01);
		verify(fx.adminLogDao).insert(eq(NOW), eq(KEVIN), any(), eq(AcctConst.ADM_ROLE_PERM_SET), isNull(),
				eq("{\"roleId\":null,\"addedCount\":0,\"removedCount\":0,\"denyCode\":\"NO_PERM\"}"), isNull(), eq(AcctConst.ADM_DENY));
	}

	@Test
	void 存檔_切換身分中_E25() {
		assertStatus(() -> svc.save(impersonate(KEVIN, HANK), "GOV", form(List.of("INSP_VIEW"), null, "原因")), HttpStatus.CONFLICT,
				AcctMsg.E25);
		verifyNoLog();
	}

	@Test
	void 存檔_原因E10_E11_角色GENERAL停用不存在過長400_不寫軌跡() {
		assertStatus(() -> svc.save(id(HANK), "GOV", form(List.of("INSP_VIEW"), null, " ")), HttpStatus.BAD_REQUEST,
				AcctMsg.E10_PREFIX + "原因");
		assertStatus(() -> svc.save(id(HANK), "GOV", form(List.of("INSP_VIEW"), null, "原".repeat(501))), HttpStatus.BAD_REQUEST,
				AcctMsg.E11_PREFIX + "原因");
		for (String r : List.of("GENERAL", "OLD", "NOPE", "X".repeat(21))) {
			assertStatus(() -> svc.save(id(HANK), r, form(List.of("INSP_VIEW"), null, "原因")), HttpStatus.BAD_REQUEST,
					AcctMsg.BAD_CODE_PREFIX + "角色");
		}
		verify(fx.roleAdminDao, never()).findRoleById("X".repeat(21));
		verify(fx.roleAdminDao, never()).lockGlobal();
		verifyNoLog();
	}

	@Test
	void 存檔_兩邊皆空_400沒有任何變更() {
		assertStatus(() -> svc.save(id(HANK), "GOV", form(null, null, "原因")), HttpStatus.BAD_REQUEST, AcctMsg.NO_CHANGE);
		assertStatus(() -> svc.save(id(HANK), "GOV", form(List.of(), List.of(), "原因")), HttpStatus.BAD_REQUEST, AcctMsg.NO_CHANGE);
		assertStatus(() -> svc.save(id(HANK), "GOV", null), HttpStatus.BAD_REQUEST, AcctMsg.E10_PREFIX + "原因");
		verify(fx.roleAdminDao, never()).findPermsByIds(any());
		verifyNoLog();
	}

	@Test
	void 存檔_碼重複_同碼兩邊_隱藏碼_不存在碼_空字串_400權限碼_不取鎖() {
		List<RolePermForm> bad = List.of(
				form(List.of("INSP_VIEW", "INSP_VIEW"), null, "原因"),
				form(List.of("INSP_VIEW"), List.of("INSP_VIEW"), "原因"),
				form(null, List.of("APPR_G5", "APPR_G5"), "原因"),
				form(List.of("REVIEW_LIST"), null, "原因"),
				form(null, List.of("REVIEW_LIST"), "原因"),
				form(List.of("NOT_A_PERM"), null, "原因"),
				form(Arrays.asList("INSP_VIEW", "  "), null, "原因"),
				form(Arrays.asList((String) null), null, "原因"));
		for (RolePermForm f : bad) {
			assertStatus(() -> svc.save(id(HANK), "GOV", f), HttpStatus.BAD_REQUEST, AcctMsg.BAD_CODE_PREFIX + "權限碼");
		}
		verify(fx.roleAdminDao, never()).lockGlobal();
		verify(fx.roleAdminDao, never()).findRolePermStatuses(any());
		verifyNoLog();
	}

	// ------------------------------------------------------------
	// 存檔：⑦～⑪（交易內）
	// ------------------------------------------------------------

	@Test
	void 存檔_取鎖逾時_E26_不寫軌跡() {
		doThrow(new CannotAcquireLockException("ORA-30006")).when(fx.roleAdminDao).lockGlobal();
		assertStatus(() -> svc.save(id(HANK), "GOV", form(List.of("INSP_VIEW"), null, "原因")), HttpStatus.CONFLICT, AcctMsg.E26);
		verifyNoLog();
		assertThat(fx.txm.rollbacks).isEqualTo(1);
	}

	@Test
	void 存檔_現況不符_added已授予或removed未授予_E27回滾不寫() {
		// APPR_G5 現在是 1，卻要加入
		assertStatus(() -> svc.save(id(HANK), "GOV", form(List.of("APPR_G5"), null, "原因")), HttpStatus.CONFLICT, AcctMsg.E27);
		// INSP_VIEW 現在是 0、SYS_ROLE 查無列，卻要拿掉
		assertStatus(() -> svc.save(id(HANK), "GOV", form(null, List.of("INSP_VIEW"), "原因")), HttpStatus.CONFLICT, AcctMsg.E27);
		assertStatus(() -> svc.save(id(HANK), "GOV", form(null, List.of("SYS_ROLE"), "原因")), HttpStatus.CONFLICT, AcctMsg.E27);
		verify(fx.roleAdminDao, times(3)).lockGlobal();
		verify(fx.roleAdminDao, never()).updateRolePermStatus(any(), any(), anyInt(), anyInt(), any(), any());
		verify(fx.roleAdminDao, never()).insertRolePerm(any(), any(), any(), any());
		verify(fx.roleAdminDao, never()).countHolders(any(), any(), any(), any());
		verifyNoLog();
		assertThat(fx.txm.rollbacks).isEqualTo(3);
	}

	@Test
	void 存檔_拿掉後SYS_ROLE歸零_E24換文案加DENY回滾_優先於E23() {
		govStatuses.put("SYS_ROLE", 1);
		holders("SYS_ROLE", 1, 0);
		holders("APPR_G5", 1, 0);
		when(fx.roleAdminDao.findInFlightByStagePerm("APPR_G5")).thenReturn(List.of(new InFlightRow("AR0001", "資訊主管")));
		assertStatus(() -> svc.save(id(HANK), "GOV", form(List.of("INSP_VIEW"), List.of("SYS_ROLE", "APPR_G5"), "整理")),
				HttpStatus.CONFLICT, AcctMsg.E24_PERM);
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(fx.adminLogDao).insert(eq(NOW), eq(HANK), eq("Hank (黃志翰)"), eq(AcctConst.ADM_ROLE_PERM_SET), isNull(), json.capture(),
				eq("整理"), eq(AcctConst.ADM_DENY));
		assertThat(json.getValue()).isEqualTo("{\"roleId\":\"GOV\",\"roleName\":\"角色GOV\",\"memberCount\":3,"
				+ "\"added\":[\"INSP_VIEW\"],\"removed\":[\"SYS_ROLE\",\"APPR_G5\"],\"denyCode\":\"LAST_SYS_ROLE\","
				+ "\"blocked\":[{\"permId\":\"APPR_G5\",\"total\":1,\"ids\":[\"AR0001\"]}]}");
		verify(fx.roleAdminDao, never()).updateRolePermStatus(any(), any(), anyInt(), anyInt(), any(), any());
		verify(fx.roleAdminDao, never()).insertRolePerm(any(), any(), any(), any());
		assertThat(fx.txm.rollbacks).isEqualTo(1);
		assertThat(fx.txm.commits).isZero();
	}

	@Test
	void 存檔_多碼同時被在途擋下_全部列出依碼分組_DENY分組_名單未改() {
		holders("APPR_G5", 1, 0);
		holders("VERIFY_G7", 1, 0);
		List<InFlightRow> g5 = new ArrayList<>();
		for (int i = 1; i <= 25; i++) {
			g5.add(new InFlightRow("AR" + String.format("%04d", i), "資訊主管"));
		}
		when(fx.roleAdminDao.findInFlightByStagePerm("APPR_G5")).thenReturn(g5);
		when(fx.roleAdminDao.findInFlightByStagePerm("VERIFY_G7")).thenReturn(List.of(new InFlightRow("AR9001", "資訊治理複驗")));
		assertStatus(() -> svc.save(id(HANK), "GOV", form(null, List.of("APPR_G5", "VERIFY_G7"), "原因")), HttpStatus.CONFLICT,
				AcctMsg.E23_PERM_PREFIX + "【關卡 5 核准／退件（APPR_G5）】" + String.join("、", g5.subList(0, 20).stream().map(InFlightRow::getDocId).toList())
						+ "（等共 25 件）；【關卡 7 複驗（VERIFY_G7）】AR9001");
		// 每碼各自排除「GOV＋該碼」那一格
		verify(fx.roleAdminDao).countHolders("APPR_G5", "GOV", null, "APPR_G5");
		verify(fx.roleAdminDao).countHolders("VERIFY_G7", "GOV", null, "VERIFY_G7");
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(fx.adminLogDao).insert(any(), any(), any(), eq(AcctConst.ADM_ROLE_PERM_SET), isNull(), json.capture(), any(),
				eq(AcctConst.ADM_DENY));
		assertThat(json.getValue()).contains("\"denyCode\":\"IN_FLIGHT\"")
				.contains("\"blocked\":[{\"permId\":\"APPR_G5\",\"total\":25,\"ids\":[\"AR0001\"").contains("\"AR0020\"]}")
				.doesNotContain("AR0021").contains("{\"permId\":\"VERIFY_G7\",\"total\":1,\"ids\":[\"AR9001\"]}");
		verify(fx.roleAdminDao, never()).updateRolePermStatus(any(), any(), anyInt(), anyInt(), any(), any());
		assertThat(fx.txm.rollbacks).isEqualTo(1);
	}

	@Test
	void 存檔_通過_removed更新1到0_added現況0走UPDATE_無列走INSERT_SUCCESS一筆同交易() {
		RolePermSaveResult r = svc.save(id(HANK), "GOV",
				form(Arrays.asList(" INSP_VIEW ", "SYS_IMPERSONATE"), List.of("APPR_G5"), " 調整 "));
		assertThat(r.getResult()).isEqualTo("SAVED");
		assertThat(r.getRoleId()).isEqualTo("GOV");
		assertThat(r.getAdded()).containsExactly("INSP_VIEW", "SYS_IMPERSONATE");
		assertThat(r.getRemoved()).containsExactly("APPR_G5");
		verify(fx.roleAdminDao).lockGlobal();
		verify(fx.roleAdminDao).updateRolePermStatus("GOV", "APPR_G5", 1, 0, NOW, HANK);
		verify(fx.roleAdminDao).updateRolePermStatus("GOV", "INSP_VIEW", 0, 1, NOW, HANK);
		verify(fx.roleAdminDao).insertRolePerm("GOV", "SYS_IMPERSONATE", NOW, HANK);
		verify(fx.roleAdminDao, never()).insertRolePerm(eq("GOV"), eq("INSP_VIEW"), any(), any());
		verify(fx.adminLogDao).insert(eq(NOW), eq(HANK), eq("Hank (黃志翰)"), eq(AcctConst.ADM_ROLE_PERM_SET), isNull(),
				eq("{\"roleId\":\"GOV\",\"roleName\":\"角色GOV\",\"memberCount\":3,\"added\":[\"INSP_VIEW\",\"SYS_IMPERSONATE\"],\"removed\":[\"APPR_G5\"]}"),
				eq("調整"), eq(AcctConst.ADM_SUCCESS));
		assertThat(fx.txm.commits).isEqualTo(1);
		assertThat(fx.txm.rollbacks).isZero();
		// 第一次存檔只為 APPR_G5 查過一次持有人（排除後仍有人、不必查變更前）；只加不拿的第二次存檔不再查
		verify(fx.roleAdminDao, times(1)).countHolders(any(), any(), any(), any());
		fx.txm.commits = 0;
		svc.save(id(HANK), "GOV", form(List.of("ACC_LIST_FULL"), null, "原因"));
		verify(fx.roleAdminDao, times(1)).countHolders(any(), any(), any(), any());
		assertThat(fx.txm.commits).isEqualTo(1);
	}

	@Test
	void 存檔_寫入影響0筆或主鍵衝突_轉E27回滾() {
		when(fx.roleAdminDao.updateRolePermStatus("GOV", "APPR_G5", 1, 0, NOW, HANK)).thenReturn(0);
		assertStatus(() -> svc.save(id(HANK), "GOV", form(null, List.of("APPR_G5"), "原因")), HttpStatus.CONFLICT, AcctMsg.E27);
		when(fx.roleAdminDao.insertRolePerm("GOV", "SYS_IMPERSONATE", NOW, HANK)).thenThrow(new DuplicateKeyException("PK"));
		assertStatus(() -> svc.save(id(HANK), "GOV", form(List.of("SYS_IMPERSONATE"), null, "原因")), HttpStatus.CONFLICT, AcctMsg.E27);
		when(fx.roleAdminDao.updateRolePermStatus("GOV", "INSP_VIEW", 0, 1, NOW, HANK)).thenReturn(0);
		assertStatus(() -> svc.save(id(HANK), "GOV", form(List.of("INSP_VIEW"), null, "原因")), HttpStatus.CONFLICT, AcctMsg.E27);
		verifyNoLog();
		assertThat(fx.txm.rollbacks).isEqualTo(3);
		assertThat(fx.txm.commits).isZero();
	}
}
