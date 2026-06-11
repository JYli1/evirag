<template>
  <main class="admin-shell">
    <aside class="admin-sidebar">
      <div class="brand-block">
        <EviRagLogo compact />
        <div>
          <strong>EviAdmin</strong>
          <span>管理后台</span>
        </div>
      </div>

      <nav class="admin-nav" aria-label="管理员功能">
        <button
          v-for="item in navItems"
          :key="item.id"
          type="button"
          :class="{ active: activeTab === item.id }"
          @click="activeTab = item.id"
        >
          <span>{{ item.icon }}</span>
          <strong>{{ item.label }}</strong>
          <small>{{ item.description }}</small>
        </button>
      </nav>

      <div class="admin-account">
        <div>
          <span>当前会话</span>
          <strong>管理员</strong>
        </div>
        <RouterLink to="/workbench">返回工作台</RouterLink>
        <button type="button" @click="logout">退出</button>
      </div>
    </aside>

    <section class="admin-main">
      <header class="admin-top">
        <div>
          <p>{{ activeNav.description }}</p>
          <h1>{{ activeNav.title }}</h1>
        </div>
        <span>MySQL 实时统计</span>
      </header>

      <section v-if="activeTab === 'overview'" class="tab-pane">
        <AdminMetricGrid :dashboard="dashboard" />

        <div class="overview-grid">
          <article class="overview-card">
            <p>文档解析状态</p>
            <h2>{{ dashboard?.readyDocuments ?? 0 }} / {{ dashboard?.totalDocuments ?? 0 }}</h2>
            <div class="stack-bar">
              <i class="ready" :style="{ width: documentReadyPercent }"></i>
              <i class="failed" :style="{ width: documentFailedPercent }"></i>
            </div>
            <small>{{ dashboard?.failedDocuments ?? 0 }} 个失败文档，{{ dashboard?.todayUploadCount ?? 0 }} 个今日上传。</small>
          </article>

          <article class="overview-card">
            <p>配置状态</p>
            <h2>{{ configStatus?.missingCount ?? 0 }} 项缺失</h2>
            <button type="button" @click="activeTab = 'settings'">查看配置</button>
            <small>密钥只展示是否配置，不显示明文。</small>
          </article>

          <article class="overview-card">
            <p>最近审计</p>
            <h2>{{ auditLogs.length }} 条</h2>
            <button type="button" @click="activeTab = 'audit'">查看审计</button>
            <small>{{ auditLogs[0]?.action || '暂无管理员操作记录' }}</small>
          </article>
        </div>

        <div class="overview-charts">
          <article class="chart-card">
            <header>
              <div>
                <p>资源规模</p>
                <h2>全站核心数据分布</h2>
              </div>
              <span>Aggregate</span>
            </header>
            <div class="bar-chart" aria-label="全站核心数据分布">
              <div v-for="item in overviewBars" :key="item.label" class="bar-row">
                <span>{{ item.label }}</span>
                <div><i :style="{ width: item.percent }"></i></div>
                <strong>{{ formatNumber(item.value) }}</strong>
              </div>
            </div>
          </article>

          <article class="chart-card document-chart">
            <header>
              <div>
                <p>文档状态</p>
                <h2>解析与索引占比</h2>
              </div>
              <span>{{ dashboard?.totalDocuments ?? 0 }} 份</span>
            </header>
            <div class="donut-layout">
              <div class="donut-chart" :style="documentRingStyle">
                <strong>{{ documentReadyPercentNumber }}%</strong>
                <small>已就绪</small>
              </div>
              <div class="chart-legend">
                <span><i class="ready"></i>已就绪 {{ dashboard?.readyDocuments ?? 0 }}</span>
                <span><i class="failed"></i>失败 {{ dashboard?.failedDocuments ?? 0 }}</span>
                <span><i class="processing"></i>处理中 {{ processingDocuments }}</span>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section v-else-if="activeTab === 'users'" class="tab-pane users-pane">
        <div class="users-summary">
          <div>
            <p>账号状态</p>
            <h2>系统准入用户</h2>
          </div>
          <div class="users-stats">
            <span>全部 <strong>{{ dashboard?.totalUsers ?? users.length }}</strong></span>
            <span>启用 <strong>{{ dashboard?.activeUsers ?? 0 }}</strong></span>
            <span>禁用 <strong>{{ dashboard?.disabledUsers ?? 0 }}</strong></span>
          </div>
        </div>

        <div class="admin-grid users-grid">
          <AdminUserTable
            :users="users"
            :updating-user-id="updatingUserId"
            :selected-user-id="selectedUserId"
            @select-user="selectUser"
            @update-status="updateUserStatus"
          />
          <AdminUserDetailPanel :detail="selectedUserDetail" :loading="loadingUserDetail" />
        </div>
      </section>

      <ConfigStatusList v-else-if="activeTab === 'settings'" class="tab-pane" :config-status="configStatus" />

      <section v-else class="audit-panel tab-pane">
        <header>
          <div>
            <p>审计日志</p>
            <h2>最近管理员操作</h2>
          </div>
          <span>{{ auditLogs.length }} 条</span>
        </header>
        <article v-for="log in auditLogs" :key="log.id" class="audit-item">
          <strong>{{ log.action }}</strong>
          <small>{{ log.targetType }} #{{ log.targetId ?? '-' }} · {{ formatDate(log.createdAt) }}</small>
          <p v-if="log.detail">{{ log.detail }}</p>
        </article>
        <p v-if="!auditLogs.length" class="empty">暂无审计日志。</p>
      </section>

      <p v-if="error" class="global-error">{{ error }}</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, type CSSProperties } from 'vue';
import { useRouter } from 'vue-router';

import {
  getAdminConfigStatus,
  getAdminDashboard,
  getAdminUserDetail,
  listAdminAuditLogs,
  listAdminUsers,
  updateAdminUserStatus,
  type AdminAuditLog,
  type AdminConfigStatus,
  type AdminDashboard,
  type AdminUser,
  type AdminUserDetail,
} from '@/api/admin';
import { apiErrorMessage } from '@/api/http';
import EviRagLogo from '@/assets/logo/EviRagLogo.vue';
import AdminMetricGrid from '@/components/admin/AdminMetricGrid.vue';
import AdminUserDetailPanel from '@/components/admin/AdminUserDetailPanel.vue';
import AdminUserTable from '@/components/admin/AdminUserTable.vue';
import ConfigStatusList from '@/components/admin/ConfigStatusList.vue';
import { useAuthStore } from '@/stores/authStore';

const router = useRouter();
const authStore = useAuthStore();

const dashboard = ref<AdminDashboard | null>(null);
const users = ref<AdminUser[]>([]);
const configStatus = ref<AdminConfigStatus | null>(null);
const auditLogs = ref<AdminAuditLog[]>([]);
const error = ref('');
const updatingUserId = ref<number | null>(null);
const selectedUserId = ref<number | null>(null);
const selectedUserDetail = ref<AdminUserDetail | null>(null);
const loadingUserDetail = ref(false);
type AdminTab = 'overview' | 'users' | 'audit' | 'settings';
const activeTab = ref<AdminTab>('overview');
const navItems: Array<{
  id: AdminTab;
  icon: string;
  label: string;
  title: string;
  description: string;
}> = [
  { id: 'overview', icon: '01', label: '全站总览', title: '全站资源与计算运营指标', description: '用户、文档、知识库和 Token 用量' },
  { id: 'users', icon: '02', label: '用户管理', title: '系统准入用户列表与单账号统计', description: '状态控制、资源数量和最近活动' },
  { id: 'audit', icon: '03', label: '审计日志', title: '全站操作审计与安全追踪', description: '管理员操作记录' },
  { id: 'settings', icon: '04', label: '配置状态', title: '知识库引擎与底座配置状态', description: '必填配置和敏感配置检查' },
];

const activeNav = computed(() => navItems.find((item) => item.id === activeTab.value) || navItems[0]);
const documentReadyPercent = computed(() => documentPercent(dashboard.value?.readyDocuments ?? 0));
const documentFailedPercent = computed(() => documentPercent(dashboard.value?.failedDocuments ?? 0));
const processingDocuments = computed(() => Math.max(
  0,
  (dashboard.value?.totalDocuments ?? 0)
    - (dashboard.value?.readyDocuments ?? 0)
    - (dashboard.value?.failedDocuments ?? 0),
));
const documentReadyPercentNumber = computed(() => documentPercentNumber(dashboard.value?.readyDocuments ?? 0));
const documentFailedPercentNumber = computed(() => documentPercentNumber(dashboard.value?.failedDocuments ?? 0));
const documentProcessingPercentNumber = computed(() => documentPercentNumber(processingDocuments.value));
const documentRingStyle = computed<CSSProperties>(() => {
  const ready = documentReadyPercentNumber.value;
  const failed = documentFailedPercentNumber.value;
  const processing = documentProcessingPercentNumber.value;
  const failedEnd = ready + failed;
  const processingEnd = Math.min(100, failedEnd + processing);
  return {
    background: `conic-gradient(var(--color-success) 0 ${ready}%, var(--color-danger) ${ready}% ${failedEnd}%, var(--color-brand-light) ${failedEnd}% ${processingEnd}%, rgba(216, 236, 243, 0.82) ${processingEnd}% 100%)`,
  };
});
const overviewBars = computed(() => {
  const items = [
    { label: '用户', value: dashboard.value?.totalUsers ?? 0 },
    { label: '知识库', value: dashboard.value?.totalKnowledgeBases ?? 0 },
    { label: '文档', value: dashboard.value?.totalDocuments ?? 0 },
    { label: '问答', value: dashboard.value?.questionCount ?? 0 },
    { label: 'Token', value: dashboard.value?.estimatedTotalTokens ?? 0 },
  ];
  const max = Math.max(...items.map((item) => item.value), 1);
  return items.map((item) => ({
    ...item,
    percent: `${Math.max(6, Math.round((item.value / max) * 100))}%`,
  }));
});

onMounted(async () => {
  await loadAdminData();
});

async function loadAdminData() {
  error.value = '';
  try {
    const [dashboardData, userData, configData, auditData] = await Promise.all([
      getAdminDashboard(),
      listAdminUsers(),
      getAdminConfigStatus(),
      listAdminAuditLogs(),
    ]);
    dashboard.value = dashboardData;
    users.value = userData;
    configStatus.value = configData;
    auditLogs.value = auditData;
    if (userData.length > 0) {
      const nextSelectedId = selectedUserId.value && userData.some((user) => user.id === selectedUserId.value)
        ? selectedUserId.value
        : userData[0].id;
      await loadUserDetail(nextSelectedId);
    } else {
      selectedUserId.value = null;
      selectedUserDetail.value = null;
    }
  } catch (err) {
    error.value = apiErrorMessage(err);
  }
}

async function selectUser(user: AdminUser) {
  await loadUserDetail(user.id);
}

async function loadUserDetail(userId: number) {
  selectedUserId.value = userId;
  loadingUserDetail.value = true;
  error.value = '';
  try {
    selectedUserDetail.value = await getAdminUserDetail(userId);
  } catch (err) {
    error.value = apiErrorMessage(err);
  } finally {
    loadingUserDetail.value = false;
  }
}

async function updateUserStatus(user: AdminUser, status: 'ACTIVE' | 'DISABLED') {
  updatingUserId.value = user.id;
  error.value = '';
  try {
    const updated = await updateAdminUserStatus(user.id, status);
    users.value = users.value.map((item) => (item.id === updated.id ? updated : item));
    auditLogs.value = await listAdminAuditLogs();
    dashboard.value = await getAdminDashboard();
    if (selectedUserId.value === updated.id) {
      await loadUserDetail(updated.id);
    }
  } catch (err) {
    error.value = apiErrorMessage(err);
  } finally {
    updatingUserId.value = null;
  }
}

async function logout() {
  authStore.clearAuth();
  await router.push('/login');
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN');
}

function documentPercent(value: number) {
  const total = dashboard.value?.totalDocuments ?? 0;
  if (!total) return '0%';
  return `${Math.round((value / total) * 100)}%`;
}

function documentPercentNumber(value: number) {
  const total = dashboard.value?.totalDocuments ?? 0;
  if (!total) return 0;
  return Math.round((value / total) * 100);
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value);
}
</script>

<style scoped>
.admin-shell {
  height: 100vh;
  display: grid;
  grid-template-columns: 288px minmax(0, 1fr);
  overflow: hidden;
  background:
    linear-gradient(rgba(18, 149, 190, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(18, 149, 190, 0.05) 1px, transparent 1px),
    radial-gradient(circle at 16% 6%, rgba(18, 149, 190, 0.13), transparent 28%),
    var(--color-soft);
  background-size: 36px 36px, 36px 36px, auto;
}

.admin-sidebar {
  height: 100vh;
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 20px;
  padding: 18px;
  border-right: 1px solid var(--color-line);
  background: var(--color-crystal);
  overflow-y: auto;
  box-shadow: 8px 0 34px rgba(19, 54, 82, 0.08);
  backdrop-filter: blur(20px);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 52px;
}

.brand-block div {
  display: grid;
  gap: 2px;
}

.brand-block strong {
  color: var(--color-ink);
  font-size: 16px;
}

.brand-block span {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

.admin-nav {
  display: grid;
  align-content: start;
  gap: 8px;
}

.admin-nav button {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 2px 10px;
  align-items: center;
  width: 100%;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 16px;
  background: transparent;
  color: var(--color-ink);
  text-align: left;
  transition:
    transform 0.16s ease,
    background 0.16s ease,
    border-color 0.16s ease,
    box-shadow 0.16s ease;
}

.admin-nav button:hover,
.admin-nav button.active {
  transform: translateY(-2px);
  border-color: var(--color-strong-line);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(223, 245, 250, 0.78));
}

.admin-nav button.active {
  box-shadow: inset 3px 0 0 var(--color-brand), 0 0 0 6px rgba(18, 149, 190, 0.05);
}

.admin-nav button span {
  grid-row: span 2;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--color-brand-dark);
  font-size: 12px;
  font-weight: 900;
}

.admin-nav button strong {
  min-width: 0;
  overflow: hidden;
  color: inherit;
  font-size: 13px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-nav button small {
  min-width: 0;
  overflow: hidden;
  color: var(--color-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-account {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--color-line);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.58);
}

.admin-account div {
  display: grid;
  gap: 4px;
}

.admin-account span {
  color: var(--color-muted);
  font-size: 11px;
  font-weight: 900;
}

.admin-account strong {
  color: var(--color-ink);
  font-size: 13px;
}

.admin-account a,
.admin-account button,
.overview-card button {
  min-height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-line);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--color-brand-dark);
  font-size: 12px;
  font-weight: 900;
  text-decoration: none;
}

.admin-main {
  height: 100vh;
  min-width: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  align-content: start;
  gap: 22px;
  padding: 24px;
  overflow: hidden;
}

.admin-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  max-width: 1440px;
  width: 100%;
  margin: 0 auto;
  padding: 16px 18px;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(18px);
}

.admin-top p,
.admin-top h1,
.admin-top span,
.overview-card p,
.overview-card h2,
.overview-card small {
  margin: 0;
}

.admin-top p {
  color: var(--color-brand);
  font-size: 12px;
  font-weight: 900;
}

.admin-top h1 {
  margin-top: 5px;
  color: var(--color-ink);
  font-size: 22px;
  line-height: 1.2;
  letter-spacing: 0;
}

.admin-top span {
  padding: 6px 10px;
  border: 1px solid var(--color-line);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.68);
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
}

.tab-pane {
  max-width: 1440px;
  width: 100%;
  margin: 0 auto;
  min-height: 0;
  overflow: auto;
}

.admin-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(360px, 1.1fr);
  gap: 18px;
}

.users-pane {
  height: 100%;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 14px;
  overflow: hidden;
}

.users-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--color-line);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.74);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(18px);
}

.users-summary p,
.users-summary h2 {
  margin: 0;
}

.users-summary p {
  color: var(--color-brand);
  font-size: 12px;
  font-weight: 900;
}

.users-summary h2 {
  margin-top: 6px;
  color: var(--color-ink);
  font-size: 22px;
}

.users-stats {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.users-stats span {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid var(--color-line);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.68);
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

.users-stats strong {
  color: var(--color-brand-dark);
  font-size: 16px;
}

.users-grid {
  min-height: 0;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.overview-card {
  position: relative;
  overflow: hidden;
  display: grid;
  gap: 10px;
  min-height: 156px;
  padding: 16px;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.74);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(18px);
}

.overview-card::after {
  content: '';
  position: absolute;
  right: -34px;
  bottom: -44px;
  width: 120px;
  height: 120px;
  border: 1px solid var(--color-strong-line);
  border-radius: 30px;
  transform: rotate(18deg);
  opacity: 0.7;
}

.overview-card p {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

.overview-card h2 {
  color: var(--color-ink);
  font-size: 28px;
  line-height: 1;
  position: relative;
  z-index: 1;
}

.overview-card small {
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.55;
}

.overview-card button {
  justify-self: start;
  padding: 0 12px;
}

.stack-bar {
  height: 14px;
  display: flex;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(216, 236, 243, 0.7);
}

.stack-bar i {
  display: block;
  min-width: 0;
}

.stack-bar .ready {
  background: var(--color-success);
}

.stack-bar .failed {
  background: var(--color-danger);
}

.overview-charts {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(340px, 0.85fr);
  gap: 14px;
  margin-top: 16px;
}

.chart-card {
  display: grid;
  gap: 16px;
  min-height: 300px;
  padding: 16px;
  border: 1px solid var(--color-line);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.74);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(18px);
}

.chart-card header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 14px;
}

.chart-card header p,
.chart-card header h2 {
  margin: 0;
}

.chart-card header p {
  color: var(--color-brand);
  font-size: 12px;
  font-weight: 900;
}

.chart-card header h2 {
  margin-top: 6px;
  color: var(--color-ink);
  font-size: 20px;
}

.chart-card header span {
  padding: 6px 10px;
  border: 1px solid var(--color-line);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.68);
  color: var(--color-brand-dark);
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
}

.bar-chart {
  display: grid;
  gap: 13px;
}

.bar-row {
  display: grid;
  grid-template-columns: 62px minmax(0, 1fr) 104px;
  align-items: center;
  gap: 12px;
}

.bar-row span,
.bar-row strong {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

.bar-row strong {
  color: var(--color-ink);
  text-align: right;
}

.bar-row div {
  height: 14px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(216, 236, 243, 0.7);
}

.bar-row i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--color-brand-dark), var(--color-brand));
  box-shadow: 0 8px 24px rgba(18, 149, 190, 0.18);
}

.donut-layout {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 18px;
  align-items: center;
}

.donut-chart {
  width: 176px;
  height: 176px;
  display: grid;
  place-items: center;
  border-radius: 999px;
}

.donut-chart::before {
  content: '';
  grid-area: 1 / 1;
  width: 112px;
  height: 112px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.donut-chart strong,
.donut-chart small {
  grid-area: 1 / 1;
  position: relative;
  z-index: 1;
}

.donut-chart strong {
  align-self: center;
  margin-top: -14px;
  color: var(--color-brand-dark);
  font-size: 30px;
}

.donut-chart small {
  align-self: center;
  margin-top: 38px;
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

.chart-legend {
  display: grid;
  gap: 10px;
}

.chart-legend span {
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--color-muted);
  font-size: 13px;
  font-weight: 900;
}

.chart-legend i {
  width: 10px;
  height: 10px;
  border-radius: 999px;
}

.chart-legend .ready {
  background: var(--color-success);
}

.chart-legend .failed {
  background: var(--color-danger);
}

.chart-legend .processing {
  background: var(--color-brand-light);
}

.audit-panel {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.74);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(18px);
}

.audit-panel header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.audit-panel p,
.audit-panel h2 {
  margin: 0;
}

.audit-panel header p,
.audit-panel header span {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

.audit-panel h2 {
  margin-top: 5px;
  color: var(--color-ink);
  font-size: 20px;
}

.audit-item {
  display: grid;
  gap: 4px;
  padding: 10px 0;
  border-top: 1px solid var(--color-line);
}

.audit-item strong {
  color: var(--color-ink);
  font-size: 13px;
}

.audit-item small,
.audit-item p,
.empty {
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.55;
}

.audit-item p,
.empty {
  margin: 0;
}

.global-error {
  position: fixed;
  left: 50%;
  bottom: 18px;
  z-index: 10;
  max-width: min(680px, calc(100vw - 32px));
  margin: 0;
  padding: 10px 13px;
  transform: translateX(-50%);
  border: 1px solid rgba(190, 18, 60, 0.24);
  border-radius: 12px;
  background: rgba(255, 248, 246, 0.95);
  color: #7b342d;
  box-shadow: var(--shadow-soft);
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 1040px) {
  .admin-shell {
    grid-template-columns: 1fr;
  }

  .admin-sidebar {
    min-height: auto;
    grid-template-rows: auto auto;
    border-right: 0;
    border-bottom: 1px solid var(--color-line);
  }

  .admin-nav {
    grid-template-columns: repeat(4, minmax(120px, 1fr));
    overflow-x: auto;
  }

  .admin-account {
    display: none;
  }

  .admin-grid {
    grid-template-columns: 1fr;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .overview-charts {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .admin-main {
    padding: 14px;
  }

  .admin-top {
    align-items: stretch;
    flex-direction: column;
  }

  .users-summary {
    align-items: stretch;
    flex-direction: column;
  }

  .users-stats {
    justify-content: flex-start;
  }

  .admin-nav {
    grid-template-columns: repeat(2, minmax(140px, 1fr));
  }

  .admin-top h1 {
    font-size: 20px;
  }

  .donut-layout {
    grid-template-columns: 1fr;
    justify-items: center;
  }

  .bar-row {
    grid-template-columns: 54px minmax(0, 1fr);
  }

  .bar-row strong {
    grid-column: 2;
    text-align: left;
  }
}
</style>
