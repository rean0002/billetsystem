/*
 * Deltagerliste: én linje pr. billet, sorteret efter navn. Kan søges i, filtreres på billettype,
 * hentes som CSV og printes som tjekind-liste til indgangen (med en "Mødt"-kolonne til flueben).
 */

adminReady.then(async () => {
    // Eventets navn i overskriften, så det også står på den printede liste
    const event = events.find((e) => e.id === currentEventId);
    if (event) document.querySelector("h1").textContent = "Deltagerliste: " + event.name;

    const types = await api(withEvent("/api/admin/ticket-types"));
    document.getElementById("ticket-type").append(...types.map((t) => h("option", { value: t.id }, t.name)));

    document.getElementById("search").addEventListener("input", debounce(loadParticipants));
    document.getElementById("ticket-type").addEventListener("change", loadParticipants);
    document.getElementById("include-cancelled").addEventListener("change", loadParticipants);
    document.getElementById("print").addEventListener("click", () => window.print());

    await loadParticipants();
}).catch((err) => showPageError(err.message));

async function loadParticipants() {
    const query = new URLSearchParams({ eventId: currentEventId });
    const search = document.getElementById("search").value.trim();
    const type = document.getElementById("ticket-type").value;
    if (search) query.set("search", search);
    if (type) query.set("ticketTypeId", type);
    if (document.getElementById("include-cancelled").checked) query.set("includeCancelled", "true");

    let participants;
    try {
        participants = await api("/api/admin/participants?" + query);
    } catch (err) {
        showPageError(err.message);
        return;
    }

    const paid = participants.filter((p) => p.orderStatus === "PAID").length;
    const pending = participants.filter((p) => p.orderStatus === "PENDING").length;
    document.getElementById("participant-summary").textContent =
        `${participants.length} ${participants.length === 1 ? "deltager" : "deltagere"} · ${paid} betalt · ${pending} afventer betaling`;

    document.getElementById("participants-empty").hidden = participants.length > 0;
    document.querySelector("#participants-table tbody").replaceChildren(...participants.map((p) =>
        h("tr", {},
            h("td", { className: "print-only check-col" }, h("span", { className: "checkbox-box" })),
            h("td", {}, h("strong", {}, p.fullName), " ", wishTags(p)),
            h("td", {}, p.ticketType),
            h("td", {}, statusBadge(p.orderStatus)),
            h("td", {}, h("a", { href: `/admin/bestillinger.html?id=${p.orderId}` }, "#" + p.orderId),
                h("small", { className: "muted block" }, p.contactName)),
            h("td", { className: "no-print" }, h("a", { href: "mailto:" + p.email }, p.email),
                p.phone && h("small", { className: "muted block" }, p.phone)))));
}
