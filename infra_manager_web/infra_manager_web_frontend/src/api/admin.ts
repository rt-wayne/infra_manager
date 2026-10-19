// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-19
// 變更說明: 新增：系統管理 API（BACKLOG 第 89 項階段 4；PRD API 表「系統管理」）
//           角色清單、可指派人員、角色成員指派與移除；後端一律再驗 SYS_ROLE，前端只做顯示控管
// ============================================================
import http from './http'
import type { AdminRole, AdminUser, MemberChangeResult, MemberPayload } from '../types/admin'

function roleBase(roleId: string): string {
  return `/admin/roles/${encodeURIComponent(roleId)}`
}

/** 角色清單（含權限中文名與成員）：GET /admin/roles（需 SYS_ROLE） */
export function listAdminRoles(): Promise<AdminRole[]> {
  return http.get<AdminRole[]>('/admin/roles').then(r => r.data)
}

/** 可指派的啟用中人員：GET /admin/users（需 SYS_ROLE 或 SYS_EXECUTOR） */
export function listAdminUsers(): Promise<AdminUser[]> {
  return http.get<AdminUser[]>('/admin/users').then(r => r.data)
}

/** 把人員加入角色：POST /admin/roles/{roleId}/members */
export function grantRole(roleId: string, payload: MemberPayload): Promise<MemberChangeResult> {
  return http.post<MemberChangeResult>(`${roleBase(roleId)}/members`, payload).then(r => r.data)
}

/** 把人員移出角色：POST /admin/roles/{roleId}/members/{userId}/revoke */
export function revokeRole(roleId: string, userId: string, reason: string): Promise<MemberChangeResult> {
  return http
    .post<MemberChangeResult>(`${roleBase(roleId)}/members/${encodeURIComponent(userId)}/revoke`, { reason })
    .then(r => r.data)
}
