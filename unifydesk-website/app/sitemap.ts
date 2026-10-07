export const dynamic="force-static";
import type {MetadataRoute} from "next";

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
];

export default function sitemap():MetadataRoute.Sitemap{
  return routes.map(({path,priority,changeFrequency})=>({
    url:`https://unifydesk.in${path}`,
    lastModified:new Date("2026-10-07"),
    changeFrequency,
    priority,
  }));
}
