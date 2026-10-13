import type { Metadata } from "next";

export type Guide = {
  slug: string;
  label: string;
  title: string;
  description: string;
  heading: string;
  intro: string;
  primaryPath: string;
  relatedPaths: string[];
  published: string;
  sections: { heading: string; paragraphs: string[]; bullets?: string[] }[];
  faqs: { question: string; answer: string }[];
};

export const guides: Guide[] = [
  {
    "slug": "whatsapp-broadcast-india",
    "label": "WhatsApp broadcast guide",
    "title": "How WhatsApp Broadcasts Work for Businesses in India",
    "description": "Understand WhatsApp broadcast preparation in India: opt-in audiences, business account readiness, approved templates, review steps and delivery limits.",
    "heading": "How to prepare a WhatsApp broadcast for business customers in India",
    "intro": "A business broadcast is a planned message to a selected group of customers—not a shortcut for messaging everyone whose number you have. This guide explains the audience, account and template checks to make before you send.",
    "primaryPath": "/whatsapp-broadcast",
    "relatedPaths": [
      "/whatsapp-shared-inbox",
      "/sms-marketing"
    ],
    "published": "2026-10-12",
    "sections": [
      {
        "heading": "What counts as a WhatsApp broadcast?",
        "paragraphs": [
          "For businesses, a broadcast is one outbound campaign delivered to many relevant recipients through a business messaging setup. It is different from an individual service conversation. The WhatsApp Business Platform has rules for initiating customer conversations and using approved message templates.",
          "A customer appearing in your CRM, importing a spreadsheet or previously buying a product does not automatically mean that marketing outreach is permitted. Document a suitable opt-in and make the purpose of the messages clear."
        ]
      },
      {
        "heading": "1. Build the right audience",
        "paragraphs": [
          "Start with why the customer should receive the update. Examples include a requested event reminder or an opted-in product announcement. Narrow the audience with contact lists and segments, then exclude people who opted out or whose consent does not cover that purpose."
        ],
        "bullets": [
          "Identify the communication purpose and message category.",
          "Verify that recipients have provided the required permission.",
          "Check duplicate numbers, country codes, exclusions and opted-out contacts.",
          "Prepare a way to handle incoming replies and stop requests."
        ]
      },
      {
        "heading": "2. Check your WhatsApp Business Platform setup",
        "paragraphs": [
          "Use a company-authorized business account and phone number. Confirm the number, account permissions, quality and messaging limits before planning volume. A draft or a connected account is not proof that production sending is enabled.",
          "Outside the applicable customer service conversation window, business-initiated messages generally require approved templates. Categories, pricing and eligibility can change, so confirm current requirements in Meta's official WhatsApp Business Platform documentation."
        ]
      },
      {
        "heading": "3. Review a template and run a small test",
        "paragraphs": [
          "Write a specific, recognizable message, avoid misleading urgency and check every template variable. Verify links, language, personalization and opt-out handling. Test on a small permitted audience first, particularly when using a new sender or changing your campaign process."
        ],
        "bullets": [
          "Confirm template approval for the intended category and language.",
          "Preview example recipient values and destination links.",
          "Test delivery and replies before selecting a larger audience.",
          "Do not equate provider acceptance with final message delivery."
        ]
      },
      {
        "heading": "4. Measure what happened",
        "paragraphs": [
          "Separate queued, accepted, sent, delivered, read and failed states according to what the provider actually reports. Investigate invalid recipients, blocked numbers and template problems instead of repeatedly retrying everyone.",
          "UnifyDesk is developing WhatsApp broadcast planning around contact audiences, consent and provider readiness. Its public WhatsApp integration is not yet generally available; the product tour uses sample data."
        ]
      }
    ],
    "faqs": [
      {
        "question": "Can I upload all my contacts and send a WhatsApp broadcast?",
        "answer": "No. A contact record alone does not prove marketing consent. Confirm the appropriate opt-in and respect opt-outs and WhatsApp policy requirements."
      },
      {
        "question": "Does WhatsApp broadcast mean free unlimited messages?",
        "answer": "No. Business Platform messaging is subject to provider rules, limits and charges. Prices and message eligibility depend on your setup and the current provider terms."
      },
      {
        "question": "Can UnifyDesk send these broadcasts today?",
        "answer": "UnifyDesk's WhatsApp workflow is in development. Public access and final supported capabilities are coming soon."
      }
    ]
  },
  {
    "slug": "email-broadcast-vs-email-marketing",
    "label": "Email campaign guide",
    "title": "Email Broadcast vs Email Marketing: What Businesses Need",
    "description": "Compare email broadcasts and ongoing email marketing. Learn about opt-in audiences, verified sending domains, unsubscribe handling and campaign delivery.",
    "heading": "Email broadcast vs email marketing: what is the difference?",
    "intro": "An email broadcast is a single campaign sent to a chosen audience. Email marketing is the broader process of earning permission, building useful campaigns and measuring results over time. The distinction helps you choose the right workflow and sender.",
    "primaryPath": "/email-campaigns",
    "relatedPaths": [
      "/sms-marketing",
      "/omnichannel-inbox"
    ],
    "published": "2026-10-12",
    "sections": [
      {
        "heading": "What is an email broadcast?",
        "paragraphs": [
          "A broadcast is one message, such as a product update, newsletter or event invitation, sent to a segment of subscribers. It usually has a single message, audience definition, send time and results report.",
          "Email marketing includes broadcasts but can also cover subscriber management, segmentation, lifecycle journeys, experimentation and reporting. A one-time broadcast does not automatically make a complete automation program."
        ]
      },
      {
        "heading": "Choose the right audience before the message",
        "paragraphs": [
          "Only send marketing to people with an appropriate permission basis, and keep records of consent. A contact's billing or support email address is not necessarily permission for promotional campaigns."
        ],
        "bullets": [
          "Specify the campaign goal and the intended segment.",
          "Filter unsubscribed, bounced and suppressed addresses.",
          "Check for duplicates and obviously invalid addresses.",
          "Keep sender identification and unsubscribe options clear."
        ]
      },
      {
        "heading": "Why a verified sending domain matters",
        "paragraphs": [
          "A campaign platform should use a sending identity controlled by your company. Depending on the provider, you may need to verify DNS records for SPF, DKIM and DMARC alignment. These help receiving systems authenticate email, but they do not guarantee delivery or inbox placement.",
          "Keep normal person-to-person mailbox conversations separate from high-volume campaign sending. Using a Gmail or Microsoft inbox connection does not mean it is the right infrastructure for bulk marketing."
        ]
      },
      {
        "heading": "How to prepare a useful campaign",
        "paragraphs": [
          "Write a clear subject line that matches the actual email. Preview the email on mobile, verify all links and personalization, and make the sender easy to recognize. Test the campaign with internal addresses before scheduling a larger send."
        ],
        "bullets": [
          "Use a narrow segment with a concrete reason to hear from you.",
          "Explain one primary action instead of stacking many competing calls to action.",
          "Include a working unsubscribe process for marketing messages.",
          "Monitor bounces, complaints and delivery reports after the send."
        ]
      },
      {
        "heading": "What UnifyDesk is building",
        "paragraphs": [
          "UnifyDesk is developing a managed email campaign workflow with domain readiness, audience and consent context, and separate usage billing. Amazon SES is the current managed sending direction. The connected inbox serves a different, conversation-focused purpose.",
          "Public access is coming soon. For now, use the product tour to explore the intended workflow with illustrative sample data rather than assuming live sending is available."
        ]
      }
    ],
    "faqs": [
      {
        "question": "Can I send a marketing broadcast from my personal Gmail inbox?",
        "answer": "A normal mailbox is designed for conversations, not as a default bulk marketing service. Review the provider's sending policies and use suitable verified campaign infrastructure."
      },
      {
        "question": "Does DKIM or SPF guarantee my email will reach the inbox?",
        "answer": "No. Authentication helps verify the sender, while placement also depends on reputation, content, audience engagement and receiving-provider decisions."
      },
      {
        "question": "Is UnifyDesk email campaign sending publicly available?",
        "answer": "The managed email workflow is still in development. Public availability and final usage rates will be published ahead of broader access."
      }
    ]
  },
  {
    "slug": "bulk-sms-dlt-india",
    "label": "SMS campaign guide",
    "title": "Bulk SMS in India: DLT Registration and Campaign Checklist",
    "description": "Plan permission-based bulk SMS campaigns in India. Understand DLT sender and template readiness, consent, message segments and provider delivery reporting.",
    "heading": "Bulk SMS in India: a practical DLT and campaign checklist",
    "intro": "Sending promotional or service-related SMS at scale in India requires more than a phone-number list. You need a suitable audience, the correct sender and template setup, and an approved provider route before a campaign is released.",
    "primaryPath": "/sms-marketing",
    "relatedPaths": [
      "/whatsapp-broadcast",
      "/email-campaigns"
    ],
    "published": "2026-10-12",
    "sections": [
      {
        "heading": "What is a bulk SMS broadcast?",
        "paragraphs": [
          "A bulk SMS broadcast is a message prepared for a group of recipients, often for a promotion, appointment reminder or operational update. Message purpose matters: promotional, service and transactional traffic may have different regulatory and provider requirements.",
          "Before sending, make sure your contact data and consent practices are appropriate to the message category. Importing numbers into a tool does not make them eligible to receive marketing."
        ]
      },
      {
        "heading": "Understand DLT readiness",
        "paragraphs": [
          "India's distributed ledger technology (DLT) framework is used by telecom operators to manage sender identities and message templates for commercial communication. Depending on the message type and route, principal entity registration, a registered sender header, content templates and consent-related setup can be required.",
          "Requirements can change and vary by traffic category, operator and provider. Confirm the current onboarding and template rules through your registered telemarketer or DLT portal; do not assume a website tool can bypass approval."
        ]
      },
      {
        "heading": "Prepare before your first SMS campaign",
        "paragraphs": [
          "Have your legal business details, authorized contacts and campaign purpose ready before starting DLT/provider registration. Keep the exact message wording and variables aligned with approved templates."
        ],
        "bullets": [
          "Register and verify the appropriate principal entity where required.",
          "Set up a permitted sender header and category-appropriate template.",
          "Review channel consent, exclusions and opted-out recipients.",
          "Check the SMS provider account, credits, destination route and template mapping.",
          "Test with permitted recipients before scaling up."
        ]
      },
      {
        "heading": "Budget for segments and failed deliveries",
        "paragraphs": [
          "SMS billing often depends on message segments, route, encoding and provider pricing. Messages containing non-GSM characters can consume more segments than expected. Preview the real rendered text, not only the template, before estimating cost.",
          "Provider acceptance, operator submission and handset delivery are distinct events. Use delivery reports or approved polling/status APIs where available and avoid promising delivery for every submitted message."
        ]
      },
      {
        "heading": "How UnifyDesk approaches managed SMS",
        "paragraphs": [
          "UnifyDesk is developing permission-based SMS broadcast workflows with audience preparation, provider sender/template readiness and usage visibility. MSG91 is the current managed SMS provider direction. Public availability and final provider support are still being finalized.",
          "UnifyDesk separates plan access from managed SMS usage. A workspace plan does not automatically include unlimited SMS sends."
        ]
      }
    ],
    "faqs": [
      {
        "question": "Can I send marketing SMS without DLT setup in India?",
        "answer": "Do not assume so. Relevant Indian commercial messaging routes commonly require DLT-related registrations and approved sender/template configuration. Verify the current rules for your traffic and provider."
      },
      {
        "question": "Does one SMS always cost one message unit?",
        "answer": "Not always. Encoding, message length and route can change the number of billed segments. Check the provider's pricing and segmentation rules."
      },
      {
        "question": "Does UnifyDesk register a DLT entity for me?",
        "answer": "No such automatic registration is promised. Businesses should confirm required entity, sender and template setup with their DLT platform and provider."
      }
    ]
  }
];

export const guidePath = (slug: string) => `/guides/${slug}`;

export function guideForSlug(slug: string): Guide {
  const guide = guides.find(item => item.slug === slug);
  if (!guide) throw new Error(`Unknown guide slug: ${slug}`);
  return guide;
}

export function metadataForGuide(slug: string): Metadata {
  const guide = guideForSlug(slug);
  return {
    title: guide.title,
    description: guide.description,
    alternates: { canonical: guidePath(slug) },
    openGraph: {
      title: `${guide.title} | UnifyDesk`,
      description: guide.description,
      url: guidePath(slug),
      type: "article",
      images: [{ url: "/opengraph-image", width: 1200, height: 630, alt: "UnifyDesk customer communication workspace" }],
    },
    twitter: { card: "summary_large_image", title: guide.title, description: guide.description, images: ["/twitter-image"] },
  };
}
