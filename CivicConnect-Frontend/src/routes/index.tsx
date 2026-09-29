import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowRight, ClipboardList, MapPin, Sparkles, Timer } from "lucide-react";
import { SiteHeader } from "@/components/SiteHeader";
import { Button } from "@/components/ui/button";
import { CATEGORIES } from "@/lib/civic";
import { useAuth } from "@/lib/use-civic";
import heroPotholes from "@/assets/hero-potholes.jpg";
import catWater from "@/assets/cat-water.jpg";
import catPower from "@/assets/cat-power.jpg";
import catWaste from "@/assets/cat-waste.jpg";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "CivicConnect | Report municipal service problems in South Africa" },
      {
        name: "description",
        content:
          "Log potholes, water leaks, power outages and waste issues with your municipality and track every request to resolution.",
      },
      { property: "og:title", content: "CivicConnect | Report municipal service problems" },
      {
        property: "og:description",
        content:
          "Log potholes, water leaks, power outages and waste issues and track every request to resolution.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Landing,
});

const slides = [heroPotholes, catWater, catWaste, catPower];

const stats = [
  { value: "48h", label: "Average first response" },
  { value: "6", label: "Service categories" },
  { value: "24/7", label: "Logging available" },
];

function Landing() {
  const { user } = useAuth();

  return (
    <div className="min-h-screen bg-background">
      <SiteHeader transparent />

      {/* Hero with cross-fading real-world imagery */}
      <section className="relative isolate flex min-h-[92vh] items-center overflow-hidden">
        <div className="absolute inset-0 -z-20">
          {slides.map((src, i) => (
            <img
              key={src}
              src={src}
              alt=""
              aria-hidden="true"
              className="animate-kenburns absolute inset-0 size-full object-cover"
              style={{
                animation: `cross-fade 24s ${i * 6}s infinite, ken-burns 24s ${i * 6}s infinite alternate`,
                opacity: i === 0 ? 1 : 0,
              }}
            />
          ))}
        </div>
        <div className="gradient-veil absolute inset-0 -z-10" />

        <div className="mx-auto w-full max-w-6xl px-5 pt-32 pb-20 text-primary-foreground">
          <span className="animate-rise glass-panel inline-flex items-center gap-2 rounded-full px-4 py-1.5 text-xs font-semibold uppercase tracking-widest">
            <Sparkles className="size-3.5" /> Citizen service portal
          </span>
          <h1
            className="animate-rise mt-6 max-w-3xl text-4xl font-bold leading-[1.05] sm:text-6xl"
            style={{ animationDelay: "0.1s" }}
          >
            Potholes, burst pipes, dark streets. <span className="text-gradient">Report it once.</span>{" "}
            Track it to the end.
          </h1>
          <p
            className="animate-rise mt-5 max-w-xl text-base/7 opacity-90"
            style={{ animationDelay: "0.2s" }}
          >
            CivicConnect gives South African residents one place to log municipal service problems,
            follow every status change, and rate the repair once the team is done.
          </p>

          <div
            className="animate-rise mt-9 flex flex-wrap gap-3"
            style={{ animationDelay: "0.3s" }}
          >
            <Button asChild size="lg" className="rounded-full px-7 shadow-glow">
              <Link to={user ? "/report" : "/auth"}>
                Report an issue <ArrowRight className="size-4" />
              </Link>
            </Button>
            <Button
              asChild
              size="lg"
              variant="ghost"
              className="glass-panel rounded-full px-7 text-primary-foreground hover:bg-primary-foreground/20 hover:text-primary-foreground"
            >
              <Link to={user ? "/dashboard" : "/auth"}>My requests</Link>
            </Button>
          </div>

          <dl
            className="animate-rise mt-14 grid max-w-2xl grid-cols-3 gap-4"
            style={{ animationDelay: "0.4s" }}
          >
            {stats.map((s) => (
              <div key={s.label} className="glass-panel rounded-2xl px-5 py-4">
                <dt className="font-display text-2xl font-bold">{s.value}</dt>
                <dd className="mt-1 text-xs opacity-85">{s.label}</dd>
              </div>
            ))}
          </dl>
        </div>
      </section>

      {/* Scrolling issue strip */}
      <section className="overflow-hidden border-y border-border bg-secondary/50 py-4">
        <div className="animate-marquee flex w-max gap-10 whitespace-nowrap text-sm font-semibold uppercase tracking-widest text-muted-foreground">
          {[...Array(2)].map((_, r) => (
            <div key={r} className="flex gap-10">
              {[
                "Potholes",
                "Load shedding faults",
                "Burst water mains",
                "Illegal dumping",
                "Dark street lights",
                "Broken robots",
                "Sewer spills",
              ].map((t) => (
                <span key={t + r} className="flex items-center gap-3">
                  <span className="size-1.5 rounded-full bg-accent" /> {t}
                </span>
              ))}
            </div>
          ))}
        </div>
      </section>

      {/* Categories */}
      <section className="mx-auto max-w-6xl px-5 py-20">
        <h2 className="text-3xl font-bold sm:text-4xl">What can you report?</h2>
        <p className="mt-3 max-w-xl text-muted-foreground">
          Six municipal categories, each with its own service level target so you know what to
          expect after you submit.
        </p>

        <div className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {CATEGORIES.map((c, i) => (
            <article
              key={c.id}
              className="hover-lift animate-rise rounded-3xl border border-border bg-card p-6 shadow-soft"
              style={{ animationDelay: `${i * 0.07}s` }}
            >
              <span className="gradient-primary flex size-10 items-center justify-center rounded-xl text-primary-foreground">
                <ClipboardList className="size-5" />
              </span>
              <h3 className="mt-4 text-lg font-semibold">{c.name}</h3>
              <p className="mt-2 text-sm text-muted-foreground">{c.blurb}</p>
              <p className="mt-4 inline-flex items-center gap-2 rounded-full bg-secondary px-3 py-1 text-xs font-semibold text-secondary-foreground">
                <Timer className="size-3.5" /> {c.sla}
              </p>
            </article>
          ))}
        </div>
      </section>

      {/* Photo band */}
      <section className="mx-auto max-w-6xl px-5 pb-20">
        <div className="grid gap-4 sm:grid-cols-3">
          {[
            { src: catWater, label: "Water outages & burst mains" },
            { src: catPower, label: "Power failures after dark" },
            { src: catWaste, label: "Uncollected refuse" },
          ].map((p, i) => (
            <figure
              key={p.label}
              className="animate-rise group relative overflow-hidden rounded-3xl shadow-soft"
              style={{ animationDelay: `${i * 0.1}s` }}
            >
              <img
                src={p.src}
                alt={p.label}
                loading="lazy"
                width={1280}
                height={864}
                className="h-60 w-full object-cover transition-transform duration-700 group-hover:scale-110"
              />
              <figcaption className="gradient-veil absolute inset-x-0 bottom-0 p-4 text-sm font-semibold text-primary-foreground">
                <MapPin className="mr-1 inline size-4" />
                {p.label}
              </figcaption>
            </figure>
          ))}
        </div>
      </section>

      {/* CTA */}
      <section className="mx-auto max-w-6xl px-5 pb-24">
        <div className="gradient-vivid animate-rise relative overflow-hidden rounded-[2rem] px-8 py-14 text-center text-primary-foreground shadow-elegant">
          <div className="animate-float absolute -right-10 -top-10 size-40 rounded-full bg-primary-foreground/10" />
          <h2 className="text-3xl font-bold sm:text-4xl">Your street deserves an answer.</h2>
          <p className="mx-auto mt-3 max-w-lg opacity-90">
            Create a free account and log your first service request in under two minutes.
          </p>
          <Button
            asChild
            size="lg"
            variant="secondary"
            className="mt-7 rounded-full px-8 hover-lift"
          >
            <Link to={user ? "/report" : "/auth"}>Get started</Link>
          </Button>
        </div>
      </section>

      <footer className="border-t border-border py-8 text-center text-sm text-muted-foreground">
        CivicConnect — SEN381 Requester Module
      </footer>
    </div>
  );
}
