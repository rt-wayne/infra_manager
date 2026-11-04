package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：首頁卡片、鈴鐺到期清單、待辦簽核（spec §3.7、§4.1）
//           待辦只回登入者此刻能處理的：APPROVE＝REVIEW 且目前關卡的角色是我；EXEC＝EXEC 且類別在我的 IM_EXEC_MAP；
//           VERIFY＝VERIFY 且我具關卡 6 角色；合併後依送出時間排序
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §4.5、§4.13、§6.3）：
//           卡片：所有登入者、只回聚合數字（口徑見 HomeDao.FIND_STATS）；
//           鈴鐺：到期提醒範圍（含 EXPIRED）逐筆判斷相關性（ACC_EXPIRY_ALL、原新增申請人、該類別執行人），daysLeft＝到期日－今天；
//           待辦：APPROVE＝REVIEW 且目前關卡 PERM_ID 在我的權限；EXEC＝EXEC／6 且類別在我的執行人對照；
//           VERIFY＝VERIFY／7 且有關卡 7 權限；回 source、title、versionNo、複驗退回次數與原因（「曾被複驗退回」提示）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-29
// 變更說明: BACKLOG 第 148 項：卡片依個人——帳號四張有 ACC_EXPIRY_ALL 看全部、否則只算我（有效身分）原新增申請的帳號；
//           緊急存取兩張有 EMG_VIEW_ALL 看全部、否則只算我申請的單；巡檢兩張看全部，但只給有 INSP_VIEW 或上述任一「看全部」者
//           （沒有的人兩個數字回 null、inspVisible＝false，前端整區不顯示）。程式不判斷角色名稱（使用者 2026-10-29 裁示 ②A）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: BACKLOG 第 158 項 R6（待辦整合；裁示 ①A ②A ③A）：待辦加第二種來源 REVIEW——有 REVIEW_SIGN_INFRA／GOV 的人，
//           待複審與逾期未審、該身分還沒簽、另一位主管的簽核人（有效身分）不是我的帳號各成一列（一個帳號＋身分一列），
//           內嵌整筆 ReviewItem 給前端開簽核彈窗；申請單列照舊排前面，審查列接在後面（逾期最久的先）。
//           過濾條件與 ReviewService.checkSameSigner、前端 reviewActableHalves 同口徑
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: BACKLOG 第 158 項 R8（⑬A）：卡片加定期審查兩張——有 REVIEW_LIST 看全部、否則只算我（有效身分）原新增申請的帳號，回 reviewAll 旗標；
//           巡檢區顯示條件不納入 REVIEW_LIST
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: BACKLOG 第 158 項 R9（鈴鐺類別 2；裁示 ①A）：加 reviewAbnormal——審查異常未處理的帳號清單，有 REVIEW_LIST 看全部、
//           否則只列我（有效身分）原新增申請的（與首頁審查異常卡同口徑，不加該類別執行人；被否決的是 B 比照到期鈴鐺加執行人）
// ============================================================

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.dao.AcctReqDao;
import com.mpx.infra_manager.dao.HomeDao;
import com.mpx.infra_manager.dao.ReviewDao;
import com.mpx.infra_manager.model.ApprStage;
import com.mpx.infra_manager.model.ExpiringAcct;
import com.mpx.infra_manager.model.HomeStats;
import com.mpx.infra_manager.model.ReviewAbnormalItem;
import com.mpx.infra_manager.model.ReviewItem;
import com.mpx.infra_manager.model.TodoItem;

@Service
public class HomeService {

	public static final String KIND_APPROVE = "APPROVE";
	public static final String KIND_EXEC = "EXEC";
	public static final String KIND_VERIFY = "VERIFY";
	public static final String KIND_REVIEW = "REVIEW";

	/** 待辦來源（申請單列的 'ACCT_REQ' 由 AcctReqDao 的 SQL 字面值帶出，這裡只有審查列用） */
	public static final String SOURCE_REVIEW = "REVIEW";

	private final PermissionService perm;
	private final CodeService codeService;
	private final HomeDao homeDao;
	private final AcctReqDao acctReqDao;
	private final ReviewDao reviewDao;
	private final Clock clock;

	public HomeService(PermissionService perm, CodeService codeService, HomeDao homeDao, AcctReqDao acctReqDao,
			ReviewDao reviewDao, Clock clock) {
		this.perm = perm;
		this.codeService = codeService;
		this.homeDao = homeDao;
		this.acctReqDao = acctReqDao;
		this.reviewDao = reviewDao;
		this.clock = clock;
	}

	public HomeStats stats(Identity id) {
		boolean acctAll = perm.can(id, AcctConst.PERM_ACC_EXPIRY_ALL);
		boolean emgAll = perm.can(id, AcctConst.PERM_EMG_VIEW_ALL);
		// 定期審查區（R8 ⑬A）：有 REVIEW_LIST 的人看全部（點卡進清單筆數才一致），其他人只算我原新增申請的帳號
		boolean reviewAll = perm.can(id, AcctConst.PERM_REVIEW_LIST);
		// 巡檢區：有巡檢權限的人，或帳號／緊急存取任一區看全部的主管（主管沒有 INSP_VIEW 也要維持原本看得到）；REVIEW_LIST 不納入
		boolean inspVisible = acctAll || emgAll || perm.can(id, AcctConst.PERM_INSP_VIEW);
		HomeStats s = homeDao.findStats(LocalDate.now(clock), acctAll ? null : id.getUserId(), emgAll ? null : id.getUserId(),
				reviewAll ? null : id.getUserId());
		s.setAcctAll(acctAll);
		s.setEmgAll(emgAll);
		s.setReviewAll(reviewAll);
		s.setInspVisible(inspVisible);
		if (!inspVisible) {
			s.setInspThisMonth(null);
			s.setInspExcptThisMonth(null);
		}
		return s;
	}

	public List<ExpiringAcct> expiring(Identity id) {
		LocalDate today = LocalDate.now(clock);
		List<ExpiringAcct> result = new ArrayList<>();
		for (ExpiringAcct a : homeDao.findExpiring(today)) {
			if (perm.expiringRelated(id, a.getApplyBy(), a.getCatgId())) {
				a.setDaysLeft(a.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(today, a.getExpiryDate().toLocalDate()));
				result.add(a);
			}
		}
		return result;
	}

	/**
	 * 鈴鐺類別 2（R9；裁示 ①A）：審查異常未處理的帳號——有 REVIEW_LIST 看全部，否則只列我（有效身分）原新增申請的；
	 * 相關性由 SQL 過濾（REVIEW_MINE_COND），與首頁「審查異常待處理」卡同口徑，筆數一致
	 */
	public List<ReviewAbnormalItem> reviewAbnormal(Identity id) {
		boolean all = perm.can(id, AcctConst.PERM_REVIEW_LIST);
		return homeDao.findReviewAbnormal(LocalDate.now(clock), all ? null : id.getUserId());
	}

	public List<TodoItem> todos(Identity id) {
		Map<Integer, ApprStage> stages = codeService.stageMap();
		List<TodoItem> result = new ArrayList<>();
		List<Integer> myStages = new ArrayList<>();
		for (int s = AcctConst.STAGE_FIRST_APPR; s <= AcctConst.STAGE_LAST_APPR; s++) {
			if (perm.can(id, CodeService.stagePermId(stages, s))) {
				myStages.add(s);
			}
		}
		if (!myStages.isEmpty()) {
			addAll(result, acctReqDao.findTodoApprove(myStages), KIND_APPROVE);
		}
		if (!id.getExecCatgIds().isEmpty()) {
			addAll(result, acctReqDao.findTodoExec(id.getExecCatgIds()), KIND_EXEC);
		}
		if (perm.can(id, CodeService.stagePermId(stages, AcctConst.STAGE_VERIFY))) {
			addAll(result, acctReqDao.findTodoVerify(), KIND_VERIFY);
		}
		result.sort(Comparator.comparing(TodoItem::getSubmitDate, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(TodoItem::getReqId, Comparator.nullsLast(Comparator.naturalOrder())));
		// 定期審查（R6；裁示 ③A）：接在申請單後面，DAO 已依逾期最久的先排好
		result.addAll(reviewTodos(id));
		return result;
	}

	/**
	 * 定期審查待辦（裁示 ②A 一個帳號＋身分一列）：有 REVIEW_SIGN_INFRA／GOV 任一才查；每個待複審／逾期未審的帳號，
	 * 我有權限的身分還沒簽、且另一位主管的簽核人（有效身分）不是我 → 一列（與 ReviewService.checkSameSigner、
	 * 前端 reviewActableHalves 同口徑：不比實際操作人，切換身分代簽放行）
	 */
	private List<TodoItem> reviewTodos(Identity id) {
		boolean infra = perm.can(id, AcctConst.PERM_REVIEW_SIGN_INFRA);
		boolean gov = perm.can(id, AcctConst.PERM_REVIEW_SIGN_GOV);
		List<TodoItem> result = new ArrayList<>();
		if (!infra && !gov) {
			return result;
		}
		String me = id.getUserId();
		for (ReviewItem r : reviewDao.findTodo(LocalDate.now(clock))) {
			if (infra && r.getInfraSignBy() == null && !me.equals(r.getGovSignBy())) {
				result.add(reviewTodo(r, AcctConst.HALF_INFRA));
			}
			if (gov && r.getGovSignBy() == null && !me.equals(r.getInfraSignBy())) {
				result.add(reviewTodo(r, AcctConst.HALF_GOV));
			}
		}
		return result;
	}

	/** 審查列：reqId 填帳號單號、關卡名「{身分}待簽」、versionNo＝進行中那一輪的版本（沒有一輪＝0）、review 內嵌整筆 */
	static TodoItem reviewTodo(ReviewItem r, String half) {
		TodoItem t = new TodoItem();
		t.setSource(SOURCE_REVIEW);
		t.setActionKind(KIND_REVIEW);
		t.setReqId(r.getAcctId());
		t.setAcctId(r.getAcctId());
		t.setReviewId(r.getReviewId());
		t.setHalf(half);
		t.setReview(r);
		t.setCatgId(r.getCatgId());
		t.setCatgName(r.getCatgName());
		t.setAcctName(r.getAcctName());
		t.setIsPriv(r.getIsPriv());
		t.setApplyBy(r.getApplyBy());
		t.setApplyByName(r.getApplyByName());
		t.setStageName(AcctConst.reviewHalfName(half) + "待簽");
		t.setVersionNo(r.getReviewVersionNo() == null ? 0 : r.getReviewVersionNo());
		String name = NotificationService.acctNameText(r.getCatgId(), r.getAcctName());
		t.setTitle("定期審查 " + (r.getCatgName() == null ? r.getCatgId() : r.getCatgName()) + (name.isEmpty() ? "" : "：" + name));
		return t;
	}

	private static void addAll(List<TodoItem> target, List<TodoItem> rows, String kind) {
		for (TodoItem t : rows) {
			t.setActionKind(kind);
			t.setTitle(title(t));
			target.add(t);
		}
	}

	/** 待辦標題：「{申請類型} {帳號類別}：{帳號名稱}」；門禁卡尚無卡號顯示「（門禁卡，待發卡）」 */
	static String title(TodoItem t) {
		String name = NotificationService.acctNameText(t.getCatgId(), t.getAcctName());
		return AcctConst.reqTypeName(t.getReqTypeCode()) + " " + (t.getCatgName() == null ? t.getCatgId() : t.getCatgName())
				+ (name.isEmpty() ? "" : "：" + name);
	}
}
