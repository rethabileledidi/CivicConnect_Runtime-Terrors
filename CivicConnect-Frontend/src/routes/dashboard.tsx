import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { ChevronDown, Inbox, Plus, Star } from "lucide-react";
import { SiteHeader } from "@/components/SiteHeader";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import {
  advanceRequest,
  categoryById,
  formatDate,
  saveFeedback,
  statusStep,
  statusTone,
  type RequestStatus,
  type ServiceRequest,
} from "@/lib/civic";
import { useAuth, useRequests } from "@/lib/use-civic";

export const Route = createFileRoute("/dashboard")({
  head: () => ({
    meta: [
      { title: "My requests | CivicConnect" },
      {
        name: "description",
        content:
          "Track your submitted municipal service requests, follow status updates and rate completed repairs.",
      },
      { property: "og:title", content: "My requests | CivicConnect" },
      {
        property: "og:description",
        content: "Track your service requests and rate completed repairs.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Dashboard,
});

const filters = ["All", "Submitted", "In Progress", "Resolved"] as const;

function Dashboard() {
  const { user, hydrated } = useAuth();
  const navigate = useNavigate();
  const requests = useRequests(user?.email);
  const [filter, setFilter] = useState<(typeof filters)[number]>("All");
  const [openId, setOpenId] = useState<string | null>(null);

  useEffect(() => {
    if (hydrated && !user) navigate({ to: "/auth" });
  }, [hydrated, user, navigate]);

  const counts = useMemo(
    () => ({
      total: requests.length,
      open: requests.filter((r) => r.status !== "Resolved").length,
      resolved: requests.filter((r) => r.status === "Resolved").length,
    }),
    [requests],
  );

  if (!user) return null;

  const visible = requests.filter((r) => filter === "All" || r.status === filter);

  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <main className="mx-auto max-w-5xl px-5 py-10">
        <div className="animate-rise flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold sm:text-4xl">Hello, {user.fullName.split(" ")[0]}</h1>
            <p className="mt-2 text-muted-foreground">
              {user.municipality} • {user.email}
            </p>
          </div>
          <Button asChild className="rounded-full shadow-glow">
            <Link to="/report">
              <Plus className="size-4" /> New request
            </Link>
          </Button>
        </div>

        <div className="mt-8 grid gap-4 sm:grid-cols-3">
          {[
            { label: "Total logged", value: counts.total },
            { label: "Still open", value: counts.open },
            { label: "Resolved", value: counts.resolved },
          ].map((s, i) => (
            <div
              key={s.label}
              className="animate-rise rounded-3xl border border-border bg-card p-6 shadow-soft"
              style={{ animationDelay: `${i * 0.08}s` }}
            >
              <p className="font-display text-3xl font-bold text-gradient">{s.value}</p>
              <p className="mt-1 text-sm text-muted-foreground">{s.label}</p>
            </div>
          ))}
        </div>

        <div className="mt-8 flex flex-wrap gap-2">
          {filters.map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={`rounded-full px-4 py-2 text-sm font-semibold transition ${
                filter === f
                  ? "gradient-primary text-primary-foreground shadow-soft"
                  : "bg-secondary text-secondary-foreground"
              }`}
            >
              {f}
            </button>
          ))}
        </div>

        <div className="mt-6 space-y-4">
          {visible.length === 0 && (
            <div className="animate-rise rounded-3xl border border-dashed border-border p-12 text-center">
              <Inbox className="mx-auto size-10 text-muted-foreground" />
              <p className="mt-4 font-semibold">Nothing here yet</p>
              <p className="mt-1 text-sm text-muted-foreground">
                Log a pothole, water leak or outage and it will appear here.
              </p>
              <Button asChild className="mt-6 rounded-full">
                <Link to="/report">Report an issue</Link>
              </Button>
            </div>
          )}

          {visible.map((r, i) => (
            <RequestCard
              key={r.id}
              request={r}
              open={openId === r.id}
              index={i}
              onToggle={() => setOpenId(openId === r.id ? null : r.id)}
            />
          ))}
        </div>
      </main>
    </div>
  );
}

function RequestCard({
  request,
  open,
  index,
  onToggle,
}: {
  request: ServiceRequest;
  open: boolean;
  index: number;
  onToggle: () => void;
}) {
  const [rating, setRating] = useState(request.feedbackRating ?? 0);
  const [comment, setComment] = useState(request.feedbackComment ?? "");
  const step = statusStep[request.status as RequestStatus];

  return (
    <article
      className="animate-rise overflow-hidden rounded-3xl border border-border bg-card shadow-soft"
      style={{ animationDelay: `${index * 0.06}s` }}
    >
      <button onClick={onToggle} className="flex w-full items-start gap-4 p-6 text-left">
        <div className="flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-xs text-muted-foreground">{request.reference}</span>
            <span
              className={`rounded-full px-3 py-1 text-xs font-semibold ${statusTone[request.status]}`}
            >
              {request.status}
            </span>
            <span className="rounded-full bg-accent/15 px-3 py-1 text-xs font-semibold text-accent">
              {request.priority}
            </span>
          </div>
          <h3 className="mt-2 text-lg font-semibold">{request.title}</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {categoryById(request.category)?.name} • {request.suburb}, {request.city} •{" "}
            {formatDate(request.createdAt)}
          </p>

          <div className="mt-4 flex gap-1.5">
            {[1, 2, 3].map((s) => (
              <span
                key={s}
                className={`h-1.5 flex-1 rounded-full transition-all duration-500 ${
                  s <= step ? "gradient-primary" : "bg-secondary"
                }`}
              />
            ))}
          </div>
        </div>
        <ChevronDown
          className={`mt-1 size-5 shrink-0 text-muted-foreground transition-transform ${open ? "rotate-180" : ""}`}
        />
      </button>

      {open && (
        <div className="animate-rise border-t border-border px-6 py-6">
          <p className="text-sm">{request.description}</p>
          <p className="mt-2 text-sm text-muted-foreground">
            {request.address}, {request.suburb} • Contact {request.contactNumber}
          </p>

          <h4 className="mt-6 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
            Status timeline
          </h4>
          <ol className="mt-3 space-y-4 border-l border-border pl-5">
            {request.history.map((h, i) => (
              <li key={i} className="relative">
                <span className="gradient-primary absolute -left-[1.6rem] top-1 size-3 rounded-full" />
                <p className="text-sm font-semibold">{h.status}</p>
                <p className="text-sm text-muted-foreground">{h.note}</p>
                <p className="text-xs text-muted-foreground/70">{formatDate(h.at)}</p>
              </li>
            ))}
          </ol>

          {request.status !== "Resolved" && (
            <Button
              variant="secondary"
              className="mt-6 rounded-full"
              onClick={() => {
                advanceRequest(request.id);
                toast.info("Status updated by the municipal team.");
              }}
            >
              Simulate next status update
            </Button>
          )}

          {request.status === "Resolved" && (
            <div className="mt-6 rounded-2xl bg-secondary/60 p-5">
              <h4 className="text-sm font-semibold">Rate this repair</h4>
              <div className="mt-2 flex gap-1">
                {[1, 2, 3, 4, 5].map((s) => (
                  <button
                    key={s}
                    aria-label={`${s} star`}
                    onClick={() => setRating(s)}
                    className="transition hover:scale-110"
                  >
                    <Star
                      className={`size-6 ${s <= rating ? "fill-accent text-accent" : "text-muted-foreground"}`}
                    />
                  </button>
                ))}
              </div>
              <Textarea
                className="mt-3 bg-card"
                rows={3}
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                placeholder="How was the service?"
              />
              <Button
                className="mt-3 rounded-full"
                onClick={() => {
                  if (rating === 0) {
                    toast.error("Pick a star rating first.");
                    return;
                  }
                  saveFeedback(request.id, rating, comment);
                  toast.success("Thanks for your feedback!");
                }}
              >
                Submit feedback
              </Button>
            </div>
          )}
        </div>
      )}
    </article>
  );
}
