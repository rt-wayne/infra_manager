// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: 新增：定期審查型別（BACKLOG 第 158 項 R5；後端 GET /reviews、POST /reviews/{acctId}/sign|reject，PRD API 表「定期審查」）
//           reviewStateCode 為後端查詢當下計算的帳號審查狀態（不存欄位）；reviewId 起十欄＝進行中那一輪（IM_ACCT_REVIEW OPEN 列），沒有就 null
//           時間欄位一律 yyyy-MM-dd HH:mm:ss 字串
// ============================================================

/** 帳號審查狀態（計算值）：審查異常／逾期未審／待複審／正常 */
export type ReviewStateCode = 'ABNORMAL' | 'OVERDUE' | 'DUE' | 'NORMAL'

/** 簽核半邊：Infra 主管半（需 REVIEW_SIGN_INFRA）／資訊治理主管半（需 REVIEW_SIGN_GOV） */
export type ReviewHalf = 'INFRA' | 'GOV'

/** 進行中那一輪的結果（動作回傳用）：OPEN＝還差另一半、DONE＝雙簽完成、REJECTED＝已駁回 */
export type ReviewRoundState = 'OPEN' | 'DONE' | 'REJECTED'

/** GET /reviews 一列（帳號主表欄＋計算狀態＋進行中那一輪） */
export interface ReviewItem {
  acctId: string
  catgId: string
  catgName: string | null
  acctName: string | null
  sysName: string | null
  assetClassId: string | null
  assetClassName: string | null
  isPriv: number | null
  holderName: string | null
  holderDept: string | null
  accessLevelCode: string | null
  acctStateCode: string
  /** 原新增申請人（駁回信收件人之一） */
  applyBy: string | null
  applyByName: string | null
  lastReviewDate: string | null
  nextReviewDate: string | null
  isReviewFlag: number | null
  reviewFlagReason: string | null
  reviewFlagSourceCode: string | null
  reviewFlagDate: string | null
  reviewStateCode: ReviewStateCode
  /** 以下十欄＝進行中那一輪；還沒有人簽時皆為 null */
  reviewId: number | null
  reviewVersionNo: number | null
  infraSignBy: string | null
  infraSignByName: string | null
  infraSignActBy: string | null
  infraSignDate: string | null
  govSignBy: string | null
  govSignByName: string | null
  govSignActBy: string | null
  govSignDate: string | null
}

/** GET /reviews 查詢條件（全部選填；stateCode 空＝異常＋逾期＋待複審） */
export interface ReviewQuery {
  keyword?: string
  stateCode?: string
}

/** 簽核／駁回的回傳 */
export interface ReviewActionResult {
  acctId: string
  reviewId: number
  reviewStateCode: ReviewRoundState
  /** 動作後的版本 */
  versionNo: number
}
