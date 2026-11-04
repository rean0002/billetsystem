/*
 * Henter Clerk (login) i browseren. Bruges af login-siden og adminsiderne.
 *
 * Den offentlige nøgle hentes fra serveren (GET /api/clerk-config), så den kun skal stå ét sted
 * (miljøvariablen CLERK_PUBLISHABLE_KEY). Selve Clerk-scriptet hentes fra jeres egen Clerk-app,
 * som Clerk anbefaler for sider uden byggeværktøj (CDN-metoden i Clerks JavaScript-quickstart).
 */

let clerkPromise = null;

/** Indlæser Clerk én gang og returnerer window.Clerk, når den er klar. */
function loadClerk() {
    clerkPromise ??= (async () => {
        const response = await fetch("/api/clerk-config");
        const { publishableKey } = response.ok ? await response.json() : {};
        if (!publishableKey) {
            throw new Error("Login er ikke sat op endnu (CLERK_PUBLISHABLE_KEY mangler på serveren).");
        }
        // Nøglen indeholder adressen på Clerk-appen: "pk_test_" + base64("noble-fox-12.clerk.accounts.dev$")
        const frontendApi = atob(publishableKey.split("_")[2]).slice(0, -1);

        await loadScript(`https://${frontendApi}/npm/@clerk/ui@1/dist/ui.browser.js`);
        await loadScript(`https://${frontendApi}/npm/@clerk/clerk-js@6/dist/clerk.browser.js`,
            { "data-clerk-publishable-key": publishableKey });
        await window.Clerk.load({ ui: { ClerkUI: window.__internal_ClerkUICtor } });
        return window.Clerk;
    })();
    return clerkPromise;
}

function loadScript(src, attributes = {}) {
    return new Promise((resolve, reject) => {
        const script = document.createElement("script");
        script.src = src;
        script.crossOrigin = "anonymous";
        for (const [name, value] of Object.entries(attributes)) script.setAttribute(name, value);
        script.onload = resolve;
        script.onerror = () => reject(new Error("Kunne ikke hente login fra Clerk. Tjek internetforbindelsen."));
        document.head.append(script);
    });
}
