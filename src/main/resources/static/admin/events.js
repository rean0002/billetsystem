/*
 * Events: se alle events med salg, opret nye og ret eksisterende.
 * "Vis" vælger eventet, så overblik, bestillinger, deltagere og billettyper viser det.
 */

let editingEventId = null; // null = nyt event

adminReady.then(() => {
    document.getElementById("new-event").addEventListener("click", () => openEventForm(null));
    document.getElementById("event-cancel").addEventListener("click", () => document.getElementById("event-dialog").close());
    document.getElementById("event-form").addEventListener("submit", saveEvent);
    renderEvents();
}).catch((err) => showPageError(err.message));

function eventStatus(e) {
    const today = new Date().toISOString().slice(0, 10);
    if (e.endDate < today) return { label: "Overstået", className: "badge-cancelled" };
    if (e.startDate <= today) return { label: "I gang", className: "badge-paid" };
    return { label: "Kommende", className: "badge-pending" };
}

function renderEvents() {
    document.getElementById("events-empty").hidden = events.length > 0;
    document.querySelector("#events-table tbody").replaceChildren(...events.map((e) => {
        const status = eventStatus(e);
        const sold = e.ticketsPaid + e.ticketsPending;
        return h("tr", {},
            h("td", {}, h("strong", {}, e.name),
                e.id === currentEventId && h("small", { className: "muted block" }, "Vises nu")),
            h("td", { className: "nowrap" }, formatDate(e.startDate),
                e.endDate !== e.startDate && h("small", { className: "muted block" }, "til " + formatDate(e.endDate))),
            h("td", {}, h("span", { className: "badge " + status.className }, status.label)),
            h("td", { className: "num" }, e.maxParticipants ? `${sold} / ${e.maxParticipants}` : sold,
                h("small", { className: "muted block" }, `${e.ticketsPaid} betalt · ${e.ticketsPending} afventer` +
                    (e.maxParticipants && sold >= e.maxParticipants ? " · udsolgt" : ""))),
            h("td", { className: "num" }, formatPrice(e.revenuePaidOre, true)),
            h("td", { className: "num nowrap" },
                h("button", { type: "button", className: "link-button", onClick: () => showEvent(e.id, "/admin/index.html") }, "Vis"),
                " ",
                h("button", { type: "button", className: "link-button", onClick: () => openEventForm(e) }, "Ret")));
    }));
}

/** Vælger eventet og går til en side, fx overblikket eller billettyperne. */
function showEvent(id, page) {
    rememberEvent(id);
    location.href = page;
}

function openEventForm(e) {
    editingEventId = e?.id ?? null;
    clearEventErrors();
    document.getElementById("event-dialog-title").textContent = e ? `Ret "${e.name}"` : "Nyt event";
    document.getElementById("event-name-input").value = e?.name ?? "";
    document.getElementById("event-start").value = e?.startDate ?? "";
    document.getElementById("event-end").value = e?.endDate ?? "";
    document.getElementById("event-max").value = e?.maxParticipants ?? "";
    document.getElementById("event-dialog").showModal();
    document.getElementById("event-name-input").focus();
}

async function saveEvent(event) {
    event.preventDefault();
    clearEventErrors();

    const value = (id) => document.getElementById(id).value.trim();
    const body = {
        name: value("event-name-input"),
        startDate: value("event-start") || null,
        endDate: value("event-end") || null,
        maxParticipants: value("event-max") ? Number(value("event-max")) : null,
    };

    const button = document.getElementById("event-save");
    button.disabled = true;
    try {
        const isNew = editingEventId == null;
        const before = new Set(events.map((e) => e.id));
        events = isNew
            ? await api("/api/admin/events", { method: "POST", body })
            : await api(`/api/admin/events/${editingEventId}`, { method: "PUT", body });
        document.getElementById("event-dialog").close();

        if (isNew) {
            // Et nyt event har ingen billettyper endnu, så vi går direkte videre til dem
            const created = events.find((e) => !before.has(e.id));
            if (created && confirm(`"${created.name}" er oprettet. Vil du oprette billettyper til eventet nu?`)) {
                showEvent(created.id, "/admin/billettyper.html");
                return;
            }
        }
        renderEvents();
        renderEventPicker();
    } catch (err) {
        for (const [field, message] of Object.entries(err.fields)) {
            const box = document.querySelector(`#event-form [data-error-for="${field}"]`);
            if (box) box.textContent = message;
        }
        if (Object.keys(err.fields).length === 0) {
            const box = document.getElementById("event-error");
            box.textContent = err.message;
            box.hidden = false;
        }
    } finally {
        button.disabled = false;
    }
}

function clearEventErrors() {
    document.querySelectorAll("#event-form .field-error").forEach((e) => (e.textContent = ""));
    document.getElementById("event-error").hidden = true;
}
