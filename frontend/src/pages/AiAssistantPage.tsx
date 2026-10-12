import { useEffect, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { formatDistanceToNow } from 'date-fns';
import { Bot, MessageSquarePlus, Send, User as UserIcon, Wrench } from 'lucide-react';
import { aiApi, type ConversationDetailDto } from '@/api/ai';
import { extractErrorMessage } from '@/api/client';
import { Button, Banner } from '@/components/ui';
import clsx from 'clsx';

export default function AiAssistantPage() {
  const queryClient = useQueryClient();
  const [activeConversationId, setActiveConversationId] = useState<number | null>(null);
  const [detail, setDetail] = useState<ConversationDetailDto | null>(null);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const { data: conversations } = useQuery({ queryKey: ['ai', 'conversations'], queryFn: aiApi.listConversations });

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [detail]);

  const loadConversation = async (id: number) => {
    setActiveConversationId(id);
    setError(null);
    const d = await aiApi.getConversation(id);
    setDetail(d);
  };

  const startNewConversation = () => {
    setActiveConversationId(null);
    setDetail(null);
    setError(null);
  };

  const handleSend = async () => {
    const message = input.trim();
    if (!message) return;
    setInput('');
    setError(null);
    setSending(true);

    // Optimistic user bubble so the UI feels immediate; replaced by the real
    // persisted transcript (including any tool calls) once the response lands.
    setDetail((prev) => ({
      id: prev?.id ?? -1,
      title: prev?.title ?? message,
      createdAt: prev?.createdAt ?? new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      messages: [...(prev?.messages ?? []), { id: -1, role: 'USER', content: message, createdAt: new Date().toISOString() }],
      toolExecutions: prev?.toolExecutions ?? [],
    }));

    try {
      const res = await aiApi.chat(message, activeConversationId ?? undefined);
      setActiveConversationId(res.conversationId);
      const fresh = await aiApi.getConversation(res.conversationId);
      setDetail(fresh);
      queryClient.invalidateQueries({ queryKey: ['ai', 'conversations'] });
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="grid h-[calc(100vh-8.5rem)] grid-cols-[220px_1fr] gap-6">
      <aside className="overflow-y-auto">
        <Button variant="secondary" onClick={startNewConversation} className="mb-3 w-full gap-1.5 text-sm">
          <MessageSquarePlus className="h-4 w-4" /> New chat
        </Button>
        <div className="space-y-1">
          {conversations?.map((c) => (
            <button
              key={c.id}
              onClick={() => loadConversation(c.id)}
              className={clsx(
                'focus-ring block w-full truncate rounded-card px-3 py-2 text-left text-sm',
                activeConversationId === c.id ? 'bg-signal-soft text-signal' : 'text-slate-600 hover:bg-slate-100'
              )}
            >
              {c.title || 'New conversation'}
            </button>
          ))}
        </div>
      </aside>

      <div className="flex flex-col overflow-hidden rounded-card border border-slate-200 bg-white">
        <div ref={scrollRef} className="flex-1 space-y-4 overflow-y-auto p-5">
          {!detail?.messages.length && (
            <div className="flex h-full flex-col items-center justify-center gap-2 text-center">
              <Bot className="h-8 w-8 text-slate-300" />
              <p className="text-sm text-slate-500">Ask about journeys, fares, your bookings, or RailMind's policies.</p>
              <p className="text-xs text-slate-400">"Find me the best train from Delhi to Mumbai tomorrow under ₹2,000"</p>
            </div>
          )}
          {detail?.messages.map((m) => (
            <MessageBubble key={m.id} role={m.role} content={m.content} />
          ))}
          {detail?.toolExecutions && detail.toolExecutions.length > 0 && (
            <ToolExecutionTrail executions={detail.toolExecutions} />
          )}
          {sending && <MessageBubble role="ASSISTANT" content="…" pending />}
        </div>

        {error && <div className="px-5 pb-2"><Banner variant="error">{error}</Banner></div>}

        <div className="flex items-end gap-2 border-t border-slate-100 p-4">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            rows={1}
            placeholder="Ask RailMind…"
            className="focus-ring max-h-32 flex-1 resize-none rounded-card border border-slate-300 px-3.5 py-2.5 text-sm"
          />
          <Button onClick={handleSend} disabled={!input.trim()} loading={sending} className="shrink-0 gap-1.5">
            <Send className="h-4 w-4" /> Send
          </Button>
        </div>
      </div>
    </div>
  );
}

function MessageBubble({ role, content, pending }: { role: string; content: string; pending?: boolean }) {
  if (role === 'TOOL') return null; // shown via ToolExecutionTrail instead, in a form users can actually parse
  const isUser = role === 'USER';
  return (
    <div className={clsx('flex gap-3', isUser && 'flex-row-reverse')}>
      <span className={clsx('flex h-7 w-7 shrink-0 items-center justify-center rounded-full', isUser ? 'bg-slate-100 text-slate-500' : 'bg-signal-soft text-signal')}>
        {isUser ? <UserIcon className="h-3.5 w-3.5" /> : <Bot className="h-3.5 w-3.5" />}
      </span>
      <div className={clsx('max-w-[75%] rounded-card px-4 py-2.5 text-sm', isUser ? 'bg-signal text-white' : 'bg-slate-100 text-ink', pending && 'animate-pulse')}>
        {content}
      </div>
    </div>
  );
}

function ToolExecutionTrail({ executions }: { executions: ConversationDetailDto['toolExecutions'] }) {
  return (
    <div className="ml-10 space-y-1.5">
      {executions.map((t) => (
        <details key={t.id} className="rounded-card border border-slate-200 bg-slate-50 px-3 py-2 text-xs">
          <summary className="flex cursor-pointer items-center gap-1.5 font-medium text-slate-600">
            <Wrench className="h-3 w-3" /> {t.toolName}
            <span className={clsx('ml-auto', t.success ? 'text-rail' : 'text-danger')}>
              {t.success ? 'ok' : 'failed'} · {formatDistanceToNow(new Date(t.createdAt), { addSuffix: true })}
            </span>
          </summary>
          {t.toolOutput && <pre className="mt-2 overflow-x-auto whitespace-pre-wrap text-slate-500">{t.toolOutput}</pre>}
        </details>
      ))}
    </div>
  );
}
