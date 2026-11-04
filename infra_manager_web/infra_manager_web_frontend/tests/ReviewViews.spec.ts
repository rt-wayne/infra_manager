// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: 新增：定期審查前端測試（BACKLOG 第 158 項 R5 第 1 單位）
//           清單：預設不帶狀態碼（需處理）、欄位與狀態標籤顏色、兩半簽核人文字、特權標籤與門禁卡待發卡、
//           狀態下拉重查、網址 ?state= 合法值當預設、失敗 toast、空結果；API：listReviews 空值不送、sign／reject 路徑與本文
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: 第 158 項 R5 第 2 單位：清單操作欄「審查」「停用申請」出現條件與捷徑 query、開彈窗與 changed 重查；
//           ReviewSignModal：摘要與兩半狀態、簽核鈕依權限與該半是否已簽、駁回半邊單選只在兩半都可選時顯示、
//           簽核確認框與 versionNo、雙簽完成提示、駁回原因必填與字數、駁回成功留窗顯示已駁回＋停用捷徑、409 關窗重查、500 留窗
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import http from '../src/api/http'
import ReviewSignModal from '../src/components/ReviewSignModal.vue'
import ReviewListView from '../src/views/ReviewListView.vue'
import { listReviews, rejectReview, signReview } from '../src/api/review'
import { resetSession, useSession } from '../src/composables/useSession'
import { useToast } from '../src/composables/useToast'
import type { ReviewItem } from '../src/types/review'
import { GENERAL_PERMS, httpError, lastToast, makeMe, makeRouter } from './helpers'

/** 單獨掛載彈窗時沒有 ToastHost，直接讀 toast 狀態的最後一則 */
function lastToastMsg(): string {
  const list = useToast().toasts.value
  return list.length ? list[list.length - 1].msg : ''
}

vi.mock('../src/api/common', () => ({
  getCodes: vi.fn(),
  getHomeStats: vi.fn(),
  listExpiring: vi.fn(),
  listTodos: vi.fn(),
  listEmgReturned: vi.fn().mockResolvedValue([])
}))
vi.mock('../src/api/review', () => ({
  listReviews: vi.fn(),
  signReview: vi.fn(),
  rejectReview: vi.fn()
}))

const listMock = vi.mocked(listReviews)
const signMock = vi.mocked(signReview)
const rejectMock = vi.mocked(rejectReview)

export function reviewItem(over: Partial<ReviewItem> = {}): ReviewItem {
  return {
    acctId: 'AR20261007-001', catgId: 'HOST', catgName: '主機帳號', acctName: 'erp_admin', sysName: 'ERP 正式機',
    assetClassId: 'H01', assetClassName: '伺服器－與營運直接相關', isPriv: 0,
    holderName: '王小明', holderDept: '資訊處', accessLevelCode: 'RW', acctStateCode: 'APPROVED',
    applyBy: '0000001', applyByName: 'Kevin (李凱鈞)',
    lastReviewDate: '2026-04-01 00:00:00', nextReviewDate: '2026-10-15 00:00:00',
    isReviewFlag: 0, reviewFlagReason: null, reviewFlagSourceCode: null, reviewFlagDate: null,
    reviewStateCode: 'OVERDUE',
    reviewId: null, reviewVersionNo: null,
    infraSignBy: null, infraSignByName: null, infraSignActBy: null, infraSignDate: null,
    govSignBy: null, govSignByName: null, govSignActBy: null, govSignDate: null,
    ...over
  }
}

describe('定期審查清單', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe({ permissions: [...GENERAL_PERMS, 'REVIEW_LIST'] })
    listMock.mockReset().mockResolvedValue([
      reviewItem(),
      reviewItem({
        acctId: 'AR20261007-002', catgId: 'DOOR', catgName: '機房門禁卡', acctName: null, sysName: null, isPriv: 1,
        reviewStateCode: 'DUE', nextReviewDate: '2026-11-10 00:00:00',
        reviewId: 7, reviewVersionNo: 1, infraSignBy: '0000004', infraSignByName: 'Terry (陳泰瑞)', infraSignActBy: '0000004',
        infraSignDate: '2026-10-30 09:15:00'
      }),
      reviewItem({ acctId: 'AR20261007-003', reviewStateCode: 'ABNORMAL', isReviewFlag: 1, reviewFlagReason: '定期審查駁回：權限過大' })
    ])
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  async function mountList(path = '/reviews'): Promise<VueWrapper> {
    const router = await makeRouter(path)
    const w = mount(ReviewListView, { global: { plugins: [router] } })
    await flushPromises()
    return w
  }

  it('預設不帶狀態碼（需處理）；列出單號、類別、名稱、使用人、日期只到日、狀態標籤與顏色', async () => {
    const w = await mountList()
    expect(listMock).toHaveBeenCalledWith({ keyword: '', stateCode: '' })
    const rows = w.findAll('tbody tr')
    expect(rows).toHaveLength(3)
    const cells = rows[0].findAll('td').map(c => c.text())
    expect(cells.slice(0, 7)).toEqual(['AR20261007-001', '主機帳號', 'erp_adminERP 正式機', '王小明', '2026-04-01', '2026-10-15', '逾期未審'])
    expect(rows[0].find('.tag').classes()).toContain('tag-warn')
    expect(rows[1].findAll('td')[6].find('.tag').text()).toBe('待複審')
    expect(rows[1].findAll('td')[6].find('.tag').classes()).toContain('tag-pending')
    expect(rows[2].findAll('td')[6].find('.tag').text()).toBe('審查異常')
    expect(rows[2].findAll('td')[6].find('.tag').classes()).toContain('tag-flag')
    w.unmount()
  })

  it('門禁卡未發卡顯示「（門禁卡，待發卡）」、特權加標籤；兩半：沒簽「—」、簽了顯示姓名與時間到分', async () => {
    const w = await mountList()
    const rows = w.findAll('tbody tr')
    expect(rows[0].findAll('td')[7].text()).toBe('—')
    expect(rows[0].findAll('td')[8].text()).toBe('—')
    expect(rows[0].find('.priv').exists()).toBe(false)
    const door = rows[1].findAll('td')
    expect(door[2].text()).toContain('（門禁卡，待發卡）')
    expect(door[2].find('.priv').text()).toBe('特權')
    expect(door[7].text()).toBe('Terry (陳泰瑞) 2026-10-30 09:15')
    expect(door[8].text()).toBe('—')
    w.unmount()
  })

  it('狀態下拉有「需處理」與四種狀態；改下拉即重查並帶狀態碼；關鍵字修剪後送出', async () => {
    const w = await mountList()
    const opts = w.findAll('select[aria-label=狀態] option').map(o => o.text())
    expect(opts).toEqual(['需處理（異常、逾期、待複審）', '審查異常', '逾期未審', '待複審', '正常'])
    await w.find('select[aria-label=狀態]').setValue('NORMAL')
    await flushPromises()
    expect(listMock).toHaveBeenLastCalledWith({ keyword: '', stateCode: 'NORMAL' })
    await w.find('input[aria-label=關鍵字]').setValue('  erp  ')
    await w.find('form.filters').trigger('submit')
    await flushPromises()
    expect(listMock).toHaveBeenLastCalledWith({ keyword: 'erp', stateCode: 'NORMAL' })
    w.unmount()
  })

  it('網址 ?state= 是下拉有的值當預設並首查即帶；不是的值視為需處理', async () => {
    let w = await mountList('/reviews?state=ABNORMAL')
    expect(listMock).toHaveBeenCalledTimes(1)
    expect(listMock).toHaveBeenCalledWith({ keyword: '', stateCode: 'ABNORMAL' })
    expect((w.find('select[aria-label=狀態]').element as HTMLSelectElement).value).toBe('ABNORMAL')
    w.unmount()

    listMock.mockClear()
    w = await mountList('/reviews?state=OPEN')
    expect(listMock).toHaveBeenCalledWith({ keyword: '', stateCode: '' })
    w.unmount()
  })

  it('查詢失敗只 toast（顯示後端訊息）；成功回空陣列才顯示查無資料', async () => {
    listMock.mockRejectedValueOnce(httpError(403, { message: '您沒有權限執行此操作' }))
    let w = await mountList()
    expect(lastToast(w)).toBe('您沒有權限執行此操作')
    expect(w.find('.empty').exists()).toBe(false)
    expect(w.find('table').exists()).toBe(false)
    w.unmount()

    listMock.mockResolvedValueOnce([])
    w = await mountList()
    expect(w.find('.empty').text()).toContain('查無資料')
    w.unmount()
  })

  it('操作欄：只有 REVIEW_LIST 時沒有「審查」；有任一簽核權限時待複審／逾期列有、異常列沒有', async () => {
    let w = await mountList()
    expect(w.find('.op-review').exists()).toBe(false)
    w.unmount()

    useSession().me.value = makeMe({ permissions: ['REVIEW_LIST', 'REVIEW_SIGN_GOV'] })
    w = await mountList()
    const rows = w.findAll('tbody tr')
    expect(rows[0].find('.op-review').exists()).toBe(true)
    expect(rows[1].find('.op-review').exists()).toBe(true)
    expect(rows[2].find('.op-review').exists()).toBe(false)
    w.unmount()
  })

  it('「停用申請」只在審查異常列、有 ACC_APPLY、帳號狀態可停用時出現；點了帶 reqType／acctId／acctName 進申請表單', async () => {
    listMock.mockResolvedValue([
      reviewItem({ acctId: 'AR20261007-003', reviewStateCode: 'ABNORMAL', isReviewFlag: 1 }),
      reviewItem({ acctId: 'AR20261007-004', reviewStateCode: 'ABNORMAL', isReviewFlag: 1, acctStateCode: 'DISABLED' }),
      reviewItem({ acctId: 'AR20261007-005', reviewStateCode: 'OVERDUE' })
    ])
    const router = await makeRouter('/reviews')
    let w: VueWrapper = mount(ReviewListView, { global: { plugins: [router] } })
    await flushPromises()
    let rows = w.findAll('tbody tr')
    expect(rows[0].find('.op-disable').text()).toBe('停用申請')
    expect(rows[1].find('.op-disable').exists()).toBe(false)
    expect(rows[2].find('.op-disable').exists()).toBe(false)
    await rows[0].find('.op-disable').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/requests/new')
    expect(router.currentRoute.value.query).toEqual({ reqType: 'DISABLE', acctId: 'AR20261007-003', acctName: 'erp_admin' })
    w.unmount()

    useSession().me.value = makeMe({ permissions: ['REVIEW_LIST'] })
    w = await mountList()
    rows = w.findAll('tbody tr')
    expect(rows[0].find('.op-disable').exists()).toBe(false)
    w.unmount()
  })

  it('點「審查」開彈窗帶該列；彈窗 changed 重查清單、close 關閉', async () => {
    useSession().me.value = makeMe({ permissions: ['REVIEW_LIST', 'REVIEW_SIGN_INFRA'] })
    const w = await mountList()
    expect(w.findComponent(ReviewSignModal).exists()).toBe(false)
    await w.findAll('tbody tr')[1].find('.op-review').trigger('click')
    const modal = w.findComponent(ReviewSignModal)
    expect(modal.exists()).toBe(true)
    expect(modal.props('item').acctId).toBe('AR20261007-002')
    expect(modal.find('h3').text()).toBe('定期審查 — AR20261007-002')

    modal.vm.$emit('changed')
    await flushPromises()
    expect(listMock).toHaveBeenCalledTimes(2)
    modal.vm.$emit('close')
    await flushPromises()
    expect(w.findComponent(ReviewSignModal).exists()).toBe(false)
    w.unmount()
  })
})

describe('定期審查簽核彈窗', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe({ permissions: [...GENERAL_PERMS, 'REVIEW_LIST', 'REVIEW_SIGN_INFRA'] })
    useToast().toasts.value = []
    signMock.mockReset()
    rejectMock.mockReset()
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  async function mountModal(item: ReviewItem): Promise<{ w: VueWrapper; router: Awaited<ReturnType<typeof makeRouter>> }> {
    const router = await makeRouter('/reviews')
    const w = mount(ReviewSignModal, { props: { item }, global: { plugins: [router] } })
    await flushPromises()
    return { w, router }
  }

  /** 治理半已由 Terry 簽、版本 1 的進行中一輪 */
  const govSigned = (): Partial<ReviewItem> => ({
    reviewId: 7, reviewVersionNo: 1, govSignBy: '0000004', govSignByName: 'Terry (陳泰瑞)', govSignActBy: '0000004', govSignDate: '2026-10-30 09:15:00'
  })

  it('摘要與兩半狀態；還沒有一輪時提示首簽建立本輪；只有 Infra 權限時只有「簽核 Infra 主管半」與「駁回」', async () => {
    const { w } = await mountModal(reviewItem({ holderDept: '資訊處' }))
    expect(w.find('h3').text()).toBe('定期審查 — AR20261007-001')
    expect(w.find('.sub').text()).toBe('狀態：逾期未審')
    expect(w.text()).toContain('王小明（資訊處）')
    expect(w.text()).toContain('2026-04-01')
    expect(w.find('.half-infra .v').text()).toBe('尚未簽核')
    expect(w.find('.half-gov .v').text()).toBe('尚未簽核')
    expect(w.text()).toContain('第一次簽核時建立本輪紀錄')
    expect(w.find('.act-sign-infra').text()).toBe('簽核 Infra 主管半')
    expect(w.find('.act-sign-gov').exists()).toBe(false)
    expect(w.find('.act-reject').exists()).toBe(true)
    expect(w.find('.act-disable').exists()).toBe(false)
    w.unmount()
  })

  it('兩半權限都有：兩半未簽兩顆簽核鈕、駁回出現半邊單選；治理半已簽只剩 Infra 鈕、駁回不顯示單選而預選 Infra', async () => {
    useSession().me.value = makeMe({ permissions: ['REVIEW_LIST', 'REVIEW_SIGN_INFRA', 'REVIEW_SIGN_GOV'] })
    let { w } = await mountModal(reviewItem())
    expect(w.find('.act-sign-infra').exists()).toBe(true)
    expect(w.find('.act-sign-gov').text()).toBe('簽核資訊治理主管半')
    await w.find('.act-reject').trigger('click')
    expect(w.findAll('input[name=review-reject-half]')).toHaveLength(2)
    expect((w.find('input.half-infra').element as HTMLInputElement).checked).toBe(true)
    expect(w.find('.reject-half-hint').exists()).toBe(false)
    w.unmount()

    ;({ w } = await mountModal(reviewItem({ ...govSigned(), reviewStateCode: 'DUE' })))
    expect(w.find('.half-gov .v').text()).toBe('Terry (陳泰瑞) 2026-10-30 09:15')
    expect(w.text()).not.toContain('第一次簽核時建立本輪紀錄')
    expect(w.find('.act-sign-infra').exists()).toBe(true)
    expect(w.find('.act-sign-gov').exists()).toBe(false)
    await w.find('.act-reject').trigger('click')
    expect(w.find('input[name=review-reject-half]').exists()).toBe(false)
    expect(w.find('.reject-half-hint').text()).toBe('以「Infra 主管半」駁回')
    w.unmount()
  })

  it('我的那一半已簽、或狀態不是待複審／逾期未審：沒有簽核與駁回鈕', async () => {
    let { w } = await mountModal(reviewItem({
      reviewId: 7, reviewVersionNo: 1, infraSignBy: '0000001', infraSignByName: 'Kevin (李凱鈞)', infraSignActBy: '0000001', infraSignDate: '2026-10-30 09:15:00'
    }))
    expect(w.find('.act-sign-infra').exists()).toBe(false)
    expect(w.find('.act-reject').exists()).toBe(false)
    w.unmount()

    ;({ w } = await mountModal(reviewItem({ reviewStateCode: 'ABNORMAL', isReviewFlag: 1, reviewFlagReason: '定期審查駁回：權限過大' })))
    expect(w.find('.flag-reason .v').text()).toBe('定期審查駁回：權限過大')
    expect(w.find('.act-sign-infra').exists()).toBe(false)
    expect(w.find('.act-reject').exists()).toBe(false)
    w.unmount()
  })

  it('簽核：確認框說明另一半狀況；沒有一輪帶版本 0；成功 toast、emit changed 與 close', async () => {
    signMock.mockResolvedValue({ acctId: 'AR20261007-001', reviewId: 8, reviewStateCode: 'OPEN', versionNo: 1 })
    const { w } = await mountModal(reviewItem())
    await w.find('.act-sign-infra').trigger('click')
    expect(w.find('.confirm-text').text()).toContain('另一半尚未簽核')
    await w.find('.confirm-sign').trigger('click')
    await flushPromises()
    expect(signMock).toHaveBeenCalledWith('AR20261007-001', 'INFRA', 0)
    expect(lastToastMsg()).toBe('AR20261007-001 已簽核Infra 主管半，待另一半簽核')
    expect(w.emitted('changed')).toHaveLength(1)
    expect(w.emitted('close')).toHaveLength(1)
    w.unmount()
  })

  it('簽核另一半已簽的輪：確認框寫本輪即完成、帶該輪版本；成功提示雙簽完成', async () => {
    signMock.mockResolvedValue({ acctId: 'AR20261007-001', reviewId: 7, reviewStateCode: 'DONE', versionNo: 3 })
    const { w } = await mountModal(reviewItem({ ...govSigned(), reviewStateCode: 'DUE' }))
    await w.find('.act-sign-infra').trigger('click')
    expect(w.find('.confirm-text').text()).toContain('本輪即完成')
    await w.find('.confirm-sign').trigger('click')
    await flushPromises()
    expect(signMock).toHaveBeenCalledWith('AR20261007-001', 'INFRA', 1)
    expect(lastToastMsg()).toBe('AR20261007-001 雙簽完成，下次應審查日已往後推 180 天')
    expect(w.emitted('close')).toHaveLength(1)
    w.unmount()
  })

  it('駁回：原因空白或超過 500 字擋下；成功送所選半邊與修剪後原因，彈窗留著顯示已駁回與「發起停用申請」、簽核鈕消失、只 emit changed', async () => {
    useSession().me.value = makeMe({ permissions: [...GENERAL_PERMS, 'REVIEW_SIGN_INFRA', 'REVIEW_SIGN_GOV'] })
    rejectMock.mockResolvedValue({ acctId: 'AR20261007-001', reviewId: 8, reviewStateCode: 'REJECTED', versionNo: 1 })
    const { w, router } = await mountModal(reviewItem())
    await w.find('.act-reject').trigger('click')
    await w.find('.confirm-reject').trigger('click')
    expect(lastToastMsg()).toBe('請填寫駁回原因')
    await w.find('#review-reject-reason').setValue('😀'.repeat(501))
    await w.find('.confirm-reject').trigger('click')
    expect(lastToastMsg()).toBe('駁回原因不可超過 500 字')
    expect(rejectMock).not.toHaveBeenCalled()

    await w.find('input.half-gov').setValue()
    await w.find('#review-reject-reason').setValue('  權限過大  ')
    await w.find('.confirm-reject').trigger('click')
    await flushPromises()
    expect(rejectMock).toHaveBeenCalledWith('AR20261007-001', 'GOV', 0, '權限過大')
    expect(lastToastMsg()).toBe('AR20261007-001 已駁回，帳號已標記審查異常')
    expect(w.find('.confirm-reject').exists()).toBe(false)
    expect(w.find('.rejected-box').text()).toContain('已駁回（資訊治理主管半）：權限過大')
    expect(w.find('.act-sign-infra').exists()).toBe(false)
    expect(w.find('.act-reject').exists()).toBe(false)
    expect(w.emitted('changed')).toHaveLength(1)
    expect(w.emitted('close')).toBeUndefined()

    await w.find('.act-disable').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/requests/new')
    expect(router.currentRoute.value.query).toEqual({ reqType: 'DISABLE', acctId: 'AR20261007-001', acctName: 'erp_admin' })
    w.unmount()
  })

  it('駁回成功但沒有 ACC_APPLY：不顯示「發起停用申請」', async () => {
    useSession().me.value = makeMe({ permissions: ['REVIEW_LIST', 'REVIEW_SIGN_INFRA'] })
    rejectMock.mockResolvedValue({ acctId: 'AR20261007-001', reviewId: 8, reviewStateCode: 'REJECTED', versionNo: 1 })
    const { w } = await mountModal(reviewItem())
    await w.find('.act-reject').trigger('click')
    await w.find('#review-reject-reason').setValue('使用人已調職')
    await w.find('.confirm-reject').trigger('click')
    await flushPromises()
    expect(rejectMock).toHaveBeenCalledWith('AR20261007-001', 'INFRA', 0, '使用人已調職')
    expect(w.find('.rejected-box').exists()).toBe(true)
    expect(w.find('.act-disable').exists()).toBe(false)
    w.unmount()
  })

  it('失敗：500 只 toast、確認框留著；409 toast 後端訊息、emit changed 與 close', async () => {
    signMock.mockRejectedValueOnce(httpError(500, { message: '資料寫入未完成' }))
    let { w } = await mountModal(reviewItem())
    await w.find('.act-sign-infra').trigger('click')
    await w.find('.confirm-sign').trigger('click')
    await flushPromises()
    expect(lastToastMsg()).toBe('資料寫入未完成')
    expect(w.find('.confirm-sign').exists()).toBe(true)
    expect(w.emitted('changed')).toBeUndefined()
    expect(w.emitted('close')).toBeUndefined()
    w.unmount()

    rejectMock.mockRejectedValueOnce(httpError(409, { message: '此帳號目前不在待複審或逾期未審狀態，不能簽核' }))
    ;({ w } = await mountModal(reviewItem()))
    await w.find('.act-reject').trigger('click')
    await w.find('#review-reject-reason').setValue('權限過大')
    await w.find('.confirm-reject').trigger('click')
    await flushPromises()
    expect(lastToastMsg()).toBe('此帳號目前不在待複審或逾期未審狀態，不能簽核')
    expect(w.find('.rejected-box').exists()).toBe(false)
    expect(w.emitted('changed')).toHaveLength(1)
    expect(w.emitted('close')).toHaveLength(1)
    w.unmount()
  })
})

describe('定期審查 API', () => {
  it('listReviews 空值不送；signReview／rejectReview 路徑與本文', async () => {
    const mod = await vi.importActual<typeof import('../src/api/review')>('../src/api/review')
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: [] })
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { acctId: 'AR20261007-001', reviewId: 7, reviewStateCode: 'OPEN', versionNo: 1 } })
    try {
      await mod.listReviews({ keyword: '', stateCode: '' })
      expect(get).toHaveBeenLastCalledWith('/reviews', { params: {} })
      await mod.listReviews({ keyword: 'erp', stateCode: 'DUE' })
      expect(get).toHaveBeenLastCalledWith('/reviews', { params: { keyword: 'erp', stateCode: 'DUE' } })
      await mod.signReview('AR20261007-001', 'INFRA', 0)
      expect(post).toHaveBeenLastCalledWith('/reviews/AR20261007-001/sign', { half: 'INFRA', versionNo: 0 })
      await mod.rejectReview('AR20261007-001', 'GOV', 1, '權限過大')
      expect(post).toHaveBeenLastCalledWith('/reviews/AR20261007-001/reject', { half: 'GOV', versionNo: 1, reason: '權限過大' })
    } finally {
      get.mockRestore()
      post.mockRestore()
    }
  })
})
