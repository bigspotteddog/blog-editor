package com.example.blogeditor;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMapAdapter;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.commands.HttpRequestCommand;
import com.example.commands.HttpResponseModel;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(value = "remove-this-to-enable/**")
@CrossOrigin(origins = "*")
public class HttpRequestController {
    @GetMapping
    @ResponseBody
    public ResponseEntity<InputStreamResource> get(HttpServletRequest req) throws Exception {
        String urlPrefix = "http://localhost:8888/editor/?url=";
        String servletPath = req.getServletPath();
        System.out.println(servletPath);
        String queryString = req.getQueryString();
        System.out.println(queryString);

        if (servletPath.contains("bootstrap-icons.css") || (queryString != null && queryString.contains("bootstrap-icons.css"))) {
            System.out.println("hello");
        }

        if (servletPath.equals("/index.html") ||
            servletPath.equals("/")) {
                ClassPathResource resource = new ClassPathResource("static/index.html");
                return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(new InputStreamResource(resource.getInputStream()));
        }

        String path = servletPath;

        String proxy = null;
        
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            System.out.println("=== Cookies ===");
            for (Cookie cookie : cookies) {
                System.out.println(String.format("Name: %s, Value: %s, Domain: %s, Path: %s", 
                    cookie.getName(), 
                    cookie.getValue(), 
                    cookie.getDomain(), 
                    cookie.getPath()));
                if (cookie.getName().equals("proxy")) {
                    proxy = cookie.getValue();
                }
            }
            if (proxy != null) {
                path = proxy;
            }
        } else {
            System.out.println("No cookies present in request");
        }

        Map<String, String> query = new HashMap<>();
        if (queryString != null) {
            String[] split = queryString.split("&");
            for (String param : split) {
                try {
                    String[] entry = param.split("=");
                    String key = entry[0];
                    if (entry.length > 1) {
                        String value = entry[1];
                        query.put(key, value);
                    }
                } catch (Exception e) {
                    System.out.println(servletPath);
                    e.printStackTrace();
                }
            }

            String url = query.get("url");
            if (url != null) {
                path = url;
            }    
        }

        System.out.print(path);

        if (path.startsWith("https://fonts.googleapis.com/css2")) {
            System.out.println("hello");
        }

        String referer = req.getHeader("referer");
        System.out.println(referer);

        String origin = req.getHeader("origin");
        System.out.println(origin);

        Map<String, List<String>> headers = HttpRequestCommand.getHeaders(req);
        headers.put("origin", Arrays.asList(proxy));
        headers.put("referer", Arrays.asList(proxy));
        HttpRequestCommand request = new HttpRequestCommand(path, HttpRequestCommand.Method.valueOf(req.getMethod().toUpperCase()), headers);
        HttpResponseModel response = request.execute();
        MultiValueMapAdapter<String, String> map = new MultiValueMapAdapter<>(response.getHeaders());
        int status = response.getStatus();
        System.out.println(status);

        if (status != 200) {
            System.out.println("not 200");
        }
        // Handle redirect
        // if (status == 302 || status == 301) {
        //     String redirectLocation = map.getFirst("Location");
        //     if (redirectLocation == null) {
        //         redirectLocation = map.getFirst("location");
        //     }
        //     // Either follow the redirect or pass it back to the client
        //     return ResponseEntity.status(status)
        //         .header(HttpHeaders.LOCATION, redirectLocation.replace(base, ""))
        //         .build();
        // }

        String contentType = "text/html;charset=utf-8";

        List<String> contentTypeList = map.get("Content-Type");
        if (contentTypeList == null) {
            contentTypeList = map.get("content-type");
        }
        if (contentTypeList != null) {
            contentType = contentTypeList.getFirst();
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();

        byte[] bytes = response.getBytes();
        if (contentType.startsWith("text/html")) {
            DependencyManager deps = new DependencyManager(proxy, urlPrefix);
            String html = new String(bytes);
            String html2 = deps.processHtml(html);
            // String html2 = UrlReplacer.replaceUrls(html, urlPrefix, proxy);
            bytes = html2.getBytes();
        } else if (contentType.startsWith("text/css")) {
            DependencyManager deps = new DependencyManager(path, urlPrefix);
            String css = new String(bytes);
            String css2 = deps.processCss(css);
            bytes = css2.getBytes();
        } else if (contentType.startsWith("text/javascript") || contentType.startsWith("application/javascript")) {
            DependencyManager deps = new DependencyManager(path, urlPrefix);
            String js = new String(bytes);
            String js2 = deps.processJsModule(path, js);
            // String js2 = UrlReplacer.replaceJsUrls(js, urlPrefix, path);
            bytes = js2.getBytes();

            builder.header("access-control-allow-origin", "*");
            builder.header("cache-control", "no-cache");
            builder.header("content-security-policy", "script-src 'self'; object-src 'self'");
            builder.header("cross-origin-resource-policy", "cross-origin");


            // builder.header("Access-Control-Allow-Origin", "http://editor.local:8888");
            // builder.header("Cross-Origin-Resource-Policy", "cross-origin");
            // builder.header("Cache-Control", "public, max-age=31536000, immutable");
            contentType = "application/javascript";
        }
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);

        // Add headers conditionally
        if (proxy != null) {
            // builder.header(HttpHeaders.SET_COOKIE, "editor=" + proxy + "; Path=/;");
        }

        ResponseEntity<InputStreamResource> responseEntity = builder
                .contentType(MediaType.valueOf(contentType))
                .body(new InputStreamResource(in));

        return responseEntity;
    }
}
