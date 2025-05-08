package com.example.blogeditor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * Utility class to replace URLs in HTML content with prefixed versions.
 */
public class UrlReplacer {

    /**
     * Replaces all URLs in HTML content with a prefixed version.
     * 
     * @param htmlContent The HTML content to process
     * @param urlPrefix The prefix to add to each URL (e.g., "http://localhost:8080?url=")
     * @param baseUrl The base URL to resolve relative URLs against (e.g., "https://stripe.com")
     * @return The processed HTML content with replaced URLs
     */
    public static String replaceUrls(String htmlContent, String urlPrefix, String baseUrl) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return htmlContent;
        }
        
        // Trim trailing slash from base URL if present
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        // Define patterns for URL attributes in HTML tags
        // This pattern matches attributes like href="...", src="...", action="...", etc.
        String attributePattern = "(\\s+)(href|src|action|data|formaction|cite|longdesc|poster|background|usemap|codebase)\\s*=\\s*['\"]([^'\"]*)['\"]";
        Pattern pattern = Pattern.compile(attributePattern, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(htmlContent);
        
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String spacing = matcher.group(1);
            String attributeName = matcher.group(2);
            String urlValue = matcher.group(3);
            
            // Skip if the URL is empty, javascript:, data:, or #
            if (urlValue.isEmpty() || 
                urlValue.startsWith("javascript:") || 
                urlValue.startsWith("data:") || 
                urlValue.equals("#")) {
                continue;
            }
            
            // Resolve relative URLs against the base URL
            String resolvedUrl = resolveUrl(baseUrl, urlValue);
            
            // Replace the URL with the prefixed version
            String replacement = spacing + attributeName + "=\"" + urlPrefix + resolvedUrl + "\"";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        
        matcher.appendTail(result);
        
        // Handle inline CSS with url() references
        String cssUrlPattern = "(url\\s*\\(\\s*['\"]?)([^'\")]+)(['\"]?\\s*\\))";
        Pattern cssPattern = Pattern.compile(cssUrlPattern);
        Matcher cssMatcher = cssPattern.matcher(result);
        
        StringBuffer finalResult = new StringBuffer();
        
        while (cssMatcher.find()) {
            String urlOpen = cssMatcher.group(1);
            String urlValue = cssMatcher.group(2);
            String urlClose = cssMatcher.group(3);
            
            // Skip data: URLs and empty URLs
            if (urlValue.isEmpty() || 
                urlValue.startsWith("data:") || 
                urlValue.startsWith("javascript:") || 
                urlValue.equals("#")) {
                continue;
            }
            
            // Resolve relative URLs against the base URL
            String resolvedUrl = resolveUrl(baseUrl, urlValue);
            
            // Replace the URL with the prefixed version
            String replacement = urlOpen + urlPrefix + resolvedUrl + urlClose;
            cssMatcher.appendReplacement(finalResult, Matcher.quoteReplacement(replacement));
        }
        
        cssMatcher.appendTail(finalResult);
        
        return finalResult.toString();
    }
    
    /**
     * Resolves a relative URL against a base URL.
     * 
     * @param baseUrl The base URL
     * @param relativeUrl The relative URL to resolve
     * @return The resolved absolute URL
     */
    private static String resolveUrl(String baseUrl, String relativeUrl) {
        // If the URL is already absolute, return it as is
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }
        
        try {
            URL base = new URL(baseUrl);
            
            // Handle protocol-relative URLs (starting with //)
            if (relativeUrl.startsWith("//")) {
                return base.getProtocol() + ":" + relativeUrl;
            }
            
            // Create a new URL by resolving against the base
            // This properly handles cases like "../images/logo.png"
            URL resolvedURL = new URL(base, relativeUrl);
            return resolvedURL.toString();
        } catch (MalformedURLException e) {
            // If something goes wrong, return the original URL
            return relativeUrl;
        }
    }
    
    /**
     * Example usage of the URL replacer.
     */
    public static void main(String[] args) {
        String htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <link rel="stylesheet" href="/styles/main.css">
                <script src="https://cdn.example.com/script.js"></script>
                <style>
                    body { background: url('images/bg.jpg'); }
                </style>
            </head>
            <body>
                <a href="/products">Products</a>
                <img src="../images/logo.png">
                <form action="https://api.example.com/submit" method="post">
                    <button formaction="https://stripe.com/api/action">Submit</button>
                </form>
                <div style="background: url(/images/pattern.png)"></div>
            </body>
            </html>
            """;
            
        String urlPrefix = "http://localhost:8080/editor/?url=";
        String baseUrl = "https://stripe.com";
        
        String result = replaceUrls(htmlContent, urlPrefix, baseUrl);
        System.out.println(result);
    }
}