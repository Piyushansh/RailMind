import { api } from '@/api/client';

export interface ChatResponse {
  conversationId: number;
  reply: string;
  mockMode: boolean;
  provider: string;
}

export interface ConversationSummaryDto {
  id: number;
  title: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MessageDto {
  id: number;
  role: 'USER' | 'ASSISTANT' | 'TOOL';
  content: string;
  createdAt: string;
}

export interface ToolExecutionDto {
  id: number;
  toolName: string;
  toolInput: string | null;
  toolOutput: string | null;
  success: boolean;
  createdAt: string;
}

export interface ConversationDetailDto {
  id: number;
  title: string | null;
  createdAt: string;
  updatedAt: string;
  messages: MessageDto[];
  toolExecutions: ToolExecutionDto[];
}

export const aiApi = {
  chat: (message: string, conversationId?: number) =>
    api.post<ChatResponse>('/ai/chat', { message, conversationId }).then((r) => r.data),
  listConversations: () => api.get<ConversationSummaryDto[]>('/ai/conversations').then((r) => r.data),
  getConversation: (id: number) => api.get<ConversationDetailDto>(`/ai/conversations/${id}`).then((r) => r.data),
};
