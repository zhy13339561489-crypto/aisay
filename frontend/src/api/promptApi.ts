import request from './index';
import type { ApiResponse } from '../types/auth';
import type { AiPrompt, AiPromptRequest } from '../types/prompt';

function unwrap<T>(response: ApiResponse<T>): T {
  return response.data;
}

export async function getPrompts(params?: { category?: string; enabled?: boolean; keyword?: string }) {
  const response = await request.get<ApiResponse<AiPrompt[]>>('/api/prompts', {
    params,
  });
  return unwrap(response.data);
}

export async function getPrompt(id: number) {
  const response = await request.get<ApiResponse<AiPrompt>>(`/api/prompts/${id}`);
  return unwrap(response.data);
}

export async function createPrompt(data: AiPromptRequest) {
  const response = await request.post<ApiResponse<AiPrompt>>('/api/prompts', data);
  return unwrap(response.data);
}

export async function updatePrompt(id: number, data: AiPromptRequest) {
  const response = await request.put<ApiResponse<AiPrompt>>(`/api/prompts/${id}`, data);
  return unwrap(response.data);
}

export async function deletePrompt(id: number) {
  await request.delete<ApiResponse<null>>(`/api/prompts/${id}`);
}
