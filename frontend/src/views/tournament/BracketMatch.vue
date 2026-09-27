<template>
  <div class="bmatch" :class="cellClass">
    <div class="bmatch__head">
      <span class="bmatch__code">{{ match.matchCode }}</span>
      <span class="bmatch__status" :class="statusClass">{{ match.statusName }}</span>
    </div>

    <!-- 两个来源槽 -->
    <div
      v-for="(slot, idx) in [match.slotA, match.slotB]"
      :key="idx"
      class="bmatch__slot"
      :class="slotClass(slot)"
    >
      <div class="bmatch__slot-main">
        <span class="bmatch__name">{{ slot.resolvedEntrantName || slotSourceText(slot) }}</span>
      </div>
      <div class="bmatch__slot-sub">
        <span class="bmatch__from">{{ slot.sourceText }}</span>
        <span class="bmatch__state" :class="stateClass(slot)">{{ slot.resolvedStateName }}</span>
      </div>
    </div>

    <!-- 去向：胜者 / 败者下一场 -->
    <div class="bmatch__flow">
      <span v-if="match.nextWinText" class="bmatch__flow-win">胜 → {{ match.nextWinText }}</span>
      <span v-else-if="!isFinal" class="bmatch__flow-win bmatch__flow-win--champ">胜者夺冠</span>
      <span v-if="match.nextLoseText" class="bmatch__flow-lose">负 → {{ match.nextLoseText }}</span>
      <span v-else-if="isLosersMatch && !bye" class="bmatch__flow-lose bmatch__flow-lose--out">负者二败出局</span>
    </div>

    <!-- 操作区 -->
    <div class="bmatch__actions">
      <template v-if="bye">
        <el-tag size="small" type="info" effect="plain">轮空自动晋级 · 非真实比赛</el-tag>
      </template>
      <template v-else-if="finished">
        <el-tag size="small" :type="winKindTagType">{{ match.winKindName || '已确认' }}</el-tag>
        <el-button v-if="canRollback" link type="warning" size="small" @click="$emit('rollback', match)">回退纠错</el-button>
      </template>
      <template v-else-if="match.canConfirm">
        <el-button size="small" type="primary" @click="$emit('confirm', match)">确认赛果</el-button>
        <el-button
          v-if="match.pendingCorrection"
          size="small"
          type="success"
          plain
          @click="$emit('correct', match)"
        >
          管理员更正
        </el-button>
      </template>
      <template v-else>
        <span class="bmatch__wait">等待双方到齐</span>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  match: { type: Object, required: true },
  canRollback: { type: Boolean, default: true }
})
defineEmits(['confirm', 'rollback', 'correct'])

const bye = computed(() => props.match.structuralBye)
const finished = computed(() => props.match.status === 'FINISHED')
const isFinal = computed(() => ['GF', 'LB-FINAL'].includes(props.match.matchCode))
const isLosersMatch = computed(() => props.match.bracketPart === 'LB')

const cellClass = computed(() => ({
  'is-bye': bye.value,
  'is-finished': finished.value,
  'is-ready': props.match.canConfirm,
  'is-wb': props.match.bracketPart === 'WB',
  'is-lb': props.match.bracketPart === 'LB',
  'is-gf': props.match.bracketPart === 'GF'
}))

const winKindTagType = computed(() => {
  if (props.match.winKind === 'WALKOVER') return 'warning'
  if (props.match.winKind === 'DOUBLE_WALKOVER') return 'danger'
  return 'success'
})

const statusClass = computed(() => ({
  'is-ready': props.match.status === 'READY',
  'is-finished': props.match.status === 'FINISHED',
  'is-waiting': props.match.status === 'WAITING'
}))

function slotSourceText(slot) {
  if (slot.resolvedState === 'WAITING') return '待上游：' + slot.sourceText
  if (slot.resolvedState === 'OUT') return '（已退赛/出局）'
  if (slot.resolvedState === 'BYE') return '轮空'
  return slot.sourceText
}

function slotClass(slot) {
  return {
    'is-present': slot.resolvedState === 'PRESENT',
    'is-waiting': slot.resolvedState === 'WAITING',
    'is-out': slot.resolvedState === 'OUT',
    'is-bye-slot': slot.resolvedState === 'BYE',
    'is-winner': props.match.winnerEntrantId && slot.resolvedEntrantId === props.match.winnerEntrantId,
    'is-loser': props.match.loserEntrantId && slot.resolvedEntrantId === props.match.loserEntrantId
  }
}

function stateClass(slot) {
  return {
    'is-present': slot.resolvedState === 'PRESENT',
    'is-waiting': slot.resolvedState === 'WAITING',
    'is-out': slot.resolvedState === 'OUT',
    'is-bye-slot': slot.resolvedState === 'BYE'
  }
}
</script>

<style scoped>
.bmatch {
  border: 1px solid #dfe3e8;
  border-radius: 10px;
  background: #fff;
  padding: 8px 10px;
  min-width: 218px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  display: flex;
  flex-direction: column;
  gap: 6px;
  border-left-width: 4px;
}
.bmatch.is-wb { border-left-color: #409eff; }
.bmatch.is-lb { border-left-color: #e6a23c; }
.bmatch.is-gf { border-left-color: #f56c6c; }
.bmatch.is-bye { border-left-color: #c0c4cc; background: #fafafa; }
.bmatch.is-finished { background: #f6fbff; }
.bmatch.is-ready { box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.18); }

.bmatch__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.bmatch__code {
  font-size: 11px;
  font-weight: 700;
  color: #5a5a5a;
  letter-spacing: 0.4px;
}
.bmatch__status {
  font-size: 10px;
  padding: 1px 7px;
  border-radius: 999px;
  background: #f0f2f5;
  color: #909399;
}
.bmatch__status.is-ready { background: #ecf5ff; color: #409eff; }
.bmatch__status.is-finished { background: #f0f9eb; color: #67c23a; }

.bmatch__slot {
  border: 1px solid #ebeef5;
  border-radius: 7px;
  padding: 5px 8px;
  background: #fff;
}
.bmatch__slot.is-waiting { background: #fafbfc; border-style: dashed; }
.bmatch__slot.is-out { background: #fef0f0; }
.bmatch__slot.is-bye-slot { background: #fafafa; }
.bmatch__slot.is-winner { border-color: #67c23a; background: #f0f9eb; }
.bmatch__slot.is-loser { opacity: 0.72; }
.bmatch__slot-main { display: flex; align-items: center; justify-content: space-between; }
.bmatch__name { font-size: 13px; font-weight: 600; color: #2f2f2f; }
.bmatch__slot-sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 2px;
  font-size: 10px;
  color: #909399;
}
.bmatch__state.is-present { color: #67c23a; }
.bmatch__state.is-waiting { color: #909399; }
.bmatch__state.is-out { color: #f56c6c; }
.bmatch__state.is-bye-slot { color: #c0c4cc; }

.bmatch__flow {
  display: flex;
  flex-direction: column;
  gap: 1px;
  font-size: 10px;
  line-height: 1.5;
}
.bmatch__flow-win { color: #409eff; }
.bmatch__flow-win--champ { color: #67c23a; font-weight: 700; }
.bmatch__flow-lose { color: #e6a23c; }
.bmatch__flow-lose--out { color: #f56c6c; }

.bmatch__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 24px;
}
.bmatch__wait { font-size: 11px; color: #a8abb2; }
</style>
