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
        <div class="chat-actions">
          <el-button
            type="primary"
            plain
            :loading="storyStore.isGenerating"
            :disabled="!chatStore.currentSessionId"
            @click="handleGenerateStory"
          >
            生成剧情大纲
          </el-button>
          <el-tag :type="chatStore.isConnected ? 'success' : 'info'" effect="plain">
            {{ chatStore.isConnected ? 'WebSocket 已连接' : 'HTTP 模式' }}
          </el-tag>
        </div>
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

    <el-dialog v-model="outlineDialogVisible" title="生成剧情大纲" width="520px">
      <el-form ref="outlineFormRef" label-position="top" :model="outlineForm" :rules="outlineRules">
        <el-form-item label="漫剧大纲题材" prop="genre">
          <el-select
            v-model="outlineForm.genre"
            filterable
            allow-create
            default-first-option
            placeholder="请选择或输入题材"
          >
            <el-option label="科幻" value="科幻" />
            <el-option label="奇幻" value="奇幻" />
            <el-option label="悬疑" value="悬疑" />
            <el-option label="爱情" value="爱情" />
            <el-option label="热血" value="热血" />
            <el-option label="都市" value="都市" />
            <el-option label="校园" value="校园" />
          </el-select>
        </el-form-item>
        <el-form-item label="大致剧情（可选）" prop="plot">
          <el-input
            v-model="outlineForm.plot"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 8 }"
            maxlength="5000"
            show-word-limit
            placeholder="例如：主角在未来城市中意外获得读心能力，被卷入一场关于记忆交易的阴谋。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="outlineDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="storyStore.isGenerating" @click="submitStoryOutline">
          生成大纲
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { useRouter } from 'vue-router';
import MessageBubble from '../components/chat/MessageBubble.vue';
import InputArea from '../components/chat/InputArea.vue';
import SessionList from '../components/chat/SessionList.vue';
import { useChatStore } from '../stores/chatStore';
import { useStoryStore } from '../stores/storyStore';

const props = defineProps<{
  sessionId?: string;
}>();

const router = useRouter();
const chatStore = useChatStore();
const storyStore = useStoryStore();
const messageContainerRef = ref<HTMLElement>();
const isCreating = ref(false);
const pageError = ref('');
const outlineDialogVisible = ref(false);
const outlineFormRef = ref<FormInstance>();
const outlineForm = reactive({
  genre: '',
  plot: '',
});
const outlineRules: FormRules<typeof outlineForm> = {
  genre: [
    { required: true, message: '请选择或输入漫剧大纲题材', trigger: 'change' },
    { max: 100, message: '题材长度不能超过 100 个字符', trigger: 'change' },
  ],
  plot: [
    { max: 5000, message: '大致剧情长度不能超过 5000 个字符', trigger: 'blur' },
  ],
};

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

async function handleGenerateStory() {
  if (!chatStore.currentSessionId) {
    ElMessage.warning('请先创建或选择一个对话会话');
    return;
  }

  outlineForm.genre = '';
  outlineForm.plot = '';
  outlineDialogVisible.value = true;
}

async function submitStoryOutline() {
  if (!chatStore.currentSessionId || !outlineFormRef.value) {
    return;
  }

  const valid = await outlineFormRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }

  await runSafely(async () => {
    const story = await storyStore.generateStory({
      sessionId: chatStore.currentSessionId as number,
      genre: outlineForm.genre,
      plot: outlineForm.plot || undefined,
    });
    outlineDialogVisible.value = false;
    chatStore.addLocalAiMessage(`剧情大纲《${story.title}》已生成，你可以在详情页继续查看和编辑。`, chatStore.currentSessionId);
    ElMessage.success('剧情大纲生成成功');
    await scrollToBottom();
    router.push(`/story/${story.id}`);
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

.chat-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
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
