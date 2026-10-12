"use client";

import Link from "next/link";
import { ArrowRight, Menu, X } from "lucide-react";
import { usePathname } from "next/navigation";
import { useRef, useState } from "react";

const links = [
  ["Product", "/product"],
  ["Solutions", "/solutions"],
  ["Guides", "/guides"],
  ["Integrations", "/integrations"],
  ["Pricing", "/pricing"],
  ["Security", "/security"],
] as const;

export function Header() {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();
  const menuButton = useRef<HTMLButtonElement>(null);
  return (
    <header
      className="marketingHeader"
      onKeyDown={(event) => {
        if (event.key === "Escape" && open) {
          setOpen(false);
          menuButton.current?.focus();
        }
      }}
    >
      <a className="skipLink" href="#main-content">
        Skip to content
      </a>
      <div className="marketingNav">
        <Link className="brand" href="/" aria-label="UnifyDesk home">
          <img src="/logo-icon.svg" alt="" width={33} height={33} />
          UnifyDesk
        </Link>
        <nav className="desktopNav" aria-label="Primary navigation">
          {links.map(([name, href]) => (
            <Link
              key={href}
              href={href}
              aria-current={pathname === href ? "page" : undefined}
            >
              {name}
            </Link>
          ))}
        </nav>
        <div className="marketingNavActions">
          <span className="launchPill">
            <span className="statusDot" />
            In development
          </span>
          <Link className="button small navCta" href="/product">
            Take a tour <ArrowRight size={15} />
          </Link>
          <button
            ref={menuButton}
            className="menu"
            onClick={() => setOpen(!open)}
            aria-label={open ? "Close navigation" : "Open navigation"}
            aria-expanded={open}
            aria-controls="mobile-navigation"
          >
            {open ? <X size={22} /> : <Menu size={22} />}
          </button>
        </div>
        {open && (
          <nav
            className="mobileNav"
            id="mobile-navigation"
            aria-label="Mobile navigation"
            onKeyDown={(event) => {
              if (event.key === "Escape") {
                setOpen(false);
                menuButton.current?.focus();
              }
            }}
          >
            {links.map(([name, href]) => (
              <Link
                key={href}
                href={href}
                aria-current={pathname === href ? "page" : undefined}
                onClick={() => setOpen(false)}
              >
                {name}
                <ArrowRight size={15} />
              </Link>
            ))}
            <Link
              className="button"
              href="/product#product-tour"
              onClick={() => setOpen(false)}
            >
              Take the product tour <ArrowRight size={16} />
            </Link>
          </nav>
        )}
      </div>
    </header>
  );
}

export function Footer() {
  return (
    <footer className="marketingFooter">
      <div className="footer">
        <div className="footerBrand">
          <Link className="brand" href="/" aria-label="UnifyDesk home">
            <img src="/logo-icon.svg" alt="" width={33} height={33} />
            UnifyDesk
          </Link>
          <p>
            Customer conversations, campaigns and growth.
            <br />
            Better connected.
          </p>
          <span className="footerStatus">
            <span className="statusDot" />
            Public access coming soon
          </span>
        </div>
        <div>
          <b>Explore</b>
          <Link href="/product">Product</Link>
          <Link href="/solutions">Solutions</Link>
          <Link href="/guides">Guides</Link>
          <Link href="/integrations">Integrations</Link>
          <Link href="/pricing">Pricing</Link>
        </div>
        <div>
          <b>Company</b>
          <Link href="/about">About us</Link>
          <Link href="/security">Security</Link>
          <Link href="/contact">Contact</Link>
        </div>
        <div>
          <b>Legal</b>
          <Link href="/privacy">Privacy</Link>
          <Link href="/terms">Terms</Link>
        </div>
      </div>
      <div className="copyright">
        <span>© 2026 UnifyDesk. All rights reserved.</span>
        <span>Built around the customer.</span>
      </div>
    </footer>
  );
}
