# LiveApiTester — AI-Powered Live API Testing Plugin for IntelliJ IDEA

[![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-blue)](https://plugins.jetbrains.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.8-orange)](https://kotlinlang.org/)
[![Version](https://img.shields.io/badge/version-2.0.0-green)](https://github.com/prakash988/LiveApiTester)

> **Like Postman/Bruno — but embedded directly inside IntelliJ IDEA, powered by GitHub Models AI, and with full IntelliJ Debugger Integration.**

---

## ✨ Features

| Feature | Description |
|---|---|
| 🚀 **Live HTTP Client** | Full request builder — URL, method, headers, body, params, auth |
| 📊 **Response Viewer** | Pretty-printed JSON, color-coded status, response time color coding |
| 🐛 **Send & Debug** | Auto-sets breakpoints on matching controller methods, starts debug session |
| ▶️ **Service Management** | Start/stop backend services directly from the plugin |
| 🤖 **AI: Explain Error** | GitHub Models AI analyzes 4xx/5xx errors and suggests fixes |
| 🤖 **AI: Suggest Tests** | AI generates positive, negative & edge-case tests |
| 🤖 **AI: Generate Body** | AI creates sample request bodies |
| 🤖 **AI: Debug Analysis** | AI analyzes debug state (stack trace, variables) when breakpoint is hit |
| 🔍 **Endpoint Scanner** | Auto-discovers Spring Boot & JAX-RS endpoints from your code |
| 📁 **Collections** | Save/organize requests |
| 🌍 **Environments** | Dev/Staging/Prod variables with `{{variable}}` interpolation |
| 📜 **Request History** | Last 100 requests with replay |
| 📋 **Copy as cURL** | One-click export as cURL command |
| ⌨️ **Keyboard Shortcuts** | `Ctrl+Enter` (send), `Ctrl+Shift+Enter` (debug), `Escape` (cancel) |
| 🔒 **Security** | Auth header masking, PasswordSafe for PAT storage |
| ⚙️ **Settings** | GitHub Models endpoint, model selector, timeouts, SSL options |

---

## 📸 UI Layout

```
┌─────────────────────┬──────────────────────────────────────────┐
│ History             │  [GET ▼] [URL field          ] [▶ Send] [🐛 Send & Debug] │
│ Collections         │  Service: ● [config ▼] [▶] [⏹]          │
│ Environments        ├──────────────────────────────────────────┤
│                     │  Params | Headers | Body | Auth           │
│                     ├──────────────────────────────────────────┤
│                     │  Status: 200 OK   Time: 45ms   Size: 2KB  │
│                     │  Body | Headers                           │
│                     │  [Copy] [🤖 Explain Error] [🤖 Suggest Tests] [🤖 Debug Analysis] │
└─────────────────────┴──────────────────────────────────────────┘
```

---

## 🚀 Installation

### From Source

**Requirements:**
- JDK 17+
- IntelliJ IDEA 2023.1+

```bash
git clone https://github.com/prakash988/LiveApiTester.git
cd LiveApiTester
./gradlew buildPlugin
```

The plugin ZIP will be in `build/distributions/`. Install it via:
**Settings → Plugins → ⚙️ → Install Plugin from Disk...**

### Run in Sandbox IDE

```bash
./gradlew runIde
```

---

## ⚙️ Configuration

### GitHub Models AI Configuration

1. Open **Settings → Tools → LiveApiTester**
2. **GitHub Models API Endpoint** (default: `https://models.github.ai/inference/chat/completions`)
3. **Model** — select from dropdown:
   - `gpt-4o` (default)
   - `gpt-4o-mini`
   - `claude-3.5-sonnet`
   - `Meta-Llama-3.1-405B-Instruct`
   - `Mistral-Large`
4. **GitHub Personal Access Token (PAT)** — stored securely in IntelliJ PasswordSafe

### Getting a GitHub PAT

1. Go to [github.com/settings/tokens](https://github.com/settings/tokens)
2. Click **"Generate new token (classic)"**
3. Select the **`models`** or **`copilot`** scope
4. Copy the token and paste it in LiveApiTester settings

> **Note:** GitHub Models API is free for GitHub Copilot subscribers.

---

## 🔧 Usage

### Making a Request

1. Open the **LiveApiTester** tool window (bottom panel)
2. Select HTTP method and enter URL
3. Add headers, params, body, or auth as needed
4. Press **▶ Send** or `Ctrl+Enter`

### Send & Debug

1. Configure your Spring Boot app as a Run Configuration
2. Enter the API URL that maps to a controller method
3. Click **🐛 Send & Debug** or press `Ctrl+Shift+Enter`
4. The plugin will:
   - Find the matching `@GetMapping`/`@PostMapping` method
   - Set a breakpoint on it automatically
   - Start your app in Debug mode (if not running)
   - Send the HTTP request
   - IntelliJ pauses at the breakpoint
5. Use the Debug Controls toolbar: **Step Over**, **Step Into**, **Resume**, **Stop**
6. Click **🤖 AI Debug Analysis** to get AI analysis of the current debug state

### Start/Stop Services

- Use the **Service** bar in the request panel
- Select a run configuration from the dropdown
- Click **▶** to start, **⏹** to stop
- The status dot shows: 🟢 running, 🟡 starting, 🔴 stopped

### Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+Enter` / `Cmd+Enter` | Send request |
| `Ctrl+Shift+Enter` | Send & Debug |
| `Escape` | Cancel in-flight request |

### Copy as cURL

Click the **cURL** button to copy the current request as a cURL command. Environment variables are resolved automatically.

---

## 🏗️ Project Structure

```
src/main/kotlin/com/liveapitester/
├── actions/
│   └── ScanEndpointsAction.kt       # Tools menu action for endpoint scanning
├── ai/
│   └── AiService.kt                 # GitHub Models AI integration
├── collections/
│   ├── ApiCollection.kt             # Collection data model
│   └── CollectionManager.kt         # Save/load collections to JSON
├── debugger/
│   ├── DebuggerService.kt           # Core debugger integration service
│   ├── EndpointMethodResolver.kt    # Maps URLs to PSI controller methods
│   └── ServiceManager.kt           # Manages run configurations
├── environment/
│   ├── Environment.kt               # Environment data model
│   └── EnvironmentManager.kt        # Manage environments
├── history/
│   ├── HistoryEntry.kt              # History entry data model
│   └── HistoryManager.kt            # Persist last 100 requests
├── http/
│   ├── HttpMethod.kt                # HTTP method enum
│   ├── ApiRequest.kt                # Request data class
│   ├── ApiResponse.kt               # Response data class
│   └── HttpExecutor.kt              # OkHttp-based HTTP client (cancellable)
├── scanner/
│   └── EndpointScanner.kt           # Spring Boot & JAX-RS endpoint scanner
├── settings/
│   ├── LiveApiTesterSettings.kt     # Persistent settings (GitHub Models defaults)
│   └── LiveApiTesterConfigurable.kt # Settings UI
└── ui/
    ├── LiveApiTesterToolWindowFactory.kt  # Tool window factory
    ├── RequestPanel.kt              # Request builder UI
    ├── ResponsePanel.kt             # Response viewer UI
    ├── CollectionsPanel.kt          # Collections browser
    ├── EnvironmentPanel.kt          # Environment manager
    ├── HistoryPanel.kt              # Request history
    ├── DebugToolbarPanel.kt         # Debug controls toolbar
    └── LiveApiTesterStatusBarWidget.kt   # Status bar widget
```

---

## 🛠️ Tech Stack

- **Language:** Kotlin 1.8
- **Build:** Gradle 8.8 with IntelliJ Platform Gradle Plugin 1.17.3
- **HTTP Client:** OkHttp 4.12.0 (with request cancellation)
- **JSON:** Gson 2.10.1
- **AI:** GitHub Models API (`https://models.github.ai/inference`)
- **UI:** IntelliJ Tool Window + Swing / JBTable / JBTabbedPane
- **Debugger:** IntelliJ XDebugger API + Java PSI
- **Min IntelliJ Version:** 2023.1 (IC or IU)

---

## 🔒 Security

- GitHub PAT stored via IntelliJ's `PasswordSafe` (never in plain text)
- Authorization header values masked by default in response headers view
- AI prompts do not include API keys/tokens
- URL validated before sending requests

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 🗺️ Roadmap

- [ ] OAuth 2.0 flow support
- [ ] WebSocket testing
- [ ] GraphQL support
- [ ] cURL import
- [ ] Response schema validation
- [ ] Publish to JetBrains Marketplace

---

## 📄 License

MIT — see [LICENSE](LICENSE) for details.
