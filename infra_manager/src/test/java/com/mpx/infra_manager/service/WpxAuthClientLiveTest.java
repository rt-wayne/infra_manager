package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-20
// 變更說明: 新增：WpxAuthClient 實連測試（BACKLOG 第 124 項階段 1 回合 2）
//           只在設了環境變數 WPX_LIVE_BASE_URL 時才跑（平常 mvnw test 一律略過），位址、私鑰路徑、appId 都由環境變數給，不進 repo
//           送一個不存在的假工號與假密碼：期望 WPX 回 -1（查無使用者）＝加密與簽章已通過、通路可用；
//           不使用任何真實帳號，不會讓任何人被鎖。執行方式見 SETUP.md 第 2 節「WPX 登入」
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.restclient.RestTemplateBuilder;

import com.mpx.infra_manager.service.WpxAuthClient.Outcome;
import com.mpx.infra_manager.service.WpxAuthClient.Result;

@EnabledIfEnvironmentVariable(named = "WPX_LIVE_BASE_URL", matches = ".+")
class WpxAuthClientLiveTest {

	/** 不存在的假工號（2026-10-20 以交付包腳本實測，WPX 測試環境回 -1） */
	static final String FAKE_USER = "ZZ99999";
	static final String FAKE_PWD = "not-a-real-password";

	@Test
	void fakeUser_returnsNoSuchUser() {
		String appId = System.getenv().getOrDefault("WPX_LIVE_APP_ID", "Test");
		WpxAuthClient client = new WpxAuthClient(
				new RestTemplateBuilder().connectTimeout(Duration.ofSeconds(10)).readTimeout(Duration.ofSeconds(10)).build(),
				System.getenv("WPX_LIVE_BASE_URL"), appId, "APX", System.getenv("WPX_LIVE_PRIVATE_KEY"),
				"infra-manager-live-test", System.getProperty("os.name", ""), Clock.systemUTC());

		Result r = client.verify(FAKE_USER, FAKE_PWD);

		assertThat(r).isEqualTo(new Result(Outcome.BAD_CREDENTIAL, -1));
	}
}
