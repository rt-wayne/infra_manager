// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：身分、代碼表、首頁、待辦的 API 型別（開發文件 spec_im_acct_insp.md 第 4.1 節）
//           欄位名照後端 camelCase；旗標欄（isDirect、isShow 等）對應 NUMBER(1)，以 number 表示
//           變更單 #1：M2 Todo 的 catgId、isPriv 改為必填（後端 /todos 一律回傳）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 6.2、6.3 節）：
//           Me 改為實際使用者／有效身分／permissions[]；新增 LoginResult；UserInfo 去掉 execCatgIds；
//           Category 加 enabled／suggestExpiry；AssetClass 改 reviewTrigger／selectable；Stage 改 permId；
//           ExpiringItem 加 catgId／acctStateCode；Todo 加 source／title／versionNo／verifyRejectCnt／verifyRejectReason 等
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 40 項：Me 加 deptName（有效身分的部門名稱，新增申請帶入申請單位）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S8：HomeStats 加 emgUndecided、emgReturned（首頁緊急存取兩張卡）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 6（裁示 ⑫A）：Me 拿掉 telNo（/me 不再回分機，申請人分機改自填）；deptName 註解改為同步表主要部門
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: BACKLOG 第 158 項 R6（待辦整合；裁示 ①A）：ActionKind 加 REVIEW；Todo 加 acctId、reviewId、half、review（審查列內嵌整筆 ReviewItem）；
//           申請單欄位（reqTypeCode、stageId、submitDate 等）在審查列為 null，型別跟著放寬
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: BACKLOG 第 158 項 R8：HomeStats 加 reviewOverdue、reviewAbnormal、reviewAll（首頁第四區定期審查兩張卡）
// ============================================================
import type { ReviewHalf, ReviewItem } from './review'

/** 後端錯誤回應本文：{ message } */
export interface ApiErrorBody {
  message?: string
}

/** GET /users 一列：切換身分名單（STATUS＝1、不含自己） */
export interface UserInfo {
  userId: string
  userName: string
  roleIds: string[]
}

/** GET /me：目前身分（切換中 userId／userName 為代理身分，realUserId／realUserName 為實際登入者） */
export interface Me {
  userId: string
  userName: string
  realUserId: string
  realUserName: string
  impersonating: boolean
  /** 實際使用者有 SYS_IMPERSONATE 且未切換中 */
  canImpersonate: boolean
  /** 有效身分的權限碼（含 GENERAL）；前端只用它決定導覽與按鈕顯示 */
  permissions: string[]
  roleIds: string[]
  /** 有效身分是哪些帳號類別的執行人 */
  execCatgIds: string[]
  /** 使用者資料的 Email（新增申請帶入申請人 Email；/me 未回傳時為 undefined） */
  email?: string | null
  /** 有效身分在同步表的主要部門名稱（新增申請帶入申請單位；查無為 null；/me 未回傳時為 undefined） */
  deptName?: string | null
}

/** POST /auth/login 回傳 */
export interface LoginResult {
  token: string
  me: Me
}

/** 帳號類別（IM_ACCT_CATG） */
export interface Category {
  catgId: string
  catgName: string
  catgDescr: string | null
  /** 開放新增申請（IS_ENABLED） */
  enabled: boolean
  /** 表單顯示「建議指定明確到期日」（IS_SUGGEST_EXPIRY） */
  suggestExpiry: boolean
  sortSeq: number
}

/** 資產分類碼（IM_ASSET_CLASS） */
export interface AssetClass {
  assetClassId: string
  assetTypeCode: string
  assetClassName: string
  /** 納入定期審查條件（IS_REVIEW_TRIGGER） */
  reviewTrigger: boolean
  /** 申請表單可選（IS_SELECTABLE） */
  selectable: boolean
  ctrlDescr: string | null
  sortSeq: number
}

/** 簽核關卡（IM_ACCT_APPR_STAGE）；stageTypeCode：SUBMIT／APPR／EXEC／VERIFY */
export interface Stage {
  stageId: number
  stageName: string
  stageTypeCode: string
  permId: string | null
}

/** GET /codes */
export interface Codes {
  categories: Category[]
  assetClasses: AssetClass[]
  stages: Stage[]
}

/** GET /home/stats（範圍由後端依權限決定，前端只照旗標顯示） */
export interface HomeStats {
  /** 帳號四張：acctAll 為 true 看全部（ACC_EXPIRY_ALL），否則只算我原新增申請的帳號 */
  acctTotal: number
  acctReviewing: number
  acctExpiring: number
  acctPriv: number
  /** 巡檢兩張看全部；inspVisible 為 false 時為 null（沒有巡檢權限也不是看全部的主管） */
  inspThisMonth: number | null
  inspExcptThisMonth: number | null
  /** 緊急存取待補核准（尚未處理，含逾期）；emgAll 為 true 看全部（EMG_VIEW_ALL），否則只算我申請的單 */
  emgUndecided: number
  /** 緊急存取異常待追蹤（已退回、尚未結案） */
  emgReturned: number
  /** 定期審查（第 158 項 R8）：逾期未定期審查（審查狀態逾期未審）；reviewAll 為 true 看全部（REVIEW_LIST），否則只算我原新增申請的帳號 */
  reviewOverdue: number
  /** 審查異常待處理（審查狀態審查異常；與逾期互斥、不分旗標來源） */
  reviewAbnormal: number
  acctAll: boolean
  emgAll: boolean
  inspVisible: boolean
  reviewAll: boolean
}

/** GET /home/expiring 一列（鈴鐺；含已逾期，daysLeft 已過為負數） */
export interface ExpiringItem {
  acctId: string
  acctName: string | null
  catgId: string
  catgName: string
  acctStateCode: string
  expiryDate: string
  daysLeft: number
}

/** 待辦可做的動作；REVIEW＝定期審查簽核（第 158 項 R6） */
export type ActionKind = 'APPROVE' | 'EXEC' | 'VERIFY' | 'REVIEW'

/** GET /todos 一列（申請單列或定期審查列） */
export interface Todo {
  /** 來源：ACCT_REQ 申請單／REVIEW 定期審查 */
  source: string
  /** 編號：申請單號；審查列是帳號單號（與該帳號的新增單號相同，列鍵要加來源與身分） */
  reqId: string
  /** 以下四欄只有審查列有值 */
  acctId: string | null
  reviewId: number | null
  /** 這一列要以哪個身分簽 */
  half: ReviewHalf | null
  /** 進行中那一輪的完整資料，直接給 ReviewSignModal */
  review: ReviewItem | null
  title: string
  /** 申請類型；審查列為 null */
  reqTypeCode: string | null
  /** 帳號類別代碼；DOOR＝機房門禁卡（決定回報執行時卡號是否必填） */
  catgId: string
  catgName: string
  acctName: string | null
  /** 是否特權帳號（NUMBER(1)） */
  isPriv: number
  /** 申請人（審查列：該帳號的原新增申請人，舊資料可能為 null） */
  applyBy: string | null
  applyByName: string | null
  /** 關卡；審查列 stageId 為 null、stageName 為「Infra 主管待簽」／「資訊治理主管待簽」 */
  stageId: number | null
  stageName: string
  /** 送出時間；審查列為 null（畫面改顯示下次應審查日） */
  submitDate: string | null
  actionKind: ActionKind
  /** 樂觀鎖版本號，動作時帶回（審查列＝進行中那一輪的版本，沒有一輪＝0） */
  versionNo: number
  verifyRejectCnt: number | null
  verifyRejectReason: string | null
}
