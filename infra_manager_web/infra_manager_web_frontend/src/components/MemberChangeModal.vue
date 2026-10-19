<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-19
  變更說明: 新增：名單指派／移除共用彈窗（BACKLOG 第 89 項階段 4 回合 2；角色指派頁先用，階段 6 執行人對照頁重用）
            指派：人員下拉（GET /admin/users，排除已在名單者）＋原因；移除：對象唯讀＋原因
            原因必填、去頭尾空白後按碼點 500 字內（與後端一致）；實際送出由呼叫端傳入 submit，彈窗不綁特定 API
            錯誤：409 且為 E23（在途案件無人接手）→ 常駐在彈窗內、彈窗不關，讓使用者看完單號；
            其他 409（E21 已在名單、E22 已不在名單、E24 最後一位角色指派者、E25 切換中、E26 鎖逾時）→ toast＋emit stale（呼叫端關彈窗並重載）；
            其餘錯誤（400、403、404、連不上）→ toast，彈窗留著
            後端錯誤本文只有 message 沒有代碼，E23 只能以文案開頭辨識（後端 AcctMsg.E23_PREFIX），兩邊要一起改
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-19
  變更說明: BACKLOG 第 89 項階段 4 審查 M1（計畫 F7，使用者裁示 A）：加 warning 屬性，移除模式顯示黃色提示（不阻擋送出）；
            文案與顯示條件由呼叫端決定（角色頁與執行人頁的受詞不同）
  AI版本  : Claude Fable 5.1 (claude-fable-5-1)
  修改日期: 2026-10-19
  變更說明: BACKLOG 第 89 項階段 5 回合 2：原因欄改用共用的 ReasonField（id、字數樣式不變）
-->
<template>
  <BaseModal :title="title" :sub="subText" close-text="取消" @close="close">
    <div v-if="mode === 'grant'" class="field">
      <label for="member-user">人員<span class="req-mark" title="必填">*</span></label>
      <p v-if="usersLoading" class="hint users-loading">人員名單載入中…</p>
      <p v-else-if="usersFailed" class="hint warn users-failed">人員名單載入失敗，請關閉後再試一次</p>
      <p v-else-if="options.length === 0" class="hint users-empty">所有啟用中的人員都已在名單中</p>
      <select v-else id="member-user" v-model="userId" :disabled="busy">
        <option value="">請選擇人員</option>
        <option v-for="u in options" :key="u.userId" :value="u.userId">{{ userText(u) }}</option>
      </select>
    </div>
    <div v-else class="kv">
      <div class="k">移除對象</div>
      <div class="v target">{{ memberText }}</div>
    </div>
    <div v-if="mode === 'revoke' && warning" class="note-box warn last-warning">⚠️ {{ warning }}</div>

    <ReasonField id="member-reason" v-model="reason" :max="REASON_MAX" :disabled="busy" placeholder="請填寫原因，會記在系統管理軌跡" />

    <div v-if="blocked" class="priv-banner blocked" role="alert">{{ blocked }}</div>

    <template #actions>
      <button type="button" class="btn btn-primary confirm-member" :disabled="busy || !canSubmit" @click="onSubmit">
        {{ busy ? '送出中…' : mode === 'grant' ? '確認指派' : '確認移除' }}
      </button>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import BaseModal from './BaseModal.vue'
import ReasonField from './ReasonField.vue'
import { listAdminUsers } from '../api/admin'
import { errorMessage, errorStatus } from '../api/errors'
import { useToast } from '../composables/useToast'
import type { AdminMember, AdminUser } from '../types/admin'
import { charLen } from '../utils/text'

const REASON_MAX = 500
const MSG_USER = '請選擇人員'
const MSG_REASON = '請填寫原因'
const MSG_REASON_LEN = `原因不可超過 ${REASON_MAX} 字`
/** 後端 AcctMsg.E23_PREFIX（名單維護版）；改文案時兩邊一起改 */
const E23_PREFIX = '移除後沒有人能處理下列在途案件'

const props = withDefaults(
  defineProps<{
    mode: 'grant' | 'revoke'
    /** 名單名稱，例「系統管理員」 */
    groupName: string
    /** 移除模式的對象 */
    member?: AdminMember | null
    /** 指派模式要排除的工號（已在名單者） */
    excludeIds?: string[]
    /** 實際送出；成功 resolve、失敗 reject 後端錯誤 */
    submit: (userId: string, reason: string) => Promise<unknown>
    /** 移除模式的黃色提示（不阻擋），例：移除後名單沒有啟用中成員；空字串不顯示 */
    warning?: string
  }>(),
  { member: null, excludeIds: () => [], warning: '' }
)
/** done：成功（帶對象工號與顯示名）；stale：名單狀態與畫面不一致等 409，呼叫端應關彈窗並重載 */
const emit = defineEmits<{ close: []; done: [userId: string, userName: string]; stale: [] }>()
const { toast } = useToast()

const users = ref<AdminUser[]>([])
const usersLoading = ref(false)
const usersFailed = ref(false)
const userId = ref('')
const reason = ref('')
const busy = ref(false)
/** E23 內容：常駐顯示到下次送出 */
const blocked = ref('')

const title = computed(() => (props.mode === 'grant' ? `指派 — ${props.groupName}` : `移除 — ${props.groupName}`))
const subText = computed(() =>
  props.mode === 'grant'
    ? '指派後對方下一個動作就生效。'
    : '移除後對方下一個動作就生效；若有在途案件只有對方能處理，系統會拒絕並列出單號。'
)
const options = computed(() => {
  const ex = new Set(props.excludeIds)
  return users.value.filter(u => !ex.has(u.userId))
})
const memberText = computed(() => {
  const m = props.member
  if (!m) return ''
  return `${m.userName || m.userId}（${m.userId}）${m.userActive ? '' : '（已停用）'}`
})
const reasonCount = computed(() => charLen(reason.value))
/** 指派模式名單沒載好（載入中、失敗、全部已在名單）時不能送 */
const canSubmit = computed(() => props.mode === 'revoke' || (!usersLoading.value && !usersFailed.value && options.value.length > 0))

function userText(u: AdminUser): string {
  return u.deptName ? `${u.userName}（${u.userId}）· ${u.deptName}` : `${u.userName}（${u.userId}）`
}

async function loadUsers(): Promise<void> {
  usersLoading.value = true
  try {
    const data = await listAdminUsers()
    if (!Array.isArray(data)) throw new Error('unexpected response')
    users.value = data
  } catch (e) {
    usersFailed.value = true
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  } finally {
    usersLoading.value = false
  }
}

/** 送出中不關：元件卸載後 emit 會被丟掉，呼叫端就不會重載 */
function close(): void {
  if (busy.value) return
  emit('close')
}

async function onSubmit(): Promise<void> {
  if (busy.value) return
  const target = props.mode === 'grant' ? userId.value : (props.member?.userId ?? '')
  if (!target) {
    toast(MSG_USER, 'amber')
    return
  }
  const text = reason.value.trim()
  if (!text) {
    toast(MSG_REASON, 'amber')
    return
  }
  if (reasonCount.value > REASON_MAX) {
    toast(MSG_REASON_LEN, 'amber')
    return
  }
  busy.value = true
  blocked.value = ''
  try {
    await props.submit(target, text)
    busy.value = false
    const name = props.mode === 'grant' ? users.value.find(u => u.userId === target)?.userName : props.member?.userName
    emit('done', target, name || target)
  } catch (e) {
    busy.value = false
    const msg = errorMessage(e, '操作失敗，請稍後再試')
    if (errorStatus(e) === 409 && msg.startsWith(E23_PREFIX)) {
      blocked.value = msg
    } else if (errorStatus(e) === 409) {
      toast(msg, 'amber')
      emit('stale')
    } else {
      toast(msg, 'amber')
    }
  }
}

onMounted(() => {
  if (props.mode === 'grant') void loadUsers()
})
</script>

<style scoped>
.field select { width: 100%; }
.blocked { margin-top: 10px; white-space: pre-wrap; }
.last-warning { margin-bottom: 12px; }
</style>
