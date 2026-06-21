# Sednium Local Spaces (LS)

Sednium LS is a highly capable, autonomous AI chat client designed for Android. It supports multi-provider generation, utilizing various state-of-the-art Large Language Models (LLMs), including on-device capabilities, local servers, and remote API providers. 

## Features

- **Multi-Provider Architecture**: Connect to APIs like OpenAI, Anthropic, Gemini, Groq, xAI, NVIDIA, OpenRouter, Custom Endpoints or interact with local/on-device instances.
- **Three Core Modes**:
  - **⚡ Quick**: Designed for maximal speed, precision, and brevity.
  - **🧠 Thinking**: Employs deep chain-of-thought reasoning before outputting its final answer, ideal for complex logic/math.
  - **💻 Coding**: Configured as an elite software architect for writing clean, optimized, production-ready code with reasoning.
- **Jetpack Compose UI**: Modern, clean, and interactive user interface following Material Design 3 guidelines.
- **Error Boundaries**: Deep-level UI crash catching mechanisms ensuring uninterrupted workflows and high reliability.
- **Rich Markdown Formatting**: Full markdown support ensuring clean, readable content formatting and code blocks for structured data.

## Getting Started

1. Set your API Keys via Google AI Studio Build's Secrets Panel or populate `BuildConfig`/`.env` securely.
2. Ensure you have network connectivity for the remote providers to respond successfully.
3. Chat, drop files, and switch modes depending on your specific query workflows!

## Security

Please do not commit your `.env` or raw `app/build.gradle.kts` files with live API keys hardcoded into them to any public repositories.

## Architecture Guidelines

- Entirely developed with decoupled Kotlin layers and StateFlow ViewModels.
- UI layer implemented using Jetpack Compose components.
- Markdown visualization enabled by composing markdown parser layers over composable elements.
