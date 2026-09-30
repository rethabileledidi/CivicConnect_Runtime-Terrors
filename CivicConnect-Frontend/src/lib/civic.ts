/**
 * CivicConnect — requester module data layer.
 * Frontend-only store backed by browser localStorage (Person 1 scope).
 */

export type RequestStatus = "Submitted" | "In Progress" | "Resolved" | "Rejected";
export type Priority = "Low" | "Medium" | "High" | "Emergency";

export type CategoryId = "roads" | "water" | "electricity" | "waste" | "lighting" | "safety";

export type CategoryInfo = {
  id: CategoryId;
  name: string;
  blurb: string;
  examples: string[];
  sla: string;
};

export const CATEGORIES: CategoryInfo[] = [
  {
    id: "roads",
    name: "Roads & Potholes",
    blurb: "Potholes, collapsed kerbs, damaged road markings and blocked storm drains.",
    examples: ["Pothole", "Damaged kerb", "Blocked storm drain", "Faded road markings"],
    sla: "14 working days",
  },
  {
    id: "water",
    name: "Water & Sanitation",
    blurb: "Burst pipes, leaking meters, sewage spills and interrupted water supply.",
    examples: ["Burst pipe", "Sewer blockage", "No water supply", "Leaking meter"],
    sla: "48 hours",
  },
  {
    id: "electricity",
    name: "Electricity",
    blurb: "Unplanned outages, faulty transformers, illegal connections and meter faults.",
    examples: ["Power outage", "Faulty transformer", "Illegal connection", "Meter fault"],
    sla: "24 hours",
  },
  {
    id: "waste",
    name: "Waste Removal",
    blurb: "Missed collections, illegal dumping and overflowing public bins.",
    examples: ["Missed collection", "Illegal dumping", "Overflowing bin"],
    sla: "7 working days",
  },
  {
    id: "lighting",
    name: "Street Lighting",
    blurb: "Street lights out, flickering lights and damaged poles.",
    examples: ["Street light out", "Damaged pole", "Light on during the day"],
    sla: "10 working days",
  },
  {
    id: "safety",
    name: "Public Safety",
    blurb: "Broken traffic lights, open manholes, vandalised facilities and unsafe structures.",
    examples: ["Robot not working", "Open manhole", "Vandalised facility"],
    sla: "24 hours",
  },
];

export const categoryById = (id: string) => CATEGORIES.find((c) => c.id === id);

export type StatusEvent = {
  status: RequestStatus;
  note: string;
  at: string;
};

export type ServiceRequest = {
  id: string;
  reference: string;
  userEmail: string;
  category: CategoryId;
  title: string;
  description: string;
  address: string;
  suburb: string;
  city: string;
  priority: Priority;
  contactNumber: string;
  status: RequestStatus;
  createdAt: string;
  updatedAt: string;
  history: StatusEvent[];
  feedbackRating?: number;
  feedbackComment?: string;
};

export type CivicUser = {
  fullName: string;
  email: string;
  phone: string;
  municipality: string;
  password: string;
};

export type AppNotification = {
  id: string;
  userEmail: string;
  title: string;
  body: string;
  at: string;
  read: boolean;
  requestId?: string;
};

const USERS_KEY = "civic.users";
const SESSION_KEY = "civic.session";
const REQUESTS_KEY = "civic.requests";
const NOTIFICATIONS_KEY = "civic.notifications";

const isBrowser = () => typeof window !== "undefined";

function read<T>(key: string, fallback: T): T {
  if (!isBrowser()) return fallback;
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

function write(key: string, value: unknown) {
  if (!isBrowser()) return;
  window.localStorage.setItem(key, JSON.stringify(value));
  window.dispatchEvent(new CustomEvent("civic:changed"));
}

export const uid = () => Math.random().toString(36).slice(2, 10);

export function makeReference(category: CategoryId) {
  const prefix = category.slice(0, 3).toUpperCase();
  return `CC-${prefix}-${Math.floor(100000 + Math.random() * 899999)}`;
}

/* ---------- Users & session ---------- */

export const getUsers = () => read<CivicUser[]>(USERS_KEY, []);

export function registerUser(user: CivicUser) {
  const users = getUsers();
  if (users.some((u) => u.email.toLowerCase() === user.email.toLowerCase())) {
    throw new Error("An account with this email already exists.");
  }
  write(USERS_KEY, [...users, user]);
  write(SESSION_KEY, user.email.toLowerCase());
  return user;
}

export function loginUser(email: string, password: string) {
  const user = getUsers().find((u) => u.email.toLowerCase() === email.toLowerCase());
  if (!user || user.password !== password) {
    throw new Error("Incorrect email or password.");
  }
  write(SESSION_KEY, user.email.toLowerCase());
  return user;
}

export function logout() {
  if (!isBrowser()) return;
  window.localStorage.removeItem(SESSION_KEY);
  window.dispatchEvent(new CustomEvent("civic:changed"));
}

export function currentUser(): CivicUser | null {
  const email = read<string | null>(SESSION_KEY, null);
  if (!email) return null;
  return getUsers().find((u) => u.email.toLowerCase() === email) ?? null;
}

/* ---------- Requests ---------- */

export const getAllRequests = () => read<ServiceRequest[]>(REQUESTS_KEY, []);

export const getUserRequests = (email: string) =>
  getAllRequests()
    .filter((r) => r.userEmail.toLowerCase() === email.toLowerCase())
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));

export type NewRequestInput = Omit<
  ServiceRequest,
  "id" | "reference" | "status" | "createdAt" | "updatedAt" | "history"
>;

export function createRequest(input: NewRequestInput) {
  const now = new Date().toISOString();
  const request: ServiceRequest = {
    ...input,
    id: uid(),
    reference: makeReference(input.category),
    status: "Submitted",
    createdAt: now,
    updatedAt: now,
    history: [{ status: "Submitted", note: "Request logged and queued for assignment.", at: now }],
  };
  write(REQUESTS_KEY, [request, ...getAllRequests()]);
  pushNotification({
    userEmail: request.userEmail,
    title: `Request ${request.reference} received`,
    body: `Your ${categoryById(request.category)?.name} request is logged. Target resolution: ${categoryById(request.category)?.sla}.`,
    requestId: request.id,
  });
  return request;
}

export function advanceRequest(id: string) {
  const requests = getAllRequests();
  const request = requests.find((r) => r.id === id);
  if (!request) return;
  const next: Record<RequestStatus, RequestStatus> = {
    Submitted: "In Progress",
    "In Progress": "Resolved",
    Resolved: "Resolved",
    Rejected: "Rejected",
  };
  const status = next[request.status];
  if (status === request.status) return;
  const at = new Date().toISOString();
  request.status = status;
  request.updatedAt = at;
  request.history = [
    ...request.history,
    {
      status,
      note:
        status === "In Progress"
          ? "A municipal field team has been assigned to your request."
          : "Work completed and verified. Please rate the service.",
      at,
    },
  ];
  write(REQUESTS_KEY, requests);
  pushNotification({
    userEmail: request.userEmail,
    title: `${request.reference} is now ${status}`,
    body:
      status === "In Progress"
        ? "A team has been dispatched to your reported location."
        : "Your issue has been resolved. Tell us how we did.",
    requestId: request.id,
  });
}

export function saveFeedback(id: string, rating: number, comment: string) {
  const requests = getAllRequests();
  const request = requests.find((r) => r.id === id);
  if (!request) return;
  request.feedbackRating = rating;
  request.feedbackComment = comment;
  request.updatedAt = new Date().toISOString();
  write(REQUESTS_KEY, requests);
}

/* ---------- Notifications ---------- */

export const getNotifications = (email: string) =>
  read<AppNotification[]>(NOTIFICATIONS_KEY, [])
    .filter((n) => n.userEmail.toLowerCase() === email.toLowerCase())
    .sort((a, b) => b.at.localeCompare(a.at));

export function pushNotification(input: Omit<AppNotification, "id" | "at" | "read">) {
  const all = read<AppNotification[]>(NOTIFICATIONS_KEY, []);
  write(NOTIFICATIONS_KEY, [
    { ...input, id: uid(), at: new Date().toISOString(), read: false },
    ...all,
  ]);
}

export function markNotificationsRead(email: string) {
  const all = read<AppNotification[]>(NOTIFICATIONS_KEY, []).map((n) =>
    n.userEmail.toLowerCase() === email.toLowerCase() ? { ...n, read: true } : n,
  );
  write(NOTIFICATIONS_KEY, all);
}

/* ---------- Formatting helpers ---------- */

export const statusTone: Record<RequestStatus, string> = {
  Submitted: "bg-secondary text-secondary-foreground",
  "In Progress": "bg-warning/25 text-warning-foreground",
  Resolved: "bg-success/20 text-success",
  Rejected: "bg-destructive/15 text-destructive",
};

export const statusStep: Record<RequestStatus, number> = {
  Submitted: 1,
  "In Progress": 2,
  Resolved: 3,
  Rejected: 3,
};

export function formatDate(iso: string) {
  return new Date(iso).toLocaleString("en-ZA", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}
