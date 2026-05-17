import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import * as authApi from '../api/authApi';
import type { RegisterRequest, UserProfileResponse, UserUpdateRequest } from '../types/auth';

function readStoredUser() {
  const rawUser = localStorage.getItem('userInfo');
  if (!rawUser) {
    return null;
  }

  try {
    return JSON.parse(rawUser) as UserProfileResponse;
  } catch {
    localStorage.removeItem('userInfo');
    return null;
  }
}

function isUnauthorized(error: unknown) {
  return (
    typeof error === 'object' &&
    error !== null &&
    'response' in error &&
    (error as { response?: { status?: number } }).response?.status === 401
  );
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '');
  const userInfo = ref<UserProfileResponse | null>(readStoredUser());
  const isLoggedIn = computed(() => Boolean(token.value));

  function persistToken(nextToken: string) {
    token.value = nextToken;
    localStorage.setItem('token', nextToken);
  }

  function persistUser(nextUser: UserProfileResponse | null) {
    userInfo.value = nextUser;
    if (nextUser) {
      localStorage.setItem('userInfo', JSON.stringify(nextUser));
      return;
    }
    localStorage.removeItem('userInfo');
  }

  async function login(username: string, password: string) {
    const loginResult = await authApi.login({ username, password });
    persistToken(loginResult.token);

    persistUser({
      id: loginResult.userId,
      username: loginResult.username,
      email: '',
      createdAt: '',
    });

    try {
      const profile = await authApi.getProfile();
      persistUser(profile);
    } catch (error) {
      if (isUnauthorized(error)) {
        logout();
        throw error;
      }
    }

    return loginResult;
  }

  async function register(payload: RegisterRequest) {
    return authApi.register(payload);
  }

  async function fetchProfile() {
    const profile = await authApi.getProfile();
    persistUser(profile);
    return profile;
  }

  async function updateProfile(payload: UserUpdateRequest) {
    const profile = await authApi.updateProfile(payload);
    persistUser(profile);
    return profile;
  }

  function logout() {
    token.value = '';
    userInfo.value = null;
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    login,
    register,
    fetchProfile,
    updateProfile,
    logout,
  };
});
