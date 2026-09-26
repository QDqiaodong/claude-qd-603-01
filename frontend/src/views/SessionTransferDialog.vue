<template>
  <el-dialog
    :model-value="modelValue"
    :title="`换台 · ${session ? session.sessionNo : ''}`"
    width="540px"
    append-to-body
    @update:model-value="close"
    @open="onOpen"
  >
    <div v-if="session" class="td-root">
      <!-- 故障原台：确认后转维护 -->
      <div class="td-box td-box--from">
        <div class="td-box__label">当前球台（故障原台）</div>
        <div class="td-box__main">
          <b>{{ session.tableNo }}</b>
          <span class="bl-muted">
            {{ currentTable ? currentTable.areaName : '' }} ·
            {{ currentTable ? currentTable.tableTypeName : '' }} ·
            ¥{{ money(session.hourlyRate) }}/时
          </span>
        </div>
        <el-tag size="small" type="warning">确认后转为「维护中」</el-tag>
      </div>

      <div class="td-arrow">↓ 换入</div>

      <el-form label-width="86px">
        <el-form-item label="目标球台" required>
          <el-select
            v-model="form.targetTableId"
            placeholder="只列出同类型且空闲的球台"
            style="width: 100%"
            :disabled="candidates.length === 0"
          >
            <el-option
              v-for="table in candidates"
              :key="table.id"
              :value="table.id"
              :label="`${table.tableNo} · ${table.areaName} · ¥${money(table.hourlyRate)}/时`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="换台原因" required>
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="例如：台呢破损 / 灯光故障 / 空调漏水"
          />
        </el-form-item>
      </el-form>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        :title="`确认换台后：${session.tableNo} 立即转为维护中，目标球台转为使用中`"
        :description="`本局单号 ${session.sessionNo} 与会员归属不变；前后两段按各自单价与实际分钟分别计费，结束时合并后再统一打会员折扣。`"
      />
      <p v-if="candidates.length === 0" class="td-empty">当前没有可换的同类型空闲球台，无法换台</p>
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="busy" :disabled="!canSubmit" @click="submit">
        确认换台（原台转维护）
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { money } from '../utils/format'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 进行中的开局单（SessionView）：至少有 id / sessionNo / tableId / tableNo / hourlyRate */
  session: { type: Object, default: null },
  /** 全部球台（TableView）：用来筛同类型空闲台 */
  tables: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'done', 'refresh'])

const busy = ref(false)
const form = reactive({ targetTableId: null, reason: '', requestId: '' })

const currentTable = computed(() =>
  props.tables.find((table) => props.session && table.id === props.session.tableId) || null
)

/** 候选目标台：同类型、空闲、非当前台（维护中与已占用的一律不出现） */
const candidates = computed(() => {
  if (!currentTable.value) {
    return []
  }
  return props.tables.filter((table) =>
    table.status === 'IDLE' &&
    table.tableType === currentTable.value.tableType &&
    table.id !== currentTable.value.id
  )
})

const canSubmit = computed(() =>
  !busy.value && form.targetTableId !== null && form.reason.trim().length > 0 && candidates.value.length > 0
)

/** 每次打开对话框生成一次幂等键：连点 / 网络重试都用同一个，服务端只生效一次 */
function onOpen() {
  form.targetTableId = null
  form.reason = ''
  form.requestId = newRequestId()
}

function newRequestId() {
  if (window.crypto && typeof window.crypto.randomUUID === 'function') {
    return window.crypto.randomUUID()
  }
  return 'req-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10)
}

function close() {
  emit('update:modelValue', false)
}

async function submit() {
  if (!canSubmit.value) {
    return
  }
  busy.value = true
  try {
    const result = await api.transferSession(props.session.id, {
      targetTableId: form.targetTableId,
      reason: form.reason.trim(),
      requestId: form.requestId
    })
    if (result && result.duplicated) {
      ElMessage.warning('该换台请求已提交过，系统未重复换台')
    } else if (result && result.transfer) {
      ElMessage.success(`换台成功：${result.transfer.fromTableNo} → ${result.transfer.toTableNo}，原台已转维护`)
    } else {
      ElMessage.success('换台成功')
    }
    close()
    emit('done')
  } catch (error) {
    // 目标台可能刚被别人开走或转入维护：让父页面刷新到最新状态，对话框留着供重选
    ElMessage.error(error.message)
    emit('refresh')
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.td-root {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.td-box {
  display: flex;
  align-items: center;
  gap: 10px;
  border-radius: 10px;
  padding: 10px 12px;
  border: 1px solid #eeeeee;
  background: #fafafa;
}

.td-box--from {
  border-color: #f0e2c0;
  background: #fffdf6;
}

.td-box__label {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}

.td-box__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
}

.td-box__main b {
  font-size: 15px;
  letter-spacing: 1px;
}

.td-arrow {
  text-align: center;
  color: #909399;
  font-size: 12px;
}

.td-empty {
  margin: 0;
  font-size: 12px;
  color: #b88230;
}
</style>
