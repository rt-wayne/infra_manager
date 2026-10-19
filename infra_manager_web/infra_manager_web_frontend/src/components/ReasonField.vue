<!--
  AI版本  : Claude Fable 5.1 (claude-fable-5-1)
  修改日期: 2026-10-19
  變更說明: 新增：系統管理共用的原因欄（BACKLOG 第 89 項階段 5 回合 2，計畫「第三頁」互動段「抽 ReasonField」）
            必填星號、文字框、即時字數（去頭尾空白後按碼點，超過上限變色）；送出檢查仍由呼叫端做
            先給名單指派／移除彈窗與角色權限存檔彈窗用；緊急存取的三個原因欄沒改（BACKLOG 第 53 項）
-->
<template>
  <div class="field">
    <label :for="id">{{ label }}<span class="req-mark" title="必填">*</span>（{{ max }} 字內）</label>
    <textarea :id="id" :value="modelValue" rows="3" :disabled="disabled" :placeholder="placeholder" @input="onInput"></textarea>
    <div class="hint count" :class="{ warn: count > max }">{{ count }} / {{ max }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { charLen } from '../utils/text'

const props = withDefaults(
  defineProps<{
    modelValue: string
    /** textarea 的 id（label 的 for） */
    id: string
    label?: string
    max?: number
    placeholder?: string
    disabled?: boolean
  }>(),
  { label: '原因', max: 500, placeholder: '請填寫原因', disabled: false }
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const count = computed(() => charLen(props.modelValue))

function onInput(ev: Event): void {
  emit('update:modelValue', (ev.target as HTMLTextAreaElement).value)
}
</script>
