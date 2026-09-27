<template>
  <div class="ta-root">
    <!-- 顶部：赛事列表 -->
    <section class="bl-panel">
      <div class="bl-panel__head">
        <h2>双败淘汰赛事</h2>
        <span>共 {{ tournaments.length }} 场 · 两败出局 · 总决赛必要时打重置赛</span>
      </div>
      <div class="bl-toolbar">
        <el-button :loading="loading" @click="loadTournaments">刷新</el-button>
        <el-button type="primary" @click="openCreate">＋ 创建赛事</el-button>
        <el-button @click="loadTournament(current.id)" :disabled="!current">重新加载括号</el-button>
        <el-button @click="openRevisions" :disabled="!current">纠错记录（版本审计）</el-button>
        <span class="bl-muted">演示赛事 T-DEMO-01 可直接看「胜者落入败者组、败者再被淘汰」；T-DEMO-02 已打完含重置赛</span>
      </div>

      <el-table :data="tournaments" size="small" @row-click="(row) => loadTournament(row.id)" highlight-current-row>
        <el-table-column prop="tournamentNo" label="编号" width="130" />
        <el-table-column prop="name" label="赛事名称" min-width="220" />
        <el-table-column label="人数" width="90">
          <template #default="{ row }">{{ row.playerCount }} / {{ row.bracketSize }} 号位</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'FINISHED' ? 'success' : 'warning'" size="small">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="championName" label="冠军" width="120">
          <template #default="{ row }">{{ row.championName || '—' }}</template>
        </el-table-column>
      </el-table>
    </section>

    <template v-if="current">
      <!-- 赛事概况 -->
      <section class="bl-panel ta-summary">
        <div class="ta-summary__main">
          <h2>{{ current.name }} <span class="ta-summary__no">{{ current.tournamentNo }}</span></h2>
          <div class="ta-summary__kv">
            <div class="bl-kv"><span>开赛人数</span><b>{{ current.playerCount }} 人</b></div>
            <div class="bl-kv"><span>括号容量</span><b>{{ current.bracketSize }} 号位</b></div>
            <div class="bl-kv"><span>首轮轮空</span><b>{{ current.byeCount }} 个（不算比赛）</b></div>
            <div class="bl-kv"><span>状态</span><b>{{ current.statusName }}</b></div>
            <div class="bl-kv"><span>冠军</span><b class="ta-champion">{{ current.championName || '尚未产生' }}</b></div>
          </div>
        </div>
        <div class="ta-legend">
          <span><i class="dot dot-ready"></i>待开赛（双方到齐）</span>
          <span><i class="dot dot-pending"></i>等待入位</span>
          <span><i class="dot dot-finished"></i>已结束</span>
          <span><i class="dot dot-bye"></i>轮空·非比赛</span>
          <span><i class="dot dot-void"></i>双方退赛作废</span>
        </div>
      </section>

      <!-- 参赛者 -->
      <section class="bl-panel">
        <div class="bl-panel__head">
          <h2>参赛者（种子顺序）</h2>
          <span>在胜者组 0 败 · 在败者组 1 败 · 出局 2 败</span>
        </div>
        <div class="ta-entries">
          <div
            v-for="entry in current.entries"
            :key="entry.id"
            class="ta-entry"
            :class="{
              'is-out': entry.status === 'ELIMINATED',
              'is-wd': entry.status === 'WITHDRAWN',
              'is-loss1': entry.status === 'ACTIVE' && entry.lossCount === 1
            }"
          >
            <span class="ta-entry__seed">{{ entry.seedNo }}</span>
            <div class="ta-entry__body">
              <div class="ta-entry__name">
                {{ entry.displayName }}
                <el-tag size="small" :type="entry.entryType === 'MEMBER' ? '' : 'info'">
                  {{ entry.entryType === 'MEMBER' ? entry.cardNo : '临时' }}
                </el-tag>
              </div>
              <div class="ta-entry__meta">
                {{ entry.status === 'WITHDRAWN'
                  ? `退赛（${entry.lossCount === 0 ? '尚未出场' : '已有一败'}）`
                  : entry.status === 'ELIMINATED'
                    ? '两败出局'
                    : entry.lossCount === 0 ? '胜者组 · 0 败' : '败者组 · 1 败' }}
              </div>
              <div v-if="entry.withdrawNote" class="ta-entry__note">{{ entry.withdrawNote }}</div>
            </div>
            <el-button
              v-if="entry.status === 'ACTIVE' && current.status === 'RUNNING'"
              size="small"
              plain
              type="danger"
              @click="openWithdraw(entry)"
            >登记退赛</el-button>
          </div>
        </div>
      </section>

      <!-- 括号视图 -->
      <section class="bl-panel">
        <div class="bl-panel__head">
          <h2>完整对阵括号</h2>
          <span>卡片标注来源与去向；横向滚动查看全部轮次</span>
        </div>

        <div class="ta-bracket-scroll">
          <div class="ta-bracket">
            <div class="ta-col ta-col--w">
              <div class="ta-col__title">胜者组</div>
              <div
                v-for="(col, rIdx) in winnersColumns"
                :key="'w' + rIdx"
                class="ta-round"
                :class="`ta-round--w${rIdx + 1}`"
              >
                <div class="ta-round__title">{{ winnerRoundLabel(rIdx) }}</div>
                <div class="ta-round__cards">
                  <BracketCard
                    v-for="m in col"
                    :key="m.id"
                    :match="m"
                    :entries="current.entries"
                    @confirm="openConfirm"
                    @rollback="openRollback"
                  />
                </div>
              </div>
            </div>

            <div class="ta-col ta-col--l">
              <div class="ta-col__title">败者组（第一败落入，第二败出局）</div>
              <div class="ta-col__l-rounds">
                <div
                  v-for="(col, rIdx) in losersColumns"
                  :key="'l' + rIdx"
                  class="ta-round"
                  :class="`ta-round--l${rIdx + 1}`"
                >
                  <div class="ta-round__title">{{ loserRoundLabel(rIdx) }}</div>
                  <div class="ta-round__cards">
                    <BracketCard
                      v-for="m in col"
                      :key="m.id"
                      :match="m"
                      :entries="current.entries"
                      @confirm="openConfirm"
                      @rollback="openRollback"
                    />
                  </div>
                </div>
              </div>
            </div>

            <div class="ta-col ta-col--g">
              <div class="ta-col__title">总决赛</div>
              <div class="ta-round">
                <div class="ta-round__title">&nbsp;</div>
                <div class="ta-round__cards">
                  <BracketCard
                    v-for="m in grandColumn"
                    :key="m.id"
                    :match="m"
                    :entries="current.entries"
                    @confirm="openConfirm"
                    @rollback="openRollback"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 待办比赛列表 -->
      <section class="bl-panel">
        <div class="bl-panel__head">
          <h2>待上场 / 全部比赛</h2>
          <span>已结束比赛的赛果不能直接改，用卡片上的「回退纠错」</span>
        </div>
        <el-table :data="matchTableData" size="small" height="360">
          <el-table-column prop="matchNo" label="场次" width="90" />
          <el-table-column prop="stageName" label="所在组" width="90" />
          <el-table-column label="轮次" width="70">
            <template #default="{ row }">{{ row.roundNo }}</template>
          </el-table-column>
          <el-table-column label="A 侧（来源）" min-width="220">
            <template #default="{ row }">
              <b>{{ row.sideAName || '待定' }}</b>
              <span class="ta-src">（{{ row.sideASource }}）</span>
            </template>
          </el-table-column>
          <el-table-column label="B 侧（来源）" min-width="220">
            <template #default="{ row }">
              <b>{{ row.sideBName || '待定' }}</b>
              <span class="ta-src">（{{ row.sideBSource }}）</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="150">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row)">{{ row.statusName }}</el-tag>
              <div v-if="!row.realMatch && row.status === 'FINISHED'" class="ta-src">非真实比赛</div>
            </template>
          </el-table-column>
          <el-table-column label="胜者 / 去向" width="150">
            <template #default="{ row }">
              <div>{{ row.winnerName || '—' }}</div>
              <div class="ta-src">→ {{ row.winnerNextNo || '终点' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'READY'"
                size="small"
                type="primary"
                @click="openConfirm(row)"
              >确认赛果</el-button>
              <el-button
                v-if="row.status === 'FINISHED' && row.realMatch"
                size="small"
                type="warning"
                plain
                @click="openRollback(row)"
              >回退纠错</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </template>

    <!-- 创建赛事 -->
    <el-dialog v-model="createVisible" title="创建双败淘汰赛事" width="680px" append-to-body>
      <el-form label-width="92px">
        <el-form-item label="赛事名称">
          <el-input v-model="createForm.name" placeholder="例如：国庆双败公开赛" maxlength="128" />
        </el-form-item>
        <el-form-item label="参赛者">
          <div class="ta-create-list">
            <div v-for="(item, idx) in createForm.entries" :key="idx" class="ta-create-row">
              <span class="ta-create-seed">种子 {{ idx + 1 }}</span>
              <el-select
                v-model="item.memberId"
                placeholder="选择会员（或留空登记临时参赛者）"
                clearable
                filterable
                style="width: 260px"
                @change="item.guestName = ''"
              >
                <el-option
                  v-for="m in members"
                  :key="m.id"
                  :value="m.id"
                  :label="`${m.cardNo} · ${m.name}`"
                />
              </el-select>
              <el-input
                v-if="!item.memberId"
                v-model="item.guestName"
                placeholder="临时参赛者姓名"
                style="width: 200px"
                maxlength="64"
              />
              <el-button
                v-if="!item.memberId && !item.guestName"
                link
                type="danger"
                @click="removeEntry(idx)"
              >移除</el-button>
            </div>
            <el-button size="small" @click="addEntry" :disabled="createForm.entries.length >= 64">
              ＋ 增加一位参赛者
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <p class="bl-muted">
        人数 {{ createForm.entries.length }}，括号容量 {{ createBracketSize }}，
        首轮轮空 {{ Math.max(0, createBracketSize - createForm.entries.length) }} 个；
        人数 4 ~ 64，数组顺序即种子顺序，轮空自动给高种子。
      </p>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="submitCreate">创建并生成括号</el-button>
      </template>
    </el-dialog>

    <!-- 确认赛果 -->
    <el-dialog v-model="confirmVisible" title="确认赛果" width="440px" append-to-body>
      <div v-if="activeMatch">
        <p class="bl-muted">
          {{ activeMatch.stageName }}第 {{ activeMatch.roundNo }} 轮 · {{ activeMatch.matchNo }}
          ｜ 胜者晋级 {{ activeMatch.winnerNextNo || '终点' }}
          <template v-if="activeMatch.loserNextNo">，败者落入 {{ activeMatch.loserNextNo }}</template>
          <template v-else-if="activeMatch.stage === 'WINNERS' || activeMatch.stage === 'LOSERS'">，败者第二败出局</template>
        </p>
        <el-radio-group v-model="confirmForm.winnerId" class="ta-winner-radio">
          <el-radio :value="activeMatch.sideAEntryId" class="ta-winner-option">
            {{ activeMatch.sideAName }}
          </el-radio>
          <el-radio :value="activeMatch.sideBEntryId" class="ta-winner-option">
            {{ activeMatch.sideBName }}
          </el-radio>
        </el-radio-group>
        <el-input v-model="confirmForm.operator" placeholder="操作人（店员）" style="margin-top: 10px" />
        <el-input
          v-model="confirmForm.note"
          placeholder="备注（可选）"
          style="margin-top: 10px"
          type="textarea"
          :rows="2"
        />
      </div>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="submitConfirm">确认并推进括号</el-button>
      </template>
    </el-dialog>

    <!-- 退赛 -->
    <el-dialog v-model="withdrawVisible" title="中途退赛登记" width="460px" append-to-body>
      <div v-if="activeEntry">
        <p>
          参赛者：<b>{{ activeEntry.displayName }}</b>（种子 {{ activeEntry.seedNo }}）
        </p>
        <el-alert
          :title="activeEntry.lossCount === 0
            ? '该选手尚未出场：退赛后不折损败绩，已到齐场次的对手直接晋级。'
            : '该选手已有一败（在败者组）：退赛后不折成两败，按退赛记录，对手直接晋级。'"
          type="info"
          :closable="false"
          style="margin: 8px 0"
        />
        <el-input v-model="withdrawForm.operator" placeholder="操作人（必填）" />
        <el-input
          v-model="withdrawForm.note"
          placeholder="退赛原因（可选）"
          style="margin-top: 10px"
          type="textarea"
          :rows="2"
        />
      </div>
      <template #footer>
        <el-button @click="withdrawVisible = false">取消</el-button>
        <el-button type="danger" :loading="busy" @click="submitWithdraw">登记退赛</el-button>
      </template>
    </el-dialog>

    <!-- 回退纠错 -->
    <el-dialog v-model="rollbackVisible" title="受控回退纠错" width="560px" append-to-body>
      <div v-if="activeMatch">
        <el-alert type="warning" :closable="false" style="margin-bottom: 10px">
          将回退「{{ activeMatch.matchNo }}」及其全部派生对阵（下游胜/败路径），
          另一分支已完成的比赛原样保留；回退后按更正结果确定性重建。
        </el-alert>
        <p class="bl-muted">
          该场：{{ activeMatch.sideAName }} vs {{ activeMatch.sideBName }}，
          原胜者 <b>{{ activeMatch.winnerName }}</b>
        </p>
        <el-form label-width="92px">
          <el-form-item label="操作人" required>
            <el-input v-model="rollbackForm.operator" placeholder="管理员姓名（必填）" />
          </el-form-item>
          <el-form-item label="纠错原因" required>
            <el-input v-model="rollbackForm.reason" type="textarea" :rows="2" placeholder="必须说明原因" />
          </el-form-item>
          <el-form-item label="更正胜者">
            <el-select v-model="rollbackForm.correctedWinnerId" clearable placeholder="留空则只回退到待确认" style="width: 320px">
              <el-option :value="activeMatch.sideAEntryId" :label="activeMatch.sideAName" />
              <el-option :value="activeMatch.sideBEntryId" :label="activeMatch.sideBName" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="rollbackVisible = false">取消</el-button>
        <el-button type="warning" :loading="busy" @click="submitRollback">回退并重建</el-button>
      </template>
    </el-dialog>

    <!-- 纠错版本审计 -->
    <el-dialog v-model="revisionsVisible" title="纠错记录（版本可追溯）" width="760px" append-to-body>
      <div v-if="revisions.length === 0" class="bl-empty">还没有回退纠错记录</div>
      <el-collapse v-else v-model="activeRevisions">
        <el-collapse-item
          v-for="rev in revisions"
          :key="rev.id"
          :name="rev.id"
          :title="`#${rev.id} 从 ${rev.rootMatchNo} 回退 · ${rev.operator} · ${rev.createdAt}`"
        >
          <div class="bl-kv"><span>原因</span><b>{{ rev.reason }}</b></div>
          <div class="bl-kv">
            <span>更正胜者</span><b>{{ rev.correctedWinnerName || '仅回退，未立即更正' }}</b>
          </div>
          <el-table :data="rev.matches" size="small" style="margin-top: 8px">
            <el-table-column prop="matchNo" label="场次" width="90" />
            <el-table-column prop="stageName" label="所在组" width="90" />
            <el-table-column prop="changeTypeName" label="变更类型" width="140" />
            <el-table-column label="纠错前 / 后快照" min-width="320">
              <template #default="{ row }">
                <pre class="ta-json">{{ row.beforeJson }}</pre>
                <div class="ta-src">↓ 重建后</div>
                <pre class="ta-json">{{ row.afterJson || '（已删除）' }}</pre>
              </template>
            </el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, h, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElTag } from 'element-plus'
import api from '../api'

// ---- 内联括号卡片子组件（同文件定义，避免多建组件文件）----
const BracketCard = {
  props: {
    match: { type: Object, required: true },
    entries: { type: Array, required: true }
  },
  emits: ['confirm', 'rollback'],
  setup(props, { emit }) {
    const entryStatus = (id) => props.entries.find((e) => e.id === id)?.status
    return () => {
      const m = props.match
      const side = (name, entryId, source) =>
        h('div', { class: ['ta-side', { 'is-winner': m.winnerEntryId === entryId && entryId }] }, [
          h('div', { class: 'ta-side__name' }, [
            entryId
              ? h('span', { class: entryStatus(entryId) === 'WITHDRAWN' ? 'ta-wd-name' : '' }, name)
              : h('span', { class: 'ta-unknown' }, '待定'),
            m.winnerEntryId === entryId && entryId ? h('span', { class: 'ta-crown' }, '胜') : null
          ]),
          h('div', { class: 'ta-side__src' }, source)
        ])

      const tagType = {
        READY: 'primary',
        PENDING: 'info',
        FINISHED: m.realMatch ? 'success' : '',
        VOID: 'danger'
      }[m.status]

      const actions = []
      if (m.status === 'READY') {
        actions.push(h(
          'button',
          { class: 'ta-card__btn ta-card__btn--primary', onClick: () => emit('confirm', m) },
          '确认赛果'
        ))
      }
      if (m.status === 'FINISHED' && m.realMatch) {
        actions.push(h(
          'button',
          { class: 'ta-card__btn', onClick: () => emit('rollback', m) },
          '回退纠错'
        ))
      }

      return h('div', {
        class: [
          'ta-card',
          `is-${(m.status || '').toLowerCase()}`,
          { 'is-bye': m.resultType === 'BYE', 'is-void': m.status === 'VOID' }
        ]
      }, [
        h('div', { class: 'ta-card__head' }, [
          h('span', { class: 'ta-card__no' }, m.matchNo),
          h(ElTag, { size: 'small', type: tagType }, () => m.statusName)
        ]),
        side('A', m.sideAEntryId, m.sideAName, m.sideASource),
        side('B', m.sideBEntryId, m.sideBName, m.sideBSource),
        h('div', { class: 'ta-card__flow' }, [
          h('span', { class: 'ta-flow-line' }, `胜 → ${m.winnerNextNo || '终点'}`),
          h('span', { class: 'ta-flow-line' }, m.loserNextNo ? `败 → ${m.loserNextNo}` : '败 → 出局')
        ]),
        m.resultNote ? h('div', { class: 'ta-card__note' }, m.resultNote) : null,
        actions.length ? h('div', { class: 'ta-card__actions' }, actions) : null
      ])
    }
  }
}

const loading = ref(false)
const busy = ref(false)
const tournaments = ref([])
const current = ref(null)
const members = ref([])

const createVisible = ref(false)
const confirmVisible = ref(false)
const withdrawVisible = ref(false)
const rollbackVisible = ref(false)
const revisionsVisible = ref(false)
const activeMatch = ref(null)
const activeEntry = ref(null)
const revisions = ref([])
const activeRevisions = ref([])

const createForm = reactive({ name: '', entries: [] })
const confirmForm = reactive({ winnerId: null, operator: '店员', note: '' })
const withdrawForm = reactive({ operator: '', note: '' })
const rollbackForm = reactive({ operator: '', reason: '', correctedWinnerId: null })

// ---- 括号分列 ----
const stageColumns = (stage) => {
  const rounds = new Map()
  for (const m of current.value.matches.filter((x) => x.stage === stage)) {
    if (!rounds.has(m.roundNo)) {
      rounds.set(m.roundNo, [])
    }
    rounds.get(m.roundNo).push(m)
  }
  return [...rounds.keys()]
    .sort((a, b) => a - b)
    .map((r) => rounds.get(r).sort((a, b) => a.seq - b.seq))
}

const winnersColumns = computed(() => (current.value ? stageColumns('WINNERS') : []))
const losersColumns = computed(() => (current.value ? stageColumns('LOSERS') : []))
const grandColumn = computed(() =>
  current.value
    ? current.value.matches
        .filter((m) => m.stage === 'GRAND_FINAL' || m.stage === 'GRAND_RESET')
        .sort((a, b) => a.seq - b.seq)
    : [])

const matchTableData = computed(() => {
  if (!current.value) {
    return []
  }
  const order = { READY: 0, PENDING: 1, FINISHED: 2, VOID: 3 }
  return [...current.value.matches].sort((a, b) => {
    const d = (order[a.status] ?? 9) - (order[b.status] ?? 9)
    return d !== 0 ? d : a.id - b.id
  })
})

const createBracketSize = computed(() => {
  const n = createForm.entries.length
  if (n === 0) {
    return 0
  }
  let b = 1
  while (b < n) {
    b <<= 1
  }
  return b
})

function winnerRoundLabel(idx) {
  const total = winnersColumns.value.length
  if (idx === 0) {
    return 'W1 首轮'
  }
  if (idx === total - 1) {
    return `W${idx + 1} 胜者组决赛`
  }
  return `W${idx + 1}`
}

function loserRoundLabel(idx) {
  const roundNo = losersColumns.value.length ? losersColumns.value[idx][0]?.roundNo : idx + 1
  return `L${roundNo} 第 ${idx + 1} 组轮次`
}

function statusTagType(row) {
  if (row.status === 'READY') {
    return 'primary'
  }
  if (row.status === 'FINISHED') {
    return row.realMatch ? 'success' : 'info'
  }
  if (row.status === 'VOID') {
    return 'danger'
  }
  return ''
}

// ---- 数据加载 ----
async function loadTournaments() {
  loading.value = true
  try {
    tournaments.value = await api.tournaments()
    if (!current.value && tournaments.value.length) {
      await loadTournament(tournaments.value[0].id)
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadTournament(id) {
  loading.value = true
  try {
    current.value = await api.tournament(id)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadMembers() {
  try {
    members.value = await api.members()
  } catch (e) {
    members.value = []
  }
}

// ---- 创建 ----
function openCreate() {
  createForm.name = ''
  createForm.entries = [
    { memberId: null, guestName: '' },
    { memberId: null, guestName: '' },
    { memberId: null, guestName: '' },
    { memberId: null, guestName: '' }
  ]
  createVisible.value = true
}

function addEntry() {
  createForm.entries.push({ memberId: null, guestName: '' })
}

function removeEntry(idx) {
  createForm.entries.splice(idx, 1)
}

async function submitCreate() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请填写赛事名称')
    return
  }
  const entries = createForm.entries.map((e) =>
    e.memberId ? { memberId: e.memberId } : { guestName: e.guestName?.trim() || '' })
  if (entries.some((e) => !e.memberId && !e.guestName)) {
    ElMessage.warning('每一位临时参赛者都必须填写姓名，或选择会员')
    return
  }
  busy.value = true
  try {
    const detail = await api.createTournament({ name: createForm.name.trim(), entries })
    createVisible.value = false
    ElMessage.success('赛事已创建，完整双败括号已生成')
    await loadTournaments()
    current.value = detail
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

// ---- 确认赛果 ----
function openConfirm(match) {
  activeMatch.value = match
  confirmForm.winnerId = null
  confirmForm.note = ''
  confirmForm.operator = '店员'
  confirmVisible.value = true
}

async function submitConfirm() {
  if (!confirmForm.winnerId) {
    ElMessage.warning('请选择获胜选手')
    return
  }
  busy.value = true
  try {
    const detail = await api.confirmMatchResult(activeMatch.value.id, {
      winnerEntryId: confirmForm.winnerId,
      operator: confirmForm.operator || '店员',
      note: confirmForm.note || null
    })
    confirmVisible.value = false
    current.value = detail
    ElMessage.success('赛果已确认，括号已按双败规则推进')
    await loadTournaments()
    current.value = detail
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

// ---- 退赛 ----
function openWithdraw(entry) {
  activeEntry.value = entry
  withdrawForm.operator = ''
  withdrawForm.note = ''
  withdrawVisible.value = true
}

async function submitWithdraw() {
  busy.value = true
  try {
    const detail = await api.withdrawEntry(
      current.value.id,
      activeEntry.value.id,
      { operator: withdrawForm.operator, note: withdrawForm.note || null }
    )
    withdrawVisible.value = false
    current.value = detail
    ElMessage.success('退赛已登记，后续对阵已自动处理')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

// ---- 回退 ----
function openRollback(match) {
  activeMatch.value = match
  rollbackForm.operator = ''
  rollbackForm.reason = ''
  rollbackForm.correctedWinnerId = null
  rollbackVisible.value = true
}

async function submitRollback() {
  if (!rollbackForm.operator.trim() || !rollbackForm.reason.trim()) {
    ElMessage.warning('操作人与纠错原因都是必填项')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定从 ${activeMatch.value.matchNo} 回退其全部派生对阵并重建？`,
      '回退确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  busy.value = true
  try {
    const detail = await api.rollbackMatch(activeMatch.value.id, {
      operator: rollbackForm.operator,
      reason: rollbackForm.reason,
      correctedWinnerEntryId: rollbackForm.correctedWinnerId
    })
    rollbackVisible.value = false
    current.value = detail
    ElMessage.success('已回退并确定性重建后续括号')
    await loadTournaments()
    current.value = detail
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

async function openRevisions() {
  try {
    revisions.value = await api.tournamentRevisions(current.value.id)
    revisionsVisible.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  await loadMembers()
  await loadTournaments()
})
</script>

<style scoped>
.ta-root {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ta-summary {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.ta-summary__main h2 {
  margin: 0 0 8px;
  font-size: 16px;
}

.ta-summary__no {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
  margin-left: 8px;
}

.ta-summary__kv {
  display: flex;
  gap: 22px;
  flex-wrap: wrap;
}

.ta-champion {
  color: #e6a23c;
}

.ta-legend {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #606266;
}

.dot {
  display: inline-block;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  margin-right: 6px;
}

.dot-ready { background: #409eff; }
.dot-pending { background: #c0c4cc; }
.dot-finished { background: #67c23a; }
.dot-bye { background: #e4d5a8; }
.dot-void { background: #f56c6c; }

/* 参赛者 */
.ta-entries {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 10px;
}

.ta-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #fafcff;
}

.ta-entry__seed {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #616161;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  flex: 0 0 auto;
}

.ta-entry__body {
  flex: 1;
  min-width: 0;
}

.ta-entry__name {
  font-weight: 600;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.ta-entry__meta {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.ta-entry__note {
  font-size: 11px;
  color: #c45656;
  margin-top: 2px;
}

.ta-entry.is-loss1 {
  background: #fdf8ec;
  border-color: #f3d9a0;
}

.ta-entry.is-out {
  background: #f7f7f7;
  opacity: 0.7;
}

.ta-entry.is-wd {
  background: #fef0f0;
  border-color: #fab6b6;
}

/* 括号 */
.ta-bracket-scroll {
  overflow-x: auto;
  padding-bottom: 8px;
}

.ta-bracket {
  display: flex;
  align-items: stretch;
  gap: 18px;
  min-width: min-content;
}

.ta-col {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ta-col--l {
  border-left: 2px dashed #dcdfe6;
  border-right: 2px dashed #dcdfe6;
  padding: 0 12px;
}

.ta-col__title {
  font-size: 13px;
  font-weight: 700;
  color: #4a4a4a;
  background: #f2f3f5;
  border-radius: 8px;
  padding: 6px 10px;
}

.ta-col__l-rounds {
  display: flex;
  gap: 12px;
  align-items: stretch;
}

.ta-round {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ta-round__title {
  font-size: 12px;
  color: #909399;
  text-align: center;
}

.ta-round__cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
  justify-content: space-around;
  flex: 1;
}

.ta-card {
  width: 218px;
  border: 1px solid #dfe4ec;
  border-radius: 10px;
  padding: 8px 10px;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ta-card.is-ready {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.12);
}

.ta-card.is-finished:not(.is-bye) {
  border-color: #95d475;
}

.ta-card.is-bye {
  background: #fbf7e8;
  border-style: dashed;
  border-color: #e4d5a8;
}

.ta-card.is-void {
  background: #fef0f0;
  border-color: #fab6b6;
}

.ta-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ta-card__no {
  font-size: 12px;
  font-weight: 700;
  color: #616161;
}

.ta-side {
  border-top: 1px dashed #eef0f4;
  padding-top: 5px;
}

.ta-side__name {
  font-size: 13px;
  font-weight: 600;
  display: flex;
  justify-content: space-between;
}

.ta-side.is-winner .ta-side__name {
  color: #67c23a;
}

.ta-unknown {
  color: #c0c4cc;
  font-weight: 400;
}

.ta-wd-name {
  color: #f56c6c;
}

.ta-crown {
  font-size: 11px;
  background: #67c23a;
  color: #fff;
  border-radius: 4px;
  padding: 0 5px;
}

.ta-side__src {
  font-size: 11px;
  color: #909399;
  line-height: 1.4;
  margin-top: 1px;
}

.ta-card__flow {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #7a8699;
  border-top: 1px dashed #eef0f4;
  padding-top: 5px;
}

.ta-card__note {
  font-size: 11px;
  color: #b88a3b;
}

.ta-card__actions {
  display: flex;
  gap: 6px;
}

.ta-card__btn {
  flex: 1;
  border: 1px solid #dcdfe6;
  background: #fff;
  border-radius: 6px;
  font-size: 12px;
  padding: 4px 0;
  cursor: pointer;
  color: #616161;
}

.ta-card__btn--primary {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
}

.ta-src {
  font-size: 11px;
  color: #a8abb2;
}

.ta-create-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.ta-create-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ta-create-seed {
  width: 56px;
  font-size: 12px;
  color: #909399;
}

.ta-winner-radio {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.ta-winner-option {
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  padding: 10px 14px;
  margin: 0;
  height: auto;
}

.ta-json {
  background: #f6f7f9;
  border-radius: 6px;
  padding: 6px 8px;
  font-size: 11px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 2px 0;
  max-height: 150px;
  overflow: auto;
}
</style>
