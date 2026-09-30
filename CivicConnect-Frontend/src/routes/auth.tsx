import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { loginUser, registerUser } from "@/lib/civic";
import { useAuth } from "@/lib/use-civic";
import heroPotholes from "@/assets/hero-potholes.jpg";

export const Route = createFileRoute("/auth")({
  head: () => ({
    meta: [
      { title: "Sign in or register | CivicConnect" },
      {
        name: "description",
        content:
          "Create a CivicConnect account or sign in to log municipal service requests and track their progress.",
      },
      { property: "og:title", content: "Sign in or register | CivicConnect" },
      {
        property: "og:description",
        content: "Create an account to log municipal service requests and track their progress.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: AuthPage,
});

type Errors = Record<string, string>;

const emailRe = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const phoneRe = /^0\d{9}$/;

function AuthPage() {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [errors, setErrors] = useState<Errors>({});
  const navigate = useNavigate();
  const { user } = useAuth();

  useEffect(() => {
    if (user) navigate({ to: "/dashboard" });
  }, [user, navigate]);

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    const v = (k: string) => String(form.get(k) ?? "").trim();
    const next: Errors = {};

    if (!emailRe.test(v("email"))) next["email"] = "Enter a valid email address.";
    if (v("password").length < 6) next["password"] = "Password must be at least 6 characters.";

    if (mode === "register") {
      if (v("fullName").length < 3) next["fullName"] = "Enter your full name.";
      if (!phoneRe.test(v("phone"))) next["phone"] = "Enter a 10-digit SA number, e.g. 0821234567.";
      if (!v("municipality")) next["municipality"] = "Tell us which municipality you fall under.";
      if (v("password") !== v("confirm")) next["confirm"] = "Passwords do not match.";
    }

    setErrors(next);
    if (Object.keys(next).length > 0) return;

    try {
      if (mode === "register") {
        registerUser({
          fullName: v("fullName"),
          email: v("email"),
          phone: v("phone"),
          municipality: v("municipality"),
          password: v("password"),
        });
        toast.success("Account created", { description: "Welcome to CivicConnect." });
      } else {
        loginUser(v("email"), v("password"));
        toast.success("Signed in");
      }
      navigate({ to: "/dashboard" });
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Something went wrong.");
    }
  }

  const field = (name: string) =>
    errors[name] ? (
      <p className="mt-1 text-xs font-medium text-destructive">{errors[name]}</p>
    ) : null;

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <div className="relative hidden overflow-hidden lg:block">
        <img
          src={heroPotholes}
          alt="Potholed street in a South African suburb"
          className="animate-kenburns absolute inset-0 size-full object-cover"
        />
        <div className="gradient-veil absolute inset-0" />
        <div className="absolute inset-0 flex flex-col justify-end p-12 text-primary-foreground">
          <ShieldCheck className="size-10" />
          <h2 className="animate-rise mt-4 text-4xl font-bold leading-tight">
            One account. Every municipal issue on your street.
          </h2>
          <p className="animate-rise mt-3 max-w-md opacity-85" style={{ animationDelay: "0.1s" }}>
            Log it, track it, rate the repair.
          </p>
        </div>
      </div>

      <div className="flex items-center justify-center px-5 py-14">
        <div className="animate-rise w-full max-w-md">
          <Link to="/" className="font-display text-lg font-bold">
            CivicConnect
          </Link>
          <h1 className="mt-6 text-3xl font-bold">
            {mode === "login" ? "Welcome back" : "Create your account"}
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">
            {mode === "login"
              ? "Sign in to view and track your service requests."
              : "Register to report problems in your area."}
          </p>

          <div className="mt-6 grid grid-cols-2 gap-1 rounded-full bg-secondary p-1">
            {(["login", "register"] as const).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => {
                  setMode(m);
                  setErrors({});
                }}
                className={`rounded-full py-2 text-sm font-semibold transition ${
                  mode === m
                    ? "gradient-primary text-primary-foreground shadow-soft"
                    : "text-muted-foreground"
                }`}
              >
                {m === "login" ? "Sign in" : "Register"}
              </button>
            ))}
          </div>

          <form onSubmit={onSubmit} noValidate className="mt-6 space-y-4">
            {mode === "register" && (
              <div>
                <Label htmlFor="fullName">Full name</Label>
                <Input id="fullName" name="fullName" placeholder="Thandi Mokoena" />
                {field("fullName")}
              </div>
            )}
            <div>
              <Label htmlFor="email">Email</Label>
              <Input id="email" name="email" type="email" placeholder="you@example.co.za" />
              {field("email")}
            </div>
            {mode === "register" && (
              <>
                <div>
                  <Label htmlFor="phone">Cellphone</Label>
                  <Input id="phone" name="phone" placeholder="0821234567" inputMode="numeric" />
                  {field("phone")}
                </div>
                <div>
                  <Label htmlFor="municipality">Municipality</Label>
                  <Input id="municipality" name="municipality" placeholder="City of Tshwane" />
                  {field("municipality")}
                </div>
              </>
            )}
            <div>
              <Label htmlFor="password">Password</Label>
              <Input id="password" name="password" type="password" placeholder="••••••••" />
              {field("password")}
            </div>
            {mode === "register" && (
              <div>
                <Label htmlFor="confirm">Confirm password</Label>
                <Input id="confirm" name="confirm" type="password" placeholder="••••••••" />
                {field("confirm")}
              </div>
            )}

            <Button type="submit" className="w-full rounded-full shadow-glow" size="lg">
              {mode === "login" ? "Sign in" : "Create account"}
            </Button>
          </form>

          <p className="mt-6 text-center text-xs text-muted-foreground">
            Accounts are stored on this device for the prototype build.
          </p>
        </div>
      </div>
    </div>
  );
}
