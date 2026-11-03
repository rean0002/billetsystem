/*
 * Login-siden. Selve login klares af Spring Security. Scriptet sørger kun for to ting:
 *   1. at hente CSRF-nøglen, som formularen skal sende med
 *   2. at vise en besked, hvis login fejlede, eller man lige er logget ud
 */

document.addEventListener("DOMContentLoaded", async () => {
    // Spring sender os tilbage med ?error eller ?logout i adressen
    const params = new URLSearchParams(location.search);
    document.getElementById("login-error").hidden = !params.has("error");
    document.getElementById("logged-out").hidden = !params.has("logout");

    try {
        const response = await fetch("/api/csrf");
        if (!response.ok) throw new Error("HTTP " + response.status);
        const csrf = await response.json();

        const field = document.getElementById("csrf");
        field.name = csrf.parameterName;
        field.value = csrf.token;
        document.getElementById("login-button").disabled = false;
    } catch (err) {
        console.error(err);
        const error = document.getElementById("login-error");
        error.textContent = "Kan ikke få forbindelse til serveren. Genindlæs siden og prøv igen.";
        error.hidden = false;
    }
});
