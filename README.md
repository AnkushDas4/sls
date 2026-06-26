# Sednium LocalSpaces

**A powerful, native Android AI chat client designed for privacy, flexibility, and agentic workflows.**

Sednium LocalSpaces is a fully native Jetpack Compose application that connects you to the world's leading foundational models, as well as your own local models. Whether you're running Ollama on your home server or connecting to Gemini, Claude, and GPT-4o, LocalSpaces provides a unified, highly polished interface.

## 🌟 Key Features

- **Bring Your Own Keys (BYOK)**: Connect directly to providers like Google Gemini, OpenAI, Anthropic Claude, xAI Grok, Groq, OpenRouter, and NVIDIA NIM.
- **Local & Offline AI**: Seamlessly connect to local model runners like **Ollama** or **LM Studio** via custom base URLs. Keep your data entirely private.
- **Multimodal Chat**: Attach images and text files directly to your prompts. The app automatically handles formatting for different provider APIs.
- **Native Voice Input**: Free, on-device voice dictation using Android's native `SpeechRecognizer`. No API costs for voice-to-text.
- **Model Context Protocol (MCP)**: Connect to MCP servers to extend your AI's capabilities with external tools and data sources.
- **Real-time Agent Activity**: Watch the model execute tools and commands in real-time with the built-in Agent Activity overlay.
- **Prompt Lab**: A dedicated workspace to test system instructions, temperature, and parameters before bringing a prompt into a chat session.
- **Configurable Presets**: Save your favorite combinations of models, system instructions, and chat modes (Quick, Thinking, Coding) for instant access.
- **Privacy First**: All your chat histories, API keys, and settings are stored locally on your Android device.
- **Focus Mode & Chat Export**: Minimize distractions with Focus Mode, and easily export your chat histories to text files.

## 🚀 Getting Started

### Prerequisites
- Android Studio (latest stable version recommended)
- Android SDK 34+
- A physical Android device or Emulator running Android 8.0 (API 26) or higher.

### Building the Project
1. Clone the repository:
   ```bash
   git clone https://github.com/sednium/localspaces.git
   ```
2. Open the project in Android Studio.
3. Sync the Gradle project files.
4. Build and run the app on your device or emulator.

## ⚙️ Configuration

LocalSpaces requires API keys to connect to cloud providers. You can add these keys directly within the app's **Settings > API Keys** menu.

### Connecting to Local Models (Ollama)
To connect to an Ollama instance running on your local network:
1. Ensure your Ollama server is accessible from your network (you may need to set `OLLAMA_HOST=0.0.0.0`).
2. In the LocalSpaces app, navigate to **Settings > Providers**.
3. Under the **Local Server** section, enter your server's IP and port (e.g., `http://192.168.1.100:11434/v1`).
4. Select **Local Server** as your active provider.

## 🛠️ Tech Stack

- **UI Toolkit**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Language**: [Kotlin](https://kotlinlang.org/)
- **Networking**: [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/)
- **Serialization**: [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/)

## 🎨 Design

Sednium LocalSpaces utilizes a custom design system inspired by Material 3, focusing on a clean, hacker-friendly aesthetic with distinct typography and high-contrast color palettes (Sednium Yellow, Sednium Red, Milk, and Dark Gray).

## 🤝 Contributing

Contributions are welcome! If you'd like to improve LocalSpaces, please follow these steps:
1. Fork the repository.
2. Create a new branch for your feature or bug fix.
3. Commit your changes with clear, descriptive messages.
4. Submit a Pull Request outlining your changes.

## 📜 License

This project is licensed under the MIT License - see the LICENSE file for details.
