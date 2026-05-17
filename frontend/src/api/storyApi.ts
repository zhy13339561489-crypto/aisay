import request from './index';
import type { ApiResponse } from '../types/auth';
import type {
  StoryDetailResponse,
  StoryGenerateRequest,
  StoryOutlineReviseRequest,
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

export async function generateStory(data: StoryGenerateRequest) {
  const response = await request.post<ApiResponse<StoryResponse>>('/api/story/generate', data);
  return unwrap(response.data);
}

export async function updateStory(id: number, data: StoryUpdateRequest) {
  const response = await request.put<ApiResponse<StoryResponse>>(`/api/story/${id}`, data);
  return unwrap(response.data);
}

export async function reviseStoryOutline(id: number, data: StoryOutlineReviseRequest) {
  const response = await request.post<ApiResponse<StoryDetailResponse>>(`/api/story/${id}/outline/revise`, data);
  return unwrap(response.data);
}

export async function deleteStory(id: number) {
  await request.delete<ApiResponse<null>>(`/api/story/${id}`);
}
