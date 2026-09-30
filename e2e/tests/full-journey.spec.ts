import { expect, test } from "@playwright/test";
import {
  loginInBrowser,
  openE2eLesson,
  registerViaApi,
  seedE2eLesson,
  uniqueEmail,
  waitForApi,
} from "./helpers";

test.describe.configure({ mode: "serial" });

test("register → LISTEN exercise → streak increments → queue shrinks", async ({ page, request }) => {
  const apiURL = process.env.API_BASE_URL ?? "http://localhost:8080";
  await waitForApi(request, apiURL);
  await seedE2eLesson(request, apiURL);
  const email = uniqueEmail("learner");
  await registerViaApi(request, apiURL, email);

  // Fresh learner: the queue is backfilled with 30 unseen exercises.
  await loginInBrowser(page, email);
  await expect(page.getByText("0 day streak")).toBeVisible();
  await expect(page.locator(".queue-list li")).toHaveCount(30);

  // Complete the two-exercise E2E lesson (TRANSLATE + LISTEN with captions).
  await openE2eLesson(page);
  await page.getByLabel("Your translation").fill("Buenos dias");
  await page.getByRole("button", { name: "Check" }).click();
  await expect(page.locator(".feedback-correct")).toContainText("Correct");
  await page.getByRole("button", { name: "Next" }).click();

  // LISTEN: captions are gated behind the hint_timing delay; wait them out.
  await expect(page.getByText("Exercise 2 of 2")).toBeVisible();
  await expect(page.getByRole("button", { name: "Show captions" })).toBeEnabled({
    timeout: 20_000,
  });
  await page.getByRole("button", { name: "Show captions" }).click();
  await expect(page.getByText("Hola")).toBeVisible();
  await page.getByLabel("What did you hear?").fill("Hola");
  await page.getByRole("button", { name: "Check" }).click();
  await expect(page.locator(".feedback-correct")).toContainText("Correct");

  await page.getByRole("button", { name: "Complete session" }).click();
  await expect(page.getByText("Session complete")).toBeVisible();
  await page.getByRole("link", { name: "Back to home" }).click();

  // Streak incremented (completion, not attempts) and the queue shrank:
  // the active unit is now the 2-exercise E2E unit, both seen → nothing due.
  await expect(page.getByText("1 day streak")).toBeVisible();
  await expect(page.getByText("Nothing due right now. Great job — come back later!")).toBeVisible();
});
