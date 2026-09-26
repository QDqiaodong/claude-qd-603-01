<template>
  <div class="ov-root" v-loading="loading">
    <!-- 台位概览 -->
    <section class="bl-panel">
      <div class="bl-panel__head">
        <h2>台位概览</h2>
        <span>数据来源：球台列表 + 平面图接口</span>
      </div>
      <div class="ov-stats">
        <div class="ov-stat">
          <span class="ov-stat__label">球台总数</span>
          <span class="ov-stat__value">{{ tables.length }}</span>
        </div>
        <div class="ov-stat">
          <span class="ov-stat__label">空闲</span>
          <span class="ov-stat__value">{{ statusCount('IDLE') }}</span>
        </div>
        <div class="ov-stat">
          <span class="ov-stat__label">使用中</span>
          <span class="ov-stat__value">{{ statusCount('OCCUPIED') }}</span>
        </div>
        <div class="ov-stat">
          <span class="ov-stat__label">维护中</span>
          <span class="ov-stat__value">{{ statusCount('MAINTENANCE') }}</span>
        </div>
        <div class="ov-stat ov-stat--wide">
          <span class="ov-stat__label">在用率（使用中 + 维护中）</span>
          <el-progress :percentage="usageRate" :stroke-width="12" :color="'#616161'" />
        </div>
      </div>
    </section>

    <!-- 计费概览 -->
    <section class="bl-panel">
      <div class="bl-panel__head">
        <h2>计费概览</h2>
        <span>开局单共 {{ sessions.length }} 笔</span>
      </div>
      <div class="ov-stats">
        <div class="ov-stat">
          <span class="ov-stat__label">进行中</span>
          <span class="ov-stat__value">{{ countBy('OPEN') }}</span>
        </div>
        <div class="ov-stat">
          <span class="ov-stat__label">已结束待结算</span>
          <span class="ov-stat__value">{{ countBy('CLOSED') }}</span>
        </div>
        <div class="ov-stat">
          <span class="ov-stat__label">已结算</span>
          <span class="ov-stat__value">{{ countBy('SETTLED') }}</span>
        </div>
        <div class="ov-stat">
          <span class="ov-stat__label">待结算应收合计</span>
          <span class="ov-stat__value">¥{{ money(pendingAmount) }}</span>
        </div>
        <div class="ov-stat">
          <span class="ov-stat__label">已结算实收合计</span>
          <span class="ov-stat__value">¥{{ money(settledAmount) }}</span>
        </div>
      </div>
    </section>

    <!-- @Formula 榜单：这几块的数据全部来自实体的只读计算列 -->
    <section class="bl-panel">
      <div class="bl-panel__head">
        <h2>只读计算列榜单（@Formula）</h2>
        <span>这些字段不落库，由 SQL 子查询现算</span>
      </div>
      <div class="ov-boards">
        <div class="ov-board">
          <h4>球台 · 累计开局次数 <code>BilliardTable.openCount</code></h4>
          <div v-for="item in topByOpenCount" :key="item.id" class="ov-bar">
            <span class="ov-bar__name">{{ item.tableNo }}</span>
            <span class="ov-bar__track"><i :style="{ width: barWidth(item.openCount, maxOpenCount) }"></i></span>
            <span class="ov-bar__value">{{ item.openCount }} 次</span>
          </div>
        </div>

        <div class="ov-board">
          <h4>球台 · 累计使用分钟 <code>BilliardTable.totalMinutes</code></h4>
          <div v-for="item in topByMinutes" :key="item.id" class="ov-bar">
            <span class="ov-bar__name">{{ item.tableNo }}</span>
            <span class="ov-bar__track"><i :style="{ width: barWidth(item.totalMinutes, maxMinutes) }"></i></span>
            <span class="ov-bar__value">{{ item.totalMinutes }} 分</span>
          </div>
        </div>

        <div class="ov-board">
          <h4>会员 · 当前进行中的局数 <code>Member.activeOrderCount</code></h4>
          <div v-for="item in topByActive" :key="item.id" class="ov-bar">
            <span class="ov-bar__name">{{ item.cardNo }}</span>
            <span class="ov-bar__track"><i :style="{ width: barWidth(item.activeOrderCount, maxActive) }"></i></span>
            <span class="ov-bar__value">{{ item.activeOrderCount }} 局</span>
          </div>
        </div>

        <div class="ov-board">
          <h4>器材 · 累计被借次数 <code>Gear.rentCount</code></h4>
          <div v-for="item in topByRent" :key="item.id" class="ov-bar">
            <span class="ov-bar__name">{{ item.gearNo }}</span>
            <span class="ov-bar__track"><i :style="{ width: barWidth(item.rentCount, maxRent) }"></i></span>
            <span class="ov-bar__value">{{ item.rentCount }} 次</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 会员与器材分布 -->
    <section class="ov-grid">
      <div class="bl-panel">
        <div class="bl-panel__head">
          <h2>会员等级分布</h2>
          <span>共 {{ members.length }} 张卡</span>
        </div>
        <div v-for="row in levelRows" :key="row.label" class="ov-bar">
          <span class="ov-bar__name">{{ row.label }}</span>
          <span class="ov-bar__track"><i :style="{ width: barWidth(row.value, members.length) }"></i></span>
          <span class="ov-bar__value">{{ row.value }} 张</span>
        </div>
      </div>
      <div class="bl-panel">
        <div class="bl-panel__head">
          <h2>器材状态分布</h2>
          <span>共 {{ gears.length }} 件 · 在租 {{ rentingCount }} 条</span>
        </div>
        <div v-for="row in gearRows" :key="row.label" class="ov-bar">
          <span class="ov-bar__name">{{ row.label }}</span>
          <span class="ov-bar__track"><i :style="{ width: barWidth(row.value, gears.length) }"></i></span>
          <span class="ov-bar__value">{{ row.value }} 件</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { money } from '../utils/format'

const loading = ref(false)
const tables = ref([])
const sessions = ref([])
const members = ref([])
const gears = ref([])
const rentals = ref([])

const usageRate = computed(() => {
  if (tables.value.length === 0) {
    return 0
  }
  const busy = tables.value.filter((table) => table.status !== 'IDLE').length
  return Math.round((busy / tables.value.length) * 100)
})

const pendingAmount = computed(() =>
  sessions.value
    .filter((item) => item.status === 'CLOSED')
    .reduce((sum, item) => sum + Number(item.payAmount || 0), 0)
)

const settledAmount = computed(() =>
  sessions.value
    .filter((item) => item.status === 'SETTLED')
    .reduce((sum, item) => sum + Number(item.payAmount || 0), 0)
)

const rentingCount = computed(() => rentals.value.filter((rental) => rental.status === 'RENTED').length)

const topByOpenCount = computed(() =>
  [...tables.value].sort((a, b) => Number(b.openCount) - Number(a.openCount)).slice(0, 6)
)
const topByMinutes = computed(() =>
  [...tables.value].sort((a, b) => Number(b.totalMinutes) - Number(a.totalMinutes)).slice(0, 6)
)
const topByActive = computed(() =>
  [...members.value].sort((a, b) => Number(b.activeOrderCount) - Number(a.activeOrderCount)).slice(0, 6)
)
const topByRent = computed(() =>
  [...gears.value].sort((a, b) => Number(b.rentCount) - Number(a.rentCount)).slice(0, 6)
)

const maxOpenCount = computed(() => maxOf(topByOpenCount.value, 'openCount'))
const maxMinutes = computed(() => maxOf(topByMinutes.value, 'totalMinutes'))
const maxActive = computed(() => maxOf(topByActive.value, 'activeOrderCount'))
const maxRent = computed(() => maxOf(topByRent.value, 'rentCount'))

const levelRows = computed(() => {
  const names = ['NORMAL', 'SILVER', 'GOLD', 'DIAMOND']
  const labels = { NORMAL: '普通会员', SILVER: '银卡会员', GOLD: '金卡会员', DIAMOND: '钻石卡会员' }
  return names.map((name) => ({
    label: labels[name],
    value: members.value.filter((member) => member.level === name).length
  }))
})

const gearRows = computed(() => {
  const names = ['AVAILABLE', 'RENTED', 'DAMAGED', 'SCRAPPED']
  const labels = { AVAILABLE: '在库', RENTED: '已借出', DAMAGED: '已损坏', SCRAPPED: '已报废' }
  return names.map((name) => ({
    label: labels[name],
    value: gears.value.filter((gear) => gear.status === name).length
  }))
})

function maxOf(list, key) {
  return list.reduce((max, item) => Math.max(max, Number(item[key] || 0)), 1)
}

function barWidth(value, max) {
  const ratio = Number(value || 0) / (max || 1)
  return `${Math.max(ratio * 100, value > 0 ? 6 : 0)}%`
}

function statusCount(status) {
  return tables.value.filter((table) => table.status === status).length
}

function countBy(status) {
  return sessions.value.filter((item) => item.status === status).length
}

async function load() {
  loading.value = true
  try {
    const [tableList, sessionList, memberList, gearList, rentalList] = await Promise.all([
      api.tables(),
      api.sessions(),
      api.members(),
      api.gears(),
      api.rentals()
    ])
    tables.value = tableList
    sessions.value = sessionList
    members.value = memberList
    gears.value = gearList
    rentals.value = rentalList
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.ov-root {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ov-stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}

.ov-stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
  border: 1px solid #e8eaed;
  border-radius: 10px;
  padding: 10px 12px;
  background: #fcfcfd;
}

.ov-stat--wide {
  grid-column: span 2;
}

.ov-stat__label {
  font-size: 12px;
  color: #909399;
}

.ov-stat__value {
  font-size: 22px;
  font-weight: 700;
  color: #2f2f2f;
  letter-spacing: 0.5px;
}

.ov-boards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.ov-board h4 {
  margin: 0 0 8px;
  font-size: 12px;
  color: #606266;
  font-weight: 600;
}

.ov-board code {
  font-size: 11px;
  color: #616161;
  background: #f2f2f2;
  border-radius: 4px;
  padding: 1px 5px;
}

.ov-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 14px;
}

.ov-bar {
  display: grid;
  grid-template-columns: 58px 1fr 62px;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #606266;
  padding: 4px 0;
}

.ov-bar__name {
  color: #303133;
  font-weight: 600;
}

.ov-bar__track {
  display: block;
  height: 9px;
  border-radius: 999px;
  background: #f0f0f0;
  overflow: hidden;
}

.ov-bar__track i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: #616161;
}

.ov-bar__value {
  text-align: right;
  color: #909399;
}
</style>
