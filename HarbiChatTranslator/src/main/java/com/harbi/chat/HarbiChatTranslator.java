package com.harbi.chat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class HarbiChatTranslator implements ClientModInitializer {
 private static final MinecraftClient MC = MinecraftClient.getInstance();
 private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
 private static final Gson GSON = new Gson();
 private static final Pattern ARABIC = Pattern.compile("[\\u0600-\\u06FF]");
 private static final Pattern PM = Pattern.compile("(?i)(\\bfrom\\b|\\bto you\\b|\\bwhispers?\\b|\\bmsg\\b|\\bpm\\b|\\btells you\\b|رسالة خاصة|يهمس)");
 private static final Path CONFIG = Path.of("config", "harbi-chat-translator.json");
 private static boolean outgoingArabic = true;
 private static boolean resending = false;
 private static long lastMention = 0, lastPm = 0;
 private static KeyBinding toggle;

 @Override public void onInitializeClient() {
  load();
  toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.harbichattranslator.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, "category.harbichattranslator"));
  ClientTickEvents.END_CLIENT_TICK.register(client -> {
   while (toggle.wasPressed()) { outgoingArabic = !outgoingArabic; save(); notifyUser("Arabic outgoing translation: " + (outgoingArabic ? "ON" : "OFF")); }
  });
  ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay) incoming(message.getString()); });
  ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> incoming(message.getString()));
  ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
   if (resending || !outgoingArabic || message.isBlank() || ARABIC.matcher(message).find()) return true;
   translate(message, "ar").thenAccept(translated -> MC.execute(() -> {
    if (MC.player == null) return;
    if (translated == null) { notifyUser("Translation failed. Message not sent."); return; }
    resending = true;
    try { MC.player.networkHandler.sendChatMessage(translated); } finally { resending = false; }
    notifyUser("Sent in Arabic: " + translated);
   }));
   return false;
  });
  ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
   if (resending || !outgoingArabic) return true;
   String lower = command.toLowerCase(java.util.Locale.ROOT);
   String prefix = null;
   for (String p : new String[]{"msg ","tell ","w ","whisper "}) if (lower.startsWith(p)) { prefix = command.substring(0, p.length()); break; }
   if (prefix == null) return true;
   String rest = command.substring(prefix.length()); int split = rest.indexOf(' ');
   if (split < 0) return true;
   String recipient = rest.substring(0, split), body = rest.substring(split+1);
   if (body.isBlank() || ARABIC.matcher(body).find()) return true;
   String commandPrefix = prefix + recipient + " ";
   translate(body, "ar").thenAccept(translated -> MC.execute(() -> {
    if (MC.player == null) return;
    if (translated == null) { notifyUser("Private message translation failed. Not sent."); return; }
    resending = true;
    try { MC.player.networkHandler.sendChatCommand(commandPrefix + translated); } finally { resending = false; }
    notifyUser("Arabic private message sent.");
   }));
   return false;
  });
 }
 private static void incoming(String raw) {
  if (raw == null || raw.isBlank() || MC.player == null) return;
  String username = MC.getSession().getUsername();
  String lower = raw.toLowerCase(java.util.Locale.ROOT);
  long now = System.currentTimeMillis();
  if (username.length() > 1 && lower.matches("(?s).*?(?<![a-zA-Z0-9_])" + Pattern.quote(username.toLowerCase(java.util.Locale.ROOT)) + "(?![a-zA-Z0-9_]).*")) {
   if (now - lastMention > 2500) { lastMention = now; sound(true); }
  }
  if (PM.matcher(raw).find() && now - lastPm > 2500) { lastPm = now; sound(false); }
  if (ARABIC.matcher(raw).find()) translate(raw, "en").thenAccept(result -> MC.execute(() -> {
   if (result != null && MC.inGameHud != null) MC.inGameHud.getChatHud().addMessage(Text.literal("§a[EN] §f" + result));
  }));
 }
 private static void sound(boolean mention) {
  if (MC.player != null) MC.player.playSound(mention ? SoundEvents.BLOCK_NOTE_BLOCK_BELL.value() : SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, mention ? 1.2f : 0.9f);
 }
 private static void notifyUser(String message) {
  if (MC.player != null) MC.player.sendMessage(Text.literal("§b[Harbi] §f" + message), true);
 }
 private static CompletableFuture<String> translate(String message, String target) {
  // Public MyMemory endpoint has usage limits and is not suitable for sensitive messages.
  String source = target.equals("ar") ? "en" : "ar";
  String query = "https://api.mymemory.translated.net/get?q=" + URLEncoder.encode(message, StandardCharsets.UTF_8) + "&langpair=" + source + "%7C" + target;
  HttpRequest request = HttpRequest.newBuilder(URI.create(query)).timeout(java.time.Duration.ofSeconds(10)).GET().build();
  return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).orTimeout(12, TimeUnit.SECONDS)
   .thenApply(response -> {
    if (response.statusCode() != 200) return null;
    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
    if (!root.has("responseData")) return null;
    JsonObject data = root.getAsJsonObject("responseData");
    String result = data.get("translatedText").getAsString();
    if (result.isBlank() || result.equalsIgnoreCase(message)) return null;
    return result;
   }).exceptionally(error -> null);
 }
 private static void load() {
  try { if (Files.exists(CONFIG)) outgoingArabic = JsonParser.parseString(Files.readString(CONFIG)).getAsJsonObject().get("outgoingArabic").getAsBoolean(); }
  catch (Exception ignored) { outgoingArabic = true; }
 }
 private static void save() {
  try { Files.createDirectories(CONFIG.getParent()); JsonObject data = new JsonObject(); data.addProperty("outgoingArabic", outgoingArabic); Files.writeString(CONFIG, GSON.toJson(data)); }
  catch (Exception ignored) {}
 }
}
