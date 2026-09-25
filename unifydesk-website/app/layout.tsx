import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata={title:{default:"UnifyDesk — Customer communication, campaigns & ads in one workspace",template:"%s | UnifyDesk"},description:"Bring customer conversations, broadcasts, advertising and team workflows into one connected workspace."};
export default function RootLayout({children}:{children:React.ReactNode}){return <html lang="en"><body>{children}</body></html>}