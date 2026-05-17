<template>
  <section class="page-card story-list">
    <div>
      <p class="eyebrow">Story Library</p>
      <h1>漫剧列表</h1>
      <p>阶段十二会在这里展示当前用户生成的漫剧卡片、分页、筛选和删除入口。</p>
    </div>
    <el-empty description="暂无列表数据，等待 Story Store 接入" />
  </section>
</template>

<style scoped lang="scss">
.page-card {
  min-height: 520px;
  padding: 40px;
  border-radius: 28px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.9), rgba(239, 246, 255, 0.9));
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.12);
}

.story-list {
  display: grid;
  gap: 32px;
}

.eyebrow {
  margin: 0 0 12px;
  color: #c2410c;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

h1 {
  margin: 0 0 12px;
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
