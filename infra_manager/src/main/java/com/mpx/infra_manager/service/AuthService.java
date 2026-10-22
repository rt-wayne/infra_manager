package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-10
// 變更說明: 新增：登入、登出、/me、切換身分名單、切換身分／切回（spec v2.0 §2.1、§2.5、§6.2）
//           登入：工號不存在、停用、無 PWD_HASH → 仍做一次 dummyHash 後回 401 A01（回應時間不洩漏帳號是否存在）；
//           LOCK_DATE 非空 → 401 A02（即使密碼正確）；密碼錯 → LOGIN_FAIL_CNT＋1、達 5 次同句寫 LOCK_DATE，回 A01；
//           成功 → 歸 0、寫 LAST_LOGIN_DATE、發新 token（DB 只存 SHA-256、EXPIRE_DATE＝現在＋8 小時；允許多個 token 並存）。
//           切換身分：實際使用者需 SYS_IMPERSONATE（無權限 403 並寫 ADMIN_LOG DENY）；對象須 STATUS＝1 且不是自己；
//           UPDATE token 列與 INSERT 管理軌跡同一交易（spec §5）；切回未切換中 409 E20。
//           log 不記密碼、token、雜湊值
//           修改：登入成功或失敗計數的 UPDATE 影響 0 筆（查詢後才被鎖定）→ 401 A02、不發 token，
//           鎖定後不論密碼對錯一律 A02（避免並發猜密碼時以 A01／A02 判斷密碼是否正確）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 40 項：/me 另回 deptName（有效身分的 DEPT_NAME，供新增申請帶入申請單位）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 1：AdminLogDao.insert 多一個 reason 參數，三處切換身分軌跡傳 null（行為不變）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: BACKLOG 第 124 項階段 3 回合 1（裁示 D0B、E1、⑬B）：同步表 PX_SYNC_EMP 查無的人——登入比照停用回 A01（過渡行為，使用者裁示 A；
//           階段 7 改走 WPX 後換成 D5A「帳號無法使用」）、切換身分對象比照停用回 404；顯示名改只用中文名（第 67 項併入）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 5 回合 2（裁示 ⑪）：/me 的 deptName 改為當下查同步表主要部門（UserDao.findPrimaryDept），
//           不再讀 IM_ACCT_USER.DEPT_NAME；切換中為代理身分的部門
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 6（裁示 ⑫A）：/me 不再回 telNo（申請人分機改由使用者在表單自填）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 7 回合 1（登入改走 WPX；裁示 ①B、②A、③B、⑨、D3A、D4A、D5A、D9B）：
//           登入不再比對本系統密碼雜湊、不再計失敗次數與鎖定；順序為 工號空白或超長／密碼空白 → A01（不送 WPX）→
//           WPX 驗證（帳密錯 A01、帳號無法使用 A04、服務異常 A05）→ 查本系統帳號列 → 沒有就依資格自動建立
//           （CREATE_BY＝SYSTEM；資格不符 A04；同工號並發建立撞主鍵時重查）→ 停用或同步表查無 A04 →
//           寫最後登入時間、發 token。WPX 先驗、再查本系統（D5A：停用帳號不必知道密碼就被告知無法使用），
//           所以拿掉防計時的假雜湊；本系統拒絕的原因只寫 log（工號＋原因），回應一律 A04
// ============================================================

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.AcctMsg;
import com.mpx.infra_manager.common.ApiException;
import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.common.Texts;
import com.mpx.infra_manager.common.TokenCodec;
import com.mpx.infra_manager.dao.AdminLogDao;
import com.mpx.infra_manager.dao.LoginTokenDao;
import com.mpx.infra_manager.dao.UserDao;
import com.mpx.infra_manager.model.AcctUser;
import com.mpx.infra_manager.model.LoginResult;
import com.mpx.infra_manager.model.MeInfo;
import com.mpx.infra_manager.model.UserInfo;
import com.mpx.infra_manager.model.UserRoleRow;
import com.mpx.infra_manager.model.request.ImpersonateForm;
import com.mpx.infra_manager.model.request.LoginForm;

import tools.jackson.databind.json.JsonMapper;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);
	private static final JsonMapper JSON = JsonMapper.builder().build();
	/** TARGET_USER_ID 欄寬 */
	static final int USER_ID_MAX = 30;

	private final UserDao userDao;
	private final LoginTokenDao tokenDao;
	private final AdminLogDao adminLogDao;
	private final WpxAuthClient wpxAuthClient;
	private final TokenCodec tokenCodec;
	private final TokenIdentityProvider identityProvider;
	private final PermissionService permissionService;
	private final TransactionTemplate tx;
	private final Clock clock;

	public AuthService(UserDao userDao, LoginTokenDao tokenDao, AdminLogDao adminLogDao, WpxAuthClient wpxAuthClient,
			TokenCodec tokenCodec, TokenIdentityProvider identityProvider, PermissionService permissionService,
			TransactionTemplate tx, Clock clock) {
		this.userDao = userDao;
		this.tokenDao = tokenDao;
		this.adminLogDao = adminLogDao;
		this.wpxAuthClient = wpxAuthClient;
		this.tokenCodec = tokenCodec;
		this.identityProvider = identityProvider;
		this.permissionService = permissionService;
		this.tx = tx;
		this.clock = clock;
	}

	// ------------------------------------------------------------
	// 登入／登出
	// ------------------------------------------------------------

	public LoginResult login(LoginForm form) {
		String userId = form == null ? null : Texts.trimToNull(form.getUserId());
		String pwd = form == null ? null : form.getPassword();
		// 明顯無效的輸入不送 WPX（避免無謂地累加對方的錯誤次數）
		if (userId == null || userId.length() > USER_ID_MAX || pwd == null || pwd.isEmpty()) {
			throw ApiException.unauthorized(AcctMsg.A01);
		}
		// 先由 WPX 驗帳密，再查本系統（裁示 D5A）；WPX 分類與原始 Code 已由 WpxAuthClient 寫 log
		WpxAuthClient.Result wpx = wpxAuthClient.verify(userId, pwd);
		switch (wpx.outcome()) {
		case SUCCESS:
			break;
		case BAD_CREDENTIAL:
			throw ApiException.unauthorized(AcctMsg.A01);
		case ACCOUNT_UNUSABLE:
			throw ApiException.unauthorized(AcctMsg.A04);
		default:
			throw ApiException.unauthorized(AcctMsg.A05);
		}
		LocalDateTime now = LocalDateTime.now(clock);
		AcctUser u = findOrCreate(userId, now);
		if (u == null) {
			log.info("登入被拒（WPX 驗證通過，本系統無帳號且不符自動建立資格）userId={}", userId);
			throw ApiException.unauthorized(AcctMsg.A04);
		}
		if (!Integer.valueOf(1).equals(u.getStatus())) {
			log.info("登入被拒（WPX 驗證通過，本系統帳號已停用）userId={}", userId);
			throw ApiException.unauthorized(AcctMsg.A04);
		}
		// 同步表查無：放行的話下一個請求就會被 token 檢查踢回登入頁
		if (!TokenIdentityProvider.inSync(u.getInSync())) {
			log.info("登入被拒（WPX 驗證通過，同步表查無）userId={}", userId);
			throw ApiException.unauthorized(AcctMsg.A04);
		}
		if (userDao.loginOk(userId, now) != 1) {
			// 查詢後帳號列被刪除（DBA 手動操作）
			log.info("登入被拒（WPX 驗證通過，寫入最後登入時間時帳號列已不存在）userId={}", userId);
			throw ApiException.unauthorized(AcctMsg.A04);
		}
		String token = tokenCodec.newToken();
		String hash = TokenCodec.sha256Hex(token);
		tokenDao.insert(hash, userId, now.plusHours(AcctConst.TOKEN_MAX_HOURS), now);
		log.info("登入成功 userId={}", userId);
		LoginResult result = new LoginResult();
		result.setToken(token);
		result.setMe(me(identityProvider.build(hash, u, u)));
		return result;
	}

	/**
	 * 查本系統帳號列；沒有就依資格自動建立（裁示 ①B、D3A、D9B），建立後重查。資格不符回 null。
	 * 已有帳號列者不再看資格（裁示 D4A：調離資訊部照常放行；DBA 預建的例外人員也走這條）。
	 */
	private AcctUser findOrCreate(String userId, LocalDateTime now) {
		AcctUser u = userDao.findById(userId);
		if (u != null) {
			return u;
		}
		try {
			if (userDao.createOnFirstLogin(userId, now) == 1) {
				log.info("首次登入自動建立帳號 userId={}", userId);
			}
		} catch (DuplicateKeyException dup) {
			// 同一工號同時首次登入：另一個請求已建好，重查即可
			log.info("首次登入自動建立時帳號已由其他請求建立 userId={}", userId);
		}
		return userDao.findById(userId);
	}

	public void logout(Identity id) {
		tokenDao.logout(id.getTokenHash(), id.getRealUserId(), LocalDateTime.now(clock));
		log.info("登出 userId={}", id.getRealUserId());
	}

	// ------------------------------------------------------------
	// /me、/users
	// ------------------------------------------------------------

	public MeInfo me(Identity id) {
		MeInfo me = new MeInfo();
		me.setUserId(id.getUserId());
		me.setUserName(id.getUserName());
		me.setRealUserId(id.getRealUserId());
		me.setRealUserName(id.getRealUserName());
		me.setImpersonating(id.isImpersonating());
		me.setCanImpersonate(!id.isImpersonating() && permissionService.can(id, AcctConst.PERM_SYS_IMPERSONATE));
		me.setPermissions(id.getPermissions().stream().sorted().toList());
		me.setRoleIds(id.getRoleIds());
		me.setExecCatgIds(id.getExecCatgIds());
		me.setEmail(id.getEmail());
		// 部門只有 /me 用：當下查有效身分在同步表的主要部門（⑪ 主管優先、其次 DEPT_ID 最小；查無為 null）
		me.setDeptName(userDao.findPrimaryDept(id.getUserId()));
		return me;
	}

	/** 切換身分名單：STATUS＝1、不含實際使用者自己 */
	public List<UserInfo> users(Identity id) {
		if (!permissionService.realCan(id, AcctConst.PERM_SYS_IMPERSONATE)) {
			throw AcctMsg.forbidden();
		}
		Map<String, UserInfo> users = new LinkedHashMap<>();
		for (UserRoleRow row : userDao.findActiveWithRoles(id.getRealUserId())) {
			UserInfo u = users.computeIfAbsent(row.getUserId(), k -> {
				UserInfo n = new UserInfo();
				n.setUserId(row.getUserId());
				n.setUserName(Texts.displayName(row.getUserName(), row.getUserId()));
				n.setRoleIds(new ArrayList<>());
				return n;
			});
			if (row.getRoleId() != null && !u.getRoleIds().contains(row.getRoleId())) {
				u.getRoleIds().add(row.getRoleId());
			}
		}
		return new ArrayList<>(users.values());
	}

	// ------------------------------------------------------------
	// 切換身分（spec §2.5）
	// ------------------------------------------------------------

	public MeInfo impersonate(Identity id, ImpersonateForm form) {
		String target = form == null ? null : Texts.trimToNull(form.getTargetUserId());
		LocalDateTime now = LocalDateTime.now(clock);
		if (!permissionService.realCan(id, AcctConst.PERM_SYS_IMPERSONATE)) {
			adminLogDao.insert(now, id.getRealUserId(), id.getRealUserName(), AcctConst.ADM_IMPERSONATE,
					target != null && target.length() <= USER_ID_MAX ? target : null, null, null, AcctConst.ADM_DENY);
			log.info("切換身分被拒（無權限）userId={}", id.getRealUserId());
			throw AcctMsg.forbidden();
		}
		if (target == null) {
			throw AcctMsg.required("切換對象");
		}
		if (target.equals(id.getRealUserId())) {
			throw ApiException.badRequest(AcctMsg.IMPERSONATE_SELF);
		}
		AcctUser t = target.length() > USER_ID_MAX ? null : userDao.findById(target);
		// 對象須啟用且同步表查得到（查無的人切過去，下一個請求也會被自動切回）
		if (t == null || !Integer.valueOf(1).equals(t.getStatus()) || !TokenIdentityProvider.inSync(t.getInSync())) {
			throw AcctMsg.notFound();
		}
		String targetName = TokenIdentityProvider.displayName(t);
		tx.executeWithoutResult(s -> {
			if (tokenDao.setActAs(id.getTokenHash(), target, id.getRealUserId(), now) != 1) {
				throw AcctMsg.unauthorized();
			}
			adminLogDao.insert(now, id.getRealUserId(), id.getRealUserName(), AcctConst.ADM_IMPERSONATE, target,
					contentJson(targetName), null, AcctConst.ADM_SUCCESS);
		});
		log.info("切換身分 userId={} target={}", id.getRealUserId(), target);
		AcctUser real = userDao.findById(id.getRealUserId());
		return me(identityProvider.build(id.getTokenHash(), real, t));
	}

	public MeInfo endImpersonate(Identity id) {
		if (!id.isImpersonating()) {
			throw ApiException.conflict(AcctMsg.E20);
		}
		LocalDateTime now = LocalDateTime.now(clock);
		String prev = id.getUserId();
		String prevName = id.getUserName();
		tx.executeWithoutResult(s -> {
			if (tokenDao.setActAs(id.getTokenHash(), null, id.getRealUserId(), now) != 1) {
				throw AcctMsg.unauthorized();
			}
			adminLogDao.insert(now, id.getRealUserId(), id.getRealUserName(), AcctConst.ADM_IMPERSONATE_END, prev,
					contentJson(prevName), null, AcctConst.ADM_SUCCESS);
		});
		log.info("切回 userId={} from={}", id.getRealUserId(), prev);
		AcctUser real = userDao.findById(id.getRealUserId());
		return me(identityProvider.build(id.getTokenHash(), real, real));
	}

	/** CONTENT_JSON：切換時記對象顯示名 */
	static String contentJson(String targetUserName) {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("targetUserName", targetUserName);
		return JSON.writeValueAsString(m);
	}
}
