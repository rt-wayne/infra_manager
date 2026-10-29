// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：代碼表（GET /codes）查詢小工具：類別名稱、資產分類說明、簽核關卡人員
//           變更單 #1：M2 刪除 doorCatgName（待辦改以 catgId 判斷門禁卡，不再以名稱比對）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 6.3、8.3 節）：資產分類改看 reviewTrigger（審查條件碼）；
//           簽核路徑預覽改為 7 關名稱（/users 只開放給切換身分者，不再列人員）；新增 stageNameOf、suggestExpiryOf
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-29
// 變更說明: 簽核流程調整：新增 meetsReviewRule（同後端 ReviewRule.matches：審查條件碼、特權、門禁卡任一）；
//           buildPreview 改回 { stageId, name, skipped }，不符合審查條件時關卡 5（資訊主管）標 skipped（後端於關卡 4 核准後跳過）
// ============================================================
import type { Codes } from '../types/common'
import { DOOR_CATG_ID, STAGE_LABEL } from './labels'

/** 不符合審查條件時跳過的關卡（資訊主管） */
export const HEAD_STAGE_ID = 5

/** 類別代碼 → 名稱；優先用後端已附的名稱 */
export function catgNameOf(codes: Codes | null, catgId: string | null | undefined, given?: string | null): string {
  if (given) return given
  if (!catgId) return '—'
  return codes?.categories.find(c => c.catgId === catgId)?.catgName ?? catgId
}

/** 資產分類說明文字 */
export function describeAsset(codes: Codes | null, assetClassId: string | null | undefined): string {
  if (!assetClassId) return '未分類'
  const a = codes?.assetClasses.find(x => x.assetClassId === assetClassId)
  if (!a) return assetClassId
  return `${a.assetClassId}－${a.assetClassName}${a.reviewTrigger ? '　⚠️ 審查條件碼，須定期審查' : ''}`
}

/** 是否審查條件碼（IS_REVIEW_TRIGGER） */
export function isReviewTriggerAsset(codes: Codes | null, assetClassId: string): boolean {
  if (!assetClassId) return false
  return codes?.assetClasses.find(x => x.assetClassId === assetClassId)?.reviewTrigger === true
}

/** 審查條件（同後端 ReviewRule.matches）：審查條件碼、特權帳號、門禁卡任一成立 */
export function meetsReviewRule(codes: Codes | null, assetClassId: string, catgId: string, isPriv: boolean): boolean {
  return isReviewTriggerAsset(codes, assetClassId) || catgId === DOOR_CATG_ID || isPriv
}

/** 類別是否顯示「建議指定明確到期日」（IS_SUGGEST_EXPIRY） */
export function suggestExpiryOf(codes: Codes | null, catgId: string): boolean {
  return codes?.categories.find(c => c.catgId === catgId)?.suggestExpiry === true
}

/** 關卡代碼 → 名稱 */
export function stageNameOf(codes: Codes | null, stageId: number | null | undefined): string {
  if (typeof stageId !== 'number') return ''
  return codes?.stages.find(s => s.stageId === stageId)?.stageName ?? STAGE_LABEL[stageId] ?? String(stageId)
}

export interface PreviewStep {
  stageId: number
  name: string
  /** 不符合審查條件時關卡 5 免簽 */
  skipped: boolean
}

/** 簽核路徑預覽：依 /codes 的 stages 排序列 7 關名稱；代碼表沒有關卡時用 Table 6.4 的名稱；headSkipped 時關卡 5 標免簽 */
export function buildPreview(codes: Codes | null, headSkipped = false): PreviewStep[] {
  const stages = [...(codes?.stages ?? [])].sort((a, b) => a.stageId - b.stageId)
  const list = stages.length > 0
    ? stages.map(s => ({ stageId: s.stageId, name: s.stageName }))
    : Object.keys(STAGE_LABEL).map(Number).sort((a, b) => a - b).map(id => ({ stageId: id, name: STAGE_LABEL[id] }))
  return list.map(s => ({ ...s, skipped: headSkipped && s.stageId === HEAD_STAGE_ID }))
}
