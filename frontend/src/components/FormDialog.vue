<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="dialogWidth"
    :top="isNarrow ? '6vh' : '10vh'"
    :close-on-click-modal="false"
    :destroy-on-close="destroyOnClose"
    append-to-body
    @closed="onClosed"
  >
    <div class="form-dialog__body">
      <slot>
        <el-form
          ref="formRef"
          :model="model"
          :rules="rules"
          :label-width="labelWidth"
          :label-position="labelPosition"
          :disabled="loading"
        >
          <slot name="form" />
        </el-form>
      </slot>
    </div>

    <template #footer>
      <slot name="footer">
        <el-button :disabled="loading" @click="close">取消</el-button>
        <el-button type="primary" :loading="loading" @click="submit">{{ confirmText }}</el-button>
      </slot>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

/**
 * 弹窗表单封装：el-dialog + el-form + 提交/取消。
 * 宽度响应式：窄屏 92%，宽屏用传入的 640/720px。
 * 表单字段写在 #form 插槽里，需要校验时把 el-form 的 ref 交回给父组件（submit 会自动校验）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  /** 宽屏宽度，窄屏自动降为 92% */
  width: { type: [String, Number], default: 640 },
  model: { type: Object, default: () => ({}) },
  rules: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  confirmText: { type: String, default: '确定' },
  labelWidth: { type: String, default: '96px' },
  /** 窄屏下标签换行显示更省横向空间 */
  labelPosition: { type: String, default: 'right' },
  destroyOnClose: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'submit', 'closed'])

const formRef = ref(null)

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const isNarrow = computed(() => typeof window !== 'undefined' && window.innerWidth <= 768)

const dialogWidth = computed(() => (isNarrow.value ? '92%' : typeof props.width === 'number' ? `${props.width}px` : props.width))

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      // 打开时清掉上一次的校验痕迹
      requestAnimationFrame(() => formRef.value?.clearValidate?.())
    }
  }
)

const close = () => {
  visible.value = false
}

/** 提交：先跑表单校验，通过后把事件抛给父组件做接口调用 */
const submit = async () => {
  if (formRef.value) {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return
  }
  emit('submit')
}

const onClosed = () => {
  formRef.value?.resetFields?.()
  emit('closed')
}

defineExpose({ formRef, close })
</script>

<style scoped>
.form-dialog__body {
  max-height: 64vh;
  overflow-y: auto;
  padding-right: 2px;
}

@media (max-width: 768px) {
  .form-dialog__body {
    max-height: 68vh;
  }
}
</style>
