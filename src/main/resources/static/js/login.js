/*
 * Login-siden. Selve login klares af Clerk (kodeord, spærring ved gætteri osv.).
 * Scriptet viser Clerks login-formular og sender arrangøren videre til adminsiden bagefter.
 *
 * Adressen kan have ?logout (lige logget ud) eller ?forbidden (logget ind, men uden admin-rolle).
 */

document.addEventListener("DOMContentLoaded", async () => {
    const params = new URLSearchParams(location.search);
    document.getElementById("logged-out").hidden = !params.has("logout");

    let clerk;
    try {
        clerk = await loadClerk();
    } catch (err) {
        const error = document.getElementById("login-error");
        error.textContent = err.message;
        error.hidden = false;
        return;
    }

    if (clerk.user && params.has("forbidden")) {
        document.getElementById("forbidden-user").textContent =
            "Logget ind som " + (clerk.user.primaryEmailAddress?.emailAddress ?? clerk.user.id);
        document.getElementById("sign-out").addEventListener("click", () =>
            clerk.signOut({ redirectUrl: "/admin/login.html?logout" }));
        document.getElementById("forbidden").hidden = false;
        return;
    }

    if (clerk.user) {
        location.href = "/admin/index.html"; // allerede logget ind
        return;
    }

    clerk.mountSignIn(document.getElementById("clerk-sign-in"), { forceRedirectUrl: "/admin/index.html" });
});
