/* Nimbus Drive - tien ich dung chung: API client, icon, dinh dang, toast, theme */
(function () {
  "use strict";

  const TOKEN_KEY = "nimbus.token";
  const USER_KEY = "nimbus.user";
  const THEME_KEY = "nimbus.theme";

  // ---------- Theme ----------
  function applyTheme(t) {
    document.documentElement.setAttribute("data-theme", t);
  }
  function currentTheme() {
    try {
      const saved = localStorage.getItem(THEME_KEY);
      if (saved) return saved;
    } catch (e) { /* ignore */ }
    return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }
  function toggleTheme() {
    const next = currentTheme() === "dark" ? "light" : "dark";
    try { localStorage.setItem(THEME_KEY, next); } catch (e) { /* ignore */ }
    applyTheme(next);
    return next;
  }
  applyTheme(currentTheme());

  /** Nut sang/toi noi o goc tren, dung cho cac trang khong co thanh cong cu. */
  function mountThemeToggle() {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "icon-btn theme-fab";
    btn.title = "Đổi giao diện sáng/tối";
    btn.setAttribute("aria-label", "Đổi giao diện sáng/tối");
    const paint = () => { btn.innerHTML = icon(currentTheme() === "dark" ? "sun" : "moon"); };
    btn.addEventListener("click", () => { toggleTheme(); paint(); });
    paint();
    document.body.appendChild(btn);
  }

  // ---------- Session ----------
  const session = {
    get token() { try { return localStorage.getItem(TOKEN_KEY); } catch (e) { return null; } },
    get user() { try { return localStorage.getItem(USER_KEY); } catch (e) { return null; } },
    save(token, user) {
      localStorage.setItem(TOKEN_KEY, token);
      localStorage.setItem(USER_KEY, user);
    },
    clear() {
      try { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); } catch (e) { /* ignore */ }
    }
  };

  // ---------- API ----------
  class ApiError extends Error {
    constructor(status, message) { super(message); this.status = status; }
  }

  async function errorFrom(res) {
    let msg = res.statusText || "Lỗi " + res.status;
    try {
      const body = await res.json();
      msg = body.detail || body.message || body.title || msg;
    } catch (e) { /* not json */ }
    if (res.status === 401) msg = "Phiên đăng nhập đã hết hạn";
    if (res.status === 413 && !msg) msg = "File quá lớn";
    return new ApiError(res.status, msg);
  }

  async function api(path, opts = {}) {
    const headers = Object.assign({}, opts.headers || {});
    if (session.token) headers["Authorization"] = "Bearer " + session.token;
    let body = opts.body;
    if (body && !(body instanceof FormData) && typeof body !== "string") {
      headers["Content-Type"] = "application/json";
      body = JSON.stringify(body);
    }
    const res = await fetch(path, { method: opts.method || "GET", headers, body });
    if (res.status === 401 && opts.auth !== false) {
      session.clear();
      window.location.href = "/index.html?expired=1";
      throw new ApiError(401, "Phiên đăng nhập đã hết hạn");
    }
    if (!res.ok) throw await errorFrom(res);
    if (opts.raw) return res;
    if (res.status === 204) return null;
    const type = res.headers.get("Content-Type") || "";
    return type.includes("json") ? res.json() : res.text();
  }

  /** Upload co bao tien do (fetch chua ho tro upload progress). */
  function uploadFile(file, folderId, onProgress) {
    return new Promise((resolve, reject) => {
      const fd = new FormData();
      fd.append("file", file);
      if (folderId) fd.append("folderId", folderId);
      const xhr = new XMLHttpRequest();
      xhr.open("POST", "/api/files");
      if (session.token) xhr.setRequestHeader("Authorization", "Bearer " + session.token);
      xhr.upload.onprogress = (e) => { if (e.lengthComputable) onProgress(e.loaded / e.total); };
      xhr.onload = () => {
        let body = null;
        try { body = JSON.parse(xhr.responseText); } catch (e) { /* ignore */ }
        if (xhr.status >= 200 && xhr.status < 300) resolve(body);
        else reject(new ApiError(xhr.status, (body && (body.detail || body.title)) || "Upload thất bại (" + xhr.status + ")"));
      };
      xhr.onerror = () => reject(new ApiError(0, "Mất kết nối mạng"));
      xhr.send(fd);
    });
  }

  // ---------- Icons (stroke, 24x24) ----------
  const P = {
    cloud: '<path d="M17.5 19a4.5 4.5 0 1 0-1.4-8.78A6 6 0 0 0 4.6 12.9 3.5 3.5 0 0 0 6.5 19z"/>',
    folder: '<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
    folderPlus: '<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M12 10v6M9 13h6"/>',
    file: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/>',
    image: '<rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-5-5L5 21"/>',
    video: '<rect x="2" y="5" width="15" height="14" rx="2"/><path d="m17 10 5-3v10l-5-3z"/>',
    audio: '<path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>',
    document: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5M9 13h6M9 17h6M9 9h1"/>',
    archive: '<rect x="3" y="4" width="18" height="5" rx="1"/><path d="M5 9v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V9M10 13h4"/>',
    code: '<path d="m16 18 6-6-6-6M8 6l-6 6 6 6"/>',
    other: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/>',
    upload: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12"/>',
    download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/>',
    share: '<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.6 13.5 6.8 4M15.4 6.5l-6.8 4"/>',
    link: '<path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7"/><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"/>',
    star: '<path d="m12 2 3.1 6.3 6.9 1-5 4.9 1.2 6.8L12 17.8 5.8 21l1.2-6.8-5-4.9 6.9-1z"/>',
    trash: '<path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6M10 11v6M14 11v6"/>',
    edit: '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/>',
    move: '<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="m13 10 3 3-3 3M8 13h8"/>',
    more: '<circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/>',
    grid: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
    list: '<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    x: '<path d="M18 6 6 18M6 6l12 12"/>',
    logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>',
    moon: '<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    home: '<path d="m3 10 9-7 9 7v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M9 22V12h6v10"/>',
    chevron: '<path d="m9 18 6-6-6-6"/>',
    check: '<path d="M20 6 9 17l-5-5"/>',
    copy: '<rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
    eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
    menu: '<path d="M3 6h18M3 12h18M3 18h18"/>',
    book: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20V3H6.5A2.5 2.5 0 0 0 4 5.5z"/><path d="M4 19.5V21h16"/>',
    alert: '<circle cx="12" cy="12" r="9"/><path d="M12 8v4M12 16h.01"/>',
    shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
    zap: '<path d="M13 2 3 14h9l-1 8 10-12h-9z"/>',
    server: '<rect x="2" y="3" width="20" height="8" rx="2"/><rect x="2" y="13" width="20" height="8" rx="2"/><path d="M6 7h.01M6 17h.01"/>',
    activity: '<path d="M22 12h-4l-3 9L9 3l-3 9H2"/>'
  };
  function icon(name, cls) {
    return '<svg class="i ' + (cls || "") + '" viewBox="0 0 24 24" aria-hidden="true">' + (P[name] || P.file) + "</svg>";
  }

  // ---------- Format ----------
  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }
  function bytes(n) {
    if (n == null) return "";
    const u = ["B", "KB", "MB", "GB", "TB"];
    let i = 0;
    let v = n;
    while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
    return (i === 0 ? v : v.toFixed(v < 10 ? 1 : 0)) + " " + u[i];
  }
  function date(iso) {
    if (!iso) return "";
    const d = new Date(iso);
    const diff = (Date.now() - d.getTime()) / 1000;
    if (diff < 60) return "Vừa xong";
    if (diff < 3600) return Math.floor(diff / 60) + " phút trước";
    if (diff < 86400) return Math.floor(diff / 3600) + " giờ trước";
    if (diff < 86400 * 7) return Math.floor(diff / 86400) + " ngày trước";
    return d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" });
  }
  function fullDate(iso) {
    return iso ? new Date(iso).toLocaleString("vi-VN") : "";
  }
  function ext(name) {
    const i = (name || "").lastIndexOf(".");
    return i > 0 ? name.slice(i + 1).toLowerCase() : "";
  }
  const CAT_LABEL = { image: "Hình ảnh", video: "Video", audio: "Âm thanh", document: "Tài liệu", archive: "Nén", code: "Mã nguồn", other: "Khác" };
  function fileIcon(category, size) {
    const cat = category || "other";
    return '<div class="ficon" style="background:color-mix(in srgb,var(--c-' + cat + ') 14%,transparent);color:var(--c-' + cat + ')">' + icon(cat) + "</div>";
  }
  function folderIcon() {
    return '<div class="ficon" style="background:color-mix(in srgb,var(--c-folder) 16%,transparent);color:var(--c-folder)">' + icon("folder") + "</div>";
  }

  // ---------- Toast ----------
  function toast(msg, kind) {
    let box = document.querySelector(".toasts");
    if (!box) {
      box = document.createElement("div");
      box.className = "toasts";
      document.body.appendChild(box);
    }
    const el = document.createElement("div");
    el.className = "toast " + (kind || "ok");
    el.innerHTML = icon(kind === "error" ? "alert" : "check") + "<span>" + esc(msg) + "</span>";
    box.appendChild(el);
    setTimeout(() => { el.style.opacity = "0"; el.style.transition = "opacity .3s"; }, 2800);
    setTimeout(() => el.remove(), 3200);
  }

  async function copyText(text) {
    try {
      await navigator.clipboard.writeText(text);
    } catch (e) {
      const ta = document.createElement("textarea");
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand("copy");
      ta.remove();
    }
  }

  window.Nimbus = { api, uploadFile, session, icon, esc, bytes, date, fullDate, ext, fileIcon, folderIcon, CAT_LABEL, toast, copyText, toggleTheme, currentTheme, mountThemeToggle, ApiError };
})();
