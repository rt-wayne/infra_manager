<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-19
  變更說明: 新增：角色權限設定頁（BACKLOG 第 89 項階段 5 回合 1，唯讀版；GET /admin/role-perms，需 SYS_ROLE）
            左邊角色清單（依後端順序、含一般人員並標「（唯讀）」），右邊選中角色的權限碼依組勾選框顯示現況；
            分組見 utils/permGroups.ts（簽核碼另拆一組）；一般人員註明調整請改 DB；
            有尚未上線的碼已授予時註明「另有 N 個…未顯示，存檔不會變動」；勾選、存檔、警示在回合 2 加上
-->
<template>
  <main>
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
            @click="selectedId = r.roleId"
          >
            {{ r.roleName }}<span v-if="r.readOnly" class="ro">（唯讀）</span>
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
                <input type="checkbox" :checked="granted.has(p.permId)" disabled />
                <span>{{ p.permName }}</span>
              </label>
            </div>
          </div>
          <p v-if="groups.length === 0" class="hint no-perm">目前沒有可在畫面設定的權限碼</p>

          <p v-if="selected.hiddenGrantedCount > 0" class="hint hidden-note">
            另有 {{ selected.hiddenGrantedCount }} 個尚未上線的權限碼未顯示，存檔不會變動
          </p>
        </section>
      </div>
      <div v-else class="empty">目前沒有可設定的角色</div>
    </template>
    <p v-else-if="loading" class="hint">載入中…</p>

    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ToastHost from '../components/ToastHost.vue'
import { getRolePerms } from '../api/admin'
import { errorMessage } from '../api/errors'
import { useToast } from '../composables/useToast'
import type { AdminRolePerms } from '../types/admin'
import { groupPerms } from '../utils/permGroups'
import type { PermGroup } from '../utils/permGroups'

const { toast } = useToast()

const roles = ref<AdminRolePerms[]>([])
const groups = ref<PermGroup[]>([])
const selectedId = ref('')
const loaded = ref(false)
const loading = ref(false)

const selected = computed(() => roles.value.find(r => r.roleId === selectedId.value) ?? null)
const granted = computed(() => new Set(selected.value?.grantedPermIds ?? []))
const memberText = computed(() => {
  const r = selected.value
  if (!r) return ''
  return r.readOnly ? `全體啟用中使用者 ${r.memberCount} 人` : `成員 ${r.memberCount} 人`
})

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

onMounted(load)
</script>

<style scoped>
.rp-layout { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 16px; align-items: start; }
.role-list { display: flex; flex-direction: column; background: #fff; border: 1px solid var(--line); border-radius: 8px; overflow: hidden; }
.role-item { text-align: left; background: #fff; border: none; border-bottom: 1px solid var(--line); padding: 12px 14px;
  font-size: 18px; cursor: pointer; color: inherit; }
.role-item:last-child { border-bottom: none; }
.role-item:hover { background: #f1f5f9; }
.role-item.active { background: #eef3f8; color: var(--navy); font-weight: 700; box-shadow: inset 4px 0 0 var(--blue); }
.role-item .ro { color: var(--gray); font-weight: 400; font-size: 16px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
.panel-title { font-size: 21px; margin: 0; color: var(--navy); }
.role-id { font-size: 16px; color: var(--gray); font-weight: 400; }
.role-descr { margin: 4px 0 8px; }
.ro-note { margin: 8px 0 12px; }
.perm-group { display: grid; grid-template-columns: 96px minmax(0, 1fr); gap: 8px; padding: 10px 0; border-top: 1px solid var(--line); }
.group-name { font-weight: 700; color: var(--navy); padding-top: 4px; }
.group-perms { display: flex; flex-wrap: wrap; gap: 6px 18px; }
.perm-check { display: inline-flex; align-items: center; gap: 6px; min-width: 0; }
.perm-check input { width: 20px; height: 20px; flex-shrink: 0; }
.hidden-note { margin-top: 12px; }
@media (max-width: 900px) {
  .rp-layout { grid-template-columns: minmax(0, 1fr); }
  .perm-group { grid-template-columns: minmax(0, 1fr); }
}
</style>
