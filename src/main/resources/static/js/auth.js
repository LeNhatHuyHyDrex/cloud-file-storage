(function () {
  "use strict";
  const { api, session, icon, esc } = window.Nimbus;

  if (session.token) {
    window.location.replace("/app.html");
    return;
  }

  window.Nimbus.mountThemeToggle();
  document.getElementById("logo-hero").innerHTML = icon("cloud");
  document.getElementById("features").innerHTML = [
    ["shield", "JWT security", "BCrypt passwords, brute-force protection"],
    ["server", "S3 standard", "AWS S3, SeaweedFS, MinIO, Ceph..."],
    ["share", "Link sharing", "Public links with expiry"],
    ["zap", "Automated CI/CD", "Test, build image, deploy"]
  ].map(([i, t, d]) => '<div class="feature"><b>' + icon(i) + esc(t) + "</b><span>" + esc(d) + "</span></div>").join("");

  let mode = "login";
  const form = document.getElementById("form");
  const err = document.getElementById("error");
  const submit = document.getElementById("submit");

  function showError(msg) {
    err.textContent = msg;
    err.classList.toggle("hidden", !msg);
  }

  if (new URLSearchParams(location.search).get("expired")) {
    showError("Your session has expired, please sign in again.");
  }

  function setMode(m) {
    mode = m;
    document.querySelectorAll(".tabs button").forEach((b) => b.classList.toggle("active", b.dataset.mode === m));
    const reg = m === "register";
    document.getElementById("confirm-field").classList.toggle("hidden", !reg);
    document.getElementById("title").textContent = reg ? "Create a new account" : "Welcome back";
    document.getElementById("subtitle").textContent = reg
      ? "Free 1 GB of storage."
      : "Sign in to access your files.";
    document.getElementById("password").autocomplete = reg ? "new-password" : "current-password";
    submit.textContent = reg ? "Create account" : "Sign in";
    showError("");
  }
  document.querySelectorAll(".tabs button").forEach((b) => b.addEventListener("click", () => setMode(b.dataset.mode)));

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const username = form.username.value.trim();
    const password = form.password.value;
    if (!/^[A-Za-z0-9._-]{3,50}$/.test(username)) {
      return showError("Username must be 3–50 characters: letters, digits and . _ - only");
    }
    if (password.length < 6) return showError("Password must be at least 6 characters");
    if (mode === "register" && password !== document.getElementById("confirm").value) {
      return showError("Passwords do not match");
    }
    submit.disabled = true;
    submit.textContent = "Processing...";
    try {
      const res = await api("/api/auth/" + mode, { method: "POST", body: { username, password }, auth: false });
      session.save(res.token, res.username);
      window.location.href = "/app.html";
    } catch (ex) {
      showError(ex.status === 401 ? "Incorrect username or password" : ex.message);
      submit.disabled = false;
      submit.textContent = mode === "register" ? "Create account" : "Sign in";
    }
  });
})();
