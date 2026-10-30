// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-19
// 變更說明: 新增：角色權限設定頁的權限碼分組（BACKLOG 第 89 項階段 5；計畫「第三頁」分組段）
//           MODULE_CODE 換中文組名（ACCT 帳號、INSP 巡檢、SYS 系統管理、EMG 緊急存取）；
//           APPR_G2～G5、VERIFY_G7 在 DB 是 ACCT，畫面另拆成「簽核」組（與後端 AcctConst.PERM_STAGE_CODES 同一組碼）；
//           組的順序固定為 帳號 → 簽核 → 巡檢 → 緊急存取 → 系統管理，組內沿用後端給的順序（SORT_SEQ）；
//           不認得的 MODULE_CODE 排在最後、組名直接顯示代碼，避免日後新模組的碼在畫面上消失
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: BACKLOG 第 158 項 R2：加 REVIEW「定期審查」組（REVIEW_LIST、REVIEW_SIGN_INFRA、REVIEW_SIGN_GOV 進可見清單），排在緊急存取之後、系統管理之前
// ============================================================
import type { AdminPerm, AdminPermModule } from '../types/admin'

/** 簽核關卡的權限碼：畫面拆成獨立「簽核」組 */
export const STAGE_PERM_IDS: readonly string[] = ['APPR_G2', 'APPR_G3', 'APPR_G4', 'APPR_G5', 'VERIFY_G7']

const STAGE_GROUP = 'STAGE'

/** 組代碼 → 中文組名；陣列順序即畫面順序 */
const GROUP_ORDER: readonly { code: string; name: string }[] = [
  { code: 'ACCT', name: '帳號' },
  { code: STAGE_GROUP, name: '簽核' },
  { code: 'INSP', name: '巡檢' },
  { code: 'EMG', name: '緊急存取' },
  { code: 'REVIEW', name: '定期審查' },
  { code: 'SYS', name: '系統管理' }
]

/** 畫面上的一組 */
export interface PermGroup {
  code: string
  name: string
  perms: AdminPerm[]
}

/** 後端模組 → 畫面分組；空組不列 */
export function groupPerms(modules: readonly AdminPermModule[] | null | undefined): PermGroup[] {
  const byCode = new Map<string, AdminPerm[]>()
  const extra: string[] = []
  const known = new Set(GROUP_ORDER.map(g => g.code))
  for (const m of modules ?? []) {
    for (const p of m.perms ?? []) {
      const code = STAGE_PERM_IDS.includes(p.permId) ? STAGE_GROUP : m.moduleCode
      if (!byCode.has(code)) {
        byCode.set(code, [])
        if (!known.has(code)) extra.push(code)
      }
      byCode.get(code)?.push(p)
    }
  }
  const order = [...GROUP_ORDER, ...extra.map(code => ({ code, name: code }))]
  return order
    .map(g => ({ code: g.code, name: g.name, perms: byCode.get(g.code) ?? [] }))
    .filter(g => g.perms.length > 0)
}
