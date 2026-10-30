package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: 新增：定期權限審查查詢單元測試（BACKLOG 第 158 項 R2；SA §5.10）
//           清單需 REVIEW_LIST 否則 403（裁示 ⑬A）、三種主管角色都看全部、狀態非法 400、四種狀態原樣轉交、
//           關鍵字修剪與 LIKE 跳脫、「今天」取自 Clock；Java 端「有審查狀態」與 ReviewRule.matches 的範圍判斷同口徑
// ============================================================

import static com.mpx.infra_manager.service.RequestServiceTest.assertStatus;
import static com.mpx.infra_manager.support.TestFixture.GARY;
import static com.mpx.infra_manager.support.TestFixture.MOLLY;
import static com.mpx.infra_manager.support.TestFixture.NOW;
import static com.mpx.infra_manager.support.TestFixture.TERRY;
import static com.mpx.infra_manager.support.TestFixture.TESTER;
import static com.mpx.infra_manager.support.TestFixture.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.AcctMsg;
import com.mpx.infra_manager.model.Acct;
import com.mpx.infra_manager.support.TestFixture;

class ReviewServiceTest {

	static final LocalDate TODAY = NOW.toLocalDate();

	TestFixture fx;
	ReviewService svc;

	@BeforeEach
	void setUp() {
		fx = new TestFixture();
		svc = fx.reviewService;
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
