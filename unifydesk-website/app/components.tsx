"use client";
import Link from "next/link";
import {Menu, X} from "lucide-react";
import {useState} from "react";

const links=[["Product","/product"],["Solutions","/solutions"],["Integrations","/integrations"],["Pricing","/pricing"],["Security","/security"]];

export function Header(){
  const [open,setOpen]=useState(false);
  return <header><div className="nav">
    <Link className="brand" href="/" aria-label="UnifyDesk home"><img src="/logo-icon.svg" alt="" width="32" height="32"/>UnifyDesk</Link>
    <nav>{links.map(([n,h])=><Link key={h} href={h}>{n}</Link>)}</nav>
    <div className="actions">
      <span className="launchPill">Coming soon</span>
      <button className="menu" onClick={()=>setOpen(!open)} aria-label={open?"Close navigation":"Open navigation"} aria-expanded={open} aria-controls="mobile-navigation">{open?<X/>:<Menu/>}</button>
    </div>
    {open&&<div className="mobileNav" id="mobile-navigation">
      {links.map(([n,h])=><Link key={h} href={h} onClick={()=>setOpen(false)}>{n}</Link>)}
      <span className="mobileLaunchPill">Public access coming soon</span>
    </div>}
  </div></header>
}

export function Footer(){
  return <footer><div className="footer">
    <div><Link className="brand" href="/"><img src="/logo-icon.svg" alt="" width="32" height="32"/>UnifyDesk</Link><p>One workspace for customer communication, campaigns and growth.</p><p className="footerStatus">Currently in development. Public access is coming soon.</p></div>
    <div><b>Product</b><Link href="/product">Overview</Link><Link href="/integrations">Integrations</Link><Link href="/pricing">Pricing</Link></div>
    <div><b>Company</b><Link href="/about">About</Link><Link href="/contact">Contact</Link><Link href="/security">Security</Link></div>
    <div><b>Legal</b><Link href="/privacy">Privacy</Link><Link href="/terms">Terms</Link></div>
  </div><div className="copyright">© 2026 UnifyDesk. All rights reserved.</div></footer>
}
