import { Hono } from "hono";
import { createMiddleware } from "hono/factory";

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

app.use("/api/*", cache);

app.get("/api/*", async (c) => {
  const url = new URL(c.req.url);

  const upstream = new URL(url.pathname.replace("/api", c.env.API_URL));
  const res = await fetch(upstream, {
    headers: {
      Authorization: `Bearer ${c.env.CHOCKIN_TOKEN}`,
      Accept: "application/json",
    },
  });

  return new Response(res.body, {
    status: res.status,
    headers: {
      "Content-Type": "applicaton/json",
    },
  });
});

export default app;
