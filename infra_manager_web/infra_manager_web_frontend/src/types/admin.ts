// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-19
// 變更說明: 新增：系統管理型別（BACKLOG 第 89 項階段 4；後端 /admin/*，PRD API 表「系統管理」）
//           grantDate 為後端 LocalDateTime 預設格式 yyyy-MM-ddTHH:mm:ss（未加 JsonFormat），畫面只取日期
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 5：角色權限設定（GET /admin/role-perms、POST /admin/roles/{roleId}/perms）型別
// ============================================================

/** 角色或執行人類別的一位成員（只列啟用中的指派） */
export interface AdminMember {
  userId: string
  userName: string | null
  /** 對象本人是否啟用中；停用者照列、畫面加註「（已停用）」 */
  userActive: boolean
  /** 指派（或重新加入）時間 */
  grantDate: string | null
  /** 指派人工號；初始資料為 SYSTEM */
  grantBy: string | null
}

/** GET /admin/roles 一列：不含 GENERAL 與停用角色 */
export interface AdminRole {
  roleId: string
  roleName: string
  roleDescr: string | null
  sortSeq: number | null
  /** 此角色授予的權限中文名（含尚未上線的碼） */
  permNames: string[]
  members: AdminMember[]
}

/** GET /admin/users 一列：可被指派的啟用中使用者 */
export interface AdminUser {
  userId: string
  userName: string
  deptName: string | null
}

/** 指派／移除的本文 */
export interface MemberPayload {
  userId: string
  reason: string
}

/** 指派／移除的回傳 */
export interface MemberChangeResult {
  roleId: string | null
  catgId: string | null
  userId: string
  result: 'GRANTED' | 'REVOKED'
}

/** 可在畫面維護的一個權限碼（只含可見碼） */
export interface AdminPerm {
  permId: string
  permName: string
  sortSeq: number | null
}

/** 依 IM_ACCT_PERM.MODULE_CODE 分組（ACCT／INSP／SYS／EMG）；組內依 SORT_SEQ */
export interface AdminPermModule {
  moduleCode: string
  perms: AdminPerm[]
}

/** 一個角色的權限現況 */
export interface AdminRolePerms {
  roleId: string
  roleName: string
  roleDescr: string | null
  /** GENERAL 為 true：畫面唯讀，後端也拒絕存檔 */
  readOnly: boolean
  /** 啟用中成員數；GENERAL＝啟用中使用者總數 */
  memberCount: number
  /** 已授予的可見碼 */
  grantedPermIds: string[]
  /** 已授予但畫面不顯示的碼數（尚未上線的碼），存檔不會變動 */
  hiddenGrantedCount: number
}

/** GET /admin/role-perms：含 GENERAL、不含停用角色 */
export interface RolePermMatrix {
  modules: AdminPermModule[]
  roles: AdminRolePerms[]
}

/** POST /admin/roles/{roleId}/perms 本文：只送差異 */
export interface RolePermPayload {
  added: string[]
  removed: string[]
  reason: string
}

/** 權限存檔的回傳 */
export interface RolePermSaveResult {
  roleId: string
  added: string[]
  removed: string[]
  result: 'SAVED'
}
