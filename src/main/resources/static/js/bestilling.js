/*
 * Bestillingssiden. Taler med to endpoints i PublicController:
 *   GET  /api/ticket-types/current  -> de billettyper, der kan købes nu
 *   POST /api/orders                -> opretter bestillingen
 *
 * Prisen, der vises her, er kun til orientering. Serveren beregner altid
 * den rigtige pris selv, så ingen kan ændre den i browseren.
 */

const MAX_PARTICIPANTS = 20; // samme grænse som @Size(max = 20) i OrderRequest

let events = [];        // events med billetter til salg, hver med sine billettyper
let selectedEvent = null;
let ticketTypes = [];   // billettyperne for det valgte event
let nextRowId = 0;      // giver hver deltagerrække et unikt id til <label for="...">

const el = (id) => document.getElementById(id);

// Priser gemmes i øre (29500 = 295,00 kr.), så vi dividerer med 100 når de vises
const kroner = new Intl.NumberFormat("da-DK", { style: "currency", currency: "DKK" });
const formatPrice = (ore) => kroner.format(ore / 100);

const dateFormat = new Intl.DateTimeFormat("da-DK", { day: "numeric", month: "long", year: "numeric" });

document.addEventListener("DOMContentLoaded", init);

async function init() {
    let allTypes;
    try {
        const response = await fetch("/api/ticket-types/current");
        if (!response.ok) throw new Error("HTTP " + response.status);
        allTypes = await response.json();
    } catch (err) {
        console.error(err);
        showUnavailable("Vi kan ikke hente billetterne lige nu. Prøv igen om lidt.");
        return;
    }

    el("loading").hidden = true;
    events = groupByEvent(allTypes);

    const available = events.filter((e) => e.seatsLeft !== 0);
    if (events.length === 0) {
        showUnavailable();
        return;
    }
    if (available.length === 0) {
        showUnavailable(events.length === 1
            ? events[0].name + " er desværre udsolgt."
            : "Alle events er desværre udsolgt.");
        return;
    }

    // Flere events til salg samtidig: lad kunden vælge. Ellers vælges det ene automatisk.
    if (events.length > 1) renderEventChoices();
    selectEvent(available[0].id);

    addParticipant();
    el("add-participant").addEventListener("click", () => {
        addParticipant();
        // Sæt markøren i det nye navnefelt, så man kan skrive med det samme
        el("participants").lastElementChild.querySelector(".participant-name").focus();
    });
    el("order-form").addEventListener("submit", submitOrder);
    el("order-form").hidden = false;
}

function showUnavailable(text) {
    el("loading").hidden = true;
    if (text) el("unavailable-text").textContent = text;
    el("unavailable").hidden = false;
}

/** Samler billettyperne under deres event, sorteret efter eventets dato. */
function groupByEvent(types) {
    const byId = new Map();
    for (const t of types) {
        if (!byId.has(t.eventId)) {
            byId.set(t.eventId, { id: t.eventId, name: t.eventName, startDate: t.eventStartDate,
                endDate: t.eventEndDate, seatsLeft: t.eventSeatsLeft, publicInfo: t.eventPublicInfo, types: [] });
        }
        byId.get(t.eventId).types.push(t);
    }
    return [...byId.values()].sort((a, b) => a.startDate.localeCompare(b.startDate));
}

function eventDates(event) {
    const start = dateFormat.format(new Date(event.startDate));
    return event.endDate === event.startDate ? start : start + " – " + dateFormat.format(new Date(event.endDate));
}

function renderEventChoices() {
    el("event-choices").replaceChildren(...events.map((event) => {
        const input = document.createElement("input");
        input.type = "radio";
        input.name = "event";
        input.value = event.id;
        input.disabled = event.seatsLeft === 0;
        input.addEventListener("change", () => selectEvent(event.id));

        const text = document.createElement("span");
        const name = document.createElement("strong");
        name.textContent = event.name;
        const info = document.createElement("small");
        info.textContent = eventDates(event) + (event.seatsLeft === 0 ? " · Udsolgt" : "");
        text.append(name, info);

        const label = document.createElement("label");
        label.className = "event-choice";
        label.append(input, text);
        return label;
    }));
    el("event-section").hidden = false;
}

/** Skifter event: viser eventets priser og giver deltagerne eventets billettyper at vælge imellem. */
function selectEvent(id) {
    selectedEvent = events.find((e) => e.id === id);
    ticketTypes = selectedEvent.types;

    const radio = document.querySelector(`input[name="event"][value="${id}"]`);
    if (radio) radio.checked = true;

    el("event-name").textContent = selectedEvent.name;
    el("event-dates").textContent = eventDates(selectedEvent);
    document.title = "Bestil billetter: " + selectedEvent.name;

    // Vis kun "pladser tilbage", når der er få, så det ikke virker presset hele tiden
    const seats = el("seats-left");
    seats.hidden = selectedEvent.seatsLeft == null || selectedEvent.seatsLeft > 20;
    seats.textContent = `Kun ${selectedEvent.seatsLeft} ${selectedEvent.seatsLeft === 1 ? "plads" : "pladser"} tilbage`;

    // Eventets praktiske info, fx gæstebilletter og åbningstider
    el("event-info").hidden = !selectedEvent.publicInfo;
    el("event-info-text").textContent = selectedEvent.publicInfo ?? "";

    renderPriceList();
    for (const select of document.querySelectorAll(".participant-type")) {
        fillTypeOptions(select);
    }
    // Er der flere deltagere end ledige pladser, fjernes de nederste
    const rows = el("participants");
    while (rows.children.length > maxParticipants()) rows.lastElementChild.remove();
    if (rows.children.length > 0) updateParticipants();
}

/** Højst 20 pr. bestilling, og aldrig flere end der er ledige pladser. */
function maxParticipants() {
    return Math.min(MAX_PARTICIPANTS, selectedEvent?.seatsLeft ?? MAX_PARTICIPANTS);
}

function renderPriceList() {
    const list = el("price-list");
    list.replaceChildren();
    for (const type of ticketTypes) {
        const li = document.createElement("li");

        const name = document.createElement("span");
        name.className = "tag-name";
        name.textContent = type.name;
        if (type.validTo) {
            const until = document.createElement("small");
            until.textContent = "Til og med " + dateFormat.format(new Date(type.validTo));
            name.append(until);
        }

        const price = document.createElement("strong");
        price.textContent = formatPrice(type.priceOre);

        li.append(name, price);
        list.append(li);
    }
}

// ---------------------------------------------------------------------
// Deltagere
// ---------------------------------------------------------------------

function addParticipant() {
    const row = el("participant-template").content.firstElementChild.cloneNode(true);
    const id = nextRowId++;

    const nameInput = row.querySelector(".participant-name");
    const typeSelect = row.querySelector(".participant-type");
    const [nameLabel, typeLabel] = row.querySelectorAll(".field label");

    // Kobl label og felt sammen, så man kan klikke på teksten, og skærmlæsere læser den op
    nameInput.id = "participant-name-" + id;
    typeSelect.id = "participant-type-" + id;
    nameLabel.htmlFor = nameInput.id;
    typeLabel.htmlFor = typeSelect.id;

    fillTypeOptions(typeSelect);

    typeSelect.addEventListener("change", updateTotal);
    row.querySelector(".remove-participant").addEventListener("click", () => {
        row.remove();
        updateParticipants();
    });

    el("participants").append(row);
    updateParticipants();
}

function fillTypeOptions(select) {
    const previous = Number(select.value);
    select.replaceChildren(...ticketTypes.map((type) => {
        const option = document.createElement("option");
        option.value = type.id;
        option.textContent = type.name + " – " + formatPrice(type.priceOre);
        option.selected = type.id === previous;
        return option;
    }));
}

/** Opdaterer knapper og total, når der er tilføjet eller fjernet en deltager. */
function updateParticipants() {
    const rows = el("participants").children;
    // Man kan ikke fjerne den sidste deltager, og man kan ikke tilføje flere end 20
    for (const row of rows) {
        row.querySelector(".remove-participant").hidden = rows.length === 1;
    }
    el("add-participant").disabled = rows.length >= maxParticipants();
    updateTotal();
}

function updateTotal() {
    let total = 0;
    for (const select of document.querySelectorAll(".participant-type")) {
        const type = ticketTypes.find((t) => t.id === Number(select.value));
        if (type) total += type.priceOre;
    }
    el("total").textContent = formatPrice(total);
}

// ---------------------------------------------------------------------
// Bestilling
// ---------------------------------------------------------------------

async function submitOrder(event) {
    event.preventDefault(); // stop browseren i selv at sende formularen og genindlæse siden
    clearErrors();

    const submit = el("submit");
    submit.disabled = true; // undgå dobbeltbestilling ved dobbeltklik
    submit.textContent = "Sender …";

    try {
        const response = await fetch("/api/orders", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(buildRequest()),
        });
        const body = await response.json().catch(() => ({}));

        if (response.status === 201) {
            showConfirmation(body);
        } else {
            showErrors(body);
        }
    } catch (err) {
        console.error(err);
        showFormError("Vi kunne ikke få forbindelse til serveren. Tjek din internetforbindelse og prøv igen.");
    } finally {
        submit.disabled = false;
        submit.textContent = "Bestil billetter";
    }
}

/** Samler formularen til det JSON, som OrderRequest forventer. */
function buildRequest() {
    const value = (id) => el(id).value.trim();
    const participants = [...el("participants").children].map((row) => ({
        fullName: row.querySelector(".participant-name").value.trim(),
        ticketTypeId: Number(row.querySelector(".participant-type").value),
        beginnerTable: row.querySelector(".participant-beginner").checked,
        needsTableEnd: row.querySelector(".participant-table-end").checked,
    }));

    return {
        contactName: value("contactName"),
        email: value("email"),
        phone: value("phone") || null, // tomme valgfrie felter sendes som null
        note: value("note") || null,
        seatWith: value("seatWith") || null,
        participants,
    };
}

function showConfirmation(order) {
    el("confirm-id").textContent = order.id;
    el("confirm-total").textContent = formatPrice(order.totalOre);
    const names = el("confirm-names");
    for (const name of order.participantNames) {
        const li = document.createElement("li");
        li.textContent = name;
        names.append(li);
    }

    // Har arrangørerne udfyldt betalingsoplysninger, vises konto, reference og frist
    if (order.payment) {
        renderPaymentInfo(el("confirm-payment"), order.payment);
        el("confirm-total").parentElement.hidden = true; // beløbet står allerede i betalingsinfo
    }
    else el("confirm-no-payment").hidden = false;

    el("order-form").hidden = true;
    el("seats-left").hidden = true;
    const confirmation = el("confirmation");
    confirmation.hidden = false;
    confirmation.focus();
    window.scrollTo({ top: 0, behavior: "smooth" });
}

// ---------------------------------------------------------------------
// Fejl fra serveren, fx {"fejl": "Ugyldige data", "felter": {"email": "E-mail mangler"}}
// ---------------------------------------------------------------------

function showErrors(body) {
    const fields = body.felter || {};

    for (const [field, message] of Object.entries(fields)) {
        const input = findInput(field);
        if (input) setFieldError(input, message);
    }

    // Fejl, der ikke hører til et bestemt felt (fx "billettypen kan ikke købes lige nu")
    const unplaced = Object.keys(fields).filter((f) => !findInput(f));
    if (unplaced.length > 0 || Object.keys(fields).length === 0) {
        const extra = unplaced.map((f) => fields[f]);
        showFormError([body.fejl || "Noget gik galt. Prøv igen.", ...extra].join(" "));
    }

    // Serveren sender felterne i tilfældig rækkefølge, så vi finder det øverste felt på siden
    document.querySelector("#order-form [aria-invalid]")?.focus();
}

/** Oversætter et feltnavn fra serveren til det rigtige inputfelt på siden. */
function findInput(field) {
    // Deltagerfelter hedder fx "participants[1].fullName"
    const match = field.match(/^participants\[(\d+)]\.(fullName|ticketTypeId)$/);
    if (match) {
        const row = el("participants").children[Number(match[1])];
        if (!row) return null;
        return row.querySelector(match[2] === "fullName" ? ".participant-name" : ".participant-type");
    }
    return document.querySelector(`#order-form [name="${field}"]`);
}

function setFieldError(input, message) {
    input.setAttribute("aria-invalid", "true");
    const error = input.closest(".field").querySelector(".field-error");
    error.textContent = message;
    error.id ||= input.id + "-error";
    input.setAttribute("aria-describedby", error.id);
}

function showFormError(message) {
    const box = el("form-error");
    box.textContent = message;
    box.hidden = false;
}

function clearErrors() {
    for (const input of document.querySelectorAll("[aria-invalid]")) {
        input.removeAttribute("aria-invalid");
        input.removeAttribute("aria-describedby");
    }
    for (const error of document.querySelectorAll(".field-error")) {
        error.textContent = "";
    }
    el("form-error").hidden = true;
}
