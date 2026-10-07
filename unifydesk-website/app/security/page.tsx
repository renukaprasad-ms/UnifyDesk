import type { Metadata } from "next";
import {
  Building2,
  KeyRound,
  LockKeyhole,
  ShieldCheck,
  TimerReset,
} from "lucide-react";
import { Footer, Header } from "../components";
import Link from "next/link";
import { ChannelLinks } from "../channel-sections";

export const metadata: Metadata = {
  title: "Security & Data Protection",
  description:
    "Learn how UnifyDesk approaches company isolation, role-based access, 2FA, sessions, provider boundaries and secure customer communication workflows.",
  alternates: { canonical: "/security" },
};

const controls = [
  [
    "Company isolation",
    "Keep company data and workspace context separated.",
    Building2,
    [
      "Company-scoped access",
      "Separate company workspaces",
      "Company-level data controls",
    ],
  ],
  [
    "Identity & access",
    "Manage who can see and modify product areas.",
    ShieldCheck,
    ["Role-based permissions", "User management", "Invitation flows"],
  ],
  [
    "Account protection",
    "Protect sign-in and sensitive account actions.",
    KeyRound,
    ["2FA support", "Session controls", "Password management"],
  ],
  [
    "Provider boundaries",
    "Keep external provider credentials and workflows controlled.",
    LockKeyhole,
    [
      "Connection permissions",
      "Managed service boundaries",
      "Provider-specific scopes",
    ],
  ],
] as const;

export default function Security() {
  return (
    <>
      <Header />
      <main id="main-content" className="routePage">
        <section className="routeHero dark">
          <div className="statusBadge dark">
            <span />
            SECURITY
          </div>
          <h1>Workspace controls are part of the product foundation.</h1>
          <p>
            UnifyDesk is being built with company boundaries, access controls
            and account protections designed for team-based customer operations.
          </p>
        </section>
        <section className="routeSection">
          <h2 className="sectionTitle">
            Controls that match how the workspace is structured.
          </h2>
          <div className="routeCardGrid four">
            {controls.map(([title, description, Icon, points]) => (
              <article className="routeCard softCard reveal" key={title}>
                <div className="featureIcon">
                  <Icon size={21} />
                </div>
                <h3>{title}</h3>
                <p>{description}</p>
                <div className="pointList">
                  {points.map((p) => (
                    <span key={p}>
                      <ShieldCheck size={14} />
                      {p}
                    </span>
                  ))}
                </div>
              </article>
            ))}
          </div>
        </section>
        <section className="splitRouteSection soft">
          <div className="routeCopy"><p className="kicker">COMPANY BOUNDARIES</p><h2>Access follows the company and the member.</h2><p>The developing platform checks company membership and role permissions before allowing company-scoped actions. A company identifier alone does not grant access to its workspace. Active membership, company status and the permission for the requested action matter together.</p><p>For a growing team, decide who needs to manage users, work with conversations and connect providers. Review access when responsibilities change so the workspace reflects the company's actual team.</p><Link className="textLink" href="/omnichannel-inbox">Explore connected conversation work</Link></div>
          <div className="seoPreparation"><h3>Keep responsibilities clear</h3><ul><li><ShieldCheck size={18}/><span>Use company roles and permissions for the work each member needs.</span></li><li><ShieldCheck size={18}/><span>Limit provider connection management to authorized members.</span></li><li><ShieldCheck size={18}/><span>Review membership and account access as the team changes.</span></li></ul><Link className="textLink" href="/solutions">See workflows for growing teams</Link></div>
        </section>
        <section className="routeSection"><p className="kicker">ACCOUNTS & CONNECTIONS</p><h2 className="sectionTitle">Protect access to the workspace and its providers.</h2><div className="routeCardGrid three">
          <article className="routeCard"><h3>Two-factor account protection</h3><p>Two-factor verification is part of the developing sign-in flow when enabled for an account. Sensitive account changes also have verification controls. Team members should protect their own accounts alongside the company's role and permission settings.</p></article>
          <article className="routeCard"><h3>Session controls</h3><p>The platform supports revoking an individual session or a user's sessions. Account changes such as a password update can revoke existing access. Session controls help a team respond when an account or device should no longer remain signed in.</p></article>
          <article className="routeCard"><h3>Provider credentials</h3><p>Connected provider credentials are stored in encrypted form and managed behind authorized company access. Disconnecting a connection removes its stored credentials. External account permissions and provider-specific scopes still govern what the connection can do.</p></article>
        </div><p className="seoInlineLinks">For <Link href="/whatsapp-shared-inbox">WhatsApp team conversations</Link>, number and business account ownership must be validated before the connection is active. Review <Link href="/integrations">integration status and provider prerequisites</Link> before planning customer operations.</p></section>
        <section className="securityLaunch">
          <TimerReset size={28} />
          <span className="softBadge">PRE-LAUNCH</span>
          <h2>
            Security and production-readiness checks continue before broader
            access.
          </h2>
          <p>
            Public documentation will expand as the platform moves through
            early-access and production readiness. These controls describe the
            developing architecture; production verification continues before
            broader access. No security certification is advertised here.
          </p>
        </section>
        <ChannelLinks paths={["/whatsapp-shared-inbox","/omnichannel-inbox"]} title="Plan team access around the customer workflow."/>
      </main>
      <Footer />
    </>
  );
}
