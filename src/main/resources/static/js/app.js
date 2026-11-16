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
    invitations: [],
    sharedFolder: null,
    trash: [],
    query: "",
    layout: (() => { try { return localStorage.getItem(LAYOUT_KEY) || "grid"; } catch (e) { return "grid"; } })(),
    profile: null,
    loading: false
  };
  const thumbs = new Map(); // fileId -> objectURL
  const selected = new Set();

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

  async function loadInvitations() {
    try {
      state.invitations = await api("/api/folder-invitations");
      const count = state.invitations.filter((i) => i.status === "PENDING").length;
      $("#invitation-count").textContent = count ? String(count) : "";
    } catch (e) { /* Keep the rest of the drive usable if notifications cannot load. */ }
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
      } else if (state.view === "incoming") {
        await loadInvitations();
      } else if (state.view === "trash") {
        state.trash = await api("/api/trash");
      } else if (state.view === "shared-folder") {
        const s = state.sharedFolder;
        s.contents = await api("/api/folder-invitations/" + s.id + "/contents" + (s.folderId ? "?folderId=" + s.folderId : ""));
      }
    } catch (e) {
      if (e.status === 404 && state.folderId) {
        state.folderId = null;
        return load();
      }
      toast(e.message, "error");
    }
    if (["files", "recent", "starred", "search"].includes(state.view)) {
      const visible = new Set(state.files.map((f) => 'f:' + f.id).concat(state.folders.map((f) => 'd:' + f.id)));
      for (const key of selected) if (!visible.has(key)) selected.delete(key);
    }
    state.loading = false;
    render();
  }

  function go(view, folderId) {
    if (view !== state.view || (folderId || null) !== state.folderId) selected.clear();
    state.view = view;
    state.folderId = folderId || null;
    if (view !== "search") {
      state.query = "";
      $("#search").value = "";
    }
    $$("#nav button").forEach((b) => b.classList.toggle("active", b.dataset.view === (view === "shared-folder" ? "incoming" : view === "search" ? "" : view)));
    $("#sidebar").classList.remove("open");
    const hash = view === "files" ? (folderId ? "#/folder/" + folderId : "#/") : "#/" + view;
    if (location.hash !== hash && view !== "search") history.pushState(null, "", hash);
    load();
  }

  function fromHash() {
    const h = location.hash.replace(/^#\/?/, "");
    if (h.startsWith("folder/")) return go("files", h.slice(7));
    if (["recent", "starred", "shared", "incoming", "trash"].includes(h)) return go(h);
    go("files");
  }
  window.addEventListener("popstate", fromHash);

  // ---------- Rendering ----------
  const TITLES = { recent: "Recent", starred: "Starred", shared: "Shared links", incoming: "Shared with me", "shared-folder": "Shared folder", trash: "Trash" };

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
    $(".view-toggle").classList.toggle("hidden", ["shared", "incoming", "shared-folder", "trash"].includes(state.view));
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
    return '<div class="folder-card' + (selected.has('d:' + f.id) ? ' selected' : '') + '" data-folder-id="' + f.id + '" title="' + esc(f.name) + '">' + selectBox('d', f) + folderIcon() +
      '<div class="name">' + esc(f.name) + "</div>" +
      '<button class="icon-btn" data-menu-folder="' + f.id + '" aria-label="Options">' + icon("more") + "</button></div>";
  }

  function fileCard(f) {
    const thumb = thumbs.get(f.id)
      ? '<img src="' + thumbs.get(f.id) + '" alt="">'
      : fileIcon(f.category);
    return '<div class="file-card' + (selected.has('f:' + f.id) ? ' selected' : '') + '" draggable="true" data-file-id="' + f.id + '" title="' + esc(f.name) + '">' + selectBox('f', f) +
      '<div class="thumb">' + thumb + (ext(f.name) ? '<span class="ext">' + esc(ext(f.name)) + "</span>" : "") +
      (f.starred ? '<span class="star-badge">' + icon("star") + "</span>" : "") + "</div>" +
      '<div class="meta"><div class="txt"><div class="name">' + esc(f.name) + '</div><div class="sub">' +
      bytes(f.size) + " · " + date(f.uploadedAt) + "</div></div>" +
      '<button class="icon-btn" data-menu-file="' + f.id + '" aria-label="Options">' + icon("more") + "</button></div></div>";
  }

  function listView() {
    const rows = state.folders.map((f) =>
      '<tr data-folder-id="' + f.id + '"><td><div class="cell-name">' + selectBox('d', f) + folderIcon() + "<span>" + esc(f.name) + "</span></div></td>" +
      '<td class="num">—</td><td class="num hide-sm">' + date(f.createdAt) + '</td><td class="num hide-sm">Folder</td>' +
      '<td class="act"><button class="icon-btn" data-menu-folder="' + f.id + '">' + icon("more") + "</button></td></tr>"
    ).concat(state.files.map((f) =>
      '<tr data-file-id="' + f.id + '" draggable="true"><td><div class="cell-name">' + selectBox('f', f) + fileIcon(f.category) + "<span>" + esc(f.name) + "</span>" +
      (f.starred ? '<span style="color:var(--accent)">' + icon("star") + "</span>" : "") + "</div></td>" +
      '<td class="num">' + bytes(f.size) + '</td><td class="num hide-sm" title="' + fullDate(f.uploadedAt) + '">' + date(f.uploadedAt) +
      '</td><td class="num hide-sm">' + (N.CAT_LABEL[f.category] || "Other") + "</td>" +
      '<td class="act"><button class="icon-btn" data-menu-file="' + f.id + '">' + icon("more") + "</button></td></tr>"
    ));
    return '<div class="table-wrap"><table class="table"><thead><tr><th>Name</th><th>Size</th><th class="hide-sm">Uploaded</th>' +
      '<th class="hide-sm">Type</th><th></th></tr></thead><tbody>' + rows.join("") + "</tbody></table></div>";
  }

  function selectBox(kind, item) {
    const key = kind + ':' + item.id;
    return '<input class="item-select" type="checkbox" data-select="' + key + '" aria-label="Select ' + esc(item.name) + '"' + (selected.has(key) ? ' checked' : '') + '>';
  }

  function selectionBar() {
    return '<div class="selection-bar"><label><input type="checkbox" data-select-all> Select all</label>' +
      '<span data-selection-count aria-live="polite">0 selected</span><button class="btn" data-download-selected disabled>Download separately</button>' +
      '<button class="btn primary" data-download-zip disabled>Download ZIP</button><button class="btn" data-clear-selection disabled>Clear</button></div>';
  }

  function updateSelection(v) {
    const boxes = $$('[data-select]', v);
    const checked = boxes.filter((b) => selected.has(b.dataset.select)).length;
    boxes.forEach((b) => { b.checked = selected.has(b.dataset.select); const card = b.closest('.file-card, .folder-card, tr'); if (card) card.classList.toggle('selected', b.checked); });
    const all = $('[data-select-all]', v);
    if (!all) return;
    all.checked = boxes.length > 0 && checked === boxes.length;
    all.indeterminate = checked > 0 && checked < boxes.length;
    $('[data-selection-count]', v).textContent = checked + (checked === 1 ? ' selected' : ' selected');
    $$('[data-download-selected], [data-download-zip], [data-clear-selection]', v).forEach((b) => { b.disabled = checked === 0; });
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

  function invitationsView() {
    if (!state.invitations.length) return '<div class="empty"><div class="art">' + icon("folder") + '</div><h3>No shared folders yet</h3><p>Folder invitations will appear here.</p></div>';
    return '<div class="shared-items">' + state.invitations.map((i) =>
      '<div class="shared-item">' + folderIcon() + '<div class="shared-item-info"><strong>' + esc(i.folderName) + '</strong><span class="muted small">Shared by ' + esc(i.owner) + (i.status === "PENDING" ? ' · Invitation pending' : ' · View and download') + '</span></div>' +
      (i.status === "PENDING" ? '<button class="btn" data-decline="' + i.id + '">Decline</button><button class="btn primary" data-accept="' + i.id + '">Accept</button>' : '<button class="btn" data-open-share="' + i.id + '">Open</button>') + '</div>').join("") + '</div>';
  }

  function sharedFolderView() {
    const s = state.sharedFolder, c = s && s.contents;
    if (!c) return '<div class="muted">Loading folder...</div>';
    return '<div class="shared-folder-head"><button class="btn" data-shared-back>' + icon("chevron") + 'Back</button><div><strong>' + esc(c.folderName) + '</strong><div class="muted small">Shared by ' + esc(c.owner) + ' · View and download</div></div><button class="btn" data-shared-folder-download>Download folder ZIP</button></div>' +
      '<div class="shared-items">' + c.folders.map((f) => '<button class="shared-item shared-entry" data-shared-folder="' + f.id + '">' + folderIcon() + '<span>' + esc(f.name) + '</span></button>').join("") +
      c.files.map((f) => '<div class="shared-item">' + fileIcon(f.category) + '<div class="shared-item-info"><strong>' + esc(f.name) + '</strong><span class="muted small">' + bytes(f.size) + '</span></div>' +
        (/^(image\/(png|jpeg|gif|webp|bmp)|video\/|audio\/|application\/pdf)/i.test(f.contentType || '') ? '<button class="btn" data-shared-preview="' + f.id + '">Preview</button>' : '') +
        '<button class="btn" data-shared-download="' + f.id + '">Download</button></div>').join("") +
      (!c.folders.length && !c.files.length ? '<p class="muted">This folder is empty.</p>' : '') + '</div>';
  }

  function trashView() {
    if (!state.trash.length) return '<div class="empty"><div class="art">' + icon('trash') + '</div><h3>Trash is empty</h3><p>Items in trash are automatically deleted after 30 days.</p></div>';
    return '<p class="muted small">Items in trash are automatically deleted after 30 days. They still count toward your storage quota.</p><div class="shared-items">' + state.trash.map((item) =>
      '<div class="shared-item">' + (item.type === 'folder' ? folderIcon() : fileIcon('other')) + '<div class="shared-item-info"><strong>' + esc(item.name) + '</strong><span class="muted small">' + (item.type === 'folder' ? 'Folder' : 'File') + ' · Deletes after ' + esc(fullDate(item.expiresAt)) + '</span></div>' +
      '<button class="btn" data-trash-restore="' + item.type + ':' + item.id + '">Restore</button><button class="btn" data-trash-purge="' + item.type + ':' + item.id + '">Delete permanently</button></div>').join('') + '</div>';
  }

  function render() {
    renderCrumbs();
    const v = $("#view");
    if (state.loading && !state.files.length && !state.folders.length) {
      v.innerHTML = '<div class="grid">' + '<div class="skeleton"></div>'.repeat(6) + "</div>";
      return;
    }
    if (state.view === "trash") {
      v.innerHTML = trashView();
    } else if (state.view === "incoming") {
      v.innerHTML = invitationsView();
    } else if (state.view === "shared-folder") {
      v.innerHTML = sharedFolderView();
    } else if (state.view === "shared") {
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
    if (["files", "recent", "starred", "search"].includes(state.view) && (state.files.length || state.folders.length)) v.insertAdjacentHTML('afterbegin', selectionBar());
    bindView(v);
    updateSelection(v);
  }

  async function loadThumbs() {
    const queue = [];
    for (const f of state.files) {
      const type = f.contentType || "";
      const isImage = f.category === "image" && /^image\/(png|jpe?g|gif|webp|bmp)(?:;|$)/i.test(type);
      const isVideo = f.category === "video" && /^video\//i.test(type);
      if ((!isImage && !isVideo) || thumbs.has(f.id) || f.size > 50 * 1024 * 1024) continue;
      thumbs.set(f.id, null);
      queue.push({ file: f, isVideo });
    }
    queue.sort((a, b) => a.file.size - b.file.size);
    async function worker() {
      while (queue.length) {
        const { file: f, isVideo } = queue.shift();
        try {
          const res = await api("/api/files/" + f.id + "/download?inline=true", { raw: true });
          const blob = await res.blob();
          const url = isVideo ? await videoThumbnail(blob) : URL.createObjectURL(blob);
          thumbs.set(f.id, url);
          const el = $('.file-card[data-file-id="' + f.id + '"] .thumb .ficon');
          if (el) el.outerHTML = '<img src="' + url + '" alt="">';
        } catch (e) { thumbs.delete(f.id); }
      }
    }
    await Promise.all(Array.from({ length: Math.min(3, queue.length) }, () => worker()));
  }

  function videoThumbnail(blob) {
    return new Promise((resolve, reject) => {
      const url = URL.createObjectURL(blob);
      const video = document.createElement("video");
      const cleanup = () => { video.removeAttribute("src"); video.load(); URL.revokeObjectURL(url); };
      video.preload = "auto";
      video.muted = true;
      video.onloadeddata = () => {
        try {
          const canvas = document.createElement("canvas");
          const scale = Math.min(1, 480 / video.videoWidth);
          canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
          canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
          canvas.getContext("2d").drawImage(video, 0, 0, canvas.width, canvas.height);
          canvas.toBlob((image) => {
            cleanup();
            if (image) resolve(URL.createObjectURL(image));
            else reject(new Error("Could not create video thumbnail"));
          }, "image/jpeg", 0.8);
        } catch (e) { cleanup(); reject(e); }
      };
      video.onerror = () => { cleanup(); reject(new Error("Could not load video thumbnail")); };
      video.src = url;
    });
  }

  const byId = (id) => state.files.find((f) => f.id === id);
  const folderById = (id) => state.folders.find((f) => f.id === id);

  function bindView(v) {
    $$('[data-select]', v).forEach((box) => {
      box.addEventListener('click', (e) => e.stopPropagation());
      box.addEventListener('change', () => { if (box.checked) selected.add(box.dataset.select); else selected.delete(box.dataset.select); updateSelection(v); });
    });
    const selectAll = $('[data-select-all]', v);
    if (selectAll) selectAll.addEventListener('change', () => {
      $$('[data-select]', v).forEach((b) => { if (selectAll.checked) selected.add(b.dataset.select); else selected.delete(b.dataset.select); });
      updateSelection(v);
    });
    const clearSelection = $('[data-clear-selection]', v);
    if (clearSelection) clearSelection.addEventListener('click', () => { selected.clear(); updateSelection(v); });
    const selectedDownload = $('[data-download-selected]', v);
    if (selectedDownload) selectedDownload.addEventListener('click', downloadSelectedSeparately);
    const selectedZip = $('[data-download-zip]', v);
    if (selectedZip) selectedZip.addEventListener('click', () => downloadZip(selectedIds()));
    $$('[data-trash-restore], [data-trash-purge]', v).forEach((b) => b.addEventListener('click', async () => {
      const restoring = !!b.dataset.trashRestore;
      const [type, id] = (b.dataset.trashRestore || b.dataset.trashPurge).split(':');
      if (!restoring && !(await confirmDialog('Delete permanently?', 'This item and its contents cannot be restored.', 'Delete permanently'))) return;
      try {
        await api('/api/trash/' + (type === 'folder' ? 'folders/' : 'files/') + id + (restoring ? '/restore' : ''), { method: restoring ? 'POST' : 'DELETE' });
        toast(restoring ? 'Restored' : 'Deleted permanently'); await load(); loadProfile();
      } catch (e) { toast(e.message, 'error'); }
    }));
    $$('[data-accept], [data-decline]', v).forEach((b) => b.addEventListener("click", async () => {
      const id = b.dataset.accept || b.dataset.decline;
      try {
        await api('/api/folder-invitations/' + id + (b.dataset.accept ? '/accept' : '/decline'), { method: 'POST' });
        await loadInvitations(); render();
        toast(b.dataset.accept ? 'Invitation accepted' : 'Invitation declined');
      } catch (e) { toast(e.message, 'error'); }
    }));
    $$('[data-open-share]', v).forEach((b) => b.addEventListener('click', () => {
      state.sharedFolder = { id: b.dataset.openShare, folderId: null, path: [] };
      go('shared-folder');
    }));
    $$('[data-shared-folder]', v).forEach((b) => b.addEventListener('click', () => {
      state.sharedFolder.path.push(state.sharedFolder.folderId);
      state.sharedFolder.folderId = b.dataset.sharedFolder;
      state.sharedFolder.contents = null;
      load();
    }));
    const back = $('[data-shared-back]', v);
    if (back) back.addEventListener('click', () => {
      if (!state.sharedFolder.path.length) return go('incoming');
      state.sharedFolder.folderId = state.sharedFolder.path.pop();
      state.sharedFolder.contents = null; load();
    });
    const sharedFolderDownload = $('[data-shared-folder-download]', v);
    if (sharedFolderDownload) sharedFolderDownload.addEventListener('click', () => {
      const s = state.sharedFolder;
      downloadArchive('/api/folder-invitations/' + s.id + '/zip' + (s.folderId ? '?folderId=' + s.folderId : ''), {}, s.contents.folderName + '.zip');
    });
    $$('[data-shared-download]', v).forEach((b) => b.addEventListener('click', async () => {
      const f = state.sharedFolder.contents.files.find((x) => x.id === b.dataset.sharedDownload);
      try {
        const res = await api('/api/folder-invitations/' + state.sharedFolder.id + '/files/' + f.id, { raw: true });
        const url = URL.createObjectURL(await res.blob());
        const a = document.createElement('a'); a.href = url; a.download = f.name; a.click();
        setTimeout(() => URL.revokeObjectURL(url), 10000);
      } catch (e) { toast(e.message, 'error'); }
    }));
    $$('[data-shared-preview]', v).forEach((b) => b.addEventListener('click', async () => {
      const f = state.sharedFolder.contents.files.find((x) => x.id === b.dataset.sharedPreview);
      if (f.size > 50 * 1024 * 1024) return toast('File is too large to preview; download it instead.', 'error');
      const m = modal({ title: f.name, wide: true, bodyClass: 'preview-body', body: '<div class="muted" style="padding:40px">Loading preview...</div>',
        onClose: () => { if (m.url) URL.revokeObjectURL(m.url); } });
      try {
        const res = await api('/api/folder-invitations/' + state.sharedFolder.id + '/files/' + f.id + '?inline=true', { raw: true });
        const blob = await res.blob();
        if (!m.isConnected) return;
        m.url = URL.createObjectURL(blob);
        const body = $('.modal-body', m), type = f.contentType || '';
        if (type.startsWith('image/')) body.innerHTML = '<img src="' + m.url + '" alt="">';
        else if (type.startsWith('video/')) body.innerHTML = '<video src="' + m.url + '" controls></video>';
        else if (type.startsWith('audio/')) body.innerHTML = '<audio src="' + m.url + '" controls></audio>';
        else body.innerHTML = '<iframe src="' + m.url + '" title="PDF"></iframe>';
      } catch (e) { if (m.isConnected) $('.modal-body', m).textContent = 'Could not load preview.'; }
    }));
    $$("[data-act=upload]", v).forEach((b) => b.addEventListener("click", pickFiles));
    $$("[data-folder-id]", v).forEach((el) => {
      el.addEventListener("click", (e) => { if (!e.target.closest("[data-menu-folder], [data-select]")) go("files", el.dataset.folderId); });
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
      el.addEventListener("click", (e) => { if (!e.target.closest("[data-menu-file], [data-select]")) preview(byId(el.dataset.fileId)); });
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
      { icon: "move", label: "Move to folder", run: () => moveDialog(f) }
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
      { icon: "download", label: "Download folder ZIP", run: () => downloadZip({ fileIds: [], folderIds: [f.id] }, f.name + '.zip') },
      { icon: "share", label: "Share folder", run: () => folderShareDialog(f) },
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

  function selectedIds() {
    const keys = Array.from(selected);
    return { fileIds: keys.filter((s) => s.startsWith('f:')).map((s) => s.slice(2)),
      folderIds: keys.filter((s) => s.startsWith('d:')).map((s) => s.slice(2)) };
  }

  async function downloadArchive(path, options, name) {
    try {
      toast('Preparing ZIP download...');
      const res = await api(path, Object.assign({ raw: true }, options));
      const url = URL.createObjectURL(await res.blob());
      const a = document.createElement('a'); a.href = url; a.download = name; a.click();
      setTimeout(() => URL.revokeObjectURL(url), 10000);
    } catch (e) { toast(e.message, 'error'); }
  }

  function downloadZip(selection, name) {
    return downloadArchive('/api/downloads/zip', { method: 'POST', body: selection }, name || 'Nimbus Drive.zip');
  }

  async function downloadSelectedSeparately() {
    const { fileIds, folderIds } = selectedIds();
    for (const id of fileIds) {
      const file = byId(id);
      if (file) await download(file);
    }
    for (const id of folderIds) {
      const folder = folderById(id);
      if (folder) await downloadZip({ fileIds: [], folderIds: [id] }, folder.name + '.zip');
    }
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

  function folderShareDialog(f) {
    const m = modal({
      title: 'Share folder “' + f.name + '”',
      body: '<p class="muted small">People can view and download this folder and its contents. Account invitations require acceptance.</p>' +
        '<form data-invite-form class="share-action"><input class="input" name="username" placeholder="Username" required maxlength="50" aria-label="Username">' +
        '<button class="btn primary" type="submit">Invite</button></form>' +
        '<div data-invitations></div><div class="share-action"><select class="input" data-expiry aria-label="Link expiration"><option value="">Link never expires</option><option value="1">1 hour</option><option value="24">24 hours</option><option value="168">7 days</option></select>' +
        '<button class="btn" data-create-folder-link>Create link</button></div><div data-folder-links></div>',
      foot: '<button class="btn" data-done>Done</button>'
    });
    $('[data-done]', m).addEventListener('click', () => m.close());
    async function refresh() {
      try {
        const [invites, links] = await Promise.all([api('/api/folders/' + f.id + '/invitations'), api('/api/folders/' + f.id + '/links')]);
        $('[data-invitations]', m).innerHTML = invites.length ? '<div class="section-title">People</div>' + invites.map((i) =>
          '<div class="shared-manage"><span>' + esc(i.recipient) + ' <span class="muted small">· ' + (i.status === 'PENDING' ? 'Pending' : 'Accepted') + '</span></span><button class="btn" data-revoke-invite="' + i.id + '">Remove</button></div>').join('') : '';
        $('[data-folder-links]', m).innerHTML = links.length ? '<div class="section-title">Links</div>' + links.map((l) =>
          '<div class="shared-manage"><span class="muted small">' + (l.expiresAt ? 'Expires ' + esc(fullDate(l.expiresAt)) : 'Never expires') + '</span><div><button class="btn" data-copy-folder-link="' + esc(l.token) + '">Copy</button> <button class="btn" data-revoke-folder-link="' + l.id + '">Revoke</button></div></div>').join('') : '';
        $$('[data-revoke-invite]', m).forEach((b) => b.addEventListener('click', async () => {
          try { await api('/api/folder-invitations/' + b.dataset.revokeInvite, { method: 'DELETE' }); await refresh(); toast('Access removed'); } catch (e) { toast(e.message, 'error'); }
        }));
        $$('[data-copy-folder-link]', m).forEach((b) => b.addEventListener('click', async () => {
          try { await N.copyText(location.origin + '/folder-share.html?t=' + encodeURIComponent(b.dataset.copyFolderLink)); toast('Link copied'); } catch (e) { toast(e.message, 'error'); }
        }));
        $$('[data-revoke-folder-link]', m).forEach((b) => b.addEventListener('click', async () => {
          try { await api('/api/folder-links/' + b.dataset.revokeFolderLink, { method: 'DELETE' }); await refresh(); toast('Link revoked'); } catch (e) { toast(e.message, 'error'); }
        }));
      } catch (e) { toast(e.message, 'error'); }
    }
    $('[data-invite-form]', m).addEventListener('submit', async (e) => {
      e.preventDefault();
      const input = $('[name=username]', m);
      try {
        await api('/api/folders/' + f.id + '/invitations', { method: 'POST', body: { username: input.value.trim() } });
        input.value = ''; await refresh(); toast('Invitation sent');
      } catch (err) { toast(err.message, 'error'); }
    });
    $('[data-create-folder-link]', m).addEventListener('click', async () => {
      try {
        const hours = $('[data-expiry]', m).value;
        const link = await api('/api/folders/' + f.id + '/links', { method: 'POST', body: { expiresInHours: hours ? +hours : null } });
        await refresh();
        try { await N.copyText(location.origin + '/folder-share.html?t=' + encodeURIComponent(link.token)); toast('Link created and copied'); }
        catch (e) { toast('Link created. Use Copy to copy it.'); }
      } catch (e) { toast(e.message, 'error'); }
    });
    refresh();
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
    if (!(await confirmDialog("Move to trash?", "“" + f.name + "” can be restored for 30 days. Its share links will be unavailable while it is in trash.", "Move to trash"))) return;
    try {
      await api("/api/files/" + f.id, { method: "DELETE" });
      toast("Moved to trash: " + f.name);
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
    if (!(await confirmDialog("Move folder to trash?", "“" + f.name + "” and its contents can be restored for 30 days. Sharing will be unavailable while in trash.", "Move to trash"))) return;
    try {
      await api("/api/folders/" + f.id, { method: "DELETE" });
      toast("Folder moved to trash");
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
  loadInvitations();
  setInterval(async () => {
    await loadInvitations();
    if (state.view === 'incoming') render();
  }, 30000);
  fromHash();
})();
