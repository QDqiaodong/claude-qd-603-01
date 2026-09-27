<template>
  <div class="td-root">
    <!-- 顶部：赛事列表 + 建赛 -->
    <section class="bl-panel">
      <div class="bl-panel__head">
        <h2>双败淘汰赛事</h2>
        <span>共 {{ list.length }} 场 · 进行中 {{ liveCount }} 场 · 已完赛 {{ finishedCount }} 场</span>
      </div>
      <div class="bl-toolbar">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="openCreate">＋ 创建赛事</el-button>
        <el-select
          :model-value="activeId"
          placeholder="选择赛事查看括号"
          style="width: 320px"
          @change="selectTournament"
        >
          <el-option
            v-for="item in list"
            :key="item.id"
            :label="`${item.tournamentName}（${item.entrantCount} 人 · ${item.statusName}${item.championName ? ' · 冠军 ' + item.championName : ''}）`"
            :value="item.id"
          />
        </el-select>
        <span class="bl-muted">二败淘汰；总决赛败者组冠军首胜会自动加一场重置赛</span>
      </div>
    </section>

    <template v-if="detail">
      <!-- 赛事头信息 -->
      <section class="bl-panel">
        <div class="td-hero">
          <div>
            <div class="td-hero__title">
              {{ detail.tournamentName }}
              <el-tag :type="detail.status === 'FINISHED' ? 'success' : 'primary'" size="small">
                {{ detail.statusName }}
              </el-tag>
              <el-tag v-if="detail.resetCode" type="danger" size="small">已进入重置赛</el-tag>
            </div>
            <div class="td-hero__meta">
              <span>开赛 {{ detail.entrantCount }} 人</span>
              <span>括号规模 {{ detail.bracketSize }}</span>
              <span>轮空 {{ detail.byeCount }}（不计场数）</span>
              <span>创建人 {{ detail.createdBy }}</span>
              <span>当前版本 v{{ detail.currentVersion }}</span>
            </div>
          </div>
          <div v-if="detail.champion" class="td-champion">
            <div class="td-champion__crown">冠军</div>
            <div class="td-champion__name">{{ detail.champion.displayName }}</div>
            <div class="td-champion__sub">{{ detail.champion.entrantKindName }} · {{ detail.champion.seedNo }} 号种子</div>
          </div>
        </div>
      </section>

      <!-- 待上场快捷区 -->
      <section class="bl-panel">
        <div class="bl-panel__head">
          <h2>待上场 / 当前可确认（{{ detail.readyMatches.length }} 场）</h2>
          <span>双方已到齐、可直接确认赛果；其余场次继续等待上游</span>
        </div>
        <div v-if="detail.readyMatches.length === 0" class="bl-empty">
          {{ detail.status === 'FINISHED' ? '赛事已结束' : '当前没有双方都到齐的场次' }}
        </div>
        <div v-else class="td-ready">
          <div v-for="m in detail.readyMatches" :key="m.id" class="td-ready__item">
            <div class="td-ready__code">{{ m.matchCode }} · {{ m.roundTitle }}</div>
            <div class="td-ready__pair">
              <span>{{ m.slotA.resolvedEntrantName }}</span>
              <em>VS</em>
              <span>{{ m.slotB.resolvedEntrantName }}</span>
            </div>
            <el-button type="primary" size="small" @click="openConfirm(m)">确认赛果</el-button>
          </div>
        </div>
      </section>

      <!-- 完整括号视图 -->
      <section class="bl-panel">
        <div class="bl-panel__head">
          <h2>完整对阵括号</h2>
          <span>
            <i class="td-legend td-legend--wb"></i> 胜者组
            <i class="td-legend td-legend--lb"></i> 败者组
            <i class="td-legend td-legend--gf"></i> 总决赛/重置赛
            ｜ 每个格子都标明来源、去向、当前轮次与待上场人员
          </span>
        </div>
        <div class="td-bracket-scroll">
          <div class="td-bracket">
            <div v-for="round in detail.rounds" :key="round.roundKey" class="td-round">
              <div class="td-round__head">
                <span class="td-round__part" :class="'is-' + round.bracketPart.toLowerCase()">{{ round.bracketPartName }}</span>
                <span class="td-round__title">{{ round.title }}</span>
              </div>
              <div class="td-round__cells">
                <BracketMatch
                  v-for="code in round.matchCodes"
                  :key="code"
                  :match="matchByCode[code]"
                  @confirm="openConfirm"
                  @rollback="openRollback"
                  @correct="openCorrect"
                />
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 参赛者状态 -->
      <section class="bl-panel">
        <div class="bl-panel__head">
          <h2>参赛者（{{ detail.entrants.length }}）</h2>
          <span>退赛区分尚未出场 / 已一败 / 已出局三态，后续晋级关系可解释</span>
        </div>
        <el-table :data="detail.entrants" size="small">
          <el-table-column prop="seedNo" label="种子" width="60" />
          <el-table-column label="姓名" min-width="130">
            <template #default="{ row }">
              {{ row.displayName }}
              <el-tag size="small" :type="row.entrantKind === 'MEMBER' ? '' : 'info'" effect="plain">
                {{ row.entrantKindName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="败场" width="60">
            <template #default="{ row }">
              <el-tag :type="row.losses >= 2 ? 'danger' : row.losses === 1 ? 'warning' : 'success'" size="small">
                {{ row.losses }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="当前状态" width="150">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="progressTagType(row.progress)"
                :effect="row.withdrawn ? 'dark' : 'light'"
              >
                {{ row.progressName }}
              </el-tag>
              <div v-if="row.withdrawn" class="td-withdraw-reason">退赛：{{ row.withdrawReason }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260">
            <template #default="{ row }">
              <el-button
                v-if="canWithdraw(row)"
                size="small"
                :type="row.played ? 'warning' : 'info'"
                @click="openWithdraw(row)"
              >
                {{ row.played ? '登记退赛（已出场）' : '登记退赛（未出场）' }}
              </el-button>
              <span v-else class="bl-muted">
                {{ row.progress === 'CHAMPION' ? '已夺冠' : row.progress === 'OUT' ? '已二败出局' : '赛事已结束' }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <!-- 纠错流水 -->
      <section class="bl-panel">
        <div class="bl-panel__head">
          <h2>版本与纠错流水（{{ detail.revisions.length }}）</h2>
          <span>版本号、操作人、原因、锚点场次与受影响场次全部留痕，刷新后一致</span>
        </div>
        <el-timeline v-if="detail.revisions.length">
          <el-timeline-item
            v-for="rev in detail.revisions"
            :key="rev.id"
            :timestamp="formatFull(rev.createdAt)"
            :type="revType(rev.changeType)"
          >
            <div class="td-rev">
              <b>v{{ rev.versionNo }} · {{ rev.changeTypeName }}</b>
              <span>操作人：{{ rev.operatorName }}</span>
              <span v-if="rev.anchorMatchCode">锚点：{{ rev.anchorMatchCode }}</span>
              <span v-if="rev.reason">原因：{{ rev.reason }}</span>
            </div>
            <div class="td-rev__codes">
              受影响场次：
              <el-tag v-for="code in rev.affectedMatchCodes" :key="code" size="small" class="td-rev__tag">
                {{ code }}
              </el-tag>
            </div>
          </el-timeline-item>
        </el-timeline>
        <div v-else class="bl-empty">还没有任何操作流水</div>
      </section>
    </template>

    <!-- 创建赛事 -->
    <el-dialog v-model="createVisible" title="创建双败淘汰赛事" width="720px" append-to-body>
      <el-form label-width="92px">
        <el-form-item label="赛事名称" required>
          <el-input v-model="createForm.name" placeholder="例如：国庆双败公开赛" />
        </el-form-item>
        <el-form-item label="操作店员">
          <el-input v-model="createForm.operator" placeholder="店员姓名；回退纠错请用含「管理员」的名字" />
        </el-form-item>
        <el-form-item label="选择参赛者">
          <div class="td-create">
            <div class="td-create__col">
              <div class="td-create__col-head">
                <span>会员（点选加入，按点选顺序排种子）</span>
              </div>
              <el-checkbox-group v-model="createForm.memberIds">
                <div v-for="m in members" :key="m.id" class="td-create__member">
                  <el-checkbox :value="m.id">
                    {{ m.cardNo }} · {{ m.name }}
                  </el-checkbox>
                </div>
              </el-checkbox-group>
            </div>
            <div class="td-create__col">
              <div class="td-create__col-head">
                <span>临时参赛者（登记姓名后加入）</span>
              </div>
              <div v-for="(walk, idx) in createForm.walkIns" :key="idx" class="td-create__walk">
                <el-input v-model="walk.name" placeholder="散客姓名" size="small" />
                <el-button link type="danger" size="small" @click="removeWalkIn(idx)">移除</el-button>
              </div>
              <el-button size="small" @click="addWalkIn">＋ 登记散客</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="种子顺序">
          <span class="bl-muted">
            已选 {{ selectedEntrantCount }} 人（括号规模 {{ plannedBracket }}，自动轮空 {{ plannedByes }} 个，高种子占轮空位）。
            种子顺序 = 会员按会员卡号升序 + 散客按登记顺序，可在下方调整：
          </span>
        </el-form-item>
        <el-form-item v-if="seedPreview.length" label="种子预览">
          <div class="td-seeds">
            <el-tag
              v-for="(s, i) in seedPreview"
              :key="i"
              class="td-seeds__tag"
              :type="s.kind === 'MEMBER' ? '' : 'info'"
            >
              {{ i + 1 }}. {{ s.name }}
            </el-tag>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="submitCreate">生成完整括号并创建</el-button>
      </template>
    </el-dialog>

    <!-- 确认赛果 -->
    <el-dialog v-model="confirmVisible" :title="`确认赛果 · ${confirmMatch?.matchCode}`" width="460px" append-to-body>
      <p class="bl-muted" v-if="confirmMatch">{{ confirmMatch.roundTitle }}｜胜方将进入：{{ confirmMatch.nextWinText || '产生冠军' }}</p>
      <el-radio-group v-model="confirmWinnerId" class="td-confirm-group">
        <el-radio
          v-for="slot in confirmSlots"
          :key="slot.resolvedEntrantId"
          :value="slot.resolvedEntrantId"
          class="td-confirm-radio"
          border
        >
          {{ slot.resolvedEntrantName }}
          <span class="bl-muted">（{{ slot.sourceText }}）</span>
        </el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="submitConfirm">确认并推进括号</el-button>
      </template>
    </el-dialog>

    <!-- 退赛 -->
    <el-dialog v-model="withdrawVisible" title="登记中途退赛" width="480px" append-to-body>
      <el-alert
        :title="withdrawAlert"
        :type="withdrawEntrant?.played ? 'warning' : 'info'"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />
      <el-input v-model="withdrawReason" type="textarea" :rows="2" placeholder="退赛原因（必填，用于解释后续晋级关系）" />
      <template #footer>
        <el-button @click="withdrawVisible = false">取消</el-button>
        <el-button type="warning" :loading="busy" @click="submitWithdraw">确认退赛并自动判罚</el-button>
      </template>
    </el-dialog>

    <!-- 管理员回退 -->
    <el-dialog v-model="rollbackVisible" :title="`受控回退 · ${rollbackMatch?.matchCode}`" width="560px" append-to-body>
      <el-alert
        title="将回退该场及其全部派生对阵（含重置赛），已经完成但不受影响的另一分支原样保留。"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 10px"
      />
      <p class="bl-muted" v-if="rollbackMatch">
        当前结果：{{ nameOf(rollbackMatch.winnerEntrantId) }} 胜 / {{ nameOf(rollbackMatch.loserEntrantId) }} 负（{{ rollbackMatch.winKindName }}）
      </p>
      <el-form label-width="84px" style="margin-top: 8px">
        <el-form-item label="管理员" required>
          <el-input v-model="adminName" placeholder="操作名需包含「管理员」，例如：管理员张姐" />
        </el-form-item>
        <el-form-item label="回退原因" required>
          <el-input v-model="rollbackReason" type="textarea" :rows="2" placeholder="例如：记分员把胜负记反 / 录像确认改判" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rollbackVisible = false">取消</el-button>
        <el-button type="warning" :loading="busy" @click="submitRollback">回退该场及派生对阵</el-button>
      </template>
    </el-dialog>

    <!-- 管理员更正结果（回退点） -->
    <el-dialog v-model="correctVisible" :title="`管理员更正结果 · ${correctMatch?.matchCode}`" width="500px" append-to-body>
      <el-alert
        title="这是回退后的更正入口：将按所选胜方确定性重建后续胜者组、败者组与重置赛；不受影响的另一分支原样保留。"
        type="success"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />
      <el-radio-group v-model="correctWinnerId" class="td-confirm-group">
        <el-radio
          v-for="slot in correctSlots"
          :key="slot.resolvedEntrantId"
          :value="slot.resolvedEntrantId"
          class="td-confirm-radio"
          border
        >
          {{ slot.resolvedEntrantName }}
          <span class="bl-muted">（{{ slot.sourceText }}）</span>
        </el-radio>
      </el-radio-group>
      <el-form label-width="84px" style="margin-top: 12px">
        <el-form-item label="管理员" required>
          <el-input v-model="correctAdminName" placeholder="操作名需包含「管理员」" />
        </el-form-item>
        <el-form-item label="更正原因" required>
          <el-input v-model="correctReason" type="textarea" :rows="2" placeholder="例如：现场录像确认改判" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="correctVisible = false">取消</el-button>
        <el-button type="success" :loading="busy" @click="submitCorrect">按更正结果重建括号</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { formatFull } from '../utils/format'
import BracketMatch from './tournament/BracketMatch.vue'
const loading = ref(false)
const busy = ref(false)
const list = ref([])
const detail = ref(null)
const activeId = ref(null)
const members = ref([])

const matchByCode = computed(() => {
  const map = {}
  if (detail.value) {
    for (const m of detail.value.matches) map[m.matchCode] = m
  }
  return map
})

const liveCount = computed(() => list.value.filter((x) => x.status === 'LIVE').length)
const finishedCount = computed(() => list.value.filter((x) => x.status === 'FINISHED').length)

async function load(selectLatest = false) {
  loading.value = true
  try {
    list.value = await api.tournaments()
    if (selectLatest && list.value.length) {
      activeId.value = list.value[0].id
    }
    if (activeId.value) {
      await loadDetail(activeId.value)
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function selectTournament(id) {
  activeId.value = id
  await loadDetail(id)
}

async function loadDetail(id) {
  try {
    detail.value = await api.tournament(id)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function nameOf(entrantId) {
  if (!entrantId || !detail.value) return '—'
  const e = detail.value.entrants.find((x) => x.id === entrantId)
  return e ? e.displayName : '—'
}

function progressTagType(progress) {
  return {
    CHAMPION: 'success',
    OUT: 'danger',
    LOSERS: 'warning',
    WINNERS: 'primary',
    WITHDRAWN_IDLE: 'info'
  }[progress] || 'info'
}

function revType(type) {
  return {
    RESULT: 'primary',
    WALKOVER: 'warning',
    ROLLBACK: 'danger',
    CORRECT: 'success'
  }[type] || ''
}

function canWithdraw(row) {
  if (detail.value.status === 'FINISHED') return false
  return row.progress === 'WINNERS' || row.progress === 'LOSERS'
}

// ---------------- 创建赛事 ----------------
const createVisible = ref(false)
const createForm = reactive({
  name: '',
  operator: '店员小王',
  memberIds: [],
  walkIns: []
})

async function openCreate() {
  try {
    members.value = await api.members()
  } catch (error) {
    members.value = []
  }
  createForm.name = ''
  createForm.memberIds = []
  createForm.walkIns = []
  createVisible.value = true
}

function addWalkIn() {
  createForm.walkIns.push({ name: '' })
}
function removeWalkIn(idx) {
  createForm.walkIns.splice(idx, 1)
}

const seedPreview = computed(() => {
  const selectedMembers = members.value
    .filter((m) => createForm.memberIds.includes(m.id))
    .map((m) => ({ name: m.name, kind: 'MEMBER' }))
  const walks = createForm.walkIns
    .filter((w) => w.name && w.name.trim())
    .map((w) => ({ name: w.name.trim(), kind: 'WALK_IN' }))
  return [...selectedMembers, ...walks]
})
const selectedEntrantCount = computed(() => seedPreview.value.length)
const plannedBracket = computed(() => {
  const n = selectedEntrantCount.value
  if (n < 2) return '—'
  let size = 2
  while (size < n) size *= 2
  return size > 32 ? '超过 32 人' : size
})
const plannedByes = computed(() =>
  typeof plannedBracket.value === 'number' ? plannedBracket.value - selectedEntrantCount.value : '—'
)

async function submitCreate() {
  if (!createForm.name.trim()) return ElMessage.error('请填写赛事名称')
  if (selectedEntrantCount.value < 4) return ElMessage.error('双败淘汰赛事至少需要 4 名参赛者')
  if (selectedEntrantCount.value > 32) return ElMessage.error('参赛人数不能超过 32')
  const entrants = seedPreview.value.map((s) =>
    s.kind === 'MEMBER'
      ? { memberId: members.value.find((m) => m.name === s.name && createForm.memberIds.includes(m.id)).id }
      : { walkInName: s.name }
  )
  busy.value = true
  try {
    const created = await api.createTournament({
      tournamentName: createForm.name.trim(),
      operatorName: createForm.operator || '店员',
      entrants
    })
    ElMessage.success('赛事已创建，完整对阵已生成')
    createVisible.value = false
    await load()
    activeId.value = created.id
    await loadDetail(created.id)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

// ---------------- 确认赛果 ----------------
const confirmVisible = ref(false)
const confirmMatch = ref(null)
const confirmWinnerId = ref(null)

const confirmSlots = computed(() => {
  const target = confirmVisible.value ? confirmMatch.value : correctMatch.value
  if (!target) return []
  return [target.slotA, target.slotB].filter((s) => s.resolvedEntrantId)
})

function openConfirm(match) {
  confirmMatch.value = match
  confirmWinnerId.value = null
  confirmVisible.value = true
}

async function submitConfirm() {
  if (!confirmWinnerId.value) return ElMessage.error('请选择胜方')
  busy.value = true
  try {
    await api.confirmMatch(activeId.value, confirmMatch.value.id, {
      winnerEntrantId: confirmWinnerId.value,
      operatorName: createForm.operator || '店员'
    })
    ElMessage.success('赛果已确认，括号已推进')
    confirmVisible.value = false
    await loadDetail(activeId.value)
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

// ---------------- 退赛 ----------------
const withdrawVisible = ref(false)
const withdrawEntrant = ref(null)
const withdrawReason = ref('')

const withdrawAlert = computed(() => {
  if (!withdrawEntrant.value) return ''
  const e = withdrawEntrant.value
  if (!e.played) {
    return '该参赛者尚未出场：退赛后，他将参加的场次判对手不战而胜；尚未绑定的场次自动判其缺阵，不会凭空产生比赛。'
  }
  if (e.losses === 1) {
    return '该参赛者已一败、正在败者组：退赛后当前/后续场次判对手不战而胜，他按二败出局处理。'
  }
  return '该参赛者状态异常'
})

function openWithdraw(row) {
  withdrawEntrant.value = row
  withdrawReason.value = ''
  withdrawVisible.value = true
}

async function submitWithdraw() {
  if (!withdrawReason.value.trim()) return ElMessage.error('请填写退赛原因')
  busy.value = true
  try {
    await api.withdrawEntrant(activeId.value, withdrawEntrant.value.id, {
      reason: withdrawReason.value.trim(),
      operatorName: createForm.operator || '店员'
    })
    ElMessage.success('退赛已登记，弃权对阵已自动推进')
    withdrawVisible.value = false
    await loadDetail(activeId.value)
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

// ---------------- 管理员回退/纠错 ----------------
const rollbackVisible = ref(false)
const rollbackMatch = ref(null)
const rollbackReason = ref('')
const adminName = ref('管理员张姐')

function openRollback(match) {
  rollbackMatch.value = match
  rollbackReason.value = ''
  rollbackVisible.value = true
}

async function submitRollback() {
  if (!rollbackReason.value.trim()) return ElMessage.error('回退必须填写原因')
  try {
    await ElMessageBox.confirm(
      `确认回退「${rollbackMatch.value.matchCode}」及其全部派生对阵？`,
      '受控回退确认',
      { type: 'warning', confirmButtonText: '确认回退', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  busy.value = true
  try {
    const rolled = await api.rollbackTournament(activeId.value, {
      anchorMatchId: rollbackMatch.value.id,
      reason: rollbackReason.value.trim(),
      operatorName: adminName.value.trim()
    })
    ElMessage.success('已回退，等待更正后的结果')
    rollbackVisible.value = false
    detail.value = rolled
    // 回退后锚点变为 READY，直接引导店员在同一入口提交更正结果
    await promptCorrect(rollbackMatch.value.matchCode)
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

async function promptCorrect(code) {
  // 回退后锚点变为「待更正」，格子上会出现「管理员更正」按钮；这里只做提示
  await loadDetail(activeId.value)
  const anchor = detail.value.matches.find((m) => m.matchCode === code)
  if (anchor && anchor.canConfirm) {
    ElMessage.info(`「${code}」已回退，可点格子上的「管理员更正」提交更正结果，后续括号将确定性重建`)
  }
}

// ---------------- 管理员更正结果（回退点） ----------------
const correctVisible = ref(false)
const correctMatch = ref(null)
const correctWinnerId = ref(null)
const correctReason = ref('')
const correctAdminName = ref('管理员张姐')

function openCorrect(match) {
  correctMatch.value = match
  correctWinnerId.value = null
  correctReason.value = ''
  correctVisible.value = true
}

async function submitCorrect() {
  if (!correctWinnerId.value) return ElMessage.error('请选择更正后的胜方')
  if (!correctReason.value.trim()) return ElMessage.error('请填写更正原因')
  if (!correctAdminName.value.trim().includes('管理员')) {
    return ElMessage.error('更正属于受控操作，操作人必须为管理员（名字含「管理员」）')
  }
  busy.value = true
  try {
    const rebuilt = await api.correctMatch(activeId.value, correctMatch.value.id, {
      winnerEntrantId: correctWinnerId.value,
      reason: correctReason.value.trim(),
      operatorName: correctAdminName.value.trim()
    })
    ElMessage.success('结果已更正，后续括号按更正结果确定性重建')
    correctVisible.value = false
    detail.value = rebuilt
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

onMounted(() => load(true))
</script>

<style scoped>
.td-root {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.td-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.td-hero__title {
  font-size: 17px;
  font-weight: 700;
  color: #2f2f2f;
  display: flex;
  align-items: center;
  gap: 8px;
}
.td-hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 18px;
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
}
.td-champion {
  text-align: center;
  border: 2px solid #f5c542;
  border-radius: 14px;
  padding: 10px 22px;
  background: linear-gradient(150deg, #fff8e1, #ffecb3);
}
.td-champion__crown { font-size: 12px; color: #b8860b; letter-spacing: 4px; }
.td-champion__name { font-size: 20px; font-weight: 800; color: #8a6d00; }
.td-champion__sub { font-size: 11px; color: #b8860b; }

.td-ready {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 10px;
}
.td-ready__item {
  border: 1px solid #e8eaed;
  border-radius: 10px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.td-ready__code { font-size: 11px; color: #909399; font-weight: 700; }
.td-ready__pair {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
}
.td-ready__pair em { font-style: normal; color: #c0c4cc; font-size: 11px; }

.td-bracket-scroll {
  overflow-x: auto;
  padding-bottom: 8px;
}
.td-bracket {
  display: flex;
  gap: 26px;
  align-items: flex-start;
  min-width: max-content;
}
.td-round {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.td-round__head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  position: sticky;
  top: 0;
  background: #fff;
  padding-bottom: 4px;
  z-index: 2;
}
.td-round__part {
  font-size: 11px;
  font-weight: 700;
  padding: 1px 8px;
  border-radius: 6px;
  width: fit-content;
}
.td-round__part.is-wb { background: #ecf5ff; color: #409eff; }
.td-round__part.is-lb { background: #fdf6ec; color: #e6a23c; }
.td-round__part.is-gf { background: #fef0f0; color: #f56c6c; }
.td-round__title { font-size: 12px; color: #606266; font-weight: 600; }
.td-round__cells {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
/* 每一轮整体垂直居中，使后段列在视觉上落在前段两场之间 */
.td-round:nth-child(2) .td-round__cells { margin-top: 40px; }
.td-round:nth-child(3) .td-round__cells { margin-top: 80px; }

.td-legend {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  margin: 0 4px 0 10px;
}
.td-legend--wb { background: #409eff; margin-left: 0; }
.td-legend--lb { background: #e6a23c; }
.td-legend--gf { background: #f56c6c; }

.td-withdraw-reason { font-size: 11px; color: #909399; margin-top: 2px; }

.td-create {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  width: 100%;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 10px;
}
.td-create__col-head { font-size: 12px; color: #606266; margin-bottom: 8px; font-weight: 600; }
.td-create__member { padding: 2px 0; font-size: 13px; }
.td-create__walk { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.td-seeds { display: flex; flex-wrap: wrap; gap: 6px; }
.td-seeds__tag { margin: 0; }

.td-confirm-group { display: flex; flex-direction: column; gap: 10px; width: 100%; }
.td-confirm-radio { width: 100%; height: auto; padding: 10px 12px; }

.td-rev {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  font-size: 13px;
}
.td-rev__codes { margin-top: 4px; font-size: 12px; color: #606266; }
.td-rev__tag { margin: 2px 4px 2px 0; }
</style>
