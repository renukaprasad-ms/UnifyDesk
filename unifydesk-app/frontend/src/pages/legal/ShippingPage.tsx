import { FiCalendar, FiCheckCircle, FiClock, FiTruck, FiZap } from 'react-icons/fi'

export default function ShippingPage() {
  return (
    <article className="prose prose-invert max-w-none space-y-8 text-slate-300 leading-relaxed text-sm sm:text-base">
      <div className="border-b border-slate-800 pb-6">
        <div className="flex items-center gap-2 text-xs text-indigo-400 font-semibold mb-2">
          <FiCalendar />
          <span>Last Updated: October 17, 2026</span>
        </div>
        <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
          Shipping and Delivery Policy
        </h1>
        <p className="mt-2 text-slate-400 text-sm">
          This policy outlines the delivery, digital provisioning, and fulfillment terms for software services, subscription licenses, and communications credits provided by UnifyDesk Technologies.
        </p>
      </div>

      {/* Key Delivery Facts */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <div className="p-4 rounded-xl bg-slate-800/40 border border-slate-700/60">
          <div className="text-indigo-400 text-sm font-bold flex items-center gap-1.5 mb-1">
            <FiZap /> 100% Digital Delivery
          </div>
          <p className="text-xs text-slate-400">All services are delivered electronically via cloud infrastructure. No physical shipping.</p>
        </div>
        <div className="p-4 rounded-xl bg-slate-800/40 border border-slate-700/60">
          <div className="text-emerald-400 text-sm font-bold flex items-center gap-1.5 mb-1">
            <FiClock /> Instant Provisioning
          </div>
          <p className="text-xs text-slate-400">Subscriptions and wallet credits activate within seconds of payment confirmation.</p>
        </div>
        <div className="p-4 rounded-xl bg-slate-800/40 border border-slate-700/60">
          <div className="text-cyan-400 text-sm font-bold flex items-center gap-1.5 mb-1">
            <FiTruck /> ₹0 Shipping Cost
          </div>
          <p className="text-xs text-slate-400">Zero shipping, packaging, handling, or courier delivery fees apply to any plan.</p>
        </div>
      </div>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">1</span>
          Nature of Goods &amp; Services
        </h2>
        <p>
          UnifyDesk is a pure Software-as-a-Service (SaaS) provider. We provide cloud-based omnichannel communication software, marketing automations, and developer APIs.
        </p>
        <p>
          <strong className="text-slate-100">No Physical Products:</strong> We do not manufacture, package, ship, or deliver physical hardware, paper documentation, or physical media (such as CDs or USB drives). All services are accessible exclusively online via web browser or RESTful API.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">2</span>
          Digital Delivery Mechanism &amp; Timelines
        </h2>
        <div className="space-y-3">
          <div className="p-4 rounded-xl bg-slate-800/30 border border-slate-700/50">
            <h3 className="font-semibold text-slate-100 text-sm flex items-center gap-2">
              <FiCheckCircle className="text-emerald-400" /> Subscription Plans &amp; Feature Upgrades
            </h3>
            <p className="text-xs sm:text-sm text-slate-300 mt-1">
              Delivered immediately upon successful payment authorization through Razorpay. Your company workspace is instantly upgraded, license limits are raised, and all plan capabilities are unlocked in real time (typically under 10 seconds).
            </p>
          </div>

          <div className="p-4 rounded-xl bg-slate-800/30 border border-slate-700/50">
            <h3 className="font-semibold text-slate-100 text-sm flex items-center gap-2">
              <FiCheckCircle className="text-emerald-400" /> Communications Wallet Top-Ups
            </h3>
            <p className="text-xs sm:text-sm text-slate-300 mt-1">
              Prepaid balance top-ups (whether manual one-time recharges or AutoPay auto-recharges) are credited to your active wallet balance in real time upon gateway confirmation. Balance updates are broadcast to your dashboard via WebSocket instantly.
            </p>
          </div>

          <div className="p-4 rounded-xl bg-slate-800/30 border border-slate-700/50">
            <h3 className="font-semibold text-slate-100 text-sm flex items-center gap-2">
              <FiCheckCircle className="text-emerald-400" /> Telecom / WhatsApp Business Account Setup
            </h3>
            <p className="text-xs sm:text-sm text-slate-300 mt-1">
              Third-party channel integrations (Meta WhatsApp Cloud API registration and TRAI DLT registration) require identity validation by respective telecom/Meta providers. These workflows typically complete within 1 to 3 business days subject to official carrier verification.
            </p>
          </div>
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">3</span>
          Shipping Charges &amp; Taxes
        </h2>
        <p>
          Because our services are fulfilled purely through cloud infrastructure:
        </p>
        <ul className="list-disc pl-5 space-y-1 text-slate-300">
          <li><strong className="text-slate-100">Shipping Fees:</strong> ₹0.00 (Zero shipping or courier fees).</li>
          <li><strong className="text-slate-100">Customs / Duties:</strong> None applicable.</li>
          <li><strong className="text-slate-100">Delivery Confirmation:</strong> An official digital tax invoice and payment receipt are automatically emailed to your registered billing address immediately after payment completion.</li>
        </ul>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">4</span>
          Product Exchange Policy
        </h2>
        <p>
          Since UnifyDesk delivers digital SaaS subscriptions, traditional physical exchanges are not applicable. Instead, customers may exchange or transition service tiers through:
        </p>
        <ul className="list-disc pl-5 space-y-1.5 text-slate-300">
          <li>
            <strong className="text-slate-100">Plan Upgrades:</strong> You may exchange your current plan for a higher tier at any time. Any remaining days on your current billing cycle are calculated and credited against the new tier on a prorated basis.
          </li>
          <li>
            <strong className="text-slate-100">Plan Downgrades:</strong> You can switch to a lower tier anytime, which will take effect at the conclusion of your current prepaid billing cycle without loss of active prepaid features.
          </li>
        </ul>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg sm:text-xl font-bold text-white flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/15 text-indigo-400 text-xs font-bold">5</span>
          Delivery Inquiries &amp; Support
        </h2>
        <p>
          If your account has been charged but your plan access or wallet balance has not reflected within 15 minutes, please reach out with your Razorpay payment ID:
        </p>
        <div className="rounded-xl bg-slate-800/60 border border-slate-700/60 p-4 text-xs space-y-1 text-slate-300">
          <div><strong className="text-slate-100">Fulfillment Support:</strong> <a href="mailto:support@unifydesk.in" className="text-indigo-400 underline">support@unifydesk.in</a></div>
          <div><strong className="text-slate-100">Billing Desk:</strong> <a href="mailto:billing@unifydesk.in" className="text-indigo-400 underline">billing@unifydesk.in</a></div>
          <div><strong className="text-slate-100">Portal:</strong> <a href="/contact" className="text-indigo-400 underline">Contact Support</a></div>
        </div>
      </section>
    </article>
  )
}
