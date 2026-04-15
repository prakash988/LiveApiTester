# LiveApiTester — AI-Powered Live API Testing Plugin for IntelliJ IDEA

[![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-blue)](https://plugins.jetbrains.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-orange)](https://kotlinlang.org/)

> **Like Postman / Bruno — but embedded directly inside IntelliJ IDEA and powered by AI.**

---

## ✨ Features

| Feature | Description |
|---|---|
| 🚀 **Live HTTP Client** | Full request builder — GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS |
| 🤖 **AI: Explain Error** | On 4xx/5xx responses, AI explains what went wrong and suggests fixes |
| 🤖 **AI: Suggest Tests** | AI generates positive, negative, and edge-case test scenarios |
| 🤖 **AI: Generate Body** | AI creates a sample request body for any endpoint |
| 🔍 **Endpoint Scanner** | Auto-discovers Spring Boot & JAX-RS REST endpoints in your project |
| 📁 **Collections** | Save and organize requests — like Postman collections |
| 🌍 **Environments** | Manage dev/staging/prod variables with `{{variable}}` interpolation |
| 📜 **Request History** | Scrollable history of all requests; click to replay |
| 🔐 **Auth Support** | Bearer Token, Basic Auth, API Key |
| ⚙️ **Settings Page** | Configure AI endpoint, model, API key, timeouts, SSL |

---

## 📸 UI Layout

```
┌─────────────────────────────────────────────────────────┐
│  LiveApiTester Tool Window                               │
├──────────────┬──────────────────────────────────────────┤
│ History      │  [GET ▾] [URL field              ] [Send]│
│ Collections  │  ┌ Params | Headers | Body | Auth ─────┐ │
│ Environments │  │  key-value tables / body editor      │ │
│              │  └──────────────────────────────────────┘ │
│              ├──────────────────────────────────────────┤
│              │  Status: 200 OK  Time: 142ms  Size: 1.2KB│
│              │  ┌ Body | Headers ───────────────────┐   │
│              │  │  {                                 │   │
│              │  │    "id": 1,                        │   │
│              │  │    "name": "example"               │   │
│              │  │  }                                 │   │
│              │  └────────────────────────────────────┘   │
│              │  [Copy] [🤖 Explain Error] [🤖 Tests]     │
└──────────────┴──────────────────────────────────────────┘
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

### AI Configuration

1. Open **Settings → Tools → LiveApiTester**
2. Set **API Endpoint** (default: `https://api.openai.com/v1/chat/completions`)
3. Set **Model** (default: `gpt-4`)
4. Enter your **API Key** (stored securely via IntelliJ PasswordSafe)

### Compatible AI Providers

| Provider | Endpoint |
|---|---|
| OpenAI | `https://api.openai.com/v1/chat/completions` |
| Azure OpenAI | `https://<your-resource>.openai.azure.com/openai/deployments/<deployment>/chat/completions?api-version=2024-02-01` |
| Ollama (local) | `http://localhost:11434/v1/chat/completions` |
| LM Studio | `http://localhost:1234/v1/chat/completions` |
| Any OpenAI-compatible | Custom URL |

---

## 🔧 Usage

### Making a Request

1. Open the **LiveApiTester** tool window (bottom panel)
2. Enter a URL in the URL bar
3. Select HTTP method from the dropdown
4. Configure **Params**, **Headers**, **Body**, **Auth** in the tabs
5. Click **Send**

### Using Variables

Define variables in the **Environments** panel:
```
base_url = http://localhost:8080
api_version = v1
token = my-secret-token
```

Use them in requests:
```
URL: {{base_url}}/{{api_version}}/users
Header: Authorization: Bearer {{token}}
```

### Endpoint Scanner

1. Go to **Tools → Scan API Endpoints** (or press `Ctrl+Shift+E`)
2. A popup shows all discovered Spring Boot / JAX-RS endpoints
3. Double-click an endpoint to load it into the request builder

### AI Features

After receiving a response:
- **🤖 Explain Error** — Enabled on 4xx/5xx; AI explains the error and suggests fixes
- **🤖 Suggest Tests** — AI generates test cases for the endpoint
- **🤖 Generate Body** — AI creates a sample request body

### Collections

- Click **+** in the Collections panel to create a collection
- Right-click a collection to add the current request
- Double-click a saved request to load it

---

## 🏗️ Project Structure

```
src/main/kotlin/com/liveapitester/
├── actions/
│   └── ScanEndpointsAction.kt      # Tools menu action for endpoint scanning
├── ai/
│   └── AiService.kt                # OpenAI-compatible AI integration
├── collections/
│   ├── ApiCollection.kt            # Collection data model
│   └── CollectionManager.kt        # Save/load collections to JSON
├── environment/
│   ├── Environment.kt              # Environment data model
│   └── EnvironmentManager.kt       # Manage environments
├── history/
│   ├── HistoryEntry.kt             # History entry data model
│   └── HistoryManager.kt           # Persist last 100 requests
├── http/
│   ├── HttpMethod.kt               # HTTP method enum
│   ├── ApiRequest.kt               # Request data class
│   ├── ApiResponse.kt              # Response data class
│   └── HttpExecutor.kt             # OkHttp request executor
├── scanner/
│   └── EndpointScanner.kt          # PSI-based REST endpoint scanner
├── settings/
│   ├── LiveApiTesterSettings.kt    # PersistentStateComponent
│   └── LiveApiTesterConfigurable.kt # Settings page UI
└── ui/
    ├── LiveApiTesterToolWindowFactory.kt # Tool window entry point
    ├── RequestPanel.kt             # Request builder UI
    ├── ResponsePanel.kt            # Response viewer UI
    ├── HistoryPanel.kt             # Request history list
    ├── CollectionsPanel.kt         # Collections tree view
    └── EnvironmentPanel.kt         # Environment manager UI
```

---

## 🛠️ Tech Stack

- **Language:** Kotlin 1.9
- **Build:** Gradle 8.8 with IntelliJ Platform Gradle Plugin 1.17.3
- **HTTP Client:** OkHttp 4.12.0
- **JSON:** Gson 2.10.1
- **AI:** OpenAI-compatible REST API
- **UI:** IntelliJ Tool Window + Swing / JBTable / JBTabbedPane
- **Min IntelliJ Version:** 2023.1 (IC or IU)

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

MIT — see [LICENSE](LICENSE) for details.
