/*
 * Overblik: nøgletal, graf over salg pr. dag, salg pr. billettype og nyeste bestillinger.
 * Alle tal kommer fra GET /api/admin/overview (StatisticsService).
 */

adminReady.then(async () => {
    if (currentEventId == null) {
        showPageError("Der er ingen events endnu. Opret det første under Events.");
        return;
    }
    let data;
    try {
        data = await api(withEvent("/api/admin/overview"));
    } catch (err) {
        showPageError(err.message);
        return;
    }
    renderHeader(data.event);
    renderPendingNotice(data);
    renderTiles(data);
    renderDailyChart(data.daily);
    renderTicketTypes(data.ticketTypes);
    renderLatestOrders(data.latestOrders);
}).catch(console.error);

function renderHeader(event) {
    document.getElementById("event-name").textContent = event.name;
    const days = event.daysUntil;
    const when = days > 1 ? `om ${days} dage` : days === 1 ? "i morgen" : days === 0 ? "i dag" : "overstået";
    document.getElementById("event-dates").textContent =
        `${formatDate(event.startDate)} – ${formatDate(event.endDate)} · ${when}`;
}

function renderPendingNotice({ ordersByStatus, overdueOrders, money }) {
    const count = ordersByStatus.PENDING;
    if (count === 0) return;
    const notice = document.getElementById("pending-notice");
    const overdue = overdueOrders > 0 ? `, heraf ${overdueOrders} med overskredet frist` : "";
    notice.textContent = `${count} ${count === 1 ? "bestilling afventer" : "bestillinger afventer"} betaling ` +
        `(${formatPrice(money.pendingOre, true)}${overdue}). Når pengene er modtaget, så markér dem som betalt →`;
    notice.hidden = false;
}

function renderTiles({ event, money, tickets, recent }) {
    const sold = tickets.paid + tickets.pending;
    const capacity = event.maxParticipants
        ? `${sold} af ${event.maxParticipants} pladser (${Math.round((sold / event.maxParticipants) * 100)} %)`
        : `${tickets.paid} betalt · ${tickets.pending} afventer`;

    const tiles = [
        { label: "Omsætning, betalt", value: formatPrice(money.paidOre, true),
          detail: money.refundedOre ? `${formatPrice(money.refundedOre, true)} refunderet` : "Kun betalte bestillinger" },
        { label: "Afventer betaling", value: formatPrice(money.pendingOre, true),
          detail: `Gns. bestilling ${formatPrice(money.averageOrderOre, true)}` },
        { label: "Billetter solgt", value: sold, detail: capacity },
        { label: "Seneste 7 dage", value: recent.ticketsLast7Days,
          detail: `billetter · ${recent.ordersLast7Days} bestillinger · ${recent.ordersToday} i dag` },
    ];

    document.getElementById("tiles").replaceChildren(...tiles.map((t) =>
        h("div", { className: "tile" },
            h("p", { className: "tile-label" }, t.label),
            h("p", { className: "tile-value" }, t.value),
            h("p", { className: "tile-detail" }, t.detail))));
}

/**
 * Søjlediagram bygget af almindelige HTML-elementer. Hver søjle er en dag; den grønne del er
 * betalte billetter, den gule del afventer. Hold musen over en dag for at se tallene.
 */
function renderDailyChart(days) {
    const chart = document.getElementById("daily-chart");
    const max = Math.max(1, ...days.map((d) => d.paid + d.pending));
    const top = niceMax(max);

    // Vandrette hjælpelinjer med tal (0, halvdelen og toppen)
    const grid = h("div", { className: "chart-grid", "aria-hidden": "true" },
        [top, top / 2, 0].map((v) => h("div", { className: "grid-line" }, h("span", {}, v))));

    const tooltip = h("div", { className: "chart-tooltip", role: "status", hidden: true });
    const dayLabel = new Intl.DateTimeFormat("da-DK", { day: "numeric", month: "short" });

    const columns = days.map((d, i) => {
        const total = d.paid + d.pending;
        const col = h("div", { className: "chart-col", tabIndex: 0,
            "aria-label": `${dayLabel.format(new Date(d.date))}: ${d.paid} betalt, ${d.pending} afventer` },
            h("div", { className: "bar" },
                d.pending > 0 && h("div", { className: "seg pending", style: `height:${(d.pending / top) * 100}%` }),
                d.paid > 0 && h("div", { className: "seg paid", style: `height:${(d.paid / top) * 100}%` })),
            // Datoer under hver 5. søjle og den sidste (i dag), så de ikke står oven i hinanden
            h("span", { className: "x-label" }, (i % 5 === 4 || i === days.length - 1) ? dayLabel.format(new Date(d.date)) : ""));

        const show = () => {
            tooltip.replaceChildren(
                h("strong", {}, dayLabel.format(new Date(d.date))),
                h("span", {}, h("i", { className: "swatch paid" }), `Betalt: ${d.paid}`),
                h("span", {}, h("i", { className: "swatch pending" }), `Afventer: ${d.pending}`),
                h("span", { className: "muted" }, `I alt: ${total}`));
            tooltip.hidden = false;
            const box = col.getBoundingClientRect();
            const parent = chart.getBoundingClientRect();
            const left = Math.min(Math.max(box.left - parent.left + box.width / 2, 70), parent.width - 70);
            tooltip.style.left = left + "px";
        };
        col.addEventListener("mouseenter", show);
        col.addEventListener("focus", show);
        col.addEventListener("mouseleave", () => (tooltip.hidden = true));
        col.addEventListener("blur", () => (tooltip.hidden = true));
        return col;
    });

    chart.replaceChildren(grid, h("div", { className: "chart-cols" }, columns), tooltip);

    // Samme tal som tabel, for dem der hellere vil læse tallene (eller bruger skærmlæser)
    document.querySelector("#daily-table tbody").replaceChildren(...days.slice().reverse().map((d) =>
        h("tr", {}, h("td", {}, formatDate(d.date)), h("td", { className: "num" }, d.paid), h("td", { className: "num" }, d.pending))));
}

/** Runder op til et pænt tal til toppen af y-aksen, fx 7 -> 8 og 23 -> 25. */
function niceMax(value) {
    if (value <= 4) return 4;
    const magnitude = 10 ** Math.floor(Math.log10(value));
    const step = [1, 2, 2.5, 5, 10].find((s) => s * magnitude >= value);
    return step * magnitude;
}

function renderTicketTypes(types) {
    const rows = types.map((t) =>
        h("tr", {},
            h("td", {}, t.name, h("small", { className: "muted block" },
                formatPrice(t.priceOre) + (t.availableNow ? " · til salg nu" : " · ikke til salg nu"))),
            h("td", { className: "num" }, t.paid),
            h("td", { className: "num" }, t.pending),
            h("td", { className: "num" }, formatPrice(t.revenuePaidOre, true))));
    const total = types.reduce((sum, t) => ({ paid: sum.paid + t.paid, pending: sum.pending + t.pending, rev: sum.rev + t.revenuePaidOre }),
        { paid: 0, pending: 0, rev: 0 });
    rows.push(h("tr", { className: "total" },
        h("td", {}, "I alt"), h("td", { className: "num" }, total.paid), h("td", { className: "num" }, total.pending),
        h("td", { className: "num" }, formatPrice(total.rev, true))));
    document.querySelector("#type-table tbody").replaceChildren(...rows);
}

function renderLatestOrders(orders) {
    const list = document.getElementById("latest-orders");
    if (orders.length === 0) {
        list.replaceChildren(h("li", { className: "muted" }, "Ingen bestillinger endnu."));
        return;
    }
    list.replaceChildren(...orders.map((o) =>
        h("li", {},
            h("a", { href: `/admin/bestillinger.html?id=${o.id}` },
                h("span", { className: "feed-main" },
                    h("strong", {}, o.contactName),
                    h("span", { className: "muted" }, `${o.ticketCount} ${o.ticketCount === 1 ? "billet" : "billetter"} · ${formatDateTime(o.createdAt)}`)),
                h("span", { className: "feed-side" }, formatPrice(o.totalOre, true), statusBadge(o.status))))));
}
