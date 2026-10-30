// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: 新增：定期審查 API（BACKLOG 第 158 項 R5；PRD API 表「定期審查」）；查詢條件空值不送；
//           簽核／駁回都帶 versionNo 樂觀鎖（進行中那一輪的版本，還沒有一輪時帶 0）
// ============================================================
import http from './http'
import type { ReviewActionResult, ReviewHalf, ReviewItem, ReviewQuery } from '../types/review'

function base(acctId: string): string {
  return `/reviews/${encodeURIComponent(acctId)}`
}

/** 定期審查清單：GET /reviews?keyword&stateCode（需 REVIEW_LIST；stateCode 空＝異常＋逾期＋待複審） */
export function listReviews(q: ReviewQuery): Promise<ReviewItem[]> {
  const params: ReviewQuery = {}
  if (q.keyword) params.keyword = q.keyword
  if (q.stateCode) params.stateCode = q.stateCode
  return http.get<ReviewItem[]>('/reviews', { params }).then(r => r.data)
}

/** 簽一半：POST /reviews/{acctId}/sign（half＝INFRA 需 REVIEW_SIGN_INFRA、GOV 需 REVIEW_SIGN_GOV） */
export function signReview(acctId: string, half: ReviewHalf, versionNo: number): Promise<ReviewActionResult> {
  return http.post<ReviewActionResult>(`${base(acctId)}/sign`, { half, versionNo }).then(r => r.data)
}

/** 駁回（整輪結束、帳號標審查異常）：POST /reviews/{acctId}/reject */
export function rejectReview(acctId: string, half: ReviewHalf, versionNo: number, reason: string): Promise<ReviewActionResult> {
  return http.post<ReviewActionResult>(`${base(acctId)}/reject`, { half, versionNo, reason }).then(r => r.data)
}
