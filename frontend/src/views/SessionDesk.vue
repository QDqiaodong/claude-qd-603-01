<template>
  <div class="sd-root">
    <!-- 一、快速开局：选一张空闲台 + 挂不挂会员卡 -->
    <section class="bl-panel">
      <div class="bl-panel__head">
        <h2>快速开局</h2>
        <span>只用空闲台；维护中或已有一局的台会被后端拦下</span>
      </div>
      <div class="sd-open">
        <el-select v-model="openForm.tableId" placeholder="选一张空闲球台" style="width: 190px">
          <el-option
            v-for="table in idleTables"
            :key="table.id"
            :value="table.id"
            :label="`${table.tableNo} · ${table.areaName} · ¥${money(table.hourlyRate)}/时`"
          />
        </el-select>
        <el-select v-model="openForm.memberId" clearable placeholder="散客（不挂会员卡）" style="width: 240px">
          <el-option :value="null" label="散客（不挂会员卡）" />
          <el-option
            v-for="member in members"
            :key="member.id"
            :value="member.id"
            :label="`${member.cardNo} ${member.name} · ${member.levelName}${member.status === 'FROZEN' ? ' · 已冻结' : ''}`"
          />
        </el-select>
        <el-button type="primary" :loading="busy" @click="submitOpen">开局</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <span class="bl-muted">空闲 {{ idleTables.length }} 张 / 共 {{ tables.length }} 张</span>
      </div>
    </section>

    <!-- 二、进行中：带秒表的小时条 -->
    <section class="bl-panel">
      <div class="bl-panel__head">
        <h2>进行中的局</h2>
        <span>点「结束计费」把台释放回空闲，并算出应收金额</span>
      </div>
      <div v-if="running.length === 0" class="bl-empty">当前没有进行中的局</div>
      <div v-else class="sd-tiles">
        <div v-for="item in running" :key="item.id" class="sd-tile sd-tile--running">
          <div class="sd-tile__head">
            <span class="sd-tile__table">{{ item.tableNo }}</span>
            <span class="sd-tile__clock">{{ clockOf(item) }}</span>
          </div>
          <div class="sd-tile__no">
            {{ item.sessionNo }}
            <el-tag v-if="item.transfers && item.transfers.length" size="small" type="warning">
              已换台 {{ item.transfers.length }} 次
            </el-tag>
          </div>
          <div class="bl-kv"><span>客户</span><b>{{ item.memberName || '散客' }}</b></div>
          <div class="bl-kv"><span>当前台单价</span><b>¥{{ money(item.hourlyRate) }}/时</b></div>
          <div class="bl-kv">
            <span>预估计费</span>
            <b>{{ previewBillableOf(item) }} 分 · ¥{{ money(previewAmount(item)) }}</b>
          </div>
          <div class="sd-tile__actions">
            <el-button size="small" @click="openTransfer(item)">换台</el-button>
            <el-button size="small" @click="openTrail(item)">轨迹</el-button>
          </div>
          <el-button type="primary" size="small" :loading="busy" @click="doClose(item)">结束计费</el-button>
        </div>
      </div>
    </section>

    <!-- 三、待结算：选支付方式收款 -->
    <section class="bl-panel">
      <div class="bl-panel__head">
        <h2>已结束待结算</h2>
        <span>余额结算会核会员卡余额，不足会直接拦住</span>
      </div>
      <div v-if="pending.length === 0" class="bl-empty">没有待结算的局</div>
      <div v-else class="sd-tiles">
        <div v-for="item in pending" :key="item.id" class="sd-tile sd-tile--pending">
          <div class="sd-tile__head">
            <span class="sd-tile__table">{{ item.tableNo }}</span>
            <el-tag size="small" type="warning">{{ item.statusName }}</el-tag>
          </div>
          <div class="sd-tile__no">{{ item.sessionNo }} · {{ item.memberName || '散客' }}</div>
          <div class="bl-kv"><span>实际 / 计费</span><b>{{ item.minutes }} / {{ item.billableMinutes }} 分</b></div>
          <div class="bl-kv"><span>应收</span><b>¥{{ money(item.amount) }}</b></div>
          <div class="bl-kv"><span>折扣 / 实收</span><b>{{ item.discount }} → ¥{{ money(item.payAmount) }}</b></div>
          <el-radio-group v-model="payModes[item.id]" size="small">
            <el-radio-button value="BALANCE">余额</el-radio-button>
            <el-radio-button value="CASH">现金</el-radio-button>
            <el-radio-button value="WECHAT">微信</el-radio-button>
            <el-radio-button value="CARD">银行卡</el-radio-button>
          </el-radio-group>
          <el-button type="primary" size="small" :loading="busy" @click="doSettle(item)">结算</el-button>
        </div>
      </div>
    </section>

    <!-- 四、全部流水 -->
    <section class="bl-panel">
      <div class="bl-panel__head">
        <h2>全部开局单</h2>
        <span>共 {{ sessions.length }} 条</span>
      </div>
      <el-radio-group v-model="filter" size="small" style="margin-bottom: 10px">
        <el-radio-button value="ALL">全部</el-radio-button>
        <el-radio-button value="OPEN">进行中</el-radio-button>
        <el-radio-button value="CLOSED">待结算</el-radio-button>
        <el-radio-button value="SETTLED">已结算</el-radio-button>
      </el-radio-group>
      <el-table :data="filtered" size="small" height="300">
        <el-table-column prop="sessionNo" label="单号" width="130" />
        <el-table-column label="球台轨迹" min-width="130">
          <template #default="{ row }">{{ trailText(row) }}</template>
        </el-table-column>
        <el-table-column label="客户" width="110">
          <template #default="{ row }">{{ row.memberName || '散客' }}</template>
        </el-table-column>
        <el-table-column prop="statusName" label="状态" width="110" />
        <el-table-column label="实际/计费" width="100">
          <template #default="{ row }">
            {{ row.minutes === null ? '—' : row.minutes + ' / ' + row.billableMinutes }}
          </template>
        </el-table-column>
        <el-table-column label="应收" width="90">
          <template #default="{ row }">{{ row.amount === null ? '—' : money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="实收" width="90">
          <template #default="{ row }">{{ row.payAmount === null ? '—' : money(row.payAmount) }}</template>
        </el-table-column>
        <el-table-column label="支付方式" width="90">
          <template #default="{ row }">{{ row.payModeName || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="70" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openTrail(row)">轨迹</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 换台对话框：选同类型空闲台 + 填原因，提交前明示原台会转维护 -->
    <SessionTransferDialog
      v-model="transferVisible"
      :session="transferSession"
      :tables="tables"
      @done="load"
      @refresh="load"
    />

    <!-- 开局详情：按时间顺序的计费分段 + 换台轨迹 -->
    <el-dialog
      v-model="trailVisible"
      :title="trail ? `开局详情 · ${trail.sessionNo}` : '开局详情'"
      width="760px"
      append-to-body
    >
      <div v-if="trail" class="sd-trail">
        <div class="sd-trail__summary">
          <el-tag size="small" :type="trail.status === 'OPEN' ? 'primary' : 'info'">{{ trail.statusName }}</el-tag>
          <span>客户 <b>{{ trail.memberName || '散客' }}</b></span>
          <span>
            当前所在球台
            <b>{{ currentSegmentOf(trail) ? currentSegmentOf(trail).tableNo : trail.tableNo }}</b>
          </span>
          <span v-if="trail.transfers && trail.transfers.length" class="sd-trail__faulty">
            故障原台 {{ trail.transfers.map((item) => item.fromTableNo).join('、') }}（已转维护）
          </span>
        </div>

        <p class="bl-muted">计费分段（按时间顺序；每段各自单价 × 实际分钟，合并后统一打会员折扣）</p>
        <el-table :data="trail.segments || []" size="small">
          <el-table-column prop="seq" label="段" width="46" />
          <el-table-column label="球台" width="150">
            <template #default="{ row }">
              {{ row.tableNo }}
              <el-tag v-if="!row.endTime" size="small" type="success">当前所在</el-tag>
              <el-tag v-else-if="row.closeCause === 'TRANSFER'" size="small" type="warning">故障转维护</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="开始" width="92">
            <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="结束" width="92">
            <template #default="{ row }">{{ row.endTime ? formatTime(row.endTime) : '进行中' }}</template>
          </el-table-column>
          <el-table-column label="实际/计费" width="86">
            <template #default="{ row }">
              {{ row.minutes === null ? '—' : row.minutes + ' / ' + row.billableMinutes }}
            </template>
          </el-table-column>
          <el-table-column label="当时单价" width="96">
            <template #default="{ row }">¥{{ money(row.hourlyRate) }}/时</template>
          </el-table-column>
          <el-table-column label="小计">
            <template #default="{ row }">{{ row.amount === null ? '—' : '¥' + money(row.amount) }}</template>
          </el-table-column>
        </el-table>

        <template v-if="trail.transfers && trail.transfers.length">
          <p class="bl-muted" style="margin-top: 12px">换台轨迹</p>
          <el-timeline style="padding-left: 4px; margin-top: 6px">
            <el-timeline-item
              v-for="item in trail.transfers"
              :key="item.id"
              :timestamp="formatFull(item.createdAt)"
            >
              {{ item.fromTableNo }} → {{ item.toTableNo }} · 原因：{{ item.reason }}（{{ item.fromTableNo }} 已转维护）
            </el-timeline-item>
          </el-timeline>
        </template>

        <div v-if="trail.amount !== null" class="sd-trail__total">
          应收 ¥{{ money(trail.amount) }} · 会员折扣 {{ trail.discount }} · 实收
          <b>¥{{ money(trail.payAmount) }}</b>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import SessionTransferDialog from './SessionTransferDialog.vue'
import { elapsedMinutes, formatFull, formatTime, money, previewBillable } from '../utils/format'

const loading = ref(false)
const busy = ref(false)
const sessions = ref([])
const tables = ref([])
const members = ref([])
const filter = ref('ALL')
const payModes = reactive({})

const openForm = reactive({ tableId: null, memberId: null })

const transferVisible = ref(false)
const transferSession = ref(null)
const trailVisible = ref(false)
const trail = ref(null)

const now = ref(Date.now())
let timer = null

const idleTables = computed(() => tables.value.filter((table) => table.status === 'IDLE'))
const running = computed(() => sessions.value.filter((item) => item.status === 'OPEN'))
const pending = computed(() => sessions.value.filter((item) => item.status === 'CLOSED'))
const filtered = computed(() => {
  if (filter.value === 'ALL') {
    return sessions.value
  }
  return sessions.value.filter((item) => item.status === filter.value)
})

function elapsedOf(item) {
  return elapsedMinutes(item.startTime, now.value)
}

function clockOf(item) {
  const total = elapsedOf(item)
  const hours = String(Math.floor(total / 60)).padStart(2, '0')
  const minutes = String(total % 60).padStart(2, '0')
  return `${hours}:${minutes}`
}

/** 当前进行中的计费段（换过台的局，当前所在球台 = 最后一段的球台） */
function currentSegmentOf(item) {
  const segments = item.segments || []
  return segments.find((segment) => !segment.endTime) || null
}

/** 全部开局单里的「球台轨迹」列：T-02 → T-05 → T-06 */
function trailText(row) {
  const segments = row.segments || []
  if (segments.length === 0) {
    return row.tableNo
  }
  return segments.map((segment) => segment.tableNo).join(' → ')
}

/**
 * 预估计费分钟：前端只做预估展示，真正算钱在后端。
 * 与后端口径一致：从未换过台的局不足一小时按一小时；换过台的局按各段实际分钟。
 */
function previewBillableOf(item) {
  const segments = item.segments || []
  if (segments.length === 0) {
    return previewBillable(elapsedOf(item))
  }
  let total = 0
  for (const segment of segments) {
    if (segment.endTime) {
      total += segment.billableMinutes || 0
    } else {
      const elapsed = elapsedMinutes(segment.startTime, now.value)
      total += segments.length === 1 ? Math.max(elapsed, 60) : elapsed
    }
  }
  return total
}

/** 预估金额：已关闭的段按已算小计累加，当前段按当时单价 × 预估分钟 */
function previewAmount(item) {
  const segments = item.segments || []
  if (segments.length === 0) {
    const rate = Number(item.hourlyRate || 0)
    return (rate * previewBillable(elapsedOf(item))) / 60
  }
  let sum = 0
  for (const segment of segments) {
    if (segment.endTime) {
      sum += Number(segment.amount || 0)
    } else {
      const elapsed = elapsedMinutes(segment.startTime, now.value)
      const billable = segments.length === 1 ? Math.max(elapsed, 60) : elapsed
      sum += (Number(segment.hourlyRate || 0) * billable) / 60
    }
  }
  return sum
}

function openTransfer(item) {
  transferSession.value = item
  transferVisible.value = true
}

function openTrail(item) {
  trail.value = item
  trailVisible.value = true
}

async function load() {
  loading.value = true
  try {
    const [sessionList, tableList, memberList] = await Promise.all([
      api.sessions(),
      api.tables(),
      api.members()
    ])
    sessions.value = sessionList
    tables.value = tableList
    members.value = memberList
    sessionList
      .filter((item) => item.status === 'CLOSED' && !payModes[item.id])
      .forEach((item) => {
        payModes[item.id] = item.memberId ? 'BALANCE' : 'CASH'
      })
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function submitOpen() {
  busy.value = true
  try {
    const session = await api.openSession({ tableId: openForm.tableId, memberId: openForm.memberId })
    ElMessage.success(`开局成功：${session.sessionNo}`)
    openForm.tableId = null
    openForm.memberId = null
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

async function doClose(item) {
  busy.value = true
  try {
    const session = await api.closeSession(item.id)
    ElMessage.success(`已结束：实际 ${session.minutes} 分，计费 ${session.billableMinutes} 分，应收 ¥${money(session.amount)}`)
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

async function doSettle(item) {
  busy.value = true
  try {
    const session = await api.settleSession(item.id, payModes[item.id])
    ElMessage.success(`${session.sessionNo} 结算完成，实收 ¥${money(session.payAmount)}`)
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  timer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
  await load()
})

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<style scoped>
.sd-root {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.sd-open {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.sd-tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(224px, 1fr));
  gap: 12px;
}

.sd-tile {
  display: flex;
  flex-direction: column;
  gap: 5px;
  border-radius: 12px;
  padding: 12px 14px;
  border: 1px solid #e8eaed;
  background: #ffffff;
}

.sd-tile--running {
  border-left: 4px solid #616161;
}

.sd-tile--pending {
  border-left: 4px solid #c9a227;
  background: #fffdf6;
}

.sd-tile__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sd-tile__table {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #2f2f2f;
}

.sd-tile__clock {
  font-family: "SF Mono", Menlo, Consolas, monospace;
  font-size: 14px;
  color: #616161;
  background: #f0f0f0;
  border-radius: 6px;
  padding: 2px 8px;
}

.sd-tile__no {
  font-size: 12px;
  color: #909399;
  margin-bottom: 2px;
}

.sd-tile :deep(.el-radio-group) {
  margin-top: 6px;
}

.sd-tile .el-button {
  margin-top: 8px;
  width: 100%;
}

.sd-tile__actions {
  display: flex;
  gap: 8px;
}

.sd-tile__actions .el-button {
  flex: 1;
  width: auto;
  margin-left: 0;
}

.sd-tile__no .el-tag {
  margin-left: 6px;
}

.sd-trail {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sd-trail__summary {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  font-size: 13px;
  color: #606266;
}

.sd-trail__faulty {
  color: #b88230;
}

.sd-trail__total {
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fffdf6;
  border: 1px solid #f0e2c0;
  font-size: 13px;
  color: #606266;
}

.sd-trail__total b {
  color: #2f2f2f;
  font-size: 15px;
}
</style>
