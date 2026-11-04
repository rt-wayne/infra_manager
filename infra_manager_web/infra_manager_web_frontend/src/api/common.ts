// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：共用 API（開發文件 spec_im_acct_insp.md 第 4.1 節）：/users、/me、/codes、/home/stats、/home/expiring、/todos
//           失敗時 Promise reject，由畫面 toast
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 6.3 節）：/users、/me 移到 api/auth.ts（與登入、切換身分同檔）
// AI版本  : Claude Fable 5 (claude-fable-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S9（SA §7.2 鈴鐺類別 3）：新增 listEmgReturned——沿用既有 GET /emergencies
//           固定帶 stateCode=RETURNED，不另開後端端點；該 API 的權限口徑（EMG_VIEW_ALL 看全部、
//           其他人只回本人的單）恰好就是類別 3 的「相關的人」
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: BACKLOG 第 158 項 R9（鈴鐺類別 2）：新增 listReviewAbnormal——GET /home/review-abnormal（後端依 REVIEW_LIST 決定看全部或只回我原新增申請的）
// ============================================================
import http from './http'
import type { Codes, ExpiringItem, HomeStats, ReviewAbnormalItem, Todo } from '../types/common'
import type { Emg } from '../types/emergency'
import { EMG_RETURNED } from '../utils/labels'

/** 代碼表：GET /codes */
export function getCodes(): Promise<Codes> {
  return http.get<Codes>('/codes').then(r => r.data)
}

/** 首頁卡片：GET /home/stats */
export function getHomeStats(): Promise<HomeStats> {
  return http.get<HomeStats>('/home/stats').then(r => r.data)
}

/** 鈴鐺到期清單：GET /home/expiring（只回與我相關的） */
export function listExpiring(): Promise<ExpiringItem[]> {
  return http.get<ExpiringItem[]>('/home/expiring').then(r => r.data)
}

/** 待辦：GET /todos（只回登入者此刻能處理的） */
export function listTodos(): Promise<Todo[]> {
  return http.get<Todo[]>('/todos').then(r => r.data)
}

/**
 * 鈴鐺類別 3（已退回未結案）：與 api/emergency.listEmergencies 同端點、固定 stateCode=RETURNED；
 * 無 EMG_VIEW_ALL 時後端只回本人的單＝SA §7.2 類別 3 的「相關的人」，前端不用再過濾
 */
export function listEmgReturned(): Promise<Emg[]> {
  return http.get<Emg[]>('/emergencies', { params: { stateCode: EMG_RETURNED } }).then(r => r.data)
}

/** 鈴鐺類別 2（審查異常未處理）：GET /home/review-abnormal（有 REVIEW_LIST 看全部，否則只回我原新增申請的） */
export function listReviewAbnormal(): Promise<ReviewAbnormalItem[]> {
  return http.get<ReviewAbnormalItem[]>('/home/review-abnormal').then(r => r.data)
}
