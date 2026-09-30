/**
 * CivicConnect — requester module data layer.
 *
 * M2: the localStorage store is replaced by the Java backend's REST API (/api). Function names
 * are kept from the prototype so the pages change as little as possible; they are now async.
 * The static CATEGORIES list is kept only for the landing page's marketing cards — the request
 * form uses the real categories from the database (fetchCategories).
 */
import {
  api,
  ApiError,
  type ApiAction,
  type ApiCategory,
  type ApiChangeResult,
  type ApiDetail,
  type ApiMessage,
  type ApiNotification,
  type ApiStaffMember,
  type ApiSummary,
  type ApiUser,
} from "./api";

export { ApiError };
export type { ApiAction, ApiCategory, ApiDetail, ApiMessage, ApiStaffMember, ApiSummary };

export type RequestStatus =
  | "Submitted"
  | "Assigned"
  | "In Progress"
  | "Reopened"
  | "Resolved"
  | "Closed"
  | "Rejected";

export type Priority = "Low" | "Medium" | "High" | "Emergency";
export type ApiPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

export const PRIORITY_TO_API: Record<Priority, ApiPriority> = {
  Low: "LOW",
  Medium: "MEDIUM",
  High: "HIGH",
  Emergency: "URGENT",
};
export const PRIORITY_LABEL: Record<ApiPriority, Priority> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
  URGENT: "Emergency",
};

/* ---------- Landing-page marketing categories (static content) ---------- */

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
    sla: "3–5 days",
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
    sla: "3–7 days",
  },
  {
    id: "lighting",
    name: "Street Lighting",
    blurb: "Street lights out, flickering lights and damaged poles.",
    examples: ["Street light out", "Damaged pole", "Light on during the day"],
    sla: "7 days",
  },
  {
    id: "safety",
    name: "Parks & Public Spaces",
    blurb: "Damaged park facilities, overgrown grass and unsafe public spaces.",
    examples: ["Broken swings", "Overgrown grass", "Vandalised facility"],
    sla: "10 days",
  },
];

export const categoryById = (id: string) => CATEGORIES.find((c) => c.id === id);

/** "48 hours" / "5 days" from the database SLA. */
export function formatSla(hours: number) {
  return hours % 24 === 0 && hours >= 48 ? `${hours / 24} days` : `${hours} hours`;
}

/* ---------- Types the pages use ---------- */

export type StatusEvent = { status: RequestStatus; note: string; at: string; by: string };

export type ServiceRequest = {
  id: string;
  numericId: number;
  reference: string;
  categoryName: string;
  title: string;
  description: string;
  location: string;
  priority: Priority;
  status: RequestStatus;
  lifecycleGroup: "OPEN" | "RESOLVED" | "CLOSED";
  overdue: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
  dueAt: string;
  assigneeName: string | null;
  history: StatusEvent[];
  actions: ApiAction[];
  messages: ApiMessage[];
  canGiveFeedback: boolean;
  feedbackRating?: number;
  feedbackComment?: string;
};

export type CivicUser = {
  userId: number;
  fullName: string;
  email: string;
  phone: string;
  municipality: string;
  role: ApiUser["role"];
};

export type AppNotification = {
  id: string;
  title: string;
  body: string;
  at: string;
  read: boolean;
  requestId?: string;
};

/** Tell every hook to re-fetch (same event name the localStorage prototype used). */
function changed() {
  if (typeof window !== "undefined") window.dispatchEvent(new CustomEvent("civic:changed"));
}

function toUser(u: ApiUser): CivicUser {
  return {
    userId: u.userId,
    fullName: u.fullName,
    email: u.email,
    phone: u.phone ?? "",
    municipality: u.municipality ?? "",
    role: u.role,
  };
}

export function toRequest(d: ApiDetail): ServiceRequest {
  const r = d.request;
  return {
    id: String(r.id),
    numericId: r.id,
    reference: r.reference,
    categoryName: r.categoryName,
    title: r.title,
    description: r.description,
    location: r.location ?? "",
    priority: PRIORITY_LABEL[r.priority],
    status: r.statusLabel as RequestStatus,
    lifecycleGroup: r.lifecycleGroup,
    overdue: r.overdue,
    version: r.version,
    createdAt: r.createdAt,
    updatedAt: r.updatedAt,
    dueAt: r.dueAt,
    assigneeName: r.assigneeName,
    history: d.history.map((h) => ({
      status: h.toStatusLabel as RequestStatus,
      note: h.note ?? "",
      at: h.changedAt,
      by: h.changedBy,
    })),
    actions: d.actions,
    messages: d.messages,
    canGiveFeedback: d.canGiveFeedback,
    feedbackRating: d.feedback?.rating,
    feedbackComment: d.feedback?.comment ?? undefined,
  };
}

/* ---------- Users & session ---------- */

export type RegistrationInput = {
  fullName: string;
  email: string;
  phone: string;
  municipality: string;
  password: string;
  confirmPassword: string;
};

export async function registerUser(input: RegistrationInput) {
  const user = toUser(await api.post<ApiUser>("/auth/register", input));
  changed();
  return user;
}

export async function loginUser(email: string, password: string) {
  const user = toUser(await api.post<ApiUser>("/auth/login", { email, password }));
  changed();
  return user;
}

export async function logout() {
  await api.post<void>("/auth/logout");
  changed();
}

/** The signed-in user, or null when not signed in. */
export async function fetchCurrentUser(): Promise<CivicUser | null> {
  try {
    return toUser(await api.get<ApiUser>("/auth/me"));
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null;
    throw e;
  }
}

/* ---------- Categories ---------- */

export const fetchCategories = () => api.get<ApiCategory[]>("/categories");

/* ---------- Requests ---------- */

export type NewRequestInput = {
  categoryId: number;
  title: string;
  description: string;
  address: string;
  suburb: string;
  city: string;
  priority: Priority;
  contactNumber: string;
};

export async function createRequest(input: NewRequestInput) {
  const detail = await api.post<ApiDetail>("/requests", {
    categoryId: input.categoryId,
    title: input.title,
    description: input.description,
    address: input.address,
    suburb: input.suburb,
    city: input.city,
    priority: PRIORITY_TO_API[input.priority],
    contactPhone: input.contactNumber,
  });
  changed();
  return toRequest(detail);
}

/** A resident's own requests, each with its timeline (residents have few requests). */
export async function fetchMyRequests(): Promise<ServiceRequest[]> {
  const list = await api.get<ApiSummary[]>("/requests?scope=mine");
  const details = await Promise.all(list.map((r) => fetchRequest(r.id)));
  return details;
}

export async function fetchRequest(id: number) {
  return toRequest(await api.get<ApiDetail>(`/requests/${id}`));
}

/** Work queue: staff see their assignments; coordinators and managers see all open requests. */
export const fetchQueue = (status?: string) =>
  api.get<ApiSummary[]>(`/requests?scope=queue${status ? `&status=${encodeURIComponent(status)}` : ""}`);

export const fetchStaff = () => api.get<ApiStaffMember[]>("/staff");

export type ChangeStatusInput = {
  target: string;
  expectedVersion: number;
  assigneeId?: number;
  note?: string;
  resolutionNotes?: string;
};

export async function changeStatus(id: number, input: ChangeStatusInput) {
  const result = await api.post<ApiChangeResult>(`/requests/${id}/status`, input);
  changed();
  return { request: toRequest(result.request), warnings: result.warnings };
}

export async function saveFeedback(id: number, rating: number, comment: string) {
  const detail = await api.post<ApiDetail>(`/requests/${id}/feedback`, { rating, comment });
  changed();
  return toRequest(detail);
}

/* ---------- Notifications ---------- */

export async function fetchNotifications(): Promise<AppNotification[]> {
  const inbox = await api.get<{ items: ApiNotification[]; unread: number }>("/notifications");
  return inbox.items.map((n) => ({
    id: String(n.notificationId),
    title: n.title,
    body: n.body,
    at: n.createdAt,
    read: n.read,
    requestId: n.requestId == null ? undefined : String(n.requestId),
  }));
}

export async function markNotificationsRead() {
  await api.post<{ updated: number }>("/notifications/read");
  changed();
}

/* ---------- Formatting helpers ---------- */

export const statusTone: Record<RequestStatus, string> = {
  Submitted: "bg-secondary text-secondary-foreground",
  Assigned: "bg-primary/15 text-primary",
  "In Progress": "bg-warning/25 text-warning-foreground",
  Reopened: "bg-warning/25 text-warning-foreground",
  Resolved: "bg-success/20 text-success",
  Closed: "bg-success/20 text-success",
  Rejected: "bg-destructive/15 text-destructive",
};

/** Three-segment progress bar on the dashboard. */
export const statusStep: Record<RequestStatus, number> = {
  Submitted: 1,
  Assigned: 1,
  "In Progress": 2,
  Reopened: 2,
  Resolved: 3,
  Closed: 3,
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

/** Turns an unknown error into a short message for a toast. */
export function errorMessage(e: unknown) {
  return e instanceof Error ? e.message : "Something went wrong.";
}
