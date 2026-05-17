<template>
  <section class="page-card">
    <p class="eyebrow">Creator Profile</p>
    <h1>个人中心</h1>
    <p>阶段十三会在这里展示头像、账号信息、统计卡片和资料编辑弹窗。</p>
    <div class="stats">
      <el-card shadow="never">
        <strong>漫剧数量</strong>
        <span>待接入</span>
      </el-card>
      <el-card shadow="never">
        <strong>对话数量</strong>
        <span>待接入</span>
      </el-card>
    </div>
  </section>
</template>

<style scoped lang="scss">
.page-card {
  min-height: 520px;
  padding: 40px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.9);
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
  margin: 0 0 12px;
  color: #172554;
  font-size: clamp(32px, 6vw, 56px);
}

p {
  color: #475569;
  font-size: 18px;
  line-height: 1.8;
}

.stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 28px;
}

.stats strong,
.stats span {
  display: block;
}

.stats span {
  margin-top: 8px;
  color: #64748b;
}

@media (max-width: 640px) {
  .stats {
    grid-template-columns: 1fr;
  }
}
</style>
