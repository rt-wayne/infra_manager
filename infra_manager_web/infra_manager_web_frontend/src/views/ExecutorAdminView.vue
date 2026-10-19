<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-19
  變更說明: 新增：執行人對照頁（BACKLOG 第 89 項階段 6 回合 1；GET /admin/executors，需 SYS_EXECUTOR）
            8 個帳號類別各一張卡（依後端順序）：名稱（代碼）、不開放新增申請的類別加註、執行人數、執行人（停用者加「（已停用）」、指派日）；
            「＋ 指派」與執行人「移除」重用 MemberChangeModal（指派／移除 API 由本頁傳入）；
            移除唯一的啟用中執行人時黃色提示（計畫 F7，不阻擋）；
            成功 toast＋重載；名單已變（409，E23 除外）關彈窗並重載；
            改到自己（對象＝目前身分）後重載 /me 與徽章（待辦數會變），重載失敗 toast U09；失去 SYS_EXECUTOR 時導向無權限頁；
            切換身分中與重載中按鈕停用（重載中停用是比照角色指派頁審查第 112 項，本頁一開始就做）
-->
<template>
  <main>
    <p class="section-note page-note">
      執行人負責關卡 6：依帳號類別實際建立或異動帳號；同一類別有多人時任一人可執行。
    </p>
    <p v-if="impersonating" class="note-box warn imp-note">切換身分中不能調整執行人，請先切回。</p>

    <template v-if="loaded">
      <div v-if="catgs.length > 0">
        <section v-for="c in catgs" :key="c.catgId" class="module-card catg-card" :data-catg="c.catgId">
          <div class="catg-head">
            <h2 class="catg-name">
              {{ c.catgName }}<span class="catg-id">（{{ c.catgId }}）</span>
              <span v-if="!c.enabled" class="tag tag-gray closed">不開放新增申請</span>
            </h2>
            <span class="head-right">
              <span class="tag tag-info">{{ c.members.length }} 位執行人</span>
              <button type="button" class="btn grant-btn" :disabled="busy" @click="openGrant(c)">＋ 指派</button>
            </span>
          </div>
          <p v-if="!c.enabled" class="hint closed-note">此類別目前不開放新申請，但仍可能有在途案件需要執行人處理。</p>
          <ul v-if="c.members.length > 0" class="members">
            <li v-for="m in c.members" :key="m.userId" class="member" :class="{ inactive: !m.userActive }">
              <span class="m-name">{{ m.userName || m.userId }}</span>
              <span class="m-id">{{ m.userId }}</span>
              <span v-if="!m.userActive" class="m-off">（已停用）</span>
              <span v-if="m.grantDate" class="m-date">指派於 {{ dateOnly(m.grantDate) }}</span>
              <button type="button" class="btn-link warn revoke-btn" :disabled="busy" @click="openRevoke(c, m)">移除</button>
            </li>
          </ul>
          <p v-else class="hint no-member">目前沒有執行人</p>
        </section>
      </div>
      <div v-else class="empty">目前沒有帳號類別</div>
    </template>
    <p v-else-if="loading" class="hint">載入中…</p>

    <MemberChangeModal
      v-if="editing"
      :mode="editing.mode"
      :group-name="editing.catg.catgName"
      :member="editing.member"
      :exclude-ids="editing.catg.members.map(m => m.userId)"
      :submit="submitChange"
      :warning="lastMemberWarning"
      @close="editing = null"
      @done="onDone"
      @stale="onStale"
    />
    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MemberChangeModal from '../components/MemberChangeModal.vue'
import ToastHost from '../components/ToastHost.vue'
import { grantExecutor, listExecutors, revokeExecutor } from '../api/admin'
import { errorMessage } from '../api/errors'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { AdminExecCatg, AdminMember } from '../types/admin'
import { dateOnly } from '../utils/date'

const { toast } = useToast()
const { me, can, impersonating, meFailed, loadMe, refreshBadges } = useSession()
const router = useRouter()

const catgs = ref<AdminExecCatg[]>([])
const loaded = ref(false)
const loading = ref(false)
/** 開著的指派／移除彈窗 */
const editing = ref<{ mode: 'grant' | 'revoke'; catg: AdminExecCatg; member: AdminMember | null } | null>(null)
/** 切換中或重載中不能開彈窗（重載中開的彈窗會帶著舊名單） */
const busy = computed(() => impersonating.value || loading.value)

/**
 * 計畫 F7（不阻擋）：要移除的人啟用中、且是該類別唯一的啟用中執行人 → 黃色提示。
 * 執行人只對應關卡 6，所以可以明確寫出後果
 */
const lastMemberWarning = computed(() => {
  const x = editing.value
  if (!x || x.mode !== 'revoke' || !x.member?.userActive) return ''
  const others = x.catg.members.filter(m => m.userActive && m.userId !== x.member?.userId)
  if (others.length > 0) return ''
  return `移除後「${x.catg.catgName}」沒有執行人，之後這個類別的申請到關卡 6（執行）將無人能處理。`
})

function openGrant(catg: AdminExecCatg): void {
  if (busy.value) return
  editing.value = { mode: 'grant', catg, member: null }
}

function openRevoke(catg: AdminExecCatg, member: AdminMember): void {
  if (busy.value) return
  editing.value = { mode: 'revoke', catg, member }
}

/** 彈窗的實際送出：依模式打指派或移除 */
function submitChange(userId: string, reason: string): Promise<unknown> {
  const x = editing.value
  if (!x) return Promise.reject(new Error('no target'))
  return x.mode === 'grant'
    ? grantExecutor(x.catg.catgId, { userId, reason })
    : revokeExecutor(x.catg.catgId, userId, reason)
}

async function onDone(userId: string, userName: string): Promise<void> {
  const x = editing.value
  editing.value = null
  if (x) {
    toast(
      x.mode === 'grant' ? `已將 ${userName} 指派為「${x.catg.catgName}」的執行人` : `已將 ${userName} 移出「${x.catg.catgName}」的執行人`,
      'teal'
    )
  }
  // 改到目前身分（切換中後端以 E25 拒絕，到這裡必是本人）：重載 /me 與徽章，待辦簽核數會隨執行人身分改變
  if (me.value && userId === me.value.userId) {
    await loadMe()
    void refreshBadges()
    if (meFailed.value) {
      toast('查詢失敗，請稍後再試', 'amber')
    } else if (me.value && !can('SYS_EXECUTOR')) {
      await router.replace('/no-access')
      return
    }
  }
  await load()
}

function onStale(): void {
  editing.value = null
  void load()
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const data = await listExecutors()
    if (!Array.isArray(data)) throw new Error('unexpected response')
    // 後端已依 SORT_SEQ 排序；陣列欄位保底，避免單筆缺欄讓整頁掛掉
    catgs.value = data.map(c => ({ ...c, enabled: c.enabled !== false, members: c.members ?? [] }))
    loaded.value = true
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-note { margin-bottom: 16px; }
.imp-note { margin-bottom: 16px; }
.catg-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
.catg-name { font-size: 21px; margin: 0; color: var(--navy); display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.catg-id { font-size: 16px; color: var(--gray); font-weight: 400; }
.closed-note { margin: 4px 0 0; }
.head-right { display: inline-flex; align-items: center; gap: 10px; }
.members { list-style: none; padding: 0; margin: 10px 0 0; border-top: 1px solid var(--line); }
.member { display: flex; flex-wrap: wrap; gap: 4px 12px; align-items: baseline; padding: 8px 0; border-bottom: 1px solid var(--line); }
.m-name { font-weight: 700; }
.m-id, .m-date, .m-off { color: var(--gray); font-size: 16px; }
.member.inactive .m-name { color: var(--gray); }
.m-date { margin-left: auto; }
</style>
