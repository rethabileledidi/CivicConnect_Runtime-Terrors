import { Link, useNavigate } from "@tanstack/react-router";
import { Bell, LogOut, Menu, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { logout, markNotificationsRead } from "@/lib/civic";
import { useAuth, useNotifications } from "@/lib/use-civic";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { formatDate } from "@/lib/civic";

export function SiteHeader({ transparent = false }: { transparent?: boolean }) {
  const { user } = useAuth();
  const notifications = useNotifications(user?.email);
  const unread = notifications.filter((n) => !n.read).length;
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  const links = [
    { to: "/", label: "Home" },
    { to: "/report", label: "Report an issue" },
    { to: "/dashboard", label: "My dashboard" },
  ] as const;

  return (
    <header
      className={
        transparent
          ? "absolute inset-x-0 top-0 z-30 text-primary-foreground"
          : "sticky top-0 z-30 border-b border-border bg-card/85 backdrop-blur-xl"
      }
    >
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-5 py-4">
        <Link to="/" className="flex items-center gap-2 font-display text-lg font-bold">
          <span className="gradient-primary flex size-9 items-center justify-center rounded-xl shadow-glow">
            <ShieldCheck className="size-5 text-primary-foreground" />
          </span>
          CivicConnect
        </Link>

        <nav className="hidden items-center gap-1 md:flex">
          {links.map((l) => (
            <Link
              key={l.to}
              to={l.to}
              className="rounded-full px-4 py-2 text-sm font-medium opacity-80 transition hover:bg-primary/10 hover:opacity-100"
              activeProps={{ className: "bg-primary/15 opacity-100" }}
            >
              {l.label}
            </Link>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          {user ? (
            <>
              <Popover onOpenChange={(o) => o && user && markNotificationsRead(user.email)}>
                <PopoverTrigger asChild>
                  <Button variant="ghost" size="icon" className="relative rounded-full">
                    <Bell className="size-5" />
                    {unread > 0 && (
                      <span className="animate-pulse-glow absolute -right-0.5 -top-0.5 flex size-5 items-center justify-center rounded-full bg-accent text-[10px] font-bold text-accent-foreground">
                        {unread}
                      </span>
                    )}
                  </Button>
                </PopoverTrigger>
                <PopoverContent align="end" className="w-80 p-0">
                  <p className="border-b border-border px-4 py-3 font-display text-sm font-semibold">
                    Notifications
                  </p>
                  <div className="max-h-80 overflow-y-auto">
                    {notifications.length === 0 && (
                      <p className="px-4 py-6 text-sm text-muted-foreground">
                        No updates yet. Submit a request to start tracking it.
                      </p>
                    )}
                    {notifications.map((n) => (
                      <div key={n.id} className="animate-rise border-b border-border/60 px-4 py-3">
                        <p className="text-sm font-semibold">{n.title}</p>
                        <p className="mt-1 text-xs text-muted-foreground">{n.body}</p>
                        <p className="mt-1 text-[11px] text-muted-foreground/70">
                          {formatDate(n.at)}
                        </p>
                      </div>
                    ))}
                  </div>
                </PopoverContent>
              </Popover>
              <Button
                variant="ghost"
                size="icon"
                className="rounded-full"
                aria-label="Sign out"
                onClick={() => {
                  logout();
                  navigate({ to: "/auth" });
                }}
              >
                <LogOut className="size-5" />
              </Button>
            </>
          ) : (
            <Button asChild className="rounded-full">
              <Link to="/auth">Sign in</Link>
            </Button>
          )}
          <Button
            variant="ghost"
            size="icon"
            className="rounded-full md:hidden"
            aria-label="Menu"
            onClick={() => setOpen((v) => !v)}
          >
            <Menu className="size-5" />
          </Button>
        </div>
      </div>

      {open && (
        <div className="animate-rise mx-4 mb-3 grid gap-1 rounded-2xl bg-card p-2 text-foreground shadow-elegant md:hidden">
          {links.map((l) => (
            <Link
              key={l.to}
              to={l.to}
              onClick={() => setOpen(false)}
              className="rounded-xl px-4 py-2 text-sm font-medium hover:bg-secondary"
            >
              {l.label}
            </Link>
          ))}
        </div>
      )}
    </header>
  );
}
