import { FiCalendar, FiLock } from 'react-icons/fi'

export default function PrivacyPage() {
  return (
    <article className="prose prose-invert max-w-none space-y-8 text-slate-300 leading-relaxed text-sm sm:text-base">
      <div className="border-b border-slate-800 pb-6">
        <div className="flex items-center gap-2 text-xs text-indigo-400 font-semibold mb-2">
          <FiCalendar />
          <span>Last Updated: October 17, 2026</span>
        </div>
        <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
          Privacy Policy
        </h1>
        <p className="mt-2 text-slate-400 text-sm">
          UnifyDesk Technologies (&quot;UnifyDesk&quot;, &quot;we&quot;, &quot;our&quot;) is committed to protecting your personal data, customer records, and communication confidentiality in accordance with the Information Technology Act, 2000, and the Digital Personal Data Protection (DPDP) Act, 2023.
        </p>
      </div>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">1</span>
          Information We Collect
        </h2>
        <div className="space-y-3">
          <div>
            <h3 className="font-semibold text-slate-100 text-sm">Account &amp; Registration Details</h3>
            <p className="text-xs sm:text-sm text-slate-300 mt-1">
              Your business name, administrator name, work email address, billing phone number, company GSTIN (optional), and login credentials.
            </p>
          </div>
          <div>
            <h3 className="font-semibold text-slate-100 text-sm">Communication &amp; Contact Data</h3>
            <p className="text-xs sm:text-sm text-slate-300 mt-1">
              Recipient phone numbers and email addresses uploaded to your CRM, campaign message templates, delivery logs, read status, and message interaction timestamps.
            </p>
          </div>
          <div>
            <h3 className="font-semibold text-slate-100 text-sm">Billing &amp; Transaction Identifiers</h3>
            <p className="text-xs sm:text-sm text-slate-300 mt-1">
              Order IDs, transaction amounts, timestamps, and gateway payment IDs provided by Razorpay. UnifyDesk does not store credit/debit card numbers, CVVs, or Net Banking PINs.
            </p>
          </div>
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">2</span>
          How We Use Your Information
        </h2>
        <ul className="list-disc pl-5 space-y-1.5 text-slate-300">
          <li>To provision and manage your multi-channel communication workspace.</li>
          <li>To dispatch SMS, WhatsApp, and Email messages as directed by your authenticated workflows.</li>
          <li>To process billing invoices, plan renewals, and wallet auto-recharges via Razorpay.</li>
          <li>To monitor security, detect fraud, verify two-way transaction signatures, and maintain compliance audits.</li>
          <li>To deliver critical system alerts, maintenance notices, and customer support responses.</li>
        </ul>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">3</span>
          Data Security &amp; Encryption
        </h2>
        <div className="rounded-xl bg-slate-800/40 border border-slate-700/60 p-4 space-y-2">
          <div className="flex items-center gap-2 font-semibold text-emerald-400 text-sm">
            <FiLock className="text-base" /> Enterprise-Grade Data Protection
          </div>
          <p className="text-xs text-slate-300">
            All data transmitted between your browser and our servers is secured using modern TLS 1.3 encryption. At rest, databases and credential vaults are encrypted using AES-256 standards. Sensitive credentials such as webhook secrets, third-party API keys, and session tokens are hashed or tokenized.
          </p>
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">4</span>
          Third-Party Service Providers
        </h2>
        <p>We work with trusted third-party technology partners to deliver our services:</p>
        <ul className="list-disc pl-5 space-y-1.5 text-slate-300">
          <li><strong className="text-slate-100">Razorpay Software Private Limited:</strong> Payment gateway aggregator processing UPI, cards, net banking, and automated mandates.</li>
          <li><strong className="text-slate-100">Meta Cloud API:</strong> Official WhatsApp Business Platform connectivity.</li>
          <li><strong className="text-slate-100">Amazon Web Services (SES):</strong> High-deliverability managed transactional email transport.</li>
          <li><strong className="text-slate-100">TRAI-Registered DLT Gateways:</strong> Commercial SMS dispatch and delivery reporting within India.</li>
        </ul>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">5</span>
          Data Retention &amp; User Rights
        </h2>
        <p>
          You have the right to access, export, modify, or permanently delete your customer contact records and workspace data. Data deletion requests are permanently fulfilled within 30 days of subscription termination, except records required for tax or statutory regulatory retention.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">6</span>
          Grievance Officer
        </h2>
        <p>
          In accordance with the Information Technology Act, 2000 and the DPDP Act, 2023, the details of our designated Grievance Officer are:
        </p>
        <div className="rounded-xl bg-slate-800/60 border border-slate-700/60 p-4 text-xs space-y-1 text-slate-300">
          <div><strong className="text-slate-100">Designation:</strong> Data Grievance Officer</div>
          <div><strong className="text-slate-100">Entity:</strong> UnifyDesk Technologies</div>
          <div><strong className="text-slate-100">Email:</strong> <a href="mailto:grievance@unifydesk.in" className="text-indigo-400 underline">grievance@unifydesk.in</a></div>
          <div><strong className="text-slate-100">Response Turnaround:</strong> Within 48 business hours</div>
          <div><strong className="text-slate-100">Jurisdiction:</strong> Bengaluru, Karnataka, India</div>
        </div>
      </section>
    </article>
  )
}
