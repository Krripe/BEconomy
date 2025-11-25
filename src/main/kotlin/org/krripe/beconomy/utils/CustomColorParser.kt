package org.krripe.beconomy.utils

import eu.pb4.placeholders.api.PlaceholderContext
import eu.pb4.placeholders.api.Placeholders
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.text.TextColor
import net.minecraft.util.Formatting

object CustomColorParser {

    fun processAllFormats(input: String, player: ServerPlayerEntity? = null): MutableText {
        val (parsed, _) = parseSegment(input, 0, Style.EMPTY, null)
        return if (player != null) {
            Placeholders.parseText(parsed, PlaceholderContext.of(player)).copy() as MutableText
        } else {
            parsed
        }
    }

    private fun parseSegment(text: String, start: Int, incomingStyle: Style, parentTag: String?): Pair<MutableText, Int> {
        val result = Text.literal("")
        var i = start
        var currentStyle = incomingStyle
        var literalStart = i

        fun flushLiteral(from: Int, to: Int) {
            if (to > from) {
                val s = text.substring(from, to)
                if (s.isNotEmpty()) result.append(Text.literal(s).setStyle(currentStyle))
            }
        }

        fun applyVanillaFormatting(c: Char) {
            val f = Formatting.byCode(c)
            currentStyle = if (f == Formatting.RESET) incomingStyle else f?.let { currentStyle.withFormatting(it) } ?: currentStyle
        }

        while (i < text.length) {
            if (text.startsWith("</", i)) {
                val endPos = text.indexOf('>', i + 2)
                if (endPos != -1) {
                    val tagName = text.substring(i + 2, endPos)
                    if (tagName == parentTag) {
                        flushLiteral(literalStart, i)
                        return result to (endPos + 1)
                    }
                }
            }
            if (text[i] == '<') {
                flushLiteral(literalStart, i)
                val endPos = text.indexOf('>', i + 1)
                if (endPos == -1) {
                    literalStart = i
                    i++
                    continue
                }
                val rawTag = text.substring(i + 1, endPos).trim()
                i = endPos + 1
                if (rawTag == "reset" || rawTag == "r") {
                    currentStyle = Style.EMPTY
                    literalStart = i
                    continue
                }
                val basic = setOf("b","i","u","strike","obf")
                if (rawTag in basic) {
                    val ns = when(rawTag) {
                        "b" -> currentStyle.withBold(true)
                        "i" -> currentStyle.withItalic(true)
                        "u" -> currentStyle.withUnderline(true)
                        "strike" -> currentStyle.withStrikethrough(true)
                        "obf" -> currentStyle.withObfuscated(true)
                        else -> currentStyle
                    }
                    val (child, newIndex) = parseSegment(text, i, ns, rawTag)
                    i = newIndex
                    result.append(child)
                    literalStart = i
                    continue
                }
                if (rawTag.startsWith("#") && rawTag.length == 7) {
                    val hex = TextColor.fromRgb(Integer.parseInt(rawTag.substring(1),16))
                    val ns = currentStyle.withColor(hex)
                    val (child, newIndex) = parseSegment(text, i, ns, "#")
                    i = newIndex
                    result.append(child)
                    literalStart = i
                    continue
                }
                if (rawTag.startsWith("gradient:")) {
                    val colorRegex = Regex("^#?[A-Fa-f0-9]{6}$")
                    val cl = rawTag.removePrefix("gradient:")
                        .split(":")
                        .map { it.trim() }
                        .filter { colorRegex.matches(it) }
                        .map { if (it.startsWith("#")) it else "#$it" }
                    val (child, newIndex) = parseSegment(text, i, currentStyle, "gradient")
                    val s = child.string
                    val grad = applyGradient(cl, s, currentStyle)
                    result.append(grad)
                    i = newIndex
                    literalStart = i
                    continue
                }
                if (rawTag.startsWith("click:")) {
                    val m = Regex("(run_command|suggest_command|open_url|copy_to_clipboard):'(.+)'").find(rawTag.removePrefix("click:"))
                    if (m != null) {
                        val (act,v) = m.destructured
                        val e = when(act) {
                            "run_command"->ClickEvent(ClickEvent.Action.RUN_COMMAND,v)
                            "suggest_command"->ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,v)
                            "open_url"->ClickEvent(ClickEvent.Action.OPEN_URL,v)
                            "copy_to_clipboard"->ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD,v)
                            else->null
                        }
                        val ns = if(e!=null) currentStyle.withClickEvent(e) else currentStyle
                        val (child, newIndex) = parseSegment(text, i, ns, "click")
                        i = newIndex
                        result.append(child)
                        literalStart = i
                        continue
                    }
                }
                if (rawTag.startsWith("hover:show_text:")) {
                    val m = Regex("show_text:'(.+)'").find(rawTag.removePrefix("hover:"))
                    if (m != null) {
                        val v = m.groupValues[1]
                        val e = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(v))
                        val ns = currentStyle.withHoverEvent(e)
                        val (child, newIndex) = parseSegment(text, i, ns, "hover")
                        i = newIndex
                        result.append(child)
                        literalStart = i
                        continue
                    }
                }
                if (rawTag.startsWith("link:")) {
                    val url = rawTag.removePrefix("link:")
                    val ns = currentStyle.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL,url))
                    val (child, newIndex) = parseSegment(text, i, ns, "link")
                    i = newIndex
                    result.append(child)
                    literalStart = i
                    continue
                }
                literalStart = i
                continue
            }
            if (text[i]=='&' && i+1<text.length) {
                flushLiteral(literalStart, i)
                applyVanillaFormatting(text[i+1])
                i+=2
                literalStart = i
                continue
            }
            i++
        }
        flushLiteral(literalStart, i)
        return result to i
    }

    private fun applyGradient(colors: List<String>, s: String, st: Style): MutableText {
        val r = Text.literal("")
        if (s.isEmpty()) return r
        val n = s.length - 1
        for (i in s.indices) {
            val c = interpolateColors(colors, i.toFloat()/n.coerceAtLeast(1))
            val color = TextColor.fromRgb(Integer.parseInt(c.substring(1),16))
            r.append(Text.literal(s[i].toString()).setStyle(st.withColor(color)))
        }
        return r
    }

    private fun interpolateColors(colors: List<String>, ratio: Float): String {
        if (colors.isEmpty()) return "#FFFFFF"
        if (colors.size == 1) return colors[0]
        val scaled = ratio * (colors.size - 1)
        val i1 = scaled.toInt().coerceIn(0, colors.size - 2)
        val i2 = i1 + 1
        val localRatio = scaled - i1
        val c1 = parseHex(colors[i1])
        val c2 = parseHex(colors[i2])
        val r = (c1.first + (c2.first - c1.first) * localRatio).toInt()
        val g = (c1.second + (c2.second - c1.second) * localRatio).toInt()
        val b = (c1.third + (c2.third - c1.third) * localRatio).toInt()
        return "#%02X%02X%02X".format(r, g, b)
    }

    private fun parseHex(hex: String): Triple<Int, Int, Int> {
        val h = hex.removePrefix("#")
        val colorInt = h.toInt(16)
        val r = (colorInt shr 16) and 0xFF
        val g = (colorInt shr 8) and 0xFF
        val b = colorInt and 0xFF
        return Triple(r, g, b)
    }
}