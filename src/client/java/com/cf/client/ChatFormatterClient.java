package com.cf.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatFormatterClient implements ClientModInitializer {

    // Regex pattern to match flexible URLs and all Markdown styles:
    // Group 1: URLs (with or without http:// / https://)
    // Group 2: Bold (**text**)
    // Group 3 & 4: Italics (*text* or _text_)
    // Group 5: Strikethrough (~~text~~)
    // Group 6: Underline (__text__)
    // Group 7: Inline Code (`text`)
    private static final Pattern CHAT_PATTERN = Pattern.compile(
            "(https?://\\S+|\\b[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\\S*)|" +
                    "\\*\\*(.*?)\\*\\*|" +
                    "(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)|" +
                    "_(.*?)_|" +
                    "~~(.*?)~~|" +
                    "__(.*?)__|" +
                    "`(.*?)`"
    );

    @Override
    public void onInitializeClient() {
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String rawText = message.getString();
            Component formattedMessage = parseChatText(rawText);

            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.player.sendSystemMessage(formattedMessage);
            }

            return false;
        });
    }

    private Component parseChatText(String input) {
        MutableComponent result = Component.empty();
        Matcher matcher = CHAT_PATTERN.matcher(input);

        int lastEnd = 0;

        while (matcher.find()) {
            // Append plain text leading up to the match
            if (matcher.start() > lastEnd) {
                result.append(Component.literal(input.substring(lastEnd, matcher.start())));
            }

            if (matcher.group(1) != null) {
                // Flexible URL Match
                String rawUrl = matcher.group(1);
                String fullUrl = rawUrl.startsWith("http") ? rawUrl : "https://" + rawUrl;

                MutableComponent linkText = Component.literal(rawUrl)
                        .withStyle(ChatFormatting.AQUA)
                        .withStyle(ChatFormatting.UNDERLINE);
                linkText.withStyle(style -> style.withClickEvent(new ClickEvent.OpenUrl(URI.create(fullUrl))));
                result.append(linkText);

            } else if (matcher.group(2) != null) {
                // Bold (**text**)
                result.append(Component.literal(matcher.group(2)).withStyle(ChatFormatting.BOLD));

            } else if (matcher.group(3) != null || matcher.group(4) != null) {
                // Italic (*text* or _text_)
                String content = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
                result.append(Component.literal(content).withStyle(ChatFormatting.ITALIC));

            } else if (matcher.group(5) != null) {
                // Strikethrough (~~text~~)
                result.append(Component.literal(matcher.group(5)).withStyle(ChatFormatting.STRIKETHROUGH));

            } else if (matcher.group(6) != null) {
                // Underline (__text__)
                result.append(Component.literal(matcher.group(6)).withStyle(ChatFormatting.UNDERLINE));

            } else if (matcher.group(7) != null) {
                // Inline Code (`text`) -> Styled gray for a clean code block look
                result.append(Component.literal(matcher.group(7)).withStyle(ChatFormatting.GRAY));
            }

            lastEnd = matcher.end();
        }

        // Append any remaining trailing text
        if (lastEnd < input.length()) {
            result.append(Component.literal(input.substring(lastEnd)));
        }

        return result;
    }
}