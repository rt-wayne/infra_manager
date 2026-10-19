<!--
  AI版本  : Claude Fable 5.1 (claude-fable-5-1)
  修改日期: 2026-10-19
  變更說明: 新增：角色權限存檔彈窗（BACKLOG 第 89 項階段 5 回合 2；計畫「第三頁」互動與錯誤段）
            摘要「加入：…；拿掉：…；此角色目前 N 位成員，存檔後他們的下一個動作就生效」、黃字警示（呼叫端算好傳入）、
            原因必填（ReasonField，去頭尾空白後按碼點 500 字內，與後端一致）；實際送出由呼叫端傳入 submit
            錯誤分流（後端錯誤本文只有 message，以文案開頭辨識，BACKLOG 第 110 項）：
              409 且為 E23（拿掉下列權限後沒有人能處理在途案件…）→ 常駐彈窗內、依碼分組逐列顯示（後端以「；」分組）、彈窗不關；
              409 且為 E24（拿掉後將沒有人持有角色指派權限…）→ 常駐彈窗內、彈窗不關；
              其他 409（E27 已被他人調整、E26 鎖逾時）→ toast＋emit stale（呼叫端關彈窗並重載）；
              其餘（400、403、連不上）→ toast，彈窗留著
-->
<template>
  <BaseModal :title="`存檔 — ${roleName}`" sub="存檔後立即生效並寫入系統管理軌跡，原因必填。" close-text="取消" @close="close">
    <div class="kv">
      <div class="k">變更摘要</div>
      <div class="v summary">
        <div v-if="added.length > 0" class="sum-added">加入：{{ names(added) }}</div>
        <div v-if="removed.length > 0" class="sum-removed">拿掉：{{ names(removed) }}</div>
        <div class="sum-members">此角色目前 {{ memberCount }} 位成員，存檔後他們的下一個動作就生效</div>
      </div>
    </div>
    <div v-if="warnings.length > 0" class="note-box warn warnings">
      <div v-for="w in warnings" :key="w">⚠️ {{ w }}</div>
    </div>

    <ReasonField id="perm-reason" v-model="reason" :disabled="busy" placeholder="請填寫調整原因，會記在系統管理軌跡" />

    <div v-if="blocked" class="priv-banner blocked" role="alert">
      <div class="blocked-head">{{ blocked.head }}</div>
      <ul v-if="blocked.groups.length > 0" class="blocked-groups">
        <li v-for="g in blocked.groups" :key="g">{{ g }}</li>
      </ul>
    </div>

    <template #actions>
      <button type="button" class="btn btn-primary confirm-save" :disabled="busy" @click="onSubmit">
        {{ busy ? '送出中…' : '確認存檔' }}
      </button>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import BaseModal from './BaseModal.vue'
import ReasonField from './ReasonField.vue'
import { errorMessage, errorStatus } from '../api/errors'
import { useToast } from '../composables/useToast'
import type { AdminPerm } from '../types/admin'
import { charLen } from '../utils/text'

const REASON_MAX = 500
const MSG_REASON = '請填寫原因'
const MSG_REASON_LEN = `原因不可超過 ${REASON_MAX} 字`
/** 後端 AcctMsg.E23_PERM_PREFIX／E24_PERM 的開頭；改文案時兩邊一起改 */
const E23_PREFIX = '拿掉下列權限後沒有人能處理在途案件'
const E24_PREFIX = '拿掉後將沒有人持有角色指派權限'

const props = defineProps<{
  roleName: string
  memberCount: number
  added: AdminPerm[]
  removed: AdminPerm[]
  warnings: string[]
  /** 實際送出；成功 resolve、失敗 reject 後端錯誤 */
  submit: (reason: string) => Promise<unknown>
}>()
/** done：成功；stale：現況已變等 409，呼叫端應關彈窗並重載 */
const emit = defineEmits<{ close: []; done: []; stale: [] }>()
const { toast } = useToast()

const reason = ref('')
const busy = ref(false)
/** E23／E24 內容：常駐顯示到下次送出 */
const blocked = ref<{ head: string; groups: string[] } | null>(null)

function names(list: AdminPerm[]): string {
  return list.map(p => p.permName).join('、')
}

/** 送出中不關：元件卸載後 emit 會被丟掉，呼叫端就不會重載 */
function close(): void {
  if (busy.value) return
  emit('close')
}

/** E23 的本文「前綴：【碼】單號；【碼】單號」→ 前綴一行、每碼一列 */
function parseBlocked(msg: string): { head: string; groups: string[] } {
  if (!msg.startsWith(E23_PREFIX)) return { head: msg, groups: [] }
  const colon = msg.indexOf('：')
  if (colon < 0) return { head: msg, groups: [] }
  const groups = msg
    .slice(colon + 1)
    .split('；')
    .map(s => s.trim())
    .filter(s => s.length > 0)
  return { head: msg.slice(0, colon + 1), groups }
}

async function onSubmit(): Promise<void> {
  if (busy.value) return
  const text = reason.value.trim()
  if (!text) {
    toast(MSG_REASON, 'amber')
    return
  }
  if (charLen(text) > REASON_MAX) {
    toast(MSG_REASON_LEN, 'amber')
    return
  }
  busy.value = true
  blocked.value = null
  try {
    await props.submit(text)
    busy.value = false
    emit('done')
  } catch (e) {
    busy.value = false
    const msg = errorMessage(e, '操作失敗，請稍後再試')
    const conflict = errorStatus(e) === 409
    if (conflict && (msg.startsWith(E23_PREFIX) || msg.startsWith(E24_PREFIX))) {
      blocked.value = parseBlocked(msg)
    } else if (conflict) {
      toast(msg, 'amber')
      emit('stale')
    } else {
      toast(msg, 'amber')
    }
  }
}
</script>

<style scoped>
.summary > div + div { margin-top: 4px; }
.sum-members { color: var(--gray); font-size: 17px; }
.warnings { margin-bottom: 12px; }
.warnings > div + div { margin-top: 4px; }
.blocked { margin-top: 10px; }
.blocked-groups { margin: 6px 0 0; padding-left: 20px; }
.blocked-groups li { overflow-wrap: anywhere; }
</style>
