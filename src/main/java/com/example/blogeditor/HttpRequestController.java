package com.example.blogeditor;

import java.io.ByteArrayInputStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMapAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.commands.HttpRequestCommand;
import com.example.commands.HttpResponseModel;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/editor/**")
public class HttpRequestController {
    @GetMapping
    @ResponseBody
    public ResponseEntity<InputStreamResource> get(HttpServletRequest req) throws Exception {
        System.out.println(req.getServletPath());

        String base = "https://throwaway95857209.github.io/blog/";
        String path = base;
        if (req.getServletPath() != null) {
            path += req.getServletPath().substring(8);
        }

        HttpResponseModel response = new HttpRequestCommand(path).execute();
        MultiValueMapAdapter<String, String> map = new MultiValueMapAdapter<>(response.getHeaders());
        int status = response.getStatus();
        System.out.println(status);

        String contentType = map.get("Content-Type").get(0);
        ByteArrayInputStream in = new ByteArrayInputStream(response.getBytes());

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(contentType))
                .body(new InputStreamResource(in));
    }
}
