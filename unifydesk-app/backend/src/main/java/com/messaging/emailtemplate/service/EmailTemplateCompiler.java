package com.messaging.emailtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import com.messaging.emailtemplate.enums.EmailTemplateEditorMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class EmailTemplateCompiler {

  private static final Pattern DANGEROUS =
      Pattern.compile("(?i)<\\s*(script|iframe|object|embed|form|input|textarea|select|button|meta|base)\\b|javascript\\s*:|expression\\s*\\(|\\son[a-z]+\\s*=");
  private static final int MAX_BLOCKS = 100;
  private final ObjectMapper objectMapper;
  private final EmailVariableService variableService;

  public Compiled compile(
      EmailTemplateEditorMode mode,
      String subject,
      String preheader,
      String documentJson,
      String htmlSource) {
    if (mode == null) throw new BadRequestException("Email editor mode is required");
    String html;
    String text;
    if (mode == EmailTemplateEditorMode.BUILDER) {
      var output = compileBuilder(documentJson);
      html = output.html();
      text = output.text();
    } else {
      html = sanitize(htmlSource);
      text = Jsoup.parse(html).text();
      if (text.isBlank()) throw new BadRequestException("HTML template must contain readable content");
    }
    List<String> variables = variableService.extract(subject, preheader, html, text);
    return new Compiled(html, text, variables, sha256((subject == null ? "" : subject) + "\n" + html + "\n" + text));
  }

  private BuilderOutput compileBuilder(String json) {
    if (json == null || json.isBlank()) throw new BadRequestException("Builder document is required");
    final JsonNode root;
    try {
      root = objectMapper.readTree(json);
    } catch (Exception e) {
      throw new BadRequestException("Builder document is invalid JSON");
    }
    JsonNode blocks = root.path("blocks");
    if (!blocks.isArray() || blocks.isEmpty()) throw new BadRequestException("Add at least one content block");
    if (blocks.size() > MAX_BLOCKS) throw new BadRequestException("Too many email blocks");

    String bg = color(root.path("settings").path("backgroundColor").asText(), "#f1f5f9");
    String contentBg = color(root.path("settings").path("contentBackgroundColor").asText(), "#ffffff");
    String accent = color(root.path("settings").path("accentColor").asText(), "#4f46e5");
    int width = Math.max(480, Math.min(720, root.path("settings").path("width").asInt(600)));

    StringBuilder rows = new StringBuilder();
    StringBuilder plain = new StringBuilder();
    for (JsonNode block : blocks) {
      String type = block.path("type").asText("").toUpperCase(Locale.ROOT);
      String value = block.path("text").asText("");
      switch (type) {
        case "HEADER" -> {
          String logo = safeUrl(block.path("logoUrl").asText());
          String brand = block.path("brandText").asText("");
          rows.append("<tr><td style=\"padding:24px 32px;text-align:center\">");
          if (logo != null) rows.append("<img src=\"").append(attr(logo)).append("\" alt=\"Logo\" style=\"max-width:180px;max-height:64px\">");
          else rows.append("<strong style=\"font-size:20px;color:").append(accent).append("\">").append(markup(brand)).append("</strong>");
          rows.append("</td></tr>");
          addPlain(plain, brand);
        }
        case "HERO" -> {
          String image = safeUrl(block.path("imageUrl").asText());
          if (image != null) rows.append("<tr><td><img src=\"").append(attr(image)).append("\" alt=\"\" width=\"100%\" style=\"display:block;width:100%;height:auto\"></td></tr>");
          String heading = block.path("heading").asText("");
          String copy = block.path("text").asText("");
          rows.append("<tr><td style=\"padding:32px;text-align:center\"><h1 style=\"margin:0 0 12px;font-size:32px;color:#0f172a\">").append(markup(heading)).append("</h1><p style=\"margin:0;line-height:1.7;color:#475569\">").append(markup(copy)).append("</p></td></tr>");
          addPlain(plain, heading); addPlain(plain, copy);
        }
        case "HEADING" -> { rows.append("<tr><td style=\"padding:12px 32px\"><h2 style=\"margin:0;color:#0f172a\">").append(markup(value)).append("</h2></td></tr>"); addPlain(plain,value); }
        case "TEXT" -> { rows.append("<tr><td style=\"padding:10px 32px;line-height:1.75;color:#334155\">").append(markup(value)).append("</td></tr>"); addPlain(plain,value); }
        case "IMAGE" -> {
          String image = safeUrl(block.path("imageUrl").asText());
          if (image != null) rows.append("<tr><td style=\"padding:16px 32px;text-align:center\"><img src=\"").append(attr(image)).append("\" alt=\"\" style=\"max-width:100%;height:auto;border-radius:12px\"></td></tr>");
        }
        case "BUTTON" -> {
          String url = safeUrl(block.path("url").asText()); if (url == null) url = "#";
          rows.append("<tr><td style=\"padding:18px 32px;text-align:center\"><a href=\"").append(attr(url)).append("\" style=\"display:inline-block;padding:13px 22px;border-radius:10px;background:").append(accent).append(";color:#fff;text-decoration:none;font-weight:700\">").append(markup(value)).append("</a></td></tr>");
          addPlain(plain,value + ": " + url);
        }
        case "DIVIDER" -> rows.append("<tr><td style=\"padding:8px 32px\"><div style=\"border-top:1px solid #e2e8f0\"></div></td></tr>");
        case "SPACER" -> rows.append("<tr><td height=\"24\" style=\"height:24px\">&nbsp;</td></tr>");
        case "FOOTER" -> { rows.append("<tr><td style=\"padding:24px 32px;background:#f8fafc;border-top:1px solid #e2e8f0;text-align:center;font-size:12px;color:#64748b\">").append(markup(value)).append("</td></tr>"); addPlain(plain,value); }
        default -> throw new BadRequestException("Unsupported email block: " + type);
      }
    }
    String html = "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head><body style=\"margin:0;background:"+bg+"\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding:24px 12px;background:"+bg+"\"><tr><td align=\"center\"><table role=\"presentation\" width=\""+width+"\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:"+width+"px;max-width:100%;background:"+contentBg+";border-radius:16px;overflow:hidden\">"+rows+"</table></td></tr></table></body></html>";
    return new BuilderOutput(html, plain.toString().trim());
  }

  private String sanitize(String source) {
    if (source == null || source.isBlank()) throw new BadRequestException("HTML source is required");
    if (DANGEROUS.matcher(source).find()) throw new BadRequestException("HTML contains unsafe elements");
    Safelist safelist = Safelist.relaxed()
        .addTags("html","head","body","style","table","thead","tbody","tfoot","tr","td","th","center","section","header","footer","main","span","div","h1","h2","h3","h4")
        .addAttributes(":all","style","class","id","role","title")
        .addAttributes("table","width","cellpadding","cellspacing","border","align")
        .addAttributes("td","width","height","align","valign","colspan","rowspan","bgcolor")
        .addAttributes("img","src","alt","width","height")
        .addAttributes("a","href","target","rel")
        .addProtocols("img","src","http","https")
        .addProtocols("a","href","http","https","mailto","tel");
    var clean = new Cleaner(safelist).clean(Jsoup.parse(source));
    clean.outputSettings().prettyPrint(false);
    return clean.outerHtml();
  }

  private String safeUrl(String v) {
    if (v == null || v.isBlank()) return null;
    String s = v.trim();
    if (s.contains("{{") && s.contains("}}")) return s;
    String lower=s.toLowerCase(Locale.ROOT);
    return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:") || lower.startsWith("tel:") ? s : null;
  }
  private String markup(String v){ return HtmlUtils.htmlEscape(v == null ? "" : v).replace("\n","<br>"); }
  private String attr(String v){ return HtmlUtils.htmlEscape(v == null ? "" : v); }
  private String color(String v,String fallback){ return v != null && v.matches("#[0-9a-fA-F]{6}") ? v : fallback; }
  private void addPlain(StringBuilder b,String v){ if(v==null||v.isBlank()) return; if(!b.isEmpty()) b.append("\n\n"); b.append(v.trim()); }
  private String sha256(String v){ try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8))); } catch(Exception e){ throw new IllegalStateException(e); } }

  public record Compiled(String html,String text,List<String> variables,String contentHash){}
  private record BuilderOutput(String html,String text){}
}
