<template>
  <main class="auth-page">
    <section class="auth-card">
      <p class="eyebrow">Welcome Back</p>
      <h1>登录创作台</h1>
      <p>阶段十会接入真实登录接口。当前可先写入临时 token，方便验证阶段九的路由守卫。</p>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="临时用户名">
          <el-input v-model="username" placeholder="例如：test" />
        </el-form-item>
        <el-button type="primary" size="large" @click="mockLogin">进入系统</el-button>
      </el-form>

      <RouterLink class="switch-link" to="/register">还没有账号？去注册</RouterLink>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();
const username = ref('创作者');

function mockLogin() {
  localStorage.setItem('token', 'phase-9-local-token');
  localStorage.setItem('userInfo', JSON.stringify({ username: username.value || '创作者' }));
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/chat';
  router.push(redirect);
}
</script>

<style scoped lang="scss">
.auth-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at 20% 20%, rgba(249, 115, 22, 0.22), transparent 24rem),
    radial-gradient(circle at 80% 10%, rgba(15, 118, 110, 0.18), transparent 22rem),
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
  color: #f97316;
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

.switch-link {
  display: inline-block;
  margin-top: 18px;
  color: #0f766e;
  font-weight: 700;
  text-decoration: none;
}
</style>
