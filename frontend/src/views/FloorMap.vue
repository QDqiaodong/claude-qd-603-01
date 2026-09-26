<template>
  <div class="fm-root">
    <!-- 顶部工具条：没有「查询表单」，只有刷新 / 新增 / 待结算提醒 -->
    <div class="bl-toolbar">
      <el-button :loading="loading" @click="load">刷新平面图</el-button>
      <el-button type="primary" @click="openCreate">＋ 新增球台</el-button>
      <span v-if="pendingCount > 0" class="fm-pill" @click="goSessions">
        待结算 {{ pendingCount }} 笔 · 去计费台处理
      </span>
      <span class="bl-muted">共 {{ tableTotal }} 张台 · 点方块即可操作</span>
    </div>

    <!-- 平面图：按区域分区，区内是台位方块 -->
    <div v-for="area in areas" :key="area.areaName" class="fm-area">
      <div class="fm-area__head">
        <h3>{{ area.areaName }}</h3>
        <div class="fm-area__stats">
          <span>共 {{ area.total }} 台</span>
          <span class="fm-area__stat fm-area__stat--idle">空闲 {{ area.idleCount }}</span>
          <span class="fm-area__stat fm-area__stat--occupied">使用中 {{ area.occupiedCount }}</span>
          <span class="fm-area__stat fm-area__stat--maintenance">维护 {{ area.maintenanceCount }}</span>
        </div>
      </div>

      <div class="fm-grid">
        <div
          v-for="table in area.tables"
          :key="table.id"
          class="fm-block"
          :class="'is-' + table.status.toLowerCase()"
          @click="openTable(table)"
        >
          <i class="fm-pocket fm-pocket--tl"></i>
          <i class="fm-pocket fm-pocket--tr"></i>
          <i class="fm-pocket fm-pocket--bl"></i>
          <i class="fm-pocket fm-pocket--br"></i>

          <div class="fm-block__top">
            <span class="fm-block__no">{{ table.tableNo }}</span>
            <span class="fm-block__state">{{ table.statusName }}</span>
          </div>
          <div class="fm-block__meta">{{ table.tableTypeName }} · ¥{{ money(table.hourlyRate) }}/时</div>

          <!-- 这两项是后端 @Formula 只读计算列算出来的，不落库 -->
          <div class="fm-block__formula">
            <span>累计开局 <b>{{ table.openCount }}</b> 次</span>
            <span>累计 <b>{{ table.totalMinutes }}</b> 分</span>
          </div>

          <div class="fm-block__foot">
            <template v-if="table.status === 'OCCUPIED'">
              已开 {{ elapsedOf(table) }} 分 · {{ table.currentMemberName || '散客' }}
            </template>
            <template v-else-if="table.status === 'MAINTENANCE'">
              暂停开放 · 点方块恢复
            </template>
            <template v-else>
              点方块开局
            </template>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && areas.length === 0" description="还没有球台，先新增一张" />

    <!-- 唯一的一个对话框：按台的状态换内容，不是「新增/编辑」两态表单 -->
    <el-dialog v-model="visible" :title="dialogTitle" width="520px" append-to-body>
      <div v-if="active" class="fm-dialog">
        <div class="fm-dialog__hero">
          <div class="fm-dialog__hero-no">{{ active.tableNo }}</div>
          <div class="fm-dialog__hero-meta">
            <p>{{ active.areaName }} · {{ active.tableTypeName }} · ¥{{ money(active.hourlyRate) }}/小时</p>
            <p>
              <el-tag size="small" :type="tagType(active.status)">{{ active.statusName }}</el-tag>
              <span class="bl-muted">
                累计开局 {{ active.openCount }} 次 · 累计 {{ active.totalMinutes }} 分钟
              </span>
            </p>
          </div>
        </div>

        <!-- 空闲台：开局 or 转维护 -->
        <div v-if="stage === 'idle'">
          <el-form label-width="86px" size="default">
            <el-form-item label="挂会员卡">
              <el-select v-model="openForm.memberId" clearable placeholder="散客（不挂会员卡）" style="width: 100%">
                <el-option :value="null" label="散客（不挂会员卡）" />
                <el-option
                  v-for="member in members"
                  :key="member.id"
                  :value="member.id"
                  :label="`${member.cardNo} ${member.name} · ${member.levelName} · 余额 ${money(member.balance)}${member.status === 'FROZEN' ? ' · 已冻结' : ''}`"
                />
              </el-select>
            </el-form-item>
          </el-form>
          <p class="bl-muted">
            开局后这张台会变成「使用中」；同一张台同一时间只允许一局，维护中的台开了也会被后端拦下。
          </p>
        </div>

        <!-- 使用中：结束计费 or 换台 -->
        <div v-else-if="stage === 'occupied'">
          <div class="fm-dialog__box">
            <div class="bl-kv"><span>进行中的单号</span><b>{{ active.currentSessionNo }}</b></div>
            <div class="bl-kv"><span>客户</span><b>{{ active.currentMemberName || '散客' }}</b></div>
            <div class="bl-kv"><span>已开时长</span><b>{{ elapsedOf(active) }} 分钟</b></div>
            <div class="bl-kv" v-if="activeTransfers.length > 0">
              <span>换台</span>
              <b>本局已换台 {{ activeTransfers.length }} 次（{{ activeTransfers.map((item) => item.fromTableNo).join('、') }} 已转维护）</b>
            </div>
            <div class="bl-kv">
              <span>按口径预估计费</span>
              <b>{{ previewBillable(elapsedOf(active)) }} 分钟（不足一小时按一小时）</b>
            </div>
          </div>
          <p class="bl-muted">台呢破损、灯光故障等突发情况可点「换台」把本局挪到同类型空闲台，原台直接转维护；否则先「结束计费」，球台立刻释放回空闲，再在计费台把这笔结算掉。</p>
        </div>

        <!-- 已结束待结算：选支付方式结算 -->
        <div v-else-if="stage === 'closed'">
          <div class="fm-dialog__box">
            <div class="bl-kv"><span>单号</span><b>{{ closed.sessionNo }}</b></div>
            <div class="bl-kv"><span>实际时长 / 计费时长</span><b>{{ closed.minutes }} / {{ closed.billableMinutes }} 分钟</b></div>
            <div class="bl-kv"><span>应收</span><b>¥{{ money(closed.amount) }}</b></div>
            <div class="bl-kv"><span>会员折扣</span><b>{{ closed.discount }}</b></div>
            <div class="bl-kv"><span>实收</span><b>¥{{ money(closed.payAmount) }}</b></div>
          </div>
          <el-form label-width="86px" style="margin-top: 10px">
            <el-form-item label="支付方式">
              <el-radio-group v-model="settleForm.payMode">
                <el-radio-button value="BALANCE">储值余额</el-radio-button>
                <el-radio-button value="CASH">现金</el-radio-button>
                <el-radio-button value="WECHAT">微信支付</el-radio-button>
                <el-radio-button value="CARD">银行卡</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-form>
          <p class="bl-muted">余额结算会核会员卡余额，不够就直接拦住并给出还差多少钱。</p>
        </div>

        <!-- 维护中：恢复 or 试开局 -->
        <div v-else-if="stage === 'maintenance'">
          <div class="fm-dialog__box">
            <p class="fm-dialog__warn">该台正在维护中，按规则不允许开局。</p>
          </div>
          <p class="bl-muted">可以点「尝试开局」验证后端拦截，也可以点「恢复空闲」把台放回可用状态。</p>
        </div>

        <!-- 该台最近的开局单 -->
        <div v-if="recent.length > 0" class="fm-dialog__recent">
          <p class="bl-muted">该台最近的开局</p>
          <div v-for="item in recent" :key="item.id" class="fm-dialog__recent-row">
            <span>{{ item.sessionNo }}</span>
            <span>{{ item.statusName }}</span>
            <span>{{ item.billableMinutes ? item.billableMinutes + ' 分' : '—' }}</span>
            <span>{{ item.payAmount ? '¥' + money(item.payAmount) : '—' }}</span>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <template v-if="stage === 'idle'">
          <el-button @click="toMaintenance">转入维护</el-button>
          <el-button type="primary" :loading="busy" @click="submitOpen">开局</el-button>
        </template>
        <template v-else-if="stage === 'occupied'">
          <el-button :loading="busy" @click="openTransfer">换台</el-button>
          <el-button type="primary" :loading="busy" @click="doClose">结束计费</el-button>
        </template>
        <template v-else-if="stage === 'closed'">
          <el-button type="primary" :loading="busy" @click="doSettle">结算</el-button>
        </template>
        <template v-else-if="stage === 'maintenance'">
          <el-button @click="tryOpen">尝试开局（应被拒绝）</el-button>
          <el-button type="primary" :loading="busy" @click="toRestore">恢复空闲</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 新增球台 -->
    <el-dialog v-model="createVisible" title="新增球台" width="460px" append-to-body>
      <el-form label-width="92px">
        <el-form-item label="台号"><el-input v-model="createForm.tableNo" placeholder="例如 T-09" /></el-form-item>
        <el-form-item label="所属区域"><el-input v-model="createForm.areaName" placeholder="例如 A 区大厅" /></el-form-item>
        <el-form-item label="台型">
          <el-select v-model="createForm.tableType" style="width: 100%">
            <el-option value="CHINESE_EIGHT" label="中式黑八" />
            <el-option value="SNOOKER" label="斯诺克" />
            <el-option value="AMERICAN_NINE" label="美式九球" />
          </el-select>
        </el-form-item>
        <el-form-item label="每小时单价">
          <el-input-number v-model="createForm.hourlyRate" :min="0" :step="2" :precision="2" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="createForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 换台对话框：从进行中的局发起，确认后原台转维护 -->
    <SessionTransferDialog
      v-model="transferVisible"
      :session="transferSession"
      :tables="allTables"
      @done="onTransferred"
      @refresh="load"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import SessionTransferDialog from './SessionTransferDialog.vue'
import { elapsedMinutes, money, previewBillable } from '../utils/format'
import { navigate } from '../router'

const loading = ref(false)
const busy = ref(false)
const areas = ref([])
const members = ref([])
const pendingCount = ref(0)
/** 单号 -> 开局单，用来算方块上的「已开多少分钟」 */
const sessionMap = ref({})

const visible = ref(false)
const createVisible = ref(false)
const active = ref(null)
const stage = ref('idle')
const closed = ref(null)
const recent = ref([])

const transferVisible = ref(false)
const transferSession = ref(null)

const openForm = reactive({ memberId: null })
const settleForm = reactive({ payMode: 'CASH' })
const createForm = reactive({
  tableNo: '',
  areaName: '',
  tableType: 'CHINESE_EIGHT',
  hourlyRate: 40,
  remark: ''
})

const now = ref(Date.now())
let timer = null

const tableTotal = computed(() => areas.value.reduce((sum, area) => sum + area.total, 0))

/** 平面图上全部球台（TableView），给换台对话框筛同类型空闲台用 */
const allTables = computed(() => areas.value.flatMap((area) => area.tables))

/** 当前这局已经发生的换台轨迹（从开局单详情里取） */
const activeTransfers = computed(() => {
  if (!active.value || !active.value.currentSessionNo) {
    return []
  }
  const item = sessionMap.value[active.value.currentSessionNo]
  return item && item.transfers ? item.transfers : []
})

const dialogTitle = computed(() => {
  if (!active.value) {
    return '球台'
  }
  if (stage.value === 'closed') {
    return `${active.value.tableNo} · 结算这笔开局单`
  }
  return `${active.value.tableNo} · ${active.value.statusName}`
})

function elapsedOf(table) {
  if (!table.currentSessionNo) {
    return 0
  }
  const item = sessionMap.value[table.currentSessionNo]
  return item ? elapsedMinutes(item.startTime, now.value) : 0
}

function tagType(status) {
  if (status === 'OCCUPIED') {
    return 'info'
  }
  if (status === 'MAINTENANCE') {
    return 'warning'
  }
  return 'success'
}

async function load() {
  loading.value = true
  try {
    const plan = await api.floorPlan()
    areas.value = plan && plan.areas ? plan.areas : []
    const sessions = await api.sessions()
    pendingCount.value = sessions.filter((item) => item.status === 'CLOSED').length
    const map = {}
    sessions.forEach((item) => {
      map[item.sessionNo] = item
    })
    sessionMap.value = map
    if (active.value) {
      const fresh = areas.value
        .flatMap((area) => area.tables)
        .find((table) => table.id === active.value.id)
      if (fresh) {
        active.value = fresh
      }
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function loadMembers() {
  try {
    members.value = await api.members()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function openTable(table) {
  active.value = table
  closed.value = null
  openForm.memberId = null
  settleForm.payMode = 'CASH'
  stage.value = table.status === 'MAINTENANCE'
    ? 'maintenance'
    : (table.status === 'OCCUPIED' ? 'occupied' : 'idle')
  visible.value = true
  try {
    recent.value = await api.sessionsOfTable(table.id)
  } catch (error) {
    recent.value = []
  }
}

async function submitOpen() {
  busy.value = true
  try {
    const session = await api.openSession({ tableId: active.value.id, memberId: openForm.memberId })
    ElMessage.success(`开局成功：${session.sessionNo}`)
    visible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

async function tryOpen() {
  busy.value = true
  try {
    await api.openSession({ tableId: active.value.id, memberId: null })
    ElMessage.warning('居然开成功了？这与规则不符')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

async function toMaintenance() {
  busy.value = true
  try {
    await api.tableMaintenance(active.value.id)
    ElMessage.success('球台已转入维护')
    visible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

async function toRestore() {
  busy.value = true
  try {
    await api.tableRestore(active.value.id)
    ElMessage.success('球台维护完成，回到空闲')
    visible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

async function doClose() {
  busy.value = true
  try {
    const session = await api.closeSession(active.value.currentSessionId)
    closed.value = session
    stage.value = 'closed'
    ElMessage.success(`已结束：实际 ${session.minutes} 分，计费 ${session.billableMinutes} 分，应收 ¥${money(session.amount)}`)
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

/** 从进行中的局发起换台：把当前开局单塞进换台对话框 */
function openTransfer() {
  const item = sessionMap.value[active.value.currentSessionNo]
  transferSession.value = {
    id: active.value.currentSessionId,
    sessionNo: active.value.currentSessionNo,
    tableId: active.value.id,
    tableNo: active.value.tableNo,
    hourlyRate: active.value.hourlyRate,
    memberName: active.value.currentMemberName,
    segments: item ? item.segments : [],
    transfers: item ? item.transfers : []
  }
  transferVisible.value = true
}

/** 换台成功：原台已转维护，关掉球台对话框并刷新平面图 */
async function onTransferred() {
  visible.value = false
  await load()
}

async function doSettle() {
  busy.value = true
  try {
    const session = await api.settleSession(closed.value.id, settleForm.payMode)
    ElMessage.success(`${session.sessionNo} 结算完成，实收 ¥${money(session.payAmount)}`)
    visible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

function openCreate() {
  createForm.tableNo = ''
  createForm.areaName = ''
  createForm.tableType = 'CHINESE_EIGHT'
  createForm.hourlyRate = 40
  createForm.remark = ''
  createVisible.value = true
}

async function submitCreate() {
  busy.value = true
  try {
    const table = await api.createTable({ ...createForm })
    ElMessage.success(`球台 ${table.tableNo} 已创建`)
    createVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

function goSessions() {
  navigate('/sessions')
}

onMounted(async () => {
  timer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
  await Promise.all([load(), loadMembers()])
})

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<style scoped>
.fm-root {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.fm-pill {
  padding: 5px 12px;
  border-radius: 999px;
  background: #f0f0f0;
  border: 1px solid #dcdcdc;
  color: #616161;
  font-size: 12px;
  cursor: pointer;
}

/* ---------------- 区域 ---------------- */
.fm-area {
  background: #ffffff;
  border: 1px solid #e8eaed;
  border-radius: 14px;
  padding: 12px 14px 16px;
}

.fm-area__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
  border-bottom: 1px dashed #e8eaed;
  padding-bottom: 8px;
}

.fm-area__head h3 {
  margin: 0;
  font-size: 14px;
  color: #2f2f2f;
  letter-spacing: 0.8px;
}

.fm-area__stats {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}

.fm-area__stat--idle {
  color: #616161;
}

.fm-area__stat--occupied {
  color: #2f2f2f;
  font-weight: 600;
}

.fm-area__stat--maintenance {
  color: #b88230;
}

/* ---------------- 台位方块（照着台球台的形状画） ---------------- */
.fm-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.fm-block {
  position: relative;
  border-radius: 14px;
  padding: 12px 14px 10px;
  min-height: 122px;
  border: 1.5px solid #d6d8db;
  background: #ffffff;
  cursor: pointer;
  transition: transform 0.12s ease, box-shadow 0.12s ease;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.fm-block:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.1);
}

.fm-block.is-occupied {
  background: #616161;
  border-color: #4e4e4e;
  color: #ffffff;
}

.fm-block.is-maintenance {
  background: repeating-linear-gradient(135deg, #f4f4f4 0 9px, #e9e9e9 9px 18px);
  border-color: #d6d8db;
  color: #8a8a8a;
}

.fm-pocket {
  position: absolute;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #cfd2d6;
}

.is-occupied .fm-pocket {
  background: #ffffff;
}

.is-maintenance .fm-pocket {
  background: #c7c7c7;
}

.fm-pocket--tl { top: 5px; left: 5px; }
.fm-pocket--tr { top: 5px; right: 5px; }
.fm-pocket--bl { bottom: 5px; left: 5px; }
.fm-pocket--br { bottom: 5px; right: 5px; }

.fm-block__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}

.fm-block__no {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 1px;
}

.fm-block__state {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #f0f0f0;
  color: #616161;
}

.is-occupied .fm-block__state {
  background: rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

.is-maintenance .fm-block__state {
  background: #e2e2e2;
  color: #777777;
}

.fm-block__meta {
  font-size: 12px;
  opacity: 0.85;
}

.fm-block__formula {
  display: flex;
  gap: 12px;
  font-size: 11px;
  opacity: 0.78;
}

.fm-block__formula b {
  font-size: 12px;
}

.fm-block__foot {
  margin-top: auto;
  padding-top: 6px;
  border-top: 1px dashed rgba(0, 0, 0, 0.12);
  font-size: 11px;
  opacity: 0.9;
}

.is-occupied .fm-block__foot {
  border-top-color: rgba(255, 255, 255, 0.28);
}

/* ---------------- 对话框 ---------------- */
.fm-dialog__hero {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f1f3;
  margin-bottom: 12px;
}

.fm-dialog__hero-no {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 58px;
  height: 46px;
  border-radius: 10px;
  background: #616161;
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
}

.fm-dialog__hero-meta p {
  margin: 0 0 4px;
  font-size: 12px;
  color: #606266;
}

.fm-dialog__box {
  background: #fafafa;
  border: 1px solid #eeeeee;
  border-radius: 10px;
  padding: 10px 12px;
}

.fm-dialog__warn {
  margin: 0;
  color: #b88230;
  font-size: 13px;
}

.fm-dialog__recent {
  margin-top: 14px;
}

.fm-dialog__recent-row {
  display: grid;
  grid-template-columns: 1.4fr 1fr 0.8fr 0.9fr;
  gap: 6px;
  font-size: 12px;
  color: #606266;
  padding: 5px 0;
  border-bottom: 1px dashed #f0f1f3;
}
</style>
