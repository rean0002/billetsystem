/*
 * Bestillingssiden. Taler med to endpoints i PublicController:
 *   GET  /api/ticket-types/current  -> de billettyper, der kan købes nu
 *   POST /api/orders                -> opretter bestillingen
 *
 * Prisen, der vises her, er kun til orientering. Serveren beregner altid
 * den rigtige pris selv, så ingen kan ændre den i browseren.
 */

const MAX_PARTICIPANTS = 20; // samme grænse som @Size(max = 20) i OrderRequest

let ticketTypes = [];   // hentes fra serveren, når siden åbner
let nextRowId = 0;      // giver hver deltagerrække et unikt id til <label for="...">

const el = (id) => document.getElementById(id);

// Priser gemmes i øre (29500 = 295,00 kr.), så vi dividerer med 100 når de vises
const kroner = new Intl.NumberFormat("da-DK", { style: "currency", currency: "DKK" });
const formatPrice = (ore) => kroner.format(ore / 100);

const dateFormat = new Intl.DateTimeFormat("da-DK", { day: "numeric", month: "long", year: "numeric" });

document.addEventListener("DOMContentLoaded", init);

async function init() {
    try {
        const response = await fetch("/api/ticket-types/current");
        if (!response.ok) throw new Error("HTTP " + response.status);
        ticketTypes = await response.json();
    } catch (err) {
        console.error(err);
        showUnavailable("Vi kan ikke hente billetterne lige nu. Prøv igen om lidt.");
        return;
    }

    el("loading").hidden = true;

    if (ticketTypes.length === 0) {
        showUnavailable();
        return;
    }

    el("event-name").textContent = ticketTypes[0].eventName;
    document.title = "Bestil billetter: " + ticketTypes[0].eventName;
    renderPriceList();

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

function renderPriceList() {
    const list = el("price-list");
    for (const type of ticketTypes) {
        const li = document.createElement("li");

        const name = document.createElement("span");
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
    const [nameLabel, typeLabel] = row.querySelectorAll("label");

    // Kobl label og felt sammen, så man kan klikke på teksten, og skærmlæsere læser den op
    nameInput.id = "participant-name-" + id;
    typeSelect.id = "participant-type-" + id;
    nameLabel.htmlFor = nameInput.id;
    typeLabel.htmlFor = typeSelect.id;

    for (const type of ticketTypes) {
        const option = document.createElement("option");
        option.value = type.id;
        option.textContent = type.name + " – " + formatPrice(type.priceOre);
        typeSelect.append(option);
    }

    typeSelect.addEventListener("change", updateTotal);
    row.querySelector(".remove-participant").addEventListener("click", () => {
        row.remove();
        updateParticipants();
    });

    el("participants").append(row);
    updateParticipants();
}

/** Opdaterer knapper og total, når der er tilføjet eller fjernet en deltager. */
function updateParticipants() {
    const rows = el("participants").children;
    // Man kan ikke fjerne den sidste deltager, og man kan ikke tilføje flere end 20
    for (const row of rows) {
        row.querySelector(".remove-participant").hidden = rows.length === 1;
    }
    el("add-participant").disabled = rows.length >= MAX_PARTICIPANTS;
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
    }));

    return {
        contactName: value("contactName"),
        email: value("email"),
        phone: value("phone") || null, // tomme valgfrie felter sendes som null
        note: value("note") || null,
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

    el("order-form").hidden = true;
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
