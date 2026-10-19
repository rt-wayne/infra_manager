package com.mpx.infra_manager_web.controller;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: 新增：系統管理（角色指派、執行人對照）API 轉送（BACKLOG 第 89 項階段 2）：
//           /admin/roles、/admin/users、/admin/roles/{roleId}/members、/admin/roles/{roleId}/members/{userId}/revoke、
//           /admin/executors、/admin/executors/{catgId}/members、/admin/executors/{catgId}/members/{userId}/revoke；
//           Authorization、Content-Type、查詢字串原樣轉送，不解析 payload；路徑變數經 BackendForwarder.seg 編碼
// ============================================================

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 頁面打 /infra_manager_web/api/v1/admin/...，原樣轉給後端同路徑。
 * 不解析 payload、不開 CrossOrigin；權限由後端判斷（SYS_ROLE／SYS_EXECUTOR）。
 */
@RestController
@RequestMapping("/api/v1")
public class AdminController {

	private final BackendForwarder backend;

	public AdminController(BackendForwarder backend) {
		this.backend = backend;
	}

	/** 角色與成員清單：GET <backend>/admin/roles */
	@GetMapping("/admin/roles")
	public ResponseEntity<byte[]> roles(HttpServletRequest request) {
		return backend.get("/admin/roles", request);
	}

	/** 指派候選人：GET <backend>/admin/users */
	@GetMapping("/admin/users")
	public ResponseEntity<byte[]> users(HttpServletRequest request) {
		return backend.get("/admin/users", request);
	}

	/** 指派角色：POST <backend>/admin/roles/{roleId}/members，JSON {userId, reason} */
	@PostMapping("/admin/roles/{roleId}/members")
	public ResponseEntity<byte[]> grantRole(@PathVariable("roleId") String roleId, HttpServletRequest request) {
		return backend.post("/admin/roles/" + BackendForwarder.seg(roleId) + "/members", request);
	}

	/** 移除角色：POST <backend>/admin/roles/{roleId}/members/{userId}/revoke，JSON {reason} */
	@PostMapping("/admin/roles/{roleId}/members/{userId}/revoke")
	public ResponseEntity<byte[]> revokeRole(@PathVariable("roleId") String roleId, @PathVariable("userId") String userId,
			HttpServletRequest request) {
		return backend.post("/admin/roles/" + BackendForwarder.seg(roleId) + "/members/" + BackendForwarder.seg(userId) + "/revoke",
				request);
	}

	/** 帳號類別與執行人清單：GET <backend>/admin/executors */
	@GetMapping("/admin/executors")
	public ResponseEntity<byte[]> executors(HttpServletRequest request) {
		return backend.get("/admin/executors", request);
	}

	/** 指派執行人：POST <backend>/admin/executors/{catgId}/members，JSON {userId, reason} */
	@PostMapping("/admin/executors/{catgId}/members")
	public ResponseEntity<byte[]> grantExecutor(@PathVariable("catgId") String catgId, HttpServletRequest request) {
		return backend.post("/admin/executors/" + BackendForwarder.seg(catgId) + "/members", request);
	}

	/** 移除執行人：POST <backend>/admin/executors/{catgId}/members/{userId}/revoke，JSON {reason} */
	@PostMapping("/admin/executors/{catgId}/members/{userId}/revoke")
	public ResponseEntity<byte[]> revokeExecutor(@PathVariable("catgId") String catgId, @PathVariable("userId") String userId,
			HttpServletRequest request) {
		return backend.post(
				"/admin/executors/" + BackendForwarder.seg(catgId) + "/members/" + BackendForwarder.seg(userId) + "/revoke", request);
	}
}
