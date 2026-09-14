'use client';

import { Client, type IMessage } from '@stomp/stompjs';
import { useEffect, useRef, useState } from 'react';
import { api, tokenStore } from './api';
import type { ChatMessage } from './types';

/**
 * REST for history, the socket for what happens next. If the socket never
 * connects, the thread still works — it just stops updating by itself, which is
 * the right way for a chat to degrade.
 */
export function useThread(requestId: string) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [live, setLive] = useState(false);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    let cancelled = false;

    api.threads
      .history(requestId)
      .then((history) => {
        if (!cancelled) setMessages(history);
      })
      .catch(() => undefined);

    void api.threads.markRead(requestId).catch(() => undefined);

    const wsUrl = api.baseUrl.replace(/^http/, 'ws') + '/ws';
    const client = new Client({
      brokerURL: wsUrl,
      // The token rides in the CONNECT frame, not the query string, so it stays
      // out of proxy access logs.
      connectHeaders: { Authorization: `Bearer ${tokenStore.access() ?? ''}` },
      reconnectDelay: 4000,
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
    clientRef.current = client;

    return () => {
      cancelled = true;
      void client.deactivate();
    };
  }, [requestId]);

  async function send(body: string) {
    const posted = await api.threads.post(requestId, body);
    // The socket echoes it back; de-duplicate on id so a slow socket does not
    // leave the sender staring at nothing.
    setMessages((prev) => (prev.some((m) => m.id === posted.id) ? prev : [...prev, posted]));
  }

  return { messages, live, send };
}
