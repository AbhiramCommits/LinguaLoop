import { expect, test, type Page, type APIRequestContext } from "@playwright/test";

export const ADMIN_EMAIL = "author@e2e.example";

export async function waitForApi(request: APIRequestContext, apiURL: string): Promise<void> {
  const deadline = Date.now() + 90_000;
  while (Date.now() < deadline) {
    try {
      const health = await request.get(`${apiURL}/actuator/health`);
      if (health.ok()) {
        return;
      }
    } catch {
      // still starting up
    }
    await new Promise((resolve) => setTimeout(resolve, 2_000));
  }
  throw new Error("API did not become healthy within 90s");
}

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@e2e.example`;
}

export async function registerViaApi(request: APIRequestContext, apiURL: string, email: string) {
  // Registration is idempotent for e2e re-runs: 409 (already registered) is fine.
  const register = await request.post(`${apiURL}/api/auth/register`, {
    data: { email, password: "e2e-password-123", displayName: "E2E Learner", timezone: "UTC" },
  });
  expect([201, 200, 409].includes(register.status()),
    `register failed: ${register.status()} ${await register.text()}`).toBeTruthy();
  const login = await request.post(`${apiURL}/api/auth/login`, {
    data: { email, password: "e2e-password-123" },
  });
  expect(login.ok()).toBeTruthy();
  return (await login.json()) as { token: string; learner: { id: number } };
}

export async function seedE2eLesson(request: APIRequestContext, apiURL: string): Promise<void> {
  // Admin bootstrap: APP_ADMIN_EMAILS must include ADMIN_EMAIL in the stack env.
  const admin = await registerViaApi(request, apiURL, ADMIN_EMAIL);
  const yaml = `
language:
  code: xx
  name: E2E Language
unit:
  title: E2E Unit
  position: 90
lessons:
  - title: E2E Lesson
    position: 1
    exercises:
      - type: TRANSLATE
        prompt: Good morning
        answer: Buenos días
      - type: LISTEN
        prompt: Listen and type what you hear.
        answer: Hola
        caption: Hola
`;
  const response = await request.post(
    `${apiURL}/api/admin/import?format=yaml&dryRun=false`,
    {
      headers: { Authorization: `Bearer ${admin.token}`, "Content-Type": "text/plain" },
      data: yaml,
    },
  );
  expect(response.ok()).toBeTruthy();
  const body = (await response.json()) as { unitAction: string };
  expect(body.unitAction).toMatch(/CREATED|REPLACED/);
}

export async function loginInBrowser(page: Page, email: string): Promise<void> {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill("e2e-password-123");
  await page.locator("form").getByRole("button", { name: "Log in" }).click();
  await expect(page.getByText(/day streak/)).toBeVisible();
}

export async function openE2eLesson(page: Page): Promise<void> {
  await page.getByRole("link", { name: "Browse" }).click();
  await page.getByRole("button", { name: "E2E Language (xx)" }).click();
  await page.getByRole("button", { name: "E2E Unit" }).click();
  await page.getByRole("link", { name: /E2E Lesson/ }).click();
  await expect(page.getByText("Exercise 1 of 2")).toBeVisible();
}
