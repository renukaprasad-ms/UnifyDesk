import type { Metadata } from "next";
import LegalDraftPage from "../legal-draft-page";
export const metadata: Metadata = {
  title: "Cookies & Analytics Notice",
  description: "Draft India Early Access Cookies & Analytics Notice for UnifyDesk",
  alternates: { canonical: "/cookies" },
  robots: { index: false, follow: false },
};
const sections = [
  {
    "title": "Known code",
    "text": [
      "The marketing-site layout currently loads Google Analytics 4 via gtag.js with afterInteractive behavior. No consent manager was found in that file. Verify deployed cookie names, tracking events and Google Analytics settings."
    ]
  },
  {
    "title": "Session data",
    "text": [
      "The app has authentication and session capabilities; audit cookies and browser storage separately on the production frontend before publishing specific durations or purposes."
    ]
  },
  {
    "title": "Choices",
    "text": [
      "Do not claim an existing opt-in/out control without a tested implementation. Determine what consent or notice is required for the actual India-only audience, tracking and applicable laws."
    ]
  }
];
const related = [{"label":"Privacy","href":"/privacy"}];
export default function Page(){
  return <LegalDraftPage title="Cookies & Analytics Notice" intro="Draft notice for website measurement and product authentication storage." sections={sections} related={related}/>;
}
