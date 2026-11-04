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
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: BACKLOG 第 158 項 R5 第 3 單位：帳號明細走 acctDao.findDetail（綁今天）、原樣帶出 DAO 算的 reviewStateCode；lookup／歷史以外不呼叫 findDetail
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: BACKLOG 第 158 項 R6：待辦的定期審查列——有 REVIEW_SIGN_INFRA 的看到 Infra 未簽的、簽過的不列、另一位簽核人（有效身分）是我的不列、
//           另一位是我代簽的（實際操作人是我、有效身分不是）照列、雙簽核權限者同帳號兩列、沒權限的人不查 findTodo；審查列接在申請單後面；欄位與標題
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: BACKLOG 第 158 項 R8：FIND_STATS 定期審查兩張卡用 ReviewDao.REVIEW_STATE_EXPR＋JOIN_ASSET_CLASS、帶 REVIEW_MINE_COND、不過濾帳號狀態；
//           stats 依 REVIEW_LIST 決定第四個參數綁 NULL 或工號、回 reviewAll，只有 REVIEW_LIST 不讓巡檢區顯示、數字原樣帶出
// ============================================================

import static com.mpx.infra_manager.service.RequestServiceTest.assertStatus;
import static com.mpx.infra_manager.support.TestFixture.ALEX;
import static com.mpx.infra_manager.support.TestFixture.GARY;
import static com.mpx.infra_manager.support.TestFixture.HANK;
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
import com.mpx.infra_manager.model.ReviewItem;
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
		// 審查必1：片段之間必須是帶空白的 " AND "（text block 單行片段曾被剝成 NULLAND）
		assertThat(stats).contains(com.mpx.infra_manager.dao.EmgDao.UNDECIDED_COND + " AND " + com.mpx.infra_manager.dao.HomeDao.EMG_MINE_COND)
				.contains(com.mpx.infra_manager.dao.EmgDao.RETURNED_COND + " AND " + com.mpx.infra_manager.dao.HomeDao.EMG_MINE_COND)
				.contains(AcctDao.EXPIRING_COND + " AND " + com.mpx.infra_manager.dao.HomeDao.ACCT_MINE_COND)
				.doesNotContain("NULLAND").doesNotContain("'DELETED'AND").doesNotContain("'REVIEW'AND");
		String insp = stats.substring(stats.indexOf("AS ACCT_PRIV"), stats.indexOf("AS INSP_EXCPT_THIS_MONTH"));
		assertThat(insp).doesNotContain(":acctApplyBy").doesNotContain(":emgApplyBy").doesNotContain(":reviewApplyBy");
		assertThat(com.mpx.infra_manager.dao.HomeDao.FIND_EXPIRING).contains(AcctDao.EXPIRING_COND);
		// R8：定期審查兩張卡——狀態用清單同一句 REVIEW_STATE_EXPR、同一個資產分類 JOIN、各帶個人過濾；不另抄狀態判斷、不過濾帳號狀態
		String review = stats.substring(stats.indexOf("AS EMG_RETURNED"));
		assertThat(review).contains("AS REVIEW_OVERDUE").contains("AS REVIEW_ABNORMAL")
				.contains(com.mpx.infra_manager.dao.ReviewDao.REVIEW_STATE_EXPR + " = 'OVERDUE' AND " + com.mpx.infra_manager.dao.HomeDao.REVIEW_MINE_COND)
				.contains(com.mpx.infra_manager.dao.ReviewDao.REVIEW_STATE_EXPR + " = 'ABNORMAL' AND " + com.mpx.infra_manager.dao.HomeDao.REVIEW_MINE_COND)
				.doesNotContain("A.STATUS").doesNotContain("ACCT_STATE_CODE = 'APPROVED' AND A.")
				.satisfies(r -> assertThat(r.split(com.mpx.infra_manager.dao.ReviewDao.JOIN_ASSET_CLASS, -1)).hasSize(3))
				.satisfies(r -> assertThat(r.split(com.mpx.infra_manager.dao.HomeDao.REVIEW_MINE_COND, -1)).hasSize(3));
		assertThat(stats.split(com.mpx.infra_manager.dao.HomeDao.REVIEW_MINE_COND, -1)).hasSize(3);
	}

	@Test
	void 首頁卡片_依權限決定看全部或只看自己_巡檢區依權限顯示() {
		when(fx.homeDao.findStats(any(), any(), any(), any())).thenAnswer(inv -> {
			com.mpx.infra_manager.model.HomeStats s = new com.mpx.infra_manager.model.HomeStats();
			s.setInspThisMonth(7L);
			s.setInspExcptThisMonth(2L);
			s.setReviewOverdue(3L);
			s.setReviewAbnormal(1L);
			return s;
		});
		// 一般人（只有 GENERAL）：帳號、緊急存取、定期審查都綁自己的工號；沒有 INSP_VIEW → 巡檢數字清空、區塊不顯示
		com.mpx.infra_manager.model.HomeStats tester = fx.homeService.stats(id(TESTER));
		verify(fx.homeDao).findStats(TODAY, TESTER, TESTER, TESTER);
		assertThat(tester.isAcctAll()).isFalse();
		assertThat(tester.isEmgAll()).isFalse();
		assertThat(tester.isReviewAll()).isFalse();
		assertThat(tester.isInspVisible()).isFalse();
		assertThat(tester.getInspThisMonth()).isNull();
		assertThat(tester.getInspExcptThisMonth()).isNull();
		// 定期審查數字原樣帶出（一般人也有「我的」兩張卡，不像巡檢區整區隱藏）
		assertThat(tester.getReviewOverdue()).isEqualTo(3L);
		assertThat(tester.getReviewAbnormal()).isEqualTo(1L);
		// 巡檢人員（KEVIN：GOV＋INSP_STAFF）：帳號、緊急存取、定期審查仍只看自己，巡檢區看全部
		com.mpx.infra_manager.model.HomeStats kevin = fx.homeService.stats(id(KEVIN));
		verify(fx.homeDao).findStats(TODAY, KEVIN, KEVIN, KEVIN);
		assertThat(kevin.isInspVisible()).isTrue();
		assertThat(kevin.isReviewAll()).isFalse();
		assertThat(kevin.getInspThisMonth()).isEqualTo(7L);
		// 資訊主管（GARY：IT_HEAD，有 REVIEW_LIST）：三區都看全部（綁 NULL）
		com.mpx.infra_manager.model.HomeStats gary = fx.homeService.stats(id(GARY));
		verify(fx.homeDao).findStats(TODAY, null, null, null);
		assertThat(gary.isAcctAll()).isTrue();
		assertThat(gary.isEmgAll()).isTrue();
		assertThat(gary.isReviewAll()).isTrue();
		assertThat(gary.isInspVisible()).isTrue();
		// 主管沒有 INSP_VIEW 也看得到巡檢區（裁示 ③A）；只有其中一個「看全部」時另兩區照樣只看自己
		com.mpx.infra_manager.model.HomeStats mgr = fx.homeService.stats(TestFixture.idWith(MICHAEL, java.util.Set.of("ACC_EXPIRY_ALL"), List.of()));
		verify(fx.homeDao).findStats(TODAY, null, MICHAEL, MICHAEL);
		assertThat(mgr.isAcctAll()).isTrue();
		assertThat(mgr.isEmgAll()).isFalse();
		assertThat(mgr.isReviewAll()).isFalse();
		assertThat(mgr.isInspVisible()).isTrue();
		assertThat(mgr.getInspExcptThisMonth()).isEqualTo(2L);
		// 只有 REVIEW_LIST（R8 ⑬A）：定期審查看全部，帳號與緊急存取仍只看自己；REVIEW_LIST 不讓巡檢區顯示
		com.mpx.infra_manager.model.HomeStats reviewer = fx.homeService.stats(TestFixture.idWith(MICHAEL, java.util.Set.of("REVIEW_LIST"), List.of()));
		verify(fx.homeDao).findStats(TODAY, MICHAEL, MICHAEL, null);
		assertThat(reviewer.isReviewAll()).isTrue();
		assertThat(reviewer.isAcctAll()).isFalse();
		assertThat(reviewer.isInspVisible()).isFalse();
		// 切換身分：以代理身分（有效身分）為「我」
		fx.homeService.stats(TestFixture.impersonate(TestFixture.HANK, TESTER));
		verify(fx.homeDao, org.mockito.Mockito.times(2)).findStats(TODAY, TESTER, TESTER, TESTER);
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

	/** 第 158 項 R5（①A）：明細走 findDetail 並綁今天，DAO 算出的審查狀態原樣回傳；lookup/{id} 仍走 findById、不算狀態 */
	@Test
	void 帳號明細_走findDetail綁今天_帶出審查狀態() {
		Acct a = fx.acct("AR20261001-001", "DB", AcctConst.AS_APPROVED);
		a.setReviewStateCode(AcctConst.RVS_DUE);
		Acct out = fx.accountService.detail(id(KEVIN), a.getAcctId());
		assertThat(out.getReviewStateCode()).isEqualTo(AcctConst.RVS_DUE);
		assertThat(out.getAllowedReqTypes()).contains("MODIFY");
		verify(fx.acctDao).findDetail(a.getAcctId(), TODAY);
		verify(fx.acctDao, never()).findById(a.getAcctId());

		fx.accountService.lookupById(id(KEVIN), a.getAcctId(), a.getAcctName());
		verify(fx.acctDao).findById(a.getAcctId());
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
	void 待辦_定期審查列_依簽核權限與同人規則_接在申請單後面() {
		// 三筆待審帳號：A 沒人簽；B Infra 由 MOLLY 切換成 TERRY 代簽（有效身分 TERRY、實際操作人 MOLLY）；
		// C 資訊治理已由 TERRY（有效身分）簽、實際操作人 TESTER
		ReviewItem a = reviewTodoItem("AR20261001-001", "DOOR", "機房門禁卡", null, LocalDateTime.of(2026, 9, 1, 0, 0), "OVERDUE");
		ReviewItem b = reviewTodoItem("AR20261001-002", "DB", "資料庫帳號", "sa", LocalDateTime.of(2026, 10, 20, 0, 0), "DUE");
		b.setReviewId(7L);
		b.setReviewVersionNo(1);
		b.setInfraSignBy(TERRY);
		b.setInfraSignActBy(MOLLY);
		ReviewItem c = reviewTodoItem("AR20261001-003", "HOST", "主機帳號", "root", LocalDateTime.of(2026, 10, 25, 0, 0), "DUE");
		c.setReviewId(8L);
		c.setReviewVersionNo(1);
		c.setGovSignBy(TERRY);
		c.setGovSignActBy(TESTER);
		when(fx.reviewDao.findTodo(TODAY)).thenReturn(List.of(a, b, c));
		TodoItem req = new TodoItem();
		req.setReqId("AR20261007-009");
		req.setReqTypeCode("ADD");
		req.setCatgId("DB");
		req.setCatgName("資料庫帳號");
		req.setSubmitDate(LocalDateTime.of(2026, 10, 7, 9, 0));
		when(fx.acctReqDao.findTodoApprove(List.of(3))).thenReturn(List.of(req));

		// TERRY（INFRA_MGR）：A 可簽 Infra；B 自己簽過不列；C 另一位主管的有效身分是我 → 不列（實際操作人是誰都一樣）
		List<TodoItem> terry = fx.homeService.todos(id(TERRY));
		assertThat(terry).extracting(TodoItem::getReqId).containsExactly("AR20261007-009", "AR20261001-001");
		TodoItem t = terry.get(1);
		assertThat(t.getSource()).isEqualTo("REVIEW");
		assertThat(t.getActionKind()).isEqualTo("REVIEW");
		assertThat(t.getAcctId()).isEqualTo("AR20261001-001");
		assertThat(t.getReviewId()).isNull();
		assertThat(t.getHalf()).isEqualTo("INFRA");
		assertThat(t.getStageName()).isEqualTo("Infra 主管待簽");
		assertThat(t.getTitle()).isEqualTo("定期審查 機房門禁卡：（門禁卡，待發卡）");
		assertThat(t.getVersionNo()).isZero();
		assertThat(t.getCatgId()).isEqualTo("DOOR");
		assertThat(t.getApplyBy()).isEqualTo(KEVIN);
		assertThat(t.getReview()).isSameAs(a);
		assertThat(t.getSubmitDate()).isNull();

		// MOLLY（GOV_MGR）：A、B 可簽資訊治理——B 的 Infra 是 MOLLY 自己代 TERRY 簽的，只比有效身分（②A）所以放行；
		// C 自己這個身分已有人簽 → 不列
		List<TodoItem> molly = fx.homeService.todos(id(MOLLY));
		assertThat(molly).extracting(TodoItem::getReqId).containsExactly("AR20261001-001", "AR20261001-002");
		assertThat(molly).extracting(TodoItem::getHalf).containsOnly("GOV");
		assertThat(molly.get(1).getStageName()).isEqualTo("資訊治理主管待簽");
		assertThat(molly.get(1).getTitle()).isEqualTo("定期審查 資料庫帳號：sa");
		assertThat(molly.get(1).getVersionNo()).isEqualTo(1);
		assertThat(molly.get(1).getReviewId()).isEqualTo(7L);

		// 同時有兩個簽核權限的人（初始資料沒有）：A 兩列（先 Infra 後資訊治理）、B 只剩資訊治理、C 只剩 Infra（另一位是 TERRY 不是我）
		List<TodoItem> both = fx.homeService.todos(TestFixture.idWith(HANK,
				java.util.Set.of(AcctConst.PERM_REVIEW_SIGN_INFRA, AcctConst.PERM_REVIEW_SIGN_GOV), List.of()));
		assertThat(both).extracting(TodoItem::getReqId, TodoItem::getHalf).containsExactly(
				org.assertj.core.groups.Tuple.tuple("AR20261001-001", "INFRA"), org.assertj.core.groups.Tuple.tuple("AR20261001-001", "GOV"),
				org.assertj.core.groups.Tuple.tuple("AR20261001-002", "GOV"), org.assertj.core.groups.Tuple.tuple("AR20261001-003", "INFRA"));

		// TESTER（沒有簽核權限）不查；GARY（IT_HEAD，只有 REVIEW_LIST）也不查 → findTodo 只被 TERRY、MOLLY、雙權限者叫了 3 次
		fx.homeService.todos(id(TESTER));
		fx.homeService.todos(id(GARY));
		verify(fx.reviewDao, org.mockito.Mockito.times(3)).findTodo(TODAY);
	}

	private static ReviewItem reviewTodoItem(String acctId, String catgId, String catgName, String acctName, LocalDateTime next, String state) {
		ReviewItem r = new ReviewItem();
		r.setAcctId(acctId);
		r.setCatgId(catgId);
		r.setCatgName(catgName);
		r.setAcctName(acctName);
		r.setIsPriv(0);
		r.setApplyBy(KEVIN);
		r.setApplyByName("李凱鈞");
		r.setNextReviewDate(next);
		r.setReviewStateCode(state);
		return r;
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
		assertThat(ReviewRule.shouldWrite("MODIFY", true, true, TODAY)).isFalse();
		// 重新啟用同新增（第 158 項裁示 ⑫B）：舊內容符合、已有審查日也重寫；新內容不符合才不寫
		assertThat(ReviewRule.shouldWrite("ENABLE", true, true, TODAY)).isTrue();
		assertThat(ReviewRule.shouldWrite("ENABLE", false, true, TODAY)).isFalse();
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
