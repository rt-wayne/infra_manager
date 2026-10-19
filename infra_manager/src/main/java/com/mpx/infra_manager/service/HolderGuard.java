package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：「最後一位持有人」檢查（BACKLOG 第 89 項階段 1；SA 4.7、裁示 ①B／②B／③A、architect G3 三條件寫法）
//           輸入「哪些權限碼失去了哪一組授予」（移除成員：角色＋人；第三頁存檔：角色＋碼），輸出：
//           (1) SYS_ROLE 是否由「有人持有」變成「無人持有」→ E24（不論有無在途；否則再也沒有人能從畫面維護角色）
//           (2) 哪些碼被在途案件卡住 → E23：碼 p 被擋的條件是三者同時成立——變更前持有人 ≥ 1、變更後持有人＝0、p 有在途單。
//               只看「變更後為 0」不夠：移除已停用的人、或拿掉一個沒有成員的角色的碼，變更前後都是 0，那些單本來就卡住，不是這次造成的。
//           持有人口徑同 PermDao.FIND_USERS_WITH_PERM（啟用中使用者；停用者 token 會被拒，不算持有人）。
//           在途：申請單依關卡 PERM_ID 資料驅動（RoleAdminDao.FIND_IN_FLIGHT_BY_STAGE_PERM）；EMG_DECIDE 另加緊急存取。
//           每個碼各自計算：一次只動一個角色的一組授予，碼之間沒有組合效應。
//           本類別只查不寫，須在呼叫端的交易內、取得全域鎖之後呼叫，查到的才是前一位管理員已提交的結果
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 階段 2 回合 1：加執行人對照的檢查 checkExecutor——同一套三條件，「持有人」換成該類別的啟用中執行人、
//           「在途」換成停在關卡 6 的同類別申請單（裁示 4.A）；Blocked.permId 放類別代碼，軌跡的 blockedPerms 跟著放類別代碼
// ============================================================

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.dao.RoleAdminDao;
import com.mpx.infra_manager.model.InFlightRow;

@Service
public class HolderGuard {

	/** 失去哪一組授予：成員指派（roleId＋userId）或角色權限列（roleId＋permId），兩者擇一 */
	public record Exclusion(String roleId, String userId, String permId) {

		public static Exclusion member(String roleId, String userId) {
			return new Exclusion(roleId, userId, null);
		}

		public static Exclusion rolePerm(String roleId, String permId) {
			return new Exclusion(roleId, null, permId);
		}
	}

	/** 某碼（執行人對照時為類別代碼）被在途案件卡住：單號（最多 ADMIN_BLOCKED_LOG_MAX 筆）與總數 */
	public record Blocked(String permId, List<InFlightRow> docs, int total) {
	}

	public record Result(boolean sysRoleGone, List<Blocked> blocked) {

		public boolean ok() {
			return !sysRoleGone && blocked.isEmpty();
		}

		public List<String> blockedPermIds() {
			return blocked.stream().map(Blocked::permId).toList();
		}

		public int blockedTotal() {
			return blocked.stream().mapToInt(Blocked::total).sum();
		}

		/** 被擋單號（跨碼合併，依碼的順序；每碼最多 ADMIN_BLOCKED_LOG_MAX 筆） */
		public List<String> blockedDocIds() {
			List<String> ids = new ArrayList<>();
			for (Blocked b : blocked) {
				for (InFlightRow d : b.docs()) {
					ids.add(d.getDocId());
				}
			}
			return ids;
		}
	}

	private final RoleAdminDao roleAdminDao;

	public HolderGuard(RoleAdminDao roleAdminDao) {
		this.roleAdminDao = roleAdminDao;
	}

	/** lostPermIds：這組授予涵蓋的權限碼（移除成員＝該角色授予的所有碼；第三頁＝拿掉的碼） */
	public Result check(Collection<String> lostPermIds, Exclusion excl) {
		boolean sysRoleGone = false;
		List<Blocked> blocked = new ArrayList<>();
		for (String permId : lostPermIds) {
			long after = roleAdminDao.countHolders(permId, excl.roleId(), excl.userId(), excl.permId());
			if (after > 0) {
				continue;
			}
			long before = roleAdminDao.countHolders(permId, null, null, null);
			if (before == 0) {
				// 變更前就無人持有：不是這次造成的，不擋
				continue;
			}
			if (AcctConst.PERM_SYS_ROLE.equals(permId)) {
				sysRoleGone = true;
				continue;
			}
			List<InFlightRow> docs = inFlight(permId);
			if (!docs.isEmpty()) {
				int keep = Math.min(docs.size(), AcctConst.ADMIN_BLOCKED_LOG_MAX);
				blocked.add(new Blocked(permId, List.copyOf(docs.subList(0, keep)), docs.size()));
			}
		}
		return new Result(sysRoleGone, List.copyOf(blocked));
	}

	/**
	 * 移除類別 catgId 的執行人 userId：同一套三條件——變更前執行人 ≥ 1、變更後＝0、該類別有停在關卡 6 的單才擋。
	 * 執行人對照與 SYS_ROLE 無關，sysRoleGone 恆為 false
	 */
	public Result checkExecutor(String catgId, String userId) {
		long after = roleAdminDao.countExecutors(catgId, userId);
		if (after > 0) {
			return new Result(false, List.of());
		}
		long before = roleAdminDao.countExecutors(catgId, null);
		if (before == 0) {
			// 變更前就沒有執行人（例如移除已停用者）：那些單本來就卡住，不是這次造成的
			return new Result(false, List.of());
		}
		List<InFlightRow> docs = roleAdminDao.findInFlightExecByCatg(catgId);
		if (docs.isEmpty()) {
			return new Result(false, List.of());
		}
		int keep = Math.min(docs.size(), AcctConst.ADMIN_BLOCKED_LOG_MAX);
		return new Result(false, List.of(new Blocked(catgId, List.copyOf(docs.subList(0, keep)), docs.size())));
	}

	/** 需要 permId 才能處理的在途案件（沒有卡單概念的碼回空） */
	List<InFlightRow> inFlight(String permId) {
		List<InFlightRow> docs = new ArrayList<>(roleAdminDao.findInFlightByStagePerm(permId));
		if (AcctConst.PERM_EMG_DECIDE.equals(permId)) {
			docs.addAll(roleAdminDao.findInFlightEmg());
		}
		return docs;
	}

	/** E23 訊息明細：「單號（關卡名）」最多 ADMIN_BLOCKED_MSG_MAX 筆，以「、」相接；超過加「（等共 N 件）」 */
	public static String describe(Result r) {
		List<String> parts = new ArrayList<>();
		int total = 0;
		for (Blocked b : r.blocked()) {
			total += b.total();
			for (InFlightRow d : b.docs()) {
				if (parts.size() < AcctConst.ADMIN_BLOCKED_MSG_MAX) {
					parts.add(d.getDocId() + "（" + d.getStageName() + "）");
				}
			}
		}
		String text = String.join("、", parts);
		return total > parts.size() ? text + "（等共 " + total + " 件）" : text;
	}
}
