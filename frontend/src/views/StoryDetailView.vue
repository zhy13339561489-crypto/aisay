<template>
  <section class="page-card">
    <p class="eyebrow">Story Detail</p>
    <h1>漫剧详情</h1>
    <p>
      当前路由作品 ID：<strong>{{ id }}</strong>。阶段十二会在这里展示摘要、角色卡片、场景列表和编辑入口。
    </p>
    <el-button type="primary" @click="$router.push('/stories')">返回列表</el-button>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  id: string;
}>();
</script>

<style scoped lang="scss">
.page-card {
  min-height: 520px;
  padding: 40px;
  border-radius: 28px;
  background:
    linear-gradient(135deg, rgba(255, 247, 237, 0.95), rgba(240, 249, 255, 0.92)),
    radial-gradient(circle at top right, rgba(249, 115, 22, 0.18), transparent 28rem);
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.12);
}

.eyebrow {
  margin: 0 0 12px;
  color: #1d4ed8;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

h1 {
  margin: 0 0 16px;
  color: #172554;
  font-size: clamp(32px, 6vw, 56px);
}

p {
  max-width: 680px;
  color: #475569;
  font-size: 18px;
  line-height: 1.8;
}
</style>
