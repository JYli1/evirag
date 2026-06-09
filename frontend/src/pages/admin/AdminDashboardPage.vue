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
      </section>

      <section v-else-if="activeTab === 'users'" class="tab-pane">
        <div class="admin-grid">
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
import { computed, onMounted, ref } from 'vue';
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
</script>

<style scoped>
.admin-shell {
  height: 100vh;
  display: grid;
  grid-template-columns: 272px minmax(0, 1fr);
  overflow: hidden;
  background: var(--color-soft);
}

.admin-sidebar {
  height: 100vh;
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 20px;
  padding: 18px;
  border-right: 1px solid var(--color-line);
  background: rgba(255, 255, 255, 0.94);
  overflow-y: auto;
  box-shadow: 8px 0 28px rgba(33, 53, 83, 0.04);
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
  transform: translateY(-1px);
  border-color: rgba(37, 99, 235, 0.22);
  background: #eef5ff;
}

.admin-nav button.active {
  box-shadow: inset 3px 0 0 rgba(37, 99, 235, 0.64);
}

.admin-nav button span {
  grid-row: span 2;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: #ffffff;
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
  background: #f8fbff;
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
  background: #ffffff;
  color: var(--color-brand-dark);
  font-size: 12px;
  font-weight: 900;
  text-decoration: none;
}

.admin-main {
  height: 100vh;
  min-width: 0;
  display: grid;
  grid-template-rows: auto 1fr;
  align-content: start;
  gap: 22px;
  padding: 24px;
  overflow-y: auto;
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
  background: rgba(255, 255, 255, 0.9);
  box-shadow: var(--shadow-card);
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
  background: #f8fbff;
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
}

.tab-pane {
  max-width: 1440px;
  width: 100%;
  margin: 0 auto;
}

.admin-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(360px, 1.1fr);
  gap: 18px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.overview-card {
  display: grid;
  gap: 10px;
  min-height: 156px;
  padding: 16px;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: #ffffff;
  box-shadow: var(--shadow-card);
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
  background: #edf2f7;
}

.stack-bar i {
  display: block;
  min-width: 0;
}

.stack-bar .ready {
  background: #0f766e;
}

.stack-bar .failed {
  background: #be123c;
}

.audit-panel {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: #ffffff;
  box-shadow: var(--shadow-card);
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
}

@media (max-width: 640px) {
  .admin-main {
    padding: 14px;
  }

  .admin-top {
    align-items: stretch;
    flex-direction: column;
  }

  .admin-nav {
    grid-template-columns: repeat(2, minmax(140px, 1fr));
  }

  .admin-top h1 {
    font-size: 20px;
  }
}
</style>
