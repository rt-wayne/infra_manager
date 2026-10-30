package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：權限判斷與資料範圍單元測試（spec v2.0 §2.3、§2.4、§4.5、§4.11、§4.13；驗收 T05、T06、T24）
//           can（GENERAL 權限、關卡權限取自 IM_ACCT_APPR_STAGE.PERM_ID）、此刻能處理（關卡 2～5／6 執行人／7）、
//           可看申請單四條件、可看帳號五條件、鈴鐺相關性、到期提醒範圍（含 EXPIRED、today＋30 邊界）；
//           權限 SQL 以參數綁定 GENERAL、角色與權限 STATUS＝1 才計
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: BACKLOG 第 158 項 R2：可看帳號第六條——有 REVIEW_LIST 的人可看審查範圍內（特權／審查觸發分類／門禁卡，且 APPROVED）
//           或掛旗標且未刪除的帳號；停用分類視同不符；沒有 REVIEW_LIST 的人不因此可看
// ============================================================

import static com.mpx.infra_manager.support.TestFixture.ALEX;
import static com.mpx.infra_manager.support.TestFixture.CATHY;
import static com.mpx.infra_manager.support.TestFixture.GARY;
import static com.mpx.infra_manager.support.TestFixture.KEVIN;
import static com.mpx.infra_manager.support.TestFixture.MICHAEL;
import static com.mpx.infra_manager.support.TestFixture.MOLLY;
import static com.mpx.infra_manager.support.TestFixture.SAGE;
import static com.mpx.infra_manager.support.TestFixture.TERRY;
import static com.mpx.infra_manager.support.TestFixture.TESTER;
import static com.mpx.infra_manager.support.TestFixture.TODAY;
import static com.mpx.infra_manager.support.TestFixture.WENSEN;
import static com.mpx.infra_manager.support.TestFixture.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.dao.PermDao;
import com.mpx.infra_manager.model.Acct;
import com.mpx.infra_manager.model.AcctReq;
import com.mpx.infra_manager.model.AcctReqDetail;
import com.mpx.infra_manager.model.ApprStage;
import com.mpx.infra_manager.support.TestFixture;

class PermissionServiceTest {

	TestFixture fx;
	PermissionService perm;
	Map<Integer, ApprStage> stages;

	@BeforeEach
	void setUp() {
		fx = new TestFixture();
		perm = fx.permissionService;
		stages = fx.codeService.stageMap();
	}

	@Test
	void GENERAL權限自動擁有_程式不判斷角色名稱() {
		assertThat(perm.can(id(TESTER), AcctConst.PERM_ACC_APPLY)).isTrue();
		assertThat(perm.can(id(TESTER), AcctConst.PERM_ACC_LIST_FULL)).isFalse();
		assertThat(PermDao.FIND_PERMS).contains("RP.ROLE_ID = :generalRoleId").contains("R.STATUS = 1")
				.contains("P.STATUS = 1").contains("RP.STATUS = 1").contains("RU.STATUS = 1").doesNotContain("'GENERAL'");
	}

	@Test
	void 此刻能處理_依關卡權限與執行人對照() {
		AcctReq r = req("REVIEW", 2, "DB");
		assertThat(perm.canHandleNow(id(ALEX), r, stages)).isTrue();
		assertThat(perm.canHandleNow(id(SAGE), r, stages)).isFalse();
		r.setCurrStageId(4);
		assertThat(perm.canHandleNow(id(KEVIN), r, stages)).isTrue();
		r.setCurrStageId(5);
		assertThat(perm.canHandleNow(id(GARY), r, stages)).isTrue();
		AcctReq e = req("EXEC", 6, "DB");
		assertThat(perm.canHandleNow(id(TERRY), e, stages)).isTrue();
		assertThat(perm.canHandleNow(id(MICHAEL), e, stages)).isTrue();
		assertThat(perm.canHandleNow(id(SAGE), e, stages)).isFalse();
		AcctReq v = req("VERIFY", 7, "DB");
		assertThat(perm.canHandleNow(id(CATHY), v, stages)).isTrue();
		assertThat(perm.canHandleNow(id(MICHAEL), v, stages)).isFalse();
		assertThat(perm.canHandleNow(id(CATHY), req("APPROVED", null, "DB"), stages)).isFalse();
	}

	@Test
	void 可看申請單_申請人_清單權限_此刻能處理_曾操作過() {
		AcctReqDetail r = req("REVIEW", 2, "DB");
		assertThat(perm.canViewRequest(id(KEVIN), r, stages)).isTrue();
		assertThat(perm.canViewRequest(id(MOLLY), r, stages)).isTrue();
		assertThat(perm.canViewRequest(id(ALEX), r, stages)).isTrue();
		assertThat(perm.canViewRequest(id(TESTER), r, stages)).isFalse();
		when(fx.acctHistDao.countReqActor(r.getReqId(), TESTER)).thenReturn(1L);
		assertThat(perm.canViewRequest(id(TESTER), r, stages)).isTrue();
	}

	@Test
	void 可看帳號_五條件() {
		Acct a = fx.acct("AR20261001-001", "DB", AcctConst.AS_APPROVED);
		a.setApplyBy(KEVIN);
		assertThat(perm.canViewAccount(id(GARY), a, stages, TODAY)).as("ACC_LIST_FULL").isTrue();
		assertThat(perm.canViewAccount(id(KEVIN), a, stages, TODAY)).as("原新增申請人").isTrue();
		assertThat(perm.canViewAccount(id(TESTER), a, stages, TODAY)).isFalse();
		when(fx.acctReqDao.countAcctInvolved(a.getAcctId(), TESTER)).thenReturn(1L);
		assertThat(perm.canViewAccount(id(TESTER), a, stages, TODAY)).as("曾送過申請／曾操作").isTrue();

		// 在途單此刻能處理
		AcctReqDetail inFlight = req("REVIEW", 2, "DB");
		when(fx.acctReqDao.findInFlightByAcct(a.getAcctId())).thenReturn(inFlight);
		assertThat(perm.canViewAccount(id(ALEX), a, stages, TODAY)).isTrue();

		// 到期提醒範圍：執行人可看；範圍外不可
		Acct b = fx.acct("AR20261001-002", "DB", AcctConst.AS_EXPIRED);
		b.setApplyBy(KEVIN);
		assertThat(perm.canViewAccount(id(MICHAEL), b, stages, TODAY)).isTrue();
		Acct h = fx.acct("AR20261001-003", "HOST", AcctConst.AS_EXPIRED);
		assertThat(perm.canViewAccount(id(MICHAEL), h, stages, TODAY)).isFalse();
		assertThat(perm.canViewAccount(id(TERRY), h, stages, TODAY)).as("ACC_EXPIRY_ALL").isTrue();
	}

	/** 第 158 項 R2：WENSEN＝INFRA_MGR（有 REVIEW_LIST、ACC_EXPIRY_ALL），沒有 ACC_LIST_FULL、不是任何類別執行人 */
	@Test
	void 可看帳號_第六條_REVIEW_LIST看審查範圍內或掛旗標的帳號() {
		// H02 非審查觸發、非特權、DB 類別、永久有效 → 不在審查範圍也不在到期範圍
		Acct a = fx.acct("AR20261001-004", "DB", AcctConst.AS_APPROVED);
		assertThat(perm.canViewAccount(id(WENSEN), a, stages, TODAY)).as("不在範圍").isFalse();
		a.setIsPriv(1);
		assertThat(perm.canViewAccount(id(WENSEN), a, stages, TODAY)).as("特權").isTrue();
		a.setIsPriv(0);
		a.setAssetClassId("H01");
		assertThat(perm.canViewAccount(id(WENSEN), a, stages, TODAY)).as("審查觸發分類").isTrue();
		a.setAssetClassId("ZZ");
		assertThat(perm.canViewAccount(id(WENSEN), a, stages, TODAY)).as("分類查無（含停用）視同不符").isFalse();
		Acct door = fx.acct("AR20261001-005", "DOOR", AcctConst.AS_APPROVED);
		assertThat(perm.canViewAccount(id(WENSEN), door, stages, TODAY)).as("門禁卡").isTrue();

		// 範圍只看 APPROVED；掛旗標的停用帳號仍可看（異常不分狀態）、已刪除不算
		a.setAssetClassId("H01");
		a.setAcctStateCode(AcctConst.AS_DISABLED);
		assertThat(perm.canViewAccount(id(WENSEN), a, stages, TODAY)).as("已停用不在範圍").isFalse();
		a.setIsReviewFlag(1);
		assertThat(perm.canViewAccount(id(WENSEN), a, stages, TODAY)).as("掛旗標").isTrue();
		a.setAcctStateCode(AcctConst.AS_DELETED);
		assertThat(perm.canViewAccount(id(WENSEN), a, stages, TODAY)).as("已刪除不算").isFalse();

		// 沒有 REVIEW_LIST 的人不因此可看
		Acct p = fx.acct("AR20261001-006", "DB", AcctConst.AS_APPROVED);
		p.setIsPriv(1);
		assertThat(perm.canViewAccount(id(TESTER), p, stages, TODAY)).isFalse();
	}

	@Test
	void 鈴鐺相關性() {
		assertThat(perm.expiringRelated(id(GARY), KEVIN, "HOST")).isTrue();
		assertThat(perm.expiringRelated(id(KEVIN), KEVIN, "HOST")).isTrue();
		assertThat(perm.expiringRelated(id(MICHAEL), KEVIN, "DB")).isTrue();
		assertThat(perm.expiringRelated(id(MICHAEL), KEVIN, "HOST")).isFalse();
		assertThat(perm.expiringRelated(id(TESTER), KEVIN, "DB")).isFalse();
	}

	@Test
	void 到期提醒範圍_含已逾期_今天加30為邊界() {
		assertThat(PermissionService.inExpiringRange("EXPIRED", "DATE", TODAY.minusDays(5), TODAY)).isTrue();
		assertThat(PermissionService.inExpiringRange("APPROVED", "DATE", TODAY.plusDays(30), TODAY)).isTrue();
		assertThat(PermissionService.inExpiringRange("APPROVED", "DATE", TODAY.plusDays(31), TODAY)).isFalse();
		assertThat(PermissionService.inExpiringRange("APPROVED", "PERM", null, TODAY)).isFalse();
		assertThat(PermissionService.inExpiringRange("DISABLED", "DATE", TODAY, TODAY)).isFalse();
	}

	static AcctReqDetail req(String state, Integer stage, String catg) {
		AcctReqDetail r = new AcctReqDetail();
		r.setReqId("AR20261007-001");
		r.setAcctId("AR20261001-001");
		r.setApplyBy(KEVIN);
		r.setReqStateCode(state);
		r.setCurrStageId(stage);
		r.setCatgId(catg);
		return r;
	}
}
