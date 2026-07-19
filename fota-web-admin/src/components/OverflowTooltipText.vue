<template>
  <span class="overflow-tooltip-root" v-bind="$attrs">
    <el-tooltip :disabled="!isOverflow || !text" :content="text" placement="top" effect="light">
      <span ref="textRef" class="overflow-tooltip-text">{{ text }}</span>
    </el-tooltip>
  </span>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

defineOptions({
  inheritAttrs: false
})

const props = defineProps({
  text: {
    type: String,
    default: ''
  }
})

const textRef = ref(null)
const isOverflow = ref(false)
let resizeObserver = null

function updateOverflowState() {
  const el = textRef.value
  if (!el) {
    isOverflow.value = false
    return
  }
  isOverflow.value = el.scrollWidth > el.clientWidth + 1
}

watch(
  () => props.text,
  async () => {
    await nextTick()
    updateOverflowState()
  }
)

onMounted(async () => {
  await nextTick()
  updateOverflowState()

  if (typeof ResizeObserver !== 'undefined' && textRef.value) {
    resizeObserver = new ResizeObserver(() => {
      updateOverflowState()
    })
    resizeObserver.observe(textRef.value)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})
</script>

<style scoped>
.overflow-tooltip-root {
  display: block;
  min-width: 0;
}

.overflow-tooltip-text {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
