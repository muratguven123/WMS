import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import type { StompSubscription } from "@stomp/stompjs";
import { keycloak } from "../api/wms-api-client";
import { FEATURES, isFeatureEnabled } from "../auth/roles";
import {
  connectStomp,
  disconnectStomp,
  getStompClient,
  safeUnsubscribe,
  subscribeTopic,
  type MessageHandler,
} from "./stomp-client";
import type { DomainEventMessage } from "./types";

interface StompContextValue {
  connected: boolean;
  subscribe: (destination: string, handler: MessageHandler) => () => void;
  userId: string | null;
  companyId: number | null;
  locationId: number | null;
}

type SubscriptionEntry = {
  destination: string;
  handler: MessageHandler;
  sub: StompSubscription | null;
};

const StompContext = createContext<StompContextValue | null>(null);

function dropSubscriptionRefs(entries: Iterable<SubscriptionEntry>): void {
  for (const entry of entries) {
    entry.sub = null;
  }
}

function resubscribeEntry(entry: SubscriptionEntry): void {
  const stomp = getStompClient();
  safeUnsubscribe(entry.sub, stomp);
  entry.sub = null;
  if (stomp.connected) {
    entry.sub = subscribeTopic(entry.destination, entry.handler);
  }
}

export function StompProvider({
  children,
  companyId,
  locationId,
}: {
  children: ReactNode;
  companyId: number | null;
  locationId: number | null;
}) {
  const [connected, setConnected] = useState(false);
  const userId = keycloak.subject ?? (keycloak.tokenParsed?.sub as string | undefined) ?? null;
  const entriesRef = useRef<Set<SubscriptionEntry>>(new Set());

  useEffect(() => {
    if ((window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode) {
      return;
    }
    if (!isFeatureEnabled(FEATURES.REALTIME)) {
      return;
    }

    let cancelled = false;
    const stomp = getStompClient();

    const resubscribeAll = () => {
      for (const entry of entriesRef.current) {
        resubscribeEntry(entry);
      }
    };

    stomp.onConnect = () => {
      if (!cancelled) {
        setConnected(true);
        resubscribeAll();
      }
    };
    stomp.onDisconnect = () => {
      setConnected(false);
      dropSubscriptionRefs(entriesRef.current);
    };
    stomp.onStompError = () => {
      setConnected(false);
      dropSubscriptionRefs(entriesRef.current);
    };
    stomp.onWebSocketClose = () => {
      setConnected(false);
      dropSubscriptionRefs(entriesRef.current);
    };

    void (async () => {
      dropSubscriptionRefs(entriesRef.current);
      await disconnectStomp();
      if (!cancelled) {
        await connectStomp();
      }
    })();

    return () => {
      cancelled = true;
      dropSubscriptionRefs(entriesRef.current);
      void disconnectStomp().then(() => setConnected(false));
    };
  }, [companyId, locationId]);

  const subscribe = useCallback((destination: string, handler: MessageHandler) => {
    const entry: SubscriptionEntry = { destination, handler, sub: null };
    entriesRef.current.add(entry);

    const stomp = getStompClient();
    if (stomp.connected) {
      entry.sub = subscribeTopic(destination, handler);
    }

    return () => {
      entriesRef.current.delete(entry);
      safeUnsubscribe(entry.sub, stomp);
      entry.sub = null;
    };
  }, []);

  const value = useMemo(
    () => ({ connected, subscribe, userId, companyId, locationId }),
    [connected, subscribe, userId, companyId, locationId],
  );

  return <StompContext.Provider value={value}>{children}</StompContext.Provider>;
}

export function useStomp(): StompContextValue {
  const ctx = useContext(StompContext);
  if (!ctx) {
    throw new Error("useStomp must be used within StompProvider");
  }
  return ctx;
}

export function useTopicSubscription(
  destination: string | null,
  handler: (event: DomainEventMessage) => void,
): void {
  const { subscribe } = useStomp();
  const handlerRef = useRef(handler);
  handlerRef.current = handler;

  useEffect(() => {
    if (!destination) return;
    return subscribe(destination, (event) => handlerRef.current(event));
  }, [destination, subscribe]);
}
