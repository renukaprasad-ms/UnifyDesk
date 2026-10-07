import { readFile } from "node:fs/promises";

const host = "unifydesk.in";
const key = "6da8197e7c984a3f83990217dc3bbeca";
const keyLocation = `https://${host}/${key}.txt`;
const endpoint = "https://api.indexnow.org/indexnow";

async function main() {
  try {
    const sitemap = await readFile(new URL("../out/sitemap.xml", import.meta.url), "utf8");
    const urls = [...sitemap.matchAll(/<loc>(.*?)<\/loc>/g)]
      .map((match) => match[1].trim())
      .filter((url) => {
        try {
          return new URL(url).hostname === host;
        } catch {
          return false;
        }
      });

    if (urls.length === 0) {
      console.warn("[IndexNow] No URLs found in out/sitemap.xml; skipping submission.");
      return;
    }

    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "content-type": "application/json; charset=utf-8" },
      body: JSON.stringify({
        host,
        key,
        keyLocation,
        urlList: urls,
      }),
    });

    if (!response.ok) {
      const body = await response.text().catch(() => "");
      console.warn(
        `[IndexNow] Submission was not accepted (HTTP ${response.status}). ${body}`.trim(),
      );
      return;
    }

    console.log(
      `[IndexNow] Submitted ${urls.length} sitemap URLs after successful deployment (HTTP ${response.status}).`,
    );
  } catch (error) {
    console.warn(
      "[IndexNow] Submission skipped because of an unexpected error:",
      error instanceof Error ? error.message : error,
    );
  }
}

await main();
