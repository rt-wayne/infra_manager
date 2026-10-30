package com.mpx.infra_manager.service;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: 新增：定期權限審查查詢（BACKLOG 第 158 項 R2；SA §5.10）
//           清單需 REVIEW_LIST（裁示 ⑬A：看全部用這個碼，沒有就 403，不做「只看自己」）；狀態篩選值不在四種審查狀態回 400；
//           關鍵字由這裡跳脫 LIKE 萬用字元；「今天」取自 Clock（測試可固定），交給 DAO 綁參數算狀態。簽核與駁回由 R3 追加
// ============================================================

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.mpx.infra_manager.common.AcctConst;
import com.mpx.infra_manager.common.AcctMsg;
import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.common.Texts;
import com.mpx.infra_manager.dao.ReviewDao;
import com.mpx.infra_manager.model.ReviewItem;

@Service
public class ReviewService {

	static final String LABEL_STATE = "狀態";

	private final PermissionService perm;
	private final ReviewDao reviewDao;
	private final Clock clock;

	public ReviewService(PermissionService perm, ReviewDao reviewDao, Clock clock) {
		this.perm = perm;
		this.reviewDao = reviewDao;
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
}
