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
  "/sources": SourcesPage
};

const state = {
  adminTab: "claims",
  adminNotice: ""
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

function getAdminUser() {
  try {
    return JSON.parse(localStorage.getItem("ltf_admin_user") || "null");
  } catch {
    return null;
  }
}

function setAdminUser(user) {
  localStorage.setItem("ltf_admin_user", JSON.stringify(user));
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
  const user = getAdminUser();
  return user?.email ? { "X-Demo-User-Email": user.email } : {};
}

function navigate(path) {
  window.history.pushState({}, "", path);
  render();
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

function ProtectedRoute(contentRenderer) {
  const user = getAdminUser();
  if (!user || user.role !== "admin") {
    return `
      <section class="page-head">
        <h1>Admin Dashboard</h1>
        <p>Admin routes require the demo admin account. Role access is checked again on every admin API request.</p>
      </section>
      <form id="admin-login" class="panel" novalidate>
        <div class="form-grid">
          <div class="field">
            <label for="admin_name">Full name</label>
            <input id="admin_name" name="full_name" value="Avery Patel" required>
          </div>
          <div class="field">
            <label for="admin_email">Admin email</label>
            <input id="admin_email" name="email" type="email" value="avery.patel@pleasantvalley.edu" required>
          </div>
        </div>
        <div class="actions">
          <button type="submit">Sign In</button>
        </div>
        <div id="admin-login-result" role="status"></div>
      </form>
    `;
  }
  return contentRenderer(user);
}

async function HomePage() {
  app.innerHTML = `
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
  `;
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
  app.innerHTML = `
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
  `;
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
  app.innerHTML = `
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
  `;
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
  app.innerHTML = `
    <section class="page-head">
      <h1>Browse Items</h1>
      <p>These cards intentionally hide storage location, finder contact, internal item codes, and private verification clues.</p>
    </section>
    <div class="toolbar">
      <input class="search-box" id="item-search" type="search" placeholder="Search category, color, brand, or location" aria-label="Search found items">
      <a class="button secondary" href="/report-found" data-link>Report Found Item</a>
    </div>
    <section id="items-grid" class="grid cards" aria-label="Found item cards"></section>
  `;
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
  const params = new URLSearchParams(window.location.search);
  const itemId = params.get("item") || "";
  app.innerHTML = `
    <section class="page-head">
      <h1>Claim Item</h1>
      <p>Ownership is not granted from the public card. A student must provide a private detail that staff can compare against sealed verification clues.</p>
    </section>
    <section id="claim-item-preview"></section>
    ${ClaimForm(itemId)}
  `;
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
      <p>Signed in as ${escapeHtml(user.full_name || user.fullName || user.email)}. View reports, review claims, archive resolved items, and inspect audit logs.</p>
    </section>
    <div id="admin-dashboard">
      <div class="notice">Loading admin records...</div>
    </div>
  `);
  const login = document.querySelector("#admin-login");
  if (login) {
    login.addEventListener("submit", submitAdminLogin);
    return;
  }
  await loadAdminDashboard();
}

async function submitAdminLogin(event) {
  event.preventDefault();
  const result = document.querySelector("#admin-login-result");
  try {
    const user = await api("/api/auth/signin", {
      method: "POST",
      body: JSON.stringify(formPayload(event.currentTarget))
    });
    if (user.role !== "admin") {
      throw new Error("This account is not an admin.");
    }
    setAdminUser(user);
    render();
  } catch (error) {
    result.innerHTML = `<div class="error">${escapeHtml(error.message)}</div>`;
  }
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
      assigned_to: getAdminUser()?.email || ""
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
  app.innerHTML = `
    <section class="page-head">
      <h1>Sources / Licenses</h1>
      <p>The project uses the existing Spring Boot and MongoDB backend with a custom static frontend. No paid service is required for the demo email workflow.</p>
    </section>
    <ul class="source-list">
      ${source("Spring Boot documentation", "Backend framework, MVC routing, validation, configuration", "https://docs.spring.io/spring-boot/index.html")}
      ${source("MongoDB documentation", "Database and document collection model", "https://www.mongodb.com/docs/manual/")}
      ${source("MDN History API", "Browser routing with history.pushState", "https://developer.mozilla.org/en-US/docs/Web/API/History_API")}
      ${source("WCAG 2.2 Quick Reference", "Accessible labels, contrast, keyboard, and responsive UX checks", "https://www.w3.org/WAI/WCAG22/quickref/")}
      ${source("Project source code", "Custom Java, HTML, CSS, and JavaScript written for this FBLA demo", "/")}
    </ul>
    <section class="panel">
      <h2>License Notes</h2>
      <p>The web UI does not import third-party frontend libraries or paid assets. Seed images referenced by demo records are placeholders unless supplied by the school.</p>
    </section>
  `;
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

function render() {
  header.innerHTML = Navbar();
  footer.innerHTML = Footer();
  const page = routes[window.location.pathname] || BrowsePage;
  page();
  app.focus({ preventScroll: true });
}

document.addEventListener("click", (event) => {
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

window.addEventListener("popstate", render);
render();
