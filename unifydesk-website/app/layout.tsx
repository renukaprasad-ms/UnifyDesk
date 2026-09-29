import type { Metadata } from "next";
import "./globals.css";
import StructuredData from "./structured-data";

export const metadata: Metadata = {
  title: {
    default: "UnifyDesk — Customer communication, campaigns & ads in one workspace",
    template: "%s | UnifyDesk",
  },
  description:
    "UnifyDesk is an upcoming connected workspace for customer conversations, broadcasts, advertising and team workflows. Public access is coming soon.",
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
    title: "UnifyDesk — One workspace for customer communication and growth",
    description:
      "UnifyDesk is under active development. Public access is coming soon.",
    url: "https://unifydesk.in",
    siteName: "UnifyDesk",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    images: ["/twitter-image"],
    title: "UnifyDesk",
    description: "Customer communication, campaigns and ads in one connected workspace.",
  },
  robots: { index: true, follow: true },
  applicationName: "UnifyDesk",
  category: "business",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <StructuredData />
        {children}
      </body>
    </html>
  );
}
