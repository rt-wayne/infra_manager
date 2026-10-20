package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-20
// 變更說明: 新增：呼叫 WPX VerifySecure 登入 API 驗證工號＋密碼（BACKLOG 第 124 項階段 1 回合 1；
//           規格見 backlog/124-登入改用外部驗證API.md「WPX VerifySecure API」）
//           流程：GET /VerifySecure/PublicKey 取 kid＋WPX 公鑰（快取一天）→ 內層 {"userid","pwd","ts","nonce"}
//           以 WPX 公鑰 RSA-OAEP（SHA-1／MGF1 SHA-1）加密成 cipher → appId|kid|ts|nonce|cipher|dbalias|machine|os|ip
//           以本系統私鑰 SHA256withRSA 簽成 sig → POST /VerifySecure/SignInGetToken
//           結果依裁示 ⑨ 分三類（帳密錯誤／帳號無法使用／服務異常），WPX 原始 Code 一併回傳供 log
//           收到 -23（kid 不存在）重取公鑰並整包重產重送一次；其他情況不重試
//           安全：密碼、cipher、sig、WPX 回應本文與 WPX 發的 Token 一律不寫 log、不保存；log 只記工號與 Code
//           RestTemplate 比照 DbConnectApiClient 在類別內自建、不註冊成 bean，非 2xx 不丟夾帶本文的例外
//           私鑰在第一次呼叫時才讀（尚未接上登入流程，缺設定不影響啟動；啟動時檢查留給階段 7）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-20
// 變更說明: 階段 1 審查 M1：成功須 Success 為 boolean true 且 Code 為 0；Code 0 但 Success 不是 true（WPX 漏設 Code 時
//           .NET 整數預設為 0）歸服務異常並記 WARN「回應不一致」（使用者裁示 ①A），避免驗證失敗卻放行。
//           已查證 WPX 回 -23 時為 HTTP 200（2026-10-20），重取公鑰流程維持只看 2xx 本文
// ============================================================

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class WpxAuthClient {

	private static final Logger log = LoggerFactory.getLogger(WpxAuthClient.class);

	static final String PUBLIC_KEY_PATH = "/VerifySecure/PublicKey";
	static final String SIGN_IN_PATH = "/VerifySecure/SignInGetToken";
	static final String OAEP = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding";
	static final String SIGN_ALG = "SHA256withRSA";
	static final int CODE_KID_NOT_FOUND = -23;
	/** 公鑰快取時間（WPX 文件建議一天刷新一次） */
	static final Duration KEY_TTL = Duration.ofDays(1);
	static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

	/** 驗證結果分類（裁示 ⑨） */
	public enum Outcome {
		/** Code 0 */
		SUCCESS,
		/** -1 查無使用者、-11～-15 密碼錯誤相關 →「帳號或密碼錯誤」 */
		BAD_CREDENTIAL,
		/** -2～-10 留職停薪、鎖定、時段、分店或 IP →「帳號無法使用」 */
		ACCOUNT_UNUSABLE,
		/** -18～-27 檢查層錯誤、未列出的代碼、連不上或回應無法解析、本系統設定不全 →「登入服務異常」 */
		SERVICE_ERROR
	}

	/**
	 * 驗證結果；code 為 WPX 原始 Code（沒有拿到 WPX 回應時為 null），只供 log，不顯示給使用者。
	 * WPX 發的 Token 不放進結果（本系統不保存）。
	 */
	public record Result(Outcome outcome, Integer code) {
		public boolean success() {
			return outcome == Outcome.SUCCESS;
		}
	}

	/** 本系統設定不全、私鑰讀不到、加密簽章失敗、WPX 回應無法使用；訊息不含密碼與回應本文 */
	static class WpxException extends Exception {
		private static final long serialVersionUID = 1L;

		WpxException(String message) {
			super(message);
		}
	}

	/** 快取中的 WPX 公鑰 */
	private record CachedKey(String kid, PublicKey key, Instant fetchedAt) {
	}

	/** 登入回應中本元件會看的兩欄；success 只在 Success 是 boolean true 時為 true */
	private record Reply(int code, boolean success) {
	}

	private final RestTemplate restTemplate;
	private final String baseUrl;
	private final String appId;
	private final String dbAlias;
	private final String privateKeyPath;
	private final String machine;
	private final String os;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();
	private final ObjectMapper objectMapper = JsonMapper.builder()
			// 解析失敗時例外訊息不夾帶回應原文（回應含 WPX 發的 Token）
			.disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
			.build();

	private volatile CachedKey cachedKey;
	private volatile PrivateKey privateKey;

	/**
	 * 位址沿用 host.properties 的 wpx.api.domain.path（與 UAT 上 PX Track 共用同一組 key）；
	 * 其餘為本系統自己的 key（config/environment.properties）。逾時同時用於連線與讀取。
	 */
	@Autowired
	public WpxAuthClient(@Value("${wpx.api.domain.path:}") String baseUrl,
			@Value("${infra.manager.wpx.app-id:}") String appId,
			@Value("${infra.manager.wpx.dbalias:APX}") String dbAlias,
			@Value("${infra.manager.wpx.private-key:}") String privateKeyPath,
			@Value("${infra.manager.wpx.timeout-seconds:10}") int timeoutSeconds,
			Clock clock) {
		this(new RestTemplateBuilder()
				.connectTimeout(Duration.ofSeconds(timeoutSeconds))
				.readTimeout(Duration.ofSeconds(timeoutSeconds))
				.build(),
				baseUrl, appId, dbAlias, privateKeyPath, localHostName(), System.getProperty("os.name", ""), clock);
	}

	/** 單元測試用：傳入已綁定 MockRestServiceServer 的 RestTemplate 與固定的主機名、作業系統 */
	WpxAuthClient(RestTemplate restTemplate, String baseUrl, String appId, String dbAlias, String privateKeyPath,
			String machine, String os, Clock clock) {
		this.restTemplate = restTemplate;
		this.restTemplate.setErrorHandler(new PassThroughErrorHandler());
		this.baseUrl = trimTrailingSlash(baseUrl);
		this.appId = appId == null ? "" : appId.trim();
		this.dbAlias = dbAlias == null || dbAlias.isBlank() ? "APX" : dbAlias.trim();
		this.privateKeyPath = privateKeyPath == null ? "" : privateKeyPath.trim();
		this.machine = machine == null ? "" : machine;
		this.os = os == null ? "" : os;
		this.clock = clock;
	}

	/**
	 * 以工號＋密碼向 WPX 驗證。不丟例外：所有失敗都歸類成 Result。
	 * 呼叫端不得記錄 pwd；本方法也不記錄。
	 */
	public Result verify(String userId, String pwd) {
		try {
			Reply reply = signIn(userId, pwd, false);
			if (reply.code() == CODE_KID_NOT_FOUND) {
				// WPX 換鑰：重取公鑰、整包重產（新的 ts、nonce、cipher、sig）再送一次
				reply = signIn(userId, pwd, true);
			}
			Outcome outcome = classify(reply.code());
			if (outcome == Outcome.SUCCESS && !reply.success()) {
				// Code 0 但 Success 不是 true：不當成功（裁示 ①A 歸服務異常）
				log.warn("WPX 回應不一致（Code 0 但 Success 不是 true）userId={}", userId);
				return new Result(Outcome.SERVICE_ERROR, reply.code());
			}
			Result result = new Result(outcome, reply.code());
			if (!result.success()) {
				log.info("WPX 登入驗證未通過 userId={} code={} 分類={}", userId, reply.code(), result.outcome());
			}
			return result;
		} catch (WpxException e) {
			log.warn("WPX 登入驗證無法完成 userId={} 原因={}", userId, e.getMessage());
			return new Result(Outcome.SERVICE_ERROR, null);
		}
	}

	/** WPX Code → 裁示 ⑨ 三分類；null（沒拿到 Code）與未列出的代碼歸服務異常 */
	static Outcome classify(Integer code) {
		if (code == null) {
			return Outcome.SERVICE_ERROR;
		}
		int c = code;
		if (c == 0) {
			return Outcome.SUCCESS;
		}
		if (c == -1 || (c <= -11 && c >= -15)) {
			return Outcome.BAD_CREDENTIAL;
		}
		if (c <= -2 && c >= -10) {
			return Outcome.ACCOUNT_UNUSABLE;
		}
		return Outcome.SERVICE_ERROR;
	}

	/** 組一整包請求並送出，回傳 WPX 的 Code 與 Success */
	private Reply signIn(String userId, String pwd, boolean refreshKey) throws WpxException {
		requireConfig();
		CachedKey wpxKey = publicKey(refreshKey);
		PrivateKey mine = privateKey();

		String ts = TS_FORMAT.format(clock.instant());
		String nonce = newNonce();
		String cipher = encrypt(wpxKey.key(), innerPayload(userId, pwd, ts, nonce));
		String ip = ""; // 裁示：本系統都是總部帳號，留空字串由 WPX 以連線來源代入
		String sig = sign(mine, signData(appId, wpxKey.kid(), ts, nonce, cipher, dbAlias, machine, os, ip));

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("appId", appId);
		body.put("kid", wpxKey.kid());
		body.put("ts", ts);
		body.put("nonce", nonce);
		body.put("cipher", cipher);
		body.put("sig", sig);
		body.put("machine", machine);
		body.put("os", os);
		body.put("ip", ip);
		body.put("dbalias", dbAlias);
		body.put("enablePublicKey", false);
		body.put("enableSessionId", false);

		JsonNode resp = call(HttpMethod.POST, SIGN_IN_PATH, toJson(body));
		JsonNode code = resp.get("Code");
		if (code == null || !code.isIntegralNumber()) {
			throw new WpxException("登入回應缺少 Code");
		}
		JsonNode success = resp.get("Success");
		return new Reply(code.intValue(), success != null && success.isBoolean() && success.booleanValue());
	}

	/** 取 WPX 公鑰；快取一天，refresh 為 true 時強制重取 */
	private synchronized CachedKey publicKey(boolean refresh) throws WpxException {
		CachedKey cached = cachedKey;
		Instant now = clock.instant();
		if (!refresh && cached != null && now.isBefore(cached.fetchedAt().plus(KEY_TTL))) {
			return cached;
		}
		JsonNode resp = call(HttpMethod.GET, PUBLIC_KEY_PATH, null);
		JsonNode kid = resp.get("kid");
		JsonNode pem = resp.get("publicKeyPem");
		if (kid == null || !kid.isString() || kid.stringValue().isBlank() || pem == null || !pem.isString()) {
			throw new WpxException("公鑰回應缺少 kid 或 publicKeyPem");
		}
		try {
			PublicKey key = KeyFactory.getInstance("RSA").generatePublic(
					new X509EncodedKeySpec(pemBody(pem.stringValue(), "PUBLIC KEY")));
			cachedKey = new CachedKey(kid.stringValue(), key, now);
			return cachedKey;
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new WpxException("WPX 公鑰格式無法解析（" + e.getClass().getSimpleName() + "）");
		}
	}

	/** 讀本系統私鑰（PKCS#8 PEM）；成功後保留在記憶體 */
	private PrivateKey privateKey() throws WpxException {
		PrivateKey key = privateKey;
		if (key != null) {
			return key;
		}
		synchronized (this) {
			if (privateKey == null) {
				try {
					String pem = Files.readString(Path.of(privateKeyPath), StandardCharsets.US_ASCII);
					privateKey = KeyFactory.getInstance("RSA").generatePrivate(
							new PKCS8EncodedKeySpec(pemBody(pem, "PRIVATE KEY")));
				} catch (IOException e) {
					// 只記類別名，不記路徑以外的內容
					throw new WpxException("私鑰檔讀不到（" + e.getClass().getSimpleName() + "），請檢查 infra.manager.wpx.private-key");
				} catch (GeneralSecurityException | IllegalArgumentException e) {
					throw new WpxException("私鑰格式無法解析（" + e.getClass().getSimpleName() + "），須為 PKCS#8 PEM");
				}
			}
			return privateKey;
		}
	}

	private void requireConfig() throws WpxException {
		if (baseUrl.isEmpty()) {
			throw new WpxException("未設定 wpx.api.domain.path");
		}
		if (appId.isEmpty()) {
			throw new WpxException("未設定 infra.manager.wpx.app-id");
		}
		if (privateKeyPath.isEmpty()) {
			throw new WpxException("未設定 infra.manager.wpx.private-key");
		}
	}

	/** 送出請求並解析 JSON；非 2xx、連不上、本文不是 JSON 都轉成 WpxException（訊息不帶本文） */
	private JsonNode call(HttpMethod method, String path, byte[] requestBody) throws WpxException {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		if (requestBody != null) {
			headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
		}
		byte[] body;
		try {
			ResponseEntity<byte[]> entity = restTemplate.exchange(URI.create(baseUrl + path), method,
					new HttpEntity<>(requestBody, headers), byte[].class);
			int status = entity.getStatusCode().value();
			if (status < 200 || status > 299) {
				throw new WpxException(path + " 回應 HTTP " + status);
			}
			body = entity.getBody();
		} catch (RestClientException | IllegalArgumentException e) {
			throw new WpxException(path + " 呼叫失敗（" + e.getClass().getSimpleName() + "）");
		}
		if (body == null || body.length == 0) {
			throw new WpxException(path + " 回應內容為空");
		}
		try {
			JsonNode node = objectMapper.readTree(body);
			if (node == null || !node.isObject()) {
				throw new WpxException(path + " 回應不是 JSON 物件");
			}
			return node;
		} catch (JacksonException e) {
			throw new WpxException(path + " 回應格式無法解析");
		}
	}

	/** 內層 payload；ts、nonce 必須與外層相同 */
	byte[] innerPayload(String userId, String pwd, String ts, String nonce) throws WpxException {
		Map<String, String> payload = new LinkedHashMap<>();
		payload.put("userid", userId == null ? "" : userId);
		payload.put("pwd", pwd == null ? "" : pwd);
		payload.put("ts", ts);
		payload.put("nonce", nonce);
		return toJson(payload);
	}

	private byte[] toJson(Object value) throws WpxException {
		try {
			return objectMapper.writeValueAsBytes(value);
		} catch (JacksonException e) {
			throw new WpxException("組請求內容失敗");
		}
	}

	/** 16 bytes 隨機值 Base64 URL-safe 無補位（22 字，WPX 允許 A-Z a-z 0-9 - _ = + /，8～64 字） */
	String newNonce() {
		byte[] bytes = new byte[16];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String encrypt(PublicKey wpxKey, byte[] payload) throws WpxException {
		try {
			Cipher c = Cipher.getInstance(OAEP);
			c.init(Cipher.ENCRYPT_MODE, wpxKey);
			return Base64.getEncoder().encodeToString(c.doFinal(payload));
		} catch (GeneralSecurityException e) {
			throw new WpxException("加密失敗（" + e.getClass().getSimpleName() + "）");
		}
	}

	/** 簽章字串：九欄依序以 | 串接，空值用空字串，不做任何大小寫或空白處理 */
	static String signData(String appId, String kid, String ts, String nonce, String cipher, String dbAlias,
			String machine, String os, String ip) {
		return String.join("|", appId, kid, ts, nonce, cipher, dbAlias, machine, os, ip);
	}

	static String sign(PrivateKey key, String data) throws WpxException {
		try {
			Signature s = Signature.getInstance(SIGN_ALG);
			s.initSign(key);
			s.update(data.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(s.sign());
		} catch (GeneralSecurityException e) {
			throw new WpxException("簽章失敗（" + e.getClass().getSimpleName() + "）");
		}
	}

	/** 取 PEM 的 Base64 本體並解碼；type 為 BEGIN／END 後的標籤（PUBLIC KEY、PRIVATE KEY） */
	static byte[] pemBody(String pem, String type) {
		String b64 = pem.replace("-----BEGIN " + type + "-----", "")
				.replace("-----END " + type + "-----", "")
				.replaceAll("\\s", "");
		return Base64.getDecoder().decode(b64);
	}

	private static String trimTrailingSlash(String url) {
		String s = url == null ? "" : url.trim();
		while (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	private static String localHostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (IOException e) {
			return "";
		}
	}

	/** 不丟例外，交由 call 自行判斷狀態碼（避免 HttpStatusCodeException 訊息夾帶回應本文） */
	private static class PassThroughErrorHandler implements ResponseErrorHandler {
		@Override
		public boolean hasError(ClientHttpResponse response) {
			return false;
		}

		@Override
		public void handleError(URI url, HttpMethod method, ClientHttpResponse response) {
			// hasError 恆為 false，不會進來
		}
	}
}
