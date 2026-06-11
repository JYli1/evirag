<template>
  <section class="document-uploader">
    <header>
      <span>文档</span>
      <label class="upload-button" :class="{ disabled: !knowledgeBaseId || uploading }">
        <input
          class="sr-only"
          type="file"
          accept=".pdf,.txt,.doc,.docx,.md"
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

      </article>
      <p v-if="!documents.length" class="empty">还没有上传文档。</p>
    </div>

    <Teleport to="body">
      <div v-if="activeDocument" class="chunk-preview-modal" @click.self="hideChunks">
        <div class="preview-content" :class="{ 'is-detail': selectedChunk }">
          <div class="preview-head">
            <div>
              <span>{{ selectedChunk ? '完整片段' : '切片预览' }}</span>
              <small>{{ selectedChunk ? chunkPosition(selectedChunk) : previewHint(activeDocument) }}</small>
            </div>
            <button type="button" class="close-button" @click="hideChunks" title="关闭预览">×</button>
          </div>
          <div class="preview-body">
            <p v-if="loadingChunkIds[activeDocument.id]" class="preview-note">正在读取切片...</p>
            <p v-else-if="chunkErrors[activeDocument.id]" class="preview-note error">
              {{ chunkErrors[activeDocument.id] }}
            </p>
            <p v-else-if="!chunksFor(activeDocument.id).length" class="preview-note">暂无可展示切片。</p>
            <div v-else-if="selectedChunk" class="chunk-detail">
              <button type="button" class="back-button" @click="selectedChunk = null">返回切片列表</button>
              <article class="chunk-detail-card">
                <header>
                  <span>#{{ selectedChunk.chunkIndex + 1 }}</span>
                  <small>{{ selectedChunk.sourceLocation || '未标注位置' }}</small>
                </header>
                <p>{{ selectedChunk.content }}</p>
              </article>
            </div>
            <div v-else class="chunk-list">
              <button
                v-for="chunk in chunksFor(activeDocument.id)"
                :key="chunk.id"
                class="chunk-card"
                type="button"
                @click="openChunkDetail(chunk)"
              >
                <span>#{{ chunk.chunkIndex + 1 }} {{ chunk.sourceLocation || '' }}</span>
                <p>{{ firstSentence(chunk.content) }}</p>
                <small>查看完整片段</small>
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

import { apiErrorMessage } from '@/api/http';
import { listDocumentChunks, type DocumentChunk, type KnowledgeDocument } from '@/api/document';

const props = defineProps<{
  // 当前选中的知识库，为空时不允许上传。
  knowledgeBaseId: number | null;
  // 当前知识库下的文档列表。
  documents: KnowledgeDocument[];
  // 是否正在上传文件。
  uploading: boolean;
  // 正在删除的文档 ID，用于禁用按钮和显示状态。
  deletingDocumentId: number | null;
}>();

const emit = defineEmits<{
  // 父组件负责真正调用 uploadDocument API。
  upload: [file: File];
  // 父组件负责删除并刷新列表。
  delete: [document: KnowledgeDocument];
}>();

// 当前打开切片预览的文档 ID。
const hoveredDocumentId = ref<number | null>(null);
// 切片缓存，避免重复打开同一文档预览时反复请求后端。
const chunkCache = ref<Record<number, DocumentChunk[]>>({});
// 每个文档的切片加载状态。
const loadingChunkIds = ref<Record<number, boolean>>({});
// 每个文档的切片加载错误。
const chunkErrors = ref<Record<number, string>>({});
// 当前在弹窗中查看完整内容的切片。
const selectedChunk = ref<DocumentChunk | null>(null);

const activeDocument = computed(() => {
  const documentId = hoveredDocumentId.value;
  if (documentId == null) {
    return null;
  }
  const document = props.documents.find((candidate) => candidate.id === documentId);
  if (!document || document.parseStatus !== 'READY' || document.chunkCount <= 0) {
    return null;
  }
  return document;
});

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file) {
    emit('upload', file);
  }
  // 清空 input，确保用户再次选择同一个文件也能触发 change。
  input.value = '';
}

async function showChunks(document: KnowledgeDocument) {
  hoveredDocumentId.value = document.id;
  selectedChunk.value = null;
  if (document.parseStatus !== 'READY' || document.chunkCount <= 0) {
    // 未完成索引或没有切片时不请求后端。
    return;
  }
  if (loadingChunkIds.value[document.id]) {
    return;
  }
  if (chunkCache.value[document.id]) {
    // 已有缓存则直接显示。
    return;
  }
  // 使用对象替换而不是原地赋值，确保 Vue 能稳定触发响应式更新。
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
  selectedChunk.value = null;
}

function openChunkDetail(chunk: DocumentChunk) {
  selectedChunk.value = chunk;
}

function confirmDelete(document: KnowledgeDocument) {
  // 删除是不可逆操作，前端先做一次确认。
  const confirmed = window.confirm(`确定删除文档「${document.originalFilename}」吗？`);
  if (confirmed) {
    emit('delete', document);
  }
}

function chunksFor(documentId: number) {
  return chunkCache.value[documentId] || [];
}

function previewHint(document: KnowledgeDocument) {
  return `${document.chunkCount} 个切片`;
}

function chunkPosition(chunk: DocumentChunk) {
  return chunk.sourceLocation || `#${chunk.chunkIndex + 1}`;
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
  // 卡片上展示短文件名，完整文件名放在 title 中，防止长文件名撑破布局。
  const dotIndex = filename.lastIndexOf('.');
  const extension = dotIndex > 0 ? filename.slice(dotIndex) : '';
  const base = dotIndex > 0 ? filename.slice(0, dotIndex) : filename;
  const suffix = extension.length <= 8 ? extension : '';
  return `${base.slice(0, 14)}...${suffix}`;
}

function firstSentence(content: string) {
  const normalized = content.replace(/\s+/g, ' ').trim();
  if (!normalized) {
    return '空片段';
  }
  const sentence = normalized.match(/^.*?[。！？!?\.]/)?.[0] || normalized;
  if (sentence.length <= 120) {
    return sentence;
  }
  return `${sentence.slice(0, 120)}...`;
}
</script>

<style scoped>
.document-uploader {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-xl);
  background:
    linear-gradient(90deg, rgba(18, 149, 190, 0.06), transparent),
    rgba(255, 255, 255, 0.58);
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
  border: 1px solid var(--color-strong-line);
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
  background: rgba(255, 255, 255, 0.68);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-base);
}

.document-item:hover {
  transform: translateY(-2px);
  border-color: var(--color-strong-line);
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
  background: rgba(18, 149, 190, 0.1);
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
}

.status.failed {
  background: rgba(180, 35, 58, 0.1);
  color: var(--color-danger);
}

.preview-button {
  padding: 4px 9px;
  border: 1px solid rgba(37, 90, 143, 0.2);
  border-radius: 8px;
  background: #e8f8fc;
  color: var(--color-brand-dark);
  cursor: pointer;
  font-size: 11px;
  font-weight: 700;
  box-shadow: var(--shadow-xs);
  transition: all var(--transition-fast);
}

.preview-button:hover {
  border-color: var(--color-brand);
  background: #dff5fa;
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
.preview-note {
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
  inset: 0;
  z-index: 3000;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(7, 26, 42, 0.56);
  backdrop-filter: blur(8px);
}

.preview-content {
  width: min(90vw, 720px);
  max-height: 85vh;
  display: grid;
  grid-template-rows: auto 1fr;
  background: rgba(255, 255, 255, 0.92);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-lg), 0 0 0 1px rgba(23, 32, 51, 0.05);
  overflow: hidden;
}

.preview-content.is-detail {
  width: min(92vw, 900px);
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
  background:
    linear-gradient(90deg, rgba(18, 149, 190, 0.08), transparent),
    rgba(255, 255, 255, 0.86);
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
  background: #e8f8fc;
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
  gap: 10px;
}

.chunk-card {
  width: 100%;
  display: grid;
  gap: 7px;
  padding: 12px 14px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.78);
  color: inherit;
  cursor: pointer;
  text-align: left;
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-fast);
}

.chunk-card:hover {
  border-color: var(--color-strong-line);
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
  margin: 0;
  color: var(--color-ink);
  font-size: 13px;
  line-height: 1.62;
  overflow-wrap: anywhere;
}

.chunk-card small {
  color: var(--color-brand-dark);
  font-size: 11px;
  font-weight: 800;
}

.chunk-detail {
  display: grid;
  gap: 12px;
}

.back-button {
  justify-self: start;
  padding: 7px 11px;
  border: 1px solid rgba(37, 90, 143, 0.2);
  border-radius: var(--radius-md);
  background: #e8f8fc;
  color: var(--color-brand-dark);
  cursor: pointer;
  font-size: 12px;
  font-weight: 800;
}

.back-button:hover {
  border-color: var(--color-brand);
  background: #dff5fa;
}

.chunk-detail-card {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  max-height: min(64vh, 580px);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background: #ffffff;
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

.chunk-detail-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--color-line);
  background: var(--color-panel-muted);
}

.chunk-detail-card header span {
  color: var(--color-brand-dark);
  font-size: 12px;
  font-weight: 900;
}

.chunk-detail-card header small {
  color: var(--color-muted);
  font-size: 11px;
  font-weight: 800;
}

.chunk-detail-card p {
  margin: 0;
  padding: 16px;
  overflow-y: auto;
  color: var(--color-ink);
  font-size: 13px;
  line-height: 1.75;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
</style>
