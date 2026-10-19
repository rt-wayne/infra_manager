// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-19
// 變更說明: 新增：系統管理前端測試（BACKLOG 第 89 項階段 4 回合 1）
//           角色指派頁（唯讀）：頁首 GENERAL 說明、每角色一卡（名稱、說明、權限中文名、成員數）、
//           停用成員加「（已停用）」、指派日只取日期、沒有成員／沒有權限的提示、空清單、查詢失敗 toast（403 帶後端訊息）；
//           API：路徑與本文、角色代碼與工號經編碼
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import http from '../src/api/http'
import RoleAdminView from '../src/views/RoleAdminView.vue'
import { listAdminRoles } from '../src/api/admin'
import { resetSession, useSession } from '../src/composables/useSession'
import type { AdminMember, AdminRole } from '../src/types/admin'
import { GENERAL_PERMS, httpError, lastToast, makeMe, makeRouter } from './helpers'

vi.mock('../src/api/admin', () => ({
  listAdminRoles: vi.fn(),
  listAdminUsers: vi.fn(),
  grantRole: vi.fn(),
  revokeRole: vi.fn()
}))

const rolesMock = vi.mocked(listAdminRoles)

function member(over: Partial<AdminMember> = {}): AdminMember {
  return {
    userId: '2410303', userName: 'Hank (何嘉浤)', userActive: true,
    grantDate: '2026-10-07T17:26:31', grantBy: 'SYSTEM', ...over
  }
}

function role(over: Partial<AdminRole> = {}): AdminRole {
  return {
    roleId: 'SYS_ADMIN', roleName: '系統管理員', roleDescr: '切換身分、手動觸發排程', sortSeq: 1,
    permNames: ['角色指派', '切換身分'],
    members: [member(), member({ userId: '0000285', userName: 'Gary (許永成)', userActive: false, grantDate: '2026-10-15T10:45:19' })],
    ...over
  }
}

describe('角色指派頁（唯讀）', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe({ permissions: [...GENERAL_PERMS, 'SYS_ROLE'] })
    rolesMock.mockReset().mockResolvedValue([
      role(),
      role({ roleId: 'DC_ADMIN', roleName: '機房管理員', roleDescr: null, sortSeq: 3, permNames: [], members: [] })
    ])
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  async function mountView(): Promise<VueWrapper> {
    const router = await makeRouter('/admin/roles')
    const w = mount(RoleAdminView, { global: { plugins: [router] } })
    await flushPromises()
    return w
  }

  it('頁首說明一般人員由系統自動授予；依後端順序每角色一卡', async () => {
    const w = await mountView()
    expect(rolesMock).toHaveBeenCalledTimes(1)
    expect(w.find('.page-note').text()).toContain('一般人員權限由系統自動授予所有啟用中的使用者，不在此維護')
    const cards = w.findAll('.role-card')
    expect(cards.map(c => c.attributes('data-role'))).toEqual(['SYS_ADMIN', 'DC_ADMIN'])
    expect(cards[0].find('.role-name').text()).toBe('系統管理員（SYS_ADMIN）')
    expect(cards[0].find('.role-descr').text()).toBe('切換身分、手動觸發排程')
    expect(cards[0].findAll('.perm').map(p => p.text())).toEqual(['角色指派', '切換身分'])
    expect(cards[0].find('.role-head .tag').text()).toBe('2 位成員')
    w.unmount()
  })

  it('成員：姓名、工號、指派日只取日期；停用者加「（已停用）」並套灰色', async () => {
    const w = await mountView()
    const ms = w.findAll('.role-card')[0].findAll('.member')
    expect(ms).toHaveLength(2)
    expect(ms[0].find('.m-name').text()).toBe('Hank (何嘉浤)')
    expect(ms[0].find('.m-id').text()).toBe('2410303')
    expect(ms[0].find('.m-date').text()).toBe('指派於 2026-10-07')
    expect(ms[0].find('.m-off').exists()).toBe(false)
    expect(ms[0].classes()).not.toContain('inactive')
    expect(ms[1].find('.m-off').text()).toBe('（已停用）')
    expect(ms[1].classes()).toContain('inactive')
    w.unmount()
  })

  it('沒有成員、沒有權限、沒有說明的角色：顯示提示且不出錯', async () => {
    const w = await mountView()
    const c = w.findAll('.role-card')[1]
    expect(c.find('.role-descr').exists()).toBe(false)
    expect(c.find('.perm-row .none').text()).toBe('（尚未授予任何權限）')
    expect(c.find('.no-member').text()).toBe('目前沒有成員')
    expect(c.find('.members').exists()).toBe(false)
    expect(c.find('.role-head .tag').text()).toBe('0 位成員')
    w.unmount()
  })

  it('後端缺陣列欄位或姓名為空：當空陣列、以工號代替姓名', async () => {
    rolesMock.mockResolvedValue([
      { ...role(), permNames: undefined, members: [member({ userName: null, grantDate: null })] } as unknown as AdminRole,
      { ...role({ roleId: 'X' }), members: undefined } as unknown as AdminRole
    ])
    const w = await mountView()
    const cards = w.findAll('.role-card')
    expect(cards).toHaveLength(2)
    expect(cards[0].find('.perm-row .none').exists()).toBe(true)
    expect(cards[0].find('.m-name').text()).toBe('2410303')
    expect(cards[0].find('.m-date').exists()).toBe(false)
    expect(cards[1].find('.no-member').exists()).toBe(true)
    w.unmount()
  })

  it('清單為空：顯示「目前沒有可維護的角色」', async () => {
    rolesMock.mockResolvedValue([])
    const w = await mountView()
    expect(w.find('.empty').text()).toBe('目前沒有可維護的角色')
    expect(w.find('.role-card').exists()).toBe(false)
    w.unmount()
  })

  it('查詢失敗：403 顯示後端訊息、其他錯誤顯示通用訊息；不顯示空清單', async () => {
    rolesMock.mockRejectedValue(httpError(403, { code: 'FORBIDDEN', message: '您沒有此功能的權限' }))
    const w = await mountView()
    expect(lastToast(w)).toContain('您沒有此功能的權限')
    expect(w.find('.empty').exists()).toBe(false)
    w.unmount()

    rolesMock.mockResolvedValue({ bad: true } as unknown as AdminRole[])
    const w2 = await mountView()
    expect(lastToast(w2)).toContain('查詢失敗，請稍後再試')
    expect(w2.find('.role-card').exists()).toBe(false)
    w2.unmount()
  })
})

describe('系統管理 API', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('角色清單與可指派人員：GET 路徑', async () => {
    const api = await vi.importActual<typeof import('../src/api/admin')>('../src/api/admin')
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: [] })
    await expect(api.listAdminRoles()).resolves.toEqual([])
    expect(get).toHaveBeenLastCalledWith('/admin/roles')
    await api.listAdminUsers()
    expect(get).toHaveBeenLastCalledWith('/admin/users')
  })

  it('指派與移除：角色代碼與工號經編碼，本文帶原因', async () => {
    const api = await vi.importActual<typeof import('../src/api/admin')>('../src/api/admin')
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { roleId: 'R/1', catgId: null, userId: 'U?1', result: 'GRANTED' } })
    await expect(api.grantRole('R/1', { userId: 'U?1', reason: '新進' })).resolves.toMatchObject({ result: 'GRANTED' })
    expect(post).toHaveBeenLastCalledWith('/admin/roles/R%2F1/members', { userId: 'U?1', reason: '新進' })
    await api.revokeRole('R/1', 'U?1', '離職')
    expect(post).toHaveBeenLastCalledWith('/admin/roles/R%2F1/members/U%3F1/revoke', { reason: '離職' })
  })
})
