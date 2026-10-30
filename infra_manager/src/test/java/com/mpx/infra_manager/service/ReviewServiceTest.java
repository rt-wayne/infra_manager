package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: 新增：定期權限審查查詢單元測試（BACKLOG 第 158 項 R2；SA §5.10）
//           清單需 REVIEW_LIST 否則 403（裁示 ⑬A）、三種主管角色都看全部、狀態非法 400、四種狀態原樣轉交、
//           關鍵字修剪與 LIKE 跳脫、「今天」取自 Clock；Java 端「有審查狀態」與 ReviewRule.matches 的範圍判斷同口徑
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: BACKLOG 第 158 項 R3a 簽核：簽核角色非法 400、無對應 REVIEW_SIGN_* 403、缺版本號 E02（都不查 DB）；
//           查無 404；正常／異常／不在範圍 409（②A、⑨A）；首簽建輪（版本須為 0、DUE_DATE＝下次應審查日或今天、只簽本半、不寫歷史）；
//           首簽撞 IND_IM_ACCT_REVIEW_02 重讀改 UPDATE（⑮A）、其他唯一鍵衝突原樣往上拋；已有一輪：版本不符 409、
//           同人兩半 409（有效身分與實際操作人各一例，⑪A）、同半重複 409 E08；第二半簽完 → DONE、主表上次＝今天／下次＋180、
//           歷史 REVIEW_DONE 帶 REVIEW_ID 與兩半簽核人；主表 0 筆 → 500 回滾
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: BACKLOG 第 158 項 R3a 審查修正：①A 交易內先鎖帳號列再讀狀態（InOrder）、進交易前的 400／403／E02 不鎖；
//           M2 ③A IS_SELF_ACTION 兩半任一簽核人是原申請人就為 1（第一半是、第二半是各一例），都不是為 0
// ============================================================

import static com.mpx.infra_manager.service.RequestServiceTest.assertStatus;
import static com.mpx.infra_manager.support.TestFixture.GARY;
import static com.mpx.infra_manager.support.TestFixture.KEVIN;
import static com.mpx.infra_manager.support.TestFixture.MOLLY;
import static com.mpx.infra_manager.support.TestFixture.NAMES;
import static com.mpx.infra_manager.support.TestFixture.NOW;
import static com.mpx.infra_manager.support.TestFixture.TERRY;
import static com.mpx.infra_manager.support.TestFixture.TESTER;
import static com.mpx.infra_manager.support.TestFixture.WENSEN;
import static com.mpx.infra_manager.support.TestFixture.id;
import static com.mpx.infra_manager.support.TestFixture.impersonate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.AcctMsg;
import com.mpx.infra_manager.model.Acct;
import com.mpx.infra_manager.model.AcctHist;
import com.mpx.infra_manager.model.ReviewActionResult;
import com.mpx.infra_manager.model.ReviewItem;
import com.mpx.infra_manager.model.request.ReviewSignForm;
import com.mpx.infra_manager.support.TestFixture;

class ReviewServiceTest {

	static final LocalDate TODAY = NOW.toLocalDate();
	static final String ACCT = "AR20261001-001";
	static final String DUP_OPEN = "ORA-00001: unique constraint (RD_USER.IND_IM_ACCT_REVIEW_02) violated";

	TestFixture fx;
	ReviewService svc;

	@BeforeEach
	void setUp() {
		fx = new TestFixture();
		svc = fx.reviewService;
	}

	// ---- 簽核共用

	/** 待複審、下次應審查日＝今天＋10、原申請人 KEVIN、尚無進行中一輪 */
	ReviewItem due() {
		ReviewItem it = new ReviewItem();
		it.setAcctId(ACCT);
		it.setApplyBy(KEVIN);
		it.setReviewStateCode(AcctConst.RVS_DUE);
		it.setNextReviewDate(TODAY.plusDays(10).atStartOfDay());
		return it;
	}

	/** 已有進行中一輪（版本 v），Infra 半已由 TERRY 簽 */
	ReviewItem infraSigned(int v) {
		ReviewItem it = due();
		it.setReviewId(55L);
		it.setReviewVersionNo(v);
		it.setInfraSignBy(TERRY);
		it.setInfraSignByName(NAMES.get(TERRY));
		it.setInfraSignActBy(TERRY);
		it.setInfraSignDate(NOW.minusHours(1));
		return it;
	}

	static ReviewSignForm form(String half, Integer v) {
		ReviewSignForm f = new ReviewSignForm();
		f.setHalf(half);
		f.setVersionNo(v);
		return f;
	}

	@Test
	void 簽核_角色非法400_無對應權限403_缺版本號E02_都不查DB() {
		assertStatus(() -> svc.sign(id(TERRY), ACCT, form(null, 0)), HttpStatus.BAD_REQUEST, AcctMsg.BAD_CODE_PREFIX + "簽核角色");
		assertStatus(() -> svc.sign(id(TERRY), ACCT, form("ALL", 0)), HttpStatus.BAD_REQUEST, AcctMsg.BAD_CODE_PREFIX + "簽核角色");
		assertStatus(() -> svc.sign(id(TERRY), ACCT, null), HttpStatus.BAD_REQUEST, AcctMsg.BAD_CODE_PREFIX + "簽核角色");
		// TERRY 只有 INFRA 半、MOLLY 只有治理半、GARY（IT_HEAD）兩半都沒有
		assertStatus(() -> svc.sign(id(TERRY), ACCT, form("GOV", 0)), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertStatus(() -> svc.sign(id(MOLLY), ACCT, form("INFRA", 0)), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertStatus(() -> svc.sign(id(GARY), ACCT, form("INFRA", 0)), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertStatus(() -> svc.sign(id(GARY), ACCT, form("GOV", 0)), HttpStatus.FORBIDDEN, AcctMsg.P01);
		assertStatus(() -> svc.sign(id(TERRY), ACCT, form("INFRA", null)), HttpStatus.BAD_REQUEST, AcctMsg.E02_PREFIX + "版本號");
		verify(fx.reviewDao, never()).findByAcct(any(), any());
		verify(fx.acctDao, never()).lockForReview(any());
	}

	@Test
	void 簽核_查無404_不在待複審或逾期未審409() {
		assertStatus(() -> svc.sign(id(TERRY), ACCT, form("INFRA", 0)), HttpStatus.NOT_FOUND, AcctMsg.N01);
		for (String state : new String[] { AcctConst.RVS_NORMAL, AcctConst.RVS_ABNORMAL, null }) {
			ReviewItem it = due();
			it.setReviewStateCode(state);
			when(fx.reviewDao.findByAcct(ACCT, TODAY)).thenReturn(it);
			assertStatus(() -> svc.sign(id(TERRY), ACCT, form("INFRA", 0)), HttpStatus.CONFLICT, AcctMsg.REVIEW_NOT_SIGNABLE);
		}
		verify(fx.reviewDao, never()).nextReviewId();
		verify(fx.reviewDao, never()).signHalf(anyLong(), anyInt(), any(), any(), any(), any());
		assertThat(fx.hists).isEmpty();
	}

	@Test
	void 首簽_先鎖帳號列再讀狀態_建本輪只簽本半_DUE_DATE為下次應審查日_不寫歷史_回OPEN版本0() {
		when(fx.reviewDao.findByAcct(ACCT, TODAY)).thenReturn(due());
		ReviewActionResult r = svc.sign(id(TERRY), ACCT, form("INFRA", 0));
		// ①A：鎖在讀狀態之前，讀到的狀態與那一輪才算數
		InOrder order = inOrder(fx.acctDao, fx.reviewDao);
		order.verify(fx.acctDao).lockForReview(ACCT);
		order.verify(fx.reviewDao).findByAcct(ACCT, TODAY);
		verify(fx.reviewDao).insertOpen(101L, ACCT, TODAY.plusDays(10), "INFRA", TERRY, TERRY, NOW);
		verify(fx.reviewDao, never()).signHalf(anyLong(), anyInt(), any(), any(), any(), any());
		verify(fx.acctDao, never()).updateReviewDates(any(), any(), any(), any(), any());
		assertThat(fx.hists).isEmpty();
		assertThat(r.getReviewId()).isEqualTo(101L);
		assertThat(r.getReviewStateCode()).isEqualTo("OPEN");
		assertThat(r.getVersionNo()).isZero();

		// 逾期未審且下次應審查日為空（⑥A）：DUE_DATE 寫今天；切換身分代簽時簽核人＝有效身分、實際操作人＝真正的人
		ReviewItem overdue = due();
		overdue.setReviewStateCode(AcctConst.RVS_OVERDUE);
		overdue.setNextReviewDate(null);
		when(fx.reviewDao.findByAcct(ACCT, TODAY)).thenReturn(overdue);
		svc.sign(impersonate(TESTER, MOLLY), ACCT, form("GOV", 0));
		verify(fx.reviewDao).insertOpen(101L, ACCT, TODAY, "GOV", MOLLY, TESTER, NOW);
	}

	@Test
	void 首簽_前端以為沒有一輪卻帶版本_409() {
		when(fx.reviewDao.findByAcct(ACCT, TODAY)).thenReturn(due());
		assertStatus(() -> svc.sign(id(TERRY), ACCT, form("INFRA", 2)), HttpStatus.CONFLICT, AcctMsg.E08);
		verify(fx.reviewDao, never()).insertOpen(anyLong(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void 首簽撞進行中索引_重讀那一輪改走UPDATE_另一半已簽就直接完成() {
		// 第一次讀：沒有一輪；INSERT 撞 IND_02；重讀：MOLLY 剛簽了治理半（版本 0）
		ReviewItem govSigned = due();
		govSigned.setReviewId(77L);
		govSigned.setReviewVersionNo(0);
		govSigned.setGovSignBy(MOLLY);
		govSigned.setGovSignByName(NAMES.get(MOLLY));
		govSigned.setGovSignActBy(MOLLY);
		govSigned.setGovSignDate(NOW);
		when(fx.reviewDao.findByAcct(ACCT, TODAY)).thenReturn(due(), govSigned);
		when(fx.reviewDao.insertOpen(anyLong(), any(), any(), any(), any(), any(), any())).thenThrow(new DuplicateKeyException(DUP_OPEN));

		ReviewActionResult r = svc.sign(id(TERRY), ACCT, form("INFRA", 0));
		verify(fx.reviewDao).signHalf(77L, 0, "INFRA", TERRY, TERRY, NOW);
		verify(fx.reviewDao).markDone(77L, 1, TODAY.plusDays(180), NOW, TERRY);
		assertThat(r.getReviewId()).isEqualTo(77L);
		assertThat(r.getReviewStateCode()).isEqualTo("DONE");
		assertThat(r.getVersionNo()).isEqualTo(2);
		assertThat(fx.hists).hasSize(1);
	}

	@Test
	void 首簽_非進行中索引的唯一鍵衝突_原樣往上拋不誤判() {
		when(fx.reviewDao.findByAcct(ACCT, TODAY)).thenReturn(due());
		when(fx.reviewDao.insertOpen(anyLong(), any(), any(), any(), any(), any(), any()))
				.thenThrow(new DuplicateKeyException("ORA-00001: unique constraint (RD_USER.PK_IM_ACCT_REVIEW) violated"));
		assertThatThrownBy(() -> svc.sign(id(TERRY), ACCT, form("INFRA", 0))).isInstanceOf(DuplicateKeyException.class);
		verify(fx.reviewDao, never()).signHalf(anyLong(), anyInt(), any(), any(), any(), any());
	}

	@Test
	void 已有一輪_版本不符409_同半重複簽409_同人兩半409() {
		when(fx.reviewDao.findByAcct(ACCT, TODAY)).thenReturn(infraSigned(3));
		// 版本不符：不碰 DAO
		assertStatus(() -> svc.sign(id(MOLLY), ACCT, form("GOV", 2)), HttpStatus.CONFLICT, AcctMsg.E08);
		verify(fx.reviewDao, never()).signHalf(anyLong(), anyInt(), any(), any(), any(), any());
		// 同半重複：UPDATE 0 筆（INFRA_SIGN_BY IS NULL 不成立）→ 409 E08
		when(fx.reviewDao.signHalf(anyLong(), anyInt(), any(), any(), any(), any())).thenReturn(0);
		assertStatus(() -> svc.sign(id(WENSEN), ACCT, form("INFRA", 3)), HttpStatus.CONFLICT, AcctMsg.E08);
		verify(fx.reviewDao).signHalf(55L, 3, "INFRA", WENSEN, WENSEN, NOW);
		assertThat(fx.hists).isEmpty();
	}

	@Test
	void 同人兩半_有效身分相同或實際操作人相同都409() {
		// Infra 半由 TERRY 簽；TERRY 若也有治理半權限（這裡用自訂權限）不得再簽治理半
		when(fx.reviewDao.findByAcct(ACCT, TODAY)).thenReturn(infraSigned(0));
		assertStatus(() -> svc.sign(TestFixture.idWith(TERRY, java.util.Set.of(AcctConst.PERM_REVIEW_SIGN_GOV), List.of()), ACCT,
				form("GOV", 0)), HttpStatus.CONFLICT, AcctMsg.REVIEW_SAME_SIGNER);
		// 實際操作人 TERRY 切換成 MOLLY 代簽治理半：有效身分不同，但實際操作人相同 → 409
		assertStatus(() -> svc.sign(impersonate(TERRY, MOLLY), ACCT, form("GOV", 0)), HttpStatus.CONFLICT, AcctMsg.REVIEW_SAME_SIGNER);
		// 反過來：Infra 半是 TESTER 切成 TERRY 代簽的，TESTER 本人（有效身分 MOLLY）再簽治理半也擋
		ReviewItem byProxy = infraSigned(0);
		byProxy.setInfraSignActBy(TESTER);
		when(fx.reviewDao.findByAcct(ACCT, TODAY)).thenReturn(byProxy);
		assertStatus(() -> svc.sign(impersonate(TESTER, MOLLY), ACCT, form("GOV", 0)), HttpStatus.CONFLICT, AcctMsg.REVIEW_SAME_SIGNER);
		verify(fx.reviewDao, never()).signHalf(anyLong(), anyInt(), any(), any(), any(), any());
	}

	@Test
	void 第二半簽完_本輪DONE_主表上次今天下次加180_歷史REVIEW_DONE帶REVIEW_ID() {
		when(fx.reviewDao.findByAcct(ACCT, TODAY)).thenReturn(infraSigned(3));
		ReviewActionResult r = svc.sign(impersonate(TESTER, MOLLY), ACCT, form("GOV", 3));

		verify(fx.reviewDao).signHalf(55L, 3, "GOV", MOLLY, TESTER, NOW);
		verify(fx.reviewDao).markDone(55L, 4, TODAY.plusDays(180), NOW, TESTER);
		verify(fx.acctDao).updateReviewDates(ACCT, TODAY, TODAY.plusDays(180), NOW, TESTER);
		assertThat(r.getReviewStateCode()).isEqualTo("DONE");
		assertThat(r.getVersionNo()).isEqualTo(5);

		assertThat(fx.hists).hasSize(1);
		AcctHist h = fx.hists.get(0);
		assertThat(h.getAcctId()).isEqualTo(ACCT);
		assertThat(h.getReviewId()).isEqualTo(55L);
		assertThat(h.getReqId()).isNull();
		assertThat(h.getEmgId()).isNull();
		assertThat(h.getStageId()).isNull();
		assertThat(h.getSourceCode()).isEqualTo("REVIEW");
		assertThat(h.getActionCode()).isEqualTo("REVIEW_DONE");
		assertThat(h.getActBy()).isEqualTo(TESTER);
		assertThat(h.getActAsId()).isEqualTo(MOLLY);
		assertThat(h.getActDate()).isEqualTo(NOW);
		assertThat(h.getIsSelfAction()).isZero();
		assertThat(h.getResultDescr()).startsWith("定期審查完成（雙簽）；Infra 半 " + NAMES.get(TERRY) + " 於 2026-10-07 09:30:00")
				.contains("；治理半 " + NAMES.get(MOLLY) + " 於 2026-10-07 10:30:00")
				.contains("；上次審查日改為 2026-10-07、下次應審查日 2027-04-05");
	}

	@Test
	void 第二半簽完_兩半任一簽核人是原申請人時IS_SELF_ACTION為1_標籤自行審查() {
		// 第二半（治理半 MOLLY）是原申請人
		ReviewItem it = infraSigned(0);
		it.setApplyBy(MOLLY);
		when(fx.reviewDao.findByAcct(ACCT, TODAY)).thenReturn(it);
		svc.sign(id(MOLLY), ACCT, form("GOV", 0));
		assertThat(fx.hists).hasSize(1);
		AcctHist h = fx.hists.get(0);
		assertThat(h.getIsSelfAction()).isEqualTo(1);
		// 歷史畫面：來源 REVIEW 的自核列顯示「自行審查」，不是拿第二半操作人去比而得的「同人兼任」
		h.setReqApplyBy(MOLLY);
		assertThat(RequestService.selfLabel(h)).isEqualTo(AcctConst.SELF_LABEL_REVIEW);

		// 第一半（Infra 半 TERRY）是原申請人、第二半不是：兩半判準一致，也標 1（M2 ③A）
		fx.hists.clear();
		ReviewItem byApplicant = infraSigned(0);
		byApplicant.setApplyBy(TERRY);
		when(fx.reviewDao.findByAcct(ACCT, TODAY)).thenReturn(byApplicant);
		svc.sign(id(MOLLY), ACCT, form("GOV", 0));
		assertThat(fx.hists).hasSize(1);
		h = fx.hists.get(0);
		assertThat(h.getIsSelfAction()).isEqualTo(1);
		h.setReqApplyBy(TERRY);
		assertThat(RequestService.selfLabel(h)).isEqualTo(AcctConst.SELF_LABEL_REVIEW);
	}

	@Test
	void 第二半簽完_主表日期0筆_500整筆回滾不留歷史() {
		when(fx.reviewDao.findByAcct(ACCT, TODAY)).thenReturn(infraSigned(0));
		when(fx.acctDao.updateReviewDates(any(), any(), any(), any(), any())).thenReturn(0);
		assertStatus(() -> svc.sign(id(MOLLY), ACCT, form("GOV", 0)), HttpStatus.INTERNAL_SERVER_ERROR, null);
		assertThat(fx.hists).isEmpty();
	}

	@Test
	void 清單_需REVIEW_LIST_否則403不查DB() {
		assertStatus(() -> svc.list(id(TESTER), null, null), HttpStatus.FORBIDDEN, null);
		verify(fx.reviewDao, never()).findList(any(), any(), any());
	}

	@Test
	void 清單_三種主管都看全部_篩選值修剪後轉交DAO_今天取自Clock() {
		svc.list(id(TERRY), " db ", " DUE ");
		verify(fx.reviewDao).findList(eq("db"), eq("DUE"), eq(TODAY));

		svc.list(id(MOLLY), null, " ");
		verify(fx.reviewDao).findList(isNull(), isNull(), eq(TODAY));

		svc.list(id(GARY), "50%_a", null);
		verify(fx.reviewDao).findList(eq("50\\%\\_a"), isNull(), eq(TODAY));
	}

	@Test
	void 清單_狀態只收四種審查狀態_其他400() {
		for (String bad : List.of("PENDING", "OPEN", "NONE")) {
			assertStatus(() -> svc.list(id(TERRY), null, bad), HttpStatus.BAD_REQUEST, AcctMsg.BAD_CODE_PREFIX + "狀態");
		}
		verify(fx.reviewDao, never()).findList(any(), any(), any());
		for (String ok : AcctConst.REVIEW_STATES) {
			svc.list(id(TERRY), null, ok);
			verify(fx.reviewDao).findList(isNull(), eq(ok), eq(TODAY));
		}
	}

	/** Java 端「有審查狀態」（可看帳號第六條）與 ReviewRule.matches 同口徑：APPROVED 且符合條件，或掛旗標且未刪除 */
	@Test
	void 有審查狀態_與ReviewRule範圍同口徑() {
		PermissionService perm = fx.permissionService;
		for (String assetClassId : List.of("H01", "H02", "H07", "S01", "ZZ")) {
			for (String catgId : List.of("DB", "DOOR")) {
				for (int isPriv : List.of(0, 1)) {
					Acct a = fx.acct("AR20261001-" + assetClassId + catgId + isPriv, catgId, AcctConst.AS_APPROVED);
					a.setAssetClassId(assetClassId);
					a.setIsPriv(isPriv);
					boolean expected = ReviewRule.matches(fx.codeService.findAssetClass(assetClassId), catgId, isPriv);
					assertThat(perm.hasReviewState(a)).as("%s/%s/%s", assetClassId, catgId, isPriv).isEqualTo(expected);
					a.setAcctStateCode(AcctConst.AS_EXPIRED);
					assertThat(perm.hasReviewState(a)).as("已逾期不在範圍（裁示 ⑦A）").isFalse();
					a.setIsReviewFlag(1);
					assertThat(perm.hasReviewState(a)).as("掛旗標就有狀態").isTrue();
					a.setAcctStateCode(AcctConst.AS_DELETED);
					assertThat(perm.hasReviewState(a)).as("已刪除不算").isFalse();
				}
			}
		}
		// 審查觸發分類至少要有一個、非觸發也至少一個，否則上面的迴圈沒有真的比到兩種結果
		assertThat(fx.codeService.findAssetClass("H01").getReviewTrigger()).isTrue();
		assertThat(fx.codeService.findAssetClass("H02").getReviewTrigger()).isFalse();
	}
}
