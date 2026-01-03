import "./index.css";
import "tippy.js/dist/tippy.css";
import tippy from "tippy.js";

function fixupPageContent(root: Element) {
  for (const gameSceneEl of root.querySelectorAll("img.game-scene")) {

    import("./model-viewer/modelViewer.ts").then(module => {
      const { setupGameScene } = module;
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
  setupMenuBarToggle();
  setupTooltips();
  setupCyclingIngredients();

  const pageContentRoot = document.querySelector("#page-content");
  if (pageContentRoot) {
    fixupPageContent(pageContentRoot);
  }
});
