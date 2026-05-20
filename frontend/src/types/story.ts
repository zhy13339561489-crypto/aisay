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
  id?: number;
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

export interface StoryVolumeOutline {
  id: number;
  volumeNumber: number;
  title: string;
  summary?: string;
  content?: string;
  endingHook?: string;
}

export interface StoryDetailResponse extends StoryResponse {
  fullContent?: string;
  volumeOutlines: StoryVolumeOutline[];
  characters: StoryCharacter[];
  scenes: StoryScene[];
}

export interface StoryUpdateRequest {
  title?: string;
  genre?: string;
  style?: string;
  synopsis?: string;
}

export interface StoryGenerateRequest {
  genre: string;
  plot?: string;
}

export interface StoryOutlineReviseRequest {
  suggestion: string;
}

export interface StoryVolumeOutlineReviseRequest {
  suggestion: string;
}

export interface StoryVolumeOutlineUpdateItem {
  volumeNumber: number;
  title: string;
  summary?: string;
  content?: string;
  endingHook?: string;
}

export interface StoryVolumeOutlineUpdateRequest {
  volumes: StoryVolumeOutlineUpdateItem[];
}

export interface StoryDetailUpdateRequest {
  synopsis?: string;
  fullContent?: string;
  characters?: StoryCharacter[];
}

export interface StoryPageResponse {
  records: StoryResponse[];
  total: number;
  size: number;
  current: number;
  pages: number;
}
