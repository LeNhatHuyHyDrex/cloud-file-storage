(function () {
  "use strict";
  const N = window.Nimbus;
  const { api, session, icon, esc, bytes, date, fullDate, ext, fileIcon, folderIcon, toast } = N;

  if (!session.token) {
    window.location.replace("/index.html");
    return;
  }

  const $ = (s, root) => (root || document).querySelector(s);
  const $$ = (s, root) => Array.from((root || document).querySelectorAll(s));
  const LAYOUT_KEY = "nimbus.layout";

  const state = {
    view: "files",          // files | recent | starred | shared | search
    folderId: null,
    path: [],               // breadcrumb [{id,name}]
    folders: [],
    files: [],
    shares: [],
    query: "",
    layout: (() => { try { return localStorage.getItem(LAYOUT_KEY) || "grid"; } catch (e) { return "grid"; } })(),
    profile: null,
    loading: false
  };
  const thumbs = new Map(); // fileId -> objectURL

  // ---------- Static icons ----------
  function hydrateIcons(root) {
    $$("[data-icon]", root).forEach((el) => { el.outerHTML = icon(el.dataset.icon); });
  }
  hydrateIcons(document);
  $(".sidebar .logo").innerHTML = icon("cloud");
  $("#btn-user").textContent = (session.user || "?").charAt(0).toUpperCase();
  function syncThemeIcon() {
    $("#btn-theme").innerHTML = icon(N.currentTheme() === "dark" ? "sun" : "moon");
  }
  syncThemeIcon();

  // ---------- Profile / storage ----------
  const CAT_ORDER = ["image", "video", "audio", "document", "code", "archive", "other"];
  async function loadProfile() {
    try {
      const p = await api("/api/me");
      state.profile = p;
      const pct = p.quotaBytes ? Math.min(100, (p.usedBytes / p.quotaBytes) * 100) : 0;
      $("#storage-text").textContent = bytes(p.usedBytes) + " / " + bytes(p.quotaBytes);
      $("#storage-bar").innerHTML = CAT_ORDER
        .filter((c) => p.bytesByCategory[c] > 0)
        .map((c) => '<span title="' + N.CAT_LABEL[c] + '" style="width:' + ((p.bytesByCategory[c] / p.quotaBytes) * 100) + '%;background:var(--c-' + c + ')"></span>')
        .join("");
      $("#storage-legend").innerHTML = CAT_ORDER
        .filter((c) => p.bytesByCategory[c] > 0)
        .slice(0, 6)
        .map((c) => '<div><i style="background:var(--c-' + c + ')"></i>' + N.CAT_LABEL[c] + " · " + bytes(p.bytesByCategory[c]) + "</div>")
        .join("") || '<div class="muted">No data yet</div>';
      $("#backend-pill").textContent = (p.storageBackend === "s3" ? "S3 object storage" : "Local storage") + " · " + pct.toFixed(1) + "%";
    } catch (e) { /* api() da xu ly 401 */ }
  }

  // ---------- Data loading ----------
  async function load() {
    state.loading = true;
    render();
    try {
      if (state.view === "files") {
        const q = state.folderId ? "?folderId=" + state.folderId : "";
        const qp = state.folderId ? "?parentId=" + state.folderId : "";
        const [folders, files, path] = await Promise.all([
          api("/api/folders" + qp),
          api("/api/files" + q),
          state.folderId ? api("/api/folders/" + state.folderId + "/path") : Promise.resolve([])
        ]);
        Object.assign(state, { folders, files, path });
      } else if (state.view === "recent") {
        Object.assign(state, { folders: [], files: await api("/api/files?view=recent") });
      } else if (state.view === "starred") {
        Object.assign(state, { folders: [], files: await api("/api/files?view=starred") });
      } else if (state.view === "search") {
        Object.assign(state, { folders: [], files: await api("/api/files?q=" + encodeURIComponent(state.query)) });
      } else if (state.view === "shared") {
        state.shares = await api("/api/shares");
      }
    } catch (e) {
      if (e.status === 404 && state.folderId) {
        state.folderId = null;
        return load();
      }
      toast(e.message, "error");
    }
    state.loading = false;
    render();
  }

  function go(view, folderId) {
    state.view = view;
    state.folderId = folderId || null;
    if (view !== "search") {
      state.query = "";
      $("#search").value = "";
    }
    $$("#nav button").forEach((b) => b.classList.toggle("active", b.dataset.view === (view === "search" ? "" : view)));
    $("#sidebar").classList.remove("open");
    const hash = view === "files" ? (folderId ? "#/folder/" + folderId : "#/") : "#/" + view;
    if (location.hash !== hash && view !== "search") history.pushState(null, "", hash);
    load();
  }

  function fromHash() {
    const h = location.hash.replace(/^#\/?/, "");
    if (h.startsWith("folder/")) return go("files", h.slice(7));
    if (["recent", "starred", "shared"].includes(h)) return go(h);
    go("files");
  }
  window.addEventListener("popstate", fromHash);

  // ---------- Rendering ----------
  const TITLES = { recent: "Recent", starred: "Starred", shared: "Shared links" };

  function renderCrumbs() {
    const c = $("#crumbs");
    if (state.view === "files") {
      const parts = ['<button data-folder="">My files</button>'];
      state.path.forEach((f) => parts.push(icon("chevron") + '<button data-folder="' + f.id + '">' + esc(f.name) + "</button>"));
      c.innerHTML = parts.join("");
      $$("button", c).forEach((b) => b.addEventListener("click", () => go("files", b.dataset.folder || null)));
    } else if (state.view === "search") {
      c.innerHTML = '<button>Results for “' + esc(state.query) + "”</button>";
    } else {
      c.innerHTML = "<button>" + TITLES[state.view] + "</button>";
    }
    const canCreate = state.view === "files";
    $("#btn-new-folder").classList.toggle("hidden", !canCreate);
    $(".view-toggle").classList.toggle("hidden", state.view === "shared");
    $$(".view-toggle button").forEach((b) => b.classList.toggle("active", b.dataset.layout === state.layout));
  }

  function emptyState() {
    const map = {
      files: ["cloud", "This folder is empty", "Drag and drop files here or click Upload to get started.", true],
      recent: ["clock", "No activity yet", "Files you upload will show up here.", false],
      starred: ["star", "No starred files", "Star important files to find them quickly.", false],
      search: ["search", "No results found", "Try a different keyword.", false],
      shared: ["link", "No shared files yet", "Open a file's menu and choose Share link to create a public link.", false]
    };
    const [ic, t, d, btn] = map[state.view];
    return '<div class="empty"><div class="art">' + icon(ic) + "</div><h3>" + t + "</h3><p>" + d + "</p>" +
      (btn ? '<button class="btn primary" data-act="upload">' + icon("upload") + "Upload files</button>" : "") + "</div>";
  }

  function folderCard(f) {
    return '<div class="folder-card" data-folder-id="' + f.id + '" title="' + esc(f.name) + '">' + folderIcon() +
      '<div class="name">' + esc(f.name) + "</div>" +
      '<button class="icon-btn" data-menu-folder="' + f.id + '" aria-label="Options">' + icon("more") + "</button></div>";
  }

  function fileCard(f) {
    const thumb = f.category === "image" && thumbs.get(f.id)
      ? '<img src="' + thumbs.get(f.id) + '" alt="">'
      : fileIcon(f.category);
    return '<div class="file-card" draggable="true" data-file-id="' + f.id + '" title="' + esc(f.name) + '">' +
      '<div class="thumb">' + thumb + (ext(f.name) ? '<span class="ext">' + esc(ext(f.name)) + "</span>" : "") +
      (f.starred ? '<span class="star-badge">' + icon("star") + "</span>" : "") + "</div>" +
      '<div class="meta"><div class="txt"><div class="name">' + esc(f.name) + '</div><div class="sub">' +
      bytes(f.size) + " · " + date(f.uploadedAt) + "</div></div>" +
      '<button class="icon-btn" data-menu-file="' + f.id + '" aria-label="Options">' + icon("more") + "</button></div></div>";
  }

  function listView() {
    const rows = state.folders.map((f) =>
      '<tr data-folder-id="' + f.id + '"><td><div class="cell-name">' + folderIcon() + "<span>" + esc(f.name) + "</span></div></td>" +
      '<td class="num">—</td><td class="num hide-sm">' + date(f.createdAt) + '</td><td class="num hide-sm">Folder</td>' +
      '<td class="act"><button class="icon-btn" data-menu-folder="' + f.id + '">' + icon("more") + "</button></td></tr>"
    ).concat(state.files.map((f) =>
      '<tr data-file-id="' + f.id + '" draggable="true"><td><div class="cell-name">' + fileIcon(f.category) + "<span>" + esc(f.name) + "</span>" +
      (f.starred ? '<span style="color:var(--accent)">' + icon("star") + "</span>" : "") + "</div></td>" +
      '<td class="num">' + bytes(f.size) + '</td><td class="num hide-sm" title="' + fullDate(f.uploadedAt) + '">' + date(f.uploadedAt) +
      '</td><td class="num hide-sm">' + (N.CAT_LABEL[f.category] || "Other") + "</td>" +
      '<td class="act"><button class="icon-btn" data-menu-file="' + f.id + '">' + icon("more") + "</button></td></tr>"
    ));
    return '<div class="table-wrap"><table class="table"><thead><tr><th>Name</th><th>Size</th><th class="hide-sm">Uploaded</th>' +
      '<th class="hide-sm">Type</th><th></th></tr></thead><tbody>' + rows.join("") + "</tbody></table></div>";
  }

  function sharedView() {
    if (!state.shares.length) return emptyState();
    const rows = state.shares.map((s) => {
      const status = s.expired ? '<span class="tag bad">Expired</span>'
        : '<span class="tag ok">' + (s.expiresAt ? "Until " + fullDate(s.expiresAt) : "Never expires") + "</span>";
      return "<tr><td><div class=\"cell-name\">" + fileIcon("other") + "<span>" + esc(s.fileName) + "</span></div></td>" +
        "<td>" + status + '</td><td class="num">' + s.downloads + ' downloads</td><td class="num hide-sm">' + date(s.createdAt) + "</td>" +
        '<td class="act" style="width:96px;white-space:nowrap"><button class="icon-btn" title="Copy link" data-copy="' + esc(s.token) + '">' + icon("copy") +
        '</button><button class="icon-btn" title="Revoke" data-revoke="' + s.id + '">' + icon("trash") + "</button></td></tr>";
    }).join("");
    return '<div class="table-wrap"><table class="table"><thead><tr><th>File</th><th>Status</th><th>Downloads</th>' +
      '<th class="hide-sm">Created</th><th></th></tr></thead><tbody>' + rows + "</tbody></table></div>";
  }

  function render() {
    renderCrumbs();
    const v = $("#view");
    if (state.loading && !state.files.length && !state.folders.length) {
      v.innerHTML = '<div class="grid">' + '<div class="skeleton"></div>'.repeat(6) + "</div>";
      return;
    }
    if (state.view === "shared") {
      v.innerHTML = sharedView();
    } else if (!state.folders.length && !state.files.length) {
      v.innerHTML = emptyState();
    } else if (state.layout === "list") {
      v.innerHTML = listView();
    } else {
      v.innerHTML =
        (state.folders.length ? '<div class="section-title">Folders</div><div class="grid">' + state.folders.map(folderCard).join("") + "</div>" : "") +
        (state.files.length ? '<div class="section-title">Files</div><div class="grid">' + state.files.map(fileCard).join("") + "</div>" : "");
      loadThumbs();
    }
    bindView(v);
  }

  async function loadThumbs() {
    for (const f of state.files) {
      if (f.category !== "image" || thumbs.has(f.id) || f.size > 8 * 1024 * 1024) continue;
      if (!/^image\/(png|jpe?g|gif|webp|bmp)/.test(f.contentType || "")) continue;
      thumbs.set(f.id, null);
      try {
        const res = await api("/api/files/" + f.id + "/download?inline=true", { raw: true });
        const url = URL.createObjectURL(await res.blob());
        thumbs.set(f.id, url);
        const el = $('.file-card[data-file-id="' + f.id + '"] .thumb .ficon');
        if (el) el.outerHTML = '<img src="' + url + '" alt="">';
      } catch (e) { thumbs.delete(f.id); }
    }
  }

  const byId = (id) => state.files.find((f) => f.id === id);
  const folderById = (id) => state.folders.find((f) => f.id === id);

  function bindView(v) {
    $$("[data-act=upload]", v).forEach((b) => b.addEventListener("click", pickFiles));
    $$("[data-folder-id]", v).forEach((el) => {
      el.addEventListener("click", (e) => { if (!e.target.closest("[data-menu-folder]")) go("files", el.dataset.folderId); });
      el.addEventListener("dragover", (e) => {
        if (e.dataTransfer.types.includes("application/x-nimbus-file")) { e.preventDefault(); el.classList.add("drop"); }
      });
      el.addEventListener("dragleave", () => el.classList.remove("drop"));
      el.addEventListener("drop", async (e) => {
        const id = e.dataTransfer.getData("application/x-nimbus-file");
        if (!id) return;
        e.preventDefault();
        e.stopPropagation();
        el.classList.remove("drop");
        await moveFile(id, el.dataset.folderId);
      });
    });
    $$("[data-file-id]", v).forEach((el) => {
      el.addEventListener("click", (e) => { if (!e.target.closest("[data-menu-file]")) preview(byId(el.dataset.fileId)); });
      el.addEventListener("dragstart", (e) => {
        e.dataTransfer.setData("application/x-nimbus-file", el.dataset.fileId);
        e.dataTransfer.effectAllowed = "move";
      });
      el.addEventListener("contextmenu", (e) => { e.preventDefault(); fileMenu(byId(el.dataset.fileId), e.clientX, e.clientY); });
    });
    $$("[data-menu-file]", v).forEach((b) => b.addEventListener("click", (e) => {
      e.stopPropagation();
      const r = b.getBoundingClientRect();
      fileMenu(byId(b.dataset.menuFile), r.right, r.bottom);
    }));
    $$("[data-menu-folder]", v).forEach((b) => b.addEventListener("click", (e) => {
      e.stopPropagation();
      const r = b.getBoundingClientRect();
      folderMenu(folderById(b.dataset.menuFolder), r.right, r.bottom);
    }));
    $$("[data-copy]", v).forEach((b) => b.addEventListener("click", async () => {
      await N.copyText(shareUrl(b.dataset.copy));
      toast("Link copied");
    }));
    $$("[data-revoke]", v).forEach((b) => b.addEventListener("click", async () => {
      if (!(await confirmDialog("Revoke link?", "People with the link will no longer be able to download the file.", "Revoke"))) return;
      await api("/api/shares/" + b.dataset.revoke, { method: "DELETE" });
      toast("Link revoked");
      load();
    }));
  }

  // ---------- Popup menu ----------
  function closePopup() { $$(".popup").forEach((p) => p.remove()); }
  function popup(items, x, y, head) {
    closePopup();
    const el = document.createElement("div");
    el.className = "popup";
    el.innerHTML = (head || "") + items.map((it, i) => it === "-" ? "<hr>"
      : '<button data-i="' + i + '" class="' + (it.danger ? "danger" : "") + '">' + icon(it.icon) + esc(it.label) + "</button>").join("");
    document.body.appendChild(el);
    const w = el.offsetWidth;
    const h = el.offsetHeight;
    el.style.left = Math.max(8, Math.min(x - w, window.innerWidth - w - 8)) + "px";
    el.style.top = Math.max(8, Math.min(y + 4, window.innerHeight - h - 8)) + "px";
    $$("button[data-i]", el).forEach((b) => b.addEventListener("click", () => { closePopup(); items[+b.dataset.i].run(); }));
  }
  document.addEventListener("click", (e) => { if (!e.target.closest(".popup")) closePopup(); });

  function fileMenu(f, x, y) {
    if (!f) return;
    const items = [
      { icon: "eye", label: "Preview", run: () => preview(f) },
      { icon: "download", label: "Download", run: () => download(f) },
      { icon: "share", label: "Share link", run: () => shareDialog(f) },
      "-",
      { icon: "star", label: f.starred ? "Unstar" : "Star", run: () => toggleStar(f) },
      { icon: "edit", label: "Rename", run: () => renameFile(f) },
      { icon: "move", label: "Move", run: () => moveDialog(f) }
    ];
    if (state.profile && state.profile.storageBackend === "s3") {
      items.push({ icon: "link", label: "Temporary S3 link (15 min)", run: () => presign(f) });
    }
    items.push("-", { icon: "trash", label: "Delete", danger: true, run: () => deleteFile(f) });
    popup(items, x, y);
  }

  function folderMenu(f, x, y) {
    if (!f) return;
    popup([
      { icon: "folder", label: "Open", run: () => go("files", f.id) },
      { icon: "edit", label: "Rename", run: () => renameFolder(f) },
      "-",
      { icon: "trash", label: "Delete folder", danger: true, run: () => deleteFolder(f) }
    ], x, y);
  }

  $("#btn-user").addEventListener("click", (e) => {
    e.stopPropagation();
    const r = e.currentTarget.getBoundingClientRect();
    const p = state.profile;
    const head = '<div class="head"><b>' + esc(session.user) + '</b><div class="small muted">' +
      (p ? p.fileCount + (p.fileCount === 1 ? " file · " : " files · ") + p.folderCount + (p.folderCount === 1 ? " folder" : " folders") : "") + "</div></div>";
    popup([
      { icon: "book", label: "API docs (Swagger)", run: () => window.open("/swagger-ui.html", "_blank") },
      { icon: "activity", label: "System status", run: () => window.open("/actuator/health", "_blank") },
      "-",
      { icon: "logout", label: "Sign out", danger: true, run: () => { session.clear(); location.href = "/index.html"; } }
    ], r.right, r.bottom, head);
  });

  // ---------- Modals ----------
  function modal(opts) {
    const bg = document.createElement("div");
    bg.className = "modal-bg";
    bg.innerHTML = '<div class="modal ' + (opts.wide ? "wide" : "") + '"><div class="modal-head">' + (opts.icon || "") +
      "<h3>" + esc(opts.title) + '</h3><button class="icon-btn" data-close>' + icon("x") + "</button></div>" +
      '<div class="modal-body ' + (opts.bodyClass || "") + '">' + opts.body + "</div>" +
      (opts.foot ? '<div class="modal-foot">' + opts.foot + "</div>" : "") + "</div>";
    document.body.appendChild(bg);
    const close = () => { bg.remove(); if (opts.onClose) opts.onClose(); };
    bg.addEventListener("mousedown", (e) => { if (e.target === bg) close(); });
    $("[data-close]", bg).addEventListener("click", close);
    bg.close = close;
    return bg;
  }
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      closePopup();
      const m = $$(".modal-bg").pop();
      if (m) m.close();
    }
    if (e.key === "/" && !/INPUT|TEXTAREA/.test(document.activeElement.tagName)) {
      e.preventDefault();
      $("#search").focus();
    }
  });

  function promptDialog(title, label, value, okText) {
    return new Promise((resolve) => {
      let done = false;
      const m = modal({
        title,
        body: '<form><div class="field" style="margin:0"><label>' + esc(label) + '</label><input class="input" name="v" maxlength="255" required></div></form>',
        foot: '<button class="btn" data-cancel>Cancel</button><button class="btn primary" data-ok>' + esc(okText || "Save") + "</button>",
        onClose: () => { if (!done) resolve(null); }
      });
      const input = $("input", m);
      input.value = value || "";
      setTimeout(() => {
        input.focus();
        const dot = input.value.lastIndexOf(".");
        input.setSelectionRange(0, dot > 0 ? dot : input.value.length);
      }, 30);
      const ok = () => {
        const v = input.value.trim();
        if (!v) return input.focus();
        done = true;
        m.close();
        resolve(v);
      };
      $("form", m).addEventListener("submit", (e) => { e.preventDefault(); ok(); });
      $("[data-ok]", m).addEventListener("click", ok);
      $("[data-cancel]", m).addEventListener("click", () => m.close());
    });
  }

  function confirmDialog(title, message, okText) {
    return new Promise((resolve) => {
      let done = false;
      const m = modal({
        title,
        body: '<p class="muted" style="margin:0;line-height:1.6">' + esc(message) + "</p>",
        foot: '<button class="btn" data-cancel>Cancel</button><button class="btn danger" data-ok>' + esc(okText || "Delete") + "</button>",
        onClose: () => { if (!done) resolve(false); }
      });
      $("[data-ok]", m).addEventListener("click", () => { done = true; m.close(); resolve(true); });
      $("[data-cancel]", m).addEventListener("click", () => m.close());
    });
  }

  // ---------- File actions ----------
  async function fetchBlob(f, inline) {
    const res = await api("/api/files/" + f.id + "/download" + (inline ? "?inline=true" : ""), { raw: true });
    return res.blob();
  }

  async function download(f) {
    try {
      toast("Downloading " + f.name + "...");
      const url = URL.createObjectURL(await fetchBlob(f, false));
      const a = document.createElement("a");
      a.href = url;
      a.download = f.name;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 10000);
    } catch (e) { toast(e.message, "error"); }
  }

  async function presign(f) {
    try {
      const r = await api("/api/files/" + f.id + "/presign");
      await N.copyText(r.url);
      toast("Presigned URL copied (expires at " + new Date(r.expiresAt).toLocaleTimeString("en-US") + ")");
    } catch (e) { toast(e.message, "error"); }
  }

  const TEXT_EXT = ["txt", "md", "csv", "json", "xml", "yml", "yaml", "log", "java", "js", "ts", "py", "c", "cpp", "h", "cs", "go", "rs", "html", "css", "sql", "sh", "kt", "php", "ini", "properties"];

  async function preview(f) {
    if (!f) return;
    const e = ext(f.name);
    const type = f.contentType || "";
    const m = modal({
      title: f.name,
      wide: true,
      icon: fileIcon(f.category),
      bodyClass: "preview-body",
      body: '<div class="muted" style="padding:40px">Loading preview...</div>',
      foot: '<span class="muted small" style="margin-right:auto">' + bytes(f.size) + " · " + fullDate(f.uploadedAt) + "</span>" +
        '<button class="btn" data-share>' + icon("share") + "Share</button>" +
        '<button class="btn primary" data-dl>' + icon("download") + "Download</button>",
      onClose: () => { if (m && m.url) URL.revokeObjectURL(m.url); }
    });
    $("[data-dl]", m).addEventListener("click", () => download(f));
    $("[data-share]", m).addEventListener("click", () => { m.close(); shareDialog(f); });
    const body = $(".modal-body", m);
    const canInline = /^image\/(png|jpe?g|gif|webp|bmp)/.test(type) || type === "application/pdf" || /^(video|audio)\//.test(type);
    const isText = TEXT_EXT.includes(e) || type.startsWith("text/");
    if (!canInline && !isText) {
      body.innerHTML = '<div class="empty">' + '<div class="art">' + icon(f.category) + "</div><h3>No preview available</h3>" +
        "<p>The ." + esc(e || "?") + " format cannot be previewed online yet.</p></div>";
      return;
    }
    if (f.size > 50 * 1024 * 1024) {
      body.innerHTML = '<div class="empty"><h3>File is too large to preview</h3><p>Download it to open.</p></div>';
      return;
    }
    try {
      if (isText && !canInline) {
        const blob = await fetchBlob(f, false);
        const text = await blob.slice(0, 512 * 1024).text();
        body.innerHTML = "<pre>" + esc(text) + (blob.size > 512 * 1024 ? "\n\n… (truncated)" : "") + "</pre>";
        return;
      }
      const blob = await fetchBlob(f, true);
      const url = URL.createObjectURL(new Blob([blob], { type }));
      m.url = url;
      if (type.startsWith("image/")) body.innerHTML = '<img src="' + url + '" alt="">';
      else if (type === "application/pdf") body.innerHTML = '<iframe src="' + url + '" title="PDF"></iframe>';
      else if (type.startsWith("video/")) body.innerHTML = '<video src="' + url + '" controls autoplay></video>';
      else body.innerHTML = '<audio src="' + url + '" controls autoplay></audio>';
    } catch (err) {
      body.innerHTML = '<div class="empty"><h3>Could not load preview</h3><p>' + esc(err.message) + "</p></div>";
    }
  }

  function shareUrl(token) {
    return location.origin + "/share.html?t=" + encodeURIComponent(token);
  }

  async function shareDialog(f) {
    const m = modal({
      title: "Share “" + f.name + "”",
      icon: fileIcon(f.category),
      body:
        '<div class="field"><label>Link expiry</label><select class="input" name="exp">' +
        '<option value="1">1 hour</option><option value="24" selected>1 day</option><option value="168">7 days</option>' +
        '<option value="720">30 days</option><option value="">Never expires</option></select></div>' +
        '<button class="btn primary block" data-create>' + icon("link") + "Create share link</button>" +
        '<div class="link-list" data-list></div>'
    });
    const list = $("[data-list]", m);
    async function refresh() {
      const all = (await api("/api/shares")).filter((s) => s.fileId === f.id);
      list.innerHTML = all.length ? '<div class="section-title" style="margin:6px 0 0">Existing links</div>' + all.map((s) =>
        '<div class="link-item"><div class="txt"><div class="share-link"><input class="input" readonly value="' + esc(shareUrl(s.token)) + '" style="height:34px"></div>' +
        '<div style="margin-top:6px">' + (s.expired ? '<span class="tag bad">Expired</span>' : '<span class="tag ok">' +
          (s.expiresAt ? "Expires " + fullDate(s.expiresAt) : "Never expires") + "</span>") +
        ' <span class="tag">' + s.downloads + " downloads</span></div></div>" +
        '<button class="icon-btn" title="Copy" data-copy="' + esc(s.token) + '">' + icon("copy") + "</button>" +
        '<button class="icon-btn" title="Revoke" data-revoke="' + s.id + '">' + icon("trash") + "</button></div>").join("") : "";
      $$("[data-copy]", list).forEach((b) => b.addEventListener("click", async () => {
        await N.copyText(shareUrl(b.dataset.copy));
        toast("Link copied");
      }));
      $$("[data-revoke]", list).forEach((b) => b.addEventListener("click", async () => {
        await api("/api/shares/" + b.dataset.revoke, { method: "DELETE" });
        toast("Link revoked");
        refresh();
      }));
    }
    $("[data-create]", m).addEventListener("click", async () => {
      const v = $("select", m).value;
      try {
        const s = await api("/api/files/" + f.id + "/shares", { method: "POST", body: { expiresInHours: v ? +v : null } });
        await N.copyText(shareUrl(s.token));
        toast("Share link created and copied");
        refresh();
      } catch (e) { toast(e.message, "error"); }
    });
    refresh();
  }

  async function toggleStar(f) {
    try {
      await api("/api/files/" + f.id + "/star", { method: "PUT", body: { starred: !f.starred } });
      toast(f.starred ? "Unstarred" : "Starred");
      load();
    } catch (e) { toast(e.message, "error"); }
  }

  async function renameFile(f) {
    const name = await promptDialog("Rename file", "New name", f.name);
    if (!name || name === f.name) return;
    try {
      await api("/api/files/" + f.id, { method: "PATCH", body: { name } });
      toast("Renamed");
      load();
    } catch (e) { toast(e.message, "error"); }
  }

  async function moveFile(id, folderId) {
    try {
      await api("/api/files/" + id + "/move", { method: "POST", body: { folderId: folderId || null } });
      toast("File moved");
      load();
    } catch (e) { toast(e.message, "error"); }
  }

  async function moveDialog(f) {
    const all = await api("/api/folders?all=true");
    const children = {};
    all.forEach((x) => { (children[x.parentId || "root"] = children[x.parentId || "root"] || []).push(x); });
    Object.values(children).forEach((arr) => arr.sort((a, b) => a.name.localeCompare(b.name)));
    const rows = [];
    (function walk(parent, depth) {
      (children[parent] || []).forEach((x) => {
        rows.push('<button data-target="' + x.id + '" style="padding-left:' + (10 + depth * 20) + 'px"' +
          (x.id === f.folderId ? " disabled" : "") + ">" + folderIcon().replace("ficon", "ficon mini") + esc(x.name) + "</button>");
        walk(x.id, depth + 1);
      });
    })("root", 0);
    let target;
    const m = modal({
      title: "Move “" + f.name + "”",
      body: '<div class="tree"><button data-target=""' + (!f.folderId ? " disabled" : "") + ">" + icon("home") + "My files (root)</button>" + rows.join("") + "</div>",
      foot: '<button class="btn" data-cancel>Cancel</button><button class="btn primary" data-ok disabled>Move here</button>'
    });
    $$(".tree .ficon", m).forEach((el) => { el.style.width = "26px"; el.style.height = "26px"; el.style.borderRadius = "7px"; });
    $$("[data-target]", m).forEach((b) => b.addEventListener("click", () => {
      $$("[data-target]", m).forEach((x) => x.classList.remove("sel"));
      b.classList.add("sel");
      target = b.dataset.target;
      $("[data-ok]", m).disabled = false;
    }));
    $("[data-cancel]", m).addEventListener("click", () => m.close());
    $("[data-ok]", m).addEventListener("click", async () => { m.close(); await moveFile(f.id, target); });
  }

  async function deleteFile(f) {
    if (!(await confirmDialog("Delete file?", "“" + f.name + "” will be permanently deleted from storage, and its share links will be revoked."))) return;
    try {
      await api("/api/files/" + f.id, { method: "DELETE" });
      toast("Deleted " + f.name);
      load();
      loadProfile();
    } catch (e) { toast(e.message, "error"); }
  }

  async function newFolder() {
    const name = await promptDialog("New folder", "Folder name", "New folder", "Create");
    if (!name) return;
    try {
      await api("/api/folders", { method: "POST", body: { name, parentId: state.folderId } });
      toast("Folder created");
      load();
      loadProfile();
    } catch (e) { toast(e.message, "error"); }
  }

  async function renameFolder(f) {
    const name = await promptDialog("Rename folder", "New name", f.name);
    if (!name || name === f.name) return;
    try {
      await api("/api/folders/" + f.id, { method: "PATCH", body: { name } });
      load();
    } catch (e) { toast(e.message, "error"); }
  }

  async function deleteFolder(f) {
    if (!(await confirmDialog("Delete folder?", "“" + f.name + "” and all subfolders and files inside it will be permanently deleted."))) return;
    try {
      await api("/api/folders/" + f.id, { method: "DELETE" });
      toast("Folder deleted");
      load();
      loadProfile();
    } catch (e) { toast(e.message, "error"); }
  }

  // ---------- Upload ----------
  const fileInput = $("#file-input");
  function pickFiles() { fileInput.click(); }
  fileInput.addEventListener("change", () => { enqueue(Array.from(fileInput.files)); fileInput.value = ""; });

  let pending = 0;
  function enqueue(files) {
    if (!files.length) return;
    const folderId = state.view === "files" ? state.folderId : null;
    const panel = $("#uploads");
    panel.classList.remove("hidden");
    const list = $("#uploads-list");
    const jobs = files.map((file) => {
      const row = document.createElement("div");
      row.className = "up-item";
      row.innerHTML = fileIcon("other") + '<div class="txt"><div class="name">' + esc(file.name) + '</div><div class="prog"><span></span></div><div class="st">Waiting · ' + bytes(file.size) + "</div></div>";
      list.prepend(row);
      return { file, row };
    });
    pending += jobs.length;
    updateUploadTitle();
    (async () => {
      for (const { file, row } of jobs) {
        const bar = $(".prog span", row);
        const st = $(".st", row);
        try {
          await N.uploadFile(file, folderId, (p) => {
            bar.style.width = (p * 100).toFixed(0) + "%";
            st.textContent = (p * 100).toFixed(0) + "% · " + bytes(file.size);
          });
          bar.style.width = "100%";
          row.classList.add("done");
          st.textContent = "Done · " + bytes(file.size);
        } catch (e) {
          row.classList.add("err");
          st.textContent = e.message;
        }
        pending--;
        updateUploadTitle();
      }
      load();
      loadProfile();
    })();
  }
  function updateUploadTitle() {
    $("#uploads-title").textContent = pending > 0 ? "Uploading " + pending + (pending === 1 ? " file..." : " files...") : "Upload complete";
  }
  $("#uploads-close").addEventListener("click", () => {
    $("#uploads").classList.add("hidden");
    if (!pending) $("#uploads-list").innerHTML = "";
  });

  // Keo tha file tu may tinh
  let dragDepth = 0;
  const isExternal = (e) => e.dataTransfer && Array.from(e.dataTransfer.types).includes("Files");
  window.addEventListener("dragenter", (e) => {
    if (!isExternal(e)) return;
    e.preventDefault();
    dragDepth++;
    $("#drop-target").textContent = "to " + (state.view === "files" && state.path.length ? "“" + state.path[state.path.length - 1].name + "”" : "My files");
    $("#drop").classList.remove("hidden");
  });
  window.addEventListener("dragover", (e) => { if (isExternal(e)) e.preventDefault(); });
  window.addEventListener("dragleave", (e) => {
    if (!isExternal(e)) return;
    dragDepth = Math.max(0, dragDepth - 1);
    if (!dragDepth) $("#drop").classList.add("hidden");
  });
  window.addEventListener("drop", (e) => {
    if (!isExternal(e)) return;
    e.preventDefault();
    dragDepth = 0;
    $("#drop").classList.add("hidden");
    enqueue(Array.from(e.dataTransfer.files));
  });

  // ---------- Toolbar ----------
  $("#btn-upload").addEventListener("click", pickFiles);
  $("#btn-upload-side").addEventListener("click", pickFiles);
  $("#btn-new-folder").addEventListener("click", newFolder);
  $("#btn-theme").addEventListener("click", () => { N.toggleTheme(); syncThemeIcon(); });
  $("#btn-menu").addEventListener("click", (e) => { e.stopPropagation(); $("#sidebar").classList.toggle("open"); });
  document.addEventListener("click", (e) => {
    if (!e.target.closest("#sidebar") && !e.target.closest("#btn-menu")) $("#sidebar").classList.remove("open");
  });
  $$("#nav button").forEach((b) => b.addEventListener("click", () => go(b.dataset.view)));
  $$(".view-toggle button").forEach((b) => b.addEventListener("click", () => {
    state.layout = b.dataset.layout;
    try { localStorage.setItem(LAYOUT_KEY, state.layout); } catch (e) { /* ignore */ }
    render();
  }));

  let searchTimer;
  $("#search").addEventListener("input", (e) => {
    clearTimeout(searchTimer);
    const q = e.target.value.trim();
    searchTimer = setTimeout(() => {
      if (q) {
        state.query = q;
        go("search");
      } else if (state.view === "search") {
        go("files");
      }
    }, 300);
  });

  loadProfile();
  fromHash();
})();
