// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: 新增：首頁測試（BACKLOG 第 46 項 S8）
//           第一階段六張卡回歸（名稱、數字、載入失敗顯示「—」並 toast、「30 天內到期」依 ACC_LIST_FULL 分流）；
//           緊急存取兩張卡：數字、點擊依 EMG_VIEW_ALL 分流（有 → 清單帶 state，沒有 → 無權限頁）
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-29
// 變更說明: BACKLOG 第 148 項：主管（acctAll／emgAll）標題不變；一般人標題加「我的」；巡檢區依 inspVisible 顯示；
//           緊急存取卡改人人可點（原「沒有 EMG_VIEW_ALL → 無權限頁」測試移除）
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import type { Router } from 'vue-router'
import HomeView from '../src/views/HomeView.vue'
import { getHomeStats } from '../src/api/common'
import { resetSession, useSession } from '../src/composables/useSession'
import type { HomeStats } from '../src/types/common'
import { GENERAL_PERMS, httpError, lastToast, makeMe, makeRouter } from './helpers'

vi.mock('../src/api/common', () => ({
  getCodes: vi.fn(),
  getHomeStats: vi.fn(),
  listExpiring: vi.fn(),
  listTodos: vi.fn(),
  listEmgReturned: vi.fn().mockResolvedValue([])
}))

const statsMock = vi.mocked(getHomeStats)

/** 主管（看全部）的回應 */
const STATS: HomeStats = {
  acctTotal: 25, acctReviewing: 10, acctExpiring: 4, acctPriv: 3,
  inspThisMonth: 7, inspExcptThisMonth: 2,
  emgUndecided: 5, emgReturned: 1,
  acctAll: true, emgAll: true, inspVisible: true
}

/** 一般人（只看自己、沒有巡檢權限）的回應 */
const MINE: HomeStats = {
  ...STATS, acctTotal: 2, acctReviewing: 1, acctExpiring: 0, acctPriv: 0,
  inspThisMonth: null, inspExcptThisMonth: null, emgUndecided: 1, emgReturned: 0,
  acctAll: false, emgAll: false, inspVisible: false
}

describe('首頁', () => {
  let router: Router

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe()
    statsMock.mockReset().mockResolvedValue({ ...STATS })
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  async function mountHome(): Promise<VueWrapper> {
    router = await makeRouter('/')
    const w = mount(HomeView, { global: { plugins: [router] } })
    await flushPromises()
    return w
  }

  /** 每張卡「標籤 → 數字」 */
  function cards(w: VueWrapper): Record<string, string> {
    return Object.fromEntries(w.findAll('.stat-card').map(c => [c.find('.lbl').text(), c.find('.num').text()]))
  }

  it('主管（看全部）：三個區塊八張卡，名稱不加「我的」，數字照後端', async () => {
    const w = await mountHome()
    expect(w.findAll('.module-card h2').map(h => h.text())).toEqual(['🔑 帳號權限管理', '🚨 緊急存取', '🛠 機房巡檢'])
    expect(cards(w)).toEqual({
      '帳號總數': '25',
      '待審核': '10',
      '30 天內到期（含已逾期）⚠️': '4',
      '特權帳號': '3',
      '緊急存取待補核准': '5',
      '緊急存取異常待追蹤': '1',
      '本月巡檢次數': '7',
      '本月發現異常項目數（未處理）': '2'
    })
    w.unmount()
  })

  it('一般人（只看自己）：帳號與緊急存取六張標題加「我的」、數字照後端，沒有巡檢區', async () => {
    statsMock.mockResolvedValueOnce({ ...MINE })
    const w = await mountHome()
    expect(w.findAll('.module-card h2').map(h => h.text())).toEqual(['🔑 帳號權限管理', '🚨 緊急存取'])
    expect(w.find('.card-insp').exists()).toBe(false)
    expect(cards(w)).toEqual({
      '我的帳號總數': '2',
      '我的待審核': '1',
      '我的30 天內到期（含已逾期）⚠️': '0',
      '我的特權帳號': '0',
      '我的緊急存取待補核准': '1',
      '我的緊急存取異常待追蹤': '0'
    })
    w.unmount()
  })

  it('兩區各自依旗標：只有帳號區看全部時，帳號區不加「我的」、緊急存取加；有巡檢權限的一般人看得到巡檢區', async () => {
    statsMock.mockResolvedValueOnce({ ...MINE, acctAll: true, inspVisible: true, inspThisMonth: 7, inspExcptThisMonth: 2 })
    const w = await mountHome()
    const labels = Object.keys(cards(w))
    expect(labels).toContain('帳號總數')
    expect(labels).toContain('我的緊急存取待補核准')
    expect(w.find('.card-insp').exists()).toBe(true)
    expect(cards(w)['本月巡檢次數']).toBe('7')
    w.unmount()
  })

  it('數字 0 照常顯示；載入失敗帳號與緊急存取六張顯示「—」、標題不加「我的」、巡檢區不顯示，並 toast 後端訊息', async () => {
    statsMock.mockResolvedValueOnce({ ...STATS, emgUndecided: 0, emgReturned: 0 })
    let w = await mountHome()
    expect(w.find('.card-emg-undecided .num').text()).toBe('0')
    expect(w.find('.card-emg-returned .num').text()).toBe('0')
    w.unmount()

    statsMock.mockRejectedValueOnce(httpError(500, { message: '資料庫連線失敗' }))
    w = await mountHome()
    expect(cards(w)).toEqual({
      '帳號總數': '—', '待審核': '—', '30 天內到期（含已逾期）⚠️': '—', '特權帳號': '—',
      '緊急存取待補核准': '—', '緊急存取異常待追蹤': '—'
    })
    expect(w.find('.card-insp').exists()).toBe(false)
    expect(lastToast(w)).toBe('資料庫連線失敗')
    w.unmount()
  })

  it('「30 天內到期」：有 ACC_LIST_FULL → 帳號列表到期篩選；沒有 → 無權限頁', async () => {
    let w = await mountHome()
    await w.find('.card-expiring').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/no-access')
    w.unmount()

    useSession().me.value = makeMe({ permissions: [...GENERAL_PERMS, 'ACC_LIST_FULL'] })
    w = await mountHome()
    await w.find('.card-expiring').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/accounts?expiring=1')
    w.unmount()
  })

  it('緊急存取兩張卡：不論有無 EMG_VIEW_ALL 都進清單並帶 state（待補核准 UNDECIDED、異常待追蹤 RETURNED）', async () => {
    const expected: [string, string][] = [['.card-emg-undecided', 'UNDECIDED'], ['.card-emg-returned', 'RETURNED']]
    for (const [sel, state] of expected) {
      // 預設 makeMe 只有 GENERAL（沒有 EMG_VIEW_ALL）；後端回只看自己的數字
      statsMock.mockResolvedValueOnce({ ...MINE })
      const w = await mountHome()
      await w.find(sel).trigger('click')
      await flushPromises()
      expect(router.currentRoute.value.path).toBe('/emergency')
      expect(router.currentRoute.value.query).toEqual({ state })
      w.unmount()
    }
  })
})
