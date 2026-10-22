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
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 4 審查 M1（計畫 F7）：移除唯一啟用中成員的黃色提示與不提示的三種情況
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 5 回合 1：角色權限設定頁（唯讀）——左清單（一般人員標唯讀）、預設選第一個可編輯角色、
//           切換角色、勾選框反映現況且全部停用、簽核拆組與組順序、一般人員說明與人數文字、隱藏碼註記、
//           缺欄保底、空清單、查詢失敗；分組函式（未知模組排最後）；API 路徑與編碼
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 5 回合 2：角色權限設定頁編輯與存檔——勾選→「●未存」與還原、一般人員點了不變；
//           切換角色與路由離開的放棄確認（繼續編輯／放棄）、關分頁 beforeunload 只在有差異時攔；
//           黃字警示三種與沒有差異時不顯示；存檔彈窗摘要、原因檢查、送出的差異內容、成功 toast＋重載＋未存消失；
//           E23 依碼分組常駐、E24 常駐、E27 toast＋關＋重載、403 留著；改到自己所屬角色重載 /me（失去 SYS_ROLE 導無權限頁、
//           /me 失敗 toast 留在本頁、不含此角色不重載）；切換身分中全部停用
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 5 審查 M1：登出、導向登入頁、切換身分後、失去 SYS_ROLE 時離開守衛直接放行；仍存得了才問
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-19
// 變更說明: BACKLOG 第 89 項階段 6 回合 1：執行人對照頁——頁首說明、每類別一卡（不開放新增的加註）、停用者加註、缺欄保底、
//           空清單與查詢失敗；指派（下拉排除已在名單者）／移除打執行人 API、成功 toast＋重載；E23 常駐、其他 409 關＋重載；
//           F7 提示與不提示；改到自己重載 /me（重載後已無 SYS_EXECUTOR〔他人同時調整角色〕導無權限頁、/me 失敗 toast 留下）；
//           切換中與重載中按鈕停用；API 路徑與編碼
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-20
// 變更說明: BACKLOG 第 124 項階段 3 回合 2（裁示 ⑬-2）：指派下拉只顯示名字、移除對象欄只有名字與「（已停用）」；假候選人不帶 deptName
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-22
// 變更說明: BACKLOG 第 124 項階段 4 回合 1（裁示 D7A）：一般人員說明期望文字補「首次登入者也會取得」
// ============================================================
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import type { Router } from 'vue-router'
import http from '../src/api/http'
import RoleAdminView from '../src/views/RoleAdminView.vue'
import RolePermAdminView from '../src/views/RolePermAdminView.vue'
import ExecutorAdminView from '../src/views/ExecutorAdminView.vue'
import {
  getRolePerms,
  grantExecutor,
  grantRole,
  listAdminRoles,
  listAdminUsers,
  listExecutors,
  revokeExecutor,
  revokeRole,
  saveRolePerms
} from '../src/api/admin'
import { getMe } from '../src/api/auth'
import { clearToken, setToken } from '../src/api/tokenStore'
import { resetSession, useSession } from '../src/composables/useSession'
import type {
  AdminExecCatg,
  AdminMember,
  AdminPermModule,
  AdminRole,
  AdminRolePerms,
  AdminUser,
  RolePermMatrix
} from '../src/types/admin'
import { groupPerms } from '../src/utils/permGroups'
import { charLen } from '../src/utils/text'
import { GENERAL_PERMS, httpError, lastToast, makeMe, makeRouter } from './helpers'

vi.mock('../src/api/admin', () => ({
  listAdminRoles: vi.fn(),
  listAdminUsers: vi.fn(),
  grantRole: vi.fn(),
  revokeRole: vi.fn(),
  getRolePerms: vi.fn(),
  saveRolePerms: vi.fn(),
  listExecutors: vi.fn(),
  grantExecutor: vi.fn(),
  revokeExecutor: vi.fn()
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
const matrixMock = vi.mocked(getRolePerms)
const saveMock = vi.mocked(saveRolePerms)
const usersMock = vi.mocked(listAdminUsers)
const grantMock = vi.mocked(grantRole)
const revokeMock = vi.mocked(revokeRole)
const meMock = vi.mocked(getMe)
const execsMock = vi.mocked(listExecutors)
const execGrantMock = vi.mocked(grantExecutor)
const execRevokeMock = vi.mocked(revokeExecutor)

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
    { userId: '2410303', userName: 'Hank (何嘉浤)' },
    { userId: '0000351', userName: 'Terry (邱健唐)' },
    { userId: '0000001', userName: 'Kevin (李凱鈞)' }
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

  it('指派：人員下拉排除已在名單者（只顯示名字），送出帶去頭尾空白的原因；成功 toast、關彈窗、重載', async () => {
    const { w } = await mountView()
    await openGrant(w)
    expect(w.find('.modal h3').text()).toBe('指派 — 系統管理員')
    const opts = w.findAll('#member-user option').map(o => o.text())
    expect(opts).toEqual(['請選擇人員', 'Terry (邱健唐)', 'Kevin (李凱鈞)'])
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

  it('移除：彈窗顯示對象（只有名字，停用者加註），送出打移除 API；成功 toast、重載', async () => {
    const { w } = await mountView()
    await w.findAll('.revoke-btn')[1].trigger('click')
    await flushPromises()
    expect(usersMock).not.toHaveBeenCalled()
    expect(w.find('.modal h3').text()).toBe('移除 — 系統管理員')
    expect(w.find('.modal .target').text()).toBe('Gary (許永成)（已停用）')
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

  it('F7：移除唯一的啟用中成員時黃色提示（含權限名），提示不阻擋送出', async () => {
    const { w } = await mountView()
    // 假資料：Hank 啟用中、Gary 已停用 → Hank 是唯一啟用中成員
    await w.findAll('.revoke-btn')[0].trigger('click')
    expect(w.find('.modal .last-warning').text()).toBe(
      '⚠️ 移除後「系統管理員」沒有成員；此角色的權限（角色指派、切換身分）若沒有其他角色持有，之後相關的申請將無人能處理。'
    )
    await w.find('#member-reason').setValue('調職')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(revokeMock).toHaveBeenCalledWith('SYS_ADMIN', '2410303', '調職')
    w.unmount()
  })

  it('F7：移除停用者、或還有其他啟用中成員時不提示；指派模式不提示；角色沒有權限時只寫沒有成員', async () => {
    rolesMock.mockResolvedValue([
      role(),
      role({ roleId: 'DC_ADMIN', roleName: '機房管理員', members: [member(), member({ userId: '0000351', userName: 'Terry (邱健唐)' })] }),
      role({ roleId: 'EMPTY', roleName: '測試角色', permNames: [], members: [member()] })
    ])
    const { w } = await mountView()
    const cards = w.findAll('.role-card')
    await cards[0].findAll('.revoke-btn')[1].trigger('click')
    expect(w.find('.modal .last-warning').exists()).toBe(false)
    await w.find('.modal .actions .btn:not(.confirm-member)').trigger('click')

    await cards[1].findAll('.revoke-btn')[0].trigger('click')
    expect(w.find('.modal .last-warning').exists()).toBe(false)
    await w.find('.modal .actions .btn:not(.confirm-member)').trigger('click')

    await cards[2].find('.revoke-btn').trigger('click')
    expect(w.find('.modal .last-warning').text()).toBe('⚠️ 移除後「測試角色」沒有成員。')
    await w.find('.modal .actions .btn:not(.confirm-member)').trigger('click')

    await cards[2].find('.grant-btn').trigger('click')
    await flushPromises()
    expect(w.find('.modal .last-warning').exists()).toBe(false)
    w.unmount()
  })

  it('字數函式：去頭尾空白後按碼點計', () => {
    expect(charLen('  ab  ')).toBe(2)
    expect(charLen('😀😀')).toBe(2)
    expect(charLen('')).toBe(0)
    expect(charLen(null)).toBe(0)
  })
})

/** 角色權限設定的假模組（取自實機回應的子集，含一個簽核碼、順序同後端） */
const MODULES: AdminPermModule[] = [
  {
    moduleCode: 'ACCT',
    perms: [
      { permId: 'ACC_APPLY', permName: '送出帳號申請', sortSeq: 1 },
      { permId: 'ACC_LIST_FULL', permName: '完整帳號列表', sortSeq: 3 },
      { permId: 'APPR_G2', permName: '關卡 2 核准／退件', sortSeq: 5 },
      { permId: 'VERIFY_G7', permName: '關卡 7 複驗通過／退回', sortSeq: 9 },
      { permId: 'ACC_EXPORT', permName: '帳號權限清單匯出', sortSeq: 18 }
    ]
  },
  {
    moduleCode: 'INSP',
    perms: [
      { permId: 'INSP_VIEW', permName: '巡檢紀錄查詢與明細', sortSeq: 10 },
      { permId: 'INSP_CREATE', permName: '填寫巡檢', sortSeq: 11 }
    ]
  },
  { moduleCode: 'SYS', perms: [{ permId: 'SYS_ROLE', permName: '角色指派', sortSeq: 13 }] },
  { moduleCode: 'EMG', perms: [{ permId: 'EMG_DECIDE', permName: '緊急存取補核准／退回／結案', sortSeq: 24 }] }
]

function rp(over: Partial<AdminRolePerms> = {}): AdminRolePerms {
  return {
    roleId: 'SYS_ADMIN', roleName: '系統管理員', roleDescr: '切換身分、手動觸發排程', readOnly: false,
    memberCount: 3, grantedPermIds: ['SYS_ROLE'], hiddenGrantedCount: 1, ...over
  }
}

function matrix(): RolePermMatrix {
  return {
    modules: MODULES,
    roles: [
      rp({ roleId: 'GENERAL', roleName: '一般人員', roleDescr: null, readOnly: true, memberCount: 13, grantedPermIds: ['ACC_APPLY'] }),
      rp(),
      rp({ roleId: 'GOV', roleName: '資訊治理', roleDescr: '關卡 4、關卡 7', memberCount: 2, grantedPermIds: ['VERIFY_G7', 'ACC_LIST_FULL'], hiddenGrantedCount: 0 })
    ]
  }
}

describe('角色權限設定頁（唯讀）', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    resetSession()
    useSession().me.value = makeMe({ permissions: ADMIN_PERMS })
    matrixMock.mockReset().mockResolvedValue(matrix())
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  async function mountView(): Promise<VueWrapper> {
    const router = await makeRouter('/admin/role-perms')
    const w = mount(RolePermAdminView, { global: { plugins: [router] } })
    await flushPromises()
    return w
  }

  function checked(w: VueWrapper): string[] {
    return w
      .findAll('.perm-check')
      .filter(c => (c.find('input').element as HTMLInputElement).checked)
      .map(c => c.attributes('data-perm') ?? '')
  }

  it('左清單依後端順序、一般人員標「（唯讀）」；預設選第一個可編輯的角色', async () => {
    const w = await mountView()
    expect(matrixMock).toHaveBeenCalledTimes(1)
    const items = w.findAll('.role-item')
    expect(items.map(i => i.text())).toEqual(['一般人員（唯讀）', '系統管理員', '資訊治理'])
    expect(items[1].classes()).toContain('active')
    expect(items[1].attributes('aria-current')).toBe('true')
    expect(w.find('.perm-panel').attributes('data-role')).toBe('SYS_ADMIN')
    expect(w.find('.panel-title').text()).toBe('系統管理員（SYS_ADMIN）')
    expect(w.find('.member-count').text()).toBe('成員 3 人')
    expect(w.find('.role-descr').text()).toBe('切換身分、手動觸發排程')
    w.unmount()
  })

  it('分組：帳號 → 簽核 → 巡檢 → 緊急存取 → 系統管理，簽核碼從帳號拆出、組內順序不變', async () => {
    const w = await mountView()
    const gs = w.findAll('.perm-group')
    expect(gs.map(g => g.find('.group-name').text())).toEqual(['帳號', '簽核', '巡檢', '緊急存取', '系統管理'])
    expect(gs[0].findAll('.perm-check').map(c => c.attributes('data-perm'))).toEqual(['ACC_APPLY', 'ACC_LIST_FULL', 'ACC_EXPORT'])
    expect(gs[1].findAll('.perm-check').map(c => c.attributes('data-perm'))).toEqual(['APPR_G2', 'VERIFY_G7'])
    expect(gs[1].find('.perm-check').text()).toBe('關卡 2 核准／退件')
    w.unmount()
  })

  it('勾選框反映選中角色的現況、可編輯角色的勾選框可用；切換角色後跟著換', async () => {
    const w = await mountView()
    expect(checked(w)).toEqual(['SYS_ROLE'])
    expect(w.findAll('.perm-check input').every(i => !(i.element as HTMLInputElement).disabled)).toBe(true)
    await w.findAll('.role-item')[2].trigger('click')
    expect(w.find('.panel-title').text()).toBe('資訊治理（GOV）')
    expect(checked(w)).toEqual(['ACC_LIST_FULL', 'VERIFY_G7'])
    expect(w.find('.member-count').text()).toBe('成員 2 人')
    expect(w.findAll('.role-item')[2].classes()).toContain('active')
    expect(w.findAll('.role-item')[1].classes()).not.toContain('active')
    w.unmount()
  })

  it('一般人員：註明調整請改 DB、人數寫全體啟用中使用者；其他角色不顯示這段', async () => {
    const w = await mountView()
    expect(w.find('.ro-note').exists()).toBe(false)
    await w.findAll('.role-item')[0].trigger('click')
    expect(w.find('.ro-note').text()).toBe('一般人員權限由系統自動給所有啟用中使用者，首次登入者也會取得；調整請改 DB（見 SETUP）。')
    expect(w.find('.member-count').text()).toBe('全體啟用中使用者 13 人')
    expect(w.find('.role-descr').exists()).toBe(false)
    expect(checked(w)).toEqual(['ACC_APPLY'])
    // 唯讀：勾選框全部停用、沒有還原／存檔鈕、點了也不變
    expect(w.findAll('.perm-check input').every(i => (i.element as HTMLInputElement).disabled)).toBe(true)
    expect(w.find('.save-btn').exists()).toBe(false)
    await w.find('[data-perm=ACC_LIST_FULL] input').trigger('change')
    expect(checked(w)).toEqual(['ACC_APPLY'])
    expect(w.find('.unsaved').exists()).toBe(false)
    w.unmount()
  })

  it('隱藏碼：hiddenGrantedCount > 0 也不顯示任何提示（階段 6 使用者裁示）', async () => {
    const w = await mountView()
    expect(w.find('.perm-panel').text()).not.toContain('尚未上線')
    expect(w.find('.hidden-note').exists()).toBe(false)
    w.unmount()
  })

  it('全部唯讀時選第一個；缺欄位當空值；空清單顯示提示', async () => {
    matrixMock.mockResolvedValueOnce({
      modules: MODULES,
      roles: [{ roleId: 'GENERAL', roleName: '一般人員', roleDescr: null, readOnly: true } as unknown as AdminRolePerms]
    })
    const w = await mountView()
    expect(w.find('.perm-panel').attributes('data-role')).toBe('GENERAL')
    expect(checked(w)).toEqual([])
    expect(w.find('.member-count').text()).toBe('全體啟用中使用者 0 人')
    w.unmount()

    matrixMock.mockResolvedValueOnce({ modules: [], roles: [] })
    const w2 = await mountView()
    expect(w2.find('.empty').text()).toBe('目前沒有可設定的角色')
    w2.unmount()
  })

  it('查詢失敗：toast 後端訊息或通用訊息，不顯示空清單', async () => {
    matrixMock.mockRejectedValueOnce(httpError(403, { message: '您沒有執行此操作的權限' }))
    const w = await mountView()
    expect(lastToast(w)).toContain('您沒有執行此操作的權限')
    expect(w.find('.empty').exists()).toBe(false)
    w.unmount()

    matrixMock.mockResolvedValueOnce({ roles: [] } as unknown as RolePermMatrix)
    const w2 = await mountView()
    expect(lastToast(w2)).toContain('查詢失敗，請稍後再試')
    expect(w2.find('.rp-layout').exists()).toBe(false)
    w2.unmount()
  })
})

describe('角色權限設定頁：編輯與存檔', () => {
  const E23_PERM = '拿掉下列權限後沒有人能處理在途案件，請先讓其他角色持有該權限：【關卡 7 複驗通過／退回（VERIFY_G7）】AR20261007-011、AR20261007-012（等共 25 件）；【角色指派（SYS_ROLE）】AR20261007-013'
  const E24_PERM = '拿掉後將沒有人持有角色指派權限（SYS_ROLE），不能存檔'

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    // 離開守衛只在仍有 token 時才問（M1），每案先給一個
    localStorage.clear()
    setToken('tok')
    resetSession()
    useSession().me.value = makeMe({ permissions: ADMIN_PERMS, roleIds: ['SYS_ADMIN'] })
    matrixMock.mockReset().mockResolvedValue(matrix())
    saveMock.mockReset().mockResolvedValue({ roleId: 'GOV', added: [], removed: [], result: 'SAVED' })
    meMock.mockReset()
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  async function mountView(): Promise<{ w: VueWrapper; router: Router }> {
    const router = await makeRouter('/admin/role-perms')
    const w = mount(RolePermAdminView, { global: { plugins: [router] } })
    await flushPromises()
    return { w, router }
  }

  async function tick(w: VueWrapper, permId: string): Promise<void> {
    await w.find(`[data-perm=${permId}] input`).trigger('change')
  }

  async function pickGov(w: VueWrapper): Promise<void> {
    await w.findAll('.role-item')[2].trigger('click')
    await flushPromises()
  }

  it('勾選後角色清單標「●未存」、還原與存檔可按；還原回到現況；勾回原狀也算沒有差異', async () => {
    const { w } = await mountView()
    expect(w.find('.unsaved').exists()).toBe(false)
    expect(w.find('.save-btn').attributes('disabled')).toBeDefined()
    expect(w.find('.reset-btn').attributes('disabled')).toBeDefined()
    await tick(w, 'ACC_APPLY')
    expect(w.findAll('.role-item')[1].find('.unsaved').text()).toBe('●未存')
    expect(w.find('.save-btn').attributes('disabled')).toBeUndefined()
    await tick(w, 'ACC_APPLY')
    expect(w.find('.unsaved').exists()).toBe(false)
    await tick(w, 'SYS_ROLE')
    expect(w.find('.unsaved').exists()).toBe(true)
    await w.find('.reset-btn').trigger('click')
    expect(w.find('.unsaved').exists()).toBe(false)
    expect((w.find('[data-perm=SYS_ROLE] input').element as HTMLInputElement).checked).toBe(true)
    w.unmount()
  })

  it('有未存變更時切換角色先確認：繼續編輯留在原角色並保留勾選；放棄才切換且草稿重置', async () => {
    const { w } = await mountView()
    await tick(w, 'ACC_APPLY')
    await w.findAll('.role-item')[2].trigger('click')
    await flushPromises()
    expect(w.find('.modal h3').text()).toBe('放棄未存的變更？')
    await w.find('.modal .actions .btn:not(.discard-ok)').trigger('click')
    await flushPromises()
    expect(w.find('.modal').exists()).toBe(false)
    expect(w.find('.perm-panel').attributes('data-role')).toBe('SYS_ADMIN')
    expect((w.find('[data-perm=ACC_APPLY] input').element as HTMLInputElement).checked).toBe(true)

    await w.findAll('.role-item')[2].trigger('click')
    await flushPromises()
    await w.find('.discard-ok').trigger('click')
    await flushPromises()
    expect(w.find('.perm-panel').attributes('data-role')).toBe('GOV')
    expect(w.find('.unsaved').exists()).toBe(false)
    // 沒有差異時切換不問
    await w.findAll('.role-item')[1].trigger('click')
    await flushPromises()
    expect(w.find('.modal').exists()).toBe(false)
    expect(w.find('.perm-panel').attributes('data-role')).toBe('SYS_ADMIN')
    w.unmount()
  })

  it('路由離開：有未存變更先確認，放棄才離開；沒有差異直接離開；卸載後守衛解除', async () => {
    const { w, router } = await mountView()
    await tick(w, 'ACC_APPLY')
    const nav = router.push('/')
    await flushPromises()
    expect(w.find('.modal h3').text()).toBe('放棄未存的變更？')
    await w.find('.modal .actions .btn:not(.discard-ok)').trigger('click')
    await nav
    expect(router.currentRoute.value.path).toBe('/admin/role-perms')
    expect(w.find('.unsaved').exists()).toBe(true)

    const nav2 = router.push('/')
    await flushPromises()
    await w.find('.discard-ok').trigger('click')
    await nav2
    expect(router.currentRoute.value.path).toBe('/')
    w.unmount()
    await router.push('/admin/role-perms')
    expect(router.currentRoute.value.path).toBe('/admin/role-perms')
  })

  it('M1：登出（token 已清）、導向登入頁、切換身分後、已失去 SYS_ROLE 時離開不問、直接放行', async () => {
    // 登出：版面先 clearToken＋resetSession 才導向登入頁
    const a = await mountView()
    setToken('tok')
    await tick(a.w, 'ACC_APPLY')
    clearToken()
    await a.router.push('/login')
    expect(a.w.find('.modal').exists()).toBe(false)
    expect(a.router.currentRoute.value.path).toBe('/login')
    a.w.unmount()

    // 切換身分後：impersonating 為 true → 不可編輯 → 放行
    resetSession()
    useSession().me.value = makeMe({ permissions: ADMIN_PERMS, roleIds: ['SYS_ADMIN'] })
    const b = await mountView()
    setToken('tok')
    await tick(b.w, 'ACC_APPLY')
    useSession().me.value = makeMe({ permissions: ADMIN_PERMS, impersonating: true, realUserId: '2410303' })
    await b.router.push('/')
    expect(b.w.find('.modal').exists()).toBe(false)
    expect(b.router.currentRoute.value.path).toBe('/')
    b.w.unmount()

    // 已失去 SYS_ROLE（例如 /me 重載後）→ 放行；仍有 token、仍可編輯且有差異 → 照常問
    resetSession()
    useSession().me.value = makeMe({ permissions: ADMIN_PERMS, roleIds: ['SYS_ADMIN'] })
    const c = await mountView()
    setToken('tok')
    await tick(c.w, 'ACC_APPLY')
    useSession().me.value = makeMe()
    await c.router.push('/')
    expect(c.w.find('.modal').exists()).toBe(false)
    expect(c.router.currentRoute.value.path).toBe('/')
    c.w.unmount()

    resetSession()
    useSession().me.value = makeMe({ permissions: ADMIN_PERMS, roleIds: ['SYS_ADMIN'] })
    const d = await mountView()
    setToken('tok')
    await tick(d.w, 'ACC_APPLY')
    const nav = d.router.push('/')
    await flushPromises()
    expect(d.w.find('.modal h3').text()).toBe('放棄未存的變更？')
    await d.w.find('.discard-ok').trigger('click')
    await nav
    expect(d.router.currentRoute.value.path).toBe('/')
    d.w.unmount()
  })

  it('關分頁（beforeunload）：有差異才攔', async () => {
    const { w } = await mountView()
    const clean = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(clean)
    expect(clean.defaultPrevented).toBe(false)
    await tick(w, 'ACC_APPLY')
    const dirtyEv = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(dirtyEv)
    expect(dirtyEv.defaultPrevented).toBe(true)
    w.unmount()
    const after = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(after)
    expect(after.defaultPrevented).toBe(false)
  })

  it('黃字警示：拿掉唯一授予的碼、加入系統管理碼、相依組合；沒有差異時不顯示', async () => {
    const { w } = await mountView()
    expect(w.find('.warnings').exists()).toBe(false)
    await pickGov(w)
    // GOV 拿掉 ACC_LIST_FULL（只有 GOV 授予）、加 SYS_ROLE（成員 2 人）、勾 INSP_CREATE 不勾 INSP_VIEW
    await tick(w, 'ACC_LIST_FULL')
    await tick(w, 'SYS_ROLE')
    await tick(w, 'INSP_CREATE')
    const lines = w.findAll('.warnings > div').map(d => d.text())
    expect(lines).toEqual([
      '⚠️ 拿掉後沒有任何角色持有「完整帳號列表」',
      '⚠️ 將有 2 人取得「角色指派」',
      '⚠️ 勾了「填寫巡檢」但沒有「巡檢紀錄查詢與明細」，成員進不了對應的頁面，這個權限會用不到'
    ])
    // 拿掉 VERIFY_G7 本來就只有 GOV 授予 → 也警示；加入非系統管理碼不警示
    await tick(w, 'VERIFY_G7')
    await tick(w, 'EMG_DECIDE')
    expect(w.findAll('.warnings > div').map(d => d.text())).toContain('⚠️ 拿掉後沒有任何角色持有「關卡 7 複驗通過／退回」')
    expect(w.findAll('.warnings > div')).toHaveLength(4)
    // 拿掉 ACC_APPLY（一般人員也有）不警示
    await w.findAll('.role-item')[1].trigger('click')
    await flushPromises()
    await w.find('.discard-ok').trigger('click')
    await flushPromises()
    await tick(w, 'SYS_ROLE')
    expect(w.findAll('.warnings > div').map(d => d.text())).toEqual(['⚠️ 拿掉後沒有任何角色持有「角色指派」'])
    w.unmount()
  })

  it('存檔彈窗：摘要列加入與拿掉的中文名、成員數、警示；原因檢查；送出只送差異與去空白的原因；成功 toast、關彈窗、重載、未存消失', async () => {
    const { w } = await mountView()
    await pickGov(w)
    await tick(w, 'ACC_LIST_FULL')
    await tick(w, 'SYS_ROLE')
    await w.find('.save-btn').trigger('click')
    expect(w.find('.modal h3').text()).toBe('存檔 — 資訊治理')
    expect(w.find('.sum-added').text()).toBe('加入：角色指派')
    expect(w.find('.sum-removed').text()).toBe('拿掉：完整帳號列表')
    expect(w.find('.sum-members').text()).toBe('此角色目前 2 位成員，存檔後他們的下一個動作就生效')
    expect(w.findAll('.modal .warnings > div')).toHaveLength(2)
    // 彈窗開著時勾選框停用
    expect(w.findAll('.perm-check input').every(i => (i.element as HTMLInputElement).disabled)).toBe(true)

    await w.find('.confirm-save').trigger('click')
    expect(lastToast(w)).toBe('請填寫原因')
    await w.find('#perm-reason').setValue('😀'.repeat(501))
    expect(w.find('.modal .count').text()).toBe('501 / 500')
    await w.find('.confirm-save').trigger('click')
    expect(lastToast(w)).toBe('原因不可超過 500 字')
    expect(saveMock).not.toHaveBeenCalled()

    await w.find('#perm-reason').setValue('  組織調整  ')
    await w.find('.confirm-save').trigger('click')
    await flushPromises()
    expect(saveMock).toHaveBeenCalledWith('GOV', { added: ['SYS_ROLE'], removed: ['ACC_LIST_FULL'], reason: '組織調整' })
    expect(lastToast(w)).toBe('已存檔「資訊治理」的權限設定')
    expect(w.find('.modal').exists()).toBe(false)
    expect(w.find('.unsaved').exists()).toBe(false)
    expect(matrixMock).toHaveBeenCalledTimes(2)
    expect(meMock).not.toHaveBeenCalled()
    expect(w.find('.perm-panel').attributes('data-role')).toBe('GOV')
    w.unmount()
  })

  it('E23：依碼分組常駐彈窗內（前綴一行、每碼一列）、彈窗不關、不重載；E24 同樣常駐', async () => {
    saveMock.mockRejectedValueOnce(httpError(409, { message: E23_PERM }))
    const { w } = await mountView()
    await pickGov(w)
    await tick(w, 'VERIFY_G7')
    await w.find('.save-btn').trigger('click')
    await w.find('#perm-reason').setValue('調整')
    await w.find('.confirm-save').trigger('click')
    await flushPromises()
    expect(w.find('.blocked-head').text()).toBe('拿掉下列權限後沒有人能處理在途案件，請先讓其他角色持有該權限：')
    expect(w.findAll('.blocked-groups li').map(li => li.text())).toEqual([
      '【關卡 7 複驗通過／退回（VERIFY_G7）】AR20261007-011、AR20261007-012（等共 25 件）',
      '【角色指派（SYS_ROLE）】AR20261007-013'
    ])
    expect(w.find('.modal').exists()).toBe(true)
    expect(matrixMock).toHaveBeenCalledTimes(1)
    expect(w.find('.unsaved').exists()).toBe(true)

    saveMock.mockRejectedValueOnce(httpError(409, { message: E24_PERM }))
    await w.find('.confirm-save').trigger('click')
    await flushPromises()
    expect(w.find('.blocked-head').text()).toBe(E24_PERM)
    expect(w.find('.blocked-groups').exists()).toBe(false)
    expect(w.find('.modal').exists()).toBe(true)
    w.unmount()
  })

  it('E27／E26：toast 後端訊息、關彈窗、重載（草稿回現況）；403：toast、彈窗留著、不重載', async () => {
    saveMock.mockRejectedValueOnce(httpError(409, { message: '權限設定已被他人調整，請重新整理' }))
    const { w } = await mountView()
    await pickGov(w)
    await tick(w, 'VERIFY_G7')
    await w.find('.save-btn').trigger('click')
    await w.find('#perm-reason').setValue('調整')
    await w.find('.confirm-save').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('權限設定已被他人調整，請重新整理')
    expect(w.find('.modal').exists()).toBe(false)
    expect(matrixMock).toHaveBeenCalledTimes(2)
    expect(w.find('.unsaved').exists()).toBe(false)

    saveMock.mockRejectedValueOnce(httpError(403, { message: '您沒有執行此操作的權限' }))
    await tick(w, 'VERIFY_G7')
    await w.find('.save-btn').trigger('click')
    await w.find('#perm-reason').setValue('調整')
    await w.find('.confirm-save').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('您沒有執行此操作的權限')
    expect(w.find('.modal').exists()).toBe(true)
    expect(w.find('.blocked').exists()).toBe(false)
    expect(matrixMock).toHaveBeenCalledTimes(2)
    w.unmount()
  })

  it('改到自己所屬的角色：重載 /me；失去 SYS_ROLE 導向無權限頁、不再重載清單', async () => {
    meMock.mockResolvedValue(makeMe())
    saveMock.mockResolvedValue({ roleId: 'SYS_ADMIN', added: [], removed: ['SYS_ROLE'], result: 'SAVED' })
    const { w, router } = await mountView()
    await tick(w, 'SYS_ROLE')
    await w.find('.save-btn').trigger('click')
    await w.find('#perm-reason').setValue('交接')
    await w.find('.confirm-save').trigger('click')
    await flushPromises()
    expect(meMock).toHaveBeenCalledTimes(1)
    expect(useSession().can('SYS_ROLE')).toBe(false)
    expect(router.currentRoute.value.path).toBe('/no-access')
    expect(matrixMock).toHaveBeenCalledTimes(1)
    w.unmount()
  })

  it('改到自己所屬的角色但仍有 SYS_ROLE：留在本頁並重載；/me 重載失敗 toast 並留在本頁', async () => {
    meMock.mockResolvedValue(makeMe({ permissions: [...ADMIN_PERMS, 'INSP_VIEW'], roleIds: ['SYS_ADMIN'] }))
    const { w, router } = await mountView()
    await tick(w, 'ACC_APPLY')
    await w.find('.save-btn').trigger('click')
    await w.find('#perm-reason').setValue('調整')
    await w.find('.confirm-save').trigger('click')
    await flushPromises()
    expect(meMock).toHaveBeenCalledTimes(1)
    expect(useSession().can('INSP_VIEW')).toBe(true)
    expect(router.currentRoute.value.path).toBe('/admin/role-perms')
    expect(matrixMock).toHaveBeenCalledTimes(2)

    // loadMe 只在仍持有 token 時把失敗記進 meFailed（沒有 token 代表 401 已處理）
    setToken('tok')
    meMock.mockRejectedValueOnce(httpError(500, {}))
    await tick(w, 'ACC_APPLY')
    await w.find('.save-btn').trigger('click')
    await w.find('#perm-reason').setValue('調整')
    await w.find('.confirm-save').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('查詢失敗，請稍後再試')
    expect(router.currentRoute.value.path).toBe('/admin/role-perms')
    expect(matrixMock).toHaveBeenCalledTimes(3)
    w.unmount()
  })

  it('切換身分中：提示先切回，勾選框與按鈕全部停用、點了不變', async () => {
    useSession().me.value = makeMe({ permissions: ADMIN_PERMS, impersonating: true, realUserId: '2410303' })
    const { w } = await mountView()
    expect(w.find('.imp-note').text()).toBe('切換身分中不能調整權限，請先切回。')
    expect(w.findAll('.perm-check input').every(i => (i.element as HTMLInputElement).disabled)).toBe(true)
    await tick(w, 'ACC_APPLY')
    expect(w.find('.unsaved').exists()).toBe(false)
    expect(w.find('.save-btn').attributes('disabled')).toBeDefined()
    expect(w.find('.reset-btn').attributes('disabled')).toBeDefined()
    w.unmount()
  })
})

/** 執行人對照的假類別：HOST 開放（Hank 啟用、Gary 停用）、SVC 不開放且沒有執行人 */
function catg(over: Partial<AdminExecCatg> = {}): AdminExecCatg {
  return {
    catgId: 'HOST', catgName: '主機帳號', enabled: true, sortSeq: 1,
    members: [member(), member({ userId: '0000285', userName: 'Gary (許永成)', userActive: false, grantDate: '2026-10-15T10:45:19' })],
    ...over
  }
}

describe('執行人對照頁', () => {
  const EXEC_PERMS = [...GENERAL_PERMS, 'SYS_EXECUTOR']
  const USERS: AdminUser[] = [
    { userId: '2410303', userName: 'Hank (何嘉浤)' },
    { userId: '0000351', userName: 'Terry (邱健唐)' },
    { userId: '0000001', userName: 'Kevin (李凱鈞)' }
  ]

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    localStorage.clear()
    resetSession()
    setToken('tok')
    useSession().me.value = makeMe({ permissions: EXEC_PERMS })
    execsMock.mockReset().mockResolvedValue([
      catg(),
      catg({ catgId: 'SVC', catgName: '服務帳號', enabled: false, sortSeq: 4, members: [] })
    ])
    usersMock.mockReset().mockResolvedValue(USERS)
    execGrantMock.mockReset().mockResolvedValue({ roleId: null, catgId: 'HOST', userId: '0000351', result: 'GRANTED' })
    execRevokeMock.mockReset().mockResolvedValue({ roleId: null, catgId: 'HOST', userId: '2410303', result: 'REVOKED' })
    meMock.mockReset()
  })

  afterEach(() => {
    vi.runAllTimers()
    vi.useRealTimers()
  })

  async function mountView(): Promise<{ w: VueWrapper; router: Router }> {
    const router = await makeRouter('/admin/executors')
    const w = mount(ExecutorAdminView, { global: { plugins: [router] } })
    await flushPromises()
    return { w, router }
  }

  it('頁首說明關卡 6；依後端順序每類別一卡；不開放新增的類別加註、沒有執行人顯示提示', async () => {
    const { w } = await mountView()
    expect(execsMock).toHaveBeenCalledTimes(1)
    expect(w.find('.page-note').text()).toContain('執行人負責關卡 6')
    const cards = w.findAll('.catg-card')
    expect(cards.map(c => c.attributes('data-catg'))).toEqual(['HOST', 'SVC'])
    expect(cards[0].find('.catg-name').text()).toBe('主機帳號（HOST）')
    expect(cards[0].find('.closed').exists()).toBe(false)
    expect(cards[0].find('.head-right .tag').text()).toBe('2 位執行人')
    expect(cards[1].find('.closed').text()).toBe('不開放新增申請')
    expect(cards[1].find('.closed-note').text()).toContain('仍可能有在途案件')
    expect(cards[1].find('.no-member').text()).toBe('目前沒有執行人')
    expect(cards[1].find('.members').exists()).toBe(false)
    w.unmount()
  })

  it('執行人：姓名、工號、指派日只取日期；停用者加「（已停用）」；缺欄保底（members 缺當空、enabled 缺當開放、姓名空用工號）', async () => {
    const { w } = await mountView()
    const ms = w.findAll('.catg-card')[0].findAll('.member')
    expect(ms[0].find('.m-name').text()).toBe('Hank (何嘉浤)')
    expect(ms[0].find('.m-date').text()).toBe('指派於 2026-10-07')
    expect(ms[1].find('.m-off').text()).toBe('（已停用）')
    expect(ms[1].classes()).toContain('inactive')
    w.unmount()

    execsMock.mockResolvedValue([
      { catgId: 'X', catgName: '某類', sortSeq: null } as unknown as AdminExecCatg,
      catg({ members: [member({ userName: null, grantDate: null })] })
    ])
    const { w: w2 } = await mountView()
    const cards = w2.findAll('.catg-card')
    expect(cards[0].find('.no-member').exists()).toBe(true)
    expect(cards[0].find('.closed').exists()).toBe(false)
    expect(cards[1].find('.m-name').text()).toBe('2410303')
    expect(cards[1].find('.m-date').exists()).toBe(false)
    w2.unmount()
  })

  it('清單為空與查詢失敗：空清單提示；403 toast 後端訊息、不顯示空清單', async () => {
    execsMock.mockResolvedValue([])
    const { w } = await mountView()
    expect(w.find('.empty').text()).toBe('目前沒有帳號類別')
    w.unmount()

    execsMock.mockRejectedValue(httpError(403, { message: '您沒有此功能的權限' }))
    const { w: w2 } = await mountView()
    expect(lastToast(w2)).toContain('您沒有此功能的權限')
    expect(w2.find('.empty').exists()).toBe(false)
    w2.unmount()
  })

  it('指派：下拉排除已在名單者，打執行人指派 API；成功 toast、關彈窗、重載，改別人不重載 /me', async () => {
    const { w } = await mountView()
    await w.find('.grant-btn').trigger('click')
    await flushPromises()
    expect(w.find('.modal h3').text()).toBe('指派 — 主機帳號')
    expect(w.findAll('#member-user option').map(o => o.text())).toEqual(['請選擇人員', 'Terry (邱健唐)', 'Kevin (李凱鈞)'])
    await w.find('#member-user').setValue('0000351')
    await w.find('#member-reason').setValue(' 新進 ')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(execGrantMock).toHaveBeenCalledWith('HOST', { userId: '0000351', reason: '新進' })
    expect(grantMock).not.toHaveBeenCalled()
    expect(lastToast(w)).toBe('已將 Terry (邱健唐) 指派為「主機帳號」的執行人')
    expect(w.find('.modal').exists()).toBe(false)
    expect(execsMock).toHaveBeenCalledTimes(2)
    expect(meMock).not.toHaveBeenCalled()
    w.unmount()
  })

  it('移除：打執行人移除 API；成功 toast、重載', async () => {
    const { w } = await mountView()
    await w.findAll('.revoke-btn')[1].trigger('click')
    expect(w.find('.modal h3').text()).toBe('移除 — 主機帳號')
    await w.find('#member-reason').setValue('離職')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(execRevokeMock).toHaveBeenCalledWith('HOST', '0000285', '離職')
    expect(lastToast(w)).toBe('已將 Gary (許永成) 移出「主機帳號」的執行人')
    expect(execsMock).toHaveBeenCalledTimes(2)
    w.unmount()
  })

  it('E23 常駐彈窗內、不重載；其他 409 toast、關彈窗、重載', async () => {
    const e23 = '移除後沒有人能處理下列在途案件，請先加入接替人員：AR20261007-011（關卡 6）'
    execRevokeMock.mockRejectedValueOnce(httpError(409, { message: e23 }))
    const { w } = await mountView()
    await w.findAll('.revoke-btn')[0].trigger('click')
    await w.find('#member-reason').setValue('調職')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(w.find('.modal .blocked').text()).toBe(e23)
    expect(execsMock).toHaveBeenCalledTimes(1)
    await w.find('.modal .actions .btn:not(.confirm-member)').trigger('click')

    execGrantMock.mockRejectedValueOnce(httpError(409, { message: '此人已在名單中，請重新整理' }))
    await w.find('.grant-btn').trigger('click')
    await flushPromises()
    await w.find('#member-user').setValue('0000351')
    await w.find('#member-reason').setValue('新進')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('此人已在名單中，請重新整理')
    expect(w.find('.modal').exists()).toBe(false)
    expect(execsMock).toHaveBeenCalledTimes(2)
    w.unmount()
  })

  it('F7：移除唯一的啟用中執行人時黃色提示、不阻擋；移除停用者、還有其他啟用中執行人、指派模式都不提示', async () => {
    execsMock.mockResolvedValue([
      catg(),
      catg({ catgId: 'DB', catgName: '資料庫帳號', members: [member(), member({ userId: '0000351', userName: 'Terry (邱健唐)' })] })
    ])
    const { w } = await mountView()
    const cards = w.findAll('.catg-card')
    await cards[0].findAll('.revoke-btn')[0].trigger('click')
    expect(w.find('.modal .last-warning').text()).toBe(
      '⚠️ 移除後「主機帳號」沒有執行人，之後這個類別的申請到關卡 6（執行）將無人能處理。'
    )
    await w.find('.modal .actions .btn:not(.confirm-member)').trigger('click')

    await cards[0].findAll('.revoke-btn')[1].trigger('click')
    expect(w.find('.modal .last-warning').exists()).toBe(false)
    await w.find('.modal .actions .btn:not(.confirm-member)').trigger('click')

    await cards[1].findAll('.revoke-btn')[0].trigger('click')
    expect(w.find('.modal .last-warning').exists()).toBe(false)
    await w.find('.modal .actions .btn:not(.confirm-member)').trigger('click')

    await cards[0].find('.grant-btn').trigger('click')
    await flushPromises()
    expect(w.find('.modal .last-warning').exists()).toBe(false)
    await w.find('.modal .actions .btn:not(.confirm-member)').trigger('click')

    await cards[0].findAll('.revoke-btn')[0].trigger('click')
    await w.find('#member-reason').setValue('調職')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(execRevokeMock).toHaveBeenCalledWith('HOST', '2410303', '調職')
    w.unmount()
  })

  it('移除自己後重載的 /me 已無 SYS_EXECUTOR（他人同時調整角色）：導向無權限頁、不再重載清單', async () => {
    execsMock.mockResolvedValue([catg({ members: [member({ userId: '0000001', userName: 'Kevin (李凱鈞)' })] })])
    meMock.mockResolvedValue(makeMe())
    const { w, router } = await mountView()
    await w.find('.revoke-btn').trigger('click')
    await w.find('#member-reason').setValue('交接')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(meMock).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).toBe('/no-access')
    expect(execsMock).toHaveBeenCalledTimes(1)
    w.unmount()
  })

  it('指派自己且仍有 SYS_EXECUTOR：留在本頁並重載；/me 重載失敗 toast 並留在本頁', async () => {
    meMock.mockResolvedValue(makeMe({ permissions: EXEC_PERMS, execCatgIds: ['HOST'] }))
    const { w, router } = await mountView()
    await w.find('.grant-btn').trigger('click')
    await flushPromises()
    await w.find('#member-user').setValue('0000001')
    await w.find('#member-reason').setValue('兼任')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(meMock).toHaveBeenCalledTimes(1)
    expect(useSession().me.value?.execCatgIds).toEqual(['HOST'])
    expect(router.currentRoute.value.path).toBe('/admin/executors')
    expect(execsMock).toHaveBeenCalledTimes(2)

    meMock.mockRejectedValueOnce(httpError(500, {}))
    await w.findAll('.grant-btn')[1].trigger('click')
    await flushPromises()
    await w.find('#member-user').setValue('0000001')
    await w.find('#member-reason').setValue('兼任')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(lastToast(w)).toBe('查詢失敗，請稍後再試')
    expect(router.currentRoute.value.path).toBe('/admin/executors')
    expect(execsMock).toHaveBeenCalledTimes(3)
    w.unmount()
  })

  it('切換身分中：提示先切回，指派與移除鈕停用', async () => {
    useSession().me.value = makeMe({ permissions: EXEC_PERMS, impersonating: true, realUserId: '2410303' })
    const { w } = await mountView()
    expect(w.find('.imp-note').text()).toBe('切換身分中不能調整執行人，請先切回。')
    expect(w.findAll('.grant-btn').every(b => b.attributes('disabled') !== undefined)).toBe(true)
    expect(w.findAll('.revoke-btn').every(b => b.attributes('disabled') !== undefined)).toBe(true)
    w.unmount()
  })

  it('重載中：指派與移除鈕停用，重載完成後恢復', async () => {
    const { w } = await mountView()
    let release: (v: AdminExecCatg[]) => void = () => {}
    execsMock.mockReturnValueOnce(new Promise(r => { release = r }))
    await w.findAll('.revoke-btn')[1].trigger('click')
    await w.find('#member-reason').setValue('離職')
    await w.find('.confirm-member').trigger('click')
    await flushPromises()
    expect(w.findAll('.grant-btn').every(b => b.attributes('disabled') !== undefined)).toBe(true)
    expect(w.findAll('.revoke-btn').every(b => b.attributes('disabled') !== undefined)).toBe(true)
    release([catg({ members: [member()] })])
    await flushPromises()
    expect(w.find('.grant-btn').attributes('disabled')).toBeUndefined()
    expect(w.findAll('.revoke-btn')).toHaveLength(1)
    w.unmount()
  })
})

describe('權限碼分組函式', () => {
  it('不認得的模組排在最後、組名用代碼；空模組不列；null 回空陣列', () => {
    const gs = groupPerms([
      { moduleCode: 'NEW', perms: [{ permId: 'NEW_X', permName: '新功能', sortSeq: 30 }] },
      { moduleCode: 'INSP', perms: [] },
      { moduleCode: 'SYS', perms: [{ permId: 'SYS_ROLE', permName: '角色指派', sortSeq: 13 }] }
    ])
    expect(gs.map(g => [g.code, g.name])).toEqual([['SYS', '系統管理'], ['NEW', 'NEW']])
    expect(groupPerms(null)).toEqual([])
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

  it('角色權限：GET 矩陣路徑；存檔路徑帶編碼角色代碼、本文只送差異與原因', async () => {
    const api = await vi.importActual<typeof import('../src/api/admin')>('../src/api/admin')
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { modules: [], roles: [] } })
    await expect(api.getRolePerms()).resolves.toEqual({ modules: [], roles: [] })
    expect(get).toHaveBeenLastCalledWith('/admin/role-perms')
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { roleId: 'R/1', added: ['A'], removed: [], result: 'SAVED' } })
    const payload = { added: ['A'], removed: [], reason: '調整' }
    await expect(api.saveRolePerms('R/1', payload)).resolves.toMatchObject({ result: 'SAVED' })
    expect(post).toHaveBeenLastCalledWith('/admin/roles/R%2F1/perms', payload)
  })

  it('執行人對照：GET 路徑；指派與移除的類別代碼與工號經編碼、本文帶原因', async () => {
    const api = await vi.importActual<typeof import('../src/api/admin')>('../src/api/admin')
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: [] })
    await expect(api.listExecutors()).resolves.toEqual([])
    expect(get).toHaveBeenLastCalledWith('/admin/executors')
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { roleId: null, catgId: 'C/1', userId: 'U?1', result: 'GRANTED' } })
    await expect(api.grantExecutor('C/1', { userId: 'U?1', reason: '新進' })).resolves.toMatchObject({ result: 'GRANTED' })
    expect(post).toHaveBeenLastCalledWith('/admin/executors/C%2F1/members', { userId: 'U?1', reason: '新進' })
    await api.revokeExecutor('C/1', 'U?1', '離職')
    expect(post).toHaveBeenLastCalledWith('/admin/executors/C%2F1/members/U%3F1/revoke', { reason: '離職' })
  })
})
