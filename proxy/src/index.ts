import { Hono } from "hono";
import { createMiddleware } from "hono/factory";
import { logger } from "hono/logger";

const cache = createMiddleware<{ Bindings: CloudflareBindings }>(async (c, next) => {
  await next();

  c.header("Cache-Control", "no-store");

  if (c.req.method !== "GET" || !c.res.ok) {
    return;
  }

  c.header(
    "Cloudflare-CDN-Cache-Control",
    `public, max-age=${c.env.CACHE_TTL}, stale-while-revalidate=60`,
  );
});

const app = new Hono<{ Bindings: CloudflareBindings }>();

app.use("*", logger());
app.use("/api/*", cache);

app.get("/api/room", async (c) => {
  const url = new URL(c.req.url);
  if (url.search) return c.redirect(url.pathname, 308);

  try {
    const res = await fetch(`${c.env.API_URL}/room`, {
      headers: {
        Authorization: `Bearer ${c.env.CHOCKIN_TOKEN}`,
        Accept: "application/json",
      },
      signal: AbortSignal.timeout(10_000),
    });

    const headers = new Headers();
    const contentType = res.headers.get("Content-Type");
    if (contentType) headers.set("Content-Type", contentType);
    return new Response(res.body, { status: res.status, headers });
  } catch (error) {
    console.error("Upstream request failed", error);
    return c.json({ error: "upstream_unavailable" }, 502);
  }
});

export default app;
