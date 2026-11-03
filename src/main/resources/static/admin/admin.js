/*
 * Fælles kode for alle adminsider: login-tjek, menu, log ud, kald til API'et og formatering.
 * Filen ligger under /admin, så man kun kan hente den, når man er logget ind.
 * Hver side har derudover sit eget script (overblik.js, bestillinger.js ...).
 */

const STATUS_LABELS = {
    PENDING: "Afventer betaling",
    PAID: "Betalt",
    CANCELLED: "Annulleret",
    REFUNDED: "Refunderet",
};

let csrf = null;           // hentes ved opstart og sendes med alle POST/PUT-kald
let events = [];           // alle events, til vælgeren i menuen
let currentEventId = null; // det event, siderne viser tal for
const EVENT_STORAGE_KEY = "billetsystem.admin.eventId";

const kroner = new Intl.NumberFormat("da-DK", { style: "currency", currency: "DKK" });
const kronerRounded = new Intl.NumberFormat("da-DK", { style: "currency", currency: "DKK", maximumFractionDigits: 0 });
const dateFormat = new Intl.DateTimeFormat("da-DK", { day: "numeric", month: "short", year: "numeric" });
const dateTimeFormat = new Intl.DateTimeFormat("da-DK", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });

/** Priser gemmes i øre (29500 = 295,00 kr.). rounded = uden ører, til store tal. */
const formatPrice = (ore, rounded = false) => (rounded ? kronerRounded : kroner).format(ore / 100);
const formatDate = (iso) => (iso ? dateFormat.format(new Date(iso)) : "–");
const formatDateTime = (iso) => (iso ? dateTimeFormat.format(new Date(iso)) : "–");

/**
 * Opretter et HTML-element. Tekst sættes altid med textContent, så navne og beskeder fra
 * kunder aldrig kan tolkes som HTML (det beskytter mod såkaldt XSS).
 *   h("a", { href: "/x", className: "link" }, "Klik her")
 */
function h(tag, props = {}, ...children) {
    const element = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
        if (key.startsWith("on")) element.addEventListener(key.slice(2).toLowerCase(), value);
        else if (key === "dataset") Object.assign(element.dataset, value);
        else if (key in element) element[key] = value;
        else element.setAttribute(key, value);
    }
    for (const child of children.flat(Infinity)) {
        if (child != null && child !== false) element.append(child instanceof Node ? child : String(child));
    }
    return element;
}

/** Små mærker for en deltagers pladsønsker, fx på deltagerlisten. */
function wishTags(p) {
    return [
        p.beginnerTable && h("span", { className: "wish-tag" }, "Begynderbord"),
        p.needsTableEnd && h("span", { className: "wish-tag" }, "Bordende"),
    ];
}

/** Et lille farvet mærke med status. Teksten står altid ved siden af farven. */
function statusBadge(status) {
    return h("span", { className: "badge badge-" + status.toLowerCase() }, STATUS_LABELS[status] ?? status);
}

/**
 * Kalder API'et og returnerer JSON.
 * Er man ikke logget ind (fx fordi sessionen er udløbet), sendes man til login-siden.
 * Fejl fra serveren kastes som en Error med serverens besked, så siden kan vise den.
 */
async function api(url, { method = "GET", body } = {}) {
    const options = { method, headers: {} };
    if (body !== undefined) {
        options.headers["Content-Type"] = "application/json";
        options.body = JSON.stringify(body);
    }
    if (method !== "GET") options.headers[csrf.headerName] = csrf.token;

    const response = await fetch(url, options);
    if (response.status === 401) {
        location.href = "/admin/login.html";
        throw new Error("Ikke logget ind");
    }
    const data = await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error(data?.fejl ?? "Noget gik galt (" + response.status + ")");
        error.fields = data?.felter ?? {};
        throw error;
    }
    return data;
}

/** Venter med at kalde fn, til brugeren har holdt pause i skrivningen (så vi ikke søger for hvert tastetryk). */
function debounce(fn, ms = 250) {
    let timer;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), ms);
    };
}

/** Tilføjer det valgte event til en adresse: withEvent("/api/admin/orders") -> "/api/admin/orders?eventId=2". */
function withEvent(url) {
    const u = new URL(url, location.origin);
    u.searchParams.set("eventId", currentEventId);
    return u.pathname + u.search;
}

/**
 * Hvilket event skal vises? 1) ?event= i adressen, 2) det, admin sidst valgte, 3) det næste kommende.
 * Valget gemmes i browseren (localStorage), så det huskes mellem siderne.
 */
function chooseEvent() {
    const exists = (id) => events.some((e) => e.id === id);
    const fromUrl = Number(new URLSearchParams(location.search).get("event"));
    let stored = null;
    try {
        stored = Number(localStorage.getItem(EVENT_STORAGE_KEY));
    } catch {
        // localStorage kan være slået fra (fx privat vindue). Så bruger vi bare standard-eventet.
    }
    const chosen = [fromUrl, stored].find((id) => id && exists(id)) ?? events.find((e) => e.isDefault)?.id ?? null;
    rememberEvent(chosen);
    return chosen;
}

function rememberEvent(id) {
    try {
        localStorage.setItem(EVENT_STORAGE_KEY, String(id));
    } catch {
        // se chooseEvent
    }
}

function renderEventPicker() {
    const select = document.getElementById("event-select");
    if (!select) return;
    const today = new Date().toISOString().slice(0, 10);
    select.replaceChildren(...events.map((e) =>
        h("option", { value: e.id, selected: e.id === currentEventId },
            e.name + (e.endDate < today ? " (overstået)" : ""))));
    select.disabled = events.length === 0;
    select.addEventListener("change", () => {
        rememberEvent(Number(select.value));
        // Genindlæs siden uden ?event= og ?id=, så den viser det nye event fra start
        location.href = location.pathname;
    });
}

/** Vises, hvis en side ikke kan hente sine data. */
function showPageError(message) {
    const box = document.getElementById("page-error");
    box.textContent = message;
    box.hidden = false;
}

/**
 * Køres af hver side ved opstart: tjekker login, viser brugernavn og gør log ud klar.
 * Returnerer et promise, så siden kan vente på, at csrf er klar.
 */
const adminReady = (async () => {
    await new Promise((resolve) => document.addEventListener("DOMContentLoaded", resolve, { once: true }));

    const [me, token, eventList] = await Promise.all([api("/api/admin/me"), api("/api/csrf"), api("/api/admin/events")]);
    csrf = token;
    events = eventList;
    currentEventId = chooseEvent();
    renderEventPicker();

    // Links som "Hent som Excel" skal hente data for det valgte event
    for (const link of document.querySelectorAll("a[data-event-link]")) {
        link.href = withEvent(link.getAttribute("href"));
    }

    document.getElementById("whoami").textContent = me.username;
    const field = document.getElementById("logout-csrf");
    field.name = csrf.parameterName;
    field.value = csrf.token;

    // Markér den side, man står på, i menuen
    for (const link of document.querySelectorAll(".admin-nav a")) {
        if (link.pathname === location.pathname) link.setAttribute("aria-current", "page");
    }
})();
