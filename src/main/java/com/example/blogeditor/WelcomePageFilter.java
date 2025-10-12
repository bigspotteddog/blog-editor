package com.example.blogeditor;

import java.io.IOException;

import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class WelcomePageFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();
        
        // Skip if it's the root path or has a file extension
        if (path.equals("/") || path.contains(".")) {
            chain.doFilter(request, response);
            return;
        }
        
        // If path doesn't end with trailing slash, redirect to add it
        if (!path.endsWith("/")) {
            httpResponse.sendRedirect(path + "/");
            return;
        }
        
        // If path ends with /, forward to index.html
        if (path.endsWith("/")) {
            httpRequest.getRequestDispatcher(path + "index.html").forward(request, response);
            return;
        }
        
        chain.doFilter(request, response);
    }
}