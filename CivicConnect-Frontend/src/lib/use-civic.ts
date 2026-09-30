import { useCallback, useEffect, useState } from "react";
import {
  fetchCategories,
  fetchCurrentUser,
  fetchMyRequests,
  fetchNotifications,
  type ApiCategory,
  type AppNotification,
  type CivicUser,
  type ServiceRequest,
} from "./civic";

/** Re-renders whenever data changes (after any create/update call) or the tab regains focus. */
export function useCivicStore() {
  const [version, setVersion] = useState(0);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    setHydrated(true);
    const bump = () => setVersion((v) => v + 1);
    window.addEventListener("civic:changed", bump);
    window.addEventListener("focus", bump);
    return () => {
      window.removeEventListener("civic:changed", bump);
      window.removeEventListener("focus", bump);
    };
  }, []);

  const refresh = useCallback(() => setVersion((v) => v + 1), []);
  return { version, hydrated, refresh };
}

/**
 * The signed-in user from the backend session. `hydrated` becomes true once the first
 * /api/auth/me call has answered, so pages do not redirect to /auth too early.
 */
export function useAuth() {
  const { version } = useCivicStore();
  const [user, setUser] = useState<CivicUser | null>(null);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    let active = true;
    fetchCurrentUser()
      .then((u) => active && setUser(u))
      .catch(() => active && setUser(null))
      .finally(() => active && setHydrated(true));
    return () => {
      active = false;
    };
  }, [version]);

  return { user, hydrated };
}

/** `email` is kept from the prototype's signature; the backend uses the session instead. */
export function useRequests(email?: string) {
  const { version } = useCivicStore();
  const [requests, setRequests] = useState<ServiceRequest[]>([]);

  useEffect(() => {
    if (!email) {
      setRequests([]);
      return;
    }
    let active = true;
    fetchMyRequests()
      .then((r) => active && setRequests(r))
      .catch(() => active && setRequests([]));
    return () => {
      active = false;
    };
  }, [email, version]);

  return requests;
}

export function useNotifications(email?: string) {
  const { version } = useCivicStore();
  const [items, setItems] = useState<AppNotification[]>([]);

  useEffect(() => {
    if (!email) {
      setItems([]);
      return;
    }
    let active = true;
    fetchNotifications()
      .then((n) => active && setItems(n))
      .catch(() => active && setItems([]));
    return () => {
      active = false;
    };
  }, [email, version]);

  return items;
}

export function useCategories() {
  const [categories, setCategories] = useState<ApiCategory[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchCategories()
      .then(setCategories)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : "Could not load categories."));
  }, []);

  return { categories, error };
}
