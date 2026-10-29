<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：首頁（開發文件 spec_im_acct_insp.md 第 6 節；GET /home/stats）6 張卡片
            「30 天內到期」可點：有帳號列表權限到 /accounts，沒有就打開頁首鈴鐺清單
            載入失敗只 toast，卡片顯示「—」
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.13、8.3 節）：卡片名稱依 4.13 口徑（待審核、30 天內到期含已逾期、
            本月發現異常項目數＝未處理）；「30 天內到期」點擊：有 ACC_LIST_FULL → 帳號列表（到期篩選），否則 → 無權限頁
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-13
  變更說明: BACKLOG 第 46 項 S8（SA §7.1）：新增「🚨 緊急存取」區塊兩張卡（待補核准＝尚未處理含逾期、異常待追蹤＝已退回未結案；
            數字看全部）；點擊：有 EMG_VIEW_ALL → 緊急存取清單（?state=UNDECIDED／RETURNED），否則 → 無權限頁
  AI版本  : Claude Fable 5.1 (claude-fable-5-1)
  修改日期: 2026-10-29
  變更說明: BACKLOG 第 148 項：卡片依個人——後端回 acctAll／emgAll／inspVisible 三個旗標；不是看全部時卡片標題加「我的」
            （只算我申請的）；緊急存取卡改為人人可點（清單本來就只列自己的單，筆數對得上）；
            機房巡檢區只在 inspVisible 時顯示（載入前與失敗時也不顯示）
-->
<template>
  <main>
    <div class="module-card">
      <h2>🔑 帳號權限管理</h2>
      <div class="stat-row">
        <div class="stat-card"><div class="num">{{ n(stats?.acctTotal) }}</div><div class="lbl">{{ acctPrefix }}帳號總數</div></div>
        <div class="stat-card"><div class="num">{{ n(stats?.acctReviewing) }}</div><div class="lbl">{{ acctPrefix }}待審核</div></div>
        <div class="stat-card click card-expiring" title="點擊查看到期清單" @click="onExpiringClick">
          <div class="num red">{{ n(stats?.acctExpiring) }}</div><div class="lbl">{{ acctPrefix }}30 天內到期（含已逾期）⚠️</div>
        </div>
        <div class="stat-card"><div class="num">{{ n(stats?.acctPriv) }}</div><div class="lbl">{{ acctPrefix }}特權帳號</div></div>
      </div>
    </div>
    <div class="module-card">
      <h2>🚨 緊急存取</h2>
      <div class="stat-row">
        <div class="stat-card click card-emg-undecided" title="點擊查看待補核准清單" @click="onEmgClick(EMG_UNDECIDED)">
          <div class="num warn">{{ n(stats?.emgUndecided) }}</div><div class="lbl">{{ emgPrefix }}緊急存取待補核准</div>
        </div>
        <div class="stat-card click card-emg-returned" title="點擊查看已退回清單" @click="onEmgClick(EMG_RETURNED)">
          <div class="num red">{{ n(stats?.emgReturned) }}</div><div class="lbl">{{ emgPrefix }}緊急存取異常待追蹤</div>
        </div>
      </div>
    </div>
    <div v-if="stats?.inspVisible" class="module-card card-insp">
      <h2>🛠 機房巡檢</h2>
      <div class="stat-row">
        <div class="stat-card"><div class="num">{{ n(stats?.inspThisMonth) }}</div><div class="lbl">本月巡檢次數</div></div>
        <div class="stat-card"><div class="num warn">{{ n(stats?.inspExcptThisMonth) }}</div><div class="lbl">本月發現異常項目數（未處理）</div></div>
      </div>
    </div>
    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ToastHost from '../components/ToastHost.vue'
import { getHomeStats } from '../api/common'
import { errorMessage } from '../api/errors'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { HomeStats } from '../types/common'
import { EMG_RETURNED, EMG_UNDECIDED } from '../utils/labels'

const router = useRouter()
const { toast } = useToast()
const { can } = useSession()
const stats = ref<HomeStats | null>(null)

/** 不是看全部時標題加「我的」，跟主管看到的全系統數字區分；載入前與失敗時沒有旗標，不加字 */
const acctPrefix = computed(() => (stats.value && !stats.value.acctAll ? '我的' : ''))
const emgPrefix = computed(() => (stats.value && !stats.value.emgAll ? '我的' : ''))

function n(v: number | null | undefined): string {
  return typeof v === 'number' ? String(v) : '—'
}

function onExpiringClick(): void {
  if (can('ACC_LIST_FULL')) {
    void router.push({ path: '/accounts', query: { expiring: '1' } })
  } else {
    void router.push('/no-access')
  }
}

/** 有 EMG_VIEW_ALL 看全部、沒有的人卡片與清單都只算自己的單，筆數一致，所以人人可進清單 */
function onEmgClick(state: string): void {
  void router.push({ path: '/emergency', query: { state } })
}

onMounted(async () => {
  try {
    stats.value = await getHomeStats()
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  }
})
</script>

<style scoped>
.num.red { color: var(--red); }
.num.warn { color: var(--orange); }
</style>
