# CivicConnect — Frontend & Requester Module (SEN381)

Person 1 deliverable: user interface, registration/login, service request submission,
categories with validation, requester dashboard, status tracking, feedback and notifications.

## Run it

```bash
npm install     # or bun install
npm run dev     # http://localhost:8080
```

## What is included

- **Landing page** (`src/routes/index.tsx`) — purple theme with animated real-world
  South African service-delivery photography (potholes, burst water mains, load shedding,
  illegal dumping): Ken Burns cross-fade hero, scrolling issue ticker, hover-lift cards.
- **Registration & login** (`src/routes/auth.tsx`) — client-side validation for name,
  email, 10-digit SA cellphone, municipality and password confirmation.
- **Service request form** (`src/routes/report.tsx`) — six municipal categories with SLAs,
  priority selection, location and contact capture, full input validation.
- **Requester dashboard** (`src/routes/dashboard.tsx`) — request list with filters,
  status progress bar, expandable status timeline, simulated status updates and a
  star-rating feedback form.
- **Notifications** — in-app bell with unread badge, fed by request and status events.
- **Design system** (`src/styles.css`) — purple oklch tokens, gradients, shadows and
  keyframe animations. No hardcoded colours in components.
- **Data layer** (`src/lib/civic.ts`) — typed store backed by browser localStorage, so the
  frontend runs standalone. Swap these functions for the team's API/DB layer later.

Stack: React 19 + TanStack Start/Router, Tailwind CSS v4, shadcn/ui, lucide-react, sonner.
