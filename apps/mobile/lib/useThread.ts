import { Client, type IMessage } from '@stomp/stompjs';
import { useEffect, useState } from 'react';
import { api, tokenStore } from './api';
import type { ChatMessage } from './types';

/**
 * React Native ships a WebSocket implementation, so stompjs works unchanged.
 * History over REST, everything after that over the socket — and if the socket
 * never connects the thread still reads, it just stops updating by itself.
 */
export function useThread(requestId: string) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [live, setLive] = useState(false);

  useEffect(() => {
    let cancelled = false;

    api.threads
      .history(requestId)
      .then((history) => {
        if (!cancelled) setMessages(history);
      })
      .catch(() => undefined);

    void api.threads.markRead(requestId).catch(() => undefined);

    const client = new Client({
      brokerURL: `${api.baseUrl.replace(/^http/, 'ws')}/ws`,
      connectHeaders: { Authorization: `Bearer ${tokenStore.access() ?? ''}` },
      reconnectDelay: 4000,
      forceBinaryWSFrames: true,
      appendMissingNULLonIncoming: true,
      onConnect: () => {
        setLive(true);
        client.subscribe(`/topic/threads/${requestId}`, (frame: IMessage) => {
          const incoming = JSON.parse(frame.body) as ChatMessage;
          setMessages((prev) => (prev.some((m) => m.id === incoming.id) ? prev : [...prev, incoming]));
        });
      },
      onWebSocketClose: () => setLive(false),
      onStompError: () => setLive(false),
    });

    client.activate();

    return () => {
      cancelled = true;
      void client.deactivate();
    };
  }, [requestId]);

  async function send(body: string) {
    const posted = await api.threads.post(requestId, body);
    setMessages((prev) => (prev.some((m) => m.id === posted.id) ? prev : [...prev, posted]));
  }

  return { messages, live, send };
}
