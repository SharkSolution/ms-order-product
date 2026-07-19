// Edge Function de Supabase: envía el email de reset de contraseña (F3, Inc.5).
// La invoca el backend (ms-order-product) con { to, link }. Envía vía Resend si
// RESEND_API_KEY está configurada; si no, responde {sent:false} (staging usa el
// link expuesto). Protegida por RESET_EDGE_KEY (Bearer) para que no la dispare
// cualquiera. Secretos: RESEND_API_KEY, RESET_EDGE_KEY, RESET_FROM (opcional).

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return new Response("method not allowed", { status: 405 });

  const expected = Deno.env.get("RESET_EDGE_KEY") ?? "";
  const auth = req.headers.get("Authorization") ?? "";
  if (expected && auth !== `Bearer ${expected}`) {
    return new Response("unauthorized", { status: 401 });
  }

  let body: { to?: string; link?: string };
  try {
    body = await req.json();
  } catch {
    return new Response("bad request", { status: 400 });
  }
  const { to, link } = body;
  if (!to || !link) return new Response("missing to/link", { status: 400 });

  const key = Deno.env.get("RESEND_API_KEY");
  const json = (o: unknown, s = 200) =>
    new Response(JSON.stringify(o), { status: s, headers: { "Content-Type": "application/json" } });

  if (!key) return json({ sent: false, reason: "RESEND_API_KEY no configurada" });

  const from = Deno.env.get("RESET_FROM") ?? "SureSell <noreply@suresell.co>";
  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      from,
      to,
      subject: "Restablece tu contraseña · SureSell",
      html:
        `<p>Recibimos una solicitud para restablecer tu contraseña.</p>` +
        `<p><a href="${link}">Cambiar mi contraseña</a></p>` +
        `<p>Si no fuiste tú, ignora este correo. El enlace expira en 1 hora.</p>`,
    }),
  });
  return json({ sent: res.ok }, res.ok ? 200 : 502);
});
