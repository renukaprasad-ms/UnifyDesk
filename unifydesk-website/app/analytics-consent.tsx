"use client";

import Link from "next/link";
import Script from "next/script";
import { useEffect, useState } from "react";

type Choice = "accepted" | "rejected" | null;
const STORAGE_KEY = "unifydesk:analytics-choice:v1";

/** Load optional GA4 only after an explicit choice. No tracking for unset/rejected consent. */
export default function AnalyticsConsent() {
  const [choice, setChoice] = useState<Choice>(null);
  const [ready, setReady] = useState(false);
  const [showSettings, setShowSettings] = useState(false);

  useEffect(() => {
    try {
      const stored = window.localStorage.getItem(STORAGE_KEY);
      if (stored === "accepted" || stored === "rejected") setChoice(stored);
    } catch {
      // Local storage can be disabled; default to no analytics.
    }
    setReady(true);
    const openSettings = () => setShowSettings(true);
    window.addEventListener("unifydesk:open-analytics-settings", openSettings);
    return () => window.removeEventListener("unifydesk:open-analytics-settings", openSettings);
  }, []);

  function choose(next: Exclude<Choice, null>) {
    try { window.localStorage.setItem(STORAGE_KEY, next); } catch {}
    if (next === "rejected" && choice === "accepted") {
      // A loaded GA library cannot be unloaded: reload without including it.
      window.location.reload();
      return;
    }
    setChoice(next);
    setShowSettings(false);
  }

  return (
    <>
      {ready && choice === "accepted" && (
        <>
          <Script
            src="https://www.googletagmanager.com/gtag/js?id=G-3NRD69RV7S"
            strategy="afterInteractive"
          />
          <Script id="unifydesk-google-analytics" strategy="afterInteractive">
            {`
              window.dataLayer = window.dataLayer || [];
              function gtag(){dataLayer.push(arguments);}
              gtag('js', new Date());
              gtag('config', 'G-3NRD69RV7S');
            `}
          </Script>
        </>
      )}
      {ready && (choice === null || showSettings) && (
        <section className="analyticsConsent" role="dialog" aria-modal="false" aria-labelledby="analytics-consent-title">
          <div>
            <h2 id="analytics-consent-title">Optional website analytics</h2>
            <p>
              We use Google Analytics to understand visits. It stays off unless you opt in.
              You can change your choice from the footer.{" "}
              <Link href="/cookies">Read the cookie notice</Link>.
            </p>
          </div>
          <div className="analyticsConsentActions">
            <button type="button" className="analyticsDecline" onClick={() => choose("rejected")}>
              Not now
            </button>
            <button type="button" className="analyticsAccept" onClick={() => choose("accepted")}>
              Allow analytics
            </button>
          </div>
        </section>
      )}
    </>
  );
}
