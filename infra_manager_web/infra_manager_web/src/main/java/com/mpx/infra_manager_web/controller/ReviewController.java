package com.mpx.infra_manager_web.controller;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: 新增：定期審查 API 轉送（BACKLOG 第 158 項 R5 實測發現：R2～R3b 只加了後端 API、殼 jar 沒有對應轉送器，
//           前端打 /reviews 被殼 jar 直接 404）：/reviews（清單）、/reviews/{acctId}/sign（簽核）、/reviews/{acctId}/reject（駁回）；
//           Authorization、Content-Type、查詢字串原樣轉送，不解析 payload
// ============================================================

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 頁面打 /infra_manager_web/api/v1/reviews...，原樣轉給後端同路徑。
 * 不解析 payload、不開 CrossOrigin。
 */
@RestController
@RequestMapping("/api/v1")
public class ReviewController {

	private final BackendForwarder backend;

	public ReviewController(BackendForwarder backend) {
		this.backend = backend;
	}

	/** 定期審查清單：GET <backend>/reviews?keyword&stateCode */
	@GetMapping("/reviews")
	public ResponseEntity<byte[]> list(HttpServletRequest request) {
		return backend.get("/reviews", request);
	}

	/** 簽核一半：POST <backend>/reviews/{acctId}/sign，JSON {half, versionNo} */
	@PostMapping("/reviews/{acctId}/sign")
	public ResponseEntity<byte[]> sign(@PathVariable("acctId") String acctId, HttpServletRequest request) {
		return backend.post("/reviews/" + BackendForwarder.seg(acctId) + "/sign", request);
	}

	/** 駁回：POST <backend>/reviews/{acctId}/reject，JSON {half, versionNo, reason} */
	@PostMapping("/reviews/{acctId}/reject")
	public ResponseEntity<byte[]> reject(@PathVariable("acctId") String acctId, HttpServletRequest request) {
		return backend.post("/reviews/" + BackendForwarder.seg(acctId) + "/reject", request);
	}
}
