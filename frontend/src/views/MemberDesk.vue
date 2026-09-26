<template>
  <div class="md-root">
    <section class="bl-panel">
      <div class="bl-panel__head">
        <h2>会员卡墙</h2>
        <span>共 {{ members.length }} 张卡 · 冻结 {{ frozenCount }} 张 · 累计进行中 {{ runningTotal }} 局</span>
      </div>
      <div class="bl-toolbar">
        <el-button :loading="loading" @click="load">刷新卡墙</el-button>
        <el-button type="primary" @click="openCreate">＋ 新增会员</el-button>
        <span class="bl-muted">卡面右下角的「进行中 N 局」来自后端只读计算列</span>
      </div>

      <div class="md-wall">
        <div
          v-for="member in members"
          :key="member.id"
          class="md-card"
          :class="{ 'is-frozen': member.status === 'FROZEN' }"
          @click="openMember(member)"
        >
          <div class="md-card__head">
            <span class="md-card__no">{{ member.cardNo }}</span>
            <span class="md-card__level">{{ member.levelName }}</span>
          </div>
          <div class="md-card__name">{{ member.name }}</div>
          <div class="md-card__balance">¥{{ money(member.balance) }}</div>
          <div class="md-card__foot">
            <span>折扣 {{ member.discount }}</span>
            <span>累计充值 ¥{{ money(member.totalRecharge) }}</span>
            <span>进行中 {{ member.activeOrderCount }} 局</span>
          </div>
          <span v-if="member.status === 'FROZEN'" class="md-card__stamp">冻结</span>
        </div>
      </div>
    </section>

    <!-- 全部充值流水 -->
    <section class="bl-panel">
      <div class="bl-panel__head">
        <h2>充值流水</h2>
        <span>共 {{ recharges.length }} 条 · 单笔区间 100 ~ 5000 元</span>
      </div>
      <el-table :data="recharges" size="small" height="300">
        <el-table-column prop="cardNo" label="卡号" width="100" />
        <el-table-column prop="memberName" label="姓名" width="100" />
        <el-table-column label="金额" width="100">
          <template #default="{ row }">¥{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="余额变化" width="170">
          <template #default="{ row }">{{ money(row.beforeBalance) }} → {{ money(row.afterBalance) }}</template>
        </el-table-column>
        <el-table-column label="等级变化" width="180">
          <template #default="{ row }">{{ row.beforeLevel }} → {{ row.afterLevel }}</template>
        </el-table-column>
        <el-table-column prop="payModeName" label="支付方式" />
      </el-table>
    </section>

    <!-- 会员卡对话框 -->
    <el-dialog v-model="visible" :title="memberTitle" width="540px" append-to-body>
      <div v-if="active">
        <div class="md-hero" :class="{ 'is-frozen': active.status === 'FROZEN' }">
          <div>
            <div class="md-hero__no">{{ active.cardNo }}</div>
            <div class="md-hero__name">{{ active.name }} · {{ active.levelName }}（折扣 {{ active.discount }}）</div>
          </div>
          <div class="md-hero__balance">¥{{ money(active.balance) }}</div>
        </div>

        <div class="md-kv-row">
          <div class="bl-kv"><span>累计充值</span><b>¥{{ money(active.totalRecharge) }}</b></div>
          <div class="bl-kv"><span>进行中的局</span><b>{{ active.activeOrderCount }} 局</b></div>
          <div class="bl-kv"><span>卡状态</span><b>{{ active.statusName }}</b></div>
          <div class="bl-kv"><span>入会日期</span><b>{{ formatDate(active.joinedAt) }}</b></div>
        </div>

        <!-- 充值 -->
        <div class="md-block">
          <h4>充值</h4>
          <div class="md-quick">
            <el-button
              v-for="amount in quickAmounts"
              :key="amount"
              size="small"
              :type="rechargeForm.amount === amount ? 'primary' : 'default'"
              @click="rechargeForm.amount = amount"
            >
              ¥{{ amount }}
            </el-button>
          </div>
          <div class="md-form-row">
            <el-input-number v-model="rechargeForm.amount" :min="0" :step="100" :precision="2" />
            <el-select v-model="rechargeForm.payMode" style="width: 140px">
              <el-option value="CASH" label="现金" />
              <el-option value="WECHAT" label="微信支付" />
              <el-option value="CARD" label="银行卡" />
              <el-option value="BALANCE" label="储值余额" />
            </el-select>
            <el-button type="primary" :loading="busy" @click="submitRecharge">充值</el-button>
          </div>
          <p class="bl-muted">累计充值跨过 3000 / 10000 / 20000 元会只升不降地升级；冻结卡充值会被拦住。</p>
        </div>

        <!-- 资料局部更新 -->
        <div class="md-block">
          <h4>改资料（只提交改动的字段）</h4>
          <div class="md-form-row">
            <el-input v-model="editForm.name" placeholder="姓名" style="width: 150px" />
            <el-input v-model="editForm.phone" placeholder="手机号" style="width: 170px" />
            <el-button :loading="busy" @click="submitEdit">保存</el-button>
          </div>
          <p class="bl-muted">
            余额和卡状态不在编辑里改：余额只能走充值或余额结算，状态只能走冻结 / 解冻。
          </p>
        </div>

        <!-- 充值流水 -->
        <div class="md-block">
          <h4>这张卡的充值流水</h4>
          <div v-if="memberRecharges.length === 0" class="bl-empty">还没有充值记录</div>
          <div v-for="item in memberRecharges" :key="item.id" class="md-flow-row">
            <span>¥{{ money(item.amount) }}</span>
            <span>{{ money(item.beforeBalance) }} → {{ money(item.afterBalance) }}</span>
            <span>{{ item.beforeLevel }} → {{ item.afterLevel }}</span>
            <span>{{ formatFull(item.createdAt) }}</span>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <template v-if="active && active.status === 'FROZEN'">
          <el-button type="primary" :loading="busy" @click="submitUnfreeze">解冻会员卡</el-button>
        </template>
        <template v-else-if="active">
          <el-button :loading="busy" @click="submitFreeze">冻结会员卡</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 新增会员 -->
    <el-dialog v-model="createVisible" title="新增会员" width="460px" append-to-body>
      <el-form label-width="86px">
        <el-form-item label="卡号"><el-input v-model="createForm.cardNo" placeholder="例如 M-008" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="createForm.name" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="createForm.phone" /></el-form-item>
      </el-form>
      <p class="bl-muted">新卡余额为 0、等级为普通会员，余额为 0 时开不了局，需要先充值。</p>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="submitCreate">建档</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { formatDate, formatFull, money } from '../utils/format'

const loading = ref(false)
const busy = ref(false)
const members = ref([])
const recharges = ref([])
const memberRecharges = ref([])

const visible = ref(false)
const createVisible = ref(false)
const active = ref(null)

const quickAmounts = [100, 500, 1000, 3000]
const rechargeForm = reactive({ amount: 500, payMode: 'WECHAT' })
const editForm = reactive({ name: '', phone: '' })
const createForm = reactive({ cardNo: '', name: '', phone: '' })

const frozenCount = computed(() => members.value.filter((member) => member.status === 'FROZEN').length)
const runningTotal = computed(() => members.value.reduce((sum, member) => sum + Number(member.activeOrderCount || 0), 0))
const memberTitle = computed(() => (active.value ? `${active.value.cardNo} · ${active.value.name}` : '会员卡'))

async function load() {
  loading.value = true
  try {
    const [memberList, rechargeList] = await Promise.all([api.members(), api.allRecharges()])
    members.value = memberList
    recharges.value = rechargeList
    if (active.value) {
      const fresh = memberList.find((member) => member.id === active.value.id)
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

async function openMember(member) {
  active.value = member
  rechargeForm.amount = 500
  rechargeForm.payMode = 'WECHAT'
  editForm.name = member.name
  editForm.phone = member.phone || ''
  visible.value = true
  try {
    memberRecharges.value = await api.memberRecharges(member.id)
  } catch (error) {
    memberRecharges.value = []
  }
}

async function submitRecharge() {
  busy.value = true
  try {
    const record = await api.recharge(active.value.id, {
      amount: rechargeForm.amount,
      payMode: rechargeForm.payMode
    })
    ElMessage.success(
      record.beforeLevel === record.afterLevel
        ? `充值成功：余额 ${money(record.afterBalance)} 元`
        : `充值成功，等级升到 ${record.afterLevel}`
    )
    memberRecharges.value = await api.memberRecharges(active.value.id)
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

async function submitEdit() {
  busy.value = true
  try {
    await api.updateMember(active.value.id, { name: editForm.name, phone: editForm.phone })
    ElMessage.success('会员资料已更新')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

async function submitFreeze() {
  busy.value = true
  try {
    await api.freezeMember(active.value.id)
    ElMessage.success('会员卡已冻结')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

async function submitUnfreeze() {
  busy.value = true
  try {
    await api.unfreezeMember(active.value.id)
    ElMessage.success('会员卡已解冻')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

function openCreate() {
  createForm.cardNo = ''
  createForm.name = ''
  createForm.phone = ''
  createVisible.value = true
}

async function submitCreate() {
  busy.value = true
  try {
    const member = await api.createMember({ ...createForm })
    ElMessage.success(`会员 ${member.cardNo} 已建档`)
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
.md-root {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.md-wall {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(238px, 1fr));
  gap: 12px;
}

/* 会员卡：照着实体卡的样子排，不是列表行 */
.md-card {
  position: relative;
  border-radius: 14px;
  padding: 12px 14px 10px;
  background: linear-gradient(150deg, #6e6e6e 0%, #4e4e4e 100%);
  color: #ffffff;
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.12s ease, box-shadow 0.12s ease;
}

.md-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.18);
}

.md-card.is-frozen {
  background: linear-gradient(150deg, #9a9a9a 0%, #7d7d7d 100%);
}

.md-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.md-card__no {
  font-size: 13px;
  letter-spacing: 1.4px;
  opacity: 0.92;
}

.md-card__level {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.2);
}

.md-card__name {
  margin-top: 8px;
  font-size: 15px;
  letter-spacing: 1px;
}

.md-card__balance {
  margin-top: 4px;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.6px;
}

.md-card__foot {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 8px;
  font-size: 11px;
  opacity: 0.85;
}

.md-card__stamp {
  position: absolute;
  right: -18px;
  top: 14px;
  transform: rotate(38deg);
  background: #ffffff;
  color: #616161;
  font-size: 11px;
  padding: 2px 22px;
  letter-spacing: 2px;
}

.md-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-radius: 12px;
  padding: 12px 14px;
  background: linear-gradient(150deg, #6e6e6e 0%, #4e4e4e 100%);
  color: #ffffff;
}

.md-hero.is-frozen {
  background: linear-gradient(150deg, #9a9a9a 0%, #7d7d7d 100%);
}

.md-hero__no {
  font-size: 14px;
  letter-spacing: 1.4px;
}

.md-hero__name {
  font-size: 12px;
  opacity: 0.9;
  margin-top: 3px;
}

.md-hero__balance {
  font-size: 22px;
  font-weight: 700;
}

.md-kv-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 2px 18px;
  margin-top: 12px;
}

.md-block {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed #e8eaed;
}

.md-block h4 {
  margin: 0 0 8px;
  font-size: 13px;
  color: #2f2f2f;
}

.md-quick {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.md-form-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.md-flow-row {
  display: grid;
  grid-template-columns: 80px 1.2fr 1.2fr 1fr;
  gap: 6px;
  font-size: 12px;
  color: #606266;
  padding: 4px 0;
  border-bottom: 1px dashed #f2f3f5;
}
</style>
