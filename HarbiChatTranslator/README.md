# Harbi Chat Translator (Fabric 1.21.10)

**This is source code, not a compiled or tested mod.** A build is required and may require adjusting dependency versions or API signatures. The GitHub Actions workflow is included to attempt that build automatically.

Features: Incoming Arabic -> English, outgoing English -> Arabic, `/msg` `/tell` `/w` `/whisper` translation, period key toggle, username mention sound and private-message sound. Translation toggle persists. Requires Fabric API and Java 21.

## Build without coding
1. Create a free GitHub account if needed.
2. Create a new repository and upload the contents of this ZIP (including `.github`).
3. Open the repository's Actions tab and run **Build Harbi Chat Translator**, or push files to trigger it.
4. If the build succeeds, download the `HarbiChatTranslator-jar` artifact, unzip it, and copy the normal `.jar` (not `-sources.jar`) into the CurseForge profile's `mods` folder.
5. Install Fabric API for Minecraft 1.21.10 in the same CurseForge profile.

## Important limitations
- **Not compiled or verified**. Build could fail due to version/API mismatch.
- Online translation uses MyMemory's public service, with usage limits and no guarantee of availability. Text is sent to a third-party service, including private messages. Do not use for confidential conversations.
- Public server chat can have custom formats. Some PM formats won't be detected, and outgoing translation may not work with custom private-message commands.
- Translations can be imperfect, particularly for Arabic dialects and Minecraft jargon.
- The period toggle activates when not typing in chat, as Minecraft keybinds normally do.
- Incoming translations appear as separate local chat lines; they are not broadcast to other players.
