/*
 * Viser, hvordan kunden betaler: beløb, konto/MobilePay, reference og frist.
 * Bruges både på bestillingssidens bekræftelse og som forhåndsvisning på adminsiden "Betaling",
 * så de to altid ser ens ud.
 *
 * payment har samme form som PaymentInstructions i Java:
 *   { amountOre, reference, dueDate, accountHolder, regNumber, accountNumber, mobilepayNumber, instructions }
 */

const paymentKroner = new Intl.NumberFormat("da-DK", { style: "currency", currency: "DKK" });
const paymentDate = new Intl.DateTimeFormat("da-DK", { weekday: "long", day: "numeric", month: "long", year: "numeric" });

function renderPaymentInfo(container, payment) {
    const make = (tag, className, text) => {
        const element = document.createElement(tag);
        if (className) element.className = className;
        if (text != null) element.textContent = text;
        return element;
    };

    /** En linje med en værdi og en "Kopiér"-knap, så kunden ikke skal skrive kontonummeret af. */
    const row = (label, value) => {
        const dt = make("dt", null, label);
        const dd = make("dd");
        dd.append(make("span", "pay-value", value));
        if (navigator.clipboard) {
            const button = make("button", "copy-button", "Kopiér");
            button.type = "button";
            button.setAttribute("aria-label", "Kopiér " + label.toLowerCase());
            button.addEventListener("click", async () => {
                try {
                    await navigator.clipboard.writeText(value);
                    button.textContent = "Kopieret ✓";
                } catch {
                    button.textContent = "Kunne ikke kopiere";
                }
                setTimeout(() => (button.textContent = "Kopiér"), 2000);
            });
            dd.append(button);
        }
        return [dt, dd];
    };

    const box = make("div", "payment-info");
    box.append(make("h3", null, "Sådan betaler du"));

    const amount = make("p", "pay-amount");
    amount.append("Beløb: ", make("strong", null, paymentKroner.format(payment.amountOre / 100)));
    box.append(amount);

    const hasBank = payment.regNumber && payment.accountNumber;
    if (hasBank) {
        const list = make("dl", "pay-list");
        list.append(...row("Reg.nr.", payment.regNumber), ...row("Kontonr.", payment.accountNumber));
        if (payment.accountHolder) list.append(...row("Modtager", payment.accountHolder));
        box.append(make("p", "pay-method", "Bankoverførsel"), list);
    }
    if (payment.mobilepayNumber) {
        const list = make("dl", "pay-list");
        list.append(...row("MobilePay", payment.mobilepayNumber));
        box.append(make("p", "pay-method", hasBank ? "Eller MobilePay" : "MobilePay"), list);
    }

    // Referencen er vigtig: uden den kan arrangørerne ikke se, hvilken bestilling betalingen hører til
    const reference = make("dl", "pay-list pay-reference");
    reference.append(...row("Skriv i beskeden", payment.reference));
    box.append(reference);

    box.append(make("p", "pay-due", "Betal senest " + paymentDate.format(new Date(payment.dueDate + "T12:00:00"))));

    if (payment.instructions) box.append(make("p", "pay-instructions", payment.instructions));

    container.replaceChildren(box);
}
