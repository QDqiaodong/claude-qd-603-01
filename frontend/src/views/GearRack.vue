<template>
  <div class="gr-root">
    <!-- 挂杆架：器材按格位排布，点格位操作 -->
    <section class="bl-panel">
      <div class="bl-panel__head">
        <h2>器材挂架</h2>
        <span>在库 {{ availableCount }} 件 · 借出 {{ rentedCount }} 件 · 损坏 {{ damagedCount }} 件 · 报废 {{ scrappedCount }} 件</span>
      </div>
      <div class="bl-toolbar">
        <el-button :loading="loading" @click="load">刷新挂架</el-button>
        <el-button type="primary" @click="openCreateGear">＋ 器材入库</el-button>
        <span class="bl-muted">每格右上角的「被借 N 次」来自后端只读计算列</span>
      </div>

      <div class="gr-rack">
        <div
          v-for="gear in gears"
          :key="gear.id"
          class="gr-slot"
          :class="'is-' + gear.status.toLowerCase()"
          @click="openGear(gear)"
        >
          <div class="gr-slot__head">
            <span class="gr-slot__no">{{ gear.gearNo }}</span>
            <span class="gr-slot__count">被借 {{ gear.rentCount }} 次</span>
          </div>
          <div class="gr-slot__name">{{ gear.gearName }}</div>
          <div class="gr-slot__meta">{{ gear.gearTypeName }} · 原价 ¥{{ money(gear.price) }}</div>
          <div class="gr-slot__foot">
            <el-tag size="small" :type="gearTag(gear.status)">{{ gear.statusName }}</el-tag>
            <span v-if="gear.status === 'RENTED'" class="gr-slot__holder">
              {{ gear.currentHolderName || '散客' }} · {{ gear.currentRentalNo }}
            </span>
          </div>
        </div>
      </div>
    </section>

    <!-- 租借流水 -->
    <section class="bl-panel">
      <div class="bl-panel__head">
        <h2>租借流水</h2>
        <span>共 {{ rentals.length }} 条 · 在租 {{ rentingCount }} 条</span>
      </div>
      <el-table :data="rentals" size="small" height="320">
        <el-table-column prop="rentalNo" label="租借单" width="130" />
        <el-table-column prop="gearNo" label="器材" width="90" />
        <el-table-column prop="gearName" label="名称" width="150" />
        <el-table-column label="借用人" width="100">
          <template #default="{ row }">{{ row.memberName || '散客' }}</template>
        </el-table-column>
        <el-table-column prop="statusName" label="状态" width="100" />
        <el-table-column label="押金" width="90">
          <template #default="{ row }">{{ money(row.deposit) }}</template>
        </el-table-column>
        <el-table-column label="赔偿" width="90">
          <template #default="{ row }">{{ row.damageFee === null ? '—' : money(row.damageFee) }}</template>
        </el-table-column>
        <el-table-column label="损坏说明" min-width="160">
          <template #default="{ row }">{{ row.damageNote || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button
              size="small"
              :disabled="row.status !== 'RENTED'"
              @click="openReturn(row)"
            >
              {{ row.status === 'RENTED' ? '归还' : '已结清' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 器材格位对话框：按状态换内容 -->
    <el-dialog v-model="gearVisible" :title="gearTitle" width="500px" append-to-body>
      <div v-if="activeGear" class="gr-dialog">
        <div class="bl-kv"><span>器材编号</span><b>{{ activeGear.gearNo }}</b></div>
        <div class="bl-kv"><span>名称 / 类型</span><b>{{ activeGear.gearName }} · {{ activeGear.gearTypeName }}</b></div>
        <div class="bl-kv"><span>原价</span><b>¥{{ money(activeGear.price) }}</b></div>
        <div class="bl-kv"><span>状态</span><b>{{ activeGear.statusName }}</b></div>
        <div class="bl-kv"><span>累计被借</span><b>{{ activeGear.rentCount }} 次</b></div>
        <div v-if="activeGear.currentRentalNo" class="bl-kv">
          <span>当前租借单</span><b>{{ activeGear.currentRentalNo }} · {{ activeGear.currentHolderName || '散客' }}</b>
        </div>

        <el-form v-if="activeGear.status === 'AVAILABLE'" label-width="86px" style="margin-top: 12px">
          <el-form-item label="借给人">
            <el-select v-model="rentForm.memberId" clearable placeholder="散客（不挂会员卡）" style="width: 100%">
              <el-option :value="null" label="散客（不挂会员卡）" />
              <el-option
                v-for="member in members"
                :key="member.id"
                :value="member.id"
                :label="`${member.cardNo} ${member.name}${member.status === 'FROZEN' ? ' · 已冻结' : ''}`"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="押金">
            <el-input-number v-model="rentForm.deposit" :min="0" :step="10" :precision="2" />
            <span class="bl-muted" style="margin-left: 8px">留空则默认取器材原价</span>
          </el-form-item>
        </el-form>

        <p v-else-if="activeGear.status === 'RENTED'" class="gr-hint">
          这件器材已借出未还，按规则不能再借一次；归还后才回到在库。
        </p>
        <p v-else-if="activeGear.status === 'DAMAGED'" class="gr-hint">
          这件器材处于损坏状态，维修完成前不能借出。
        </p>
        <p v-else class="gr-hint">这件器材已报废，不能再借出。</p>
      </div>

      <template #footer>
        <el-button @click="gearVisible = false">关闭</el-button>
        <template v-if="activeGear && activeGear.status === 'AVAILABLE'">
          <el-button type="primary" :loading="busy" @click="submitRent">借出</el-button>
        </template>
        <template v-else-if="activeGear && activeGear.status === 'RENTED'">
          <el-button @click="tryRent">尝试再借一次（应被拒绝）</el-button>
          <el-button type="primary" :loading="busy" @click="openReturnOfGear">去归还</el-button>
        </template>
        <template v-else-if="activeGear && activeGear.status === 'DAMAGED'">
          <el-button @click="tryRent">尝试借出（应被拒绝）</el-button>
          <el-button type="primary" :loading="busy" @click="submitRepair">维修完成，回在库</el-button>
        </template>
        <template v-else-if="activeGear && activeGear.status === 'SCRAPPED'">
          <el-button @click="tryRent">尝试借出（应被拒绝）</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 归还对话框：正常归还 / 登记损坏赔偿 -->
    <el-dialog v-model="returnVisible" title="器材归还" width="500px" append-to-body>
      <div v-if="activeRental" class="gr-dialog">
        <div class="bl-kv"><span>租借单</span><b>{{ activeRental.rentalNo }}</b></div>
        <div class="bl-kv"><span>器材</span><b>{{ activeRental.gearNo }} {{ activeRental.gearName }}</b></div>
        <div class="bl-kv"><span>借用人</span><b>{{ activeRental.memberName || '散客' }}</b></div>
        <div class="bl-kv"><span>押金</span><b>¥{{ money(activeRental.deposit) }}</b></div>

        <el-form label-width="96px" style="margin-top: 12px">
          <el-form-item label="归还结果">
            <el-radio-group v-model="returnForm.damaged">
              <el-radio-button :value="false">完好归还</el-radio-button>
              <el-radio-button :value="true">损坏赔偿</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <template v-if="returnForm.damaged">
            <el-form-item label="赔偿金额">
              <el-input-number v-model="returnForm.damageFee" :min="0" :step="10" :precision="2" />
              <span class="bl-muted" style="margin-left: 8px">不得超过原价 ¥{{ money(activeRental.gearPrice) }}</span>
            </el-form-item>
            <el-form-item label="损坏说明">
              <el-input v-model="returnForm.damageNote" placeholder="例如：杆头开裂" />
            </el-form-item>
          </template>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="submitReturn">确认归还</el-button>
      </template>
    </el-dialog>

    <!-- 器材入库 -->
    <el-dialog v-model="createVisible" title="器材入库" width="460px" append-to-body>
      <el-form label-width="92px">
        <el-form-item label="器材编号"><el-input v-model="createForm.gearNo" placeholder="例如 Q-009" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="createForm.gearName" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="createForm.gearType" style="width: 100%">
            <el-option value="CUE" label="球杆" />
            <el-option value="GLOVE" label="手套" />
            <el-option value="BRIDGE" label="架杆" />
            <el-option value="BALL_SET" label="球组" />
          </el-select>
        </el-form-item>
        <el-form-item label="原价">
          <el-input-number v-model="createForm.price" :min="0" :step="20" :precision="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="submitCreateGear">入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { money } from '../utils/format'

const loading = ref(false)
const busy = ref(false)
const gears = ref([])
const rentals = ref([])
const members = ref([])

const gearVisible = ref(false)
const returnVisible = ref(false)
const createVisible = ref(false)
const activeGear = ref(null)
const activeRental = ref(null)

const rentForm = reactive({ memberId: null, deposit: null })
const returnForm = reactive({ damaged: false, damageFee: 0, damageNote: '' })
const createForm = reactive({ gearNo: '', gearName: '', gearType: 'CUE', price: 200 })

const availableCount = computed(() => gears.value.filter((gear) => gear.status === 'AVAILABLE').length)
const rentedCount = computed(() => gears.value.filter((gear) => gear.status === 'RENTED').length)
const damagedCount = computed(() => gears.value.filter((gear) => gear.status === 'DAMAGED').length)
const scrappedCount = computed(() => gears.value.filter((gear) => gear.status === 'SCRAPPED').length)
const rentingCount = computed(() => rentals.value.filter((rental) => rental.status === 'RENTED').length)

const gearTitle = computed(() => (activeGear.value ? `${activeGear.value.gearNo} · ${activeGear.value.statusName}` : '器材'))

function gearTag(status) {
  if (status === 'RENTED') {
    return 'warning'
  }
  if (status === 'AVAILABLE') {
    return 'success'
  }
  if (status === 'DAMAGED') {
    return 'danger'
  }
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const [gearList, rentalList, memberList] = await Promise.all([
      api.gears(),
      api.rentals(),
      api.members()
    ])
    gears.value = gearList
    rentals.value = rentalList
    members.value = memberList
    if (activeGear.value) {
      const fresh = gearList.find((gear) => gear.id === activeGear.value.id)
      if (fresh) {
        activeGear.value = fresh
      }
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function openGear(gear) {
  activeGear.value = gear
  rentForm.memberId = null
  rentForm.deposit = null
  gearVisible.value = true
}

async function submitRent() {
  busy.value = true
  try {
    const body = { gearId: activeGear.value.id, memberId: rentForm.memberId }
    if (rentForm.deposit !== null && rentForm.deposit !== undefined) {
      body.deposit = rentForm.deposit
    }
    const rental = await api.rent(body)
    ElMessage.success(`借出成功：${rental.rentalNo}`)
    gearVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

async function tryRent() {
  busy.value = true
  try {
    await api.rent({ gearId: activeGear.value.id, memberId: null })
    ElMessage.warning('居然借成功了？这与规则不符')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

async function submitRepair() {
  busy.value = true
  try {
    await api.repairGear(activeGear.value.id)
    ElMessage.success('维修完成，已回到在库')
    gearVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

function openReturn(rental) {
  activeRental.value = rental
  returnForm.damaged = false
  returnForm.damageFee = 0
  returnForm.damageNote = ''
  returnVisible.value = true
}

function openReturnOfGear() {
  const rental = rentals.value.find((item) => item.id === activeGear.value.currentRentalId)
  if (!rental) {
    ElMessage.warning('没找到这件器材的租借单')
    return
  }
  gearVisible.value = false
  openReturn(rental)
}

async function submitReturn() {
  busy.value = true
  try {
    const body = { damaged: returnForm.damaged }
    if (returnForm.damaged) {
      body.damageFee = returnForm.damageFee
      body.damageNote = returnForm.damageNote
    }
    const rental = await api.returnGear(activeRental.value.id, body)
    ElMessage.success(
      returnForm.damaged
        ? `已按损坏结清，赔偿 ¥${money(rental.damageFee)}`
        : `归还成功：${rental.rentalNo}`
    )
    returnVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

function openCreateGear() {
  createForm.gearNo = ''
  createForm.gearName = ''
  createForm.gearType = 'CUE'
  createForm.price = 200
  createVisible.value = true
}

async function submitCreateGear() {
  busy.value = true
  try {
    const gear = await api.createGear({ ...createForm })
    ElMessage.success(`器材 ${gear.gearNo} 已入库`)
    createVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.gr-root {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 挂杆架：格位是竖向长条，像挂在墙上的杆位 */
.gr-rack {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(178px, 1fr));
  gap: 10px;
}

.gr-slot {
  border: 1px solid #e2e4e8;
  border-radius: 10px;
  padding: 10px 12px;
  background: #ffffff;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 5px;
  border-top: 4px solid #cfd2d6;
  transition: transform 0.12s ease, box-shadow 0.12s ease;
}

.gr-slot:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 14px rgba(0, 0, 0, 0.08);
}

.gr-slot.is-available {
  border-top-color: #616161;
}

.gr-slot.is-rented {
  border-top-color: #c9a227;
  background: #fffdf6;
}

.gr-slot.is-damaged {
  border-top-color: #c45656;
  background: #fdf6f6;
}

.gr-slot.is-scrapped {
  border-top-color: #b0b0b0;
  background: #f7f7f7;
  color: #909399;
}

.gr-slot__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}

.gr-slot__no {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.8px;
}

.gr-slot__count {
  font-size: 11px;
  color: #909399;
}

.gr-slot__name {
  font-size: 13px;
  color: #303133;
}

.gr-slot__meta {
  font-size: 11px;
  color: #909399;
}

.gr-slot__foot {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  flex-wrap: wrap;
}

.gr-slot__holder {
  font-size: 11px;
  color: #909399;
}

.gr-dialog .bl-kv {
  font-size: 13px;
}

.gr-hint {
  margin: 12px 0 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f7f7f7;
  color: #606266;
  font-size: 12px;
  line-height: 1.7;
}
</style>
