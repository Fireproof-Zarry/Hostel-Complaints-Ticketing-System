package com.example.demo.hostel.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The all-in-one Docker image serves the React app from this server (app.serve-frontend).
 * Its pages are routes inside the app, not files, so opening or refreshing /student or
 * /admin must return index.html. On Render the frontend is a separate site and this is off.
 */
@Controller
@ConditionalOnProperty(name = "app.serve-frontend", havingValue = "true")
public class FrontendController {

    @GetMapping({"/", "/login", "/student", "/admin", "/profile"})
    public String app() {
        return "forward:/index.html";
    }
}
