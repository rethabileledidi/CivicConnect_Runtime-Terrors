import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { CheckCircle2 } from "lucide-react";
import { SiteHeader } from "@/components/SiteHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiError, createRequest, formatSla, type Priority } from "@/lib/civic";
import { useAuth, useCategories } from "@/lib/use-civic";

export const Route = createFileRoute("/report")({
  head: () => ({
    meta: [
      { title: "Log a service request | CivicConnect" },
      {
        name: "description",
        content:
          "Submit a municipal service request: choose a category, describe the problem and pin the location.",
      },
      { property: "og:title", content: "Log a service request | CivicConnect" },
      {
        property: "og:description",
        content: "Choose a category, describe the problem and submit it to your municipality.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: ReportPage,
});

const priorities: Priority[] = ["Low", "Medium", "High", "Emergency"];

function ReportPage() {
  const { user, hydrated } = useAuth();
  const navigate = useNavigate();
  const [category, setCategory] = useState<number | "">("");
  const [busy, setBusy] = useState(false);
  const { categories, error: categoryError } = useCategories();
  const [priority, setPriority] = useState<Priority>("Medium");
  const [description, setDescription] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (hydrated && !user) navigate({ to: "/auth" });
  }, [hydrated, user, navigate]);

  if (!user) return null;

  const selected = categories.find((c) => c.categoryId === category);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!user) return;
    const form = new FormData(e.currentTarget);
    const v = (k: string) => String(form.get(k) ?? "").trim();
    const next: Record<string, string> = {};

    if (!category) next["category"] = "Choose a category.";
    if (v("title").length < 5) next["title"] = "Give a short, clear title.";
    if (description.trim().length < 20)
      next["description"] = "Describe the problem in at least 20 characters.";
    if (v("address").length < 5)
      next["address"] = "Street address or nearest landmark is required.";
    if (!v("suburb")) next["suburb"] = "Suburb is required.";
    if (!v("city")) next["city"] = "City or town is required.";
    if (!/^0\d{9}$/.test(v("contactNumber")))
      next["contactNumber"] = "Enter a 10-digit contact number.";

    setErrors(next);
    if (Object.keys(next).length > 0) {
      toast.error("Please fix the highlighted fields.");
      return;
    }

    setBusy(true);
    try {
      const request = await createRequest({
        categoryId: category as number,
        title: v("title"),
        description: description.trim(),
        address: v("address"),
        suburb: v("suburb"),
        city: v("city"),
        priority,
        contactNumber: v("contactNumber"),
      });
      toast.success(`Request ${request.reference} submitted`, {
        description: "You can track its status on your dashboard.",
      });
      navigate({ to: "/dashboard" });
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) setErrors(err.fieldErrors);
      toast.error(err instanceof Error ? err.message : "Could not submit the request.");
    } finally {
      setBusy(false);
    }
  }

  const err = (n: string) =>
    errors[n] ? <p className="mt-1 text-xs font-medium text-destructive">{errors[n]}</p> : null;

  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <main className="mx-auto max-w-4xl px-5 py-10">
        <h1 className="animate-rise text-3xl font-bold sm:text-4xl">Log a service request</h1>
        <p className="animate-rise mt-2 text-muted-foreground">
          The more detail you give, the faster the right team gets dispatched.
        </p>

        <form onSubmit={onSubmit} noValidate className="mt-8 space-y-8">
          <section className="animate-rise rounded-3xl border border-border bg-card p-6 shadow-soft">
            <h2 className="text-lg font-semibold">1. Category</h2>
            <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {categories.map((c) => (
                <button
                  key={c.categoryId}
                  type="button"
                  onClick={() => setCategory(c.categoryId)}
                  className={`hover-lift rounded-2xl border p-4 text-left transition ${
                    category === c.categoryId
                      ? "border-primary bg-primary/10 shadow-glow"
                      : "border-border bg-background"
                  }`}
                >
                  <span className="flex items-center justify-between font-semibold">
                    {c.name}
                    {category === c.categoryId && <CheckCircle2 className="size-4 text-primary" />}
                  </span>
                  <span className="mt-1 block text-xs text-muted-foreground">{c.description}</span>
                </button>
              ))}
            </div>
            {err("category")}
            {categoryError && (
              <p className="mt-2 text-sm text-destructive">Could not load categories: {categoryError}</p>
            )}
            {selected && (
              <p className="animate-rise mt-4 rounded-2xl bg-secondary px-4 py-3 text-sm text-secondary-foreground">
                Handled by {selected.departmentName} — target resolution {formatSla(selected.slaHours)}.
              </p>
            )}
          </section>

          <section className="animate-rise rounded-3xl border border-border bg-card p-6 shadow-soft">
            <h2 className="text-lg font-semibold">2. Describe the problem</h2>
            <div className="mt-4 grid gap-4">
              <div>
                <Label htmlFor="title">Title</Label>
                <Input id="title" name="title" placeholder="Large pothole on Church Street" />
                {err("title")}
              </div>
              <div>
                <Label htmlFor="description">Description</Label>
                <Textarea
                  id="description"
                  name="description"
                  rows={5}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Describe what you see, how long it has been there and any danger it causes."
                />
                <div className="mt-1 flex justify-between text-xs text-muted-foreground">
                  <span>{errors["description"] ? "" : "Minimum 20 characters"}</span>
                  <span>{description.length}/600</span>
                </div>
                {err("description")}
              </div>
              <div>
                <Label>Priority</Label>
                <div className="mt-2 flex flex-wrap gap-2">
                  {priorities.map((p) => (
                    <button
                      key={p}
                      type="button"
                      onClick={() => setPriority(p)}
                      className={`rounded-full px-4 py-2 text-sm font-semibold transition ${
                        priority === p
                          ? "gradient-primary text-primary-foreground shadow-soft"
                          : "bg-secondary text-secondary-foreground"
                      }`}
                    >
                      {p}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </section>

          <section className="animate-rise rounded-3xl border border-border bg-card p-6 shadow-soft">
            <h2 className="text-lg font-semibold">3. Location & contact</h2>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <div className="sm:col-span-2">
                <Label htmlFor="address">Street address or landmark</Label>
                <Input id="address" name="address" placeholder="123 Church Street" />
                {err("address")}
              </div>
              <div>
                <Label htmlFor="suburb">Suburb</Label>
                <Input id="suburb" name="suburb" placeholder="Arcadia" />
                {err("suburb")}
              </div>
              <div>
                <Label htmlFor="city">City / town</Label>
                <Input id="city" name="city" placeholder="Pretoria" />
                {err("city")}
              </div>
              <div>
                <Label htmlFor="contactNumber">Contact number</Label>
                <Input
                  id="contactNumber"
                  name="contactNumber"
                  defaultValue={user.phone}
                  inputMode="numeric"
                />
                {err("contactNumber")}
              </div>
            </div>
          </section>

          <Button
            type="submit"
            size="lg"
            disabled={busy}
            className="w-full rounded-full shadow-glow sm:w-auto"
          >
            {busy ? "Submitting…" : "Submit request"}
          </Button>
        </form>
      </main>
    </div>
  );
}
