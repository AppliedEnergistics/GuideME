import "./index.css";
import "tippy.js/dist/tippy.css";
import tippy from "tippy.js";

// All tooltips, including those of game scenes, use the look of Minecraft tooltips (see tooltip.css)
tippy.setDefaultProps({
  theme: "minecraft",
  arrow: false,
  animation: false,
});

/**
 * The root of the website. This is determined once on load, since the location changes while navigating, but the
 * relative path to the root on the body element does not.
 */
const siteRoot = new URL(
  document.body.dataset.pathToRoot || "./",
  location.href,
);

function isInternalLink(link: URL): boolean {
  return (
    link.origin === siteRoot.origin &&
    link.pathname.startsWith(siteRoot.pathname)
  );
}

/**
 * Attributes that contain URLs. The asset prefix of scenes is a URL to the root of the website.
 */
const URL_ATTRIBUTES = [
  "href",
  "src",
  "data-scene-src",
  "data-scene-asset-prefix",
];

/**
 * Pages use URLs relative to themselves. Since we move content between pages when navigating, we make all URLs
 * absolute, including those in templates.
 */
function resolveRelativeUrls(container: ParentNode, baseUrl: string) {
  for (const attribute of URL_ATTRIBUTES) {
    container.querySelectorAll(`[${attribute}]`).forEach((element) => {
      const value = element.getAttribute(attribute);
      if (value === null || value.startsWith("#")) {
        return; // Skip anchor-only links
      }

      try {
        element.setAttribute(attribute, new URL(value, baseUrl).href);
      } catch (e) {
        console.warn("Invalid URL in %o:", element, value);
      }
    });
  }

  container.querySelectorAll("[srcset]").forEach((element) => {
    const srcset = element.getAttribute("srcset") ?? "";
    element.setAttribute(
      "srcset",
      srcset
        .split(",")
        .map((candidate) => {
          const [url = "", ...descriptors] = candidate.trim().split(/\s+/);
          return [new URL(url, baseUrl).href, ...descriptors].join(" ");
        })
        .join(", "),
    );
  });

  container.querySelectorAll("template").forEach((template) => {
    resolveRelativeUrls(template.content, baseUrl);
  });
}

/**
 * Marks the link to the current page as active in the navigation bar and expands its parents.
 */
function updateActiveNavigationLink(pageUrl: string) {
  const navbar = document.querySelector(".navbar");
  if (!navbar) {
    return;
  }
  navbar
    .querySelectorAll("a.active")
    .forEach((link) => link.classList.remove("active"));
  for (const link of navbar.querySelectorAll<HTMLAnchorElement>("a[href]")) {
    if (link.href === pageUrl) {
      link.classList.add("active");
      for (
        let details = link.closest("details");
        details;
        details = details.parentElement?.closest("details") ?? null
      ) {
        details.open = true;
      }
    }
  }
}

async function handleNavigationAsync(
  targetUrl: URL,
  pushState: boolean = true,
) {
  const fetchUrl = new URL(targetUrl);
  fetchUrl.hash = "";

  console.info("Fetching %s", fetchUrl);
  const response = await fetch(fetchUrl, {
    mode: "same-origin",
  });
  if (!response.ok) {
    throw new Error(
      "Failed to fetch " + fetchUrl + ": HTTP Status " + response.status,
    );
  }

  const domParser = new DOMParser();
  const newDocument = domParser.parseFromString(
    await response.text(),
    "text/html",
  );
  const newPageContent = newDocument.querySelector("#page-content");
  if (!newPageContent) {
    throw new Error("Fetched page does not include page content");
  }
  const currentPageContent = document.querySelector("#page-content");
  if (!currentPageContent) {
    return;
  }

  // Use response.url to follow redirects
  const pageUrl = response.url;
  resolveRelativeUrls(newPageContent, pageUrl);
  document.adoptNode(newPageContent);
  currentPageContent.replaceWith(newPageContent);
  document.title = newDocument.title;

  const newPageTitle = newDocument.querySelector(".page-title");
  const currentPageTitle = document.querySelector(".page-title");
  if (newPageTitle && currentPageTitle) {
    currentPageTitle.innerHTML = newPageTitle.innerHTML;
  }

  fixupPageContent(newPageContent);
  updateActiveNavigationLink(pageUrl);

  if (pushState) {
    history.pushState({ url: pageUrl }, "", pageUrl + targetUrl.hash);
  }

  if (targetUrl.hash) {
    document
      .getElementById(decodeURIComponent(targetUrl.hash.substring(1)))
      ?.scrollIntoView();
  } else {
    newPageContent.parentElement?.scrollTo(0, 0);
  }
}

function handleNavigation(targetUrl: URL, pushState: boolean = true) {
  handleNavigationAsync(targetUrl, pushState).catch((err) => {
    console.error(`Navigating to ${targetUrl} failed: ${err}`);
    window.location.href = targetUrl.toString();
  });
}

function hookNavigation() {
  document.addEventListener(
    "click",
    (e) => {
      if (e.target instanceof Element) {
        const closestLink = e.target?.closest("a");
        if (closestLink && closestLink.href) {
          // Expand the navbar as needed
          closestLink.closest("summary")?.click();

          const targetUrl = new URL(closestLink.href);
          if (
            targetUrl.host === location.host &&
            targetUrl.pathname === location.pathname
          ) {
            return; // Let anchor navigation handle it
          }

          if (isInternalLink(targetUrl)) {
            console.log("Clicked internal link %s", targetUrl);
            e.preventDefault();
            handleNavigation(targetUrl);
          }
        }
      }
    },
    {
      capture: true,
    },
  );

  // Remember the initial page so that navigating back to it works
  history.replaceState({ url: location.href }, "");

  // Handle browser back/forward buttons
  window.addEventListener("popstate", function (e) {
    if (e.state && e.state.url) {
      handleNavigation(new URL(e.state.url), false);
    }
  });
}

function fixupPageContent(root: Element) {
  setupTooltips(root);

  for (const gameSceneEl of root.querySelectorAll("img.game-scene")) {
    import("./model-viewer/modelViewer.ts")
      .then((module) => {
        const { setupGameScene } = module;
        setupGameScene(gameSceneEl as HTMLElement).catch((err) => {
          console.error(
            "Failed to set up game scene @ %o: %s",
            gameSceneEl,
            err,
          );
        });
      })
      .catch((err) => {
        console.error("Failed to load module viewer scripts.", err);
      });
  }
}

function cycleChildren(container: Element) {
  const current = container.querySelector(".current");
  current?.classList.remove("current");
  const nextEl = current?.nextElementSibling ?? container.firstElementChild;
  nextEl?.classList.toggle("current", true);
}

function setupCyclingIngredients() {
  setInterval(() => {
    for (const ingredientBox of document.querySelectorAll(".cycling")) {
      // We run 1s after the page load, and should cycle immediately to the next element
      if (!ingredientBox.classList.contains("is-cycling")) {
        ingredientBox.classList.add("is-cycling");
        ingredientBox
          .querySelector(":first-child")
          ?.classList.toggle("current", true);
      }
      cycleChildren(ingredientBox);
    }
  }, 1000);
}

/**
 * Configures the "burger menu" button to toggle the expanded CSS class on main.
 * This is only used on mobile if there isn't enough space to show the menu bar
 * continuously.
 */
function setupMenuBarToggle() {
  const mainElement = document.querySelector("body > main");
  mainElement
    ?.querySelector(".navbar-burger")
    ?.addEventListener("click", (e) => {
      e.preventDefault();
      mainElement?.classList.toggle("menu-expanded");
    });
}

function setupTooltips(root: Element) {
  tippy(root.querySelectorAll(".minecraft-tooltip"), {
    content(reference: Element) {
      if (!(reference instanceof HTMLElement)) {
        return "";
      }

      const textContent = reference.dataset.tooltipText;
      if (textContent) {
        return textContent;
      }

      const id = reference.dataset.template;
      if (!id) {
        console.warn(
          "Found element %o marked as tooltip without template.",
          reference,
        );
        return "";
      }
      const template = document.getElementById(id);
      if (!template) {
        console.warn(
          "Found element %o with tooltip from template, which is missing.",
          reference,
        );
        return "";
      }
      return template.innerHTML;
    },
    allowHTML: true,
    inlinePositioning: true,
  });
}

const MAX_SEARCH_RESULTS = 20;

/**
 * Appends text to the element, highlighting occurrences of the given terms.
 */
function appendHighlighted(
  element: HTMLElement,
  text: string,
  terms: string[],
) {
  const escapedTerms = terms
    .filter((term) => term.length > 0)
    .map((term) => term.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
  if (escapedTerms.length === 0) {
    element.append(text);
    return;
  }
  const pattern = new RegExp(`(${escapedTerms.join("|")})`, "gi");
  text.split(pattern).forEach((part, index) => {
    // Matches are at odd indices, since the pattern is a capturing group
    if (index % 2 === 1) {
      const mark = document.createElement("mark");
      mark.append(part);
      element.append(mark);
    } else {
      element.append(part);
    }
  });
}

/**
 * Adds a search field to the navigation bar, which loads the search index on first use.
 */
function setupSearch() {
  const slot = document.querySelector(".search-slot");
  if (!slot) {
    return;
  }

  const input = document.createElement("input");
  input.type = "search";
  input.className = "search-input";
  input.placeholder = "Search";
  input.setAttribute("aria-label", "Search the guide");
  const resultList = document.createElement("ul");
  resultList.className = "search-results";
  resultList.hidden = true;
  slot.append(input, resultList);

  let searchPromise: Promise<import("./search.ts").GuideSearch> | undefined;
  const loadSearch = () => {
    searchPromise ??= import("./search.ts").then((module) =>
      module.GuideSearch.load(new URL("search-index.json", siteRoot).href),
    );
    return searchPromise;
  };

  const hideResults = () => {
    resultList.hidden = true;
  };

  const updateResults = async () => {
    const query = input.value.trim();
    if (!query) {
      hideResults();
      return;
    }

    let hits;
    try {
      hits = (await loadSearch()).search(query, MAX_SEARCH_RESULTS);
    } catch (err) {
      console.error("Failed to search: %s", err);
      return;
    }
    if (input.value.trim() !== query) {
      return; // The query changed while the search was loading
    }

    resultList.replaceChildren();
    for (const hit of hits) {
      const link = document.createElement("a");
      link.href = new URL(hit.url, siteRoot).href;
      const title = document.createElement("span");
      title.className = "title";
      appendHighlighted(title, hit.title, hit.terms);
      const snippet = document.createElement("span");
      snippet.className = "snippet";
      appendHighlighted(snippet, hit.snippet, hit.terms);
      link.append(title, snippet);
      const item = document.createElement("li");
      item.append(link);
      resultList.append(item);
    }
    if (hits.length === 0) {
      const item = document.createElement("li");
      item.className = "no-results";
      item.append("No results");
      resultList.append(item);
    }
    resultList.hidden = false;
  };

  input.addEventListener("focus", () => {
    loadSearch().catch((err) =>
      console.error("Failed to load search index: %s", err),
    );
    if (input.value.trim()) {
      resultList.hidden = false;
    }
  });
  input.addEventListener("input", () => void updateResults());
  input.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      input.value = "";
      hideResults();
    } else if (e.key === "Enter") {
      resultList.querySelector("a")?.click();
    } else if (e.key === "ArrowDown") {
      e.preventDefault();
      resultList.querySelector("a")?.focus();
    }
  });
  resultList.addEventListener("keydown", (e) => {
    const current = (e.target as Element).closest("li");
    if (e.key === "ArrowDown") {
      e.preventDefault();
      current?.nextElementSibling?.querySelector("a")?.focus();
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      const previous = current?.previousElementSibling?.querySelector("a");
      (previous ?? input).focus();
    } else if (e.key === "Escape") {
      input.focus();
      hideResults();
    }
  });
  // Navigating to a result is handled by the regular link handling
  resultList.addEventListener("click", () => {
    hideResults();
    input.blur();
  });
  // Hide the results when focus leaves the search
  slot.addEventListener("focusout", (e) => {
    if (
      !(e instanceof FocusEvent) ||
      !slot.contains(e.relatedTarget as Node | null)
    ) {
      hideResults();
    }
  });
}

document.addEventListener("DOMContentLoaded", function () {
  resolveRelativeUrls(document, location.href);
  hookNavigation();
  setupMenuBarToggle();
  setupCyclingIngredients();
  setupSearch();

  const pageContentRoot = document.querySelector("#page-content");
  if (pageContentRoot) {
    fixupPageContent(pageContentRoot);
  }
});
