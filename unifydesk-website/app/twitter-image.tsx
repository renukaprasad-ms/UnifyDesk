export const dynamic="force-static";
import {ImageResponse} from "next/og";
export const alt="UnifyDesk — Run customer communication from one place";
export const size={width:1200,height:600};
export const contentType="image/png";
export default function Image(){return new ImageResponse(
<div style={{width:"100%",height:"100%",display:"flex",background:"#f8fafc",fontFamily:"Arial",padding:"68px",position:"relative",overflow:"hidden"}}>
  <div style={{position:"absolute",width:600,height:600,borderRadius:300,background:"#e8f0ff",right:-120,top:-250,display:"flex"}}/>
  <div style={{display:"flex",flexDirection:"column",zIndex:2,width:"62%"}}>
    <div style={{display:"flex",alignItems:"center",gap:14,fontSize:30,fontWeight:700,color:"#0f172a"}}>
      <div style={{width:58,height:58,borderRadius:14,background:"#2563eb",display:"flex",alignItems:"center",justifyContent:"center",color:"white",fontSize:36,fontWeight:800}}>U</div>
      <span>UnifyDesk</span>
    </div>
    <div style={{display:"flex",flexDirection:"column",fontSize:68,lineHeight:1.02,letterSpacing:-3,fontWeight:800,color:"#0f172a",marginTop:80}}>
      <span>Run customer</span><span>communication from</span><span style={{color:"#2563eb"}}>one place.</span>
    </div>
    <div style={{display:"flex",fontSize:23,lineHeight:1.5,color:"#64748b",marginTop:28}}>Conversations, campaigns, email, WhatsApp and advertising in one connected workspace.</div>
  </div>
  <div style={{position:"absolute",right:65,top:145,width:365,height:350,border:"2px solid #e2e8f0",borderRadius:22,background:"white",display:"flex",padding:22,boxShadow:"0 20px 50px #1e3a5f15"}}>
    <div style={{width:78,background:"#fff",borderRight:"1px solid #e2e8f0",display:"flex",flexDirection:"column",gap:18,padding:"20px 10px"}}>{[0,1,2,3,4].map(i=><div key={i} style={{height:12,borderRadius:6,background:i===2?"#c7d2fe":"#eef2ff",display:"flex"}}/>)}</div>
    <div style={{flex:1,display:"flex",flexDirection:"column",gap:18,padding:"20px"}}>{[0,1,2].map(i=><div key={i} style={{height:66,borderRadius:10,background:"#f8fafc",border:"1px solid #e2e8f0",display:"flex"}}/>)}</div>
  </div>
</div>,size)}