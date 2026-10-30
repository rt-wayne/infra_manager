package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: 新增：定期權限審查查詢（BACKLOG 第 158 項 R2；SA §5.10）
//           清單需 REVIEW_LIST（裁示 ⑬A：看全部用這個碼，沒有就 403，不做「只看自己」）；狀態篩選值不在四種審查狀態回 400；
//           關鍵字由這裡跳脫 LIKE 萬用字元；「今天」取自 Clock（測試可固定），交給 DAO 綁參數算狀態。簽核與駁回由 R3 追加
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: BACKLOG 第 158 項 R3a：簽核 sign（half＝INFRA／GOV，各需 REVIEW_SIGN_INFRA／REVIEW_SIGN_GOV）。
//           順序：400 簽核角色 → 403 → E02 版本號 → 交易內：404 查無 → 409 不在待複審／逾期未審（②A、⑨A）→
//           沒有進行中一輪：versionNo 須為 0（①B），取序列、INSERT 本輪只簽本半（⑮A）；撞 IND_IM_ACCT_REVIEW_02（兩人同時首簽）
//           就重讀那一輪改走 UPDATE → 有一輪：版本相符、⑪A 另一半的有效身分或實際操作人不是我 → UPDATE 本半（0 筆＝409 E08）→
//           兩半都簽了：本輪改 DONE、主表上次＝今天、下次＝今天＋180（①A）、歷史 REVIEW_DONE（㉑A，帶 REVIEW_ID）。
//           單半簽核不寫歷史、不寄信；駁回、守門由 R3b 追加
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: BACKLOG 第 158 項 R3a 審查修正：①A 簽核交易一開始先鎖帳號主表那一列（AcctDao.lockForReview），
//           同帳號的簽核、標旗標、關卡 6 取消進行中一輪排隊執行，讀到的狀態與那一輪才算數；
//           M2 ③A REVIEW_DONE 的 IS_SELF_ACTION 改為「兩半任一簽核人（有效身分）是原新增申請人」，標籤改顯示「自行審查」
// ============================================================

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
import com.mpx.infra_manager.dao.AcctDao;
import com.mpx.infra_manager.dao.AcctHistDao;
import com.mpx.infra_manager.dao.ReviewDao;
import com.mpx.infra_manager.model.AcctHist;
import com.mpx.infra_manager.model.ReviewActionResult;
import com.mpx.infra_manager.model.ReviewItem;
import com.mpx.infra_manager.model.request.ReviewSignForm;

@Service
public class ReviewService {

	private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

	static final String LABEL_STATE = "狀態";
	static final String LABEL_VERSION = "版本號";
	/** 同帳號一筆進行中的唯一函式索引；ORA-00001 訊息含此名稱才是「兩人同時首簽」 */
	static final String IDX_OPEN_ROUND = "IND_IM_ACCT_REVIEW_02";
	/** IM_ACCT_HIST.RESULT_DESCR 欄寬（與 RequestService 同） */
	static final int RESULT_DESCR_MAX = 1000;
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final PermissionService perm;
	private final ReviewDao reviewDao;
	private final AcctDao acctDao;
	private final AcctHistDao acctHistDao;
	private final TransactionTemplate tx;
	private final Clock clock;

	public ReviewService(PermissionService perm, ReviewDao reviewDao, AcctDao acctDao, AcctHistDao acctHistDao,
			TransactionTemplate tx, Clock clock) {
		this.perm = perm;
		this.reviewDao = reviewDao;
		this.acctDao = acctDao;
		this.acctHistDao = acctHistDao;
		this.tx = tx;
		this.clock = clock;
	}

	/** 清單（REVIEW_LIST）：stateCode 空＝異常＋逾期未審＋待複審；給 NORMAL 才列正常的 */
	public List<ReviewItem> list(Identity id, String keyword, String stateCode) {
		perm.require(id, AcctConst.PERM_REVIEW_LIST);
		String state = Texts.trimToNull(stateCode);
		if (state != null && !AcctConst.REVIEW_STATES.contains(state)) {
			throw AcctMsg.badCode(LABEL_STATE);
		}
		return reviewDao.findList(Texts.escapeLike(Texts.trimToNull(keyword)), state, LocalDate.now(clock));
	}

	/**
	 * 簽核一半（REVIEW_SIGN_INFRA／REVIEW_SIGN_GOV）。狀態在交易內以當天重讀（簽核當下才算數）；
	 * versionNo＝進行中那一輪的版本，還沒有一輪時帶 0（①B）
	 */
	public ReviewActionResult sign(Identity id, String acctId, ReviewSignForm form) {
		String half = Texts.trimToNull(form == null ? null : form.getHalf());
		if (half == null || !AcctConst.REVIEW_HALVES.contains(half)) {
			throw AcctMsg.badCode(AcctMsg.WHAT_REVIEW_HALF);
		}
		boolean infra = AcctConst.HALF_INFRA.equals(half);
		perm.require(id, infra ? AcctConst.PERM_REVIEW_SIGN_INFRA : AcctConst.PERM_REVIEW_SIGN_GOV);
		if (form.getVersionNo() == null) {
			throw AcctMsg.required(LABEL_VERSION);
		}
		int v = form.getVersionNo();
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
		LocalDate today = now.toLocalDate();

		ReviewActionResult r = tx.execute(s -> {
			// ①A：先鎖帳號列，同帳號的另一半簽核、緊急存取標旗標、關卡 6 取消本輪都排在這之後，下面讀到的狀態與那一輪才算數
			acctDao.lockForReview(acctId);
			ReviewItem item = loadSignable(acctId, today);
			if (item.getReviewId() == null) {
				// 前端看到的是「還沒有一輪」卻帶了版本：它看的那一輪已結束（或資料已變），要重新整理
				if (v != 0) {
					throw AcctMsg.concurrent();
				}
				long reviewId = reviewDao.nextReviewId();
				LocalDate due = item.getNextReviewDate() == null ? today : item.getNextReviewDate().toLocalDate();
				try {
					reviewDao.insertOpen(reviewId, acctId, due, half, id.getUserId(), id.getRealUserId(), now);
					return result(acctId, reviewId, AcctConst.RR_OPEN, 0);
				} catch (DuplicateKeyException e) {
					if (!isOpenRoundDup(e)) {
						log.error("建立定期審查本輪發生非進行中索引的唯一鍵衝突 acctId={} reviewId={}", acctId, reviewId);
						throw e;
					}
					// 兩人同時首簽：對方剛建好本輪，重讀後改走 UPDATE（⑮A）
					log.info("定期審查首簽撞進行中索引，改走更新 acctId={}", acctId);
					item = loadSignable(acctId, today);
					if (item.getReviewId() == null) {
						throw AcctMsg.concurrent();
					}
				}
			} else if (v != item.getReviewVersionNo()) {
				throw AcctMsg.concurrent();
			}
			long reviewId = item.getReviewId();
			int version = item.getReviewVersionNo();
			checkSameSigner(id, item, infra);
			if (reviewDao.signHalf(reviewId, version, half, id.getUserId(), id.getRealUserId(), now) != 1) {
				throw AcctMsg.concurrent();
			}
			version++;
			boolean otherSigned = infra ? item.getGovSignBy() != null : item.getInfraSignBy() != null;
			if (!otherSigned) {
				return result(acctId, reviewId, AcctConst.RR_OPEN, version);
			}
			// 雙簽完成（①A）：本輪 DONE → 主表日期 → 歷史，任一步沒寫到整筆回滾
			LocalDate next = ReviewRule.nextReview(today);
			if (reviewDao.markDone(reviewId, version, next, now, id.getRealUserId()) != 1) {
				throw AcctMsg.concurrent();
			}
			version++;
			int rows = acctDao.updateReviewDates(acctId, today, next, now, id.getRealUserId());
			if (rows != 1) {
				log.error("定期審查完成寫主表日期影響筆數 {}（預期 1），交易回復 acctId={} reviewId={}", rows, acctId, reviewId);
				throw ApiException.internal("資料寫入未完成（帳號 " + acctId + "），請聯絡系統管理員");
			}
			acctHistDao.insert(doneHist(id, item, reviewId, infra, now, today, next));
			return result(acctId, reviewId, AcctConst.RR_DONE, version);
		});
		log.info("定期審查簽核 acctId={} half={} reviewId={} state={} signBy={} realUser={}", acctId, half, r.getReviewId(),
				r.getReviewStateCode(), id.getUserId(), id.getRealUserId());
		return r;
	}

	/** 交易內重讀：查無 404；狀態不是待複審／逾期未審 409（含異常、正常、不在範圍） */
	private ReviewItem loadSignable(String acctId, LocalDate today) {
		ReviewItem item = reviewDao.findByAcct(acctId, today);
		if (item == null) {
			throw AcctMsg.notFound();
		}
		// 不在範圍時狀態為 null（List.of 的 contains(null) 會丟 NPE，先判空）
		if (item.getReviewStateCode() == null || !AcctConst.REVIEW_SIGNABLE_STATES.contains(item.getReviewStateCode())) {
			throw ApiException.conflict(AcctMsg.REVIEW_NOT_SIGNABLE);
		}
		return item;
	}

	/** ⑪A：另一半的簽核人（有效身分）＝我的有效身分、或另一半的實際操作人＝我的實際操作人 → 409 */
	private static void checkSameSigner(Identity id, ReviewItem item, boolean infra) {
		String otherBy = infra ? item.getGovSignBy() : item.getInfraSignBy();
		String otherActBy = infra ? item.getGovSignActBy() : item.getInfraSignActBy();
		if (id.getUserId().equals(otherBy) || id.getRealUserId().equals(otherActBy)) {
			throw ApiException.conflict(AcctMsg.REVIEW_SAME_SIGNER);
		}
	}

	/**
	 * REVIEW_DONE 歷史（㉑A）：SOURCE_CODE＝REVIEW、REVIEW_ID＝本輪；REQ_ID／EMG_ID／STAGE_ID／APPLY_DEPT 空；
	 * ACT_BY＝第二半的實際操作人、ACT_AS＝代理身分、IS_SELF_ACTION＝兩半任一簽核人（有效身分）是該帳號原新增申請人
	 * （兩半判準一致；歷史畫面標籤由 RequestService.selfLabel 依來源 REVIEW 顯示「自行審查」）。
	 * item 是簽第二半之前讀到的，所以本半的簽核人與時間用當下的值
	 */
	private static AcctHist doneHist(Identity id, ReviewItem item, long reviewId, boolean infra, LocalDateTime now,
			LocalDate today, LocalDate next) {
		String infraName = infra ? id.getUserName() : item.getInfraSignByName();
		LocalDateTime infraDate = infra ? now : item.getInfraSignDate();
		String govName = infra ? item.getGovSignByName() : id.getUserName();
		LocalDateTime govDate = infra ? item.getGovSignDate() : now;
		StringBuilder sb = new StringBuilder();
		sb.append("定期審查完成（雙簽）；Infra 半 ").append(infraName).append(" 於 ").append(DATE_TIME_FMT.format(infraDate));
		sb.append("；治理半 ").append(govName).append(" 於 ").append(DATE_TIME_FMT.format(govDate));
		sb.append("；上次審查日改為 ").append(DATE_FMT.format(today)).append("、下次應審查日 ").append(DATE_FMT.format(next));

		AcctHist h = new AcctHist();
		h.setAcctId(item.getAcctId());
		h.setReviewId(reviewId);
		h.setSourceCode(AcctConst.HIST_SRC_REVIEW);
		h.setActionCode(AcctConst.ACT_REVIEW_DONE);
		h.setActBy(id.getRealUserId());
		h.setActByName(id.getRealUserName());
		h.setActAsId(id.actAsId());
		h.setActAsName(id.actAsName());
		h.setActDate(now);
		h.setResultDescr(Texts.truncate(sb.toString(), RESULT_DESCR_MAX));
		String otherBy = infra ? item.getGovSignBy() : item.getInfraSignBy();
		String applyBy = item.getApplyBy();
		h.setIsSelfAction(applyBy != null && (applyBy.equals(id.getUserId()) || applyBy.equals(otherBy)) ? 1 : 0);
		h.setCreateBy(id.getRealUserId());
		return h;
	}

	/** 唯一鍵衝突是否來自同帳號進行中索引：沿例外鏈找 ORA-00001 訊息中的「.IND_IM_ACCT_REVIEW_02)」（寫法同 RequestService） */
	static boolean isOpenRoundDup(DuplicateKeyException e) {
		String token = "." + IDX_OPEN_ROUND + ")";
		for (Throwable t = e; t != null; t = t.getCause()) {
			String msg = t.getMessage();
			if (msg != null && msg.contains(token)) {
				return true;
			}
		}
		return false;
	}

	private static ReviewActionResult result(String acctId, long reviewId, String state, int version) {
		ReviewActionResult r = new ReviewActionResult();
		r.setAcctId(acctId);
		r.setReviewId(reviewId);
		r.setReviewStateCode(state);
		r.setVersionNo(version);
		return r;
	}
}
