import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import { ensureValidAccessToken } from "../api/wms-api-client";
import { buildWsUrl } from "./destinations";
import type { DomainEventMessage } from "./types";

export type MessageHandler = (event: DomainEventMessage) => void;

let client: Client | null = null;
let disconnectPromise: Promise<void> | null = null;

function getOrCreateClient(): Client {
  if (!client) {
    client = new Client({
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      beforeConnect: async () => {
        const token = await ensureValidAccessToken(60);
        if (!token) {
          window.dispatchEvent(new CustomEvent("wms:auth-required"));
          throw new Error("No access token for WebSocket");
        }
        client!.brokerURL = buildWsUrl(token);
      },
    });
  }
  return client;
}

export function getStompClient(): Client {
  return getOrCreateClient();
}

/** Kapalı veya kapanmakta olan sokette UNSUBSCRIBE göndermeyi engeller. */
export function safeUnsubscribe(
  sub: StompSubscription | null | undefined,
  stomp: Client = getOrCreateClient(),
): void {
  if (!sub || !stomp.connected) {
    return;
  }
  try {
    sub.unsubscribe();
  } catch (err) {
    console.debug("STOMP unsubscribe skipped (socket closed)", err);
  }
}

export function parseDomainEvent(message: IMessage): DomainEventMessage {
  return JSON.parse(message.body) as DomainEventMessage;
}

export function subscribeTopic(
  destination: string,
  handler: MessageHandler,
): StompSubscription | null {
  const stomp = getOrCreateClient();
  if (!stomp.connected) {
    return null;
  }
  return stomp.subscribe(destination, (message) => {
    try {
      handler(parseDomainEvent(message));
    } catch (err) {
      console.warn("Failed to parse STOMP message", err);
    }
  });
}

export async function connectStomp(): Promise<void> {
  if ((window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode) {
    return;
  }
  if (disconnectPromise) {
    await disconnectPromise;
  }
  const stomp = getOrCreateClient();
  if (stomp.connected || stomp.active) {
    return;
  }
  const token = await ensureValidAccessToken(60);
  if (!token) return;
  stomp.brokerURL = buildWsUrl(token);
  stomp.activate();
}

export async function disconnectStomp(): Promise<void> {
  const stomp = client;
  if (!stomp?.active) {
    return;
  }
  const pending = stomp.deactivate();
  disconnectPromise = pending;
  try {
    await pending;
  } finally {
    if (disconnectPromise === pending) {
      disconnectPromise = null;
    }
  }
}
