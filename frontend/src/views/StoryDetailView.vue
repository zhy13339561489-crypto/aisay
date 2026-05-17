<template>
  <section class="detail-page">
    <el-skeleton v-if="storyStore.isLoading && !story" :rows="10" animated />

    <template v-else-if="story">
      <header class="detail-hero">
        <div>
          <p class="eyebrow">Story Detail</p>
          <h1>{{ story.title }}</h1>
          <div class="tag-row">
            <el-tag v-if="story.genre" effect="plain">{{ story.genre }}</el-tag>
            <el-tag v-if="story.style" type="success" effect="plain">{{ story.style }}</el-tag>
            <el-tag type="warning" effect="light">{{ statusLabel(story.status) }}</el-tag>
          </div>
        </div>
        <div class="hero-actions">
          <el-button @click="router.push('/stories')">返回列表</el-button>
          <el-button type="primary" @click="openEditDialog">编辑</el-button>
          <el-button type="success" plain @click="openReviseDialog">大纲修改</el-button>
          <el-button type="warning" plain @click="ElMessage.info('封面生成将在后续 AI 图片阶段接入')">
            生成封面
          </el-button>
          <el-button type="danger" plain @click="confirmDelete">删除</el-button>
        </div>
      </header>

      <section class="summary-card">
        <h2>故事摘要</h2>
        <p>{{ story.synopsis || '暂无摘要。' }}</p>
      </section>

      <section class="content-grid">
        <article class="panel script-panel">
          <h2>剧情大纲</h2>
          <p class="script-text">{{ story.fullContent || '暂无剧情大纲。' }}</p>
        </article>

        <article class="panel">
          <h2>主要角色设定</h2>
          <div v-if="story.characters.length > 0" class="character-grid">
            <CharacterCard
              v-for="character in story.characters"
              :key="character.id"
              :character="character"
            />
          </div>
          <el-empty v-else description="暂无角色设定" />
        </article>
      </section>
    </template>

    <el-empty v-else description="漫剧不存在或无权访问">
      <el-button type="primary" @click="router.push('/stories')">返回列表</el-button>
    </el-empty>

    <el-dialog v-model="editDialogVisible" title="编辑漫剧信息" width="520px">
      <el-form label-position="top" :model="editForm">
        <el-form-item label="标题">
          <el-input v-model.trim="editForm.title" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="类型">
          <el-input v-model.trim="editForm.genre" maxlength="100" />
        </el-form-item>
        <el-form-item label="风格">
          <el-input v-model.trim="editForm.style" maxlength="100" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input
            v-model="editForm.synopsis"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 8 }"
            maxlength="5000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="isSaving" @click="saveStory">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reviseDialogVisible" title="大纲修改意见" width="560px">
      <el-form label-position="top">
        <el-form-item label="请输入你希望如何修改剧情大纲">
          <el-input
            v-model="reviseSuggestion"
            type="textarea"
            :autosize="{ minRows: 5, maxRows: 10 }"
            maxlength="5000"
            show-word-limit
            placeholder="例如：加强反派动机，把第二幕改得更悬疑，增加女主和主角的情感冲突。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviseDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="isRevising" @click="submitOutlineRevision">提交修改</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import CharacterCard from '../components/story/CharacterCard.vue';
import { useStoryStore } from '../stores/storyStore';

const props = defineProps<{
  id: string;
}>();

const router = useRouter();
const storyStore = useStoryStore();
const editDialogVisible = ref(false);
const reviseDialogVisible = ref(false);
const isSaving = ref(false);
const isRevising = ref(false);
const reviseSuggestion = ref('');
const editForm = reactive({
  title: '',
  genre: '',
  style: '',
  synopsis: '',
});

const story = computed(() => storyStore.currentStory);

onMounted(loadStory);

watch(
  () => props.id,
  () => {
    loadStory();
  },
);

async function loadStory() {
  const storyId = Number(props.id);
  if (!Number.isFinite(storyId) || storyId <= 0) {
    router.replace('/stories');
    return;
  }

  await storyStore.fetchStoryDetail(storyId);
}

function openEditDialog() {
  if (!story.value) {
    return;
  }
  editForm.title = story.value.title;
  editForm.genre = story.value.genre || '';
  editForm.style = story.value.style || '';
  editForm.synopsis = story.value.synopsis || '';
  editDialogVisible.value = true;
}

async function saveStory() {
  if (!story.value) {
    return;
  }

  isSaving.value = true;
  try {
    await storyStore.updateStory(story.value.id, {
      title: editForm.title,
      genre: editForm.genre,
      style: editForm.style,
      synopsis: editForm.synopsis,
    });
    ElMessage.success('漫剧信息已更新');
    editDialogVisible.value = false;
  } finally {
    isSaving.value = false;
  }
}

function openReviseDialog() {
  reviseSuggestion.value = '';
  reviseDialogVisible.value = true;
}

async function submitOutlineRevision() {
  if (!story.value) {
    return;
  }

  const suggestion = reviseSuggestion.value.trim();
  if (!suggestion) {
    ElMessage.warning('请输入大纲修改意见');
    return;
  }

  isRevising.value = true;
  try {
    await storyStore.reviseStoryOutline(story.value.id, { suggestion });
    ElMessage.success('大纲修改请求已提交');
    reviseDialogVisible.value = false;
  } finally {
    isRevising.value = false;
  }
}

async function confirmDelete() {
  if (!story.value) {
    return;
  }

  try {
    await ElMessageBox.confirm('删除后该漫剧不可恢复，确定继续吗？', '删除漫剧', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  await storyStore.deleteStory(story.value.id);
  ElMessage.success('漫剧已删除');
  router.push('/stories');
}

function statusLabel(status?: string) {
  if (status === 'published') {
    return '已发布';
  }
  if (status === 'archived') {
    return '已归档';
  }
  return '草稿';
}
</script>

<style scoped lang="scss">
.detail-page {
  display: grid;
  gap: 20px;
}

.detail-hero,
.summary-card,
.panel {
  border: 1px solid rgba(23, 37, 84, 0.1);
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.1);
}

.detail-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 34px;
  background:
    radial-gradient(circle at top right, rgba(29, 78, 216, 0.16), transparent 24rem),
    linear-gradient(135deg, rgba(255, 247, 237, 0.95), rgba(240, 249, 255, 0.92));
}

.eyebrow {
  margin: 0 0 12px;
  color: #1d4ed8;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

h1 {
  margin: 0 0 14px;
  color: #172554;
  font-size: clamp(34px, 6vw, 64px);
}

h2,
h3 {
  margin: 0;
  color: #172554;
}

.tag-row,
.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.summary-card,
.panel {
  padding: 24px;
}

.summary-card p,
.script-text {
  color: #475569;
  line-height: 1.8;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(320px, 0.95fr);
  gap: 20px;
}

.script-text {
  margin: 16px 0 0;
  white-space: pre-wrap;
}

.character-grid {
  display: grid;
  gap: 14px;
  margin-top: 16px;
}

@media (max-width: 900px) {
  .detail-hero,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .detail-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
