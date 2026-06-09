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
      >
        <div class="document-row document-actions-row">
          <div class="document-actions">
            <button
              v-if="doc.parseStatus === 'READY' && doc.chunkCount > 0"
              class="preview-button"
              type="button"
              :title="`预览 ${doc.originalFilename} 的切片`"
              @click.stop="showChunks(doc)"
            >
              预览
            </button>
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
        <div class="document-copy" :title="doc.originalFilename">
          <strong>{{ displayFilename(doc.originalFilename) }}</strong>
          <small>{{ formatSize(doc.fileSizeBytes) }} · {{ doc.chunkCount }} 个切片</small>
        </div>

        <p v-if="doc.parseStatus === 'FAILED'" class="raw-error">
          {{ doc.errorStage || '处理失败' }}：{{ doc.rawErrorSummary || doc.errorMessage || '未返回原始错误' }}
        </p>

        <div v-if="shouldShowPreview(doc)" class="chunk-preview-modal" @click.self="hideChunks">
          <div class="preview-content">
            <div class="preview-head">
              <div>
                <span>切片预览</span>
                <small>{{ previewHint(doc) }}</small>
              </div>
              <button type="button" class="close-button" @click="hideChunks" title="关闭预览">×</button>
            </div>
            <div class="preview-body">
              <p v-if="loadingChunkIds[doc.id]" class="preview-note">正在读取切片...</p>
              <p v-else-if="chunkErrors[doc.id]" class="preview-note error">{{ chunkErrors[doc.id] }}</p>
              <p v-else-if="!chunksFor(doc.id).length" class="preview-note">暂无可展示切片。</p>
              <div v-else class="chunk-list">
                <article v-for="chunk in chunksFor(doc.id).slice(0, 10)" :key="chunk.id" class="chunk-card">
                  <span>#{{ chunk.chunkIndex + 1 }} {{ chunk.sourceLocation || '' }}</span>
                  <p>{{ chunk.content }}</p>
                </article>
                <small v-if="chunksFor(doc.id).length > 10" class="more-note">
                  还有 {{ chunksFor(doc.id).length - 10 }} 个切片未展示
                </small>
              </div>
            </div>
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
  if (loadingChunkIds.value[document.id]) {
    return;
  }
  if (chunkCache.value[document.id]) {
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
  return document.parseStatus === 'READY' && document.chunkCount > 0;
}

function chunksFor(documentId: number) {
  return chunkCache.value[documentId] || [];
}

function previewHint(document: KnowledgeDocument) {
  if (document.chunkCount <= 10) {
    return `${document.chunkCount} 个切片`;
  }
  return `前 10 / 共 ${document.chunkCount} 个`;
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

function displayFilename(filename: string) {
  if (filename.length <= 18) {
    return filename;
  }
  const dotIndex = filename.lastIndexOf('.');
  const extension = dotIndex > 0 ? filename.slice(dotIndex) : '';
  const base = dotIndex > 0 ? filename.slice(0, dotIndex) : filename;
  const suffix = extension.length <= 8 ? extension : '';
  return `${base.slice(0, 14)}...${suffix}`;
}
</script>

<style scoped>
.document-uploader {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-xl);
  background: var(--color-panel-muted);
  box-shadow: var(--shadow-sm);
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
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.upload-button {
  padding: 8px 14px;
  border: 1px solid var(--color-brand);
  border-radius: 999px;
  background: var(--color-brand-dark);
  color: #ffffff;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-fast);
}

.upload-button:hover:not(.disabled) {
  background: var(--color-brand);
  box-shadow: var(--shadow-glow);
  transform: translateY(-1px);
}

.upload-button.disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.document-list {
  display: grid;
  gap: 10px;
}

.document-item {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-base);
}

.document-item:hover {
  transform: translateY(-2px);
  border-color: var(--color-brand-light);
  box-shadow: var(--shadow-md);
}

.document-item.failed {
  border-color: rgba(180, 35, 58, 0.22);
  background: rgba(255, 248, 249, 0.86);
}

.document-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.document-copy {
  min-width: 0;
  display: grid;
  gap: 4px;
  padding-top: 2px;
}

.document-item strong {
  display: block;
  max-width: 100%;
  overflow: hidden;
  color: var(--color-ink);
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-item small {
  display: block;
  color: var(--color-muted);
  font-size: 11px;
}

.document-actions {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 6px;
}

.status {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(52, 107, 132, 0.1);
  color: var(--color-info);
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
  box-shadow: var(--shadow-xs);
}

.status.ready {
  background: rgba(22, 116, 91, 0.1);
  color: var(--color-success);
}

.status.processing {
  background: rgba(163, 106, 31, 0.12);
  color: var(--color-accent);
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
}

.status.failed {
  background: rgba(180, 35, 58, 0.1);
  color: var(--color-danger);
}

.preview-button {
  padding: 4px 9px;
  border: 1px solid rgba(37, 90, 143, 0.2);
  border-radius: 8px;
  background: #eef5fb;
  color: var(--color-brand-dark);
  cursor: pointer;
  font-size: 11px;
  font-weight: 700;
  box-shadow: var(--shadow-xs);
  transition: all var(--transition-fast);
}

.preview-button:hover {
  border-color: var(--color-brand);
  background: #e1edf6;
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.delete-button {
  width: 28px;
  height: 28px;
  display: inline-grid;
  place-items: center;
  border: 1px solid rgba(180, 35, 58, 0.22);
  border-radius: var(--radius-md);
  background: #ffffff;
  color: var(--color-danger);
  cursor: pointer;
  font-size: 18px;
  font-weight: 700;
  line-height: 1;
  box-shadow: var(--shadow-xs);
  transition: all var(--transition-fast);
}

.delete-button:hover:not(:disabled) {
  border-color: var(--color-danger);
  background: #fff1f3;
  box-shadow: var(--shadow-sm);
  transform: scale(1.05);
}

.delete-button:disabled {
  cursor: progress;
  opacity: 0.5;
}

.raw-error,
.empty,
.preview-note,
.more-note {
  margin: 0;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.6;
}

.raw-error {
  max-height: 60px;
  overflow: hidden;
  padding: 8px 10px;
  background: #fff1f3;
  color: #8f1f2f;
  border-radius: var(--radius-sm);
  font-size: 11px;
}

.chunk-preview-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(8px);
  animation: fadeIn 0.2s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.preview-content {
  width: min(90vw, 720px);
  max-height: 85vh;
  display: grid;
  grid-template-rows: auto 1fr;
  background: #ffffff;
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-lg), 0 0 0 1px rgba(23, 32, 51, 0.05);
  overflow: hidden;
  animation: slideIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(-30px) scale(0.9);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.preview-body {
  overflow-y: auto;
  padding: 20px;
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-line);
  background: var(--color-panel-muted);
}

.preview-head > div {
  display: flex;
  align-items: center;
  gap: 12px;
}

.preview-head span {
  color: var(--color-brand-dark);
  font-size: 14px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.preview-head small {
  padding: 4px 10px;
  background: #eef5fb;
  color: var(--color-brand-dark);
  font-size: 11px;
  font-weight: 700;
  border-radius: 999px;
  box-shadow: var(--shadow-xs);
}

.close-button {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: #ffffff;
  color: var(--color-muted);
  cursor: pointer;
  font-size: 22px;
  font-weight: 700;
  line-height: 1;
  box-shadow: var(--shadow-xs);
  transition: all var(--transition-fast);
}

.close-button:hover {
  border-color: var(--color-danger);
  background: #fff1f3;
  color: var(--color-danger);
  box-shadow: var(--shadow-sm);
  transform: rotate(90deg);
}

.preview-note {
  padding: 16px;
  text-align: center;
  color: var(--color-muted);
  background: var(--color-panel-muted);
  border-radius: var(--radius-md);
  border: 1px dashed var(--color-line);
}

.preview-note.error {
  color: #8f1f2f;
  background: #fff1f3;
  border-color: rgba(180, 35, 58, 0.22);
}

.chunk-list {
  display: grid;
  gap: 12px;
}

.chunk-card {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background: #ffffff;
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-fast);
}

.chunk-card:hover {
  border-color: var(--color-brand-light);
  box-shadow: var(--shadow-md);
  transform: translateX(4px);
}

.chunk-card span {
  color: var(--color-brand);
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.chunk-card p {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 5;
  color: var(--color-ink);
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.more-note {
  padding: 12px;
  text-align: center;
  color: var(--color-muted);
  background: #eef5fb;
  border-radius: var(--radius-md);
  font-weight: 600;
}

.chunk-preview {
  display: grid;
  gap: 9px;
  padding: 10px;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 12px;
  background: #ffffff;
  box-shadow: inset 3px 0 0 rgba(37, 99, 235, 0.3);
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--color-line);
  background: var(--color-panel-muted);
}

.preview-head > div {
  display: flex;
  align-items: center;
  gap: 12px;
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
  border-radius: 10px;
  background: var(--color-panel-muted);
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
