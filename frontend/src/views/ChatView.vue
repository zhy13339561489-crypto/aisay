<template>
  <section class="page-card">
    <p class="eyebrow">Chat Studio</p>
    <h1>AI 漫剧对话</h1>
    <p class="summary">
      这里会承载阶段十一的会话列表、消息气泡和输入区。当前路由已支持
      <code>/chat</code> 与 <code>/chat/:sessionId</code>。
    </p>
    <el-alert
      v-if="sessionId"
      :title="`当前路由会话 ID：${sessionId}`"
      type="success"
      show-icon
      :closable="false"
    />
    <el-empty v-else description="阶段十一会在这里接入真实聊天体验" />
  </section>
</template>

<script setup lang="ts">
defineProps<{
  sessionId?: string;
}>();
</script>

<style scoped lang="scss">
.page-card {
  min-height: 520px;
  padding: 40px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.12);
}

.eyebrow {
  margin: 0 0 12px;
  color: #0f766e;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  color: #172554;
  font-size: clamp(32px, 6vw, 64px);
}

.summary {
  max-width: 680px;
  color: #475569;
  font-size: 18px;
  line-height: 1.8;
}
</style>
