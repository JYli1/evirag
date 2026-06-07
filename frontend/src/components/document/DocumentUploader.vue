<template>
  <section class="document-uploader">
    <header>
      <span>文档</span>
      <label class="upload-button" :class="{ disabled: !knowledgeBaseId || uploading }">
        <input
          class="sr-only"
          type="file"
          accept=".pdf,.txt,.docx,.md"
          :disabled="!knowledgeBaseId || uploading"
          @change="handleFileChange"
        />
        {{ uploading ? '上传中' : '上传' }}
      </label>
    </header>

    <div class="document-list">
      <article v-for="doc in documents" :key="doc.id" class="document-item">
        <div>
          <strong>{{ doc.originalFilename }}</strong>
          <small>{{ formatSize(doc.fileSizeBytes) }} · {{ doc.chunkCount }} 个切片</small>
        </div>
        <span class="status" :class="doc.parseStatus.toLowerCase()">{{ statusText(doc.parseStatus) }}</span>
        <p v-if="doc.parseStatus === 'FAILED'" class="raw-error">
          {{ doc.errorStage || '处理失败' }}：{{ doc.rawErrorSummary || doc.errorMessage || '未返回原始错误' }}
        </p>
      </article>
      <p v-if="!documents.length" class="empty">还没有上传文档。</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { KnowledgeDocument } from '@/api/document';

defineProps<{
  knowledgeBaseId: number | null;
  documents: KnowledgeDocument[];
  uploading: boolean;
}>();

const emit = defineEmits<{
  upload: [file: File];
}>();

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file) {
    emit('upload', file);
  }
  input.value = '';
}

function statusText(status: string) {
  if (status === 'READY') return '已就绪';
  if (status === 'PROCESSING') return '处理中';
  if (status === 'FAILED') return '失败';
  return status;
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
</script>

<style scoped>
.document-uploader {
  display: grid;
  gap: 12px;
}

header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

header span {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 900;
}

.upload-button {
  padding: 6px 10px;
  border: 1px solid var(--color-strong-line);
  border-radius: var(--radius-sm);
  background: #f8fbf8;
  color: var(--color-brand-dark);
  font-size: 12px;
  font-weight: 900;
}

.upload-button.disabled {
  opacity: 0.52;
}

.document-list {
  display: grid;
  gap: 8px;
}

.document-item {
  display: grid;
  gap: 7px;
  padding: 10px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.72);
}

.document-item strong {
  display: block;
  overflow-wrap: anywhere;
  color: var(--color-ink);
  font-size: 13px;
}

.document-item small {
  display: block;
  margin-top: 3px;
  color: var(--color-muted);
  font-size: 12px;
}

.status {
  justify-self: start;
  padding: 3px 7px;
  border-radius: var(--radius-sm);
  background: rgba(72, 111, 141, 0.1);
  color: var(--color-info);
  font-size: 12px;
  font-weight: 900;
}

.status.ready {
  background: rgba(31, 122, 87, 0.1);
  color: var(--color-brand-dark);
}

.status.failed {
  background: rgba(178, 74, 63, 0.1);
  color: var(--color-danger);
}

.raw-error,
.empty {
  margin: 0;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.55;
}

.raw-error {
  color: rgba(123, 52, 45, 0.72);
}
</style>
