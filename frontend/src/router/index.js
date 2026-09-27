import { reactive, shallowRef, markRaw } from 'vue'

import FloorMap from '../views/FloorMap.vue'
import SessionDesk from '../views/SessionDesk.vue'
import GearRack from '../views/GearRack.vue'
import MemberDesk from '../views/MemberDesk.vue'
import TournamentDesk from '../views/TournamentDesk.vue'
import OverviewBoard from '../views/OverviewBoard.vue'

/**
 * 轻量前端路由（底部标签栏版）。
 *
 * 这里的 main.js 是仓里既有的、不允许改动的入口（它只 createApp(App).use(ElementPlus).mount()），
 * 没有 app.use(router) 这一步，所以不引入 vue-router 的插件安装流程，
 * 直接导出一份响应式路由状态 + 视图组件，由 App.vue 用底部标签栏 + <component :is> 切换。
 *
 * 用法对页面完全透明：navigate('/sessions') 切页，route.title / route.hint 给顶栏标题。
 */
export const tabs = [
  {
    path: '/floor-map',
    label: '平面图',
    glyph: '台',
    title: '球台平面图',
    hint: '每张台就是一个方块：空闲点击直接开局，使用中点击结束并结算，维护中点击恢复'
  },
  {
    path: '/sessions',
    label: '开局计费',
    glyph: '局',
    title: '开局与计费台',
    hint: '开局 → 结束 → 结算只能往前走一步；不足一小时按一小时，会员自动打折'
  },
  {
    path: '/gears',
    label: '器材租借',
    glyph: '杆',
    title: '球杆与器材挂架',
    hint: '点挂架上的格位借出 / 归还；借出未还不能重复借，损坏要按赔偿金额结清'
  },
  {
    path: '/members',
    label: '会员充值',
    glyph: '卡',
    title: '会员卡与充值',
    hint: '点会员卡充值、冻结或解冻；余额不足会拦住开局与余额结算'
  },
  {
    path: '/tournaments',
    label: '双败赛事',
    glyph: '赛',
    title: '双败淘汰赛事',
    hint: '建赛自动生成胜者组/败者组/总决赛括号；二败淘汰，总决赛爆冷自动加重置赛，可受控回退纠错'
  },
  {
    path: '/overview',
    label: '运营概览',
    glyph: '览',
    title: '运营概览',
    hint: '台位占用、累计开局次数与累计分钟数都来自后端的只读计算列（@Formula）'
  }
]

const first = tabs[0]

export const route = reactive({
  path: first.path,
  title: first.title,
  hint: first.hint
})

export const currentView = shallowRef(markRaw(FloorMap))

const viewByPath = {
  '/floor-map': markRaw(FloorMap),
  '/sessions': markRaw(SessionDesk),
  '/gears': markRaw(GearRack),
  '/members': markRaw(MemberDesk),
  '/tournaments': markRaw(TournamentDesk),
  '/overview': markRaw(OverviewBoard)
}

export function navigate(path) {
  if (!applyPath(path)) {
    return
  }
  // 同步到 hash，刷新和直链（#/gears）都能落回同一页
  if (window.location.hash !== '#' + path) {
    window.location.hash = path
  }
}

function applyPath(path) {
  const target = tabs.find((item) => item.path === path)
  if (!target) {
    return false
  }
  route.path = target.path
  route.title = target.title
  route.hint = target.hint
  currentView.value = viewByPath[target.path]
  return true
}

function hashPath() {
  return window.location.hash.replace(/^#/, '')
}

window.addEventListener('hashchange', () => {
  applyPath(hashPath())
})

if (hashPath()) {
  applyPath(hashPath())
}

export function defaultPath() {
  return first.path
}
