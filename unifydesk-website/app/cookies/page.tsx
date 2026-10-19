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
      "The marketing-site code only inserts optional Google Analytics scripts after a visitor selects Allow analytics. No optional analytics script is requested before a choice or after a rejection. Verify this behavior in the deployed site and audit the actual cookie names, tracking events and Google Analytics settings."
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
      "The website offers Allow analytics and Not now, stores the choice in local browser storage, and provides Cookie settings in the footer. Rejecting analytics after a previous acceptance reloads the page to stop further analytics requests. Check real browser behavior and any additional legal obligations before declaring the implementation compliant."
    ]
  }
];
const related = [{"label":"Privacy","href":"/privacy"}];
export default function Page(){
  return <LegalDraftPage title="Cookies & Analytics Notice" intro="Draft notice for website measurement and product authentication storage." sections={sections} related={related}/>;
}
