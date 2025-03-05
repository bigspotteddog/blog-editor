package com.example.blogeditor;

import java.io.ByteArrayInputStream;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMapAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.commands.HttpRequestCommand;
import com.example.commands.HttpResponseModel;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/editor/**")
public class HttpRequestController {
    @GetMapping
    @ResponseBody
    public ResponseEntity<InputStreamResource> get(HttpServletRequest req) throws Exception {
        System.out.println(req.getServletPath());

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

        String base = "/";
        if (proxy != null) {
            base = proxy;
        }
        if (editor != null) {
            base = editor;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String end = req.getRequestURI().replace(base, "");
        if (end.equals("/")) {
            end = "";
        }
        if (end.startsWith("/")) {
            end = end.substring(1);
        }
        if (end.startsWith("editor")) {
            end = end.substring(6);
        }
        if (end.startsWith("/")) {
            end = end.substring(1);
        }
        String path = base + "/" + end;

        HttpResponseModel response = new HttpRequestCommand(path).execute();
        MultiValueMapAdapter<String, String> map = new MultiValueMapAdapter<>(response.getHeaders());
        int status = response.getStatus();
        System.out.println(status);

        // Handle redirect
        if (status == 302 || status == 301) {
            String redirectLocation = map.getFirst("Location");
            if (redirectLocation == null) {
                redirectLocation = map.getFirst("location");
            }
            // Either follow the redirect or pass it back to the client
            return ResponseEntity.status(status)
                .header(HttpHeaders.LOCATION, "/editor" + redirectLocation.replace(base, ""))
                .build();
        }

        List<String> contentTypeList = map.get("Content-Type");
        if (contentTypeList == null) {
            contentTypeList = map.get("content-type");
        }
        String contentType = contentTypeList.getFirst();
        ByteArrayInputStream in = new ByteArrayInputStream(response.getBytes());

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
