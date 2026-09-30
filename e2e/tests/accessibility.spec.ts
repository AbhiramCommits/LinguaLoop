import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { ADMIN_EMAIL, loginInBrowser, openE2eLesson, registerViaApi, seedE2eLesson, uniqueEmail, waitForApi } from "./helpers";

const serious = ["serious", "critical"];

test.describe.configure({ mode: "serial" });

test("home, lesson and author pages have no serious or critical axe violations", async ({
  page,
  request,
}) => {
  const apiURL = process.env.API_BASE_URL ?? "http://localhost:8080";
  await waitForApi(request, apiURL);
  await seedE2eLesson(request, apiURL);
  const email = uniqueEmail("axe");
  await registerViaApi(request, apiURL, email);

  // Home
  await loginInBrowser(page, email);
  const homeScan = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  const homeViolations = homeScan.violations.filter((v) => serious.includes(v.impact ?? ""));
  expect(homeViolations, JSON.stringify(homeViolations, null, 2)).toEqual([]);

  // Lesson player
  await openE2eLesson(page);
  await expect(page.getByText("Exercise 1 of 2")).toBeVisible();
  const lessonScan = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  const lessonViolations = lessonScan.violations.filter((v) => serious.includes(v.impact ?? ""));
  expect(lessonViolations, JSON.stringify(lessonViolations, null, 2)).toEqual([]);

  // Author page (admin user — ADMIN_EMAIL is bootstrapped via APP_ADMIN_EMAILS)
  await page.getByRole("button", { name: "Log out" }).click();
  await loginInBrowser(page, ADMIN_EMAIL);
  await page.goto("/author");
  await expect(page.getByText("Lesson authoring")).toBeVisible();
  const authorScan = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  const authorViolations = authorScan.violations.filter((v) => serious.includes(v.impact ?? ""));
  expect(authorViolations, JSON.stringify(authorViolations, null, 2)).toEqual([]);

  // Experiments dashboard
  await page.goto("/experiments");
  await expect(page.getByRole("heading", { name: "Experiments" })).toBeVisible();
  const experimentsScan = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  const experimentsViolations = experimentsScan.violations.filter((v) =>
    serious.includes(v.impact ?? ""),
  );
  expect(experimentsViolations, JSON.stringify(experimentsViolations, null, 2)).toEqual([]);
});
