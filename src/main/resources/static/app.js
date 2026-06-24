import {
  confirmEmailVerification,
  friendlyAuthError,
  getCurrentUser,
  hasAppwriteConfig,
  isEmailVerified,
  loginWithEmail,
  logoutCurrentSession,
  resendVerificationEmail,
  signInWithGoogle,
  signUpWithEmail
} from "./lib/appwrite.js";

const app = document.querySelector("#app");
const header = document.querySelector("#site-header");
const footer = document.querySelector("#site-footer");

const routes = {
  "/": HomePage,
  "/report-lost": ReportLostPage,
  "/report-found": ReportFoundPage,
  "/browse": BrowsePage,
  "/claim": ClaimPage,
  "/admin": AdminPage,
  "/login": LoginPage,
  "/signup": SignupPage,
  "/verify-email": VerifyEmailPage,
  "/auth/callback": AuthCallbackPage,
  "/sources": SourcesPage
};

const state = {
  adminTab: "claims",
  adminNotice: "",
  authLoading: true,
  authUser: null,
  backendUser: null,
  authNotice: "",
  authError: ""
};

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function field(object, ...keys) {
  for (const key of keys) {
    if (object && object[key] !== undefined && object[key] !== null && object[key] !== "") {
      return object[key];
    }
  }
  return "";
}

async function api(path, options = {}) {
  const headers = {
    "Accept": "application/json",
    ...(options.body ? { "Content-Type": "application/json" } : {}),
    ...(options.headers || {})
  };
  const response = await fetch(path, { ...options, headers });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const message = payload?.message || payload?.error || "Request failed";
    throw new Error(message);
  }
  return payload;
}

function adminHeaders() {
  const user = state.authUser;
  return user?.email ? { "X-Demo-User-Email": user.email } : {};
}

function navigate(path) {
  window.history.pushState({}, "", path);
  render();
}

function currentPathWithSearch() {
  return `${window.location.pathname}${window.location.search}`;
}

function authNextParam() {
  return encodeURIComponent(currentPathWithSearch());
}

function authUserName(user = state.authUser) {
  return user?.name || user?.email || "signed-in user";
}

async function loadAuthUser() {
  state.authLoading = true;
  state.authError = "";

  if (!hasAppwriteConfig()) {
    state.authUser = null;
    state.backendUser = null;
    state.authLoading = false;
    return;
  }

  try {
    state.authUser = await getCurrentUser();
    await syncBackendUser();
  } catch {
    state.authUser = null;
    state.backendUser = null;
  } finally {
    state.authLoading = false;
  }
}

async function syncBackendUser() {
  if (!state.authUser?.email) {
    return;
  }

  try {
    state.backendUser = await api("/api/auth/signin", {
      method: "POST",
      body: JSON.stringify({
        full_name: authUserName(),
        email: state.authUser.email
      })
    });
  } catch {
    // Appwrite auth should still work if the demo sync endpoint is unavailable.
    state.backendUser = null;
  }
}

function AuthRequiredCard() {
  return `
    <section class="page-head auth-head">
      <h1>Sign In Required</h1>
      <p>Students and staff must sign in before opening the lost-and-found app pages. This keeps reports, claims, and dashboard actions tied to an account.</p>
    </section>
    <section class="panel auth-panel">
      <div>
        <h2>Continue to Lost Then Found</h2>
        <p class="hint">Use email/password or Google sign-in. After signup, Appwrite sends an email verification link.</p>
      </div>
      <div class="actions">
        <a class="button" href="/login?next=${authNextParam()}" data-link>Sign In</a>
        <a class="button secondary" href="/signup?next=${authNextParam()}" data-link>Create Account</a>
      </div>
    </section>
  `;
}

function AuthWarningBanner() {
  if (!state.authUser || isEmailVerified(state.authUser)) {
    return "";
  }

  return `
    <section class="verify-banner" role="status" aria-live="polite">
      <div>
        <strong>Email verification needed</strong>
        <span>${escapeHtml(state.authUser.email)} is signed in, but Appwrite has not marked this email as verified yet.</span>
      </div>
      <button class="secondary" data-resend-verification type="button">Resend verification email</button>
    </section>
  `;
}

function PageFrame(content) {
  const notice = state.authNotice;
  const error = state.authError;
  state.authNotice = "";
  state.authError = "";

  return `
    ${AuthWarningBanner()}
    ${notice ? `<div class="success">${escapeHtml(notice)}</div>` : ""}
    ${error ? `<div class="error">${escapeHtml(error)}</div>` : ""}
    ${content}
  `;
}

function ProtectedRoute(contentRenderer) {
  if (state.authLoading) {
    return PageFrame(`<div class="panel loading-panel">Checking your session...</div>`);
  }

  if (!state.authUser) {
    return PageFrame(AuthRequiredCard());
  }

  return PageFrame(contentRenderer(state.authUser));
}

function requireSignedInPage() {
  if (state.authLoading || !state.authUser) {
    app.innerHTML = ProtectedRoute(() => "");
    return false;
  }
  return true;
}

function getNextPath(defaultPath = "/browse") {
  const params = new URLSearchParams(window.location.search);
  const next = params.get("next") || defaultPath;
  return next.startsWith("/") && !next.startsWith("//") ? next : defaultPath;
}

function AppwriteConfigWarning() {
  if (hasAppwriteConfig()) {
    return "";
  }

  return `
    <div class="error">
      Appwrite is missing its public config. Set VITE_APPWRITE_ENDPOINT and VITE_APPWRITE_PROJECT_ID before using authentication.
    </div>
  `;
}

function AuthFormIntro(mode) {
  const isSignup = mode === "signup";
  return `
    <section class="page-head auth-head">
      <h1>${isSignup ? "Create Account" : "Sign In"}</h1>
      <p>${isSignup
        ? "Create a student or staff account with Appwrite. The app signs you in and sends an email verification link immediately after signup."
        : "Sign in with email/password or Google to open the lost-and-found app routes."}</p>
    </section>
  `;
}

async function LoginPage() {
  const params = new URLSearchParams(window.location.search);
  if (state.authUser) {
    app.innerHTML = PageFrame(`
      <section class="page-head auth-head">
        <h1>Already Signed In</h1>
        <p>${escapeHtml(authUserName())} is signed in.</p>
      </section>
      <div class="actions">
        <a class="button" href="${escapeHtml(getNextPath())}" data-link>Continue</a>
        <button class="secondary" data-logout type="button">Logout</button>
      </div>
    `);
    return;
  }

  app.innerHTML = PageFrame(`
    ${AuthFormIntro("login")}
    <section class="panel auth-panel">
      ${AppwriteConfigWarning()}
      ${params.get("oauth") === "failed" ? `<div class="error">Google sign-in did not finish. Try again or use email/password.</div>` : ""}
      <form id="login-form" class="auth-form" novalidate>
        <div class="form-grid">
          ${input("email", "Email", "student@pleasantvalley.edu", true, "email")}
          <div class="field">
            <label for="password">Password</label>
            <input id="password" name="password" type="password" autocomplete="current-password" required>
          </div>
        </div>
        <div class="actions">
          <button type="submit" ${hasAppwriteConfig() ? "" : "disabled"}>Sign In</button>
          <button class="secondary" data-google-login type="button" ${hasAppwriteConfig() ? "" : "disabled"}>Sign in with Google</button>
        </div>
        <div id="login-result" role="status"></div>
      </form>
      <p class="auth-switch">New to Lost Then Found? <a href="/signup?next=${encodeURIComponent(getNextPath())}" data-link>Create an account</a>.</p>
    </section>
  `);

  document.querySelector("#login-form").addEventListener("submit", submitLogin);
  document.querySelector("[data-google-login]").addEventListener("click", startGoogleLogin);
}

async function SignupPage() {
  if (state.authUser) {
    app.innerHTML = PageFrame(`
      <section class="page-head auth-head">
        <h1>Account Active</h1>
        <p>${escapeHtml(authUserName())} is already signed in.</p>
      </section>
      <div class="actions">
        <a class="button" href="${escapeHtml(getNextPath())}" data-link>Continue</a>
      </div>
    `);
    return;
  }

  app.innerHTML = PageFrame(`
    ${AuthFormIntro("signup")}
    <section class="panel auth-panel">
      ${AppwriteConfigWarning()}
      <form id="signup-form" class="auth-form" novalidate>
        <div class="form-grid">
          ${input("name", "Full name", "Riley Chen", true)}
          ${input("email", "Email", "student@pleasantvalley.edu", true, "email")}
          <div class="field">
            <label for="password">Password</label>
            <input id="password" name="password" type="password" autocomplete="new-password" minlength="8" required>
            <span class="hint">Appwrite requires at least 8 characters.</span>
          </div>
          <div class="field">
            <label for="confirm_password">Confirm password</label>
            <input id="confirm_password" name="confirm_password" type="password" autocomplete="new-password" minlength="8" required>
          </div>
        </div>
        <div class="actions">
          <button type="submit" ${hasAppwriteConfig() ? "" : "disabled"}>Create Account</button>
          <button class="secondary" data-google-login type="button" ${hasAppwriteConfig() ? "" : "disabled"}>Sign up with Google</button>
        </div>
        <div id="signup-result" role="status"></div>
      </form>
      <p class="auth-switch">Already have an account? <a href="/login?next=${encodeURIComponent(getNextPath())}" data-link>Sign in</a>.</p>
    </section>
  `);

  document.querySelector("#signup-form").addEventListener("submit", submitSignup);
  document.querySelector("[data-google-login]").addEventListener("click", startGoogleLogin);
}

async function AuthCallbackPage() {
  const next = getNextPath();
  app.innerHTML = PageFrame(`<div class="panel loading-panel">Finishing Google sign-in...</div>`);
  await loadAuthUser();

  if (state.authUser) {
    state.authNotice = "Signed in with Google.";
    navigate(next);
    return;
  }

  app.innerHTML = PageFrame(`
    <section class="page-head auth-head">
      <h1>Google Sign-In Failed</h1>
      <p>Appwrite did not return an active session. Try again from the sign-in page.</p>
    </section>
    <a class="button" href="/login?next=${encodeURIComponent(next)}" data-link>Back to Sign In</a>
  `);
}

async function VerifyEmailPage() {
  const params = new URLSearchParams(window.location.search);
  const userId = params.get("userId");
  const secret = params.get("secret");

  app.innerHTML = PageFrame(`<div class="panel loading-panel">Confirming email verification...</div>`);

  if (!userId || !secret) {
    app.innerHTML = PageFrame(`
      <section class="page-head auth-head">
        <h1>Verification Link Problem</h1>
        <p>The verification link is missing userId or secret. Use the resend button after signing in to request a fresh link.</p>
      </section>
      <a class="button" href="/login" data-link>Sign In</a>
    `);
    return;
  }

  try {
    // The verification email sends userId and secret in the URL. This page
    // confirms them with Appwrite and then refreshes the current account.
    await confirmEmailVerification(userId, secret);
    await loadAuthUser();
    state.authNotice = "Email verified. Your account is ready.";
    app.innerHTML = PageFrame(`
      <section class="page-head auth-head">
        <h1>Email Verified</h1>
        <p>Your Appwrite account email has been confirmed.</p>
      </section>
      <a class="button" href="/browse" data-link>Continue to App</a>
    `);
  } catch (error) {
    app.innerHTML = PageFrame(`
      <section class="page-head auth-head">
        <h1>Verification Failed</h1>
        <p>${escapeHtml(friendlyAuthError(error))}</p>
      </section>
      <div class="actions">
        <a class="button" href="/login" data-link>Sign In</a>
        ${state.authUser ? `<button class="secondary" data-resend-verification type="button">Resend verification email</button>` : ""}
      </div>
    `);
  }
}

async function submitLogin(event) {
  event.preventDefault();
  const result = document.querySelector("#login-result");
  const button = event.submitter;
  const next = getNextPath();
  button.disabled = true;
  result.innerHTML = `<div class="notice">Signing in with Appwrite...</div>`;

  try {
    state.authUser = await loginWithEmail(formPayload(event.currentTarget));
    await syncBackendUser();
    state.authNotice = isEmailVerified(state.authUser)
      ? "Signed in."
      : "Signed in. Please verify your email to remove the warning banner.";
    navigate(next);
  } catch (error) {
    result.innerHTML = `<div class="error">${escapeHtml(friendlyAuthError(error))}</div>`;
  } finally {
    button.disabled = false;
  }
}

async function submitSignup(event) {
  event.preventDefault();
  const result = document.querySelector("#signup-result");
  const button = event.submitter;
  const payload = formPayload(event.currentTarget);
  const next = getNextPath();

  if (payload.password !== payload.confirm_password) {
    result.innerHTML = `<div class="error">Passwords must match.</div>`;
    return;
  }

  button.disabled = true;
  result.innerHTML = `<div class="notice">Creating your Appwrite account...</div>`;

  try {
    state.authUser = await signUpWithEmail(payload);
    await syncBackendUser();
    state.authNotice = "Account created. Appwrite sent a verification link to your email.";
    navigate(next);
  } catch (error) {
    result.innerHTML = `<div class="error">${escapeHtml(friendlyAuthError(error))}</div>`;
  } finally {
    button.disabled = false;
  }
}

function startGoogleLogin() {
  const result = document.querySelector("#login-result, #signup-result");
  if (result) {
    result.innerHTML = `<div class="notice">Redirecting to Google through Appwrite...</div>`;
  }

  try {
    signInWithGoogle(getNextPath());
  } catch (error) {
    if (result) {
      result.innerHTML = `<div class="error">${escapeHtml(friendlyAuthError(error))}</div>`;
    }
  }
}

function Navbar() {
  const links = [
    ["/", "Home"],
    ["/report-lost", "Report Lost"],
    ["/report-found", "Report Found"],
    ["/browse", "Browse"],
    ["/claim", "Claim"],
    ["/admin", "Admin"],
    ["/sources", "Sources"]
  ];
  const current = window.location.pathname;
  return `
    <nav class="site-header" aria-label="Primary">
      <div class="nav-wrap">
        <a class="brand" href="/" data-link>
          <span class="brand-mark" aria-hidden="true">LTF</span>
          <span>Lost Then Found</span>
        </a>
        <div class="nav-links">
          ${links.map(([href, label]) => `
            <a class="nav-link ${current === href ? "active" : ""}" href="${href}" data-link>${label}</a>
          `).join("")}
          ${state.authUser ? `
            <span class="nav-user">${escapeHtml(authUserName())}</span>
            <button class="nav-button secondary" data-logout type="button">Logout</button>
          ` : `
            <a class="nav-link ${current === "/login" ? "active" : ""}" href="/login" data-link>Sign In</a>
          `}
        </div>
      </div>
    </nav>
  `;
}

function Footer() {
  return `
    <div class="footer-wrap">
      <span>PVHS demo platform for FBLA Website Coding & Development.</span>
      <span>Secure claims, public-safe cards, admin audit trail.</span>
    </div>
  `;
}

function StatusBadge(status) {
  const raw = String(status || "FOUND").toUpperCase();
  const label = raw === "APPROVED" ? "FOUND"
    : raw === "CLAIMED" || raw === "VERIFIED" || raw === "APPROVED_CLAIM" ? "VERIFIED"
    : raw === "PENDING_REVIEW" || raw === "SUBMITTED" || raw === "CLAIM_PENDING" ? "CLAIM_PENDING"
    : raw === "RETURNED" || raw === "COMPLETED" || raw === "ARCHIVED" ? "ARCHIVED"
    : raw === "REJECTED" ? "DENIED"
    : raw;
  const tone = label === "FOUND" ? "found"
    : label === "CLAIM_PENDING" ? "pending"
    : label === "VERIFIED" ? "verified"
    : label === "DENIED" ? "danger"
    : "archived";
  return `<span class="status ${tone}">${escapeHtml(label.replaceAll("_", " "))}</span>`;
}

function ItemCard(item, options = {}) {
  const id = field(item, "id");
  const title = field(item, "title", "found_item_title") || "Found item";
  const description = field(item, "description") || "Public details are intentionally limited. Submit a claim with a private detail to prove ownership.";
  const category = field(item, "category") || "uncategorized";
  const color = field(item, "color");
  const brand = field(item, "brand");
  const location = field(item, "location_found", "locationFound");
  const date = field(item, "date_found", "dateFound");
  const status = field(item, "status") || "FOUND";
  return `
    <article class="item-card">
      <div class="item-card-header">
        <h3 class="item-title">${escapeHtml(title)}</h3>
        ${StatusBadge(status)}
      </div>
      <p>${escapeHtml(description)}</p>
      <div class="item-meta">
        <span class="meta-pill">${escapeHtml(category)}</span>
        ${color ? `<span class="meta-pill">${escapeHtml(color)}</span>` : ""}
        ${brand ? `<span class="meta-pill">${escapeHtml(brand)}</span>` : ""}
        ${location ? `<span class="meta-pill">${escapeHtml(location)}</span>` : ""}
        ${date ? `<span class="meta-pill">${escapeHtml(date)}</span>` : ""}
      </div>
      ${options.hideClaim ? "" : `<a class="button secondary" href="/claim?item=${encodeURIComponent(id)}" data-link>Start Claim</a>`}
    </article>
  `;
}

function ClaimForm(itemId = "") {
  return `
    <form id="claim-form" class="panel" novalidate>
      <div class="form-grid">
        <div class="field">
          <label for="found_item_id">Found item ID</label>
          <input id="found_item_id" name="found_item_id" value="${escapeHtml(itemId)}" required>
        </div>
        <div class="field">
          <label for="claimant_name">Your name</label>
          <input id="claimant_name" name="claimant_name" autocomplete="name" required>
        </div>
        <div class="field">
          <label for="claimant_email">School email</label>
          <input id="claimant_email" name="claimant_email" type="email" autocomplete="email" required>
        </div>
        <div class="field">
          <label for="student_id">Student ID</label>
          <input id="student_id" name="student_id" placeholder="PV12345">
        </div>
        <div class="field full">
          <label for="claim_reason">Why do you believe this is yours?</label>
          <textarea id="claim_reason" name="claim_reason" required></textarea>
        </div>
        <div class="field full">
          <label for="identifying_details">Secret ownership detail</label>
          <textarea id="identifying_details" name="identifying_details" required placeholder="Describe a hidden mark, contents, scratch, label, or other detail not visible on the public card."></textarea>
          <span class="hint">This detail is private and visible to admins for verification.</span>
        </div>
        <div class="field full">
          <label for="pickup_availability">Pickup availability</label>
          <input id="pickup_availability" name="pickup_availability" placeholder="After third period or after school">
        </div>
      </div>
      <div class="actions">
        <button type="submit">Submit Claim</button>
      </div>
      <div id="claim-result" role="status"></div>
    </form>
  `;
}

function AdminTable({ columns, rows, empty = "No records yet." }) {
  if (!rows?.length) {
    return `<div class="empty-state"><p>${escapeHtml(empty)}</p></div>`;
  }
  return `
    <div class="table-wrap">
      <table>
        <thead>
          <tr>${columns.map((column) => `<th scope="col">${escapeHtml(column.label)}</th>`).join("")}</tr>
        </thead>
        <tbody>
          ${rows.map((row) => `
            <tr>
              ${columns.map((column) => `<td>${column.render ? column.render(row) : escapeHtml(field(row, column.key))}</td>`).join("")}
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

async function HomePage() {
  app.innerHTML = PageFrame(`
    <section class="hero">
      <div class="hero-copy">
        <h1>Lost item recovery with matching, private claims, and admin verification.</h1>
        <p>Students can report a lost item, review possible matches, submit a secret ownership detail, and let staff approve pickup with an audit trail.</p>
        <div class="actions">
          <a class="button" href="/report-lost" data-link>Report Lost Item</a>
          <a class="button secondary" href="/browse" data-link>Browse Found Items</a>
        </div>
      </div>
      <aside class="workflow-panel" aria-label="Demo workflow">
        ${["Lost report", "Possible matches", "Secret claim", "Admin approval"].map((label, index) => `
          <div class="workflow-step">
            <span class="step-dot">${index + 1}</span>
            <span><strong>${label}</strong><span>${workflowText(index)}</span></span>
          </div>
        `).join("")}
      </aside>
    </section>
    <section class="metric-row" id="home-metrics" aria-label="Platform snapshot">
      ${[1, 2, 3, 4].map(() => `<div class="metric"><strong>-</strong><span>Loading</span></div>`).join("")}
    </section>
    <section class="panel">
      <h2>Seven-minute judge path</h2>
      <p>Use the seeded calculator and AirPods examples, then submit a lost report, claim a match, approve the claim as admin, and show the notification plus audit log.</p>
    </section>
  `);
  try {
    const [items, claims, lostReports] = await Promise.all([
      api("/api/items"),
      api("/api/entities/Claim"),
      api("/api/entities/LostReport")
    ]);
    document.querySelector("#home-metrics").innerHTML = [
      metric(items.length, "Public found items"),
      metric(lostReports.length, "Lost reports"),
      metric(claims.filter((claim) => ["submitted", "pending_review"].includes(String(claim.status || "").toLowerCase())).length, "Pending claims"),
      metric(claims.filter((claim) => String(claim.status || "").toLowerCase() === "approved").length, "Approved claims")
    ].join("");
  } catch {
    document.querySelector("#home-metrics").innerHTML = [
      metric("Demo", "Seed data enabled"),
      metric("4", "Core routes"),
      metric("AA", "Accessibility target"),
      metric("2MB", "Photo limit")
    ].join("");
  }
}

function workflowText(index) {
  return [
    "Student submits category, color, location, date, and keywords.",
    "Backend scores found items using explainable local matching.",
    "Public cards hide sensitive details while claims collect proof.",
    "Admins approve, notify, and audit every important action."
  ][index];
}

function metric(value, label) {
  return `<div class="metric"><strong>${escapeHtml(value)}</strong><span>${escapeHtml(label)}</span></div>`;
}

async function ReportLostPage() {
  if (!requireSignedInPage()) {
    return;
  }

  app.innerHTML = PageFrame(`
    <section class="page-head">
      <h1>Report Lost Item</h1>
      <p>Submit the details a student is likely to know. The backend immediately compares the report against found items by category, color, location, keywords, and date range.</p>
    </section>
    <form id="lost-form" class="panel" novalidate>
      <div class="form-grid">
        ${input("title", "Item name", "Lost graphing calculator", true)}
        ${select("category", "Category", ["electronics", "bags_cases", "school_supplies", "clothing", "personal_items", "food_containers"])}
        ${input("color", "Color", "Silver")}
        ${input("brand", "Brand", "Texas Instruments")}
        ${input("location_lost", "Last seen location", "Gym Entrance", true)}
        ${input("date_lost", "Date lost", "", true, "date")}
        ${input("contact_name", "Contact name", "Riley Chen", true)}
        ${input("contact_email", "Contact email", "riley.chen@pleasantvalley.edu", true, "email")}
        <div class="field full">
          <label for="description">Description and keywords</label>
          <textarea id="description" name="description" required>Silver calculator with my name label under the slide cover.</textarea>
        </div>
        <div class="field full">
          <label for="extra_notes">Extra notes</label>
          <textarea id="extra_notes" name="extra_notes">Lost during the basketball game before halftime.</textarea>
        </div>
      </div>
      <div class="actions">
        <button type="submit">Submit and Match</button>
      </div>
      <div id="lost-result" role="status"></div>
    </form>
  `);
  document.querySelector("#date_lost").valueAsDate = new Date();
  document.querySelector("#lost-form").addEventListener("submit", submitLostReport);
}

async function submitLostReport(event) {
  event.preventDefault();
  const result = document.querySelector("#lost-result");
  const button = event.submitter;
  button.disabled = true;
  result.innerHTML = `<div class="notice">Checking found items for possible matches...</div>`;
  try {
    const payload = formPayload(event.currentTarget);
    const report = await api("/api/entities/LostReport", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    const matches = field(report, "matched_items", "matchedItems") || [];
    result.innerHTML = `
      <div class="success">Lost report saved. ${matches.length ? "Possible matches are listed below." : "No strong matches yet."}</div>
      ${matches.length ? `<h2>Possible Matches</h2><div class="grid cards">${matches.map(MatchCard).join("")}</div>` : ""}
    `;
  } catch (error) {
    result.innerHTML = `<div class="error">${escapeHtml(error.message)}</div>`;
  } finally {
    button.disabled = false;
  }
}

function MatchCard(match) {
  const id = field(match, "found_item_id", "foundItemId");
  const title = field(match, "found_item_title", "foundItemTitle") || id;
  const confidence = field(match, "confidence") || 0;
  const reasons = field(match, "reasons") || [];
  return `
    <article class="match-card">
      <div class="item-card-header">
        <h3 class="item-title">${escapeHtml(title)}</h3>
        <span class="confidence">${escapeHtml(confidence)}% match</span>
      </div>
      <div class="compact-list">
        ${(Array.isArray(reasons) ? reasons : []).map((reason) => `<span class="meta-pill">${escapeHtml(reason)}</span>`).join("")}
      </div>
      <div class="actions">
        <a class="button" href="/claim?item=${encodeURIComponent(id)}" data-link>Claim This Item</a>
      </div>
    </article>
  `;
}

async function ReportFoundPage() {
  if (!requireSignedInPage()) {
    return;
  }

  app.innerHTML = PageFrame(`
    <section class="page-head">
      <h1>Report Found Item</h1>
      <p>Found-item reports create a public-safe card and keep owner-verification clues private for staff review.</p>
    </section>
    <form id="found-form" class="panel" novalidate>
      <div class="form-grid">
        ${input("title", "Item name", "Black AirPods-style Case", true)}
        ${select("category", "Category", ["electronics", "bags_cases", "school_supplies", "clothing", "personal_items", "food_containers"])}
        ${input("color", "Color", "Black")}
        ${input("brand", "Brand", "Apple")}
        ${input("location_found", "Location found", "Gym Bleachers", true)}
        ${input("date_found", "Date found", "", true, "date")}
        ${input("time_found", "Time found", "20:40", false, "time")}
        ${input("finder_name", "Finder name", "Coach Miller")}
        <div class="field full">
          <label for="description">Public description</label>
          <textarea id="description" name="description" required>Black wireless earbud case found after the basketball game.</textarea>
        </div>
        <div class="field full">
          <label for="verification_clue">Private verification clue</label>
          <textarea id="verification_clue" name="verification_clue" required>Small silver initials on the hinge and a scratch on the back corner.</textarea>
          <span class="hint">This clue is stored separately from the public card.</span>
        </div>
        <div class="field full">
          <label for="photo">Photo upload</label>
          <input id="photo" name="photo" type="file" accept="image/jpeg,image/png,image/webp">
          <span class="hint">Allowed: jpg, jpeg, png, webp. Max size: 2MB.</span>
        </div>
      </div>
      <div class="actions">
        <button type="submit">Submit Found Item</button>
      </div>
      <div id="found-result" role="status"></div>
    </form>
  `);
  document.querySelector("#date_found").valueAsDate = new Date();
  document.querySelector("#found-form").addEventListener("submit", submitFoundItem);
}

async function submitFoundItem(event) {
  event.preventDefault();
  const result = document.querySelector("#found-result");
  const button = event.submitter;
  button.disabled = true;
  result.innerHTML = `<div class="notice">Saving found item...</div>`;
  try {
    const form = event.currentTarget;
    const payload = formPayload(form);
    const photoInput = form.querySelector("#photo");
    if (photoInput.files.length) {
      payload.photo_urls = [await uploadPhoto(photoInput.files[0])];
    }
    payload.status = "FOUND";
    payload.record_type = "found";
    payload.private_verification_clues = [payload.verification_clue];
    delete payload.verification_clue;
    const saved = await api("/api/items", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    result.innerHTML = `
      <div class="success">Found item saved with public status ${escapeHtml(saved.status || "FOUND")}.</div>
      <div class="grid cards">${ItemCard(saved, { hideClaim: true })}</div>
    `;
    form.reset();
  } catch (error) {
    result.innerHTML = `<div class="error">${escapeHtml(error.message)}</div>`;
  } finally {
    button.disabled = false;
  }
}

async function uploadPhoto(file) {
  const extension = file.name.split(".").pop().toLowerCase();
  if (!["jpg", "jpeg", "png", "webp"].includes(extension)) {
    throw new Error("Photos must be jpg, jpeg, png, or webp files.");
  }
  if (file.size > 2 * 1024 * 1024) {
    throw new Error("Photos must be 2MB or smaller.");
  }
  const dataUrl = await new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error("Could not read photo file."));
    reader.readAsDataURL(file);
  });
  const response = await api("/api/uploads", {
    method: "POST",
    body: JSON.stringify({
      file_name: file.name,
      content_type: file.type,
      data_url: dataUrl
    })
  });
  return response.file_url;
}

async function BrowsePage() {
  if (!requireSignedInPage()) {
    return;
  }

  app.innerHTML = PageFrame(`
    <section class="page-head">
      <h1>Browse Items</h1>
      <p>These cards intentionally hide storage location, finder contact, internal item codes, and private verification clues.</p>
    </section>
    <div class="toolbar">
      <input class="search-box" id="item-search" type="search" placeholder="Search category, color, brand, or location" aria-label="Search found items">
      <a class="button secondary" href="/report-found" data-link>Report Found Item</a>
    </div>
    <section id="items-grid" class="grid cards" aria-label="Found item cards"></section>
  `);
  try {
    const items = await api("/api/items");
    renderItems(items);
    document.querySelector("#item-search").addEventListener("input", (event) => {
      const query = event.target.value.toLowerCase();
      renderItems(items.filter((item) => JSON.stringify(item).toLowerCase().includes(query)));
    });
  } catch (error) {
    document.querySelector("#items-grid").innerHTML = `<div class="error">${escapeHtml(error.message)}</div>`;
  }
}

function renderItems(items) {
  document.querySelector("#items-grid").innerHTML = items.length
    ? items.map(ItemCard).join("")
    : `<div class="empty-state"><p>No public found items match that search.</p></div>`;
}

async function ClaimPage() {
  if (!requireSignedInPage()) {
    return;
  }

  const params = new URLSearchParams(window.location.search);
  const itemId = params.get("item") || "";
  app.innerHTML = PageFrame(`
    <section class="page-head">
      <h1>Claim Item</h1>
      <p>Ownership is not granted from the public card. A student must provide a private detail that staff can compare against sealed verification clues.</p>
    </section>
    <section id="claim-item-preview"></section>
    ${ClaimForm(itemId)}
  `);
  if (itemId) {
    try {
      const item = await api(`/api/items/${encodeURIComponent(itemId)}`);
      document.querySelector("#claim-item-preview").innerHTML = `<div class="grid cards">${ItemCard(item, { hideClaim: true })}</div>`;
      document.querySelector("#found_item_id").value = field(item, "id");
    } catch (error) {
      document.querySelector("#claim-item-preview").innerHTML = `<div class="notice">Public item preview unavailable. You can still submit the item ID if staff gave it to you.</div>`;
    }
  }
  document.querySelector("#claim-form").addEventListener("submit", submitClaim);
}

async function submitClaim(event) {
  event.preventDefault();
  const result = document.querySelector("#claim-result");
  const button = event.submitter;
  button.disabled = true;
  result.innerHTML = `<div class="notice">Submitting claim for admin review...</div>`;
  try {
    const payload = formPayload(event.currentTarget);
    payload.status = "pending_review";
    payload.risk_score = 10;
    payload.risk_flags = ["student submitted claim"];
    const claim = await api("/api/entities/Claim", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    result.innerHTML = `
      <div class="success">Claim ${escapeHtml(claim.id)} submitted. Status: ${StatusBadge(claim.status)}</div>
      <p class="hint">An admin can now approve or deny the claim from the dashboard.</p>
    `;
    event.currentTarget.reset();
  } catch (error) {
    result.innerHTML = `<div class="error">${escapeHtml(error.message)}</div>`;
  } finally {
    button.disabled = false;
  }
}

async function AdminPage() {
  app.innerHTML = ProtectedRoute((user) => `
    <section class="page-head">
      <h1>Admin Dashboard</h1>
      <p>Signed in as ${escapeHtml(authUserName(user))}. View reports, review claims, archive resolved items, and inspect audit logs.</p>
    </section>
    <div id="admin-dashboard">
      <div class="notice">Loading admin records...</div>
    </div>
  `);
  if (!state.authUser) {
    return;
  }
  await loadAdminDashboard();
}

async function loadAdminDashboard() {
  const target = document.querySelector("#admin-dashboard");
  try {
    const [dashboard, recoveryCenter, patternAlerts] = await Promise.all([
      api("/api/admin/dashboard", { headers: adminHeaders() }),
      api("/api/admin/recovery-center", { headers: adminHeaders() }),
      api("/api/sentinel/alerts", { headers: adminHeaders() })
    ]);
    target.innerHTML = AdminDashboard({
      ...dashboard,
      recovery_center: recoveryCenter,
      pattern_alerts: patternAlerts
    });
    bindAdminActions();
  } catch (error) {
    target.innerHTML = `<div class="error">${escapeHtml(error.message)}</div>`;
  }
}

function AdminDashboard(dashboard) {
  const foundItems = dashboard.found_items || [];
  const lostReports = dashboard.lost_reports || [];
  const claims = dashboard.claims || [];
  const pendingClaims = dashboard.pending_claims || [];
  const notifications = dashboard.notifications || [];
  const auditLogs = dashboard.audit_logs || [];
  const recoveryCenter = dashboard.recovery_center || { summary: {}, cases: [] };
  const patternAlerts = dashboard.pattern_alerts || [];
  const tabs = [
    ["claims", `Pending Claims (${pendingClaims.length})`],
    ["recovery", `Recovery Center (${field(recoveryCenter.summary || {}, "active_cases", "activeCases") || 0})`],
    ["pattern", `Pattern Review (${patternAlerts.length})`],
    ["items", `Found Items (${foundItems.length})`],
    ["lost", `Lost Reports (${lostReports.length})`],
    ["scenarios", "Demo Scenarios"],
    ["notifications", `Notifications (${notifications.length})`],
    ["audit", `Audit Log (${auditLogs.length})`]
  ];
  const data = { foundItems, lostReports, claims, pendingClaims, notifications, auditLogs, recoveryCenter, patternAlerts };
  return `
    ${state.adminNotice ? `<div class="success">${escapeHtml(state.adminNotice)}</div>` : ""}
    <section class="metric-row">
      ${metric(foundItems.length, "Found items")}
      ${metric(lostReports.length, "Lost reports")}
      ${metric(pendingClaims.length, "Pending claims")}
      ${metric(field(recoveryCenter.summary || {}, "open_missions", "openMissions") || 0, "Open missions")}
    </section>
    <div class="admin-tabs" role="tablist" aria-label="Admin views">
      ${tabs.map(([tab, label]) => `<button class="${state.adminTab === tab ? "active" : ""}" data-admin-tab="${tab}" type="button">${escapeHtml(label)}</button>`).join("")}
    </div>
    <section id="admin-table-region">
      ${AdminTabContent(data)}
    </section>
  `;
}

function AdminTabContent(data) {
  if (state.adminTab === "recovery") {
    const summary = data.recoveryCenter.summary || {};
    const cases = data.recoveryCenter.cases || [];
    return `
      <section class="admin-tools" aria-label="Recovery Center summary">
        ${metric(field(summary, "active_cases", "activeCases") || 0, "Active cases")}
        ${metric(field(summary, "open_missions", "openMissions") || 0, "Open missions")}
        ${metric(field(summary, "claims_awaiting_review", "claimsAwaitingReview") || 0, "Claims awaiting review")}
        ${metric(field(summary, "pickup_ready_cases", "pickupReadyCases") || 0, "Pickup-ready cases")}
      </section>
      ${AdminTable({
        rows: cases,
        empty: "No recovery cases yet. Create a demo scenario or submit a Lost Report.",
        columns: [
          { label: "Case", render: (item) => `<strong>${escapeHtml(field(item.recovery_case, "case_code", "caseCode") || field(item.recovery_case, "id"))}</strong><br><span class="hint">${escapeHtml(field(item.recovery_case, "id"))}</span>` },
          { label: "Lost Report", render: (item) => `<strong>${escapeHtml(field(item.lost_report, "title") || "Missing linked report")}</strong><br><span class="hint">${escapeHtml(field(item.lost_report, "id") || field(item.recovery_case, "lost_report_id", "lostReportId"))}</span>` },
          { label: "Status", render: (item) => StatusBadge(field(item.recovery_case, "status")) },
          { label: "Next Action", render: (item) => escapeHtml(field(item, "next_action", "nextAction")) },
          { label: "Missions", render: (item) => escapeHtml((field(item, "missions") || []).length) },
          { label: "Controls", render: (item) => `
            <div class="actions table-actions">
              <button class="secondary" data-refresh-case="${escapeHtml(field(item.recovery_case, "lost_report_id", "lostReportId"))}" type="button">Refresh</button>
              <button class="secondary" data-assign-case="${escapeHtml(field(item.recovery_case, "id"))}" type="button">Assign</button>
              <button class="secondary" data-create-mission="${escapeHtml(field(item.recovery_case, "id"))}" type="button">Mission</button>
            </div>
          ` }
        ]
      })}
    `;
  }
  if (state.adminTab === "pattern") {
    return `
      <section class="admin-toolbar">
        <button data-recompute-pattern type="button">Recompute Pattern Review</button>
      </section>
      ${AdminTable({
        rows: data.patternAlerts,
        empty: "No Pattern Review alerts yet. Run the Gym Electronics Pattern demo scenario, then recompute.",
        columns: [
          { label: "Alert", render: (alert) => `<strong>${escapeHtml(alert.title || "Pattern Review")}</strong><br><span class="hint">${escapeHtml(alert.id)}</span>` },
          { label: "Pattern", render: (alert) => `${escapeHtml(alert.category)}<br><span class="hint">${escapeHtml(alert.campus_zone_id || alert.campusZoneId || "unknown zone")}</span>` },
          { label: "Counts", render: (alert) => `${escapeHtml(alert.observed_count || alert.observedCount || 0)} recent / ${escapeHtml(alert.baseline_count || alert.baselineCount || 0)} baseline` },
          { label: "Sources", render: (alert) => escapeHtml((alert.source_lost_report_ids || alert.sourceLostReportIds || []).join(", ")) },
          { label: "Status", render: (alert) => StatusBadge(alert.status) },
          { label: "Actions", render: (alert) => `
            <div class="actions table-actions">
              <button class="secondary" data-alert-ack="${escapeHtml(alert.id)}" type="button">Ack</button>
              <button class="secondary" data-alert-mission="${escapeHtml(alert.id)}" type="button">Mission</button>
              <button class="secondary" data-alert-resolve="${escapeHtml(alert.id)}" type="button">Resolve</button>
              <button class="danger" data-alert-dismiss="${escapeHtml(alert.id)}" type="button">Dismiss</button>
            </div>
          ` }
        ]
      })}
    `;
  }
  if (state.adminTab === "items") {
    return AdminTable({
      rows: data.foundItems,
      columns: [
        { label: "Item", render: (item) => `<strong>${escapeHtml(item.title)}</strong><br><span class="hint">${escapeHtml(item.id)}</span>` },
        { label: "Status", render: (item) => StatusBadge(item.status) },
        { label: "Location", render: (item) => escapeHtml(field(item, "location_found", "locationFound")) },
        { label: "Storage", render: (item) => escapeHtml(field(item, "storage_location", "storageLocation") || "Not set") },
        { label: "Action", render: (item) => `<button class="secondary" data-archive-item="${escapeHtml(item.id)}" type="button">Archive</button>` }
      ]
    });
  }
  if (state.adminTab === "lost") {
    return AdminTable({
      rows: data.lostReports,
      columns: [
        { label: "Report", render: (report) => `<strong>${escapeHtml(report.title)}</strong><br><span class="hint">${escapeHtml(report.id)}</span>` },
        { label: "Student", render: (report) => escapeHtml(field(report, "contact_email", "contactEmail")) },
        { label: "Location", render: (report) => escapeHtml(field(report, "location_lost", "locationLost")) },
        { label: "Status", render: (report) => StatusBadge(report.status) },
        { label: "Matches", render: (report) => escapeHtml((field(report, "matched_items", "matchedItems") || []).length) }
      ]
    });
  }
  if (state.adminTab === "audit") {
    return AdminTable({
      rows: data.auditLogs,
      columns: [
        { label: "Action", key: "action" },
        { label: "Entity", render: (log) => `${escapeHtml(log.entity_type || log.entityType)}<br><span class="hint">${escapeHtml(log.entity_id || log.entityId)}</span>` },
        { label: "By", render: (log) => escapeHtml(log.performed_by || log.performedBy) },
        { label: "Details", key: "details" },
        { label: "Date", render: (log) => escapeHtml(log.created_date || log.createdDate) }
      ]
    });
  }
  if (state.adminTab === "notifications") {
    return AdminTable({
      rows: data.notifications,
      empty: "No notification previews yet.",
      columns: [
        { label: "Title", render: (note) => `<strong>${escapeHtml(note.title)}</strong><br><span class="hint">${escapeHtml(note.type)}</span>` },
        { label: "Student", render: (note) => escapeHtml(note.user_email || note.userEmail) },
        { label: "Message", key: "message" },
        { label: "Item", render: (note) => escapeHtml(note.related_item_id || note.relatedItemId) },
        { label: "Date", render: (note) => escapeHtml(note.created_date || note.createdDate) }
      ]
    });
  }
  if (state.adminTab === "scenarios") {
    return `
      <section class="scenario-grid" aria-label="Demo scenarios">
        ${ScenarioButton("airpods_gym", "AirPods at Gym", "Creates a Lost Report, Recovery Case, gym missions, Found Item, and pending Claim.")}
        ${ScenarioButton("gym_electronics_pattern", "Gym Electronics Pattern", "Creates enough real Lost Reports for a valid Pattern Review alert.")}
        ${ScenarioButton("library_water_bottle", "Library Water Bottle", "Creates a low-priority Lost Report, Recovery Case, and mission.")}
      </section>
      <form id="custom-scenario-form" class="panel scenario-form" novalidate>
        <h3>Custom Scenario</h3>
        <div class="form-grid">
          ${input("title", "Lost item title", "Lost debate folder", true)}
          ${select("category", "Category", ["electronics", "bags_cases", "school_supplies", "clothing", "personal_items", "food_containers"])}
          ${input("location_lost", "Location", "Main Office", true)}
          ${input("contact_email", "Contact email", "demo.student@pleasantvalley.edu", true, "email")}
        </div>
        <div class="actions">
          <button data-custom-scenario type="submit">Create Custom Scenario</button>
        </div>
      </form>
    `;
  }
  return AdminTable({
    rows: data.pendingClaims,
    empty: "No pending claims need review.",
    columns: [
      { label: "Claim", render: (claim) => `<strong>${escapeHtml(claim.claimant_name || claim.claimantName)}</strong><br><span class="hint">${escapeHtml(claim.id)}</span>` },
      { label: "Item", render: (claim) => `${escapeHtml(claim.found_item_title || claim.foundItemTitle || claim.found_item_id || claim.foundItemId)}<br><span class="hint">${escapeHtml(claim.found_item_id || claim.foundItemId)}</span>` },
      { label: "Private Detail", render: (claim) => escapeHtml(claim.identifying_details || claim.identifyingDetails) },
      { label: "Status", render: (claim) => StatusBadge(claim.status) },
      { label: "Decision", render: (claim) => `
        <div class="actions">
          <button data-approve-claim="${escapeHtml(claim.id)}" type="button">Approve</button>
          <button class="danger" data-deny-claim="${escapeHtml(claim.id)}" type="button">Deny</button>
        </div>
      ` }
    ]
  });
}

function ScenarioButton(id, title, description) {
  return `
    <article class="scenario-card">
      <h3>${escapeHtml(title)}</h3>
      <p>${escapeHtml(description)}</p>
      <button data-demo-scenario="${escapeHtml(id)}" type="button">Create</button>
    </article>
  `;
}

function bindAdminActions() {
  document.querySelectorAll("[data-admin-tab]").forEach((button) => {
    button.addEventListener("click", async () => {
      state.adminTab = button.dataset.adminTab;
      await loadAdminDashboard();
    });
  });
  document.querySelectorAll("[data-approve-claim]").forEach((button) => {
    button.addEventListener("click", () => adminDecision(`/api/admin/claims/${button.dataset.approveClaim}/approve`, "Claim approved."));
  });
  document.querySelectorAll("[data-deny-claim]").forEach((button) => {
    button.addEventListener("click", () => adminDecision(`/api/admin/claims/${button.dataset.denyClaim}/deny`, "Claim denied."));
  });
  document.querySelectorAll("[data-archive-item]").forEach((button) => {
    button.addEventListener("click", () => adminDecision(`/api/admin/items/${button.dataset.archiveItem}/archive`, "Item archived."));
  });
  document.querySelectorAll("[data-refresh-case]").forEach((button) => {
    button.addEventListener("click", () => adminDecision(`/api/recovery-cases/lost-reports/${button.dataset.refreshCase}/refresh`, "Recovery plan refreshed."));
  });
  document.querySelectorAll("[data-assign-case]").forEach((button) => {
    button.addEventListener("click", () => adminDecision(`/api/recovery-cases/${button.dataset.assignCase}/assign`, "Recovery case assigned.", {
      assigned_to: state.authUser?.email || ""
    }));
  });
  document.querySelectorAll("[data-create-mission]").forEach((button) => {
    button.addEventListener("click", () => adminDecision(`/api/recovery-cases/${button.dataset.createMission}/missions`, "Recovery mission created.", {
      title: "Admin follow-up mission",
      recommended_action: "Check the next likely location and update the mission status.",
      priority: "medium",
      status: "open"
    }));
  });
  document.querySelectorAll("[data-demo-scenario]").forEach((button) => {
    button.addEventListener("click", () => adminDecision(`/api/admin/demo-scenarios/${button.dataset.demoScenario}`, "Demo scenario created."));
  });
  document.querySelectorAll("[data-alert-ack]").forEach((button) => {
    button.addEventListener("click", () => adminDecision(`/api/sentinel/alerts/${button.dataset.alertAck}/acknowledge`, "Pattern Review alert acknowledged."));
  });
  document.querySelectorAll("[data-alert-mission]").forEach((button) => {
    button.addEventListener("click", () => adminDecision(`/api/sentinel/alerts/${button.dataset.alertMission}/mission`, "Pattern Review mission created."));
  });
  document.querySelectorAll("[data-alert-resolve]").forEach((button) => {
    button.addEventListener("click", () => adminDecision(`/api/sentinel/alerts/${button.dataset.alertResolve}/resolve`, "Pattern Review alert resolved.", {
      resolution_notes: "Resolved during demo review."
    }));
  });
  document.querySelectorAll("[data-alert-dismiss]").forEach((button) => {
    button.addEventListener("click", () => adminDecision(`/api/sentinel/alerts/${button.dataset.alertDismiss}/dismiss`, "Pattern Review alert dismissed.", {
      resolution_notes: "Dismissed after admin review."
    }));
  });
  const recompute = document.querySelector("[data-recompute-pattern]");
  if (recompute) {
    recompute.addEventListener("click", () => adminDecision("/api/sentinel/recompute", "Pattern Review recomputed."));
  }
  const customScenario = document.querySelector("#custom-scenario-form");
  if (customScenario) {
    customScenario.addEventListener("submit", (event) => {
      event.preventDefault();
      adminDecision("/api/admin/demo-scenarios/custom", "Custom demo scenario created.", formPayload(event.currentTarget));
    });
  }
}

async function adminDecision(path, message, body = {}) {
  const target = document.querySelector("#admin-dashboard");
  try {
    const payload = await api(path, {
      method: "POST",
      headers: adminHeaders(),
      body: JSON.stringify({ admin_notes: message, ...body })
    });
    state.adminNotice = payload?.message ? `${message} ${payload.message}` : message;
    await loadAdminDashboard();
  } catch (error) {
    target.insertAdjacentHTML("afterbegin", `<div class="error">${escapeHtml(error.message)}</div>`);
  }
}

async function SourcesPage() {
  app.innerHTML = PageFrame(`
    <section class="page-head">
      <h1>Sources / Licenses</h1>
      <p>The project uses the existing Spring Boot and MongoDB backend, the custom static frontend, and Appwrite Web SDK for browser authentication.</p>
    </section>
    <ul class="source-list">
      ${source("Appwrite Web SDK and Auth documentation", "Email/password auth, Google OAuth sessions, and email verification", "https://appwrite.io/docs/products/auth/email-password")}
      ${source("Spring Boot documentation", "Backend framework, MVC routing, validation, configuration", "https://docs.spring.io/spring-boot/index.html")}
      ${source("MongoDB documentation", "Database and document collection model", "https://www.mongodb.com/docs/manual/")}
      ${source("MDN History API", "Browser routing with history.pushState", "https://developer.mozilla.org/en-US/docs/Web/API/History_API")}
      ${source("WCAG 2.2 Quick Reference", "Accessible labels, contrast, keyboard, and responsive UX checks", "https://www.w3.org/WAI/WCAG22/quickref/")}
      ${source("Project source code", "Custom Java, HTML, CSS, and JavaScript written for this FBLA demo", "/")}
    </ul>
    <section class="panel">
      <h2>License Notes</h2>
      <p>The auth UI imports the Appwrite Web SDK from a pinned CDN version. No paid assets are included, and seed images referenced by demo records are placeholders unless supplied by the school.</p>
    </section>
  `);
}

function source(title, description, href) {
  return `<li><strong>${escapeHtml(title)}</strong><span>${escapeHtml(description)}</span><br><a href="${escapeHtml(href)}">${escapeHtml(href)}</a></li>`;
}

function input(name, label, placeholder = "", required = false, type = "text") {
  return `
    <div class="field">
      <label for="${name}">${label}</label>
      <input id="${name}" name="${name}" type="${type}" placeholder="${escapeHtml(placeholder)}" ${required ? "required" : ""}>
    </div>
  `;
}

function select(name, label, options) {
  return `
    <div class="field">
      <label for="${name}">${label}</label>
      <select id="${name}" name="${name}" required>
        ${options.map((option) => `<option value="${escapeHtml(option)}">${escapeHtml(option.replaceAll("_", " "))}</option>`).join("")}
      </select>
    </div>
  `;
}

function formPayload(form) {
  const data = new FormData(form);
  const payload = {};
  data.forEach((value, key) => {
    if (value instanceof File) {
      return;
    }
    payload[key] = String(value).trim();
  });
  return payload;
}

async function render() {
  header.innerHTML = Navbar();
  footer.innerHTML = Footer();
  const page = routes[window.location.pathname] || BrowsePage;
  await page();
  app.focus({ preventScroll: true });
}

document.addEventListener("click", (event) => {
  const logoutButton = event.target.closest("[data-logout]");
  if (logoutButton) {
    event.preventDefault();
    handleLogout(logoutButton);
    return;
  }

  const resendButton = event.target.closest("[data-resend-verification]");
  if (resendButton) {
    event.preventDefault();
    handleResendVerification(resendButton);
    return;
  }

  const link = event.target.closest("[data-link]");
  if (!link) {
    return;
  }
  const url = new URL(link.href, window.location.origin);
  if (url.origin !== window.location.origin) {
    return;
  }
  event.preventDefault();
  navigate(url.pathname + url.search);
});

async function handleLogout(button) {
  button.disabled = true;
  try {
    await logoutCurrentSession();
    state.authUser = null;
    state.backendUser = null;
    state.authNotice = "Logged out.";
    navigate("/");
  } catch (error) {
    state.authError = friendlyAuthError(error);
    await render();
  } finally {
    button.disabled = false;
  }
}

async function handleResendVerification(button) {
  button.disabled = true;
  try {
    await resendVerificationEmail();
    state.authNotice = "Verification email sent. Check your inbox for the Appwrite link.";
    await render();
  } catch (error) {
    state.authError = friendlyAuthError(error);
    await render();
  } finally {
    button.disabled = false;
  }
}

window.addEventListener("popstate", () => {
  render();
});

async function start() {
  app.innerHTML = `<div class="panel loading-panel">Checking your session...</div>`;
  await loadAuthUser();
  await render();
}

start();
