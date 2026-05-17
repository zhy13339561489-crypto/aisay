import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/chat',
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: {
      guestOnly: true,
    },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue'),
    meta: {
      guestOnly: true,
    },
  },
  {
    path: '/',
    component: () => import('../components/common/AppLayout.vue'),
    meta: {
      requiresAuth: true,
    },
    children: [
      {
        path: 'chat',
        name: 'chat',
        component: () => import('../views/ChatView.vue'),
      },
      {
        path: 'chat/:sessionId',
        name: 'chat-session',
        component: () => import('../views/ChatView.vue'),
        props: true,
      },
      {
        path: 'stories',
        name: 'stories',
        component: () => import('../views/StoryList.vue'),
      },
      {
        path: 'story/:id',
        name: 'story-detail',
        component: () => import('../views/StoryDetailView.vue'),
        props: true,
      },
      {
        path: 'user',
        name: 'user-center',
        component: () => import('../views/UserCenter.vue'),
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/chat',
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  const token = localStorage.getItem('token');

  if (to.meta.requiresAuth && !token) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath,
      },
    };
  }

  if (to.meta.guestOnly && token) {
    return '/chat';
  }

  return true;
});

export default router;
