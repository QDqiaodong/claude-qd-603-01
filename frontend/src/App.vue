<template>
  <div class="bl-shell">
    <!-- 顶栏：当前页标题 + 一句话说明 -->
    <header class="bl-topbar">
      <div class="bl-topbar__text">
        <h1>{{ route.title }}</h1>
        <p>{{ route.hint }}</p>
      </div>
      <div class="bl-topbar__mark">
        <span class="bl-topbar__mark-line">台球</span>
        <span class="bl-topbar__mark-line">俱乐部</span>
      </div>
    </header>

    <!-- 内容区：平面图 / 计费 / 器材 / 会员 / 概览 -->
    <section class="bl-page">
      <component :is="currentView" />
    </section>

    <!-- 底部标签栏：移动端形态，一排标签切模块 -->
    <nav class="bl-tabbar">
      <button
        v-for="tab in tabs"
        :key="tab.path"
        class="bl-tab"
        :class="{ 'is-active': tab.path === route.path }"
        type="button"
        @click="navigate(tab.path)"
      >
        <span class="bl-tab__glyph">{{ tab.glyph }}</span>
        <span class="bl-tab__label">{{ tab.label }}</span>
      </button>
    </nav>
  </div>
</template>

<script setup>
import { tabs, route, currentView, navigate } from './router'
</script>

<style>
body {
  background: #f5f6f8;
}

.bl-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 100%;
  background: #f5f6f8;
}

/* ---------------- 顶栏 ---------------- */
.bl-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 20px 12px;
  background: #ffffff;
  border-bottom: 1px solid #e4e7ed;
}

.bl-topbar__text h1 {
  margin: 0;
  font-size: 18px;
  color: #2f2f2f;
  letter-spacing: 0.6px;
}

.bl-topbar__text p {
  margin: 5px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #8c8c8c;
}

.bl-topbar__mark {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 46px;
  height: 46px;
  border-radius: 12px;
  background: #616161;
  color: #fff;
  font-size: 12px;
  line-height: 1.15;
  letter-spacing: 1px;
}

.bl-topbar__mark-line {
  display: block;
}

/* ---------------- 内容区 ---------------- */
.bl-page {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 14px 18px 18px;
}

/* ---------------- 底部标签栏 ---------------- */
.bl-tabbar {
  display: flex;
  align-items: stretch;
  flex: 0 0 auto;
  height: 58px;
  background: #ffffff;
  border-top: 1px solid #e4e7ed;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.04);
}

.bl-tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  border: none;
  background: transparent;
  padding: 6px 2px 4px;
  cursor: pointer;
  color: #9a9a9a;
  transition: color 0.15s ease;
}

.bl-tab + .bl-tab {
  border-left: 1px solid #f2f3f5;
}

.bl-tab__glyph {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  border: 1px solid #d8d8d8;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
}

.bl-tab__label {
  font-size: 11px;
  letter-spacing: 0.5px;
}

.bl-tab.is-active {
  color: #616161;
  font-weight: 600;
}

.bl-tab.is-active .bl-tab__glyph {
  background: #616161;
  border-color: #616161;
  color: #ffffff;
}

/* ---------------- 跨页面共用的小件 ---------------- */
.bl-panel {
  background: #ffffff;
  border: 1px solid #e8eaed;
  border-radius: 12px;
  padding: 14px 16px;
}

.bl-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.bl-panel__head h2 {
  margin: 0;
  font-size: 15px;
  color: #2f2f2f;
  letter-spacing: 0.4px;
}

.bl-panel__head span {
  font-size: 12px;
  color: #909399;
}

.bl-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.bl-muted {
  color: #909399;
  font-size: 12px;
}

.bl-kv {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: #606266;
  line-height: 1.9;
}

.bl-kv b {
  font-weight: 600;
  color: #2f2f2f;
}

.bl-empty {
  padding: 28px 0;
  text-align: center;
  color: #a8abb2;
  font-size: 13px;
}
</style>
