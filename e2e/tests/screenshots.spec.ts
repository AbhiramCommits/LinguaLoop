import { expect, test } from "@playwright/test";
import {
  ADMIN_EMAIL,
  loginInBrowser,
  openE2eLesson,
  registerViaApi,
  seedE2eLesson,
  uniqueEmail,
  waitForApi,
} from "./helpers";

// Captures the README screenshots. Run locally:
//   DOCS_IMAGES=$PWD/../docs/images npx playwright test screenshots
test.describe.configure({ mode: "serial" });

test("capture home, lesson player and experiments dashboard screenshots", async ({ page, request }) => {
  const apiURL = process.env.API_BASE_URL ?? "http://localhost:8080";
  const outputDir = process.env.DOCS_IMAGES;
  test.skip(!outputDir, "DOCS_IMAGES not set; screenshots are only captured locally");

  await waitForApi(request, apiURL);
  await seedE2eLesson(request, apiURL);
  const email = uniqueEmail("shots");
  await registerViaApi(request, apiURL, email);
  await loginInBrowser(page, email);

  await expect(page.getByText(/day streak/)).toBeVisible();
  await page.screenshot({ path: `${outputDir}/home.png`, fullPage: true });

  await openE2eLesson(page);
  await expect(page.getByText("Exercise 1 of 2")).toBeVisible();
  await page.screenshot({ path: `${outputDir}/lesson-player.png`, fullPage: true });

  await page.goto("/experiments");
  await expect(page.getByRole("heading", { name: "Experiments" })).toBeVisible();
  await page.screenshot({ path: `${outputDir}/experiments.png`, fullPage: true });

  // The author page requires the admin role (ADMIN_EMAIL is bootstrapped
  // via APP_ADMIN_EMAILS on the stack).
  await page.getByRole("button", { name: "Log out" }).click();
  await loginInBrowser(page, ADMIN_EMAIL);
  await page.goto("/author");
  await expect(page.getByText("Lesson authoring")).toBeVisible();
  await page.screenshot({ path: `${outputDir}/author.png`, fullPage: true });
});
