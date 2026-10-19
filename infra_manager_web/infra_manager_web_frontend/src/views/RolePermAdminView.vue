<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-19
  變更說明: 新增：角色權限設定頁（BACKLOG 第 89 項階段 5 回合 1，唯讀版；GET /admin/role-perms，需 SYS_ROLE）
            左邊角色清單（依後端順序、含一般人員並標「（唯讀）」），右邊選中角色的權限碼依組勾選框顯示現況；
            分組見 utils/permGroups.ts（簽核碼另拆一組）；一般人員註明調整請改 DB；
            有尚未上線的碼已授予時註明「另有 N 個…未顯示，存檔不會變動」
  AI版本  : Claude Fable 5.1 (claude-fable-5-1)
  修改日期: 2026-10-19
  變更說明: BACKLOG 第 89 項階段 5 回合 2（計畫「第三頁」互動、黃字警示、錯誤段）：
            勾選改草稿（一般人員與切換身分中仍停用）；有差異時角色清單標「●未存」、「還原」「存檔…」可按；
            切換角色、路由離開（router.beforeEach，元件卸載時解除；不用 onBeforeRouteLeave 是因為它要在 RouterView 內才生效，
            測試直接掛載會失效）、關分頁（beforeunload）前有未存變更都先確認是否放棄；
            黃字警示三種（拿掉後沒有任何角色持有、加入系統管理碼將有 N 人取得、INSP_CREATE 無 INSP_VIEW／ACC_EXPORT 無 ACC_LIST_FULL），
            只在有差異時顯示、不阻擋；「存檔…」開 RolePermSaveModal，成功 toast＋先套用草稿再重載、
            /me 的 roleIds 含此角色時重載 /me（失敗 toast U09），失去 SYS_ROLE 導向無權限頁；現況已變（409）關彈窗並重載
-->
<template>
  <main>
    <p v-if="impersonating" class="note-box warn imp-note">切換身分中不能調整權限，請先切回。</p>

    <template v-if="loaded">
      <div v-if="roles.length > 0" class="rp-layout">
        <nav class="role-list" aria-label="角色">
          <button
            v-for="r in roles"
            :key="r.roleId"
            type="button"
            class="role-item"
            :class="{ active: r.roleId === selectedId }"
            :data-role="r.roleId"
            :aria-current="r.roleId === selectedId ? 'true' : undefined"
            @click="select(r.roleId)"
          >
            <span>{{ r.roleName }}<span v-if="r.readOnly" class="ro">（唯讀）</span></span>
            <span v-if="r.roleId === selectedId && dirty" class="unsaved">●未存</span>
          </button>
        </nav>

        <section v-if="selected" class="module-card perm-panel" :data-role="selected.roleId">
          <div class="panel-head">
            <h2 class="panel-title">{{ selected.roleName }}<span class="role-id">（{{ selected.roleId }}）</span></h2>
            <span class="tag tag-info member-count">{{ memberText }}</span>
          </div>
          <p v-if="selected.roleDescr" class="hint role-descr">{{ selected.roleDescr }}</p>
          <p v-if="selected.readOnly" class="note-box ro-note">
            一般人員權限由系統自動給所有啟用中使用者，調整請改 DB（見 SETUP）。
          </p>

          <div v-for="g in groups" :key="g.code" class="perm-group" :data-group="g.code">
            <div class="group-name">{{ g.name }}</div>
            <div class="group-perms">
              <label v-for="p in g.perms" :key="p.permId" class="perm-check" :data-perm="p.permId">
                <input type="checkbox" :checked="draft.has(p.permId)" :disabled="!canEdit || saveOpen" @change="toggle(p.permId)" />
                <span>{{ p.permName }}</span>
              </label>
            </div>
          </div>
          <p v-if="groups.length === 0" class="hint no-perm">目前沒有可在畫面設定的權限碼</p>

          <p v-if="selected.hiddenGrantedCount > 0" class="hint hidden-note">
            另有 {{ selected.hiddenGrantedCount }} 個尚未上線的權限碼未顯示，存檔不會變動
          </p>

          <div v-if="warnings.length > 0" class="note-box warn warnings">
            <div v-for="w in warnings" :key="w">⚠️ {{ w }}</div>
          </div>

          <div v-if="!selected.readOnly" class="form-actions">
            <button type="button" class="btn reset-btn" :disabled="!dirty || !canEdit" @click="resetDraft">還原</button>
            <button type="button" class="btn btn-primary save-btn" :disabled="!dirty || !canEdit" @click="saveOpen = true">存檔…</button>
          </div>
        </section>
      </div>
      <div v-else class="empty">目前沒有可設定的角色</div>
    </template>
    <p v-else-if="loading" class="hint">載入中…</p>

    <RolePermSaveModal
      v-if="saveOpen && selected"
      :role-name="selected.roleName"
      :member-count="selected.memberCount"
      :added="addedPerms"
      :removed="removedPerms"
      :warnings="warnings"
      :submit="submitSave"
      @close="saveOpen = false"
      @done="onSaved"
      @stale="onStale"
    />

    <BaseModal v-if="discardAsk" title="放棄未存的變更？" sub="這個角色的勾選還沒存檔，離開後會回到目前的設定。" close-text="繼續編輯" @close="answerDiscard(false)">
      <template #actions>
        <button type="button" class="btn btn-warn discard-ok" @click="answerDiscard(true)">放棄變更</button>
      </template>
    </BaseModal>

    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import BaseModal from '../components/BaseModal.vue'
import RolePermSaveModal from '../components/RolePermSaveModal.vue'
import ToastHost from '../components/ToastHost.vue'
import { getRolePerms, saveRolePerms } from '../api/admin'
import { errorMessage } from '../api/errors'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { AdminRolePerms } from '../types/admin'
import { groupPerms } from '../utils/permGroups'
import type { PermGroup } from '../utils/permGroups'

/** 加入時提醒受影響人數的系統管理碼（計畫黃字警示第 2 種） */
const SENSITIVE_PERMS: readonly string[] = ['SYS_IMPERSONATE', 'SYS_ROLE', 'SYS_EXECUTOR']
/** 相依組合（裁示 ⑰A，只提醒不擋）：[需要的碼, 它依賴的碼] */
const DEPENDENT_PERMS: readonly [string, string][] = [
  ['INSP_CREATE', 'INSP_VIEW'],
  ['ACC_EXPORT', 'ACC_LIST_FULL']
]

const { toast } = useToast()
const { me, can, impersonating, meFailed, loadMe, refreshBadges } = useSession()
const router = useRouter()

const roles = ref<AdminRolePerms[]>([])
const groups = ref<PermGroup[]>([])
const selectedId = ref('')
const loaded = ref(false)
const loading = ref(false)
/** 選中角色的勾選草稿（存檔前的狀態） */
const draft = ref<Set<string>>(new Set())
const saveOpen = ref(false)
/** 「放棄未存變更？」確認框開著時，等答案的 resolve */
const discardAsk = ref<((ok: boolean) => void) | null>(null)

const selected = computed(() => roles.value.find(r => r.roleId === selectedId.value) ?? null)
const original = computed(() => new Set(selected.value?.grantedPermIds ?? []))
/** 畫面上所有可見碼（依組順序攤平），差異與警示都照這個順序列 */
const allPerms = computed(() => groups.value.flatMap(g => g.perms))
const permName = computed(() => new Map(allPerms.value.map(p => [p.permId, p.permName])))
const addedPerms = computed(() => allPerms.value.filter(p => draft.value.has(p.permId) && !original.value.has(p.permId)))
const removedPerms = computed(() => allPerms.value.filter(p => original.value.has(p.permId) && !draft.value.has(p.permId)))
const dirty = computed(() => addedPerms.value.length > 0 || removedPerms.value.length > 0)
const canEdit = computed(() => !!selected.value && !selected.value.readOnly && !impersonating.value)
const memberText = computed(() => {
  const r = selected.value
  if (!r) return ''
  return r.readOnly ? `全體啟用中使用者 ${r.memberCount} 人` : `成員 ${r.memberCount} 人`
})

/** 黃字警示（不阻擋）；只在有差異時算 */
const warnings = computed(() => {
  const s = selected.value
  if (!s || !dirty.value) return []
  const name = (id: string): string => permName.value.get(id) ?? id
  const out: string[] = []
  for (const p of removedPerms.value) {
    const othersHold = roles.value.some(r => r.roleId !== s.roleId && r.grantedPermIds.includes(p.permId))
    if (!othersHold) out.push(`拿掉後沒有任何角色持有「${p.permName}」`)
  }
  for (const p of addedPerms.value) {
    if (SENSITIVE_PERMS.includes(p.permId)) out.push(`將有 ${s.memberCount} 人取得「${p.permName}」`)
  }
  for (const [need, base] of DEPENDENT_PERMS) {
    if (draft.value.has(need) && !draft.value.has(base)) {
      out.push(`勾了「${name(need)}」但沒有「${name(base)}」，成員進不了對應的頁面，這個權限會用不到`)
    }
  }
  return out
})

function resetDraft(): void {
  draft.value = new Set(original.value)
}

// 選中的角色物件換了（切換角色、重載）就回到現況
watch(selected, resetDraft)

function toggle(permId: string): void {
  if (!canEdit.value) return
  const next = new Set(draft.value)
  if (next.has(permId)) next.delete(permId)
  else next.add(permId)
  draft.value = next
}

/** 問使用者要不要放棄未存變更；確認框開著時再問一次就沿用同一個答案 */
function askDiscard(): Promise<boolean> {
  return new Promise(resolve => {
    const prev = discardAsk.value
    discardAsk.value = ok => {
      prev?.(ok)
      resolve(ok)
    }
  })
}

function answerDiscard(ok: boolean): void {
  const ask = discardAsk.value
  discardAsk.value = null
  ask?.(ok)
}

async function select(roleId: string): Promise<void> {
  if (roleId === selectedId.value) return
  if (dirty.value && !(await askDiscard())) return
  selectedId.value = roleId
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const data = await getRolePerms()
    if (!data || !Array.isArray(data.roles) || !Array.isArray(data.modules)) throw new Error('unexpected response')
    // 陣列與數字欄位保底，避免單筆缺欄讓整頁掛掉
    roles.value = data.roles.map(r => ({
      ...r,
      readOnly: r.readOnly === true,
      memberCount: r.memberCount ?? 0,
      grantedPermIds: r.grantedPermIds ?? [],
      hiddenGrantedCount: r.hiddenGrantedCount ?? 0
    }))
    groups.value = groupPerms(data.modules)
    // 重載後保留原本選的角色；原本沒選或該角色已不在清單 → 第一個可編輯的角色，全唯讀就第一個
    if (!roles.value.some(r => r.roleId === selectedId.value)) {
      selectedId.value = (roles.value.find(r => !r.readOnly) ?? roles.value[0])?.roleId ?? ''
    }
    loaded.value = true
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  } finally {
    loading.value = false
  }
}

/** 彈窗的實際送出：只送差異 */
function submitSave(reason: string): Promise<unknown> {
  const s = selected.value
  if (!s) return Promise.reject(new Error('no role'))
  return saveRolePerms(s.roleId, {
    added: addedPerms.value.map(p => p.permId),
    removed: removedPerms.value.map(p => p.permId),
    reason
  })
}

async function onSaved(): Promise<void> {
  const s = selected.value
  saveOpen.value = false
  if (!s) return
  toast(`已存檔「${s.roleName}」的權限設定`, 'teal')
  // 先把草稿套成現況（讓「未存」立刻消失、離開守衛不再攔），再向後端重載真正的現況
  const saved = [...draft.value]
  roles.value = roles.value.map(r => (r.roleId === s.roleId ? { ...r, grantedPermIds: saved } : r))
  // 改到自己所屬的角色：重載 /me 讓導覽與權限跟上；失去本頁權限就離開
  if (me.value?.roleIds?.includes(s.roleId)) {
    await loadMe()
    void refreshBadges()
    if (meFailed.value) {
      toast('查詢失敗，請稍後再試', 'amber')
    } else if (!can('SYS_ROLE')) {
      await router.replace('/no-access')
      return
    }
  }
  await load()
}

function onStale(): void {
  saveOpen.value = false
  void load()
}

function onBeforeUnload(ev: BeforeUnloadEvent): void {
  if (!dirty.value) return
  ev.preventDefault()
  ev.returnValue = ''
}

let stopLeaveGuard: (() => void) | null = null

onMounted(() => {
  void load()
  window.addEventListener('beforeunload', onBeforeUnload)
  // 本元件只在本路由存在，任何導航都是離開；有未存變更先問
  stopLeaveGuard = router.beforeEach(async () => (dirty.value ? await askDiscard() : true))
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
  stopLeaveGuard?.()
  stopLeaveGuard = null
})
</script>

<style scoped>
.imp-note { margin-bottom: 16px; }
.rp-layout { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 16px; align-items: start; }
.role-list { display: flex; flex-direction: column; background: #fff; border: 1px solid var(--line); border-radius: 8px; overflow: hidden; }
.role-item { display: flex; justify-content: space-between; align-items: center; gap: 8px; text-align: left; background: #fff;
  border: none; border-bottom: 1px solid var(--line); padding: 12px 14px; font-size: 18px; cursor: pointer; color: inherit; }
.role-item:last-child { border-bottom: none; }
.role-item:hover { background: #f1f5f9; }
.role-item.active { background: #eef3f8; color: var(--navy); font-weight: 700; box-shadow: inset 4px 0 0 var(--blue); }
.role-item .ro { color: var(--gray); font-weight: 400; font-size: 16px; }
.role-item .unsaved { color: #92400e; font-size: 15px; font-weight: 700; white-space: nowrap; }
.panel-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
.panel-title { font-size: 21px; margin: 0; color: var(--navy); }
.role-id { font-size: 16px; color: var(--gray); font-weight: 400; }
.role-descr { margin: 4px 0 8px; }
.ro-note { margin: 8px 0 12px; }
.perm-group { display: grid; grid-template-columns: 96px minmax(0, 1fr); gap: 8px; padding: 10px 0; border-top: 1px solid var(--line); }
.group-name { font-weight: 700; color: var(--navy); padding-top: 4px; }
.group-perms { display: flex; flex-wrap: wrap; gap: 6px 18px; }
.perm-check { display: inline-flex; align-items: center; gap: 6px; min-width: 0; cursor: pointer; }
.perm-check input { width: 20px; height: 20px; flex-shrink: 0; }
.perm-check input:disabled { cursor: not-allowed; }
.hidden-note { margin-top: 12px; }
.warnings { margin-top: 12px; }
.warnings > div + div { margin-top: 4px; }
@media (max-width: 900px) {
  .rp-layout { grid-template-columns: minmax(0, 1fr); }
  .perm-group { grid-template-columns: minmax(0, 1fr); }
}
</style>
