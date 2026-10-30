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
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-30
  變更說明: 首頁配色（裁示 ①A ②A ③A）：三區各一主色（帳號藍、緊急存取橘紅、巡檢綠），區塊標題列塗滿主色白字、
            卡片淡色底；四張警示卡（到期、待補核准、異常待追蹤、巡檢異常）數字大於 0 時整張卡換淡紅／淡橘底加外框；
            樣式只寫在本頁（共用的 .module-card／.stat-card 其他頁也在用，不動）
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-30
  變更說明: BACKLOG 第 154、155 項：橘色數字改深橘 #92400e（原 --orange 在淡底上對比不到 3:1）；
            緊急存取區淡底改中性暖灰、淡橘警示底加深一階，警示時底色看得出變化
-->
<template>
  <main>
    <div class="module-card sec sec-acct">
      <h2>🔑 帳號權限管理</h2>
      <div class="stat-row">
        <div class="stat-card"><div class="num">{{ n(stats?.acctTotal) }}</div><div class="lbl">{{ acctPrefix }}帳號總數</div></div>
        <div class="stat-card"><div class="num">{{ n(stats?.acctReviewing) }}</div><div class="lbl">{{ acctPrefix }}待審核</div></div>
        <div :class="['stat-card', 'click', 'card-expiring', alertClass(stats?.acctExpiring, 'red')]" title="點擊查看到期清單" @click="onExpiringClick">
          <div class="num red">{{ n(stats?.acctExpiring) }}</div><div class="lbl">{{ acctPrefix }}30 天內到期（含已逾期）⚠️</div>
        </div>
        <div class="stat-card"><div class="num">{{ n(stats?.acctPriv) }}</div><div class="lbl">{{ acctPrefix }}特權帳號</div></div>
      </div>
    </div>
    <div class="module-card sec sec-emg">
      <h2>🚨 緊急存取</h2>
      <div class="stat-row">
        <div :class="['stat-card', 'click', 'card-emg-undecided', alertClass(stats?.emgUndecided, 'warn')]" title="點擊查看待補核准清單" @click="onEmgClick(EMG_UNDECIDED)">
          <div class="num warn">{{ n(stats?.emgUndecided) }}</div><div class="lbl">{{ emgPrefix }}緊急存取待補核准</div>
        </div>
        <div :class="['stat-card', 'click', 'card-emg-returned', alertClass(stats?.emgReturned, 'red')]" title="點擊查看已退回清單" @click="onEmgClick(EMG_RETURNED)">
          <div class="num red">{{ n(stats?.emgReturned) }}</div><div class="lbl">{{ emgPrefix }}緊急存取異常待追蹤</div>
        </div>
      </div>
    </div>
    <div v-if="stats?.inspVisible" class="module-card sec sec-insp card-insp">
      <h2>🛠 機房巡檢</h2>
      <div class="stat-row">
        <div class="stat-card"><div class="num">{{ n(stats?.inspThisMonth) }}</div><div class="lbl">本月巡檢次數</div></div>
        <div :class="['stat-card', 'card-insp-excpt', alertClass(stats?.inspExcptThisMonth, 'warn')]">
          <div class="num warn">{{ n(stats?.inspExcptThisMonth) }}</div><div class="lbl">本月發現異常項目數（未處理）</div>
        </div>
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

/** 警示卡：數字大於 0 才整張卡標色（0、載入前、失敗都不標），一眼看出哪裡有事要處理 */
function alertClass(v: number | null | undefined, kind: 'red' | 'warn'): string {
  return typeof v === 'number' && v > 0 ? `alert-${kind}` : ''
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
/* 各區主色：--sec 標題列與數字、--sec-bg 卡片底、--sec-line 卡片框 */
.sec-acct { --sec: #2f5fad; --sec-bg: #eef3fb; --sec-line: #c9d8f0; }
/* 緊急存取主色與警示同色系，淡底用中性暖灰，警示的淡橘底才分得出來 */
.sec-emg { --sec: #c2410c; --sec-bg: #faf6f3; --sec-line: #ead9cc; }
.sec-insp { --sec: #2e8b57; --sec-bg: #edf8f1; --sec-line: #c3e6cf; }

.sec { padding-top: 0; overflow: hidden; box-shadow: 0 1px 4px rgba(27, 42, 74, 0.08); border-color: var(--sec-line); }
/* 標題列貼齊卡片上緣與左右（抵銷 .module-card 的左右內距 18px） */
.sec h2 { margin: 0 -18px 14px; padding: 10px 18px; background: var(--sec); color: #fff; text-align: center; }

.stat-card { --card-bg: var(--sec-bg); --card-line: var(--sec-line); background: var(--card-bg); border: 1px solid var(--card-line); }
.stat-card .num { color: var(--sec); }
.stat-card .lbl { color: var(--dark); }
.stat-card.click { transition: box-shadow 0.15s, transform 0.15s; }
/* 覆蓋共用的 hover 青綠底，維持本卡底色、改用陰影表示可點 */
.stat-card.click:hover { background: var(--card-bg); box-shadow: 0 3px 10px rgba(27, 42, 74, 0.15); transform: translateY(-1px); }

/* 警示卡（數字大於 0）：放在主色之後，蓋掉卡片底與框 */
/* 外框加粗 1px，內距少 1px 抵銷，卡片大小不變 */
.stat-card.alert-red { --card-bg: #fdecec; --card-line: var(--red); border-width: 2px; padding: 11px 13px; }
.stat-card.alert-warn { --card-bg: #ffe9c2; --card-line: var(--orange); border-width: 2px; padding: 11px 13px; }
.stat-card .num.red { color: var(--red); }
/* 全站 --orange 在淡底上對比不到大字 3:1，數字改用深橘（同 .hint.warn、.btn-warn） */
.stat-card .num.warn { color: #92400e; }
</style>
