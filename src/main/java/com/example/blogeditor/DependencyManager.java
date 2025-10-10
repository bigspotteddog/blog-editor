package com.example.blogeditor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A complete solution to manage JavaScript dependencies, handle URL rewriting,
 * and resolve the dynamic import issue.
 */
public class DependencyManager {
    private final String baseUrl;
    private final String urlPrefix;
    private final Set<String> processedModules = new HashSet<>();
    private final Map<String, String> moduleCache = new HashMap<>();

    // This should remain off if we are going to save pages because it changes the urls.
    // Pages to be edited should simply be served from the same domain.
    private final boolean active = false;
    
    /**
     * Creates a new dependency manager.
     * 
     * @param baseUrl The base URL for resolving relative URLs
     * @param urlPrefix The prefix to add to URLs
     */
    public DependencyManager(String baseUrl, String urlPrefix) {
        if (baseUrl == null) {
            System.out.println("hello");
        }
        // this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.baseUrl = baseUrl;
        this.urlPrefix = urlPrefix;
    }
    
    /**
     * Processes an HTML document and all its dependencies.
     * 
     * @param htmlContent The HTML content to process
     * @return The processed HTML with the import proxy helper script added
     */
    public String processHtml(String htmlContent) {
        if (!active) {
            return htmlContent;
        }

        // First, add our import proxy helper script to the head section
        String proxyHelperScript = generateImportProxyScript();
        String htmlWithHelper = addHelperScript(htmlContent, proxyHelperScript);
        
        // Process the HTML content (URLs in attributes, inline styles, etc.)
        String processedHtml = UrlReplacer.replaceUrls(htmlWithHelper, urlPrefix, baseUrl);
        
        return processedHtml;
    }
    
    public String processCss(String cssContent) {     
        if (!active) {
            return cssContent;
        }

        String processedCss = UrlReplacer.replaceCssUrls(cssContent, urlPrefix, baseUrl);
        return processedCss;
    }

    /**
     * Adds the import proxy helper script to the HTML head section.
     */
    private String addHelperScript(String htmlContent, String script) {
        if (!active) {
            return htmlContent;
        }

        // Find the end of the head tag
        Pattern headPattern = Pattern.compile("</head>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = headPattern.matcher(htmlContent);
        
        if (matcher.find()) {
            StringBuilder sb = new StringBuilder(htmlContent);
            sb.insert(matcher.start(), script);
            return sb.toString();
        }
        
        // If no head tag, add one
        Pattern htmlPattern = Pattern.compile("<html[^>]*>", Pattern.CASE_INSENSITIVE);
        matcher = htmlPattern.matcher(htmlContent);
        
        if (matcher.find()) {
            StringBuilder sb = new StringBuilder(htmlContent);
            sb.insert(matcher.end(), "\n<head>\n" + script + "\n</head>\n");
            return sb.toString();
        }
        
        // Last resort: add at the beginning
        return "<html>\n<head>\n" + script + "\n</head>\n" + htmlContent + "\n</html>";
    }
    
    /**
     * Generates the import proxy helper script.
     */
    private String generateImportProxyScript() {
        return """
<!-- Import proxy helper script to handle dynamic imports -->
<script type="module">
    window.__importProxy = function(importPromise) {
        return importPromise.catch(function(error) {
            if (error.message && error.message.includes('not allowed for dynamic import')) {
                console.log('Import error detected, attempting to fix...');

                var urlMatch = /import.*?['"]([^'"]+)['"]/i.exec(error.stack);
                if (urlMatch && urlMatch[1]) {
                    var fullUrl = urlMatch[1];
                    console.log('Problematic URL:', fullUrl);

                    var urlParts = fullUrl.split('?url=');
                    var originalUrl = urlParts.length > 1 ? urlParts[1] : fullUrl;

                    if (originalUrl) {
                        console.log('Attempting direct import of:', originalUrl);
                        return import(originalUrl).catch(function(secondError) {
                            console.error('Direct import also failed:', secondError);
                            throw secondError;
                        });
                    }
                }
            }

            console.error('Unable to handle import error:', error);
            throw error;
        });
    };

    window.__originalDynamicImport = (url) => import(url);

    window.__getOriginalUrl = function(url) {
        if (url.includes('?url=')) {
            return url.split('?url=')[1];
        }
        return url;
    };
</script>""";
    }
    
    /**
     * Process a JavaScript module and all its dependencies.
     * 
     * @param jsPath The path to the JavaScript module
     * @return The processed JavaScript content
     */
    public String processJsModule(String jsPath, String content) throws IOException {
        if (!active) {
            return content;
        }

        // Check if we've already processed this module
        if (processedModules.contains(jsPath)) {
            return moduleCache.get(jsPath);
        }
        
        // Mark as processed to avoid circular dependencies
        processedModules.add(jsPath);
        
        // Process the module content
        String processedContent = UrlReplacer.replaceJsUrls(content, urlPrefix, baseUrl);
        
        // Cache the processed content
        moduleCache.put(jsPath, processedContent);
        
        return processedContent;
    }
    
    /**
     * Extract all JavaScript module imports from HTML content.
     * 
     * @param htmlContent The HTML content to scan
     * @return A list of JavaScript module paths
     */
    public List<String> extractJsImports(String htmlContent) {
        List<String> imports = new ArrayList<>();
        
        // Find script tags with src attribute
        Pattern scriptPattern = Pattern.compile("<script[^>]*src\\s*=\\s*['\"]([^'\"]*)['\"][^>]*>", 
                                               Pattern.CASE_INSENSITIVE);
        Matcher matcher = scriptPattern.matcher(htmlContent);
        
        while (matcher.find()) {
            String src = matcher.group(1);
            if (src.endsWith(".js")) {
                imports.add(src);
            }
        }
        
        // Find JS imports in inline script tags
        Pattern inlinePattern = Pattern.compile("<script[^>]*>(.*?)</script>", 
                                               Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        matcher = inlinePattern.matcher(htmlContent);
        
        while (matcher.find()) {
            String scriptContent = matcher.group(1);
            imports.addAll(extractJsImportsFromJs(scriptContent));
        }
        
        return imports;
    }
    
    /**
     * Extract JavaScript imports from JavaScript content.
     */
    private List<String> extractJsImportsFromJs(String jsContent) {
        List<String> imports = new ArrayList<>();
        
        // Match static imports
        Pattern staticImportPattern = Pattern.compile("import\\s+(?:\\{[^}]*\\}\\s+from\\s+)?['\"]([^'\"]+)['\"]", 
                                                     Pattern.DOTALL);
        Matcher matcher = staticImportPattern.matcher(jsContent);
        
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        
        // Match dynamic imports
        Pattern dynamicImportPattern = Pattern.compile("import\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)", 
                                                      Pattern.DOTALL);
        matcher = dynamicImportPattern.matcher(jsContent);
        
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        
        return imports;
    }
    
    /**
     * Main method for testing.
     */
    public static void main(String[] args) {
        String baseUrl = "https://stripe.com";
        String urlPrefix = "http://editor.local:8888/editor/?url=";
        
        // Example HTML content with dynamic imports
        String htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <link rel="stylesheet" href="/styles/main.css">
                <script src="https://cdn.example.com/script.js"></script>
            </head>
            <body>
                <script type="module">
                    import { helper } from './utils.js';
                    import('./dynamic-module.js').then(module => {
                        console.log(module);
                    });
                </script>
                <script type="application/json" id="AnalyticsConfigurationJSON">
                    {"GTM_ID":"GTM-WK8882T","GTM_FRAME_URL":"https://b.stripecdn.com/stripethirdparty-srv/assets/","environment":"production"}
                </script>
            </body>
            </html>
            """;
        
        DependencyManager manager = new DependencyManager(baseUrl, urlPrefix);
        String processedHtml = manager.processHtml(htmlContent);
        
        System.out.println("=== PROCESSED HTML WITH IMPORT PROXY ===");
        System.out.println(processedHtml);
    }
}