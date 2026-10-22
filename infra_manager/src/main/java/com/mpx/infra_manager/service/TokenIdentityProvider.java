package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：第一階段 IdentityProvider 實作——本模組自建 token（spec v2.0 §2.1、§2.2、§2.5）
//           Bearer → SHA-256 → IM_ACCT_LOGIN_TOKEN；有效條件：未登出、現在 < EXPIRE_DATE（登入＋8 小時）、
//           現在 − LAST_ACCESS_DATE ≤ 30 分鐘、實際使用者 STATUS＝1；任一不符 401 A03。
//           通過後 LAST_ACCESS_DATE 節流為每 60 秒最多寫一次；代理身分 STATUS 變 0 時自動視為切回（清 ACT_AS_USER_ID，不寫軌跡）。
//           權限、角色、執行人類別每次請求自 DB 載入（不快取）。日後換共用登入只換此類別
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 28 項：閒置上限改 480 分鐘（AcctConst.TOKEN_IDLE_MINUTES，本類別程式未改）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 40 項：Identity 帶入有效身分的 DEPT_NAME
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: BACKLOG 第 124 項階段 3 回合 1（裁示 D0B、E1、⑬B）：token 有效另須實際使用者在同步表 PX_SYNC_EMP 查得到（USER_IN_SYNC＝1），
//           查無回 401 A03 並記一行專用 INFO log（D10A 不防同步異常，這是同步出錯那天唯一的診斷線索）；
//           代理身分同步表查無比照停用自動切回；顯示名改只用中文名（AcctUser.userName 已由同步表帶出）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 5 回合 2：Identity 不再帶部門（改由 /me 當下查同步表）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 6（裁示 ⑫A）：Identity 不再帶分機
// ============================================================

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.AcctMsg;
import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.common.IdentityProvider;
import com.mpx.infra_manager.common.Texts;
import com.mpx.infra_manager.common.TokenCodec;
import com.mpx.infra_manager.dao.LoginTokenDao;
import com.mpx.infra_manager.dao.UserDao;
import com.mpx.infra_manager.model.AcctUser;
import com.mpx.infra_manager.model.LoginToken;

@Service
public class TokenIdentityProvider implements IdentityProvider {

	private static final Logger log = LoggerFactory.getLogger(TokenIdentityProvider.class);

	private final LoginTokenDao tokenDao;
	private final UserDao userDao;
	private final PermissionService permissionService;
	private final Clock clock;

	public TokenIdentityProvider(LoginTokenDao tokenDao, UserDao userDao, PermissionService permissionService, Clock clock) {
		this.tokenDao = tokenDao;
		this.userDao = userDao;
		this.permissionService = permissionService;
		this.clock = clock;
	}

	@Override
	public Identity resolve(String authorizationHeader) {
		String token = TokenCodec.parseBearer(authorizationHeader);
		if (token == null) {
			throw AcctMsg.unauthorized();
		}
		String hash = TokenCodec.sha256Hex(token);
		LoginToken t = tokenDao.find(hash);
		LocalDateTime now = LocalDateTime.now(clock);
		if (!isValid(t, now)) {
			throw AcctMsg.unauthorized();
		}
		if (!inSync(t.getUserInSync())) {
			// token 本身有效、本系統也啟用，只是同步表查無：D10A 下不防同步異常，這一行是出事那天唯一的線索
			log.info("token 有效但同步表查無 userId={}", t.getUserId());
			throw AcctMsg.unauthorized();
		}
		if (needTouch(t.getLastAccessDate(), now)) {
			tokenDao.touch(hash, now);
		}
		String actAs = t.getActAsUserId();
		if (actAs != null && (!Integer.valueOf(1).equals(t.getActAsStatus()) || !inSync(t.getActAsInSync()))) {
			// 代理身分已停用或同步表查無：自動切回，不寫軌跡（spec §2.5、裁示 E1）
			tokenDao.setActAs(hash, null, t.getUserId(), now);
			actAs = null;
		}
		AcctUser real = userDao.findById(t.getUserId());
		if (real == null) {
			throw AcctMsg.unauthorized();
		}
		AcctUser eff = actAs == null ? real : userDao.findById(actAs);
		if (eff == null) {
			eff = real;
		}
		return build(hash, real, eff);
	}

	/** 依實際使用者與有效身分組出 Identity（權限自 DB 載入）；登入、切換身分後也用此方法 */
	public Identity build(String tokenHash, AcctUser real, AcctUser effective) {
		boolean impersonating = !real.getUserId().equals(effective.getUserId());
		PermissionService.Grants g = permissionService.load(effective.getUserId());
		return new Identity(tokenHash, real.getUserId(), displayName(real), effective.getUserId(), displayName(effective),
				impersonating, g.permissions(), g.roleIds(), g.execCatgIds(), effective.getEmail());
	}

	public static String displayName(AcctUser u) {
		return Texts.displayName(u.getUserName(), u.getUserId());
	}

	/** 同步表「查得到」旗標（SQL 的 CASE WHEN EXISTS … THEN 1 ELSE 0 END） */
	static boolean inSync(Integer flag) {
		return Integer.valueOf(1).equals(flag);
	}

	/** token 有效條件（spec §2.1）；同步表查得到另在 resolve 判斷（要記專用 log） */
	static boolean isValid(LoginToken t, LocalDateTime now) {
		if (t == null || t.getLogoutDate() != null || t.getExpireDate() == null || t.getLastAccessDate() == null) {
			return false;
		}
		if (!now.isBefore(t.getExpireDate())) {
			return false;
		}
		if (Duration.between(t.getLastAccessDate(), now).compareTo(Duration.ofMinutes(AcctConst.TOKEN_IDLE_MINUTES)) > 0) {
			return false;
		}
		return Integer.valueOf(1).equals(t.getUserStatus());
	}

	static boolean needTouch(LocalDateTime lastAccess, LocalDateTime now) {
		return Duration.between(lastAccess, now).getSeconds() >= AcctConst.TOKEN_TOUCH_SECONDS;
	}
}
