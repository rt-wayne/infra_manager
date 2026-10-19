// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-19
// 變更說明: 新增：系統管理型別（BACKLOG 第 89 項階段 4；後端 /admin/*，PRD API 表「系統管理」）
//           grantDate 為後端 LocalDateTime 預設格式 yyyy-MM-ddTHH:mm:ss（未加 JsonFormat），畫面只取日期
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
