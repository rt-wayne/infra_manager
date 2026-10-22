package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-10
// 變更說明: 新增：登入、token 時效、切換身分單元測試（spec v2.0 §2.1、§2.5、§6.2；驗收 T01、T02、T13）
//           登入：工號不存在／停用仍做雜湊計算後 A01、鎖定 A02（即使密碼正確）、密碼錯累加失敗次數、成功發 token（DB 只存 SHA-256）；
//           修改：查詢後才被鎖定（成功或失敗計數 UPDATE 影響 0 筆）→ A02、不發 token、不補 dummyHash；
//           token：未帶／亂碼／已登出／閒置超過 30 分／登入超過 8 小時／使用者停用皆 401 A03，LAST_ACCESS_DATE 每 60 秒最多寫一次，
//           代理身分停用自動切回；切換身分：無 SYS_IMPERSONATE 403 並寫 DENY、不可切自己、切換與軌跡同一交易、
//           切換中仍以實際使用者權限判斷可改切別人；切回未切換中 409 E20；/me 回 telNo、email（有效身分）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 28 項：閒置上限改 480 分，邊界測試由 30／31 分改 480／481 分、resolve 閒置案例由 40 分改 481 分
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 40 項：/me 另回 deptName（有效身分），切換身分案例一併斷言
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 1：AdminLogDao.insert 多 reason 參數，切換身分軌跡斷言補 null
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: BACKLOG 第 124 項階段 3 回合 1（裁示 D0B、E1）：同步表查無——登入比照停用 A01（使用者裁示 A）、token 401 A03 且不寫 LAST_ACCESS、
//           代理身分查無自動切回、切換身分對象查無 404；顯示名只有中文名（期望字串「英文名 (中文名)」改中文名）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 5 回合 2：/me 部門改由 UserDao.findPrimaryDept 當下查（切換中為代理身分；查無為 null）；AcctUser 夾具不再設部門
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 6（裁示 ⑫A）：/me 不再回分機，拿掉 telNo 斷言與夾具的分機
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 7 回合 1（登入改走 WPX）：登入測試全部重寫，WpxAuthClient 用 mock——
//           明顯無效輸入 A01 且不送 WPX；WPX 三分類對應 A01／A04／A05 且不查本系統；成功寫最後登入時間、發 token、已有帳號不看資格；
//           首次登入資格符合自動建立後重查、資格不符 A04、並發撞主鍵重查；停用、同步表查無 A04；寫最後登入時間影響 0 筆 A04。
//           刪除密碼雜湊、失敗計數、鎖定相關案例
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.AcctMsg;
import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.common.TokenCodec;
import com.mpx.infra_manager.dao.AdminLogDao;
import com.mpx.infra_manager.dao.LoginTokenDao;
import com.mpx.infra_manager.dao.UserDao;
import com.mpx.infra_manager.model.AcctUser;
import com.mpx.infra_manager.model.LoginResult;
import com.mpx.infra_manager.model.LoginToken;
import com.mpx.infra_manager.model.MeInfo;
import com.mpx.infra_manager.model.request.ImpersonateForm;
import com.mpx.infra_manager.model.request.LoginForm;
import com.mpx.infra_manager.support.TestFixture;

class AuthServiceTest {

	TestFixture fx;
	UserDao userDao;
	LoginTokenDao tokenDao;
	AdminLogDao adminLogDao;
	WpxAuthClient wpx;
	TokenIdentityProvider provider;
	AuthService auth;

	/** 不在 TestFixture 名單內、本系統還沒有帳號列的工號（首次登入情境） */
	static final String NEWBIE = "0000099";

	@BeforeEach
	void setUp() {
		fx = new TestFixture();
		userDao = mock(UserDao.class);
		tokenDao = mock(LoginTokenDao.class);
		adminLogDao = mock(AdminLogDao.class);
		wpx = mock(WpxAuthClient.class);
		provider = new TokenIdentityProvider(tokenDao, userDao, fx.permissionService, fx.clock);
		auth = new AuthService(userDao, tokenDao, adminLogDao, wpx, new TokenCodec(), provider, fx.permissionService, fx.tx,
				fx.clock);
		// 預設 WPX 驗證通過；個別案例再改
		when(wpx.verify(anyString(), anyString())).thenReturn(wpxResult(WpxAuthClient.Outcome.SUCCESS, 0));
		for (String u : TestFixture.NAMES.keySet()) {
			when(userDao.findById(u)).thenReturn(user(u, 1));
			when(fx.permDao.findPerms(u)).thenReturn(new ArrayList<>(TestFixture.permsOf(u)));
			when(fx.permDao.findRoleIds(u)).thenReturn(TestFixture.USER_ROLES.get(u));
			when(fx.permDao.findExecCatgs(u)).thenReturn(TestFixture.USER_EXEC.getOrDefault(u, List.of()));
		}
		when(tokenDao.setActAs(anyString(), any(), anyString(), any())).thenReturn(1);
		when(userDao.loginOk(anyString(), any())).thenReturn(1);
	}

	// ------------------------------------------------------------
	// 登入
	// ------------------------------------------------------------

	@Test
	void 登入成功_WPX驗過_回token_DB只存SHA256_寫最後登入時間_已有帳號不看資格() {
		LoginResult r = auth.login(login(" " + KEVIN + " ", "pw"));
		verify(wpx).verify(KEVIN, "pw");
		ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
		verify(tokenDao).insert(hash.capture(), eq(KEVIN), eq(NOW.plusHours(8)), eq(NOW));
		assertThat(hash.getValue()).hasSize(64).isNotEqualTo(r.getToken()).isEqualTo(TokenCodec.sha256Hex(r.getToken()));
		verify(userDao).loginOk(KEVIN, NOW);
		// 裁示 D4A：已有帳號列者不再判斷資格、不走建立
		verify(userDao, never()).createOnFirstLogin(anyString(), any());
		assertThat(r.getMe().getUserId()).isEqualTo(KEVIN);
		assertThat(r.getMe().getPermissions()).contains("ACC_APPLY", "APPR_G4", "VERIFY_G7", "INSP_VIEW");
	}

	@Test
	void 登入_工號空白或超長_密碼空白_A01且不送WPX() {
		String tooLong = "1".repeat(AuthService.USER_ID_MAX + 1);
		for (LoginForm f : List.of(login(null, "pw"), login("   ", "pw"), login(tooLong, "pw"), login(KEVIN, null),
				login(KEVIN, ""))) {
			assertStatus(() -> auth.login(f), HttpStatus.UNAUTHORIZED, AcctMsg.A01);
		}
		assertStatus(() -> auth.login(null), HttpStatus.UNAUTHORIZED, AcctMsg.A01);
		verify(wpx, never()).verify(any(), any());
		verify(userDao, never()).findById(anyString());
		verify(tokenDao, never()).insert(any(), any(), any(), any());
	}

	/** 裁示 ⑨：WPX 沒驗過就不查本系統（D5A：判斷順序 WPX 先、本系統後） */
	@Test
	void 登入_WPX未通過_帳密錯A01_帳號無法使用A04_服務異常A05_皆不查本系統() {
		when(wpx.verify(KEVIN, "bad")).thenReturn(wpxResult(WpxAuthClient.Outcome.BAD_CREDENTIAL, -11));
		assertStatus(() -> auth.login(login(KEVIN, "bad")), HttpStatus.UNAUTHORIZED, AcctMsg.A01);
		when(wpx.verify(KEVIN, "locked")).thenReturn(wpxResult(WpxAuthClient.Outcome.ACCOUNT_UNUSABLE, -3));
		assertStatus(() -> auth.login(login(KEVIN, "locked")), HttpStatus.UNAUTHORIZED, AcctMsg.A04);
		when(wpx.verify(KEVIN, "down")).thenReturn(wpxResult(WpxAuthClient.Outcome.SERVICE_ERROR, null));
		assertStatus(() -> auth.login(login(KEVIN, "down")), HttpStatus.UNAUTHORIZED, AcctMsg.A05);
		verify(userDao, never()).findById(anyString());
		verify(userDao, never()).createOnFirstLogin(anyString(), any());
		verify(userDao, never()).loginOk(anyString(), any());
		verify(tokenDao, never()).insert(any(), any(), any(), any());
	}

	/** 裁示 ①B、D3A：本系統沒有帳號列 → 依資格建立（建立句本身含資格判斷）→ 重查後照常登入 */
	@Test
	void 登入_首次登入資格符合_自動建立後重查_發token() {
		when(userDao.findById(NEWBIE)).thenReturn(null, user(NEWBIE, 1));
		when(userDao.createOnFirstLogin(NEWBIE, NOW)).thenReturn(1);
		LoginResult r = auth.login(login(NEWBIE, "pw"));
		verify(userDao).createOnFirstLogin(NEWBIE, NOW);
		verify(userDao, org.mockito.Mockito.times(2)).findById(NEWBIE);
		verify(userDao).loginOk(NEWBIE, NOW);
		verify(tokenDao).insert(anyString(), eq(NEWBIE), eq(NOW.plusHours(8)), eq(NOW));
		assertThat(r.getMe().getUserId()).isEqualTo(NEWBIE);
	}

	@Test
	void 登入_首次登入資格不符_A04_不發token() {
		// 未設定的 findById 回 null、createOnFirstLogin 回 0（建立句因資格不符影響 0 筆）
		assertStatus(() -> auth.login(login(NEWBIE, "pw")), HttpStatus.UNAUTHORIZED, AcctMsg.A04);
		verify(userDao).createOnFirstLogin(NEWBIE, NOW);
		verify(userDao, never()).loginOk(anyString(), any());
		verify(tokenDao, never()).insert(any(), any(), any(), any());
	}

	@Test
	void 登入_首次登入同時建立撞主鍵_重查後照常登入() {
		when(userDao.findById(NEWBIE)).thenReturn(null, user(NEWBIE, 1));
		when(userDao.createOnFirstLogin(NEWBIE, NOW)).thenThrow(new DuplicateKeyException("ORA-00001"));
		LoginResult r = auth.login(login(NEWBIE, "pw"));
		verify(tokenDao).insert(anyString(), eq(NEWBIE), eq(NOW.plusHours(8)), eq(NOW));
		assertThat(r.getMe().getUserId()).isEqualTo(NEWBIE);
	}

	/** 裁示 D5A：WPX 驗過但本系統停用、或同步表查無 →「帳號無法使用」；已有帳號列不走建立 */
	@Test
	void 登入_WPX驗過但停用或同步表查無_A04() {
		when(userDao.findById(TESTER)).thenReturn(user(TESTER, 0));
		assertStatus(() -> auth.login(login(TESTER, "pw")), HttpStatus.UNAUTHORIZED, AcctMsg.A04);
		AcctUser gone = user(HANK, 1);
		gone.setInSync(0);
		when(userDao.findById(HANK)).thenReturn(gone);
		assertStatus(() -> auth.login(login(HANK, "pw")), HttpStatus.UNAUTHORIZED, AcctMsg.A04);
		verify(userDao, never()).createOnFirstLogin(anyString(), any());
		verify(userDao, never()).loginOk(anyString(), any());
		verify(tokenDao, never()).insert(any(), any(), any(), any());
	}

	@Test
	void 登入_寫最後登入時間影響0筆_A04不發token() {
		when(userDao.loginOk(KEVIN, NOW)).thenReturn(0);
		assertStatus(() -> auth.login(login(KEVIN, "pw")), HttpStatus.UNAUTHORIZED, AcctMsg.A04);
		verify(tokenDao, never()).insert(any(), any(), any(), any());
	}

	// ------------------------------------------------------------
	// token 時效
	// ------------------------------------------------------------

	@Test
	void token有效條件() {
		assertThat(TokenIdentityProvider.isValid(token(NOW.minusMinutes(480), NOW.plusHours(1), null, 1), NOW)).isTrue();
		assertThat(TokenIdentityProvider.isValid(token(NOW.minusMinutes(481), NOW.plusHours(1), null, 1), NOW)).isFalse();
		assertThat(TokenIdentityProvider.isValid(token(NOW, NOW, null, 1), NOW)).isFalse();
		assertThat(TokenIdentityProvider.isValid(token(NOW, NOW.plusHours(1), NOW.minusMinutes(1), 1), NOW)).isFalse();
		assertThat(TokenIdentityProvider.isValid(token(NOW, NOW.plusHours(1), null, 0), NOW)).isFalse();
		assertThat(TokenIdentityProvider.isValid(null, NOW)).isFalse();
	}

	@Test
	void resolve_未帶亂碼查無皆401_A03() {
		assertStatus(() -> provider.resolve(null), HttpStatus.UNAUTHORIZED, AcctMsg.A03);
		assertStatus(() -> provider.resolve("Bearer garbage"), HttpStatus.UNAUTHORIZED, AcctMsg.A03);
		when(tokenDao.find(TokenCodec.sha256Hex("tok"))).thenReturn(token(NOW.minusMinutes(481), NOW.plusHours(1), null, 1));
		assertStatus(() -> provider.resolve("Bearer tok"), HttpStatus.UNAUTHORIZED, AcctMsg.A03);
	}

	@Test
	void resolve_節流寫LAST_ACCESS_代理身分停用自動切回() {
		String hash = TokenCodec.sha256Hex("tok");
		LoginToken t = token(NOW.minusSeconds(30), NOW.plusHours(1), null, 1);
		t.setUserId(HANK);
		when(tokenDao.find(hash)).thenReturn(t);
		Identity id = provider.resolve("Bearer tok");
		assertThat(id.getUserId()).isEqualTo(HANK);
		verify(tokenDao, never()).touch(any(), any());

		t.setLastAccessDate(NOW.minusSeconds(90));
		t.setActAsUserId(GARY);
		t.setActAsStatus(0);
		Identity back = provider.resolve("Bearer tok");
		verify(tokenDao).touch(hash, NOW);
		verify(tokenDao).setActAs(hash, null, HANK, NOW);
		assertThat(back.isImpersonating()).isFalse();
		verify(adminLogDao, never()).insert(any(), any(), any(), any(), any(), any(), any(), any());

		t.setActAsStatus(1);
		Identity as = provider.resolve("Bearer tok");
		assertThat(as.isImpersonating()).isTrue();
		assertThat(as.getUserId()).isEqualTo(GARY);
		assertThat(as.getRealUserId()).isEqualTo(HANK);
		assertThat(as.getPermissions()).contains("APPR_G5").doesNotContain("SYS_IMPERSONATE");
	}

	/** 第 124 項裁示 E1：本人同步表查無 → 401（不寫 LAST_ACCESS）；代理身分查無 → 比照停用自動切回 */
	@Test
	void resolve_同步表查無_本人401_代理身分自動切回() {
		String hash = TokenCodec.sha256Hex("tok");
		LoginToken t = token(NOW.minusSeconds(90), NOW.plusHours(1), null, 1);
		t.setUserId(HANK);
		t.setUserInSync(0);
		when(tokenDao.find(hash)).thenReturn(t);
		assertStatus(() -> provider.resolve("Bearer tok"), HttpStatus.UNAUTHORIZED, AcctMsg.A03);
		verify(tokenDao, never()).touch(any(), any());

		t.setUserInSync(1);
		t.setActAsUserId(GARY);
		t.setActAsStatus(1);
		t.setActAsInSync(0);
		Identity back = provider.resolve("Bearer tok");
		verify(tokenDao).setActAs(hash, null, HANK, NOW);
		assertThat(back.isImpersonating()).isFalse();
		assertThat(back.getUserId()).isEqualTo(HANK);
	}

	// ------------------------------------------------------------
	// 切換身分
	// ------------------------------------------------------------

	@Test
	void 切換身分_無權限403並寫DENY() {
		ImpersonateForm f = target(GARY);
		assertStatus(() -> auth.impersonate(id(KEVIN), f), HttpStatus.FORBIDDEN, AcctMsg.P01);
		verify(adminLogDao).insert(NOW, KEVIN, "李凱鈞", AcctConst.ADM_IMPERSONATE, GARY, null, null, AcctConst.ADM_DENY);
		verify(tokenDao, never()).setActAs(any(), any(), any(), any());
	}

	@Test
	void 切換身分_成功_同一交易寫token與軌跡_不可切自己() {
		assertStatus(() -> auth.impersonate(id(HANK), target(HANK)), HttpStatus.BAD_REQUEST, AcctMsg.IMPERSONATE_SELF);
		when(userDao.findPrimaryDept(GARY)).thenReturn("資訊部" + GARY);
		MeInfo me = auth.impersonate(id(HANK), target(GARY));
		verify(tokenDao).setActAs("hash-" + HANK, GARY, HANK, NOW);
		verify(adminLogDao).insert(eq(NOW), eq(HANK), eq("黃志翰"), eq(AcctConst.ADM_IMPERSONATE), eq(GARY),
				eq("{\"targetUserName\":\"許永成\"}"), isNull(), eq(AcctConst.ADM_SUCCESS));
		assertThat(fx.txm.commits).isEqualTo(1);
		assertThat(me.getUserId()).isEqualTo(GARY);
		assertThat(me.getRealUserId()).isEqualTo(HANK);
		assertThat(me.getImpersonating()).isTrue();
		assertThat(me.getCanImpersonate()).isFalse();
		assertThat(me.getEmail()).isEqualTo(GARY + "@example.com");
		// 部門＝代理身分（有效身分）在同步表的主要部門，/me 當下查
		assertThat(me.getDeptName()).isEqualTo("資訊部" + GARY);
		verify(userDao, never()).findPrimaryDept(HANK);
	}

	/** 第 124 項階段 5 回合 2：/me 部門當下查同步表；查無回 null（前端申請單位欄留空自填） */
	@Test
	void me_部門讀同步表主要部門_查無為null() {
		when(userDao.findPrimaryDept(HANK)).thenReturn("資訊部");
		assertThat(auth.me(id(HANK)).getDeptName()).isEqualTo("資訊部");
		when(userDao.findPrimaryDept(HANK)).thenReturn(null);
		assertThat(auth.me(id(HANK)).getDeptName()).isNull();
	}

	/** 第 124 項裁示 E1：切換對象同步表查無比照停用 404 */
	@Test
	void 切換身分_對象同步表查無_404() {
		AcctUser gone = user(GARY, 1);
		gone.setInSync(0);
		when(userDao.findById(GARY)).thenReturn(gone);
		assertStatus(() -> auth.impersonate(id(HANK), target(GARY)), HttpStatus.NOT_FOUND, AcctMsg.N01);
		verify(tokenDao, never()).setActAs(any(), any(), any(), any());
	}

	@Test
	void 切換中_以實際使用者權限判斷_可改切別人_也可查名單() {
		Identity asGary = impersonate(HANK, GARY);
		auth.impersonate(asGary, target(KEVIN));
		verify(tokenDao).setActAs("hash-" + HANK, KEVIN, HANK, NOW);
		auth.users(asGary);
		verify(userDao).findActiveWithRoles(HANK);
	}

	@Test
	void 切回_未切換中409E20_切換中寫IMPERSONATE_END() {
		assertStatus(() -> auth.endImpersonate(id(HANK)), HttpStatus.CONFLICT, AcctMsg.E20);
		MeInfo me = auth.endImpersonate(impersonate(HANK, GARY));
		verify(tokenDao).setActAs(eq("hash-" + HANK), isNull(), eq(HANK), eq(NOW));
		verify(adminLogDao).insert(eq(NOW), eq(HANK), any(), eq(AcctConst.ADM_IMPERSONATE_END), eq(GARY), any(), isNull(),
				eq(AcctConst.ADM_SUCCESS));
		assertThat(me.getUserId()).isEqualTo(HANK);
		assertThat(me.getCanImpersonate()).isTrue();
	}

	@Test
	void 名單_無權限403() {
		assertStatus(() -> auth.users(id(KEVIN)), HttpStatus.FORBIDDEN, AcctMsg.P01);
	}

	@Test
	void 登出_寫LOGOUT_DATE() {
		auth.logout(id(KEVIN));
		verify(tokenDao).logout("hash-" + KEVIN, KEVIN, NOW);
	}

	// ------------------------------------------------------------

	static AcctUser user(String id, int status) {
		AcctUser u = new AcctUser();
		u.setUserId(id);
		u.setUserName(TestFixture.NAMES.get(id));
		u.setInSync(1);
		u.setEmail(id + "@example.com");
		u.setStatus(status);
		return u;
	}

	static WpxAuthClient.Result wpxResult(WpxAuthClient.Outcome outcome, Integer code) {
		return new WpxAuthClient.Result(outcome, code);
	}

	static LoginToken token(LocalDateTime lastAccess, LocalDateTime expire, LocalDateTime logout, int userStatus) {
		LoginToken t = new LoginToken();
		t.setUserId(KEVIN);
		t.setLastAccessDate(lastAccess);
		t.setExpireDate(expire);
		t.setLogoutDate(logout);
		t.setUserStatus(userStatus);
		t.setUserInSync(1);
		t.setActAsInSync(1);
		return t;
	}

	static LoginForm login(String userId, String pwd) {
		LoginForm f = new LoginForm();
		f.setUserId(userId);
		f.setPassword(pwd);
		return f;
	}

	static ImpersonateForm target(String userId) {
		ImpersonateForm f = new ImpersonateForm();
		f.setTargetUserId(userId);
		return f;
	}
}
