<!--
  AI版本  : Claude Fable 5.1 (claude-fable-5-1)
  修改日期: 2026-11-04
  變更說明: 新增：定期審查簽核／駁回彈窗（BACKLOG 第 158 項 R5 第 2 單位；POST /reviews/{acctId}/sign、/reject）
            由清單列「審查」開啟，props 帶整列 ReviewItem（沒有單筆 API，資料以清單為準）；顯示帳號摘要與本輪兩半簽核狀況
            裁示②A：簽核鈕兩半各一（「簽核 Infra 主管半」需 REVIEW_SIGN_INFRA、「簽核資訊治理主管半」需 REVIEW_SIGN_GOV），
            只在我有該半權限、該半尚未簽、狀態為待複審／逾期未審時出現；按下先開確認框（同緊急存取補核准）
            駁回鈕在任一半可由我駁回時出現；駁回原因 ReasonField 500 字；兩半都可駁回時用單選鈕選半邊，只有一半時預選且不顯示
            versionNo 一律帶進行中那一輪的版本，沒有一輪帶 0（R3a 補充裁示①B）
            簽核成功：toast（雙簽完成另提示新日期已往後推）、emit changed 後關閉（彈窗沒有重載來源）
            裁示③A：駁回成功後彈窗留著，改顯示「已駁回」區塊與「發起停用申請」鈕（需 ACC_APPLY 且帳號狀態可停用）→ /requests/new?reqType=DISABLE
            409（版本不符、同半已簽、同人兩半、狀態已變）照後端訊息 toast、emit changed 並關閉讓清單重查；其他錯誤只 toast、彈窗留著
            送出中忽略主、子彈窗關閉（同緊急存取詳情彈窗：避免卸載後 emit 被丟掉）
-->
<template>
  <BaseModal :title="`定期審查 — ${item.acctId}`" :sub="subText" wide @close="closeMain">
    <div class="grid2">
      <div class="kv"><div class="k">帳號類別</div><div class="v">{{ item.catgName || item.catgId }}</div></div>
      <div class="kv">
        <div class="k">帳號名稱</div>
        <div class="v">{{ item.acctName || '（門禁卡，待發卡）' }}<span v-if="isOn(item.isPriv)" class="tag tag-priv priv">特權</span></div>
      </div>
      <div class="kv"><div class="k">所屬系統</div><div class="v">{{ item.sysName || '—' }}</div></div>
      <div class="kv"><div class="k">使用人</div><div class="v">{{ holderText }}</div></div>
      <div class="kv"><div class="k">上次審查</div><div class="v">{{ dateOnly(item.lastReviewDate) || '—' }}</div></div>
      <div class="kv"><div class="k">下次應審查</div><div class="v">{{ dateOnly(item.nextReviewDate) || '—' }}</div></div>
      <div class="kv"><div class="k">原申請人</div><div class="v">{{ item.applyByName || item.applyBy || '—' }}</div></div>
      <div class="kv"><div class="k">資產分類</div><div class="v">{{ item.assetClassName || item.assetClassId || '—' }}</div></div>
    </div>
    <div v-if="isOn(item.isReviewFlag)" class="kv flag-reason">
      <div class="k">審查異常原因</div>
      <div class="v pre">{{ item.reviewFlagReason || '（未填）' }}</div>
    </div>

    <div class="divider-lbl">本輪簽核</div>
    <div class="kv half-infra">
      <div class="k">{{ REVIEW_HALF_LABEL.INFRA }}</div>
      <div class="v">{{ halfText(item.infraSignByName, item.infraSignBy, item.infraSignDate) }}</div>
    </div>
    <div class="kv half-gov">
      <div class="k">{{ REVIEW_HALF_LABEL.GOV }}</div>
      <div class="v">{{ halfText(item.govSignByName, item.govSignBy, item.govSignDate) }}</div>
    </div>
    <p v-if="!item.reviewId && signable" class="hint">還沒有人簽核；第一次簽核時建立本輪紀錄。</p>

    <div v-if="rejected" class="note-box warn rejected-box">
      <b>已駁回（{{ REVIEW_HALF_LABEL[rejected.half] }}）</b>：{{ rejected.reason }}<br />
      此帳號已標記審查異常，並通知原申請人與資訊治理主管；請確認後提出修改或停用申請，複驗通過後異常會自動清除。
    </div>

    <template #actions>
      <template v-if="!rejected">
        <button v-if="canSign('INFRA')" type="button" class="btn btn-primary act-sign-infra" :disabled="busy" @click="openSign('INFRA')">簽核 Infra 主管半</button>
        <button v-if="canSign('GOV')" type="button" class="btn btn-primary act-sign-gov" :disabled="busy" @click="openSign('GOV')">簽核資訊治理主管半</button>
        <button v-if="rejectableHalves.length > 0" type="button" class="btn btn-warn act-reject" :disabled="busy" @click="openReject">駁回</button>
      </template>
      <button v-else-if="canDisable" type="button" class="btn btn-primary act-disable" @click="goDisable">發起停用申請</button>
    </template>
  </BaseModal>

  <BaseModal v-if="signing" :title="`簽核 — ${item.acctId}`" :sub="`${REVIEW_HALF_LABEL[signing]}`" close-text="取消" @close="closeSub">
    <p class="confirm-text">確認以「{{ REVIEW_HALF_LABEL[signing] }}」簽核此帳號的定期審查？{{ otherSigned(signing) ? '另一半已簽核，送出後本輪即完成，下次應審查日往後推 180 天。' : '另一半尚未簽核，兩半都簽核後本輪才完成。' }}</p>
    <template #actions>
      <button type="button" class="btn btn-primary confirm-sign" :disabled="busy" @click="doSign">{{ busy ? '送出中…' : '確認簽核' }}</button>
    </template>
  </BaseModal>

  <BaseModal v-if="rejecting" :title="`駁回 — ${item.acctId}`" :sub="MSG_REJECT_SUB" close-text="取消" @close="closeSub">
    <div v-if="rejectableHalves.length > 1" class="field">
      <label>駁回半邊<span class="req-mark" title="必填">*</span></label>
      <div class="radio-row">
        <label v-for="h in rejectableHalves" :key="h">
          <input v-model="rejectHalf" type="radio" name="review-reject-half" :value="h" :class="`half-${h.toLowerCase()}`" /> {{ REVIEW_HALF_LABEL[h] }}
        </label>
      </div>
    </div>
    <p v-else class="hint reject-half-hint">以「{{ REVIEW_HALF_LABEL[rejectHalf] }}」駁回</p>
    <ReasonField id="review-reject-reason" v-model="rejectReason" label="駁回原因" :max="REASON_MAX" placeholder="請說明駁回原因，例如權限過大、使用人已調職" :disabled="busy" />
    <template #actions>
      <button type="button" class="btn btn-warn confirm-reject" :disabled="busy" @click="doReject">{{ busy ? '送出中…' : '確認駁回' }}</button>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import BaseModal from './BaseModal.vue'
import ReasonField from './ReasonField.vue'
import { errorMessage, errorStatus } from '../api/errors'
import { rejectReview, signReview } from '../api/review'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { ReviewHalf, ReviewItem } from '../types/review'
import { dateOnly, toMinute } from '../utils/date'
import { REVIEW_HALF_LABEL, REVIEW_STATE_LABEL, accountOps, isOn, label } from '../utils/labels'
import type { PermCode } from '../utils/perms'
import { charLen } from '../utils/text'

const REASON_MAX = 500
const MSG_REJECT_SUB = '駁回後整輪結束、帳號標記審查異常，並通知原申請人與資訊治理主管；已簽的另一半不帶到下一輪。'
const MSG_REASON = '請填寫駁回原因'
const MSG_REASON_LEN = `駁回原因不可超過 ${REASON_MAX} 字`

const props = defineProps<{ item: ReviewItem }>()
/** changed：簽核或駁回成功（含 409 後），讓清單重查 */
const emit = defineEmits<{ close: []; changed: [] }>()
const { toast } = useToast()
const { can } = useSession()
const router = useRouter()

const busy = ref(false)
/** 正在確認簽核的半邊；空代表確認框沒開 */
const signing = ref<ReviewHalf | ''>('')
const rejecting = ref(false)
const rejectHalf = ref<ReviewHalf>('INFRA')
const rejectReason = ref('')
/** 駁回成功後的結果（裁示③A：彈窗留著顯示已駁回與停用捷徑） */
const rejected = ref<{ half: ReviewHalf; reason: string } | null>(null)

const subText = computed(() => `狀態：${label(REVIEW_STATE_LABEL, props.item.reviewStateCode)}`)
const holderText = computed(() => {
  const x = props.item
  if (!x.holderName) return '—'
  return x.holderDept ? `${x.holderName}（${x.holderDept}）` : x.holderName
})
/** 只有待複審、逾期未審可以簽核或駁回（後端交易內會以當天重算，這裡只決定按鈕要不要出現） */
const signable = computed(() => props.item.reviewStateCode === 'DUE' || props.item.reviewStateCode === 'OVERDUE')
/** versionNo 一律帶，沒有進行中一輪帶 0（R3a 補充裁示①B） */
const versionNo = computed(() => props.item.reviewVersionNo ?? 0)

function signedBy(half: ReviewHalf): string | null {
  return half === 'INFRA' ? props.item.infraSignBy : props.item.govSignBy
}
function permOf(half: ReviewHalf): PermCode {
  return half === 'INFRA' ? 'REVIEW_SIGN_INFRA' : 'REVIEW_SIGN_GOV'
}
/** 我有該半權限、該半尚未簽、狀態可簽才顯示該半的簽核鈕；同人兩半（⑪A）由後端擋 */
function canSign(half: ReviewHalf): boolean {
  return signable.value && can(permOf(half)) && !signedBy(half)
}
function otherSigned(half: ReviewHalf): boolean {
  return !!signedBy(half === 'INFRA' ? 'GOV' : 'INFRA')
}
/** 可由我駁回的半邊：條件同簽核（被駁回的那一半已有人簽就不能駁回，R3b 裁示①A） */
const rejectableHalves = computed<ReviewHalf[]>(() => (['INFRA', 'GOV'] as ReviewHalf[]).filter(canSign))
/** 駁回後的停用捷徑：需 ACC_APPLY，且帳號目前狀態可發起停用（有在途申請與否由後端檢查） */
const canDisable = computed(() => can('ACC_APPLY') && accountOps({ acctStateCode: props.item.acctStateCode }).includes('DISABLE'))

/** 一半的簽核狀況：「姓名 2026-10-30 10:30」，還沒簽顯示「尚未簽核」 */
function halfText(name: string | null, by: string | null, date: string | null): string {
  if (!by && !name) return '尚未簽核'
  return `${name || by} ${toMinute(date)}`.trim()
}

/** 送出中不關主彈窗（卸載後 emit 會被丟掉）；子彈窗開著時也不關（鍵盤 Tab 摸得到底下的關閉鈕） */
function closeMain(): void {
  if (busy.value || signing.value || rejecting.value) return
  emit('close')
}
function closeSub(): void {
  if (busy.value) return
  signing.value = ''
  rejecting.value = false
}

function openSign(half: ReviewHalf): void {
  signing.value = half
}
function openReject(): void {
  rejectReason.value = ''
  rejectHalf.value = rejectableHalves.value[0] ?? 'INFRA'
  rejecting.value = true
}

/**
 * 執行簽核或駁回：成功回 true 給呼叫端收尾；失敗 toast 後端訊息，
 * 409（版本不符、同半已簽、同人兩半、狀態已變）代表畫面上的資料已過時，關閉並讓清單重查
 */
async function run(action: () => Promise<unknown>): Promise<boolean> {
  if (busy.value) return false
  busy.value = true
  try {
    await action()
    return true
  } catch (e) {
    toast(errorMessage(e, '操作失敗，請稍後再試'), 'amber')
    if (errorStatus(e) === 409) {
      signing.value = ''
      rejecting.value = false
      emit('changed')
      emit('close')
    }
    return false
  } finally {
    busy.value = false
  }
}

async function doSign(): Promise<void> {
  const half = signing.value
  if (!half) return
  const done = otherSigned(half)
  const ok = await run(() => signReview(props.item.acctId, half, versionNo.value))
  if (!ok) return
  toast(done ? `${props.item.acctId} 雙簽完成，下次應審查日已往後推 180 天` : `${props.item.acctId} 已簽核${REVIEW_HALF_LABEL[half]}，待另一半簽核`, 'teal')
  signing.value = ''
  emit('changed')
  emit('close')
}

async function doReject(): Promise<void> {
  const half = rejectHalf.value
  const text = rejectReason.value.trim()
  if (!text) {
    toast(MSG_REASON, 'amber')
    return
  }
  if (charLen(rejectReason.value) > REASON_MAX) {
    toast(MSG_REASON_LEN, 'amber')
    return
  }
  const ok = await run(() => rejectReview(props.item.acctId, half, versionNo.value, text))
  if (!ok) return
  toast(`${props.item.acctId} 已駁回，帳號已標記審查異常`, 'teal')
  rejecting.value = false
  rejected.value = { half, reason: text }
  emit('changed')
}

/** 裁示③A：駁回後直接帶到停用申請表單（同帳號操作鈕的 query 格式） */
function goDisable(): void {
  const query: Record<string, string> = { reqType: 'DISABLE', acctId: props.item.acctId }
  if (props.item.acctName) query.acctName = props.item.acctName
  void router.push({ path: '/requests/new', query })
}
</script>

<style scoped>
.pre { white-space: pre-wrap; }
.priv { margin-left: 6px; }
.rejected-box { margin-top: 12px; }
</style>
