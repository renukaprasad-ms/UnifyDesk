import type {Metadata} from "next";
import {Building2,KeyRound,LockKeyhole,ShieldCheck,TimerReset} from "lucide-react";
import {Footer,Header} from "../components";

export const metadata:Metadata={
  title:"Security | UnifyDesk",
  description:"Learn about UnifyDesk company isolation, identity controls, 2FA, sessions and provider boundaries.",
  alternates:{canonical:"/security"}
};

const controls=[
  ["Company isolation","Keep company data and workspace context separated.",Building2,["Company-scoped access","Workspace epoch boundaries","Scoped data operations"]],
  ["Identity & access","Manage who can see and modify product areas.",ShieldCheck,["Role-based permissions","User management","Invitation flows"]],
  ["Account protection","Protect sign-in and sensitive account actions.",KeyRound,["2FA support","Session controls","Password management"]],
  ["Provider boundaries","Keep external provider credentials and workflows controlled.",LockKeyhole,["Connection permissions","Managed service boundaries","Provider-specific scopes"]]
] as const;

export default function Security(){
  return <><Header/><main className="routePage">
    <section className="routeHero dark"><div className="statusBadge dark"><span/>SECURITY</div><h1>Workspace controls are part of the product foundation.</h1><p>UnifyDesk is being built with company boundaries, access controls and account protections designed for team-based customer operations.</p></section>
    <section className="routeSection"><h2 className="sectionTitle">Controls that match how the workspace is structured.</h2><div className="routeCardGrid four">{controls.map(([title,description,Icon,points])=><article className="routeCard softCard reveal" key={title}><div className="featureIcon"><Icon size={21}/></div><h3>{title}</h3><p>{description}</p><div className="pointList">{points.map(p=><span key={p}><ShieldCheck size={14}/>{p}</span>)}</div></article>)}</div></section>
    <section className="securityLaunch"><TimerReset size={28}/><span className="softBadge">PRE-LAUNCH</span><h2>Security and production-readiness checks continue before broader access.</h2><p>Public documentation will expand as the platform moves through early-access and production readiness.</p></section>
  </main><Footer/></>
}