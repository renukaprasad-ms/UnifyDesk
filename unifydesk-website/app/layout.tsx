import type { Metadata } from "next";
import Script from "next/script";
import "./globals.css";
import StructuredData from "./structured-data";

export const metadata: Metadata = {
  title: {
    default: "UnifyDesk | Customer Communication & Marketing Platform",
    template: "%s | UnifyDesk",
  },
  description:
    "UnifyDesk brings customer conversations, contacts, broadcasts, email, SMS, WhatsApp and advertising workflows into one connected business workspace.",
  metadataBase: new URL("https://unifydesk.in"),
  icons: {
    icon: [
      { url: "/favicon.ico" },
      { url: "/favicon-48x48.png", sizes: "48x48", type: "image/png" },
      { url: "/logo-icon.svg", type: "image/svg+xml" },
    ],
    shortcut: "/favicon.ico",
  },
  openGraph: {
    images: [
      {
        url: "/opengraph-image",
        width: 1200,
        height: 630,
        alt: "UnifyDesk — Run customer communication from one place",
      },
    ],
    title: "UnifyDesk | Customer Communication & Marketing Platform",
    description:
      "Manage customer conversations, broadcasts, email, SMS, WhatsApp and advertising workflows from one connected UnifyDesk workspace.",
    url: "https://unifydesk.in",
    siteName: "UnifyDesk",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    images: ["/twitter-image"],
    title: "UnifyDesk",
    description: "Customer communication, broadcasts, email, SMS, WhatsApp and advertising workflows in one connected workspace.",
  },
  robots: { index: true, follow: true },
  applicationName: "UnifyDesk",
  category: "business",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" data-scroll-behavior="smooth">
      <body>
        <Script
          src="https://www.googletagmanager.com/gtag/js?id=G-3NRD69RV7S"
          strategy="afterInteractive"
        />
        <Script id="google-analytics" strategy="afterInteractive">
          {`
            window.dataLayer = window.dataLayer || [];
            function gtag(){dataLayer.push(arguments);}
            gtag('js', new Date());
            gtag('config', 'G-3NRD69RV7S');
          `}
        </Script>
        <StructuredData />
        {children}
      </body>
    </html>
  );
}
