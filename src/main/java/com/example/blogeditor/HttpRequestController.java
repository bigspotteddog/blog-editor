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
@RequestMapping(value = "/**")
@CrossOrigin(origins = "*")
public class HttpRequestController {
    @GetMapping
    @ResponseBody
    public ResponseEntity<InputStreamResource> get(HttpServletRequest req) throws Exception {
        if (req.getServletPath().equals("/index.html") || req.getServletPath().equals("/")) {
            ClassPathResource resource = new ClassPathResource("static/index.html");
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new InputStreamResource(resource.getInputStream()));
        }

        Map<String, String> query = new HashMap<>();
        String queryString = req.getQueryString();
        String[] split = queryString.split("&");
        for (String param : split) {
            String[] entry = param.split("=");
            String key = entry[0];
            String value = entry[1];
            query.put(key, value);
        }

        String referer = req.getHeader("referer");
        System.out.println(referer);

        String origin = req.getHeader("origin");
        System.out.println(origin);

        String proxy = null;
        String editor = null;
        
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
                if (cookie.getName().equals("editor")) {
                    editor = cookie.getValue();
                }
            }
        } else {
            System.out.println("No cookies present in request");
        }

        String path = proxy;
        String url = query.get("url");
        if (url != null) {
            path = url;
        }
        System.out.print(path);

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

        byte[] bytes = response.getBytes();
        if (contentType.startsWith("text/html")) {
            String html = new String(bytes);
            String html2 = UrlReplacer.replaceUrls(html, "http://localhost:8888/editor/?url=", proxy);
            bytes = html2.getBytes();
        } else if (contentType.startsWith("text/javascript")) {
            String js = new String(bytes);
            String js2 = UrlReplacer.replaceJsUrls(js, "http://localhost:8888/editor/?url=", path);
            bytes = js2.getBytes();
        }
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
        .contentType(MediaType.valueOf(contentType));

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
