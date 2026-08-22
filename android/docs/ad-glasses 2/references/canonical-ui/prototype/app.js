const HERO = '../assets/ad-glasses-hero-v1.png';

const iconPaths = {
  home: '<path d="M3 11.5 12 4l9 7.5v8a1.5 1.5 0 0 1-1.5 1.5h-5v-6h-5v6h-5A1.5 1.5 0 0 1 3 19.5z"/>',
  assistant: '<path d="M8 3h8l1 3 3 1v10l-3 1-1 3H8l-1-3-3-1V7l3-1z"/><path d="M9 10h.01M15 10h.01M9 15c2 1 4 1 6 0"/>',
  library: '<path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H11v17H6.5A2.5 2.5 0 0 0 4 22zM20 5.5A2.5 2.5 0 0 0 17.5 3H13v17h4.5A2.5 2.5 0 0 1 20 22z"/>',
  automation: '<path d="m13 2-8 12h7l-1 8 8-12h-7z"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3A1.7 1.7 0 0 0 10 3V2.8h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1z"/>',
  back: '<path d="m15 18-6-6 6-6"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  chevron: '<path d="m9 18 6-6-6-6"/>',
  mic: '<rect x="9" y="3" width="6" height="12" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/>',
  camera: '<path d="M4 7h4l2-2h4l2 2h4v12H4z"/><circle cx="12" cy="13" r="4"/>',
  sync: '<path d="M20 7h-5V2M4 17h5v5M19 11a7 7 0 0 0-12-5l-2 2M5 13a7 7 0 0 0 12 5l2-2"/>',
  record: '<circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="3" fill="currentColor" stroke="none"/>',
  battery: '<rect x="3" y="7" width="17" height="10" rx="2"/><path d="M22 10v4M7 10v4"/>',
  storage: '<path d="M4 6c0-2 16-2 16 0v12c0 2-16 2-16 0z"/><path d="M4 6c0 2 16 2 16 0M4 12c0 2 16 2 16 0"/>',
  bluetooth: '<path d="m6 7 11 10-5 4V3l5 4L6 17"/>',
  phone: '<rect x="7" y="2" width="10" height="20" rx="2"/><path d="M11 18h2"/>',
  bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>',
  image: '<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8" cy="9" r="1.5"/><path d="m21 15-5-5L5 20"/>',
  globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a15 15 0 0 1 0 18M12 3a15 15 0 0 0 0 18"/>',
  attach: '<path d="m20 11-8.5 8.5a5 5 0 0 1-7-7L14 3a3.5 3.5 0 0 1 5 5l-9.5 9.5a2 2 0 0 1-3-3L15 6"/>',
  send: '<path d="m22 2-7 20-4-9-9-4zM22 2 11 13"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
  alert: '<path d="M12 3 2.5 20h19z"/><path d="M12 9v4M12 17h.01"/>',
  play: '<path d="m8 5 11 7-11 7z"/>',
  pause: '<path d="M8 5v14M16 5v14"/>',
  lock: '<rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>',
  file: '<path d="M6 2h8l4 4v16H6zM14 2v5h5"/>',
  trash: '<path d="M4 7h16M9 7V4h6v3M7 7l1 14h8l1-14M10 11v6M14 11v6"/>',
  cloud: '<path d="M7 19h11a4 4 0 0 0 .7-7.9A7 7 0 0 0 5.2 9.5 4.8 4.8 0 0 0 7 19z"/>',
  shield: '<path d="M12 3 5 6v5c0 5 3 8 7 10 4-2 7-5 7-10V6z"/><path d="m9 12 2 2 4-4"/>',
  more: '<circle cx="12" cy="5" r="1" fill="currentColor"/><circle cx="12" cy="12" r="1" fill="currentColor"/><circle cx="12" cy="19" r="1" fill="currentColor"/>'
};

function icon(name) {
  return `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${iconPaths[name] || iconPaths.chevron}</svg>`;
}

function brand() {
  return `<div class="brand">
    <svg class="brand-mark" viewBox="0 0 32 22" aria-hidden="true">
      <rect x="2" y="5" width="11" height="10" rx="4" fill="none" stroke="#111318" stroke-width="2"/>
      <rect x="19" y="5" width="11" height="10" rx="4" fill="none" stroke="#111318" stroke-width="2"/>
      <path d="M13 9c2-2 4-2 6 0" fill="none" stroke="#111318" stroke-width="2" stroke-linecap="round"/>
      <circle cx="27" cy="7" r="2" fill="#3156d3"/>
    </svg>
    <span class="brand-name">AD Glasses</span>
  </div>`;
}

function topbar(title, options = {}) {
  if (options.detail) {
    return `<header class="topbar detail">
      <button class="icon-button" data-route="${options.back || 'home'}" aria-label="Back">${icon('back')}</button>
      <div class="top-title">${title}</div>
      ${options.action ? `<button class="icon-button" aria-label="${options.action}">${icon(options.actionIcon || 'more')}</button>` : ''}
    </header>`;
  }
  return `<header class="topbar">${brand()}<button class="icon-button" data-route="settings" aria-label="Settings">${icon('settings')}</button></header>`;
}

const navItems = [
  ['home', 'Home', 'home'], ['assistant', 'Assistant', 'assistant'], ['library', 'Library', 'library'], ['automations', 'Automations', 'automation']
];

function bottomNav(active) {
  return `<nav class="bottom-nav" aria-label="Primary">${navItems.map(([route, label, glyph]) =>
    `<button class="nav-item ${active === route ? 'active' : ''}" data-route="${route}" ${active === route ? 'aria-current="page"' : ''}>${icon(glyph)}<span>${label}</span></button>`
  ).join('')}</nav>`;
}

function shell(active, body, options = {}) {
  return `<div class="phone ${options.focused ? 'focused' : ''}">${options.header === false ? '' : topbar(options.title || '', options)}${body}${options.focused ? '' : bottomNav(active)}</div>`;
}

function statusChip(text, tone = 'blue') { return `<span class="chip ${tone}"><span class="dot"></span>${text}</span>`; }

function quick(label, glyph, route, primary = false) {
  return `<button class="quick-action ${primary ? 'primary' : ''}" data-route="${route}">${icon(glyph)}<span>${label}</span></button>`;
}

function permissionRow(glyph, title, subtitle, status, tone = '') {
  return `<div class="card row"><div class="leading-icon ${tone}">${icon(glyph)}</div><div class="grow"><div class="row-title">${title}</div><div class="row-subtitle">${subtitle}</div></div><span class="chip ${status === 'Allowed' ? 'green' : ''}">${status}</span></div>`;
}

const screens = {};

screens.welcome = () => shell('', `<section class="content center" style="padding-top:34px">
  ${brand()}
  <div class="hero-stage" style="min-height:315px"><img class="hero-glasses" src="${HERO}" alt="Brand-neutral smart glasses"></div>
  <h1>Your glasses.<br>Your AI. Your data.</h1>
  <p class="lead">Bring useful intelligence to compatible smart glasses through your phone—on your terms.</p>
  <div class="button-stack"><button class="button block" data-route="readiness">Set up my glasses</button><button class="button secondary block" data-route="home">Explore without pairing</button></div>
  <div class="action-row" style="justify-content:center;margin-top:16px"><button class="button ghost">Supported devices</button><button class="button ghost" data-route="privacy">Privacy</button></div>
</section>`, { focused: true, header: false });

screens.readiness = () => shell('', `${topbar('Glasses setup', { detail: true, back: 'welcome' })}<section class="content">
  <div class="progress-steps"><div class="progress-step active">Prepare</div><div class="progress-step">Find</div><div class="progress-step">Confirm</div></div>
  <div class="eyebrow">Step 1 of 3</div><h1>Ready to connect</h1><p>We ask only for what is needed now. Other permissions appear when a feature needs them.</p>
  <div class="permission-list">
    ${permissionRow('bluetooth','Nearby devices','Find and connect compatible glasses','Required')}
    ${permissionRow('bell','Notifications','Show connection and active-session status','Later','neutral')}
    ${permissionRow('mic','Microphone','Requested when you start voice or recording','Later','neutral')}
    ${permissionRow('image','Photos and media','Requested when you capture, import or export','Later','neutral')}
  </div>
  <div class="button-stack"><button class="button block" data-route="brands">Allow nearby devices</button></div>
</section>`, { focused: true, header: false });

screens.brands = () => shell('', `${topbar('Choose glasses', { detail: true, back: 'readiness' })}<section class="content">
  <h1>Which glasses are you setting up?</h1><p>This helps AD Glasses choose the correct connection path. You can change it before connecting.</p>
  <div class="brand-list">
    <button class="brand-row selected" data-route="scan"><div class="leading-icon">${icon('camera')}</div><div class="grow"><div class="row-title">HeyCyan compatible</div><div class="row-subtitle">Camera, onboard media and local Sync</div></div><div class="maturity accent">Primary</div></button>
    <button class="brand-row"><div class="leading-icon neutral">${icon('camera')}</div><div class="grow"><div class="row-title">Eyevue</div><div class="row-subtitle">Runtime-gated device features</div></div><div class="maturity">Experimental</div></button>
    <button class="brand-row"><div class="leading-icon neutral">${icon('camera')}</div><div class="grow"><div class="row-title">Meta Ray-Ban</div><div class="row-subtitle">Android DAT availability required</div></div><div class="maturity">Experimental</div></button>
    <button class="brand-row"><div class="leading-icon neutral">${icon('assistant')}</div><div class="grow"><div class="row-title">Meizu MYVU / Star Air</div><div class="row-subtitle">Display and notification features</div></div><div class="maturity">Experimental</div></button>
    <button class="brand-row"><div class="leading-icon neutral">${icon('mic')}</div><div class="grow"><div class="row-title">Generic audio glasses</div><div class="row-subtitle">Phone audio and microphone routing only</div></div><div class="maturity">Limited</div></button>
    <button class="brand-row"><div class="leading-icon neutral">?</div><div class="grow"><div class="row-title">I’m not sure</div><div class="row-subtitle">Identify safely without sending vendor commands</div></div>${icon('chevron')}</button>
  </div>
</section>`, { focused: true, header: false });

screens.scan = () => shell('', `${topbar('Find glasses', { detail: true, back: 'brands' })}<section class="content">
  <div class="progress-steps"><div class="progress-step done">Prepare</div><div class="progress-step active">Find</div><div class="progress-step">Confirm</div></div>
  <div class="scan-orbit"><div class="scan-mark">${icon('bluetooth')}</div></div>
  <div class="center"><h1>Looking nearby</h1><p>Keep your glasses powered on and close to this phone.</p></div>
  <div class="section-title"><h2>Found nearby</h2><button class="section-link">Stop</button></div>
  <button class="brand-row selected" data-route="confirm"><div class="leading-icon">${icon('camera')}</div><div class="grow"><div class="row-title">HeyCyan-compatible glasses</div><div class="row-subtitle">Identifier ending • 7A3C · Strong signal</div></div>${icon('chevron')}</button>
  <button class="brand-row"><div class="leading-icon neutral">?</div><div class="grow"><div class="row-title">Unknown BLE device</div><div class="row-subtitle">Identifier ending • 19B2 · Weak signal</div></div>${icon('chevron')}</button>
  <div class="button-stack"><button class="button secondary block">I can’t find my glasses</button></div>
</section>`, { focused: true, header: false });

screens.confirm = () => shell('', `<div class="sheet-backdrop"><section class="sheet">
  <div class="grabber"></div><div class="confirm-hero"><img src="${HERO}" alt="Selected smart glasses"></div>
  <div class="center">${statusChip('Primary support','green')}<h1 style="margin-top:12px">Connect these glasses?</h1><p>HeyCyan-compatible · Identifier ending 7A3C</p></div>
  <div class="card"><div class="eyebrow">Available after connection</div><div class="row" style="margin-top:12px"><span class="chip">Camera</span><span class="chip">Onboard media</span><span class="chip">Local Sync</span></div><p class="helper" style="margin:12px 0 0">Capabilities will be read from the device. Unsupported controls stay hidden.</p></div>
  <div class="button-stack"><button class="button block" data-route="home">Connect glasses</button><button class="button secondary block" data-route="scan">Cancel</button></div>
</section></div>`, { focused: true, header: false });

function deviceStage(state = 'Connected') {
  return `<section class="open-card"><div class="hero-stage"><img class="hero-glasses" src="${HERO}" alt="Connected smart glasses"></div><div class="device-name">${statusChip(state, state === 'Connected' ? 'green' : 'amber')}<h2>My glasses</h2><div class="metrics"><span class="metric">${icon('battery')}82%</span><span class="metric">${icon('storage')}61% free</span></div></div></section>`;
}

function activityBanner(kind = 'translation') {
  const map = {
    translation: ['Live translation', 'Spanish → English · Listening', 'assistant-live'],
    sync: ['Syncing media', '12 of 27 items · 45%', 'sync'],
    recording: ['Meeting recording', '18:42 · Phone microphone', 'automation-detail'],
    firmware: ['Firmware update', 'Wi-Fi component · 45%', 'firmware']
  };
  const [title, subtitle, route] = map[kind];
  return `<button class="activity-banner" data-route="${route}" style="width:100%;border:0;text-align:left"><span class="pulse"></span><div class="grow"><div class="row-title">${title}</div><div class="row-subtitle">${subtitle}</div></div>${icon('chevron')}</button>`;
}

screens.home = () => shell('home', `${topbar()}<section class="content">
  ${deviceStage()}
  <div class="section-title"><h2>What do you want to do?</h2></div>
  <div class="quick-grid">${quick('Ask','mic','assistant',true)}${quick('Capture','camera','assistant')}${quick('Sync','sync','sync')}${quick('Record','record','automation-detail')}</div>
  <div class="section-title"><h2>Today</h2><button class="section-link" data-route="library">Open Library</button></div>
  <div class="recent-list">
    <button class="recent-item row" data-route="content"><div class="thumb">${icon('image')}</div><div class="grow"><div class="row-title">Saved visual answer</div><div class="row-subtitle">How this control panel works · 10:42</div></div>${icon('chevron')}</button>
    <button class="recent-item row"><div class="leading-icon neutral">${icon('file')}</div><div class="grow"><div class="row-title">Meeting summary</div><div class="row-subtitle">4 decisions · 3 action items</div></div>${icon('chevron')}</button>
  </div>
</section>`, { header: false });

screens['home-active'] = () => shell('home', `${topbar()}<section class="content">
  ${deviceStage()}
  <div class="spacer"></div>${activityBanner('translation')}
  <div class="section-title"><h2>Quick actions</h2></div>
  <div class="quick-grid">${quick('Ask','mic','assistant',true)}${quick('Capture','camera','assistant')}${quick('Sync','sync','sync')}${quick('Record','record','automation-detail')}</div>
  <div class="section-title"><h2>Recent outcomes</h2></div>
  <div class="recent-list"><button class="recent-item row"><div class="leading-icon">${icon('globe')}</div><div class="grow"><div class="row-title">Menu translation saved</div><div class="row-subtitle">Transcript · 8 minutes</div></div>${icon('chevron')}</button></div>
</section>`, { header: false });

screens['home-disconnected'] = () => shell('home', `${topbar()}<section class="content">
  <div class="hero-stage" style="opacity:.72"><img class="hero-glasses" src="${HERO}" alt="Disconnected smart glasses"></div>
  <div class="device-name">${statusChip('Disconnected','red')}<h2>My glasses</h2><p>Last seen 7 minutes ago</p></div>
  <div class="card warning-card row" style="margin-top:18px"><div class="leading-icon warning">${icon('alert')}</div><div class="grow"><div class="row-title">Connection lost</div><div class="row-subtitle">Keep the glasses nearby, then reconnect. Your saved content is unaffected.</div></div></div>
  <div class="button-stack"><button class="button block">Reconnect</button><button class="button secondary block" data-route="device">Open Device Center</button></div>
  <div class="section-title"><h2>Available without glasses</h2></div><div class="quick-grid">${quick('Ask','mic','assistant',true)}${quick('Library','library','library')}${quick('Notes','file','content')}${quick('Settings','settings','settings')}</div>
</section>`, { header: false });

screens.assistant = () => shell('assistant', `${topbar()}<section class="content">
  <div style="padding:18px 0 12px"><div class="eyebrow">Automatic routing</div><h1>What can I help with?</h1><p>Ask by text, voice, or camera. AD Glasses chooses on-device, your cloud, or web search when allowed.</p></div>
  <div class="suggestion-grid">
    <button class="suggestion" data-route="conversation"><div class="leading-icon">${icon('mic')}</div><h3 style="margin-top:12px">Voice</h3><p class="helper">Ask hands-free</p></button>
    <button class="suggestion" data-route="conversation"><div class="leading-icon">${icon('camera')}</div><h3 style="margin-top:12px">What I see</h3><p class="helper">Use phone or glasses</p></button>
    <button class="suggestion" data-route="assistant-live"><div class="leading-icon">${icon('record')}</div><h3 style="margin-top:12px">Live</h3><p class="helper">Continuous conversation</p></button>
    <button class="suggestion" data-route="conversation"><div class="leading-icon">${icon('assistant')}</div><h3 style="margin-top:12px">Text</h3><p class="helper">Start a conversation</p></button>
  </div>
  <div class="section-title"><h2>Try asking</h2></div>
  <button class="card row block" data-route="grounded" style="text-align:left"><div class="leading-icon neutral">${icon('globe')}</div><div class="grow"><div class="row-title">What changed in today’s Android release?</div><div class="row-subtitle">Uses web search only after your routing choice</div></div>${icon('chevron')}</button>
  <div class="section-title"><h2>Recent</h2><button class="section-link">See all</button></div>
  <button class="recent-item row" data-route="conversation" style="width:100%;text-align:left"><div class="grow"><div class="row-title">Control panel explanation</div><div class="row-subtitle">Visual question · Today</div></div>${icon('chevron')}</button>
  <div class="spacer"></div><div class="composer"><button class="icon-button" aria-label="Attach">${icon('attach')}</button><input aria-label="Message" placeholder="Ask anything…"><button class="icon-button" aria-label="Voice">${icon('mic')}</button><button class="icon-button send" data-route="conversation" aria-label="Send">${icon('send')}</button></div>
</section>`, { header: false });

screens.conversation = () => shell('', `${topbar('Conversation', { detail: true, back: 'assistant', action: 'More' })}<section class="content">
  <div class="center"><span class="chip">Today · 10:42</span></div><div class="spacer"></div>
  <div class="bubble user">What does this control panel do?</div>
  <div class="card" style="padding:8px;margin:0 0 14px 12%;"><div style="height:150px;border-radius:12px;background:linear-gradient(135deg,#ced6e0,#7f8c9e);display:grid;place-items:center;color:white">${icon('image')}<span class="chip" style="position:absolute;margin-top:95px">Captured with glasses</span></div></div>
  <div class="bubble assistant"><strong>It appears to be an industrial temperature controller.</strong><br><br>The center value is the current temperature, while the smaller value is likely the target. I can help identify the exact model if you capture its label.</div>
  <div class="action-row"><button class="chip">Read aloud</button><button class="chip">Save to Library</button><button class="chip">Details</button></div>
  <div class="spacer"></div><div class="composer" style="bottom:12px"><button class="icon-button">${icon('attach')}</button><input placeholder="Ask a follow-up…"><button class="icon-button send">${icon('send')}</button></div>
</section>`, { focused: true, header: false });

screens.grounded = () => shell('', `${topbar('Grounded answer', { detail: true, back: 'assistant', action: 'More' })}<section class="content">
  <div class="bubble user">What changed in today’s Android release?</div>
  <div class="bubble assistant"><span class="chip blue">${icon('search')} Searched the web</span><br><br><strong>The release focuses on reliability and developer tooling.</strong><br><br>Highlights include platform stability fixes, updated build tools and revised compatibility guidance. Check the linked official notes before changing a production build.</div>
  <div class="section-title"><h2>Sources</h2></div>
  <button class="source-row row" style="width:100%;text-align:left"><div class="leading-icon">1</div><div class="grow"><div class="row-title">Android Developers · Release notes</div><div class="row-subtitle">developer.android.com</div></div>${icon('chevron')}</button>
  <button class="source-row row" style="width:100%;text-align:left"><div class="leading-icon neutral">2</div><div class="grow"><div class="row-title">Android Studio · Known issues</div><div class="row-subtitle">developer.android.com</div></div>${icon('chevron')}</button>
  <div class="action-row" style="margin-top:16px"><button class="button secondary">Ask follow-up</button><button class="button secondary">Read aloud</button><button class="button secondary">Save</button></div>
  <div class="card soft-card" style="margin-top:20px"><div class="row-title">Search boundary</div><div class="row-subtitle">Your question was shared with the configured search provider. Personal Library content was not included.</div></div>
</section>`, { focused: true, header: false });

screens['assistant-live'] = () => shell('', `${topbar('Live session', { detail: true, back: 'assistant' })}<section class="content">
  <div class="live-stage"><div class="eyebrow accent">Glasses microphone · Automatic</div><h1 style="margin-top:10px">Listening</h1><div class="orb"><div class="wave"><i></i><i></i><i></i><i></i><i></i></div></div><p class="lead" style="margin-top:34px;max-width:310px">“Help me understand what I’m looking at…”</p><span class="chip blue">On device until more capability is needed</span></div>
  <div class="button-pair"><button class="button secondary">${icon('mic')} Mute</button><button class="button danger" data-route="assistant">End session</button></div>
</section>`, { focused: true, header: false });

screens.library = () => shell('library', `${topbar()}<section class="content">
  <div class="section-title" style="margin-top:8px"><h1 style="margin:0">Library</h1><button class="icon-button" aria-label="Search">${icon('search')}</button></div>
  <div class="segmented"><button class="segment active">Timeline</button><button class="segment">Collections</button></div>
  <div class="filter-row" style="margin-top:14px"><span class="chip blue">All</span><span class="chip">Photos</span><span class="chip">Videos</span><span class="chip">Audio</span><span class="chip">Notes</span><span class="chip">Memories</span></div>
  <div class="spacer"></div>${activityBanner('sync')}
  <div class="section-title"><h2>Today</h2><button class="section-link">Select</button></div>
  <div class="media-grid"><button class="media-tile" data-route="content"><span class="chip">Photo · Glasses</span><div><strong>Control panel</strong><div style="font-size:12px;opacity:.8">10:42</div></div></button><button class="media-tile"><span class="chip">Video · 0:18</span><div><strong>Workshop demo</strong><div style="font-size:12px;opacity:.8">11:15</div></div></button><button class="media-tile note"><span class="chip">Meeting</span><div><strong>Design review</strong><div style="font-size:12px;color:#6c6659">4 decisions · 3 actions</div></div></button><button class="media-tile"><span class="chip">Audio · Phone</span><div><strong>Voice note</strong><div style="font-size:12px;opacity:.8">Transcript ready</div></div></button></div>
</section>`, { header: false });

screens.content = () => shell('', `${topbar('Photo', { detail: true, back: 'library', action: 'More' })}<section class="content">
  <div style="height:300px;border-radius:20px;background:linear-gradient(145deg,#ccd5e0,#718097);display:grid;place-items:center;color:white">${icon('image')}</div>
  <div class="section-title"><div><h1 style="margin:0">Control panel</h1><p style="margin:4px 0 0">Captured with glasses · Today, 10:42</p></div></div>
  <button class="button block" data-route="conversation">${icon('assistant')} Ask about this photo</button>
  <div class="section-title"><h2>Linked answer</h2></div><div class="card"><h3>Industrial temperature controller</h3><p style="margin:0">The center value is likely the current temperature. Capture the label for exact identification.</p><button class="button ghost" data-route="conversation" style="padding-left:0">Open conversation ${icon('chevron')}</button></div>
  <div class="section-title"><h2>Details</h2></div><div class="card kv"><span>Source</span><span class="value">Glasses camera</span><span>Captured</span><span class="value">Today, 10:42</span><span>File size</span><span class="value">Available from media record</span></div>
  <div class="action-row" style="margin-top:16px"><button class="button secondary">Share</button><button class="button secondary">Add to collection</button><button class="button ghost danger">Delete</button></div>
</section>`, { focused: true, header: false });

const automationCard = (title, description, status, tone = '', route = 'automation-detail') => `<button class="automation-card" data-route="${route}" style="width:100%;text-align:left"><div class="row"><div class="leading-icon ${tone}">${icon(title.includes('Meeting') ? 'file' : title.includes('Translator') ? 'globe' : title.includes('Caption') ? 'assistant' : title.includes('Diary') ? 'camera' : title.includes('Audio') ? 'mic' : 'automation')}</div><div class="grow"><div class="row-title">${title}</div><div class="row-subtitle">${description}</div></div>${icon('chevron')}</div><div style="margin-top:11px">${statusChip(status, status === 'Ready' ? 'green' : status === 'Needs setup' ? 'amber' : 'blue')}</div></button>`;

screens.automations = () => shell('automations', `${topbar()}<section class="content">
  <div class="section-title" style="margin-top:8px"><h1 style="margin:0">Automations</h1></div>
  <div class="card row"><div class="grow"><div class="row-title">Pause passive automations</div><div class="row-subtitle">Temporarily stop background capture and context collection.</div></div><div class="switch on" aria-label="Passive automations active"></div></div>
  <div class="automation-group"><div class="eyebrow">Personal AI</div>${automationCard('Local Agent','Safely assists with supported phone actions after approval.','Ready','','local-agent-detail')}</div>
  <div class="automation-group"><div class="eyebrow">Meetings and communication</div>${automationCard('Meeting Spark Notes','Turns a recording you start into transcript, summary and actions.','Ready')}${automationCard('Live Caption Relay','Phone captions with compatible glasses display output.','Unavailable','neutral')}${automationCard('Hands-Free Translator','Translates speech with phone or supported display output.','Needs setup','','translator-detail')}</div>
  <div class="automation-group"><div class="eyebrow">Capture and memory</div>${automationCard('Auto Diary','Builds private daily memory from selected phone context.','Ready')}${automationCard('Auto Audio','Schedules supported HeyCyan onboard recordings and Sync.','Ready')}${automationCard('Visual Diary','Periodic camera captures on compatible glasses.','Needs setup','','capture-detail')}</div>
  <div class="automation-group"><div class="eyebrow">Productivity</div>${automationCard('Errand Brain','Turns spoken errands into reviewable tasks and reminders.','Ready')}</div>
  <div class="button-pair"><button class="button secondary" data-route="community">Browse community</button><button class="button secondary" data-route="community">Import locally</button></div>
</section>`, { header: false });

screens['automation-detail'] = () => shell('', `${topbar('Meeting Spark Notes', { detail: true, back: 'automations', action: 'Settings', actionIcon: 'settings' })}<section class="content">
  <div class="row"><div class="leading-icon">${icon('file')}</div><div class="grow"><h1 style="margin:0">Meeting Spark Notes</h1><p style="margin:4px 0 0">Recording → transcript → useful summary</p></div><div class="switch on"></div></div>
  <div class="spacer"></div><div class="card success-card"><div class="row-title">Ready to record</div><div class="row-subtitle">Phone microphone · English · Your cloud</div></div>
  <div class="section-title"><h2>How it works</h2></div><div class="card timeline"><div class="timeline-step done"><h3>You start and stop recording</h3><p>Nothing records silently in the background.</p></div><div class="timeline-step done"><h3>Audio is transcribed</h3><p>Use the selected phone or Bluetooth input.</p></div><div class="timeline-step active"><h3>AD Glasses prepares outcomes</h3><p>Summary, decisions and action items appear in Library.</p></div></div>
  <div class="section-title"><h2>Configuration</h2></div><div class="card">
    <div class="field"><label>Microphone input</label><select><option>Phone microphone</option><option>Bluetooth audio route</option></select></div>
    <div class="field"><label>Language</label><select><option>Automatic</option><option>English</option></select></div>
    <div class="field"><label>Processing</label><select><option>Your cloud</option><option>On device</option></select><span class="helper">Availability depends on configured providers.</span></div>
    <div class="row"><div class="grow"><div class="row-title">Keep original recording</div><div class="row-subtitle">Store locally until you delete it</div></div><div class="switch"></div></div>
  </div>
  <div class="button-stack"><button class="button block" data-route="meeting-active">Start safe test</button><button class="button secondary block">Review and enable</button></div>
</section>`, { focused: true, header: false });

screens.approval = () => shell('', `<div class="sheet-backdrop"><section class="sheet"><div class="grabber"></div><div class="eyebrow">Phone action approval</div><h1 style="margin-top:8px">Send a message to John</h1><p>Local Agent is proposing one action. Nothing has been sent.</p>
  <div class="card"><div class="row"><div class="leading-icon">${icon('send')}</div><div class="grow"><div class="eyebrow">App and target</div><div class="row-title">Telegram · John Smith</div></div></div><div class="row divided"><div class="leading-icon neutral">${icon('assistant')}</div><div class="grow"><div class="eyebrow">Exact action</div><div class="row-title">Type “I will be about 10 minutes late” and tap Send</div></div></div><div class="row divided"><div class="leading-icon neutral">${icon('storage')}</div><div class="grow"><div class="eyebrow">Data used</div><div class="row-title">Current chat and selected contact</div></div></div></div>
  <div class="card warning-card row"><div class="leading-icon warning">${icon('alert')}</div><div class="grow"><div class="row-title">This cannot be undone by the agent</div><div class="row-subtitle">The message will be sent only after this confirmation.</div></div></div>
  <div class="button-stack"><button class="button block" data-route="action-result">Confirm once</button><button class="button secondary block">Edit request</button><button class="button ghost block" data-route="assistant">Cancel</button></div>
</section></div>`, { focused: true, header: false });

screens.device = () => shell('', `${topbar('Device Center', { detail: true, back: 'home', action: 'More' })}<section class="content">
  <div class="center">${statusChip('Connected','green')}<div class="hero-stage" style="min-height:215px"><img class="hero-glasses" style="height:190px" src="${HERO}" alt="Connected smart glasses"></div><h1 style="margin-bottom:4px">My glasses</h1><p>HeyCyan-compatible · Primary</p></div>
  <div class="card"><div class="kv"><span>Connection</span><span class="value">Bluetooth LE</span><span>Last seen</span><span class="value">Now</span><span>Battery</span><span class="value">82% · Fresh</span><span>Storage</span><span class="value">61% free · Fresh</span></div></div>
  <div class="section-title"><h2>Actions</h2></div><div class="quick-grid">${quick('Ask','mic','assistant',true)}${quick('Capture','camera','assistant')}${quick('Sync','sync','sync')}${quick('Record','record','automation-detail')}</div>
  <div class="section-title"><h2>Supported controls</h2></div><div class="card settings-list"><div class="row"><div class="leading-icon neutral">${icon('camera')}</div><div class="grow"><div class="row-title">Capture and recording</div><div class="row-subtitle">Photo, video and onboard audio settings</div></div>${icon('chevron')}</div><div class="row divided"><div class="leading-icon neutral">${icon('sync')}</div><div class="grow"><div class="row-title">HeyCyan Firmware Lab</div><div class="row-subtitle">Experimental · owner-controlled packages</div></div>${icon('chevron')}</div><div class="row divided"><div class="leading-icon neutral">${icon('settings')}</div><div class="grow"><div class="row-title">Advanced and diagnostics</div><div class="row-subtitle">Logs, preview and transport tools</div></div>${icon('chevron')}</div></div>
  <div class="button-pair" style="margin-top:16px"><button class="button secondary">Refresh</button><button class="button secondary">Disconnect</button></div>
</section>`, { focused: true, header: false });

screens.sync = () => shell('', `${topbar('Sync', { detail: true, back: 'home' })}<section class="content">
  <div class="center" style="padding:18px 0">${statusChip('Local Wi-Fi connected','blue')}<h1 style="margin-top:14px">Transferring media</h1><p>Files move directly from your glasses to this phone.</p></div>
  <div class="card"><div class="row"><div class="grow"><div class="row-title">12 of 27 items</div><div class="row-subtitle">48.2 MB of 108.4 MB</div></div><strong class="accent">45%</strong></div><div class="progress" style="margin:14px 0 20px"><span style="--progress:45%"></span></div><div class="timeline"><div class="timeline-step done"><h3>Prepare glasses</h3></div><div class="timeline-step done"><h3>Establish local Wi-Fi</h3></div><div class="timeline-step done"><h3>Read media list</h3></div><div class="timeline-step active"><h3>Transfer</h3><p>Current item: video · 7.8 MB</p></div><div class="timeline-step"><h3>Save to Library</h3></div></div></div>
  <div class="card soft-card"><div class="row-title">Your media stays on the glasses</div><div class="row-subtitle">Sync does not automatically delete source files.</div></div>
  <div class="button-stack"><button class="button secondary block danger">Cancel sync</button></div>
</section>`, { focused: true, header: false });

screens.settings = () => shell('', `${topbar('Settings', { detail: true, back: 'home' })}<section class="content">
  <div class="card settings-list"><button class="row" data-route="device" style="border:0;background:transparent;text-align:left"><div class="leading-icon">${icon('camera')}</div><div class="grow"><div class="row-title">My glasses</div><div class="row-subtitle">Connected · HeyCyan-compatible</div></div>${icon('chevron')}</button><button class="row divided" data-route="ai-services" style="border:0;background:transparent;text-align:left"><div class="leading-icon">${icon('assistant')}</div><div class="grow"><div class="row-title">AI readiness</div><div class="row-subtitle">On device ready · Your cloud needs setup</div></div>${icon('chevron')}</button></div>
  <div class="section-title"><h2>General</h2></div><div class="card settings-list"><div class="row"><div class="grow"><div class="row-title">Language</div></div><span class="muted">English</span>${icon('chevron')}</div><div class="row divided"><div class="grow"><div class="row-title">Notifications</div></div><span class="muted">On</span>${icon('chevron')}</div><div class="row divided"><div class="grow"><div class="row-title">Permissions</div></div><span class="muted">Review</span>${icon('chevron')}</div></div>
  <div class="section-title"><h2>Intelligence</h2></div><div class="card settings-list"><button class="row" data-route="ai-services" style="border:0;background:transparent;text-align:left"><div class="grow"><div class="row-title">AI services and routing</div></div>${icon('chevron')}</button><button class="row divided" data-route="automations" style="border:0;background:transparent;text-align:left"><div class="grow"><div class="row-title">Automation provider</div></div>${icon('chevron')}</button></div>
  <div class="section-title"><h2>Privacy and data</h2></div><div class="card settings-list"><button class="row" data-route="privacy" style="border:0;background:transparent;text-align:left"><div class="grow"><div class="row-title">Privacy, memory and retention</div></div>${icon('chevron')}</button><div class="row divided"><div class="grow"><div class="row-title">Storage and export</div></div>${icon('chevron')}</div></div>
  <div class="section-title"><h2>Support</h2></div><div class="card settings-list"><button class="row" data-route="advanced" style="border:0;background:transparent;text-align:left"><div class="grow"><div class="row-title">Advanced and diagnostics</div></div>${icon('chevron')}</button><button class="row divided" data-route="prototype-controls" style="border:0;background:transparent;text-align:left"><div class="grow"><div class="row-title">Prototype controls</div><div class="row-subtitle">Debug build only</div></div>${icon('chevron')}</button><div class="row divided"><div class="grow"><div class="row-title">About AD Glasses</div></div>${icon('chevron')}</div></div>
</section>`, { focused: true, header: false });

screens['ai-services'] = () => shell('', `${topbar('AI services', { detail: true, back: 'settings' })}<section class="content">
  <div class="card soft-card"><div class="row"><div class="leading-icon">${icon('assistant')}</div><div class="grow"><div class="row-title">Automatic routing</div><div class="row-subtitle">Private/offline work stays local when suitable. Current public information may use web grounding.</div></div><div class="switch on"></div></div></div>
  <div class="section-title"><h2>Providers</h2><button class="section-link">Test all</button></div>
  <div class="card"><div class="row"><div class="leading-icon">${icon('phone')}</div><div class="grow"><div class="row-title">On device</div><div class="row-subtitle">Local model · Ready</div></div>${statusChip('Ready','green')}</div><div class="row divided"><div class="leading-icon neutral">${icon('cloud')}</div><div class="grow"><div class="row-title">Your cloud</div><div class="row-subtitle">Your relay · Not configured</div></div>${statusChip('Set up','amber')}</div><div class="row divided"><div class="leading-icon neutral">${icon('globe')}</div><div class="grow"><div class="row-title">Web grounding</div><div class="row-subtitle">Ask before search</div></div>${statusChip('Available','blue')}</div></div>
  <div class="section-title"><h2>Defaults</h2></div><div class="card kv"><span>Chat and requests</span><span class="value">Automatic</span><span>Image questions</span><span class="value">Automatic</span><span>Automations</span><span class="value">Your cloud</span><span>Live sessions</span><span class="value">Automatic</span></div>
  <div class="section-title"><h2>Configure your cloud</h2></div><div class="card"><div class="field"><label>Relay base URL</label><input placeholder="https://your-relay.example"></div><div class="field"><label>Optional token</label><input type="password" placeholder="Stored securely on device"><span class="helper">The prototype does not transmit or store this value.</span></div><div class="field"><label>Model</label><select><option>Auto-discover after test</option></select></div><div class="button-pair"><button class="button secondary">Save</button><button class="button">Save and test</button></div></div>
</section>`, { focused: true, header: false });

screens.privacy = () => shell('', `${topbar('Privacy and data', { detail: true, back: 'settings' })}<section class="content">
  <div class="card"><div class="row"><div class="leading-icon success">${icon('shield')}</div><div class="grow"><div class="row-title">Private Local</div><div class="row-subtitle">Current memory mode · content and retrieval stay on this phone</div></div>${statusChip('Active','green')}</div><div class="row divided"><div class="grow"><div class="row-title">Pause passive automations</div><div class="row-subtitle">Stops configured background context collection</div></div><div class="switch"></div></div></div>
  <div class="section-title"><h2>Memory modes</h2></div><div class="card settings-list"><div class="row"><div class="leading-icon">${icon('phone')}</div><div class="grow"><div class="row-title">Private Local</div><div class="row-subtitle">Available now</div></div>${icon('check')}</div><div class="row divided"><div class="leading-icon neutral">${icon('cloud')}</div><div class="grow"><div class="row-title">Encrypted Sync</div><div class="row-subtitle">Backend not configured</div></div><span class="chip amber">Unavailable</span></div><div class="row divided"><div class="leading-icon neutral">${icon('cloud')}</div><div class="grow"><div class="row-title">Fast Cloud Memory</div><div class="row-subtitle">Requires a future owner backend</div></div><span class="chip">Unavailable</span></div></div>
  <div class="section-title"><h2>Data on this phone</h2><button class="section-link">View inventory</button></div><div class="card kv"><span>Media</span><span class="value">17 items</span><span>Recordings and transcripts</span><span class="value">4 items</span><span>Notes and summaries</span><span class="value">8 items</span><span>Saved answers</span><span class="value">3 items</span><span>Models and indexes</span><span class="value">1.2 GB</span></div>
  <div class="section-title"><h2>Data actions</h2></div><div class="button-stack"><button class="button secondary block">Export data</button><button class="button secondary block">Import archive</button><button class="button ghost block danger">Clear local data…</button></div>
</section>`, { focused: true, header: false });

screens.firmware = () => shell('', `${topbar('Firmware Lab', { detail: true, back: 'device' })}<section class="content">
  <div class="row"><div class="grow"><div class="eyebrow">HeyCyan only</div><h1 style="margin:4px 0">Experimental update</h1><p>Updates are an exclusive device session. No Sync, recording or capture can run at the same time.</p></div><span class="chip amber">Experimental</span></div>
  <div class="card kv"><span>Device profile</span><span class="value">HeyCyan-compatible</span><span>Wi-Fi component</span><span class="value">Read from device</span><span>Bluetooth component</span><span class="value">Read from device</span><span>Package source</span><span class="value">Owner service or local recovery pair</span></div>
  <div class="section-title"><h2>Preflight</h2></div><div class="card settings-list"><div class="row"><div class="leading-icon success">${icon('check')}</div><div class="grow"><div class="row-title">Device profile matches</div></div><span class="chip green">Ready</span></div><div class="row divided"><div class="leading-icon success">${icon('check')}</div><div class="grow"><div class="row-title">Paired package validated</div><div class="row-subtitle">One .swu and companion .bin</div></div><span class="chip green">Ready</span></div><div class="row divided"><div class="leading-icon warning">${icon('alert')}</div><div class="grow"><div class="row-title">Power policy</div><div class="row-subtitle">Waiting for hardware-tested threshold</div></div><span class="chip amber">Blocked</span></div><div class="row divided"><div class="leading-icon success">${icon('check')}</div><div class="grow"><div class="row-title">No conflicting session</div></div><span class="chip green">Ready</span></div></div>
  <div class="card warning-card" style="margin-top:14px"><div class="row-title">Nothing has been flashed</div><div class="row-subtitle">The prototype simulates this flow. Production requires exact-device confirmation, package integrity and tested recovery.</div></div>
  <div class="button-stack"><button class="button block" disabled style="opacity:.45">Review exact update pair</button><button class="button secondary block">Open technical details</button></div>
</section>`, { focused: true, header: false });

screens.advanced = () => shell('', `${topbar('Advanced', { detail: true, back: 'settings' })}<section class="content">
  <div class="card warning-card"><div class="row"><div class="leading-icon warning">${icon('alert')}</div><div class="grow"><div class="row-title">Expert tools</div><div class="row-subtitle">These modules can expose diagnostics or experimental runtimes. Risky actions require confirmation.</div></div></div></div>
  <div class="section-title"><h2>Diagnostics</h2></div><div class="card settings-list"><div class="row"><div class="leading-icon neutral">${icon('file')}</div><div class="grow"><div class="row-title">Diagnostics hub</div><div class="row-subtitle">Device, provider and transcription status</div></div>${icon('chevron')}</div><div class="row divided"><div class="leading-icon neutral">${icon('shield')}</div><div class="grow"><div class="row-title">Redacted diagnostic bundle</div><div class="row-subtitle">Preview exactly what will be exported</div></div>${icon('chevron')}</div></div>
  <div class="section-title"><h2>HeyCyan tools</h2></div><div class="card settings-list"><div class="row"><div class="leading-icon neutral">${icon('camera')}</div><div class="grow"><div class="row-title">Preview and device probes</div><div class="row-subtitle">Experimental · no automatic commands</div></div>${icon('chevron')}</div><div class="row divided"><div class="leading-icon neutral">${icon('sync')}</div><div class="grow"><div class="row-title">Wi-Fi and transfer detail</div><div class="row-subtitle">P2P state, device-reported IP and local HTTP</div></div>${icon('chevron')}</div><div class="row divided"><div class="leading-icon warning">${icon('alert')}</div><div class="grow"><div class="row-title">Wi-Fi ADB</div><div class="row-subtitle">Debug exposure warning and explicit confirmation</div></div>${icon('chevron')}</div></div>
  <div class="section-title"><h2>External runtimes</h2></div><div class="card settings-list"><div class="row"><div class="leading-icon neutral">${icon('assistant')}</div><div class="grow"><div class="row-title">Phone automation bridge</div><div class="row-subtitle">external automation, AutoInput and Accessibility readiness</div></div><span class="chip">Prototype</span></div><div class="row divided"><div class="leading-icon neutral">${icon('globe')}</div><div class="grow"><div class="row-title">Display runtimes</div><div class="row-subtitle">EvenHub, Mentra relay and research adapters</div></div><span class="chip">Research</span></div></div>
</section>`, { focused: true, header: false });

screens['prototype-controls'] = () => shell('', `${topbar('Prototype controls', { detail: true, back: 'settings' })}<section class="content">
  <div class="card soft-card"><div class="row-title">Debug build only</div><div class="row-subtitle">These deterministic fixtures change UI state without invoking hardware, network, credentials or firmware.</div></div>
  <div class="section-title"><h2>Device scenario</h2></div><div class="card"><div class="field"><label>Family</label><select><option>HeyCyan-compatible · Primary</option><option>Eyevue · Experimental</option><option>Meta · Experimental</option><option>Meizu · Experimental</option><option>Generic audio · Limited</option></select></div><div class="field"><label>Connection</label><select><option>Connected</option><option>Connecting</option><option>Reconnecting</option><option>Disconnected</option><option>Failed</option></select></div><div class="field"><label>Metric freshness</label><select><option>Fresh</option><option>Stale</option><option>Unknown</option></select></div></div>
  <div class="section-title"><h2>Product state</h2></div><div class="card"><div class="field"><label>Global activity</label><select><option>None</option><option>Recording</option><option>Sync</option><option>Live translation</option><option>Live captions</option><option>Firmware</option></select></div><div class="field"><label>AI providers</label><select><option>Local ready · Cloud needs setup</option><option>All ready</option><option>Offline</option><option>Authentication failed</option></select></div><div class="field"><label>Library</label><select><option>Populated</option><option>Empty</option><option>Processing</option></select></div><div class="field"><label>Automation</label><select><option>Ready</option><option>Running</option><option>Paused</option><option>Incompatible</option><option>Permission lost</option><option>Failed</option></select></div></div>
  <div class="section-title"><h2>Review modes</h2></div><div class="card settings-list"><div class="row"><div class="grow"><div class="row-title">Long copy</div></div><div class="switch"></div></div><div class="row divided"><div class="grow"><div class="row-title">Large text fixture</div></div><div class="switch"></div></div><div class="row divided"><div class="grow"><div class="row-title">Reduced motion</div></div><div class="switch"></div></div></div>
  <div class="button-stack"><button class="button block" data-route="home">Apply scenario</button><button class="button secondary block">Reset all seed data</button></div>
</section>`, { focused: true, header: false });

screens['permission-denied'] = () => shell('', `${topbar('Glasses setup', { detail: true, back: 'welcome' })}<section class="content">
  <div class="progress-steps"><div class="progress-step active">Prepare</div><div class="progress-step">Find</div><div class="progress-step">Confirm</div></div>
  <div class="leading-icon warning" style="width:58px;height:58px;border-radius:18px">${icon('alert')}</div><h1 style="margin-top:18px">Nearby-device access is off</h1><p>AD Glasses cannot scan until Android allows nearby-device access. No location history is collected by this permission.</p>
  <div class="card danger-card row"><div class="leading-icon">${icon('bluetooth')}</div><div class="grow"><div class="row-title">Nearby devices</div><div class="row-subtitle">Denied in Android settings</div></div><span class="chip red">Denied</span></div>
  <div class="section-title"><h2>How to recover</h2></div><div class="card timeline"><div class="timeline-step active"><h3>Open app permissions</h3><p>Choose Nearby devices for AD Glasses.</p></div><div class="timeline-step"><h3>Allow access</h3><p>Return here after changing the setting.</p></div><div class="timeline-step"><h3>Try again</h3><p>The scan begins only when you ask.</p></div></div>
  <div class="button-stack"><button class="button block">Open app permissions</button><button class="button secondary block" data-route="readiness">Check again</button><button class="button ghost block" data-route="welcome">Not now</button></div>
</section>`, { focused: true, header: false });

screens['connect-progress'] = () => shell('', `${topbar('Connecting', { detail: true, back: 'scan' })}<section class="content">
  <div class="center"><div class="hero-stage" style="min-height:260px"><img class="hero-glasses" src="${HERO}" alt="Selected glasses"></div><span class="chip blue"><span class="dot"></span>Connecting securely</span><h1 style="margin-top:14px">Preparing your glasses</h1><p>Keep the glasses close to this phone.</p></div>
  <div class="card timeline"><div class="timeline-step done"><h3>Preparing</h3><p>Device family confirmed</p></div><div class="timeline-step active"><h3>Connecting</h3><p>Establishing the supported device session</p></div><div class="timeline-step"><h3>Reading capabilities</h3><p>Only supported controls will appear</p></div></div>
  <div class="button-stack"><button class="button secondary block" data-route="scan">Cancel</button></div>
</section>`, { focused: true, header: false });

screens['home-connecting'] = () => shell('home', `${topbar()}<section class="content">
  <div class="hero-stage" style="opacity:.86"><img class="hero-glasses" src="${HERO}" alt="Connecting smart glasses"></div><div class="device-name"><span class="chip blue"><span class="dot"></span>Connecting</span><h2>My glasses</h2><p>Reading capabilities…</p></div>
  <div class="card soft-card row" style="margin-top:20px"><div class="leading-icon">${icon('sync')}</div><div class="grow"><div class="row-title">Finishing setup</div><div class="row-subtitle">Controls will appear after the device reports what it supports.</div></div></div>
  <div class="section-title"><h2>Available now</h2></div><div class="quick-grid">${quick('Ask','mic','assistant',true)}${quick('Library','library','library')}${quick('Notes','file','content')}${quick('Settings','settings','settings')}</div>
  <div class="button-stack"><button class="button secondary block">Cancel connection</button></div>
</section>`, { header: false });

screens['assistant-offline'] = () => shell('assistant', `${topbar()}<section class="content">
  <div class="leading-icon warning" style="margin-top:22px">${icon('cloud')}</div><h1 style="margin-top:16px">Cloud is unavailable</h1><p>You can continue with on-device AI. Web search, image analysis and some automations remain paused until your configured provider returns.</p>
  <div class="card"><div class="row"><div class="leading-icon success">${icon('phone')}</div><div class="grow"><div class="row-title">On-device AI</div><div class="row-subtitle">Voice, text and suitable private tasks</div></div>${statusChip('Ready','green')}</div><div class="row divided"><div class="leading-icon neutral">${icon('cloud')}</div><div class="grow"><div class="row-title">Your cloud</div><div class="row-subtitle">Connection failed</div></div>${statusChip('Offline','red')}</div><div class="row divided"><div class="leading-icon neutral">${icon('globe')}</div><div class="grow"><div class="row-title">Web grounding</div><div class="row-subtitle">Paused with cloud route</div></div>${statusChip('Paused','amber')}</div></div>
  <div class="button-stack"><button class="button block">Continue on device</button><button class="button secondary block" data-route="ai-services">Review AI services</button></div>
  <div class="composer"><button class="icon-button">${icon('attach')}</button><input placeholder="Ask an offline question…"><button class="icon-button send">${icon('send')}</button></div>
</section>`, { header: false });

screens['action-result'] = () => shell('', `${topbar('Action result', { detail: true, back: 'assistant' })}<section class="content">
  <div class="leading-icon warning" style="width:58px;height:58px;border-radius:18px;margin-top:18px">${icon('alert')}</div><h1 style="margin-top:18px">Message prepared, not sent</h1><p>Local Agent entered the text, but Android did not confirm that the Send control was activated.</p>
  <div class="card warning-card"><div class="eyebrow">Partial outcome</div><div class="row-title" style="margin-top:8px">Telegram opened John Smith’s chat</div><div class="row-subtitle">The message remains in the compose field. Review it before sending manually.</div></div>
  <div class="section-title"><h2>What happened</h2></div><div class="card timeline"><div class="timeline-step done"><h3>Opened Telegram</h3></div><div class="timeline-step done"><h3>Selected the target chat</h3></div><div class="timeline-step done"><h3>Entered the approved text</h3></div><div class="timeline-step active"><h3>Send was not confirmed</h3></div></div>
  <div class="button-stack"><button class="button block">Open Telegram to review</button><button class="button secondary block">Try again with approval</button><button class="button ghost block" data-route="assistant">Done</button></div>
</section>`, { focused: true, header: false });

screens['library-empty'] = () => shell('library', `${topbar()}<section class="content">
  <div class="section-title" style="margin-top:8px"><h1 style="margin:0">Library</h1><button class="icon-button">${icon('search')}</button></div><div class="segmented"><button class="segment active">Timeline</button><button class="segment">Collections</button></div>
  <div class="live-stage" style="min-height:460px"><div class="leading-icon neutral" style="width:70px;height:70px;border-radius:22px">${icon('library')}</div><h1 style="margin-top:20px">Your Library is ready</h1><p style="max-width:300px">Captures, recordings, transcripts, notes, memories and saved answers will appear here.</p><div class="button-stack" style="width:100%;max-width:310px"><button class="button block" data-route="assistant">Capture or ask</button><button class="button secondary block" data-route="sync">Sync from glasses</button><button class="button ghost block">Create note</button></div></div>
</section>`, { header: false });

screens['local-agent-detail'] = () => shell('', `${topbar('Local Agent', { detail: true, back: 'automations', action: 'Settings', actionIcon: 'settings' })}<section class="content">
  <div class="row"><div class="leading-icon">${icon('automation')}</div><div class="grow"><h1 style="margin:0">Local Agent</h1><p style="margin:4px 0 0">Supported phone actions with owner approval</p></div><div class="switch on"></div></div>
  <div class="card warning-card" style="margin-top:18px"><div class="row-title">Approval is required for consequential actions</div><div class="row-subtitle">Local Agent never reports completion until Android returns an observable result.</div></div>
  <div class="section-title"><h2>Readiness</h2></div><div class="card settings-list"><div class="row"><div class="leading-icon success">${icon('check')}</div><div class="grow"><div class="row-title">Accessibility service</div></div>${statusChip('Ready','green')}</div><div class="row divided"><div class="leading-icon success">${icon('check')}</div><div class="grow"><div class="row-title">Notification access</div></div>${statusChip('Ready','green')}</div><div class="row divided"><div class="leading-icon neutral">${icon('shield')}</div><div class="grow"><div class="row-title">App privacy list</div><div class="row-subtitle">Banking and authenticator apps excluded</div></div>${icon('chevron')}</div></div>
  <div class="section-title"><h2>Approval policy</h2></div><div class="card"><div class="field"><label>Default</label><select><option>Ask before acting</option><option>Read-only suggestions</option></select></div><div class="row"><div class="grow"><div class="row-title">Screen captures</div><div class="row-subtitle">Keep only for the active approved task</div></div><div class="switch"></div></div></div>
  <div class="button-stack"><button class="button block">Run read-only test</button><button class="button secondary block" data-route="approval">Preview approval sheet</button></div>
</section>`, { focused: true, header: false });

screens['translator-detail'] = () => shell('', `${topbar('Hands-Free Translator', { detail: true, back: 'automations' })}<section class="content">
  <div class="row"><div class="leading-icon">${icon('globe')}</div><div class="grow"><h1 style="margin:0">Translator</h1><p style="margin:4px 0 0">Speech in · translated speech or supported text out</p></div><span class="chip amber">Needs setup</span></div>
  <div class="section-title"><h2>Language pair</h2></div><div class="card"><div class="button-pair"><div class="field"><label>From</label><select><option>Spanish</option></select></div><div class="field"><label>To</label><select><option>English</option></select></div></div><button class="button secondary block">Swap languages</button></div>
  <div class="section-title"><h2>Input and output</h2></div><div class="card"><div class="field"><label>Microphone input</label><select><option>Phone microphone</option><option>Compatible glasses microphone</option></select></div><div class="field"><label>Output</label><select><option>Spoken on phone</option><option>Phone captions</option><option>Compatible glasses display</option></select><span class="helper">Display output appears only when the active device reports support.</span></div><div class="row"><div class="grow"><div class="row-title">Save transcript</div><div class="row-subtitle">Keep locally in Library</div></div><div class="switch"></div></div></div>
  <div class="button-stack"><button class="button block" data-route="automation-live">Test phrase</button><button class="button secondary block">Review and enable</button></div>
</section>`, { focused: true, header: false });

screens['capture-detail'] = () => shell('', `${topbar('Visual Diary', { detail: true, back: 'automations' })}<section class="content">
  <div class="row"><div class="leading-icon">${icon('camera')}</div><div class="grow"><h1 style="margin:0">Visual Diary</h1><p style="margin:4px 0 0">Periodic captures you explicitly configure</p></div><span class="chip amber">Needs setup</span></div>
  <div class="card soft-card" style="margin-top:18px"><div class="row-title">No passive scene scanning</div><div class="row-subtitle">Captures follow the interval and pause controls below. Unsupported camera sources are not shown.</div></div>
  <div class="section-title"><h2>Capture plan</h2></div><div class="card"><div class="field"><label>Camera source</label><select><option>HeyCyan-compatible camera</option></select></div><div class="field"><label>Interval while active</label><select><option>Every 15 minutes</option><option>Every 30 minutes</option><option>Every hour</option></select></div><div class="field"><label>Processing</label><select><option>On device when available</option><option>Your cloud</option></select></div><div class="field"><label>Retention</label><select><option>Review after 24 hours</option><option>Keep until deleted</option></select></div><div class="row"><div class="grow"><div class="row-title">Pause when battery is low</div></div><div class="switch on"></div></div></div>
  <div class="button-stack"><button class="button block">Run one visible test capture</button><button class="button secondary block">Review and enable</button></div>
</section>`, { focused: true, header: false });

screens.community = () => shell('', `${topbar('Community', { detail: true, back: 'automations' })}<section class="content">
  <h1>Browse integrations</h1><p>Community items come only from your configured service or a local package.</p><div class="field"><label class="helper">Search</label><input placeholder="Search outcomes or capabilities"></div><div class="filter-row"><span class="chip blue">Compatible</span><span class="chip">Local</span><span class="chip">Communication</span><span class="chip">Productivity</span></div>
  <div class="section-title"><h2>Available locally</h2></div><div class="card"><div class="row"><div class="leading-icon">${icon('file')}</div><div class="grow"><div class="row-title">Quiet meeting timer</div><div class="row-subtitle">Local package · v1.2 · Phone only</div></div>${icon('chevron')}</div><div class="row divided"><div class="grow"><div class="eyebrow">Access</div><div class="row-subtitle">Notifications · No cloud data</div></div><span class="chip green">Compatible</span></div></div>
  <div class="card"><div class="row"><div class="leading-icon neutral">${icon('globe')}</div><div class="grow"><div class="row-title">Travel phrase helper</div><div class="row-subtitle">Owner community · v0.8 · Phone audio</div></div>${icon('chevron')}</div><div class="row divided"><div class="grow"><div class="eyebrow">Access</div><div class="row-subtitle">Microphone · Your cloud</div></div><span class="chip amber">Review</span></div></div>
  <div class="button-stack"><button class="button secondary block">Import package locally</button><button class="button ghost block">Publish an integration</button></div>
</section>`, { focused: true, header: false });

screens['automation-live'] = () => shell('', `${topbar('Live translation', { detail: true, back: 'translator-detail' })}<section class="content">
  <div class="center"><span class="chip blue"><span class="dot"></span>Listening · Spanish → English</span><h1 style="margin-top:18px">Conversation</h1><p>Phone microphone · Spoken phone output</p></div>
  <div class="card" style="margin-top:20px"><div class="eyebrow">Heard · Spanish</div><p class="lead" style="color:var(--ink);margin:8px 0 0">¿A qué hora sale el próximo tren?</p></div><div class="card soft-card"><div class="eyebrow accent">Translation · English</div><p class="lead" style="color:var(--ink);margin:8px 0 0">What time does the next train leave?</p><button class="button ghost" style="padding-left:0">Read aloud</button></div>
  <div class="live-stage" style="min-height:250px"><div class="orb" style="width:110px;height:110px"><div class="wave"><i></i><i></i><i></i></div></div><p style="margin-top:24px">Waiting for the next phrase…</p></div>
  <div class="button-pair"><button class="button secondary">${icon('mic')} Mute</button><button class="button danger">Stop and save</button></div>
</section>`, { focused: true, header: false });

screens['meeting-active'] = () => shell('', `${topbar('Meeting recording', { detail: true, back: 'automation-detail' })}<section class="content">
  <div class="live-stage" style="min-height:470px"><span class="chip red"><span class="dot"></span>Recording</span><h1 style="font-size:44px;margin:18px 0 4px">18:42</h1><p>Phone microphone · English</p><div class="orb" style="width:140px;height:140px;color:var(--danger);background:radial-gradient(circle at 40% 35%,#fff,#ffe4e6)"><div class="wave"><i></i><i></i><i></i><i></i><i></i></div></div><div class="card" style="width:100%;margin-top:34px;text-align:left"><div class="eyebrow">Live transcript</div><p style="margin:8px 0 0;color:var(--ink)">“…the next step is to test the connection flow with the physical glasses…”</p></div></div>
  <div class="button-pair"><button class="button secondary">${icon('pause')} Pause</button><button class="button danger">Stop recording</button></div>
</section>`, { focused: true, header: false });

screens['device-limited'] = () => shell('', `${topbar('Device Center', { detail: true, back: 'home' })}<section class="content">
  <div class="center"><span class="chip amber">Limited</span><div class="hero-stage" style="min-height:230px;opacity:.8"><img class="hero-glasses" style="height:185px" src="${HERO}" alt="Generic audio glasses illustration"></div><h1 style="margin-bottom:4px">Audio glasses</h1><p>Generic audio · Connected</p></div>
  <div class="card warning-card"><div class="row-title">Limited capability mode</div><div class="row-subtitle">AD Glasses detected a phone audio route. Camera, onboard storage, media Sync and display controls are unavailable.</div></div>
  <div class="section-title"><h2>Available</h2></div><div class="card settings-list"><div class="row"><div class="leading-icon">${icon('mic')}</div><div class="grow"><div class="row-title">Audio input route</div><div class="row-subtitle">Use for Assistant and recordings</div></div>${statusChip('Ready','green')}</div><div class="row divided"><div class="leading-icon neutral">${icon('assistant')}</div><div class="grow"><div class="row-title">Ask with voice</div><div class="row-subtitle">AI runs through the phone</div></div>${icon('chevron')}</div></div>
  <div class="button-stack"><button class="button block" data-route="assistant">Ask with voice</button><button class="button secondary block">Disconnect</button></div>
</section>`, { focused: true, header: false });

screens['sync-result'] = () => shell('', `${topbar('Sync summary', { detail: true, back: 'library' })}<section class="content">
  <div class="leading-icon warning" style="width:58px;height:58px;border-radius:18px;margin-top:18px">${icon('alert')}</div><h1 style="margin-top:18px">Most items were imported</h1><p>Saved items remain in Library. One failed item can be tried again.</p>
  <div class="card kv"><span class="success">Imported</span><strong>15</strong><span>Duplicates skipped</span><strong>2</strong><span class="danger">Failed</span><strong>1</strong></div>
  <div class="section-title"><h2>Failed item</h2></div><div class="card row"><div class="leading-icon warning">${icon('file')}</div><div class="grow"><div class="row-title">Video · identifier ending 82F1</div><div class="row-subtitle">Connection interrupted while transferring</div></div></div>
  <div class="card soft-card"><div class="row-title">Source files were not deleted</div><div class="row-subtitle">The glasses keep their media until you choose to remove it from Device Center.</div></div>
  <div class="button-stack"><button class="button block">Continue remaining files</button><button class="button secondary block" data-route="library">Open Library</button><button class="button ghost block">Done</button></div>
</section>`, { focused: true, header: false });

screens['firmware-progress'] = () => shell('', `${topbar('Firmware Lab', { detail: true, back: 'firmware' })}<section class="content">
  <div class="center"><span class="chip red"><span class="dot"></span>Exclusive update session</span><h1 style="margin-top:18px">Updating Wi-Fi component</h1><p>Keep the glasses powered and nearby. Cancellation is unavailable during this verified flashing stage.</p></div>
  <div class="card"><div class="row"><div class="grow"><div class="row-title">Overall progress</div></div><strong class="accent">45%</strong></div><div class="progress" style="margin:14px 0 24px"><span style="--progress:45%"></span></div><div class="timeline"><div class="timeline-step done"><h3>Read versions</h3></div><div class="timeline-step done"><h3>Validate and stage pair</h3></div><div class="timeline-step active"><h3>Wi-Fi update</h3><p>Transferring verified component</p></div><div class="timeline-step"><h3>Restore and recheck</h3></div><div class="timeline-step"><h3>Bluetooth update</h3></div><div class="timeline-step"><h3>Reconnect and verify</h3></div></div></div>
  <div class="button-stack"><button class="button secondary block">Show technical detail</button></div>
</section>`, { focused: true, header: false });

screens['firmware-result'] = () => shell('', `${topbar('Firmware result', { detail: true, back: 'device' })}<section class="content">
  <div class="leading-icon warning" style="width:58px;height:58px;border-radius:18px;margin-top:18px">${icon('alert')}</div><h1 style="margin-top:18px">Wi-Fi updated; Bluetooth needs recovery</h1><p>The first component completed and the device reconnected. The companion Bluetooth update did not verify.</p>
  <div class="card warning-card"><div class="eyebrow">Partial update</div><div class="row-title" style="margin-top:8px">Do not repeat the Wi-Fi stage</div><div class="row-subtitle">Recovery should continue from the validated companion package after versions are read again.</div></div>
  <div class="section-title"><h2>Component status</h2></div><div class="card settings-list"><div class="row"><div class="leading-icon success">${icon('check')}</div><div class="grow"><div class="row-title">Wi-Fi component</div><div class="row-subtitle">Completed and rechecked</div></div>${statusChip('Complete','green')}</div><div class="row divided"><div class="leading-icon warning">${icon('alert')}</div><div class="grow"><div class="row-title">Bluetooth component</div><div class="row-subtitle">Verification failed</div></div>${statusChip('Recovery','amber')}</div></div>
  <div class="button-stack"><button class="button block">Review safe recovery</button><button class="button secondary block">Export redacted diagnostics</button><button class="button ghost block" data-route="device">Return to Device Center</button></div>
</section>`, { focused: true, header: false });

function render() {
  const params = new URLSearchParams(location.search);
  const route = params.get('screen') || 'home';
  const factory = screens[route] || screens.home;
  document.getElementById('app').innerHTML = factory();
  document.querySelectorAll('[data-route]').forEach((element) => {
    element.addEventListener('click', () => {
      const next = element.dataset.route;
      history.pushState({}, '', `?screen=${encodeURIComponent(next)}`);
      render();
      scrollTo({ top: 0, behavior: 'instant' });
    });
  });
}

if ('scrollRestoration' in history) history.scrollRestoration = 'manual';
scrollTo(0, 0);
addEventListener('popstate', render);
render();
