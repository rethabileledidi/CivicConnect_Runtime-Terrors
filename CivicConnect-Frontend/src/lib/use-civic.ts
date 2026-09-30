import { useCallback, useEffect, useState } from "react";
import { currentUser, getNotifications, getUserRequests, type CivicUser } from "./civic";

/** Re-renders whenever the CivicConnect local store changes. */
export function useCivicStore() {
  const [version, setVersion] = useState(0);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    setHydrated(true);
    const bump = () => setVersion((v) => v + 1);
    window.addEventListener("civic:changed", bump);
    window.addEventListener("storage", bump);
    return () => {
      window.removeEventListener("civic:changed", bump);
      window.removeEventListener("storage", bump);
    };
  }, []);

  const refresh = useCallback(() => setVersion((v) => v + 1), []);
  return { version, hydrated, refresh };
}

export function useAuth() {
  const { version, hydrated } = useCivicStore();
  const [user, setUser] = useState<CivicUser | null>(null);

  useEffect(() => {
    setUser(currentUser());
  }, [version, hydrated]);

  return { user, hydrated };
}

export function useRequests(email?: string) {
  const { version, hydrated } = useCivicStore();
  const [requests, setRequests] = useState<ReturnType<typeof getUserRequests>>([]);

  useEffect(() => {
    setRequests(email ? getUserRequests(email) : []);
  }, [email, version, hydrated]);

  return requests;
}

export function useNotifications(email?: string) {
  const { version, hydrated } = useCivicStore();
  const [items, setItems] = useState<ReturnType<typeof getNotifications>>([]);

  useEffect(() => {
    setItems(email ? getNotifications(email) : []);
  }, [email, version, hydrated]);

  return items;
}
