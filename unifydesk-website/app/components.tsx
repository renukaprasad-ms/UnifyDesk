"use client";
import Link from "next/link";
import {Menu, X} from "lucide-react";
import {usePathname} from "next/navigation";
import {useState} from "react";

const links=[
  ["Product","/product"],
  ["Solutions","/solutions"],
  ["Integrations","/integrations"],
  ["Pricing","/pricing"],
  ["Security","/security"],
] as const;

export function Header(){
  const [open,setOpen]=useState(false);
  const pathname=usePathname();

  return <header className="marketingHeader">
    <div className="marketingNav">
      <Link className="brand" href="/" aria-label="UnifyDesk home">
        <img src="/logo-icon.svg" alt="UnifyDesk" width="34" height="34" aria-hidden="true"/>
        UnifyDesk
      </Link>
      <nav className="desktopNav" aria-label="Primary navigation">
        {links.map(([name,href])=>
          <Link className={pathname===href?"active":""} key={href} href={href}>{name}</Link>
        )}
      </nav>
      <div className="marketingNavActions">
        <span className="launchPill"><span/>Early access · soon</span>
        <Link className="button small navCta" href="/product">Explore product</Link>
        <button className="menu" onClick={()=>setOpen(!open)} aria-label={open?"Close navigation":"Open navigation"} aria-expanded={open} aria-controls="mobile-navigation">
          {open?<X/>:<Menu/>}
        </button>
      </div>
      {open&&<div className="mobileNav" id="mobile-navigation">
        {links.map(([name,href])=>
          <Link className={pathname===href?"active":""} key={href} href={href} onClick={()=>setOpen(false)}>{name}</Link>
        )}
        <Link className="button" href="/product" onClick={()=>setOpen(false)}>Explore product</Link>
      </div>}
    </div>
  </header>
}

export function Footer(){
  return <footer className="marketingFooter">
    <div className="footer">
      <div className="footerBrand">
        <Link className="brand footerLogo" href="/">
          <img src="/logo-icon.svg" alt="UnifyDesk" width="34" height="34" aria-hidden="true"/>
          UnifyDesk
        </Link>
        <p>One workspace for customer communication, campaigns and growth.</p>
        <p className="footerStatus">Currently in development · Public access coming soon.</p>
      </div>
      <div><b>Product</b><Link href="/product">Overview</Link><Link href="/integrations">Integrations</Link><Link href="/pricing">Pricing</Link></div>
      <div><b>Company</b><Link href="/about">About</Link><Link href="/contact">Contact</Link><Link href="/security">Security</Link></div>
      <div><b>Legal</b><Link href="/privacy">Privacy</Link><Link href="/terms">Terms</Link></div>
    </div>
    <div className="copyright">
      <span>© 2026 UnifyDesk. All rights reserved.</span>
      <span>Customer communication · Broadcast · Ads</span>
    </div>
  </footer>
}