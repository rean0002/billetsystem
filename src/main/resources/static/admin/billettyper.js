/*
 * Billettyper: se priser, perioder og salg, opret nye billettyper og ret eksisterende.
 * Datoerne i formularen er i din lokale tid (dansk tid), og sendes til serveren med tidszone.
 */

let types = [];
let editingId = null; // null = ny billettype

adminReady.then(async () => {
    document.getElementById("new-type").addEventListener("click", () => openForm(null));
    document.getElementById("type-cancel").addEventListener("click", () => document.getElementById("type-dialog").close());
    document.getElementById("type-form").addEventListener("submit", save);

    try {
        types = await api(withEvent("/api/admin/ticket-types"));
        const event = events.find((e) => e.id === currentEventId);
        if (event) document.querySelector("h1").textContent = "Billettyper: " + event.name;
        renderTypes();
    } catch (err) {
        showPageError(err.message);
    }
}).catch(console.error);

/** Til salg nu, planlagt, udløbet eller slået fra? */
function availability(t) {
    const now = new Date();
    if (!t.active) return { label: "Slået fra", className: "badge-cancelled" };
    if (new Date(t.validFrom) > now) return { label: "Planlagt", className: "badge-pending" };
    if (t.validTo && new Date(t.validTo) < now) return { label: "Udløbet", className: "badge-cancelled" };
    return { label: "Til salg nu", className: "badge-paid" };
}

function renderTypes() {
    document.querySelector("#types-table tbody").replaceChildren(...types.map((t) => {
        const status = availability(t);
        return h("tr", {},
            h("td", {}, h("strong", {}, t.name), h("small", { className: "muted block" }, formatPrice(t.priceOre))),
            h("td", { className: "nowrap" }, "Fra " + formatDateTime(t.validFrom), h("small", { className: "muted block" },
                t.validTo ? "til " + formatDateTime(t.validTo) : "ingen slutdato")),
            h("td", {}, h("span", { className: "badge " + status.className }, status.label)),
            h("td", { className: "num" }, t.soldPaid + t.soldPending,
                h("small", { className: "muted block" }, `${t.soldPaid} betalt · ${t.soldPending} afventer`)),
            h("td", { className: "num" }, formatPrice(t.revenuePaidOre, true)),
            h("td", { className: "num" }, h("button", { type: "button", className: "link-button", onClick: () => openForm(t) }, "Ret")));
    }));
}

function openForm(type) {
    editingId = type?.id ?? null;
    clearFormErrors();
    document.getElementById("type-dialog-title").textContent = type ? `Ret "${type.name}"` : "Ny billettype";
    document.getElementById("type-name").value = type?.name ?? "";
    document.getElementById("type-price").value = type ? (type.priceOre / 100).toFixed(2) : "";
    document.getElementById("type-from").value = type ? toLocalInput(type.validFrom) : toLocalInput(new Date().toISOString());
    document.getElementById("type-to").value = type?.validTo ? toLocalInput(type.validTo) : "";
    document.getElementById("type-active").checked = type?.active ?? true;
    document.getElementById("type-dialog").showModal();
    document.getElementById("type-name").focus();
}

async function save(event) {
    event.preventDefault();
    clearFormErrors();

    const price = document.getElementById("type-price").value;
    const from = document.getElementById("type-from").value;
    const to = document.getElementById("type-to").value;
    const body = {
        name: document.getElementById("type-name").value.trim(),
        priceOre: price === "" ? null : Math.round(Number(price) * 100), // kr. -> øre
        validFrom: from ? new Date(from).toISOString() : null,
        validTo: to ? new Date(to).toISOString() : null,
        active: document.getElementById("type-active").checked,
    };

    const button = document.getElementById("type-save");
    button.disabled = true;
    try {
        types = editingId
            ? await api(`/api/admin/ticket-types/${editingId}`, { method: "PUT", body })
            : await api(withEvent("/api/admin/ticket-types"), { method: "POST", body });
        renderTypes();
        document.getElementById("type-dialog").close();
    } catch (err) {
        for (const [field, message] of Object.entries(err.fields)) {
            const box = document.querySelector(`[data-error-for="${field}"]`);
            if (box) box.textContent = message;
        }
        if (Object.keys(err.fields).length === 0) {
            const box = document.getElementById("type-error");
            box.textContent = err.message;
            box.hidden = false;
        }
    } finally {
        button.disabled = false;
    }
}

function clearFormErrors() {
    document.querySelectorAll("#type-form .field-error").forEach((e) => (e.textContent = ""));
    document.getElementById("type-error").hidden = true;
}

/** "2026-10-07T22:00:00Z" -> "2026-10-08T00:00" i lokal tid, som <input type="datetime-local"> forventer. */
function toLocalInput(iso) {
    const d = new Date(iso);
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
