/*
 * Bestillinger: liste med filter og søgning, og et vindue med detaljer, hvor admin kan
 * markere en bestilling som betalt, annullere den osv.
 *
 * Adressen kan styre siden: ?status=PENDING vælger filteret, ?id=5 åbner bestilling 5.
 */

const STATUS_ACTIONS = {
    PAID: { label: "Markér som betalt", className: "primary" },
    CANCELLED: { label: "Annullér bestilling", className: "danger",
        confirm: "Annullér bestillingen? Billetterne tæller så ikke længere som solgt." },
    REFUNDED: { label: "Markér som refunderet", className: "danger",
        confirm: "Markér bestillingen som refunderet? Husk selv at sende pengene tilbage." },
    PENDING: { label: "Genåbn bestilling", className: "secondary" },
};

const params = new URLSearchParams(location.search);
let currentStatus = params.get("status") ?? "";
let counts = {};

adminReady.then(async () => {
    document.getElementById("search").addEventListener("input", debounce(loadOrders));
    document.getElementById("dialog-close").addEventListener("click", closeDialog);
    document.getElementById("order-dialog").addEventListener("close", () => setUrl({ id: null }));

    await loadCounts();
    await loadOrders();
    if (params.has("id")) openOrder(Number(params.get("id")));
}).catch(console.error);

/** Antal pr. status til fanerne øverst. */
async function loadCounts() {
    const all = await api(withEvent("/api/admin/orders"));
    counts = { "": all.length };
    for (const o of all) counts[o.status] = (counts[o.status] ?? 0) + 1;
    renderTabs();
}

function renderTabs() {
    const tabs = [["", "Alle"], ...Object.entries(STATUS_LABELS)];
    document.getElementById("status-tabs").replaceChildren(...tabs.map(([value, label]) =>
        h("button", {
            type: "button",
            className: "filter-tab",
            "aria-pressed": String(value === currentStatus),
            onClick: () => {
                currentStatus = value;
                setUrl({ status: value || null });
                renderTabs();
                loadOrders();
            },
        }, label, h("span", { className: "count" }, counts[value] ?? 0))));
}

async function loadOrders() {
    const query = new URLSearchParams({ eventId: currentEventId });
    if (currentStatus) query.set("status", currentStatus);
    const search = document.getElementById("search").value.trim();
    if (search) query.set("search", search);

    let orders;
    try {
        orders = await api("/api/admin/orders?" + query);
    } catch (err) {
        showPageError(err.message);
        return;
    }

    document.getElementById("orders-empty").hidden = orders.length > 0;
    document.querySelector("#orders-table tbody").replaceChildren(...orders.map((o) => {
        const row = h("tr", { tabIndex: 0, onClick: () => openOrder(o.id),
                onKeydown: (e) => e.key === "Enter" && openOrder(o.id) },
            h("td", {}, "#" + o.id),
            h("td", { className: "nowrap" }, formatDateTime(o.createdAt)),
            h("td", {}, h("strong", {}, o.contactName), h("small", { className: "muted block" }, o.email)),
            h("td", { className: "num" }, o.ticketCount),
            h("td", { className: "num" }, formatPrice(o.totalOre)),
            h("td", {}, statusBadge(o.status), o.overdue && h("small", { className: "overdue block" }, "Frist overskredet")));
        return row;
    }));
}

// ---------------------------------------------------------------------
// Detaljevinduet
// ---------------------------------------------------------------------

async function openOrder(id) {
    let order;
    try {
        order = await api(`/api/admin/orders/${id}`);
    } catch (err) {
        showPageError(err.message);
        return;
    }
    renderOrder(order);
    setUrl({ id });
    const dialog = document.getElementById("order-dialog");
    if (!dialog.open) dialog.showModal();
}

function closeDialog() {
    document.getElementById("order-dialog").close();
}

function renderOrder(o) {
    document.getElementById("dialog-title").replaceChildren(`Bestilling #${o.id} `, statusBadge(o.status));

    const contact = h("dl", { className: "details" },
        h("dt", {}, "Kontaktperson"), h("dd", {}, o.contactName),
        h("dt", {}, "E-mail"), h("dd", {}, h("a", { href: "mailto:" + o.email }, o.email)),
        h("dt", {}, "Telefon"), h("dd", {}, o.phone ? h("a", { href: "tel:" + o.phone }, o.phone) : "–"),
        h("dt", {}, "Bestilt"), h("dd", {}, formatDateTime(o.createdAt)),
        h("dt", {}, "Betalingsfrist"), h("dd", {}, formatDate(o.paymentDueDate),
            o.overdue && h("span", { className: "overdue" }, " · overskredet")),
        h("dt", {}, "Betalt"), h("dd", {}, formatDateTime(o.paidAt)),
        h("dt", {}, "Bekræftelse"), h("dd", {}, mailStatus(o, "confirmation", o.confirmationSentAt)),
        o.status === "PAID" && [h("dt", {}, "Mail om betaling"), h("dd", {}, mailStatus(o, "payment-received", o.paidMailSentAt))],
        // Referencen, kunden er bedt om at skrive i beskeden ved bankoverførsel
        h("dt", {}, "Reference"), h("dd", {}, "Bestilling " + o.id));

    const note = o.note && h("div", { className: "note" }, h("strong", {}, "Besked fra kunden"), h("p", {}, o.note));
    const seatWith = o.seatWith.length > 0 && h("div", { className: "note" },
        h("strong", {}, "Vil sidde sammen med"), h("p", {}, o.seatWith.join(", ")),
        h("a", { href: "/admin/borde.html" }, "Se på bordsiden"));

    const participants = h("table", { className: "data-table" },
        h("thead", {}, h("tr", {}, h("th", {}, "Deltager"), h("th", {}, "Billettype"), h("th", { className: "num" }, "Pris"))),
        h("tbody", {}, o.participants.map((p) =>
            h("tr", {}, h("td", {}, p.fullName, " ", wishTags(p)), h("td", {}, p.ticketType), h("td", { className: "num" }, formatPrice(p.priceOre))))),
        h("tfoot", {}, h("tr", { className: "total" }, h("td", { colSpan: 2 }, "I alt"), h("td", { className: "num" }, formatPrice(o.totalOre)))));

    const error = h("p", { className: "form-error", role: "alert", hidden: true });
    const actions = h("div", { className: "dialog-actions" }, o.allowedStatuses.map((status) => {
        const action = STATUS_ACTIONS[status];
        return h("button", {
            type: "button",
            className: "button " + action.className,
            onClick: async (e) => {
                if (action.confirm && !confirm(action.confirm)) return;
                e.target.disabled = true;
                try {
                    const updated = await api(`/api/admin/orders/${o.id}/status`, { method: "POST", body: { status } });
                    renderOrder(updated);
                    await Promise.all([loadCounts(), loadOrders()]);
                } catch (err) {
                    error.textContent = err.message;
                    error.hidden = false;
                    e.target.disabled = false;
                }
            },
        }, action.label);
    }));

    const hint = o.status === "PENDING" &&
        h("p", { className: "hint" }, `Tjek at ${formatPrice(o.totalOre)} er modtaget med beskeden "Bestilling ${o.id}", ` +
            "før du markerer bestillingen som betalt." +
            (o.overdue ? " Fristen er overskredet: skriv evt. til kunden, eller annullér bestillingen for at frigive pladserne." : ""));

    document.getElementById("dialog-body").replaceChildren(contact, note || "", seatWith || "", h("h3", {}, "Deltagere"), participants, error, hint || "", actions);
}

/**
 * "Sendt 3. nov. 2026, 16.20" eller "Ikke sendt", og en knap til at sende mailen (igen).
 * type er "confirmation" eller "payment-received".
 */
function mailStatus(o, type, sentAt) {
    const status = h("span", { className: sentAt ? "" : "muted" }, sentAt ? "Sendt " + formatDateTime(sentAt) : "Ikke sendt");
    const button = h("button", {
        type: "button",
        className: "link-button",
        onClick: async () => {
            button.disabled = true;
            button.textContent = "Sender …";
            try {
                renderOrder(await api(`/api/admin/orders/${o.id}/mail?type=${type}`, { method: "POST" }));
            } catch (err) {
                status.textContent = err.message;
                status.className = "overdue";
                button.disabled = false;
                button.textContent = "Prøv igen";
            }
        },
    }, sentAt ? "Send igen" : "Send nu");
    return h("span", { className: "mail-status" }, status, button);
}

/** Opdaterer adressen uden at genindlæse siden, så man kan kopiere et link til fx én bestilling. */
function setUrl(changes) {
    const url = new URL(location.href);
    for (const [key, value] of Object.entries(changes)) {
        if (value == null) url.searchParams.delete(key);
        else url.searchParams.set(key, value);
    }
    history.replaceState(null, "", url);
}
