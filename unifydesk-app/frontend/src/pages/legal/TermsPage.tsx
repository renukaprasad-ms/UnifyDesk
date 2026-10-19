import { FiCalendar, FiCheckCircle } from 'react-icons/fi'

export default function TermsPage() {
  return (
    <article className="prose prose-invert max-w-none space-y-8 text-slate-300 leading-relaxed text-sm sm:text-base">
      <div className="border-b border-slate-800 pb-6">
        <div className="flex items-center gap-2 text-xs text-indigo-400 font-semibold mb-2">
          <FiCalendar />
          <span>Last Updated: October 17, 2026</span>
        </div>
        <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
          Terms and Conditions
        </h1>
        <p className="mt-2 text-slate-400 text-sm">
          Please read these Terms and Conditions carefully before using the UnifyDesk SaaS platform, API services, and associated websites operated by UnifyDesk Technologies (&quot;UnifyDesk&quot;, &quot;we&quot;, &quot;us&quot;, or &quot;our&quot;).
        </p>
      </div>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">1</span>
          Acceptance of Terms
        </h2>
        <p>
          By creating an account, accessing, or using the UnifyDesk platform (<strong className="text-slate-100">app.unifydesk.in</strong>), you agree to be bound by these Terms and Conditions and our Privacy Policy. If you do not agree to all terms, you may not access or use our services.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">2</span>
          Description of Services
        </h2>
        <p>
          UnifyDesk provides a multi-channel cloud communication, CRM, and marketing automation platform enabling businesses to manage WhatsApp Business API, SMS, Email communications, contact databases, workflow automations, and advertising campaign analytics.
        </p>
        <p>
          Services are delivered electronically through web application dashboards and APIs. No physical software media is delivered.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">3</span>
          Account Registration &amp; Security
        </h2>
        <ul className="list-disc pl-5 space-y-1.5 text-slate-300">
          <li>You must provide accurate, current, and complete business information during registration.</li>
          <li>You are responsible for safeguarding your account credentials, API tokens, and 2FA secrets.</li>
          <li>You agree to notify UnifyDesk immediately upon becoming aware of any unauthorized access or security breach.</li>
        </ul>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">4</span>
          Acceptable Use &amp; Messaging Compliance
        </h2>
        <p>You agree not to use the platform for:</p>
        <ul className="list-disc pl-5 space-y-1.5 text-slate-300">
          <li>Transmitting unsolicited commercial communications (Spam) without verified opt-in consent.</li>
          <li>Violating Telecom Regulatory Authority of India (TRAI) guidelines, Distributed Ledger Technology (DLT) regulations, or Meta WhatsApp Business Commerce &amp; Messaging Policies.</li>
          <li>Distributing fraudulent, deceptive, defamatory, obscene, or unlawful materials.</li>
          <li>Attempting to probe, scan, or breach system security or bypass API rate limits.</li>
        </ul>
        <p className="text-xs text-amber-300/90 bg-amber-500/10 border border-amber-500/20 rounded-xl p-3">
          UnifyDesk reserves the right to immediately terminate or suspend accounts found in violation of anti-spam and telecom regulatory policies without refund.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">5</span>
          Subscription Fees, Billing &amp; AutoPay
        </h2>
        <p>
          Access to premium features requires a paid subscription plan. All fees are denominated in <strong className="text-white">Indian Rupees (INR)</strong> unless otherwise specified and are exclusive of applicable taxes (e.g., GST), which will be levied as prescribed by Indian tax laws.
        </p>
        <div className="rounded-xl bg-slate-800/50 border border-slate-700/60 p-4 space-y-2">
          <div className="font-semibold text-slate-100 flex items-center gap-2">
            <FiCheckCircle className="text-emerald-400" /> Payment Processing via Razorpay
          </div>
          <p className="text-xs text-slate-300">
            All credit card, debit card, UPI, Net Banking, and e-Mandate / recurring AutoPay payments are processed securely through RBI-authorized payment aggregators (Razorpay Software Private Limited). We do not store raw card numbers or banking passwords on our servers.
          </p>
        </div>
        <p>
          <strong className="text-slate-100">Recurring Mandates &amp; AutoPay:</strong> If you authorize an AutoPay mandate for subscription renewals or wallet replenishment, you authorize recurring charges up to your authorized limit (₹15,000 max) until you pause or cancel the mandate in your Billing settings.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">6</span>
          Termination &amp; Cancellation
        </h2>
        <p>
          You may cancel your subscription at any time directly through the Billing &amp; Finance dashboard. Upon cancellation, your access remains active until the end of your prepaid period. Refer to our <a href="/cancellation-and-refund" className="text-indigo-400 underline hover:text-indigo-300">Cancellation and Refund Policy</a> for details.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">7</span>
          Limitation of Liability &amp; Governing Law
        </h2>
        <p>
          To the maximum extent permitted by applicable law, UnifyDesk shall not be liable for any indirect, punitive, or consequential damages resulting from downtime, network outages, third-party telecommunication failures, or Meta API suspensions.
        </p>
        <p>
          These Terms are governed by and construed in accordance with the laws of India. Any disputes arising under or related to these Terms shall be subject to the exclusive jurisdiction of the competent courts in <strong className="text-slate-100">Bengaluru, Karnataka, India</strong>.
        </p>
      </section>
    </article>
  )
}
