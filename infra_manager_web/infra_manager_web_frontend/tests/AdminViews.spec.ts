// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-19
// 變更說明: 新增：系統管理前端測試（BACKLOG 第 89 項階段 4 回合 1）
//           角色指派頁（唯讀）：頁首 GENERAL 說明、每角色一卡（名稱、說明、權限中文名、成員數）、
//           停用成員加「（已停用）」、指派日只取日期、沒有成員／沒有權限的提示、空清單、查詢失敗 toast（403 帶後端訊息）；
//           API：路徑與本文、角色代碼與工號經編碼
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 4 回合 2：指派／移除彈窗——人員下拉排除已在名單者、名單載入失敗或全已在名單時不能送；
//           送出檢查（未選人、原因空白、501 碼點擋、500 碼點可送、原因去頭尾空白）；成功 toast＋關彈窗＋重載；
//           E23 常駐彈窗內且不重載；其他 409 toast＋關彈窗＋重載；400 toast 彈窗留著；
//           改到自己重載 /me（失去 SYS_ROLE 導無權限頁、仍有則留下）；改別人不重載 /me；切換中按鈕停用；
//           字數函式按碼點並去頭尾空白
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import type { Router } from 'vue-router'
import http from '../src/api/http'
import RoleAdminView from '../src/views/RoleAdminView.vue'
import { grantRole, listAdminRoles, listAdminUsers, revokeRole } from '../src/api/admin'
import { getMe } from '../src/api/auth'
import { resetSession, useSession } from '../src/composables/useSession'
import type { AdminMember, AdminRole, AdminUser } from '../src/types/admin'
import { charLen } from '../src/utils/text'
import { GENERAL_PERMS, httpError, lastToast, makeMe, makeRouter } from './helpers'

vi.mock('../src/api/admin', () => ({
  listAdminRoles: vi.fn(),
  listAdminUsers: vi.fn(),
  grantRole: vi.fn(),
  revokeRole: vi.fn()
}))
vi.mock('../src/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  getMe: vi.fn(),
  listUsers: vi.fn(),
  impersonate: vi.fn(),
  endImpersonate: vi.fn()
}))
vi.mock('../src/api/common', () => ({
  getCodes: vi.fn(),
  getHomeStats: vi.fn(),
  listExpiring: vi.fn().mockResolvedValue([]),
  listTodos: vi.fn().mockResolvedValue([]),
  listEmgReturned: vi.fn().mockResolvedValue([])
}))

const rolesMock = vi.mocked(listAdminRoles)
const usersMock = vi.mocked(listAdminUsers)
const grantMock = vi.mocked(grantRole)
const revokeMock = vi.mocked(revokeRole)
const meMock = vi.mocked(getMe)

const ADMIN_PERMS = [...GENERAL_PERMS, 'SYS_ROLE']
const E23_MSG = '移除後沒有人能處理下列在途案件，請先加入接替人員：AR20261007-011（關卡 5）'

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

describe('角色指派頁：指派與移除', () => {
  const USERS: AdminUser[] = [
    { userId: '2410303', userName: 'Hank (何嘉浤)', deptName: '資訊處' },
    { userId: '0000351', userName: 'Terry (邱健唐)', deptName: '資訊處' },
    { userId: '0000001', userName: 'Kevin (李凱鈞)', deptName: null }
  ]

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe({ permissions: ADMIN_PERMS })
    rolesMock.mockReset().mockResolvedValue([role()])
    usersMock.mockReset().mockResolvedValue(USERS)
    grantMock.mockReset().mockResolvedValue({ roleId: 'SYS_ADMIN', catgId: null, userId: '0000351', result: 'GRANTED' })
    revokeMock.mockReset().mockResolvedValue({ roleId: 'SYS_ADMIN', catgId: null, userId: '2410303', result: 'REVOKED' })
    meMock.mockReset()
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  async function mountView(): Promise<{ w: VueWrapper; router: Router }> {
    const router = await makeRouter('/admin/roles')
    const w = mount(RoleAdminView, { global: { plugins: [router] } })
    await flushPromises()
    return { w, router }
  }

  async function openGrant(w: VueWrapper): Promise<void> {
    await w.find('.grant-btn').trigger('click')
    await flushPromises()
  }

  it('指派：人員下拉排除已在名單者（顯示姓名、工號、部門），送出帶去頭尾空白的原因；成功 toast、關彈窗、重載', async () => {
    const { w } = await mountView()
    await openGrant(w)
    expect(w.find('.modal h3').text()).toBe('指派 — 系統管理員')
    const opts = w.findAll('#member-user option').map(o => o.text())
    expect(opts).toEqual(['請選擇人員', 'Terry (邱健唐)（0000351）· 資訊處', 'Kevin (李凱鈞)（0000001）'])
    await w.find('#member-user').setValue('0000351')
    await w.find('#member-reason').setValue('  新進同仁  ')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(grantMock).toHaveBeenCalledWith('SYS_ADMIN', { userId: '0000351', reason: '新進同仁' })
    expect(lastToast(w)).toBe('已將 Terry (邱健唐) 指派為「系統管理員」')
    expect(w.find('.modal').exists()).toBe(false)
    expect(rolesMock).toHaveBeenCalledTimes(2)
    expect(meMock).not.toHaveBeenCalled()
    w.unmount()
  })

  it('移除：彈窗顯示對象（停用者加註），送出打移除 API；成功 toast、重載', async () => {
    const { w } = await mountView()
    await w.findAll('.revoke-btn')[1].trigger('click')
    await flushPromises()
    expect(usersMock).not.toHaveBeenCalled()
    expect(w.find('.modal h3').text()).toBe('移除 — 系統管理員')
    expect(w.find('.modal .target').text()).toBe('Gary (許永成)（0000285）（已停用）')
    await w.find('#member-reason').setValue('離職')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(revokeMock).toHaveBeenCalledWith('SYS_ADMIN', '0000285', '離職')
    expect(lastToast(w)).toBe('已將 Gary (許永成) 移出「系統管理員」')
    expect(w.find('.modal').exists()).toBe(false)
    expect(rolesMock).toHaveBeenCalledTimes(2)
    w.unmount()
  })

  it('送出檢查：未選人、原因空白、超過 500 碼點都擋下不打 API；500 個 emoji 可送', async () => {
    const { w } = await mountView()
    await openGrant(w)
    await w.find('#member-reason').setValue('原因')
    await w.find('.confirm-member').trigger('click')
    expect(lastToast(w)).toBe('請選擇人員')
    await w.find('#member-user').setValue('0000351')
    await w.find('#member-reason').setValue('   ')
    await w.find('.confirm-member').trigger('click')
    expect(lastToast(w)).toBe('請填寫原因')
    await w.find('#member-reason').setValue('😀'.repeat(501))
    expect(w.find('.count').text()).toBe('501 / 500')
    expect(w.find('.count').classes()).toContain('warn')
    await w.find('.confirm-member').trigger('click')
    expect(lastToast(w)).toBe('原因不可超過 500 字')
    expect(grantMock).not.toHaveBeenCalled()
    await w.find('#member-reason').setValue('😀'.repeat(500))
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(grantMock).toHaveBeenCalledTimes(1)
    w.unmount()
  })

  it('E23：在途案件訊息常駐彈窗內、彈窗不關、不重載；再送出前清掉', async () => {
    revokeMock.mockRejectedValueOnce(httpError(409, { message: E23_MSG }))
    const { w } = await mountView()
    await w.findAll('.revoke-btn')[0].trigger('click')
    await w.find('#member-reason').setValue('調職')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(w.find('.modal .blocked').text()).toBe(E23_MSG)
    expect(w.find('.modal').exists()).toBe(true)
    expect(rolesMock).toHaveBeenCalledTimes(1)
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(w.find('.modal').exists()).toBe(false)
    w.unmount()
  })

  it('其他 409（已在名單、鎖逾時、最後一位角色指派者）：toast 後端訊息、關彈窗、重載', async () => {
    grantMock.mockRejectedValueOnce(httpError(409, { message: '此人已在名單中，請重新整理' }))
    const { w } = await mountView()
    await openGrant(w)
    await w.find('#member-user').setValue('0000351')
    await w.find('#member-reason').setValue('新進')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('此人已在名單中，請重新整理')
    expect(w.find('.modal').exists()).toBe(false)
    expect(rolesMock).toHaveBeenCalledTimes(2)

    revokeMock.mockRejectedValueOnce(httpError(409, { message: '不能移除最後一位具角色指派權限的人員' }))
    await w.findAll('.revoke-btn')[0].trigger('click')
    await w.find('#member-reason').setValue('調職')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('不能移除最後一位具角色指派權限的人員')
    expect(w.find('.modal').exists()).toBe(false)
    expect(rolesMock).toHaveBeenCalledTimes(3)
    w.unmount()
  })

  it('400／403：toast 後端訊息、彈窗留著、不重載', async () => {
    grantMock.mockRejectedValueOnce(httpError(400, { message: '欄位內容不正確：角色' }))
    const { w } = await mountView()
    await openGrant(w)
    await w.find('#member-user').setValue('0000351')
    await w.find('#member-reason').setValue('新進')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('欄位內容不正確：角色')
    expect(w.find('.modal').exists()).toBe(true)
    expect(w.find('.modal .blocked').exists()).toBe(false)
    expect(rolesMock).toHaveBeenCalledTimes(1)
    w.unmount()
  })

  it('人員名單載入失敗或全部已在名單：顯示提示、確認鈕停用', async () => {
    usersMock.mockRejectedValueOnce(httpError(500, {}))
    const { w } = await mountView()
    await openGrant(w)
    expect(w.find('.users-failed').exists()).toBe(true)
    expect(w.find('.confirm-member').attributes('disabled')).toBeDefined()
    await w.find('.modal .actions .btn:not(.confirm-member)').trigger('click')
    expect(w.find('.modal').exists()).toBe(false)

    usersMock.mockResolvedValueOnce([USERS[0]])
    await openGrant(w)
    expect(w.find('.users-empty').text()).toBe('所有啟用中的人員都已在名單中')
    expect(w.find('.confirm-member').attributes('disabled')).toBeDefined()
    w.unmount()
  })

  it('移除自己且失去 SYS_ROLE：重載 /me 後導向無權限頁', async () => {
    rolesMock.mockResolvedValue([role({ members: [member({ userId: '0000001', userName: 'Kevin (李凱鈞)' })] })])
    meMock.mockResolvedValue(makeMe())
    const { w, router } = await mountView()
    await w.find('.revoke-btn').trigger('click')
    await w.find('#member-reason').setValue('交接')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(meMock).toHaveBeenCalledTimes(1)
    expect(useSession().can('SYS_ROLE')).toBe(false)
    expect(router.currentRoute.value.path).toBe('/no-access')
    w.unmount()
  })

  it('指派自己到另一角色且仍有 SYS_ROLE：重載 /me 後留在本頁並重載清單', async () => {
    meMock.mockResolvedValue(makeMe({ permissions: [...ADMIN_PERMS, 'INSP_VIEW'] }))
    const { w, router } = await mountView()
    await openGrant(w)
    await w.find('#member-user').setValue('0000001')
    await w.find('#member-reason').setValue('兼任')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(meMock).toHaveBeenCalledTimes(1)
    expect(useSession().can('INSP_VIEW')).toBe(true)
    expect(router.currentRoute.value.path).toBe('/admin/roles')
    expect(rolesMock).toHaveBeenCalledTimes(2)
    w.unmount()
  })

  it('切換身分中：提示先切回，指派與移除鈕停用', async () => {
    useSession().me.value = makeMe({ permissions: ADMIN_PERMS, impersonating: true, realUserId: '2410303' })
    const { w } = await mountView()
    expect(w.find('.imp-note').text()).toBe('切換身分中不能調整角色，請先切回。')
    expect(w.find('.grant-btn').attributes('disabled')).toBeDefined()
    expect(w.findAll('.revoke-btn').every(b => b.attributes('disabled') !== undefined)).toBe(true)
    w.unmount()
  })

  it('字數函式：去頭尾空白後按碼點計', () => {
    expect(charLen('  ab  ')).toBe(2)
    expect(charLen('😀😀')).toBe(2)
    expect(charLen('')).toBe(0)
    expect(charLen(null)).toBe(0)
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
