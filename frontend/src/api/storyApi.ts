import request from './index';
import type { ApiResponse } from '../types/auth';
import type {
  StoryDetailResponse,
  StoryPageResponse,
  StoryResponse,
  StoryUpdateRequest,
} from '../types/story';

function unwrap<T>(response: ApiResponse<T>): T {
  return response.data;
}

export async function getStories(page = 1, size = 10) {
  const response = await request.get<ApiResponse<StoryPageResponse>>('/api/story/list', {
    params: {
      page,
      size,
    },
  });
  return unwrap(response.data);
}

export async function getStoryDetail(id: number) {
  const response = await request.get<ApiResponse<StoryDetailResponse>>(`/api/story/${id}`);
  return unwrap(response.data);
}

export async function generateStory(sessionId: number) {
  const response = await request.post<ApiResponse<StoryResponse>>('/api/story/generate', { sessionId });
  return unwrap(response.data);
}

export async function updateStory(id: number, data: StoryUpdateRequest) {
  const response = await request.put<ApiResponse<StoryResponse>>(`/api/story/${id}`, data);
  return unwrap(response.data);
}

export async function deleteStory(id: number) {
  await request.delete<ApiResponse<null>>(`/api/story/${id}`);
}
