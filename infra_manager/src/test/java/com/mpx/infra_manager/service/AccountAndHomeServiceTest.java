package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增（v2.0 重寫，取代舊版同名測試）：帳號、首頁、待辦單元測試（spec v2.0 §2.4、§4.2、§4.5、§4.13、§6.3、§6.4；
//           驗收 T06⑤、T19、T24、T25）：申請前提含 EXPIRED、在途時 allowedReqTypes 為空；/accounts 需 ACC_LIST_FULL、
//           stateCounts 不套狀態條件；lookup 需完整名稱；lookup/{id} 名稱不符且不可看 → 404；明細他人 403；
//           鈴鐺逐筆相關、daysLeft 可為負；待辦依權限碼與執行人對照、帶 versionNo 與標題；審查條件共用函式
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S8：FIND_STATS 含緊急存取兩張卡的共用計數條件、不依登入者過濾
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-29
// 變更說明: BACKLOG 第 148 項：FIND_STATS 帳號四張與緊急存取兩張各帶可選的個人過濾、巡檢不過濾；
//           stats 依 ACC_EXPIRY_ALL／EMG_VIEW_ALL 決定綁 NULL 或有效身分工號、巡檢區依 INSP_VIEW 或任一看全部
// ============================================================

import static com.mpx.infra_manager.service.RequestServiceTest.assertStatus;
import static com.mpx.infra_manager.support.TestFixture.ALEX;
import static com.mpx.infra_manager.support.TestFixture.GARY;
import static com.mpx.infra_manager.support.TestFixture.KEVIN;
import static com.mpx.infra_manager.support.TestFixture.MICHAEL;
import static com.mpx.infra_manager.support.TestFixture.MOLLY;
import static com.mpx.infra_manager.support.TestFixture.TERRY;
import static com.mpx.infra_manager.support.TestFixture.TESTER;
import static com.mpx.infra_manager.support.TestFixture.TODAY;
import static com.mpx.infra_manager.support.TestFixture.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.AcctMsg;
import com.mpx.infra_manager.dao.AcctDao;
import com.mpx.infra_manager.model.Acct;
import com.mpx.infra_manager.model.AcctListResult;
import com.mpx.infra_manager.model.AcctLookupBrief;
import com.mpx.infra_manager.model.AssetClass;
import com.mpx.infra_manager.model.CodeCountRow;
import com.mpx.infra_manager.model.ExpiringAcct;
import com.mpx.infra_manager.model.TodoItem;
import com.mpx.infra_manager.support.TestFixture;

class AccountAndHomeServiceTest {

	TestFixture fx;

	@BeforeEach
	void setUp() {
		fx = new TestFixture();
	}

	// ------------------------------------------------------------
	// 申請前提（Table 6.5）
	// ------------------------------------------------------------

	@Test
	void 申請前提_含已逾期_在途時不可異動() {
		assertThat(AccountService.allowedReqTypes("APPROVED", false)).containsExactly("MODIFY", "DISABLE", "DELETE");
		assertThat(AccountService.allowedReqTypes("EXPIRED", false)).containsExactly("DISABLE", "DELETE");
		assertThat(AccountService.allowedReqTypes("DISABLED", false)).containsExactly("ENABLE", "DELETE");
		assertThat(AccountService.allowedReqTypes("DELETED", false)).isEmpty();
		assertThat(AccountService.allowedReqTypes("REVIEW", false)).isEmpty();
		assertThat(AccountService.allowedReqTypes("APPROVED", true)).isEmpty();
		assertThat(AccountService.isAllowed("ADD", "APPROVED")).isFalse();
	}

	// ------------------------------------------------------------
	// 帳號
	// ------------------------------------------------------------

	@Test
	void 帳號列表_需ACC_LIST_FULL_計數與篩選() {
		assertStatus(() -> fx.accountService.list(id(TESTER), null, null, null, null, null), HttpStatus.FORBIDDEN, AcctMsg.P01);
		Acct a = fx.acct("AR20261001-001", "DB", AcctConst.AS_APPROVED);
		when(fx.acctDao.findList(any())).thenReturn(new ArrayList<>(List.of(a)));
		when(fx.acctDao.countByState(any())).thenReturn(List.of(cc("APPROVED", 3), cc("DELETED", 1)));
		when(fx.acctDao.countByCatg(any())).thenReturn(List.of(cc("DB", 2)));
		when(fx.acctDao.countExpiring(TODAY)).thenReturn(4L);
		AcctListResult r = fx.accountService.list(id(GARY), " db_% ", null, null, null, "1");
		assertThat(r.getItems().get(0).getAllowedReqTypes()).containsExactly("MODIFY", "DISABLE", "DELETE");
		assertThat(r.getStateCounts()).containsEntry("APPROVED", 3L).containsEntry("DELETED", 1L);
		assertThat(r.getCatgCounts()).containsEntry("DB", 2L);
		assertThat(r.getExpiringCount()).isEqualTo(4L);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> p = ArgumentCaptor.forClass(Map.class);
		verify(fx.acctDao).findList(p.capture());
		assertThat(p.getValue()).containsEntry("keyword", "db\\_\\%").containsEntry("expiring", 1);
		assertStatus(() -> fx.accountService.list(id(GARY), null, null, "VOID", null, null), HttpStatus.BAD_REQUEST,
				AcctMsg.BAD_CODE_PREFIX + "狀態");
	}

	@Test
	void 帳號列表SQL_計數排除自身篩選_預設不含已刪除_到期條件共用() {
		assertThat(AcctDao.COUNT_BY_STATE).doesNotContain(":stateCode").contains(":catgId").contains(":expiring");
		assertThat(AcctDao.COUNT_BY_CATG).doesNotContain(":catgId").contains(":stateCode");
		assertThat(AcctDao.FIND_LIST).contains("A.ACCT_STATE_CODE <> 'DELETED'").contains(AcctDao.EXPIRING_COND);
		assertThat(AcctDao.COUNT_EXPIRING).contains(AcctDao.EXPIRING_COND).doesNotContain(":keyword");
		assertThat(AcctDao.EXPIRING_COND).contains("A.EXPIRY_DATE <= :today + 30").contains("A.ACCT_STATE_CODE = 'EXPIRED'");
		String stats = com.mpx.infra_manager.dao.HomeDao.FIND_STATS;
		assertThat(stats).contains(AcctDao.EXPIRING_COND)
				// S8：緊急存取兩張卡共用計數條件
				.contains(com.mpx.infra_manager.dao.EmgDao.UNDECIDED_COND).contains("AS EMG_UNDECIDED")
				.contains(com.mpx.infra_manager.dao.EmgDao.RETURNED_COND).contains("AS EMG_RETURNED");
		// 第 148 項：帳號四張、緊急存取兩張各帶可選的個人過濾（綁 NULL＝看全部）；巡檢兩張不過濾
		assertThat(stats.split("AS INSP_THIS_MONTH")[0]).containsOnlyOnce("AS ACCT_TOTAL")
				.satisfies(acct -> assertThat(acct.split(com.mpx.infra_manager.dao.HomeDao.ACCT_MINE_COND, -1)).hasSize(5));
		assertThat(stats.split(com.mpx.infra_manager.dao.HomeDao.EMG_MINE_COND, -1)).hasSize(3);
		String insp = stats.substring(stats.indexOf("AS ACCT_PRIV"), stats.indexOf("AS INSP_EXCPT_THIS_MONTH"));
		assertThat(insp).doesNotContain(":acctApplyBy").doesNotContain(":emgApplyBy");
		assertThat(com.mpx.infra_manager.dao.HomeDao.FIND_EXPIRING).contains(AcctDao.EXPIRING_COND);
	}

	@Test
	void 首頁卡片_依權限決定看全部或只看自己_巡檢區依權限顯示() {
		when(fx.homeDao.findStats(any(), any(), any())).thenAnswer(inv -> {
			com.mpx.infra_manager.model.HomeStats s = new com.mpx.infra_manager.model.HomeStats();
			s.setInspThisMonth(7L);
			s.setInspExcptThisMonth(2L);
			return s;
		});
		// 一般人（只有 GENERAL）：帳號與緊急存取都綁自己的工號；沒有 INSP_VIEW → 巡檢數字清空、區塊不顯示
		com.mpx.infra_manager.model.HomeStats tester = fx.homeService.stats(id(TESTER));
		verify(fx.homeDao).findStats(TODAY, TESTER, TESTER);
		assertThat(tester.isAcctAll()).isFalse();
		assertThat(tester.isEmgAll()).isFalse();
		assertThat(tester.isInspVisible()).isFalse();
		assertThat(tester.getInspThisMonth()).isNull();
		assertThat(tester.getInspExcptThisMonth()).isNull();
		// 巡檢人員（KEVIN：GOV＋INSP_STAFF）：帳號與緊急存取仍只看自己，巡檢區看全部
		com.mpx.infra_manager.model.HomeStats kevin = fx.homeService.stats(id(KEVIN));
		verify(fx.homeDao).findStats(TODAY, KEVIN, KEVIN);
		assertThat(kevin.isInspVisible()).isTrue();
		assertThat(kevin.getInspThisMonth()).isEqualTo(7L);
		// 資訊主管（GARY：IT_HEAD）：兩區都看全部（綁 NULL）
		com.mpx.infra_manager.model.HomeStats gary = fx.homeService.stats(id(GARY));
		verify(fx.homeDao).findStats(TODAY, null, null);
		assertThat(gary.isAcctAll()).isTrue();
		assertThat(gary.isEmgAll()).isTrue();
		assertThat(gary.isInspVisible()).isTrue();
		// 主管沒有 INSP_VIEW 也看得到巡檢區（裁示 ③A）；只有其中一個「看全部」時另一區照樣只看自己
		com.mpx.infra_manager.model.HomeStats mgr = fx.homeService.stats(TestFixture.idWith(MICHAEL, java.util.Set.of("ACC_EXPIRY_ALL"), List.of()));
		verify(fx.homeDao).findStats(TODAY, null, MICHAEL);
		assertThat(mgr.isAcctAll()).isTrue();
		assertThat(mgr.isEmgAll()).isFalse();
		assertThat(mgr.isInspVisible()).isTrue();
		assertThat(mgr.getInspExcptThisMonth()).isEqualTo(2L);
		// 切換身分：以代理身分（有效身分）為「我」
		fx.homeService.stats(TestFixture.impersonate(TestFixture.HANK, TESTER));
		verify(fx.homeDao, org.mockito.Mockito.times(2)).findStats(TODAY, TESTER, TESTER);
	}

	@Test
	void lookup_需完整帳號名稱_精確比對() {
		assertStatus(() -> fx.accountService.lookup(id(TESTER), " "), HttpStatus.BAD_REQUEST, AcctMsg.E02_PREFIX + "帳號名稱");
		AcctLookupBrief b = new AcctLookupBrief();
		b.setAcctStateCode("APPROVED");
		b.setInFlight(true);
		when(fx.acctDao.findLookup("db_user01")).thenReturn(List.of(b));
		assertThat(fx.accountService.lookup(id(TESTER), " db_user01 ")).singleElement()
				.satisfies(x -> assertThat(x.getAllowedReqTypes()).isEmpty());
		assertThat(AcctDao.FIND_LOOKUP).contains("A.ACCT_NAME = :acctName").doesNotContain("LIKE")
				.contains("('APPROVED', 'EXPIRED', 'DISABLED')");
	}

	@Test
	void lookupById_名稱相符或可看_否則404_狀態限制() {
		Acct a = fx.acct("AR20261001-001", "DB", AcctConst.AS_APPROVED);
		a.setApplyBy(MOLLY);
		assertStatus(() -> fx.accountService.lookupById(id(TESTER), a.getAcctId(), null), HttpStatus.NOT_FOUND, AcctMsg.N01);
		assertStatus(() -> fx.accountService.lookupById(id(TESTER), a.getAcctId(), "acct_x"), HttpStatus.NOT_FOUND, AcctMsg.N01);
		assertThat(fx.accountService.lookupById(id(TESTER), a.getAcctId(), a.getAcctName()).getAcctId()).isEqualTo(a.getAcctId());
		assertThat(fx.accountService.lookupById(id(MOLLY), a.getAcctId(), null).getAllowedReqTypes()).contains("MODIFY");
		a.setAcctStateCode(AcctConst.AS_REVIEW);
		assertStatus(() -> fx.accountService.lookupById(id(MOLLY), a.getAcctId(), a.getAcctName()), HttpStatus.NOT_FOUND,
				AcctMsg.N01);
	}

	@Test
	void 帳號明細與歷史_他人403_歷史帶標籤() {
		Acct a = fx.acct("AR20261001-001", "DB", AcctConst.AS_APPROVED);
		assertStatus(() -> fx.accountService.detail(id(TESTER), a.getAcctId()), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertStatus(() -> fx.accountService.detail(id(TESTER), "AR20991231-999"), HttpStatus.NOT_FOUND, AcctMsg.N01);
		com.mpx.infra_manager.model.AcctHist h = new com.mpx.infra_manager.model.AcctHist();
		h.setIsSelfAction(1);
		h.setActBy(KEVIN);
		h.setReqApplyBy(KEVIN);
		when(fx.acctHistDao.findByAcct(a.getAcctId())).thenReturn(List.of(h));
		assertThat(fx.accountService.history(id(KEVIN), a.getAcctId()).get(0).getSelfActionLabel())
				.isEqualTo(AcctConst.SELF_LABEL_APPLICANT);
	}

	// ------------------------------------------------------------
	// 首頁、鈴鐺、待辦
	// ------------------------------------------------------------

	@Test
	void 鈴鐺_逐筆相關_無相關者不顯示_剩餘天數可為負() {
		ExpiringAcct a = exp("A", "DB", KEVIN, TODAY.plusDays(10), "APPROVED");
		ExpiringAcct b = exp("B", "HOST", MOLLY, TODAY.minusDays(3), "EXPIRED");
		when(fx.homeDao.findExpiring(TODAY)).thenAnswer(inv -> List.of(copy(a), copy(b)));
		assertThat(fx.homeService.expiring(id(GARY))).extracting(ExpiringAcct::getAcctId).containsExactly("A", "B");
		assertThat(fx.homeService.expiring(id(MICHAEL))).extracting(ExpiringAcct::getAcctId).containsExactly("A");
		assertThat(fx.homeService.expiring(id(TESTER))).isEmpty();
		assertThat(fx.homeService.expiring(id(GARY)).get(1).getDaysLeft()).isEqualTo(-3L);
	}

	@Test
	void 待辦_依關卡權限與執行人對照_帶標題() {
		TodoItem t = new TodoItem();
		t.setReqId("AR20261007-001");
		t.setReqTypeCode("ADD");
		t.setCatgId("DOOR");
		t.setCatgName("機房門禁卡");
		t.setSubmitDate(LocalDateTime.of(2026, 10, 7, 9, 0));
		when(fx.acctReqDao.findTodoApprove(List.of(2))).thenReturn(List.of(t));
		List<TodoItem> alex = fx.homeService.todos(id(ALEX));
		assertThat(alex).singleElement().satisfies(x -> {
			assertThat(x.getActionKind()).isEqualTo("APPROVE");
			assertThat(x.getTitle()).isEqualTo("新增 機房門禁卡：（門禁卡，待發卡）");
		});
		verify(fx.acctReqDao).findTodoExec(List.of("HOST", "APP", "SVC", "NET", "CERT", "DOOR"));
		verify(fx.acctReqDao, never()).findTodoVerify();

		fx.homeService.todos(id(TESTER));
		verify(fx.acctReqDao, never()).findTodoApprove(List.of());
		fx.homeService.todos(id(KEVIN));
		verify(fx.acctReqDao).findTodoApprove(List.of(4));
		verify(fx.acctReqDao).findTodoVerify();
		fx.homeService.todos(id(TERRY));
		verify(fx.acctReqDao).findTodoApprove(List.of(3));
		verify(fx.acctReqDao).findTodoExec(List.of("DB"));
		verify(fx.acctReqDao, never()).findTodoExec(List.of());
	}

	@Test
	void 審查條件共用函式_分類觸發_門禁_特權() {
		AssetClass h01 = TestFixture.assetClasses().get(0);
		AssetClass h02 = TestFixture.assetClasses().get(1);
		assertThat(ReviewRule.matches(h01, "DB", 0)).isTrue();
		assertThat(ReviewRule.matches(h02, "DOOR", 0)).isTrue();
		assertThat(ReviewRule.matches(h02, "DB", 1)).isTrue();
		assertThat(ReviewRule.matches(null, "DB", 0)).isFalse();
		assertThat(ReviewRule.shouldWrite("ADD", true, false, null)).isTrue();
		assertThat(ReviewRule.shouldWrite("MODIFY", true, false, TODAY)).isTrue();
		assertThat(ReviewRule.shouldWrite("MODIFY", true, true, null)).isTrue();
		assertThat(ReviewRule.shouldWrite("ENABLE", true, true, TODAY)).isFalse();
		assertThat(ReviewRule.shouldWrite("DISABLE", true, false, null)).isFalse();
		assertThat(ReviewRule.shouldWrite("ADD", false, false, null)).isFalse();
		assertThat(ReviewRule.nextReview(TODAY)).isEqualTo(TODAY.plusDays(180));
	}

	static ExpiringAcct exp(String id, String catg, String applyBy, java.time.LocalDate date, String state) {
		ExpiringAcct e = new ExpiringAcct();
		e.setAcctId(id);
		e.setCatgId(catg);
		e.setApplyBy(applyBy);
		e.setExpiryDate(date.atStartOfDay());
		e.setAcctStateCode(state);
		return e;
	}

	static ExpiringAcct copy(ExpiringAcct s) {
		ExpiringAcct e = exp(s.getAcctId(), s.getCatgId(), s.getApplyBy(), s.getExpiryDate().toLocalDate(), s.getAcctStateCode());
		return e;
	}

	static CodeCountRow cc(String code, long n) {
		CodeCountRow r = new CodeCountRow();
		r.setCode(code);
		r.setCnt(n);
		return r;
	}
}
