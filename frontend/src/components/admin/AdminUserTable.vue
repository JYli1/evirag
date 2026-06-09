<template>
  <section class="user-table">
    <header>
      <div>
        <p>用户管理</p>
        <h2>账号状态</h2>
      </div>
      <span>{{ users.length }} 个用户</span>
    </header>

    <div class="table">
      <div class="row head">
        <span>邮箱</span>
        <span>角色</span>
        <span>状态</span>
        <span>操作</span>
      </div>
      <div
        v-for="user in users"
        :key="user.id"
        class="row"
        :class="{ selected: user.id === selectedUserId }"
        role="button"
        tabindex="0"
        @click="select(user)"
        @keydown.enter="select(user)"
        @keydown.space.prevent="select(user)"
      >
        <strong>{{ user.email }}</strong>
        <span>{{ user.role }}</span>
        <span class="status" :class="user.status.toLowerCase()">{{ statusText(user.status) }}</span>
        <button type="button" :disabled="updatingUserId === user.id" @click.stop="toggle(user)">
          {{ user.status === 'ACTIVE' ? '禁用' : '启用' }}
        </button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { AdminUser } from '@/api/admin';

defineProps<{
  users: AdminUser[];
  updatingUserId: number | null;
  selectedUserId: number | null;
}>();

const emit = defineEmits<{
  selectUser: [user: AdminUser];
  updateStatus: [user: AdminUser, status: 'ACTIVE' | 'DISABLED'];
}>();

function select(user: AdminUser) {
  emit('selectUser', user);
}

function toggle(user: AdminUser) {
  emit('updateStatus', user, user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE');
}

function statusText(status: string) {
  if (status === 'ACTIVE') return '启用';
  if (status === 'DISABLED') return '禁用';
  return status;
}
</script>

<style scoped>
.user-table {
  display: grid;
  gap: 14px;
}

header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 12px;
}

header p,
header h2 {
  margin: 0;
}

header p,
header span {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

header h2 {
  margin-top: 5px;
  color: var(--color-ink);
  font-size: 20px;
}

.table {
  overflow: auto;
  border: 1px solid var(--color-line);
  border-radius: 18px;
  background: #ffffff;
  box-shadow: var(--shadow-card);
}

.row {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 90px 90px 82px;
  gap: 12px;
  align-items: center;
  min-width: 560px;
  padding: 11px 13px;
  border-bottom: 1px solid var(--color-line);
}

.row:last-child {
  border-bottom: 0;
}

.row:not(.head) {
  cursor: pointer;
}

.row:not(.head):hover,
.row.selected {
  background: #eef5ff;
}

.row.selected {
  box-shadow: inset 3px 0 0 rgba(37, 99, 235, 0.64);
}

.row.head {
  background: #f8fbff;
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

.row strong {
  overflow-wrap: anywhere;
  color: var(--color-ink);
  font-size: 13px;
}

.row span {
  color: var(--color-muted);
  font-size: 13px;
}

.status {
  justify-self: start;
  padding: 4px 7px;
  border-radius: 999px;
  font-weight: 900;
}

.status.active {
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
}

.status.disabled {
  background: rgba(190, 18, 60, 0.1);
  color: var(--color-danger);
}

button {
  height: 32px;
  border: 1px solid var(--color-line);
  border-radius: 12px;
  background: #ffffff;
  color: var(--color-brand-dark);
  font-weight: 900;
}
</style>
