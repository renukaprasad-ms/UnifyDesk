import { FiCalendar, FiCheckCircle, FiClock, FiRefreshCw } from 'react-icons/fi'

export default function RefundPage() {
  return (
    <article className="prose prose-invert max-w-none space-y-8 text-slate-300 leading-relaxed text-sm sm:text-base">
      <div className="border-b border-slate-800 pb-6">
        <div className="flex items-center gap-2 text-xs text-indigo-400 font-semibold mb-2">
          <FiCalendar />
          <span>Last Updated: October 17, 2026</span>
        </div>
        <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
          Cancellation and Refund Policy
        </h1>
        <p className="mt-2 text-slate-400 text-sm">
          At UnifyDesk, customer satisfaction and trust are our top priorities. This policy clearly outlines our cancellation terms, refund eligibility criteria, and processing timelines for all SaaS subscription plans and prepaid communications wallet top-ups.
        </p>
      </div>

      {/* Highlights Box */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <div className="p-4 rounded-xl bg-slate-800/40 border border-slate-700/60">
          <div className="text-indigo-400 text-sm font-bold flex items-center gap-1.5 mb-1">
            <FiCheckCircle /> 7-Day Guarantee
          </div>
          <p className="text-xs text-slate-400">Full refund for first-time paid plan subscriptions within 7 days.</p>
        </div>
        <div className="p-4 rounded-xl bg-slate-800/40 border border-slate-700/60">
          <div className="text-emerald-400 text-sm font-bold flex items-center gap-1.5 mb-1">
            <FiRefreshCw /> 0 Cancellation Fees
          </div>
          <p className="text-xs text-slate-400">Cancel subscriptions or recurring AutoPay mandates anytime with zero penalty.</p>
        </div>
        <div className="p-4 rounded-xl bg-slate-800/40 border border-slate-700/60">
          <div className="text-amber-400 text-sm font-bold flex items-center gap-1.5 mb-1">
            <FiClock /> 5-7 Days Payout
          </div>
          <p className="text-xs text-slate-400">Approved refunds are credited to the original payment source via Razorpay.</p>
        </div>
      </div>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">1</span>
          Subscription Cancellation Terms
        </h2>
        <p>
          You may cancel your monthly or annual subscription at any time directly through the <strong className="text-slate-100">Billing &amp; Finance &gt; Plans</strong> dashboard or by notifying our support desk:
        </p>
        <ul className="list-disc pl-5 space-y-1.5 text-slate-300">
          <li>
            <strong className="text-slate-100">Immediate vs. Period-End:</strong> Cancellation takes effect at the end of the current paid billing cycle. You will retain full access to all paid plan features and quotas until the end of the billing period.
          </li>
          <li>
            <strong className="text-slate-100">AutoPay Mandates:</strong> Cancelling your plan or revoking an AutoPay mandate immediately stops all future automatic renewals and payment attempts.
          </li>
          <li>
            <strong className="text-slate-100">No Cancellation Penalty:</strong> UnifyDesk never charges early termination or cancellation fees.
          </li>
        </ul>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">2</span>
          Refund Eligibility Criteria
        </h2>
        <div className="space-y-3">
          <div className="p-3.5 rounded-xl bg-slate-800/30 border border-slate-700/50">
            <h3 className="font-semibold text-slate-100 text-sm">7-Day New Subscription Money-Back Guarantee</h3>
            <p className="text-xs sm:text-sm text-slate-300 mt-1">
              If you subscribe to a paid plan for the first time and find that UnifyDesk does not suit your business workflow, you may request a 100% full refund within 7 calendar days of your initial payment, provided total platform messaging usage is under 100 messages.
            </p>
          </div>

          <div className="p-3.5 rounded-xl bg-slate-800/30 border border-slate-700/50">
            <h3 className="font-semibold text-slate-100 text-sm">Prepaid Communications Wallet Balances</h3>
            <p className="text-xs sm:text-sm text-slate-300 mt-1">
              Unused real money balances added to your prepaid communications wallet (via one-time recharge or Auto-Recharge) are refundable upon formal account closure, minus any promotional bonus credits and applicable gateway processing charges.
            </p>
          </div>

          <div className="p-3.5 rounded-xl bg-slate-800/30 border border-slate-700/50">
            <h3 className="font-semibold text-slate-100 text-sm">Duplicate Charges &amp; Technical Billing Errors</h3>
            <p className="text-xs sm:text-sm text-slate-300 mt-1">
              If you are charged twice for the same transaction due to a network glitch or gateway timeout, the duplicate charge will be automatically identified and refunded in full within 24 to 48 hours.
            </p>
          </div>
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">3</span>
          Non-Refundable Circumstances
        </h2>
        <p>Refunds cannot be issued under the following scenarios:</p>
        <ul className="list-disc pl-5 space-y-1.5 text-slate-300">
          <li>Carrier, telecom, or WhatsApp Business API usage charges already consumed by dispatched messages.</li>
          <li>Account suspension or termination resulting from violation of Anti-Spam laws, TRAI regulations, or fraudulent activities.</li>
          <li>Refund requests raised beyond 7 days from the transaction date for active recurring subscriptions.</li>
          <li>Promotional referral bonuses, beta discounts, or complimentary credits.</li>
        </ul>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">4</span>
          Refund Processing &amp; Payout Timelines
        </h2>
        <p>
          All approved refunds are initiated through our payment aggregator, <strong className="text-slate-100">Razorpay</strong>, back to the original method of payment (UPI ID, Debit/Credit Card, or Net Banking bank account):
        </p>
        <ul className="list-disc pl-5 space-y-1.5 text-slate-300">
          <li><strong className="text-slate-100">Approval &amp; Initiation:</strong> Within 24-48 business hours of ticket verification.</li>
          <li><strong className="text-slate-100">Bank Credit Timeline:</strong> Typically 5 to 7 working days, depending on your card issuer or banking institution. For UPI transactions, funds often credit within 24-48 hours.</li>
          <li>You will receive an automated confirmation email with the Razorpay Refund Reference Number (RRN) as soon as the refund is initiated.</li>
        </ul>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">5</span>
          How to Request a Refund
        </h2>
        <p>
          To submit a refund request, contact our billing desk with your payment details:
        </p>
        <div className="rounded-xl bg-slate-800/60 border border-slate-700/60 p-4 text-xs space-y-1.5 text-slate-300">
          <div><strong className="text-slate-100">Email:</strong> <a href="mailto:billing@unifydesk.in" className="text-indigo-400 underline">billing@unifydesk.in</a> (cc: <a href="mailto:support@unifydesk.in" className="text-indigo-400 underline">support@unifydesk.in</a>)</div>
          <div><strong className="text-slate-100">Required Details:</strong> Registered Email, Company ID, Razorpay Order/Payment ID, and Reason for Refund.</div>
          <div><strong className="text-slate-100">Helpline:</strong> <a href="/contact" className="text-indigo-400 underline">Visit our Contact Desk</a></div>
        </div>
      </section>
    </article>
  )
}
