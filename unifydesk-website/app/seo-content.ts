import type { Metadata } from "next";

type ContentBlock = { title: string; body: string };
export type ChannelGuide = {
  path: string;
  label: string;
  title: string;
  description: string;
  heading: string;
  intro: string;
  purpose: string;
  workflowTitle: string;
  steps: ContentBlock[];
  features: ContentBlock[];
  useCases: ContentBlock[];
  providerTitle: string;
  providerBody: string;
  preparation: string[];
  faqs: ContentBlock[];
  related: string[];
};

export const channelGuides: ChannelGuide[] = [
  {
    path: "/whatsapp-shared-inbox",
    label: "WhatsApp shared inbox",
    title: "WhatsApp Shared Inbox for Customer Support Teams",
    description: "Explore a team WhatsApp inbox built around customer context, company access and connected conversations. See UnifyDesk's planned support workflow.",
    heading: "A WhatsApp shared inbox built around the customer.",
    intro: "When several people answer one business number, the next reply needs context. UnifyDesk is building a shared WhatsApp workspace where your team can review the conversation and the customer behind it together.",
    purpose: "For support and operations teams handling enquiries, order questions and follow-ups through a company-owned WhatsApp connection. The focus is the ongoing conversation: who the customer is, what they asked and what the team should do next.",
    workflowTitle: "From a new enquiry to an informed reply.",
    steps: [
      { title: "Connect the business number", body: "An authorized company member connects the WhatsApp business account and number. The connection needs provider validation before the team can use it; a saved setup alone does not mean messaging is ready." },
      { title: "Review the conversation and contact", body: "Open the shared conversation alongside customer details. Before replying, check the earlier messages and whether another channel holds useful context, such as an email about the same order." },
      { title: "Continue with a clear next step", body: "Use the customer's history to answer consistently. Agree on a team process for unresolved questions and review access when colleagues join or leave the company." },
    ],
    features: [
      { title: "One company connection", body: "A business number belongs to its company workspace. Team access should follow company membership and permissions rather than circulating provider credentials among colleagues." },
      { title: "Customer context beside the reply", body: "Contacts and conversations are part of the same product direction. A support enquiry should be understandable without manually reconstructing the customer from separate records." },
      { title: "Respect the channel's rules", body: "WhatsApp reply windows, templates and account permissions still apply. The shared inbox does not remove provider restrictions or turn every contact into permission to send marketing." },
    ],
    useCases: [
      { title: "Order and service enquiries", body: "A small support team can review what a customer has already shared before responding to a delivery, appointment or service question." },
      { title: "Support across working shifts", body: "A colleague returning to the conversation can read the shared history and contact context. Define responsibility for follow-up so customers receive a coherent response." },
    ],
    providerTitle: "WhatsApp connection, company controls.",
    providerBody: "The WhatsApp integration is in development. Provider account ownership, number readiness and authorized access are prerequisites. Public access is coming soon; the product tour illustrates the workspace with sample data.",
    preparation: ["A business account and number your company controls", "Team members and appropriate access permissions", "A clear process for replies and unresolved enquiries"],
    faqs: [
      { title: "Is the WhatsApp shared inbox available now?", body: "UnifyDesk is in development and public sign-up is coming soon. Explore the sample product tour to understand the workflow; it does not connect a real WhatsApp account." },
      { title: "How does this differ from a WhatsApp broadcast?", body: "The shared inbox is for handling individual conversations. A broadcast is outbound outreach to a selected audience and needs a separate review of consent, content and provider template requirements." },
    ],
    related: ["/omnichannel-inbox", "/whatsapp-broadcast"],
  },
  {
    path: "/whatsapp-broadcast",
    label: "WhatsApp broadcast",
    title: "WhatsApp Broadcast Workflows for Business Teams",
    description: "Plan WhatsApp broadcasts with audience consent, provider templates and campaign review. Explore UnifyDesk's developing business outreach workflow.",
    heading: "Plan WhatsApp broadcasts with a clear audience and purpose.",
    intro: "A useful broadcast starts before the send button. UnifyDesk is building an outreach workspace that connects customer audiences, campaign preparation and the provider account used to deliver the message.",
    purpose: "For marketing and operations teams planning relevant updates to customers who have agreed to hear from them. Keep audience selection and campaign review separate from the daily work of replying to individual conversations.",
    workflowTitle: "Prepare the audience. Review the message. Check readiness.",
    steps: [
      { title: "Define who should receive it", body: "Start with a specific purpose, such as an opted-in event audience or a service update. Review customer consent and exclusions rather than treating every imported phone number as a marketing recipient." },
      { title: "Prepare suitable content", body: "Match the message to the audience and confirm the template and category required by WhatsApp. Check variables, destinations and timing before the campaign is handed over for sending." },
      { title: "Confirm the sending connection", body: "Review the business number, account status and applicable provider limits. After sending, distinguish provider acceptance from successful delivery and route customer replies back into the team's conversation process." },
    ],
    features: [
      { title: "Audience context", body: "Customer records give the campaign a starting point. The team's job is to select a relevant audience and respect consent, including requests to stop receiving outreach." },
      { title: "Provider-aware preparation", body: "Template approval and account permissions remain WhatsApp responsibilities. A prepared draft cannot substitute for a valid provider setup or guarantee that every message will be delivered." },
      { title: "Replies continue the relationship", body: "Outreach can create support questions. Plan who will review incoming replies so the campaign and the customer conversation stay connected." },
    ],
    useCases: [
      { title: "Opted-in event updates", body: "Share a relevant reminder with people who requested event communication. Review the audience, template and destination before sending." },
      { title: "Customer service announcements", body: "Prepare an appropriate update for affected customers, such as a change in service availability. Confirm the provider's requirements for the message category." },
    ],
    providerTitle: "WhatsApp delivery has its own prerequisites.",
    providerBody: "WhatsApp is in development in UnifyDesk. Business account and number validation, templates, messaging limits and provider charges still apply. Final channel availability and pricing will be published before broader public access.",
    preparation: ["A relevant audience with recorded consent", "An appropriate provider-approved template where required", "An owner for campaign review and incoming replies"],
    faqs: [
      { title: "Can I broadcast to every number in my contacts?", body: "A contact record alone is not evidence of permission for marketing. Review consent, exclusions and the provider's applicable requirements before including a person in a broadcast." },
      { title: "Does a platform plan include WhatsApp delivery charges?", body: "UnifyDesk separates workspace access from provider usage. WhatsApp charges and final commercial terms need to be reviewed separately; paid plan details are coming soon." },
    ],
    related: ["/whatsapp-shared-inbox", "/sms-marketing"],
  },
  {
    path: "/sms-marketing",
    label: "SMS marketing",
    title: "SMS Marketing Workflows with Managed Sending",
    description: "Explore SMS campaign preparation, consent, sender and template readiness, and separate usage billing in UnifyDesk's developing managed SMS workflow.",
    heading: "Prepare SMS marketing that is relevant and ready to send.",
    intro: "SMS gives a team a concise way to reach customers, but a short message still needs careful preparation. UnifyDesk is developing managed SMS workflows around audiences, provider setup and usage visibility.",
    purpose: "For teams planning permission-based announcements and promotional updates. The workflow brings the customer audience and campaign draft together while keeping sender, template and delivery requirements visible.",
    workflowTitle: "Make the audience and sending setup ready together.",
    steps: [
      { title: "Choose a relevant audience", body: "Identify the customers who should receive this update and check consent and exclusions. Keep service messages and promotional outreach distinct when reviewing the message purpose." },
      { title: "Review the sender and template", body: "Confirm the provider account, sender identity and template required for the destination. For Indian SMS routes, check the applicable DLT registration and template requirements with the provider before sending." },
      { title: "Check message and usage expectations", body: "Review message length, variables and links. Encoding and message segments can affect provider usage, so confirm the provider's applicable rates and account readiness before releasing a campaign." },
    ],
    features: [
      { title: "Audience-led campaigns", body: "A campaign should have a specific reason to reach a customer. Use contact context to prepare the audience and maintain a process for consent changes and opt-outs." },
      { title: "Managed sending boundaries", body: "MSG91 is the managed SMS provider currently in development. Provider provisioning, sender and template readiness remain prerequisites; account configuration does not guarantee successful delivery." },
      { title: "Usage separate from access", body: "Workspace entitlements and SMS sending usage are different costs. Plan the platform access the team needs and review managed SMS rates before setting a campaign budget." },
    ],
    useCases: [
      { title: "Relevant promotional updates", body: "Prepare a concise offer for an opted-in audience. Make the purpose and destination clear and review the required promotional sender and template setup." },
      { title: "Time-sensitive announcements", body: "Reach the appropriate customer group with an operational update. Confirm its message category and use a channel and provider route suitable for that purpose." },
    ],
    providerTitle: "MSG91 in development. Other choices under review.",
    providerBody: "The managed SMS workflow uses MSG91 as its current provider direction. MetaReach is under evaluation and is not a committed production integration. Public access, supported routes and final usage rates are coming soon.",
    preparation: ["Consent and exclusions for the intended audience", "A provider-ready sender and required templates", "A budget that allows for provider usage and message segments"],
    faqs: [
      { title: "Does UnifyDesk handle every sender or template approval?", body: "Sender and template requirements depend on the provider and destination. Confirm the applicable setup with the provider; UnifyDesk does not promise to bypass registration or approval requirements." },
      { title: "Is SMS usage included in the Free plan?", body: "The Free workspace plan should not be interpreted as free SMS delivery. Managed sending is a separate usage layer, with final rates and commercial terms to be published before launch." },
    ],
    related: ["/whatsapp-broadcast", "/email-campaigns"],
  },
  {
    path: "/email-campaigns",
    label: "Email campaigns",
    title: "Email Campaign Workflows with Managed Email",
    description: "Explore email campaigns with verified senders, customer audiences and managed delivery. Understand how UnifyDesk separates inbox email from campaign sending.",
    heading: "Build email campaigns around your audience and sender identity.",
    intro: "Campaign email needs a prepared audience and a sending setup your company controls. UnifyDesk is developing managed email workflows that bring campaign preparation into the same workspace as customer communication.",
    purpose: "For teams planning newsletters, product updates and other permission-based email outreach. Prepare content and sending readiness together, while keeping connected personal or team inboxes separate from campaign infrastructure.",
    workflowTitle: "A verified sender is the start of the campaign.",
    steps: [
      { title: "Prepare the sending identity", body: "Set up the company sender or domain and complete the required verification. Review the provider's DNS and account requirements before treating the domain as ready for campaign delivery." },
      { title: "Review audience and content", body: "Select the customers who agreed to receive this kind of email. Check the subject, sender name, links and message, and include an appropriate way for recipients to stop receiving marketing." },
      { title: "Confirm delivery readiness", body: "Check provider status, sending capacity and campaign ownership before sending. Review the provider's available delivery signals afterward; acceptance for sending is different from reaching the recipient's inbox." },
    ],
    features: [
      { title: "Managed campaign delivery", body: "Amazon SES is the current managed email direction in development. Domain verification and provider readiness are part of the setup, with usage kept separate from the workspace subscription." },
      { title: "Inbox email has a different role", body: "Gmail and Microsoft connections are for mailbox conversations and inbox workflows. Connecting a mailbox is not a promise of bulk campaign sending through that account." },
      { title: "Customer context informs outreach", body: "Plan email for a relevant customer group and a clear purpose. Maintain a process for exclusions, unsubscribes and failed addresses rather than repeatedly sending to an unchanged imported list." },
    ],
    useCases: [
      { title: "Customer newsletters", body: "Prepare a useful update for people who subscribed. Confirm the sender identity and audience before the team reviews the campaign." },
      { title: "Product and service updates", body: "Explain a change relevant to an existing customer group. Check message purpose, destination links and the appropriate sending setup." },
    ],
    providerTitle: "Campaign providers and inbox providers, explained.",
    providerBody: "Amazon SES, Gmail and Microsoft are in development, with different roles. Public rollout of additional SendGrid and Azure Email campaign provider choices is planned. No integration label guarantees production readiness or a delivery rate.",
    preparation: ["A verified company sender or domain", "A permission-based audience and unsubscribe process", "Provider readiness and a separate email usage budget"],
    faqs: [
      { title: "Can a connected Gmail or Microsoft inbox send campaigns?", body: "Those connections serve inbox conversations. The managed campaign workflow uses campaign delivery infrastructure; mailbox connectivity alone does not establish bulk sending availability." },
      { title: "Does domain verification guarantee inbox placement?", body: "No. Verification is a setup prerequisite. Inbox placement also depends on factors such as sender reputation, audience quality, message content and the receiving service; no delivery guarantee is advertised." },
    ],
    related: ["/sms-marketing", "/omnichannel-inbox"],
  },
  {
    path: "/omnichannel-inbox",
    label: "Omnichannel inbox",
    title: "Omnichannel Inbox for Connected Customer Conversations",
    description: "Explore an omnichannel inbox bringing WhatsApp, email and SMS conversations around shared customer context, company access and provider-specific workflows.",
    heading: "An omnichannel inbox with one customer context.",
    intro: "A customer can email a question and follow up on WhatsApp. UnifyDesk is building a connected inbox so the team can understand the relationship across channels while responding through the appropriate provider.",
    purpose: "For businesses where customer work crosses email, WhatsApp and SMS. The goal is to bring conversations and contact context closer together without pretending that every channel has the same capabilities or delivery rules.",
    workflowTitle: "Understand the customer before choosing the next reply.",
    steps: [
      { title: "Connect the company's channels", body: "Prepare the provider accounts the team will use and assign authorized members to manage them. A channel becomes usable only when its own connection and readiness requirements are met." },
      { title: "Review context across conversations", body: "Look at the customer's contact details and available conversation history. Check whether an email and a messaging enquiry concern the same issue before duplicating work or asking the customer to repeat it." },
      { title: "Respond through the right channel", body: "Choose the appropriate conversation and respect that provider's reply, template and account restrictions. Define a team process for follow-up when an enquiry needs another colleague or channel." },
    ],
    features: [
      { title: "Contacts connect the work", body: "The workspace puts contacts alongside customer communication. Teams can use that context to understand an enquiry instead of treating each provider window as a new relationship." },
      { title: "Channels keep their capabilities", body: "Email mailbox sync, WhatsApp replies and SMS delivery have different requirements. Provider-specific behavior stays visible rather than being hidden behind a universal promise." },
      { title: "Company access stays central", body: "Company membership and permissions govern workspace access. Review who can connect providers and work with conversations as the team grows." },
    ],
    useCases: [
      { title: "Support moving from email to messaging", body: "A customer follows up about an earlier email through WhatsApp. Review available customer context before responding so the follow-up stays coherent." },
      { title: "One team, several communication channels", body: "A growing business can organize provider connections and conversation work in one product direction, with a clear process for channel ownership and team access." },
    ],
    providerTitle: "Connected providers, at their own pace.",
    providerBody: "WhatsApp, Gmail, Microsoft and managed SMS are in development. The supported experience depends on each provider's readiness and permissions. The sample product tour demonstrates the intended workspace; public access is coming soon.",
    preparation: ["Company-owned provider accounts for each channel", "Customer records and a process for maintaining context", "Appropriate roles and a clear follow-up process"],
    faqs: [
      { title: "Are all channels available in the same way?", body: "No. Channel capabilities and rollout depend on the provider. Check the integrations page for current public availability labels and the prerequisites for each connection." },
      { title: "Does an omnichannel inbox also send marketing campaigns?", body: "The inbox handles conversation work. Campaign preparation is a separate workflow that can use customer context, with its own audience consent, provider readiness and usage requirements." },
    ],
    related: ["/whatsapp-shared-inbox", "/email-campaigns"],
  },
];

export function guideFor(path: string): ChannelGuide {
  const guide = channelGuides.find((item) => item.path === path);
  if (!guide) throw new Error(`Unknown channel guide: ${path}`);
  return guide;
}

export function guideMetadata(path: string): Metadata {
  const guide = guideFor(path);
  return {
    title: guide.title,
    description: guide.description,
    alternates: { canonical: guide.path },
    openGraph: {
      title: `${guide.title} | UnifyDesk`,
      description: guide.description,
      url: guide.path,
      type: "website",
      images: [{ url: "/opengraph-image", width: 1200, height: 630, alt: "UnifyDesk customer communication workspace" }],
    },
    twitter: { card: "summary_large_image", title: guide.title, description: guide.description, images: ["/twitter-image"] },
  };
}
