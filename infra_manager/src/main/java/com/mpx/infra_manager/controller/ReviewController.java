package com.mpx.infra_manager.controller;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: 新增：定期權限審查 API（BACKLOG 第 158 項 R2）：GET /reviews（清單，依狀態與關鍵字篩選）；簽核／駁回由 R3 追加
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: BACKLOG 第 158 項 R3a：POST /reviews/{acctId}/sign（簽一半；half＝INFRA／GOV、versionNo）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: BACKLOG 第 158 項 R3b：POST /reviews/{acctId}/reject（駁回；half、reason、versionNo）
// ============================================================

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.model.ReviewActionResult;
import com.mpx.infra_manager.model.ReviewItem;
import com.mpx.infra_manager.model.request.ReviewRejectForm;
import com.mpx.infra_manager.model.request.ReviewSignForm;
import com.mpx.infra_manager.service.ReviewService;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {

	private final ReviewService reviewService;

	public ReviewController(ReviewService reviewService) {
		this.reviewService = reviewService;
	}

	@GetMapping("/reviews")
	public List<ReviewItem> list(Identity id, @RequestParam(required = false) String keyword,
			@RequestParam(required = false) String stateCode) {
		return reviewService.list(id, keyword, stateCode);
	}

	@PostMapping("/reviews/{acctId}/sign")
	public ReviewActionResult sign(Identity id, @PathVariable String acctId, @RequestBody(required = false) ReviewSignForm form) {
		return reviewService.sign(id, acctId, form);
	}

	@PostMapping("/reviews/{acctId}/reject")
	public ReviewActionResult reject(Identity id, @PathVariable String acctId,
			@RequestBody(required = false) ReviewRejectForm form) {
		return reviewService.reject(id, acctId, form);
	}
}
