import request from './index';
import type { ApiResponse } from '../types/auth';
import type { ChatSessionResponse, MessageResponse } from '../types/chat';

function unwrap<T>(response: ApiResponse<T>): T {
  return response.data;
}

export async function startSession(title?: string) {
  const response = await request.post<ApiResponse<ChatSessionResponse>>('/api/chat/start', title ? { title } : {});
  return unwrap(response.data);
}

export async function sendMessage(sessionId: number, content: string) {
  const response = await request.post<ApiResponse<MessageResponse>>('/api/chat/message', { sessionId, content });
  return unwrap(response.data);
}

export async function getHistory(sessionId: number) {
  const response = await request.get<ApiResponse<MessageResponse[]>>(`/api/chat/history/${sessionId}`);
  return unwrap(response.data);
}

export async function getSessions() {
  const response = await request.get<ApiResponse<ChatSessionResponse[]>>('/api/chat/sessions');
  return unwrap(response.data);
}

export async function deleteSession(sessionId: number) {
  await request.delete<ApiResponse<null>>(`/api/chat/session/${sessionId}`);
}
