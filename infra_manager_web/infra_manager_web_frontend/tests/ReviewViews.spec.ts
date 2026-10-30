// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-30
// 變更說明: 新增：定期審查前端測試（BACKLOG 第 158 項 R5 第 1 單位）
//           清單：預設不帶狀態碼（需處理）、欄位與狀態標籤顏色、兩半簽核人文字、特權標籤與門禁卡待發卡、
//           狀態下拉重查、網址 ?state= 合法值當預設、失敗 toast、空結果；API：listReviews 空值不送、sign／reject 路徑與本文
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import http from '../src/api/http'
import ReviewListView from '../src/views/ReviewListView.vue'
import { listReviews } from '../src/api/review'
import { resetSession, useSession } from '../src/composables/useSession'
import type { ReviewItem } from '../src/types/review'
import { GENERAL_PERMS, httpError, lastToast, makeMe, makeRouter } from './helpers'

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
