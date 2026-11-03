/*
 * Betaling: arrangørerne indtaster kontonummer, MobilePay, frist og en besked.
 * Til højre vises en forhåndsvisning med samme kode som kundens bekræftelse (js/betalingsinfo.js).
 */

const FIELDS = ["accountHolder", "regNumber", "accountNumber", "mobilepayNumber", "paymentDays", "instructions"];
const field = (name) => document.getElementById(name);

adminReady.then(async () => {
    let settings;
    try {
        settings = await api("/api/admin/payment-settings");
    } catch (err) {
        showPageError(err.message);
        return;
    }
    for (const name of FIELDS) field(name).value = settings[name] ?? "";

    const form = document.getElementById("payment-form");
    form.addEventListener("input", () => {
        document.getElementById("payment-saved").hidden = true;
        renderPreview();
    });
    form.addEventListener("submit", save);
    renderPreview();
}).catch(console.error);

function formValues() {
    const values = Object.fromEntries(FIELDS.map((name) => [name, field(name).value.trim()]));
    values.paymentDays = values.paymentDays === "" ? 0 : Number(values.paymentDays);
    return values;
}

function renderPreview() {
    const v = formValues();
    const preview = document.getElementById("preview");
    const hasBank = v.regNumber && v.accountNumber;
    if (!hasBank && !v.mobilepayNumber) {
        preview.replaceChildren(h("p", { className: "notice-warn card" },
            "Udfyld reg.nr. og kontonummer (eller MobilePay). Indtil da ser kunderne ingen betalingsinfo, " +
            "kun at bestillingen er modtaget."));
        return;
    }
    const due = new Date();
    due.setDate(due.getDate() + (v.paymentDays || 0));
    renderPaymentInfo(preview, {
        ...v,
        amountOre: 59000,
        reference: "Bestilling 42",
        dueDate: due.toISOString().slice(0, 10),
    });
}

async function save(event) {
    event.preventDefault();
    document.querySelectorAll("#payment-form .field-error").forEach((e) => (e.textContent = ""));
    document.getElementById("payment-error").hidden = true;

    // Tomme felter sendes som null, så de ryddes i databasen
    const body = Object.fromEntries(Object.entries(formValues()).map(([k, v]) => [k, v === "" ? null : v]));

    const button = document.getElementById("payment-save");
    button.disabled = true;
    try {
        await api("/api/admin/payment-settings", { method: "PUT", body });
        document.getElementById("payment-saved").hidden = false;
    } catch (err) {
        for (const [name, message] of Object.entries(err.fields)) {
            const box = document.querySelector(`#payment-form [data-error-for="${name}"]`);
            if (box) box.textContent = message;
        }
        if (Object.keys(err.fields).length === 0) {
            const box = document.getElementById("payment-error");
            box.textContent = err.message;
            box.hidden = false;
        }
    } finally {
        button.disabled = false;
    }
}
