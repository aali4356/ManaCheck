"use strict";
const $ = (id) => document.getElementById(id);
const page = document.body.dataset.page;
const deckId = document.body.dataset.deckId;
const state = {
  deck: null,
  analysis: null,
  dirty: false,
  run: null,
  preview: null,
};
function node(tag, cls, text) {
  const element = document.createElement(tag);
  if (cls) element.className = cls;
  if (text !== undefined) element.textContent = text;
  return element;
}
function icon(name) {
  const element = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  element.classList.add("icon");
  element.setAttribute("aria-hidden", "true");
  const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
  use.setAttribute("href", "/icons.svg#" + name);
  element.append(use);
  return element;
}
function initShell() {
  if (page === "library")
    document
      .querySelector('.nav-item[href="/"]')
      .setAttribute("aria-current", "page");
  document.querySelectorAll("img[data-card-image]").forEach((image) => {
    const hide = () => {
      image.hidden = true;
    };
    image.addEventListener("error", hide);
    if (image.complete && !image.naturalWidth) hide();
  });
  if (page === "library")
    document
      .querySelectorAll('a[href="#new-deck"], a[href="/#new-deck"]')
      .forEach((link) => {
        link.addEventListener("click", () => $("new-name").focus());
      });
}
function feedback(message, error = false) {
  $("feedback").textContent = message;
  $("feedback").classList.toggle("error", error);
}
async function api(path, method = "GET", body) {
  const options = { method, headers: { Accept: "application/json" } };
  if (body !== undefined) {
    options.headers["Content-Type"] = "application/json";
    options.body = JSON.stringify(body);
  }
  const response = await fetch("/api" + path, options);
  const result = await response.json();
  if (!response.ok) throw new Error(result.detail || "The request failed.");
  return result;
}
async function busy(button, work) {
  if (button.dataset.busy) return;
  const wasDisabled = button.disabled;
  button.dataset.busy = "true";
  button.setAttribute("aria-busy", "true");
  button.disabled = true;
  try {
    await work();
  } catch (error) {
    feedback(error.message, true);
  } finally {
    delete button.dataset.busy;
    button.removeAttribute("aria-busy");
    button.disabled = wasDisabled;
    controls();
  }
}
function controls() {
  if (page !== "editor" || !state.deck) return;
  $("save-deck").disabled = !state.dirty || !!$("save-deck").dataset.busy;
  $("generate-suggestions").disabled =
    state.dirty || !!$("generate-suggestions").dataset.busy;
  $("save-state").textContent = state.dirty
    ? "Unsaved changes · save to update analysis"
    : "All changes saved";
  document.querySelectorAll("[data-apply]").forEach((button) => {
    button.disabled = state.dirty || !!button.dataset.busy;
  });
  $("explain-ai").disabled =
    !state.run ||
    state.run.explanationStatus !== "rules" ||
    !!$("explain-ai").dataset.busy;
}
function dirty() {
  state.dirty = true;
  controls();
}
function wireSearch(inputId, resultsId, commanderMode, select) {
  const input = $(inputId),
    results = $(resultsId);
  let timer,
    serial = 0;
  input.addEventListener("input", () => {
    clearTimeout(timer);
    const current = ++serial;
    const query = input.value.trim();
    if (!query) {
      results.replaceChildren();
      return;
    }
    timer = setTimeout(async () => {
      try {
        const params = new URLSearchParams({
          q: query,
          commanders: String(commanderMode),
        });
        if (!commanderMode && state.deck)
          params.set("commanderId", state.deck.commander.oracleId);
        const data = await api("/cards?" + params);
        if (current !== serial) return;
        results.replaceChildren();
        if (!data.cards.length)
          results.append(
            node("p", "empty-note", "No matching cards in this catalog."),
          );
        for (const card of data.cards) {
          const button = node("button", "search-result");
          button.type = "button";
          button.dataset.action = commanderMode
            ? "select-commander"
            : "add-card";
          button.dataset.oracleId = card.oracleId;
          const text = node("span");
          text.append(
            node("span", "", card.name),
            node("span", "quiet", card.typeLine),
          );
          button.append(
            text,
            node("span", "result-action", commanderMode ? "Choose" : "Add"),
          );
          button.addEventListener("click", () => {
            select(card);
            results.replaceChildren();
            input.value = "";
            ++serial;
          });
          results.append(button);
        }
      } catch (error) {
        if (current === serial) feedback(error.message, true);
      }
    }, 180);
  });
}
function total() {
  return 1 + state.deck.entries.reduce((sum, entry) => sum + entry.quantity, 0);
}
function renderDeck() {
  const deck = state.deck;
  $("commander-name").textContent = deck.commander.name;
  $("commander-type").textContent = deck.commander.typeLine;
  $("revision-label").textContent = "Revision " + deck.revision;
  $("deck-theme").value = deck.theme;
  $("deck-total").textContent = total() + " / 100";
  const image = $("commander-image"),
    uri = deck.commander.faces[0]?.imageUri;
  if (uri && uri.startsWith("https://cards.scryfall.io/")) {
    image.src = uri;
    image.alt = "Full card image of " + deck.commander.name;
    image.hidden = false;
    image.onerror = () => {
      image.hidden = true;
    };
  } else image.hidden = true;
  const container = $("deck-entries");
  container.replaceChildren();
  if (!deck.entries.length)
    container.append(node("p", "empty-note", "Add cards or paste a decklist."));
  for (const entry of [...deck.entries].sort((a, b) =>
    a.card.name.localeCompare(b.card.name),
  )) {
    const row = node("div", "deck-entry");
    row.dataset.cardId = entry.card.oracleId;
    const info = node("div", "card-info");
    info.append(
      node("span", "card-name", entry.card.name),
      node("span", "quiet", entry.card.typeLine),
    );
    const mana = node("span", "mana-cost", String(entry.card.manaValue));
    mana.title = "Mana value";
    const quantity = node("input", "quantity-input");
    quantity.type = "number";
    quantity.min = "1";
    quantity.max = "1000";
    quantity.value = entry.quantity;
    quantity.setAttribute("aria-label", "Quantity for " + entry.card.name);
    quantity.addEventListener("change", () => {
      const value = Number(quantity.value);
      if (!Number.isInteger(value) || value < 1 || value > 1000) {
        quantity.value = entry.quantity;
        feedback("Choose a whole quantity from 1 to 1,000.", true);
        return;
      }
      entry.quantity = value;
      dirty();
      $("deck-total").textContent = total() + " / 100";
    });
    const remove = node("button", "remove-card");
    remove.append(icon("close"));
    remove.type = "button";
    remove.setAttribute("aria-label", "Remove " + entry.card.name);
    remove.addEventListener("click", () => {
      state.deck.entries = state.deck.entries.filter(
        (e) => e.card.oracleId !== entry.card.oracleId,
      );
      dirty();
      renderDeck();
    });
    row.append(info, mana, quantity, remove);
    container.append(row);
  }
  controls();
}
function renderAnalysis() {
  const a = state.analysis,
    box = $("validation");
  box.replaceChildren();
  const count = node("p", "validation-total", String(a.totalCards));
  count.append(node("span", "", " / 100 cards"));
  box.append(count);
  const important = a.errors.filter((e) => e.code !== "CARD_COUNT");
  box.append(
    node(
      "p",
      "validation-state",
      a.complete
        ? "Construction checks passed"
        : important.length
          ? "Review " + important.length + " construction findings"
          : "Draft · " + (100 - a.totalCards) + " cards to go",
    ),
  );
  if (important.length) {
    const list = node("ul", "findings");
    for (const finding of important)
      list.append(node("li", "", finding.message));
    box.append(list);
  }
  if (a.warnings.length) {
    const detail = node("details", "compact-details");
    detail.append(node("summary", "", "Notes (" + a.warnings.length + ")"));
    const list = node("ul", "findings warnings");
    a.warnings.forEach((f) => list.append(node("li", "", f.message)));
    detail.append(list);
    box.append(detail);
  }
  const roles = $("role-coverage");
  roles.replaceChildren();
  for (const [role, target] of Object.entries(a.targets)) {
    const row = node("div", "role-row"),
      value = a.roles[role] || 0;
    row.append(
      node("span", "", role === "interaction" ? "Interaction" : role),
      node("span", "quiet", value + " / " + target),
    );
    const bar = node("progress");
    bar.max = target;
    bar.value = value;
    bar.setAttribute(
      "aria-label",
      role + ": " + value + " of heuristic target " + target,
    );
    row.append(bar);
    roles.append(row);
  }
  const chart = $("mana-curve");
  chart.replaceChildren();
  const max = Math.max(1, ...Object.values(a.manaCurve));
  for (const bucket of ["0", "1", "2", "3", "4", "5", "6", "7+"]) {
    const value = a.manaCurve[bucket] || 0,
      column = node("div", "curve-column");
    const bar = node("meter");
    bar.min = 0;
    bar.max = max;
    bar.value = value;
    bar.setAttribute(
      "aria-label",
      "Mana value " + bucket + ": " + value + " cards",
    );
    column.append(
      node("span", "", String(value)),
      bar,
      node("span", "", bucket),
    );
    chart.append(column);
  }
  const colors = $("color-distribution");
  colors.replaceChildren();
  const names = {
    W: "White",
    U: "Blue",
    B: "Black",
    R: "Red",
    G: "Green",
    C: "Colorless",
  };
  for (const [color, value] of Object.entries(a.colors))
    if (value)
      colors.append(node("span", "color-chip", names[color] + " " + value));
  $("catalog-timestamp").textContent = a.catalogUpdatedAt
    ? "Catalog snapshot: " + new Date(a.catalogUpdatedAt).toLocaleString()
    : "No active catalog.";
}
async function refreshAnalysis() {
  state.analysis = await api("/decks/" + deckId + "/analysis");
  renderAnalysis();
}
function clearSuggestions() {
  state.run = null;
  $("suggestions").replaceChildren(
    node("p", "empty-note", "Deck changed. Find new suggestions."),
  );
  $("explanation-controls").hidden = true;
}
function renderSuggestions() {
  const run = state.run,
    box = $("suggestions");
  box.replaceChildren();
  if (!run.suggestions.length)
    box.append(
      node(
        "p",
        "empty-note",
        "No useful changes found for this deck and catalog.",
      ),
    );
  for (const suggestion of run.suggestions) {
    const card = node("article", "suggestion-card");
    card.dataset.suggestion = suggestion.id;
    card.append(node("h3", "", suggestion.add.name));
    if (suggestion.cut)
      card.append(node("p", "cut-note", "Remove 1 " + suggestion.cut.name));
    card.append(node("p", "reason", suggestion.ruleExplanation));
    if (suggestion.aiExplanation)
      card.append(
        node("p", "ai-note", "AI explanation: " + suggestion.aiExplanation),
      );
    const apply = node(
      "button",
      "button secondary",
      suggestion.cut ? "Apply swap" : "Add to deck",
    );
    apply.type = "button";
    apply.dataset.apply = suggestion.id;
    apply.addEventListener("click", () =>
      busy(apply, async () => {
        state.deck = await api(
          "/recommendations/" + run.id + "/apply",
          "POST",
          {
            suggestionId: suggestion.id,
            expectedRevision: state.deck.revision,
          },
        );
        state.dirty = false;
        $("revision-note").value = "";
        renderDeck();
        clearSuggestions();
        await refreshAnalysis();
        feedback("Change saved as revision " + state.deck.revision + ".");
      }),
    );
    card.append(apply);
    box.append(card);
  }
  $("explanation-controls").hidden = !run.suggestions.length;
  const labels = {
    rules: "Rule explanations",
    pending: "AI request pending",
    haiku: "Haiku explanations · review claims",
    fallback: "Fallback · rule explanations",
  };
  $("explanation-status").textContent =
    labels[run.explanationStatus] +
    (run.explanationDetail ? ". " + run.explanationDetail : "");
  $("model-usage").textContent = JSON.stringify(
    {
      model: run.model || "No provider request",
      usage: Object.keys(run.usage).length ? run.usage : "Usage not reported",
    },
    null,
    2,
  );
  controls();
}
async function initLibrary() {
  const list = $("deck-list"),
    rows = [...list.children];
  const updateDecks = () => {
    const query = $("deck-filter").value.trim().toLocaleLowerCase();
    const sort = $("deck-sort").value;
    const ordered = [...rows].sort((a, b) => {
      const tie = a.dataset.deckName.localeCompare(b.dataset.deckName);
      if (sort === "name") return tie;
      if (sort === "cards")
        return Number(b.dataset.total) - Number(a.dataset.total) || tie;
      return (
        Date.parse(b.dataset.updated) - Date.parse(a.dataset.updated) || tie
      );
    });
    let matches = 0;
    for (const row of ordered) {
      row.hidden = !`${row.dataset.deckName} ${row.dataset.commander}`
        .toLocaleLowerCase()
        .includes(query);
      if (!row.hidden) matches++;
    }
    list.replaceChildren(...ordered);
    $("deck-count").textContent =
      matches === rows.length ? String(matches) : `${matches} / ${rows.length}`;
    $("deck-empty").hidden = matches > 0;
    $("deck-empty").textContent = rows.length
      ? "No matching decks. Clear the search to see all decks."
      : "No decks yet. Choose a commander to create one.";
  };
  $("deck-filter").addEventListener("input", updateDecks);
  $("deck-sort").addEventListener("change", updateDecks);
  document.querySelectorAll("[data-date]").forEach((label) => {
    const date = new Date(label.dataset.date);
    label.textContent =
      "Updated " +
      date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
    label.title = date.toLocaleString();
  });
  let commander = null;
  wireSearch("commander-search", "commander-results", true, (card) => {
    commander = card;
    $("selected-commander").textContent = card.name;
    $("create-deck").disabled = false;
  });
  $("create-form").addEventListener("submit", (event) => {
    event.preventDefault();
    if (!commander) return;
    busy($("create-deck"), async () => {
      const deck = await api("/decks", "POST", {
        name: $("new-name").value.trim(),
        commanderOracleId: commander.oracleId,
        theme: $("new-theme").value,
      });
      window.location.assign("/decks/" + deck.id);
    });
  });
  const catalog = await api("/catalog/imports");
  $("refresh-label").textContent =
    catalog.activeSource === "scryfall-oracle"
      ? "Refresh catalog"
      : "Load full catalog";
  const sourceLabel =
    catalog.activeSource === "offline-demo"
      ? "Offline sample"
      : catalog.activeSource === "scryfall-oracle"
        ? "Full Oracle catalog"
        : "Local catalog";
  $("catalog-source").textContent =
    sourceLabel +
    " · " +
    (catalog.sourceUpdatedAt
      ? "snapshot " + new Date(catalog.sourceUpdatedAt).toLocaleString()
      : "No snapshot loaded.");
  $("refresh-catalog").addEventListener("click", () =>
    busy($("refresh-catalog"), async () => {
      const started = await api("/catalog/imports", "POST");
      feedback("Importing catalog. You can keep editing decks.");
      let result;
      do {
        await new Promise((resolve) => setTimeout(resolve, 1000));
        result = await api("/catalog/imports/" + started.id);
      } while (result.status === "RUNNING");
      if (result.status === "FAILED") throw new Error(result.error);
      const latest = await api("/catalog/imports");
      $("catalog-summary").textContent =
        latest.activeCards.toLocaleString() + " cards";
      $("refresh-label").textContent = "Refresh catalog";
      $("catalog-source").textContent =
        "Full Oracle catalog · snapshot " +
        new Date(latest.sourceUpdatedAt).toLocaleString();
      feedback(
        result.status === "UNCHANGED"
          ? "The catalog is already up to date."
          : "Catalog updated.",
      );
    }),
  );
}
async function initEditor() {
  state.deck = await api("/decks/" + deckId);
  renderDeck();
  await refreshAnalysis();
  wireSearch("card-search", "card-results", false, (card) => {
    const existing = state.deck.entries.find(
      (e) => e.card.oracleId === card.oracleId,
    );
    if (existing) existing.quantity++;
    else state.deck.entries.push({ card, quantity: 1 });
    dirty();
    renderDeck();
  });
  wireSearch(
    "editor-commander-search",
    "editor-commander-results",
    true,
    (card) => {
      state.deck.commander = card;
      state.preview = null;
      $("use-import").hidden = true;
      dirty();
      renderDeck();
    },
  );
  $("deck-theme").addEventListener("change", () => {
    state.deck.theme = $("deck-theme").value;
    dirty();
  });
  $("revision-note").addEventListener("input", dirty);
  $("save-deck").addEventListener("click", () =>
    busy($("save-deck"), async () => {
      state.deck = await api("/decks/" + deckId + "/revisions", "POST", {
        expectedRevision: state.deck.revision,
        commanderOracleId: state.deck.commander.oracleId,
        theme: state.deck.theme,
        note: $("revision-note").value,
        entries: state.deck.entries.map((e) => ({
          oracleId: e.card.oracleId,
          quantity: e.quantity,
        })),
      });
      state.dirty = false;
      $("revision-note").value = "";
      renderDeck();
      clearSuggestions();
      await refreshAnalysis();
      feedback("Saved revision " + state.deck.revision + ".");
    }),
  );
  $("import-text").addEventListener("input", () => {
    state.preview = null;
    $("use-import").hidden = true;
    $("import-preview").replaceChildren();
  });
  $("preview-import").addEventListener("click", () =>
    busy($("preview-import"), async () => {
      const preview = await api(
        "/decks/" + deckId + "/imports/preview",
        "POST",
        {
          text: $("import-text").value,
          commanderOracleId: state.deck.commander.oracleId,
        },
      );
      state.preview = preview;
      const box = $("import-preview");
      box.replaceChildren();
      if (preview.errors.length) {
        preview.errors.forEach((error) =>
          box.append(node("p", "error", error)),
        );
        $("use-import").hidden = true;
      } else {
        box.append(
          node(
            "p",
            "",
            preview.lines.length +
              " lines resolved. Replaces the draft; save to keep it.",
          ),
        );
        $("use-import").hidden = false;
      }
    }),
  );
  $("use-import").addEventListener("click", () => {
    if (!state.preview || state.preview.errors.length) return;
    const found = new Map(
      state.preview.lines.map((line) => [line.card.oracleId, line.card]),
    );
    state.deck.entries = state.preview.entries.map((entry) => ({
      card: found.get(entry.oracleId),
      quantity: entry.quantity,
    }));
    dirty();
    renderDeck();
    feedback("Decklist imported. Save to keep these changes.");
  });
  $("generate-suggestions").addEventListener("click", () =>
    busy($("generate-suggestions"), async () => {
      state.run = await api("/decks/" + deckId + "/recommendations", "POST");
      renderSuggestions();
      feedback("Suggestions validated for this revision and catalog.");
    }),
  );
  $("explain-ai").addEventListener("click", () =>
    busy($("explain-ai"), async () => {
      const id = state.run.id,
        result = await api("/recommendations/" + id + "/explanations", "POST");
      if (state.run?.id === id) {
        state.run = result;
        renderSuggestions();
      }
    }),
  );
  window.addEventListener("beforeunload", (event) => {
    if (state.dirty) {
      event.preventDefault();
      event.returnValue = "";
    }
  });
}
async function initCompare() {
  const from = $("compare-from"),
    to = $("compare-to");
  const params = new URLSearchParams(window.location.search);
  const values = [...to.options].map((o) => o.value);
  to.value = values.includes(params.get("to")) ? params.get("to") : values[0];
  from.value = values.includes(params.get("from"))
    ? params.get("from")
    : values[Math.min(1, values.length - 1)];
  let serial = 0;
  async function compare() {
    const current = ++serial;
    try {
      const [diff, earlier, later] = await Promise.all([
        api(
          "/decks/" +
            deckId +
            "/revisions/diff?from=" +
            from.value +
            "&to=" +
            to.value,
        ),
        api("/decks/" + deckId + "/revisions/" + from.value),
        api("/decks/" + deckId + "/revisions/" + to.value),
      ]);
      if (current !== serial) return;
      $("from-note").textContent = earlier.note || "No revision note.";
      $("to-note").textContent = later.note || "No revision note.";
      $("theme-diff").textContent =
        "Theme: " + earlier.theme + " → " + later.theme;
      const body = $("diff-entries");
      body.replaceChildren();
      $("diff-empty").hidden = diff.length > 0;
      for (const entry of diff) {
        const row = node("tr");
        row.append(
          node("td", "", entry.name),
          node("td", "", String(entry.beforeQuantity)),
          node("td", "", String(entry.afterQuantity)),
          node(
            "td",
            entry.delta > 0 ? "delta-plus" : "delta-minus",
            (entry.delta > 0 ? "+" : "") + entry.delta,
          ),
        );
        body.append(row);
      }
      history.replaceState(null, "", "?from=" + from.value + "&to=" + to.value);
    } catch (error) {
      feedback(error.message, true);
    }
  }
  from.addEventListener("change", compare);
  to.addEventListener("change", compare);
  await compare();
}
(async () => {
  try {
    initShell();
    if (page === "library") await initLibrary();
    if (page === "editor") await initEditor();
    if (page === "compare") await initCompare();
    document.body.dataset.ready = "true";
  } catch (error) {
    document.body.dataset.ready = "error";
    feedback(error.message, true);
  }
})();
