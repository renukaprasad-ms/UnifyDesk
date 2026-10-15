export default function StructuredData(){
  const data=[
    {
      "@context":"https://schema.org",
      "@type":"Organization",
      "@id":"https://unifydesk.in/#organization",
      name:"UnifyDesk",
      url:"https://unifydesk.in/",
      logo:"https://unifydesk.in/logo.svg",
      description:"UnifyDesk is a customer communication and marketing platform that connects conversations, contacts, broadcasts and advertising workflows."
    },
    {
      "@context":"https://schema.org",
      "@type":"WebSite",
      "@id":"https://unifydesk.in/#website",
      url:"https://unifydesk.in/",
      name:"UnifyDesk",
      publisher:{"@id":"https://unifydesk.in/#organization"}
    },
    {
      "@context":"https://schema.org",
      "@type":"SoftwareApplication",
      "@id":"https://unifydesk.in/#software",
      name:"UnifyDesk",
      url:"https://unifydesk.in/",
      applicationCategory:"BusinessApplication",
      applicationSubCategory:"Customer communication and marketing platform",
      operatingSystem:"Web",
      description:"A connected workspace for customer conversations, contacts, broadcasts, CRM pipelines, calendar bookings, email, SMS, WhatsApp, Google Ads and Meta Ads.",
      featureList:[
        "Shared customer inbox",
        "Visual CRM and deal pipelines",
        "Public calendar booking and lead capture forms",
        "Contact and consent management",
        "Email, SMS and WhatsApp broadcasts",
        "Campaign attribution and revenue forecasting",
        "Google Ads and Meta Ads workflows",
        "Role-based workspace controls"
      ],
      publisher:{"@id":"https://unifydesk.in/#organization"}
    }
  ];
  return <script type="application/ld+json" dangerouslySetInnerHTML={{__html:JSON.stringify(data)}}/>
}
