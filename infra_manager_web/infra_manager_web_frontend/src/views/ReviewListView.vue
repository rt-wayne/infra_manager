<!--
  AI版本  : Claude Fable 5.1 (claude-fable-5-1)
  修改日期: 2026-10-30
  變更說明: 新增：定期審查清單（BACKLOG 第 158 項 R5 第 1 單位；GET /reviews，需 REVIEW_LIST，看全部）
            篩選：關鍵字（帳號單號、帳號名稱、使用人、所屬系統）、狀態（預設「需處理」＝異常＋逾期＋待複審，後端不帶 stateCode）
            每列：帳號單號、類別、帳號名稱、所屬系統、使用人、上次／下次審查日、審查狀態標籤、進行中那一輪兩半的簽核人與時間（沒有顯示「—」）
            網址 ?state= 是下拉有的值時當預設篩選（首頁第四區導入用，R8）；查詢失敗只 toast；分頁每頁 20 筆
            操作欄（審查彈窗、停用捷徑）留第 2 單位
-->
<template>
  <main>
    <div class="toolbar">
      <form class="filters" @submit.prevent="load">
        <input v-model="keyword" type="text" placeholder="搜尋帳號單號 / 帳號名稱 / 使用人 / 系統" aria-label="關鍵字" autocomplete="off" />
        <button type="submit" class="btn btn-primary" :disabled="loading">{{ loading ? '查詢中…' : '搜尋' }}</button>
        <select v-model="stateCode" aria-label="狀態" @change="load">
          <option v-for="o in REVIEW_STATE_FILTER" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
      </form>
    </div>
    <p class="hint review-hint">審查狀態依查詢當下計算：下次應審查日 30 天內為待複審，已過期或從未審查為逾期未審；駁回後為審查異常，複驗通過才清除。</p>

    <template v-if="loaded">
      <div v-if="rows.length > 0">
        <p class="res-head">共 {{ rows.length }} 筆，第 {{ page }} / {{ totalPages }} 頁</p>
        <table class="tbl">
          <colgroup>
            <col style="width: 13%" />
            <col style="width: 9%" />
            <col />
            <col style="width: 11%" />
            <col style="width: 9%" />
            <col style="width: 9%" />
            <col style="width: 9%" />
            <col style="width: 9%" />
            <col style="width: 11%" />
            <col style="width: 11%" />
          </colgroup>
          <thead>
            <tr>
              <th>帳號單號</th><th>類別</th><th>帳號名稱 / 系統</th><th>使用人</th><th>上次審查</th><th>下次應審查</th><th>狀態</th><th>Infra 半</th><th>治理半</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in pageRows" :key="r.acctId">
              <td>{{ r.acctId }}</td>
              <td>{{ r.catgName || r.catgId }}</td>
              <td>
                <div>{{ r.acctName || '（門禁卡，待發卡）' }}<span v-if="isOn(r.isPriv)" class="tag tag-priv priv">特權</span></div>
                <div v-if="r.sysName" class="sub">{{ r.sysName }}</div>
              </td>
              <td>{{ r.holderName || '—' }}</td>
              <td>{{ dateOnly(r.lastReviewDate) || '—' }}</td>
              <td>{{ dateOnly(r.nextReviewDate) || '—' }}</td>
              <td><span class="tag" :class="reviewStateTagClass(r.reviewStateCode)">{{ label(REVIEW_STATE_LABEL, r.reviewStateCode) }}</span></td>
              <td class="half">{{ halfText(r.infraSignByName, r.infraSignBy, r.infraSignDate) }}</td>
              <td class="half">{{ halfText(r.govSignByName, r.govSignBy, r.govSignDate) }}</td>
              <td class="ops"></td>
            </tr>
          </tbody>
        </table>
        <PagerBar v-model:page="page" :total-pages="totalPages" />
      </div>
      <div v-else class="empty">查無資料，請換個條件再試一次</div>
    </template>

    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PagerBar from '../components/PagerBar.vue'
import ToastHost from '../components/ToastHost.vue'
import { errorMessage } from '../api/errors'
import { listReviews } from '../api/review'
import { usePaging } from '../composables/usePaging'
import { useToast } from '../composables/useToast'
import type { ReviewItem, ReviewQuery } from '../types/review'
import { dateOnly, toMinute } from '../utils/date'
import { REVIEW_STATE_FILTER, REVIEW_STATE_LABEL, isOn, label, reviewStateTagClass } from '../utils/labels'

const { toast } = useToast()
const route = useRoute()

/** 網址 ?state= 是下拉有的值才當預設篩選，其餘一律視為需處理 */
function initialState(): string {
  const v = route.query.state
  return typeof v === 'string' && REVIEW_STATE_FILTER.some(o => o.value === v) ? v : ''
}

const keyword = ref('')
const stateCode = ref(initialState())
const rows = ref<ReviewItem[]>([])
const loaded = ref(false)
const loading = ref(false)
const { page, totalPages, pageRows } = usePaging(rows)

/** 一半的簽核狀況：「姓名 2026-10-30 10:30」，還沒簽顯示「—」 */
function halfText(name: string | null, by: string | null, date: string | null): string {
  if (!by && !name) return '—'
  return `${name || by} ${toMinute(date)}`.trim()
}

async function load(): Promise<void> {
  const q: ReviewQuery = { keyword: keyword.value.trim(), stateCode: stateCode.value }
  loading.value = true
  try {
    const data = await listReviews(q)
    if (!Array.isArray(data)) throw new Error('unexpected response')
    rows.value = data
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
.review-hint { margin: -4px 0 10px; }
.sub { color: var(--gray); font-size: 0.9em; }
.priv { margin-left: 6px; }
.half { white-space: normal; }
</style>
