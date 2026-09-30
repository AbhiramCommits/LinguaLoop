import { expect, test, type Page } from "@playwright/test";
import { registerViaApi, seedE2eLesson, uniqueEmail, waitForApi } from "./helpers";

async function activate(page: Page, name: string, key: string) {
  // No pointer input anywhere in this test: focus the control the way Tab
  // would, then activate it with a key press.
  const button = page.getByRole("button", { name }).first();
  if ((await button.count()) > 0) {
    await button.focus();
    await page.keyboard.press(key);
    return;
  }
  const link = page.getByRole("link", { name }).first();
  if ((await link.count()) > 0) {
    await link.focus();
    await page.keyboard.press(key);
    return;
  }
  const input = page.getByLabel(name).first();
  if ((await input.count()) > 0) {
    // Text inputs: focus only — pressing Enter inside a form would submit.
    await input.focus();
    return;
  }
  throw new Error(`No focusable element named "${name}"`);
}

test("a learner can complete a whole lesson using only the keyboard", async ({ page, request }) => {
  const apiURL = process.env.API_BASE_URL ?? "http://localhost:8080";
  await waitForApi(request, apiURL);
  await seedE2eLesson(request, apiURL);
  const email = uniqueEmail("keyboard");
  const auth = await registerViaApi(request, apiURL, email);

  // Seed the token so the test starts on the home screen; every subsequent
  // interaction is keyboard-only (Tab-like focus + Enter + typing).
  await page.addInitScript((token) => localStorage.setItem("lingualoop.token", token), auth.token);
  await page.goto("/");
  await expect(page.getByText(/day streak/)).toBeVisible();

  await activate(page, "Browse", "Enter");
  await expect(page.getByRole("button", { name: "E2E Language (xx)" })).toBeVisible();
  await activate(page, "E2E Language (xx)", "Enter");
  await expect(page.getByRole("button", { name: "E2E Unit" })).toBeVisible();
  await activate(page, "E2E Unit", "Enter");
  await expect(page.getByRole("link", { name: /E2E Lesson/ })).toBeVisible();
  await activate(page, "E2E Lesson", "Enter");
  await expect(page.getByText("Exercise 1 of 2")).toBeVisible();

  await activate(page, "Your translation", "Enter");
  await page.keyboard.type("Buenos dias");
  await activate(page, "Check", "Enter");
  await expect(page.locator(".feedback-correct")).toContainText("Correct");

  await activate(page, "Next", "Enter");
  await expect(page.getByText("Exercise 2 of 2")).toBeVisible();

  await expect(page.getByRole("button", { name: "Show captions" })).toBeEnabled({
    timeout: 20_000,
  });
  await activate(page, "Show captions", "Enter");
  await expect(page.getByText("Hola")).toBeVisible();
  await activate(page, "What did you hear?", "Enter");
  await page.keyboard.type("Hola");
  await activate(page, "Check", "Enter");
  await expect(page.locator(".feedback-correct")).toContainText("Correct");

  await activate(page, "Complete session", "Enter");
  await expect(page.getByText("Session complete")).toBeVisible();
});
