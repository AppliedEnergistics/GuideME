import "./index.css";
import "tippy.js/dist/tippy.css";
import tippy from "tippy.js";

function isInternalLink(link: URL): boolean {
    const pathToRoot = document.body.dataset.pathToRoot;
    const baseUrl = new URL(pathToRoot ?? '/', location.href);
    return link.host === baseUrl.host && link.pathname.startsWith(baseUrl.pathname);
}

function resolveRelativeLinks(container: ParentNode) {
    container.querySelectorAll('a[href]').forEach(link => {
        const href = link.getAttribute('href');
        if (!href || href.startsWith('#')) {
            return; // Skip anchor-only links
        }

        try {
            const absoluteUrl = new URL(href, window.location.href);
            link.setAttribute('href', absoluteUrl.href);
        } catch (e) {
            console.warn('Invalid link %o:', link, href);
        }
    });
}

async function handleNavigationAsync(targetUrl: URL, pushState: boolean = true) {
    const fetchUrl = new URL(targetUrl);
    fetchUrl.hash = '';

    console.info("Fetching %s", fetchUrl);
    const response = await fetch(fetchUrl, {
        mode: "same-origin"
    });
    if (!response.ok) {
        throw new Error("Failed to fetch " + fetchUrl + ": HTTP Status " + response.status);
    }

    const domParser = new DOMParser();
    const newDocument = domParser.parseFromString(await response.text(), "text/html");
    const newPageContent = newDocument.querySelector("#page-content");
    if (!newPageContent) {
        throw new Error("Fetched page does not include page content");
    }
    const currentPageContent = document.querySelector("#page-content");
    if (!currentPageContent) {
        return;
    }
    document.adoptNode(newPageContent);
    currentPageContent.replaceWith(newPageContent);
    document.title = newDocument.title;
    fixupPageContent(newPageContent);

    if (pushState) {
        // Use response.url to follow redirects
        history.pushState({url: response.url}, '', response.url);
    }

    newPageContent.parentElement?.scrollTo(0, 0);
}

function handleNavigation(targetUrl: URL, pushState: boolean = true) {
    handleNavigationAsync(targetUrl, pushState)
        .catch(err => {
            console.error(`Navigating to ${targetUrl} failed: ${err}`);
            window.location.href = targetUrl.toString();
        });
}

function hookNavigation() {
    document.addEventListener("click", e => {
        if (e.target instanceof Element) {
            const closestLink = e.target?.closest("a");
            if (closestLink) {
                // Expand the navbar as needed
                closestLink.closest("summary")?.click();

                const targetUrl = new URL(closestLink.href);
                if (targetUrl.host === location.host && targetUrl.pathname === location.pathname) {
                    return; // Let anchor navigation handle it
                }

                if (isInternalLink(targetUrl)) {
                    console.log("Clicked internal link %s", targetUrl);
                    e.preventDefault();
                    handleNavigation(targetUrl);
                }
            }
        }
    }, {
        capture: true
    });

    // Handle browser back/forward buttons
    window.addEventListener('popstate', function (e) {
        if (e.state && e.state.url) {
            handleNavigation(e.state.url, false);
        }
    });
}

function fixupPageContent(root: Element) {
    for (const gameSceneEl of root.querySelectorAll("img.game-scene")) {

        import("./model-viewer/modelViewer.ts").then(module => {
            const {setupGameScene} = module;
            setupGameScene(gameSceneEl as HTMLElement).catch((err) => {
                console.error("Failed to set up game scene @ %o: %s", gameSceneEl, err);
            });
        }).catch(err => {
            console.error('Failed to load module viewer scripts.', err);
        })
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
        for (const ingredientBox of document.querySelectorAll(
            ".ingredient-box.cycling",
        )) {
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

function setupTooltips() {
    tippy("#page-content .minecraft-tooltip", {
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

document.addEventListener("DOMContentLoaded", function () {
    hookNavigation();
    setupMenuBarToggle();
    setupTooltips();
    setupCyclingIngredients();

    const pageContentRoot = document.querySelector("#page-content");
    if (pageContentRoot) {
        fixupPageContent(pageContentRoot);
    }
    resolveRelativeLinks(document);
});
