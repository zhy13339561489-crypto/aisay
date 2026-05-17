<template>
  <section class="chat-workspace">
    <SessionList
      :sessions="chatStore.sessions"
      :current-session-id="chatStore.currentSessionId"
      :loading="chatStore.isLoading"
      :creating="isCreating"
      @create="handleCreateSession"
      @select="handleSelectSession"
      @delete="handleDeleteSession"
    />

    <section class="chat-panel">
      <header class="chat-header">
        <div>
          <p class="eyebrow">Chat Studio</p>
          <h1>{{ chatStore.currentSession?.title || 'AI 漫剧对话' }}</h1>
        </div>
        <el-tag :type="chatStore.isConnected ? 'success' : 'info'" effect="plain">
          {{ chatStore.isConnected ? 'WebSocket 已连接' : 'HTTP 模式' }}
        </el-tag>
      </header>

      <el-alert
        v-if="pageError"
        class="page-error"
        :title="pageError"
        type="error"
        show-icon
        :closable="false"
      >
        <template #default>
          <el-button size="small" type="primary" plain @click="initializeChatPage">重新加载</el-button>
        </template>
      </el-alert>

      <div ref="messageContainerRef" class="message-list">
        <template v-if="chatStore.messages.length > 0">
          <MessageBubble
            v-for="message in chatStore.messages"
            :key="message.id"
            :role="message.role"
            :content="message.content"
            :timestamp="message.createdAt"
          />
        </template>

        <div v-else class="welcome-card">
          <p>把脑洞丢进来。</p>
          <h2>从一句设定，长出一部漫剧。</h2>
          <span>试试：“我想写一个赛博都市里寻找失落星核的少年故事。”</span>
        </div>
      </div>

      <InputArea
        :loading="chatStore.isSending"
        :disabled="chatStore.isLoading || Boolean(pageError)"
        @send="handleSendMessage"
      />
    </section>
  </section>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import MessageBubble from '../components/chat/MessageBubble.vue';
import InputArea from '../components/chat/InputArea.vue';
import SessionList from '../components/chat/SessionList.vue';
import { useChatStore } from '../stores/chatStore';

const props = defineProps<{
  sessionId?: string;
}>();

const router = useRouter();
const chatStore = useChatStore();
const messageContainerRef = ref<HTMLElement>();
const isCreating = ref(false);
const pageError = ref('');

onMounted(initializeChatPage);

watch(
  () => props.sessionId,
  async (nextSessionId) => {
    await runSafely(() => syncRouteSession(nextSessionId));
  },
);

watch(
  () => chatStore.messages.length,
  async () => {
    await scrollToBottom();
  },
);

onBeforeUnmount(() => {
  chatStore.disconnectWebSocket();
});

async function initializeChatPage() {
  pageError.value = '';
  try {
    await chatStore.loadSessions();
    await syncRouteSession(props.sessionId);
  } catch (error) {
    pageError.value = getErrorMessage(error);
  }
}

async function syncRouteSession(rawSessionId?: string) {
  if (!rawSessionId) {
    return;
  }

  const sessionId = Number(rawSessionId);
  if (!Number.isFinite(sessionId) || sessionId <= 0) {
    router.replace('/chat');
    return;
  }

  if (chatStore.currentSessionId !== sessionId) {
    await chatStore.switchSession(sessionId);
    await scrollToBottom();
  }
}

async function handleCreateSession() {
  isCreating.value = true;
  pageError.value = '';
  try {
    const session = await chatStore.startNewSession();
    router.push(`/chat/${session.id}`);
  } catch (error) {
    pageError.value = getErrorMessage(error);
  } finally {
    isCreating.value = false;
  }
}

function handleSelectSession(sessionId: number) {
  if (sessionId !== chatStore.currentSessionId) {
    router.push(`/chat/${sessionId}`);
  }
}

async function handleDeleteSession(sessionId: number) {
  try {
    await ElMessageBox.confirm('删除后该会话将从列表中移除，确定继续吗？', '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  await runSafely(async () => {
    await chatStore.deleteSession(sessionId);
    if (!chatStore.currentSessionId) {
      router.push('/chat');
    }
  });
}

async function handleSendMessage(content: string) {
  const hadSession = Boolean(chatStore.currentSessionId);
  await runSafely(async () => {
    const response = await chatStore.sendMessage(content);
    if (!hadSession && chatStore.currentSessionId) {
      router.push(`/chat/${chatStore.currentSessionId}`);
    }
    if (response) {
      await scrollToBottom();
    }
  });
}

async function scrollToBottom() {
  await nextTick();
  const container = messageContainerRef.value;
  if (container) {
    container.scrollTop = container.scrollHeight;
  }
}

async function runSafely(action: () => Promise<void>) {
  pageError.value = '';
  try {
    await action();
  } catch (error) {
    pageError.value = getErrorMessage(error);
    ElMessage.error(pageError.value);
  }
}

function getErrorMessage(error: unknown) {
  if (
    typeof error === 'object' &&
    error !== null &&
    'response' in error &&
    (error as { response?: { data?: { message?: string } } }).response?.data?.message
  ) {
    return (error as { response: { data: { message: string } } }).response.data.message;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return '聊天页加载失败，请确认后端服务已启动且登录状态有效';
}
</script>

<style scoped lang="scss">
.chat-workspace {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 18px;
  min-height: calc(100vh - 132px);
}

.chat-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-height: 0;
  padding: 22px;
  border-radius: 30px;
  background:
    radial-gradient(circle at top right, rgba(29, 78, 216, 0.12), transparent 24rem),
    rgba(255, 255, 255, 0.78);
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.12);
}

.chat-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
}

.eyebrow {
  margin: 0 0 8px;
  color: #0f766e;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  color: #172554;
  font-size: clamp(28px, 4vw, 42px);
}

.message-list {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  padding: 18px 8px 18px 0;
  scroll-behavior: smooth;
}

.page-error {
  margin-bottom: 16px;
}

.welcome-card {
  display: grid;
  min-height: 100%;
  place-content: center;
  padding: 36px;
  border: 1px dashed rgba(15, 118, 110, 0.26);
  border-radius: 28px;
  text-align: center;
  background: rgba(255, 255, 255, 0.54);
}

.welcome-card p {
  margin: 0 0 10px;
  color: #f97316;
  font-weight: 800;
}

.welcome-card h2 {
  margin: 0 0 12px;
  color: #172554;
  font-size: clamp(28px, 5vw, 48px);
}

.welcome-card span {
  color: #64748b;
}

@media (max-width: 900px) {
  .chat-workspace {
    grid-template-columns: 1fr;
  }
}
</style>
