(function () {
  "use strict";
  const { api, session, icon, esc } = window.Nimbus;

  if (session.token) {
    window.location.replace("/app.html");
    return;
  }

  document.getElementById("logo-hero").innerHTML = icon("cloud");
  document.getElementById("features").innerHTML = [
    ["shield", "Bảo mật JWT", "Mật khẩu BCrypt, chống dò mật khẩu"],
    ["server", "Chuẩn S3", "AWS S3, SeaweedFS, MinIO, Ceph..."],
    ["share", "Chia sẻ link", "Link công khai có hạn dùng"],
    ["zap", "CI/CD tự động", "Test, build image, deploy"]
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
    showError("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.");
  }

  function setMode(m) {
    mode = m;
    document.querySelectorAll(".tabs button").forEach((b) => b.classList.toggle("active", b.dataset.mode === m));
    const reg = m === "register";
    document.getElementById("confirm-field").classList.toggle("hidden", !reg);
    document.getElementById("title").textContent = reg ? "Tạo tài khoản mới" : "Chào mừng trở lại";
    document.getElementById("subtitle").textContent = reg
      ? "Miễn phí 1 GB dung lượng lưu trữ."
      : "Đăng nhập để truy cập kho file của bạn.";
    document.getElementById("password").autocomplete = reg ? "new-password" : "current-password";
    submit.textContent = reg ? "Tạo tài khoản" : "Đăng nhập";
    showError("");
  }
  document.querySelectorAll(".tabs button").forEach((b) => b.addEventListener("click", () => setMode(b.dataset.mode)));

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const username = form.username.value.trim();
    const password = form.password.value;
    if (!/^[A-Za-z0-9._-]{3,50}$/.test(username)) {
      return showError("Tên đăng nhập 3–50 ký tự, chỉ gồm chữ, số và . _ -");
    }
    if (password.length < 6) return showError("Mật khẩu tối thiểu 6 ký tự");
    if (mode === "register" && password !== document.getElementById("confirm").value) {
      return showError("Mật khẩu nhập lại không khớp");
    }
    submit.disabled = true;
    submit.textContent = "Đang xử lý...";
    try {
      const res = await api("/api/auth/" + mode, { method: "POST", body: { username, password }, auth: false });
      session.save(res.token, res.username);
      window.location.href = "/app.html";
    } catch (ex) {
      showError(ex.status === 401 ? "Sai tài khoản hoặc mật khẩu" : ex.message);
      submit.disabled = false;
      submit.textContent = mode === "register" ? "Tạo tài khoản" : "Đăng nhập";
    }
  });
})();
