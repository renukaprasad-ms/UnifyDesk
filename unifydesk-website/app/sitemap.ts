export const dynamic="force-static";
import type {MetadataRoute} from "next";
import {channelGuides} from "./seo-content";
import { guides, guidePath } from "./guide-content";

const routes=[
  {path:"",priority:1,changeFrequency:"weekly" as const},
  {path:"/product",priority:.9,changeFrequency:"weekly" as const},
  {path:"/solutions",priority:.9,changeFrequency:"weekly" as const},
  {path:"/integrations",priority:.9,changeFrequency:"weekly" as const},
  {path:"/pricing",priority:.9,changeFrequency:"weekly" as const},
  {path:"/security",priority:.7,changeFrequency:"monthly" as const},
  {path:"/about",priority:.6,changeFrequency:"monthly" as const},
  {path:"/contact",priority:.6,changeFrequency:"monthly" as const},
  {path:"/privacy",priority:.3,changeFrequency:"yearly" as const},
  {path:"/terms",priority:.3,changeFrequency:"yearly" as const},
  ...channelGuides.map(({path})=>({path,priority:.7,changeFrequency:"monthly" as const})),
  {path:"/guides",priority:.7,changeFrequency:"monthly" as const},
  ...guides.map(guide=>({path:guidePath(guide.slug),priority:.7,changeFrequency:"monthly" as const})),
];

export default function sitemap():MetadataRoute.Sitemap{
  return routes.map(({path,priority,changeFrequency})=>({
    url:`https://unifydesk.in${path}`,
    // Source edit date, deliberately fixed rather than refreshed on every build.
    lastModified:new Date(path.startsWith("/guides") ? "2026-10-12" : "2026-10-07"),
    changeFrequency,
    priority,
  }));
}
