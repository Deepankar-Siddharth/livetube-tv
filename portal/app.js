"use strict";

const API_ORIGIN = "https://api.github.com";
const CATALOGUE_PATH = "data/channels.json";
const LOCAL_CATALOGUE_URLS = ["../data/channels.json", "data/channels.json"];
const TOKEN_STORAGE_KEY = "livetubetv.githubToken";
const REPOSITORY_STORAGE_KEY = "livetubetv.repository";
const SCHEMA_VERSION = 2;
const LEGACY_SCHEMA_VERSION = 1;
const MAX_FILE_BYTES = 2 * 1024 * 1024;
const ROOT_KEYS = ["channels", "data_version", "schema_version", "updated_at"];
const LEGACY_CHANNEL_KEYS = [
  "category",
  "enabled",
  "id",
  "live_url",
  "logo",
  "name",
  "sort_order",
  "youtube_handle",
];
const CHANNEL_KEYS = [
  "category",
  "enabled",
  "id",
  "language",
  "live_url",
  "logo",
  "name",
  "region",
  "sort_order",
  "subcategory",
  "youtube_handle",
];
const CATEGORY_DEFINITIONS = [
  { name: "News", subcategories: ["Hindi News", "English & Global News", "Business & Market", "International News", "Debate & Digital Media"] },
  { name: "Regional", subcategories: ["Uttar Pradesh & Uttarakhand", "Bihar & Jharkhand", "Punjab & Haryana", "Bhojpuri", "Marathi", "Bengali", "Telugu", "Tamil", "Kannada", "Malayalam", "Gujarati", "Odia & North-East"] },
  { name: "Devotional", subcategories: ["Hindu Devotional", "Live Darshan & Aarti", "Gurbani & Sikh", "Islamic", "Christian"] },
  { name: "Kids & Family", subcategories: ["Cartoons & Animation", "Rhymes & Nursery", "Kids Learning"] },
  { name: "Knowledge", subcategories: ["Science & Technology", "Space", "History & Nature", "Travel", "Documentaries"] },
  { name: "Music & Entertainment", subcategories: ["Bollywood & Retro", "Indie & Pop", "Bhakti & Classical", "Youth & Entertainment"] },
  { name: "Sports & Live", subcategories: ["Sports News", "Cricket", "Fitness & Yoga", "Gaming & Esports", "Parliament & Governance", "Weather & Live Events"] },
];
const VALID_SUBCATEGORIES = new Map(CATEGORY_DEFINITIONS.map((category) => [category.name, new Set(category.subcategories)]));
const LEGACY_CLASSIFICATION = {
  "English News": ["News", "English & Global News", "English", "National"],
  "National Hindi News": ["News", "Hindi News", "Hindi", "National"],
  "Business News": ["News", "Business & Market", "Hindi", "National"],
  "Devotional & Spiritual": ["Devotional", "Hindu Devotional", "Hindi", "National"],
  Entertainment: ["Music & Entertainment", "Youth & Entertainment", "Hindi", "National"],
  Sports: ["Sports & Live", "Sports News", "Hindi", "National"],
  "Regional News": ["Regional", "Uttar Pradesh & Uttarakhand", "Hindi", "North India"],
  Documentary: ["Knowledge", "Documentaries", "English", "National"],
  Music: ["Music & Entertainment", "Bollywood & Retro", "Hindi", "National"],
  Other: ["News", "Hindi News", "Hindi", "National"],
};
const ID_PATTERN = /^[a-z0-9][a-z0-9_-]{0,63}$/;
const HANDLE_PATTERN = /^@[A-Za-z0-9._-]{1,100}$/;
const UTC_TIMESTAMP_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/;
const SHA_PATTERN = /^[a-f0-9]{40}$/;
const CONTROL_CHARACTER_PATTERN = /[\u0000-\u001f\u007f]/;

const state = {
  channels: [],
  dataVersion: 1,
  updatedAt: "1970-01-01T00:00:00Z",
  baseSha: "",
  baseConfigurationKey: "",
  baseSerialized: "",
  hasRemoteBase: false,
  conflict: false,
  editIndex: -1,
  busy: false,
};

const elements = {
  statTotal: document.querySelector("#statTotal"),
  statCategories: document.querySelector("#statCategories"),
  statEnabled: document.querySelector("#statEnabled"),
  statDisabled: document.querySelector("#statDisabled"),
  statUpdated: document.querySelector("#statUpdated"),
  notice: document.querySelector("#notice"),
  noticeText: document.querySelector("#noticeText"),
  dismissNotice: document.querySelector("#dismissNotice"),
  owner: document.querySelector("#owner"),
  repo: document.querySelector("#repo"),
  branch: document.querySelector("#branch"),
  token: document.querySelector("#token"),
  clearToken: document.querySelector("#clearToken"),
  fetchRemote: document.querySelector("#fetchRemote"),
  saveRemote: document.querySelector("#saveRemote"),
  exportCatalogue: document.querySelector("#exportCatalogue"),
  importCatalogue: document.querySelector("#importCatalogue"),
  importFile: document.querySelector("#importFile"),
  form: document.querySelector("#channelForm"),
  editIndex: document.querySelector("#editIndex"),
  editorHeading: document.querySelector("#editorHeading"),
  editorMode: document.querySelector("#editorMode"),
  channelName: document.querySelector("#channelName"),
  channelId: document.querySelector("#channelId"),
  channelCategory: document.querySelector("#channelCategory"),
  channelSubcategory: document.querySelector("#channelSubcategory"),
  channelLanguage: document.querySelector("#channelLanguage"),
  channelRegion: document.querySelector("#channelRegion"),
  channelEnabled: document.querySelector("#channelEnabled"),
  channelHandle: document.querySelector("#channelHandle"),
  channelLogo: document.querySelector("#channelLogo"),
  previewChannel: document.querySelector("#previewChannel"),
  canonicalPreview: document.querySelector("#canonicalPreview"),
  cancelEdit: document.querySelector("#cancelEdit"),
  formError: document.querySelector("#formError"),
  channelRows: document.querySelector("#channelRows"),
  channelCount: document.querySelector("#channelCount"),
  documentMeta: document.querySelector("#documentMeta"),
  emptyState: document.querySelector("#emptyState"),
  search: document.querySelector("#search"),
  categoryFilter: document.querySelector("#categoryFilter"),
  connectionDot: document.querySelector("#connectionDot"),
  connectionState: document.querySelector("#connectionState"),
};

function isPlainObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function sameKeys(object, expected) {
  const actual = Object.keys(object).sort();
  const wanted = [...expected].sort();
  return actual.length === wanted.length && actual.every((key, index) => key === wanted[index]);
}

function requireTrimmedString(value, field, maximum) {
  if (typeof value !== "string") throw new Error(`${field} must be a string.`);
  if (!value.length || value !== value.trim() || value.length > maximum) {
    throw new Error(`${field} must be non-empty, trimmed, and at most ${maximum} characters.`);
  }
  if (CONTROL_CHARACTER_PATTERN.test(value)) {
    throw new Error(`${field} contains a control character.`);
  }
  return value;
}

function requirePositiveInteger(value, field) {
  if (!Number.isSafeInteger(value) || value < 1) {
    throw new Error(`${field} must be a positive safe integer.`);
  }
  return value;
}

function validateUtcTimestamp(value, field) {
  const timestamp = requireTrimmedString(value, field, 40);
  const match = UTC_TIMESTAMP_PATTERN.exec(timestamp);
  if (!match) throw new Error(`${field} must be an ISO-8601 UTC timestamp.`);
  const year = Number(timestamp.slice(0, 4));
  const month = Number(timestamp.slice(5, 7));
  const day = Number(timestamp.slice(8, 10));
  const hour = Number(timestamp.slice(11, 13));
  const minute = Number(timestamp.slice(14, 16));
  const second = Number(timestamp.slice(17, 19));
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
  if (
    month < 1 || month > 12 || day < 1 || day > daysInMonth ||
    hour > 23 || minute > 59 || second > 59 || Number.isNaN(Date.parse(timestamp))
  ) {
    throw new Error(`${field} must be a real ISO-8601 UTC timestamp.`);
  }
  return timestamp;
}

function validateHttpsLogo(value, field) {
  const logo = requireTrimmedString(value, field, 2048);
  let parsed;
  try {
    parsed = new URL(logo);
  } catch (error) {
    throw new Error(`${field} must be a valid HTTPS URL.`);
  }
  if (parsed.protocol !== "https:" || !parsed.hostname || parsed.username || parsed.password || parsed.hash) {
    throw new Error(`${field} must be an HTTPS URL without credentials or a fragment.`);
  }
  return logo;
}

function validateCatalogue(value) {
  if (!isPlainObject(value) || !sameKeys(value, ROOT_KEYS)) {
    throw new Error("The catalogue root must contain only schema_version, data_version, updated_at, and channels.");
  }
  if (![LEGACY_SCHEMA_VERSION, SCHEMA_VERSION].includes(value.schema_version)) {
    throw new Error(`Unsupported schema_version; expected 1 or ${SCHEMA_VERSION}.`);
  }
  const sourceSchemaVersion = value.schema_version;
  const dataVersion = requirePositiveInteger(value.data_version, "data_version");
  const updatedAt = validateUtcTimestamp(value.updated_at, "updated_at");
  if (!Array.isArray(value.channels) || value.channels.length === 0) {
    throw new Error("channels must be a non-empty array.");
  }

  const seen = new Map();
  const channels = value.channels.map((candidate, index) => {
    const prefix = `channels[${index}]`;
    const expectedKeys = sourceSchemaVersion === LEGACY_SCHEMA_VERSION ? LEGACY_CHANNEL_KEYS : CHANNEL_KEYS;
    if (!isPlainObject(candidate) || !sameKeys(candidate, expectedKeys)) {
      throw new Error(`${prefix} has missing or unknown fields.`);
    }

    const id = requireTrimmedString(candidate.id, `${prefix}.id`, 64);
    if (!ID_PATTERN.test(id)) throw new Error(`${prefix}.id has an invalid format.`);
    const name = requireTrimmedString(candidate.name, `${prefix}.name`, 160);
    const storedCategory = requireTrimmedString(candidate.category, `${prefix}.category`, 64);
    let category;
    let subcategory;
    let language;
    let region;
    if (sourceSchemaVersion === LEGACY_SCHEMA_VERSION) {
      const migrated = LEGACY_CLASSIFICATION[storedCategory];
      if (!migrated) throw new Error(`${prefix}.category is not a supported legacy category.`);
      [category, subcategory, language, region] = migrated;
    } else {
      category = storedCategory;
      const validSubcategories = VALID_SUBCATEGORIES.get(category);
      if (!validSubcategories) throw new Error(`${prefix}.category is not supported.`);
      subcategory = requireTrimmedString(candidate.subcategory, `${prefix}.subcategory`, 96);
      if (!validSubcategories.has(subcategory)) {
        throw new Error(`${prefix}.subcategory does not belong to ${category}.`);
      }
      language = requireTrimmedString(candidate.language, `${prefix}.language`, 64);
      region = requireTrimmedString(candidate.region, `${prefix}.region`, 64);
    }

    const logo = validateHttpsLogo(candidate.logo, `${prefix}.logo`);
    const youtubeHandle = requireTrimmedString(candidate.youtube_handle, `${prefix}.youtube_handle`, 101);
    if (!HANDLE_PATTERN.test(youtubeHandle)) {
      throw new Error(`${prefix}.youtube_handle must be a valid normalized YouTube handle.`);
    }
    const liveUrl = requireTrimmedString(candidate.live_url, `${prefix}.live_url`, 256);
    const canonicalUrl = `https://www.youtube.com/${youtubeHandle}/live`;
    if (liveUrl !== canonicalUrl) throw new Error(`${prefix}.live_url is not canonical; expected ${canonicalUrl}.`);
    try {
      const parsed = new URL(liveUrl);
      if (parsed.protocol !== "https:" || parsed.hostname !== "www.youtube.com" || parsed.search || parsed.hash) {
        throw new Error("non-canonical parts");
      }
    } catch (error) {
      throw new Error(`${prefix}.live_url is not a valid YouTube URL.`);
    }
    if (typeof candidate.enabled !== "boolean") throw new Error(`${prefix}.enabled must be a boolean.`);
    const sortOrder = requirePositiveInteger(candidate.sort_order, `${prefix}.sort_order`);

    for (const [field, normalizedValue] of [
      ["id", id],
      ["youtube_handle", youtubeHandle],
      ["live_url", liveUrl],
      ["sort_order", String(sortOrder)],
    ]) {
      const folded = normalizedValue.toLocaleLowerCase("en-US");
      const uniquenessKey = `${field}\u0000${folded}`;
      const previous = seen.get(uniquenessKey);
      if (previous !== undefined) {
        throw new Error(`${prefix}.${field} duplicates channels[${previous}].${field}.`);
      }
      seen.set(uniquenessKey, index);
    }

    return {
      id,
      name,
      category,
      subcategory,
      language,
      region,
      logo,
      youtube_handle: youtubeHandle,
      live_url: liveUrl,
      enabled: candidate.enabled,
      sort_order: sortOrder,
    };
  });

  return { schema_version: SCHEMA_VERSION, data_version: dataVersion, updated_at: updatedAt, channels };
}

function currentDocument() {
  return {
    schema_version: SCHEMA_VERSION,
    data_version: state.dataVersion,
    updated_at: state.updatedAt,
    channels: state.channels,
  };
}

function serializeCatalogue() {
  return `${JSON.stringify(currentDocument(), null, 2)}\n`;
}

function adoptDocument(document) {
  state.channels = document.channels;
  state.dataVersion = document.data_version;
  state.updatedAt = document.updated_at;
}

function nextMetadata() {
  if (!Number.isSafeInteger(state.dataVersion) || state.dataVersion >= Number.MAX_SAFE_INTEGER) {
    throw new Error("data_version has reached the safe integer limit.");
  }
  return { data_version: state.dataVersion + 1, updated_at: new Date().toISOString().replace(/\.\d{3}Z$/, "Z") };
}

function bytesToBase64(bytes) {
  let binary = "";
  const chunkSize = 0x8000;
  for (let index = 0; index < bytes.length; index += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(index, index + chunkSize));
  }
  return btoa(binary);
}

function utf8ToBase64(value) {
  return bytesToBase64(new TextEncoder().encode(value));
}

function base64ToUtf8(value) {
  if (typeof value !== "string" || !value) throw new Error("GitHub returned an empty file.");
  const binary = atob(value.replace(/\s/g, ""));
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
  return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
}

function readSessionRepository() {
  try {
    const value = JSON.parse(sessionStorage.getItem(REPOSITORY_STORAGE_KEY) || "null");
    return isPlainObject(value) ? value : null;
  } catch (error) {
    return null;
  }
}

function saveSessionRepository() {
  try {
    sessionStorage.setItem(
      REPOSITORY_STORAGE_KEY,
      JSON.stringify({
        owner: elements.owner.value.trim(),
        repo: elements.repo.value.trim(),
        branch: elements.branch.value.trim(),
      }),
    );
  } catch (error) {
    // The portal remains usable when browser storage is disabled.
  }
}

function readStoredToken() {
  try {
    return localStorage.getItem(TOKEN_STORAGE_KEY) || "";
  } catch (error) {
    return "";
  }
}

function storeToken(value) {
  try {
    if (value) localStorage.setItem(TOKEN_STORAGE_KEY, value);
    else localStorage.removeItem(TOKEN_STORAGE_KEY);
  } catch (error) {
    showNotice("This browser blocked localStorage. The token remains available only on this page.", "warning");
  }
}

function inferGitHubRepository() {
  const segments = window.location.pathname.split("/").filter(Boolean);
  if (window.location.hostname.endsWith("github.io") && segments.length >= 2) {
    return { owner: segments[0], repo: segments[1] };
  }
  return null;
}

function initializeRepositoryFields() {
  const values = readSessionRepository() || inferGitHubRepository() || { owner: "", repo: "", branch: "main" };
  elements.owner.value = values.owner || "";
  elements.repo.value = values.repo || "";
  elements.branch.value = values.branch || "main";
}

function repositoryConfiguration() {
  const owner = elements.owner.value.trim();
  const repo = elements.repo.value.trim();
  const branch = elements.branch.value.trim();
  if (!/^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$/.test(owner)) {
    throw new Error("Enter a valid GitHub owner or organisation name.");
  }
  if (!/^[A-Za-z0-9_.-]{1,100}$/.test(repo) || repo === "." || repo === "..") {
    throw new Error("Enter a valid repository name.");
  }
  if (
    !branch || branch.length > 255 || branch.startsWith("-") || branch.endsWith("/") ||
    branch.includes("..") || CONTROL_CHARACTER_PATTERN.test(branch) || /[\s~^:?*[\\]/.test(branch)
  ) {
    throw new Error("Enter a valid branch name.");
  }
  return { owner, repo, branch };
}

function configurationKey(configuration) {
  return JSON.stringify([configuration.owner, configuration.repo, configuration.branch]);
}

function contentEndpoint(configuration, includeBranch) {
  const endpoint = `${API_ORIGIN}/repos/${encodeURIComponent(configuration.owner)}/${encodeURIComponent(configuration.repo)}/contents/${CATALOGUE_PATH}`;
  return includeBranch ? `${endpoint}?ref=${encodeURIComponent(configuration.branch)}` : endpoint;
}

async function githubRequest(url, { method = "GET", token = "", body } = {}) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 20000);
  const headers = { Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28" };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";

  try {
    const response = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store",
      credentials: "omit",
      referrerPolicy: "no-referrer",
      signal: controller.signal,
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
      const message = isPlainObject(payload) && typeof payload.message === "string" ? payload.message : `HTTP ${response.status}`;
      const error = new Error(message);
      error.status = response.status;
      error.response = payload;
      throw error;
    }
    return payload;
  } catch (error) {
    if (error.name === "AbortError") throw new Error("GitHub did not respond within 20 seconds.");
    throw error;
  } finally {
    window.clearTimeout(timeout);
  }
}

function setBusy(busy) {
  state.busy = busy;
  elements.fetchRemote.disabled = busy;
  elements.importCatalogue.disabled = busy;
  elements.exportCatalogue.disabled = busy;
  updateActionState();
}

function setConnectionStatus(label, kind = "") {
  elements.connectionState.textContent = label;
  elements.connectionDot.className = `status-dot${kind ? ` ${kind}` : ""}`;
}

function showNotice(message, kind = "success", dismissible = true) {
  elements.noticeText.textContent = message;
  elements.notice.className = `notice${kind === "error" ? " error" : kind === "warning" ? " warning" : ""}`;
  elements.dismissNotice.hidden = !dismissible;
  elements.notice.hidden = false;
}

function hideNotice() {
  elements.notice.hidden = true;
  elements.noticeText.textContent = "";
}

function catalogueHasLocalChanges() {
  return state.hasRemoteBase && serializeCatalogue() !== state.baseSerialized;
}

function updateActionState() {
  const dirty = catalogueHasLocalChanges();
  elements.saveRemote.disabled = state.busy || !state.hasRemoteBase || !dirty || state.conflict;
  elements.saveRemote.textContent = state.conflict ? "Resolve conflict before saving" : "Save catalogue to GitHub";
  if (!state.hasRemoteBase) setConnectionStatus(state.busy ? "Connecting…" : "Local preview", state.busy ? "dirty" : "");
  else if (state.conflict) setConnectionStatus("GitHub changed — reload required", "error");
  else if (dirty) setConnectionStatus("Unsaved local changes", "dirty");
  else setConnectionStatus("Connected and current", "connected");
}

function slugify(value) {
  return value
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLocaleLowerCase("en-US")
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .replace(/_{2,}/g, "_")
    .slice(0, 64)
    .replace(/_+$/g, "");
}

function normalizeHandleInput(value) {
  const input = value.trim();
  if (!input) throw new Error("Enter a YouTube handle.");
  if (HANDLE_PATTERN.test(input)) return input;
  if (input.includes("/") || /^[a-z]+:/i.test(input)) {
    let url;
    try {
      url = new URL(input);
    } catch (error) {
      throw new Error("The YouTube URL is invalid.");
    }
    if (url.protocol !== "https:" || url.hostname !== "www.youtube.com" || url.search || url.hash) {
      throw new Error("Only an exact https://www.youtube.com/@handle/live URL is accepted.");
    }
    const match = url.pathname.match(/^\/(@[A-Za-z0-9._-]{1,100})\/live$/);
    if (!match) throw new Error("Only a YouTube handle live URL is accepted.");
    return match[1];
  }
  const handle = input.startsWith("@") ? input : `@${input}`;
  if (!HANDLE_PATTERN.test(handle)) throw new Error("Use a YouTube handle such as @example.");
  return handle;
}

function updateCanonicalPreview() {
  try {
    const handle = normalizeHandleInput(elements.channelHandle.value);
    elements.canonicalPreview.href = `https://www.youtube.com/${handle}/live`;
    elements.canonicalPreview.hidden = false;
  } catch (error) {
    elements.canonicalPreview.removeAttribute("href");
    elements.canonicalPreview.hidden = true;
  }
}

function populateCategoryEditorOptions() {
  const fragment = document.createDocumentFragment();
  for (const category of CATEGORY_DEFINITIONS) {
    const option = document.createElement("option");
    option.value = category.name;
    option.textContent = category.name;
    fragment.append(option);
  }
  elements.channelCategory.replaceChildren(fragment);
}

function populateCategoryFilter() {
  const selected = elements.categoryFilter.value;
  const fragment = document.createDocumentFragment();
  const all = document.createElement("option");
  all.value = "";
  all.textContent = "All categories";
  fragment.append(all);
  for (const category of CATEGORY_DEFINITIONS) {
    const option = document.createElement("option");
    option.value = category.name;
    option.textContent = category.name;
    fragment.append(option);
  }
  elements.categoryFilter.replaceChildren(fragment);
  if (CATEGORY_DEFINITIONS.some((category) => category.name === selected)) {
    elements.categoryFilter.value = selected;
  }
}

function updateSubcategoryOptions(selected = "") {
  const subcategories = VALID_SUBCATEGORIES.get(elements.channelCategory.value) || new Set();
  const fragment = document.createDocumentFragment();
  for (const name of subcategories) {
    const option = document.createElement("option");
    option.value = name;
    option.textContent = name;
    fragment.append(option);
  }
  elements.channelSubcategory.replaceChildren(fragment);
  if (subcategories.has(selected)) elements.channelSubcategory.value = selected;
}

function makeActionButton(label, className, action) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = `row-button${className ? ` ${className}` : ""}`;
  button.textContent = label;
  button.setAttribute("aria-label", label === "↑" ? "Move channel up" : label === "↓" ? "Move channel down" : label);
  button.addEventListener("click", action);
  return button;
}

function renderDashboard() {
  const categories = new Set(state.channels.map((channel) => channel.category));
  const enabled = state.channels.filter((channel) => channel.enabled).length;
  elements.statTotal.textContent = String(state.channels.length);
  elements.statCategories.textContent = String(categories.size);
  elements.statEnabled.textContent = String(enabled);
  elements.statDisabled.textContent = String(state.channels.length - enabled);
  const parsed = Date.parse(state.updatedAt);
  elements.statUpdated.textContent = Number.isNaN(parsed)
    ? state.updatedAt
    : new Date(parsed).toLocaleString();
}

function renderTable() {
  const search = elements.search.value.trim().toLocaleLowerCase("en-US");
  const category = elements.categoryFilter.value;
  const filtered = state.channels.filter((channel) => {
    const matchesCategory = !category || channel.category === category;
    const haystack = `${channel.name} ${channel.id} ${channel.youtube_handle} ${channel.category} ${channel.subcategory} ${channel.language} ${channel.region}`.toLocaleLowerCase("en-US");
    return matchesCategory && (!search || haystack.includes(search));
  });

  const fragment = document.createDocumentFragment();
  for (const channel of filtered) {
    const index = state.channels.indexOf(channel);
    const row = document.createElement("tr");

    const channelCell = document.createElement("td");
    const identity = document.createElement("div");
    identity.className = "channel-cell";
    const image = document.createElement("img");
    image.className = "channel-logo";
    image.src = channel.logo;
    image.alt = "";
    image.loading = "lazy";
    image.referrerPolicy = "no-referrer";
    image.addEventListener("error", () => { image.hidden = true; });
    const identityText = document.createElement("div");
    const name = document.createElement("span");
    name.className = "channel-name";
    name.textContent = channel.name;
    const id = document.createElement("span");
    id.className = "channel-id";
    id.textContent = `#${channel.sort_order} · ${channel.id}`;
    identityText.append(name, id);
    identity.append(image, identityText);
    channelCell.append(identity);

    const categoryCell = document.createElement("td");
    const badge = document.createElement("span");
    badge.className = "category-badge";
    badge.textContent = channel.category;
    badge.title = `${channel.category} · ${channel.subcategory}`;
    const classification = document.createElement("small");
    classification.className = "channel-id";
    classification.textContent = `${channel.subcategory} · ${channel.language} · ${channel.region}`;
    categoryCell.append(badge, document.createElement("br"), classification);

    const youtubeCell = document.createElement("td");
    const youtubeLink = document.createElement("a");
    youtubeLink.className = "handle-link";
    youtubeLink.href = channel.live_url;
    youtubeLink.target = "_blank";
    youtubeLink.rel = "noopener noreferrer";
    youtubeLink.textContent = channel.youtube_handle;
    youtubeCell.append(youtubeLink);

    const statusCell = document.createElement("td");
    const status = document.createElement("span");
    status.className = `status-badge${channel.enabled ? " enabled" : " disabled"}`;
    status.textContent = channel.enabled ? "Enabled" : "Disabled";
    statusCell.append(status);

    const actionsCell = document.createElement("td");
    const actions = document.createElement("div");
    actions.className = "row-actions";
    actions.append(
      makeActionButton("↑", "", () => moveChannel(index, -1)),
      makeActionButton("↓", "", () => moveChannel(index, 1)),
      makeActionButton("Edit", "", () => editChannel(index)),
      makeActionButton("Delete", "delete", () => deleteChannel(index)),
    );
    actionsCell.append(actions);
    row.append(channelCell, categoryCell, youtubeCell, statusCell, actionsCell);
    fragment.append(row);
  }

  elements.channelRows.replaceChildren(fragment);
  elements.channelCount.textContent = String(state.channels.length);
  elements.documentMeta.textContent = `data v${state.dataVersion}`;
  elements.emptyState.hidden = filtered.length !== 0;
  populateCategoryFilter();
  renderDashboard();
  updateActionState();
}

function resetForm() {
  state.editIndex = -1;
  elements.form.reset();
  elements.editIndex.value = "-1";
  elements.channelId.value = "";
  elements.channelCategory.value = CATEGORY_DEFINITIONS[0].name;
  updateSubcategoryOptions();
  elements.channelLanguage.value = "";
  elements.channelRegion.value = "";
  elements.channelEnabled.value = "true";
  elements.editorHeading.textContent = "Add a channel";
  elements.editorMode.textContent = "New";
  elements.form.querySelector('button[type="submit"]').textContent = "Add to catalogue";
  elements.formError.hidden = true;
  elements.formError.textContent = "";
  elements.canonicalPreview.hidden = true;
  elements.canonicalPreview.removeAttribute("href");
}

function editChannel(index) {
  const channel = state.channels[index];
  if (!channel) return;
  state.editIndex = index;
  elements.editIndex.value = String(index);
  elements.channelName.value = channel.name;
  elements.channelId.value = channel.id;
  elements.channelCategory.value = channel.category;
  updateSubcategoryOptions(channel.subcategory);
  elements.channelLanguage.value = channel.language;
  elements.channelRegion.value = channel.region;
  elements.channelEnabled.value = String(channel.enabled);
  elements.channelHandle.value = channel.youtube_handle;
  elements.channelLogo.value = channel.logo;
  elements.editorHeading.textContent = `Edit ${channel.name}`;
  elements.editorMode.textContent = "Editing";
  elements.form.querySelector('button[type="submit"]').textContent = "Update channel";
  elements.formError.hidden = true;
  updateCanonicalPreview();
  document.querySelector("#editorPanel").scrollIntoView({ behavior: "smooth", block: "start" });
  elements.channelName.focus();
}

function moveChannel(index, direction) {
  const target = index + direction;
  if (index < 0 || index >= state.channels.length || target < 0 || target >= state.channels.length) return;
  const nextChannels = state.channels.map((channel) => ({ ...channel }));
  [nextChannels[index], nextChannels[target]] = [nextChannels[target], nextChannels[index]];
  nextChannels.forEach((channel, position) => { channel.sort_order = position + 1; });
  try {
    const metadata = nextMetadata();
    const validated = validateCatalogue({ schema_version: SCHEMA_VERSION, ...metadata, channels: nextChannels });
    adoptDocument(validated);
    renderTable();
    showNotice("Channel order changed. Save the catalogue to publish the new order.", "warning");
  } catch (error) {
    showNotice(`Could not reorder channels: ${error.message}`, "error");
  }
}

function deleteChannel(index) {
  const channel = state.channels[index];
  if (!channel) return;
  if (!window.confirm(`Delete “${channel.name}” from this local catalogue?`)) return;
  const nextChannels = state.channels.filter((_, channelIndex) => channelIndex !== index);
  try {
    const metadata = nextMetadata();
    const validated = validateCatalogue({ schema_version: SCHEMA_VERSION, ...metadata, channels: nextChannels });
    adoptDocument(validated);
    if (state.editIndex === index) resetForm();
    else if (state.editIndex > index) state.editIndex -= 1;
    renderTable();
    showNotice(`${channel.name} was removed from the local copy. Save to GitHub to publish the change.`, "warning");
  } catch (error) {
    showNotice(`Could not delete channel: ${error.message}`, "error");
  }
}

function validateLogoInput(value) {
  return validateHttpsLogo(value.trim(), "Logo");
}

function submitChannel(event) {
  event.preventDefault();
  elements.formError.hidden = true;
  try {
    const name = requireTrimmedString(elements.channelName.value, "Name", 160);
    const category = requireTrimmedString(elements.channelCategory.value, "Category", 64);
    const subcategory = requireTrimmedString(elements.channelSubcategory.value, "Subcategory", 96);
    const validSubcategories = VALID_SUBCATEGORIES.get(category);
    if (!validSubcategories || !validSubcategories.has(subcategory)) {
      throw new Error("Choose a subcategory that belongs to the selected category.");
    }
    const language = requireTrimmedString(elements.channelLanguage.value, "Language", 64);
    const region = requireTrimmedString(elements.channelRegion.value, "Region", 64);
    const enabled = elements.channelEnabled.value === "true";
    const youtubeHandle = normalizeHandleInput(elements.channelHandle.value);
    const logo = validateLogoInput(elements.channelLogo.value);
    const existing = state.editIndex >= 0 ? state.channels[state.editIndex] : null;
    const id = existing ? existing.id : slugify(name);
    if (!id) throw new Error("Name must contain at least one Latin letter or digit for its ID.");
    const nextChannels = [...state.channels];
    const candidate = {
      id,
      name,
      category,
      subcategory,
      language,
      region,
      logo,
      youtube_handle: youtubeHandle,
      live_url: `https://www.youtube.com/${youtubeHandle}/live`,
      enabled,
      sort_order: existing ? existing.sort_order : Math.max(0, ...state.channels.map((channel) => channel.sort_order)) + 1,
    };
    if (existing) nextChannels[state.editIndex] = candidate;
    else nextChannels.push(candidate);
    const metadata = nextMetadata();
    const validated = validateCatalogue({ schema_version: SCHEMA_VERSION, ...metadata, channels: nextChannels });
    adoptDocument(validated);
    resetForm();
    renderTable();
    showNotice(`${name} ${existing ? "updated" : "added"} in the local catalogue.`);
  } catch (error) {
    elements.formError.textContent = error.message;
    elements.formError.hidden = false;
  }
}

async function loadPublishedCatalogueFallback() {
  let configuration;
  try {
    configuration = repositoryConfiguration();
  } catch (error) {
    throw new Error("Configure the GitHub repository before loading the catalogue.");
  }
  const url = `https://raw.githubusercontent.com/${encodeURIComponent(configuration.owner)}/${encodeURIComponent(configuration.repo)}/${encodeURIComponent(configuration.branch)}/${CATALOGUE_PATH}`;
  const response = await fetch(url, { cache: "no-cache", credentials: "omit", referrerPolicy: "no-referrer" });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return validateCatalogue(await response.json());
}

async function loadLocalCatalogue() {
  for (const url of LOCAL_CATALOGUE_URLS) {
    try {
      const response = await fetch(url, { cache: "no-cache", credentials: "omit" });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const document = validateCatalogue(await response.json());
      adoptDocument(document);
      renderTable();
      showNotice(`Loaded ${document.channels.length} channels from the bundled catalogue.`);
      return;
    } catch (_) {
      // Try the next local path before falling back to the configured GitHub raw URL.
    }
  }

  try {
    const document = await loadPublishedCatalogueFallback();
    adoptDocument(document);
    renderTable();
    showNotice(`Loaded ${document.channels.length} channels from the public GitHub catalogue.`);
  } catch (remoteError) {
    state.channels = [];
    renderTable();
    showNotice(`Could not load the catalogue: ${remoteError.message}`, "error");
  }
}

async function fetchRemoteCatalogue() {
  hideNotice();
  try {
    const configuration = repositoryConfiguration();
    saveSessionRepository();
    setBusy(true);
    setConnectionStatus("Fetching…", "dirty");
    const payload = await githubRequest(contentEndpoint(configuration, true), { token: elements.token.value });
    if (!isPlainObject(payload) || payload.type !== "file" || payload.encoding !== "base64" || !SHA_PATTERN.test(payload.sha)) {
      throw new Error("GitHub did not return a versioned file blob with a valid SHA.");
    }
    const document = validateCatalogue(JSON.parse(base64ToUtf8(payload.content)));
    if (catalogueHasLocalChanges() && !window.confirm("Replace unsaved local changes with the latest GitHub version?")) return;
    adoptDocument(document);
    state.baseSha = payload.sha;
    state.baseConfigurationKey = configurationKey(configuration);
    state.baseSerialized = serializeCatalogue();
    state.hasRemoteBase = true;
    state.conflict = false;
    resetForm();
    renderTable();
    showNotice(`Fetched ${document.channels.length} channels at commit ${payload.sha.slice(0, 7)}.`);
  } catch (error) {
    showNotice(`Fetch failed: ${error.message}`, "error");
  } finally {
    setBusy(false);
  }
}

function isShaConflict(error) {
  return error.status === 409 || (error.status === 422 && /sha|blob|concurrent|conflict/i.test(error.message));
}

async function saveRemoteCatalogue() {
  hideNotice();
  try {
    const configuration = repositoryConfiguration();
    const document = validateCatalogue(currentDocument());
    if (!state.hasRemoteBase || !SHA_PATTERN.test(state.baseSha)) {
      throw new Error("Fetch the repository file before saving so GitHub can enforce a conflict check.");
    }
    if (state.baseConfigurationKey !== configurationKey(configuration)) {
      throw new Error("The repository or branch changed. Fetch GitHub again before saving.");
    }
    const token = elements.token.value;
    if (!token) throw new Error("A fine-grained token with contents write permission is required to save.");
    saveSessionRepository();
    setBusy(true);
    const content = `${JSON.stringify(document, null, 2)}\n`;
    const payload = await githubRequest(contentEndpoint(configuration, false), {
      method: "PUT",
      token,
      body: {
        message: `Update channel catalogue to data v${document.data_version}`,
        content: utf8ToBase64(content),
        sha: state.baseSha,
        branch: configuration.branch,
      },
    });
    if (!isPlainObject(payload) || !isPlainObject(payload.content) || !SHA_PATTERN.test(payload.content.sha)) {
      throw new Error("GitHub saved the file but returned an invalid blob response.");
    }
    adoptDocument(document);
    state.baseSha = payload.content.sha;
    state.baseSerialized = `${JSON.stringify(document, null, 2)}\n`;
    state.hasRemoteBase = true;
    state.conflict = false;
    renderTable();
    showNotice("Catalogue committed. The generate-m3u workflow will publish the updated playlist.");
  } catch (error) {
    if (isShaConflict(error)) {
      state.conflict = true;
      showNotice(
        "GitHub reports a concurrent edit. Your local work is preserved, but nothing was overwritten. Export it if needed, then fetch GitHub again and reconcile manually.",
        "error",
        false,
      );
    } else {
      showNotice(`Save failed: ${error.message}`, "error");
    }
  } finally {
    setBusy(false);
  }
}

function exportCatalogue() {
  try {
    const document = validateCatalogue(currentDocument());
    const blob = new Blob([`${JSON.stringify(document, null, 2)}\n`], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const anchor = window.document.createElement("a");
    anchor.href = url;
    anchor.download = "channels.json";
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    showNotice("Exported a token-free catalogue JSON file.");
  } catch (error) {
    showNotice(`Export failed: ${error.message}`, "error");
  }
}

async function importCatalogueFile(file) {
  if (!file) return;
  hideNotice();
  if (file.size > MAX_FILE_BYTES) {
    showNotice("The selected JSON file is larger than 2 MiB.", "error");
    return;
  }
  try {
    const document = validateCatalogue(JSON.parse(await file.text()));
    if (state.channels.length && !window.confirm("Replace the current local catalogue with this JSON file?")) return;
    adoptDocument(document);
    resetForm();
    renderTable();
    showNotice(`Imported ${document.channels.length} channels. Fetch GitHub before saving to establish a conflict guard.`, "warning");
  } catch (error) {
    showNotice(`Import failed: ${error.message}`, "error");
  } finally {
    elements.importFile.value = "";
  }
}

function initializeEvents() {
  elements.dismissNotice.addEventListener("click", hideNotice);
  for (const input of [elements.owner, elements.repo, elements.branch]) input.addEventListener("change", saveSessionRepository);
  elements.token.addEventListener("change", () => storeToken(elements.token.value));
  elements.clearToken.addEventListener("click", () => {
    elements.token.value = "";
    storeToken("");
    showNotice("The token was removed from this browser's localStorage.", "warning");
    elements.token.focus();
  });
  elements.fetchRemote.addEventListener("click", fetchRemoteCatalogue);
  elements.saveRemote.addEventListener("click", saveRemoteCatalogue);
  elements.exportCatalogue.addEventListener("click", exportCatalogue);
  elements.importCatalogue.addEventListener("click", () => elements.importFile.click());
  elements.importFile.addEventListener("change", () => {
    const files = elements.importFile.files;
    importCatalogueFile(files && files.length ? files[0] : null);
  });
  elements.channelName.addEventListener("input", () => {
    if (state.editIndex < 0) elements.channelId.value = slugify(elements.channelName.value);
  });
  elements.channelHandle.addEventListener("input", updateCanonicalPreview);
  elements.channelCategory.addEventListener("change", () => updateSubcategoryOptions());
  elements.previewChannel.addEventListener("click", () => {
    try {
      const handle = normalizeHandleInput(elements.channelHandle.value);
      window.open(`https://www.youtube.com/${handle}/live`, "_blank", "noopener,noreferrer");
    } catch (error) {
      elements.formError.textContent = error.message;
      elements.formError.hidden = false;
    }
  });
  elements.form.addEventListener("submit", submitChannel);
  elements.cancelEdit.addEventListener("click", resetForm);
  elements.search.addEventListener("input", renderTable);
  elements.categoryFilter.addEventListener("change", renderTable);
}

async function initialize() {
  initializeRepositoryFields();
  elements.token.value = readStoredToken();
  populateCategoryEditorOptions();
  initializeEvents();
  resetForm();
  renderTable();
  await loadLocalCatalogue();
}

initialize().catch((error) => showNotice(`Portal initialization failed: ${error.message}`, "error", false));
