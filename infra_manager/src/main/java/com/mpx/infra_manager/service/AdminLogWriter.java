package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：名單維護的系統管理軌跡寫入與 CONTENT_JSON 組裝（BACKLOG 第 89 項階段 1；裁示 ②A／⑥A）
//           SUCCESS 由呼叫端在交易內寫（名單與軌跡同一交易，SA 第 1008 行）；DENY 在交易外單句寫（比照 AuthService 切換身分被拒），
//           寫失敗只記 error log、仍回原本的 403／409。
//           CONTENT_JSON 以 Jackson 組（不拼字串）；欄位宣告 VARCHAR2(2000 CHAR) 仍受單欄 4000 bytes 限制（BACKLOG 第 11 項），
//           組好超過 ADMIN_CONTENT_MAX_BYTES 就從可裁剪清單（被擋單號）逐筆丟、保留 blockedTotal，不截斷 JSON。
//           ACT_BY／ACT_BY_NAME／CREATE_BY＝實際操作人（切換身分中已被 E25 擋下，即本人）；TARGET_USER_ID 超過欄寬存 null；REASON 截 500 字
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 階段 3 回合 1：權限設定存檔（ROLE_PERM_SET）的擋下內容 putBlockedByPerm——一次存檔可拿掉多個碼、每碼各自有在途單，
//           所以依碼分組 blocked[{permId、total、ids[]}]，每碼最多 ADMIN_BLOCKED_PER_PERM_LOG_MAX 筆（6 碼 × 20 筆約 2.9 KB，
//           放 50 筆會超過 4000 bytes）；每碼的 ids 各自是可裁剪清單，超限時仍從最長的那組丟
// ============================================================

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.common.Texts;
import com.mpx.infra_manager.dao.AdminLogDao;

import tools.jackson.databind.json.JsonMapper;

@Service
public class AdminLogWriter {

	private static final Logger log = LoggerFactory.getLogger(AdminLogWriter.class);
	private static final JsonMapper JSON = JsonMapper.builder().build();
	/** TARGET_USER_ID 欄寬 */
	static final int USER_ID_MAX = 30;

	private final AdminLogDao adminLogDao;

	public AdminLogWriter(AdminLogDao adminLogDao) {
		this.adminLogDao = adminLogDao;
	}

	/** 成功軌跡；呼叫端須在交易內呼叫 */
	public void success(LocalDateTime now, Identity id, String actionCode, String targetUserId, Map<String, Object> content,
			String reason) {
		adminLogDao.insert(now, id.getRealUserId(), id.getRealUserName(), actionCode, safeTarget(targetUserId),
				contentJson(content, List.of()), safeReason(reason), AcctConst.ADM_SUCCESS);
	}

	/** 拒絕軌跡（無權限、E23、E24）；在交易外呼叫，寫失敗只記 log */
	public void deny(LocalDateTime now, Identity id, String actionCode, String targetUserId, Map<String, Object> content,
			List<List<String>> trimmable, String reason) {
		try {
			adminLogDao.insert(now, id.getRealUserId(), id.getRealUserName(), actionCode, safeTarget(targetUserId),
					contentJson(content, trimmable), safeReason(reason), AcctConst.ADM_DENY);
		} catch (RuntimeException e) {
			log.error("寫入 DENY 軌跡失敗 action={} actBy={} target={}", actionCode, id.getRealUserId(), targetUserId, e);
		}
	}

	public void deny(LocalDateTime now, Identity id, String actionCode, String targetUserId, Map<String, Object> content,
			String reason) {
		deny(now, id, actionCode, targetUserId, content, List.of(), reason);
	}

	/** 把 HolderGuard 的擋下結果放進 content（denyCode、blockedPerms[]、blockedTotal、blockedIds[]）；回傳可裁剪的單號清單 */
	public static List<String> putBlocked(Map<String, Object> content, HolderGuard.Result r) {
		content.put("denyCode", r.sysRoleGone() ? AcctConst.DENY_LAST_SYS_ROLE : AcctConst.DENY_IN_FLIGHT);
		content.put("blockedPerms", r.blockedPermIds());
		content.put("blockedTotal", r.blockedTotal());
		List<String> ids = new ArrayList<>(r.blockedDocIds());
		content.put("blockedIds", ids);
		return ids;
	}

	/**
	 * 權限設定存檔（ROLE_PERM_SET）被擋：denyCode、blocked[{permId、total、ids[]}]（每碼最多 ADMIN_BLOCKED_PER_PERM_LOG_MAX 筆）；
	 * 回傳每碼的 ids 清單，當作可裁剪清單
	 */
	public static List<List<String>> putBlockedByPerm(Map<String, Object> content, HolderGuard.Result r) {
		content.put("denyCode", r.sysRoleGone() ? AcctConst.DENY_LAST_SYS_ROLE : AcctConst.DENY_IN_FLIGHT);
		List<Map<String, Object>> blocked = new ArrayList<>();
		List<List<String>> trimmable = new ArrayList<>();
		for (HolderGuard.Blocked b : r.blocked()) {
			List<String> ids = new ArrayList<>();
			for (int i = 0; i < b.docs().size() && i < AcctConst.ADMIN_BLOCKED_PER_PERM_LOG_MAX; i++) {
				ids.add(b.docs().get(i).getDocId());
			}
			Map<String, Object> one = new LinkedHashMap<>();
			one.put("permId", b.permId());
			one.put("total", b.total());
			one.put("ids", ids);
			blocked.add(one);
			trimmable.add(ids);
		}
		content.put("blocked", blocked);
		return trimmable;
	}

	/**
	 * 組 CONTENT_JSON。UTF-8 位元組超過 ADMIN_CONTENT_MAX_BYTES 時，每輪從 trimmable 裡最長的那個清單丟掉最後一筆再重組，
	 * 直到符合或清單都空（此時照實回傳，由 DB 欄寬把關）
	 */
	static String contentJson(Map<String, Object> content, List<List<String>> trimmable) {
		String json = JSON.writeValueAsString(content);
		while (json.getBytes(StandardCharsets.UTF_8).length > AcctConst.ADMIN_CONTENT_MAX_BYTES) {
			List<String> longest = null;
			for (List<String> l : trimmable) {
				if (!l.isEmpty() && (longest == null || l.size() > longest.size())) {
					longest = l;
				}
			}
			if (longest == null) {
				break;
			}
			longest.remove(longest.size() - 1);
			json = JSON.writeValueAsString(content);
		}
		return json;
	}

	/** 依序放入的 content 骨架 */
	public static Map<String, Object> content() {
		return new LinkedHashMap<>();
	}

	static String safeTarget(String target) {
		return target != null && target.length() <= USER_ID_MAX ? target : null;
	}

	static String safeReason(String reason) {
		return Texts.truncate(Texts.trimToNull(reason), AcctConst.ADMIN_REASON_MAX);
	}
}
