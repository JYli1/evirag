<template>
  <main class="admin-page">
    <header class="admin-head">
      <EviRagLogo />
      <nav>
        <RouterLink to="/workbench">返回工作台</RouterLink>
        <button type="button" @click="logout">退出</button>
      </nav>
    </header>

    <section class="title-band">
      <p>管理员面板</p>
      <h1>系统运行状态与用户管理</h1>
      <span>统计来自 MySQL 主数据；配置面板只显示是否配置，不展示密钥明文。</span>
    </section>

    <AdminMetricGrid :dashboard="dashboard" />

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

    <ConfigStatusList class="config-wrap" :config-status="configStatus" />

    <section class="audit-panel">
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
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
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
</script>

<style scoped>
.admin-page {
  min-height: 100vh;
  display: grid;
  gap: 20px;
  align-content: start;
  padding: 20px;
  background: var(--color-soft);
}

.admin-head,
.title-band,
.audit-panel,
.admin-grid,
.config-wrap {
  max-width: 1440px;
  width: 100%;
  margin: 0 auto;
}

.admin-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 16px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: rgba(251, 253, 249, 0.92);
}

nav {
  display: flex;
  align-items: center;
  gap: 10px;
}

nav a,
nav button {
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: #ffffff;
  color: var(--color-brand-dark);
  font-size: 13px;
  font-weight: 900;
  text-decoration: none;
}

.title-band {
  padding: 20px 2px 4px;
}

.title-band p,
.title-band h1,
.title-band span {
  margin: 0;
}

.title-band p {
  color: var(--color-brand);
  font-size: 13px;
  font-weight: 900;
}

.title-band h1 {
  margin-top: 6px;
  color: var(--color-ink);
  font-size: 34px;
  line-height: 1.2;
  letter-spacing: 0;
}

.title-band span {
  display: block;
  margin-top: 10px;
  color: var(--color-muted);
  line-height: 1.65;
}

.admin-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(360px, 1.1fr);
  gap: 18px;
}

.audit-panel {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: #ffffff;
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
  border: 1px solid rgba(178, 74, 63, 0.24);
  border-radius: var(--radius-md);
  background: rgba(255, 248, 246, 0.95);
  color: #7b342d;
  box-shadow: var(--shadow-soft);
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 1040px) {
  .admin-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .admin-head {
    align-items: stretch;
    flex-direction: column;
  }

  nav {
    width: 100%;
  }

  nav a,
  nav button {
    flex: 1;
  }

  .title-band h1 {
    font-size: 28px;
  }
}
</style>
