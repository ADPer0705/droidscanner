(() => {
  const REPO = "ADPer0705/droidscanner";

  // ---------------------------------------------------------------------------
  // Theme persistence (syncs cleanly with dark/light toggle)
  // ---------------------------------------------------------------------------
  const root = document.documentElement;
  const savedTheme = localStorage.getItem("droidscanner_theme");
  if (savedTheme === "light" || savedTheme === "dark") {
    root.setAttribute("data-theme", savedTheme);
  }
  const themeBtn = document.getElementById("theme-toggle");
  if (themeBtn) {
    themeBtn.addEventListener("click", () => {
      const next = root.getAttribute("data-theme") === "light" ? "dark" : "light";
      root.setAttribute("data-theme", next);
      localStorage.setItem("droidscanner_theme", next);
    });
  }

  // ---------------------------------------------------------------------------
  // Interactive Security Audit Simulator
  // ---------------------------------------------------------------------------
  const simBody = document.getElementById("sim-body");
  const tabButtons = Array.from(document.querySelectorAll(".sim-tab"));

  const state = {
    scenario: "torch",
    torchView: "reasons",
    torchTrusted: false,
    postureFixed: {
      private_dns: false,
      lock_otp: false,
      user_ca: false,
      usb_adb: false
    }
  };

  function gaugeSvg(score, colorVar) {
    const r = 28;
    const c = 2 * Math.PI * r;
    const clamped = Math.max(0, Math.min(100, score));
    const offset = c - (clamped / 100) * c;
    return `
      <div class="score-gauge">
        <svg viewBox="0 0 70 70" aria-hidden="true">
          <circle cx="35" cy="35" r="${r}" fill="none" stroke="var(--border-strong)" stroke-width="6"></circle>
          <circle cx="35" cy="35" r="${r}" fill="none" stroke="var(${colorVar})" stroke-width="6"
            stroke-linecap="round" stroke-dasharray="${c.toFixed(1)}" stroke-dashoffset="${offset.toFixed(1)}"></circle>
        </svg>
        <div class="score-gauge-num">
          <strong>${clamped}</strong>
          <span>/100</span>
        </div>
      </div>`;
  }

  function renderTorch() {
    const score = state.torchTrusted ? 0 : 94;
    const colorVar = state.torchTrusted ? "--low" : "--critical";
    const subContent =
      state.torchView === "reasons"
        ? `
          <div class="sim-list">
            <div class="sim-row">
              <div><strong>Banking Trojan Pattern</strong><p>Accessibility Service enabled + Screen Overlay active simultaneously.</p></div>
              <span class="sim-pts critical">+22</span>
            </div>
            <div class="sim-row">
              <div><strong>Context Mismatch (Utility App)</strong><p>A flashlight utility has no functional reason to read SMS, Contacts, or Call Logs.</p></div>
              <span class="sim-pts critical">+18</span>
            </div>
            <div class="sim-row">
              <div><strong>Sideloaded via Chrome · Dropper Risk</strong><p>Installed outside stores while requesting Install Unknown Apps.</p></div>
              <span class="sim-pts high">+12</span>
            </div>
          </div>`
        : state.torchView === "dex"
        ? `
          <div class="sim-list">
            <div class="sim-row">
              <div><strong>Hidden DEX Payload Found</strong><p>Disguised bytecode <code>assets/logo.dat</code> matched DEX magic bytes.</p></div>
              <span class="sim-pts critical">ALERT</span>
            </div>
            <div class="sim-row">
              <div><strong>Sensitive DEX APIs Referenced</strong><p><code>DexClassLoader</code>, <code>SmsManager.sendTextMessage</code>, <code>performGlobalAction</code></p></div>
              <span class="sim-pts high">3 APIs</span>
            </div>
            <div class="sim-row">
              <div><strong>Embedded Trackers (6 SDKs)</strong><p>AppsFlyer, Meta Audience Network, Cuebiq (Location Broker), AppLovin…</p></div>
              <span class="sim-pts high">6 SDKs</span>
            </div>
          </div>`
        : `
          <div class="sim-list">
            <div class="sim-row"><div><strong>Accessibility + Special Access</strong><p>Accessibility enabled (+20), Overlay (+12), Install Apps (+3)</p></div><span class="sim-pts critical">+35</span></div>
            <div class="sim-row"><div><strong>SMS + Contacts + Location</strong><p>Sensitive dangerous grants across 4 categories</p></div><span class="sim-pts high">+31</span></div>
            <div class="sim-row"><div><strong>Combo Rules + Bounded AI (+4)</strong><p>Deterministic rules own the Critical verdict; AI stays secondary.</p></div><span class="sim-pts critical">+28</span></div>
          </div>`;

    simBody.innerHTML = `
      <div class="sim-app-card">
        <div class="sim-app-meta">
          <h4>Torch Pro</h4>
          <p class="sim-app-pkg">com.torch.pro · v1.4 · targetSdk 28</p>
          <div class="sim-chip-row">
            <span class="sim-pill ${state.torchTrusted ? "low" : "critical"}">${state.torchTrusted ? "REVIEWED & TRUSTED" : "CRITICAL RISK"}</span>
            <span class="sim-pill high">Sideloaded via Chrome</span>
          </div>
        </div>
        ${gaugeSvg(score, colorVar)}
      </div>
      <div class="sim-subtabs">
        <button class="sim-subtab ${state.torchView === "reasons" ? "is-active" : ""}" data-sub="reasons">Why Flagged</button>
        <button class="sim-subtab ${state.torchView === "dex" ? "is-active" : ""}" data-sub="dex">DEX &amp; Trackers</button>
        <button class="sim-subtab ${state.torchView === "score" ? "is-active" : ""}" data-sub="score">Breakdown</button>
      </div>
      ${subContent}
      <div class="sim-footer-bar">
        <span>${state.torchTrusted ? "Alerts quieted — auto-revokes if perms or cert change." : "Device Admin active? Guided 2-step removal built in."}</span>
        <button class="sim-action-btn ${state.torchTrusted ? "is-fixed" : ""}" id="sim-trust-btn">
          ${state.torchTrusted ? "✓ Trusted (Revoke)" : "Simulate Trust"}
        </button>
      </div>`;

    simBody.querySelectorAll(".sim-subtab").forEach((btn) => {
      btn.addEventListener("click", () => {
        state.torchView = btn.dataset.sub;
        renderScenario();
      });
    });
    const trustBtn = document.getElementById("sim-trust-btn");
    if (trustBtn) {
      trustBtn.addEventListener("click", () => {
        state.torchTrusted = !state.torchTrusted;
        renderScenario();
      });
    }
  }

  function renderImpersonator() {
    simBody.innerHTML = `
      <div class="sim-app-card">
        <div class="sim-app-meta">
          <h4>Gооgle Plаy Services</h4>
          <p class="sim-app-pkg">com.android.update.helper · v1.0 · targetSdk 22</p>
          <div class="sim-chip-row">
            <span class="sim-pill critical">IMPERSONATION</span>
            <span class="sim-pill critical">DEBUG CERT</span>
            <span class="sim-pill high">Sideloaded APK</span>
          </div>
        </div>
        ${gaugeSvg(88, "--critical")}
      </div>
      <div class="sim-list">
        <div class="sim-row">
          <div><strong>Unicode Homoglyph Spoofing Detected</strong><p>Uses Cyrillic <code>о</code> &amp; <code>а</code> to mimic official <code>com.google.android.gms</code>.</p></div>
          <span class="sim-pts critical">+24</span>
        </div>
        <div class="sim-row">
          <div><strong>Debug Certificate + Repackaged APK</strong><p>Signed by <code>CN=Android Debug</code> with <code>FLAG_DEBUGGABLE=true</code>.</p></div>
          <span class="sim-pts critical">+30</span>
        </div>
        <div class="sim-row">
          <div><strong>Bypasses Runtime Prompts (API 22)</strong><p>Legacy target SDK auto-grants declared sensitive permissions at install.</p></div>
          <span class="sim-pts high">+12</span>
        </div>
      </div>
      <div class="sim-footer-bar">
        <span>Caught on first scan using fast metadata — no cloud lookup needed.</span>
        <span class="sim-pill critical">Uninstall Recommended</span>
      </div>`;
  }

  function renderPosture() {
    const items = [
      { id: "private_dns", title: "Encrypted DNS (Private DNS)", bad: "Turned off — Wi-Fi DNS lookups unencrypted", good: "Automatic (DNS-over-TLS active)", sev: "medium" },
      { id: "lock_otp", title: "Lock-Screen OTP Privacy", bad: "Sensitive notifications visible while locked", good: "Sensitive content hidden on lock screen", sev: "medium" },
      { id: "user_ca", title: "User CA Certificates", bad: "1 custom CA installed: Charles Proxy Root", good: "0 custom user CA certificates", sev: "critical" },
      { id: "usb_adb", title: "USB & Wireless Debugging", bad: "USB Debugging left on", good: "USB & Wireless debugging switched off", sev: "high" }
    ];
    const fixedCount = Object.values(state.postureFixed).filter(Boolean).length;
    const score = 52 + fixedCount * 12;
    const colorVar = score >= 88 ? "--low" : score >= 70 ? "--medium" : "--high";

    simBody.innerHTML = `
      <div class="sim-app-card">
        <div class="sim-app-meta">
          <h4>Device Protection Posture</h4>
          <p class="sim-app-pkg">16 rootless public-API checks · Tap Fix to simulate hardening</p>
          <div class="sim-chip-row">
            <span class="sim-pill ${score >= 88 ? "low" : "high"}">${4 - fixedCount} setting(s) to harden</span>
            <span class="sim-pill low">Lock &amp; Encryption OK</span>
          </div>
        </div>
        ${gaugeSvg(score, colorVar)}
      </div>
      <div class="sim-list">
        ${items
          .map((item) => {
            const fixed = state.postureFixed[item.id];
            return `
              <div class="sim-row">
                <div>
                  <strong>${item.title}</strong>
                  <p>${fixed ? item.good : item.bad}</p>
                </div>
                <button class="sim-action-btn ${fixed ? "is-fixed" : ""}" data-fix="${item.id}">
                  ${fixed ? "✓ Fixed" : "Fix"}
                </button>
              </div>`;
          })
          .join("")}
      </div>`;

    simBody.querySelectorAll("[data-fix]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const key = btn.dataset.fix;
        state.postureFixed[key] = !state.postureFixed[key];
        renderScenario();
      });
    });
  }

  function renderPreinstall() {
    simBody.innerHTML = `
      <div class="sim-app-card">
        <div class="sim-app-meta">
          <h4>bank-update-v4.apk</h4>
          <p class="sim-app-pkg">Picked via Storage Access Framework · 6.4 MB · Not installed</p>
          <div class="sim-chip-row">
            <span class="sim-pill critical">DO NOT INSTALL</span>
            <span class="sim-pill critical">SIGNER MISMATCH</span>
          </div>
        </div>
        ${gaugeSvg(91, "--critical")}
      </div>
      <div class="sim-list">
        <div class="sim-row">
          <div><strong>Developer Signing Certificate Mismatch</strong><p>Package is already installed on this phone, but this APK is signed by a DIFFERENT key.</p></div>
          <span class="sim-pts critical">CRITICAL</span>
        </div>
        <div class="sim-row">
          <div><strong>Banking Overlay Target List in DEX</strong><p>Code strings reference 18 major banking &amp; UPI apps alongside Accessibility.</p></div>
          <span class="sim-pts critical">18 HITS</span>
        </div>
        <div class="sim-row">
          <div><strong>Legacy v1-Only JAR Signature</strong><p>Missing modern APK Signature Scheme v2/v3 signing block.</p></div>
          <span class="sim-pts high">v1 ONLY</span>
        </div>
      </div>
      <div class="sim-footer-bar">
        <span>Zero storage permission · Temp copy deleted immediately after scan.</span>
        <span class="sim-pill low">100% Offline</span>
      </div>`;
  }

  function renderScenario() {
    if (!simBody) return;
    tabButtons.forEach((btn) => {
      const active = btn.dataset.scenario === state.scenario;
      btn.classList.toggle("is-active", active);
      btn.setAttribute("aria-selected", active ? "true" : "false");
    });
    if (state.scenario === "torch") renderTorch();
    else if (state.scenario === "impersonator") renderImpersonator();
    else if (state.scenario === "posture") renderPosture();
    else renderPreinstall();
  }

  tabButtons.forEach((btn) => {
    btn.addEventListener("click", () => {
      state.scenario = btn.dataset.scenario;
      renderScenario();
    });
  });

  renderScenario();

  // ---------------------------------------------------------------------------
  // Copy Obtainium URL helper
  // ---------------------------------------------------------------------------
  const copyBtn = document.getElementById("copy-repo-btn");
  const repoCode = document.getElementById("obtainium-url");
  if (copyBtn && repoCode) {
    copyBtn.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(repoCode.textContent.trim());
        copyBtn.textContent = "Copied ✓";
        setTimeout(() => (copyBtn.textContent = "Copy URL"), 2000);
      } catch {
        copyBtn.textContent = "Select & Copy";
      }
    });
  }

  // ---------------------------------------------------------------------------
  // Live GitHub Releases metadata (graceful fallback if rate-limited or offline)
  // ---------------------------------------------------------------------------
  function formatMB(bytes) {
    if (!bytes || bytes <= 0) return "";
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  async function hydrateGitHubMetadata() {
    try {
      const [repoRes, relRes] = await Promise.all([
        fetch(`https://api.github.com/repos/${REPO}`, { headers: { Accept: "application/vnd.github+json" } }),
        fetch(`https://api.github.com/repos/${REPO}/releases/latest`, { headers: { Accept: "application/vnd.github+json" } })
      ]);

      if (repoRes.ok) {
        const repoData = await repoRes.json();
        const starsEl = document.getElementById("stars-count");
        if (starsEl && typeof repoData.stargazers_count === "number" && repoData.stargazers_count > 0) {
          starsEl.textContent = `${repoData.stargazers_count} ${repoData.stargazers_count === 1 ? "star" : "stars"}`;
        }
      }

      if (relRes.ok) {
        const rel = await relRes.json();
        const tag = rel.tag_name || "v1.1.2";
        const tagEl = document.getElementById("release-tag");
        const relPill = document.getElementById("release-pill");
        const installBadge = document.getElementById("install-version-badge");
        if (tagEl) tagEl.textContent = tag;
        if (relPill && rel.html_url) relPill.href = rel.html_url;
        if (installBadge) installBadge.textContent = tag;

        const assets = Array.isArray(rel.assets) ? rel.assets : [];
        const abis = ["arm64-v8a", "armeabi-v7a", "universal", "x86_64"];
        abis.forEach((abi) => {
          const asset = assets.find((a) => a.name && a.name.includes(abi) && a.name.endsWith(".apk"));
          if (!asset) return;
          const card = document.querySelector(`.abi-option[data-abi="${abi}"]`);
          const sizeEl = document.querySelector(`[data-abi-size="${abi}"]`);
          if (card && asset.browser_download_url) card.href = asset.browser_download_url;
          if (sizeEl && asset.size) sizeEl.textContent = `${formatMB(asset.size)} · Direct APK`;
          if (abi === "arm64-v8a") {
            const heroBtn = document.getElementById("hero-download-btn");
            const heroLbl = document.getElementById("hero-apk-label");
            if (heroBtn && asset.browser_download_url) heroBtn.href = asset.browser_download_url;
            if (heroLbl && asset.size) heroLbl.textContent = `${tag} arm64 · ${formatMB(asset.size)}`;
          }
        });
      }
    } catch {
      // Static fallback links remain fully functional.
    }
  }

  hydrateGitHubMetadata();
})();
