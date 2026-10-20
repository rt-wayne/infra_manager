package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-20
// 變更說明: 新增：WpxAuthClient 單元測試（BACKLOG 第 124 項階段 1 回合 1）
//           以 MockRestServiceServer 模擬 WPX；WPX 端與本系統端各用一組本機臨時產生的 RSA 金鑰對，
//           模擬端以 WPX 私鑰解 cipher、以本系統公鑰驗 sig，確認加密、簽章、九欄順序與內外層 ts／nonce 一致
//           涵蓋：Code 三分類、公鑰快取一天與 -23 重取重送（只重送一次）、HTTP 非 2xx／連不上／逾時／非 JSON／缺 Code、
//           設定不全與私鑰檔讀不到不發請求、nonce 格式與不重複、log 不含密碼
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-20
// 變更說明: 階段 1 審查 M1：Code 0 但 Success 不是 boolean true（false、缺欄、字串 "true"）一律歸服務異常、附 Code 0，並記「回應不一致」
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestTemplate;

import com.mpx.infra_manager.service.WpxAuthClient.Outcome;
import com.mpx.infra_manager.service.WpxAuthClient.Result;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class WpxAuthClientTest {

	static final String BASE = "http://wpx.example.invalid:8777";
	static final String APP_ID = "TestApp";
	static final String MACHINE = "TEST-AP01";
	static final String OS = "Linux";
	static final String USER = "0000001";
	static final String PWD = "FAKE_PWD_PLACEHOLDER";
	static final String KID = "kid-1";

	static KeyPair wpxKeys;
	static KeyPair myKeys;

	@TempDir
	static Path tempDir;
	static Path privateKeyFile;

	final ObjectMapper om = JsonMapper.builder().build();
	JobAndNotifyTest.MutableClock clock;
	RestTemplate restTemplate;
	MockRestServiceServer server;
	WpxAuthClient client;
	ListAppender<ILoggingEvent> logs;

	@BeforeAll
	static void keys() throws Exception {
		KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
		g.initialize(2048);
		wpxKeys = g.generateKeyPair();
		myKeys = g.generateKeyPair();
		privateKeyFile = tempDir.resolve("my.private.pkcs8.pem");
		Files.writeString(privateKeyFile, pem("PRIVATE KEY", myKeys.getPrivate().getEncoded()), StandardCharsets.US_ASCII);
	}

	@BeforeEach
	void setUp() {
		clock = new JobAndNotifyTest.MutableClock(LocalDateTime.of(2026, 10, 20, 10, 0, 0));
		restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
		client = newClient(BASE, APP_ID, privateKeyFile.toString());
		logs = new ListAppender<>();
		logs.start();
		((Logger) LoggerFactory.getLogger(WpxAuthClient.class)).addAppender(logs);
	}

	@AfterEach
	void tearDown() {
		((Logger) LoggerFactory.getLogger(WpxAuthClient.class)).detachAppender(logs);
	}

	WpxAuthClient newClient(String base, String appId, String keyPath) {
		return new WpxAuthClient(restTemplate, base, appId, "APX", keyPath, MACHINE, OS, clock);
	}

	static String pem(String type, byte[] der) {
		return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
				+ "\n-----END " + type + "-----\n";
	}

	static String publicKeyJson(String kid, PublicKey key) {
		return "{\"Success\":true,\"Message\":\"\",\"kid\":\"" + kid + "\",\"algorithm\":\"RSA-OAEP-SHA1\",\"keySize\":2048,"
				+ "\"publicKeyPem\":\"" + pem("PUBLIC KEY", key.getEncoded()).replace("\n", "\\n") + "\",\"acceptedKids\":[\"" + kid + "\"]}";
	}

	static String signInJson(int code) {
		return "{\"Success\":" + (code == 0) + ",\"Message\":\"x\",\"Code\":" + code
				+ ",\"PXEIPTokenId\":\"FAKE_WPX_TOKEN\",\"RefreshKey\":\"FAKE_REFRESH\",\"PublicKey\":\"\",\"SessionId\":\"\"}";
	}

	void expectPublicKey(ExpectedCount count) {
		server.expect(count, requestTo(BASE + WpxAuthClient.PUBLIC_KEY_PATH))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(publicKeyJson(KID, wpxKeys.getPublic()), MediaType.APPLICATION_JSON));
	}

	/** 模擬 WPX 登入端：解 cipher、驗 sig、記下請求，回指定 Code */
	ResponseCreator wpxSignIn(List<JsonNode> captured, int... codes) {
		int[] call = { 0 };
		return request -> {
			String body = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
			JsonNode outer = om.readTree(body);
			captured.add(outer);
			int code = codes[Math.min(call[0]++, codes.length - 1)];
			return withSuccess(signInJson(code), MediaType.APPLICATION_JSON).createResponse(request);
		};
	}

	void expectSignIn(ExpectedCount count, List<JsonNode> captured, int... codes) {
		server.expect(count, requestTo(BASE + WpxAuthClient.SIGN_IN_PATH))
				.andExpect(method(HttpMethod.POST))
				.andRespond(wpxSignIn(captured, codes));
	}

	JsonNode decryptInner(JsonNode outer) throws Exception {
		Cipher c = Cipher.getInstance(WpxAuthClient.OAEP);
		c.init(Cipher.DECRYPT_MODE, wpxKeys.getPrivate());
		return om.readTree(c.doFinal(Base64.getDecoder().decode(outer.get("cipher").stringValue())));
	}

	boolean sigValid(JsonNode o) throws Exception {
		String data = String.join("|", o.get("appId").stringValue(), o.get("kid").stringValue(), o.get("ts").stringValue(),
				o.get("nonce").stringValue(), o.get("cipher").stringValue(), o.get("dbalias").stringValue(),
				o.get("machine").stringValue(), o.get("os").stringValue(), o.get("ip").stringValue());
		Signature s = Signature.getInstance(WpxAuthClient.SIGN_ALG);
		s.initVerify(myKeys.getPublic());
		s.update(data.getBytes(StandardCharsets.UTF_8));
		return s.verify(Base64.getDecoder().decode(o.get("sig").stringValue()));
	}

	List<String> logLines() {
		List<String> lines = new ArrayList<>();
		for (ILoggingEvent e : logs.list) {
			lines.add(e.getFormattedMessage());
		}
		return lines;
	}

	@Test
	void success_requestIsEncryptedSignedAndMatchesSpec() throws Exception {
		List<JsonNode> captured = new ArrayList<>();
		expectPublicKey(once());
		expectSignIn(once(), captured, 0);

		Result r = client.verify(USER, PWD);

		server.verify();
		assertThat(r).isEqualTo(new Result(Outcome.SUCCESS, 0));
		assertThat(r.success()).isTrue();
		JsonNode o = captured.get(0);
		// 外層欄位
		assertThat(o.get("appId").stringValue()).isEqualTo(APP_ID);
		assertThat(o.get("kid").stringValue()).isEqualTo(KID);
		assertThat(o.get("ts").stringValue()).isEqualTo("2026-10-20T02:00:00Z"); // 台北 10:00 → UTC 02:00
		assertThat(o.get("dbalias").stringValue()).isEqualTo("APX");
		assertThat(o.get("machine").stringValue()).isEqualTo(MACHINE);
		assertThat(o.get("os").stringValue()).isEqualTo(OS);
		assertThat(o.get("ip").stringValue()).isEmpty();
		assertThat(o.get("enablePublicKey").booleanValue()).isFalse();
		assertThat(o.get("enableSessionId").booleanValue()).isFalse();
		// 外層不得出現明文密碼
		assertThat(o.toString()).doesNotContain(PWD);
		// 內層：WPX 私鑰解得開，ts／nonce 與外層一致
		JsonNode inner = decryptInner(o);
		assertThat(inner.get("userid").stringValue()).isEqualTo(USER);
		assertThat(inner.get("pwd").stringValue()).isEqualTo(PWD);
		assertThat(inner.get("ts").stringValue()).isEqualTo(o.get("ts").stringValue());
		assertThat(inner.get("nonce").stringValue()).isEqualTo(o.get("nonce").stringValue());
		// 簽章：九欄依序串接，本系統公鑰驗得過
		assertThat(sigValid(o)).isTrue();
	}

	@Test
	void signData_joinsNineFieldsInOrderKeepingEmptyValues() {
		assertThat(WpxAuthClient.signData("a", "k", "t", "n", "c", "d", "", "o", ""))
				.isEqualTo("a|k|t|n|c|d||o|");
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, -11, -12, -13, -14, -15 })
	void classify_badCredential(int code) {
		assertThat(WpxAuthClient.classify(code)).isEqualTo(Outcome.BAD_CREDENTIAL);
	}

	@ParameterizedTest
	@ValueSource(ints = { -2, -3, -4, -5, -6, -7, -8, -9, -10 })
	void classify_accountUnusable(int code) {
		assertThat(WpxAuthClient.classify(code)).isEqualTo(Outcome.ACCOUNT_UNUSABLE);
	}

	@ParameterizedTest
	@ValueSource(ints = { -16, -17, -18, -19, -20, -21, -22, -23, -24, -25, -26, -27, -28, -99, 1 })
	void classify_serviceErrorIncludingUnlistedCodes(int code) {
		assertThat(WpxAuthClient.classify(code)).isEqualTo(Outcome.SERVICE_ERROR);
	}

	@Test
	void classify_nullIsServiceError() {
		assertThat(WpxAuthClient.classify(null)).isEqualTo(Outcome.SERVICE_ERROR);
	}

	@Test
	void accountCodes_returnOutcomeWithOriginalCode() {
		List<JsonNode> captured = new ArrayList<>();
		expectPublicKey(once());
		expectSignIn(once(), captured, -4);

		assertThat(client.verify(USER, PWD)).isEqualTo(new Result(Outcome.ACCOUNT_UNUSABLE, -4));
		server.verify();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"Success\":false,\"Message\":\"x\",\"Code\":0}",
			"{\"Message\":\"x\",\"Code\":0}",
			"{\"Success\":\"true\",\"Message\":\"x\",\"Code\":0}" })
	void codeZeroWithoutSuccessTrue_isNotSuccess(String body) {
		expectPublicKey(once());
		server.expect(once(), requestTo(BASE + WpxAuthClient.SIGN_IN_PATH))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		assertThat(client.verify(USER, PWD)).isEqualTo(new Result(Outcome.SERVICE_ERROR, 0));
		server.verify();
		assertThat(logLines()).singleElement().asString().contains("回應不一致", USER).doesNotContain(PWD);
	}

	@Test
	void publicKey_cachedWithinOneDay() {
		List<JsonNode> captured = new ArrayList<>();
		expectPublicKey(once());
		expectSignIn(times(2), captured, 0, 0);

		client.verify(USER, PWD);
		clock.set(LocalDateTime.of(2026, 10, 21, 9, 59, 59)); // 未滿一天
		client.verify(USER, PWD);

		server.verify();
	}

	@Test
	void publicKey_refetchedAfterOneDay() {
		List<JsonNode> captured = new ArrayList<>();
		expectPublicKey(times(2));
		expectSignIn(times(2), captured, 0, 0);

		client.verify(USER, PWD);
		clock.set(LocalDateTime.of(2026, 10, 21, 10, 0, 0)); // 滿一天
		client.verify(USER, PWD);

		server.verify();
	}

	@Test
	void kidNotFound_refetchesKeyAndResendsFreshPackageOnce() throws Exception {
		List<JsonNode> captured = new ArrayList<>();
		expectPublicKey(times(2));
		expectSignIn(times(2), captured, -23, 0);

		Result r = client.verify(USER, PWD);

		server.verify();
		assertThat(r).isEqualTo(new Result(Outcome.SUCCESS, 0));
		// 重送是整包重產：nonce 不同、cipher 不同，簽章仍有效
		assertThat(captured.get(1).get("nonce").stringValue()).isNotEqualTo(captured.get(0).get("nonce").stringValue());
		assertThat(captured.get(1).get("cipher").stringValue()).isNotEqualTo(captured.get(0).get("cipher").stringValue());
		assertThat(sigValid(captured.get(1))).isTrue();
		assertThat(decryptInner(captured.get(1)).get("nonce").stringValue())
				.isEqualTo(captured.get(1).get("nonce").stringValue());
	}

	@Test
	void kidNotFoundTwice_doesNotRetryAgain() {
		List<JsonNode> captured = new ArrayList<>();
		expectPublicKey(times(2));
		expectSignIn(times(2), captured, -23, -23);

		assertThat(client.verify(USER, PWD)).isEqualTo(new Result(Outcome.SERVICE_ERROR, -23));
		server.verify();
	}

	@Test
	void signInHttp500_isServiceErrorWithoutCode() {
		expectPublicKey(once());
		server.expect(once(), requestTo(BASE + WpxAuthClient.SIGN_IN_PATH)).andRespond(withServerError());

		assertThat(client.verify(USER, PWD)).isEqualTo(new Result(Outcome.SERVICE_ERROR, null));
		server.verify();
	}

	@Test
	void publicKeyUnreachable_isServiceError() {
		server.expect(once(), requestTo(BASE + WpxAuthClient.PUBLIC_KEY_PATH))
				.andRespond(withException(new IOException("Connection refused")));

		assertThat(client.verify(USER, PWD)).isEqualTo(new Result(Outcome.SERVICE_ERROR, null));
		server.verify();
	}

	@Test
	void signInTimeout_isServiceError() {
		expectPublicKey(once());
		server.expect(once(), requestTo(BASE + WpxAuthClient.SIGN_IN_PATH))
				.andRespond(withException(new SocketTimeoutException("Read timed out")));

		assertThat(client.verify(USER, PWD)).isEqualTo(new Result(Outcome.SERVICE_ERROR, null));
		server.verify();
	}

	@Test
	void signInNotJson_isServiceError() {
		expectPublicKey(once());
		server.expect(once(), requestTo(BASE + WpxAuthClient.SIGN_IN_PATH))
				.andRespond(withSuccess("<html>gateway</html>", MediaType.TEXT_HTML));

		assertThat(client.verify(USER, PWD)).isEqualTo(new Result(Outcome.SERVICE_ERROR, null));
		server.verify();
	}

	@Test
	void signInWithoutCode_isServiceError() {
		expectPublicKey(once());
		server.expect(once(), requestTo(BASE + WpxAuthClient.SIGN_IN_PATH))
				.andRespond(withSuccess("{\"Success\":false,\"Message\":\"x\"}", MediaType.APPLICATION_JSON));

		assertThat(client.verify(USER, PWD)).isEqualTo(new Result(Outcome.SERVICE_ERROR, null));
		server.verify();
	}

	@Test
	void publicKeyMissingKid_isServiceError() {
		server.expect(once(), requestTo(BASE + WpxAuthClient.PUBLIC_KEY_PATH))
				.andRespond(withSuccess("{\"Success\":true,\"publicKeyPem\":\"x\"}", MediaType.APPLICATION_JSON));

		assertThat(client.verify(USER, PWD)).isEqualTo(new Result(Outcome.SERVICE_ERROR, null));
		server.verify();
	}

	@Test
	void missingConfig_isServiceErrorWithoutAnyRequest() {
		assertThat(newClient("", APP_ID, privateKeyFile.toString()).verify(USER, PWD).outcome()).isEqualTo(Outcome.SERVICE_ERROR);
		assertThat(newClient(BASE, " ", privateKeyFile.toString()).verify(USER, PWD).outcome()).isEqualTo(Outcome.SERVICE_ERROR);
		assertThat(newClient(BASE, APP_ID, "").verify(USER, PWD).outcome()).isEqualTo(Outcome.SERVICE_ERROR);
		server.verify(); // 沒有設定任何期望：一個請求都不該發
	}

	@Test
	void privateKeyFileMissing_isServiceError() {
		expectPublicKey(once());
		WpxAuthClient c = newClient(BASE, APP_ID, tempDir.resolve("no-such.pem").toString());

		assertThat(c.verify(USER, PWD)).isEqualTo(new Result(Outcome.SERVICE_ERROR, null));
		server.verify();
	}

	@Test
	void privateKeyNotPkcs8_isServiceError() throws Exception {
		Path bad = tempDir.resolve("bad.pem");
		Files.writeString(bad, "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n", StandardCharsets.US_ASCII);
		expectPublicKey(once());

		assertThat(newClient(BASE, APP_ID, bad.toString()).verify(USER, PWD)).isEqualTo(new Result(Outcome.SERVICE_ERROR, null));
		server.verify();
	}

	@Test
	void nonce_formatAllowedAndUnique() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			String n = client.newNonce();
			assertThat(n).matches("[A-Za-z0-9\\-_=+/]{8,64}");
			seen.add(n);
		}
		assertThat(seen).hasSize(200);
	}

	@Test
	void logs_neverContainPassword() {
		List<JsonNode> captured = new ArrayList<>();
		expectPublicKey(once());
		expectSignIn(times(2), captured, -14, -22);
		client.verify(USER, PWD); // 帳密錯誤
		client.verify(USER, PWD); // 檢查層錯誤
		newClient(BASE, APP_ID, "").verify(USER, PWD); // 設定不全

		List<String> lines = logLines();
		assertThat(lines).hasSize(3);
		assertThat(lines).allSatisfy(l -> assertThat(l).doesNotContain(PWD, "FAKE_WPX_TOKEN", "FAKE_REFRESH"));
		assertThat(lines.get(0)).contains(USER, "-14");
		server.verify();
	}
}
