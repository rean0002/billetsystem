/*
 * Borde: overblik til bordplanen. Viser grupper (bestillinger med flere deltagere eller
 * med navne, de vil sidde med), begynder-/singlebordet og dem, der har brug for en bordende.
 * Kun bestillinger, der afventer betaling eller er betalt, tæller med.
 */

adminReady.then(async () => {
    document.getElementById("print").addEventListener("click", () => window.print());
    const event = events.find((e) => e.id === currentEventId);
    if (event) document.querySelector("h1").textContent = "Pladsønsker: " + event.name;

    let data;
    try {
        data = await api(withEvent("/api/admin/seating"));
    } catch (err) {
        showPageError(err.message);
        return;
    }

    const missing = data.groups.flatMap((g) => g.seatWith).filter((w) => w.foundInOrderId == null).length;
    document.getElementById("seating-summary").textContent =
        `${data.seatsTaken} deltagere · ${data.groups.length} grupper · ${data.beginnerTable.length} ved begynderbordet · ` +
        `${data.tableEnd.length} med bordende` + (missing ? ` · ${missing} navne ikke fundet` : "");

    renderGroups(data.groups);
    renderPeople("beginner", data.beginnerTable, "Ingen har valgt begynder-/singlebordet endnu.");
    renderPeople("table-end", data.tableEnd, "Ingen har bedt om en bordende endnu.");
}).catch(console.error);

function renderGroups(groups) {
    const container = document.getElementById("groups");
    if (groups.length === 0) {
        container.replaceChildren(h("p", { className: "empty" }, "Ingen grupper endnu."));
        return;
    }
    container.replaceChildren(...groups.map((g) =>
        h("article", { className: "group" },
            h("div", { className: "group-head" },
                h("strong", {}, `${g.participants.length + g.seatWith.length} personer`),
                h("a", { href: `/admin/bestillinger.html?id=${g.orderId}` }, `Bestilling #${g.orderId}`),
                statusBadge(g.status)),
            h("p", { className: "group-names" }, g.participants.join(", ")),
            g.seatWith.length > 0 && h("div", { className: "group-wishes" },
                h("span", { className: "muted" }, "Vil sidde med: "),
                g.seatWith.map((w, i) => [
                    i > 0 ? ", " : "",
                    w.foundInOrderId
                        ? h("a", { href: `/admin/bestillinger.html?id=${w.foundInOrderId}`, title: `Har billet i bestilling #${w.foundInOrderId}` }, w.name)
                        : h("span", { className: "not-found", title: "Ingen deltager med dette navn. Måske har personen ikke købt billet endnu, eller navnet er stavet anderledes." },
                            w.name + " (ikke fundet)"),
                ])))));
}

function renderPeople(id, people, emptyText) {
    const list = document.getElementById(id);
    if (people.length === 0) {
        list.replaceChildren(h("li", { className: "muted" }, emptyText));
        return;
    }
    list.replaceChildren(...people.map((p) =>
        h("li", {},
            h("span", {}, h("strong", {}, p.fullName), h("small", { className: "muted block" }, "Bestilt af " + p.contactName)),
            h("span", { className: "person-side" },
                h("a", { href: `/admin/bestillinger.html?id=${p.orderId}` }, "#" + p.orderId), statusBadge(p.status)))));
}
