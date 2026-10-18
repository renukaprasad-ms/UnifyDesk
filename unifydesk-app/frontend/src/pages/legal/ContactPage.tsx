import { useState } from 'react'
import {
  FiCheckCircle,
  FiClock,
  FiMail,
  FiMapPin,
  FiMessageSquare,
  FiPhone,
  FiSend,
  FiShield,
} from 'react-icons/fi'

export default function ContactPage() {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [subject, setSubject] = useState('General Inquiry')
  const [message, setMessage] = useState('')
  const [submitted, setSubmitted] = useState(false)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitted(true)
  }

  return (
    <article className="prose prose-invert max-w-none space-y-8 text-slate-300 leading-relaxed text-sm sm:text-base">
      <div className="border-b border-slate-800 pb-6">
        <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
          Contact Us
        </h1>
        <p className="mt-2 text-slate-400 text-sm">
          We are here to assist you with onboarding, technical support, billing inquiries, or compliance questions. Our dedicated team is based in Bengaluru, Karnataka, India.
        </p>
      </div>

      {/* Quick Contact Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 not-prose">
        <div className="p-5 rounded-2xl bg-slate-800/40 border border-slate-700/60 flex flex-col justify-between">
          <div>
            <div className="w-10 h-10 rounded-xl bg-indigo-500/15 text-indigo-400 flex items-center justify-center mb-3">
              <FiMail className="text-lg" />
            </div>
            <h3 className="font-bold text-white text-sm">Email Support</h3>
            <p className="text-xs text-slate-400 mt-1">General &amp; Technical Support</p>
          </div>
          <div className="mt-4">
            <a
              href="mailto:support@unifydesk.in"
              className="text-xs sm:text-sm font-semibold text-indigo-400 hover:text-indigo-300 underline block"
            >
              support@unifydesk.in
            </a>
            <a
              href="mailto:billing@unifydesk.in"
              className="text-xs text-slate-400 hover:text-slate-200 mt-1 block"
            >
              billing@unifydesk.in
            </a>
          </div>
        </div>

        <div className="p-5 rounded-2xl bg-slate-800/40 border border-slate-700/60 flex flex-col justify-between">
          <div>
            <div className="w-10 h-10 rounded-xl bg-emerald-500/15 text-emerald-400 flex items-center justify-center mb-3">
              <FiPhone className="text-lg" />
            </div>
            <h3 className="font-bold text-white text-sm">Phone &amp; WhatsApp</h3>
            <p className="text-xs text-slate-400 mt-1">Direct merchant helpline</p>
          </div>
          <div className="mt-4">
            <span className="text-xs sm:text-sm font-semibold text-emerald-400 block">
              +91 97412 12961
            </span>
            <span className="text-[11px] text-slate-400 mt-0.5 block">
              Toll &amp; WhatsApp Support Available
            </span>
          </div>
        </div>

        <div className="p-5 rounded-2xl bg-slate-800/40 border border-slate-700/60 flex flex-col justify-between">
          <div>
            <div className="w-10 h-10 rounded-xl bg-cyan-500/15 text-cyan-400 flex items-center justify-center mb-3">
              <FiClock className="text-lg" />
            </div>
            <h3 className="font-bold text-white text-sm">Operating Hours</h3>
            <p className="text-xs text-slate-400 mt-1">Standard business hours</p>
          </div>
          <div className="mt-4 text-xs text-slate-300">
            <div><strong className="text-slate-100">Mon - Fri:</strong> 9:00 AM – 7:00 PM IST</div>
            <div><strong className="text-slate-100">Saturday:</strong> 10:00 AM – 4:00 PM IST</div>
            <div className="text-[11px] text-emerald-400 mt-1">24/7 Critical System Monitoring</div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8 pt-4">
        {/* Office & Corporate Details */}
        <div className="space-y-6">
          <section className="space-y-3">
            <h2 className="text-lg font-bold text-white flex items-center gap-2">
              <FiMapPin className="text-indigo-400" /> Operating &amp; Registered Address
            </h2>
            <div className="rounded-xl bg-slate-800/50 border border-slate-700/60 p-4 text-xs text-slate-300 space-y-2 leading-relaxed">
              <div className="font-semibold text-slate-100 text-sm">UnifyDesk Technologies</div>
              <p>
                No. 42, 3rd Floor, Outer Ring Road, Kadubeesanahalli, Marathahalli,
                <br />
                Bengaluru, Karnataka - 560103, India.
              </p>
              <div className="pt-1 text-[11px] text-slate-400 border-t border-slate-700/60">
                Country of Operation: <strong className="text-slate-200">India</strong>
              </div>
            </div>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-bold text-white flex items-center gap-2">
              <FiShield className="text-indigo-400" /> Regulatory &amp; Grievance Officer
            </h2>
            <div className="rounded-xl bg-slate-800/50 border border-slate-700/60 p-4 text-xs text-slate-300 space-y-1.5 leading-relaxed">
              <div><strong className="text-slate-100">Name:</strong> Renuka Prasad</div>
              <div><strong className="text-slate-100">Designation:</strong> Grievance Officer &amp; Operations Lead</div>
              <div><strong className="text-slate-100">Email:</strong> <a href="mailto:grievance@unifydesk.in" className="text-indigo-400 underline">grievance@unifydesk.in</a></div>
              <div><strong className="text-slate-100">SLA:</strong> Acknowledgement within 24h; Resolution within 48h.</div>
            </div>
          </section>
        </div>

        {/* Interactive Contact Form */}
        <div className="not-prose">
          <div className="rounded-2xl bg-slate-900 border border-slate-800 p-6 shadow-xl">
            <h3 className="text-base font-bold text-white flex items-center gap-2 mb-1">
              <FiMessageSquare className="text-indigo-400" /> Send us a Message
            </h3>
            <p className="text-xs text-slate-400 mb-5">
              Have a question about our platform or billing? Fill out the form below.
            </p>

            {submitted ? (
              <div className="p-6 rounded-xl bg-emerald-500/10 border border-emerald-500/25 text-center space-y-2 animate-scale-in">
                <FiCheckCircle className="h-10 w-10 text-emerald-400 mx-auto" />
                <h4 className="text-base font-bold text-white">Message Received!</h4>
                <p className="text-xs text-slate-300">
                  Thank you for contacting UnifyDesk. Our support desk will reply to{' '}
                  <strong className="text-white">{email}</strong> within 4 business hours.
                </p>
                <button
                  type="button"
                  onClick={() => {
                    setSubmitted(false)
                    setMessage('')
                  }}
                  className="mt-3 text-xs font-semibold text-indigo-400 hover:text-indigo-300 underline"
                >
                  Send another inquiry
                </button>
              </div>
            ) : (
              <form onSubmit={handleSubmit} className="space-y-4 text-xs">
                <div>
                  <label className="block text-slate-300 font-medium mb-1">Your Full Name</label>
                  <input
                    type="text"
                    required
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="e.g. Rahul Sharma"
                    className="w-full rounded-xl bg-slate-950 border border-slate-700 px-3.5 py-2.5 text-slate-100 focus:outline-none focus:border-indigo-500 transition-colors"
                  />
                </div>

                <div>
                  <label className="block text-slate-300 font-medium mb-1">Work Email Address</label>
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="name@company.com"
                    className="w-full rounded-xl bg-slate-950 border border-slate-700 px-3.5 py-2.5 text-slate-100 focus:outline-none focus:border-indigo-500 transition-colors"
                  />
                </div>

                <div>
                  <label className="block text-slate-300 font-medium mb-1">Inquiry Subject</label>
                  <select
                    value={subject}
                    onChange={(e) => setSubject(e.target.value)}
                    className="w-full rounded-xl bg-slate-950 border border-slate-700 px-3.5 py-2.5 text-slate-100 focus:outline-none focus:border-indigo-500 transition-colors"
                  >
                    <option value="General Inquiry">General Inquiry</option>
                    <option value="Billing & Invoicing">Billing &amp; Invoicing</option>
                    <option value="AutoPay & Mandates">AutoPay &amp; Mandates</option>
                    <option value="WhatsApp Business API">WhatsApp Business API</option>
                    <option value="Refund Request">Refund Request</option>
                    <option value="Technical Support">Technical Support</option>
                  </select>
                </div>

                <div>
                  <label className="block text-slate-300 font-medium mb-1">Message</label>
                  <textarea
                    rows={4}
                    required
                    value={message}
                    onChange={(e) => setMessage(e.target.value)}
                    placeholder="Describe how we can help your business..."
                    className="w-full rounded-xl bg-slate-950 border border-slate-700 px-3.5 py-2.5 text-slate-100 focus:outline-none focus:border-indigo-500 transition-colors resize-none"
                  />
                </div>

                <button
                  type="submit"
                  className="w-full inline-flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-indigo-500 to-purple-600 px-5 py-3 text-xs sm:text-sm font-semibold text-white shadow-lg shadow-indigo-500/25 hover:from-indigo-600 hover:to-purple-700 transition-all cursor-pointer"
                >
                  <FiSend /> Send Inquiry
                </button>
              </form>
            )}
          </div>
        </div>
      </div>
    </article>
  )
}
