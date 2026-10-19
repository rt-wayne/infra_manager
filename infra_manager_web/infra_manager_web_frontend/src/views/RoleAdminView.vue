<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-19
  變更說明: 新增：角色指派頁（BACKLOG 第 89 項階段 4 回合 1，唯讀版；GET /admin/roles，需 SYS_ROLE）
            每個角色一張卡：名稱、說明、授予權限中文名、成員（停用者加灰色「（已停用）」、顯示指派日）；
            GENERAL 不列，頁首說明一般人員由系統自動授予；指派與移除鈕在回合 2 加上
-->
<template>
  <main>
    <p class="section-note page-note">
      一般人員權限由系統自動授予所有啟用中的使用者，不在此維護。
    </p>

    <template v-if="loaded">
      <div v-if="roles.length > 0">
        <section v-for="r in roles" :key="r.roleId" class="module-card role-card" :data-role="r.roleId">
          <div class="role-head">
            <h2 class="role-name">{{ r.roleName }}<span class="role-id">（{{ r.roleId }}）</span></h2>
            <span class="tag tag-info">{{ r.members.length }} 位成員</span>
          </div>
          <p v-if="r.roleDescr" class="hint role-descr">{{ r.roleDescr }}</p>
          <div class="perm-row">
            <span class="lbl">授予權限：</span>
            <template v-if="r.permNames.length > 0">
              <span v-for="p in r.permNames" :key="p" class="tag tag-cat perm">{{ p }}</span>
            </template>
            <span v-else class="hint none">（尚未授予任何權限）</span>
          </div>
          <ul v-if="r.members.length > 0" class="members">
            <li v-for="m in r.members" :key="m.userId" class="member" :class="{ inactive: !m.userActive }">
              <span class="m-name">{{ m.userName || m.userId }}</span>
              <span class="m-id">{{ m.userId }}</span>
              <span v-if="!m.userActive" class="m-off">（已停用）</span>
              <span v-if="m.grantDate" class="m-date">指派於 {{ dateOnly(m.grantDate) }}</span>
            </li>
          </ul>
          <p v-else class="hint no-member">目前沒有成員</p>
        </section>
      </div>
      <div v-else class="empty">目前沒有可維護的角色</div>
    </template>
    <p v-else-if="loading" class="hint">載入中…</p>

    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ToastHost from '../components/ToastHost.vue'
import { listAdminRoles } from '../api/admin'
import { errorMessage } from '../api/errors'
import { useToast } from '../composables/useToast'
import type { AdminRole } from '../types/admin'
import { dateOnly } from '../utils/date'

const { toast } = useToast()

const roles = ref<AdminRole[]>([])
const loaded = ref(false)
const loading = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    const data = await listAdminRoles()
    if (!Array.isArray(data)) throw new Error('unexpected response')
    // 後端已依 SORT_SEQ 排序；陣列欄位保底，避免單筆缺欄讓整頁掛掉
    roles.value = data.map(r => ({ ...r, permNames: r.permNames ?? [], members: r.members ?? [] }))
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
.role-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
.role-name { font-size: 21px; margin: 0; color: var(--navy); }
.role-id { font-size: 16px; color: var(--gray); font-weight: 400; }
.role-descr { margin: 4px 0 8px; }
.perm-row { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin: 8px 0; }
.perm-row .lbl { color: var(--gray); font-size: 17px; }
.members { list-style: none; padding: 0; margin: 10px 0 0; border-top: 1px solid var(--line); }
.member { display: flex; flex-wrap: wrap; gap: 4px 12px; align-items: baseline; padding: 8px 0; border-bottom: 1px solid var(--line); }
.m-name { font-weight: 700; }
.m-id, .m-date { color: var(--gray); font-size: 16px; }
.m-off { color: var(--gray); font-size: 16px; }
.member.inactive .m-name { color: var(--gray); }
.m-date { margin-left: auto; }
</style>
