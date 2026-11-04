package com.mpx.infra_manager.controller;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：GET /api/v1/codes（代碼表，不需身分）、/home/stats、/home/expiring、/todos（spec §4.1）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（spec_im_acct_insp_v2.0.md §6.1、§6.3）：身分改 Identity（Bearer）；/codes 也需登入
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-16
// 變更說明: BACKLOG 第 68 項批 2：Java 套件 com.mpx.im_acct_insp 改名為 com.mpx.infra_manager（專案改名 infra_manager），只改 package／import／全名引用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: BACKLOG 第 158 項 R9：加 GET /home/review-abnormal（鈴鐺類別 2 審查異常未處理；殼 jar HomeController 同步加轉送）
// ============================================================

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mpx.infra_manager.common.Identity;
import com.mpx.infra_manager.model.Codes;
import com.mpx.infra_manager.model.ExpiringAcct;
import com.mpx.infra_manager.model.HomeStats;
import com.mpx.infra_manager.model.ReviewAbnormalItem;
import com.mpx.infra_manager.model.TodoItem;
import com.mpx.infra_manager.service.CodeService;
import com.mpx.infra_manager.service.HomeService;

@RestController
@RequestMapping("/api/v1")
public class HomeController {

	private final CodeService codeService;
	private final HomeService homeService;

	public HomeController(CodeService codeService, HomeService homeService) {
		this.codeService = codeService;
		this.homeService = homeService;
	}

	@GetMapping("/codes")
	public Codes codes(Identity id) {
		return codeService.getCodes();
	}

	@GetMapping("/home/stats")
	public HomeStats stats(Identity id) {
		return homeService.stats(id);
	}

	@GetMapping("/home/expiring")
	public List<ExpiringAcct> expiring(Identity id) {
		return homeService.expiring(id);
	}

	/** 鈴鐺類別 2（R9）：審查異常未處理，範圍由 Service 依 REVIEW_LIST 決定 */
	@GetMapping("/home/review-abnormal")
	public List<ReviewAbnormalItem> reviewAbnormal(Identity id) {
		return homeService.reviewAbnormal(id);
	}

	@GetMapping("/todos")
	public List<TodoItem> todos(Identity id) {
		return homeService.todos(id);
	}
}
