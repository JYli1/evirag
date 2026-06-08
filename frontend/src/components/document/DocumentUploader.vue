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
      <article
        v-for="doc in documents"
        :key="doc.id"
        class="document-item"
        :class="{ failed: doc.parseStatus === 'FAILED' }"
        @mouseenter="showChunks(doc)"
        @mouseleave="hideChunks"
        @focusin="showChunks(doc)"
      >
        <div class="document-row">
          <div class="document-copy">
            <strong>{{ doc.originalFilename }}</strong>
            <small>{{ formatSize(doc.fileSizeBytes) }} · {{ doc.chunkCount }} 个切片</small>
          </div>
          <div class="document-actions">
            <span class="status" :class="doc.parseStatus.toLowerCase()">{{ statusText(doc.parseStatus) }}</span>
            <button
              class="delete-button"
              type="button"
              :disabled="deletingDocumentId === doc.id"
              :title="`删除 ${doc.originalFilename}`"
              @click.stop="confirmDelete(doc)"
            >
              {{ deletingDocumentId === doc.id ? '删' : '×' }}
            </button>
          </div>
        </div>

        <p v-if="doc.parseStatus === 'FAILED'" class="raw-error">
          {{ doc.errorStage || '处理失败' }}：{{ doc.rawErrorSummary || doc.errorMessage || '未返回原始错误' }}
        </p>

        <div v-if="shouldShowPreview(doc)" class="chunk-preview">
          <div class="preview-head">
            <span>切片预览</span>
            <small>{{ previewHint(doc) }}</small>
          </div>
          <p v-if="loadingChunkIds[doc.id]" class="preview-note">正在读取切片...</p>
          <p v-else-if="chunkErrors[doc.id]" class="preview-note error">{{ chunkErrors[doc.id] }}</p>
          <p v-else-if="!chunksFor(doc.id).length" class="preview-note">暂无可展示切片。</p>
          <div v-else class="chunk-list">
            <article v-for="chunk in chunksFor(doc.id).slice(0, 5)" :key="chunk.id" class="chunk-card">
              <span>#{{ chunk.chunkIndex + 1 }} {{ chunk.sourceLocation || '' }}</span>
              <p>{{ chunk.content }}</p>
            </article>
            <small v-if="chunksFor(doc.id).length > 5" class="more-note">
              还有 {{ chunksFor(doc.id).length - 5 }} 个切片未展示
            </small>
          </div>
        </div>
      </article>
      <p v-if="!documents.length" class="empty">还没有上传文档。</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';

import { apiErrorMessage } from '@/api/http';
import { listDocumentChunks, type DocumentChunk, type KnowledgeDocument } from '@/api/document';

defineProps<{
  knowledgeBaseId: number | null;
  documents: KnowledgeDocument[];
  uploading: boolean;
  deletingDocumentId: number | null;
}>();

const emit = defineEmits<{
  upload: [file: File];
  delete: [document: KnowledgeDocument];
}>();

const hoveredDocumentId = ref<number | null>(null);
const chunkCache = ref<Record<number, DocumentChunk[]>>({});
const loadingChunkIds = ref<Record<number, boolean>>({});
const chunkErrors = ref<Record<number, string>>({});

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file) {
    emit('upload', file);
  }
  input.value = '';
}

async function showChunks(document: KnowledgeDocument) {
  hoveredDocumentId.value = document.id;
  if (document.parseStatus !== 'READY' || document.chunkCount <= 0) {
    return;
  }
  if (chunkCache.value[document.id] || loadingChunkIds.value[document.id]) {
    return;
  }
  loadingChunkIds.value = { ...loadingChunkIds.value, [document.id]: true };
  chunkErrors.value = { ...chunkErrors.value, [document.id]: '' };
  try {
    const chunks = await listDocumentChunks(document.id);
    chunkCache.value = { ...chunkCache.value, [document.id]: chunks };
  } catch (err) {
    chunkErrors.value = { ...chunkErrors.value, [document.id]: apiErrorMessage(err) };
  } finally {
    loadingChunkIds.value = { ...loadingChunkIds.value, [document.id]: false };
  }
}

function hideChunks() {
  hoveredDocumentId.value = null;
}

function confirmDelete(document: KnowledgeDocument) {
  const confirmed = window.confirm(`确定删除文档「${document.originalFilename}」吗？`);
  if (confirmed) {
    emit('delete', document);
  }
}

function shouldShowPreview(document: KnowledgeDocument) {
  if (hoveredDocumentId.value !== document.id) {
    return false;
  }
  return document.parseStatus === 'READY' && (document.chunkCount > 0 || Boolean(chunkErrors.value[document.id]));
}

function chunksFor(documentId: number) {
  return chunkCache.value[documentId] || [];
}

function previewHint(document: KnowledgeDocument) {
  if (document.chunkCount <= 5) {
    return `${document.chunkCount} 个切片`;
  }
  return `前 5 / 共 ${document.chunkCount} 个`;
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
  cursor: pointer;
  font-size: 12px;
  font-weight: 900;
}

.upload-button.disabled {
  cursor: not-allowed;
  opacity: 0.52;
}

.document-list {
  display: grid;
  gap: 8px;
}

.document-item {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.72);
}

.document-item.failed {
  border-color: rgba(178, 74, 63, 0.22);
  background: rgba(255, 248, 246, 0.84);
}

.document-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 9px;
}

.document-copy {
  min-width: 0;
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

.document-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.status {
  padding: 3px 7px;
  border-radius: var(--radius-sm);
  background: rgba(72, 111, 141, 0.1);
  color: var(--color-info);
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
}

.status.ready {
  background: rgba(31, 122, 87, 0.1);
  color: var(--color-brand-dark);
}

.status.processing {
  background: rgba(164, 112, 38, 0.12);
  color: #805820;
}

.status.failed {
  background: rgba(178, 74, 63, 0.1);
  color: var(--color-danger);
}

.delete-button {
  width: 26px;
  height: 26px;
  display: inline-grid;
  place-items: center;
  border: 1px solid rgba(178, 74, 63, 0.28);
  border-radius: var(--radius-sm);
  background: #ffffff;
  color: var(--color-danger);
  cursor: pointer;
  font-size: 18px;
  font-weight: 900;
  line-height: 1;
}

.delete-button:disabled {
  cursor: progress;
  opacity: 0.58;
}

.raw-error,
.empty,
.preview-note,
.more-note {
  margin: 0;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.55;
}

.raw-error {
  max-height: 54px;
  overflow: hidden;
  color: rgba(123, 52, 45, 0.72);
}

.chunk-preview {
  display: grid;
  gap: 9px;
  padding: 10px;
  border: 1px solid rgba(31, 122, 87, 0.16);
  border-radius: var(--radius-sm);
  background: #fbfdf9;
  box-shadow: inset 3px 0 0 rgba(31, 122, 87, 0.22);
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.preview-head span {
  color: var(--color-brand-dark);
  font-size: 12px;
  font-weight: 900;
}

.preview-head small {
  color: var(--color-muted);
  font-size: 11px;
}

.preview-note.error {
  color: var(--color-danger);
}

.chunk-list {
  display: grid;
  gap: 8px;
}

.chunk-card {
  display: grid;
  gap: 4px;
  padding: 8px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: #ffffff;
}

.chunk-card span {
  color: var(--color-muted);
  font-size: 11px;
  font-weight: 900;
}

.chunk-card p {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
  color: var(--color-ink);
  font-size: 12px;
  line-height: 1.58;
  overflow-wrap: anywhere;
}
</style>
