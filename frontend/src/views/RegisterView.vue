<template>
  <main class="auth-page">
    <section class="auth-card">
      <p class="eyebrow">New Creator</p>
      <h1>注册账号</h1>
      <p>阶段十会接入真实注册表单。当前页面用于验证 `/register` 路由和访客页面跳转。</p>
      <el-button type="primary" size="large" @click="$router.push('/login')">返回登录</el-button>
    </section>
  </main>
</template>

<style scoped lang="scss">
.auth-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at 20% 20%, rgba(29, 78, 216, 0.2), transparent 24rem),
    radial-gradient(circle at 80% 70%, rgba(249, 115, 22, 0.18), transparent 22rem),
    #f8fafc;
}

.auth-card {
  width: min(100%, 460px);
  padding: 38px;
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 24px 90px rgba(15, 23, 42, 0.14);
}

.eyebrow {
  margin: 0 0 12px;
  color: #1d4ed8;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

h1 {
  margin: 0 0 12px;
  color: #172554;
  font-size: 42px;
}

p {
  color: #475569;
  line-height: 1.8;
}
</style>
