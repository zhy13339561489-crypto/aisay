export interface StoryResponse {
  id: number;
  title: string;
  genre?: string;
  style?: string;
  synopsis?: string;
  status?: string;
  coverImagePath?: string;
  createdAt: string;
}

export interface StoryCharacter {
  id: number;
  name: string;
  role?: string;
  description?: string;
  personality?: string;
  appearance?: Record<string, unknown>;
}

export interface StoryScene {
  id: number;
  sceneNumber: number;
  setting?: string;
  description?: string;
  visualElements?: Record<string, unknown>;
}

export interface StoryDetailResponse extends StoryResponse {
  fullContent?: string;
  characters: StoryCharacter[];
  scenes: StoryScene[];
}

export interface StoryUpdateRequest {
  title?: string;
  genre?: string;
  style?: string;
  synopsis?: string;
}

export interface StoryPageResponse {
  records: StoryResponse[];
  total: number;
  size: number;
  current: number;
  pages: number;
}
