import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { AlertTriangle, BarChart3, ClipboardList, RefreshCw } from "lucide-react";
import { SiteHeader } from "@/components/SiteHeader";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  ApiError,
  changeStatus,
  errorMessage,
  fetchQueue,
  fetchRequest,
  fetchStaff,
  formatDate,
  PRIORITY_LABEL,
  statusTone,
  type ApiAction,
  type ApiStaffMember,
  type ApiSummary,
  type RequestStatus,
  type ServiceRequest,
} from "@/lib/civic";
import { useAuth } from "@/lib/use-civic";

/**
 * Work queue for staff, coordinators and managers (Person 1 page, wired to the Person 2 API).
 * The action buttons come straight from the backend's State pattern (GET /api/requests/{id}
 * returns "actions"), so this page never decides what is legal — it only shows what it is told.
 */
export const Route = createFileRoute("/staff")({
  head: () => ({
    meta: [
      { title: "Work queue | CivicConnect" },
      { name: "description", content: "Assign, progress and resolve municipal service requests." },
    ],
  }),
  component: StaffPage,
});

const REPORTS_URL =
  (import.meta.env?.VITE_REPORTS_URL as string | undefined) ??
  "http://localhost:8081/civicconnect/dashboard";

const statusFilters = [
  { value: "", label: "All open" },
  { value: "SUBMITTED", label: "Submitted" },
  { value: "ASSIGNED", label: "Assigned" },
  { value: "IN_PROGRESS", label: "In Progress" },
  { value: "REOPENED", label: "Reopened" },
];

function StaffPage() {
  const { user, hydrated } = useAuth();
  const navigate = useNavigate();
  const [status, setStatus] = useState("");
  const [queue, setQueue] = useState<ApiSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [selected, setSelected] = useState<ServiceRequest | null>(null);
  const [staff, setStaff] = useState<ApiStaffMember[]>([]);

  const isOversight = user?.role === "COORDINATOR" || user?.role === "MANAGER" || user?.role === "ADMIN";

  useEffect(() => {
    if (!hydrated) return;
    if (!user) navigate({ to: "/auth" });
    else if (user.role === "RESIDENT") navigate({ to: "/dashboard" });
  }, [hydrated, user, navigate]);

  const loadQueue = useCallback(async () => {
    setLoading(true);
    try {
      setQueue(await fetchQueue(status || undefined));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [status]);

  const loadSelected = useCallback(async (id: number) => {
    try {
      setSelected(await fetchRequest(id));
    } catch (e) {
      toast.error(errorMessage(e));
      setSelected(null);
    }
  }, []);

  useEffect(() => {
    if (user && user.role !== "RESIDENT") void loadQueue();
  }, [user, loadQueue]);

  useEffect(() => {
    if (isOversight) fetchStaff().then(setStaff).catch(() => setStaff([]));
  }, [isOversight]);

  useEffect(() => {
    if (selectedId != null) void loadSelected(selectedId);
  }, [selectedId, loadSelected]);

  if (!user || user.role === "RESIDENT") return null;

  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <main className="mx-auto max-w-6xl px-5 py-10">
        <div className="animate-rise flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="flex items-center gap-2 text-3xl font-bold sm:text-4xl">
              <ClipboardList className="size-8" /> Work queue
            </h1>
            <p className="mt-2 text-muted-foreground">
              {user.fullName} •{" "}
              {user.role === "STAFF" ? "requests assigned to you" : "all open requests"} • overdue first
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {(user.role === "MANAGER" || user.role === "COORDINATOR" || user.role === "ADMIN") && (
              <Button asChild variant="secondary" className="rounded-full">
                <a href={REPORTS_URL} target="_blank" rel="noreferrer">
                  <BarChart3 className="size-4" /> Management dashboard
                </a>
              </Button>
            )}
            <Button variant="secondary" className="rounded-full" onClick={() => void loadQueue()}>
              <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} /> Refresh
            </Button>
          </div>
        </div>

        <div className="mt-6 flex flex-wrap gap-2">
          {statusFilters.map((f) => (
            <button
              key={f.value}
              onClick={() => setStatus(f.value)}
              className={`rounded-full px-4 py-2 text-sm font-semibold transition ${
                status === f.value
                  ? "gradient-primary text-primary-foreground shadow-soft"
                  : "bg-secondary text-secondary-foreground"
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>

        <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_1.2fr]">
          <section aria-label="Queue" className="space-y-3">
            {queue.length === 0 && !loading && (
              <p className="rounded-3xl border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
                Nothing in this queue.
              </p>
            )}
            {queue.map((r) => (
              <button
                key={r.id}
                onClick={() => setSelectedId(r.id)}
                className={`hover-lift w-full rounded-2xl border p-4 text-left transition ${
                  selectedId === r.id ? "border-primary bg-primary/5 shadow-glow" : "border-border bg-card"
                }`}
              >
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-mono text-xs text-muted-foreground">{r.reference}</span>
                  <span
                    className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${statusTone[r.statusLabel as RequestStatus]}`}
                  >
                    {r.statusLabel}
                  </span>
                  <span className="text-xs font-semibold text-accent">{PRIORITY_LABEL[r.priority]}</span>
                  {r.overdue && (
                    <span className="flex items-center gap-1 text-xs font-semibold text-destructive">
                      <AlertTriangle className="size-3" /> OVERDUE
                    </span>
                  )}
                </div>
                <p className="mt-1 font-semibold">{r.title}</p>
                <p className="text-xs text-muted-foreground">
                  {r.categoryName} • due {formatDate(r.dueAt)}
                  {r.assigneeName ? ` • ${r.assigneeName}` : " • unassigned"}
                </p>
              </button>
            ))}
          </section>

          <section aria-label="Request detail">
            {selected ? (
              <RequestPanel
                request={selected}
                staff={staff}
                onChanged={(r) => {
                  setSelected(r);
                  void loadQueue();
                }}
                onStale={() => {
                  if (selectedId != null) void loadSelected(selectedId);
                  void loadQueue();
                }}
              />
            ) : (
              <p className="rounded-3xl border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
                Choose a request to see its history and available actions.
              </p>
            )}
          </section>
        </div>
      </main>
    </div>
  );
}

function RequestPanel({
  request,
  staff,
  onChanged,
  onStale,
}: {
  request: ServiceRequest;
  staff: ApiStaffMember[];
  onChanged: (r: ServiceRequest) => void;
  onStale: () => void;
}) {
  const [assigneeId, setAssigneeId] = useState<string>("");
  const [note, setNote] = useState("");
  const [resolution, setResolution] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setAssigneeId("");
    setNote("");
    setResolution("");
  }, [request.numericId, request.version]);

  const needs = (k: keyof ApiAction) => request.actions.some((a) => a[k]);

  async function run(a: ApiAction) {
    if (a.needsAssignee && !assigneeId) return toast.error("Choose a staff member to assign.");
    if (a.needsNote && !note.trim()) return toast.error("A note is required for this action.");
    if (a.needsResolutionNotes && !resolution.trim()) return toast.error("Describe what was done.");
    setBusy(true);
    try {
      const result = await changeStatus(request.numericId, {
        target: a.target,
        expectedVersion: request.version,
        assigneeId: a.needsAssignee ? Number(assigneeId) : undefined,
        note: note.trim() || undefined,
        resolutionNotes: a.needsResolutionNotes ? resolution.trim() : undefined,
      });
      toast.success(`${request.reference} is now ${result.request.status}.`);
      result.warnings.forEach((w) => toast.warning(w));
      onChanged(result.request);
    } catch (e) {
      toast.error(errorMessage(e));
      if (e instanceof ApiError && e.code === "STALE_REQUEST") onStale();
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className="animate-rise rounded-3xl border border-border bg-card p-6 shadow-soft">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-mono text-xs text-muted-foreground">{request.reference}</span>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${statusTone[request.status]}`}>
          {request.status}
        </span>
        <span className="text-xs text-muted-foreground">version {request.version}</span>
      </div>
      <h2 className="mt-2 text-xl font-semibold">{request.title}</h2>
      <p className="mt-1 text-sm text-muted-foreground">
        {request.categoryName} • {request.location} • due {formatDate(request.dueAt)}
      </p>
      <p className="mt-3 text-sm">{request.description}</p>

      {request.actions.length > 0 ? (
        <div className="mt-6 space-y-3 rounded-2xl bg-secondary/50 p-4">
          {needs("needsAssignee") && (
            <div>
              <Label htmlFor="assignee">Assign to</Label>
              <select
                id="assignee"
                className="mt-1 w-full rounded-xl border border-border bg-card px-3 py-2 text-sm"
                value={assigneeId}
                onChange={(e) => setAssigneeId(e.target.value)}
              >
                <option value="">Choose a staff member…</option>
                {staff.map((s) => (
                  <option key={s.userId} value={s.userId}>
                    {s.fullName}
                  </option>
                ))}
              </select>
            </div>
          )}
          {needs("needsResolutionNotes") && (
            <div>
              <Label htmlFor="resolution">What was done?</Label>
              <Textarea
                id="resolution"
                rows={2}
                className="bg-card"
                value={resolution}
                onChange={(e) => setResolution(e.target.value)}
                placeholder="e.g. Replaced the burst section of pipe and restored supply."
              />
            </div>
          )}
          <div>
            <Label htmlFor="note">Note for the audit trail {needs("needsNote") ? "(required to reject)" : "(optional)"}</Label>
            <Textarea
              id="note"
              rows={2}
              className="bg-card"
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
          </div>
          <div className="flex flex-wrap gap-2">
            {request.actions.map((a) => (
              <Button
                key={a.target}
                disabled={busy}
                variant={a.target === "REJECTED" ? "destructive" : "default"}
                className="rounded-full"
                onClick={() => void run(a)}
              >
                {a.label}
              </Button>
            ))}
          </div>
        </div>
      ) : (
        <p className="mt-6 rounded-2xl bg-secondary/50 p-4 text-sm text-muted-foreground">
          No actions available to you at this stage.
        </p>
      )}

      <h3 className="mt-6 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
        Audit trail (append-only)
      </h3>
      <ol className="mt-3 space-y-3 border-l border-border pl-5">
        {request.history.map((h, i) => (
          <li key={i} className="relative">
            <span className="gradient-primary absolute -left-[1.6rem] top-1 size-3 rounded-full" />
            <p className="text-sm font-semibold">{h.status}</p>
            <p className="text-sm text-muted-foreground">{h.note}</p>
            <p className="text-xs text-muted-foreground/70">
              {formatDate(h.at)} • {h.by}
            </p>
          </li>
        ))}
      </ol>

      {request.messages.length > 0 && (
        <>
          <h3 className="mt-6 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
            Message centre <span className="rounded-full bg-secondary px-2 py-0.5 text-[10px]">SIMULATED</span>
          </h3>
          <ul className="mt-3 space-y-2">
            {request.messages.map((m, i) => (
              <li key={i} className="rounded-xl bg-secondary/50 px-4 py-2 text-xs">
                <span className="font-semibold">{m.channel}</span> to {m.recipient} •{" "}
                {m.status === "SENT" ? "sent (simulated)" : `${m.status.toLowerCase()}, ${m.attempts} attempt(s)`}
                <p className="mt-1 text-muted-foreground">{m.body}</p>
              </li>
            ))}
          </ul>
        </>
      )}
    </article>
  );
}
