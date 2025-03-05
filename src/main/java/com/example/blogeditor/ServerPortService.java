package com.example.blogeditor;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

// @Service
public class ServerPortService {
    private int port;

    public int getPort() {
        return port;
    }

    @EventListener
    public void onApplicationEvent(final ServletWebServerInitializedEvent event) {
        port = event.getWebServer().getPort();
        System.out.println("Port: ${port}".replace("${port}", String.valueOf(port)));

        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    desktop.browse(new URI("http://localhost:${port}".replace("${port}", String.valueOf(port))));
                } catch (IOException | URISyntaxException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("Desktop browsing not supported.");
            }
        } else {
            System.out.println("Desktop not supported.");
        }
    }
}