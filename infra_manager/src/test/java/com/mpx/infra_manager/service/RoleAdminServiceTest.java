package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：角色指派維護單元測試（BACKLOG 第 89 項階段 1；SA 4.7、裁示 ①B／②A／③A／⑤B／⑥A／⑦A）
//           清單：無 SYS_ROLE 403 不寫軌跡、角色分組權限名與成員（停用者 userActive＝false）、候選人兩碼擇一；
//           指派：無權限 403＋DENY（NO_PERM、原因有存）、切換中 E25、缺對象 E02、原因 E10／E11、角色 GENERAL／停用／不存在 400、
//                 對象停用或不存在 404、已在名單 E21、重新指派走 UPDATE 0→1 不 INSERT、新指派走 INSERT、主鍵衝突轉 E21、SUCCESS 同交易；
//           移除：不在名單 E22、SYS_ROLE 歸零 E24＋DENY 且回滾、在途 E23（訊息含單號）＋DENY 且回滾、通過才 UPDATE 1→0、
//                 對象已停用或查無仍可移除、改自己允許；取鎖逾時 E26 不寫軌跡
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 階段 2 回合 1：執行人對照——清單需 SYS_EXECUTOR（8 類全列含關閉類別、成員分組）、只有 SYS_EXECUTOR 也能指派；
//           指派：無權限 DENY 記 catgId、類別不存在／過長 400「帳號類別」、對象停用 404、關閉類別仍可指派、SUCCESS 記 catgId／catgName；
//           移除：在途（關卡 6 同類別）E23＋DENY 的 blockedPerms 放類別代碼且不查權限持有人、通過才 UPDATE 1→0、不在名單 E22、切換中 E25
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: BACKLOG 第 124 項階段 3 回合 1：MemberRow 刪英文名、期望顯示名改只有中文名（第 67 項併入）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: BACKLOG 第 124 項階段 3 回合 2（E3、⑬-2）：成員本系統啟用但同步表查無 → userActive＝false；指派對象同步表查無 404；候選人不帶部門
// ============================================================

import static com.mpx.infra_manager.service.RequestServiceTest.assertStatus;
import static com.mpx.infra_manager.support.TestFixture.GARY;
import static com.mpx.infra_manager.support.TestFixture.HANK;
import static com.mpx.infra_manager.support.TestFixture.KEVIN;
import static com.mpx.infra_manager.support.TestFixture.NOW;
import static com.mpx.infra_manager.support.TestFixture.TESTER;
import static com.mpx.infra_manager.support.TestFixture.id;
import static com.mpx.infra_manager.support.TestFixture.impersonate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.UncategorizedSQLException;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.AcctMsg;
import com.mpx.infra_manager.model.AcctUser;
import com.mpx.infra_manager.model.AdminExecCatg;
import com.mpx.infra_manager.model.AdminRole;
import com.mpx.infra_manager.model.AdminUser;
import com.mpx.infra_manager.model.InFlightRow;
import com.mpx.infra_manager.model.MemberChangeResult;
import com.mpx.infra_manager.model.MemberRow;
import com.mpx.infra_manager.model.RolePermRow;
import com.mpx.infra_manager.model.RoleRow;
import com.mpx.infra_manager.model.request.MemberForm;
import com.mpx.infra_manager.model.request.ReasonForm;
import com.mpx.infra_manager.support.TestFixture;

class RoleAdminServiceTest {

	TestFixture fx;
	RoleAdminService svc;

	@BeforeEach
	void setUp() {
		fx = new TestFixture();
		svc = fx.roleAdminService;
		for (String u : TestFixture.NAMES.keySet()) {
			when(fx.userDao.findById(u)).thenReturn(AuthServiceTest.user(u, 1));
		}
		when(fx.roleAdminDao.findRoleById(anyString())).thenAnswer(inv -> role(inv.getArgument(0), 1));
		when(fx.roleAdminDao.findRoleById("GENERAL")).thenReturn(role("GENERAL", 1));
		when(fx.roleAdminDao.findRoleById("NOPE")).thenReturn(null);
		when(fx.roleAdminDao.findRoleById("OLD")).thenReturn(role("OLD", 0));
		when(fx.roleAdminDao.updateRoleMemberStatus(anyString(), anyString(), anyInt(), anyInt(), any(), anyString())).thenReturn(1);
		when(fx.roleAdminDao.insertRoleMember(anyString(), anyString(), any(), anyString())).thenReturn(1);
		when(fx.roleAdminDao.findPermIdsOfRole(anyString())).thenAnswer(inv -> TestFixture.ROLE_PERMS.getOrDefault(inv.getArgument(0),
				List.of()));
		// 預設：排除後仍有人持有 → 不擋
		when(fx.roleAdminDao.countHolders(anyString(), any(), any(), any())).thenReturn(2L);
		when(fx.roleAdminDao.findInFlightByStagePerm(anyString())).thenReturn(List.of());
		when(fx.roleAdminDao.findInFlightEmg()).thenReturn(List.of());
		// 執行人對照：預設排除後仍有人、無在途
		when(fx.roleAdminDao.updateExecMemberStatus(anyString(), anyString(), anyInt(), anyInt(), any(), anyString())).thenReturn(1);
		when(fx.roleAdminDao.insertExecMember(anyString(), anyString(), any(), anyString())).thenReturn(1);
		when(fx.roleAdminDao.countExecutors(anyString(), any())).thenReturn(2L);
		when(fx.roleAdminDao.findInFlightExecByCatg(anyString())).thenReturn(List.of());
	}

	static RoleRow role(String roleId, int status) {
		RoleRow r = new RoleRow();
		r.setRoleId(roleId);
		r.setRoleName("角色" + roleId);
		r.setIsSystem("GENERAL".equals(roleId) ? 1 : 0);
		r.setSortSeq(1);
		r.setStatus(status);
		return r;
	}

	static MemberForm grant(String userId, String reason) {
		MemberForm f = new MemberForm();
		f.setUserId(userId);
		f.setReason(reason);
		return f;
	}

	static ReasonForm reason(String reason) {
		ReasonForm f = new ReasonForm();
		f.setReason(reason);
		return f;
	}

	// ------------------------------------------------------------
	// 清單
	// ------------------------------------------------------------

	@Test
	void 清單_無SYS_ROLE_403且不寫軌跡() {
		assertStatus(() -> svc.roles(id(KEVIN)), HttpStatus.FORBIDDEN, AcctMsg.P01);
		verify(fx.adminLogDao, never()).insert(any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void 清單_角色分組權限名與成員_停用者標記() {
		when(fx.roleAdminDao.findRoles()).thenReturn(List.of(role("GOV", 1), role("INSP_STAFF", 1)));
		RolePermRow p1 = new RolePermRow();
		p1.setRoleId("GOV");
		p1.setPermName("關卡 4 核准");
		RolePermRow p2 = new RolePermRow();
		p2.setRoleId("GOV");
		p2.setPermName("關卡 7 複驗");
		when(fx.roleAdminDao.findRolePerms()).thenReturn(List.of(p1, p2));
		MemberRow m1 = new MemberRow();
		m1.setGroupId("GOV");
		m1.setUserId(KEVIN);
		m1.setUserName("李凱鈞");
		m1.setUserStatus(1);
		m1.setUserInSync(1);
		m1.setGrantDate(NOW);
		m1.setGrantBy(HANK);
		MemberRow m2 = new MemberRow();
		m2.setGroupId("GOV");
		m2.setUserId("GHOST");
		m2.setUserStatus(null);
		m2.setUserInSync(0);
		// 本系統啟用但同步表查無（離職）→ 視為停用（E3）
		MemberRow m3 = new MemberRow();
		m3.setGroupId("GOV");
		m3.setUserId(GARY);
		m3.setUserName("許永成");
		m3.setUserStatus(1);
		m3.setUserInSync(0);
		when(fx.roleAdminDao.findRoleMembers()).thenReturn(List.of(m1, m2, m3));

		List<AdminRole> roles = svc.roles(id(HANK));
		assertThat(roles).hasSize(2);
		AdminRole gov = roles.get(0);
		assertThat(gov.getRoleId()).isEqualTo("GOV");
		assertThat(gov.getPermNames()).containsExactly("關卡 4 核准", "關卡 7 複驗");
		assertThat(gov.getMembers()).hasSize(3);
		assertThat(gov.getMembers().get(0).getUserName()).isEqualTo("李凱鈞");
		assertThat(gov.getMembers().get(0).getUserActive()).isTrue();
		assertThat(gov.getMembers().get(0).getGrantDate()).isEqualTo(NOW);
		assertThat(gov.getMembers().get(0).getGrantBy()).isEqualTo(HANK);
		// 使用者表查無 → 顯示工號、視為未啟用
		assertThat(gov.getMembers().get(1).getUserName()).isEqualTo("GHOST");
		assertThat(gov.getMembers().get(1).getUserActive()).isFalse();
		assertThat(gov.getMembers().get(2).getUserName()).isEqualTo("許永成");
		assertThat(gov.getMembers().get(2).getUserActive()).isFalse();
		assertThat(roles.get(1).getPermNames()).isEmpty();
		assertThat(roles.get(1).getMembers()).isEmpty();
	}

	@Test
	void 候選人_SYS_ROLE或SYS_EXECUTOR擇一_含自己() {
		AcctUser u = AuthServiceTest.user(HANK, 1);
		when(fx.roleAdminDao.findCandidates()).thenReturn(List.of(u));
		List<AdminUser> users = svc.users(id(HANK));
		assertThat(users).hasSize(1);
		assertThat(users.get(0).getUserId()).isEqualTo(HANK);
		assertThat(users.get(0).getUserName()).isEqualTo("黃志翰");
		assertThat(svc.users(TestFixture.idWith(GARY, java.util.Set.of("SYS_EXECUTOR"), List.of()))).hasSize(1);
		assertStatus(() -> svc.users(id(KEVIN)), HttpStatus.FORBIDDEN, AcctMsg.P01);
	}

	// ------------------------------------------------------------
	// 指派
	// ------------------------------------------------------------

	@Test
	void 指派_無權限_403並寫DENY_原因有存_不取鎖() {
		assertStatus(() -> svc.grantRole(id(KEVIN), "GOV", grant(GARY, "接手")), HttpStatus.FORBIDDEN, AcctMsg.P01);
		verify(fx.adminLogDao).insert(eq(NOW), eq(KEVIN), eq("李凱鈞"), eq(AcctConst.ADM_ROLE_GRANT), eq(GARY),
				eq("{\"roleId\":\"GOV\",\"denyCode\":\"NO_PERM\"}"), eq("接手"), eq(AcctConst.ADM_DENY));
		verify(fx.roleAdminDao, never()).lockGlobal();
		assertThat(fx.txm.begins).isZero();
	}

	@Test
	void 指派_切換身分中_E25() {
		assertStatus(() -> svc.grantRole(impersonate(KEVIN, HANK), "GOV", grant(GARY, "接手")), HttpStatus.CONFLICT, AcctMsg.E25);
		verify(fx.adminLogDao, never()).insert(any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void 指派_欄位檢查順序_對象E02_原因E10_E11() {
		assertStatus(() -> svc.grantRole(id(HANK), "GOV", grant("  ", "x")), HttpStatus.BAD_REQUEST, AcctMsg.E02_PREFIX + "對象");
		assertStatus(() -> svc.grantRole(id(HANK), "GOV", grant(GARY, " ")), HttpStatus.BAD_REQUEST, AcctMsg.E10_PREFIX + "原因");
		assertStatus(() -> svc.grantRole(id(HANK), "GOV", grant(GARY, "原".repeat(501))), HttpStatus.BAD_REQUEST,
				AcctMsg.E11_PREFIX + "原因");
		assertStatus(() -> svc.grantRole(id(HANK), "GOV", null), HttpStatus.BAD_REQUEST, AcctMsg.E02_PREFIX + "對象");
		verify(fx.roleAdminDao, never()).lockGlobal();
	}

	@Test
	void 指派_角色GENERAL停用不存在過長_400() {
		for (String r : List.of("GENERAL", "OLD", "NOPE", "X".repeat(21))) {
			assertStatus(() -> svc.grantRole(id(HANK), r, grant(GARY, "原因")), HttpStatus.BAD_REQUEST, AcctMsg.BAD_CODE_PREFIX + "角色");
		}
		verify(fx.roleAdminDao, never()).findRoleById("X".repeat(21));
	}

	@Test
	void 指派_對象不存在或停用或同步表查無_404() {
		when(fx.userDao.findById(TESTER)).thenReturn(AuthServiceTest.user(TESTER, 0));
		assertStatus(() -> svc.grantRole(id(HANK), "GOV", grant(TESTER, "原因")), HttpStatus.NOT_FOUND, AcctMsg.N01);
		assertStatus(() -> svc.grantRole(id(HANK), "GOV", grant("9999999", "原因")), HttpStatus.NOT_FOUND, AcctMsg.N01);
		// 本系統啟用但同步表查無（E3）
		AcctUser gone = AuthServiceTest.user(TESTER, 1);
		gone.setInSync(0);
		when(fx.userDao.findById(TESTER)).thenReturn(gone);
		assertStatus(() -> svc.grantRole(id(HANK), "GOV", grant(TESTER, "原因")), HttpStatus.NOT_FOUND, AcctMsg.N01);
		verify(fx.roleAdminDao, never()).lockGlobal();
	}

	@Test
	void 指派_已在名單_E21_回滾不寫() {
		when(fx.roleAdminDao.findRoleMemberStatus("GOV", GARY)).thenReturn(1);
		assertStatus(() -> svc.grantRole(id(HANK), "GOV", grant(GARY, "原因")), HttpStatus.CONFLICT, AcctMsg.E21);
		verify(fx.roleAdminDao).lockGlobal();
		verify(fx.roleAdminDao, never()).insertRoleMember(any(), any(), any(), any());
		verify(fx.adminLogDao, never()).insert(any(), any(), any(), any(), any(), any(), any(), any());
		assertThat(fx.txm.rollbacks).isEqualTo(1);
	}

	@Test
	void 指派_重新指派走UPDATE不INSERT_SUCCESS同交易() {
		when(fx.roleAdminDao.findRoleMemberStatus("GOV", GARY)).thenReturn(0);
		MemberChangeResult r = svc.grantRole(id(HANK), "GOV", grant(" " + GARY + " ", " 回鍋 "));
		assertThat(r.getResult()).isEqualTo("GRANTED");
		assertThat(r.getRoleId()).isEqualTo("GOV");
		assertThat(r.getUserId()).isEqualTo(GARY);
		verify(fx.roleAdminDao).updateRoleMemberStatus("GOV", GARY, 0, 1, NOW, HANK);
		verify(fx.roleAdminDao, never()).insertRoleMember(any(), any(), any(), any());
		verify(fx.adminLogDao).insert(eq(NOW), eq(HANK), eq("黃志翰"), eq(AcctConst.ADM_ROLE_GRANT), eq(GARY),
				eq("{\"roleId\":\"GOV\",\"roleName\":\"角色GOV\",\"targetUserName\":\"許永成\",\"before\":{\"status\":0},\"after\":{\"status\":1}}"),
				eq("回鍋"), eq(AcctConst.ADM_SUCCESS));
		assertThat(fx.txm.commits).isEqualTo(1);
	}

	@Test
	void 指派_新指派走INSERT_before為null_主鍵衝突轉E21() {
		when(fx.roleAdminDao.findRoleMemberStatus("GOV", GARY)).thenReturn(null);
		svc.grantRole(id(HANK), "GOV", grant(GARY, "新人"));
		verify(fx.roleAdminDao).insertRoleMember("GOV", GARY, NOW, HANK);
		verify(fx.roleAdminDao, never()).updateRoleMemberStatus(any(), any(), anyInt(), anyInt(), any(), any());
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(fx.adminLogDao).insert(any(), any(), any(), any(), any(), json.capture(), any(), eq(AcctConst.ADM_SUCCESS));
		assertThat(json.getValue()).contains("\"before\":{\"status\":null}").contains("\"after\":{\"status\":1}");

		// Mockito 對 Integer 回傳預設是 0（會走 UPDATE 路徑），要明寫 null 才是「查無列」
		when(fx.roleAdminDao.findRoleMemberStatus("GOV", KEVIN)).thenReturn(null);
		when(fx.roleAdminDao.insertRoleMember("GOV", KEVIN, NOW, HANK)).thenThrow(new DuplicateKeyException("PK"));
		assertStatus(() -> svc.grantRole(id(HANK), "GOV", grant(KEVIN, "撞")), HttpStatus.CONFLICT, AcctMsg.E21);
	}

	@Test
	void 指派_取鎖逾時_E26_不寫軌跡() {
		doThrow(new CannotAcquireLockException("ORA-30006")).when(fx.roleAdminDao).lockGlobal();
		assertStatus(() -> svc.grantRole(id(HANK), "GOV", grant(GARY, "原因")), HttpStatus.CONFLICT, AcctMsg.E26);
		verify(fx.adminLogDao, never()).insert(any(), any(), any(), any(), any(), any(), any(), any());
		assertThat(fx.txm.rollbacks).isEqualTo(1);
	}

	/** 2026-10-19 實機：ORA-30006 實際是 UncategorizedSQLException（SQLState 99999、錯誤碼 30006），要轉 E26 不能落到 500 */
	@Test
	void 指派_取鎖逾時_實機型別UncategorizedSQLException錯誤碼30006_轉E26() {
		doThrow(new UncategorizedSQLException("PreparedStatementCallback", "SELECT ... FOR UPDATE WAIT 5",
				new SQLException("ORA-30006: 資源忙碌; WAIT 逾時無法取得", "99999", 30006))).when(fx.roleAdminDao).lockGlobal();
		assertStatus(() -> svc.grantRole(id(HANK), "GOV", grant(GARY, "原因")), HttpStatus.CONFLICT, AcctMsg.E26);
		verify(fx.adminLogDao, never()).insert(any(), any(), any(), any(), any(), any(), any(), any());
		assertThat(fx.txm.rollbacks).isEqualTo(1);
	}

	/** 其他 SQL 錯誤不能被誤轉成 E26（照原樣拋出，由全域處理成 500） */
	@Test
	void 指派_其他SQL錯誤_不轉E26_原樣拋出() {
		UncategorizedSQLException other = new UncategorizedSQLException("PreparedStatementCallback", "SELECT",
				new SQLException("ORA-00942: 表格或視觀表不存在", "42000", 942));
		doThrow(other).when(fx.roleAdminDao).lockGlobal();
		assertThatThrownBy(() -> svc.grantRole(id(HANK), "GOV", grant(GARY, "原因"))).isSameAs(other);
	}

	/** 審查 M1：Spring 走 SQLState 分類時可能只給父類別，不是 CannotAcquireLockException；父類別也要轉 E26、不能落到 500 */
	@Test
	void 指派_取鎖逾時_父類別例外也轉E26() {
		doThrow(new PessimisticLockingFailureException("ORA-30006")).when(fx.roleAdminDao).lockGlobal();
		assertStatus(() -> svc.grantRole(id(HANK), "GOV", grant(GARY, "原因")), HttpStatus.CONFLICT, AcctMsg.E26);
		verify(fx.adminLogDao, never()).insert(any(), any(), any(), any(), any(), any(), any(), any());
		assertThat(fx.txm.rollbacks).isEqualTo(1);
	}

	// ------------------------------------------------------------
	// 移除
	// ------------------------------------------------------------

	@Test
	void 移除_無權限_403並寫DENY_切換中E25_原因E10() {
		assertStatus(() -> svc.revokeRole(id(KEVIN), "GOV", GARY, reason("x")), HttpStatus.FORBIDDEN, AcctMsg.P01);
		verify(fx.adminLogDao).insert(eq(NOW), eq(KEVIN), any(), eq(AcctConst.ADM_ROLE_REVOKE), eq(GARY),
				eq("{\"roleId\":\"GOV\",\"denyCode\":\"NO_PERM\"}"), eq("x"), eq(AcctConst.ADM_DENY));
		assertStatus(() -> svc.revokeRole(impersonate(KEVIN, HANK), "GOV", GARY, reason("x")), HttpStatus.CONFLICT, AcctMsg.E25);
		assertStatus(() -> svc.revokeRole(id(HANK), "GOV", GARY, null), HttpStatus.BAD_REQUEST, AcctMsg.E10_PREFIX + "原因");
		assertStatus(() -> svc.revokeRole(id(HANK), "GENERAL", GARY, reason("x")), HttpStatus.BAD_REQUEST, AcctMsg.BAD_CODE_PREFIX + "角色");
	}

	@Test
	void 移除_不在名單_E22_回滾() {
		when(fx.roleAdminDao.findRoleMemberStatus("GOV", GARY)).thenReturn(0);
		assertStatus(() -> svc.revokeRole(id(HANK), "GOV", GARY, reason("x")), HttpStatus.CONFLICT, AcctMsg.E22);
		when(fx.roleAdminDao.findRoleMemberStatus("GOV", "9999999")).thenReturn(null);
		assertStatus(() -> svc.revokeRole(id(HANK), "GOV", "9999999", reason("x")), HttpStatus.CONFLICT, AcctMsg.E22);
		verify(fx.roleAdminDao, never()).updateRoleMemberStatus(any(), any(), anyInt(), anyInt(), any(), any());
		verify(fx.roleAdminDao, never()).countHolders(any(), any(), any(), any());
		assertThat(fx.txm.rollbacks).isEqualTo(2);
	}

	@Test
	void 移除_SYS_ROLE歸零_E24_DENY在交易外_名單未改() {
		when(fx.roleAdminDao.findRoleMemberStatus("SYS_ADMIN", HANK)).thenReturn(1);
		when(fx.roleAdminDao.countHolders(eq("SYS_ROLE"), eq("SYS_ADMIN"), eq(HANK), isNull())).thenReturn(0L);
		when(fx.roleAdminDao.countHolders(eq("SYS_ROLE"), isNull(), isNull(), isNull())).thenReturn(1L);
		assertStatus(() -> svc.revokeRole(id(HANK), "SYS_ADMIN", HANK, reason("離職")), HttpStatus.CONFLICT, AcctMsg.E24);
		verify(fx.roleAdminDao, never()).updateRoleMemberStatus(any(), any(), anyInt(), anyInt(), any(), any());
		assertThat(fx.txm.rollbacks).isEqualTo(1);
		assertThat(fx.txm.commits).isZero();
		verify(fx.adminLogDao).insert(eq(NOW), eq(HANK), any(), eq(AcctConst.ADM_ROLE_REVOKE), eq(HANK),
				eq("{\"roleId\":\"SYS_ADMIN\",\"roleName\":\"角色SYS_ADMIN\",\"targetUserName\":\"黃志翰\",\"denyCode\":\"LAST_SYS_ROLE\",\"blockedPerms\":[],\"blockedTotal\":0,\"blockedIds\":[]}"),
				eq("離職"), eq(AcctConst.ADM_DENY));
	}

	@Test
	void 移除_在途卡住_E23含單號_DENY含被擋清單_名單未改() {
		when(fx.roleAdminDao.findRoleMemberStatus("IT_HEAD", GARY)).thenReturn(1);
		when(fx.roleAdminDao.countHolders(eq("APPR_G5"), eq("IT_HEAD"), eq(GARY), isNull())).thenReturn(0L);
		when(fx.roleAdminDao.countHolders(eq("APPR_G5"), isNull(), isNull(), isNull())).thenReturn(1L);
		when(fx.roleAdminDao.findInFlightByStagePerm("APPR_G5"))
				.thenReturn(List.of(new InFlightRow("AR20261007-001", "資訊主管"), new InFlightRow("AR20261007-002", "資訊主管")));
		assertStatus(() -> svc.revokeRole(id(HANK), "IT_HEAD", GARY, reason("調職")), HttpStatus.CONFLICT,
				AcctMsg.E23_PREFIX + "AR20261007-001（資訊主管）、AR20261007-002（資訊主管）");
		verify(fx.roleAdminDao, never()).updateRoleMemberStatus(any(), any(), anyInt(), anyInt(), any(), any());
		assertThat(fx.txm.rollbacks).isEqualTo(1);
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(fx.adminLogDao).insert(eq(NOW), eq(HANK), any(), eq(AcctConst.ADM_ROLE_REVOKE), eq(GARY), json.capture(), eq("調職"),
				eq(AcctConst.ADM_DENY));
		assertThat(json.getValue()).contains("\"denyCode\":\"IN_FLIGHT\"").contains("\"blockedPerms\":[\"APPR_G5\"]")
				.contains("\"blockedTotal\":2").contains("\"blockedIds\":[\"AR20261007-001\",\"AR20261007-002\"]");
		// 失去的碼＝該角色授予的全部碼，每個都查了排除後人數
		verify(fx.roleAdminDao).findPermIdsOfRole("IT_HEAD");
		verify(fx.roleAdminDao).countHolders("EMG_DECIDE", "IT_HEAD", GARY, null);
	}

	@Test
	void 移除_通過檢查_UPDATE1到0_SUCCESS同交易_改自己允許() {
		when(fx.roleAdminDao.findRoleMemberStatus("SYS_ADMIN", HANK)).thenReturn(1);
		MemberChangeResult r = svc.revokeRole(id(HANK), "SYS_ADMIN", HANK, reason("交接完成"));
		assertThat(r.getResult()).isEqualTo("REVOKED");
		verify(fx.roleAdminDao).updateRoleMemberStatus("SYS_ADMIN", HANK, 1, 0, NOW, HANK);
		verify(fx.adminLogDao).insert(eq(NOW), eq(HANK), eq("黃志翰"), eq(AcctConst.ADM_ROLE_REVOKE), eq(HANK),
				eq("{\"roleId\":\"SYS_ADMIN\",\"roleName\":\"角色SYS_ADMIN\",\"targetUserName\":\"黃志翰\",\"before\":{\"status\":1},\"after\":{\"status\":0}}"),
				eq("交接完成"), eq(AcctConst.ADM_SUCCESS));
		assertThat(fx.txm.commits).isEqualTo(1);
	}

	@Test
	void 移除_對象已停用或查無_仍可移除_顯示名退回工號() {
		when(fx.userDao.findById(TESTER)).thenReturn(AuthServiceTest.user(TESTER, 0));
		when(fx.roleAdminDao.findRoleMemberStatus("GOV", TESTER)).thenReturn(1);
		svc.revokeRole(id(HANK), "GOV", TESTER, reason("已離職"));
		verify(fx.roleAdminDao).updateRoleMemberStatus("GOV", TESTER, 1, 0, NOW, HANK);

		when(fx.roleAdminDao.findRoleMemberStatus("GOV", "GHOST")).thenReturn(1);
		svc.revokeRole(id(HANK), "GOV", "GHOST", reason("幽靈列"));
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(fx.adminLogDao).insert(any(), any(), any(), any(), eq("GHOST"), json.capture(), eq("幽靈列"), eq(AcctConst.ADM_SUCCESS));
		assertThat(json.getValue()).contains("\"targetUserName\":\"GHOST\"");
	}

	@Test
	void 移除_UPDATE影響0筆_E22() {
		when(fx.roleAdminDao.findRoleMemberStatus("GOV", GARY)).thenReturn(1);
		when(fx.roleAdminDao.updateRoleMemberStatus("GOV", GARY, 1, 0, NOW, HANK)).thenReturn(0);
		assertStatus(() -> svc.revokeRole(id(HANK), "GOV", GARY, reason("x")), HttpStatus.CONFLICT, AcctMsg.E22);
		verify(fx.adminLogDao, never()).insert(any(), any(), any(), any(), any(), any(), any(), any());
	}

	// ------------------------------------------------------------
	// 執行人對照
	// ------------------------------------------------------------

	/** 只有 SYS_EXECUTOR、沒有 SYS_ROLE 的身分 */
	static com.mpx.infra_manager.common.Identity execAdmin() {
		return TestFixture.idWith(GARY, java.util.Set.of("SYS_EXECUTOR"), List.of());
	}

	@Test
	void 執行人清單_需SYS_EXECUTOR_8類全列含關閉類別_成員分組() {
		MemberRow m = new MemberRow();
		m.setGroupId("HOST");
		m.setUserId(TestFixture.ALEX);
		m.setUserName("陳儀仁");
		m.setUserStatus(1);
		m.setUserInSync(1);
		m.setGrantDate(NOW);
		m.setGrantBy(HANK);
		when(fx.roleAdminDao.findExecMembers()).thenReturn(List.of(m));

		List<AdminExecCatg> catgs = svc.executors(execAdmin());
		assertThat(catgs).hasSize(8);
		assertThat(catgs.get(0).getCatgId()).isEqualTo("HOST");
		assertThat(catgs.get(0).getCatgName()).isEqualTo("主機帳號");
		assertThat(catgs.get(0).getEnabled()).isTrue();
		assertThat(catgs.get(0).getMembers()).hasSize(1);
		assertThat(catgs.get(0).getMembers().get(0).getUserName()).isEqualTo("陳儀仁");
		// 關閉新申請的類別照樣列出、可維護
		assertThat(catgs.get(2).getCatgId()).isEqualTo("APP");
		assertThat(catgs.get(2).getEnabled()).isFalse();
		assertThat(catgs.get(2).getMembers()).isEmpty();
		// 只有 SYS_ROLE（沒有 SYS_EXECUTOR）不能看
		assertStatus(() -> svc.executors(TestFixture.idWith(HANK, java.util.Set.of("SYS_ROLE"), List.of())), HttpStatus.FORBIDDEN,
				AcctMsg.P01);
		verify(fx.adminLogDao, never()).insert(any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void 執行人指派_無權限DENY記catgId_類別不合法400_對象404_關閉類別仍可指派_SUCCESS記類別名() {
		assertStatus(() -> svc.grantExecutor(id(KEVIN), "HOST", grant(GARY, "支援")), HttpStatus.FORBIDDEN, AcctMsg.P01);
		verify(fx.adminLogDao).insert(eq(NOW), eq(KEVIN), any(), eq(AcctConst.ADM_EXEC_GRANT), eq(GARY),
				eq("{\"catgId\":\"HOST\",\"denyCode\":\"NO_PERM\"}"), eq("支援"), eq(AcctConst.ADM_DENY));
		// 過長的類別代碼在 DENY 裡存空
		assertStatus(() -> svc.grantExecutor(id(KEVIN), "X".repeat(11), grant(GARY, "支援")), HttpStatus.FORBIDDEN, AcctMsg.P01);
		verify(fx.adminLogDao).insert(eq(NOW), eq(KEVIN), any(), eq(AcctConst.ADM_EXEC_GRANT), eq(GARY),
				eq("{\"catgId\":null,\"denyCode\":\"NO_PERM\"}"), eq("支援"), eq(AcctConst.ADM_DENY));

		for (String c : List.of("NOPE", "X".repeat(11))) {
			assertStatus(() -> svc.grantExecutor(execAdmin(), c, grant(GARY, "支援")), HttpStatus.BAD_REQUEST,
					AcctMsg.BAD_CODE_PREFIX + "帳號類別");
		}
		when(fx.userDao.findById(TESTER)).thenReturn(AuthServiceTest.user(TESTER, 0));
		assertStatus(() -> svc.grantExecutor(execAdmin(), "HOST", grant(TESTER, "支援")), HttpStatus.NOT_FOUND, AcctMsg.N01);
		verify(fx.roleAdminDao, never()).lockGlobal();

		when(fx.roleAdminDao.findExecMemberStatus("APP", KEVIN)).thenReturn(null);
		MemberChangeResult r = svc.grantExecutor(execAdmin(), "APP", grant(KEVIN, "支援"));
		assertThat(r.getCatgId()).isEqualTo("APP");
		assertThat(r.getRoleId()).isNull();
		assertThat(r.getUserId()).isEqualTo(KEVIN);
		assertThat(r.getResult()).isEqualTo("GRANTED");
		verify(fx.roleAdminDao).insertExecMember("APP", KEVIN, NOW, GARY);
		verify(fx.roleAdminDao, never()).insertRoleMember(any(), any(), any(), any());
		verify(fx.adminLogDao).insert(eq(NOW), eq(GARY), eq("許永成"), eq(AcctConst.ADM_EXEC_GRANT), eq(KEVIN),
				eq("{\"catgId\":\"APP\",\"catgName\":\"應用系統帳號\",\"targetUserName\":\"李凱鈞\",\"before\":{\"status\":null},\"after\":{\"status\":1}}"),
				eq("支援"), eq(AcctConst.ADM_SUCCESS));
		assertThat(fx.txm.commits).isEqualTo(1);
	}

	@Test
	void 執行人移除_在途卡住_E23_DENY的blockedPerms放類別_不查權限持有人() {
		when(fx.roleAdminDao.findExecMemberStatus("HOST", TestFixture.ALEX)).thenReturn(1);
		when(fx.roleAdminDao.countExecutors("HOST", TestFixture.ALEX)).thenReturn(0L);
		when(fx.roleAdminDao.countExecutors("HOST", null)).thenReturn(1L);
		when(fx.roleAdminDao.findInFlightExecByCatg("HOST")).thenReturn(List.of(new InFlightRow("AR20261007-003", "執行人")));
		assertStatus(() -> svc.revokeExecutor(execAdmin(), "HOST", TestFixture.ALEX, reason("離職")), HttpStatus.CONFLICT,
				AcctMsg.E23_PREFIX + "AR20261007-003（執行人）");
		verify(fx.roleAdminDao, never()).updateExecMemberStatus(any(), any(), anyInt(), anyInt(), any(), any());
		verify(fx.roleAdminDao, never()).countHolders(any(), any(), any(), any());
		assertThat(fx.txm.rollbacks).isEqualTo(1);
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(fx.adminLogDao).insert(eq(NOW), eq(GARY), any(), eq(AcctConst.ADM_EXEC_REVOKE), eq(TestFixture.ALEX), json.capture(),
				eq("離職"), eq(AcctConst.ADM_DENY));
		assertThat(json.getValue()).contains("\"catgId\":\"HOST\"").contains("\"denyCode\":\"IN_FLIGHT\"")
				.contains("\"blockedPerms\":[\"HOST\"]").contains("\"blockedTotal\":1").contains("\"blockedIds\":[\"AR20261007-003\"]");
	}

	@Test
	void 執行人移除_通過_UPDATE1到0_SUCCESS_不在名單E22_切換中E25() {
		when(fx.roleAdminDao.findExecMemberStatus("DB", TestFixture.TERRY)).thenReturn(1);
		MemberChangeResult r = svc.revokeExecutor(execAdmin(), "DB", TestFixture.TERRY, reason("調職"));
		assertThat(r.getCatgId()).isEqualTo("DB");
		assertThat(r.getResult()).isEqualTo("REVOKED");
		verify(fx.roleAdminDao).updateExecMemberStatus("DB", TestFixture.TERRY, 1, 0, NOW, GARY);
		verify(fx.adminLogDao).insert(eq(NOW), eq(GARY), any(), eq(AcctConst.ADM_EXEC_REVOKE), eq(TestFixture.TERRY),
				eq("{\"catgId\":\"DB\",\"catgName\":\"資料庫帳號\",\"targetUserName\":\"邱健唐\",\"before\":{\"status\":1},\"after\":{\"status\":0}}"),
				eq("調職"), eq(AcctConst.ADM_SUCCESS));
		assertThat(fx.txm.commits).isEqualTo(1);

		when(fx.roleAdminDao.findExecMemberStatus("DB", GARY)).thenReturn(null);
		assertStatus(() -> svc.revokeExecutor(execAdmin(), "DB", GARY, reason("x")), HttpStatus.CONFLICT, AcctMsg.E22);
		assertStatus(() -> svc.revokeExecutor(impersonate(KEVIN, HANK), "DB", GARY, reason("x")), HttpStatus.CONFLICT, AcctMsg.E25);
		assertStatus(() -> svc.revokeExecutor(execAdmin(), "DB", GARY, null), HttpStatus.BAD_REQUEST, AcctMsg.E10_PREFIX + "原因");
	}
}
