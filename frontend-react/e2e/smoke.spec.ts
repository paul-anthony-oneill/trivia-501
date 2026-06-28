import { test, expect } from "@playwright/test";

test("homepage loads", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveTitle(/Trivia 501/);
  await expect(page.locator("body")).toBeVisible();
});

test("daily challenge page loads", async ({ page }) => {
  await page.goto("/daily");
  await expect(page.locator("body")).toBeVisible();
});
