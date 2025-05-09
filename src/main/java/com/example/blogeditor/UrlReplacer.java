package com.example.blogeditor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * Utility class to replace URLs in HTML and JavaScript content with prefixed versions.
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
        
        // Process HTML content
        String result = replaceHtmlUrls(htmlContent, urlPrefix, baseUrl);
        
        // Process any JavaScript content within the HTML
        result = replaceJsUrls(result, urlPrefix, baseUrl);
        
        // Process JSON content within script tags
        result = replaceJsonUrls(result, urlPrefix, baseUrl);
        
        return result;
    }
    
    /**
     * Replaces URLs in HTML content.
     */
    private static String replaceHtmlUrls(String htmlContent, String urlPrefix, String baseUrl) {
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
     * Replaces all URLs in JavaScript content with a prefixed version.
     * 
     * @param jsContent The JavaScript content to process
     * @param urlPrefix The prefix to add to each URL
     * @param baseUrl The base URL to resolve relative URLs against
     * @return The processed JavaScript content with replaced URLs
     */
    public static String replaceJsUrls(String jsContent, String urlPrefix, String baseUrl) {
        if (jsContent == null || jsContent.isEmpty()) {
            return jsContent;
        }
        
        // Handle minified JavaScript with various import patterns
        // This handles both normal and minified JS with or without spaces
        String[] patterns = {
            // Static imports with named exports: import{a}from"./path.js" or import { a } from "./path.js"
            "(import\\s*\\{[^}]*\\}\\s*from\\s*[\"'])([^\"']+)([\"'])",
            
            // Simple imports: import"./path.js" or import "./path.js"
            "(import\\s*[\"'])([^\"']+)([\"'])",
            
            // Dynamic imports: import("./path.js")
            "(import\\s*\\(\\s*[\"'])([^\"']+)([\"']\\s*\\))"
        };
        
        String result = jsContent;
        
        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(result);
            StringBuffer buffer = new StringBuffer();
            
            while (matcher.find()) {
                String prefix = matcher.group(1);
                String urlValue = matcher.group(2);
                String suffix = matcher.group(3);
                
                // Skip if not a JS file path or is a special import
                if (!isJsFilePath(urlValue) || isSpecialJsImport(urlValue)) {
                    continue;
                }
                
                // Resolve relative URLs against the base URL
                String resolvedUrl = resolveUrl(baseUrl, urlValue);
                
                // Replace with prefixed URL
                String replacement = prefix + urlPrefix + resolvedUrl + suffix;
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            }
            
            matcher.appendTail(buffer);
            result = buffer.toString();
        }
        
        // Process URLs in JavaScript string literals
        result = processJsStringUrls(result, urlPrefix, baseUrl);
        
        return result;
    }
    
    /**
     * Processes URLs found in JavaScript string literals.
     */
    private static String processJsStringUrls(String content, String urlPrefix, String baseUrl) {
        // Pattern to find URLs in JS string literals
        // This looks for http:// or https:// URLs in single or double quotes
        String urlPattern = "(['\"])(https?://[^'\"\\s]+?)(['\"])";
        Pattern pattern = Pattern.compile(urlPattern);
        Matcher matcher = pattern.matcher(content);
        
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String quote = matcher.group(1);
            String url = matcher.group(2);
            String endQuote = matcher.group(3);
            
            // Skip if not a valid URL or is a special URL
            if (url.isEmpty() || shouldSkipUrl(url)) {
                continue;
            }
            
            String replacement = quote + urlPrefix + url + endQuote;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Replaces URLs in JSON content found within script tags.
     */
    private static String replaceJsonUrls(String content, String urlPrefix, String baseUrl) {
        // Find script tags with application/json type
        String scriptPattern = "(<script[^>]*type\\s*=\\s*['\"]application/json['\"][^>]*>)(.*?)(</script>)";
        Pattern pattern = Pattern.compile(scriptPattern, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String scriptOpen = matcher.group(1);
            String jsonContent = matcher.group(2);
            String scriptClose = matcher.group(3);
            
            // Process the JSON content to find and replace URLs
            String processedJson = processJsonStringUrls(jsonContent, urlPrefix, baseUrl);
            
            String replacement = scriptOpen + processedJson + scriptClose;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        
        matcher.appendTail(result);
        
        // Also process inline JSON strings outside of script tags
        // This handles JSON embedded in data attributes or JavaScript variables
        return result.toString();
    }
    
    /**
     * Processes URLs found in JSON string values.
     */
    private static String processJsonStringUrls(String jsonContent, String urlPrefix, String baseUrl) {
        // Pattern to find JSON key-value pairs with URLs
        // Looks for "key":"http://..." or "key": "http://..."
        String urlPattern = "(\"\\s*[^\"]+\\s*\"\\s*:\\s*\")(https?://[^\"]+)(\"\\s*)";
        Pattern pattern = Pattern.compile(urlPattern);
        Matcher matcher = pattern.matcher(jsonContent);
        
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String keyPrefix = matcher.group(1);
            String url = matcher.group(2);
            String suffix = matcher.group(3);
            
            // Skip if not a valid URL or is a special URL
            if (url.isEmpty() || shouldSkipUrl(url)) {
                continue;
            }
            
            String replacement = keyPrefix + urlPrefix + url + suffix;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Checks if a path is likely a JavaScript file path.
     */
    private static boolean isJsFilePath(String path) {
        // Check for .js extension or relative/absolute path indicators
        return path.endsWith(".js") || path.startsWith("./") || 
               path.startsWith("../") || path.startsWith("/") ||
               path.matches("^(?:https?:)?//.*\\.js$");
    }
    
    /**
     * Checks if a JavaScript import is a special type that shouldn't be processed.
     */
    private static boolean isSpecialJsImport(String path) {
        // Skip built-in Node.js modules or browser-native modules
        return path.matches("^[a-zA-Z0-9_-]+$") || // bare module specifiers
               path.startsWith("node:") ||         // Node.js protocol imports
               path.startsWith("data:") ||         // Data URIs
               path.startsWith("blob:") ||         // Blob URIs
               path.startsWith("javascript:");     // JavaScript URIs
    }
    
    /**
     * Checks if a URL should be skipped from replacement.
     */
    private static boolean shouldSkipUrl(String url) {
        return url.startsWith("data:") ||         // Data URIs
               url.startsWith("blob:") ||         // Blob URIs
               url.startsWith("javascript:") ||   // JavaScript URIs
               url.equals("#") ||                 // Anchor links
               url.contains("localhost") ||       // Already localhost URLs
               url.contains("127.0.0.1");         // Local IP addresses
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
        // Example HTML content
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
                    <button formaction="/api/action">Submit</button>
                </form>
                <div style="background: url(/images/pattern.png)"></div>
                <script>
                    import { helper } from './utils.js';
                    import('./dynamic-module.js').then(module => {
                        console.log(module);
                    });
                    
                    // String with URL
                    const apiEndpoint = "https://api.example.com/v1/data";
                    fetch(apiEndpoint);
                </script>
                <script type="application/json" id="AnalyticsConfigurationJSON">
                    {"GTM_ID":"GTM-WK8882T","GTM_FRAME_URL":"https://b.stripecdn.com/stripethirdparty-srv/assets/","environment":"production"}
                </script>
            </body>
            </html>
            """;
        
        // Example JavaScript content - both regular and minified formats
        String jsContent1 = """
            import {a} from "./v1-chunk-L3B776EL.js";
            import "./v1-chunk-6IOYUKIA.js";
            import "./v1-chunk-QAMXPDSP.js";
            import "./v1-chunk-NW6KZYBF.js";
            import "./v1-chunk-KWARAS4N.js";
            export {a as loadScripts};
            """;
            
        String jsContent2 = """
            import{a}from"./v1-chunk-L3B776EL.js";import"./v1-chunk-6IOYUKIA.js";import"./v1-chunk-QAMXPDSP.js";import"./v1-chunk-NW6KZYBF.js";import"./v1-chunk-KWARAS4N.js";export{a as loadScripts};
            """;
            
        String urlPrefix = "https://sheep-warm-cicada.ngrok-free.app?url=";
        String baseUrl = "https://stripe.com";
        
        System.out.println("==== PROCESSED HTML ====");
        String processedHtml = replaceUrls(htmlContent, urlPrefix, baseUrl);
        System.out.println(processedHtml);
        
        System.out.println("\n==== PROCESSED JS (Regular Format) ====");
        String processedJs1 = replaceJsUrls(jsContent1, urlPrefix, baseUrl);
        System.out.println(processedJs1);
        
        System.out.println("\n==== PROCESSED JS (Minified Format) ====");
        String processedJs2 = replaceJsUrls(jsContent2, urlPrefix, baseUrl);
        System.out.println(processedJs2);
        
        // Example with just the JSON script tag
        String jsonScriptExample = """
            <script type="application/json" id="AnalyticsConfigurationJSON">{"GTM_ID":"GTM-WK8882T","GTM_FRAME_URL":"https://b.stripecdn.com/stripethirdparty-srv/assets/","environment":"production"}</script>
            """;
        
        System.out.println("\n==== PROCESSED JSON SCRIPT ====");
        String processedJsonScript = replaceUrls(jsonScriptExample, urlPrefix, baseUrl);
        System.out.println(processedJsonScript);
    }
}