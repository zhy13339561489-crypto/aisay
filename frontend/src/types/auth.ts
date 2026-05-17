export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  userId: number;
  username: string;
}

export interface UserProfileResponse {
  id: number;
  username: string;
  email: string;
  avatarPath?: string;
  createdAt: string;
}

export interface UserUpdateRequest {
  username?: string;
  email?: string;
  avatarPath?: string;
}
