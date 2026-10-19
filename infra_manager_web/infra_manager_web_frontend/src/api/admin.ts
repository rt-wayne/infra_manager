// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-19
// 變更說明: 新增：系統管理 API（BACKLOG 第 89 項階段 4；PRD API 表「系統管理」）
//           角色清單、可指派人員、角色成員指派與移除；後端一律再驗 SYS_ROLE，前端只做顯示控管
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 5：角色權限設定的查詢與存檔
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 6：執行人對照的查詢、指派、移除（需 SYS_EXECUTOR）
// ============================================================
import http from './http'
import type {
  AdminExecCatg,
  AdminRole,
  AdminUser,
  MemberChangeResult,
  MemberPayload,
  RolePermMatrix,
  RolePermPayload,
  RolePermSaveResult
} from '../types/admin'

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

function execBase(catgId: string): string {
  return `/admin/executors/${encodeURIComponent(catgId)}`
}

/** 帳號類別與執行人：GET /admin/executors（需 SYS_EXECUTOR） */
export function listExecutors(): Promise<AdminExecCatg[]> {
  return http.get<AdminExecCatg[]>('/admin/executors').then(r => r.data)
}

/** 把人員加入某類別的執行人：POST /admin/executors/{catgId}/members */
export function grantExecutor(catgId: string, payload: MemberPayload): Promise<MemberChangeResult> {
  return http.post<MemberChangeResult>(`${execBase(catgId)}/members`, payload).then(r => r.data)
}

/** 把人員移出某類別的執行人：POST /admin/executors/{catgId}/members/{userId}/revoke */
export function revokeExecutor(catgId: string, userId: string, reason: string): Promise<MemberChangeResult> {
  return http
    .post<MemberChangeResult>(`${execBase(catgId)}/members/${encodeURIComponent(userId)}/revoke`, { reason })
    .then(r => r.data)
}

/** 角色 × 可見權限碼現況：GET /admin/role-perms（需 SYS_ROLE） */
export function getRolePerms(): Promise<RolePermMatrix> {
  return http.get<RolePermMatrix>('/admin/role-perms').then(r => r.data)
}

/** 存一個角色的權限差異：POST /admin/roles/{roleId}/perms（需 SYS_ROLE） */
export function saveRolePerms(roleId: string, payload: RolePermPayload): Promise<RolePermSaveResult> {
  return http.post<RolePermSaveResult>(`${roleBase(roleId)}/perms`, payload).then(r => r.data)
}
