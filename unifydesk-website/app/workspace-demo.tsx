"use client";

import { useId, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import {
  ArrowRight,
  Bell,
  Check,
  ChevronDown,
  Home,
  Inbox,
  Mail,
  MessageCircle,
  Radio,
  Search,
  ShieldCheck,
  Target,
  Users,
} from "lucide-react";

const tabs = [
  { name: "Dashboard", icon: Home },
  { name: "Inbox", icon: Inbox },
  { name: "Contacts", icon: Users },
  { name: "Broadcast", icon: Radio },
  { name: "Ad Center", icon: Target },
] as const;
type View = (typeof tabs)[number]["name"];

export function WorkspaceDemo() {
  const [view, setView] = useState<View>("Dashboard");
  const id = useId();
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);
  function navigate(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    let next = index;
    if (event.key === "ArrowDown" || event.key === "ArrowRight")
      next = (index + 1) % tabs.length;
    else if (event.key === "ArrowUp" || event.key === "ArrowLeft")
      next = (index + tabs.length - 1) % tabs.length;
    else if (event.key === "Home") next = 0;
    else if (event.key === "End") next = tabs.length - 1;
    else return;
    event.preventDefault();
    setView(tabs[next].name);
    tabRefs.current[next]?.focus();
  }
  return (
    <div className="workspaceDemo">
      <div className="demoTopbar">
        <img src="/logo-icon.svg" alt="" width={19} height={19} />
        <b>UnifyDesk</b>
        <span>Workspace preview</span>
        <span className="demoSample">SAMPLE DATA</span>
      </div>
      <div className="demoShell">
        <aside className="demoSidebar">
          <div className="demoCompany">
            <span>AS</span>
            <div>
              <b>Acme Studio</b>
              <small>Company workspace</small>
            </div>
            <ChevronDown size={13} />
          </div>
          <p className="miniKicker">WORKSPACE</p>
          <div
            className="demoTabs"
            role="tablist"
            aria-label="Workspace preview views"
            aria-orientation="vertical"
          >
            {tabs.map(({ name, icon: Icon }, index) => (
              <button
                key={name}
                ref={(element) => {
                  tabRefs.current[index] = element;
                }}
                id={`${id}-tab-${index}`}
                role="tab"
                aria-label={name}
                aria-selected={view === name}
                aria-controls={`${id}-panel`}
                tabIndex={view === name ? 0 : -1}
                onClick={() => setView(name)}
                onKeyDown={(event) => navigate(event, index)}
              >
                <Icon size={16} />
                <span>{name}</span>
              </button>
            ))}
          </div>
          <div className="demoSidebarBottom">
            <ShieldCheck size={15} />
            <span>Your team. One workspace.</span>
          </div>
        </aside>
        <div className="demoMain">
          <div className="demoToolbar">
            <span>{view}</span>
            <div>
              <Bell size={15} />
              <span className="demoAvatar">PR</span>
            </div>
          </div>
          <div
            className="demoPanel"
            id={`${id}-panel`}
            role="tabpanel"
            aria-labelledby={`${id}-tab-${tabs.findIndex((tab) => tab.name === view)}`}
            tabIndex={0}
          >
            {view === "Dashboard" && (
              <>
                <div className="demoWelcome">
                  <p className="miniKicker">YOUR WORKSPACE, CONNECTED</p>
                  <h3>Welcome, Priya</h3>
                  <p>A little context for the work ahead.</p>
                </div>
                <div className="demoStats">
                  <div>
                    <Users size={16} />
                    <small>Contacts</small>
                    <b>2,480</b>
                  </div>
                  <div>
                    <Inbox size={16} />
                    <small>Open conversations</small>
                    <b>18</b>
                  </div>
                  <div>
                    <Radio size={16} />
                    <small>Recent broadcasts</small>
                    <b>6</b>
                  </div>
                </div>
                <div className="demoActivity">
                  <div className="demoSectionLabel">
                    <b>Recent activity</b>
                    <span>This workspace</span>
                  </div>
                  {[
                    [
                      MessageCircle,
                      "A new conversation",
                      "Priya Shah · WhatsApp",
                      "Now",
                    ],
                    [
                      Radio,
                      "Product update broadcast",
                      "Email · Delivery activity",
                      "12m",
                    ],
                    [
                      Users,
                      "Customer list updated",
                      "Product updates · Contacts",
                      "1h",
                    ],
                  ].map(([Icon, title, detail, time]) => {
                    const ActivityIcon = Icon as typeof Users;
                    return (
                      <div key={String(title)}>
                        <span className="activityIcon">
                          <ActivityIcon size={16} />
                        </span>
                        <p>
                          <b>{String(title)}</b>
                          <small>{String(detail)}</small>
                        </p>
                        <span>{String(time)}</span>
                      </div>
                    );
                  })}
                </div>
              </>
            )}
            {view === "Inbox" && (
              <>
                <div className="demoViewHeading">
                  <h3>Conversations, together.</h3>
                  <p>Customer context stays close to the thread.</p>
                </div>
                <div className="demoConversation">
                  <div className="demoConversationTitle">
                    <span className="demoAvatar">PS</span>
                    <div>
                      <b>Priya Shah</b>
                      <small>
                        <MessageCircle size={11} />
                        WhatsApp · Sample conversation
                      </small>
                    </div>
                    <span className="demoTag">Open</span>
                  </div>
                  <div className="demoMessage">
                    <p>Hi! Can you tell me more about the campaign options?</p>
                    <small>10:24 AM</small>
                  </div>
                  <div className="demoMessage outgoing">
                    <p>
                      Of course. We can help you find the right workflow for
                      your team.
                    </p>
                    <small>
                      10:26 AM <Check size={11} />
                    </small>
                  </div>
                  <div className="demoContextNote">
                    <Users size={15} />
                    <div>
                      <b>Customer context</b>
                      <small>Product updates list · Email opted in</small>
                    </div>
                  </div>
                </div>
              </>
            )}
            {view === "Contacts" && (
              <>
                <div className="demoViewHeading">
                  <h3>People, with context.</h3>
                  <p>Profiles, audiences and channel-level consent.</p>
                </div>
                <div className="demoFilter">
                  <Search size={14} />
                  <span>Product updates list</span>
                  <span className="demoTag">3 sample contacts</span>
                </div>
                <div className="demoTableWrap">
                  <table className="demoTable">
                    <thead>
                      <tr>
                        <th>Contact</th>
                        <th>Email consent</th>
                      </tr>
                    </thead>
                    <tbody>
                      {[
                        {
                          name: "Priya Shah",
                          email: "priya@example.com",
                          consent: true,
                        },
                        {
                          name: "Daniel Lee",
                          email: "daniel@example.com",
                          consent: true,
                        },
                        {
                          name: "Maya Rao",
                          email: "maya@example.com",
                          consent: false,
                        },
                      ].map(({ name, email, consent }) => (
                        <tr key={name}>
                          <td>
                            <b>{name}</b>
                            <small>{email}</small>
                          </td>
                          <td>
                            <span
                              className={consent ? "consentTag" : "unknownTag"}
                            >
                              {consent ? "Opted in" : "Unknown"}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <p className="demoFootnote">
                  <ShieldCheck size={13} />
                  Consent belongs to the customer and channel.
                </p>
              </>
            )}
            {view === "Broadcast" && (
              <>
                <div className="demoViewHeading">
                  <h3>Make the next message count.</h3>
                  <p>Preview the audience before sending.</p>
                </div>
                <article className="demoCampaign">
                  <span className="demoTag">Draft · Sample campaign</span>
                  <h4>Your next product update</h4>
                  <div>
                    <span>
                      <Mail size={15} />
                      Email
                    </span>
                    <span>Product updates list</span>
                  </div>
                  <dl>
                    <div>
                      <dt>Audience preview</dt>
                      <dd>872 recipients</dd>
                    </div>
                    <div>
                      <dt>Consent rule</dt>
                      <dd>Email opted in</dd>
                    </div>
                  </dl>
                  <ol className="demoCampaignSteps">
                    <li>
                      <Check size={13} />
                      Audience
                    </li>
                    <li>
                      <Check size={13} />
                      Preview
                    </li>
                    <li>
                      Ready to review <ArrowRight size={13} />
                    </li>
                  </ol>
                </article>
                <p className="demoFootnote">
                  Illustrative workflow. No messages are sent from this preview.
                </p>
              </>
            )}
            {view === "Ad Center" && (
              <>
                <div className="demoViewHeading">
                  <h3>Your next growth workflow.</h3>
                  <p>Google and Meta accounts in one Ad Center.</p>
                </div>
                <div className="demoAdAccounts">
                  <div>
                    <Target size={20} />
                    <b>Google Ads</b>
                    <span className="demoTag">Sample account</span>
                  </div>
                  <div>
                    <Target size={20} />
                    <b>Meta Ads</b>
                    <span className="demoTag">Sample account</span>
                  </div>
                </div>
                <article className="demoCampaign">
                  <span className="miniKicker">CAMPAIGN WORKSPACE</span>
                  <h4>Autumn acquisition</h4>
                  <dl>
                    <div>
                      <dt>Objective</dt>
                      <dd>Leads</dd>
                    </div>
                    <div>
                      <dt>Stage</dt>
                      <dd>Campaign setup</dd>
                    </div>
                    <div>
                      <dt>Provider spend</dt>
                      <dd>Billed separately</dd>
                    </div>
                  </dl>
                </article>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
