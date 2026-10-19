package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：名單維護軌跡寫入與 CONTENT_JSON 組裝單元測試（BACKLOG 第 89 項階段 1；裁示 ②A／⑥A、BACKLOG 第 11 項）
//           SUCCESS／DENY 寫入的欄位（實際操作人、對象、原因截 500 字、對象超欄寬存 null）；DENY 寫失敗吞掉只記 log；
//           JSON 由 Jackson 組（含中文、引號不會壞）；最壞情況（50 筆單號＋兩個 30 字中文名）不需裁剪；
//           超過 3900 bytes 時逐筆丟單號、blockedTotal 保留、JSON 仍完整；putBlocked 的欄位
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 階段 3 回合 1：putBlockedByPerm 依碼分組、每碼最多 20 筆、每碼 ids 各自可裁剪；
//           ROLE_PERM_SET 最壞情況（20 碼 added＋removed、30 字角色名、6 個卡單碼各 20 筆）不需裁剪且低於上限
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.dao.AdminLogDao;
import com.mpx.infra_manager.model.InFlightRow;
import com.mpx.infra_manager.support.TestFixture;

import tools.jackson.databind.json.JsonMapper;

class AdminLogWriterTest {

	AdminLogDao dao;
	AdminLogWriter writer;
	Identity hank = TestFixture.id(TestFixture.HANK);

	@BeforeEach
	void setUp() {
		dao = mock(AdminLogDao.class);
		writer = new AdminLogWriter(dao);
	}

	@Test
	void 成功軌跡_實際操作人_對象_JSON_原因() {
		Map<String, Object> c = AdminLogWriter.content();
		c.put("roleId", "GOV");
		c.put("roleName", "資訊治理");
		c.put("targetUserName", "Kevin (李凱鈞)");
		c.put("before", Map.of("status", 0));
		c.put("after", Map.of("status", 1));
		writer.success(TestFixture.NOW, hank, AcctConst.ADM_ROLE_GRANT, TestFixture.KEVIN, c, "  接手關卡 4  ");
		verify(dao).insert(TestFixture.NOW, TestFixture.HANK, "Hank (黃志翰)", AcctConst.ADM_ROLE_GRANT, TestFixture.KEVIN,
				"{\"roleId\":\"GOV\",\"roleName\":\"資訊治理\",\"targetUserName\":\"Kevin (李凱鈞)\",\"before\":{\"status\":0},\"after\":{\"status\":1}}",
				"接手關卡 4", AcctConst.ADM_SUCCESS);
	}

	@Test
	void 拒絕軌跡_原因截500字_對象超欄寬存null_寫失敗只記log() {
		String longReason = "原".repeat(600);
		writer.deny(TestFixture.NOW, hank, AcctConst.ADM_ROLE_REVOKE, "x".repeat(31), AdminLogWriter.content(), longReason);
		ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
		verify(dao).insert(any(), anyString(), anyString(), any(), org.mockito.ArgumentMatchers.isNull(), any(), reason.capture(),
				org.mockito.ArgumentMatchers.eq(AcctConst.ADM_DENY));
		assertThat(reason.getValue()).hasSize(AcctConst.ADMIN_REASON_MAX);

		doThrow(new DataAccessResourceFailureException("db down")).when(dao).insert(any(), any(), any(), any(), any(), any(), any(),
				any());
		writer.deny(TestFixture.NOW, hank, AcctConst.ADM_ROLE_REVOKE, TestFixture.KEVIN, AdminLogWriter.content(), null);
		// 沒丟例外即通過
	}

	@Test
	void putBlocked_欄位與可裁剪清單() {
		HolderGuard.Result r = new HolderGuard.Result(false, List.of(
				new HolderGuard.Blocked("APPR_G5", List.of(new InFlightRow("AR0001", "資訊主管")), 1),
				new HolderGuard.Blocked("EMG_DECIDE", List.of(new InFlightRow("EA0001", "待補核准"), new InFlightRow("EA0002", "待補核准")), 9)));
		Map<String, Object> c = AdminLogWriter.content();
		List<String> ids = AdminLogWriter.putBlocked(c, r);
		assertThat(c).containsEntry("denyCode", "IN_FLIGHT").containsEntry("blockedPerms", List.of("APPR_G5", "EMG_DECIDE"))
				.containsEntry("blockedTotal", 10);
		assertThat(ids).containsExactly("AR0001", "EA0001", "EA0002").isSameAs(c.get("blockedIds"));

		Map<String, Object> c2 = AdminLogWriter.content();
		AdminLogWriter.putBlocked(c2, new HolderGuard.Result(true, List.of()));
		assertThat(c2).containsEntry("denyCode", "LAST_SYS_ROLE").containsEntry("blockedTotal", 0);
	}

	@Test
	void 最壞情況_50筆單號加兩個30字中文名_不需裁剪且低於上限() {
		Map<String, Object> c = AdminLogWriter.content();
		c.put("roleId", "ACCT_REGISTRY");
		c.put("roleName", "角".repeat(30));
		c.put("targetUserName", "名".repeat(30) + " (" + "名".repeat(30) + ")");
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < AcctConst.ADMIN_BLOCKED_LOG_MAX; i++) {
			ids.add("AR20261019" + String.format("%04d", i));
		}
		c.put("denyCode", "IN_FLIGHT");
		c.put("blockedPerms", List.of("APPR_G2", "APPR_G3", "APPR_G4", "APPR_G5", "VERIFY_G7", "EMG_DECIDE"));
		c.put("blockedTotal", 999);
		c.put("blockedIds", ids);
		String json = AdminLogWriter.contentJson(c, List.of(ids));
		assertThat(json.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(AcctConst.ADMIN_CONTENT_MAX_BYTES);
		assertThat(ids).hasSize(AcctConst.ADMIN_BLOCKED_LOG_MAX);
	}

	@Test
	void 超過上限_逐筆丟單號_總數保留_JSON完整可解析() {
		Map<String, Object> c = AdminLogWriter.content();
		c.put("roleId", "GOV");
		List<String> a = new ArrayList<>();
		List<String> b = new ArrayList<>();
		for (int i = 0; i < 300; i++) {
			a.add("AR20261019" + String.format("%04d", i));
			if (i < 100) {
				b.add("EA20261019" + String.format("%04d", i));
			}
		}
		c.put("blockedTotal", 400);
		c.put("a", a);
		c.put("b", b);
		String json = AdminLogWriter.contentJson(c, List.of(a, b));
		assertThat(json.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(AcctConst.ADMIN_CONTENT_MAX_BYTES);
		assertThat(a.size() + b.size()).isLessThan(400).isGreaterThan(100);
		// 每輪丟最長清單的最後一筆：較短的 b 不會被丟光
		assertThat(b).isNotEmpty();
		@SuppressWarnings("unchecked")
		Map<String, Object> parsed = JsonMapper.builder().build().readValue(json, Map.class);
		assertThat(parsed).containsEntry("blockedTotal", 400).containsEntry("roleId", "GOV");
	}

	@Test
	void putBlockedByPerm_依碼分組_每碼最多20筆_每碼ids各自可裁剪() {
		List<InFlightRow> many = new ArrayList<>();
		for (int i = 1; i <= 50; i++) {
			many.add(new InFlightRow("AR" + String.format("%04d", i), "資訊主管"));
		}
		HolderGuard.Result r = new HolderGuard.Result(false, List.of(
				new HolderGuard.Blocked("APPR_G5", many, 73),
				new HolderGuard.Blocked("EMG_DECIDE", List.of(new InFlightRow("EA0001", "待補核准")), 1)));
		Map<String, Object> c = AdminLogWriter.content();
		List<List<String>> trimmable = AdminLogWriter.putBlockedByPerm(c, r);
		assertThat(c).containsEntry("denyCode", "IN_FLIGHT").doesNotContainKey("blockedIds");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> blocked = (List<Map<String, Object>>) c.get("blocked");
		assertThat(blocked).hasSize(2);
		assertThat(blocked.get(0)).containsEntry("permId", "APPR_G5").containsEntry("total", 73);
		assertThat((List<?>) blocked.get(0).get("ids")).hasSize(AcctConst.ADMIN_BLOCKED_PER_PERM_LOG_MAX).first().isEqualTo("AR0001");
		assertThat(blocked.get(1)).containsEntry("permId", "EMG_DECIDE").containsEntry("total", 1);
		assertThat(trimmable).hasSize(2);
		assertThat(trimmable.get(0)).isSameAs(blocked.get(0).get("ids"));

		Map<String, Object> c2 = AdminLogWriter.content();
		assertThat(AdminLogWriter.putBlockedByPerm(c2, new HolderGuard.Result(true, List.of()))).isEmpty();
		assertThat(c2).containsEntry("denyCode", "LAST_SYS_ROLE").containsEntry("blocked", List.of());
	}

	/** 計畫檔估算約 2.9 KB：added＋removed 合計 20 碼、30 字角色名、6 個卡單碼各 20 筆單號 */
	@Test
	void ROLE_PERM_SET最壞情況_20碼差異加6碼各20筆單號_不需裁剪且低於上限() {
		Map<String, Object> c = AdminLogWriter.content();
		c.put("roleId", "ACCT_REGISTRY");
		c.put("roleName", "角".repeat(30));
		c.put("memberCount", 999);
		c.put("added", AcctConst.ADMIN_VISIBLE_PERMS.subList(0, 14));
		c.put("removed", AcctConst.ADMIN_VISIBLE_PERMS.subList(14, 20));
		List<HolderGuard.Blocked> blocked = new ArrayList<>();
		for (String permId : List.of("APPR_G2", "APPR_G3", "APPR_G4", "APPR_G5", "VERIFY_G7", "EMG_DECIDE")) {
			List<InFlightRow> docs = new ArrayList<>();
			for (int i = 0; i < AcctConst.ADMIN_BLOCKED_LOG_MAX; i++) {
				docs.add(new InFlightRow("AR20261019-" + String.format("%03d", i), "關卡"));
			}
			blocked.add(new HolderGuard.Blocked(permId, docs, 999));
		}
		List<List<String>> trimmable = AdminLogWriter.putBlockedByPerm(c, new HolderGuard.Result(false, blocked));
		String json = AdminLogWriter.contentJson(c, trimmable);
		assertThat(json.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(AcctConst.ADMIN_CONTENT_MAX_BYTES);
		for (List<String> ids : trimmable) {
			assertThat(ids).hasSize(AcctConst.ADMIN_BLOCKED_PER_PERM_LOG_MAX);
		}
	}

	@Test
	void 可裁剪清單都空仍超過_照實回傳不無窮迴圈() {
		Map<String, Object> c = AdminLogWriter.content();
		c.put("big", "字".repeat(2000));
		String json = AdminLogWriter.contentJson(c, List.of(new ArrayList<>()));
		assertThat(json.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(AcctConst.ADMIN_CONTENT_MAX_BYTES);
	}
}
