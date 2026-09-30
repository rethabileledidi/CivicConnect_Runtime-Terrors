/**
 * CivicConnect — HTTP client for the Java backend (/api on Tomcat).
 *
 * In development the Vite dev server proxies /api to Tomcat (see vite.config.ts), so the
 * browser talks to one origin and the session cookie just works. Every state-changing call
 * sends the X-Requested-With header that the backend's ApiSecurityFilter requires (CSRF defence).
 */

const API_BASE = (import.meta.env?.VITE_API_BASE_URL as string | undefined) ?? "/api";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

type Method = "GET" | "POST";

async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      credentials: "include",
      headers: {
        Accept: "application/json",
        ...(method !== "GET" ? { "X-Requested-With": "CivicConnect" } : {}),
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, "NETWORK", "Cannot reach the CivicConnect server. Is the backend running?");
  }

  if (response.status === 204) return undefined as T;

  const text = await response.text();
  const data = text ? safeJson(text) : undefined;
  if (!response.ok) {
    const err = (data ?? {}) as { error?: string; message?: string; fieldErrors?: Record<string, string> };
    throw new ApiError(
      response.status,
      err.error ?? "HTTP_" + response.status,
      err.message ?? "Something went wrong. Please try again.",
      err.fieldErrors ?? {},
    );
  }
  return data as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
};

/* ---------- Response shapes (mirror the Java records in com.civicconnect.service.RequestViews) ---------- */

export type ApiUser = {
  userId: number;
  email: string;
  fullName: string;
  role: "RESIDENT" | "STAFF" | "COORDINATOR" | "MANAGER" | "ADMIN";
  phone: string | null;
  municipality: string | null;
};

export type ApiCategory = {
  categoryId: number;
  code: string;
  name: string;
  description: string | null;
  departmentName: string;
  slaHours: number;
};

export type ApiSummary = {
  id: number;
  reference: string;
  title: string;
  description: string;
  categoryId: number;
  categoryName: string;
  departmentName: string;
  status: string;
  statusLabel: string;
  lifecycleGroup: "OPEN" | "RESOLVED" | "CLOSED";
  priority: "LOW" | "MEDIUM" | "HIGH" | "URGENT";
  location: string | null;
  requesterName: string;
  assigneeId: number | null;
  assigneeName: string | null;
  createdAt: string;
  updatedAt: string;
  dueAt: string;
  overdue: boolean;
  version: number;
};

export type ApiHistoryItem = {
  fromStatus: string | null;
  toStatus: string;
  toStatusLabel: string;
  changedBy: string;
  changedByRole: string;
  changedAt: string;
  assigneeName: string | null;
  note: string | null;
};

export type ApiAction = {
  target: string;
  label: string;
  needsAssignee: boolean;
  needsNote: boolean;
  needsResolutionNotes: boolean;
};

export type ApiMessage = {
  channel: "SMS" | "WHATSAPP";
  recipient: string;
  body: string;
  status: "PENDING" | "SENT" | "FAILED";
  attempts: number;
  createdAt: string;
  sentAt: string | null;
};

export type ApiDetail = {
  request: ApiSummary;
  history: ApiHistoryItem[];
  actions: ApiAction[];
  feedback: { rating: number; comment: string | null; submittedAt: string } | null;
  canGiveFeedback: boolean;
  messages: ApiMessage[];
};

export type ApiChangeResult = { request: ApiDetail; warnings: string[] };

export type ApiNotification = {
  notificationId: number;
  requestId: number | null;
  title: string;
  body: string;
  createdAt: string;
  read: boolean;
};

export type ApiStaffMember = { userId: number; fullName: string };
